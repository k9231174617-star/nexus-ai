package com.nexus.agent.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RootCommand — high-level root command builder with fluent API.
 * Constructs and executes complex root operations safely.
 */
class RootCommand(private val bridge: RootBridge) {

    private val commands = mutableListOf<String>()
    private var workingDirectory: String? = null
    private var environmentVars = mutableMapOf<String, String>()
    private var timeoutMs: Long = 30000L

    fun cd(path: String): RootCommand {
        workingDirectory = path
        return this
    }

    fun env(key: String, value: String): RootCommand {
        environmentVars[key] = value
        return this
    }

    fun timeout(ms: Long): RootCommand {
        timeoutMs = ms
        return this
    }

    fun run(command: String): RootCommand {
        commands.add(command)
        return this
    }

    fun cat(path: String): RootCommand {
        commands.add("cat \"$path\"")
        return this
    }

    fun echo(content: String, path: String? = null): RootCommand {
        val escaped = content.replace("\"", "\\\"")
        if (path != null) {
            commands.add("echo \"$escaped\" > \"$path\"")
        } else {
            commands.add("echo \"$escaped\"")
        }
        return this
    }

    fun mkdir(path: String, recursive: Boolean = true): RootCommand {
        val flag = if (recursive) "-p" else ""
        commands.add("mkdir $flag \"$path\"")
        return this
    }

    fun rm(path: String, recursive: Boolean = false, force: Boolean = true): RootCommand {
        val flags = buildString {
            if (recursive) append("r")
            if (force) append("f")
        }
        commands.add("rm -$flags \"$path\"")
        return this
    }

    fun cp(src: String, dst: String, recursive: Boolean = false): RootCommand {
        val flag = if (recursive) "-r" else ""
        commands.add("cp $flag \"$src\" \"$dst\"")
        return this
    }

    fun mv(src: String, dst: String): RootCommand {
        commands.add("mv \"$src\" \"$dst\"")
        return this
    }

    fun chmod(path: String, mode: String): RootCommand {
        commands.add("chmod $mode \"$path\"")
        return this
    }

    fun chown(path: String, owner: String, group: String? = null): RootCommand {
        val target = if (group != null) "$owner:$group" else owner
        commands.add("chown $target \"$path\"")
        return this
    }

    fun ln(src: String, dst: String, symbolic: Boolean = true): RootCommand {
        val flag = if (symbolic) "-s" else ""
        commands.add("ln $flag \"$src\" \"$dst\"")
        return this
    }

    fun touch(path: String): RootCommand {
        commands.add("touch \"$path\"")
        return this
    }

    fun find(path: String, name: String? = null, type: String? = null): RootCommand {
        val args = buildString {
            append("\"$path\"")
            if (name != null) append(" -name \"$name\"")
            if (type != null) append(" -type $type")
        }
        commands.add("find $args")
        return this
    }

    fun grep(pattern: String, path: String, recursive: Boolean = false): RootCommand {
        val flags = if (recursive) "-r" else ""
        commands.add("grep $flags \"$pattern\" \"$path\"")
        return this
    }

    fun sed(pattern: String, replacement: String, path: String): RootCommand {
        commands.add("sed -i 's/$pattern/$replacement/g' \"$path\"")
        return this
    }

    fun awk(script: String, input: String? = null): RootCommand {
        if (input != null) {
            commands.add("awk '$script' \"$input\"")
        } else {
            commands.add("awk '$script'")
        }
        return this
    }

    fun tar(mode: String, archive: String, paths: List<String>): RootCommand {
        val pathStr = paths.joinToString(" ") { "\"$it\"" }
        commands.add("tar $mode \"$archive\" $pathStr")
        return this
    }

    fun zip(archive: String, paths: List<String>): RootCommand {
        val pathStr = paths.joinToString(" ") { "\"$it\"" }
        commands.add("zip -r \"$archive\" $pathStr")
        return this
    }

    fun unzip(archive: String, destination: String? = null): RootCommand {
        val destFlag = if (destination != null) "-d \"$destination\"" else ""
        commands.add("unzip -o \"$archive\" $destFlag")
        return this
    }

    fun ps(options: String = "-A"): RootCommand {
        commands.add("ps $options")
        return this
    }

