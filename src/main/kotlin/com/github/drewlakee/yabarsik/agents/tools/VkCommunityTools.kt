package com.github.drewlakee.yabarsik.agents.tools

import com.embabel.agent.api.annotation.LlmTool
import com.embabel.common.textio.template.TemplateRenderer
import com.github.drewlakee.yabarsik.telegram.chat.appendNewLine
import com.github.drewlakee.yabarsik.vk.api.GetComments
import com.github.drewlakee.yabarsik.vk.api.GetWallposts
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.VkWallpostComments.Response.VkWallpostComment
import com.github.drewlakee.yabarsik.vk.api.VkWallposts.VkWallpostsResponse.VkWallpostsItem
import com.github.drewlakee.yabarsik.vk.api.getLastWallposts
import com.github.drewlakee.yabarsik.vk.community.VkCommunity
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.valueOrNull

private data class VkCommunityWallpost(
    val wallpost: VkWallpostsItem,
    val comments: List<VkWallpostComment>,
)

private data class AudioTrack(
    val dateString: String,
    val artist: String,
    val title: String,
)

private data class Photo(
    val dateString: String,
    val id: String,
    val ownerId: String,
)

class VkCommunityTools(
    private val vkApi: VkApi,
    private val vkManagerCommunity: VkCommunity,
    private val templateRenderer: TemplateRenderer,
) {
    @LlmTool(
        name = "get-recently-posted-audio-tracks",
        description = "Получает уже ранее опубликованные треки в сообществе",
    )
    fun getRecentlyPostedAudioTracks(
        @LlmTool.Param(description = "Запрашиваемое количество постов. Максимум 100 на один вызов") limit: Int,
    ): String =
        vkApi
            .getLastWallposts(
                domain = vkManagerCommunity.domain,
                count = limit,
            ).let { result ->
                when (result) {
                    is Failure<*> -> {
                        "Произошла сетевая ошибка. Контент не получилось получить из постов с limit=$limit"
                    }

                    is Success<*> -> {
                        buildString {
                            val audioTracks =
                                result
                                    .valueOrNull()!!
                                    .response.items
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
                                                AudioTrack(
                                                    dateString = wallpost.dateString,
                                                    artist = attachment.audio!!.artist,
                                                    title = attachment.audio.title,
                                                )
                                            }
                                    }.toList()

                            append(
                                templateRenderer.renderLoadedTemplate(
                                    "classpath:/templates/get-recently-posted-audio-tracks-tool.jinja",
                                    mapOf(
                                        "audioTracks" to audioTracks,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }

    @LlmTool(
        name = "get-managed-community-wallposts",
        description = "Получает посты со стены сообщества, в котором публикуются посты. В ответе будут лайки, репосты, комментарии",
    )
    fun getCommunityWallposts(
        @LlmTool.Param(description = "Смещение по постам. Если 0, то посты берутся с последнего") offset: Int,
        @LlmTool.Param(description = "Запрашиваемое количество постов. Максимум 100 на один вызов") limit: Int,
    ): String =
        vkApi
            .invoke(
                GetWallposts(
                    domain = vkManagerCommunity.domain,
                    offset = offset,
                    count = limit,
                ),
            ).let { result ->
                when (result) {
                    is Failure<*> -> {
                        "Произошла сетевая ошибка. Посты не получилось получить с offset=$offset limit=$limit"
                    }

                    is Success<*> -> {
                        buildString {
                            appendLine(
                                "Посты получены с offset=$offset limit=$limit. Всего в сообществе постов count=${result.valueOrNull()!!.response.count}",
                            )
                            appendNewLine()

                            val communityWallposts =
                                result.valueOrNull()!!.response.items.map { wallpost ->
                                    if (wallpost.comments.count > 0) {
                                        return@map VkCommunityWallpost(
                                            wallpost = wallpost,
                                            comments =
                                                vkApi(
                                                    GetComments(
                                                        ownerId = vkManagerCommunity.id,
                                                        postId = wallpost.id,
                                                    ),
                                                ).orThrow().response.items,
                                        )
                                    }

                                    VkCommunityWallpost(
                                        wallpost = wallpost,
                                        comments = listOf(),
                                    )
                                }

                            append(
                                templateRenderer.renderLoadedTemplate(
                                    "classpath:/templates/get-managed-community-wallposts-tool.jinja",
                                    mapOf(
                                        "communityWallposts" to communityWallposts,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
}
