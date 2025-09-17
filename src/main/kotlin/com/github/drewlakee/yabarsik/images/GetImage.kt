package com.github.drewlakee.yabarsik.images

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.Base64

data class Image(
    val url: String,
    val base64String: String,
)

class GetImage(val url: String) : ImagesApiAction<Image> {
    override fun toRequest() = Request(Method.GET, uri = url)

    override fun toResult(response: Response): Result4k<Image, RemoteRequestFailed> = when(response.status) {
        Status.OK -> Success(Image(
            url = url,
            base64String = with(response.body) {
                val imageType = ImagesApiAction.getImageType(response.body.payload.array()).name.lowercase()
                val encodedImage = Base64.getEncoder().encodeToString(response.body.payload.array())
                "data:image/$imageType;base64,$encodedImage"
            }
        ))
        else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
    }
}
