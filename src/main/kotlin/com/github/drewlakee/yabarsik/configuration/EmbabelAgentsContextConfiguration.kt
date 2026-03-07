package com.github.drewlakee.yabarsik.configuration

import com.github.drewlakee.yabarsik.agents.VkCommunityContentManagerAgent
import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.vk.community.VkCommunity
import com.github.drewlakee.yabarsik.vk.content.VkContentProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class EmbabelAgentsContextConfiguration {
    @Bean
    open fun vkCommunityContentManagerAgent(
        vkApi: VkApi,
        imagesApi: ImagesApi,
        discogsApi: DiscogsApi,
        vkCommunity: VkCommunity,
        vkContentProvider: VkContentProvider,
    ) = VkCommunityContentManagerAgent(
        vkApi = vkApi,
        imagesApi = imagesApi,
        discogsApi = discogsApi,
        vkManagerCommunity = vkCommunity,
        vkContentProvider = vkContentProvider,
    )
}
