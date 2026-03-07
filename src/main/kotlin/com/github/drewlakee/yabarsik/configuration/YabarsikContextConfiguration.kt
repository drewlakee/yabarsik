package com.github.drewlakee.yabarsik.configuration

import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.discogs.api.http
import com.github.drewlakee.yabarsik.images.Http
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.telegram.api.http
import com.github.drewlakee.yabarsik.telegram.chat.TelegramReportChat
import com.github.drewlakee.yabarsik.telegram.chat.TelegramReportChatProperties
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.api.http
import com.github.drewlakee.yabarsik.vk.community.VkCommunity
import com.github.drewlakee.yabarsik.vk.content.ContentProvidersConfigurationProperties
import com.github.drewlakee.yabarsik.vk.content.VkContentProvider
import com.github.drewlakee.yabarsik.yandex.function.YandexFunctionService
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api
import com.github.drewlakee.yabarsik.yandex.s3.api.http
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
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
        @Value("\${yabarsik.s3.access-key-id}") accessKeyId: String,
        @Value("\${yabarsik.s3.secret-access-key}") secretAccessKey: String,
    ): YandexS3Api =
        YandexS3Api.http(
            accessKeyId = accessKeyId,
            secretAccessKey = secretAccessKey,
        )

    @Bean
    open fun vkCommunity(
        @Value("\${yabarsik.vk.community.id}") communityId: Int,
        @Value("\${yabarsik.vk.community.domain}") communityDomain: String,
    ) = VkCommunity(
        id = communityId,
        domain = communityDomain,
    )

    @Bean
    @ConfigurationProperties(prefix = "yabarsik.content")
    open fun contentProvidersConfigurationProperties() = ContentProvidersConfigurationProperties()

    @Bean
    @ConfigurationProperties(prefix = "yabarsik.telegram.report")
    open fun telegramReportChatProperties() = TelegramReportChatProperties()

    @Bean
    open fun telegramReportChat(
        telegramReportChatProperties: TelegramReportChatProperties,
        telegramApi: TelegramApi,
    ) = TelegramReportChat(
        chatProperties = telegramReportChatProperties,
        telegramApi = telegramApi,
    )

    @Bean
    open fun yandexFunctionService(
        @Value("\${yabarsik.function.traceLinkTemplate}") traceLinkTemplate: String,
    ) = YandexFunctionService(
        traceLinkTemplate = traceLinkTemplate,
    )

    @Bean
    open fun vkContentProvider(configurationProperties: ContentProvidersConfigurationProperties) =
        VkContentProvider(
            configurationProperties = configurationProperties,
        )
}
