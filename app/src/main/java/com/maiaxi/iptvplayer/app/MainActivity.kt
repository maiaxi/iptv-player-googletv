package com.maiaxi.iptvplayer.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.maiaxi.iptvplayer.services.Http

class MainActivity : ComponentActivity() {
    /**
     * Set by the player, so remote keys reach it before Compose uses them to move focus.
     * Returns true when the key was handled.
     */
    var keyHandler: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
                .build()
        }
        setContent { AppRoot() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyHandler?.invoke(event) == true || super.dispatchKeyEvent(event)
}
