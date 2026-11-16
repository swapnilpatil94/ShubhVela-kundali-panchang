package app.parivaar.panchang.core.model.profile

import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.engine.CalcSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.jvm.JvmInline
import kotlin.time.Instant

/** Random 128-bit record id as 32 lowercase hex characters. The id is random and not personal data. */
@JvmInline
value class RecordId(val hex: String) {
    init {
        require(hex.length == LENGTH && hex.all { it in '0'..'9' || it in 'a'..'f' }) { "Invalid record id" }
    }

    override fun toString(): String = "RecordId($hex)"

    companion object {
        const val LENGTH = 32
    }
}

enum class Relation { SELF, SPOUSE, PARENT, CHILD, SIBLING, GRANDPARENT, GRANDCHILD, RELATIVE, FRIEND, OTHER }

/** MANUAL_OTHER profiles are never shareable (MASTER Part 8). */
enum class ProfileOrigin { OWN, MANUAL_OTHER }

/** Birth date and optional time (seconds precision). [timeKnown] is true exactly when [time] is present. */
data class BirthData(val date: LocalDate, val time: LocalTime?, val timeKnown: Boolean) {
    init {
        require(timeKnown == (time != null)) { "Birth time flag mismatch" }
        require(time == null || time.nanosecond == 0) { "Birth time must have seconds precision" }
    }

    override fun toString(): String = "BirthData(redacted)"
}

/**
 * Birth place as chosen at entry: the M05 place, the offset resolved for the birth moment (with source and flags),
 * an optional manual override and the DST-fold choice. [birthInstant] is null exactly when the time is unknown
 * (convention to be confirmed in M10).
 */
data class PlaceSnapshot(
    val place: GeoPlace,
    val offset: ResolvedOffset,
    val manualOffset: ManualOffset?,
    val ambiguity: AmbiguityChoice,
    val birthInstant: Instant?,
) {
    override fun toString(): String = "PlaceSnapshot(redacted)"
}

/** Date (and optional time, seconds precision) of passing, used only for the shraddha tithi. */
data class PassingData(val date: LocalDate, val time: LocalTime?) {
    init {
        require(time == null || time.nanosecond == 0) { "Passing time must have seconds precision" }
    }

    override fun toString(): String = "PassingData(redacted)"
}

/** Which yearly family dates this profile contributes (M10). All on by default. */
data class FamilyPrefs(val tithi: Boolean = true, val nakshatra: Boolean = true, val shraddha: Boolean = true)

data class Profile(
    val id: RecordId,
    val displayName: String,
    val relation: Relation,
    val origin: ProfileOrigin,
    val birth: BirthData,
    val place: PlaceSnapshot,
    val calc: CalcSettings,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Payload v2 (M10): null for the living and for every v1 row. */
    val passing: PassingData? = null,
    val familyPrefs: FamilyPrefs = FamilyPrefs(),
) {
    override fun toString(): String = "Profile(redacted)"
}
