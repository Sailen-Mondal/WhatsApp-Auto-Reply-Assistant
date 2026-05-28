package com.whatsappautoreply.data.remote.llm

import retrofit2.http.Body
import retrofit2.http.POST

interface HuggingFaceApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body request: HuggingFaceChatRequest
    ): HuggingFaceChatResponse
}
