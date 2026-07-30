package com.whatsappautoreply.data.remote.llm.provider

import com.google.gson.Gson
import com.whatsappautoreply.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiCompatibleProvider(
    override val name: String,
    override val modelName: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient,
    private val gson: Gson,
    private val extraParams: Map<String, Any>? = null
) : LlmProvider {

    override suspend fun generateCompletion(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String? = withContext(Dispatchers.IO) {
        val requestBodyMap = mutableMapOf<String, Any>(
            "model" to modelName,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            ),
            "max_tokens" to maxTokens,
            "temperature" to temperature
        )
        
        extraParams?.let { requestBodyMap.putAll(it) }

        val jsonBody = gson.toJson(requestBodyMap)
        val request = Request.Builder()
            .url(baseUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            // OpenRouter specific headers (ignored by others safely)
            .header("HTTP-Referer", "https://github.com/whatsapp-auto-reply")
            .header("X-Title", "WhatsApp Auto Reply")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("HTTP ${response.code}: $errorBody")
            }

            val responseString = response.body?.string() ?: return@withContext null
            
            // Parse OpenAI-compatible response
            val root = gson.fromJson(responseString, Map::class.java)
            val choices = root["choices"] as? List<*>
            val firstChoice = choices?.firstOrNull() as? Map<*, *>
            val message = firstChoice?.get("message") as? Map<*, *>
            message?.get("content") as? String
        }
    }

    override suspend fun checkHealth(): Boolean {
        return try {
            generateCompletion("health check", "ping", maxTokens = 5) != null
        } catch (e: Exception) {
            DebugLogger.logError("LlmProvider", "$name health check failed", e)
            false
        }
    }
}
