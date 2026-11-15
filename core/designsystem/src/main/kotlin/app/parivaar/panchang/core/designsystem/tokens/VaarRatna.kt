@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName")
package app.parivaar.panchang.core.designsystem.tokens

import kotlinx.datetime.DayOfWeek

/** Navaratna gems. The seven weekday gems tint the dock, gem chip and sky; Gomed and Lehsunia are for graha screens only. */
enum class Gem(val argb: Long) {
    MANIKYA(PpPalette.MANIKYA),
    MOTI(PpPalette.MOTI),
    MOONGA(PpPalette.MOONGA),
    PANNA(PpPalette.PANNA),
    PUKHRAJ(PpPalette.PUKHRAJ),
    HEERA(PpPalette.HEERA),
    NEELAM(PpPalette.NEELAM),
    GOMED(PpPalette.GOMED),
    LEHSUNIA(PpPalette.LEHSUNIA),
}

/**
 * The weekday lord's gem. The weekday is the vaar of the engine day (sunrise to sunrise) shown on Aaj,
 * not the civil date; the caller passes that.
 */
fun gemFor(weekday: DayOfWeek): Gem = when (weekday) {
    DayOfWeek.SUNDAY -> Gem.MANIKYA
    DayOfWeek.MONDAY -> Gem.MOTI
    DayOfWeek.TUESDAY -> Gem.MOONGA
    DayOfWeek.WEDNESDAY -> Gem.PANNA
    DayOfWeek.THURSDAY -> Gem.PUKHRAJ
    DayOfWeek.FRIDAY -> Gem.HEERA
    DayOfWeek.SATURDAY -> Gem.NEELAM
}

/** Mixes [argb] toward white by [f] (0..1) in sRGB; alpha is kept. */
fun lighten(argb: Long, f: Float = 0.25f): Long {
    val t = f.coerceIn(0f, 1f).toDouble()
    fun ch(shift: Int): Long {
        val c = ((argb shr shift) and 0xFF).toDouble()
        return Math.round(c + (255.0 - c) * t).coerceIn(0L, 255L)
    }
    return (argb and 0xFF000000L) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
