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
): YandexLlmModelsApiAction<YandexFoundationModelsResponse> {
    override fun toRequest() = Request(Method.POST, "/foundationModels/v1/completion")
        .body(
            yandexFoundationModelsRequest {
                modelUri = "gpt://$folderId/$modelVersion"
                stream = false
                temperature = 1.0f
                maxTokens = 2000
                reasoningEnabled = false
                requestMessages += messages.map { YandexFoundationModelsMutableMessage(it.role, it.text) }
            }.let { YandexLlmModelsApiAction.toJsonString(it) }
        )

    override fun toResult(response: Response): Result4k<YandexFoundationModelsResponse, RemoteRequestFailed> = when(response.status) {
        Status.OK -> Success(YandexLlmModelsApiAction.jsonTo(response.body))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}
