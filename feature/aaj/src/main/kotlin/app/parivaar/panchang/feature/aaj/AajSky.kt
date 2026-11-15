@file:Suppress("MatchingDeclarationName")

package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.designsystem.planets.PlanetBody
import app.parivaar.panchang.core.designsystem.planets.PlanetLight
import app.parivaar.panchang.core.designsystem.planets.planetLightOf
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.WindowKind
import kotlin.time.Instant

/** The planet of the current hora with its lighting for the sky header. Computed off the main thread by [AajModel]. */
data class HoraSky(val body: PlanetBody, val light: PlanetLight)

/** Hora lord of the hora running at [now] as a drawable body; Rahu and Ketu (and no hora) give null. */
fun horaBodyAt(days: List<PanchangDay>, now: Instant): PlanetBody? {
    val code = days.asSequence().flatMap { it.windows.asSequence() }
        .firstOrNull { it.kind == WindowKind.HORA && it.start <= now && now < it.end }?.code
    return when (code) {
        "SUN" -> PlanetBody.SUN
        "MOON" -> PlanetBody.MOON
        "MARS" -> PlanetBody.MARS
        "MERCURY" -> PlanetBody.MERCURY
        "JUPITER" -> PlanetBody.JUPITER
        "VENUS" -> PlanetBody.VENUS
        "SATURN" -> PlanetBody.SATURN
        else -> null
    }
}

private fun PlanetBody.engineBody(): Body? = when (this) {
    PlanetBody.SUN -> null
    PlanetBody.MOON -> Body.MOON
    PlanetBody.MERCURY -> Body.MERCURY
    PlanetBody.VENUS -> Body.VENUS
    PlanetBody.MARS -> Body.MARS
    PlanetBody.JUPITER -> Body.JUPITER
    PlanetBody.SATURN -> Body.SATURN
}

/** Lighting of [body] at [at]: the engine geometry ([AajEngine.skyLight]) turned into the renderer's [PlanetLight]. */
fun horaSkyOf(engine: AajEngine, body: PlanetBody, at: Instant, place: Place): HoraSky? {
    val eb = body.engineBody() ?: return HoraSky(body, PlanetLight.FULL)
    val l = engine.skyLight(eb, at, place)
    val light = l?.let {
        planetLightOf(body, it.phaseAngleDeg, it.brightLimbDeg, it.parallacticDeg, it.eastOfSun, it.ringTiltDeg)
    }
    return light?.let { HoraSky(body, it) }
}
