package com.maiaxi.iptvplayer.services

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Http {
    /** Many IPTV providers reject unknown clients, but almost all allow VLC. */
    const val USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"

    /** Shared by the API clients, Media3 and Coil, so they reuse connections. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(
                if (request.header("User-Agent") == null) {
                    request.newBuilder().header("User-Agent", USER_AGENT).build()
                } else {
                    request
                }
            )
        }
        .build()

    /**
     * For streams. Panels load-balance with redirects, sometimes to a server that doesn't answer;
     * a short connect timeout lets ExoPlayer's own retry reach another one quickly.
     */
    val streamClient: OkHttpClient = client.newBuilder().connectTimeout(5, TimeUnit.SECONDS).build()
}

class HttpResult(val code: Int, val body: String)

/**
 * Streams the body of a 2xx response into [file], through a temporary file so a failed download
 * never replaces a good one. Returns false for other statuses or an empty body.
 */
suspend fun OkHttpClient.download(request: Request, file: File): Boolean = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val saved = try {
                response.use {
                    val body = it.body
                    if (!it.isSuccessful || body == null) return@use false
                    val temp = File(file.parentFile, "${file.name}.part")
                    val bytes = temp.outputStream().use { out -> body.byteStream().copyTo(out) }
                    bytes > 0 && temp.renameTo(file)
                }
            } catch (e: IOException) {
                cont.resumeWithException(e)
                return
            }
            cont.resume(saved)
        }
    })
}

/** Runs the request and reads the whole body. Cancelling the coroutine cancels the call. */
suspend fun OkHttpClient.fetch(request: Request): HttpResult = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val result = try {
                response.use { HttpResult(it.code, it.body?.string().orEmpty()) }
            } catch (e: IOException) {
                cont.resumeWithException(e)
                return
            }
            cont.resume(result)
        }
    })
}
