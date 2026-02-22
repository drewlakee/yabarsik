package com.github.drewlakee.yabarsik.agents

import com.embabel.agent.api.annotation.Agent
import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.images.ImagesApi
import com.github.drewlakee.yabarsik.telegram.api.TelegramApi
import com.github.drewlakee.yabarsik.vk.api.VkApi
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api

@Agent(
    name = "Агент по управлению контентом в сообществе Вконтакте",
    description = "Занимается поиском подходящего контента и принимает решение о его публикации",
)
class VkCommunityContentManagerAgent(
    private val telegramApi: TelegramApi,
    private val vkApi: VkApi,
    private val imagesApi: ImagesApi,
    private val discogsApi: DiscogsApi,
    private val yandexS3Api: YandexS3Api,
)
