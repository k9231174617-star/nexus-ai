package com.nexus.agent.core.cli

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellSession @Inject constructor(
    private val suChecker: com.nexus.agent.core.root.SuChecker,
) {
    private var userProcess: Process? = null
    private var rootProcess: Process? = null

    fun getUserSession(): Process? {
        if (userProcess?.isAlive == true) return userProcess
        return try {
            userProcess = ProcessBuilder("sh").apply {
                redirectErrorStream(true)
            }.start()
            userProcess
        } catch (e: Exception) {
            Log.e("ShellSession", "Failed to start user shell", e)
            null
        }
    }

    fun getRootSession(): Process? {
        if (!suChecker.isRootAvailable()) return null
        if (rootProcess?.isAlive == true) return rootProcess
        return try {
            rootProcess = ProcessBuilder("su").apply {
                redirectErrorStream(true)
            }.start()
            rootProcess
        } catch (e: Exception) {
            Log.e("ShellSession", "Failed to start root shell", e)
            null
        }
    }

    fun closeAll() {
        userProcess?.destroy()
        rootProcess?.destroy()
        userProcess = null
        rootProcess = null
    }
}