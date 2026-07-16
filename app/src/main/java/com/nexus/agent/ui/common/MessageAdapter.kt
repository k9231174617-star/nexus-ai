package com.nexus.agent.core.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexus.agent.databinding.ItemMessageBinding
import io.noties.markwon.Markwon

class MessageAdapter : ListAdapter<MessageModel, MessageAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)
        val ctx = holder.binding.root.context
        val markwon = Markwon.create(ctx)

        with(holder.binding) {
            if (msg.isUser) {
                msgBubble.setBackgroundResource(R.drawable.bg_bubble_user)
                avatarView.visibility = android.view.View.GONE
                msgName.text = "YOU"
                msgName.setTextColor(ctx.getColor(R.color.text_secondary))
            } else {
                msgBubble.setBackgroundResource(R.drawable.bg_bubble_assistant)
                avatarView.visibility = android.view.View.VISIBLE
                msgName.text = "NEXUS"
                msgName.setTextColor(ctx.getColor(R.color.red_core))
            }

            markwon.setMarkdown(msgText, msg.displayText)
            msgTime.text = formatTime(msg.timestamp)

            copyBtn.setOnClickListener {
                val clipboard = ctx.getSystemService(
                    android.content.Context.CLIPBOARD_SERVICE
                ) as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("NEXUS", msg.content)
                )
            }
        }
    }

    private fun formatTime(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<MessageModel>() {
            override fun areItemsTheSame(a: MessageModel, b: MessageModel) = a.id == b.id
            override fun areContentsTheSame(a: MessageModel, b: MessageModel) = a == b
        }
    }
}