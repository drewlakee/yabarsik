// https://yandex.cloud/ru/docs/functions/concepts/function-invoke
package com.github.drewlakee.yabarsik

import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.discogs.api.Http
import com.github.drewlakee.yabarsik.images.Http
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.scenario.play
import com.github.drewlakee.yabarsik.scenario.vk.DailyScheduleWatching
import com.github.drewlakee.yabarsik.telegram.api.Http
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.vk.api.Http
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.yandex.llm.api.Http
import com.github.drewlakee.yabarsik.yandex.llm.api.YandexLlmModelsApi
import com.github.drewlakee.yabarsik.yandex.s3.api.Http
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api
import yandex.cloud.sdk.functions.Context
import yandex.cloud.sdk.functions.YcFunction

class Request()

data class Response(val message: String) {
    object Status {
        val OK = Response("OK")
    }
}

class YcHandler : YcFunction<Request, Response> {
    override fun handle(request: Request, context: Context): Response {
        val barsik = runCatching {
            Barsik(
                context = context,
                telegramApi = TelegramApi.Http(),
                yandexS3Api = YandexS3Api.Http(),
                yandexLlmModelsApi = YandexLlmModelsApi.Http(),
                vkApi = VkApi.Http(),
                imagesApi = ImagesApi.Http(),
                discogsApi = DiscogsApi.Http(),
            )
        }

        if (barsik.isFailure) {
            barsik.exceptionOrNull()?.run(::logError)
            return Response("ERROR: ${barsik.exceptionOrNull()?.message ?: "Unknown error"}")
        }

        with(barsik.getOrThrow()) {
            play(DailyScheduleWatching()).run {
                if (sendTelegramMessage()) {
                    this@with.sendTelegramMessage(
                        message() + "\n\n${getTelegramFormattedTraceLinK()}"
                    )
                }
            }
        }

        return Response.Status.OK
    }
}