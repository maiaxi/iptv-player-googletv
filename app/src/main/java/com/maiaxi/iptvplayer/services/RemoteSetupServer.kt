package com.maiaxi.iptvplayer.services

import android.content.Context
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.models.SourceDraft
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * A small web form served by the TV on the local network, so source details can be typed on a
 * phone instead of with the remote. Nothing leaves the network. Each server has a random path,
 * shown only on screen, and other requests are refused. It's short so it can be typed too.
 */
class RemoteSetupServer(
    private val context: Context,
    private val onDraft: (SourceDraft) -> Unit,
) : NanoHTTPD(0) {
    private val token = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** The address to open on a phone, or null when the TV has no local network address. */
    val address: String?
        get() = localAddress()?.let { "http://$it:$listeningPort/$token" }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/$token") return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        if (session.method == Method.POST) {
            // Merges the form fields into `parameters`.
            session.parseBody(HashMap())
            val params = session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
            onDraft(
                SourceDraft(
                    mode = if (params["mode"] == "m3u") SourceDraft.Mode.M3U else SourceDraft.Mode.XTREAM,
                    name = params["name"].orEmpty(),
                    server = params["server"].orEmpty(),
                    username = params["username"].orEmpty(),
                    password = params["password"].orEmpty(),
                    m3uAddress = params["m3u"].orEmpty(),
                ),
            )
            return html(page(sent = true))
        }
        return html(page(sent = false))
    }

    private fun html(body: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun page(sent: Boolean): String {
        fun s(id: Int) = context.getString(id).escaped()
        val content = if (sent) {
            "<p class=\"sent\">${s(R.string.remote_sent)}</p>"
        } else {
            """
            <form method="post" action="/$token" accept-charset="UTF-8">
              <div class="modes">
                <label><input type="radio" name="mode" value="xtream" checked onchange="show()"> ${s(R.string.xtream_codes)}</label>
                <label><input type="radio" name="mode" value="m3u" onchange="show()"> ${s(R.string.m3u_playlist)}</label>
              </div>
              <label>${s(R.string.name)}<input name="name" placeholder="${s(R.string.my_tv)}"></label>
              <div id="xtream">
                <label>${s(R.string.server)}<input name="server" type="url" inputmode="url" placeholder="http://server.com:8080" autocapitalize="off" autocorrect="off"></label>
                <label>${s(R.string.username)}<input name="username" autocapitalize="off" autocorrect="off"></label>
                <label>${s(R.string.password)}<input name="password" type="password"></label>
              </div>
              <div id="m3u" hidden>
                <label>${s(R.string.address)}<input name="m3u" type="url" inputmode="url" placeholder="https://…" autocapitalize="off" autocorrect="off"></label>
                <p class="hint">${s(R.string.xtream_conversion_hint)}</p>
              </div>
              <button>${s(R.string.remote_send)}</button>
            </form>
            <script>
              function show() {
                const m3u = document.querySelector('input[name=mode]:checked').value === 'm3u';
                document.getElementById('m3u').hidden = !m3u;
                document.getElementById('xtream').hidden = m3u;
              }
            </script>
            """.trimIndent()
        }
        return """
            <!doctype html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${s(R.string.app_name)}</title>
            <style>
              body { font: 17px system-ui, sans-serif; background: #0b0d10; color: #eceef2; margin: 0; padding: 24px 16px; }
              main { max-width: 480px; margin: auto; }
              h1 { font-size: 22px; margin: 0 0 20px; }
              label { display: block; margin: 14px 0; color: #b8bcc6; }
              input:not([type=radio]) { display: block; box-sizing: border-box; width: 100%; margin-top: 6px; padding: 12px;
                font: inherit; color: inherit; background: #22262e; border: 1px solid #3a3f49; border-radius: 8px; }
              .modes { display: flex; gap: 20px; } .modes label { color: #eceef2; }
              .hint { font-size: 14px; color: #8b909b; }
              button { width: 100%; margin-top: 12px; padding: 14px; font: inherit; font-weight: 600;
                background: #b4a7ff; color: #1d1065; border: 0; border-radius: 10px; }
              .sent { font-size: 20px; }
            </style></head>
            <body><main><h1>${s(R.string.remote_page_title)}</h1>$content</main></body></html>
        """.trimIndent()
    }

    private companion object {
        const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
        val random = SecureRandom()

        /** The TV's IPv4 address on the local network, preferring a private (site-local) one. */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .sortedByDescending { it.isSiteLocalAddress }
                .firstOrNull()?.hostAddress
        }.getOrNull()

        fun String.escaped() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
