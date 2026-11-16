package app.parivaar.panchang.platform.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Regression for the M10 start-up crash: every rule JSON file the app opens must be staged into the APK assets. */
class RuleAssetsPackagedTest {
    private val module =
        File(System.getProperty("user.dir")).let { if (File(it, "build.gradle.kts").exists()) it else File(it, "app") }
    private val root = module.parentFile

    @Test fun everyRuleFileTheAppOpensIsStaged() {
        val staging = File(module, "build.gradle.kts").readText()
        val referenced =
            File(module, "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
                .flatMap { Regex("\"rules/([a-z_]+\\.json)\"").findAll(it.readText()).map { m -> m.groupValues[1] } }
                .toSet()
        assertTrue(referenced.isNotEmpty(), "no rule references found, the scan is broken")
        for (name in referenced) {
            assertTrue(File(root, "rules/$name").isFile, "rules/$name does not exist in the repo")
            assertTrue("\"$name\"" in staging, "rules/$name is opened at run time but is not staged in the app build")
        }
    }
}
