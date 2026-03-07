package com.github.drewlakee.yabarsik.agents.tools

import com.embabel.agent.api.annotation.LlmTool
import com.embabel.common.textio.template.TemplateRenderer
import com.github.drewlakee.yabarsik.discogs.api.ArtistReleases
import com.github.drewlakee.yabarsik.discogs.api.DiscogsApi
import com.github.drewlakee.yabarsik.discogs.api.getArtistReleases
import dev.forkhandles.result4k.valueOrNull

data class Track(
    val artist: String,
    val trackTitle: String,
)

class DiscogsTools(
    private val discogsApi: DiscogsApi,
    private val templateRenderer: TemplateRenderer,
) {
    @LlmTool(
        name = "find-artist-releases-discogs",
        description = "Получает из сервиса Discogs дополнительную информацию о релизах исполнителей, их жанрах, стилях и так далее",
    )
    fun findArtistReleases(
        @LlmTool.Param(description = "Исполнители и их треки, по которым нужно найти дополнительную информацию по релизам") tracks:
            List<Track>,
    ): String {
        val discogsArtistReleases: List<ArtistReleases> =
            sequence {
                tracks
                    .mapNotNull { (artist, track) ->
                        discogsApi
                            .getArtistReleases(
                                artist = artist,
                                track = track,
                            ).valueOrNull()
                    }.forEach { yield(it) }
                tracks
                    .asSequence()
                    .mapNotNull { artist ->
                        discogsApi
                            .getArtistReleases(
                                artist = artist.artist,
                            ).valueOrNull()
                    }.forEach { yield(it) }
            }.groupBy { it.artist }
                .entries
                .asSequence()
                .map { (artist, releases) ->
                    ArtistReleases(
                        artist = artist,
                        releases = releases.flatMap { it.releases }.distinctBy { it.title },
                    )
                }.filter { it.releases.isNotEmpty() }
                .toList()

        return templateRenderer.renderLoadedTemplate(
            "classpath:/templates/find-artist-releases-discogs-tool.jinja",
            mapOf(
                "discogsArtistReleases" to discogsArtistReleases,
            ),
        )
    }
}
