package app.parivaar.panchang.core.data

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Fable review item 8: scripts/manifest_audit.sh passes on the repo and fails loudly on drift. */
class ManifestAuditTest {
    private val root = File(System.getProperty("pp.root.dir"))
    private val script = File(root, "scripts/manifest_audit.sh")

    private fun run(dir: File): Pair<Int, String> {
        val p =
            try {
                ProcessBuilder("bash", script.path, dir.path).redirectErrorStream(true).start()
            } catch (_: IOException) {
                assumeTrue(false, "bash not available")
                error("unreachable")
            }
        val out = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(60, TimeUnit.SECONDS))
        return p.exitValue() to out
    }

    /** A source-only copy of the repo's manifests with [edit] applied to the main manifest. */
    private fun crafted(edit: (String) -> String): File {
        val dir = createTempDirectory("ppaudit").toFile()
        File(root, "core/data/src/main/res/xml").copyRecursively(File(dir, "core/data/src/main/res/xml"))
        File(root, "app/src/debug/AndroidManifest.xml").copyTo(File(dir, "app/src/debug/AndroidManifest.xml"))
        val main = File(dir, "app/src/main/AndroidManifest.xml").apply { parentFile.mkdirs() }
        main.writeText(edit(File(root, "app/src/main/AndroidManifest.xml").readText()))
        return dir
    }

    @Test fun repositoryManifestsPass() {
        assumeTrue(script.isFile, "script missing")
        val (code, out) = run(root)
        assertEquals(0, code, out)
        assertTrue(out.contains("manifest_audit: OK"), out)
    }

    @Test fun driftFailsWithAClearMessage() {
        val perm = "<uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />"
        fun addPerm(p: String): (String) -> String =
            { it.replace(perm, "$perm<uses-permission android:name=\"$p\" />") }
        fun addComp(c: String): (String) -> String = { it.replace("</application>", "$c</application>") }
        val cases = mapOf<String, (String) -> String>(
            "INTERNET" to addPerm("android.permission.INTERNET"),
            "READ_CONTACTS" to addPerm("android.permission.READ_CONTACTS"),
            "intentMatchingFlags" to { it.replace("android:intentMatchingFlags=\"enforceIntentFilter\"", "") },
            "allowBackup" to { it.replace("android:allowBackup=\"true\"", "android:allowBackup=\"false\"") },
            "allow-list" to addComp("<receiver android:name=\".Leaky\" android:exported=\"true\" />"),
            "app/src/debug" to addComp("<receiver android:name=\".DebugGateReceiver\" android:exported=\"true\" />"),
            "explicit android:exported" to addComp("<service android:name=\".Implicit\" />"),
        )
        for ((needle, edit) in cases) {
            val dir = crafted(edit)
            try {
                val (code, out) = run(dir)
                assertNotEquals(0, code, "$needle: $out")
                assertTrue(out.contains("FAIL") && out.contains(needle), "$needle: $out")
            } finally {
                dir.deleteRecursively()
            }
        }
    }
}
