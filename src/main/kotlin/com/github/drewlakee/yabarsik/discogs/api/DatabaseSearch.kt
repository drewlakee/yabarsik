// https://www.discogs.com/developers?#page:database,header:database-search
package com.github.drewlakee.yabarsik.discogs.api

import com.github.drewlakee.yabarsik.logError
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.map
import org.http4k.cloudnative.RemoteRequestFailed
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

data class DiscogsDatabaseResults(
    val results: List<Result>,
) {
    data class Result(
        val label: List<String>?,
        val genre: List<String>?,
        val style: List<String>?,
        val title: String,
        val type: String,
    )
}

data class DatabaseSearch(
    val artist: String,
    val track: String? = null,
) : DiscogsApiAction<DiscogsDatabaseResults> {
    override fun toRequest() =
        Request(
            Method.GET,
            buildString {
                append("/database/search")

                listOf(
                    "?artist=$artist",
                    "per_page=5",
                    track?.let { "track=$it" },
                )
                    .filter { it != null }
                    .joinToString(separator = "&")
                    .run(::append)
            },
        )

    override fun toResult(response: Response): Result4k<DiscogsDatabaseResults, RemoteRequestFailed> =
        when (response.status) {
            Status.OK ->
                runCatching { DiscogsApiAction.jsonTo<DiscogsDatabaseResults>(response.body) }
                    .let {
                        if (it.isSuccess) {
                            Success(it.getOrNull()!!)
                        } else {
                            it.exceptionOrNull()?.run(::logError)
                            Failure(RemoteRequestFailed(response.status, response.bodyString()))
                        }
                    }
            else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
        }
}

data class ArtistReleases(
    val artist: String,
    val releases: List<Release>,
) {
    data class Release(val title: String, val genres: Set<String>, val styles: Set<String>, val labels: Set<String>)

    override fun toString() = buildString {
        append(artist).append(": ")
        releases.joinToString(prefix = "[", separator = ",", postfix = "]") { release ->
            with(release) {
                buildString {
                    append("(")
                    append("release title: ").append(title).append("; ")
                    append("genres: ").append(genres.joinToString(separator = ",")).append("; ")
                    append("styles: ").append(styles.joinToString(separator = ",")).append("; ")
                    append("labels: ").append(labels.joinToString(separator = ","))
                    append(")")
                }
            }
        }.run(::append)
    }
}

fun DiscogsApi.getArtistReleases(
    artist: String,
    track: String? = null,
): Result4k<ArtistReleases, RemoteRequestFailed> =
    invoke(
        DatabaseSearch(
            artist = artist,
            track = track,
        ),
    ).map { response ->
        ArtistReleases(
            artist = artist,
            releases = response.results.map { release ->
                ArtistReleases.Release(
                    title = release.title,
                    genres = release.genre?.toSet() ?: setOf(),
                    styles = release.style?.toSet() ?: setOf(),
                    labels = release.label?.toSet() ?: setOf(),
                )
            }
        )
    }
