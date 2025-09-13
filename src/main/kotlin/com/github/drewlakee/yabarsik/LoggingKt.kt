// https://yandex.cloud/ru/docs/functions/concepts/logs
package com.github.drewlakee.yabarsik

private fun Throwable.formatedStackTraceString() =
    stackTraceToString()
        .replace("\n", "")
        .replace("\\s+".toRegex(), " ")

fun logInfo(message: String) {
    println("""{"level":"INFO","message":"$message"}""")
}

fun logError(throwable: Throwable) {
    with(throwable) {
        System.err.println("""{"level":"ERROR","message":"$message; ${formatedStackTraceString()}"}""")
    }
}