package com.nexus.agent.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

/**
 * Accessibility service for screen reading and automated actions.
 * Enables the AI agent to read screen content and interact with UI elements.
 */
class Screen2ActionService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isActive = false
    private var lastEventTime = 0L

    companion object {
        private var instance: Screen2ActionService? = null
        private var _lastScreenContent = ""
        private val _actionQueue = mutableListOf<suspend () -> Unit>()

        fun getScreenContent(): String = _lastScreenContent

        fun isServiceRunning(): Boolean = instance != null

        /** Click on a node matching text */
        suspend fun clickOnText(text: String, timeoutMs: Long = 5000) {
            val service = instance ?: return
            withTimeout(timeoutMs) {
                while (true) {
                    val root = service.rootInActiveWindow ?: run { delay(500); continue }
                    val node = findNodeByText(root, text)
                    if (node != null) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        node.recycle()
                        root.recycle()
                        return@withTimeout
                    }
                    root.recycle()
                    delay(500)
                }
            }
        }

        /** Perform a gesture (swipe, tap coordinates) */
        suspend fun performGesture(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300) {
            val service = instance ?: return
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            service.dispatchGesture(gesture, null, null)
        }

        /** Get nodes matching a condition */
        suspend fun findNodesByText(text: String, timeoutMs: Long = 3000): List<String> {
            val service = instance ?: return emptyList()
            return withTimeoutOrNull(timeoutMs) {
                while (true) {
                    val root = service.rootInActiveWindow ?: run { delay(300); continue }
                    val results = mutableListOf<String>()
                    collectTexts(root, results)
                    root.recycle()
                    if (results.isNotEmpty()) return@withTimeoutOrNull results
                    delay(300)
                }
            } ?: emptyList()
        }

        private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
            if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findNodeByText(child, text)
                if (found != null) { child.recycle(); return found }
                child.recycle()
            }
            return null
        }

        private fun collectTexts(node: AccessibilityNodeInfo, results: MutableList<String>) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { results.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { results.add(it) }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { collectTexts(it, results); it.recycle() }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isActive = true
        android.util.Log.i("Screen2Action", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isActive) return
        lastEventTime = event.eventTime

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                scope.launch {
                    updateScreenContent()
                }
            }
        }
    }

    override fun onInterrupt() {
        isActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isActive = false
        scope.cancel()
    }

    private suspend fun updateScreenContent() {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectAllTexts(root, texts)
        _lastScreenContent = texts.joinToString("\n")
        root.recycle()
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectAllTexts(it, texts); it.recycle() }
        }
    }
}
