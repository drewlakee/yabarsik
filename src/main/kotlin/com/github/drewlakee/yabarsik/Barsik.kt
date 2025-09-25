package com.github.drewlakee.yabarsik

import com.github.drewlakee.yabarsik.configuration.BarsikConfiguration
import com.github.drewlakee.yabarsik.configuration.Llm
import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.discogs.api.getArtistReleases
import com.github.drewlakee.yabarsik.images.GetImage
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.vk.api.GetGroups
import com.github.drewlakee.yabarsik.vk.api.GetUsers
import com.github.drewlakee.yabarsik.vk.api.PostWallpost
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.VkPostWallpostAttachment
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType
import com.github.drewlakee.yabarsik.vk.api.getTodayWallpost
import com.github.drewlakee.yabarsik.vk.api.takeAttachmentsRandomly
import com.github.drewlakee.yabarsik.yandex.llm.api.AskOpenAiModels
import com.github.drewlakee.yabarsik.yandex.llm.api.AskOpenAiModelsImageUrlContent
import com.github.drewlakee.yabarsik.yandex.llm.api.AskOpenAiModelsMessage
import com.github.drewlakee.yabarsik.yandex.llm.api.AskOpenAiModelsTextContent
import com.github.drewlakee.yabarsik.yandex.llm.api.AskYandexFoundationModels
import com.github.drewlakee.yabarsik.yandex.llm.api.AskYandexFoundationModelsMessage
import com.github.drewlakee.yabarsik.yandex.llm.api.CommonLlmMessageRole
import com.github.drewlakee.yabarsik.yandex.llm.api.YandexLlmModelsApi
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.peekFailure
import org.http4k.cloudnative.RemoteRequestFailed
import yandex.cloud.sdk.functions.Context
import java.time.LocalDate
import java.time.ZoneId

