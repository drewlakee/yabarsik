package com.github.drewlakee.yabarsik.scenario.vk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.drewlakee.yabarsik.Barsik
import com.github.drewlakee.yabarsik.BarsikGptTextMessage
import com.github.drewlakee.yabarsik.BarsilGptImageUrlMessage
import com.github.drewlakee.yabarsik.SimpleGptResponse
import com.github.drewlakee.yabarsik.configuration.Content
import com.github.drewlakee.yabarsik.logError
import com.github.drewlakee.yabarsik.scenario.BarsikScenario
import com.github.drewlakee.yabarsik.scenario.BarsikScenarioResult
import com.github.drewlakee.yabarsik.vk.api.VkPostWallpostAttachment
import com.github.drewlakee.yabarsik.vk.api.VkWallposts
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType
import com.github.drewlakee.yabarsik.yandex.llm.api.CommonLlmMessageRole
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.failureOrNull
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.peekFailure
import dev.forkhandles.result4k.recover
import dev.forkhandles.result4k.valueOrNull
import kotlin.random.Random

class DailyScheduleWatchingResult(
    private val success: Boolean,
    private val message: String = "",
    private val sendTelegram: Boolean = false,
) : BarsikScenarioResult {
    override fun isSuccessful(): Boolean = success

    override fun message(): String = message

    override fun sendTelegramMessage(): Boolean = sendTelegram
}

private data class ExpectedAudioPromtResult(
    val result: List<Approval>,
) {
    data class Approval(
        val band: String,
        val approval: Float,
    )
}

private data class ExpectedPhotoPromtResult(
    val result: List<Approval>,
) {
    data class Approval(
        val photo: String,
        val approval: Boolean,
    )
}

