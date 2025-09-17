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