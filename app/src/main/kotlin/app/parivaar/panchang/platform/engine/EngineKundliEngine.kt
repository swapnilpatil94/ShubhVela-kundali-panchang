package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.engine.jyotish.DashaPeriod
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.feature.aaj.PpPerf
import app.parivaar.panchang.feature.kundli.CalInputsLite
import app.parivaar.panchang.feature.kundli.ChartRequest
import app.parivaar.panchang.feature.kundli.FamilyDateItem
import app.parivaar.panchang.feature.kundli.KundliBundle
import app.parivaar.panchang.feature.kundli.KundliEngine
import kotlinx.datetime.LocalDate

/**
 * [app.parivaar.panchang.feature.kundli.DirectKundliEngine] under the app's single engine lock (as
 * [EngineCalendarEngine]). Only durations are logged; requests and results never are.
 */
class EngineKundliEngine(private val delegate: KundliEngine, private val lock: Any) : KundliEngine {
    override fun bundle(r: ChartRequest): KundliBundle =
        synchronized(lock) { PpPerf.time("kundli.bundle") { delegate.bundle(r) } }

    override fun dasha(r: ChartRequest, path: List<Graha>): List<DashaPeriod<Graha>> =
        synchronized(lock) { delegate.dasha(r, path) }

    override fun familyDates(
        ps: List<Profile>,
        first: LocalDate,
        last: LocalDate,
        home: CalInputsLite,
    ): List<FamilyDateItem> =
        synchronized(lock) { PpPerf.time("kundli.family") { delegate.familyDates(ps, first, last, home) } }
}
