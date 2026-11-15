@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.planets

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Bodies the renderer draws (UI type; no engine type in the design system). */
enum class PlanetBody { SUN, MOON, MERCURY, VENUS, MARS, JUPITER, SATURN }

/**
 * Lighting of one drawn body (docs/specs/M07R.md section 6, "Planets").
 *
 * [sunX], [sunY], [sunZ]: unit vector toward the Sun in view space (+x right, +y up = zenith side of the disc,
 * +z toward the viewer). [ringTiltDeg]: Saturn's ring tilt B (Earth's latitude over the ring plane), null for
 * every other body.
 */
data class PlanetLight(val sunX: Float, val sunY: Float, val sunZ: Float, val ringTiltDeg: Float?) {
    companion object {
        /** Fully lit from the viewer's side (previews, missing data). */
        val FULL = PlanetLight(0f, 0f, 1f, null)
    }
}

/**
 * Builds [PlanetLight] from values the feature layer already has from the engine's `SkyGeometry.light(...)`
 * (`BodyLight`): no engine call here.
 *
 * - Moon: theta = chi - q (bright-limb position angle minus parallactic angle; q = 0 when there is no place, i.e.
 *   north up), sun = (-sin i sin theta, sin i cos theta, cos i). theta = 0 lights the top, 90 the left (east).
 * - Mercury..Saturn: sun = (+-sin i, 0, cos i): east of the Sun (evening sky) the Sun is to the west, so on the
 *   right of a north-up disc: +sin i.
 * - Sun: emissive, (0, 0, 1).
 * - Saturn: [ringTiltDeg] = B; ignored for other bodies.
 */
fun planetLightOf(
    body: PlanetBody,
    phaseAngleDeg: Double,
    brightLimbDeg: Double,
    parallacticDeg: Double?,
    eastOfSun: Boolean,
    ringTiltDeg: Double?,
): PlanetLight {
    val i = phaseAngleDeg * DEG
    return when (body) {
        PlanetBody.SUN -> PlanetLight.FULL
        PlanetBody.MOON -> {
            val theta = (brightLimbDeg - (parallacticDeg ?: 0.0)) * DEG
            PlanetLight((-sin(i) * sin(theta)).toFloat(), (sin(i) * cos(theta)).toFloat(), cos(i).toFloat(), null)
        }
        else -> PlanetLight(
            (if (eastOfSun) sin(i) else -sin(i)).toFloat(),
            0f,
            cos(i).toFloat(),
            if (body == PlanetBody.SATURN) ringTiltDeg?.toFloat() else null,
        )
    }
}

/** Lighting model of the shader and of the CPU reference. */
enum class LightModel(val code: Float) { LAMBERT(0f), LOMMEL_SEELIGER(1f), EMISSIVE(2f) }

/**
 * Per-body look until the owner textures arrive (procedural surface). Colours are ARGB; [soft] is the terminator
 * softness (cosmetic, spec values), [limb] a linear limb-darkening coefficient, [bands] the latitude band frequency
 * of the gas giants (0 = none), [spots] the noise contrast, [spinPxPerSec] the spin in texture pixels per second
 * of a 2048 px wide equirectangular map (the Moon keeps one face toward the Earth: 0), [earthshine] 1 adds the
 * faint earthshine on the Moon's night side.
 */
data class PlanetStyle(
    val model: LightModel,
    val soft: Float,
    val limb: Float,
    val colA: Long,
    val colB: Long,
    val colC: Long,
    val bands: Float,
    val spots: Float,
    val spinPxPerSec: Float,
    val earthshine: Float = 0f,
)

