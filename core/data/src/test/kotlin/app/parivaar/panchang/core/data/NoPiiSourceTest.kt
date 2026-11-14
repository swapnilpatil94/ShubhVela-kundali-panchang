package app.parivaar.panchang.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoPiiSourceTest {
    private val root = File(System.getProperty("pp.root.dir"), "core/data/src/main")
    private val banned =
        listOf("android.util.Log", "println", "printStackTrace", "security.crypto", "Timber", "Crashlytics")

    private fun hit(line: String) = banned.any { it in line }

    @Test
    fun mainSourcesHaveNoLoggingOrBannedLibraries() {
        assertTrue(root.isDirectory, "source root missing")
        val scanned = root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.toList()
        assertTrue(scanned.count { it.extension == "kt" } >= 10, "too few sources scanned")
        val offenders =
            scanned.asSequence()
                .flatMap { f -> f.readLines().withIndex().filter { hit(it.value) }.map { "${f.name}:${it.index + 1}" } }
                .toList()
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun onlyAllowedFileNamesAreUsed() {
        val pattern = Regex("\"([A-Za-z_.-]*\\.(?:db|bin|ppx|tmp))\"")
        val literals =
            root.walkTopDown().filter { it.extension == "kt" }
                .flatMap { pattern.findAll(it.readText()).map { m -> m.groupValues[1] } }
                .toSet()
        val allowed = setOf("pp_family.db", "snapshot.bin", "snapshot.bin.tmp", "family-backup.ppx")
        val prefixes = listOf(".", "dek.", "kb.")
        val extra = literals.filterNot { l -> l in allowed || prefixes.any { l.startsWith(it) } }
        assertEquals(emptyList(), extra)
    }
}
