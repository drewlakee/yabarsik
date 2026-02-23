package com.github.drewlakee.yabarsik.vk.content

enum class ContentProviderType {
    VK,
}

enum class ContentMedia {
    IMAGES,
    MUSIC,
}

open class ContentProvidersConfigurationProperties {
    lateinit var providers: List<ContentProvider>

    data class ContentProvider(
        var provider: ContentProviderType,
        var domain: String,
        var media: List<ContentMedia>,
    )
}
