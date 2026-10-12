
package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URLEncoder

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://archive.org"
    override var name = "Internet Archive"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "mediatype:movies AND licenseurl:http*publicdomain*" to
            "Public Domain Films"
    )

    private fun makeSearchUrl(query: String, page: Int): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "$mainUrl/advancedsearch.php?q=$encoded" +
            "&fl%5B%5D=identifier&fl%5B%5D=title" +
            "&fl%5B%5D=year&output=json&rows=24&page=$page"
    }

    private suspend fun findMovies(
        query: String,
        page: Int
    ): List<SearchResponse> {
        val json = JSONObject(app.get(makeSearchUrl(query, page)).text)
        val docs = json.getJSONObject("response").getJSONArray("docs")

        return (0 until docs.length()).mapNotNull { i ->
            val item = docs.getJSONObject(i)
            val id = item.optString("identifier")
            val title = item.optString("title")

            if (id.isBlank() || title.isBlank()) {
                null
            } else {
                newMovieSearchResponse(
                    title,
                    "$mainUrl/details/$id",
                    TvType.Movie
                ) {
                    posterUrl = "$mainUrl/services/img/$id"
                    year = item.optString("year").toIntOrNull()
                }
            }
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val movies = try {
            findMovies(
                "mediatype:movies AND licenseurl:http*publicdomain*",
                page
            )
        } catch (_: Exception) {
            emptyList()
        }

        return newHomePageResponse(request.name, movies)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val safeQuery = query
            .replace("\\", " ")
            .replace("\"", " ")

        val archiveQuery =
            "mediatype:movies AND licenseurl:http*publicdomain* " +
            "AND title:\"$safeQuery\""

        return try {
            findMovies(archiveQuery, 1)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.substringAfterLast("/")
            .substringBefore("?")
            .takeIf { it.isNotBlank() } ?: return null

        return try {
            val json = JSONObject(
                app.get("$mainUrl/metadata/$id").text
            )
            val metadata = json.optJSONObject("metadata")
                ?: return null

            val license = metadata.optString("licenseurl")
            val permitted = license.contains(
                "publicdomain",
                ignoreCase = true
            ) || license.contains("/zero/", ignoreCase = true)

            if (!permitted) return null

            val title = metadata.optString("title", id)
            val description = metadata.optString("description")
            val year = metadata.optString("year").toIntOrNull()

            
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                id
            ) {
                posterUrl = "$mainUrl/services/img/$id"
                this.year = year
                plot = description
            }

        } catch (_: Exception) {
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val json = JSONObject(
                app.get("$mainUrl/metadata/$data").text
            )
            val metadata = json.optJSONObject("metadata")
                ?: return false

            val license = metadata.optString("licenseurl")
            val permitted = license.contains(
                "publicdomain",
                ignoreCase = true
            ) || license.contains("/zero/", ignoreCase = true)

            if (!permitted) return false

            val files = json.optJSONArray("files") ?: return false
            var found = false

            for (i in 0 until files.length()) {
                val file = files.optJSONObject(i) ?: continue
                val fileName = file.optString("name")

                if (!fileName.endsWith(".mp4", ignoreCase = true)) {
                    continue
                }

                if (file.optString("private") == "true") continue

                val encodedName = URLEncoder.encode(
                    fileName,
                    "UTF-8"
                ).replace("+", "%20")

                val videoUrl = "$mainUrl/download/$data/$encodedName"

                callback.invoke(
                    newExtractorLink(
                        this.name,
                        fileName,
                        videoUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/details/$data"
                        this.quality = Qualities.Unknown.value
                    }
                )
                found = true
            }

            found
        } catch (_: Exception) {
            false
        }
    }
}
