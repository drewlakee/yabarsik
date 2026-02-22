package com.github.drewlakee.yabarsik.configuration

import com.github.drewlakee.yabarsik.agents.VkCommunityContentManagerAgent
import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.community.VkCommunity
import com.github.drewlakee.yabarsik.vk.content.VkContentProvider
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
        vkCommunity: VkCommunity,
        vkContentProvider: VkContentProvider,
    ) = VkCommunityContentManagerAgent(
        telegramApi = telegramApi,
        vkApi = vkApi,
        imagesApi = imagesApi,
        discogsApi = discogsApi,
        vkCommunity = vkCommunity,
        vkContentProvider = vkContentProvider,
    )
}
