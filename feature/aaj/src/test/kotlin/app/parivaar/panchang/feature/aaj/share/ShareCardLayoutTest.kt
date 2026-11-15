@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareCardLayoutTest {
    private fun model(city: String?) = ShareCardModel(
        "Brand", "Sunday, 15 November 2026", city,
        listOf("Tithi: Shukla Saptami until 4:20 AM (Nov 17)", "Nakshatra: Shravana", "Yoga: Siddha", "Karana: Taitila"),
        "Good for new work until 2:40 PM", "Language: English", "Traditional interpretation, not a scientific prediction.",
    )

    @Test fun `sizes follow the format`() {
        assertEquals(1080 to 1080, ShareCardLayout.compute(ShareFormat.SQUARE_1080, model(null)).let { it.width to it.height })
        assertEquals(1080 to 1920, ShareCardLayout.compute(ShareFormat.STORY_1080x1920, model(null)).let { it.width to it.height })
    }

    @Test fun `city is absent unless provided`() {
        assertFalse(ShareCardLayout.compute(ShareFormat.SQUARE_1080, model(null)).items.any { it.role == ShareRole.CITY })
        val with = ShareCardLayout.compute(ShareFormat.SQUARE_1080, model("Pune"))
        assertEquals(listOf("Pune"), with.items.filter { it.role == ShareRole.CITY }.map { it.text })
        assertFalse(ShareCardLayout.compute(ShareFormat.SQUARE_1080, model("  ")).items.any { it.role == ShareRole.CITY })
    }

    @Test fun `the footer and every limb line are present`() {
        for (f in ShareFormat.entries) {
            val items = ShareCardLayout.compute(f, model("Pune")).items
            assertEquals(1, items.count { it.role == ShareRole.FOOTER })
            assertEquals(4, items.count { it.role == ShareRole.LIMB })
        }
    }

    @Test fun `items stay inside the card and never overlap`() {
        for (f in ShareFormat.entries) {
            val l = ShareCardLayout.compute(f, model("Pune"))
            val sorted = l.items.sortedBy { it.y }
            sorted.forEach {
                assertTrue(it.x >= 0 && it.x + it.width <= l.width, "x ${it.role}")
                assertTrue(it.y >= 0 && it.y + it.height <= l.height, "y ${it.role} $f")
            }
            sorted.zipWithNext().forEach { (a, b) -> assertTrue(a.y + a.height <= b.y, "${a.role} overlaps ${b.role} in $f") }
        }
    }
}
