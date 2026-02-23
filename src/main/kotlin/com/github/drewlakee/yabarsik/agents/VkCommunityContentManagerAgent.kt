package com.github.drewlakee.yabarsik.agents

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.AgentImage
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.SomeOf
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.github.drewlakee.yabarsik.configuration.YabarsikLlmModels
import com.github.drewlakee.yabarsik.discogs.api.ArtistReleases
import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.discogs.api.getArtistReleases
import com.github.drewlakee.yabarsik.images.GetImage
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.prompt.YabarsikPromptContributors
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.telegram.chat.TelegramReportChat
import com.github.drewlakee.yabarsik.vk.api.GetComments
import com.github.drewlakee.yabarsik.vk.api.PostWallpost
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.VkPostWallpostAttachment
import com.github.drewlakee.yabarsik.vk.api.VkWallpostComments.Response.VkWallpostComment
import com.github.drewlakee.yabarsik.vk.api.VkWallposts.VkWallpostsResponse.VkWallpostsItem
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType
import com.github.drewlakee.yabarsik.vk.api.getLastWallposts
import com.github.drewlakee.yabarsik.vk.api.getOnlyOpenOwners
import com.github.drewlakee.yabarsik.vk.api.takeAttachmentsRandomly
import com.github.drewlakee.yabarsik.vk.community.VkCommunity
import com.github.drewlakee.yabarsik.vk.content.ContentMedia
import com.github.drewlakee.yabarsik.vk.content.VkContentProvider
import com.github.drewlakee.yabarsik.vk.content.VkWallpostAttachment
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.recover
import dev.forkhandles.result4k.valueOrNull
import java.time.Instant

data class PublishNewContentVerdictSomeOf(
    val shouldNotPublishNewContext: ShouldNotPublishNewContext? = null,
    val shouldPublishNewContent: ShouldPublishNewContent? = null,
) : SomeOf

data class ShouldNotPublishNewContext(
    val modelResultExplanation: String,
)

data class ShouldPublishNewContent(
    val modelResultExplanation: String,
)

data class PublishNewContentVerdict(
    @get:JsonPropertyDescription("Финальное решение об необходимости свежей публикации")
    val shouldPublish: Boolean,
    @get:JsonPropertyDescription("Объяснение причин, по которым было принято финальное решение")
    val modelResultExplanation: String,
)

data class CommunityWallpost(
    val wallpost: VkWallpostsItem,
    val comments: List<VkWallpostComment>,
)

data class ActualCommunityContent(
    val lastWallposts: List<CommunityWallpost>,
)

data class VkCommunityContentManagerAgentResult(
    val response: String,
)

data class AppropriateMusicMediaAttachment(
    val attachment: VkWallpostsItem.VkWallpostsAttachment,
    val llmChoice: LlmAppropriateMusicMedia,
)

data class LlmAppropriateMusicMedia(
    @get:JsonPropertyDescription("Идентификатор владельца трека (ownerId)")
    val ownerId: Int,
    @get:JsonPropertyDescription("Идентификатор самого трека (id)")
    val id: Int,
    @get:JsonPropertyDescription("Причина по которой был сделан выбор")
    val modelChoiceExplanation: String,
)

data class AppropriateImageMediaAttachment(
    val attachment: VkWallpostsItem.VkWallpostsAttachment,
    val llmChoice: LlmAppropriateImageMedia,
)

data class LlmAppropriateImagesMedia(
    @get:JsonPropertyDescription("Указанные фотографии в запросе")
    val images: List<LlmAppropriateImageMedia>,
)

data class LlmAppropriateImageMedia(
    @get:JsonPropertyDescription("Идентификатор владельца картинки (ownerId)")
    val ownerId: Int,
    @get:JsonPropertyDescription("Идентификатор самой картинки (id)")
    val id: Int,
    @get:JsonPropertyDescription("Эта картинка понравилась модели - true, иначе - false")
    val modelChoice: Boolean,
    @get:JsonPropertyDescription("Пояснение модели, почему картинка понравилась")
    val modelChoiceExplanation: String,
)

