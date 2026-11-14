package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.MaxError
import app.parivaar.panchang.engine.jyotish.M02Golden.ARCSEC
import app.parivaar.panchang.engine.jyotish.M02Golden.a
import app.parivaar.panchang.engine.jyotish.M02Golden.d
import app.parivaar.panchang.engine.jyotish.M02Golden.l
import app.parivaar.panchang.engine.jyotish.M02Golden.o
import app.parivaar.panchang.engine.jyotish.M02Golden.s
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** House geometry (2,520 cases) and LAST / true obliquity (500 rows), docs/specs/M02.md section 4. */
@Tag("golden")
class HousesGoldenTest {
    private companion object {
        const val GEOMETRY_LIMIT_DEG = 1e-7
        const val LAST_LIMIT_ARCSEC = 0.02
        const val EPS_LIMIT_ARCSEC = 0.005
    }

    private fun deg(err: Double, e: MaxError, at: String) = e.add(abs(Zodiac.wrap180(err)), at)

    private fun checkList(got: List<Double>, want: JsonElement, e: MaxError, at: String) {
        val w = want.a()
        assertEquals(12, w.size, "fixture list size")
        assertEquals(12, got.size, "engine list size")
        for (i in 0 until 12) deg(got[i] - w[i].d(), e, "$at[$i]")
    }

    @Test
    fun houseGeometryMatchesOracle() {
        val root = M02Golden.fixture("house_geometry.json")
        val rows = M02Golden.rows(root, "v", 2520, "count")
        val asc = MaxError()
        val mc = MaxError()
        val sripati = MaxError()
        val placidus = MaxError()
        var unavailable = 0
        var computed = 0
        for (row in rows) {
            val r = row.a()
            val a = HouseAngles(r[0].d(), r[2].d())
            val lat = r[1].d()
            val at = "theta=${r[0].d()} phi=$lat eps=${r[2].d()}"
            val sr = Houses.tropical(a, lat, HouseSystem.SRIPATI)
            assertIs<HouseResult.Cusps>(sr, "SRIPATI is always available ($at)")
            deg(sr.ascDeg - r[3].d(), asc, at)
            deg(sr.mcDeg - r[4].d(), mc, at)
            checkList(sr.cuspsDeg, r[5], sripati, "$at cusps")
            checkList(sr.startsDeg, r[6], sripati, "$at starts")
            val pl = Houses.tropical(a, lat, HouseSystem.PLACIDUS)
            if (r[7] is JsonNull) {
                val u = assertIs<HouseResult.Unavailable>(pl, "Placidus must be unavailable ($at)")
                assertEquals(HouseError.PLACIDUS_HIGH_LATITUDE, u.code, at)
                unavailable++
            } else {
                val c = assertIs<HouseResult.Cusps>(pl, "Placidus must compute ($at)")
                checkList(c.cuspsDeg, r[7], placidus, "$at placidus")
                deg(c.ascDeg - r[3].d(), asc, at)
                deg(c.mcDeg - r[4].d(), mc, at)
                computed++
            }
        }
        assertEquals(M02Golden.metaInt(root, "placidusCount"), computed, "Placidus computed count")
        assertEquals(2520 - computed, unavailable, "Placidus unavailable count")
        val all = listOf("asc" to asc, "mc" to mc, "sripati cusps+starts" to sripati, "placidus cusps" to placidus)
        M02Golden.report(
            "Houses geometry (2520 cases, limit ${GEOMETRY_LIMIT_DEG} deg)",
            all.map { (n, e) -> "$n: n=${e.count} max=${e.value} deg at ${e.where}" } +
                "placidus unavailable exactly where null: $unavailable",
        )
        M02Golden.assertNoFailures(
            "house geometry",
            all.filter { !(it.second.value <= GEOMETRY_LIMIT_DEG) }
                .map { "${it.first} ${it.second.value} at ${it.second.where}" },
        )
    }

    @Test
    fun lastAndObliquityMatchOracle() {
        val root = M02Golden.fixture("house_angles.json")
        val rows = M02Golden.rows(root, "v", 500, "count")
        val lons = root.getValue("meta").o().getValue("cities").a().associate { it.o().getValue("name").s() to it.o() }
        val last = MaxError()
        val eps = MaxError()
        for (row in rows) {
            val r = row.a()
            val city = lons.getValue(r[0].s())
            val place = Place(city.getValue("lat").d(), city.getValue("lon").d())
            val a = Houses.angles(Instant.fromEpochMilliseconds(r[1].l()), place)
            val at = "${r[0].s()} ms=${r[1].l()}"
            last.add(abs(Zodiac.wrap180(a.lastDeg - r[2].d())) * ARCSEC, at)
            eps.add(abs(a.epsTrueDeg - r[3].d()) * ARCSEC, at)
        }
        M02Golden.report(
            "Houses LAST and obliquity (500 rows)",
            listOf(
                "LAST: n=${last.count} max\"=${last.value} limit\"=$LAST_LIMIT_ARCSEC at ${last.where}",
                "epsTrue: n=${eps.count} max\"=${eps.value} limit\"=$EPS_LIMIT_ARCSEC at ${eps.where}",
            ),
        )
        M02Golden.assertNoFailures(
            "house angles",
            listOfNotNull(
                "LAST ${last.value}\" at ${last.where}".takeIf { !(last.value <= LAST_LIMIT_ARCSEC) },
                "eps ${eps.value}\" at ${eps.where}".takeIf { !(eps.value <= EPS_LIMIT_ARCSEC) },
            ),
        )
        assertEquals(500, last.count)
    }
}
