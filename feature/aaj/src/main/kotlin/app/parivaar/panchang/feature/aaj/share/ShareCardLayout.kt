@file:Suppress("MagicNumber", "MaxLineLength") // pixel layout constants

package app.parivaar.panchang.feature.aaj.share

enum class ShareFormat(val width: Int, val height: Int) {
    SQUARE_1080(1080, 1080),
    STORY_1080x1920(1080, 1920),
}

/** Everything printed on the card, already in the user's language. [city] is null unless the user allows it. */
data class ShareCardModel(
    val brand: String,
    val date: String,
    val city: String?,
    val limbLines: List<String>,
    val verdict: String,
    val language: String,
    val footer: String,
)

enum class ShareRole { BRAND, DATE, CITY, LIMB, VERDICT, LANGUAGE, FOOTER }

/** A text slot: top-left at ([x], [y]) in pixels, at most [maxLines] lines inside [width] x [height]. */
data class ShareItem(
    val role: ShareRole,
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val textSizePx: Float,
    val maxLines: Int,
    val bold: Boolean,
)

data class ShareLayout(val width: Int, val height: Int, val items: List<ShareItem>)

/** Pure layout: which lines appear and where, per format. No personal data beyond the optional [ShareCardModel.city]. */
object ShareCardLayout {
    private const val MARGIN = 72

    fun compute(format: ShareFormat, m: ShareCardModel): ShareLayout {
        val w = format.width
        val h = format.height
        val story = format == ShareFormat.STORY_1080x1920
        val inner = w - 2 * MARGIN
        val scale = if (story) 1.0f else 0.82f
        val gap = if (story) 36 else 22
        val items = ArrayList<ShareItem>()
        var y = if (story) 160 else 72

        fun add(role: ShareRole, text: String, size: Float, lines: Int, bold: Boolean) {
            val slot = (size * 1.3f * lines).toInt()
            items += ShareItem(role, text, MARGIN, y, inner, slot, size, lines, bold)
            y += slot + gap
        }
        add(ShareRole.BRAND, m.brand, 52f * scale, 1, true)
        add(ShareRole.DATE, m.date, 60f * scale, 2, true)
        m.city?.takeIf { it.isNotBlank() }?.let { add(ShareRole.CITY, it, 44f * scale, 1, false) }
        m.limbLines.forEach { add(ShareRole.LIMB, it, 46f * scale, 1, false) }
        add(ShareRole.VERDICT, m.verdict, 52f * scale, if (story) 3 else 2, true)
        add(ShareRole.LANGUAGE, m.language, 34f * scale, 1, false)
        // The footer sits at the bottom edge, never overlapped by the lines above.
        val footerSize = 32f * scale
        val footerSlot = (footerSize * 1.3f * 2).toInt()
        val footerY = h - MARGIN - footerSlot
        items += ShareItem(ShareRole.FOOTER, m.footer, MARGIN, footerY, inner, footerSlot, footerSize, 2, false)
        return ShareLayout(w, h, items)
    }
}
