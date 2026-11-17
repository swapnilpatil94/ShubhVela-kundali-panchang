package app.parivaar.panchang.platform.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M12: the Bonds engine opens rules/koota.json and content/bond_weights.json from assets; both must be staged. */
class BondAssetsPackagedTest {
    private val module =
        File(System.getProperty("user.dir")).let { if (File(it, "build.gradle.kts").exists()) it else File(it, "app") }
    private val staging = File(module, "build.gradle.kts").readText()

    @Test fun bondAssetsExistAndAreStaged() {
        assertTrue(File(module.parentFile, "rules/koota.json").isFile)
        assertTrue(File(module.parentFile, "content/bond_weights.json").isFile)
        assertTrue("\"koota.json\"" in staging)
        assertTrue("include(\"bond_weights.json\")" in staging && "into(\"content\")" in staging)
        val src = File(module, "src/main/kotlin/app/parivaar/panchang/platform/engine/EngineBondEngine.kt").readText()
        assertTrue("\"content/bond_weights.json\"" in src && "\"rules/koota.json\"" in src)
    }

    @Test fun stagedOutputHoldsBondWeightsWhenPresent() {
        val out = File(module, "build/generated/ruleAssets")
        if (!out.isDirectory) return
        assertTrue(File(out, "content/bond_weights.json").isFile, "staged assets lack content/bond_weights.json")
    }
}
