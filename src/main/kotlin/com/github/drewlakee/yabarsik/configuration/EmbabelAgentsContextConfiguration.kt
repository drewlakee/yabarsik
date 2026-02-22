package com.github.drewlakee.yabarsik.configuration

import com.github.drewlakee.yabarsik.agents.VkCommunityContentManagerAgent
import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class EmbabelAgentsContextConfiguration {
    @Bean
    open fun vkCommunityContentManagerAgent(
        telegramApi: TelegramApi,
        vkApi: VkApi,
        imagesApi: ImagesApi,
        discogsApi: DiscogsApi,
        yandexS3Api: YandexS3Api,
    ) = VkCommunityContentManagerAgent(
        telegramApi = telegramApi,
        vkApi = vkApi,
        imagesApi = imagesApi,
        discogsApi = discogsApi,
        yandexS3Api = yandexS3Api,
    )
}
