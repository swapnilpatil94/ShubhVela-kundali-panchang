@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.notify

import app.parivaar.panchang.core.model.settings.ReminderKind
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.feature.aaj.reminders.AlarmSink
import app.parivaar.panchang.feature.aaj.reminders.PlannedReminder
import app.parivaar.panchang.feature.aaj.reminders.ReminderContent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

class ReplanTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val d0 = LocalDate(2026, 11, 15)
    private fun at(d: LocalDate, h: Int) = LocalDateTime(d, LocalTime(h, 0)).toInstant(zone)
    private val ekadashi = FestivalOccurrence("ekadashi_x", LocalDate(2026, 11, 18), null, null, null, null, null, null, emptySet())

    private class FakeSink : AlarmSink {
        val log = mutableListOf<String>()
        val set = mutableListOf<Triple<Int, Instant, Duration>>()
        override fun setWindow(requestCode: Int, triggerAt: Instant, window: Duration) {
            log += "set"
            set += Triple(requestCode, triggerAt, window)
        }
        override fun cancelAll() { log += "cancel" }
    }

    private fun input(enabled: Set<ReminderKind>) =
        PlanInput(zone, emptyList(), listOf(ekadashi), setOf("ekadashi_x"), emptyList(), emptyList(), enabled)

    @Test
    fun replanCancelsAllThenSetsTwoMinuteWindows() {
        val sink = FakeSink()
        val n = Replanner({ at(d0, 8) }, { input(setOf(ReminderKind.VRAT_EVE)) }, sink).replan()
        assertEquals(1, n)
        assertEquals(listOf("cancel", "set"), sink.log)
        assertEquals(at(LocalDate(2026, 11, 17), 19), sink.set.single().second)
        assertEquals(2, sink.set.single().third.inWholeMinutes)
    }

    @Test
    fun noEnabledCategoriesCancelsEverything() {
        val sink = FakeSink()
        assertEquals(0, Replanner({ at(d0, 8) }, { input(emptySet()) }, sink).replan())
        assertEquals(listOf("cancel"), sink.log)
    }

    @Test
    fun noInputLeavesAlarmsAlone() {
        val sink = FakeSink()
        assertEquals(-1, Replanner({ at(d0, 8) }, { null }, sink).replan())
        assertTrue(sink.log.isEmpty())
    }

    @Test
    fun clockAdvanceDropsPastTrigger() {
        val sink = FakeSink()
        Replanner({ at(LocalDate(2026, 11, 17), 20) }, { input(setOf(ReminderKind.VRAT_EVE)) }, sink).replan()
        assertTrue(sink.set.isEmpty())
    }

    @Test
    fun payloadRoundTripsAndHasNoPersonalKeys() {
        val a = at(d0, 1)
        val contents =
            listOf(
                ReminderContent.VratEve("ekadashi_x", TimeRange(a, at(d0, 3))),
                ReminderContent.VratEve("ekadashi_x", null),
                ReminderContent.Grahan(TimeRange(a, at(d0, 4))),
                ReminderContent.FamilyDate,
                ReminderContent.Brahma(a),
            )
        val kinds = listOf(ReminderKind.VRAT_EVE, ReminderKind.VRAT_EVE, ReminderKind.GRAHAN, ReminderKind.FAMILY_DATES, ReminderKind.SUNRISE_BRAHMA)
        for ((k, c) in kinds.zip(contents)) {
            val p = ReminderPayload(k, zone.id, c)
            assertEquals(p, ReminderPayload.fromMap(p.toMap()))
            assertTrue(p.toMap().keys.all { it in setOf("k", "z", "i", "a", "b") })
        }
        assertNull(ReminderPayload.fromMap(mapOf("k" to "GRAHAN", "z" to "UTC")))
        assertNull(ReminderPayload.fromMap(mapOf("k" to "NOPE", "z" to "UTC")))
    }

    @Test
    fun channelsMapAndSinkExposesNoExactAlarm() {
        assertEquals("vrat_eve", Channels.idFor(ReminderKind.VRAT_EVE))
        assertEquals("family_dates", Channels.idFor(ReminderKind.FAMILY_DATES))
        assertEquals("grahan", Channels.idFor(ReminderKind.GRAHAN))
        assertEquals("daily_times", Channels.idFor(ReminderKind.SUNRISE_BRAHMA))
        assertFalse(AlarmSink::class.java.methods.any { "exact" in it.name.lowercase() })
        assertEquals(PlannedReminder.requestCodeOf(ReminderKind.GRAHAN, d0), PlannedReminder.requestCodeOf(ReminderKind.GRAHAN, d0))
    }
}
