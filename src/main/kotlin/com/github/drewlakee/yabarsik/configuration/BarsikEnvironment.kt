package com.github.drewlakee.yabarsik.configuration

object BarsikEnvironment {
    val TELEGRAM_TOKEN = "TELEGRAM_TOKEN".getOrThrow()
    val VK_SERVICE_ACCESS_TOKEN = "VK_SERVICE_ACCESS_TOKEN".getOrThrow()
    val VK_COMMUNITY_ACCESS_TOKEN = "VK_COMMUNITY_ACCESS_TOKEN".getOrThrow()
    val YANDEX_CLOUD_LLM_API_KEY = "YANDEX_CLOUD_LLM_API_KEY".getOrThrow()
    val CONFIGURATION_S3_OBJECT_ID = "CONFIGURATION_S3_OBJECT_ID".getOrThrow()
    val CONFIGURATION_S3_BUCKET = "CONFIGURATION_S3_BUCKET".getOrThrow()
}

private fun String.getOrThrow(): String = if (System.getenv(this) == null) {
    throw IllegalStateException("Barsik will be malfunctioning without value of environment variable '$this'.")
} else {
    System.getenv(this)
}