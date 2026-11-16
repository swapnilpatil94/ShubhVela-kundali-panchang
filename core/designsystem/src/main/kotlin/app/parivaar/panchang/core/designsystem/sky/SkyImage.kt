@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName")

package app.parivaar.panchang.core.designsystem.sky

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a sky image comes from. [Placeholder] until the 9 graded WebPs ship; [Drawable] is the WebP slot (res or asset pack). */
sealed interface SkySource {
    data class Placeholder(val gem: Gem) : SkySource
    data class Drawable(@DrawableRes val id: Int) : SkySource
}

/**
 * The image layer of the header. Decoded at display size on [Dispatchers.Default], centre-cropped, then the text-safe
 * luminance cap is applied on the CPU (D3) for every source on every API level. Decorative: no semantics.
 */
@Composable
fun SkyImage(source: SkySource, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.background(Color(PpPalette.INK0))) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val bitmap by produceState<ImageBitmap?>(null, source, wPx, hPx, density) {
            value = if (wPx <= 0 || hPx <= 0) null else withContext(Dispatchers.Default) { decodeSky(context, source, wPx, hPx, density) }
        }
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

private fun decodeSky(context: android.content.Context, source: SkySource, wPx: Int, hPx: Int, density: Float): ImageBitmap {
    // Placeholders are rendered at display size (nebula at half scale inside, stars at full resolution) and cached per (gem, size, density).
    val (w, h, px) = when (source) {
        is SkySource.Placeholder -> {
            val key = Triple(source.gem, wPx to hPx, density)
            val cached = placeholderCache.get(key)
            if (cached != null) return cached
            Triple(wPx, hPx, placeholderSkyPixels(source.gem, wPx, hPx))
        }
        is SkySource.Drawable -> {
            val bmp = decodeCropped(context, source.id, wPx, hPx)
            val p = IntArray(wPx * hPx)
            bmp.getPixels(p, 0, wPx, 0, 0, wPx, hPx)
            bmp.recycle()
            Triple(wPx, hPx, p)
        }
    }
    val feather = SkySafeZone.FEATHER_DP * density * w / wPx
    applyTextSafeZone(px, w, h, feather)
    val image = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    if (source is SkySource.Placeholder) {
        placeholderCache.put(Triple(source.gem, wPx to hPx, density), image)
    }
    return image
}

/** LRU of finished (capped) placeholder skies, bounded by bytes (ARGB_8888): about two 1080 x 990 headers, whatever the size. */
internal const val PLACEHOLDER_CACHE_BYTES = 9L * 1024 * 1024
private val placeholderCache = ByteLru<Triple<Gem, Pair<Int, Int>, Float>, ImageBitmap>(PLACEHOLDER_CACHE_BYTES) { it.width.toLong() * it.height * 4 }

private fun decodeCropped(context: android.content.Context, id: Int, wPx: Int, hPx: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(context.resources, id, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= wPx && bounds.outHeight / (sample * 2) >= hPx) sample *= 2
    val src = BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inSampleSize = sample })
    val out = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
    val scale = maxOf(wPx.toFloat() / src.width, hPx.toFloat() / src.height)
    val cw = (wPx / scale).toInt().coerceAtMost(src.width)
    val ch = (hPx / scale).toInt().coerceAtMost(src.height)
    val left = (src.width - cw) / 2
    Canvas(out).drawBitmap(src, Rect(left, 0, left + cw, ch), Rect(0, 0, wPx, hPx), null)
    src.recycle()
    return out
}
