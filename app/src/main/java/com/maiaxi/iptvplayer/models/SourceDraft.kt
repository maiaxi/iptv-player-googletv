package com.maiaxi.iptvplayer.models

import android.content.Context
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.services.AppError
import com.maiaxi.iptvplayer.services.LibraryStore
import com.maiaxi.iptvplayer.services.XtreamClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** The fields of the add/edit source form, and how they turn into a `Source`. */
data class SourceDraft(
    val mode: Mode = Mode.XTREAM,
    val name: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uAddress: String = "",
) {
    enum class Mode { XTREAM, M3U }

    val isValid: Boolean
        get() = when (mode) {
            Mode.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
            Mode.M3U -> m3uAddress.trim().toHttpUrlOrNull() != null
        }

    /**
     * Validates the draft, checks Xtream credentials against the server, and adds or updates the
     * source. Throws an [AppError] with a user-facing message.
     */
    suspend fun save(editing: Source?, library: LibraryStore, context: Context) {
        val kind: SourceKind = when (mode) {
            Mode.XTREAM -> {
                var address = server.trim()
                if ("://" !in address) address = "http://$address"
                address.toHttpUrlOrNull() ?: throw AppError(R.string.error_server_address)
                SourceKind.Xtream(address, username.trim(), password)
            }
            Mode.M3U -> {
                val url = m3uAddress.trim().toHttpUrlOrNull() ?: throw AppError(R.string.error_playlist_address)
                xtreamFromM3u(url) ?: SourceKind.M3u(url.toString())
            }
        }

        if (kind is SourceKind.Xtream) {
            XtreamClient(context, kind.server, kind.username, kind.password, UUID.randomUUID().toString()).accountInfo()
        }

        val fallbackName = when (kind) {
            is SourceKind.Xtream -> kind.server.toHttpUrlOrNull()?.host ?: "IPTV"
            is SourceKind.M3u -> kind.url.toHttpUrlOrNull()?.host ?: context.getString(R.string.m3u_playlist)
        }
        val finalName = name.trim().ifEmpty { fallbackName }

        if (editing != null) {
            library.update(editing.copy(name = finalName, kind = kind))
        } else {
            library.add(Source(name = finalName, kind = kind))
        }
    }

    companion object {
        fun from(source: Source): SourceDraft = when (val kind = source.kind) {
            is SourceKind.Xtream -> SourceDraft(
                Mode.XTREAM, source.name, server = kind.server, username = kind.username, password = kind.password,
            )
            is SourceKind.M3u -> SourceDraft(Mode.M3U, source.name, m3uAddress = kind.url)
        }

        /** `http://host:port/get.php?username=u&password=p&type=m3u_plus` → Xtream account. */
        private fun xtreamFromM3u(url: HttpUrl): SourceKind.Xtream? {
            if (url.pathSegments.lastOrNull() != "get.php") return null
            val user = url.queryParameter("username") ?: return null
            val pass = url.queryParameter("password") ?: return null
            val server = HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port).build()
            return SourceKind.Xtream(server.toString().trimEnd('/'), user, pass)
        }
    }
}
