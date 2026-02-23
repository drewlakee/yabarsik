package com.github.drewlakee.yabarsik.vk.content

@JvmInline
value class AudioTitle private constructor(val title: String) {
    companion object {
        fun formatted(title: String) = AudioTitle(title.lowercase())
    }

    override fun toString() = title
}