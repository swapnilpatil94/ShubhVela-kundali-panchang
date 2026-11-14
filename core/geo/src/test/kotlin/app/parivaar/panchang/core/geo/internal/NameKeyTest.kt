package app.parivaar.panchang.core.geo.internal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NameKeyTest {
    @Test fun matchesPythonFixtureForEveryRow() {
        val dir = requireNotNull(System.getProperty("pp.golden.dir")) { "pp.golden.dir not set" }
        val rows = Json.parseToJsonElement(File(dir, "geo_namekey.json").readText())
            .jsonObject.getValue("rows").jsonArray
        assertTrue(rows.size >= 40)
        for (row in rows) {
            val input = row.jsonObject.getValue("in").jsonPrimitive.content
            val expected = row.jsonObject.getValue("key").jsonPrimitive.content
            assertEquals(expected, NameKey.of(input), "row ${rows.indexOf(row)}")
        }
    }

    @Test fun basics() {
        assertEquals("sao paulo", NameKey.of("  São   Paulo!! "))
        assertEquals("", NameKey.of("---"))
        assertEquals(listOf("a", "b"), NameKey.tokens("a b"))
        assertTrue(NameKey.tooShort(NameKey.of("a")))
        assertTrue(NameKey.tooShort(NameKey.of(" a ")))
        assertEquals(false, NameKey.tooShort(NameKey.of("ab")))
    }
}
