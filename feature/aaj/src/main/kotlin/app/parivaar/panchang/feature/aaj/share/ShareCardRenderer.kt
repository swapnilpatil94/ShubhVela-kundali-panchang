package app.parivaar.panchang.feature.aaj.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import app.parivaar.panchang.core.designsystem.tokens.PpPalette

/**
 * Draws the plain share image in the brand light colours. It writes no metadata (no location, no device info):
 * a bitmap only. The caller saves it to the cache and shares it through a FileProvider.
 */
object ShareCardRenderer {
    fun render(format: ShareFormat, model: ShareCardModel): Bitmap {
        val layout = ShareCardLayout.compute(format, model)
        val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PpPalette.Light.BACKGROUND.toInt())
        layout.items.forEach { draw(canvas, it) }
        return bitmap
    }

    private fun draw(canvas: Canvas, item: ShareItem) {
        val color = when (item.role) {
            ShareRole.BRAND, ShareRole.VERDICT -> PpPalette.Light.PRIMARY_TEXT
            ShareRole.LANGUAGE, ShareRole.FOOTER -> PpPalette.Light.ON_SURFACE_VARIANT
            else -> PpPalette.Light.ON_SURFACE
        }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toInt()
            textSize = item.textSizePx
            typeface = if (item.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val text = StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, item.width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(item.maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(item.x.toFloat(), item.y.toFloat())
        text.draw(canvas)
        canvas.restore()
    }

}
