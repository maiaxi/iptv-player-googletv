package com.maiaxi.iptvplayer.services

import com.maiaxi.iptvplayer.models.EpgEntry
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Streaming XMLTV parser. Guides are often 10–100 MB, so only programmes for the wanted channels
 * inside a time window are kept. Channel ids are matched case-insensitively, and the result is
 * keyed by lowercased id.
 */
object XmltvParser {
    fun parse(
        input: InputStream,
        parser: XmlPullParser,
        channels: Set<String>,
        from: Long,
        to: Long,
    ): Map<String, List<EpgEntry>> {
        val wanted = channels.mapTo(HashSet()) { it.lowercase() }
        val guide = HashMap<String, MutableList<EpgEntry>>()
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Feeds are full of undeclared entities like &nbsp;; relaxed mode skips them.
            runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
            parser.setInput(unzipped(input), null)
            readProgrammes(parser, wanted, from, to, guide)
        } catch (e: XmlPullParserException) {
            // A broken feed still yields the programmes read before the error, as in the Swift app.
        } catch (e: IOException) {
        }
        return guide.mapValues { (_, entries) -> entries.sortedBy { it.start } }
    }

    private fun readProgrammes(
        parser: XmlPullParser,
        wanted: Set<String>,
        from: Long,
        to: Long,
        guide: HashMap<String, MutableList<EpgEntry>>,
    ) {
        var channel: String? = null
        var start = 0L
        var end = 0L
        var title = ""
        var desc = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channel = null
                        val id = parser.getAttributeValue(null, "channel")?.lowercase()
                        val s = date(parser.getAttributeValue(null, "start"))
                        val e = date(parser.getAttributeValue(null, "stop"))
                        if (id != null && id in wanted && s != null && e != null && e > from && s < to) {
                            channel = id
                            start = s
                            end = e
                            title = ""
                            desc = ""
                        }
                    }
                    "title" -> if (channel != null && title.isEmpty()) title = parser.nextText().trim()
                    "desc" -> if (channel != null && desc.isEmpty()) desc = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme" && channel != null) {
                    if (title.isNotEmpty()) {
                        // Some feeds list the same slot twice with slightly different times; keep the first.
                        val entries = guide.getOrPut(channel) { mutableListOf() }
                        if (entries.none { it.start < end && it.end > start && it.title == title }) {
                            entries += EpgEntry(title, desc.ifEmpty { null }, start, end)
                        }
                    }
                    channel = null
                }
            }
            event = parser.next()
        }
    }

    private fun unzipped(input: InputStream): InputStream {
        val buffered = BufferedInputStream(input)
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        return if (gzip) GZIPInputStream(buffered) else buffered
    }

    /** `20240301193000 +0100`, `20240301193000+0100` or `20240301193000` (UTC). */
    fun date(value: String?): Long? {
        val s = value?.trim() ?: return null
        if (s.length < 14 || !s.substring(0, 14).all { it.isDigit() }) return null
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                s.substring(0, 4).toInt(), s.substring(4, 6).toInt() - 1, s.substring(6, 8).toInt(),
                s.substring(8, 10).toInt(), s.substring(10, 12).toInt(), s.substring(12, 14).toInt(),
            )
        }.timeInMillis
        val offset = s.substring(14).trim()
        if (offset.length != 5 || offset[0] !in "+-" || !offset.substring(1).all { it.isDigit() }) return utc
        val minutes = offset.substring(1, 3).toInt() * 60 + offset.substring(3, 5).toInt()
        val sign = if (offset[0] == '-') -1 else 1
        return utc - sign * minutes * 60_000L
    }
}
