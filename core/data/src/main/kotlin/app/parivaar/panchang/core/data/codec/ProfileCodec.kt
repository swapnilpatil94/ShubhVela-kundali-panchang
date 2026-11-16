package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.model.profile.BirthData
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal object ProfileCodec {
    private const val TWO = 2

    private fun two(v: Int) = v.toString().padStart(TWO, '0')

    private fun birthToJson(b: BirthData): JsonObject =
        obj(
            "date" to str(b.date.toString()),
            "time" to str(b.time?.let { "${two(it.hour)}:${two(it.minute)}:${two(it.second)}" }),
            "timeKnown" to bool(b.timeKnown),
        )

    private fun birthFromJson(o: JsonElement): BirthData =
        read(o) {
            val date = LocalDate.parse(string("date"))
            check(date.year in DataLimits.BIRTH_YEAR_MIN..DataLimits.BIRTH_YEAR_MAX)
            BirthData(
                date = date,
                time = stringOrNull("time")?.let(LocalTime::parse),
                timeKnown = boolean("timeKnown"),
            )
        }

    fun toJson(p: Profile): JsonObject =
        obj(
            "id" to str(p.id.hex),
            "name" to str(p.displayName),
            "relation" to enumName(p.relation),
            "origin" to enumName(p.origin),
            "birth" to birthToJson(p.birth),
            "place" to PlaceCodec.snapshotToJson(p.place),
            "calc" to SettingsCodec.calcToJson(p.calc),
            "notes" to str(p.notes),
            "createdAt" to instant(p.createdAt),
            "updatedAt" to instant(p.updatedAt),
        )

    /** True when [p] would decode again (same bounds as stored and imported rows). */
    fun isValid(p: Profile): Boolean =
        try {
            fromJson(toJson(p))
            true
        } catch (_: CodecException) {
            false
        }

    fun fromJson(o: JsonElement): Profile =
        guarded {
            read(o) {
                val name = string("name")
                val notes = stringOrNull("notes")
                check(name.length in 1..DataLimits.NAME_MAX)
                check(notes == null || notes.length <= DataLimits.NOTES_MAX)
                Profile(
                    id = RecordId(string("id")),
                    displayName = name,
                    relation = enumValue("relation", Relation.entries),
                    origin = enumValue("origin", ProfileOrigin.entries),
                    birth = birthFromJson(objectAt("birth")),
                    place = PlaceCodec.snapshotFromJson(objectAt("place")),
                    calc = SettingsCodec.calcFromJson(objectAt("calc")),
                    notes = notes,
                    createdAt = instant("createdAt"),
                    updatedAt = instant("updatedAt"),
                )
            }
        }
}
