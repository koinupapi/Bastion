package com.koinupapi.bastion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SeerrBrowseOption(val id: Int, val name: String)
data class SeerrBrowsePage(
    val items: List<SeerrRelatedTitle> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val error: String? = null
)

/** Server-side discovery and pagination; requests go through the configured Seerr instance. */
object SeerrBrowseClient {
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend fun get(config: ServiceConfig, path: String): String = withContext(Dispatchers.IO) {
        val addresses = when (config.mode) {
            "Local only" -> listOf("Local" to config.localUrl)
            "Remote only" -> listOf("Remote" to config.remoteUrl)
            else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
        }.filter { it.second.isNotBlank() }
        val failures = mutableListOf<String>()
        for ((label, address) in addresses) {
            try {
                val url = URL(address.trimEnd('/') + path)
                require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 2500
                    connection.readTimeout = 5000
                    if (config.apiKey.startsWith("session:")) {
                        connection.setRequestProperty("Cookie", "connect.sid=" + config.apiKey.removePrefix("session:"))
                    } else connection.setRequestProperty("X-Api-Key", config.apiKey)
                    connection.setRequestProperty("Accept", "application/json")
                    val status = connection.responseCode
                    if (status !in 200..299) error("HTTP $status")
                    return@withContext connection.inputStream.bufferedReader().use { it.readText() }
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                failures.add("$label: ${e.message ?: e.javaClass.simpleName}")
                if (e.message == "HTTP 401" || e.message == "HTTP 403") break
            }
        }
        error(failures.joinToString("; ").ifBlank { "No Seerr connection configured" })
    }

    suspend fun options(config: ServiceConfig, path: String): Result<List<SeerrBrowseOption>> = runCatching {
        val array = JSONArray(get(config, "/api/v1/$path"))
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { item ->
                val id = item.optInt("id")
                val name = item.optString("name")
                if (id > 0 && name.isNotBlank()) SeerrBrowseOption(id, name) else null
            }
        }
    }

    suspend fun searchStudios(config: ServiceConfig, query: String): Result<List<SeerrBrowseOption>> = runCatching {
        val data = JSONObject(get(config, "/api/v1/search/company?query=${encode(query)}&page=1"))
        val array = data.optJSONArray("results") ?: JSONArray()
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { item ->
                val id = item.optInt("id")
                val name = item.optString("name")
                if (id > 0 && name.isNotBlank()) SeerrBrowseOption(id, name) else null
            }
        }
    }

    suspend fun browse(
        config: ServiceConfig, kind: String, page: Int,
        filter: String = "all", id: Int = 0, query: String = ""
    ): SeerrBrowsePage = try {
        val path = when (kind) {
            "Trending" -> "/discover/trending?mediaType=${encode(filter)}"
            "Popular Movies" -> "/discover/movies?"
            "Popular TV Shows" -> "/discover/tv?"
            "Upcoming Movies" -> "/discover/movies/upcoming?"
            "Upcoming TV Shows" -> "/discover/tv/upcoming?"
            "Movie genre" -> "/discover/movies/genre/$id?"
            "TV genre" -> "/discover/tv/genre/$id?"
            "Studio" -> "/discover/movies/studio/$id?"
            "Network" -> "/discover/tv/network/$id?"
            "Search" -> "/search?query=${encode(query)}&"
            else -> error("Unknown browse category")
        }
        val separator = if (path.contains("?") && !path.endsWith("?") && !path.endsWith("&")) "&" else ""
        val data = JSONObject(get(config, "/api/v1$path${separator}page=${page.coerceAtLeast(1)}"))
        val results = data.optJSONArray("results") ?: JSONArray()
        val items = (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val mediaType = item.optString("mediaType")
            if (mediaType == "person") return@mapNotNull null
            val isTv = when (mediaType) {
                "tv" -> true
                "movie" -> false
                else -> kind in listOf("Popular TV Shows", "Upcoming TV Shows", "TV genre", "Network") ||
                    (kind in listOf("Trending", "Search") && item.has("firstAirDate") && !item.has("releaseDate"))
            }
            val title = item.optString(if (isTv) "name" else "title")
            val tmdbId = item.optInt("id")
            if (tmdbId <= 0 || title.isBlank()) return@mapNotNull null
            val poster = item.optString("posterPath").takeIf { it.startsWith("/") && !it.startsWith("//") }
                ?.let { "https://image.tmdb.org/t/p/w342$it" }
            val date = item.optString(if (isTv) "firstAirDate" else "releaseDate")
            SeerrRelatedTitle(title, poster, date.take(4), item.optDouble("voteAverage", 0.0),
                tmdbId, isTv, item.optJSONObject("mediaInfo")?.optInt("status", 0) ?: 0)
        }
        SeerrBrowsePage(items, data.optInt("page", page),
            data.optInt("totalPages", 1).coerceAtLeast(1))
    } catch (e: Exception) {
        SeerrBrowsePage(page = page, error = e.message ?: e.javaClass.simpleName)
    }
}
