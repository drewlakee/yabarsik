package com.github.drewlakee.yabarsik.scenario.vk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.drewlakee.yabarsik.Barsik
import com.github.drewlakee.yabarsik.BarsikGptTextMessage
import com.github.drewlakee.yabarsik.BarsilGptImageUrlMessage
import com.github.drewlakee.yabarsik.SimpleGptResponse
import com.github.drewlakee.yabarsik.configuration.Content
import com.github.drewlakee.yabarsik.discogs.api.ArtistReleases
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
        logInfo("Start playing scenario")
        logInfo("Scenario schedule is ${barsik.configuration.wallposts.dailySchedule}")

        val currentZoneId = ZoneId.of(barsik.configuration.wallposts.dailySchedule.timeZone)
        val amortizationSchedule = barsik.configuration.wallposts.dailySchedule.checkpoints
            .asSequence()
            .sortedBy { checkpoint -> LocalTime.parse(checkpoint.at) }
            .map { checkpoint ->
                checkpoint.copy(
                    at = with(checkpoint.at) {
                        val amortizationSeconds = Duration.parse(checkpoint.amortizationDuration).inWholeSeconds
                        val randomDuration = Random.nextLong(0, amortizationSeconds).seconds
                        LocalTime.parse(checkpoint.at).plus(randomDuration.toJavaDuration()).toString()
                    }
                )
            }
            .toList()

        logInfo("New schedule after amortization is $amortizationSchedule")

        val (currentDate, currentScheduleCheckpoint) = LocalDate.now(currentZoneId) to
            amortizationSchedule.lastOrNull() { checkpoint ->
                LocalTime.parse(checkpoint.at).isBefore(LocalTime.now(currentZoneId))
            }

        if (currentScheduleCheckpoint == null) {
            logInfo("None checkpoints were reached. It seems the time has not come yet")
            return DailyScheduleWatchingResult(success = true)
        }

        logInfo("Requesting today wallposts from domain=${barsik.configuration.wallposts.domain}")
        val todayWallposts =
            barsik.getVkTodayWallposts(
                domain = barsik.configuration.wallposts.domain,
                today = currentDate,
                zone = currentZoneId,
            )
        logInfo("Response today wallposts [${todayWallposts.valueOrNull()?.response?.items?.size ?: "error"}]: ${todayWallposts.valueOrNull() ?: "error"}")

        if (todayWallposts.failureOrNull() != null) {
            todayWallposts.failureOrNull()?.run(::logError)
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

        val checkpointsBeforeNowCount = amortizationSchedule.count { checkpoint ->
            LocalTime.parse(checkpoint.at).isBefore(LocalTime.now(currentZoneId))
        }

        val alreadyPostedWallpostsCount = sortedTodayWallposts.count { (localTime, _) -> localTime.isBefore(LocalTime.now(currentZoneId)) }

        if (alreadyPostedWallpostsCount >= checkpointsBeforeNowCount) {
            logInfo("It seems the public page is already managed without my help. I decided to check according to the current checkpoint ${currentScheduleCheckpoint}, " +
                "but there are already enough posts on the page. There are $alreadyPostedWallpostsCount posts, and according to my schedule ${amortizationSchedule}, there should have been $checkpointsBeforeNowCount at maximum.")
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

        logInfo("Checking if LLM models are available at the moment")
        val textModelAvailability = barsik.askTextGpt(
            temperature = 0.0f,
            messages = listOf(
                BarsikGptTextMessage(
                    role = CommonLlmMessageRole.SYSTEM,
                    text = "Are you available? Reply just OK, if you're fine."
                )
            )
        )

        if (textModelAvailability.failureOrNull() != null) {
            textModelAvailability.failureOrNull()?.run(::logError)
            return DailyScheduleWatchingResult(
                success = false,
                sendTelegram = true,
                message = "Текстовая модель в данный момент недоступна, проверь что там не так!"
            )
        }
        logInfo("Text model response is ${textModelAvailability.valueOrNull()}")

        val multiModalModelAvailability = barsik.askMultiModalGpt(
            temperature = 0.0f,
            messages = listOf(
                BarsikGptTextMessage(
                    role = CommonLlmMessageRole.USER,
                    text = "Are you available? Reply just OK, if you're fine."
                )
            )
        )

        if (multiModalModelAvailability.failureOrNull() != null) {
            multiModalModelAvailability.failureOrNull()?.run(::logError)
            return DailyScheduleWatchingResult(
                success = false,
                sendTelegram = true,
                message = "Мульти-модальная модель в данный момент недоступна, проверь что там не так!"
            )
        }
        logInfo("Multi-modal model response is ${multiModalModelAvailability.valueOrNull()}")

        logInfo("Collecting content providers")
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
        logInfo("Got providers [${mediaProviders.size}]: $mediaProviders")

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

        val domainWallpostsCountMemoization = mutableMapOf<String, Int>()

        var musicAttachments =
            buildList {
                for (limit in 1..5) {
                    val domain = mediaProviders[Content.Provider.Media.MUSIC]!!.getRandomProvider().domain
                    val domainWallpostsCount = domainWallpostsCountMemoization[domain]
                    this@DailyScheduleWatching.logInfo("Requesting music attachments from domain=$domain")
                    barsik
                        .takeVkAttachmentsRandomly(
                            domain = domain,
                            count = barsik.configuration.content.settings.takeMusicAttachmentsPerProvider,
                            type = VkWallpostsAttachmentType.AUDIO,
                            domainWallpostsCount = domainWallpostsCount,
                        ).peekFailure(::logError)
                        .valueOrNull()?.let {
                            domainWallpostsCountMemoization[domain] = it.totalWallpostsCount
                            this@DailyScheduleWatching.logInfo("Response music attachments from domain=$domain [${it.attachments.size}]: $it")
                            it.attachments.forEach {
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
                    val domain = mediaProviders[Content.Provider.Media.IMAGES]!!.getRandomProvider().domain
                    val domainWallpostsCount = domainWallpostsCountMemoization[domain]
                    this@DailyScheduleWatching.logInfo("Requesting photo attachments from domain=$domain")
                    barsik
                        .takeVkAttachmentsRandomly(
                            domain = domain,
                            count = barsik.configuration.content.settings.takeImagesAttachmentsPerProvider,
                            type = VkWallpostsAttachmentType.PHOTO,
                            domainWallpostsCount = domainWallpostsCount,
                        ).peekFailure(::logError)
                        .valueOrNull()?.let {
                            domainWallpostsCountMemoization[domain] = it.totalWallpostsCount
                            this@DailyScheduleWatching.logInfo("Response photo attachments from domain=$domain [${it.attachments.size}]: $it")
                            it.attachments.forEach {
                                if (this.size < barsik.configuration.content.settings.imagesAttachmentsCollectorSize) {
                                    add(it)
                                } else {
                                    return@buildList
                                }
                            }
                        }
                }
            }

        logInfo("Collecting communities/users from attachements")
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
        logInfo("Resulting communities/users from attachements: communities=$communities, users=$users")

        logInfo("Requesting open user accounts for sharing attachments")

        val openSharingAttachmentsUsers =
            barsik
                .getVkUsers(users.distinct())
                .peekFailure(::logError)
                .map { it.users }
                .recover { listOf() }
                .associateBy { it.id }

        logInfo("Response open user accounts for sharing attachments: ${openSharingAttachmentsUsers.keys}")

        logInfo("Requesting open communities for sharing attachments")

        val openSharingAttachmentsCommunities =
            barsik
                .getVkGroups(communities.map { it * -1 }.distinct())
                .peekFailure(::logError)
                .map { it.response.groups }
                .recover { listOf() }
                .associateBy { it.id * -1 }

        logInfo("Response open communities for sharing attachments: ${openSharingAttachmentsCommunities.keys}")

        val openSharingOwnerIds =
            users.filter { it !in openSharingAttachmentsUsers || openSharingAttachmentsUsers[it]!!.isOpenAccount() } +
                communities.filter { it !in openSharingAttachmentsCommunities || openSharingAttachmentsCommunities[it]!!.isOpenCommunity() }

        logInfo("Filtered potentially open ownerIds: $openSharingOwnerIds")

        musicAttachments = musicAttachments.filter { it.audio!!.ownerId in openSharingOwnerIds }
        photoAttachments = photoAttachments.filter { it.photo!!.ownerId in openSharingOwnerIds }

        logInfo("Filtered open music/photo attachments: music=$musicAttachments, photos=$photoAttachments")

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

        logInfo("Requesting Discogs artist and track releases")

        val discogsArtistTrackReleases = musicAttachments.asSequence()
            .map { it.audio!!.artist to it.audio.title }
            .mapNotNull { (artist, track) ->
                logInfo("Requesting Discogs releases for artist=$artist, track=$track")
                val artistTrackReleases = barsik.getArtistReleases(
                    artist = artist,
                    track = track,
                ).peekFailure(::logError).valueOrNull()
                logInfo("Response Discogs releases for artist=$artist, track=$track: ${artistTrackReleases ?: "error"}")
                artistTrackReleases
            }
            .toList()

        logInfo("Response Discogs artist and track releases: $discogsArtistTrackReleases")

        logInfo("Requesting Discogs releases only by artists")

        val discogsOnlyArtistReleases = musicAttachments.asSequence()
            .map { it.audio!!.artist }
            .distinct()
            .mapNotNull { artist ->
                logInfo("Requesting Discogs releases for artist=$artist")
                val artistReleases = barsik.getArtistReleases(
                    artist = artist,
                ).peekFailure(::logError).valueOrNull()
                logInfo("Response Discogs releases for artist=$artist: ${artistReleases ?: "error"}")
                artistReleases
            }
            .toList()

        logInfo("Response Discogs releases only by artists: $discogsOnlyArtistReleases")

        val discogsArtistsReleases = (discogsArtistTrackReleases.asSequence() + discogsOnlyArtistReleases.asSequence())
            .groupBy { it.artist }
            .entries
            .asSequence()
            .map { (artist, releases) ->
                ArtistReleases(
                    artist = artist,
                    releases = releases.flatMap { it.releases }.distinctBy { it.title },
                )
            }
            .filter { it.releases.isNotEmpty() }
            .toList()

        logInfo("Requesting LLM about music attachments")

        val llmAudioResponse =
            barsik.askTextGpt(
                temperature = barsik.configuration.llm.audioPromt.temperature,
                messages =
                    buildList {
                        add(
                            BarsikGptTextMessage(
                                role = CommonLlmMessageRole.SYSTEM,
                                text = barsik.configuration.llm.audioPromt.systemInstruction,
                            )
                        )

                        if (discogsOnlyArtistReleases.isNotEmpty()) {
                            add(
                                BarsikGptTextMessage(
                                    role = CommonLlmMessageRole.SYSTEM,
                                    text = "${barsik.configuration.llm.audioPromt.discogsContext}\n${discogsArtistsReleases.joinToString(separator = "\n")}",
                                )
                            )
                        }

                        add(
                            BarsikGptTextMessage(
                                role = CommonLlmMessageRole.USER,
                                text =
                                    musicAttachments.groupBy { it.audio!!.artist }.entries.joinToString(
                                        separator = ", ",
                                        ) { (artist, _) -> artist },
                                )
                        )
                    },
            )

        logInfo("Got LLM response about music attachments. response=${llmAudioResponse.valueOrNull() ?: "error"}")

        if (llmAudioResponse.failureOrNull() != null) {
            llmAudioResponse.failureOrNull()?.run(::logError)
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

        logInfo("Downloading images from the Internet")

        val downloadedImages =
            photoAttachments
                .asSequence()
                .map {
                    it to
                        barsik.getImage(
                            url =
                                it.photo!!.origPhoto!!.url,
                        )
                }.filter { it.second.failureOrNull() == null }
                .map { it.first to it.second.valueOrNull()!! }
                .toList()

        logInfo("Downloaded images from the Internet: ${downloadedImages.map { (attachment, _) -> attachment }}")

        if (downloadedImages.isEmpty()) {
            return DailyScheduleWatchingResult(
                success = false,
                message = "Все подобранные фотографии не получилось загрузить из интернета... надо проверить будет в чем проблема!",
                sendTelegram = true,
            )
        }

        logInfo("Requesting LLM about photo attachments")

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

        logInfo("Got LLM response about photo attachments. response=${llmPhotoResponse.mapNotNull { it.valueOrNull() }}")

        if (llmPhotoResponse.all { it.failureOrNull() != null }) {
            llmPhotoResponse.forEach { it.failureOrNull()?.run(::logError) }
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

        logInfo("Approved by LLM music: ${approvedBands.keys}")

        val approvedPhotos =
            llmPhotoResult
                .asSequence()
                .filter { it.failureOrNull() == null }
                .map { it.valueOrNull()!! }
                .flatMap { it.result }
                .filter { it.approval }
                .associateBy { it.photo }

        logInfo("Approved by LLM photos: ${approvedPhotos.keys}")

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

        logInfo("Resulting attachments: music=$approvedMusicAttachment, photo=$approvedPhotoAttachment")

        logInfo("Creating post in VK for domain=${barsik.configuration.wallposts.domain}")

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
                ).peekFailure(::logError)

        logInfo("Got result of creating a post in VK. response=${createdPost.valueOrNull() ?: "error"}")

        if (createdPost.failureOrNull() != null) {
            createdPost.failureOrNull()?.run(::logError)
            return DailyScheduleWatchingResult(
                success = false,
                message = "Я был так близок, столько всего насобирал и отобрал, но Вконтакте не дал мне почему то это запостить... ну и ладно",
                sendTelegram = true,
            )
        }

        logInfo("Scenario is completely played")
        return DailyScheduleWatchingResult(
            success = true,
            message =
                """
                Ура! Я неплохо потрудился и вот, что у меня вышло, пойду отдыхать...
                
                [картиночка](${approvedPhotoAttachment.photo.origPhoto!!.url}) и [${approvedMusicAttachment.audio.artist} - ${approvedMusicAttachment.audio.title}](${approvedMusicAttachment.audio!!.url})
                
                Положил на [страничку](https://vk.com/${barsik.configuration.wallposts.domain}?w=wall${barsik.configuration.wallposts.communityId}_${createdPost.orThrow().response.postId})!
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