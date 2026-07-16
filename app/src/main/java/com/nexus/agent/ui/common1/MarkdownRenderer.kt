package com.nexus.agent.ui.common

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.*
import android.view.View
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import org.commonmark.node.*

/**
 * Custom Markdown renderer with code highlighting and Nexus AI styling
 */
class MarkdownRenderer(context: Context) {

    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(CorePlugin())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(ImagesPlugin.create())
        .usePlugin(CoilImagesPlugin.create(context))
        .usePlugin(SyntaxHighlightPlugin.create(Prism4jThemeDefault.create()))
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    .codeBackgroundColor(0xFF1E1E2E.toInt())
                    .codeTextColor(0xFFCDD6F4.toInt())
                    .blockquoteColor(0xFF00F0FF.toInt())
                    .linkColor(0xFF00F0FF.toInt())
                    .headingBreakHeight(0)
                    .headingTextSizeMultipliers(floatArrayOf(2.0f, 1.5f, 1.25f, 1.0f, 0.875f, 0.75f))
            }

            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                builder.setFactory(Code::class.java) { _, _ ->
                    arrayOf(
                        TypefaceSpan("monospace"),
                        BackgroundColorSpan(0xFF1E1E2E.toInt()),
                        ForegroundColorSpan(0xFFCDD6F4.toInt()),
                        RelativeSizeSpan(0.9f)
                    )
                }
            }
        })
        .build()

    fun render(textView: TextView, markdown: String) {
        markwon.setMarkdown(textView, preprocessMarkdown(markdown))
    }

    fun renderToSpannable(markdown: String): Spannable {
        return markwon.toMarkdown(preprocessMarkdown(markdown))
    }

    /**
     * Pre-process markdown for better mobile rendering
     */
    private fun preprocessMarkdown(input: String): String {
        return input
            // Fix code blocks without language
            .replace(Regex("```\\s*\\n"), "```text\n")
            // Convert single backtick inline code to proper format
            .replace(Regex("`([^`]+)`")) { "`${it.groupValues[1]}`" }
            // Fix bullet points
            .replace(Regex("^[\\s]*[-*][\\s]", RegexOption.MULTILINE), "• ")
    }

    /**
     * Extract code blocks for copy functionality
     */
    fun extractCodeBlocks(markdown: String): List<CodeBlock> {
        val blocks = mutableListOf<CodeBlock>()
        val regex = Regex("```(\\w+)?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        
        regex.findAll(markdown).forEach { match ->
            blocks.add(CodeBlock(
                language = match.groupValues[1].takeIf { it.isNotBlank() } ?: "text",
                code = match.groupValues[2].trim()
            ))
        }
        
        return blocks
    }

    data class CodeBlock(
        val language: String,
        val code: String
    )

    /**
     * Custom plugin for Nexus-specific rendering
     */
    private class CorePlugin : AbstractMarkwonPlugin() {
        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
            // Custom link handler
            builder.linkResolver { view, link ->
                // Handle internal Nexus links (nexus:// protocol)
                if (link.startsWith("nexus://")) {
                    handleNexusLink(view, link)
                } else {
                    // Default browser handling
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                    view.context.startActivity(intent)
                }
            }
        }

        private fun handleNexusLink(view: View, link: String) {
            // Internal navigation handling
            val path = link.removePrefix("nexus://")
            // Emit event or navigate internally
        }
    }
}
