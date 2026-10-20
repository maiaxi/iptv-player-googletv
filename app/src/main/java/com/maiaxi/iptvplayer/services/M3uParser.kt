package com.maiaxi.iptvplayer.services

import com.maiaxi.iptvplayer.models.ContentKind
import com.maiaxi.iptvplayer.models.LibraryData
import com.maiaxi.iptvplayer.models.MediaCategory
import com.maiaxi.iptvplayer.models.MediaItem

object M3uParser {
    private val attribute = Regex("""([A-Za-z0-9\-_]+)="([^"]*)"""")

    fun parse(text: String, sourceId: String, uncategorized: String): LibraryData {
        val items = mutableListOf<MediaItem>()
        val categoryNames = mutableMapOf<ContentKind, MutableList<String>>()
        val seenCategories = mutableSetOf<String>()

        var pendingInfo: ExtInf? = null
        var pendingGroup: String? = null
        var pendingUserAgent: String? = null
        var pendingReferrer: String? = null
        var epgUrl: String? = null

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U") -> {
                    // `#EXTM3U url-tvg="http://a.xml.gz,http://b.xml"` — use the first guide.
                    val attrs = parseExtInf(line).attrs
                    epgUrl = (attrs["url-tvg"] ?: attrs["x-tvg-url"])
                        ?.split(",")?.first()?.trim()?.ifEmpty { null }
                }
                line.startsWith("#EXTINF") -> pendingInfo = parseExtInf(line)
                line.startsWith("#EXTGRP:") -> pendingGroup = line.removePrefix("#EXTGRP:")
                line.startsWith("#EXTVLCOPT:") -> {
                    val option = line.removePrefix("#EXTVLCOPT:")
                    val value = option.split("=", limit = 2).last()
                    if (option.startsWith("http-user-agent")) pendingUserAgent = value
                    if (option.startsWith("http-referrer")) pendingReferrer = value
                }
                !line.startsWith("#") -> {
                    val info = pendingInfo ?: ExtInf(lastPathComponent(line), emptyMap())
                    val group = info.attrs["group-title"] ?: pendingGroup ?: uncategorized
                    val kind = kindFor(line)
                    val categoryId = "${kind.key}:$group"
                    if (seenCategories.add(categoryId)) {
                        categoryNames.getOrPut(kind) { mutableListOf() }.add(group)
                    }
                    items += MediaItem(
                        id = "$sourceId:${kind.key}:${items.size}",
                        name = info.name.ifEmpty { info.attrs["tvg-name"] ?: lastPathComponent(line) },
                        logo = info.attrs["tvg-logo"]?.ifEmpty { null },
                        categoryId = categoryId,
                        kind = kind,
                        streamUrl = line,
                        xtreamId = null,
                        rating = null,
                        userAgent = pendingUserAgent ?: info.attrs["user-agent"],
                        referrer = pendingReferrer,
                        epgId = info.attrs["tvg-id"]?.ifEmpty { null },
                    )
                    pendingInfo = null
                    pendingGroup = null
                    pendingUserAgent = null
                    pendingReferrer = null
                }
            }
        }

        val categories = ContentKind.entries.flatMap { kind ->
            categoryNames[kind].orEmpty().map { MediaCategory("${kind.key}:$it", it, kind) }
        }
        return LibraryData(items, categories, account = null, fetchedAt = System.currentTimeMillis(), epgUrl = epgUrl)
    }

    private class ExtInf(val name: String, val attrs: Map<String, String>)

    /** `#EXTINF:-1 tvg-id="x" tvg-logo="y" group-title="News, World",Channel Name` */
    private fun parseExtInf(line: String): ExtInf {
        var inQuotes = false
        var nameStart = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            if (c == ',' && !inQuotes) {
                nameStart = i + 1
                break
            }
        }
        val header = if (nameStart >= 0) line.substring(0, nameStart) else line
        val name = if (nameStart >= 0) line.substring(nameStart).trim() else ""
        val attrs = attribute.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return ExtInf(name, attrs)
    }

    fun kindFor(url: String): ContentKind {
        val path = path(url).lowercase()
        if ("/movie/" in path) return ContentKind.MOVIE
        if ("/series/" in path) return ContentKind.SERIES
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        if (ext in setOf("mp4", "mkv", "avi", "mov", "m4v")) return ContentKind.MOVIE
        return ContentKind.LIVE
    }

    private fun path(url: String): String {
        val noQuery = url.substringBefore('?').substringBefore('#')
        val scheme = noQuery.indexOf("://")
        return if (scheme < 0) noQuery else "/" + noQuery.substring(scheme + 3).substringAfter('/', "")
    }

    private fun lastPathComponent(url: String): String = path(url).trimEnd('/').substringAfterLast('/')
}
