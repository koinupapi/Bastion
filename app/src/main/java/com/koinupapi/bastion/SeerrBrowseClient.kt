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

/**
 * Shared server-side discovery criteria. Null/empty values mean "use Seerr defaults".
 * Keep draft and applied instances separate in the UI; only applied values should
 * become part of the browse destination and page-cache key.
 */
data class SeerrDiscoverFilters(
    val sortBy: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val ratingFrom: Double? = null,
    val ratingTo: Double? = null,
    val votesFrom: Int? = null,
    val votesTo: Int? = null,
    val runtimeFrom: Int? = null,
    val runtimeTo: Int? = null,
    val originalLanguage: String? = null,
    val watchRegion: String? = null,
    val providerIds: Set<Int> = emptySet(),
    val certification: String? = null,
    val certificationCountry: String? = null,
    val keywordIds: Set<Int> = emptySet(),
    val excludedKeywordIds: Set<Int> = emptySet(),
    val tvStatus: String? = null
) {
    fun isEmpty(): Boolean = this == SeerrDiscoverFilters()
}

/** Explicitly separate broad discovery from fixed lists such as Trending/Upcoming. */
enum class SeerrDiscoverMediaType { MOVIE, TV }

/** Parameter names follow Seerr's /api/v1/discover/movies and /discover/tv API. */
internal fun SeerrDiscoverFilters.toQueryParameters(mediaType: SeerrDiscoverMediaType): Map<String, String> {
    val result = linkedMapOf<String, String>()
    fun put(name: String, value: String?) {
        if (!value.isNullOrBlank()) result[name] = value
    }
    fun ids(values: Set<Int>): String? = values.filter { it > 0 }.sorted()
        .takeIf { it.isNotEmpty() }?.joinToString(",")
    put("sortBy", sortBy)
    val datePrefix = if (mediaType == SeerrDiscoverMediaType.MOVIE) "primaryReleaseDate" else "firstAirDate"
    put("${datePrefix}Gte", dateFrom)
    put("${datePrefix}Lte", dateTo)
    ratingFrom?.let { result["voteAverageGte"] = it.toString() }
    ratingTo?.let { result["voteAverageLte"] = it.toString() }
    votesFrom?.let { result["voteCountGte"] = it.toString() }
    votesTo?.let { result["voteCountLte"] = it.toString() }
    runtimeFrom?.let { result["withRuntimeGte"] = it.toString() }
    runtimeTo?.let { result["withRuntimeLte"] = it.toString() }
    put("language", originalLanguage)
    put("watchRegion", watchRegion)
    // Seerr/TMDB accept provider IDs separated by pipes (OR semantics).
    put("watchProviders", providerIds.filter { it > 0 }.sorted()
        .takeIf { it.isNotEmpty() }?.joinToString("|"))
    put("certification", certification)
    put("certificationCountry", certificationCountry)
    put("keywords", ids(keywordIds))
    put("excludeKeywords", ids(excludedKeywordIds))
    if (mediaType == SeerrDiscoverMediaType.TV) put("status", tvStatus)
    return result
}

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
                    android.util.Log.d("BastionSeerrBrowse", "GET $path via $label: HTTP $status")
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

    /**
     * Advanced discovery via Seerr's main discover endpoints. The criteria are
     * applied server-side, so page counts and results remain accurate.
     */
    suspend fun discover(
        config: ServiceConfig,
        mediaType: SeerrDiscoverMediaType,
        page: Int,
        filters: SeerrDiscoverFilters = SeerrDiscoverFilters(),
        genreId: Int = 0,
        studioOrNetworkId: Int = 0
    ): SeerrBrowsePage {
        val params = linkedMapOf<String, String>()
        if (genreId > 0) params["genre"] = genreId.toString()
        if (studioOrNetworkId > 0) params[
            if (mediaType == SeerrDiscoverMediaType.MOVIE) "studio" else "network"
        ] = studioOrNetworkId.toString()
        params.putAll(filters.toQueryParameters(mediaType))
        val path = if (mediaType == SeerrDiscoverMediaType.MOVIE) "movies" else "tv"
        val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return browse(config,
            if (mediaType == SeerrDiscoverMediaType.MOVIE) "Popular Movies" else "Popular TV Shows",
            page, overridePath = "/discover/$path?$query")
    }

    suspend fun browse(
        config: ServiceConfig, kind: String, page: Int,
        filter: String = "all", id: Int = 0, query: String = "", overridePath: String? = null
    ): SeerrBrowsePage = try {
        val path = overridePath ?: when (kind) {
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
