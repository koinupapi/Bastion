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
    suspend fun recentRequests(config: ServiceConfig): SeerrRecentResult =
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
                    val data = get("/api/v1/request?take=3&skip=0&sort=added")
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
                        SeerrRecentRequest(title, status, posterUrl)
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
data class SeerrRecentRequest(val title: String, val status: String, val posterUrl: String?)
data class SeerrRecentResult(
    val instance: String, val total: Int, val requests: List<SeerrRecentRequest>, val error: String?
)
