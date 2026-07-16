package com.nexus.agent.core.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePermissionHelper @Inject constructor(
    private val context: Context,
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun getRequiredPermissions(): Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    fun isAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
}