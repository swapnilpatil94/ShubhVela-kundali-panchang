package app.parivaar.panchang.core.model.geo

import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlin.jvm.JvmInline
import kotlin.time.Instant

/** A searchable place. Coordinates are WGS84 degrees; [overlayKey] selects a historical tz overlay (or null). */
data class GeoPlace(
    val id: Long,
    val name: String,
    val admin1: String?,
    val countryCode: String,
    val latDeg: Double,
    val lonDeg: Double,
    val elevationM: Double?,
    val population: Long,
    val zoneId: String,
    val overlayKey: String?,
) {
    /** Never prints the name or coordinates (privacy: a place can identify a person). */
    override fun toString(): String = "GeoPlace(id=$id)"
}

/** [tier]: 0 exact key, 1 key prefix, 2 token prefixes (see M05 spec 4.2). */
data class PlaceHit(val place: GeoPlace, val matchedName: String, val tier: Int) {
    override fun toString(): String = "PlaceHit(tier=$tier)"
}

/** Everything the offset resolver needs to know about a place. */
data class TzContext(val zoneId: String, val lonDeg: Double, val overlayKey: String?) {
    override fun toString(): String = "TzContext(redacted)"
}

/** Longest zone id accepted anywhere (IANA ids are far shorter). */
const val ZONE_ID_MAX = 64

/**
 * True when the runtime time-zone database knows [zoneId]: the one check shared by stored-data decoding, the
 * offset resolver and every `TimeZone.of` caller, so an id that passes can never throw later on this runtime.
 */
fun isKnownZoneId(zoneId: String): Boolean =
    zoneId.length in 1..ZONE_ID_MAX &&
        try {
            TimeZone.of(zoneId)
            true
        } catch (_: IllegalTimeZoneException) {
            false
        }

fun GeoPlace.tzContext(): TzContext = TzContext(zoneId, lonDeg, overlayKey)

/** User supplied UTC offset in seconds. */
@JvmInline
value class ManualOffset(val seconds: Int) {
    init {
        require(seconds in MIN_SECONDS..MAX_SECONDS) { "Manual offset out of range" }
    }

    companion object {
        const val MIN_SECONDS = -43_200
        const val MAX_SECONDS = 50_400
    }
}

enum class OffsetSource { IANA, OVERLAY, LMT, MANUAL }

enum class OffsetFlag {
    DST,
    GAP_SHIFTED,
    AMBIGUOUS,
    NONEXISTENT_DATE,
    HISTORICAL_VERIFY,
    PRE_STANDARD_LMT,
    NO_OVERLAY_DATA,
}

enum class AmbiguityChoice { EARLIER, LATER }

data class ResolvedOffset(
    val offsetSeconds: Int,
    val source: OffsetSource,
    val flags: Set<OffsetFlag>,
    val overlayId: String?,
)

sealed interface CivilDayOffset {
    data class Valid(val offset: ResolvedOffset) : CivilDayOffset

    data object Nonexistent : CivilDayOffset
}

/** [alternative] is the other instant when the local time is AMBIGUOUS, else null. */
data class LocalResolution(val instant: Instant, val offset: ResolvedOffset, val alternative: Instant?)

/** Thrown for a zone id the runtime does not know. The message never carries the id. */
class UnknownZoneException : IllegalArgumentException("Unknown time zone")
