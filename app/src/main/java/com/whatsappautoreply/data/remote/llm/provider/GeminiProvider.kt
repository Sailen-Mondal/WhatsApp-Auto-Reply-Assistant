package com.whatsappautoreply.data.remote.llm.provider

import com.google.gson.Gson
import com.whatsappautoreply.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiProvider(
    private val apiKey: String,
    private val client: OkHttpClient,
    private val gson: Gson
) : LlmProvider {
    override val name: String = "Gemini"
    override val modelName: String = "gemini-2.5-flash"

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    override suspend fun generateCompletion(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/$modelName:generateContent?key=$apiKey"

        val requestBodyMap = mapOf(
            "system_instruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemPrompt))
            ),
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to userPrompt))
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to temperature,
                "maxOutputTokens" to maxTokens
            )
        )

        val jsonBody = gson.toJson(requestBodyMap)
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("HTTP ${response.code}: $errorBody")
            }

            val responseString = response.body?.string() ?: return@withContext null
            
            // Parse Gemini response
            val root = gson.fromJson(responseString, Map::class.java)
            val candidates = root["candidates"] as? List<*>
            val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
            val content = firstCandidate?.get("content") as? Map<*, *>
            val parts = content?.get("parts") as? List<*>
            val firstPart = parts?.firstOrNull() as? Map<*, *>
            firstPart?.get("text") as? String
        }
    }

    override suspend fun checkHealth(): Boolean {
        return try {
            generateCompletion("health check", "ping", maxTokens = 5) != null
        } catch (e: Exception) {
            DebugLogger.logError("LlmProvider", "Gemini health check failed", e)
            false
        }
    }
}
