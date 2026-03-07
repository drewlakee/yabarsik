package com.github.drewlakee.yabarsik.telegram.chat

import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import dev.forkhandles.result4k.failureOrNull

class TelegramReportChat(
    private val chatProperties: TelegramReportChatProperties,
    private val telegramApi: TelegramApi,
) {
    fun sendMessage(message: TelegramMessage) {
        telegramApi
            .sendMessage(
                chatId = chatProperties.chatId,
                message = message.toString(),
            ).also {
                it.failureOrNull()?.let { failure ->
                    println("Failed to send telegram message: ${failure.message}")
                }
            }
    }
}
