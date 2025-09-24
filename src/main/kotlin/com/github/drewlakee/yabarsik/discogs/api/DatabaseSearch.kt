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
                            logError(it.exceptionOrNull())
                            Failure(RemoteRequestFailed(response.status, response.bodyString()))
                        }
                    }
            else -> Failure(RemoteRequestFailed(response.status, response.bodyString()))
        }
}

data class CompactArtistTrackInfo(
    val labels: Set<String>,
    val genres: Set<String>,
    val styles: Set<String>,
    val releaseTitles: Set<String>,
) {
    override fun toString() = "жанры: $genres, стили жанров: $styles, названия релизов: $releaseTitles, музыкальные лейблы: $labels"
}

fun DiscogsApi.getCompactArtistTrackInfo(
    artist: String,
    track: String? = null,
): Result4k<CompactArtistTrackInfo, RemoteRequestFailed> =
    invoke(
        DatabaseSearch(
            artist = artist,
            track = track,
        ),
    ).map { mergedArtistTrackResults ->
        val labels = mutableSetOf<String>()
        val genres = mutableSetOf<String>()
        val styles = mutableSetOf<String>()
        val releaseTitles = mutableSetOf<String>()

        mergedArtistTrackResults.results.forEach { result ->
            with(result) {
                label?.run(labels::addAll)
                genre?.run(genres::addAll)
                style?.run(styles::addAll)
                title.run(releaseTitles::add)
            }
        }

        CompactArtistTrackInfo(
            labels = labels,
            genres = genres,
            styles = styles,
            releaseTitles = releaseTitles,
        )
    }
