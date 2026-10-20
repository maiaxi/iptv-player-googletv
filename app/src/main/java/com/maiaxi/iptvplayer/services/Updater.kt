package com.maiaxi.iptvplayer.services

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maiaxi.iptvplayer.BuildConfig
import com.maiaxi.iptvplayer.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Updates from GitHub releases, the sideloaded counterpart of the Swift app's Sparkle updater.
 * The release APK is downloaded and handed to the system installer, which asks the user to
 * confirm. Android only installs it over this app when it's signed with the same key.
 */
class Updater(app: Application) : AndroidViewModel(app) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val version: String, val apkUrl: String) : State
        data object Downloading : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    val currentVersion: String = BuildConfig.VERSION_NAME

    init {
        // Debug builds have their own id and version suffix, so they never update from releases.
        if (!BuildConfig.DEBUG) check(quiet = true)
    }

    /** With [quiet], failures and "up to date" leave no message, as for the check at launch. */
    fun check(quiet: Boolean = false) {
        if (state == State.Checking || state == State.Downloading) return
        state = State.Checking
        viewModelScope.launch {
            state = try {
                val latest = latestRelease()
                when {
                    latest != null && isNewer(latest.version, currentVersion) -> latest
                    quiet -> State.Idle
                    else -> State.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) State.Idle else State.Failed(e.userMessage(getApplication()))
            }
        }
    }

    fun install() {
        val available = state as? State.Available ?: return
        val app: Application = getApplication()
        // Android 8+ asks once per app before it may install others.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                app.startActivity(settings)
            } catch (e: ActivityNotFoundException) {
                state = State.Failed(app.getString(R.string.update_allow_unknown))
            }
            return
        }
        state = State.Downloading
        viewModelScope.launch {
            try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val apk = File(dir, "iptv-player-googletv-${available.version}.apk")
                if (!Http.client.download(Request.Builder().url(available.apkUrl).build(), apk)) {
                    throw AppError(R.string.update_download_failed)
                }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                // Stays available, in case the user cancels the installer.
                state = available
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = State.Failed(e.userMessage(app))
            }
        }
    }

    private suspend fun latestRelease(): State.Available? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val response = Http.client.fetch(request)
        if (response.code == 404) return null
        if (response.code !in 200..299) throw AppError(R.string.error_server_status, response.code)
        val release = JSONObject(response.body)
        val version = release.optString("tag_name").removePrefix("v").ifEmpty { return null }
        val assets = release.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") && "debug" !in it.optString("name") }
            ?: return null
        return State.Available(version, apk.getString("browser_download_url"))
    }

    companion object {
        const val REPO = "maiaxi/iptv-player-googletv"

        /** "0.10.1" > "0.9.3": compared number by number, so a pre-release suffix is ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (diff != 0) return diff > 0
            }
            return false
        }
    }
}
