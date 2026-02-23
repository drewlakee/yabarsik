package com.github.drewlakee.yabarsik.yandex.s3.api

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Status
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import java.net.URI

interface YandexS3Api {
    fun getObject(
        bucket: String,
        objectId: String,
    ): Result4k<GetObjectResponse, RemoteRequestFailed>

    companion object
}

fun YandexS3Api.Companion.http(
    accessKeyId: String,
    secretAccessKey: String,
) = object : YandexS3Api {
    private val s3Client =
        S3Client
            .builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create("https://storage.yandexcloud.net"))
            .httpClientBuilder(ApacheHttpClient.builder())
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .build()

    override fun getObject(
        bucket: String,
        objectId: String,
    ): Result4k<GetObjectResponse, RemoteRequestFailed> =
        runCatching {
            s3Client.getObject(
                GetObjectRequest
                    .builder()
                    .key(objectId)
                    .bucket(bucket)
                    .build(),
            )
        }.let {
            if (it.isSuccess) {
                return Success(it.getOrThrow().response())
            } else {
                it.exceptionOrNull()?.run(::println)
                return Failure(
                    RemoteRequestFailed(
                        status = Status.INTERNAL_SERVER_ERROR,
                        message = it.exceptionOrNull()!!.message ?: "Unknown error",
                    ),
                )
            }
        }
}
