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
        val m = PayloadMigrations(mapOf((1 to 0) to renameStep))
        val out = m.migrate(1, 0, JsonObject(mapOf("old" to JsonPrimitive(7))))
        assertEquals(JsonObject(mapOf("new" to JsonPrimitive(7))), out)
    }

    @Test
    fun currentVersionIsUntouchedAndDefaultHasNoSteps() {
        val o = JsonObject(mapOf("a" to JsonPrimitive(1)))
        assertEquals(o, PayloadMigrations.DEFAULT.migrate(2, PayloadMigrations.CURRENT_VERSION, o))
        assertFailsWith<CodecException> { PayloadMigrations.DEFAULT.migrate(1, 0, o) }
    }

    @Test
    fun futureAndNegativeVersionsRejected() {
        val o = JsonObject(emptyMap())
        assertFailsWith<CodecException> { PayloadMigrations.DEFAULT.migrate(1, 2, o) }
        assertFailsWith<CodecException> { PayloadMigrations.DEFAULT.migrate(1, -1, o) }
    }

    @Test
    fun payloadBytesRejectGarbage() {
        assertFailsWith<CodecException> { PayloadBytes.decode("{".toByteArray(), 1, 1) }
        assertFailsWith<CodecException> { PayloadBytes.decode("1".toByteArray(), 1, 1) }
        assertEquals(JsonObject(emptyMap()), PayloadBytes.decode("{}".toByteArray(), 1, 1))
    }
}
