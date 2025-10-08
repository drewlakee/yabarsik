package com.github.drewlakee.yabarsik.scenario.vk

@JvmInline
value class AudioTitle private constructor(val title: String) {
    companion object {
        fun formatted(title: String) = AudioTitle(title.lowercase())
    }
}
