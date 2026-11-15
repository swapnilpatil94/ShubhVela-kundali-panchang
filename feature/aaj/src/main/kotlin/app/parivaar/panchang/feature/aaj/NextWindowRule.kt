package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlin.time.Duration
import kotlin.time.Instant

object NextWindowRule {
    val SHOWN: Set<WindowKind> = HeaderRule.INTERVALS + setOf(
        WindowKind.ABHIJIT, WindowKind.BRAHMA_MUHURTA, WindowKind.AMRIT_KAAL,
    )

    /** The earliest shown window with `end > now`, searched over every given day. */
    fun next(now: Instant, days: List<PanchangDay>): NextWindow? {
        val w = days.flatMap { it.windows }.filter { it.kind in SHOWN && it.end > now }
            .minWithOrNull(compareBy<Window> { it.start }.thenBy { it.kind.ordinal }.thenBy { it.part }) ?: return null
        val active = w.start <= now
        return NextWindow(w, if (active) Duration.ZERO else w.start - now, active)
    }
}
