package com.maiaxi.iptvplayer.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class XmltvParserTest {
    // 2024-03-01 12:00 UTC
    private val noon = 1_709_294_400_000L
    private val hour = 3_600_000L

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv>
          <channel id="RTP1.pt"><display-name>RTP 1</display-name></channel>
          <programme start="20240301110000 +0000" stop="20240301120000 +0000" channel="RTP1.pt">
            <title lang="pt">Jornal da Tarde</title><desc>News &amp; weather&nbsp;today</desc>
          </programme>
          <programme start="20240301130000 +0100" stop="20240301140000 +0100" channel="rtp1.pt">
            <title>Second</title><title lang="en">Second (en)</title>
          </programme>
          <programme start="20240301120500 +0000" stop="20240301125500 +0000" channel="RTP1.pt">
            <title>Second</title>
          </programme>
          <programme start="20240305120000 +0000" stop="20240305130000 +0000" channel="RTP1.pt">
            <title>Too late</title>
          </programme>
          <programme start="20240301120000 +0000" stop="20240301130000 +0000" channel="other">
            <title>Unwanted</title>
          </programme>
          <programme start="20240301140000" stop="20240301150000" channel="RTP1.pt"><title>No offset</title></programme>
        </tv>
    """.trimIndent()

    private fun parse(bytes: ByteArray) =
        XmltvParser.parse(bytes.inputStream(), KXmlParser(), setOf("rtp1.PT"), noon - 2 * hour, noon + 24 * hour)

    @Test
    fun keepsWantedChannelsInsideWindow() {
        val guide = parse(feed.toByteArray())
        assertEquals(setOf("rtp1.pt"), guide.keys)
        assertEquals(listOf("Jornal da Tarde", "Second", "No offset"), guide.getValue("rtp1.pt").map { it.title })
    }

    @Test
    fun readsTimesWithAndWithoutOffsets() {
        val entries = parse(feed.toByteArray()).getValue("rtp1.pt")
        assertEquals(noon - hour, entries[0].start)
        // 13:00 +0100 is 12:00 UTC.
        assertEquals(noon, entries[1].start)
        assertEquals(noon + 2 * hour, entries[2].start)
    }

    @Test
    fun keepsFirstTitleAndSkipsUnknownEntities() {
        val entries = parse(feed.toByteArray()).getValue("rtp1.pt")
        assertEquals("Second", entries[1].title)
        assertTrue(entries[0].description!!.startsWith("News & weather"))
        assertNull(entries[1].description)
    }

    @Test
    fun readsGzippedFeeds() {
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(feed.toByteArray()) } }
        assertEquals(3, parse(zipped.toByteArray()).getValue("rtp1.pt").size)
    }

    @Test
    fun keepsProgrammesReadBeforeAnError() {
        val broken = feed.substringBefore("<programme start=\"20240305") + "<programme <<<"
        assertEquals(2, parse(broken.toByteArray()).getValue("rtp1.pt").size)
    }

    @Test
    fun parsesDates() {
        assertEquals(noon, XmltvParser.date("20240301120000 +0000"))
        assertEquals(noon, XmltvParser.date("20240301070000-0500"))
        assertEquals(noon, XmltvParser.date("20240301120000"))
        assertNull(XmltvParser.date("2024-03-01"))
    }
}
