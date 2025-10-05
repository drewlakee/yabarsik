// https://dev.vk.com/ru/method/groups.getById
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

data class VkGroups(
    val response: Response,
) {
    data class Response(
        val groups: List<Group>,
    ) {
        data class Group(
            val id: Int,
            val name: String,
            @field:JsonProperty("screen_name") val screenName: String,
            @field:JsonProperty("is_closed") val isClosed: Int,
        )
    }
}

data class GetGroups(val groupIds: List<Int>): VkApiAction<VkGroups> {
    override fun toRequest() = Request(Method.POST, "/method/groups.getById")
        .body(
            listOf(
                "access_token=$VK_SERVICE_ACCESS_TOKEN",
                "group_ids=${groupIds.joinToString( ",")}",
                "v=5.199",
            ).filter { it != null }.joinToString(separator = "&"),
        )

    override fun toResult(response: Response): Result4k<VkGroups, RemoteRequestFailed> =
        when (response.status) {
            Status.OK -> runCatching { VkApiAction.jsonTo<VkGroups>(response.body) }
                .let {
                    if (it.isSuccess) {
                        Success(it.getOrNull()!!)
                    } else {
                        it.exceptionOrNull()?.run(::logError)
                        Failure(RemoteRequestFailed(response.status, response.bodyString()))
                    }
                }
            else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
        }
}
