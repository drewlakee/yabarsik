package com.github.drewlakee.yabarsik.discogs.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.drewlakee.yabarsik.configuration.BarsikEnvironment.DISCOGS_TOKEN
import dev.forkhandles.result4k.Result4k
import org.http4k.client.OkHttp
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.connect.Action
import org.http4k.core.Body
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.filter.RequestFilters.SetHeader

interface DiscogsApiAction<R> : Action<Result4k<R, RemoteRequestFailed>> {

    companion object {
        protected val JSON = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        protected inline fun <reified R> jsonTo(body: Body): R = JSON.readValue(body.stream)
    }
}

interface DiscogsApi {
    operator fun <R : Any> invoke(action: DiscogsApiAction<R>): Result4k<R, RemoteRequestFailed>

    companion object
}

fun DiscogsApi.Companion.Http() = object : DiscogsApi {
    private val http = SetBaseUriFrom(Uri.of("https://api.discogs.com"))
        .then(SetHeader("Accept", "application/json"))
        .then(SetHeader("Authorization", "Discogs token=$DISCOGS_TOKEN"))
        .then(OkHttp())

    override fun <R : Any> invoke(action: DiscogsApiAction<R>): Result4k<R, RemoteRequestFailed> =
        action.toResult(http.invoke(action.toRequest()))
}