package com.github.drewlakee.yabarsik.agents

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.SomeOf
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.github.drewlakee.yabarsik.configuration.YabarsikLlmModels
import com.github.drewlakee.yabarsik.discogs.api.ArtistReleases
import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.discogs.api.getArtistReleases
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.prompt.YabarsikPromptContributors
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.vk.api.GetComments
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.VkWallpostComments.Response.VkWallpostComment
import com.github.drewlakee.yabarsik.vk.api.VkWallposts.VkWallpostsResponse.VkWallpostsItem
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType
import com.github.drewlakee.yabarsik.vk.api.getLastWallposts
import com.github.drewlakee.yabarsik.vk.api.getOnlyOpenOwners
import com.github.drewlakee.yabarsik.vk.api.takeAttachmentsRandomly
import com.github.drewlakee.yabarsik.vk.community.VkCommunity
import com.github.drewlakee.yabarsik.vk.content.ContentMedia
import com.github.drewlakee.yabarsik.vk.content.VkContentProvider
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.recover
import dev.forkhandles.result4k.valueOrNull

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

data class AppropriateMusicMedia(
    @get:JsonPropertyDescription("Идентификатор владельца трека (ownerId)")
    val ownerId: String,
    @get:JsonPropertyDescription("Идентификатор самого трекаы (id)")
    val id: String,
    @get:JsonPropertyDescription("Причина по которой был выбран этот трек")
    val chooseModelExplanation: String,
)

@Agent(
    name = "Агент по управлению контентом в сообществе Вконтакте",
    description = "Занимается поиском подходящего контента и принимает решение о его публикации",
)
class VkCommunityContentManagerAgent(
    private val telegramApi: TelegramApi,
    private val vkApi: VkApi,
    private val imagesApi: ImagesApi,
    private val discogsApi: DiscogsApi,
    private val vkCommunity: VkCommunity,
    private val vkContentProvider: VkContentProvider,
) {
    @Action(description = "Собирает актуальное состояние контента в сообществе для принятия дальнейших решений")
    fun collectCommunityContent(): ActualCommunityContent {
        val lastCommunityWallposts =
            vkApi
                .getLastWallposts(
                    domain = vkCommunity.domain,
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
                                        ownerId = vkCommunity.id,
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
        // PublishNewContentVerdictSomeOf(
        //     shouldPublishNewContent = ShouldPublishNewContent("verdict.modelResultExplanation"),
        // )
        operationContext
            .ai()
            .withLlm(YabarsikLlmModels.GENERIC_MODEL.modelName)
            .withPromptContributor(YabarsikPromptContributors.mediaCommunityManager)
            .rendering("publishNewContentVerdict.jinja")
            .createObject(
                PublishNewContentVerdict::class.java,
                mapOf("content" to actualCommunityContent),
            ).let { verdict ->
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
    fun findAppropriateMusicMedia(operationContext: OperationContext): AppropriateMusicMedia {
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
            throw RuntimeException("findAppropriateMusicMedia: there's not enough (<2) media attachments for ranking")
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
                    domain = vkCommunity.domain,
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
            .rendering("findAppropriateMusicMedia.jinja")
            .createObject(
                AppropriateMusicMedia::class.java,
                mapOf(
                    "attachments" to attachments,
                    "discogsArtistReleases" to discogsArtistReleases,
                    "lastAudioTracks" to lastAudioTracks,
                ),
            )
    }

    // @AchievesGoal(description = "")
    // @Action
    // fun testTest(obj: AppropriateMusicMedia): VkCommunityContentManagerAgentResult = VkCommunityContentManagerAgentResult("T")
}
