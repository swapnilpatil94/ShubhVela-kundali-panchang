@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.aaj

import android.content.Context
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.feature.aaj.AajEngine
import app.parivaar.panchang.PanchangApp
import kotlinx.datetime.LocalDate

/** Builds the engine on first use (parses rule files from assets), so launch stays light. Call on the compute thread. */
class LazyAajEngine(private val context: Context) : AajEngine {
    // One engine per process: the shared EngineAccess (same ephemeris and calculators as reminders and widgets).
    private val delegate: AajEngine by lazy { (context.applicationContext as PanchangApp).engineAccess.aajEngine }

    override fun days(first: LocalDate, count: Int, place: Place, s: CalcSettings, off: (LocalDate) -> Int) =
        delegate.days(first, count, place, s, off)

    override fun calendar(date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings) =
        delegate.calendar(date, offsetSeconds, place, s)

    override fun festivals(
        date: LocalDate, place: Place, s: CalcSettings, region: String, ekadashi: Tradition, off: (LocalDate) -> Int,
    ) = delegate.festivals(date, place, s, region, ekadashi, off)

    override fun eclipse(date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings) =
        delegate.eclipse(date, offsetSeconds, place, s)

    override fun limbSpans(date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings) =
        delegate.limbSpans(date, offsetSeconds, place, s)
}
