package com.nexus.agent.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import com.nexus.agent.R
import com.nexus.agent.core.cli.CLIExecutor
import com.nexus.agent.ui.common.NeonToggleView

/**
 * Плавающий оверлей командной строки.
 * Может работать как внутри фрагмента (View), так и как системный оверлей (WindowManager).
 * Поддерживает drag-and-drop, сворачивание/разворачивание, root-режим.
 */
class CLIOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Callbacks
    var onClose: (() -> Unit)? = null
    var onCommandExecuted: ((String, String) -> Unit)? = null

    // Views
    private lateinit var headerView: View
    private lateinit var tvTitle: TextView
    private lateinit var btnCollapse: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnRootToggle: NeonToggleView
    private lateinit var scrollOutput: ScrollView
    private lateinit var tvOutput: TextView
    private lateinit var inputContainer: LinearLayout
    private lateinit var tvPrompt: TextView
    private lateinit var etCommand: com.nexus.agent.ui.common.ChatInputBar
    private lateinit var btnExecute: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var btnHistory: ImageButton

    // State
    private var isCollapsed = false
    private var isRootMode = false
    private var isSystemOverlay = false
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private val cliExecutor: CLIExecutor by lazy { CLIExecutor(context) }
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    // UI Constants
    private val collapsedHeight by lazy { dpToPx(56) }
    private val expandedHeight by lazy { dpToPx(400) }
    private val width by lazy { dpToPx(360) }

    init {
        initView()
        setupDragBehavior()
        applyNeonStyling()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.view_cli_overlay, this, true)

        headerView = findViewById(R.id.cliHeader)
        tvTitle = findViewById(R.id.tvCliTitle)
        btnCollapse = findViewById(R.id.btnCollapse)
        btnClose = findViewById(R.id.btnClose)
        btnRootToggle = findViewById(R.id.btnRootToggle)
        scrollOutput = findViewById(R.id.scrollOutput)
        tvOutput = findViewById(R.id.tvOutput)
        inputContainer = findViewById(R.id.inputContainer)
        tvPrompt = findViewById(R.id.tvPrompt)
        etCommand = findViewById(R.id.etCommand)
        btnExecute = findViewById(R.id.btnExecute)
        btnClear = findViewById(R.id.btnClear)
        btnHistory = findViewById(R.id.btnHistory)

        // Setup neon border background
        background = createNeonBackground()

        btnClose.setOnClickListener { onClose?.invoke() }
        btnCollapse.setOnClickListener { toggleCollapse() }
        
        btnRootToggle.setOnCheckedChangeListener { isChecked ->
            isRootMode = isChecked
            updatePrompt()
        }

        btnExecute.setOnClickListener { executeCommand() }
        btnClear.setOnClickListener { clearOutput() }
        btnHistory.setOnClickListener { showHistory() }

        // Command input handling
        etCommand.apply {
            onSendMessage = { text, _ ->
                if (text.isNotBlank()) executeCommand(text)
            }
        }

        updatePrompt()
    }

    private fun createNeonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat()
            setColor(ContextCompat.getColor(context, R.color.bg_card_dark))
            setStroke(dpToPx(2), ContextCompat.getColor(context, R.color.neon_cyan))
        }
    }

    private fun applyNeonStyling() {
        // Применяем неоновые тени и эффекты
        elevation = dpToPx(8).toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineSpotShadowColor = ContextCompat.getColor(context, R.color.neon_cyan)
        }
    }

    private fun setupDragBehavior() {
        headerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = x
                    initialY = y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = initialX + (event.rawX - initialTouchX)
                    val newY = initialY + (event.rawY - initialTouchY)
                    
                    if (isSystemOverlay && windowParams != null) {
                        windowParams?.x = newX.toInt()
                        windowParams?.y = newY.toInt()
                        windowManager?.updateViewLayout(this, windowParams)
                    } else {
                        x = newX
                        y = newY
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleCollapse() {
        isCollapsed = !isCollapsed
        
        val targetHeight = if (isCollapsed) collapsedHeight else expandedHeight
        val animator = ValueAnimator.ofInt(height, targetHeight).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                layoutParams.height = value
                requestLayout()
            }
            doOnEnd {
                btnCollapse.setImageResource(
                    if (isCollapsed) R.drawable.ic_expand else R.drawable.ic_collapse
                )
                inputContainer.visibility = if (isCollapsed) View.GONE else View.VISIBLE
                scrollOutput.visibility = if (isCollapsed) View.GONE else View.VISIBLE
            }
        }
        animator.start()
    }

    private fun executeCommand(command: String = etCommand.getText()) {
        if (command.isBlank()) return

        // Add to history
        commandHistory.add(command)
        historyIndex = commandHistory.size

        // Display command in output
        appendOutput("\n${getPrompt()}$command\n", ContextCompat.getColor(context, R.color.neon_green))

        // Clear input
        etCommand.setText("")

        // Execute
        if (isRootMode) {
            executeRootCommand(command)
        } else {
            executeShellCommand(command)
        }
    }

    private fun executeShellCommand(command: String) {
        appendOutput("Executing...\n", ContextCompat.getColor(context, R.color.neon_yellow))
        
        cliExecutor.execute(command) { result ->
            post {
                when {
                    result.isSuccess -> {
                        appendOutput(result.getOrNull() ?: "", ContextCompat.getColor(context, R.color.text_primary))
                    }
                    else -> {
                        appendOutput(
                            "Error: ${result.exceptionOrNull()?.message}\n",
                            ContextCompat.getColor(context, R.color.neon_red)
                        )
                    }
                }
                onCommandExecuted?.invoke(command, result.getOrDefault(""))
            }
        }
    }

    private fun executeRootCommand(command: String) {
        val rootBridge = com.nexus.agent.core.root.RootBridge(context)
        if (!rootBridge.isRootAvailable()) {
            appendOutput("Root access not available!\n", ContextCompat.getColor(context, R.color.neon_red))
            return
        }

        appendOutput("[ROOT] Executing...\n", ContextCompat.getColor(context, R.color.neon_orange))
        
        rootBridge.execute(command) { output, exitCode ->
            post {
                if (exitCode == 0) {
                    appendOutput(output, ContextCompat.getColor(context, R.color.text_primary))
                } else {
                    appendOutput("Exit code $exitCode: $output\n", ContextCompat.getColor(context, R.color.neon_red))
                }
            }
        }
    }

    private fun appendOutput(text: String, color: Int) {
        tvOutput.append(text)
        tvOutput.setTextColor(color)
        scrollOutput.post {
            scrollOutput.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun clearOutput() {
        tvOutput.text = ""
        appendOutput("Nexus CLI v1.0\nType 'help' for commands\n\n", ContextCompat.getColor(context, R.color.neon_cyan))
    }

    private fun showHistory() {
        if (commandHistory.isEmpty()) {
            appendOutput("\nNo history yet\n", ContextCompat.getColor(context, R.color.neon_yellow))
            return
        }

        val historyText = commandHistory.mapIndexed { index, cmd -> 
            "${index + 1}. $cmd" 
        }.joinToString("\n")
        
        appendOutput("\n--- Command History ---\n$historyText\n\n", ContextCompat.getColor(context, R.color.neon_purple))
    }

    private fun updatePrompt() {
        val prompt = getPrompt()
        tvPrompt.text = prompt
        tvTitle.text = if (isRootMode) "Nexus CLI [ROOT]" else "Nexus CLI"
        tvTitle.setTextColor(
            if (isRootMode) ContextCompat.getColor(context, R.color.neon_red)
            else ContextCompat.getColor(context, R.color.neon_cyan)
        )
    }

    private fun getPrompt(): String {
        return if (isRootMode) "# " else "$ "
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    // === System Overlay Mode ===

    /**
     * Прикрепляет оверлей к системному WindowManager (требует SYSTEM_ALERT_WINDOW permission).
     */
    fun showAsSystemOverlay() {
        if (isSystemOverlay) return
        
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowParams = WindowManager.LayoutParams(
            width,
            expandedHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager?.addView(this, windowParams)
        isSystemOverlay = true
    }

    /**
     * Удаляет системный оверлей.
     */
    fun hideSystemOverlay() {
        if (!isSystemOverlay) return
        windowManager?.removeView(this)
        isSystemOverlay = false
    }

    /**
     * Привязывает позицию оверлея к якорной View (для внутрифрагментного режима).
     */
    fun anchorToView(anchor: View) {
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        
        x = location[0].toFloat() - width + anchor.width
        y = location[1].toFloat() + anchor.height + dpToPx(8)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cliExecutor.release()
    }
}
