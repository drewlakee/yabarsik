package com.github.drewlakee.yabarsik.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.drewlakee.yabarsik.logError

data class BarsikConfiguration(
    private val yamlMapper: ObjectMapper,
    private val configuration: Configuration,
) {
    val telegram = configuration.telegram
    val wallposts = configuration.wallposts
    val content = configuration.content
    val llm = configuration.llm

    fun toYamlString(): String = runCatching {
        yamlMapper.writeValueAsString(configuration)
            .replace("---", "")
    }
        .onFailure { logError(it) }
        .getOrElse {
            "FAILED TO PARSE YAML" + if (it.message != null) ": ${it.message}" else ""
        }
}

data class Configuration(
    val wallposts: Wallposts,
    val llm: Llm,
    val content: Content,
    val telegram: Telegram,
)

data class Llm(
    val folderId: String,
    val textGtp: TextGtp,
    val multiModalGpt: MultiModalGpt,
    val audioPromt: AudioPromt,
    val photoPromt: PhotoPromt,
) {

    enum class Api {
        YANDEX,
        OPENAI,
    }

    data class TextGtp(val model: String, val api: Api)
    data class MultiModalGpt(val model: String, val api: Api)
    data class AudioPromt(val temperature: Float, val systemInstruction: String)
    data class PhotoPromt(val temperature: Float, val systemInstruction: String)
}

data class Telegram(val report: Report) {
    data class Report(val chatId: String)
}

data class Wallposts(val communityId: Int, val domain: String, val dailySchedule: DailySchedule) {
    data class DailySchedule(val timeZone: String, val checkpoints: List<Checkpoint>) {
        data class Checkpoint(val at: String, val plusPostponeDuration: String)
    }
}

data class Content(val settings: Settings, val providers: List<Provider>) {
    data class Settings(
        val musicAttachmentsCollectorSize: Int,
        val takeMusicAttachmentsPerProvider: Int,
        val imagesAttachmentsCollectorSize: Int,
        val takeImagesAttachmentsPerProvider: Int,
        val musicLlmApprovalThreshold: Float,
    )

    data class Provider(
        val provider: Type,
        val domain: String,
        val media: List<Media>,
    ) {
        enum class Type {
            VK,
        }
        enum class Media {
            MUSIC,
            IMAGES,
        }
    }
}