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

data class DownloadedImage(
    val url: String,
    val mimeType: String,
    val base64String: String,
    val bytes: ByteArray,
)

class GetImage(
    val url: String,
) : ImagesApiAction<DownloadedImage> {
    override fun toRequest() = Request(Method.GET, uri = url)

    override fun toResult(response: Response): Result4k<DownloadedImage, RemoteRequestFailed> =
        when (response.status) {
            Status.OK -> {
                val imageType = ImagesApiAction.getImageType(response.body.payload.array()).name.lowercase()
                Success(
                    DownloadedImage(
                        url = url,
                        mimeType = "image/$imageType",
                        bytes = response.body.payload.array(),
                        base64String =
                            with(response.body) {
                                val encodedImage = Base64.getEncoder().encodeToString(response.body.payload.array())
                                "data:image/$imageType;base64,$encodedImage"
                            },
                    ),
                )
            }

            else -> {
                Failure(RemoteRequestFailed(response.status, response.bodyString()))
            }
        }
}
