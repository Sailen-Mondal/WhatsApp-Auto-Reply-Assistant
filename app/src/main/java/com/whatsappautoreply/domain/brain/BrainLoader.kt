package com.whatsappautoreply.domain.brain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the LLM system prompt by compositing all relevant brain files.
 *
 * Assembly order (from BrainFile.promptOrder):
 *   1. IDENTITY.md  — Who OWL is
 *   2. SOUL.md      — Core personality
 *   3. AGENTS.md    — Operating instructions
 *   4. USER.md      — User profile
 *   5. MEMORY.md    — Long-term durable facts
 *   6. SKILL.md     — Reply engine rules
 *   7. TOOLS.md     — Platform constraints
 *   + today's memory/YYYY-MM-DD.md (after MEMORY)
 *   + tone hint (gentle per-chat nudge)
 *   + emotional context (from conversation analysis)
 *
 * This replaces the monolithic PromptTemplates.systemPrompt() function.
 * All personality, rules, and user context now come from editable files.
 */
@Singleton
class BrainLoader @Inject constructor(
    private val brainRepository: BrainRepository
) {
    companion object {
        private const val SECTION_SEPARATOR = "\n\n---\n\n"
    }

    /**
     * Assemble the full system prompt from all brain files.
     *
     * @param toneHint Optional per-chat tone guidance (e.g., "flirty", "professional")
     * @param emotionalContext Optional detected emotional context from conversation analysis
     */
    suspend fun assembleSystemPrompt(
        toneHint: String? = null,
        emotionalContext: String? = null
    ): String {
        val sections = mutableListOf<String>()

        // Load all files that should be injected into the prompt, in order
        for (brainFile in BrainFile.systemPromptFiles) {
            val content = brainRepository.read(brainFile).trim()
            if (content.isNotBlank()) {
                sections.add(content)
            }
        }

        // Inject yesterday's memory if available (continuity context)
        val yesterdayMemory = brainRepository.readYesterdayMemory().trim()
        if (yesterdayMemory.isNotBlank() && !yesterdayMemory.contains("No notes yet for today")) {
            sections.add("## YESTERDAY'S CONTEXT\n\n$yesterdayMemory")
        }

        // Inject today's daily memory (fresh context)
        val todayMemory = brainRepository.readTodayMemory().trim()
        if (todayMemory.isNotBlank() && !todayMemory.contains("No notes yet for today")) {
            sections.add("## TODAY'S CONTEXT\n\n$todayMemory")
        }

        // Append tone hint as a gentle nudge (not a hard override)
        val toneGuidance = buildToneGuidance(toneHint)
        if (toneGuidance.isNotBlank()) {
            sections.add(toneGuidance)
        }

        // Append emotional context from conversation analysis
        if (!emotionalContext.isNullOrBlank()) {
            sections.add("## CONVERSATION CONTEXT RIGHT NOW\n\n$emotionalContext")
        }

        return sections.joinToString(SECTION_SEPARATOR)
    }

    /**
     * Read the content of a single brain file (used by Brain Editor).
     */
    suspend fun readFile(brainFile: BrainFile): String {
        return brainRepository.read(brainFile)
    }

    /**
     * Build a tone guidance string from a per-chat tone hint.
     * Tones are gentle nudges — the persona adapts naturally regardless.
     */
    private fun buildToneGuidance(toneHint: String?): String {
        val normalized = toneHint?.lowercase()?.trim()
        if (normalized.isNullOrBlank() || normalized == "auto") return ""

        val guidance = when (normalized) {
            "professional" -> "The user prefers a more professional vibe for this chat. Keep it clear, direct, and polished — but still human."
            "flirty"       -> "The user wants a flirty vibe for this chat. Be playfully flirtatious, confident, a little teasing — but always tasteful."
            "funny"        -> "The user wants a funny vibe for this chat. Be witty, use wordplay or light sarcasm — keep it sharp, not silly."
            "romantic"     -> "The user wants a romantic vibe for this chat. Be warm, heartfelt, and sincere — not cringe."
            "formal"       -> "The user wants a formal tone for this chat. Use proper sentences, no abbreviations, respectful throughout."
            "chill"        -> "The user wants a super chill vibe for this chat. Like texting a close friend — short, relaxed, casual."
            "friendly"     -> "The user wants a friendly, warm vibe for this chat. Positive energy, supportive, casual but respectful."
            else           -> "The user has hinted at a '$normalized' vibe for this chat. Adapt naturally."
        }
        return "## TONE HINT\n\n$guidance\n\nThis is a gentle preference — adapt naturally based on the conversation flow. Don't force it."
    }
}