class DailyScheduleWatching : BarsikScenario<DailyScheduleWatchingResult> {
    private val llmJsonAnswerMapper =
        ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun play(barsik: Barsik): DailyScheduleWatchingResult {
        val mediaProviders =
            barsik.configuration.content.providers
                .asSequence()
                .flatMap { provider ->
                    provider.media.map { media ->
                        media to provider
                    }
                }.groupBy(
                    keySelector = { (media, _) -> media },
                    valueTransform = { (_, provider) -> provider },
                )

        if (mediaProviders[Content.Provider.Media.MUSIC]?.isEmpty() ?: true ||
            mediaProviders[Content.Provider.Media.IMAGES]?.isEmpty() ?: true
        ) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Хм... ты забыл указать мне источники для контента? Ну ладно, тогда я отдыхаю \\uD83D\\uDE3C\"",
                sendTelegram = true,
            )
        }

        val musicAttachments =
            buildList {
                while (this.size < barsik.configuration.content.settings.musicAttachmentsCollectorSize) {
                    barsik
                        .takeVkAttachmentsRandomly(
                            domain = mediaProviders[Content.Provider.Media.MUSIC]!!.getRandomProvider().domain,
                            count = barsik.configuration.content.settings.takeMusicAttachmentsPerProvider,
                            type = VkWallpostsAttachmentType.AUDIO,
                        ).peekFailure { logError(it.cause) }
                        .recover { mutableListOf() }
                        .let {
                            it.forEach {
                                if (this.size < barsik.configuration.content.settings.musicAttachmentsCollectorSize) {
                                    add(it)
                                }
                            }
                        }
                }
            }

        val photoAttachments =
            buildList {
                while (this.size < barsik.configuration.content.settings.imagesAttachmentsCollectorSize) {
                    barsik
                        .takeVkAttachmentsRandomly(
                            domain = mediaProviders[Content.Provider.Media.IMAGES]!!.getRandomProvider().domain,
                            count = barsik.configuration.content.settings.takeImagesAttachmentsPerProvider,
                            type = VkWallpostsAttachmentType.PHOTO,
                        ).peekFailure { logError(it.cause) }
                        .recover { mutableListOf() }
                        .let {
                            it.forEach {
                                if (this.size < barsik.configuration.content.settings.imagesAttachmentsCollectorSize) {
                                    add(it)
                                }
                            }
                        }
                }
            }

        if (musicAttachments.isEmpty()) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Эх, что-то у меня не вышло найти музычку Вконтакте, попробую попозже еще раз...",
                sendTelegram = true,
            )
        }

        if (photoAttachments.isEmpty()) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Так, блин, котята разбежались, что-то у меня не вышло никого найти, попробую попозже что ли...",
                sendTelegram = true,
            )
        }

        val llmAudioResponse =
            barsik.askTextGpt(
                temperature = barsik.configuration.llm.audioPromt.temperature,
                messages =
                    listOf(
                        BarsikGptTextMessage(
                            role = CommonLlmMessageRole.SYSTEM,
                            text = barsik.configuration.llm.audioPromt.systemInstruction,
                        ),
                        BarsikGptTextMessage(
                            role = CommonLlmMessageRole.USER,
                            text = musicAttachments.groupBy { it.audio!!.artist }.entries.joinToString(separator = ", ") { (artist, _) -> artist },
                        ),
                    ),
            )

        if (llmAudioResponse.failureOrNull() != null) {
            logError(llmAudioResponse.failureOrNull()!!.cause)
            return DailyScheduleWatchingResult(
                success = false,
                message = "Мне почему-то llm не отвечает по музыке... постучусь позже",
                sendTelegram = true,
            )
        }

        val llmAudioResult = llmAudioResponse.orThrow().toExpectedAudioPromtResult()
        if (llmAudioResult.failureOrNull() != null) {
            return DailyScheduleWatchingResult(
                success = false,
                message = """
                LLM то ненастоящий, как-то неожидаемо ответил насчет музыки, возможно, галлюцинация, но я не понял... в общем в другой раз еще раз попробую. 
                
                Вот, что мне отдал:
                ${llmAudioResult.failureOrNull()}
            """.trimIndent(),
                sendTelegram = true,
            )
        }

        val downloadedImages = photoAttachments.asSequence()
            .map {
                it to barsik.getImage(url = it.photo!!.origPhoto?.url ?: it.photo.sizes.last().url)
            }
            .filter { it.second.failureOrNull() == null }
            .map { it.first to it.second.valueOrNull()!! }
            .toList()

        if (downloadedImages.isEmpty()) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Все подобранные фотографии не получилось загрузить из интернета... надо проверить будет в чем проблема!",
                sendTelegram = true
            )
        }

        val llmPhotoResponse = downloadedImages.windowed(3, 3, partialWindows = true).map { windowed ->
            barsik.askMultiModalGpt(
                temperature = barsik.configuration.llm.photoPromt.temperature,
                messages =
                    buildList {
                        add(
                            BarsikGptTextMessage(
                                role = CommonLlmMessageRole.SYSTEM,
                                text = barsik.configuration.llm.photoPromt.systemInstruction,
                            ),
                        )

                        windowed.forEach { (attachment, image) ->
                            add(
                                BarsikGptTextMessage(
                                    role = CommonLlmMessageRole.USER,
                                    text = attachment.photo!!.id.toString(),
                                ),
                            )

                            add(
                                BarsilGptImageUrlMessage(
                                    role = CommonLlmMessageRole.USER,
                                    url = image.base64String,
                                ),
                            )
                        }
                    },
            )
        }

        if (llmPhotoResponse.all { it.failureOrNull() != null }) {
            llmPhotoResponse.filter { it.failureOrNull() != null }.forEach { logError(it.failureOrNull()!!.cause) }
            return DailyScheduleWatchingResult(
                success = false,
                message = "Мне почему-то llm не отвечает по картинкам... постучусь позже",
                sendTelegram = true,
            )
        }

        val llmPhotoResult = llmPhotoResponse.asSequence()
            .filter { it.failureOrNull() == null }
            .map { it.valueOrNull()!! }
            .map { it.toExpectedPhotoPromtResult() }
            .toList()

        if (llmPhotoResult.all { it.failureOrNull() != null }) {
            return DailyScheduleWatchingResult(
                success = false,
                message = """
                LLM видимо не справился с моими великолепными фотографиями... в общем в другой раз еще раз попробую. 
                
                Вот, что мне отдал:
                ${llmPhotoResult.filter { it.failureOrNull() != null }.map { it.failureOrNull() }}
            """.trimIndent(),
                sendTelegram = true,
            )
        }

        val approvedBands = llmAudioResult.valueOrNull()!!.result.asSequence()
            .filter { it.approval >= barsik.configuration.content.settings.musicLlmApprovalThreshold }
            .associateBy { it.band }

        val approvedPhotos = llmPhotoResult.asSequence()
            .filter { it.failureOrNull() == null }
            .map { it.valueOrNull()!! }
            .flatMap { it.result }
            .filter { it.approval }
            .associateBy { it.photo }

        val resultingMusicAttachments = musicAttachments.filter { it.audio!!.artist in approvedBands }
        val resultingPhotoAttachments = photoAttachments.filter { it.photo!!.id.toString() in approvedPhotos }

        if (resultingMusicAttachments.isEmpty()) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Кажется LLM вообще не зашла никакая группа... в следующий раз повезет!",
                sendTelegram = true,
            )
        }

        if (resultingPhotoAttachments.isEmpty()) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Кажется LLM вообще не понравилась никакая фотография... в следующий раз я точно найду еще лучше!",
                sendTelegram = true,
            )
        }

        val approvedMusicAttachment = resultingMusicAttachments.getRandomAttachment()
        val approvedPhotoAttachment = resultingPhotoAttachments.getRandomAttachment()

        val createdPost = barsik.createVkWallpost(
            attachments = listOf(
                VkPostWallpostAttachment(
                    type = approvedMusicAttachment.type,
                    ownerId = approvedMusicAttachment.audio!!.ownerId,
                    mediaId = approvedMusicAttachment.audio!!.id,
                ),
                VkPostWallpostAttachment(
                    type = approvedPhotoAttachment.type,
                    ownerId = approvedPhotoAttachment.photo!!.ownerId,
                    mediaId = approvedPhotoAttachment.photo!!.id,
                )
            )
        ).peekFailure { logError(it.cause) }

        if (createdPost.failureOrNull() != null) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Я был так близок, столько всего насобирал и отобрал, но Вконтакте не дал мне почему то это запостить... ну и ладно",
                sendTelegram = true,
            )
        }

        return DailyScheduleWatchingResult(
            success = true,
            message = """
                Ура! Я неплохо потрудился и вот, что у меня вышло, пойду отдыхать...
                
                [картиночка](${approvedPhotoAttachment.photo.origPhoto?.url ?: approvedPhotoAttachment.photo.sizes.last().url}) и _${approvedMusicAttachment.audio.artist} - ${approvedMusicAttachment.audio.title}_
                
                Положил это тут: https://vk.com/${barsik.configuration.wallposts.domain}?w=wall${barsik.configuration.wallposts.communityId}_${createdPost.orThrow().response.postId}
            """.trimIndent(),
            sendTelegram = true,
        )
    }

    private fun SimpleGptResponse.toExpectedAudioPromtResult(): Result4k<ExpectedAudioPromtResult, SimpleGptResponse> =
        runCatching {
            llmJsonAnswerMapper.readValue(
                this.answer,
                ExpectedAudioPromtResult::class.java,
            )
        }.let {
            if (it.isSuccess) {
                Success(it.getOrThrow())
            } else {
                Failure(this)
            }
        }

    private fun SimpleGptResponse.toExpectedPhotoPromtResult(): Result4k<ExpectedPhotoPromtResult, SimpleGptResponse> =
        runCatching {
            llmJsonAnswerMapper.readValue(
                this.answer,
                ExpectedPhotoPromtResult::class.java,
            )
        }.let {
            if (it.isSuccess) {
                Success(it.getOrThrow())
            } else {
                Failure(this)
            }
        }
}

private fun List<Content.Provider>.getRandomProvider() = this[Random.nextInt(size)]

private fun List<VkWallposts.VkWallpostsResponse.VkWallpostsItem.VkWallpostsAttachment>.getRandomAttachment() = this[Random.nextInt(size)]