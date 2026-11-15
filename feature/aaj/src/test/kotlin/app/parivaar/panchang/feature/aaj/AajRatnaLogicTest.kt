package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.designsystem.components.OrbTone
import app.parivaar.panchang.core.designsystem.planets.PlanetBody
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.Synthetic.day
import app.parivaar.panchang.feature.aaj.Synthetic.t
import app.parivaar.panchang.feature.aaj.Synthetic.win
import app.parivaar.panchang.feature.aaj.ui.AbhiState
import app.parivaar.panchang.feature.aaj.ui.ArcGeometry
import app.parivaar.panchang.feature.aaj.ui.PathSeg
import app.parivaar.panchang.feature.aaj.ui.abhiStateOf
import app.parivaar.panchang.feature.aaj.ui.toneOfChoghadiya
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AajRatnaLogicTest {
    private val days = listOf(
        day(
            0,
            listOf(
                win(WindowKind.HORA, 6.0, 7.0, "MARS"),
                win(WindowKind.HORA, 7.0, 8.0, "RAHU"),
                win(WindowKind.HORA, 8.0, 9.0, "MOON"),
            ),
        ),
    )

    @Test fun `hora lord maps to its planet and half-open boundaries`() {
        assertEquals(PlanetBody.MARS, horaBodyAt(days, t(6.0)))
        assertNull(horaBodyAt(days, t(7.5))) // Rahu draws no planet
        assertEquals(PlanetBody.MOON, horaBodyAt(days, t(8.0)))
        assertNull(horaBodyAt(days, t(9.0)))
    }

    @Test fun `abhi state follows the header rule output`() {
        assertEquals(AbhiState.GOOD, abhiStateOf(Header.GoodUntil(t(9.0))))
        assertEquals(AbhiState.WAIT, abhiStateOf(Header.WaitUntil(WindowKind.RAHU_KAAL, t(9.0), null)))
        assertEquals(AbhiState.NEUTRAL, abhiStateOf(Header.Unavailable))
        assertEquals(AbhiState.NEUTRAL, abhiStateOf(Header.TomorrowFrom(null)))
    }

    @Test fun `choghadiya tones`() {
        assertEquals(OrbTone.GOOD, toneOfChoghadiya("AMRIT"))
        assertEquals(OrbTone.NEUTRAL, toneOfChoghadiya("CHAR"))
        assertEquals(OrbTone.AVOID, toneOfChoghadiya("KAAL"))
    }

    @Test fun `arc hit test picks the part under the finger`() {
        val g = ArcGeometry(400f, 216f, 1f)
        val segs = listOf(PathSeg(0f, 0.5f, OrbTone.GOOD, "a"), PathSeg(0.5f, 1f, OrbTone.AVOID, "b"))
        // Upper-left of the arc is early in the day; upper-right is late.
        assertEquals(0, g.segmentAt(Offset(g.cx - g.r * 0.9f, g.cy - g.r * 0.4f), segs))
        assertEquals(1, g.segmentAt(Offset(g.cx + g.r * 0.9f, g.cy - g.r * 0.4f), segs))
        assertEquals(-1, g.segmentAt(Offset(g.cx, g.cy - g.r * 0.2f), segs)) // inside the dome, off the band
    }
}
