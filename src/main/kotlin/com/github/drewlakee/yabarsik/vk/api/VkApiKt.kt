package com.github.drewlakee.yabarsik.vk.api

import dev.forkhandles.result4k.valueOrNull
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

fun VkApi.getOnlyOpenOwners(ownerIds: List<Int>): Set<Int> {
    val (groups, users) = ownerIds.distinct().partition { it.isGroup() }
    log.info { "Found vk owners: groups=$groups, users=$users" }

    val existingUsers =
        if (users.isNotEmpty()) {
            this
                .invoke(GetUsers(users))
                .valueOrNull()
                ?.users
                ?.associateBy { it.id }
                ?: mapOf()
        } else {
            mapOf()
        }

    val existingGroups =
        if (groups.isNotEmpty()) {
            this
                .invoke(GetGroups(groups.map { it * -1 }))
                .valueOrNull()
                ?.response
                ?.groups
                ?.associateBy { it.id * -1 }
                ?: mapOf()
        } else {
            mapOf()
        }

    log.info { "Existing vk owners: groups=${existingGroups.values}, users=${existingUsers.values}" }

    return sequence {
        users
            .asSequence()
            .filter { it !in existingUsers || existingUsers[it]!!.isOpenAccount() }
            .forEach { yield(it) }
        groups
            .asSequence()
            .filter { it !in existingGroups || existingGroups[it]!!.isOpenGroup() }
            .forEach { yield(it) }
    }.toSet()
        .also {
            log.info { "Open vk owner ids $it" }
        }
}

private fun VkGroups.Response.Group.isOpenGroup() = isClosed == 0

private fun VkUsers.User.isOpenAccount() = !isClosed && canSeeAudio == 1 && canAccessClosed

private fun Int.isGroup() = this < 0
