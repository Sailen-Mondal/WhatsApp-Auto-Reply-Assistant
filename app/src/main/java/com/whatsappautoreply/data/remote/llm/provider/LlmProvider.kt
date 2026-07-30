package com.whatsappautoreply.data.remote.llm.provider

interface LlmProvider {
    val name: String
    val modelName: String

    @Throws(Exception::class)
    suspend fun generateCompletion(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 5000,
        temperature: Double = 1.5
    ): String?
    
    @Throws(Exception::class)
    suspend fun checkHealth(): Boolean
}
