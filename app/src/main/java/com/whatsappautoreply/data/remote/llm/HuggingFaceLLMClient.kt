package com.whatsappautoreply.data.remote.llm

import com.whatsappautoreply.BuildConfig
import com.whatsappautoreply.data.database.dao.LLMLogDao
import com.whatsappautoreply.data.database.entity.LLMLogEntity
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.data.database.entity.SettingsEntity
import com.whatsappautoreply.domain.autoreply.ContextMessage
import com.whatsappautoreply.domain.llm.ContextBuilder
import com.whatsappautoreply.domain.llm.PromptTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton
import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.util.DebugLogger

@Singleton
class HuggingFaceLLMClient @Inject constructor(
    private val llmLogDao: LLMLogDao,
    private val settingsDao: SettingsDao
) {
    companion object {
        private const val TAG = "LLMClient"
        private const val BASE_URL = "https://openrouter.ai/api/"

        // ─── Model selection ─────────────────────────────────────────────────
        // Primary: OpenRouter Owl Alpha — price=0/0 (truly free), confirmed working
        private const val PRIMARY_MODEL   = "openrouter/owl-alpha"
        // Fallback: Llama 3.1 8B — free, fast, used if primary is unavailable
        private const val FALLBACK_MODEL  = "meta-llama/llama-3.1-8b-instruct:free"

        // ─── Bootstrap key ────────────────────────────────────────────────────
        // Loaded from local.properties (git-ignored) via BuildConfig at compile time.
        // Never hardcode secrets here — set OPENROUTER_API_KEY in local.properties.
        val BOOTSTRAP_KEY: String get() = BuildConfig.OPENROUTER_API_KEY
    }

    // Cached OkHttp client — rebuilt whenever the key changes
    @Volatile private var _api: HuggingFaceApi? = null
    @Volatile private var _cachedKey: String = ""

    // ─── API Key & Client Setup ───────────────────────────────────────────────

    /**
     * Returns the stored API key, bootstrapping from the hardcoded constant if
     * none has been saved yet.  Call this every time — it is lightweight (1 DB read).
     */
    private suspend fun getApiKey(): String {
        val stored = settingsDao.getSetting("api_key")?.value?.takeIf { it.isNotBlank() }
        if (stored != null) return stored

        // First run: persist the bootstrap key so it works immediately without
        // the user needing to open Settings first.
        DebugLogger.logEvent(TAG, "BOOTSTRAP_KEY_SAVED")
        settingsDao.insertSetting(SettingsEntity("api_key", BOOTSTRAP_KEY))
        return BOOTSTRAP_KEY
    }

    fun invalidateClient() {
        _api = null
        _cachedKey = ""
        DebugLogger.logEvent(TAG, "CLIENT_INVALIDATED")
    }

    private suspend fun getApi(): HuggingFaceApi {
        val key = getApiKey()
        // Rebuild the OkHttp client if the key has changed (e.g. user updated it in Settings)
        if (_api == null || key != _cachedKey) {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                         else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("Authorization", "Bearer $key")
                        .header("HTTP-Referer", "https://github.com/whatsapp-auto-reply")
                        .header("X-Title", "WhatsApp Auto Reply")
                        .header("Content-Type", "application/json")
                        .method(original.method, original.body)
                        .build()
                    chain.proceed(request)
                }
                .build()

            _api = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(HuggingFaceApi::class.java)

            _cachedKey = key
            DebugLogger.logEvent(TAG, "CLIENT_INITIALIZED", mapOf("has_key" to key.isNotBlank()))
        }
        return _api!!
    }

    // ─── Main Reply Generation ────────────────────────────────────────────────

    /**
     * Generate a reply from a list of raw MessageEntity objects.
     * Uses ContextBuilder for smart context windowing.
     */
    suspend fun generateReply(
        chatId: String,
        recentMessages: List<MessageEntity>,
        preferredTone: String? = null,
        detectedMood: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val builtContext = ContextBuilder.build(recentMessages)
        val tone = if (preferredTone.isNullOrBlank() || preferredTone == "auto") "auto" else preferredTone

        val systemPrompt = PromptTemplates.systemPrompt(tone, detectedMood)
        val userPrompt   = PromptTemplates.userPrompt(builtContext.contextSnippet, builtContext.lastIncomingText)

        DebugLogger.logEvent(TAG, "GENERATE_REPLY_START", mapOf(
            "chatId"               to chatId,
            "tone"                 to tone,
            "mood"                 to (detectedMood ?: "unknown"),
            "context_lines"        to builtContext.messageCount,
            "was_truncated"        to builtContext.wasTruncated,
            "last_incoming_preview" to builtContext.lastIncomingText.take(40)
        ))

        try {
            val reply = executeWithFallback(PRIMARY_MODEL, FALLBACK_MODEL) { model ->
                val request = HuggingFaceChatRequest(
                    model    = model,
                    messages = listOf(
                        HuggingFaceMessage(role = "system", content = systemPrompt),
                        HuggingFaceMessage(role = "user",   content = userPrompt)
                    ),
                    maxTokens   = 120,
                    temperature = 0.75
                )
                val response = getApi().createChatCompletion(request)
                cleanReply(response.choices?.firstOrNull()?.message?.content?.trim())
            } ?: return@withContext null

            if (reply == "[NO_REPLY]") {
                DebugLogger.logEvent(TAG, "REPLY_NO_REPLY_SIGNAL", mapOf("chatId" to chatId))
                return@withContext null
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
            DebugLogger.logError(TAG, "GENERATE_REPLY_ERROR", e, mapOf("chatId" to chatId))
            null
        }
    }

    /**
     * Generate a reply from a ContextMessage list (used by the auto-reply worker).
     */
    suspend fun generateReply(
        context: List<ContextMessage>,
        tone: String,
        detectedMood: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val builtContext   = ContextBuilder.buildFromContext(context)
        val systemPrompt   = PromptTemplates.systemPrompt(tone, detectedMood)
        val userPrompt     = PromptTemplates.userPrompt(builtContext.contextSnippet, builtContext.lastIncomingText)

        try {
            val reply = executeWithFallback(PRIMARY_MODEL, FALLBACK_MODEL) { model ->
                val request = HuggingFaceChatRequest(
                    model    = model,
                    messages = listOf(
                        HuggingFaceMessage(role = "system", content = systemPrompt),
                        HuggingFaceMessage(role = "user",   content = userPrompt)
                    ),
                    maxTokens   = 120,
                    temperature = 0.75
                )
                cleanReply(getApi().createChatCompletion(request).choices?.firstOrNull()?.message?.content?.trim())
            } ?: return@withContext null

            if (reply == "[NO_REPLY]") return@withContext null
            reply
        } catch (e: Exception) {
            DebugLogger.logError(TAG, "GENERATE_REPLY_CONTEXT_ERROR", e)
            null
        }
    }

    // ─── Mood & Tone Analysis ─────────────────────────────────────────────────

    /**
     * Analyze the conversation to detect tone and mood using the LLM.
     * Returns a Pair of (tone, mood). Both can be null if analysis fails.
     */
    suspend fun analyzeToneAndMood(recentMessages: List<MessageEntity>): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            val builtContext = ContextBuilder.build(recentMessages)

            try {
                val raw = executeWithFallback(PRIMARY_MODEL, FALLBACK_MODEL) { model ->
                    val request = HuggingFaceChatRequest(
                        model    = model,
                        messages = listOf(
                            HuggingFaceMessage(role = "system", content = PromptTemplates.TONE_ANALYSIS_SYSTEM),
                            HuggingFaceMessage(role = "user",   content = PromptTemplates.toneAnalysisUserPrompt(builtContext.contextSnippet))
                        ),
                        maxTokens   = 30,
                        temperature = 0.3
                    )
                    getApi().createChatCompletion(request).choices?.firstOrNull()?.message?.content?.trim()
                } ?: return@withContext Pair(null, null)

                val toneMatch = Regex("\"tone\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                val moodMatch = Regex("\"mood\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)

                DebugLogger.logEvent(TAG, "TONE_MOOD_ANALYZED",
                    mapOf("tone" to (toneMatch ?: "null"), "mood" to (moodMatch ?: "null")))
                Pair(toneMatch, moodMatch)
            } catch (e: Exception) {
                DebugLogger.logError(TAG, "TONE_MOOD_ANALYSIS_ERROR", e)
                Pair(null, null)
            }
        }

    /**
     * Legacy single-return tone analyzer for backward compatibility.
     */
    suspend fun analyzeTone(recentMessages: List<MessageEntity>): String? =
        analyzeToneAndMood(recentMessages).first

    // ─── LLM-Based Question Detection ────────────────────────────────────────

    /**
     * Use the LLM to classify whether a message is a question or expects a response.
     */
    suspend fun isQuestionOrExpectsReply(text: String): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false

        try {
            val raw = executeWithFallback(PRIMARY_MODEL, FALLBACK_MODEL) { model ->
                val request = HuggingFaceChatRequest(
                    model    = model,
                    messages = listOf(
                        HuggingFaceMessage(role = "system", content = PromptTemplates.QUESTION_DETECTION_SYSTEM),
                        HuggingFaceMessage(role = "user",   content = PromptTemplates.questionDetectionUserPrompt(text.take(200)))
                    ),
                    maxTokens   = 5,
                    temperature = 0.1
                )
                getApi().createChatCompletion(request).choices?.firstOrNull()?.message?.content?.trim()?.uppercase()
            } ?: return@withContext true // failsafe: assume yes if LLM fails

            val result = raw.startsWith("YES")
            DebugLogger.logEvent(TAG, "QUESTION_DETECTED",
                mapOf("text_preview" to text.take(40), "result" to result))
            result
        } catch (e: Exception) {
            DebugLogger.logError(TAG, "QUESTION_DETECTION_ERROR", e)
            true // failsafe: reply rather than miss a message
        }
    }

    // ─── Retry + Fallback Engine ──────────────────────────────────────────────

    /**
     * Execute [block] with the primary model (2 retries on 429/503).
     * If the primary model exhausts all retries, tries the fallback model once.
     * 401 is NOT retried (auth error — key is wrong, not a transient issue).
     */
    private suspend fun <T> executeWithFallback(
        primaryModel: String,
        fallbackModel: String,
        block: suspend (model: String) -> T
    ): T? {
        // Try primary model with retries
        val primaryResult = executeWithRetry(primaryModel, block)
        if (primaryResult != null) return primaryResult

        // Primary exhausted — try fallback model once
        DebugLogger.logEvent(TAG, "SWITCHING_TO_FALLBACK_MODEL",
            mapOf("primary" to primaryModel, "fallback" to fallbackModel))
        return try {
            block(fallbackModel)
        } catch (e: Exception) {
            DebugLogger.logError(TAG, "FALLBACK_MODEL_FAILED", e,
                mapOf("model" to fallbackModel))
            null
        }
    }

    /**
     * Try [block] with [model] up to 3 times (1 attempt + 2 retries).
     * Backs off exponentially on 429 / 502 / 503.
     * Returns null if all attempts fail; propagates 401 immediately.
     */
    private suspend fun <T> executeWithRetry(
        model: String,
        block: suspend (model: String) -> T
    ): T? {
        var delayMs = 1_000L
        repeat(3) { attempt ->
            try {
                val result = block(model)
                if (attempt > 0) {
                    DebugLogger.logEvent(TAG, "RETRY_SUCCESS",
                        mapOf("model" to model, "attempt" to attempt + 1))
                }
                return result
            } catch (e: HttpException) {
                val code = e.code()
                DebugLogger.logEvent(TAG, "RETRY_HTTP_ERROR",
                    mapOf("model" to model, "code" to code, "attempt" to attempt + 1))
                when (code) {
                    401 -> {
                        // Auth error — no point retrying with the same key
                        DebugLogger.logError(TAG, "AUTH_ERROR_KEY_INVALID", e)
                        return null
                    }
                    429, 502, 503 -> {
                        // Rate limit or server overload — back off and retry
                        if (attempt < 2) {
                            DebugLogger.logEvent(TAG, "BACKOFF_WAIT",
                                mapOf("model" to model, "delay_ms" to delayMs))
                            delay(delayMs)
                            delayMs *= 3
                        }
                    }
                    else -> {
                        // Unknown HTTP error — give up on this model
                        DebugLogger.logError(TAG, "HTTP_ERROR_UNRETRIABLE", e,
                            mapOf("model" to model, "code" to code))
                        return null
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logEvent(TAG, "RETRY_GENERIC_ERROR",
                    mapOf("model" to model, "attempt" to attempt + 1, "error" to e.message))
                if (attempt < 2) {
                    delay(delayMs)
                    delayMs *= 3
                }
            }
        }
        DebugLogger.logEvent(TAG, "MODEL_ALL_RETRIES_FAILED", mapOf("model" to model))
        return null
    }

    // ─── Reply Sanitization ───────────────────────────────────────────────────

    private fun cleanReply(text: String?): String? {
        if (text == null) return null
        var cleaned = text

        // Remove model artifact tags
        cleaned = cleaned.replace("[/INST]", "",  ignoreCase = true)
        cleaned = cleaned.replace("</s>",   "",   ignoreCase = true)
        cleaned = cleaned.replace("<s>",    "",   ignoreCase = true)
        cleaned = cleaned.replace("[ME]",   "",   ignoreCase = true)
        cleaned = cleaned.replace("[THEM]", "",   ignoreCase = true)

        // Remove markdown formatting
        cleaned = cleaned.replace(Regex("\\*+"), "")
        cleaned = cleaned.replace(Regex("_+"),   "")

        // Strip leading "Reply:" or "My reply:" if model hallucinated it
        cleaned = cleaned.replace(Regex("^(my reply:|reply:)\\s*", RegexOption.IGNORE_CASE), "")

        // Trim whitespace and extra quotes
        cleaned = cleaned.trim('"', '\'', ' ', '\n', '\r')

        // Block AI self-identification phrases
        val lower = cleaned.lowercase()
        if (lower.contains("as an ai")       ||
            lower.contains("i'm an ai")       ||
            lower.contains("i am an ai")      ||
            lower.contains("language model")  ||
            lower.contains("i cannot help")   ||
            lower.contains("i can't help")) {
            DebugLogger.logEvent(TAG, "REPLY_BLOCKED_AI_DISCLAIMER")
            return null
        }

        // Length validation (min 2 chars, max 500)
        if (cleaned.length < 2 || cleaned.length > 500) {
            DebugLogger.logEvent(TAG, "REPLY_BLOCKED_LENGTH", mapOf("length" to cleaned.length))
            return null
        }

        return cleaned.ifBlank { null }
    }
}
