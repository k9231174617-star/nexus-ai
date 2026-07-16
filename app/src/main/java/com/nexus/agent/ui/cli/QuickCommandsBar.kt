package com.nexus.agent.ui.cli

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.nexus.agent.R
import com.nexus.agent.databinding.ViewQuickCommandsBarBinding

/**
 * QuickCommandsBar — horizontal scrollable bar with frequently used CLI commands.
 * Organized by category: Navigation, File Ops, Process, Network, System.
 */
class QuickCommandsBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val binding: ViewQuickCommandsBarBinding =
        ViewQuickCommandsBarBinding.inflate(LayoutInflater.from(context), this, true)

    private var commandClickListener: ((Command) -> Unit)? = null

    enum class Command {
        CLEAR, LS, PWD, CD_UP,
        GREP, CHMOD,
        PS, TOP,
        IFCONFIG, NETSTAT, ADB,
        LOGCAT, DUMPSYS, PM_LIST, AM_START
    }

    private val commandCategories = mapOf(
        "Navigation" to listOf(Command.LS, Command.PWD, Command.CD_UP),
        "File" to listOf(Command.GREP, Command.CHMOD),
        "Process" to listOf(Command.PS, Command.TOP),
        "Network" to listOf(Command.IFCONFIG, Command.NETSTAT, Command.ADB),
        "System" to listOf(Command.LOGCAT, Command.DUMPSYS, Command.PM_LIST, Command.AM_START),
        "Clear" to listOf(Command.CLEAR)
    )

    init {
        isHorizontalScrollBarEnabled = false
        setupCommandChips()
    }

    private fun setupCommandChips() {
        commandCategories.forEach { (category, commands) ->
            // Add category label
            val categoryLabel = createCategoryLabel(category)
            binding.chipContainer.addView(categoryLabel)

            // Add command chips
            commands.forEach { command ->
                val chip = createCommandChip(command)
                binding.chipContainer.addView(chip)
            }

            // Add spacer between categories
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(16, 0)
            }
            binding.chipContainer.addView(spacer)
        }
    }

    private fun createCategoryLabel(text: String): View {
        return android.widget.TextView(context).apply {
            this.text = text
            textSize = 10f
            setTextColor(ContextCompat.getColor(context, R.color.terminal_category_label))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
                marginEnd = 4
                topMargin = 12
            }
        }
    }

    private fun createCommandChip(command: Command): Chip {
        return Chip(context).apply {
            text = getCommandLabel(command)
            isCheckable = false
            isClickable = true
            setChipBackgroundColorResource(getCommandColor(command))
            setTextColor(ContextCompat.getColor(context, R.color.terminal_chip_text))
            textSize = 12f
            chipMinHeight = 48f
            setEnsureMinTouchTargetSize(false)

            setOnClickListener {
                animateChipClick(this)
                commandClickListener?.invoke(command)
            }
        }
    }

    private fun animateChipClick(chip: Chip) {
        chip.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(80)
            .withEndAction {
                chip.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .start()
            }
            .start()
    }

    private fun getCommandLabel(command: Command): String {
        return when (command) {
            Command.CLEAR -> "clear"
            Command.LS -> "ls -la"
            Command.PWD -> "pwd"
            Command.CD_UP -> "cd .."
            Command.GREP -> "grep"
            Command.CHMOD -> "chmod"
            Command.PS -> "ps"
            Command.TOP -> "top"
            Command.IFCONFIG -> "ifconfig"
            Command.NETSTAT -> "netstat"
            Command.ADB -> "adb"
            Command.LOGCAT -> "logcat"
            Command.DUMPSYS -> "dumpsys"
            Command.PM_LIST -> "pm list"
            Command.AM_START -> "am start"
        }
    }

    private fun getCommandColor(command: Command): Int {
        return when (command) {
            Command.CLEAR -> R.color.chip_clear
            Command.LS, Command.PWD, Command.CD_UP -> R.color.chip_navigation
            Command.GREP, Command.CHMOD -> R.color.chip_file
            Command.PS, Command.TOP -> R.color.chip_process
            Command.IFCONFIG, Command.NETSTAT, Command.ADB -> R.color.chip_network
            Command.LOGCAT, Command.DUMPSYS, Command.PM_LIST, Command.AM_START -> R.color.chip_system
        }
    }

    fun setOnCommandClickListener(listener: (Command) -> Unit) {
        this.commandClickListener = listener
    }

    fun addCustomCommand(label: String, action: () -> Unit) {
        val chip = Chip(context).apply {
            text = label
            isCheckable = false
            isClickable = true
            setChipBackgroundColorResource(R.color.chip_custom)
            setTextColor(ContextCompat.getColor(context, R.color.terminal_chip_text))
            setOnClickListener { action() }
        }
        binding.chipContainer.addView(chip, 0)
    }

    fun removeCommand(command: Command) {
        val chipToRemove = binding.chipContainer.children.find { view ->
            (view as? Chip)?.text == getCommandLabel(command)
        }
        chipToRemove?.let { binding.chipContainer.removeView(it) }
    }

    fun setCategoryVisible(category: String, visible: Boolean) {
        // Implementation to show/hide specific categories
    }
}

// Extension to iterate over ViewGroup children
private val android.view.ViewGroup.children: Sequence<View>
    get() = (0 until childCount).asSequence().map { getChildAt(it) }
