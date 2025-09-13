// https://yandex.cloud/ru/docs/functions/concepts/function-invoke
package com.github.drewlakee.yabarsik

import com.github.drewlakee.yabarsik.telegram.Http
import com.github.drewlakee.yabarsik.telegram.TelegramApi
import com.github.drewlakee.yabarsik.yandex.s3.Http
import com.github.drewlakee.yabarsik.yandex.s3.YandexS3Api
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
        Barsik(
            telegramApi = TelegramApi.Http(),
            yandexS3Api = YandexS3Api.Http(),
        ).notifyAboutExecution()
        return Response.Status.OK
    }
}