package com.whatsappautoreply.domain.brain

/**
 * Enum representing all brain files in the OWL personality system.
 *
 * Brain files live in two locations:
 *   1. assets/brain/ — shipped defaults (read-only, always available)
 *   2. filesDir/brain/ — runtime copies (user-editable, persisted between launches)
 *
 * On first launch, all asset files are copied to filesDir. The user can then
 * edit the filesDir copies via the Brain Editor without touching the defaults.
 */
enum class BrainFile(
    /** Filename within the brain directory (e.g., "SOUL.md") */
    val fileName: String,
    /** Sub-directory within brain/ (null = root level) */
    val subDir: String? = null,
    /** Emoji representing this file's role */
    val emoji: String,
    /** Human-readable role description */
    val role: String,
    /** One-line summary of what this file contains */
    val summary: String,
    /** Whether this file is shown in the Brain Editor for user editing */
    val isEditable: Boolean = true,
    /** Whether this file is injected into the LLM system prompt */
    val includeInSystemPrompt: Boolean = true,
    /** Order in which this file is injected into the system prompt (lower = earlier) */
    val promptOrder: Int = 99
) {
    IDENTITY(
        fileName = "IDENTITY.md",
        emoji = "🏷️",
        role = "Self-Image",
        summary = "Agent name, self-description, social role, and critical identity rules",
        promptOrder = 1
    ),
    SOUL(
        fileName = "SOUL.md",
        emoji = "🎭",
        role = "Personality & Values",
        summary = "Core values, emotional style, playfulness, flirtation rules, what OWL naturally avoids",
        promptOrder = 2
    ),
    AGENTS(
        fileName = "AGENTS.md",
        emoji = "⚙️",
        role = "Session Manager",
        summary = "Startup sequence, reply flow, when to consult memory, operating rules",
        promptOrder = 3
    ),
    USER(
        fileName = "USER.md",
        emoji = "👤",
        role = "User Profile",
        summary = "Who the user is, language preferences, communication style, likes and dislikes",
        promptOrder = 4
    ),
    MEMORY(
        fileName = "MEMORY.md",
        emoji = "🧬",
        role = "Long-term Memory",
        summary = "Durable stable facts and preferences that persist across all sessions",
        promptOrder = 5
    ),
    SKILL(
        fileName = "SKILL.md",
        subDir = "skills/whatsapp-replies",
        emoji = "💬",
        role = "Reply Engine",
        summary = "How to classify messages, generate replies, adapt language, maintain rhythm",
        promptOrder = 6
    ),
    TOOLS(
        fileName = "TOOLS.md",
        emoji = "🛠️",
        role = "Platform Details",
        summary = "WhatsApp integration, reply constraints, rate limits, API details",
        isEditable = false,
        promptOrder = 7
    ),
    HEARTBEAT(
        fileName = "HEARTBEAT.md",
        emoji = "⏰",
        role = "Proactive Checks",
        summary = "Optional periodic self-maintenance and memory review tasks",
        includeInSystemPrompt = false,
        promptOrder = 98
    ),
    BOOTSTRAP(
        fileName = "BOOTSTRAP.md",
        emoji = "🚀",
        role = "First-Run Setup",
        summary = "One-time initialization sequence, memory seed, reset instructions",
        includeInSystemPrompt = false,
        isEditable = false,
        promptOrder = 99
    );

    /** The relative path within the brain directory (e.g., "SOUL.md" or "skills/whatsapp-replies/SKILL.md") */
    val relativePath: String get() = if (subDir != null) "$subDir/$fileName" else fileName

    companion object {
        /** All files that should be injected into the LLM system prompt, in order */
        val systemPromptFiles: List<BrainFile>
            get() = entries
                .filter { it.includeInSystemPrompt }
                .sortedBy { it.promptOrder }

        /** All files visible in the Brain Editor */
        val editableFiles: List<BrainFile>
            get() = entries
                .filter { it.isEditable }
                .sortedBy { it.promptOrder }
    }
}
