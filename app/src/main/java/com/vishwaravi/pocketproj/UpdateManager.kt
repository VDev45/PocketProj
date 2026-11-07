package com.vishwaravi.pocketproj

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseUrl: String,
    val notes: String
)

/**
 * Matches the machine-readable version line the release workflow writes into every release body:
 *
 *     **Version** `0.3.0` (versionCode `3`)
 *
 * The backticks around the code are optional so a harmless formatting tweak upstream does not
 * silently disable update checks.
 */
private val RELEASE_VERSION =
    Regex("""\*\*Version\*\*\s*`([^`]+)`\s*\(\s*versionCode\s*`?(\d+)`?\s*\)""")

/**
 * Extracts `(versionName, versionCode)` from a release body, or null if it is not there.
 *
 * GitHub's releases API exposes no `version_code` or `version_name` fields — the top-level keys it
 * actually returns are tag_name, name, body, assets and so on — so the body is the only place the
 * published version appears.
 *
 * Returning null makes the caller report "no update available" rather than guess. That is the point:
 * the release tag is `build-<run_number>`, so deriving a version code from it made the GitHub Actions
 * run number stand in for a version, and every release looked permanently newer than the running
 * app. Against the current build that meant users on build 270 (versionCode 3) were always told
 * build 270 was available, under the name "build-270".
 */
internal fun parseReleaseVersion(body: String): Pair<String, Long>? {
    val match = RELEASE_VERSION.find(body) ?: return null
    val name = match.groupValues[1].trim()
    val code = match.groupValues[2].toLongOrNull() ?: return null
    return if (name.isEmpty() || code <= 0L) null else name to code
}

class UpdateManager(private val context: Context) {
    suspend fun checkForUpdate(): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(15_000) {
            runCatchingCancellable {
                val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "PocketProj/" + BuildConfig.VERSION_NAME)
                }
                try {
                    if (connection.responseCode !in 200..299) error("GitHub returned HTTP " + connection.responseCode)
                    val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val body = release.optString("body").trim()
                    val (versionName, versionCode) = parseReleaseVersion(body) ?: return@runCatchingCancellable null
                    val assets = release.optJSONArray("assets")
                    var downloadUrl: String? = null
                    if (assets != null) {
                        for (index in 0 until assets.length()) {
                            val asset = assets.optJSONObject(index) ?: continue
                            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url")
                                if (downloadUrl.isNotBlank()) break
                            }
                        }
                    }
                    if (versionCode <= BuildConfig.VERSION_CODE || downloadUrl.isNullOrBlank()) null
                    else AppUpdate(
                        versionName, versionCode, downloadUrl, release.optString("html_url"), body
                    )
                } finally { connection.disconnect() }
            }
        } ?: Result.failure(IllegalStateException("Update check timed out"))
    }

    suspend fun downloadAndInstall(update: AppUpdate): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val target = File(context.cacheDir, "pocket-proj-update-" + update.versionCode + ".apk")
            val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.android.package-archive")
                setRequestProperty("User-Agent", "PocketProj/" + BuildConfig.VERSION_NAME)
            }
            try {
                if (connection.responseCode !in 200..299) error("Download failed: HTTP " + connection.responseCode)
                connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            } finally { connection.disconnect() }
            val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", target)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    fun canInstallUnknownSources(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/vishwaravi/pocket-proj/releases/latest"
    }
}