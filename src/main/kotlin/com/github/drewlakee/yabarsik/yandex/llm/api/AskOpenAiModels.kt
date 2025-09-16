package com.github.drewlakee.yabarsik.yandex.llm.api

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

data class AskOpenAiModelsMessage(
    val role: CommonLlmMessageRole,
    val content: List<AskOpenAiModelsMessageContent>,
)

sealed interface AskOpenAiModelsMessageContent {
    fun type(): String
}

data class AskOpenAiModelsTextContent(val text: String): AskOpenAiModelsMessageContent {
    override fun type(): String = "text"
}

data class AskOpenAiModelsImageUrlContent(val url: String): AskOpenAiModelsMessageContent {
    override fun type(): String = "image_url"
}

data class AskOpenAiModels(
    val folderId: String,
    val modelVersion: String,
    val messages: List<AskOpenAiModelsMessage>,
    val temperature: Float? = null,
) : YandexLlmModelsApiAction<OpenAiModelsResponse> {
    override fun toRequest() = Request(Method.POST, "/v1/chat/completions")
        .body(
            openAiModelsRequest {
                model = "gpt://$folderId/${modelVersion}"
                requestTemperature = temperature ?: 0.3f
                requestMessages = messages.map {
                    MutableOpenAiModelsMessage(
                        role = it.role,
                        content = it.content.map {
                            when (it) {
                                is AskOpenAiModelsTextContent -> MutableOpenAiModelsMessageContent(
                                    type = it.type(),
                                    text = it.text,
                                )
                                is AskOpenAiModelsImageUrlContent -> MutableOpenAiModelsMessageContent(
                                    type = it.type(),
                                    imageUrl = it.url,
                                )
                            }
                        }
                    )
                }
            }.let { YandexLlmModelsApiAction.toJsonString(it) }
        )

    override fun toResult(response: Response): Result4k<OpenAiModelsResponse, RemoteRequestFailed> = when(response.status) {
        Status.OK -> Success(YandexLlmModelsApiAction.jsonTo(response.body))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}
