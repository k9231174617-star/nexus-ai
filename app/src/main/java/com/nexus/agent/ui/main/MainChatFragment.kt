package com.nexus.agent.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.agent.R
import com.nexus.agent.core.chat.ChatViewModel
import com.nexus.agent.core.chat.MessageModel
import com.nexus.agent.ui.common.ChatInputBar
import com.nexus.agent.ui.common.ContextModeBar
import com.nexus.agent.ui.common.TypingIndicatorView
import kotlinx.coroutines.launch

/**
 * Фрагмент основного чата с AI.
 * Отображает список сообщений, поле ввода, индикатор печати и контекстную панель.
 */
class MainChatFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var chatInputBar: ChatInputBar
    private lateinit var typingIndicator: TypingIndicatorView
    private lateinit var contextModeBar: ContextModeBar
    private lateinit var progressBar: ProgressBar
    private lateinit var messageAdapter: com.nexus.agent.core.chat.MessageAdapter

    private val viewModel: ChatViewModel by lazy {
        // В реальном приложении используйте ViewModelProvider или Hilt
        ChatViewModel()
    }

    companion object {
        fun newInstance() = MainChatFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupChatInput()
        setupContextModeBar()
        observeViewModel()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerChat)
        chatInputBar = view.findViewById(R.id.chatInputBar)
        typingIndicator = view.findViewById(R.id.typingIndicator)
        contextModeBar = view.findViewById(R.id.contextModeBar)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        messageAdapter = com.nexus.agent.core.chat.MessageAdapter(
            onCopyClick = { text -> copyToClipboard(text) },
            onRegenerateClick = { message -> regenerateMessage(message) },
            onFileClick = { filePath -> openFilePreview(filePath) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
            itemAnimator = null // Отключаем анимацию для плавного стриминга
        }

        // Автоскролл к новым сообщениям
        messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                recyclerView.scrollToPosition(messageAdapter.itemCount - 1)
            }
        })
    }

    private fun setupChatInput() {
        chatInputBar.apply {
            onSendMessage = { text, attachments ->
                if (text.isNotBlank() || attachments.isNotEmpty()) {
                    viewModel.sendMessage(text, attachments)
                }
            }
            onVoiceInput = { startVoiceInput() }
            onAttachmentClick = { showAttachmentPicker() }
        }
    }

    private fun setupContextModeBar() {
        contextModeBar.apply {
            onModeChanged = { mode ->
                viewModel.setContextMode(mode)
            }
            availableModes = listOf(
                ContextModeBar.Mode.DEFAULT,
                ContextModeBar.Mode.CODE,
                ContextModeBar.Mode.FILE,
                ContextModeBar.Mode.WEB
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        messageAdapter.submitList(messages.toList())
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        typingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
                        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                        chatInputBar.setEnabled(!isLoading)
                    }
                }

                launch {
                    viewModel.streamingText.collect { partialText ->
                        messageAdapter.updateStreamingMessage(partialText)
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let { showError(it) }
                    }
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) 
            as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Nexus AI", text)
        clipboard.setPrimaryClip(clip)
        
        com.nexus.agent.ui.common.ToastManager.show(requireContext(), "Copied to clipboard")
    }

    private fun regenerateMessage(message: MessageModel) {
        viewModel.regenerateMessage(message.id)
    }

    private fun openFilePreview(filePath: String) {
        // Навигация к просмотру файла
        val filesFragment = com.nexus.agent.ui.files.FilesFragment.newInstance(filePath)
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, filesFragment)
            .addToBackStack("file_preview")
            .commit()
    }

    private fun startVoiceInput() {
        val voiceManager = com.nexus.agent.core.voice.VoiceInputManager(requireContext())
        voiceManager.startListening { recognizedText ->
            chatInputBar.appendText(recognizedText)
        }
    }

    private fun showAttachmentPicker() {
        // Открытие диалога выбора файлов
        val dialog = com.nexus.agent.ui.common.FileAttachmentView.newInstance()
        dialog.show(parentFragmentManager, "attachment_picker")
    }

    private fun showError(message: String) {
        com.nexus.agent.ui.common.ToastManager.show(requireContext(), "Error: $message")
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveDraft(chatInputBar.getText())
    }

    override fun onResume() {
        super.onResume()
        viewModel.getDraft()?.let { draft ->
            chatInputBar.setText(draft)
        }
    }
}
