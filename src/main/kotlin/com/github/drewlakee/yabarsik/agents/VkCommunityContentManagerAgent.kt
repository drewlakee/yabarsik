package com.github.drewlakee.yabarsik.agents

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.SomeOf
import com.embabel.common.ai.model.LlmOptions
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.github.drewlakee.yabarsik.agents.tools.DiscogsTools
import com.github.drewlakee.yabarsik.agents.tools.VkCommunityTools
import com.github.drewlakee.yabarsik.agents.tools.VkContentProviderTools
import com.github.drewlakee.yabarsik.configuration.YabarsikLlmModels
import com.github.drewlakee.yabarsik.prompt.YabarsikPromptContributors
import com.github.drewlakee.yabarsik.vk.api.PostWallpost
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.VkPostWallpostAttachment
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType
import com.github.drewlakee.yabarsik.vk.community.VkCommunity
import dev.forkhandles.result4k.orThrow
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

sealed interface VkCommunityContentManagerAgentResult {
    data class AchievedGoal(
        val message: String,
    ) : VkCommunityContentManagerAgentResult

    data class IntermediateResult(
        val message: String,
    ) : VkCommunityContentManagerAgentResult
}

data class LlmAppropriateMusicMedia(
    @get:JsonPropertyDescription("Идентификатор владельца трека (ownerId)")
    val ownerId: Int,
    @get:JsonPropertyDescription("Идентификатор самого трека (id)")
    val id: Int,
    @get:JsonPropertyDescription("Название исполнителя")
    val artist: String,
    @get:JsonPropertyDescription("Название трека")
    val track: String,
    @get:JsonPropertyDescription("Причина по которой был сделан выбор")
    val modelChoiceExplanation: String,
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
    @get:JsonPropertyDescription("Ссылка на картинку")
    val url: String,
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
    private val vkApi: VkApi,
    private val vkManagerCommunity: VkCommunity,
    private val vkCommunityTools: VkCommunityTools,
    private val discogsTools: DiscogsTools,
    private val vkContentProviderTools: VkContentProviderTools,
) {
    @Action(description = "Агент принимает решение о том, следует ли совершить публикацию")
    fun givesNewContentPublishVerdict(operationContext: OperationContext): PublishNewContentVerdictSomeOf =
        operationContext
            .ai()
            .withLlm(
                LlmOptions().apply {
                    model = YabarsikLlmModels.GENERIC_MODEL.modelName
                    temperature = 0.6
                },
            ).withToolObject(vkCommunityTools)
            .withPromptContributor(YabarsikPromptContributors.mediaCommunityManager)
            .rendering("publishNewContentVerdict.jinja")
            .createObject(
                PublishNewContentVerdict::class.java,
                mapOf(
                    "nowDateString" to Instant.now().toString(),
                ),
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
        VkCommunityContentManagerAgentResult.IntermediateResult(verdict.modelResultExplanation)

    @Action(description = "Отбирает подходящее музыкально сопровождение для публикации")
    fun findAppropriateMusicMedia(operationContext: OperationContext): LlmAppropriateMusicMedia =
        operationContext
            .ai()
            .withLlm(
                LlmOptions().apply {
                    model = YabarsikLlmModels.GENERIC_MODEL.modelName
                    temperature = 0.6
                },
            ).withToolObjects(
                vkCommunityTools,
                vkContentProviderTools,
                discogsTools,
            ).withPromptContributor(YabarsikPromptContributors.mediaCommunityManager)
            .rendering("findAppropriateMusicMedia.jinja")
            .createObject(
                LlmAppropriateMusicMedia::class.java,
                mapOf(
                    "nowDateString" to Instant.now().toString(),
                ),
            )

    @Action(description = "Отбирает подходящее изображение для публикации")
    fun findAppropriateImageMedia(operationContext: OperationContext): LlmAppropriateImageMedia =
        vkContentProviderTools
            .findRandomImagesLazySequence()
            .windowed(size = 3, step = 3, partialWindows = true)
            .flatMap { images ->
                operationContext
                    .ai()
                    .withLlm(
                        LlmOptions().apply {
                            model = YabarsikLlmModels.MULI_MODAL_GENERIC_MODEL.modelName
                            temperature = 0.6
                        },
                    ).withPromptContributor(YabarsikPromptContributors.mediaCommunityManager)
                    .withImages(images.map { it.agentImage })
                    .rendering("findAppropriateImageMedia.jinja")
                    .createObject(
                        LlmAppropriateImagesMedia::class.java,
                        mapOf(
                            "attachments" to images.map { it.attachment },
                        ),
                    ).images
            }.firstOrNull {
                it.modelChoice
            } ?: throw RuntimeException("LLM didn't recognize any appropriate image")

    @AchievesGoal(description = "Создается свежая публикация в сообществе")
    @Action
    fun publishNewWallpost(
        shouldPublishNewContent: ShouldPublishNewContent,
        appropriateMusicMedia: LlmAppropriateMusicMedia,
        appropriateImageMedia: LlmAppropriateImageMedia,
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
                                    ownerId = it.ownerId,
                                    mediaId = it.id,
                                )
                            },
                            appropriateImageMedia.let {
                                VkPostWallpostAttachment(
                                    type = VkWallpostsAttachmentType.PHOTO,
                                    ownerId = it.ownerId,
                                    mediaId = it.id,
                                )
                            },
                        ),
                ),
            ).orThrow()
        val agentResult =
            """
            [Создан пост #${createdWallpost.response.postId}](https://vk.com/${vkManagerCommunity.domain}?w=wall${vkManagerCommunity.id}_${createdWallpost.response.postId})
            
            ${shouldPublishNewContent.modelResultExplanation}
            
            [картиночка](${appropriateImageMedia.url}) (ownerId=${appropriateImageMedia.ownerId}, id=${appropriateImageMedia.id})
            ${appropriateImageMedia.modelChoiceExplanation}
            
            Трек: ${appropriateMusicMedia.artist} — ${appropriateMusicMedia.track} (ownerId=${appropriateMusicMedia.ownerId}, id=${appropriateMusicMedia.id})
            ${appropriateMusicMedia.modelChoiceExplanation}
            """.trimIndent()
        return VkCommunityContentManagerAgentResult.AchievedGoal(agentResult)
    }
}
