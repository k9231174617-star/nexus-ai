package com.nexus.agent.core.root

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SystemModifier — high-level system modification operations via root.
 * Handles build.prop edits, hosts file, DNS, firewall rules, and system tweaks.
 */
class SystemModifier(
    private val context: Context,
    private val bridge: RootBridge,
    private val suChecker: SuChecker
) {
    companion object {
        private const val TAG = "SystemModifier"
        private const val BUILD_PROP_PATH = "/system/build.prop"
        private const val BUILD_PROP_BACKUP = "/system/build.prop.bak"
        private const val HOSTS_PATH = "/system/etc/hosts"
        private const val HOSTS_BACKUP = "/system/etc/hosts.bak"
        private const val DNS_PATH = "/system/etc/resolv.conf"
    }

    private val propCache = mutableMapOf<String, String>()

    suspend fun readBuildProp(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext emptyMap()
        }

        val result = bridge.readFile(BUILD_PROP_PATH)
        if (result.isFailure) {
            Log.e(TAG, "Failed to read build.prop")
            return@withContext emptyMap()
        }

        val props = mutableMapOf<String, String>()
        result.getOrNull()?.lines()?.forEach { line ->
            if (line.contains("=") && !line.trimStart().startsWith("#")) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    props[parts[0].trim()] = parts[1].trim()
                }
            }
        }

        propCache.clear()
        propCache.putAll(props)
        props
    }

    suspend fun getProp(key: String, defaultValue: String = ""): String = withContext(Dispatchers.IO) {
        propCache[key] ?: run {
            val result = bridge.execute("getprop \"$key\" 2>/dev/null || echo \"__NOT_FOUND__\"")
            if (result.isSuccess) {
                val value = result.getOrNull()?.stdout?.trim() ?: defaultValue
                if (value != "__NOT_FOUND__") {
                    propCache[key] = value
                    return@withContext value
                }
            }
            defaultValue
        }
    }

    suspend fun setProp(key: String, value: String, persist: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext false
        }

        // Set runtime property
        val runtimeResult = bridge.execute("setprop \"$key\" \"$value\"")
        if (runtimeResult.isFailure || runtimeResult.getOrNull()?.exitCode != 0) {
            Log.e(TAG, "Failed to set runtime prop: $key")
            return@withContext false
        }

        propCache[key] = value

        // Persist to build.prop if requested
        if (persist) {
            val persistResult = persistBuildProp(key, value)
            if (!persistResult) {
                Log.w(TAG, "Failed to persist prop to build.prop")
            }
        }

        true
    }

    suspend fun persistBuildProp(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val remountResult = bridge.remountSystem(true)
        if (remountResult.isFailure) {
            Log.e(TAG, "Failed to remount system RW")
            return@withContext false
        }

        // Backup if not exists
        if (!bridge.fileExists(BUILD_PROP_BACKUP)) {
            bridge.execute("cp \"$BUILD_PROP_PATH\" \"$BUILD_PROP_BACKUP\"")
        }

        // Check if key exists
        val checkResult = bridge.execute("grep -q \"^$key=\" \"$BUILD_PROP_PATH\" && echo \"exists\" || echo \"missing\"")
        val exists = checkResult.isSuccess && checkResult.getOrNull()?.stdout?.trim() == "exists"

        val command = if (exists) {
            "sed -i \"s/^$key=.*/$key=$value/\" \"$BUILD_PROP_PATH\""
        } else {
            "echo \"$key=$value\" >> \"$BUILD_PROP_PATH\""
        }

        val result = bridge.execute(command)
        if (result.isFailure || result.getOrNull()?.exitCode != 0) {
            Log.e(TAG, "Failed to modify build.prop")
            return@withContext false
        }

        // Verify
        val verify = bridge.execute("grep \"^$key=\" \"$BUILD_PROP_PATH\"")
        verify.isSuccess && verify.getOrNull()?.stdout?.contains("$key=$value") == true
    }

    suspend fun removeProp(key: String): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext false
        }

        val remountResult = bridge.remountSystem(true)
        if (remountResult.isFailure) {
            Log.e(TAG, "Failed to remount system RW")
            return@withContext false
        }

        val result = bridge.execute("sed -i \"/^$key=/d\" \"$BUILD_PROP_PATH\"")
        propCache.remove(key)

        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun restoreBuildProp(): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.fileExists(BUILD_PROP_BACKUP)) {
            Log.w(TAG, "No build.prop backup found")
            return@withContext false
        }

        val remountResult = bridge.remountSystem(true)
        if (remountResult.isFailure) {
            Log.e(TAG, "Failed to remount system RW")
            return@withContext false
        }

        val result = bridge.execute("cp \"$BUILD_PROP_BACKUP\" \"$BUILD_PROP_PATH\" && chmod 644 \"$BUILD_PROP_PATH\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun readHosts(): List<HostEntry> = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext emptyList()
        }

        val result = bridge.readFile(HOSTS_PATH)
        if (result.isFailure) {
            Log.e(TAG, "Failed to read hosts file")
            return@withContext emptyList()
        }

        val entries = mutableListOf<HostEntry>()
        result.getOrNull()?.lines()?.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                entries.add(HostEntry(comment = trimmed))
                return@forEach
            }

            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                entries.add(HostEntry(ip = parts[0], hostname = parts[1], comment = null))
            }
        }

        entries
    }

    suspend fun addHostEntry(ip: String, hostname: String): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext false
        }

        val remountResult = bridge.remountSystem(true)
        if (remountResult.isFailure) {
            Log.e(TAG, "Failed to remount system RW")
            return@withContext false
        }

        // Backup if not exists
        if (!bridge.fileExists(HOSTS_BACKUP)) {
            bridge.execute("cp \"$HOSTS_PATH\" \"$HOSTS_BACKUP\"")
        }

        val entry = "$ip\t$hostname"
        val result = bridge.execute("echo \"$entry\" >> \"$HOSTS_PATH\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun removeHostEntry(hostname: String): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext false
        }

        val remountResult = bridge.remountSystem(true)
        if (remountResult.isFailure) {
            Log.e(TAG, "Failed to remount system RW")
            return@withContext false
        }

        val result = bridge.execute("sed -i \"/$hostname/d\" \"$HOSTS_PATH\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun blockHost(hostname: String): Boolean = addHostEntry("127.0.0.1", hostname)

    suspend fun unblockHost(hostname: String): Boolean = removeHostEntry(hostname)

    suspend fun restoreHosts(): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.fileExists(HOSTS_BACKUP)) {
            Log.w(TAG, "No hosts backup found")
            return@withContext false
        }

        val remountResult = bridge.remountSystem(true)
        if (remountResult.isFailure) {
            Log.e(TAG, "Failed to remount system RW")
            return@withContext false
        }

        val result = bridge.execute("cp \"$HOSTS_BACKUP\" \"$HOSTS_PATH\" && chmod 644 \"$HOSTS_PATH\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setDns(servers: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext false
        }

        val remountResult = bridge.remountSystem(true)
        if (remountResult.isFailure) {
            Log.e(TAG, "Failed to remount system RW")
            return@withContext false
        }

        val content = buildString {
            servers.forEach { appendLine("nameserver $it") }
        }

        val result = bridge.writeFile(DNS_PATH, content)
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun addIptablesRule(chain: String, rule: String): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext false
        }

        val result = bridge.execute("iptables -$chain $rule")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun removeIptablesRule(chain: String, rule: String): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext false
        }

        val result = bridge.execute("iptables -D $chain $rule")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun flushIptables(): Boolean = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext false
        }

        val result = bridge.execute("iptables -F && iptables -X && iptables -t nat -F && iptables -t nat -X && iptables -t mangle -F && iptables -t mangle -X")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun listIptablesRules(): String = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) {
            val connected = bridge.connect()
            if (!connected) return@withContext ""
        }

        val result = bridge.execute("iptables -L -v -n --line-numbers")
        result.getOrNull()?.stdout ?: ""
    }

    suspend fun blockPort(port: Int, protocol: String = "tcp"): Boolean = withContext(Dispatchers.IO) {
        addIptablesRule("A", "INPUT -p $protocol --dport $port -j DROP")
    }

    suspend fun allowPort(port: Int, protocol: String = "tcp"): Boolean = withContext(Dispatchers.IO) {
        addIptablesRule("I", "INPUT -p $protocol --dport $port -j ACCEPT")
    }

    suspend fun blockIp(ip: String): Boolean = withContext(Dispatchers.IO) {
        addIptablesRule("A", "INPUT -s $ip -j DROP")
    }

    suspend fun allowIp(ip: String): Boolean = withContext(Dispatchers.IO) {
        addIptablesRule("I", "INPUT -s $ip -j ACCEPT")
    }

    suspend fun enableIpForwarding(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val value = if (enable) "1" else "0"
        val result = bridge.execute("echo $value > /proc/sys/net/ipv4/ip_forward")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setTcpWindowScaling(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val value = if (enable) "1" else "0"
        val result = bridge.execute("echo $value > /proc/sys/net/ipv4/tcp_window_scaling")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setTcpTimestamps(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val value = if (enable) "1" else "0"
        val result = bridge.execute("echo $value > /proc/sys/net/ipv4/tcp_timestamps")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setKernelParam(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("echo \"$value\" > \"$path\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun getKernelParam(path: String): String = withContext(Dispatchers.IO) {
        val result = bridge.execute("cat \"$path\" 2>/dev/null || echo \"__READ_ERROR__\"")
        result.getOrNull()?.stdout?.trim()?.takeIf { it != "__READ_ERROR__" } ?: ""
    }

    suspend fun setScheduler(device: String, scheduler: String): Boolean = withContext(Dispatchers.IO) {
        val path = "/sys/block/$device/queue/scheduler"
        val result = bridge.execute("echo \"$scheduler\" > \"$path\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun getScheduler(device: String): String = withContext(Dispatchers.IO) {
        val path = "/sys/block/$device/queue/scheduler"
        val result = bridge.execute("cat \"$path\"")
        result.getOrNull()?.stdout?.trim() ?: ""
    }

    suspend fun setReadAhead(device: String, kb: Int): Boolean = withContext(Dispatchers.IO) {
        val path = "/sys/block/$device/queue/read_ahead_kb"
        val result = bridge.execute("echo \"$kb\" > \"$path\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setGovernor(cpu: Int, governor: String): Boolean = withContext(Dispatchers.IO) {
        val path = "/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor"
        val result = bridge.execute("echo \"$governor\" > \"$path\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun getGovernor(cpu: Int): String = withContext(Dispatchers.IO) {
        val path = "/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor"
        val result = bridge.execute("cat \"$path\"")
        result.getOrNull()?.stdout?.trim() ?: ""
    }

    suspend fun setMinFreq(cpu: Int, freq: Int): Boolean = withContext(Dispatchers.IO) {
        val path = "/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq"
        val result = bridge.execute("echo \"$freq\" > \"$path\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setMaxFreq(cpu: Int, freq: Int): Boolean = withContext(Dispatchers.IO) {
        val path = "/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_max_freq"
        val result = bridge.execute("echo \"$freq\" > \"$path\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setSwappiness(value: Int): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("echo \"$value\" > /proc/sys/vm/swappiness")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setVfsCachePressure(value: Int): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("echo \"$value\" > /proc/sys/vm/vfs_cache_pressure")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun dropCaches(level: DropCacheLevel): Boolean = withContext(Dispatchers.IO) {
        val value = when (level) {
            DropCacheLevel.PAGE_CACHE -> "1"
            DropCacheLevel.DENTRIES_INODES -> "2"
            DropCacheLevel.ALL -> "3"
        }
        val result = bridge.execute("echo \"$value\" > /proc/sys/vm/drop_caches")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun remountPartition(partition: String, mode: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("mount -o remount,$mode $partition")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun bindMount(src: String, dst: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("mount -o bind \"$src\" \"$dst\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun unbindMount(dst: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("umount \"$dst\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun createLoopDevice(imagePath: String): String? = withContext(Dispatchers.IO) {
        val result = bridge.execute("losetup -f --show \"$imagePath\"")
        if (result.isSuccess) {
            result.getOrNull()?.stdout?.trim()?.takeIf { it.startsWith("/dev/loop") }
        } else null
    }

    suspend fun detachLoopDevice(device: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("losetup -d \"$device\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("sync")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun reboot(mode: RebootMode = RebootMode.NORMAL): Boolean = withContext(Dispatchers.IO) {
        val arg = when (mode) {
            RebootMode.NORMAL -> ""
            RebootMode.RECOVERY -> "recovery"
            RebootMode.BOOTLOADER -> "bootloader"
            RebootMode.SAFE_MODE -> "safe_mode"
        }
        val result = if (arg.isNotEmpty()) {
            bridge.execute("reboot $arg")
        } else {
            bridge.execute("reboot")
        }
        result.isSuccess
    }

    suspend fun softReboot(): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("setprop sys.powerctl reboot")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun shutdown(): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("reboot -p")
        result.isSuccess
    }

    suspend fun setPermission(path: String, mode: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("chmod $mode \"$path\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setOwner(path: String, owner: String, group: String? = null): Boolean = withContext(Dispatchers.IO) {
        val target = if (group != null) "$owner:$group" else owner
        val result = bridge.execute("chown $target \"$path\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun createSymlink(target: String, linkPath: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("ln -s \"$target\" \"$linkPath\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun removeSymlink(linkPath: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("rm \"$linkPath\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun isSymlink(path: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("[ -L \"$path\" ] && echo \"yes\" || echo \"no\"")
        result.isSuccess && result.getOrNull()?.stdout?.trim() == "yes"
    }

    suspend fun getSymlinkTarget(path: String): String = withContext(Dispatchers.IO) {
        val result = bridge.execute("readlink -f \"$path\"")
        result.getOrNull()?.stdout?.trim() ?: ""
    }

        suspend fun installApk(apkPath: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm install -r -d \"$apkPath\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun uninstallApk(packageName: String, keepData: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val flag = if (keepData) "-k" else ""
        val result = bridge.execute("pm uninstall $flag \"$packageName\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun clearAppData(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm clear \"$packageName\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("am force-stop \"$packageName\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun grantPermission(packageName: String, permission: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm grant \"$packageName\" \"$permission\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun revokePermission(packageName: String, permission: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm revoke \"$packageName\" \"$permission\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun setDefaultApp(packageName: String, category: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("cmd package set-home-activity \"$packageName\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun hideApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm hide \"$packageName\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun unhideApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm unhide \"$packageName\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun disableApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm disable-user --user 0 \"$packageName\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun enableApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm enable \"$packageName\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun isAppEnabled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm list packages -e | grep \"$packageName\"")
        result.isSuccess && result.getOrNull()?.stdout?.contains(packageName) == true
    }

    suspend fun getAppPath(packageName: String): String = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm path \"$packageName\"")
        result.getOrNull()?.stdout?.trim()?.removePrefix("package:") ?: ""
    }

    suspend fun backupApp(packageName: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val appPath = getAppPath(packageName)
        if (appPath.isEmpty()) return@withContext false

        val result = bridge.execute("cp \"$appPath\" \"$outputPath\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun restoreApp(backupPath: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("cp \"$backupPath\" \"$(pm path \"$packageName\" | sed 's/package://')\"")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun wipeDalvikCache(): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("rm -rf /data/dalvik-cache/* && rm -rf /data/user/*/dalvik-cache/*")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun fixPermissions(): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("pm compile --reset && pm compile -a && pm compile --check-prof false -a")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    suspend fun trimFstrim(): Boolean = withContext(Dispatchers.IO) {
        val result = bridge.execute("fstrim -v /data && fstrim -v /cache && fstrim -v /system")
        result.isSuccess && result.getOrNull()?.exitCode == 0
    }

    data class HostEntry(
        val ip: String? = null,
        val hostname: String? = null,
        val comment: String? = null
    ) {
        override fun toString(): String {
            return when {
                comment != null && comment.startsWith("#") -> comment
                ip != null && hostname != null -> "$ip\t$hostname"
                else -> ""
            }
        }
    }

    enum class DropCacheLevel {
        PAGE_CACHE, DENTRIES_INODES, ALL
    }

    enum class RebootMode {
        NORMAL, RECOVERY, BOOTLOADER, SAFE_MODE
    }
}
