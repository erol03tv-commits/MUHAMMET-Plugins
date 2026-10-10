package com.example

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newMovieSearchResponse
import org.json.JSONObject
import java.net.URLEncoder

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://archive.org"
    override var name = "Internet Archive Public Domain"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl =
            "$mainUrl/advancedsearch.php" +
            "?q=mediatype%3Amovies%20AND%20licenseurl%3Ahttp%2Apublicdomain%2A" +
            "%20AND%20title%3A%22$encodedQuery%22" +
            "&fl%5B%5D=identifier" +
            "&fl%5B%5D=title" +
            "&fl%5B%5D=year" +
            "&output=json&rows=30"

        return try {
            val response = app.get(searchUrl).text
            val docs = JSONObject(response)
                .getJSONObject("response")
                .getJSONArray("docs")

            (0 until docs.length()).mapNotNull { index ->
                val item = docs.getJSONObject(index)
                val identifier = item.optString("identifier")
                val title = item.optString("title")

                if (identifier.isBlank() || title.isBlank()) {
                    null
                } else {
                    newMovieSearchResponse(
                        title,
                        "$mainUrl/details/$identifier",
                        TvType.Movie
                    ) {
                        posterUrl = "$mainUrl/services/img/$identifier"
                        year = item.optInt("year").takeIf { it > 0 }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
