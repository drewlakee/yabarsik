package com.github.drewlakee.yabarsik

import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.peekFailure

class Barsik(
    val telegramApi: TelegramApi,
    val yandexS3Api: YandexS3Api,
) {
    val configuration: BarsikConfiguration = yandexS3Api.getBarsikConfiguration()
        .peekFailure { logError(it) }
        .orThrow()

    fun notifyAboutExecution() {
        telegramApi.sendMessage(
            configuration.telegram.report.chatId,
            """Привет\! Меня только что разбудили, держу в курсе\. Вот моя текущая конфигурация:
                ```log
                ${configuration.toYamlString()}
                ```
                """
        )
    }
}