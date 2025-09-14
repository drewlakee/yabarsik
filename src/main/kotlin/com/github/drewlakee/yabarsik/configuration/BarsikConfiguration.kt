package com.github.drewlakee.yabarsik.configuration

import com.fasterxml.jackson.databind.ObjectMapper

data class BarsikConfiguration(
    private val yamlMapper: ObjectMapper,
    private val configuration: Configuration,
) {
    val telegram = configuration.telegram
    val wallposts = configuration.wallposts
    val content = configuration.content

    data class Configuration(
        val telegram: Telegram,
        val wallposts: Wallposts,
        val content: Content,
    )

    data class Telegram(val report: Report) {
        data class Report(val chatId: String)
    }

    data class Wallposts(val communityId: String, val schedule: Schedule) {
        data class Schedule(val timeZone: String, val checkpoints: List<Checkpoint>) {
            data class Checkpoint(val at: String, val variancePeriod: String)
        }
    }

    data class Content(val providers: List<ContentProvider>) {
        data class ContentProvider(
            val provider: ContentProviderType,
            val subjectId: String,
            val media: List<Media>,
        ) {
            enum class ContentProviderType {
                VK,
            }
            enum class Media {
                MUSIC,
                IMAGES,
            }
        }
    }

    fun toYamlString(): String = yamlMapper.writeValueAsString(configuration)
        .replace("---", "")
}