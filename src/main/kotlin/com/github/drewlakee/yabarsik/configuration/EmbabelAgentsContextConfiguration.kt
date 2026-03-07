package com.github.drewlakee.yabarsik.configuration

import com.embabel.common.textio.template.TemplateRenderer
import com.github.drewlakee.yabarsik.agents.VkCommunityContentManagerAgent
import com.github.drewlakee.yabarsik.agents.tools.DiscogsTools
import com.github.drewlakee.yabarsik.agents.tools.VkCommunityTools
import com.github.drewlakee.yabarsik.agents.tools.VkContentProviderTools
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
        vkCommunity: VkCommunity,
        vkCommunityTools: VkCommunityTools,
        discogsTools: DiscogsTools,
        vkContentProviderTools: VkContentProviderTools,
    ) = VkCommunityContentManagerAgent(
        vkApi = vkApi,
        vkManagerCommunity = vkCommunity,
        vkCommunityTools = vkCommunityTools,
        discogsTools = discogsTools,
        vkContentProviderTools = vkContentProviderTools,
    )

    @Bean
    open fun vkCommunityTools(
        vkApi: VkApi,
        vkManagerCommunity: VkCommunity,
        templateRenderer: TemplateRenderer,
    ) = VkCommunityTools(
        vkApi = vkApi,
        vkManagerCommunity = vkManagerCommunity,
        templateRenderer = templateRenderer,
    )

    @Bean
    open fun discogsTools(
        discogsApi: DiscogsApi,
        templateRenderer: TemplateRenderer,
    ) = DiscogsTools(
        discogsApi = discogsApi,
        templateRenderer = templateRenderer,
    )

    @Bean
    open fun vkContentProviderTools(
        vkApi: VkApi,
        vkContentProvider: VkContentProvider,
        templateRenderer: TemplateRenderer,
        vkManagerCommunity: VkCommunity,
        imagesApi: ImagesApi,
    ) = VkContentProviderTools(
        vkApi = vkApi,
        templateRenderer = templateRenderer,
        vkContentProvider = vkContentProvider,
        vkManagerCommunity = vkManagerCommunity,
        imagesApi = imagesApi,
    )
}
