package com.github.drewlakee.yabarsik.configuration

class BarsikConfiguration {
    val telegramChatId = System.getenv("TELEGRAM_CHAT_ID")
        ?: throw IllegalStateException("TELEGRAM_CHAT_ID is expected at environment")
    val telegramToken = System.getenv("TELEGRAM_TOKEN")
        ?: throw IllegalStateException("TELEGRAM_TOKEN is expected at environment")
}