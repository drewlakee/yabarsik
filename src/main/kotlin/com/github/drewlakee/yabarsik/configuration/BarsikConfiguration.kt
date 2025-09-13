package com.github.drewlakee.yabarsik.configuration

import com.fasterxml.jackson.databind.ObjectMapper

data class BarsikConfiguration(
    private val yamlMapper: ObjectMapper,
    private val configuration: Configuration,
) {
    val telegram = configuration.telegram

    data class Configuration(
        val telegram: Telegram
    )

    data class Telegram(val report: Report) {
        data class Report(val chatId: String)
    }

    fun toYamlString(): String = yamlMapper.writeValueAsString(configuration)
}