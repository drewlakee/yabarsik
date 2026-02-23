package com.github.drewlakee.yabarsik.telegram.api

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.message.Message

interface TelegramApi {
    fun sendMessage(
        chatId: String,
        message: String,
    ): Result4k<Message, Throwable>

    companion object
}

fun TelegramApi.Companion.http(token: String) =
    object : TelegramApi {
        private val http = OkHttpTelegramClient(token)

        override fun sendMessage(
            chatId: String,
            message: String,
        ): Result4k<Message, Throwable> =
            runCatching {
                http.execute(
                    SendMessage
                        .builder()
                        .text(message)
                        .chatId(chatId)
                        .parseMode(ParseMode.MARKDOWN)
                        .build(),
                )
            }.let {
                if (it.isSuccess) {
                    return Success(it.getOrThrow())
                } else {
                    it.exceptionOrNull()?.run(::println)
                    return Failure(it.exceptionOrNull()!!)
                }
            }
    }
