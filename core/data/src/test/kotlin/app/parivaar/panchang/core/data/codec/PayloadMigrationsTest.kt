package app.parivaar.panchang.core.data.codec

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PayloadMigrationsTest {
    private val renameStep: (JsonObject) -> JsonObject = { o ->
        JsonObject(o.filterKeys { it != "old" } + ("new" to (o["old"] ?: JsonPrimitive(0))))
    }

    @Test
    fun testOnlyV0ToV1StepRuns() {
        // Pinned to a v1 world (current = 1) so only the v0 -> v1 step applies; the default is now v2 (M10).
        val m = PayloadMigrations(mapOf((1 to 0) to renameStep), current = 1)
        val out = m.migrate(1, 0, JsonObject(mapOf("old" to JsonPrimitive(7))))
        assertEquals(JsonObject(mapOf("new" to JsonPrimitive(7))), out)
    }

    @Test
    fun currentVersionIsUntouchedAndDefaultHasNoV0Step() {
        val o = JsonObject(mapOf("a" to JsonPrimitive(1)))
        assertEquals(o, PayloadMigrations.DEFAULT.migrate(2, PayloadMigrations.CURRENT_VERSION, o))
        assertFailsWith<CodecException> { PayloadMigrations.DEFAULT.migrate(1, 0, o) }
    }

    @Test
    fun futureAndNegativeVersionsRejected() {
        val o = JsonObject(emptyMap())
        val future = PayloadMigrations.CURRENT_VERSION + 1
        assertFailsWith<CodecException> { PayloadMigrations.DEFAULT.migrate(1, future, o) }
        assertFailsWith<CodecException> { PayloadMigrations.DEFAULT.migrate(1, -1, o) }
    }

    @Test
    fun payloadBytesRejectGarbage() {
        // Read at the current version (M10: 2), so no migration step touches the payload.
        val v = PayloadMigrations.CURRENT_VERSION
        assertFailsWith<CodecException> { PayloadBytes.decode("{".toByteArray(), 1, v) }
        assertFailsWith<CodecException> { PayloadBytes.decode("1".toByteArray(), 1, v) }
        assertEquals(JsonObject(emptyMap()), PayloadBytes.decode("{}".toByteArray(), 1, v))
    }
}
