package com.koinupapi.bastion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ServiceConfig(
    val id: String, val type: String, val name: String,
    val localUrl: String, val remoteUrl: String, val apiKey: String, val mode: String
)

class ServiceStore(context: Context) {
    private val prefs = context.getSharedPreferences("bastion_secure_services", Context.MODE_PRIVATE)
    private val alias = "bastion_service_config_key"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun save(configs: List<ServiceConfig>) {
        val array = JSONArray()
        configs.forEach {
            array.put(JSONObject().put("id", it.id).put("type", it.type).put("name", it.name)
                .put("local", it.localUrl).put("remote", it.remoteUrl)
                .put("key", it.apiKey).put("mode", it.mode))
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("payload", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
    }
    fun load(): List<ServiceConfig> {
        val payload = prefs.getString("payload", null) ?: return emptyList()
        return try {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val json = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                ServiceConfig(obj.getString("id"), obj.getString("type"), obj.getString("name"),
                    obj.getString("local"), obj.getString("remote"), obj.getString("key"), obj.getString("mode"))
            }
        } catch (_: Exception) { emptyList() }
    }
}

object SeerrClient {
    suspend fun test(local: String, remote: String, apiKey: String, mode: String): String =
        withContext(Dispatchers.IO) {
            val addresses = when (mode) {
                "Local only" -> listOf("Local" to local)
                "Remote only" -> listOf("Remote" to remote)
                else -> listOf("Local" to local, "Remote" to remote)
            }.filter { it.second.isNotBlank() }
            val failures = mutableListOf<String>()
            for ((label, address) in addresses) {
                try {
                    val url = URL(address.trimEnd('/') + "/api/v1/status")
                    require(url.protocol == "https" || (label == "Local" && url.protocol == "http")) {
                        "Invalid URL scheme"
                    }
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 2500
                        connection.readTimeout = 4000
                        connection.setRequestProperty("X-Api-Key", apiKey)
                        connection.setRequestProperty("Accept", "application/json")
                        val status = connection.responseCode
                        if (status in 200..299) {
                            val body = connection.inputStream.bufferedReader().use { it.readText() }
                            val data = JSONObject(body)
                            if (data.has("version") || data.has("commitTag") || data.has("updateAvailable"))
                                return@withContext "Connected via ${label.lowercase()} address (HTTP $status)"
                            failures.add("$label: unexpected response")
                        } else failures.add("$label: HTTP $status")
                    } finally { connection.disconnect() }
                } catch (error: Exception) {
                    failures.add("$label: ${error.javaClass.simpleName}")
                }
            }
            "Connection failed — " + failures.joinToString("; ")
        }
    private suspend fun api(config: ServiceConfig, path: String, payload: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val addresses = when (config.mode) {
                "Local only" -> listOf("Local" to config.localUrl)
                "Remote only" -> listOf("Remote" to config.remoteUrl)
                else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
            }.filter { it.second.isNotBlank() }
            var failure = "No connection configured"
            for ((label, address) in addresses) {
                try {
                    val url = URL(address.trimEnd('/') + path)
                    require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 2500
                        connection.readTimeout = 7000
                        connection.instanceFollowRedirects = false
                        connection.setRequestProperty("X-Api-Key", config.apiKey)
                        connection.setRequestProperty("Accept", "application/json")
                        if (payload != null) {
                            connection.requestMethod = "POST"
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
                        }
                        val status = connection.responseCode
                        if (status !in 200..299) {
                            val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                                ?.take(250).orEmpty()
                            throw IllegalStateException("HTTP $status $detail")
                        }
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        return@withContext if (body.isBlank()) JSONObject() else JSONObject(body)
                    } finally { connection.disconnect() }
                } catch (error: Exception) {
                    failure = "$label: ${error.message ?: error.javaClass.simpleName}"
                    if (failure.contains("HTTP 401") || failure.contains("HTTP 403")) break
                }
            }
            throw IllegalStateException(failure)
        }

    suspend fun requestOptions(config: ServiceConfig, isTv: Boolean): Result<List<SeerrRequestServer>> =
        withContext(Dispatchers.IO) { runCatching {
            val path = if (isTv) "/api/v1/service/sonarr" else "/api/v1/service/radarr"
            val addresses = when (config.mode) {
                "Local only" -> listOf("Local" to config.localUrl)
                "Remote only" -> listOf("Remote" to config.remoteUrl)
                else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
            }.filter { it.second.isNotBlank() }
            var raw: String? = null
            var error = "No connection configured"
            for ((label, address) in addresses) {
                try {
                    val url = URL(address.trimEnd('/') + path)
                    require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 2500
                        connection.readTimeout = 7000
                        connection.setRequestProperty("X-Api-Key", config.apiKey)
                        connection.setRequestProperty("Accept", "application/json")
                        val status = connection.responseCode
                        if (status !in 200..299) throw IllegalStateException("HTTP $status")
                        raw = connection.inputStream.bufferedReader().use { it.readText() }
                    } finally { connection.disconnect() }
                    break
                } catch (e: Exception) {
                    error = "$label: ${e.message ?: e.javaClass.simpleName}"
                    if (error.contains("HTTP 401") || error.contains("HTTP 403")) break
                }
            }
            val response = raw ?: throw IllegalStateException(error)
            val array = if (response.trimStart().startsWith("[")) JSONArray(response)
                else JSONObject(response).optJSONArray("results") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                val entry = array.optJSONObject(i) ?: return@mapNotNull null
                // The server list is intentionally non-sensitive and does not contain
                // quality profiles/root folders. Fetch the documented per-server endpoint.
                val detail = api(config, "$path/${entry.optInt("id")}")
                val settings = detail.optJSONObject("server") ?: entry
                val profiles = detail.optJSONArray("profiles") ?: JSONArray()
                val roots = detail.optJSONArray("rootFolders")
                    ?: detail.optJSONArray("rootfolders")
                    ?: detail.optJSONArray("directories") ?: JSONArray()
                SeerrRequestServer(
                    id = entry.optInt("id"),
                    name = entry.optString("name"),
                    profiles = (0 until profiles.length()).mapNotNull { j ->
                        profiles.optJSONObject(j)?.let {
                            SeerrRequestChoice(it.optInt("id"), it.optString("name"))
                        }
                    },
                    roots = (0 until roots.length()).mapNotNull { j ->
                        roots.optJSONObject(j)?.let {
                            SeerrRequestChoice(it.optInt("id"), it.optString("path").ifBlank { it.optString("name") })
                        }
                    },
                    defaultProfile = settings.optInt("activeProfileId"),
                    defaultRoot = roots.let { folders ->
                        (0 until folders.length()).firstOrNull { index ->
                            folders.optJSONObject(index)?.optString("path") == settings.optString("activeDirectory")
                        }?.let { folders.optJSONObject(it)?.optInt("id") } ?: -1
                    }
                )
            }
        } }

    suspend fun submitRequest(config: ServiceConfig, tmdbId: Int, isTv: Boolean,
        serverId: Int, profileId: Int, rootPath: String, seasons: List<Int>): Result<Unit> =
        runCatching {
            val payload = JSONObject()
                .put("mediaType", if (isTv) "tv" else "movie")
                .put("mediaId", tmdbId)
                .put("serverId", serverId)
                .put("profileId", profileId)
                .put("rootFolder", rootPath)
            if (isTv) payload.put("seasons", JSONArray(seasons))
            api(config, "/api/v1/request", payload)
            Unit
        }

    suspend fun submitIssue(config: ServiceConfig, mediaId: Int, type: Int, message: String): Result<Unit> =
        runCatching {
            require(mediaId > 0) { "Seerr media record is unavailable" }
            api(config, "/api/v1/issue", JSONObject()
                .put("mediaId", mediaId).put("issueType", type).put("message", message))
            Unit
        }

    suspend fun issueDetail(config: ServiceConfig, id: Int): Result<SeerrIssue> = runCatching {
        parseIssue(api(config, "/api/v1/issue/$id"))
    }

    suspend fun commentOnIssue(config: ServiceConfig, id: Int, message: String): Result<Unit> =
        runCatching {
            require(message.isNotBlank()) { "Enter a reply" }
            api(config, "/api/v1/issue/$id/comment", JSONObject().put("message", message.trim()))
            Unit
        }

    suspend fun changeIssueStatus(config: ServiceConfig, id: Int, resolved: Boolean): Result<Unit> =
        runCatching {
            api(config, "/api/v1/issue/$id/${if (resolved) "resolved" else "open"}", JSONObject())
            Unit
        }

    private fun parseIssue(issue: JSONObject): SeerrIssue {
        val media = issue.optJSONObject("media")
        val author = issue.optJSONObject("createdBy")
        val comments = issue.optJSONArray("comments") ?: JSONArray()
        val messages = (0 until comments.length()).mapNotNull { i ->
            comments.optJSONObject(i)?.optString("message")?.takeIf { it.isNotBlank() }
        }
        return SeerrIssue(
            id = issue.optInt("id"),
            type = when (issue.optInt("issueType")) {
                1 -> "Video"
                2 -> "Audio"
                3 -> "Subtitles"
                4 -> "Other"
                else -> "Issue"
            },
            resolved = issue.optInt("status") == 2,
            tmdbId = media?.optInt("tmdbId") ?: 0,
            author = author?.optString("displayName")?.takeIf { it.isNotBlank() }
                ?: author?.optString("username")?.takeIf { it.isNotBlank() }
                ?: "Unknown user",
            createdAt = issue.optString("createdAt").take(10),
            message = messages.firstOrNull().orEmpty(),
            comments = messages
        )
    }

    suspend fun issuePage(config: ServiceConfig, take: Int, skip: Int, filter: String): SeerrIssueResult =
        withContext(Dispatchers.IO) {
            val addresses = when (config.mode) {
                "Local only" -> listOf("Local" to config.localUrl)
                "Remote only" -> listOf("Remote" to config.remoteUrl)
                else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
            }.filter { it.second.isNotBlank() }
            val failures = mutableListOf<String>()
            for ((label, address) in addresses) {
                try {
                    val url = URL(address.trimEnd('/') +
                        "/api/v1/issue?take=$take&skip=$skip&sort=added&filter=${filter.lowercase()}")
                    require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 2500
                        connection.readTimeout = 5000
                        connection.setRequestProperty("X-Api-Key", config.apiKey)
                        connection.setRequestProperty("Accept", "application/json")
                        val code = connection.responseCode
                        if (code !in 200..299) throw IllegalStateException("HTTP $code")
                        val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        val rows = data.optJSONArray("results") ?: JSONArray()
                        val issues = (0 until rows.length()).mapNotNull { index ->
                            val issue = rows.optJSONObject(index) ?: return@mapNotNull null
                            parseIssue(issue)
                        }
                        return@withContext SeerrIssueResult(
                            issues, data.optJSONObject("pageInfo")?.optInt("results", issues.size)
                                ?: issues.size)
                    } finally { connection.disconnect() }
                } catch (e: Exception) {
                    failures.add("$label: ${e.message ?: e.javaClass.simpleName}")
                    if (e.message == "HTTP 401" || e.message == "HTTP 403") break
                }
            }
            SeerrIssueResult(error = failures.joinToString("; ").ifBlank { "No connection configured" })
        }

    suspend fun discover(config: ServiceConfig, category: String): SeerrDiscoverResult =
        withContext(Dispatchers.IO) {
            val paths = mapOf(
                "Trending" to "/api/v1/discover/trending",
                "Popular Movies" to "/api/v1/discover/movies",
                "Popular TV Shows" to "/api/v1/discover/tv",
                "Upcoming Movies" to "/api/v1/discover/movies/upcoming",
                "Upcoming TV Shows" to "/api/v1/discover/tv/upcoming")
            val path = paths[category] ?: return@withContext SeerrDiscoverResult(error = "Unknown category")
            val addresses = when (config.mode) {
                "Local only" -> listOf("Local" to config.localUrl)
                "Remote only" -> listOf("Remote" to config.remoteUrl)
                else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
            }.filter { it.second.isNotBlank() }
            val failures = mutableListOf<String>()
            for ((label, address) in addresses) {
                try {
                    val url = URL(address.trimEnd('/') + path + "?page=1")
                    require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 2500
                        connection.readTimeout = 5000
                        connection.setRequestProperty("X-Api-Key", config.apiKey)
                        connection.setRequestProperty("Accept", "application/json")
                        val code = connection.responseCode
                        if (code !in 200..299) throw IllegalStateException("HTTP $code")
                        val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        val results = data.optJSONArray("results") ?: JSONArray()
                        val items = (0 until results.length()).mapNotNull { index ->
                            val item = results.optJSONObject(index) ?: return@mapNotNull null
                            val id = item.optInt("id", 0)
                            val isTv = when (item.optString("mediaType")) {
                                "tv" -> true
                                "movie" -> false
                                else -> category.contains("TV") ||
                                    (category == "Trending" && item.has("firstAirDate") && !item.has("releaseDate"))
                            }
                            val title = item.optString(if (isTv) "name" else "title")
                            if (id <= 0 || title.isBlank()) return@mapNotNull null
                            val poster = item.optString("posterPath")
                                .takeIf { it.startsWith("/") && !it.startsWith("//") }
                                ?.let { "https://image.tmdb.org/t/p/w342$it" }
                            val date = item.optString(if (isTv) "firstAirDate" else "releaseDate")
                            SeerrRelatedTitle(title, poster, date.take(4),
                                item.optDouble("voteAverage", 0.0), id, isTv,
                                item.optJSONObject("mediaInfo")?.optInt("status", 0) ?: 0)
                        }
                        return@withContext SeerrDiscoverResult(items)
                    } finally { connection.disconnect() }
                } catch (e: Exception) {
                    failures.add("$label: ${e.message ?: e.javaClass.simpleName}")
                    if (e.message == "HTTP 401" || e.message == "HTTP 403") break
                }
            }
            SeerrDiscoverResult(error = failures.joinToString("; ").ifBlank { "No connection configured" })
        }

    suspend fun mediaDetails(config: ServiceConfig, request: SeerrRecentRequest): SeerrMediaDetails =
        withContext(Dispatchers.IO) {
            if (request.tmdbId <= 0) return@withContext SeerrMediaDetails(error = "Missing TMDB identifier")
            val addresses = when (config.mode) {
                "Local only" -> listOf("Local" to config.localUrl)
                "Remote only" -> listOf("Remote" to config.remoteUrl)
                else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
            }.filter { it.second.isNotBlank() }
            val errors = mutableListOf<String>()
            for ((label, address) in addresses) {
                try {
                    fun get(path: String): JSONObject {
                        val url = URL(address.trimEnd('/') + path)
                        require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                        val connection = url.openConnection() as HttpURLConnection
                        try {
                            connection.instanceFollowRedirects = false
                            connection.connectTimeout = 3000
                            connection.readTimeout = 6000
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                            connection.setRequestProperty("Accept", "application/json")
                            val status = connection.responseCode
                            if (status !in 200..299) throw IllegalStateException("HTTP $status")
                            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        } finally { connection.disconnect() }
                    }
                    val kind = if (request.isTv) "tv" else "movie"
                    val base = "/api/v1/$kind/${request.tmdbId}"
                    val detail = get(base)
                    fun names(array: JSONArray?): List<String> =
                        (0 until (array?.length() ?: 0)).mapNotNull {
                            array?.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank)
                        }
                    fun image(path: String?, size: String): String? =
                        path?.takeIf { it.startsWith("/") && !it.startsWith("//") }
                            ?.let { "https://image.tmdb.org/t/p/$size$it" }
                    val castArray = detail.optJSONObject("credits")?.optJSONArray("cast")
                    val cast = (0 until minOf(castArray?.length() ?: 0, 18)).mapNotNull { i ->
                        castArray?.optJSONObject(i)?.let {
                            SeerrCast(it.optString("name"), it.optString("character"),
                                image(it.optString("profilePath"), "w185"))
                        }
                    }
                    val crew = detail.optJSONObject("credits")?.optJSONArray("crew")
                    val directors = (0 until (crew?.length() ?: 0)).mapNotNull { i ->
                        crew?.optJSONObject(i)?.takeIf { it.optString("job") == "Director" }
                            ?.optString("name")?.takeIf(String::isNotBlank)
                    }
                    fun related(suffix: String): List<SeerrRelatedTitle> = try {
                        val arr = get("$base/$suffix").optJSONArray("results")
                        (0 until minOf(arr?.length() ?: 0, 16)).mapNotNull { i ->
                            arr?.optJSONObject(i)?.let {
                                val title = it.optString(if (request.isTv) "name" else "title")
                                if (title.isBlank() || it.optInt("id", 0) <= 0) null else SeerrRelatedTitle(title,
                                    image(it.optString("posterPath"), "w342"),
                                    it.optString(if (request.isTv) "firstAirDate" else "releaseDate").take(4),
                                    it.optDouble("voteAverage", 0.0), it.optInt("id"), request.isTv,
                                    it.optJSONObject("mediaInfo")?.optInt("status") ?: 0)
                            }
                        }
                    } catch (_: Exception) { emptyList() }
                    return@withContext SeerrMediaDetails(
                        backdropUrl = image(detail.optString("backdropPath"), "w1280"),
                        posterUrl = image(detail.optString("posterPath"), "w500"),
                        overview = detail.optString("overview").takeIf(String::isNotBlank),
                        tagline = detail.optString("tagline").takeIf(String::isNotBlank),
                        genres = names(detail.optJSONArray("genres")),
                        studios = names(detail.optJSONArray(if (request.isTv) "networks" else "productionCompanies")),
                        countries = names(detail.optJSONArray("productionCountries")),
                        languages = names(detail.optJSONArray("spokenLanguages")),
                        directors = directors, cast = cast,
                        runtime = detail.optInt("runtime", 0).takeIf { it > 0 }
                            ?: detail.optJSONArray("episodeRunTime")?.optInt(0)?.takeIf { it > 0 },
                        rating = detail.optDouble("voteAverage", 0.0).takeIf { it > 0 },
                        releaseDate = detail.optString(if (request.isTv) "firstAirDate" else "releaseDate"),
                        recommendations = related("recommendations"), similar = related("similar"),
                        mediaStatus = detail.optJSONObject("mediaInfo")?.optInt("status", 0) ?: 0,
                        seerrMediaId = detail.optJSONObject("mediaInfo")?.optInt("id", 0) ?: 0)
                } catch (e: Exception) {
                    errors.add("$label: ${e.message ?: e.javaClass.simpleName}")
                    if (e.message == "HTTP 401" || e.message == "HTTP 403") break
                }
            }
            SeerrMediaDetails(error = errors.joinToString("; ").ifBlank { "No connection configured" })
        }

    suspend fun recentRequests(config: ServiceConfig): SeerrRecentResult =
        requestPage(config, 3, 0, "All")

    suspend fun requestPage(config: ServiceConfig, take: Int, skip: Int, filter: String): SeerrRecentResult =
        withContext(Dispatchers.IO) {
            val addresses = when (config.mode) {
                "Local only" -> listOf("Local" to config.localUrl)
                "Remote only" -> listOf("Remote" to config.remoteUrl)
                else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
            }.filter { it.second.isNotBlank() }
            val failures = mutableListOf<String>()
            for ((label, address) in addresses) {
                try {
                    fun get(path: String): JSONObject {
                        val url = URL(address.trimEnd('/') + path)
                        require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                        val connection = url.openConnection() as HttpURLConnection
                        try {
                            connection.instanceFollowRedirects = false
                            connection.connectTimeout = 2500
                            connection.readTimeout = 4000
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                            connection.setRequestProperty("Accept", "application/json")
                            val code = connection.responseCode
                            if (code !in 200..299) throw IllegalStateException("HTTP $code")
                            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        } finally { connection.disconnect() }
                    }
                    val queryFilter = when (filter) {
                        "Pending" -> "&filter=pending"
                        "Approved" -> "&filter=approved"
                        else -> ""
                    }
                    val data = get("/api/v1/request?take=$take&skip=$skip&sort=added$queryFilter")
                    val total = data.optInt("pageInfoTotalResults", -1).let {
                        if (it >= 0) it else data.optJSONObject("pageInfo")?.optInt("results", 0) ?: 0
                    }
                    val entries = data.optJSONArray("results") ?: JSONArray()
                    val requests = (0 until entries.length()).mapNotNull { index ->
                        val request = entries.optJSONObject(index) ?: return@mapNotNull null
                        val media = request.optJSONObject("media") ?: return@mapNotNull null
                        val type = media.optString("mediaType", "movie")
                        val id = media.optInt("tmdbId", 0)
                        val fallback = if (type == "tv") "Series #$id" else "Movie #$id"
                        val detail = if (id > 0) try {
                            get("/api/v1/${if (type == "tv") "tv" else "movie"}/$id")
                        } catch (_: Exception) { null } else null
                        val title = detail?.optString(if (type == "tv") "name" else "title")
                            ?.takeIf { it.isNotBlank() } ?: fallback
                        val posterPath = detail?.optString("posterPath")
                            ?.takeIf { it.startsWith("/") && !it.startsWith("//") }
                        val posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w185$it" }
                        val mediaStatus = when (media.optInt("status", 0)) {
                            2 -> "Pending"
                            3 -> "Processing"
                            4 -> "Partially available"
                            5 -> "Available"
                            else -> null
                        }
                        val requestStatus = when (request.optInt("status", 0)) {
                            1 -> "Pending"
                            2 -> "Approved"
                            3 -> "Declined"
                            else -> "Requested"
                        }
                        val status = if (requestStatus == "Declined") requestStatus
                            else mediaStatus ?: requestStatus
                        val releaseDate = detail?.optString(
                            if (type == "tv") "firstAirDate" else "releaseDate").orEmpty()
                        val year = releaseDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
                        val requester = request.optJSONObject("requestedBy")
                        val requestedBy = requester?.optString("displayName")
                            ?.takeIf { it.isNotBlank() }
                            ?: requester?.optString("username")?.takeIf { it.isNotBlank() }
                        val overview = detail?.optString("overview")?.takeIf { it.isNotBlank() }
                        val seasons = request.optJSONArray("seasons")?.let { arr ->
                            (0 until arr.length()).mapNotNull { n ->
                                arr.optJSONObject(n)?.optInt("seasonNumber", -1)?.takeIf { it >= 0 }
                            }.distinct().sorted()
                        }.orEmpty()
                        SeerrRecentRequest(title, status, posterUrl, year, requestedBy,
                            overview, releaseDate.takeIf { it.isNotBlank() }, type == "tv", seasons, id)
                    }
                    return@withContext SeerrRecentResult(config.name, total.coerceAtLeast(requests.size), requests, null)
                } catch (e: Exception) {
                    failures.add("$label: ${e.message ?: e.javaClass.simpleName}")
                    if (e.message == "HTTP 401" || e.message == "HTTP 403") break
                }
            }
            SeerrRecentResult(config.name, 0, emptyList(), failures.joinToString("; ").ifBlank { "No connection configured" })
        }
}
data class SeerrRecentRequest(
    val title: String, val status: String, val posterUrl: String?,
    val year: String?, val requestedBy: String?,
    val overview: String? = null, val releaseDate: String? = null,
    val isTv: Boolean = false, val seasons: List<Int> = emptyList(),
    val tmdbId: Int = 0
)
data class SeerrRecentResult(
    val instance: String, val total: Int, val requests: List<SeerrRecentRequest>, val error: String?
)