    fun kill(pid: Int, signal: Int = 9): RootCommand {
        commands.add("kill -$signal $pid")
        return this
    }

    fun mount(device: String, mountPoint: String, fsType: String? = null, options: String? = null): RootCommand {
        val args = buildString {
            append("\"$device\" \"$mountPoint\"")
            if (fsType != null) append(" -t $fsType")
            if (options != null) append(" -o $options")
        }
        commands.add("mount $args")
        return this
    }

    fun umount(mountPoint: String, force: Boolean = false): RootCommand {
        val flag = if (force) "-f" else ""
        commands.add("umount $flag \"$mountPoint\"")
        return this
    }

    fun dd(ifPath: String, ofPath: String, bs: String = "4M", count: Long? = null, seek: Long? = null, skip: Long? = null): RootCommand {
        val args = buildString {
            append("if=\"$ifPath\" of=\"$ofPath\" bs=$bs")
            if (count != null) append(" count=$count")
            if (seek != null) append(" seek=$seek")
            if (skip != null) append(" skip=$skip")
        }
        commands.add("dd $args")
        return this
    }

    fun hexdump(path: String, length: Int? = null, offset: Long? = null): RootCommand {
        val args = buildString {
            append("\"$path\"")
            if (length != null) append(" -n $length")
            if (offset != null) append(" -s $offset")
        }
        commands.add("hexdump -C $args")
        return this
    }

    fun strings(path: String, minLength: Int = 4): RootCommand {
        commands.add("strings -n $minLength \"$path\"")
        return this
    }

    fun pm(command: String, vararg args: String): RootCommand {
        val argStr = args.joinToString(" ") { "\"$it\"" }
        commands.add("pm $command $argStr")
        return this
    }

    fun am(action: String, vararg args: String): RootCommand {
        val argStr = args.joinToString(" ") { "\"$it\"" }
        commands.add("am $action $argStr")
        return this
    }

    fun dumpsys(service: String, vararg args: String): RootCommand {
        val argStr = args.joinToString(" ") { "\"$it\"" }
        commands.add("dumpsys $service $argStr")
        return this
    }

    fun logcat(options: String = "-d", filter: String? = null): RootCommand {
        val filterStr = if (filter != null) "\"$filter\"" else ""
        commands.add("logcat $options $filterStr")
        return this
    }

    fun getprop(property: String? = null): RootCommand {
        if (property != null) {
            commands.add("getprop \"$property\"")
        } else {
            commands.add("getprop")
        }
        return this
    }

    fun setprop(property: String, value: String): RootCommand {
        commands.add("setprop \"$property\" \"$value\"")
        return this
    }

    fun settings(namespace: String, command: String, key: String, value: String? = null): RootCommand {
        val valStr = if (value != null) "\"$value\"" else ""
        commands.add("settings $namespace $command \"$key\" $valStr")
        return this
    }

    fun iptables(vararg args: String): RootCommand {
        val argStr = args.joinToString(" ") { "\"$it\"" }
        commands.add("iptables $argStr")
        return this
    }

    fun ip(vararg args: String): RootCommand {
        val argStr = args.joinToString(" ") { "\"$it\"" }
        commands.add("ip $argStr")
        return this
    }

    fun ping(host: String, count: Int = 4): RootCommand {
        commands.add("ping -c $count \"$host\"")
        return this
    }

    fun curl(url: String, output: String? = null, headers: Map<String, String> = emptyMap()): RootCommand {
        val headerArgs = headers.entries.joinToString(" ") { "-H \"${it.key}: ${it.value}\"" }
        val outputArg = if (output != null) "-o \"$output\"" else ""
        commands.add("curl -L $headerArgs $outputArg \"$url\"")
        return this
    }

    fun wget(url: String, output: String? = null): RootCommand {
        val outputArg = if (output != null) "-O \"$output\"" else ""
        commands.add("wget $outputArg \"$url\"")
        return this
    }

    fun base64(mode: String = "-d", input: String? = null): RootCommand {
        if (input != null) {
            commands.add("echo \"$input\" | base64 $mode")
        } else {
            commands.add("base64 $mode")
        }
        return this
    }

