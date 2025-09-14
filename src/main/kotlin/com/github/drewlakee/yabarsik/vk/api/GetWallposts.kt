// https://dev.vk.com/ru/method/wall.get
package com.github.drewlakee.yabarsik.vk.api

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

data class VkWallposts(
    val response: VkWallpostsResponse,
) {
    data class VkWallpostsResponse(
        val count: Int,
        val items: List<VkWallpostsItem>,
    ) {
        data class VkWallpostsItem(
            val id: Int,
            val date: Long,
            @field:JsonProperty("postponed_id") val postponedId: Int?,
            val attachments: List<VkWallpostsAttachment>,
        ) {
            data class VkWallpostsAttachment(
                val type: VkWallpostsAttachmentType,
                val photo: VkWallpostsAttachmentPhoto?,
                val audio: VkWallpostsAttachmentAudio?,
            ) {

                enum class VkWallpostsAttachmentType(@get:JsonValue val type: String) {
                    PHOTO("photo"),
                    AUDIO("audio"),
                    @JsonEnumDefaultValue
                    UNKNOWN("unknown"),
                }

                data class VkWallpostsAttachmentPhoto(
                    val id: Int,
                    @field:JsonProperty("owner_id") val ownerId: Int,
                    @field:JsonProperty("orig_photo") val origPhoto: VkWallpostsAttachmentPhotoOrig,
                ) {
                    data class VkWallpostsAttachmentPhotoOrig(val height: Int, val width: Int, val url: String)
                }

                data class VkWallpostsAttachmentAudio(
                    val id: Int,
                    @field:JsonProperty("owner_id") val ownerId: Int,
                    val artist: String,
                    val title: String,
                )
            }
        }
    }
}

data class GetWallposts(val domain: String, val offset: Int = 0, val count: Int = 100): VkApiAction<VkWallposts> {
    override fun toRequest(): Request = Request(Method.POST, "/method/wall.get")
        .body(
            listOf(
                "access_token=${VkApiAction.serviceAccessToken}",
                "domain=$domain",
                "offset=$offset",
                "count=$count",
                "v=5.199",
            ).filter { it != null }.joinToString(separator = "&")
        )

    override fun toResult(response: Response): Result4k<VkWallposts, RemoteRequestFailed> = when (response.status) {
        Status.OK -> Success(VkApiAction.jsonTo(response.body))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}
