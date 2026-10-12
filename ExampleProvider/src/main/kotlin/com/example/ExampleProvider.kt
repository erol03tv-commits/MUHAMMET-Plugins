
package com.example

import com.lagradost.cloudstream3.*
import org.json.JSONObject
import java.net.URLEncoder

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://archive.org"
    override var name = "Internet Archive"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        Pair("publicdomain", "Public Domain Films")
    )

    private fun searchUrl(query: String, page: Int): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return "$mainUrl/advancedsearch.php?q=$encodedQuery" +
            "&fl%5B%5D=identifier&fl%5B%5D=title" +
            "&fl%5B%5D=year&output=json&rows=24&page=$page"
    }

    private suspend fun getMovies(
        query: String,
        page: Int
    ): List<SearchResponse> {
        val response = app.get(searchUrl(query, page)).text
        val docs = JSONObject(response)
            .getJSONObject("response")
            .getJSONArray("docs")

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
        val query =
            "mediatype:movies AND licenseurl:http*publicdomain*"

        val movies = try {
            getMovies(query, page)
        } catch (_: Exception) {
            emptyList()
        }

        return newHomePageResponse(request.name, movies)
    }

    override suspend fun search(
        query: String
    ): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val archiveQuery =
            "mediatype:movies AND licenseurl:http*publicdomain* " +
            "AND title:(${query})"

        return try {
            getMovies(archiveQuery, 1)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

