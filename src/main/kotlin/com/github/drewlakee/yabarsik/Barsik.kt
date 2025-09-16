package com.github.drewlakee.yabarsik

import com.github.drewlakee.yabarsik.configuration.BarsikConfiguration
import com.github.drewlakee.yabarsik.configuration.Llm
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType
import com.github.drewlakee.yabarsik.vk.api.takeAttachmentsRandomly
import com.github.drewlakee.yabarsik.yandex.llm.api.AskOpenAiModels
import com.github.drewlakee.yabarsik.yandex.llm.api.AskOpenAiModelsImageUrlContent
import com.github.drewlakee.yabarsik.yandex.llm.api.AskYandexFoundationModels
import com.github.drewlakee.yabarsik.yandex.llm.api.AskYandexFoundationModelsMessage
import com.github.drewlakee.yabarsik.yandex.llm.api.CommonLlmMessageRole
import com.github.drewlakee.yabarsik.yandex.llm.api.AskOpenAiModelsMessage
import com.github.drewlakee.yabarsik.yandex.llm.api.AskOpenAiModelsTextContent
import com.github.drewlakee.yabarsik.yandex.llm.api.YandexLlmModelsApi
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.peekFailure
import org.http4k.cloudnative.RemoteRequestFailed

class Barsik(
    private val telegramApi: TelegramApi,
    private val yandexS3Api: YandexS3Api,
    private val yandexLlmModelsApi: YandexLlmModelsApi,
    private val vkApi: VkApi,
) {
    val configuration: BarsikConfiguration = yandexS3Api.getBarsikConfiguration()
        .peekFailure { logError(it) }
        .orThrow()

    companion object {
        fun Barsik.sendTelegramMessage(message: String) = telegramApi.sendMessage(
            chatId = configuration.telegram.report.chatId,
            message = message
        )

        fun Barsik.takeVkAttachmentsRandomly(domain: String, count: Int, type: VkWallpostsAttachmentType) =
            vkApi.takeAttachmentsRandomly(
                domain = domain,
                count = count,
                type = type,
            )

        fun Barsik.askTextGpt(temperature: Float, messages: List<BarsikGptMessage>): Result4k<SimpleGptResponse, RemoteRequestFailed> {
            return when (configuration.llm.textGtp.api) {
                Llm.Api.YANDEX -> yandexLlmModelsApi.invoke(
                    AskYandexFoundationModels(
                        folderId = configuration.llm.folderId,
                        temperature = temperature,
                        modelVersion = configuration.llm.textGtp.model,
                        messages = messages.asSequence()
                            .filterIsInstance<BarsikGptTextMessage>()
                            .map {
                                AskYandexFoundationModelsMessage(
                                    role = it.role,
                                    text = it.text,
                                )
                            }
                            .toList()
                    )
                ).map {
                    it.result.alternatives.asSequence()
                        .filter { it.message.role == CommonLlmMessageRole.ASSISTANT }
                        .firstOrNull()
                        ?.let {
                            SimpleGptResponse(
                                answer = it.message.text
                            )
                        } ?: SimpleGptResponse("There is none actual answer from ${CommonLlmMessageRole.ASSISTANT}")
                }
                Llm.Api.OPENAI -> yandexLlmModelsApi.invoke(
                    AskOpenAiModels(
                        folderId = configuration.llm.folderId,
                        modelVersion = configuration.llm.textGtp.model,
                        messages = buildList { 
                            messages.groupBy { it.role() }
                                .entries
                                .map { (role, messages) ->
                                    AskOpenAiModelsMessage(
                                        role = role,
                                        content = messages.asSequence()
                                            .filterIsInstance<BarsikGptTextMessage>()
                                            .map {
                                                AskOpenAiModelsTextContent(
                                                    text = it.text
                                                )
                                            }
                                            .toList()
                                    )
                                }.forEach { add(it) }
                        }
                    )
                ).map {
                    it.choices.asSequence()
                        .filter { it.message.role == CommonLlmMessageRole.ASSISTANT }
                        .firstOrNull()
                        ?.let {
                            SimpleGptResponse(
                                answer = it.message.content
                            )
                        } ?: SimpleGptResponse("There is none actual answer from ${CommonLlmMessageRole.ASSISTANT}")
                }
            }
        }

        fun Barsik.askMultiModalGpt(temperature: Float, messages: List<BarsikGptMessage>): Result4k<SimpleGptResponse, RemoteRequestFailed> {
            return when (configuration.llm.textGtp.api) {
                Llm.Api.YANDEX -> yandexLlmModelsApi.invoke(
                    AskYandexFoundationModels(
                        folderId = configuration.llm.folderId,
                        modelVersion = configuration.llm.multiModalGpt.model,
                        temperature = temperature,
                        messages = messages.asSequence()
                            .filterIsInstance<BarsikGptTextMessage>()
                            .map {
                                AskYandexFoundationModelsMessage(
                                    role = it.role,
                                    text = it.text,
                                )
                            }
                            .toList()
                    )
                ).map {
                    it.result.alternatives.asSequence()
                        .filter { it.message.role == CommonLlmMessageRole.ASSISTANT }
                        .firstOrNull()
                        ?.let {
                            SimpleGptResponse(
                                answer = it.message.text
                            )
                        } ?: SimpleGptResponse("There is none actual answer from ${CommonLlmMessageRole.ASSISTANT}")
                }
                Llm.Api.OPENAI -> yandexLlmModelsApi.invoke(
                    AskOpenAiModels(
                        folderId = configuration.llm.folderId,
                        modelVersion = configuration.llm.multiModalGpt.model,
                        temperature = temperature,
                        messages = buildList {
                            messages.groupBy { it.role() }
                                .entries
                                .map { (role, messages) ->
                                    AskOpenAiModelsMessage(
                                        role = role,
                                        content = messages.asSequence()
                                            .map {
                                                when (it) {
                                                    is BarsikGptTextMessage -> AskOpenAiModelsTextContent(
                                                        text = it.text
                                                    )
                                                    is BarsilGptImageUrlMessage -> AskOpenAiModelsImageUrlContent(
                                                        url = it.url
                                                    )
                                                }
                                            }
                                            .toList()
                                    )
                                }.forEach { add(it) }
                        }
                    )
                ).map {
                    it.choices.asSequence()
                        .filter { it.message.role == CommonLlmMessageRole.ASSISTANT }
                        .firstOrNull()
                        ?.let {
                            SimpleGptResponse(
                                answer = it.message.content
                            )
                        } ?: SimpleGptResponse("There is none actual answer from ${CommonLlmMessageRole.ASSISTANT}")
                }
            }
        }
    }
}

data class SimpleGptResponse(val answer: String)

sealed interface BarsikGptMessage {
    fun role(): CommonLlmMessageRole
}

data class BarsikGptTextMessage(val role: CommonLlmMessageRole, val text: String): BarsikGptMessage {
    override fun role() = role
}

data class BarsilGptImageUrlMessage(val role: CommonLlmMessageRole, val url: String): BarsikGptMessage {
    override fun role() = role
}