package com.github.drewlakee.yabarsik.yandex.s3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.drewlakee.yabarsik.configuration.BarsikConfiguration
import com.github.drewlakee.yabarsik.logError
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.net.URI

interface YandexS3Api {
    fun getBarsikConfiguration(): Result4k<BarsikConfiguration, Throwable>

    companion object
}

fun YandexS3Api.Companion.Http() = object : YandexS3Api {
    private val s3Client = S3Client.builder()
        .region(Region.US_EAST_1)
        .endpointOverride(URI.create("https://storage.yandexcloud.net"))
        .httpClientBuilder(ApacheHttpClient.builder())
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .build()
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)


    override fun getBarsikConfiguration(): Result4k<BarsikConfiguration, Throwable> =
        runCatching {
            s3Client.getObject (
                GetObjectRequest.builder()
                    .key("configuration.yml")
                    .bucket("yabarsik")
                    .build()
            ).let {
                yamlMapper
                    .readValue(it.readAllBytes(), BarsikConfiguration.Configuration::class.java)
            }
        }.let {
            if (it.isSuccess) {
                return Success(BarsikConfiguration(
                    yamlMapper = yamlMapper,
                    configuration = it.getOrThrow(),
                ))
            } else {
                logError(it.exceptionOrNull()!!)
                return Failure(it.exceptionOrNull()!!)
            }
        }
}

