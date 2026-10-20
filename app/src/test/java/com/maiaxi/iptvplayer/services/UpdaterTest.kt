package com.maiaxi.iptvplayer.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updater.isNewer("0.1.1", "0.1.0"))
        assertTrue(Updater.isNewer("0.10.0", "0.9.9"))
        assertTrue(Updater.isNewer("1.0", "0.9.9"))
        assertFalse(Updater.isNewer("0.1.0", "0.1.0"))
        assertFalse(Updater.isNewer("0.1.0", "0.1.1"))
        assertFalse(Updater.isNewer("0.1.0", "0.1.0-debug"))
    }
}
