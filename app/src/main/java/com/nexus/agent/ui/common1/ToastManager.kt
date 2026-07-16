package com.nexus.agent.ui.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.nexus.agent.R
import com.nexus.agent.databinding.ViewCustomToastBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Custom toast/snackbar manager with Nexus AI styling
 */
object ToastManager {

    private const val SHORT_DURATION = 2000L
    private const val LONG_DURATION = 3500L

    // Success toast
    fun success(anchor: View, message: String, durationMs: Long = SHORT_DURATION) {
        showCustomToast(anchor, message, ToastType.SUCCESS, durationMs)
    }

    // Error toast
    fun error(anchor: View, message: String, durationMs: Long = LONG_DURATION) {
        showCustomToast(anchor, message, ToastType.ERROR, durationMs)
    }

    // Info toast
    fun info(anchor: View, message: String, durationMs: Long = SHORT_DURATION) {
        showCustomToast(anchor, message, ToastType.INFO, durationMs)
    }

    // Warning toast
    fun warning(anchor: View, message: String, durationMs: Long = LONG_DURATION) {
        showCustomToast(anchor, message, ToastType.WARNING, durationMs)
    }

    // Loading toast with auto-dismiss
    fun loading(anchor: View, message: String): () -> Unit {
        val dismiss = showPersistentToast(anchor, message, ToastType.LOADING)
        return dismiss
    }

    private fun showCustomToast(
        anchor: View,
        message: String,
        type: ToastType,
        durationMs: Long
    ) {
        val parent = findSuitableParent(anchor) ?: return
        
        val binding = ViewCustomToastBinding.inflate(
            LayoutInflater.from(anchor.context),
            parent,
            false
        )

        setupToastView(binding, message, type)

        parent.addView(binding.root)
        
        // Animate in
        binding.root.alpha = 0f
        binding.root.translationY = 50f
        binding.root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Auto dismiss
        anchor.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            delay(durationMs)
            dismissToast(binding.root, parent)
        }
    }

    private fun showPersistentToast(
        anchor: View,
        message: String,
        type: ToastType
    ): () -> Unit {
        val parent = findSuitableParent(anchor) ?: return {}
        
        val binding = ViewCustomToastBinding.inflate(
            LayoutInflater.from(anchor.context),
            parent,
            false
        )

        setupToastView(binding, message, type)
        
        // Add progress indicator for loading
        binding.progressBar.visibility = View.VISIBLE

        parent.addView(binding.root)
        
        // Animate in
        binding.root.alpha = 0f
        binding.root.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        return {
            dismissToast(binding.root, parent)
        }
    }

    private fun setupToastView(
        binding: ViewCustomToastBinding,
        message: String,
        type: ToastType
    ) {
        binding.apply {
            tvMessage.text = message
            
            val (iconRes, bgRes, colorRes) = when (type) {
                ToastType.SUCCESS -> Triple(
                    R.drawable.ic_connection_ok,
                    R.drawable.bg_toast_success,
                    R.color.neon_green
                )
                ToastType.ERROR -> Triple(
                    R.drawable.ic_connection_error,
                    R.drawable.bg_toast_error,
                    R.color.neon_red
                )
                ToastType.INFO -> Triple(
                    R.drawable.ic_main_agent,
                    R.drawable.bg_toast_info,
                    R.color.neon_cyan
                )
                ToastType.WARNING -> Triple(
                    R.drawable.ic_universal,
                    R.drawable.bg_toast_warning,
                    R.color.neon_orange
                )
                ToastType.LOADING -> Triple(
                    R.drawable.ic_nexus_logo,
                    R.drawable.bg_toast_info,
                    R.color.neon_cyan
                )
            }

            ivIcon.setImageResource(iconRes)
            root.setBackgroundResource(bgRes)
            ivIcon.setColorFilter(ContextCompat.getColor(root.context, colorRes))
        }
    }

    private fun dismissToast(view: View, parent: ViewGroup) {
        view.animate()
            .alpha(0f)
            .translationY(-30f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    parent.removeView(view)
                }
            })
            .start()
    }

    private fun findSuitableParent(view: View): FrameLayout? {
        var current: View? = view
        while (current != null) {
            if (current is FrameLayout && current.id == R.id.toast_container) {
                return current
            }
            val parent = current.parent
            current = if (parent is View) parent else null
        }
        
        // Fallback to decor view
        val activity = view.context as? android.app.Activity
        return activity?.window?.decorView?.findViewById(android.R.id.content) as? FrameLayout
    }

    // Snackbar alternatives for simple messages
    fun showSnackbar(
        anchor: View,
        message: String,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) {
        val snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
        
        snackbar.view.setBackgroundResource(R.drawable.bg_snackbar)
        
        actionText?.let { text ->
            snackbar.setAction(text) { action?.invoke() }
            snackbar.setActionTextColor(ContextCompat.getColor(anchor.context, R.color.neon_cyan))
        }

        snackbar.show()
    }

    private enum class ToastType {
        SUCCESS, ERROR, INFO, WARNING, LOADING
    }
}
