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
import com.github.drewlakee.yabarsik.logInfo
import com.github.drewlakee.yabarsik.scenario.BarsikScenario
import com.github.drewlakee.yabarsik.scenario.BarsikScenarioResult
import com.github.drewlakee.yabarsik.vk.api.VkGroups
import com.github.drewlakee.yabarsik.vk.api.VkPostWallpostAttachment
import com.github.drewlakee.yabarsik.vk.api.VkUsers
import com.github.drewlakee.yabarsik.vk.api.VkWallposts
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType
import com.github.drewlakee.yabarsik.yandex.llm.api.CommonLlmMessageRole
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.failureOrNull
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.peekFailure
import dev.forkhandles.result4k.recover
import dev.forkhandles.result4k.valueOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
        val currentZoneId = ZoneId.of(barsik.configuration.wallposts.dailySchedule.timeZone)
        val (currentDate, currentScheduleCheckpoint) =
            with(barsik.configuration.wallposts.dailySchedule) {
                LocalDate.now(currentZoneId) to
                    checkpoints
                        .asSequence()
                        .sortedBy { checkpoint -> LocalTime.parse(checkpoint.at) }
                        .lastOrNull() { checkpoint -> LocalTime.parse(checkpoint.at).isBefore(LocalTime.now(currentZoneId)) }
            }

        if (currentScheduleCheckpoint == null) {
            logInfo("It seems the time has not come yet, let’s sleep some more… my schedule: ${barsik.configuration.wallposts.dailySchedule.checkpoints}")
            return DailyScheduleWatchingResult(success = true)
        }

        val previousCheckpoint = with (barsik.configuration.wallposts.dailySchedule) {
            val previousCheckpoint = checkpoints
                .asSequence()
                .sortedBy { checkpoint -> LocalTime.parse(checkpoint.at) }
                .lastOrNull { checkpoint -> LocalTime.parse(checkpoint.at).isBefore(LocalTime.parse(currentScheduleCheckpoint.at)) }

            previousCheckpoint ?: currentScheduleCheckpoint
        }

        val isStillPreviousPostponeCooldownBetweenPosts = previousCheckpoint?.let { previous ->
            val amortization = Duration.parse("PT5M")
            val cooldown = previous.plusPostponeDuration.let { Duration.parse(it) }.plus(amortization)
            val previousLocalTime = LocalTime.parse(previous.at)
            (LocalTime.now(currentZoneId).toSecondOfDay() - previousLocalTime.toSecondOfDay()).toLong().seconds.inWholeHours < cooldown.inWholeHours
        } ?: false

        if (isStillPreviousPostponeCooldownBetweenPosts) {
            logInfo("It’s possible that the previous post has been postponed now, we need to wait… the previous one at  ${LocalTime.parse(previousCheckpoint.at)} with cooldown ${previousCheckpoint.plusPostponeDuration}")
            return DailyScheduleWatchingResult(success = true)
        }

        val todayWallposts =
            barsik.getVkTodayWallposts(
                domain = barsik.configuration.wallposts.domain,
                today = currentDate,
                zone = currentZoneId,
            )

        if (todayWallposts.failureOrNull() != null) {
            logError(todayWallposts.failureOrNull()!!.cause)
            return DailyScheduleWatchingResult(
                success = false,
                message = "Пытался узнать что вы сегодня ($currentDate, $currentZoneId) постили в паблике, но получаю ошибку...",
                sendTelegram = true,
            )
        }

        val sortedTodayWallposts =
            todayWallposts
                .valueOrNull()!!
                .response
                .items
                .map { wallpost -> LocalTime.ofInstant(Instant.ofEpochSecond(wallpost.date), currentZoneId) to wallpost }
                .sortedBy { (localTime, _) -> localTime }

        val checkpointsBeforeNowCount = barsik.configuration.wallposts.dailySchedule.checkpoints.count { checkpoint ->
            LocalTime.parse(checkpoint.at).isBefore(LocalTime.now())
        }

        val alreadyPostedWallpostsCount = sortedTodayWallposts.count { (localTime, _) -> localTime.isBefore(LocalTime.now(currentZoneId)) }

        if (alreadyPostedWallpostsCount >= checkpointsBeforeNowCount) {
            logInfo("It seems the public page is managing without me… I decided to check according to the schedule ${currentScheduleCheckpoint}, " +
                "but the guys have already posted enough updates. There are already $alreadyPostedWallpostsCount, and according to my calculations, there should have been $checkpointsBeforeNowCount, for me to take care of it myself!")
            return DailyScheduleWatchingResult(success = true)
        }

        val isStillCooldownBetweenPosts =  sortedTodayWallposts.lastOrNull()?.first?.let { lastPostLocalTime ->
            val cooldown = barsik.configuration.wallposts.dailySchedule.periodBetweenPostings.let { Duration.parse(it) }
            (LocalTime.now(currentZoneId).toSecondOfDay() - lastPostLocalTime.toSecondOfDay()).toLong().seconds.inWholeHours < cooldown.inWholeHours
        } ?: false

        if (isStillCooldownBetweenPosts) {
            val lastPostLocalTime = sortedTodayWallposts.last().first
            logInfo("Waiting for the cooldown to pass (${barsik.configuration.wallposts.dailySchedule.periodBetweenPostings}) " +
                "since the last post at $lastPostLocalTime, and I’ll be ready to post something new!")
            return DailyScheduleWatchingResult(success = true)
        }

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
                message =
                    """
                    Хм... ты забыл указать мне источники для контента? Ну ладно, тогда я отдыхаю \uD83D\uDE3C"
                    """.trimIndent(),
                sendTelegram = true,
            )
        }

        var musicAttachments =
            buildList {
                for (limit in 1..5) {
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
                                } else {
                                    return@buildList
                                }
                            }
                        }
                }
            }

        var photoAttachments =
            buildList {
                for (limit in 1..5) {
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
                                } else {
                                    return@buildList
                                }
                            }
                        }
                }
            }

        val communities =
            photoAttachments
                .asSequence()
                .filter { it.photo!!.isCommunityOwner() }
                .map { it.photo!!.ownerId }
                .toList() +
                musicAttachments
                    .asSequence()
                    .filter { it.audio!!.isCommunityOwner() }
                    .map { it.audio!!.ownerId }
                    .toList()
        val users =
            photoAttachments
                .asSequence()
                .filter { !it.photo!!.isCommunityOwner() }
                .map { it.photo!!.ownerId }
                .toList() +
                musicAttachments
                    .asSequence()
                    .filter { !it.audio!!.isCommunityOwner() }
                    .map { it.audio!!.ownerId }
                    .toList()

        val openSharingAttachmentsUsers =
            barsik
                .getVkUsers(users.distinct())
                .peekFailure { logError(it.cause) }
                .map { it.users }
                .recover { listOf() }
                .associateBy { it.id }

        val openSharingAttachmentsCommunities =
            barsik
                .getVkGroups(communities.map { it * -1 }.distinct())
                .peekFailure { logError(it.cause) }
                .map { it.response.groups }
                .recover { listOf() }
                .associateBy { it.id * -1 }

        val openSharingOwnerIds =
            users.filter { it !in openSharingAttachmentsUsers || openSharingAttachmentsUsers[it]!!.isOpenAccount() } +
                communities.filter { it !in openSharingAttachmentsCommunities || openSharingAttachmentsCommunities[it]!!.isOpenCommunity() }

        musicAttachments = musicAttachments.filter { it.audio!!.ownerId in openSharingOwnerIds }
        photoAttachments = photoAttachments.filter { it.photo!!.ownerId in openSharingOwnerIds }

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
                            text =
                                musicAttachments.groupBy { it.audio!!.artist }.entries.joinToString(
                                    separator = ", ",
                                ) { (artist, _) -> artist },
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
                message =
                    """
                    LLM то ненастоящий, как-то неожидаемо ответил насчет музыки, возможно, галлюцинация, но я не понял... в общем в другой раз еще раз попробую. 
                    
                    Вот, что мне отдал:
                    ${llmAudioResult.failureOrNull()}
                    """.trimIndent(),
                sendTelegram = true,
            )
        }

        val downloadedImages =
            photoAttachments
                .asSequence()
                .map {
                    it to
                        barsik.getImage(
                            url =
                                it.photo!!.origPhoto?.url ?: it.photo.sizes
                                    .last()
                                    .url,
                        )
                }.filter { it.second.failureOrNull() == null }
                .map { it.first to it.second.valueOrNull()!! }
                .toList()

        if (downloadedImages.isEmpty()) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Все подобранные фотографии не получилось загрузить из интернета... надо проверить будет в чем проблема!",
                sendTelegram = true,
            )
        }

        val llmPhotoResponse =
            downloadedImages.windowed(3, 3, partialWindows = true).map { windowed ->
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

        val llmPhotoResult =
            llmPhotoResponse
                .asSequence()
                .filter { it.failureOrNull() == null }
                .map { it.valueOrNull()!! }
                .map { it.toExpectedPhotoPromtResult() }
                .toList()

        if (llmPhotoResult.all { it.failureOrNull() != null }) {
            return DailyScheduleWatchingResult(
                success = false,
                message =
                    """
                    LLM видимо не справился с моими великолепными фотографиями... в общем в другой раз еще раз попробую. 
                    
                    Вот, что мне отдал:
                    ${llmPhotoResult.filter { it.failureOrNull() != null }.map { it.failureOrNull() }}
                    """.trimIndent(),
                sendTelegram = true,
            )
        }

        val approvedBands =
            llmAudioResult
                .valueOrNull()!!
                .result
                .asSequence()
                .filter { it.approval >= barsik.configuration.content.settings.musicLlmApprovalThreshold }
                .associateBy { it.band }

        val approvedPhotos =
            llmPhotoResult
                .asSequence()
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

        val publishUtcDate = Instant.now().plus(currentScheduleCheckpoint.plusPostponeDuration.let { Duration.parse(it).toJavaDuration() })
        val createdPost =
            barsik
                .createVkWallpost(
                    attachments =
                        listOf(
                            VkPostWallpostAttachment(
                                type = approvedMusicAttachment.type,
                                ownerId = approvedMusicAttachment.audio!!.ownerId,
                                mediaId = approvedMusicAttachment.audio!!.id,
                            ),
                            VkPostWallpostAttachment(
                                type = approvedPhotoAttachment.type,
                                ownerId = approvedPhotoAttachment.photo!!.ownerId,
                                mediaId = approvedPhotoAttachment.photo!!.id,
                            ),
                        ),
                    publishDate = publishUtcDate.epochSecond,
                ).peekFailure { logError(it.cause) }

        if (createdPost.failureOrNull() != null) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Я был так близок, столько всего насобирал и отобрал, но Вконтакте не дал мне почему то это запостить... ну и ладно",
                sendTelegram = true,
            )
        }

        val (publishDate, publishTime) = publishUtcDate.atZone(currentZoneId).let { it.toLocalDate() to it.toLocalTime() }
        return DailyScheduleWatchingResult(
            success = true,
            message =
                """
                Ура! Я неплохо потрудился и вот, что у меня вышло, пойду отдыхать...
                
                [картиночка](${approvedPhotoAttachment.photo.origPhoto?.url ?: approvedPhotoAttachment.photo.sizes
                    .last()
                    .url}) и [${approvedMusicAttachment.audio.artist} - ${approvedMusicAttachment.audio.title}](${approvedMusicAttachment.audio!!.url})
                
                Положил в [предложку](https://vk.com/${barsik.configuration.wallposts.domain}?w=wall${barsik.configuration.wallposts.communityId}_${createdPost.orThrow().response.postId}) и запланировал на $publishDate в $publishTime!
                
                ```дебаг_инфа
                photoOwnerId=${approvedPhotoAttachment.photo.ownerId}
                audioOwnerId=${approvedMusicAttachment.audio.ownerId}
                ```
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

private fun VkUsers.User.isOpenAccount() = !isClosed && canSeeAudio == 1 && canAccessClosed

private fun VkGroups.Response.Group.isOpenCommunity() = isClosed == 0

private fun VkWallposts.VkWallpostsResponse.VkWallpostsItem.VkWallpostsAttachment.VkWallpostsAttachmentAudio.isCommunityOwner() =
    ownerId < 0

private fun VkWallposts.VkWallpostsResponse.VkWallpostsItem.VkWallpostsAttachment.VkWallpostsAttachmentPhoto.isCommunityOwner() =
    ownerId < 0
