package com.maiaxi.iptvplayer.services

import android.content.Context
import androidx.annotation.StringRes
import com.maiaxi.iptvplayer.R
import java.io.IOException

/** An error with a message the UI can localize. */
class AppError(@param:StringRes val res: Int, vararg val args: Any) : Exception()

fun Throwable.userMessage(context: Context): String = when (this) {
    is AppError -> context.getString(res, *args)
    // OkHttp's messages ("Failed to connect to /10.0.2.2:8765") are English and technical.
    is IOException -> context.getString(R.string.error_network)
    else -> localizedMessage ?: toString()
}
