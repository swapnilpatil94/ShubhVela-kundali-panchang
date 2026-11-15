package app.parivaar.panchang.feature.aaj.reminders

import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.LocalEclipse
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure planner over the next 14 local days (M08 D4). All times come from the engine inputs; this only selects
 * and orders. Never schedules exact alarms, never reads settings other than the opted-in [ReminderKind]s.
 */
object ReminderPlanner {
    const val HORIZON_DAYS = 14
    const val MAX_PER_DAY = 2
    val EVE_TIME: LocalTime = LocalTime(19, 0)

    /** Festival ids whose definition lists vrat rules (including Ekadashi). */
    fun vratIds(defs: List<FestivalDef>): Set<String> = defs.filter { it.vratRules.isNotEmpty() }.map { it.id }.toSet()

    /**
     * @param days engine days covering the horizon (used for Brahma muhurta start and sunrise)
     * @param festivals occurrences for the horizon plus one day; only ids in [vratIds] become reminders
     * @param eclipses local eclipses; only those with a visible interval count
     * @param familyDates bare dates from [FamilyDateSource]
     * @param enabled opted-in categories (empty means no reminders)
     * The horizon is the 14 local dates starting at today; a reminder belongs to the date it fires on.
     */
    @Suppress("LongParameterList")
    fun plan(
        now: Instant,
        zone: TimeZone,
        days: List<PanchangDay>,
        festivals: List<FestivalOccurrence>,
        vratIds: Set<String>,
        eclipses: List<LocalEclipse>,
        familyDates: List<LocalDate>,
        enabled: Set<ReminderKind>,
    ): List<PlannedReminder> {
        if (enabled.isEmpty()) return emptyList()
        val today = now.toLocalDateTime(zone).date
        val lastTrigger = today.plus(DatePeriod(days = HORIZON_DAYS - 1))
        val out = ArrayList<PlannedReminder>()
        if (ReminderKind.GRAHAN in enabled) out += grahan(eclipses, zone)
        if (ReminderKind.VRAT_EVE in enabled) out += vrat(festivals, vratIds, zone)
        if (ReminderKind.FAMILY_DATES in enabled) {
            out += familyDates.map { eve(it, ReminderKind.FAMILY_DATES, ReminderContent.FamilyDate, zone) }
        }
        if (ReminderKind.SUNRISE_BRAHMA in enabled) out += brahma(days)

        return out
            .filter { it.triggerAt > now }
            .filter { it.triggerAt.toLocalDateTime(zone).date in today..lastTrigger }
            .sortedBy { it.triggerAt }
            // One reminder per (kind, event date): the request code is the identity.
            .distinctBy { it.requestCode }
            .groupBy { it.triggerAt.toLocalDateTime(zone).date }
            .values
            .flatMap { day -> day.sortedWith(compareBy({ priority(it.kind) }, { it.triggerAt })).take(MAX_PER_DAY) }
            .sortedBy { it.triggerAt }
    }

    private fun eve(event: LocalDate, kind: ReminderKind, content: ReminderContent, zone: TimeZone): PlannedReminder =
        PlannedReminder(
            kind, event, LocalDateTime(event.plus(DatePeriod(days = -1)), EVE_TIME).toInstant(zone), content,
        )

    private fun grahan(eclipses: List<LocalEclipse>, zone: TimeZone): List<PlannedReminder> =
        eclipses.mapNotNull { e ->
            e.visible?.let { v ->
                eve(v.start.toLocalDateTime(zone).date, ReminderKind.GRAHAN, ReminderContent.Grahan(v), zone)
            }
        }

    private fun vrat(
        festivals: List<FestivalOccurrence>,
        vratIds: Set<String>,
        zone: TimeZone,
    ): List<PlannedReminder> =
        festivals.filter { it.id in vratIds }.sortedWith(compareBy({ it.date }, { it.id })).map {
            eve(it.date, ReminderKind.VRAT_EVE, ReminderContent.VratEve(it.id, it.parana), zone)
        }

    private fun brahma(days: List<PanchangDay>): List<PlannedReminder> =
        days.mapNotNull { d ->
            d.windows.firstOrNull { it.kind == WindowKind.BRAHMA_MUHURTA }?.let {
                PlannedReminder(ReminderKind.SUNRISE_BRAHMA, d.date, it.start, ReminderContent.Brahma(d.sunrise))
            }
        }

    private val PRIORITY =
        listOf(ReminderKind.GRAHAN, ReminderKind.VRAT_EVE, ReminderKind.FAMILY_DATES, ReminderKind.SUNRISE_BRAHMA)

    /** Lower is more important: GRAHAN > VRAT_EVE > FAMILY_DATES > SUNRISE_BRAHMA. */
    internal fun priority(k: ReminderKind): Int = PRIORITY.indexOf(k)
}
