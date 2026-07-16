package com.nexus.agent.ui.code

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.agent.R
import com.nexus.agent.core.chat.ChatViewModel
import com.nexus.agent.core.llm.PromptEngineer
import com.nexus.agent.ui.common.ChatInputBar
import com.nexus.agent.ui.common.CopyButton
import com.nexus.agent.ui.common.MarkdownRenderer

/**
 * Выдвижная панель AI-ассистента для кода.
 * Позволяет анализировать, объяснять, рефакторить и генерировать код.
 */
class CodeAIDrawer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onInsertCode: ((String) -> Unit)? = null
    var onReplaceSelection: ((String) -> Unit)? = null
    var onExplainRequest: ((String) -> Unit)? = null
    var onRefactorRequest: ((String, String) -> Unit)? = null

    private lateinit var btnToggle: ImageButton
    private lateinit var drawerContent: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var recyclerActions: RecyclerView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatInputBar: ChatInputBar
    private lateinit var scrollChat: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCurrentFile: TextView

    private var isExpanded = false
    private var drawerHeight = 0
    private var collapsedHeight = 0
    private var currentFile: String = ""
    private var lastAnalyzedCode: String = ""

    private val chatMessages = mutableListOf<ChatMessage>()
    private val promptEngineer = PromptEngineer()

    data class ChatMessage(
        val isUser: Boolean,
        val content: String,
        val codeBlocks: List<String> = emptyList()
    )

    data class AiAction(
        val icon: Int,
        val label: String,
        val action: () -> Unit
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.view_code_ai_drawer, this, true)
        initViews()
        setupActions()
        setupChat()
    }

    private fun initViews() {
        btnToggle = findViewById(R.id.btnToggleDrawer)
        drawerContent = findViewById(R.id.drawerContent)
        tvTitle = findViewById(R.id.tvDrawerTitle)
        btnClose = findViewById(R.id.btnCloseDrawer)
        recyclerActions = findViewById(R.id.recyclerActions)
        chatContainer = findViewById(R.id.chatContainer)
        chatInputBar = findViewById(R.id.chatInputBar)
        scrollChat = findViewById(R.id.scrollChat)
        progressBar = findViewById(R.id.progressBar)
        tvCurrentFile = findViewById(R.id.tvCurrentFile)

        btnToggle.setOnClickListener { toggle() }
        btnClose.setOnClickListener { collapse() }

        collapsedHeight = dpToPx(48)
        drawerHeight = dpToPx(400)

        layoutParams.height = collapsedHeight
    }

    private fun setupActions() {
        val actions = listOf(
            AiAction(R.drawable.ic_ai_explain, "Explain") {
                if (lastAnalyzedCode.isNotBlank()) {
                    explainCode(lastAnalyzedCode)
                }
            },
            AiAction(R.drawable.ic_ai_refactor, "Refactor") {
                showRefactorOptions()
            },
            AiAction(R.drawable.ic_ai_optimize, "Optimize") {
                requestAction("Optimize this code for performance")
            },
            AiAction(R.drawable.ic_ai_document, "Document") {
                requestAction("Add documentation comments to this code")
            },
            AiAction(R.drawable.ic_ai_test, "Generate Tests") {
                requestAction("Generate unit tests for this code")
            },
            AiAction(R.drawable.ic_ai_convert, "Convert") {
                showLanguageConversionDialog()
            }
        )

        recyclerActions.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerActions.adapter = ActionAdapter(actions)
    }

    private fun setupChat() {
        chatInputBar.onSendMessage = { text, _ ->
            if (text.isNotBlank()) {
                addMessage(ChatMessage(isUser = true, content = text))
                sendToAI(text)
            }
        }
    }

    fun toggle() {
        if (isExpanded) collapse() else expand()
    }

    fun expand() {
        if (isExpanded) return
        animateHeight(collapsedHeight, drawerHeight) {
            isExpanded = true
            btnToggle.setImageResource(R.drawable.ic_expand_more)
            recyclerActions.visibility = View.VISIBLE
            chatContainer.visibility = View.VISIBLE
        }
    }

    fun collapse() {
        if (!isExpanded) return
        animateHeight(drawerHeight, collapsedHeight) {
            isExpanded = false
            btnToggle.setImageResource(R.drawable.ic_expand_less)
            recyclerActions.visibility = View.GONE
            chatContainer.visibility = View.GONE
        }
    }

    private fun animateHeight(from: Int, to: Int, onEnd: () -> Unit) {
        ValueAnimator.ofInt(from, to).apply {
            duration = 300
            addUpdateListener { animator ->
                layoutParams.height = animator.animatedValue as Int
                requestLayout()
            }
            doOnEnd { onEnd() }
            start()
        }
    }

    fun setCurrentFile(path: String) {
        currentFile = path
        tvCurrentFile.text = File(path).name
    }

    fun analyzeCode(code: String) {
        lastAnalyzedCode = code
        addMessage(ChatMessage(
            isUser = false,
            content = "I've analyzed the selected code. What would you like me to do with it?",
            codeBlocks = listOf(code.take(500))
        ))
        if (!isExpanded) expand()
    }

    fun analyzeFile(file: File) {
        setCurrentFile(file.absolutePath)
        val code = try {
            file.readText().take(2000)
        } catch (e: Exception) {
            "Could not read file"
        }
        analyzeCode(code)
    }

    private fun explainCode(code: String) {
        showProgress(true)
        val prompt = promptEngineer.buildExplainPrompt(code, detectLanguage(currentFile))

        // Simulate AI call
        simulateAiResponse("Here's what this code does:\n\n" +
            "1. It initializes variables\n" +
            "2. Processes input data\n" +
            "3. Returns the result\n\n" +
            "The key logic is in the loop that iterates through the collection.")
    }

    private fun requestAction(instruction: String) {
        if (lastAnalyzedCode.isBlank()) return
        showProgress(true)

        val prompt = "$instruction:\n```\n$lastAnalyzedCode\n```"
        addMessage(ChatMessage(isUser = true, content = instruction))

        // AI call
        simulateAiResponse("Here's the refactored code:\n```kotlin\n// Optimized version\nfun optimized() {\n    // Implementation\n}\n```")
    }

    private fun showRefactorOptions() {
        val options = arrayOf("Rename variables", "Extract method", "Simplify", "Add null safety")
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Refactor")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestAction("Rename variables to be more descriptive")
                    1 -> requestAction("Extract this into a separate method")
                    2 -> requestAction("Simplify this code")
                    3 -> requestAction("Add null safety checks")
                }
            }
            .show()
    }

    private fun showLanguageConversionDialog() {
        val languages = arrayOf("Kotlin", "Java", "Python", "JavaScript", "C++")
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Convert to")
            .setItems(languages) { _, which ->
                requestAction("Convert this code to ${languages[which]}")
            }
            .show()
    }

    private fun sendToAI(message: String) {
        showProgress(true)
        // Real implementation would call LLMBridge
        simulateAiResponse("I understand. Let me help you with that.")
    }

    private fun simulateAiResponse(response: String) {
        postDelayed({
            showProgress(false)
            addMessage(ChatMessage(isUser = false, content = response))
        }, 1000)
    }

    private fun addMessage(message: ChatMessage) {
        chatMessages.add(message)

        val messageView = LayoutInflater.from(context).inflate(
            if (message.isUser) R.layout.item_message_user else R.layout.item_message_ai,
            chatContainer, false
        )

        val tvContent = messageView.findViewById<TextView>(R.id.tvMessageContent)
        val codeContainer = messageView.findViewById<LinearLayout>(R.id.codeContainer)

        // Render markdown
        tvContent.text = message.content

        // Add code blocks with action buttons
        message.codeBlocks.forEach { code ->
            val codeView = LayoutInflater.from(context).inflate(R.layout.view_code_block, codeContainer, false)
            val tvCode = codeView.findViewById<TextView>(R.id.tvCode)
            val btnCopy = codeView.findViewById<CopyButton>(R.id.btnCopy)
            val btnInsert = codeView.findViewById<Button>(R.id.btnInsert)
            val btnReplace = codeView.findViewById<Button>(R.id.btnReplace)

            tvCode.text = code
            btnCopy.setTextToCopy(code)
            btnInsert.setOnClickListener { onInsertCode?.invoke(code) }
            btnReplace.setOnClickListener { onReplaceSelection?.invoke(code) }

            codeContainer.addView(codeView)
        }

        chatContainer.addView(messageView)
        scrollChat.post { scrollChat.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun detectLanguage(path: String): String {
        return when (File(path).extension) {
            "kt" -> "kotlin"
            "java" -> "java"
            else -> "text"
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // === Action Adapter ===

    private inner class ActionAdapter(
        private val actions: List<AiAction>
    ) : RecyclerView.Adapter<ActionAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val btnAction: Button = itemView.findViewById(R.id.btnAction)

            fun bind(action: AiAction) {
                btnAction.text = action.label
                btnAction.setCompoundDrawablesWithIntrinsicBounds(action.icon, 0, 0, 0)
                btnAction.setOnClickListener { action.action() }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_ai_action, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(actions[position])
        }

        override fun getItemCount() = actions.size
    }
}
