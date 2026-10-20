package com.maiaxi.iptvplayer.services

import com.maiaxi.iptvplayer.models.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {
    private fun parse(text: String) = M3uParser.parse(text.trimIndent(), "src", "Uncategorized")

    @Test
    fun readsAttributesAndName() {
        val data = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="rtp1.pt" tvg-name="RTP 1 HD" tvg-logo="http://logo/rtp1.png" group-title="Portugal",RTP 1
            http://example.com/live/rtp1.ts
            """
        )
        val item = data.items.single()
        assertEquals("src:live:0", item.id)
        assertEquals("RTP 1", item.name)
        assertEquals("http://logo/rtp1.png", item.logo)
        assertEquals("rtp1.pt", item.epgId)
        assertEquals("live:Portugal", item.categoryId)
        assertEquals("http://example.com/live/rtp1.ts", item.streamUrl)
        assertEquals(listOf("Portugal"), data.categories.map { it.name })
    }

    @Test
    fun keepsCommasInsideQuotedGroupTitle() {
        val data = parse(
            """
            #EXTINF:-1 group-title="News, World",BBC World
            http://example.com/bbc.m3u8
            """
        )
        val item = data.items.single()
        assertEquals("BBC World", item.name)
        assertEquals("live:News, World", item.categoryId)
    }

    @Test
    fun fallsBackToExtgrpAndUncategorized() {
        val data = parse(
            """
            #EXTINF:-1,One
            #EXTGRP:Sports
            http://example.com/1
            #EXTINF:-1,Two
            http://example.com/2
            http://example.com/three.ts
            """
        )
        assertEquals(listOf("live:Sports", "live:Uncategorized", "live:Uncategorized"), data.items.map { it.categoryId })
        assertEquals("three.ts", data.items[2].name)
    }

    @Test
    fun readsVlcOptions() {
        val data = parse(
            """
            #EXTINF:-1,Channel
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 (X11)
            #EXTVLCOPT:http-referrer=https://site.example/
            http://example.com/a.m3u8
            #EXTINF:-1 user-agent="Custom/1.0",Other
            http://example.com/b.m3u8
            """
        )
        assertEquals("Mozilla/5.0 (X11)", data.items[0].userAgent)
        assertEquals("https://site.example/", data.items[0].referrer)
        assertEquals("Custom/1.0", data.items[1].userAgent)
        assertNull(data.items[1].referrer)
    }

    @Test
    fun readsFirstGuideUrl() {
        val data = parse(
            """
            #EXTM3U url-tvg="http://a.example/guide.xml.gz, http://b.example/guide.xml"
            #EXTINF:-1,A
            http://example.com/a
            """
        )
        assertEquals("http://a.example/guide.xml.gz", data.epgUrl)
        assertEquals("http://x.example/epg.xml", parse("#EXTM3U x-tvg-url=\"http://x.example/epg.xml\"").epgUrl)
    }

    @Test
    fun detectsKindFromPathAndExtension() {
        assertEquals(ContentKind.MOVIE, M3uParser.kindFor("http://h:8080/movie/u/p/123.mkv"))
        assertEquals(ContentKind.SERIES, M3uParser.kindFor("http://h:8080/series/u/p/456.mp4"))
        assertEquals(ContentKind.MOVIE, M3uParser.kindFor("http://cdn.example/films/Film.MP4?token=1"))
        assertEquals(ContentKind.MOVIE, M3uParser.kindFor("http://cdn.example/a.m4v"))
        assertEquals(ContentKind.LIVE, M3uParser.kindFor("http://h:8080/live/u/p/1.ts"))
        assertEquals(ContentKind.LIVE, M3uParser.kindFor("http://h:8080/u/p/1"))
        assertEquals(ContentKind.LIVE, M3uParser.kindFor("http://movie/stream.m3u8"))
    }

    @Test
    fun groupsCategoriesByKind() {
        val data = parse(
            """
            #EXTINF:-1 group-title="Action",Film
            http://h/movie/u/p/1.mp4
            #EXTINF:-1 group-title="Action",Channel
            http://h/live/u/p/2.ts
            """
        )
        assertEquals(listOf("live:Action", "movie:Action"), data.categories.map { it.id })
        assertEquals(listOf(ContentKind.LIVE, ContentKind.MOVIE), data.categories.map { it.kind })
    }
}
