// https://yandex.cloud/ru/docs/functions/concepts/function-invoke
package com.github.drewlakee.yabarsik

import com.github.drewlakee.yabarsik.configuration.BarsikConfiguration
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
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
        val configuration = BarsikConfiguration()
        val telegram = OkHttpTelegramClient(configuration.telegramToken)
        telegram.execute(SendMessage(configuration.telegramChatId, "Привет! Меня только что разбудили, держу в курсе."))
        logInfo("My function executed.")
        return Response.Status.OK
    }
}