@Agent(
    name = "Агент по управлению контентом в сообществе Вконтакте",
    description = "Занимается поиском подходящего контента и принимает решение о его публикации",
)
class VkCommunityContentManagerAgent(
    private val telegramApi: TelegramApi,
    private val telegramReportChat: TelegramReportChat,
    private val vkApi: VkApi,
    private val imagesApi: ImagesApi,
    private val discogsApi: DiscogsApi,
    private val vkManagerCommunity: VkCommunity,
    private val vkContentProvider: VkContentProvider,
) {
    @Action(description = "Собирает актуальное состояние контента в сообществе для принятия дальнейших решений")
    fun collectCommunityContent(): ActualCommunityContent {
        val lastCommunityWallposts =
            vkApi
                .getLastWallposts(
                    domain = vkManagerCommunity.domain,
                    count = 10,
                ).orThrow()
                .response.items
                .map { post ->
                    if (post.comments.count > 0) {
                        return@map CommunityWallpost(
                            wallpost = post,
                            comments =
                                vkApi(
                                    GetComments(
                                        ownerId = vkManagerCommunity.id,
                                        postId = post.id,
                                    ),
                                ).orThrow().response.items,
                        )
                    }

                    CommunityWallpost(
                        wallpost = post,
                        comments = listOf(),
                    )
                }

        return ActualCommunityContent(lastCommunityWallposts)
    }

    @Action(description = "Агент принимает решение о том, следует ли совершить публикацию")
    fun givesNewContentPublishVerdict(
        operationContext: OperationContext,
        actualCommunityContent: ActualCommunityContent,
    ): PublishNewContentVerdictSomeOf =
        operationContext
            .ai()
            .withLlm(YabarsikLlmModels.GENERIC_MODEL.modelName)
            .withPromptContributor(YabarsikPromptContributors.mediaCommunityManager)
            .rendering("publishNewContentVerdict.jinja")
            .createObject(
                PublishNewContentVerdict::class.java,
                mapOf(
                    "content" to actualCommunityContent,
                    "nowDateString" to Instant.now().toString(),
                ),
            ).let { verdict ->
                telegramApi.sendMessage(
                    chatId = telegramReportChat.chatId,
                    message = verdict.modelResultExplanation,
                )
                if (verdict.shouldPublish) {
                    PublishNewContentVerdictSomeOf(
                        shouldPublishNewContent = ShouldPublishNewContent(verdict.modelResultExplanation),
                    )
                } else {
                    PublishNewContentVerdictSomeOf(
                        shouldNotPublishNewContext = ShouldNotPublishNewContext(verdict.modelResultExplanation),
                    )
                }
            }

    @AchievesGoal(description = "Агент решил не публиковать новый контент")
    @Action
    fun doNotPublishAnyContent(verdict: ShouldNotPublishNewContext): VkCommunityContentManagerAgentResult =
        VkCommunityContentManagerAgentResult(verdict.modelResultExplanation)

    @Action(description = "Отбирает подходящее музыкально сопровождение для публикации")
    fun findAppropriateMusicMedia(operationContext: OperationContext): AppropriateMusicMediaAttachment {
        val wallpostsCountMemoizationPerDomain = mutableMapOf<String, Int>()
        val collectedAttachments =
            sequence {
                repeat(5) {
                    val domain = vkContentProvider.getRandomByMedia(ContentMedia.MUSIC).domain
                    val response =
                        vkApi
                            .takeAttachmentsRandomly(
                                domain = domain,
                                count = 2,
                                type = VkWallpostsAttachmentType.AUDIO,
                                domainWallpostsCount = wallpostsCountMemoizationPerDomain[domain],
                            ).orThrow()
                    wallpostsCountMemoizationPerDomain[domain] = response.totalWallpostsCount
                    response.attachments.forEach { yield(it) }
                }
            }.toList()

        val openOwners = vkApi.getOnlyOpenOwners(collectedAttachments.map { it.audio!!.ownerId })

        val attachments = collectedAttachments.filter { it.audio!!.ownerId in openOwners }

        if (attachments.size <= 2) {
            telegramApi.sendMessage(
                chatId = telegramReportChat.chatId,
                message = "Не получилось найти подходящее количество треков для ранжирования, возможно, попробую позже еще раз",
            )
            throw RuntimeException("findAppropriateMusicMedia: there's not enough (<2) media attachments for LLM ranking")
        }

        val discogsArtistReleases: List<ArtistReleases> =
            sequence {
                attachments
                    .asSequence()
                    .map { it.audio!!.artist to it.audio.title }
                    .distinct()
                    .mapNotNull { (artist, track) ->
                        discogsApi
                            .getArtistReleases(
                                artist = artist,
                                track = track,
                            ).valueOrNull()
                    }.forEach { yield(it) }
                attachments
                    .asSequence()
                    .map { it.audio!!.artist }
                    .distinct()
                    .mapNotNull { artist ->
                        discogsApi
                            .getArtistReleases(
                                artist = artist,
                            ).valueOrNull()
                    }.forEach { yield(it) }
            }.groupBy { it.artist }
                .entries
                .asSequence()
                .map { (artist, releases) ->
                    ArtistReleases(
                        artist = artist,
                        releases = releases.flatMap { it.releases }.distinctBy { it.title },
                    )
                }.filter { it.releases.isNotEmpty() }
                .toList()

        data class LastAudioTrack(
            val dateString: String,
            val artist: String,
            val title: String,
        )

        val lastAudioTracks =
            vkApi
                .getLastWallposts(
                    domain = vkManagerCommunity.domain,
                    count = 100,
                ).map { it.response.items }
                .recover { listOf() }
                .asSequence()
                .filter {
                    it.attachments.isNotEmpty() &&
                        it.attachments.any {
                            it.audio != null
                        }
                }.flatMap { wallpost ->
                    wallpost.attachments
                        .asSequence()
                        .filter { it.audio != null }
                        .map { attachment ->
                            LastAudioTrack(
                                dateString = wallpost.dateString,
                                artist = attachment.audio!!.artist,
                                title = attachment.audio.title,
                            )
                        }
                }.toList()

        return operationContext
            .ai()
            .withLlm(YabarsikLlmModels.GENERIC_MODEL.modelName)
            .withPromptContributor(YabarsikPromptContributors.mediaCommunityManager)
            .rendering("findAppropriateMusicMedia.jinja")
            .createObject(
                LlmAppropriateMusicMedia::class.java,
                mapOf(
                    "attachments" to attachments,
                    "discogsArtistReleases" to discogsArtistReleases,
                    "lastAudioTracks" to lastAudioTracks,
                    "nowDateString" to Instant.now().toString(),
                ),
            ).let { llmChoice ->
                AppropriateMusicMediaAttachment(
                    attachment =
                        attachments.first {
                            it.audio!!.id == llmChoice.id &&
                                it.audio.ownerId == llmChoice.ownerId
                        },
                    llmChoice = llmChoice,
                )
            }
    }

    @Action(description = "Отбирает подходящее изображение для публикации")
    fun findAppropriateImageMedia(operationContext: OperationContext): AppropriateImageMediaAttachment {
        val previousChoiceImages =
            vkApi
                .getLastWallposts(
                    domain = vkManagerCommunity.domain,
                    count = 100,
                ).map { it.response.items }
                .recover { listOf() }
                .asSequence()
                .filter {
                    it.attachments.isNotEmpty() &&
                        it.attachments.any { image ->
                            image.photo != null
                        }
                }.flatMap {
                    it.attachments
                        .asSequence()
                        .filter { it.photo != null }
                        .map(VkWallpostAttachment::formAttachmentId)
                }.toSet()

        val wallpostsCountMemoizationPerDomain = mutableMapOf<String, Int>()
        val collectedAttachments =
            sequence {
                repeat(5) {
                    val domain = vkContentProvider.getRandomByMedia(ContentMedia.IMAGES).domain
                    val response =
                        vkApi
                            .takeAttachmentsRandomly(
                                domain = domain,
                                count = 2,
                                type = VkWallpostsAttachmentType.PHOTO,
                                domainWallpostsCount = wallpostsCountMemoizationPerDomain[domain],
                                excludeWallpostAttachments = previousChoiceImages,
                            ).orThrow()
                    wallpostsCountMemoizationPerDomain[domain] = response.totalWallpostsCount
                    response.attachments.forEach { yield(it) }
                }
            }.toList()

        val openOwners = vkApi.getOnlyOpenOwners(collectedAttachments.map { it.photo!!.ownerId })

        val attachments = collectedAttachments.filter { it.photo!!.ownerId in openOwners }

        if (attachments.size <= 2) {
            telegramApi.sendMessage(
                chatId = telegramReportChat.chatId,
                message = "Не получилось найти подходящее количество картинок для ранжирования, возможно, попробую позже еще раз",
            )
            throw RuntimeException("there's not enough (<2) media attachments for LLM ranking")
        }

        return attachments
            .windowed(size = 3, step = 3, partialWindows = true)
            .asSequence()
            .flatMap { attachments ->
                operationContext
                    .ai()
                    .withLlm(YabarsikLlmModels.MULI_MODAL_GENERIC_MODEL.modelName)
                    .withPromptContributor(YabarsikPromptContributors.mediaCommunityManager)
                    .withImages(
                        attachments.map { attachment ->
                            imagesApi(GetImage(attachment.photo!!.origPhoto!!.url)).orThrow().let {
                                AgentImage.create(
                                    mimeType = it.mimeType,
                                    data = it.bytes,
                                )
                            }
                        },
                    ).rendering("findAppropriateImageMedia.jinja")
                    .createObject(
                        LlmAppropriateImagesMedia::class.java,
                        mapOf(
                            "attachments" to attachments,
                        ),
                    ).images
            }.firstOrNull {
                it.modelChoice
            }?.let { llmChoice ->
                AppropriateImageMediaAttachment(
                    attachment =
                        attachments.first {
                            it.photo!!.id == llmChoice.id &&
                                it.photo.ownerId == llmChoice.ownerId
                        },
                    llmChoice = llmChoice,
                )
            }
            ?: throw RuntimeException("LLM didn't recognize any appropriate image")
    }

    @AchievesGoal(description = "Создается свежая публикация в сообществе")
    @Action
    fun publishNewWallpost(
        appropriateMusicMedia: AppropriateMusicMediaAttachment,
        appropriateImageMedia: AppropriateImageMediaAttachment,
    ): VkCommunityContentManagerAgentResult {
        val createdWallpost =
            vkApi(
                PostWallpost(
                    ownerId = vkManagerCommunity.id,
                    attachments =
                        listOf(
                            appropriateMusicMedia.let {
                                VkPostWallpostAttachment(
                                    type = VkWallpostsAttachmentType.AUDIO,
                                    ownerId = it.llmChoice.ownerId,
                                    mediaId = it.llmChoice.id,
                                )
                            },
                            appropriateImageMedia.let {
                                VkPostWallpostAttachment(
                                    type = VkWallpostsAttachmentType.PHOTO,
                                    ownerId = it.llmChoice.ownerId,
                                    mediaId = it.llmChoice.id,
                                )
                            },
                        ),
                ),
            ).orThrow()
        val agentResult =
            """
            [Создан пост #${createdWallpost.response.postId}](https://vk.com/${vkManagerCommunity.domain}?w=wall${vkManagerCommunity.id}_${createdWallpost.response.postId})
            
            [картиночка](${appropriateImageMedia.attachment.photo!!.origPhoto!!.url}) (ownerId=${appropriateMusicMedia.llmChoice.ownerId}, id=${appropriateMusicMedia.llmChoice.id})
            ${appropriateImageMedia.llmChoice.modelChoiceExplanation}
            
            Трек: ${appropriateMusicMedia.attachment.audio!!.artist} — ${appropriateMusicMedia.attachment.audio.title} (ownerId=${appropriateMusicMedia.llmChoice.ownerId}, id=${appropriateMusicMedia.llmChoice.id})
            ${appropriateMusicMedia.llmChoice.modelChoiceExplanation}
            """.trimIndent()

        telegramApi.sendMessage(
            chatId = telegramReportChat.chatId,
            message = agentResult,
        )
        return VkCommunityContentManagerAgentResult(agentResult)
    }
}
