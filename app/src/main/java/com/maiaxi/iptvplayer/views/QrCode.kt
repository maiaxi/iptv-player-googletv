package com.maiaxi.iptvplayer.views

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** A QR code for [text], drawn black on white with a one-module quiet zone. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    // Scaled up from one pixel per module, so keep the edges sharp.
    Image(bitmap, contentDescription = null, filterQuality = FilterQuality.None, modifier = modifier)
}