    fun md5sum(path: String): RootCommand {
        commands.add("md5sum \"$path\"")
        return this
    }

    fun sha256sum(path: String): RootCommand {
        commands.add("sha256sum \"$path\"")
        return this
    }

    fun openssl(command: String, vararg args: String): RootCommand {
        val argStr = args.joinToString(" ") { "\"$it\"" }
        commands.add("openssl $command $argStr")
        return this
    }

    fun sqlite3(database: String, query: String): RootCommand {
        commands.add("sqlite3 \"$database\" \"$query\"")
        return this
    }

    fun scp(src: String, dst: String, port: Int = 22, identity: String? = null): RootCommand {
        val idFlag = if (identity != null) "-i \"$identity\"" else ""
        commands.add("scp -P $port $idFlag \"$src\" \"$dst\"")
        return this
    }

    fun ssh(host: String, command: String, port: Int = 22, identity: String? = null): RootCommand {
        val idFlag = if (identity != null) "-i \"$identity\"" else ""
        commands.add("ssh -p $port $idFlag \"$host\" \"$command\"")
        return this
    }

    fun nc(host: String, port: Int): RootCommand {
        commands.add("nc -v \"$host\" $port")
        return this
    }

    fun netstat(options: String = "-tulpn"): RootCommand {
        commands.add("netstat $options")
        return this
    }

    fun lsof(path: String? = null): RootCommand {
        if (path != null) {
            commands.add("lsof \"$path\"")
        } else {
            commands.add("lsof")
        }
        return this
    }

    fun strace(pid: Int, options: String = "-f -e trace=all"): RootCommand {
        commands.add("strace $options -p $pid")
        return this
    }

    fun tcpdump(interfaceName: String = "any", filter: String? = null): RootCommand {
        val filterStr = if (filter != null) "\"$filter\"" else ""
        commands.add("tcpdump -i $interfaceName $filterStr")
        return this
    }

    fun ifconfig(interfaceName: String? = null): RootCommand {
        if (interfaceName != null) {
            commands.add("ifconfig \"$interfaceName\"")
        } else {
            commands.add("ifconfig")
        }
        return this
    }

    fun route(): RootCommand {
        commands.add("route -n")
        return this
    }

    fun arp(): RootCommand {
        commands.add("cat /proc/net/arp")
        return this
    }

    fun catProc(path: String): RootCommand {
        commands.add("cat \"/proc/$path\"")
        return this
    }

    fun catSys(path: String): RootCommand {
        commands.add("cat \"/sys/$path\"")
        return this
    }

    fun ls(path: String, options: String = "-la"): RootCommand {
        commands.add("ls $options \"$path\"")
        return this
    }

    fun df(path: String? = null): RootCommand {
        if (path != null) {
            commands.add("df -h \"$path\"")
        } else {
            commands.add("df -h")
        }
        return this
    }

    fun du(path: String, options: String = "-sh"): RootCommand {
        commands.add("du $options \"$path\"")
        return this
    }

    fun free(): RootCommand {
        commands.add("free -h")
        return this
    }

    fun top(options: String = "-n 1"): RootCommand {
        commands.add("top $options")
        return this
    }

    fun uptime(): RootCommand {
        commands.add("uptime")
        return this
    }

    fun whoami(): RootCommand {
        commands.add("whoami")
        return this
    }

    fun id(): RootCommand {
        commands.add("id")
        return this
    }

    fun uname(options: String = "-a"): RootCommand {
        commands.add("uname $options")
        return this
    }

    fun build(): BuiltCommand {
        val sb = StringBuilder()

        workingDirectory?.let { sb.appendLine("cd \"$it\"") }

        environmentVars.forEach { (k, v) ->
            sb.appendLine("export $k=\"$v\"")
        }

        if (commands.size == 1) {
            sb.append(commands.first())
        } else {
            commands.forEach { cmd ->
                sb.appendLine(cmd)
            }
        }

        return BuiltCommand(sb.toString().trimEnd(), timeoutMs)
    }

    suspend fun execute(): Result<RootBridge.CommandOutput> {
        val built = build()
        return bridge.execute(built.command, built.timeoutMs)
    }

    data class BuiltCommand(
        val command: String,
        val timeoutMs: Long
    )
}
