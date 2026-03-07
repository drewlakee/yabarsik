package com.github.drewlakee.yabarsik.yandex.function

import yandex.cloud.sdk.functions.Context

class YandexFunctionService(
    private val traceLinkTemplate: String,
) {
    fun getTraceLink(functionContext: Context) =
        traceLinkTemplate
            .replace("{requestId}", functionContext.requestId)
            .replace("{functionId}", functionContext.functionId)
}
