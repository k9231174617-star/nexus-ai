package com.nexus.agent.ui.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.nexus.agent.R
import com.nexus.agent.core.files.model.BreadcrumbItem

class BreadcrumbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val recyclerView: RecyclerView
    private val adapter: BreadcrumbAdapter
    private var onBreadcrumbClick: ((BreadcrumbItem) -> Unit)? = null
    private var isRootZone: Boolean = false

    init {
        recyclerView = RecyclerView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = FlexboxLayoutManager(context).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.NOWRAP
            }
            overScrollMode = OVER_SCROLL_NEVER
        }
        addView(recyclerView)
        
        isHorizontalScrollBarEnabled = false
        
        adapter = BreadcrumbAdapter { item, action ->
            when (action) {
                BreadcrumbAction.CLICK -> onBreadcrumbClick?.invoke(item)
                BreadcrumbAction.LONG_PRESS -> copyPathToClipboard(item.fullPath)
            }
        }
        recyclerView.adapter = adapter
    }

    fun setBreadcrumbs(items: List<BreadcrumbItem>) {
        adapter.submitList(items)
        post {
            fullScroll(FOCUS_RIGHT)
        }
    }

    fun setOnBreadcrumbClickListener(listener: (BreadcrumbItem) -> Unit) {
        onBreadcrumbClick = listener
    }

    fun setRootZone(isRoot: Boolean) {
        isRootZone = isRoot
        adapter.setRootZone(isRoot)
    }

    private fun copyPathToClipboard(path: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("path", path))
    }

    private inner class BreadcrumbAdapter(
        private val onAction: (BreadcrumbItem, BreadcrumbAction) -> Unit
    ) : RecyclerView.Adapter<BreadcrumbAdapter.ViewHolder>() {

        private var items: List<BreadcrumbItem> = emptyList()
        private var isRootZone: Boolean = false

        fun submitList(newItems: List<BreadcrumbItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun setRootZone(root: Boolean) {
            isRootZone = root
            notifyItemChanged(items.size - 1)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_breadcrumb, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position)
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameText: TextView = itemView.findViewById(R.id.tv_breadcrumb_name)
            private val separator: TextView = itemView.findViewById(R.id.tv_separator)
            private val rootIndicator: ImageView = itemView.findViewById(R.id.iv_root_indicator)

            fun bind(item: BreadcrumbItem, position: Int) {
                val isLast = position == items.size - 1
                
                // Имя папки с обрезанием для средних элементов
                nameText.text = when {
                    position == 0 -> item.displayName  // Root всегда полный
                    isLast -> item.displayName          // Последний полный
                    else -> truncateMiddle(item.displayName, 12)
                }
                
                // Стиль: последний элемент — акцентный
                nameText.alpha = if (isLast) 1.0f else 0.7f
                nameText.isEnabled = !isLast  // Последний не кликабелен

                // Разделитель
                separator.isVisible = position < items.size - 1

                // Индикатор root для последнего элемента
                rootIndicator.isVisible = isLast && isRootZone
                rootIndicator.setImageResource(
                    if (isRootZone) R.drawable.ic_root_active else R.drawable.ic_root_inactive
                )

                // Клики
                itemView.setOnClickListener {
                    if (!isLast) onAction(item, BreadcrumbAction.CLICK)
                }
                
                itemView.setOnLongClickListener {
                    onAction(item, BreadcrumbAction.LONG_PRESS)
                    true
                }
            }

            private fun truncateMiddle(text: String, maxLength: Int): String {
                if (text.length <= maxLength) return text
                val half = maxLength / 2
                return text.take(half) + "…" + text.takeLast(half)
            }
        }
    }

    private enum class BreadcrumbAction {
        CLICK, LONG_PRESS
    }
}
