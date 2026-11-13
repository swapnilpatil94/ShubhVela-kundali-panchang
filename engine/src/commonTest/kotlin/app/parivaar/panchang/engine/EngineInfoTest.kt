package app.parivaar.panchang.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineInfoTest {
    @Test
    fun describeIncludesModuleName() {
        assertEquals("parivaar-engine", EngineInfo.describe())
    }
}
