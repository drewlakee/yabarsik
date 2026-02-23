package com.github.drewlakee.yabarsik.vk.content

import com.github.drewlakee.yabarsik.vk.api.VkWallposts
import com.github.drewlakee.yabarsik.vk.api.VkWallpostsAttachmentType

@JvmInline
value class VkWallpostAttachment private constructor(
    val id: String,
) {
    companion object {
        fun formAttachmentId(attachment: VkWallposts.VkWallpostsResponse.VkWallpostsItem.VkWallpostsAttachment): VkWallpostAttachment =
            VkWallpostAttachment(
                when (attachment.type) {
                    VkWallpostsAttachmentType.PHOTO -> "${attachment.type}${attachment.photo!!.ownerId}_${attachment.photo.id}"
                    VkWallpostsAttachmentType.AUDIO -> "${attachment.type}${attachment.audio!!.ownerId}_${attachment.audio.id}"
                    VkWallpostsAttachmentType.UNKNOWN -> "unknownId"
                },
            )
    }

    override fun toString() = id
}
