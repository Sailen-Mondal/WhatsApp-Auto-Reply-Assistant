package com.whatsappautoreply.data.remote.llm

import com.google.gson.annotations.SerializedName

data class HuggingFaceChatRequest(
    val model: String,
    val messages: List<HuggingFaceMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 120,
    val temperature: Double = 0.6
)

data class HuggingFaceMessage(
    val role: String,
    val content: String
)

data class HuggingFaceChatResponse(
    val id: String?,
    val choices: List<HuggingFaceChoice>?,
    val created: Long?,
    val model: String?,
    val usage: HuggingFaceUsage?
)

data class HuggingFaceChoice(
    val index: Int?,
    val message: HuggingFaceMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class HuggingFaceUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)
