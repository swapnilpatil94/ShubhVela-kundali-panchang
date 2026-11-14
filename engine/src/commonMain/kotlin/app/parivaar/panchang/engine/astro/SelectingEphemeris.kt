package app.parivaar.panchang.engine.astro

/** Which backend serves an instant (docs/specs/M01.md section 3.7). */
enum class BackendId { PRECISION_DE440, BASE_ASTRONOMY_ENGINE }

/**
 * Picks the backend per instant: [precision] wherever it is present and the TT Julian Day lies in its
 * `validRange` (inclusive), otherwise [base]; outside both, [EphemerisRangeException].
 *
 * [precision] is null when the DE440 excerpt is not installed. Both arguments are plain [Ephemeris] values, so
 * this class (commonMain) never refers to the Astronomy Engine artifact. Not thread-safe, like its backends.
 */
class SelectingEphemeris(private val precision: Ephemeris?, private val base: Ephemeris) : Ephemeris {
    /** The base backend's range (the wider one in production: 1700 to 2200). */
    override val validRange: ClosedRange<JulianDay> get() = base.validRange

    /** The backend that serves [tt]. Throws [EphemerisRangeException] when neither does (NaN included). */
    fun backendFor(tt: JulianDay): BackendId = when {
        precision != null && tt in precision.validRange -> BackendId.PRECISION_DE440
        tt in base.validRange -> BackendId.BASE_ASTRONOMY_ENGINE
        else -> throw EphemerisRangeException(tt)
    }

    override fun geoApparent(body: Body, tt: JulianDay): EclipticPos = backend(tt).geoApparent(body, tt)

    /** As [geoApparent] with `speedDegPerDay` = 0.0, from the same backend (used by rise/set). */
    internal fun geoApparentNoSpeed(body: Body, tt: JulianDay): EclipticPos = backend(tt).positionOnly(body, tt)

    private fun backend(tt: JulianDay): Ephemeris = when (backendFor(tt)) {
        BackendId.PRECISION_DE440 -> checkNotNull(precision)
        BackendId.BASE_ASTRONOMY_ENGINE -> base
    }
}

/**
 * [Ephemeris.geoApparent] with `speedDegPerDay` = 0.0. The engine's own backends answer with one evaluation
 * instead of the three of the central difference (rise/set needs positions only); any other implementation
 * is asked for the full position.
 */
internal fun Ephemeris.positionOnly(body: Body, tt: JulianDay): EclipticPos = when (this) {
    is De440Ephemeris -> geoApparentNoSpeed(body, tt)
    is SelectingEphemeris -> geoApparentNoSpeed(body, tt)
    else -> platformPositionOnly(this, body, tt) ?: geoApparent(body, tt).copy(speedDegPerDay = 0.0)
}

/**
 * Position-only path of a backend that lives outside commonMain (the Astronomy Engine one, `src/jvmShared`),
 * or null when [eph] is not such a backend. A target without platform backends returns null.
 */
internal expect fun platformPositionOnly(eph: Ephemeris, body: Body, tt: JulianDay): EclipticPos?
