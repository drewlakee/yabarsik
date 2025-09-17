package com.github.drewlakee.yabarsik.images

import dev.forkhandles.result4k.Result4k
import org.http4k.client.OkHttp
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.connect.Action

enum class ImageType {
    JPEG, PNG, GIF, BMP, UNKNOWN
}

interface ImagesApiAction<R> : Action<Result4k<R, RemoteRequestFailed>> {

    companion object {
        fun getImageType(byteArray: ByteArray): ImageType {
            if (byteArray.size < 8) { // Minimum size to check for common headers
                return ImageType.UNKNOWN
            }

            // JPEG: Starts with FF D8 FF
            if (byteArray[0].toUByte() == 0xFF.toUByte() &&
                byteArray[1].toUByte() == 0xD8.toUByte() &&
                byteArray[2].toUByte() == 0xFF.toUByte()) {
                return ImageType.JPEG
            }

            // PNG: Starts with 89 50 4E 47 0D 0A 1A 0A
            if (byteArray[0].toUByte() == 0x89.toUByte() &&
                byteArray[1].toUByte() == 0x50.toUByte() &&
                byteArray[2].toUByte() == 0x4E.toUByte() &&
                byteArray[3].toUByte() == 0x47.toUByte() &&
                byteArray[4].toUByte() == 0x0D.toUByte() &&
                byteArray[5].toUByte() == 0x0A.toUByte() &&
                byteArray[6].toUByte() == 0x1A.toUByte() &&
                byteArray[7].toUByte() == 0x0A.toUByte()) {
                return ImageType.PNG
            }

            // GIF: Starts with 47 49 46 38 37 61 or 47 49 46 38 39 61
            if (byteArray[0].toUByte() == 0x47.toUByte() &&
                byteArray[1].toUByte() == 0x49.toUByte() &&
                byteArray[2].toUByte() == 0x46.toUByte() &&
                byteArray[3].toUByte() == 0x38.toUByte() &&
                (byteArray[4].toUByte() == 0x37.toUByte() || byteArray[4].toUByte() == 0x39.toUByte()) &&
                byteArray[5].toUByte() == 0x61.toUByte()) {
                return ImageType.GIF
            }

            // BMP: Starts with 42 4D
            if (byteArray[0].toUByte() == 0x42.toUByte() &&
                byteArray[1].toUByte() == 0x4D.toUByte()) {
                return ImageType.BMP
            }

            return ImageType.UNKNOWN
        }
    }
}

interface ImagesApi {
    operator fun <R : Any> invoke(action: ImagesApiAction<R>): Result4k<R, RemoteRequestFailed>

    companion object
}

fun ImagesApi.Companion.Http() = object : ImagesApi {
    private val http = OkHttp()

    override fun <R : Any> invoke(action: ImagesApiAction<R>): Result4k<R, RemoteRequestFailed> =
        action.toResult(http(action.toRequest()))
}