fun planetStyle(body: PlanetBody): PlanetStyle = when (body) {
    PlanetBody.SUN -> PlanetStyle(LightModel.EMISSIVE, 0f, 0f, 0xFFE0661F, 0xFFF59A2E, 0xFFFFD27A, 0f, 0.8f, 3f)
    PlanetBody.MOON -> PlanetStyle(
        LightModel.LOMMEL_SEELIGER, 0.02f, 0f,
        0xFF5E5E62, 0xFF9C9A96, 0xFFC9C6C0,
        0f, 1f, 0f, 1f,
    )
    PlanetBody.MERCURY -> PlanetStyle(
        LightModel.LOMMEL_SEELIGER, 0.02f, 0f,
        0xFF6B6560, 0xFF9A928A, 0xFFBDB5AC,
        0f, 1f, 2f,
    )
    PlanetBody.VENUS -> PlanetStyle(
        LightModel.LAMBERT, 0.06f, 0.25f,
        0xFFC9A66B, 0xFFE3C99A, 0xFFF3E4C4,
        2.5f, 0.35f, 2f,
    )
    PlanetBody.MARS -> PlanetStyle(LightModel.LAMBERT, 0.06f, 0.1f, 0xFF7A3418, 0xFFB8542A, 0xFFD98A5A, 0f, 1f, 5f)
    PlanetBody.JUPITER -> PlanetStyle(
        LightModel.LAMBERT, 0.15f, 0.2f,
        0xFF9A6B45, 0xFFD3B48C, 0xFFF1E6D2,
        14f, 0.6f, 9f,
    )
    PlanetBody.SATURN -> PlanetStyle(LightModel.LAMBERT, 0.15f, 0.2f, 0xFFB08A55, 0xFFD9BE8A, 0xFFEFE0BC, 10f, 0.3f, 8f)
}

/** Disc radius in px inside a square of [sizePx]: Saturn leaves room for the A ring (outer edge 2.269 radii). */
fun planetRadiusPx(body: PlanetBody, sizePx: Float): Float =
    if (body == PlanetBody.SATURN) sizePx / (2f * SATURN_FRAME_RADII) else sizePx / 2f

/** Spin in radians per second for [body] ([PlanetStyle.spinPxPerSec] on a 2048 px map). */
fun spinRadPerSec(body: PlanetBody): Float = (2.0 * PI * planetStyle(body).spinPxPerSec / TEXTURE_WIDTH_PX).toFloat()

/** Normalises a light vector (defensive: the shader assumes a unit vector). */
internal fun PlanetLight.unitSun(): FloatArray {
    val n = sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ)
    return if (n > 1e-6f) floatArrayOf(sunX / n, sunY / n, sunZ / n) else floatArrayOf(0f, 0f, 1f)
}

/**
 * The one-animated-planet-per-screen budget. The first view that asks gets it; others draw a static frame
 * until the holder releases it. Not thread-safe (main thread only).
 */
class PlanetAnimationBudget {
    private var holder: Any? = null

    /** True if [token] holds (or now takes) the budget. */
    fun tryAcquire(token: Any): Boolean {
        if (holder == null) holder = token
        return holder === token
    }

    fun release(token: Any) {
        if (holder === token) holder = null
    }
}

/** Inputs of the pause rules. */
data class AnimationGate(
    val requested: Boolean,
    val budgetGranted: Boolean,
    val reducedMotion: Boolean,
    val batterySaver: Boolean,
    val resumed: Boolean,
    val onScreen: Boolean,
    val shaderAvailable: Boolean,
) {
    /** Spin only when every rule allows it; otherwise the static frame is drawn. */
    val animate: Boolean
        get() = requested && budgetGranted && !reducedMotion && !batterySaver && resumed && onScreen && shaderAvailable
}

/** Caps redraws at about 30 fps: a frame is taken only if at least 33 ms passed since the last taken one. */
class FrameGate(private val minIntervalNanos: Long = MIN_FRAME_NANOS) {
    private var last = Long.MIN_VALUE

    fun take(frameNanos: Long): Boolean {
        if (last != Long.MIN_VALUE && frameNanos - last < minIntervalNanos) return false
        last = frameNanos
        return true
    }

    companion object {
        const val MIN_FRAME_NANOS = 33_000_000L
    }
}

private const val DEG = PI / 180.0
private const val TEXTURE_WIDTH_PX = 2048.0
internal const val SATURN_FRAME_RADII = 2.3f
