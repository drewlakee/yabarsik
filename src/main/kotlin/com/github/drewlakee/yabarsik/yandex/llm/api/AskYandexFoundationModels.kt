package com.github.drewlakee.yabarsik.yandex.llm.api

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

data class AskYandexFoundationModelsMessage(
    val role: CommonLlmMessageRole,
    val text: String,
)

data class AskYandexFoundationModels(
    val folderId: String,
    val modelVersion: String,
    val messages: List<AskYandexFoundationModelsMessage>,
    val temperature: Float? = null,
): YandexLlmModelsApiAction<YandexFoundationModelsResponse> {
    override fun toRequest() = Request(Method.POST, "/foundationModels/v1/completion")
        .body(
            YandexFoundationModelsRequest(
                modelUri = "gpt://$folderId/$modelVersion",
                completionOptions = YandexFoundationModelsRequest.CompletionOptions(
                    stream = false,
                    temperature = temperature ?: 0.3f,
                    maxTokens = 2000,
                    reasoningOptions = YandexFoundationModelsRequest.CompletionOptions.ReasoningOptions(
                        mode = if (false) {
                            YandexFoundationModelsRequest.CompletionOptions.ReasoningOptions.Mode.ENABLED
                        } else {
                            YandexFoundationModelsRequest.CompletionOptions.ReasoningOptions.Mode.DISABLED
                        }
                    )
                ),
                messages = messages.map {
                    YandexFoundationModelsRequest.YandexFoundationModelsMessage(
                        role = it.role,
                        text = it.text
                    )
                }
            ).let { YandexLlmModelsApiAction.toJsonString(it) }
        )

    override fun toResult(response: Response): Result4k<YandexFoundationModelsResponse, RemoteRequestFailed> = when(response.status) {
        Status.OK -> Success(YandexLlmModelsApiAction.jsonTo(response.body))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}
