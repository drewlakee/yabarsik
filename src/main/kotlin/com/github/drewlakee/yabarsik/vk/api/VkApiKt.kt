package com.github.drewlakee.yabarsik.vk.api

import dev.forkhandles.result4k.valueOrNull

fun VkApi.getOnlyOpenOwners(ownerIds: List<Int>): Set<Int> =
    sequence {
        val (groups, users) = ownerIds.distinct().partition { it.isGroup() }
        if (users.isNotEmpty()) {
            this@getOnlyOpenOwners
                .invoke(GetUsers(users))
                .valueOrNull()
                ?.users
                ?.asSequence()
                ?.filter { it.isOpenAccount() }
                ?.map { it.id }
                ?.forEach { yield(it) }
            this@getOnlyOpenOwners
                .invoke(GetGroups(users))
                .valueOrNull()
                ?.response
                ?.groups
                ?.asSequence()
                ?.filter { it.isOpenGroup() }
                ?.map { it.id }
                ?.forEach { yield(it) }
        }
        if (groups.isNotEmpty()) {
            this@getOnlyOpenOwners
                .invoke(GetGroups(groups.map { it * -1 }))
                .valueOrNull()
                ?.response
                ?.groups
                ?.asSequence()
                ?.filter { it.isOpenGroup() }
                ?.map { it.id * -1 }
                ?.forEach { yield(it) }
        }
    }.toSet()

private fun VkGroups.Response.Group.isOpenGroup() = isClosed == 0

private fun VkUsers.User.isOpenAccount() = !isClosed && canSeeAudio == 1 && canAccessClosed

private fun Int.isGroup() = this < 0
