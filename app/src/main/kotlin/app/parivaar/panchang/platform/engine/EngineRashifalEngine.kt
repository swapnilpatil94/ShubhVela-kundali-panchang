package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.content.horoscope.Line
import app.parivaar.panchang.content.horoscope.Period
import app.parivaar.panchang.content.horoscope.RashifalRules
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.feature.horoscope.NatalSnapshot
import app.parivaar.panchang.feature.horoscope.PeriodWindows
import app.parivaar.panchang.feature.horoscope.RashifalEngine
import kotlin.time.Instant

/** The rashifal port over the shared engine (M11 C4 rules), under the engine lock. Blocking; nothing is logged. */
class EngineRashifalEngine(private val access: EngineAccess) : RashifalEngine {
    override fun h1(sign: Rashi, period: Period, now: Instant, variants: (String) -> Int): List<Line>? {
        val a = access.active() ?: return null
        return access.rashifal(a) { src, dayStart ->
            val date = PeriodWindows.panchangDate(now, access.today(a, now), dayStart)
            val w = PeriodWindows.window(period, date, dayStart)
            RashifalRules(src, variants).h1(sign, period, w).lines
        }
    }

    override fun h2(natal: NatalSnapshot, now: Instant, variants: (String) -> Int): List<Line>? {
        val inputs = natal.toInputs()
        val a = access.active()
        if (inputs == null || a == null) return null
        return access.rashifal(a) { src, dayStart ->
            val date = PeriodWindows.panchangDate(now, access.today(a, now), dayStart)
            RashifalRules(src, variants).h2(inputs, PeriodWindows.window(Period.DAY, date, dayStart)).lines
        }
    }
}
