@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.planets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.preview.PpPreviews

// Previews draw CPU frames (layoutlib has no AGSL).

private fun moon(phaseDeg: Double, chiDeg: Double) =
    planetLightOf(PlanetBody.MOON, phaseDeg, chiDeg, null, eastOfSun = chiDeg > 180.0, ringTiltDeg = null)

@PpPreviews
@Composable
private fun MoonPhasesPreview() = PanchangTheme {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // New, waxing crescent, first quarter, waxing gibbous, full, last quarter (north up, no place).
        val phases = listOf(178.0 to 270.0, 120.0 to 265.0, 90.0 to 270.0, 45.0 to 275.0, 0.0 to 270.0, 90.0 to 90.0)
        for ((i, chi) in phases) {
            PlanetView(PlanetBody.MOON, moon(i, chi), 48.dp, animated = false)
        }
    }
}

@PpPreviews
@Composable
private fun SaturnPreview() = PanchangTheme {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanetView(PlanetBody.SATURN, planetLightOf(PlanetBody.SATURN, 5.5, 0.0, null, true, 20.0), 140.dp, false)
        PlanetView(PlanetBody.SATURN, planetLightOf(PlanetBody.SATURN, 6.0, 0.0, null, false, -4.0), 140.dp, false)
    }
}

@PpPreviews
@Composable
private fun JupiterPreview() = PanchangTheme {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanetView(PlanetBody.JUPITER, planetLightOf(PlanetBody.JUPITER, 11.0, 0.0, null, false, null), 112.dp, false)
        PlanetView(PlanetBody.MARS, planetLightOf(PlanetBody.MARS, 40.0, 0.0, null, false, null), 112.dp, false)
    }
}
