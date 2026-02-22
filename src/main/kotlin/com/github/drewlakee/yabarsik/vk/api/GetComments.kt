package com.github.drewlakee.yabarsik.vk.api

import com.github.drewlakee.yabarsik.logError
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.time.Instant

data class VkWallpostComments(
    val response: Response,
) {
    data class Response(
        val count: Int,
        val items: List<VkWallpostComment>,
    ) {
        data class VkWallpostComment(
            val date: Long,
            val text: String,
        ) {
            val dateString = Instant.ofEpochSecond(date).toString()
        }
    }
}

data class GetComments(
    val ownerId: Int,
    val postId: Int,
) : VkApiAction<VkWallpostComments> {
    override fun apiAccessToken(): VkAccessToken = VkAccessToken.SERVICE

    override fun toRequest(): Request =
        Request(Method.POST, "/method/wall.getComments")
            .body(
                listOf(
                    "owner_id=$ownerId",
                    "post_id=$postId",
                    "v=5.199",
                ).joinToString(separator = "&"),
            )

    override fun toResult(response: Response): Result4k<VkWallpostComments, RemoteRequestFailed> =
        when (response.status) {
            Status.OK -> {
                runCatching { VkApiAction.jsonTo<VkWallpostComments>(response.body) }
                    .let {
                        if (it.isSuccess) {
                            Success(it.getOrNull()!!)
                        } else {
                            it.exceptionOrNull()?.run(::logError)
                            Failure(RemoteRequestFailed(response.status, response.bodyString()))
                        }
                    }
            }

            else -> {
                Failure(RemoteRequestFailed(response.status, response.bodyString()))
            }
        }
}
