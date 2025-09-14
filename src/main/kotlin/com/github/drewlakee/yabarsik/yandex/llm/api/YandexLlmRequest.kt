package com.github.drewlakee.yabarsik.yandex.llm.api

data class YandexLlmRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<LlmMesssage>,
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

    data class LlmMesssage(
        val role: LlmMessageRole,
        val text: String,
    )
}

class YandexLlmRequestBuilder(
    var modelUri: String? = null,
    var stream: Boolean = false,
    var temperature: Float = 0.6f,
    var maxTokens: Int = 2000,
    var reasoningEnabled: Boolean = false,
    var requestMessages: MutableList<MutableMessage> = mutableListOf(),
)

class MutableMessage(
    var role: LlmMessageRole,
    var text: String,
)

fun yandexLllmRequest(builderAction: YandexLlmRequestBuilder.() -> Unit): YandexLlmRequest {
    val builder = YandexLlmRequestBuilder()
    builderAction(builder)
    return YandexLlmRequest(
        modelUri = builder.modelUri!!,
        completionOptions = YandexLlmRequest.CompletionOptions(
            stream = builder.stream,
            temperature = builder.temperature,
            maxTokens = builder.maxTokens,
            reasoningOptions = YandexLlmRequest.CompletionOptions.ReasoningOptions(
                mode = if (builder.reasoningEnabled) {
                    YandexLlmRequest.CompletionOptions.ReasoningOptions.Mode.ENABLED
                } else {
                    YandexLlmRequest.CompletionOptions.ReasoningOptions.Mode.DISABLED
                }
            )
        ),
        messages = builder.requestMessages.map {
            YandexLlmRequest.LlmMesssage(
                role = it.role,
                text = it.text
            )
        }
    )
}