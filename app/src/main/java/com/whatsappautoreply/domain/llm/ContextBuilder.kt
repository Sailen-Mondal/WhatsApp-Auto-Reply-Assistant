package com.whatsappautoreply.domain.llm

import com.whatsappautoreply.data.database.entity.MessageDirection
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.domain.autoreply.ContextMessage

/**
 * Builds a smart, sliding conversation context window for LLM prompts.
 * Enhanced with:
 *   - Time gap annotations (shows conversation pacing)
 *   - BOT_OUTGOING distinction ([ME🤖] tag)
 *   - Language detection heuristic
 *   - Larger context window for personality consistency
 */
object ContextBuilder {

    private const val MAX_CONTEXT_CHARS = 3000
    private const val MAX_MESSAGES = 15

    // Common Bengali words/patterns for language detection
    private val BANGLA_MARKERS = listOf(
        "ki", "keno", "kothay", "kemon", "holo", "korcho", "jani", "bolo",
        "achen", "ami", "tumi", "apni", "kore", "hobe", "ache", "nei",
        "bhalo", "mone", "bujhi", "bolchi", "korbo", "jabo", "asho",
        "dekhi", "shuno", "bhai", "didi", "re", "je",
        "ta", "ota", "eta", "ki holo", "ki korcho", "kemon acho"
    )

    // Common Hindi/Hinglish words for language detection
    private val HINDI_MARKERS = listOf(
        "kya", "kaise", "kahan", "kyun", "kab", "kaun", "hai",
        "haan", "nahi", "accha", "theek", "bhai", "yaar", "karo",
        "batao", "chalo", "dekho", "suno", "bol", "kar", "ho",
        "raha", "rahi", "wala", "mein", "toh", "abhi", "bas"
    )

    data class BuiltContext(
        val historyLines: List<String>,
        val lastIncomingText: String,
        val contextSnippet: String,
        val messageCount: Int,
        val wasTruncated: Boolean,
        val detectedLanguage: String? = null
    )

    fun build(messages: List<MessageEntity>): BuiltContext {
        val sorted = messages.sortedBy { it.timestamp }
        val limited = sorted.takeLast(MAX_MESSAGES)

        val lines = mutableListOf<String>()
        var totalChars = 0
        var truncated = false
        var prevTimestamp: Long? = null

        for (msg in limited) {
            // Add time gap annotation
            if (prevTimestamp != null) {
                val gap = msg.timestamp - prevTimestamp
                val gapAnnotation = formatTimeGap(gap)
                if (gapAnnotation != null) {
                    lines.add(gapAnnotation)
                    totalChars += gapAnnotation.length + 1
                }
            }
            prevTimestamp = msg.timestamp

            val tag = when (msg.direction) {
                MessageDirection.INCOMING -> "[THEM]"
                MessageDirection.OUTGOING -> "[ME]"
                MessageDirection.BOT_OUTGOING -> "[ME\uD83E\uDD16]"
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
            totalChars += line.length + 1
        }

        val snippet = lines.joinToString("\n")
        val lastIncoming = limited.lastOrNull { it.direction == MessageDirection.INCOMING }
        val lastIncomingText = lastIncoming?.text?.trim()
            ?: lastIncoming?.mediaCaption?.trim()
            ?: "[no text]"

        // Detect language from all message texts
        val allTexts = limited.mapNotNull { it.text?.trim() }
        val detectedLang = detectLanguage(allTexts)

        return BuiltContext(
            historyLines = lines,
            lastIncomingText = lastIncomingText,
            contextSnippet = snippet,
            messageCount = lines.count { it.startsWith("[THEM]") || it.startsWith("[ME") },
            wasTruncated = truncated,
            detectedLanguage = detectedLang
        )
    }

    fun buildFromContext(context: List<ContextMessage>): BuiltContext {
        val limited = context.takeLast(MAX_MESSAGES)
        val lines = mutableListOf<String>()
        var totalChars = 0
        var truncated = false
        var prevTimestamp: Long? = null

        for (msg in limited) {
            // Add time gap annotation
            if (prevTimestamp != null && msg.timestamp > 0) {
                val gap = msg.timestamp - prevTimestamp
                val gapAnnotation = formatTimeGap(gap)
                if (gapAnnotation != null) {
                    lines.add(gapAnnotation)
                    totalChars += gapAnnotation.length + 1
                }
            }
            if (msg.timestamp > 0) prevTimestamp = msg.timestamp

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

        val allTexts = limited.mapNotNull { it.text?.trim() }
        val detectedLang = detectLanguage(allTexts)

        return BuiltContext(
            historyLines = lines,
            lastIncomingText = lastIncomingText,
            contextSnippet = snippet,
            messageCount = lines.count { it.startsWith("[THEM]") || it.startsWith("[ME") },
            wasTruncated = truncated,
            detectedLanguage = detectedLang
        )
    }

    /**
     * Format a time gap between messages as an annotation.
     * Returns null for small gaps (< 30 minutes).
     */
    private fun formatTimeGap(gapMs: Long): String? {
        val minutes = gapMs / 60_000
        return when {
            minutes < 30 -> null
            minutes < 60 -> "[~${minutes}m gap]"
            minutes < 1440 -> "[~${minutes / 60}h gap]"
            else -> "[~${minutes / 1440}d gap]"
        }
    }

    /**
     * Simple language detection heuristic.
     * Scans message texts for Bengali, Hindi, or English markers.
     */
    private fun detectLanguage(texts: List<String>): String? {
        if (texts.isEmpty()) return null

        val combined = texts.joinToString(" ").lowercase()
        val words = combined.split(Regex("\\s+"))

        var banglaScore = 0
        var hindiScore = 0
        var englishWordCount = 0

        for (word in words) {
            val clean = word.trim(',', '.', '!', '?', '"', '\'', '(', ')')
            if (clean.isBlank()) continue

            when {
                BANGLA_MARKERS.any { clean == it || (clean.length > 3 && clean.startsWith(it)) } -> banglaScore++
                HINDI_MARKERS.any { clean == it || (clean.length > 3 && clean.startsWith(it)) } -> hindiScore++
                clean.matches(Regex("[a-z]+")) && clean.length > 2 -> englishWordCount++
            }
        }

        val total = banglaScore + hindiScore + englishWordCount
        if (total == 0) return null

        return when {
            banglaScore > hindiScore && banglaScore > 0 -> {
                if (englishWordCount > banglaScore) "Banglish" else "Bengali"
            }
            hindiScore > banglaScore && hindiScore > 0 -> {
                if (englishWordCount > hindiScore) "Hinglish" else "Hindi"
            }
            englishWordCount > 0 -> "English"
            else -> null
        }
    }
}
