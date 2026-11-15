package app.parivaar.panchang.feature.calendar.ics

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcsSharePurgeTest {
    @Test fun `purge removes only older ics files`() {
        val cache = kotlin.io.path.createTempDirectory().toFile()
        val dir = File(cache, "share").apply { mkdirs() }
        val old = File(dir, "old.ics").apply { writeText("x") }
        val current = File(dir, "new.ics").apply { writeText("x") }
        val image = File(dir, "card.png").apply { writeText("x") }
        IcsShare(cache, { error("unused") }, "t").purge(keep = current)
        assertFalse(old.exists())
        assertTrue(current.exists() && image.exists())
        cache.deleteRecursively()
    }
}
