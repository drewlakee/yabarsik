package com.github.drewlakee.yabarsik.telegram.chat

sealed interface TelegramMessage {
    companion object {
        fun formatted(builderAction: StringBuilder.() -> Unit) =
            TelegramFormattedMessageBuilder(
                stringBuilder = StringBuilder().apply(builderAction),
            )
    }
}

fun StringBuilder.appendNewLine() = append("\n\n")

class TelegramFormattedMessageBuilder(
    private val stringBuilder: StringBuilder = StringBuilder(),
) : TelegramMessage,
    Appendable by stringBuilder {
    override fun toString() =
        stringBuilder
            .replace(Regex("\n"), "\n|")
            .trimMargin("|")
}
