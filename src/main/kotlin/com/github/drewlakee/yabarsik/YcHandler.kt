// https://yandex.cloud/ru/docs/functions/concepts/function-invoke
package com.github.drewlakee.yabarsik

import com.github.drewlakee.yabarsik.scenario.play
import com.github.drewlakee.yabarsik.scenario.vk.DailyScheduleWatching
import yandex.cloud.sdk.functions.Context
import yandex.cloud.sdk.functions.YcFunction

class Request

data class Response(
    val message: String,
) {
    object Status {
        val OK = Response("OK")
    }
}

class YcHandler : YcFunction<Request, Response> {
    override fun handle(
        request: Request,
        context: Context,
    ): Response {
        val barsik =
            runCatching {
                Barsik(
                    context = context,
                    telegramApi = TODO(), // TelegramApi.Http(),
                    yandexS3Api = TODO(), // YandexS3Api.Http(),
                    yandexLlmModelsApi = TODO(), // YandexLlmModelsApi.Http(),
                    vkApi = TODO(), // VkApi.Http(),
                    imagesApi = TODO(), // ImagesApi.Http(),
                    discogsApi = TODO(), // DiscogsApi.Http(),
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
                        message() + "\n\n${getTelegramFormattedTraceLinK()}",
                    )
                }
            }
        }

        return Response.Status.OK
    }
}
