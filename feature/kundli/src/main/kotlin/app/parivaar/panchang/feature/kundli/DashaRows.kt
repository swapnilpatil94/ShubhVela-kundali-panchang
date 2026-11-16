package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.designsystem.chart.DashaRow
import app.parivaar.panchang.engine.jyotish.DashaPeriod
import app.parivaar.panchang.engine.jyotish.Graha
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Flattens the dasha tree into rows (rule 5). A period is current when start <= now < end, compared on the engine's
 * instants (never on rounded dates), so a boundary instant belongs to the later period. Dates show in [zone].
 */
object DashaRows {
    fun isCurrent(p: DashaPeriod<Graha>, now: Instant): Boolean = now >= p.start && now < p.end

    fun build(
        maha: List<DashaPeriod<Graha>>,
        children: Map<List<Graha>, List<DashaPeriod<Graha>>>,
        expanded: Set<List<Graha>>,
        now: Instant,
        zone: TimeZone,
    ): List<DashaRow> {
        val out = ArrayList<DashaRow>()
        fun add(level: List<DashaPeriod<Graha>>) {
            for (p in level) {
                val open = p.lords in expanded
                out += DashaRow(
                    level = p.lords.size,
                    lords = p.lords.map { it.ordinal },
                    start = p.start.toLocalDateTime(zone).date,
                    end = p.end.toLocalDateTime(zone).date,
                    current = isCurrent(p, now),
                    expandable = p.lords.size < DirectKundliEngine.DASHA_DEPTH,
                    expanded = open,
                )
                if (open) children[p.lords]?.let { add(it) }
            }
        }
        add(maha)
        return out
    }

    /** Paths auto-expanded: every proper prefix of the current chain (rule 5). */
    fun autoExpand(current: List<DashaPeriod<Graha>>): Set<List<Graha>> =
        current.filter { it.lords.size < DirectKundliEngine.DASHA_DEPTH }.map { it.lords }.toSet()
}
