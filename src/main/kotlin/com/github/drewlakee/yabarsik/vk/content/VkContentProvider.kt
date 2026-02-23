package com.github.drewlakee.yabarsik.vk.content

import com.github.drewlakee.yabarsik.vk.content.ContentProvidersConfigurationProperties.ContentProvider

class VkContentProvider(
    configurationProperties: ContentProvidersConfigurationProperties,
) {
    private val byMedia: Map<ContentMedia, List<ContentProvider>> =
        configurationProperties.providers
            .asSequence()
            .flatMap { provider ->
                provider.media
                    .asSequence()
                    .map { media -> media to provider }
            }.groupBy(
                keySelector = { (media, _) -> media },
                valueTransform = { (_, provider) -> provider },
            )

    init {
        ContentMedia.entries.forEach { media ->
            with(byMedia[media]) {
                if (this == null || this.isEmpty()) {
                    throw IllegalArgumentException(
                        "All providers expected to be provided at least with one element, but $media does not exist. Please, configure it",
                    )
                }
            }
        }
    }

    fun getRandomByMedia(media: ContentMedia) = (byMedia[media] ?: emptyList()).random()
}
