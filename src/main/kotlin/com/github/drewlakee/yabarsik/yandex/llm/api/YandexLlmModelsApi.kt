package com.github.drewlakee.yabarsik.yandex.llm.api

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.drewlakee.yabarsik.BarsikEnvironment.YANDEX_CLOUD_LLM_API_KEY
import dev.forkhandles.result4k.Result4k
import org.http4k.client.OkHttp
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.connect.Action
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.filter.RequestFilters.SetHeader

interface YandexLlmModelsApiAction<R>  : Action<Result4k<R, RemoteRequestFailed>> {

    companion object  {
        protected val JSON = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        protected inline fun <reified R> jsonTo(body: Body): R = JSON.readValue(body.stream)
        protected fun toJsonString(data: Any): String = JSON.writeValueAsString(data)
    }
}

interface YandexLlmModelsApi {
    operator fun <R : Any> invoke(action: YandexLlmModelsApiAction<R>): Result4k<R, RemoteRequestFailed>

    companion object
}

fun YandexLlmModelsApi.Companion.Http() = object : YandexLlmModelsApi {
    private val http = SetBaseUriFrom(Uri.of("https://llm.api.cloud.yandex.net"))
        .then(SetHeader("Content-Type", "application/json"))
        .then(SetHeader("Accept", "application/json"))
        .then(SetHeader("Authorization", "Api-Key $YANDEX_CLOUD_LLM_API_KEY"))
        .then(OkHttp())

    override fun <R : Any> invoke(action: YandexLlmModelsApiAction<R>): Result4k<R, RemoteRequestFailed> =
        action.toResult(http(action.toRequest()))
}

enum class CommonLlmMessageRole(@field:JsonValue val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
}