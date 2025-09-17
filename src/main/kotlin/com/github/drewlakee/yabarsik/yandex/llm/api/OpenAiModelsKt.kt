package com.github.drewlakee.yabarsik.yandex.llm.api

import com.fasterxml.jackson.annotation.JsonProperty

data class OpenAiModelsRequest(
    val model: String,
    val temperature: Float,
    val messages: List<Message>,
) {
    data class Message(
        val role: CommonLlmMessageRole,
        val content: List<Content>,
    ) {
        data class Content(
            val type: String,
            val text: String? = null,
            @get:JsonProperty("image_url") val imageUrl: ImageUrl? = null,
        ) {
            data class ImageUrl(val url: String)
        }
    }
}

data class OpenAiModelsResponse(
    val id: String,
    @field:JsonProperty("object") val obj: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage,
) {
    data class Choice(
        val index: Int,
        val message: Message,
        @field:JsonProperty("finish_reason") val finishReason: String,
    ) {
        data class Message(
            val role: CommonLlmMessageRole,
            val content: String,
        )
    }

    data class Usage(
        @field:JsonProperty("prompt_tokens") val promptTokens: Int,
        @field:JsonProperty("total_tokens") val totalTokens: Int,
        @field:JsonProperty("completion_tokens") val completionTokens: Int,
    )
}
