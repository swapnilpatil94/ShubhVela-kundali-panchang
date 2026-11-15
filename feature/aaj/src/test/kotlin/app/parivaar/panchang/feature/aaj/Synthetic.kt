package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.engine.panchang.Direction
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Shool
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Synthetic days in UTC (offset 0): hour h of day 0 is `h`, day n adds 24 n hours. Base date 2026-11-15. */
object Synthetic {
    val base = LocalDate(2026, 11, 15)
    private val epoch0 = base.toEpochDays() * 86_400L

    fun t(hours: Double): Instant = Instant.fromEpochSeconds(epoch0 + (hours * 3600).toLong())

    fun win(kind: WindowKind, from: Double, to: Double, code: String? = null, night: Boolean = false) =
        Window(kind, 1, code, night, t(from), t(to))

    fun chog(code: String, from: Double, to: Double) = win(WindowKind.CHOGHADIYA, from, to, code)

    /** Day [n] (0 = base date) with sunrise 06:00, sunset 18:00. */
    fun day(n: Int, windows: List<Window> = emptyList()): PanchangDay {
        val date = base.plus(DatePeriod(days = n))
        val o = 24.0 * n
        return PanchangDay(
            date = date, utcOffsetSeconds = 0, vaar = Vaar.of(date), sunrise = t(o + 6), sunset = t(o + 18),
            nextSunrise = t(o + 30), flags = emptySet(), backend = null, limbs = emptyMap(), windows = windows,
            unavailable = emptySet(), shool = Shool(Direction.EAST, "x"),
        )
    }
}
