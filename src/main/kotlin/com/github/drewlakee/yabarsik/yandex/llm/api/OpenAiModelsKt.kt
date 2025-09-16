package com.github.drewlakee.yabarsik.yandex.llm.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.drewlakee.yabarsik.yandex.llm.api.OpenAiModelsRequest.Message.Content

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
            val text: String?,
            @get:JsonProperty("image_url") val imageUrl: ImageUrl?,
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

data class MutableOpenAiModelsRequest(
    var model: String = "",
    var requestTemperature: Float = 0.3f,
    var requestMessages: List<MutableOpenAiModelsMessage> = mutableListOf(),
)

data class MutableOpenAiModelsMessage(
    var role: CommonLlmMessageRole,
    val content: List<MutableOpenAiModelsMessageContent>,
)

data class MutableOpenAiModelsMessageContent(
    val type: String,
    val text: String? = null,
    val imageUrl: String? = null,
)

fun openAiModelsRequest(builderAction: MutableOpenAiModelsRequest.() -> Unit): OpenAiModelsRequest {
    val builder = MutableOpenAiModelsRequest().apply(builderAction)
    return OpenAiModelsRequest(
        model = builder.model,
        temperature = builder.requestTemperature,
        messages = builder.requestMessages.map {
            OpenAiModelsRequest.Message(
                role = it.role,
                content = it.content.map {
                    Content(
                        type = it.type,
                        text = it.text,
                        imageUrl = it.imageUrl?.let { Content.ImageUrl(it) }
                    )
                },
            )
        }
    )
}