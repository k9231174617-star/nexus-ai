package com.nexus.agent.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexus.agent.R
import com.nexus.agent.databinding.ItemDrawerBinding

/**
 * Adapter for the navigation drawer with neon accent support
 */
class DrawerAdapter(
    private val onItemClick: (DrawerItem) -> Unit
) : ListAdapter<DrawerItem, DrawerAdapter.DrawerViewHolder>(DrawerDiffCallback()) {

    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerViewHolder {
        val binding = ItemDrawerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DrawerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DrawerViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    fun setSelectedPosition(position: Int) {
        val previous = selectedPosition
        selectedPosition = position
        notifyItemChanged(previous)
        notifyItemChanged(position)
    }

    inner class DrawerViewHolder(
        private val binding: ItemDrawerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    setSelectedPosition(position)
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: DrawerItem, isSelected: Boolean) {
            binding.apply {
                tvTitle.text = item.title
                
                // Icon with neon tint when selected
                ivIcon.setImageResource(item.iconRes)
                ivIcon.setColorFilter(
                    ContextCompat.getColor(
                        root.context,
                        if (isSelected) R.color.neon_cyan else R.color.text_secondary
                    )
                )

                // Selection indicator
                viewSelectionIndicator.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                
                // Background
                root.setBackgroundResource(
                    if (isSelected) R.drawable.bg_drawer_item_selected else R.drawable.bg_drawer_item
                )

                // Badge
                if (item.badgeCount > 0) {
                    tvBadge.visibility = View.VISIBLE
                    tvBadge.text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString()
                } else {
                    tvBadge.visibility = View.GONE
                }

                // Alpha for disabled items
                root.alpha = if (item.isEnabled) 1.0f else 0.4f
                root.isClickable = item.isEnabled
            }
        }
    }

    class DrawerDiffCallback : DiffUtil.ItemCallback<DrawerItem>() {
        override fun areItemsTheSame(old: DrawerItem, new: DrawerItem) = old.id == new.id
        override fun areContentsTheSame(old: DrawerItem, new: DrawerItem) = old == new
    }
}

data class DrawerItem(
    val id: String,
    val title: String,
    val iconRes: Int,
    val badgeCount: Int = 0,
    val isEnabled: Boolean = true,
    val destination: DrawerDestination
)

enum class DrawerDestination {
    MAIN_AGENT,
    CODE_AGENT,
    UNIVERSAL_AGENT,
    CLI,
    FILES,
    MEMORY,
    PLANNER,
    SANDBOX,
    BROWSER,
    RAG,
    GRAPH,
    OBSERVABILITY,
    SETTINGS
}
