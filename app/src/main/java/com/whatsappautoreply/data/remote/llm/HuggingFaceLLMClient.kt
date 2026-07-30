package com.whatsappautoreply.data.remote.llm

import com.whatsappautoreply.BuildConfig
import com.whatsappautoreply.data.database.dao.LLMLogDao
import com.whatsappautoreply.data.database.entity.LLMLogEntity
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.data.repository.SettingsRepository
import com.whatsappautoreply.domain.autoreply.ContextMessage
import com.whatsappautoreply.domain.brain.BrainLoader
import com.whatsappautoreply.domain.llm.ContextBuilder
import com.whatsappautoreply.domain.llm.PromptTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Singleton
import com.whatsappautoreply.util.DebugLogger
import com.whatsappautoreply.data.remote.llm.provider.LlmRouter
import com.whatsappautoreply.data.remote.llm.provider.OpenAiCompatibleProvider
import com.whatsappautoreply.data.remote.llm.provider.GeminiProvider
import com.google.gson.Gson

@Singleton
class HuggingFaceLLMClient @Inject constructor(
    private val llmLogDao: LLMLogDao,
    /** Used for API key decryption — do NOT bypass this with direct DAO access */
    private val settingsRepository: SettingsRepository,
    private val brainLoader: BrainLoader
) {
    companion object {
        private const val TAG = "LLMClient"

        // ─── Bootstrap key ────────────────────────────────────────────────────
        // Loaded from local.properties (git-ignored) via BuildConfig at compile time.
        // Never hardcode secrets here — set OPENROUTER_API_KEY in local.properties.
        val BOOTSTRAP_KEY: String get() = BuildConfig.OPENROUTER_API_KEY
    }

    // Cached Router — rebuilt whenever the openrouter key changes
    @Volatile private var _router: LlmRouter? = null
    @Volatile private var _cachedKey: String = ""

    // ─── API Key & Client Setup ───────────────────────────────────────────────

    /**
     * Returns the decrypted stored API key, bootstrapping from the hardcoded
     * constant if none has been saved yet.
     */
    private suspend fun getApiKey(): String {
        val stored = settingsRepository.getString("api_key")?.trim()?.replace("\n", "")?.replace("\r", "")?.takeIf { it.isNotBlank() }
        if (stored != null) return stored

        val bootstrapKey = BOOTSTRAP_KEY.trim().replace("\n", "").replace("\r", "")
        DebugLogger.logEvent(TAG, "BOOTSTRAP_KEY_SAVED")
        settingsRepository.setString("api_key", bootstrapKey)
        return bootstrapKey
    }

    fun invalidateClient() {
        _router = null
        _cachedKey = ""
        DebugLogger.logEvent(TAG, "CLIENT_INVALIDATED")
    }

    private suspend fun getRouter(): LlmRouter {
        val key = getApiKey()
        if (_router == null || key != _cachedKey) {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                         else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val gson = Gson()

            // Primary: Gemini
            val geminiProvider = GeminiProvider(
                apiKey = BuildConfig.GEMINI_API_KEY,
                client = client,
                gson = gson
            )

            // Secondary: OpenRouter
            val openRouterProvider = OpenAiCompatibleProvider(
                name = "OpenRouter",
                modelName = "google/gemma-4-31b-it:free",
                baseUrl = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = key,
                client = client,
                gson = gson
            )

            // Fallback: NVIDIA NIM (Qwen2.5)
            val nimProvider = OpenAiCompatibleProvider(
                name = "NVIDIA NIM",
                modelName = "qwen/qwen2.5-72b-instruct",
                baseUrl = "https://integrate.api.nvidia.com/v1/chat/completions",
                apiKey = BuildConfig.NVIDIA_NIM_API_KEY,
                client = client,
                gson = gson
            )

            _router = LlmRouter(listOf(geminiProvider, openRouterProvider, nimProvider))
            _cachedKey = key
            DebugLogger.logEvent(TAG, "ROUTER_INITIALIZED", mapOf("has_key" to key.isNotBlank()))
        }
        return _router!!
    }

    // ─── Main Reply Generation ────────────────────────────────────────────────

    suspend fun generateReply(
        chatId: String,
        recentMessages: List<MessageEntity>,
        preferredTone: String? = null,
        detectedMood: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val builtContext = ContextBuilder.build(recentMessages)
        val tone = if (preferredTone.isNullOrBlank() || preferredTone == "auto") "auto" else preferredTone

        val systemPrompt = brainLoader.assembleSystemPrompt(tone, detectedMood)
        val userPrompt   = PromptTemplates.userPrompt(builtContext.contextSnippet, builtContext.lastIncomingText, builtContext.detectedLanguage)

        DebugLogger.logEvent(TAG, "GENERATE_REPLY_START", mapOf(
            "chatId"               to chatId,
            "tone"                 to tone,
            "mood"                 to (detectedMood ?: "unknown"),
            "context_lines"        to builtContext.messageCount,
            "was_truncated"        to builtContext.wasTruncated,
            "last_incoming_preview" to builtContext.lastIncomingText.take(40)
        ))

        try {
            val rawReply = getRouter().executeWithRouting(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                maxTokens = 5000,
                temperature = 0.7
            )
            
            var reply = cleanReply(rawReply)

            if (reply == "[NO_REPLY]") {
                DebugLogger.logEvent(TAG, "REPLY_NO_REPLY_SIGNAL", mapOf("chatId" to chatId))
                return@withContext null
            }

            if (reply == null) {
                // Emergency Fallback
                val emergencyReplies = listOf("Ekto pore reply dichi.", "Network ta ekto ulta palta korche \uD83D\uDE05", "Hold on, ekto busy achi.")
                reply = emergencyReplies.random()
                DebugLogger.logEvent(TAG, "EMERGENCY_FALLBACK_TRIGGERED", mapOf("chatId" to chatId, "reply" to reply))
            }

            // Persist to log
            llmLogDao.insertLog(LLMLogEntity(
                chatId             = chatId,
                inputContextSnippet = builtContext.contextSnippet.take(500),
                generatedReply     = reply,
                toneUsed           = tone,
                wasAutoSent        = false
            ))

            DebugLogger.logEvent(TAG, "REPLY_GENERATED", mapOf(
                "chatId"        to chatId,
                "reply_length"  to reply.length,
                "reply_preview" to reply.take(50)
            ))

            reply
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DebugLogger.logError(TAG, "GENERATE_REPLY_ERROR", e, mapOf("chatId" to chatId))
            // Return emergency fallback on total failure as well
            val emergencyReplies = listOf("Ekto pore reply dichi.", "Network ta ekto ulta palta korche \uD83D\uDE05", "Hold on, ekto busy achi.")
            emergencyReplies.random()
        }
    }

    suspend fun generateReply(
        context: List<ContextMessage>,
        tone: String,
        detectedMood: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val builtContext   = ContextBuilder.buildFromContext(context)
        val systemPrompt   = brainLoader.assembleSystemPrompt(tone, detectedMood)
        val userPrompt     = PromptTemplates.userPrompt(builtContext.contextSnippet, builtContext.lastIncomingText, builtContext.detectedLanguage)

        try {
            val rawReply = getRouter().executeWithRouting(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                maxTokens = 5000,
                temperature = 0.7
            )
            
            var reply = cleanReply(rawReply)

            if (reply == "[NO_REPLY]") return@withContext null

            if (reply == null) {
                // Emergency Fallback
                val emergencyReplies = listOf("Ekto pore reply dichi.", "Network ta ekto ulta palta korche \uD83D\uDE05", "Hold on, ekto busy achi.")
                reply = emergencyReplies.random()
                DebugLogger.logEvent(TAG, "EMERGENCY_FALLBACK_TRIGGERED", mapOf("reply" to reply))
            }
            
            reply
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DebugLogger.logError(TAG, "GENERATE_REPLY_CONTEXT_ERROR", e)
            val emergencyReplies = listOf("Ekto pore reply dichi.", "Network ta ekto ulta palta korche \uD83D\uDE05", "Hold on, ekto busy achi.")
            emergencyReplies.random()
        }
    }

    // ─── Mood & Tone Analysis ─────────────────────────────────────────────────

    suspend fun analyzeToneAndMood(recentMessages: List<MessageEntity>): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            val builtContext = ContextBuilder.build(recentMessages)

            try {
                val raw = getRouter().executeWithRouting(
                    systemPrompt = PromptTemplates.EMOTIONAL_ANALYSIS_SYSTEM,
                    userPrompt = PromptTemplates.emotionalAnalysisUserPrompt(builtContext.contextSnippet),
                    maxTokens = 40,
                    temperature = 0.3
                ) ?: return@withContext Pair(null, null)

                val emotionMatch = Regex("\"emotion\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                val dynamicMatch = Regex("\"dynamic\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)

                DebugLogger.logEvent(TAG, "EMOTIONAL_CONTEXT_ANALYZED",
                    mapOf("emotion" to (emotionMatch ?: "null"), "dynamic" to (dynamicMatch ?: "null")))
                Pair(emotionMatch, dynamicMatch)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                DebugLogger.logError(TAG, "EMOTIONAL_ANALYSIS_ERROR", e)
                Pair(null, null)
            }
        }

    suspend fun analyzeTone(recentMessages: List<MessageEntity>): String? =
        analyzeToneAndMood(recentMessages).first

    // ─── LLM-Based Question Detection ────────────────────────────────────────

    suspend fun isQuestionOrExpectsReply(text: String): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false

        try {
            val raw = getRouter().executeWithRouting(
                systemPrompt = PromptTemplates.QUESTION_DETECTION_SYSTEM,
                userPrompt = PromptTemplates.questionDetectionUserPrompt(text.take(200)),
                maxTokens = 5,
                temperature = 0.1
            )?.uppercase() ?: return@withContext true // failsafe: assume yes if LLM fails

            val result = raw.startsWith("YES")
            DebugLogger.logEvent(TAG, "QUESTION_DETECTED",
                mapOf("text_preview" to text.take(40), "result" to result))
            result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DebugLogger.logError(TAG, "QUESTION_DETECTION_ERROR", e)
            true // failsafe: reply rather than miss a message
        }
    }

    // ─── Reply Sanitization ───────────────────────────────────────────────────

    private fun cleanReply(text: String?): String? {
        if (text == null) return null
        var cleaned = text

        // Remove model artifact tags
        cleaned = cleaned.replace("[/INST]", "",  ignoreCase = true)
        cleaned = cleaned.replace("</s>",   "",   ignoreCase = true)
        cleaned = cleaned.replace("<s>",    "",   ignoreCase = true)
        cleaned = cleaned.replace("[THEM]", "",   ignoreCase = true)

        // Remove [ME] and [ME🤖] tags if model hallucinated them
        cleaned = cleaned.replace(Regex("\\[ME[^\\]]*]"), "")

        // Remove markdown formatting
        cleaned = cleaned.replace(Regex("\\*+"), "")
        cleaned = cleaned.replace(Regex("_+"),   "")

        // Strip leading "Reply:" or "My reply:" if model hallucinated it
        cleaned = cleaned.replace(Regex("^(my reply:|reply:)\\s*", RegexOption.IGNORE_CASE), "")

        // Trim outer whitespace and quotes, but PRESERVE internal line breaks
        cleaned = cleaned.trim('"', '\'', ' ')
        cleaned = cleaned.trimStart('\n', '\r').trimEnd('\n', '\r').trim()

        // Ban emojis completely
        cleaned = cleaned.filter { !it.isSurrogate() }

        // Block AI self-identification phrases
        val lower = cleaned.lowercase()
        val aiPhrases = listOf(
            "as an ai", "i'm an ai", "i am an ai", "language model",
            "i cannot help", "i can't help", "i'm a bot", "i am a bot",
            "as an assistant", "i'm an assistant"
        )
        if (aiPhrases.any { lower.contains(it) }) {
            DebugLogger.logEvent(TAG, "REPLY_BLOCKED_AI_DISCLAIMER")
            return null
        }

        // Block robotic/assistant-like phrases
        val roboticPhrases = listOf(
            "i hope this helps", "feel free to ask", "let me know if you need",
            "is there anything else", "how can i help", "how can i assist",
            "i'd be happy to help", "don't hesitate to"
        )
        if (roboticPhrases.any { lower.contains(it) }) {
            DebugLogger.logEvent(TAG, "REPLY_BLOCKED_ROBOTIC", mapOf("preview" to cleaned.take(60)))
            return null
        }

        // Length validation (min 1 char, max 800)
        if (cleaned.length < 1 || cleaned.length > 800) {
            DebugLogger.logEvent(TAG, "REPLY_BLOCKED_LENGTH", mapOf("length" to cleaned.length))
            return null
        }

        return cleaned.ifBlank { null }
    }
}
