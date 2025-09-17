// https://dev.vk.com/ru/method/wall.post
package com.github.drewlakee.yabarsik.vk.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.drewlakee.yabarsik.configuration.BarsikEnvironment.VK_COMMUNITY_ACCESS_TOKEN
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

data class VkPostWallpost(val response: VkPostWallpostResponse) {
    data class VkPostWallpostResponse(
        @field:JsonProperty("post_id") val postId: Int
    )
}

data class VkPostWallpostAttachment(
    val type: VkWallpostsAttachmentType,
    val ownerId: Int,
    val mediaId: Int,
) {
    override fun toString() = "${type}${ownerId}_${mediaId}"
}

data class PostWallpost(
    val ownerId: Int,
    val attachments: List<VkPostWallpostAttachment>,
) : VkApiAction<VkPostWallpost> {
    override fun toRequest() = Request(Method.POST, "/method/wall.post")
        .body(
            listOf(
                "access_token=$VK_COMMUNITY_ACCESS_TOKEN",
                "owner_id=$ownerId",
                if (attachments.isNotEmpty()) "attachments=${attachments.joinToString(",")}" else null,
                "from_group=1",
                "v=5.199",
            ).filter { it != null }.joinToString(separator = "&")
        )

    override fun toResult(response: Response): Result4k<VkPostWallpost, RemoteRequestFailed> = when(response.status) {
        Status.OK -> Success(VkApiAction.jsonTo(response.body))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}