package com.koinupapi.bastion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class BastionUpdate(val versionCode: Int, val url: String, val digest: String?, val page: String, val branch: String = "main")

object BastionUpdater {
    private const val RELEASES = "https://api.github.com/repos/koinupapi/Bastion/releases?per_page=20"

    suspend fun check(context: Context, branch: String = "main"): BastionUpdate? = withContext(Dispatchers.IO) {
        val installed = if (Build.VERSION.SDK_INT >= 33)
            context.packageManager.getPackageInfo(context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)).longVersionCode
        else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val conn = URL(RELEASES).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Bastion-Android")
            if (conn.responseCode !in 200..299) error("GitHub returned HTTP ${conn.responseCode}")
            val releases = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until releases.length()).mapNotNull { index ->
                val release = releases.getJSONObject(index)
                if (release.optBoolean("draft")) return@mapNotNull null
                val version = Regex("^(?:dev|build)-(\\d+)$").matchEntire(release.optString("tag_name"))
                    ?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                if (version <= installed) return@mapNotNull null
                val tag = release.optString("tag_name")
                if (tag.startsWith("build-")) {
                    val releaseBranch = release.optString("body").lineSequence()
                        .firstOrNull { it.startsWith("Branch: ") }?.removePrefix("Branch: ")?.trim()
                    if (releaseBranch != branch) return@mapNotNull null
                } else if (branch != "main") return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?: return@mapNotNull null
                BastionUpdate(version, asset.getString("browser_download_url"),
                    asset.optString("digest").takeIf { it.startsWith("sha256:") },
                    release.optString("html_url"), branch)
            }.maxByOrNull { it.versionCode }
        } finally { conn.disconnect() }
    }

    suspend fun availableBranches(): List<String> = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/koinupapi/Bastion/branches?per_page=100").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Bastion-Android")
            if (conn.responseCode !in 200..299) error("GitHub returned HTTP " + conn.responseCode)
            val items = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until items.length()).mapNotNull { i ->
                items.getJSONObject(i).optString("name").takeIf { it.startsWith("feature/") }
            }.sorted()
        } finally { conn.disconnect() }
    }

    suspend fun download(context: Context, update: BastionUpdate): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "Bastion-${update.versionCode}.apk")
        val temporary = File(directory, "Bastion-${update.versionCode}.part")
        val conn = URL(update.url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Bastion-Android")
            if (conn.responseCode !in 200..299) error("APK download failed: HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> temporary.outputStream().use { input.copyTo(it) } }
            require(temporary.length() > 0) { "Downloaded APK is empty" }
            update.digest?.removePrefix("sha256:")?.let { expected ->
                val digest = MessageDigest.getInstance("SHA-256")
                temporary.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(expected, ignoreCase = true)) { "APK checksum mismatch" }
            }
            require(temporary.renameTo(target)) { "Could not prepare APK" }
            target
        } finally { conn.disconnect(); temporary.delete() }
    }

    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
