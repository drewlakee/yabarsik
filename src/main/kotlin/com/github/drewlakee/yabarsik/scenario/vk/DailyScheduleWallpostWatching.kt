package com.github.drewlakee.yabarsik.scenario.vk

import com.github.drewlakee.yabarsik.Barsik
import com.github.drewlakee.yabarsik.Barsik.Companion.askMultiModalGpt
import com.github.drewlakee.yabarsik.Barsik.Companion.askTextGpt
import com.github.drewlakee.yabarsik.Barsik.Companion.sendTelegramMessage
import com.github.drewlakee.yabarsik.Barsik.Companion.takeVkAttachmentsRandomly
import com.github.drewlakee.yabarsik.BarsikGptTextMessage
import com.github.drewlakee.yabarsik.configuration.Content
import com.github.drewlakee.yabarsik.scenario.BarsikScenario
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType
import com.github.drewlakee.yabarsik.yandex.llm.api.CommonLlmMessageRole
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.failureOrNull
import dev.forkhandles.result4k.orThrow
import kotlin.random.Random

class DailyScheduleWatchingResult() {}

class DailyScheduleWatching : BarsikScenario<DailyScheduleWatchingResult> {
    override fun play(barsik: Barsik): Result4k<DailyScheduleWatchingResult, Throwable> {
        val mediaProviders = barsik.configuration.content.providers.asSequence()
            .flatMap { provider ->
                provider.media.map { media ->
                        media to provider
                    }
            }
            .groupBy(
                keySelector = { (media, _) -> media },
                valueTransform = { (_, provider) -> provider }
            )

        if (mediaProviders[Content.Provider.Media.MUSIC]?.isEmpty() ?: true ||
            mediaProviders[Content.Provider.Media.IMAGES]?.isEmpty() ?: true) {
            barsik.sendTelegramMessage("Хм... ты забыл указать мне источники для контента? Ну ладно, тогда я отдыхаю \uD83D\uDE3C")
            return Success(DailyScheduleWatchingResult())
        }

        val musicAttachments = barsik.takeVkAttachmentsRandomly(
            domain = mediaProviders[Content.Provider.Media.MUSIC]!!.getRandomProvider().domain,
            count = barsik.configuration.content.settings.musicAttachmentsCollectorSize,
            type = VkWallpostsAttachmentType.AUDIO
        )

        val photoAttachments = barsik.takeVkAttachmentsRandomly(
            domain = mediaProviders[Content.Provider.Media.IMAGES]!!.getRandomProvider().domain,
            count = barsik.configuration.content.settings.imagesAttachmentsCollectorSize,
            type = VkWallpostsAttachmentType.PHOTO
        )

        if (musicAttachments.failureOrNull() != null || musicAttachments.orThrow().isEmpty()) {
            barsik.sendTelegramMessage("Эх, что-то у меня не вышло найти музычку Вконтакте, попробую попозже еще раз...")
            return Success(DailyScheduleWatchingResult())
        }

        if (photoAttachments.failureOrNull() != null || photoAttachments.orThrow().isEmpty()) {
            barsik.sendTelegramMessage("Так, блин, котята разбежались, что-то у меня не вышло никого найти, попробую попозже что ли...")
            return Success(DailyScheduleWatchingResult())
        }

        with (musicAttachments.orThrow()) {
            barsik.askTextGpt(
                temperature = barsik.configuration.llm.audioPromt.temperature,
                messages = listOf(
                    BarsikGptTextMessage(
                        role = CommonLlmMessageRole.SYSTEM,
                        text = barsik.configuration.llm.audioPromt.systemInstruction,
                    ),
                    BarsikGptTextMessage(
                        role = CommonLlmMessageRole.USER,
                        text = this.joinToString(separator = ", ") { it.audio!!.artist },
                    ),
                )
            )
        }

        with (photoAttachments.orThrow()) {
            barsik.askMultiModalGpt(
                temperature = barsik.configuration.llm.photoPromt.temperature,
                messages = buildList {
                    add(
                        BarsikGptTextMessage(
                            role = CommonLlmMessageRole.SYSTEM,
                            text = barsik.configuration.llm.audioPromt.systemInstruction,
                        )
                    )

                    this@with.forEach { attachment ->
                        add(
                            BarsikGptTextMessage(
                                role = CommonLlmMessageRole.USER,
                                text = attachment.photo!!.id.toString(),
                            )
                        )
                        add(
                            BarsikGptTextMessage(
                                role = CommonLlmMessageRole.USER,
                                text = attachment.photo.origPhoto.url,
                            )
                        )
                    }
                }
            )
        }


        return Success(DailyScheduleWatchingResult())
    }
}

private fun List<Content.Provider>.getRandomProvider() = this.get(Random.nextInt(size))