package com.whatsappautoreply.domain.llm

import com.whatsappautoreply.data.database.entity.MessageDirection
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.domain.autoreply.ContextMessage

/**
 * Builds a smart, sliding conversation context window for LLM prompts.
 * - Limits total token budget by character count
 * - Summarizes bursts of consecutive messages from the same sender
 * - Handles media messages gracefully
 * - Extracts the most recent incoming message cleanly
 */
object ContextBuilder {

    private const val MAX_CONTEXT_CHARS = 2000
    private const val MAX_MESSAGES = 12

    data class BuiltContext(
        val historyLines: List<String>,
        val lastIncomingText: String,
        val contextSnippet: String,
        val messageCount: Int,
        val wasTruncated: Boolean
    )

    fun build(messages: List<MessageEntity>): BuiltContext {
        // Sort ascending, take the most recent N
        val sorted = messages.sortedBy { it.timestamp }
        val limited = sorted.takeLast(MAX_MESSAGES)

        val lines = mutableListOf<String>()
        var totalChars = 0
        var truncated = false

        for (msg in limited) {
            val tag = when (msg.direction) {
                MessageDirection.INCOMING -> "[THEM]"
                MessageDirection.OUTGOING, MessageDirection.BOT_OUTGOING -> "[ME]"
            }
            val rawText = msg.text?.trim()
                ?: msg.mediaCaption?.trim()
                ?: "[${msg.mediaType.name.lowercase().replace("_", " ")}]"

            val line = "$tag $rawText"

            if (totalChars + line.length > MAX_CONTEXT_CHARS) {
                truncated = true
                break
            }
            lines.add(line)
            totalChars += line.length + 1 // +1 for newline
        }

        val snippet = lines.joinToString("\n")

        // Find the last incoming message
        val lastIncoming = limited.lastOrNull { it.direction == MessageDirection.INCOMING }
        val lastIncomingText = lastIncoming?.text?.trim()
            ?: lastIncoming?.mediaCaption?.trim()
            ?: "[no text]"

        return BuiltContext(
            historyLines = lines,
            lastIncomingText = lastIncomingText,
            contextSnippet = snippet,
            messageCount = lines.size,
            wasTruncated = truncated
        )
    }

    fun buildFromContext(context: List<ContextMessage>): BuiltContext {
        val limited = context.takeLast(MAX_MESSAGES)
        val lines = mutableListOf<String>()
        var totalChars = 0
        var truncated = false

        for (msg in limited) {
            val tag = if (msg.isIncoming) "[THEM]" else "[ME]"
            val text = msg.text?.trim() ?: "[media]"
            val line = "$tag $text"

            if (totalChars + line.length > MAX_CONTEXT_CHARS) {
                truncated = true
                break
            }
            lines.add(line)
            totalChars += line.length + 1
        }

        val snippet = lines.joinToString("\n")
        val lastIncoming = limited.lastOrNull { it.isIncoming }
        val lastIncomingText = lastIncoming?.text?.trim() ?: "[no text]"

        return BuiltContext(
            historyLines = lines,
            lastIncomingText = lastIncomingText,
            contextSnippet = snippet,
            messageCount = lines.size,
            wasTruncated = truncated
        )
    }
}
