// https://yandex.cloud/ru/docs/functions/concepts/logs
package com.github.drewlakee.yabarsik

inline fun <reified T> T.logInfo(message: String) {
    println(
        """{"level":"INFO","message":"${T::class.simpleName}: ${message.replace("\n", "")
            .replace("\\s+".toRegex(), " ")}"}""",
    )
}

inline fun <reified T> T.logError(message: String) {
    System.err.println("""{"level":"ERROR","message":"${T::class.simpleName}: $message}"}""")
}

inline fun <reified T> T.logError(throwable: Throwable) {
    System.err.println(
        """{"level":"ERROR","message":"${T::class.simpleName}: ${throwable.stackTraceToString()
            .replace("\n", "")
            .replace("\\s+".toRegex(), " ")}"}""",
    )
}

private inline fun <reified T> T.logError(message: String, throwable: Throwable) {
    val errorMessages = listOfNotNull(
        message,
        throwable
            .stackTraceToString()
            .replace("\n", "")
            .replace("\\s+".toRegex(), " "),
    )
    System.err.println("""{"level":"ERROR","message":"${T::class.simpleName}: ${errorMessages.joinToString("; ")}"}""")
}
