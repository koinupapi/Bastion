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
    private suspend fun api(config: ServiceConfig, path: String, payload: JSONObject? = null, method: String = "GET"): JSONObject =
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
                        connection.readTimeout = if (path == "/api/v1/request" && payload != null) 20000 else 7000
                        connection.instanceFollowRedirects = false
                        if (config.apiKey.startsWith("session:")) {
                            connection.setRequestProperty("Cookie", "connect.sid=" + config.apiKey.removePrefix("session:"))
                        } else {
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                        }
                        connection.setRequestProperty("Accept", "application/json")
                        if (payload != null || method != "GET") {
                            connection.requestMethod = if (payload != null && method == "GET") "POST" else method
                            if (payload != null) {
                                connection.doOutput = true
                                connection.setRequestProperty("Content-Type", "application/json")
                                connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
                            }
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
                    if (payload != null || method != "GET") {
                        if (error is java.net.SocketTimeoutException) {
                            throw IllegalStateException("Seerr did not respond in time. Your request may have been received. Check Seerr requests before trying again.", error)
                        }
                        throw IllegalStateException(failure, error)
                    }
                    if (failure.contains("HTTP 401") || failure.contains("HTTP 403")) break
                }
            }
            throw IllegalStateException(failure)
        }

    /** Authenticate as a local Seerr account; returns a session token, not the password. */
    suspend fun loginLocal(local: String, remote: String, mode: String,
        email: String, password: String): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) { runCatching {
            require(email.isNotBlank() && password.isNotBlank()) { "Email and password required" }
            val addresses = when (mode) {
                "Local only" -> listOf("Local" to local)
                "Remote only" -> listOf("Remote" to remote)
                else -> listOf("Local" to local, "Remote" to remote)
            }.filter { it.second.isNotBlank() }
            var failure = "No server configured"
            for ((label, address) in addresses) {
                try {
                    val url = URL(address.trimEnd('/') + "/api/v1/auth/local")
                    require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.connectTimeout = 3000
                        connection.readTimeout = 7000
                        connection.instanceFollowRedirects = false
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Accept", "application/json")
                        connection.outputStream.bufferedWriter().use {
                            it.write(JSONObject().put("email", email.trim())
                                .put("password", password).toString())
                        }
                        val status = connection.responseCode
                        if (status !in 200..299) throw IllegalStateException("HTTP $status")
                        val cookie = connection.headerFields.entries
                            .firstOrNull { it.key?.equals("Set-Cookie", true) == true }
                            ?.value.orEmpty()
                            .firstOrNull { it.startsWith("connect.sid=") }
                            ?.substringAfter("connect.sid=")?.substringBefore(";")
                            ?: throw IllegalStateException("Seerr did not return a session cookie")
                        require(cookie.isNotBlank()) { "Empty Seerr session" }
                        return@runCatching ("session:" + cookie) to label
                    } finally { connection.disconnect() }
                } catch (e: Exception) {
                    failure = "$label: " + (e.message ?: "Connection failed")
                    if (failure.contains("HTTP 401") || failure.contains("HTTP 403")) break
                }
            }
            throw IllegalStateException(failure)
        } }

    /** Read the settings saved on this request, rather than assuming server defaults. */
    suspend fun requestApprovalDetails(config: ServiceConfig, requestId: Int,
        isTv: Boolean): Result<SeerrApprovalDetails> = runCatching {
        require(requestId > 0) { "Invalid request" }
        val request = api(config, "/api/v1/request/$requestId")
        val serverId = request.optInt("serverId", 0).takeIf { it > 0 }
        val profileId = request.optInt("profileId", 0).takeIf { it > 0 }
        val rootFolder = request.optString("rootFolder").takeIf { it.isNotBlank() }
        val options = requestOptions(config, isTv).getOrNull().orEmpty()
        // Pending requests may omit serverId; infer only when exactly one
        // server is configured. Never silently choose among multiple servers.
        val server = options.firstOrNull { it.id == serverId }
            ?: if (serverId == null) options.singleOrNull() else null
        val resolvedProfileId = profileId ?: server?.defaultProfile?.takeIf { it > 0 }
        val profile = server?.profiles?.firstOrNull { it.id == resolvedProfileId }
        val defaultRoot = server?.let { configured ->
            configured.roots.firstOrNull { it.id == configured.defaultRoot }
        }
        val root = server?.roots?.firstOrNull {
            it.name == rootFolder || it.id.toString() == rootFolder
        } ?: if (rootFolder == null) defaultRoot else null
        SeerrApprovalDetails(
            server = server?.name ?: serverId?.let { "Server #$it" },
            profile = profile?.name ?: resolvedProfileId?.let { "Profile #$it" },
            rootFolder = rootFolder ?: root?.name,
            hasServer = server != null,
            hasProfile = resolvedProfileId != null && profile != null,
            hasRoot = rootFolder != null || root != null
        )
    }

    /** Load editable destination from the actual request; never rely on list defaults. */
    suspend fun editableRequestDestination(config: ServiceConfig, requestId: Int):
        Result<SeerrEditableDestination> = runCatching {
        require(requestId > 0) { "Invalid request" }
        val request = api(config, "/api/v1/request/$requestId")
        require(request.optInt("status") == 1) { "Only pending requests can be edited." }
        SeerrEditableDestination(
            serverId = request.optInt("serverId", 0),
            profileId = request.optInt("profileId", 0),
            rootFolder = request.optString("rootFolder"),
            is4k = request.optBoolean("is4k", false)
        )
    }

    /** Update an existing pending request in-place and read it back before reporting success.
     * TV updates MUST include original seasons; otherwise Seerr rejects or alters the request.
     * No automatic approval or duplicate request creation is performed here.
     */
    suspend fun updateRequestDestination(config: ServiceConfig, requestId: Int,
        isTv: Boolean, serverId: Int, profileId: Int, rootFolder: String): Result<Unit> = runCatching {
        // Seerr instance/profile IDs may legitimately be zero. The UI validates
        // selections against the options returned by Seerr; do not reject ID 0.
        require(requestId > 0 && serverId >= 0 && profileId >= 0 && rootFolder.isNotBlank()) {
            "Choose a valid instance, quality profile and root folder."
        }
        require(canManageRequests(config).getOrThrow()) { "No request-management permission." }
        val current = api(config, "/api/v1/request/$requestId")
        require(current.optInt("status") == 1) { "Request is no longer pending. Refresh the request." }
        val body = JSONObject()
            .put("mediaType", if (isTv) "tv" else "movie")
            .put("serverId", serverId)
            .put("profileId", profileId)
            .put("rootFolder", rootFolder)
            .put("is4k", current.optBoolean("is4k", false))
        if (current.has("tags") && !current.isNull("tags")) body.put("tags", current.get("tags"))
        if (isTv) {
            val original = current.optJSONArray("seasons") ?: error("TV request has no seasons; cannot safely edit.")
            val seasons = JSONArray()
            for (i in 0 until original.length()) {
                val season = original.optJSONObject(i)
                val number = season?.optInt("seasonNumber", -1) ?: original.optInt(i, -1)
                require(number >= 0) { "Invalid TV season data; edit in Seerr instead." }
                seasons.put(number)
            }
            require(seasons.length() > 0) { "TV request has no seasons; cannot safely edit." }
            body.put("seasons", seasons)
            if (current.has("languageProfileId") && !current.isNull("languageProfileId"))
                body.put("languageProfileId", current.get("languageProfileId"))
        }
        api(config, "/api/v1/request/$requestId", body, "PUT")
        val verified = api(config, "/api/v1/request/$requestId")
        require(verified.optInt("serverId") == serverId &&
            verified.optInt("profileId") == profileId &&
            verified.optString("rootFolder") == rootFolder) {
            "Seerr accepted the edit, but destination verification failed. Check the request before approving."
        }
        Unit
    }

    /** Create a local Seerr user and set their initial password. No password is persisted. */
    suspend fun createLocalUser(config: ServiceConfig, email: String,
        username: String, password: String): Result<Int> = runCatching {
        require(email.contains("@") && email.substringAfter("@").contains(".")) { "Enter a valid email address" }
        require(username.isNotBlank()) { "Enter a username" }
        require(password.length >= 8) { "Password must be at least 8 characters" }
        val created = api(config, "/api/v1/user", JSONObject()
            .put("email", email.trim())
            .put("username", username.trim())
            .put("permissions", 2))
        val id = created.optInt("id", 0)
        require(id > 0) { "Seerr did not return a user ID; check the Seerr user list" }
        try {
            api(config, "/api/v1/user/$id/settings/password", JSONObject()
                .put("currentPassword", JSONObject.NULL)
                .put("newPassword", password))
        } catch (error: Exception) {
            throw IllegalStateException(
                "User was created (ID $id), but setting their password failed. " +
                "Set the password in Seerr before sharing the account. " +
                (error.message ?: ""), error)
        }
        id
    }

    /** Seerr permission bits: ADMIN=2, MANAGE_REQUESTS=16.
     * Fail closed if the authenticated user's permissions cannot be read. */
    suspend fun canManageRequests(config: ServiceConfig): Result<Boolean> = runCatching {
        val user = api(config, "/api/v1/auth/me")
        require(user.has("permissions") && !user.isNull("permissions")) {
            "Seerr did not provide user permissions"
        }
        val permissions = user.getInt("permissions")
        (permissions and 2) != 0 || (permissions and 16) != 0
    }

    suspend fun changeRequestStatus(config: ServiceConfig, requestId: Int,
        approve: Boolean): Result<Unit> = runCatching {
        require(requestId > 0) { "Invalid request" }
        api(config, "/api/v1/request/$requestId/${if (approve) "approve" else "decline"}", JSONObject())
        Unit
    }

    suspend fun retryRequest(config: ServiceConfig, requestId: Int): Result<Unit> = runCatching {
        require(requestId > 0) { "Invalid request" }
        api(config, "/api/v1/request/$requestId/retry", JSONObject())
        Unit
    }

    suspend fun removeRequest(config: ServiceConfig, requestId: Int): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching {
            require(requestId > 0) { "Invalid request" }
            val addresses = when (config.mode) {
                "Local only" -> listOf("Local" to config.localUrl)
                "Remote only" -> listOf("Remote" to config.remoteUrl)
                else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
            }.filter { it.second.isNotBlank() }
            var failure = "No connection configured"
            var removed = false
            for ((label, address) in addresses) {
                try {
                    val url = URL(address.trimEnd('/') + "/api/v1/request/$requestId")
                    require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "DELETE"
                        connection.connectTimeout = 2500
                        connection.readTimeout = 7000
                        connection.instanceFollowRedirects = false
                        if (config.apiKey.startsWith("session:")) {
                            connection.setRequestProperty("Cookie", "connect.sid=" + config.apiKey.removePrefix("session:"))
                        } else {
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                        }
                        val status = connection.responseCode
                        if (status !in 200..299) {
                            val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                                ?.take(250).orEmpty()
                            throw IllegalStateException("HTTP $status $detail")
                        }
                        removed = true
                    } finally { connection.disconnect() }
                    break
                } catch (e: Exception) {
                    failure = "$label: ${e.message ?: e.javaClass.simpleName}"
                    if (failure.contains("HTTP 401") || failure.contains("HTTP 403")) break
                }
            }
            if (!removed) throw IllegalStateException(failure)
        } }

    suspend fun partialRequestsEnabled(config: ServiceConfig): Result<Boolean> =
        runCatching { api(config, "/api/v1/settings/main").optBoolean("partialRequestsEnabled", false) }

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
                        if (config.apiKey.startsWith("session:")) {
                            connection.setRequestProperty("Cookie", "connect.sid=" + config.apiKey.removePrefix("session:"))
                        } else {
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                        }
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

    /** Read recent requests without fetching each title's details. Used only to
     * reconcile an ambiguous POST; it never submits or retries a request. */
    private suspend fun recentRequestIds(config: ServiceConfig, tmdbId: Int, isTv: Boolean): Set<Int> {
        val result = mutableSetOf<Int>()
        var skip = 0
        repeat(5) {
            val response = api(config, "/api/v1/request?take=100&skip=$skip&sort=added")
            val entries = response.optJSONArray("results") ?: JSONArray()
            for (index in 0 until entries.length()) {
                val request = entries.optJSONObject(index) ?: continue
                val media = request.optJSONObject("media") ?: continue
                if (media.optInt("tmdbId") == tmdbId &&
                    media.optString("mediaType") == if (isTv) "tv" else "movie") {
                    request.optInt("id").takeIf { it > 0 }?.let { result.add(it) }
                }
            }
            if (entries.length() < 100) return result
            skip += entries.length()
        }
        return result
    }

    suspend fun submitRequest(config: ServiceConfig, tmdbId: Int, isTv: Boolean,
        serverId: Int, profileId: Int, rootPath: String, seasons: List<Int>): Result<Unit> =
        runCatching {
            // Best effort: distinguish a newly created request from an older one.
            val previousIds = runCatching { recentRequestIds(config, tmdbId, isTv) }.getOrNull()
            val payload = JSONObject()
                .put("mediaType", if (isTv) "tv" else "movie")
                .put("mediaId", tmdbId)
                .put("serverId", serverId)
                .put("profileId", profileId)
                .put("rootFolder", rootPath)
            if (isTv) payload.put("seasons", JSONArray(seasons))
            try {
                api(config, "/api/v1/request", payload)
            } catch (error: IllegalStateException) {
                if (error.message?.contains("may have been received") == true) {
                    val currentIds = runCatching {
                        recentRequestIds(config, tmdbId, isTv)
                    }.getOrNull()
                    when (SeerrSubmissionReconciliation.classify(previousIds, currentIds)) {
                        SeerrSubmissionReconciliation.Outcome.NEW_REQUEST_FOUND ->
                            throw IllegalStateException(
                                "Seerr created the request, but the response timed out. " +
                                    "Check its status in Requests; it may still fail during processing.", error)
                        SeerrSubmissionReconciliation.Outcome.EXISTING_REQUEST_FOUND ->
                            throw IllegalStateException(
                                "Seerr has a request for this title, but the submission result is uncertain. " +
                                    "Check Requests before trying again.", error)
                        SeerrSubmissionReconciliation.Outcome.UNCONFIRMED -> Unit
                    }
                }
                throw error
            }
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

    suspend fun currentIssueUser(config: ServiceConfig): Result<SeerrIssueUser> = runCatching {
        val user = api(config, "/api/v1/auth/me")
        SeerrIssueUser(user.optInt("id"), user.optInt("permissions"))
    }

    suspend fun deleteIssue(config: ServiceConfig, id: Int): Result<Unit> = runCatching {
        require(id > 0)
        api(config, "/api/v1/issue/$id", method = "DELETE")
        Unit
    }

    suspend fun editIssueComment(config: ServiceConfig, commentId: Int, message: String): Result<Unit> = runCatching {
        require(commentId > 0)
        require(message.isNotBlank()) { "Comment cannot be empty" }
        api(config, "/api/v1/issueComment/$commentId",
            JSONObject().put("message", message.trim()), method = "PUT")
        Unit
    }

    suspend fun deleteIssueComment(config: ServiceConfig, commentId: Int): Result<Unit> = runCatching {
        require(commentId > 0)
        api(config, "/api/v1/issueComment/$commentId", method = "DELETE")
        Unit
    }

    private fun parseIssue(issue: JSONObject): SeerrIssue {
        val media = issue.optJSONObject("media")
        val author = issue.optJSONObject("createdBy")
        val comments = issue.optJSONArray("comments") ?: JSONArray()
        val entries = (0 until comments.length()).mapNotNull { i ->
            val item = comments.optJSONObject(i) ?: return@mapNotNull null
            val message = item.optString("message")
            if (message.isBlank()) return@mapNotNull null
            val user = item.optJSONObject("user")
            SeerrIssueComment(
                id = item.optInt("id"),
                userId = user?.optInt("id") ?: 0,
                author = user?.optString("displayName")?.takeIf { it.isNotBlank() }
                    ?: user?.optString("username")?.takeIf { it.isNotBlank() }
                    ?: "Unknown user",
                message = message,
                createdAt = item.optString("createdAt")
            )
        }
        return SeerrIssue(
            id = issue.optInt("id"),
            creatorId = author?.optInt("id") ?: 0,
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
            message = entries.firstOrNull()?.message.orEmpty(),
            comments = entries.map { it.message },
            commentEntries = entries
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
                        if (config.apiKey.startsWith("session:")) {
                            connection.setRequestProperty("Cookie", "connect.sid=" + config.apiKey.removePrefix("session:"))
                        } else {
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                        }
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

    suspend fun discover(config: ServiceConfig, category: String, query: String = ""): SeerrDiscoverResult =
        withContext(Dispatchers.IO) {
            val paths = mapOf(
                "Trending" to "/api/v1/discover/trending",
                "Popular Movies" to "/api/v1/discover/movies",
                "Popular TV Shows" to "/api/v1/discover/tv",
                "Upcoming Movies" to "/api/v1/discover/movies/upcoming",
                "Upcoming TV Shows" to "/api/v1/discover/tv/upcoming")
            val path = if (category == "Search") "/api/v1/search?query=" + java.net.URLEncoder.encode(query.trim(), "UTF-8") else paths[category] ?: return@withContext SeerrDiscoverResult(error = "Unknown category")
            val addresses = when (config.mode) {
                "Local only" -> listOf("Local" to config.localUrl)
                "Remote only" -> listOf("Remote" to config.remoteUrl)
                else -> listOf("Local" to config.localUrl, "Remote" to config.remoteUrl)
            }.filter { it.second.isNotBlank() }
            val failures = mutableListOf<String>()
            for ((label, address) in addresses) {
                try {
                    val url = URL(address.trimEnd('/') + path + if (category == "Search") "&page=1" else "?page=1")
                    require(url.protocol == "https" || (label == "Local" && url.protocol == "http"))
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 2500
                        connection.readTimeout = 5000
                        if (config.apiKey.startsWith("session:")) {
                            connection.setRequestProperty("Cookie", "connect.sid=" + config.apiKey.removePrefix("session:"))
                        } else {
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                        }
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
                                    ((category == "Trending" || category == "Search") && item.has("firstAirDate") && !item.has("releaseDate"))
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
                            if (config.apiKey.startsWith("session:")) {
                            connection.setRequestProperty("Cookie", "connect.sid=" + config.apiKey.removePrefix("session:"))
                        } else {
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                        }
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
                        seerrMediaId = detail.optJSONObject("mediaInfo")?.optInt("id", 0) ?: 0,
                        tvSeasons = (0 until (detail.optJSONArray("seasons")?.length() ?: 0)).mapNotNull { index ->
                            detail.optJSONArray("seasons")?.optJSONObject(index)?.let { season ->
                                val number = season.optInt("seasonNumber", -1)
                                if (number < 0) null else SeerrTvSeason(number,
                                    season.optString("name").ifBlank { "Season $number" },
                                    season.optInt("episodeCount", 0),
                                    detail.optJSONObject("mediaInfo")?.optJSONArray("seasons")?.let { info ->
                                        (0 until info.length()).mapNotNull { i -> info.optJSONObject(i) }
                                            .firstOrNull { it.optInt("seasonNumber", -1) == number }
                                            ?.optInt("status", 0)
                                    } ?: 0)
                            }
                        })
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
                            if (config.apiKey.startsWith("session:")) {
                            connection.setRequestProperty("Cookie", "connect.sid=" + config.apiKey.removePrefix("session:"))
                        } else {
                            connection.setRequestProperty("X-Api-Key", config.apiKey)
                        }
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
                            4 -> "Failed"
                            5 -> "Completed"
                            else -> "Unknown"
                        }
                        val status = if (requestStatus == "Declined" || requestStatus == "Failed" || requestStatus == "Pending") requestStatus
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
                            overview, releaseDate.takeIf { it.isNotBlank() }, type == "tv", seasons, id,
                            request.optInt("id"), requestStatus, mediaStatus)
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
    val tmdbId: Int = 0,
    val requestId: Int = 0, val requestStatus: String = "",
    val mediaStatus: String? = null
)
data class SeerrRecentResult(
    val instance: String, val total: Int, val requests: List<SeerrRecentRequest>, val error: String?
)

data class SeerrCast(val name: String, val character: String, val photoUrl: String?)
data class SeerrRelatedTitle(val title: String, val posterUrl: String?, val year: String, val rating: Double, val tmdbId: Int, val isTv: Boolean, val mediaStatus: Int = 0)
data class SeerrTvSeason(val number: Int, val name: String, val episodes: Int, val status: Int)
data class SeerrMediaDetails(
    val backdropUrl: String? = null, val posterUrl: String? = null,
    val overview: String? = null, val tagline: String? = null,
    val genres: List<String> = emptyList(), val studios: List<String> = emptyList(),
    val countries: List<String> = emptyList(), val languages: List<String> = emptyList(),
    val directors: List<String> = emptyList(), val cast: List<SeerrCast> = emptyList(),
    val runtime: Int? = null, val rating: Double? = null, val releaseDate: String = "",
    val recommendations: List<SeerrRelatedTitle> = emptyList(),
    val similar: List<SeerrRelatedTitle> = emptyList(),
    val mediaStatus: Int = 0, val seerrMediaId: Int = 0,
    val tvSeasons: List<SeerrTvSeason> = emptyList(), val error: String? = null
)

data class SeerrDiscoverResult(val items: List<SeerrRelatedTitle> = emptyList(), val error: String? = null)

data class SeerrIssueComment(
    val id: Int, val userId: Int, val author: String,
    val message: String, val createdAt: String
)
data class SeerrIssue(
    val id: Int, val creatorId: Int = 0, val type: String, val resolved: Boolean, val tmdbId: Int,
    val author: String, val createdAt: String, val message: String,
    val comments: List<String>,
    val commentEntries: List<SeerrIssueComment> = emptyList()
)
data class SeerrIssueUser(val id: Int, val permissions: Int)
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

/** Values read from the individual Seerr request; absent fields are not inferred. */
data class SeerrApprovalDetails(
    val server: String?, val profile: String?, val rootFolder: String?,
    val hasServer: Boolean, val hasProfile: Boolean, val hasRoot: Boolean
)

/** Selected destination on an existing request, not server defaults. */
data class SeerrEditableDestination(
    val serverId: Int, val profileId: Int, val rootFolder: String, val is4k: Boolean
)
