package com.github.drewlakee.yabarsik.yandex.llm.api

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

data class AskGptMessage(
    val role: LlmMessageRole,
    val text: String,
)

data class AskGptLlmAnswer(
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
                val role: LlmMessageRole,
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

data class AskGpt(
    val folderId: String,
    val modelVersion: String,
    val messages: List<AskGptMessage>,
): YandexLlmModelsApiAction<AskGptLlmAnswer> {
    override fun toRequest() = Request(Method.POST, "/foundationModels/v1/completion")
        .body(
            yandexLllmRequest {
                modelUri = "gpt://$folderId/$modelVersion"
                stream = false
                temperature = 0.6f
                maxTokens = 2000
                reasoningEnabled = false
                messages.forEach { requestMessages += MutableMessage(it.role, it.text) }
            }.let { YandexLlmModelsApiAction.toJsonString(it) }
        )

    override fun toResult(response: Response): Result4k<AskGptLlmAnswer, RemoteRequestFailed> = when(response.status) {
        Status.OK -> Success(YandexLlmModelsApiAction.jsonTo(response.body))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}
