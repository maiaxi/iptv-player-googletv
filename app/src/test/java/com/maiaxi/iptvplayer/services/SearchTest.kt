package com.maiaxi.iptvplayer.services

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    @Test
    fun foldsCaseAndAccents() {
        assertEquals("acao e ficcao", "Ação E Ficção".folded())
        assertEquals("rtp1 noticias", "RTP1 Notícias".folded())
        assertEquals("zurich", "Zürich".folded())
    }
}
