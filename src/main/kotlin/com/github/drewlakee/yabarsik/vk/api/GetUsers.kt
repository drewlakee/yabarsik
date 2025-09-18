package com.github.drewlakee.yabarsik.vk.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.drewlakee.yabarsik.configuration.BarsikEnvironment.VK_SERVICE_ACCESS_TOKEN
import com.github.drewlakee.yabarsik.logError
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

data class VkUsers(
    @field:JsonProperty("response") val users: List<User>,
) {
    data class User(
        val id: Int,
        @field:JsonProperty("can_see_audio") val canSeeAudio: Int,
        @field:JsonProperty("screen_name") val screenName: String,
        @field:JsonProperty("first_name") val firstName: String,
        @field:JsonProperty("last_name") val lastName: String,
        @field:JsonProperty("can_access_closed") val canAccessClosed: Boolean,
        @field:JsonProperty("is_closed") val isClosed: Boolean,
    )
}

data class GetUsers(val userIds: List<Int>): VkApiAction<VkUsers> {
    override fun toRequest() = Request(Method.POST, "/method/users.get")
        .body(
            listOf(
                "user_ids=${userIds.joinToString(",")}",
                "access_token=$VK_SERVICE_ACCESS_TOKEN",
                "fields=can_see_audio,screen_name",
                "v=5.199",
            ).filter { it != null }.joinToString(separator = "&"),
        )

    override fun toResult(response: Response): Result4k<VkUsers, RemoteRequestFailed> =
        when (response.status) {
            Status.OK -> runCatching { VkApiAction.jsonTo<VkUsers>(response.body) }
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