class Barsik(
    private val context: Context,
    private val telegramApi: TelegramApi,
    private val yandexLlmModelsApi: YandexLlmModelsApi,
    private val vkApi: VkApi,
    private val imagesApi: ImagesApi,
    private val discogsApi: DiscogsApi,
    yandexS3Api: YandexS3Api,
) {
    val configuration: BarsikConfiguration = yandexS3Api
            .getBarsikConfiguration()
            .peekFailure { logError(it) }
            .orThrow()
    
    fun getImage(url: String) = 
        imagesApi.invoke(
            GetImage(
                url = url
            )
        )

    fun getTraceLink() = "https://console.yandex.cloud/folders/${configuration.cloud.function.folderId}/functions/functions/" +
        "${context.functionId}/logs?from=now-1h&to=now&size=100&linesInRow=1&resourceTypes=serverless.function" +
        "&resourceIds=${context.functionId}&query=request_id+%3D+%22${context.requestId}%22"

    fun getTelegramFormattedTraceLinK() = "[Следы Барсика в облаке](${getTraceLink()})"

    fun sendTelegramMessage(message: String) =
        telegramApi.sendMessage(
            chatId = configuration.telegram.report.chatId,
            message = message,

        )

    fun getVkUsers(userIds: List<Int>) = vkApi.invoke(
        GetUsers(
            userIds = userIds,
        )
    )
    
    fun getVkGroups(groupsIds: List<Int>) = vkApi.invoke(
        GetGroups(
            groupIds = groupsIds,
        )
    )

    fun getVkTodayWallposts(domain: String, today: LocalDate, zone: ZoneId) =
        vkApi.getTodayWallpost(domain, today, zone)

    fun takeVkAttachmentsRandomly(
        domain: String,
        count: Int,
        type: VkWallpostsAttachmentType,
        domainWallpostsCount: Int? = null,
    ) = vkApi.takeAttachmentsRandomly(
        domain = domain,
        count = count,
        type = type,
        domainWallpostsCount = domainWallpostsCount,
    )

    fun getArtistReleases(
        artist: String,
        track: String? = null,
    ) = discogsApi.getArtistReleases(
        artist = artist,
        track = track,
    )

    
    fun createVkWallpost(attachments: List<VkPostWallpostAttachment>, publishDate: Long? = null) = vkApi.invoke(
        PostWallpost(
            ownerId = configuration.wallposts.communityId,
            attachments = attachments,
            publishDate = publishDate,
        )
    )

    fun askTextGpt(
        temperature: Float,
        messages: List<BarsikGptMessage>,
    ): Result4k<SimpleGptResponse, RemoteRequestFailed> =
        when (configuration.llm.textGtp.api) {
            Llm.Api.YANDEX ->
                yandexLlmModelsApi
                    .invoke(
                        AskYandexFoundationModels(
                            folderId = configuration.llm.folderId,
                            temperature = temperature,
                            modelVersion = configuration.llm.textGtp.model,
                            messages =
                                messages
                                    .asSequence()
                                    .filterIsInstance<BarsikGptTextMessage>()
                                    .map {
                                        AskYandexFoundationModelsMessage(
                                            role = it.role,
                                            text = it.text,
                                        )
                                    }.toList(),
                        ),
                    ).map {
                        it.result.alternatives
                            .asSequence()
                            .filter { it.message.role == CommonLlmMessageRole.ASSISTANT }
                            .firstOrNull()
                            ?.let {
                                SimpleGptResponse(
                                    answer = it.message.text,
                                )
                            } ?: SimpleGptResponse("There is none actual answer from ${CommonLlmMessageRole.ASSISTANT}")
                    }
            Llm.Api.OPENAI ->
                yandexLlmModelsApi
                    .invoke(
                        AskOpenAiModels(
                            folderId = configuration.llm.folderId,
                            modelVersion = configuration.llm.textGtp.model,
                            temperature = temperature,
                            messages =
                                buildList {
                                    messages
                                        .groupBy { it.role() }
                                        .entries
                                        .map { (role, messages) ->
                                            AskOpenAiModelsMessage(
                                                role = role,
                                                content =
                                                    messages
                                                        .asSequence()
                                                        .filterIsInstance<BarsikGptTextMessage>()
                                                        .map {
                                                            AskOpenAiModelsTextContent(
                                                                text = it.text,
                                                            )
                                                        }.toList(),
                                            )
                                        }.forEach { add(it) }
                                },
                        ),
                    ).map {
                        it.choices
                            .asSequence()
                            .filter { it.message.role == CommonLlmMessageRole.ASSISTANT }
                            .firstOrNull()
                            ?.let {
                                SimpleGptResponse(
                                    answer = it.message.content,
                                )
                            } ?: SimpleGptResponse("There is none actual answer from ${CommonLlmMessageRole.ASSISTANT}")
                    }
        }

    fun askMultiModalGpt(
        temperature: Float,
        messages: List<BarsikGptMessage>,
    ): Result4k<SimpleGptResponse, RemoteRequestFailed> =
        when (configuration.llm.textGtp.api) {
            Llm.Api.YANDEX ->
                yandexLlmModelsApi
                    .invoke(
                        AskYandexFoundationModels(
                            folderId = configuration.llm.folderId,
                            modelVersion = configuration.llm.multiModalGpt.model,
                            temperature = temperature,
                            messages =
                                messages
                                    .asSequence()
                                    .filterIsInstance<BarsikGptTextMessage>()
                                    .map {
                                        AskYandexFoundationModelsMessage(
                                            role = it.role,
                                            text = it.text,
                                        )
                                    }.toList(),
                        ),
                    ).map {
                        it.result.alternatives
                            .asSequence()
                            .filter { it.message.role == CommonLlmMessageRole.ASSISTANT }
                            .firstOrNull()
                            ?.let {
                                SimpleGptResponse(
                                    answer = it.message.text,
                                )
                            } ?: SimpleGptResponse("There is none actual answer from ${CommonLlmMessageRole.ASSISTANT}")
                    }
            Llm.Api.OPENAI ->
                yandexLlmModelsApi
                    .invoke(
                        AskOpenAiModels(
                            folderId = configuration.llm.folderId,
                            modelVersion = configuration.llm.multiModalGpt.model,
                            temperature = temperature,
                            messages =
                                buildList {
                                    messages
                                        .groupBy { it.role() }
                                        .entries
                                        .map { (role, messages) ->
                                            AskOpenAiModelsMessage(
                                                role = role,
                                                content =
                                                    messages
                                                        .asSequence()
                                                        .map {
                                                            when (it) {
                                                                is BarsikGptTextMessage ->
                                                                    AskOpenAiModelsTextContent(
                                                                        text = it.text,
                                                                    )
                                                                is BarsilGptImageUrlMessage ->
                                                                    AskOpenAiModelsImageUrlContent(
                                                                        url = it.url,
                                                                    )
                                                            }
                                                        }.toList(),
                                            )
                                        }.forEach { add(it) }
                                },
                        ),
                    ).map {
                        it.choices
                            .asSequence()
                            .filter { it.message.role == CommonLlmMessageRole.ASSISTANT }
                            .firstOrNull()
                            ?.let {
                                SimpleGptResponse(
                                    answer = it.message.content,
                                )
                            } ?: SimpleGptResponse("There is none actual answer from ${CommonLlmMessageRole.ASSISTANT}")
                    }
        }
}

data class SimpleGptResponse(
    val answer: String,
)

sealed interface BarsikGptMessage {
    fun role(): CommonLlmMessageRole
}

data class BarsikGptTextMessage(
    val role: CommonLlmMessageRole,
    val text: String,
) : BarsikGptMessage {
    override fun role() = role
}

data class BarsilGptImageUrlMessage(
    val role: CommonLlmMessageRole,
    val url: String,
) : BarsikGptMessage {
    override fun role() = role
}
