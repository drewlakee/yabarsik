// https://yandex.cloud/ru/docs/functions/concepts/function-invoke
package com.github.drewlakee.yabarsik

import com.github.drewlakee.yabarsik.telegram.api.Http
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
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
                telegramApi = TelegramApi.Http(),
                yandexS3Api = YandexS3Api.Http(),
                yandexLlmModelsApi = YandexLlmModelsApi.Http(),
            )
        }

        if (barsik.isFailure) {
            logError(barsik.exceptionOrNull())
            return Response("ERROR: ${barsik.exceptionOrNull()?.message ?: "Unknown error"}")
        }

        with (barsik.getOrThrow()) {
            telegramApi.sendMessage(
                configuration.telegram.report.chatId,
                """Привет\! Меня только что разбудили, держу в курсе\. Вот моя текущая конфигурация:
                ```log
                ${configuration.toYamlString()}
                ```
                """
            )
        }

        return Response.Status.OK
    }
}