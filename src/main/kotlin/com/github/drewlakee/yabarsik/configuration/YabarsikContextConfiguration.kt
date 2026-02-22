package com.github.drewlakee.yabarsik.configuration

import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.discogs.api.http
import com.github.drewlakee.yabarsik.images.Http
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.telegram.api.http
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.http
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api
import com.github.drewlakee.yabarsik.yandex.s3.api.http
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
open class YabarsikContextConfiguration {
    @Bean
    open fun objectMapperBuilder(): Jackson2ObjectMapperBuilder = Jackson2ObjectMapperBuilder()

    @Bean
    open fun discogsApi(
        @Value("\${yabarsik.discogs.token}") token: String,
    ): DiscogsApi = DiscogsApi.http(token)

    @Bean
    open fun imagesApi(): ImagesApi = ImagesApi.Http()

    @Bean
    open fun telegramApi(
        @Value("\${yabarsik.telegram.token}") token: String,
    ): TelegramApi = TelegramApi.http(token)

    @Bean
    open fun vkApi(
        @Value("\${yabarsik.vk.service-token}") serviceToken: String,
        @Value("\${yabarsik.vk.community-token}") communityToken: String,
    ): VkApi =
        VkApi.http(
            serviceAccessToken = serviceToken,
            communityAccessToken = communityToken,
        )

    @Bean
    open fun yandexS3StorageApi(
        @Value("\${yabarsik.s3.configuration-object-id}") configurationObjectId: String,
        @Value("\${yabarsik.s3.configuration-bucket}") configurationBucket: String,
    ): YandexS3Api =
        YandexS3Api.http(
            configurationS3ObjectId = configurationObjectId,
            configurationS3Bucket = configurationBucket,
        )
}
