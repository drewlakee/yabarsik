// https://dev.vk.com/ru/method/wall.get
package com.github.drewlakee.yabarsik.vk.api

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.github.drewlakee.yabarsik.configuration.BarsikEnvironment.VK_SERVICE_ACCESS_TOKEN
import com.github.drewlakee.yabarsik.logError
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.peekFailure
import dev.forkhandles.result4k.recover
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

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
                data class VkWallpostsAttachmentPhoto(
                    val id: Int,
                    @field:JsonProperty("owner_id") val ownerId: Int,
                    @field:JsonProperty("orig_photo") val origPhoto: VkWallpostsAttachmentPhotoOrig?,
                ) {
                    data class VkWallpostsAttachmentPhotoOrig(
                        val height: Int,
                        val width: Int,
                        val url: String,
                    )
                }

                data class VkWallpostsAttachmentAudio(
                    val id: Int,
                    @field:JsonProperty("owner_id") val ownerId: Int,
                    val artist: String,
                    val title: String,
                    val url: String,
                )
            }
        }
    }
}

data class GetWallposts(
    val domain: String,
    val offset: Int = 0,
    val count: Int = 100,
) : VkApiAction<VkWallposts> {
    override fun toRequest(): Request =
        Request(Method.POST, "/method/wall.get")
            .body(
                listOf(
                    "access_token=$VK_SERVICE_ACCESS_TOKEN",
                    "domain=$domain",
                    "offset=${Math.max(0, offset)}",
                    "count=${Math.max(0, count.coerceAtMost(100))}",
                    "v=5.199",
                ).filter { it != null }.joinToString(separator = "&"),
            )

    override fun toResult(response: Response): Result4k<VkWallposts, RemoteRequestFailed> =
        when (response.status) {
            Status.OK -> runCatching { VkApiAction.jsonTo<VkWallposts>(response.body) }
                .let {
                    if (it.isSuccess) {
                        Success(it.getOrNull()!!)
                    } else {
                        logError(IllegalArgumentException("status=${response.status}, body=${response.bodyString()}", it.exceptionOrNull()))
                        Failure(RemoteRequestFailed(response.status, response.bodyString()))
                    }
                }
            else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
        }
}

enum class VkWallpostsAttachmentType(
    @get:JsonValue val type: String,
) {
    PHOTO("photo"),
    AUDIO("audio"),

    @JsonEnumDefaultValue
    UNKNOWN("unknown"),
}

fun VkApi.getTotalWallpostsCount(domain: String): Result4k<Int, RemoteRequestFailed> =
    invoke(
        GetWallposts(
            domain = domain,
            offset = 0,
            count = 0,
        ),
    ).map { it.response.count }

fun VkApi.getTodayWallpost(domain: String, today: LocalDate, zone: ZoneId): Result4k<VkWallposts, RemoteRequestFailed> =
    invoke(
        GetWallposts(
            domain = domain,
            offset = 0,
            count = 10,
        )
    ).map {
        VkWallposts(
            response = VkWallposts.VkWallpostsResponse(
                count = it.response.count,
                items = it.response.items.filter {
                    LocalDate.ofInstant(Instant.ofEpochSecond(it.date), zone).isEqual(today)
                },
            )
        )
    }

data class RandomVkAttachments(
    val totalWallpostsCount: Int,
    val attachments: List<VkWallposts.VkWallpostsResponse.VkWallpostsItem.VkWallpostsAttachment>,
)

fun VkApi.takeAttachmentsRandomly(
    domain: String,
    count: Int,
    type: VkWallpostsAttachmentType,
    domainWallpostsCount: Int?,
): Result4k<RandomVkAttachments, RemoteRequestFailed> =
    (domainWallpostsCount?.let(::Success) ?: getTotalWallpostsCount(domain))
        .map { totalWallpostsCount ->
            buildList {
                for (limit in 1..10) {
                    invoke(
                        GetWallposts(
                            domain = domain,
                            offset =
                                with(Random.nextInt(0, totalWallpostsCount)) {
                                    if (this + count > totalWallpostsCount) {
                                        return@with this - (this + count - totalWallpostsCount)
                                    }

                                    this
                                },
                            count = 100,
                        ),
                    ).peekFailure { logError(it.cause) }
                        .map {
                            val attachments = mutableListOf<VkWallposts.VkWallpostsResponse.VkWallpostsItem.VkWallpostsAttachment>()
                            val buffer = it.response.items.toMutableList()
                            while (attachments.size < count && buffer.isNotEmpty()) {
                                val j = Random.nextInt(buffer.size)
                                val last = buffer.removeLast()
                                val value = if (j < buffer.size) buffer.set(j, last) else last
                                with(value.attachments.firstOrNull { attachment -> attachment.type == type }) {
                                    when {
                                        this == null -> {}
                                        this.type == VkWallpostsAttachmentType.AUDIO -> if (this.audio!!.url.isNotBlank()) {
                                            attachments.add(this)
                                        }
                                        this.type == VkWallpostsAttachmentType.PHOTO -> if (this.photo!!.origPhoto != null) {
                                            attachments.add(this)
                                        }
                                        else -> attachments.add(this)
                                    }
                                }
                            }

                            attachments
                        }.recover { listOf() }
                        .forEach { attachment ->
                            if (this.size < count) {
                                add(attachment)
                            } else {
                                return@buildList
                            }
                        }
                }
            }.let {
                RandomVkAttachments(
                    totalWallpostsCount = totalWallpostsCount,
                    attachments = it,
                )
            }
        }.peekFailure { logError(it.cause) }
