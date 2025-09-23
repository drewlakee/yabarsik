// https://yandex.cloud/ru/docs/functions/concepts/logs
package com.github.drewlakee.yabarsik

inline fun <reified T> T.logInfo(message: String) {
    println("""{"level":"INFO","message":"${T::class.simpleName}: ${message.replace("\n", "")
        .replace("\\s+".toRegex(), " ")}"}""")
}

inline fun <reified T> T.logError(throwable: Throwable?) {
    if (throwable != null) {
        with(throwable) {
            System.err.println("""{"level":"ERROR","message":"${T::class.simpleName}: $message; ${stackTraceToString()
                .replace("\n", "")
                .replace("\\s+".toRegex(), " ")}"}""")
        }
    }
}