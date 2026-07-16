package com.nexus.agent.core.sandbox

import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Управляет изоляцией процессов в отдельных Linux namespaces.
 * Создаёт PID, mount, network и UTS namespaces для полной изоляции.
 */
class NamespaceContainer(
    private val config: SandboxConfig
) {
    companion object {
        private const val TAG = "NamespaceContainer"
        private const val UNshare_FLAGS = 
            OsConstants.CLONE_NEWNS or      // mount namespace
            OsConstants.CLONE_NEWPID or       // PID namespace
            OsConstants.CLONE_NEWNET or       // network namespace
            OsConstants.CLONE_NEWUTS or       // UTS namespace
            OsConstants.CLONE_NEWIPC or       // IPC namespace
            OsConstants.CLONE_NEWUSER         // user namespace
    }

    private var isInitialized = false
    private var namespaceFd: ParcelFileDescriptor? = null
    private val mountPoints = mutableListOf<File>()
    private val boundPaths = mutableMapOf<String, String>()

    /**
     * Инициализирует namespace-контейнер.
     * Создаёт rootfs overlay и настраивает mount namespace.
     */
    @Throws(IOException::class)
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.w(TAG, "Namespace already initialized")
            return true
        }

        return try {
            // Создаём базовую директорию sandbox
            val sandboxRoot = File(config.sandboxRootPath)
            if (!sandboxRoot.exists()) {
                sandboxRoot.mkdirs()
            }

            // Создаём overlay structure (lower, upper, work, merged)
            createOverlayStructure(sandboxRoot)

            // Создаём необходимые mount points
            createMountPoints(sandboxRoot)

            // Сохраняем file descriptor текущего namespace для возврата
            namespaceFd = ParcelFileDescriptor.open(
                File("/proc/self/ns/mnt"),
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            isInitialized = true
            Log.i(TAG, "Namespace container initialized at ${sandboxRoot.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize namespace", e)
            cleanup()
            false
        }
    }

    /**
     * Создаёт overlay filesystem структуру для изолированного rootfs.
     */
    private fun createOverlayStructure(root: File) {
        val dirs = listOf("lower", "upper", "work", "merged")
        dirs.forEach { dirName ->
            val dir = File(root, dirName)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            mountPoints.add(dir)
        }

        // Копируем базовые системные файлы в lower layer
        val lowerDir = File(root, "lower")
        setupBaseFilesystem(lowerDir)
    }

    /**
     * Настраивает базовую файловую систему в lower layer.
     */
    private fun setupBaseFilesystem(lowerDir: File) {
        // Создаём стандартную Unix FHS структуру
        val fhsDirs = listOf(
            "bin", "etc", "lib", "lib64", "proc", "sys",
            "tmp", "usr", "usr/bin", "usr/lib", "dev", "home/sandbox"
        )

        fhsDirs.forEach { path ->
            File(lowerDir, path).mkdirs()
        }

        // Создаём базовые конфигурационные файлы
        File(lowerDir, "etc/passwd").writeText(
            "sandbox:x:1000:1000:sandbox:/home/sandbox:/bin/sh\n"
        )
        File(lowerDir, "etc/group").writeText(
            "sandbox:x:1000:\n"
        )
        File(lowerDir, "etc/hosts").writeText(
            "127.0.0.1 localhost\n::1 localhost\n"
        )

        // Создаём симлинки на системные библиотеки если нужно
        if (config.allowSystemLibraries) {
            bindSystemLibraries(lowerDir)
        }
    }

    /**
     * Монтирует системные библиотеки в sandbox (read-only).
     */
    private fun bindSystemLibraries(lowerDir: File) {
        val systemLibPaths = listOf(
            "/system/lib",
            "/system/lib64",
            "/vendor/lib",
            "/vendor/lib64"
        )

        systemLibPaths.forEach { srcPath ->
            val src = File(srcPath)
            if (src.exists()) {
                val dest = File(lowerDir, srcPath.removePrefix("/"))
                dest.parentFile?.mkdirs()
                boundPaths[srcPath] = dest.absolutePath
            }
        }
    }

    /**
     * Создаёт необходимые mount points (proc, sys, dev, tmp).
     */
    private fun createMountPoints(root: File) {
        val mergedDir = File(root, "merged")

        // procfs - изолированный /proc
        val procDir = File(mergedDir, "proc")
        procDir.mkdirs()
        mountPoints.add(procDir)

        // sysfs - ограниченный доступ
        val sysDir = File(mergedDir, "sys")
        sysDir.mkdirs()
        mountPoints.add(sysDir)

        // devfs - минимальный набор устройств
        val devDir = File(mergedDir, "dev")
        devDir.mkdirs()
        createMinimalDevices(devDir)
        mountPoints.add(devDir)

        // tmpfs для /tmp
        val tmpDir = File(mergedDir, "tmp")
        tmpDir.mkdirs()
        mountPoints.add(tmpDir)
    }

    /**
     * Создаёт минимальный набор device nodes.
     */
    private fun createMinimalDevices(devDir: File) {
        // null, zero, random, urandom, tty
        val devices = listOf(
            Triple("null", 1, 3),
            Triple("zero", 1, 5),
            Triple("random", 1, 8),
            Triple("urandom", 1, 9),
            Triple("tty", 5, 0)
        )

        devices.forEach { (name, major, minor) ->
            val devicePath = File(devDir, name).absolutePath
            try {
                Os.mknod(devicePath, OsConstants.S_IFCHR or 0o666, Os.makedev(major, minor))
            } catch (e: Exception) {
                Log.w(TAG, "Cannot create device node $name: ${e.message}")
                // Fallback: создаём пустой файл
                File(devDir, name).createNewFile()
            }
        }
    }

    /**
     * Выполняет unshare для создания новых namespaces.
     * Требует CAP_SYS_ADMIN или root (для некоторых namespace types).
     */
    @Throws(IOException::class)
    fun enterNamespace(): Boolean {
        if (!isInitialized) {
            throw IllegalStateException("Namespace not initialized. Call initialize() first.")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Os.unshare(UNshare_FLAGS)
                Log.d(TAG, "Successfully entered new namespaces")
                true
            } else {
                Log.e(TAG, "Namespaces not supported on API < 21")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unshare namespaces", e)
            false
        }
    }

    /**
     * Монтирует overlay filesystem.
     */
    @Throws(IOException::class)
    fun mountOverlay(): Boolean {
        val root = File(config.sandboxRootPath)
        val lowerDir = File(root, "lower")
        val upperDir = File(root, "upper")
        val workDir = File(root, "work")
        val mergedDir = File(root, "merged")

        if (!mergedDir.exists()) {
            mergedDir.mkdirs()
        }

        val overlayOptions = 
            "lowerdir=${lowerDir.absolutePath}," +
            "upperdir=${upperDir.absolutePath}," +
            "workdir=${workDir.absolutePath}"

        return try {
            Os.mount(
                "overlay",
                mergedDir.absolutePath,
                "overlay",
                OsConstants.MS_NOATIME,
                overlayOptions
            )
            Log.i(TAG, "Overlay mounted at ${mergedDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mount overlay", e)
            false
        }
    }

    /**
     * Выполняет pivot_root для смены rootfs.
     */
    @Throws(IOException::class)
    fun pivotRoot(): Boolean {
        val root = File(config.sandboxRootPath)
        val mergedDir = File(root, "merged")
        val oldRoot = File(mergedDir, ".old_root")

        if (!mergedDir.exists()) {
            throw IOException("Merged directory does not exist")
        }

        return try {
            oldRoot.mkdirs()

            // pivot_root(new_root, put_old)
            Os.pivot_root(mergedDir.absolutePath, oldRoot.absolutePath)

            // Отмонтируем старый root
            Os.umount2("/.old_root", OsConstants.MNT_DETACH)

            // Меняем текущую директорию
            Os.chdir("/")

            Log.i(TAG, "Pivot root successful")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pivot root failed", e)
            false
        }
    }

    /**
     * Настраивает сетевую изоляцию (loopback only).
     */
    fun setupNetworkIsolation(): Boolean {
        return try {
            // В новом network namespace есть только loopback
            // Активируем его
            val loInterface = java.net.NetworkInterface.getByName("lo")
            if (loInterface != null && !loInterface.isUp) {
                // Требует root для включения интерфейса
                Log.w(TAG, "Loopback interface may need root to be brought up")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Network isolation setup failed", e)
            false
        }
    }

    /**
     * Настраивает user namespace mapping (uid/gid map).
     */
    fun setupUserMapping(realUid: Int, realGid: Int): Boolean {
        return try {
            val uidMap = "0 $realUid 1\n"
            val gidMap = "0 $realUid 1\n"

            File("/proc/self/uid_map").writeText(uidMap)
            File("/proc/self/setgroups").writeText("deny")
            File("/proc/self/gid_map").writeText(gidMap)

            Log.d(TAG, "User namespace mapping configured")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup user mapping", e)
            false
        }
    }

    /**
     * Возвращает путь к изолированному rootfs.
     */
    fun getSandboxRoot(): String {
        return File(config.sandboxRootPath, "merged").absolutePath
    }

    /**
     * Возвращает исходный namespace (для восстановления).
     */
    fun getOriginalNamespace(): ParcelFileDescriptor? {
        return namespaceFd
    }

    /**
     * Очищает все ресурсы и отмонтирует mount points.
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up namespace container")

        // Отмонтируем overlay
        val mergedDir = File(config.sandboxRootPath, "merged")
        if (mergedDir.exists()) {
            try {
                Os.umount2(mergedDir.absolutePath, OsConstants.MNT_FORCE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unmount overlay: ${e.message}")
            }
        }

        // Отмонтируем остальные mount points в обратном порядке
        mountPoints.reversed().forEach { mountPoint ->
            try {
                Os.umount2(mountPoint.absolutePath, OsConstants.MNT_FORCE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unmount ${mountPoint.absolutePath}: ${e.message}")
            }
        }

        namespaceFd?.close()
        namespaceFd = null

        // Удаляем директории если нужно
        if (config.cleanUpOnExit) {
            File(config.sandboxRootPath).deleteRecursively()
        }

        isInitialized = false
        mountPoints.clear()
        boundPaths.clear()
    }

    /**
     * Проверяет, находимся ли мы внутри sandbox namespace.
     */
    fun isInsideSandbox(): Boolean {
        return try {
            val currentNs = File("/proc/self/ns/mnt").canonicalPath
            val originalNs = namespaceFd?.let {
                File("/proc/self/fd/${it.fd}").canonicalPath
            }
            currentNs != originalNs
        } catch (e: Exception) {
            false
        }
    }
}
