package com.nexus.agent.core.cli

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHandler(private val activity: Activity) {

    companion object {
        const val REQUEST_CODE = 1001
    }

    val requiredPermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.INTERNET,
        Manifest.permission.RECORD_AUDIO
    )

    fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions() {
        ActivityCompat.requestPermissions(activity, requiredPermissions, REQUEST_CODE)
    }

    fun shouldShowRationale(): Boolean {
        return requiredPermissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
    }
}
