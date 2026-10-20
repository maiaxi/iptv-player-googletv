package com.maiaxi.iptvplayer.views

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/** Requests focus once the next frame is laid out, so lazy items are attached. False if it couldn't. */
suspend fun FocusRequester.focusAfterLayout(): Boolean {
    withFrameNanos { }
    return try {
        requestFocus()
        true
    } catch (e: IllegalStateException) {
        false
    }
}
