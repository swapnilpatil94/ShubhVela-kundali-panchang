package app.parivaar.panchang.feature.kundli

import android.content.res.Resources
import app.parivaar.panchang.core.designsystem.chart.ChartNames
import kotlinx.datetime.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** All Kundli display names from resources (rashi, graha, nakshatra, tithi, masa, glyphs). */
class KundliNames(private val res: Resources) : ChartNames {
    private val rashis = res.getStringArray(R.array.kundli_rashi_names)
    private val grahas = res.getStringArray(R.array.kundli_graha_names)
    private val glyphs = res.getStringArray(R.array.kundli_graha_glyphs)
    private val naks = res.getStringArray(R.array.kundli_nakshatra_names)
    private val tithis = res.getStringArray(R.array.kundli_tithi_names)
    private val masas = res.getStringArray(R.array.kundli_masa_names)

    override fun rashi(index: Int): String = rashis[index]
    override fun graha(code: Int): String = grahas[code]
    override fun house(number: Int): String = res.getString(R.string.kundli_house_n, number.toString())
    override val lagna: String = res.getString(R.string.kundli_lagna)
    override val retrograde: String = res.getString(R.string.kundli_retrograde)
    override val noPlanets: String = res.getString(R.string.kundli_no_planets)

    fun glyph(code: Int): String = glyphs[code]
    fun nakshatra(n: Int): String = naks[n]
    fun masa(m: Int): String = masas[m]

    /** Tithi 1..30 as "Shukla Panchami" / "Krishna Navami". */
    fun tithi(t: Int): String {
        val paksha = res.getString(if (t <= PAKSHA) R.string.kundli_shukla else R.string.kundli_krishna)
        val i = (t - 1) % PAKSHA
        val name = if (t == AMAVASYA) res.getString(R.string.kundli_amavasya) else tithis[i]
        return if (t == PAKSHA || t == AMAVASYA) name else "$paksha $name"
    }

    private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

    fun date(d: LocalDate): String = dateFmt.format(java.time.LocalDate.of(d.year, d.month.ordinal + 1, d.day))

    private companion object {
        const val PAKSHA = 15
        const val AMAVASYA = 30
    }
}
