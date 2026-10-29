package com.marklvi.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Public GitHub Releases-based updater. Installation always goes through Android's user-approved installer. */
object UpdateManager {
    private const val OWNER = "heshan-athukorala"
    private const val REPOSITORY = "JARVIS"
    private const val APK_ASSET = "MARK-LVI-JARVIS.apk"
    private const val CHECKSUM_ASSET = "MARK-LVI-JARVIS.apk.sha256"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPOSITORY/releases/latest"

    data class ReleaseInfo(val tag: String, val title: String, val notes: String, val apkUrl: String, val checksumUrl: String)

    fun latestRelease(): ReleaseInfo? {
        val json = JSONObject(getText(API_URL))
        if (json.optBoolean("draft", true) || json.optBoolean("prerelease", true)) return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        var checksumUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            when (asset.optString("name")) {
                APK_ASSET -> apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                CHECKSUM_ASSET -> checksumUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
            }
        }
        val tag = json.optString("tag_name")
        if (tag.isBlank() || apkUrl == null || checksumUrl == null) return null
        return ReleaseInfo(tag, json.optString("name", tag), json.optString("body", "No release notes."), apkUrl!!, checksumUrl!!)
    }

    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        fun parts(value: String): List<Int> = Regex("\\d+").findAll(value).take(3).mapNotNull { it.value.toIntOrNull() }.toList().let { it + List((3 - it.size).coerceAtLeast(0)) { 0 } }
        val remote = parts(remoteTag)
        val current = parts(currentVersion)
        for (i in 0..2) if (remote[i] != current[i]) return remote[i] > current[i]
        return false
    }

    fun downloadAndVerify(activity: Activity, release: ReleaseInfo): File {
        val apkFile = File(activity.cacheDir, APK_ASSET)
        val checksumFile = File(activity.cacheDir, CHECKSUM_ASSET)
        download(release.apkUrl, apkFile)
        download(release.checksumUrl, checksumFile)
        val expected = checksumFile.readText().trim().split(Regex("\\s+")).firstOrNull()?.lowercase(Locale.ROOT)
            ?: throw IllegalStateException("Release checksum file is empty")
        val actual = sha256(apkFile)
        if (!expected.matches(Regex("[0-9a-f]{64}")) || expected != actual) {
            apkFile.delete()
            checksumFile.delete()
            throw SecurityException("APK checksum verification failed. Installation cancelled.")
        }
        return apkFile
    }

    fun openInstaller(activity: Activity, apkFile: File): String {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            activity.startActivity(settingsIntent)
            return "Please allow MARK LVI to install updates, then run the update command again."
        }
        val contentUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
        return "APK verified. Android's installer is open; confirm installation to finish the update."
    }

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "MARK-LVI-Android-Updater")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun download(url: String, destination: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 30000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MARK-LVI-Android-Updater")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Download failed (HTTP ${connection.responseCode})")
            connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        } finally { connection.disconnect() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
