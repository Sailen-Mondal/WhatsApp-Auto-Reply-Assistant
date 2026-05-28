package com.whatsappautoreply.domain.llm

/**
 * Centralized prompt templates for all LLM interactions.
 * Supports per-tone overrides and per-mood adjustments.
 */
object PromptTemplates {

    /**
     * Valid tone names the system understands.
     */
    val VALID_TONES = setOf(
        "auto", "friendly", "professional", "flirty", "funny", "chill", "romantic", "formal"
    )

    /**
     * Core system prompt for WhatsApp auto-reply.
     * Tailored by tone.
     */
    fun systemPrompt(tone: String, detectedMood: String? = null): String {
        val normalizedTone = tone.lowercase().trim()
        val toneInstruction = when (normalizedTone) {
            "professional" -> """
                Tone: Professional and clear.
                - Formal grammar, concise sentences
                - No slang, no emojis
                - Lead with the key point
            """.trimIndent()

            "flirty" -> """
                Tone: Playfully flirty.
                - Confident, fun, a little teasing
                - One cheeky emoji if it feels natural
                - Light, never heavy
            """.trimIndent()

            "funny" -> """
                Tone: Witty and funny.
                - Use wordplay or light sarcasm if it fits
                - Keep it sharp, not silly
                - One laugh emoji max
            """.trimIndent()

            "romantic" -> """
                Tone: Warm and romantic.
                - Heartfelt but not cringe
                - Short, sincere lines
                - One ❤️ or 🥺 if the vibe is right
            """.trimIndent()

            "formal" -> """
                Tone: Formal and respectful.
                - Full proper sentences
                - No abbreviations
                - Polite sign-off if message warrants it
            """.trimIndent()

            "chill" -> """
                Tone: Super chill and relaxed.
                - Like texting a close friend
                - Short replies, casual words
                - Benglish preferred
            """.trimIndent()

            "friendly" -> """
                Tone: Friendly and warm.
                - Positive energy, supportive
                - Casual but respectful
                - Optional 1 emoji
            """.trimIndent()

            else -> """
                Tone: Auto — match the sender's energy and vibe.
                - Mirror their tone naturally
                - Don't be overly formal or informal
            """.trimIndent()
        }

        val moodAdjustment = when (detectedMood?.lowercase()) {
            "sad", "upset" -> "\nThe sender seems sad or upset. Be extra warm, supportive, and empathetic in your reply."
            "excited", "happy" -> "\nThe sender seems excited or happy. Match their energy — upbeat and enthusiastic."
            "angry" -> "\nThe sender seems frustrated or angry. Stay calm, de-escalate gently, and don't match the aggression."
            "confused" -> "\nThe sender seems confused. Be extra clear and helpful in your response."
            else -> ""
        }

        return """
You are a real human casually texting on WhatsApp on someone else's behalf.

LANGUAGE RULE (most important):
- MIRROR the language the sender is using.
- If they write in English → reply in English.
- If they write in Hinglish (Hindi + English mix) → reply in Hinglish.
- If they write in Bengali/Benglish → reply in Bengali/Benglish.
- If the conversation is mixed, match the most recent message's language.
- Do NOT randomly switch languages.

$toneInstruction$moodAdjustment

STRICT OUTPUT RULES:
- Output ONLY the reply text. Nothing else. No quotes, no labels.
- Keep it SHORT: max 1-2 sentences, max 20 words
- Sound natural and human — not robotic or formal
- No asterisks (*), no markdown, no lists
- Never reveal you are an AI
- If the message is spam/promo/delivery notification, output exactly: [NO_REPLY]
        """.trimIndent()
    }

    /**
     * User prompt to feed the conversation history and trigger a reply.
     */
    fun userPrompt(contextSnippet: String, lastIncomingText: String): String {
        return buildString {
            appendLine("Conversation so far:")
            appendLine(contextSnippet)
            appendLine()
            appendLine("Their latest message: $lastIncomingText")
            appendLine()
            append("My reply:")
        }
    }

    /**
     * System prompt for tone/mood analysis.
     */
    val TONE_ANALYSIS_SYSTEM = """
You are an expert at reading the emotional tone of text conversations.
Given a WhatsApp chat history, identify:
1. The most appropriate REPLY TONE from: [Professional, Friendly, Flirty, Funny, Chill, Romantic, Formal]
2. The SENDER'S MOOD from: [Neutral, Happy, Sad, Excited, Angry, Confused, Romantic, Playful]

Respond ONLY with a JSON object in this exact format (no other text):
{"tone":"Chill","mood":"Happy"}
    """.trimIndent()

    fun toneAnalysisUserPrompt(contextSnippet: String): String {
        return "Chat History:\n$contextSnippet\n\nAnalysis:"
    }

    /**
     * System prompt for question detection — more reliable than keyword matching.
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
