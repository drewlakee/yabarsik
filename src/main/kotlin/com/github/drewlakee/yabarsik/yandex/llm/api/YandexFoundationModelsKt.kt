package com.github.drewlakee.yabarsik.yandex.llm.api

data class YandexFoundationModelsRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<YandexFoundationModelsMessage>,
) {
    data class CompletionOptions(
        val stream: Boolean,
        val temperature: Float,
        val maxTokens: Int,
        val reasoningOptions: ReasoningOptions,
    ) {
        data class ReasoningOptions(val mode: Mode) {
            enum class Mode {
                DISABLED,
                ENABLED,
            }
        }
    }

    data class YandexFoundationModelsMessage(
        val role: CommonLlmMessageRole,
        val text: String,
    )
}

class YandexLlmRequestBuilder(
    var modelUri: String? = null,
    var stream: Boolean = false,
    var requestTemperature: Float = 0.6f,
    var maxTokens: Int = 2000,
    var reasoningEnabled: Boolean = false,
    var requestMessages: MutableList<YandexFoundationModelsMutableMessage> = mutableListOf(),
)

class YandexFoundationModelsMutableMessage(
    var role: CommonLlmMessageRole,
    var text: String,
)

data class YandexFoundationModelsResponse(
    val result: Result,
) {
    data class Result(
        val modelVersion: String,
        val alternatives: List<Alternative>,
        val usage: Usage,
    ) {
        data class Alternative(
            val message: Message,
            val status: String,
        ) {
            data class Message(
                val role: CommonLlmMessageRole,
                val text: String,
            )
        }

        data class Usage(
            val inputTextTokens: String,
            val completionTokens: String,
            val totalTokens: String,
            val completionTokensDetails: CompletionTokensDetails?,
        ) {
            data class CompletionTokensDetails(val reasoningTokens: String)
        }
    }
}

fun yandexFoundationModelsRequest(builderAction: YandexLlmRequestBuilder.() -> Unit): YandexFoundationModelsRequest {
    val builder = YandexLlmRequestBuilder()
    builderAction(builder)
    return YandexFoundationModelsRequest(
        modelUri = builder.modelUri!!,
        completionOptions = YandexFoundationModelsRequest.CompletionOptions(
            stream = builder.stream,
            temperature = builder.requestTemperature,
            maxTokens = builder.maxTokens,
            reasoningOptions = YandexFoundationModelsRequest.CompletionOptions.ReasoningOptions(
                mode = if (builder.reasoningEnabled) {
                    YandexFoundationModelsRequest.CompletionOptions.ReasoningOptions.Mode.ENABLED
                } else {
                    YandexFoundationModelsRequest.CompletionOptions.ReasoningOptions.Mode.DISABLED
                }
            )
        ),
        messages = builder.requestMessages.map {
            YandexFoundationModelsRequest.YandexFoundationModelsMessage(
                role = it.role,
                text = it.text
            )
        }
    )
}