package com.whatsappautoreply.domain.llm

/**
 * Centralized prompt templates for all LLM interactions.
 *
 * ARCHITECTURE NOTE:
 * The OWL persona system prompt is no longer hardcoded here.
 * It is now assembled at runtime by [com.whatsappautoreply.domain.brain.BrainLoader]
 * from the 8 brain files stored in assets/brain/ and filesDir/brain/.
 *
 * This object now acts as a thin façade for:
 *   - Operational prompt templates (emotional analysis, question detection, user prompt)
 *   - Constants used by the LLM client
 *
 * For the full system prompt, call: BrainLoader.assembleSystemPrompt(toneHint, emotionalContext)
 */
object PromptTemplates {

    /**
     * Tone hint names the system understands.
     * These are gentle guidance — the persona adapts naturally regardless.
     */
    val TONE_HINTS = setOf(
        "auto", "friendly", "professional", "flirty", "funny", "chill", "romantic", "formal"
    )

    // Keep backward compatibility
    val VALID_TONES get() = TONE_HINTS

    /**
     * User prompt to feed conversation history and trigger a reply.
     * This is unchanged — it provides conversation context to the LLM.
     */
    fun userPrompt(
        contextSnippet: String,
        lastIncomingText: String,
        detectedLanguage: String? = null
    ): String {
        val langHint = if (!detectedLanguage.isNullOrBlank()) {
            "Chat language detected: $detectedLanguage\n\n"
        } else ""

        return buildString {
            append(langHint)
            appendLine("Conversation so far:")
            appendLine(contextSnippet)
            appendLine()
            appendLine("Their latest message: $lastIncomingText")
            appendLine()
            appendLine("CRITICAL RULE: DO NOT USE EMOJIS! Emojis are strictly banned.")
            appendLine()
            append("My reply:")
        }
    }

    /**
     * System prompt for emotional context analysis.
     * Returns a JSON object with emotion, dynamic, and energy.
     */
    val EMOTIONAL_ANALYSIS_SYSTEM = """
You are an expert at reading emotional context in text conversations.
Given a WhatsApp chat history, analyze:
1. EMOTION: The sender's current emotional state (e.g., neutral, happy, sad, excited, frustrated, confused, playful, romantic, uncertain, anxious)
2. DYNAMIC: The conversation dynamic (e.g., casual_banter, deep_talk, flirty_exchange, practical, awkward, emotional_support, catching_up)
3. ENERGY: The energy level (high, medium, low)

Respond ONLY with a JSON object in this exact format (no other text):
{"emotion":"playful","dynamic":"casual_banter","energy":"high"}
    """.trimIndent()

    // Backward compatibility alias
    val TONE_ANALYSIS_SYSTEM get() = EMOTIONAL_ANALYSIS_SYSTEM

    fun emotionalAnalysisUserPrompt(contextSnippet: String): String {
        return "Chat History:\n$contextSnippet\n\nAnalysis:"
    }

    // Backward compatibility alias
    fun toneAnalysisUserPrompt(contextSnippet: String) = emotionalAnalysisUserPrompt(contextSnippet)

    /**
     * System prompt for question detection — whether a message expects a response.
     */
    val QUESTION_DETECTION_SYSTEM = """
You are a message classifier. Determine if the given message is asking a question or expecting a response.
Consider: direct questions, implicit questions, requests for opinion, "wdyt", "right?", "no?", "na?", etc.
Output ONLY: YES or NO
    """.trimIndent()

    fun questionDetectionUserPrompt(text: String): String {
        return "Message: $text\n\nIs this a question or does it expect a response? (YES/NO):"
    }
}
