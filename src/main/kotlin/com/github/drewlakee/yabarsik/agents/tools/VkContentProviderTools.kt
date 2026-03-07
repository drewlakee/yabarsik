package com.github.drewlakee.yabarsik.agents.tools

import com.embabel.agent.api.annotation.LlmTool
import com.embabel.agent.api.common.AgentImage
import com.embabel.common.textio.template.TemplateRenderer
import com.github.drewlakee.yabarsik.images.GetImage
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.VkWallposts
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

data class RandomPhoto(
    val agentImage: AgentImage,
    val attachment: VkWallposts.VkWallpostsResponse.VkWallpostsItem.VkWallpostsAttachment,
)

class VkContentProviderTools(
    private val vkContentProvider: VkContentProvider,
    private val vkManagerCommunity: VkCommunity,
    private val vkApi: VkApi,
    private val imagesApi: ImagesApi,
    private val templateRenderer: TemplateRenderer,
) {
    // Utility tool due to the reason that tools are text-based for LLMs, and OpenAI, for example, has specific API for images
    fun findRandomImagesLazySequence(): Sequence<RandomPhoto> {
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
            throw RuntimeException("there's not enough (<2) media attachments for LLM ranking")
        }

        return attachments.asSequence().map { attachment ->
            RandomPhoto(
                agentImage =
                    imagesApi(GetImage(attachment.photo!!.origPhoto!!.url)).orThrow().let {
                        AgentImage.create(
                            mimeType = it.mimeType,
                            data = it.bytes,
                        )
                    },
                attachment = attachment,
            )
        }
    }

    @LlmTool(
        name = "find-available-audio-tracks",
        description = "Ищет треки по известным источникам, которые доступны для публикации в посте",
    )
    fun findAudioTracks(
        @LlmTool.Param(
            description = "Запрашиваемое количество треков для поиска. Желательно не больше 15. В результате может прийти не меньше 2",
        ) limitTracks: Int,
        @LlmTool.Param(
            description = "Сколько стоит брать из каждого источника, чтобы был больше разброс между исполнителями. Желательно не больше 3",
        ) limitByTracksSource: Int,
    ): String {
        val repeat = limitTracks / limitByTracksSource
        val wallpostsCountMemoizationPerDomain = mutableMapOf<String, Int>()
        val collectedAttachments =
            sequence {
                repeat(repeat) {
                    val domain = vkContentProvider.getRandomByMedia(ContentMedia.MUSIC).domain
                    val response =
                        vkApi
                            .takeAttachmentsRandomly(
                                domain = domain,
                                count = limitByTracksSource,
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
            throw RuntimeException("findAppropriateMusicMedia: there's not enough (<2) media attachments for LLM ranking")
        }

        return templateRenderer.renderLoadedTemplate(
            "classpath:/templates/find-available-audio-tracks-tool.jinja",
            mapOf(
                "attachments" to attachments,
            ),
        )
    }
}