data class SeerrCast(val name: String, val character: String, val photoUrl: String?)
data class SeerrRelatedTitle(val title: String, val posterUrl: String?, val year: String, val rating: Double, val tmdbId: Int, val isTv: Boolean, val mediaStatus: Int = 0)
data class SeerrMediaDetails(
    val backdropUrl: String? = null, val posterUrl: String? = null,
    val overview: String? = null, val tagline: String? = null,
    val genres: List<String> = emptyList(), val studios: List<String> = emptyList(),
    val countries: List<String> = emptyList(), val languages: List<String> = emptyList(),
    val directors: List<String> = emptyList(), val cast: List<SeerrCast> = emptyList(),
    val runtime: Int? = null, val rating: Double? = null, val releaseDate: String = "",
    val recommendations: List<SeerrRelatedTitle> = emptyList(),
    val similar: List<SeerrRelatedTitle> = emptyList(),
    val mediaStatus: Int = 0, val seerrMediaId: Int = 0, val error: String? = null
)

data class SeerrDiscoverResult(val items: List<SeerrRelatedTitle> = emptyList(), val error: String? = null)

data class SeerrIssue(
    val id: Int, val type: String, val resolved: Boolean, val tmdbId: Int,
    val author: String, val createdAt: String, val message: String,
    val comments: List<String>
)
data class SeerrIssueResult(
    val issues: List<SeerrIssue> = emptyList(), val total: Int = 0,
    val error: String? = null
)

data class SeerrRequestChoice(val id: Int, val name: String)
data class SeerrRequestServer(
    val id: Int, val name: String,
    val profiles: List<SeerrRequestChoice>, val roots: List<SeerrRequestChoice>,
    val defaultProfile: Int, val defaultRoot: Int
)
