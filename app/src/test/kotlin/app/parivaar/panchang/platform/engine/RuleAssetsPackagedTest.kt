package app.parivaar.panchang.platform.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression for the M10 start-up crash: every rule JSON file or directory any module opens from assets at run time
 * must be staged into the APK assets (`app/build/generated/ruleAssets`, task `stageRuleAssets`).
 */
class RuleAssetsPackagedTest {
    private val module =
        File(System.getProperty("user.dir")).let { if (File(it, "build.gradle.kts").exists()) it else File(it, "app") }
    private val root = module.parentFile
    private val staging = File(module, "build.gradle.kts").readText()

    /** Main Kotlin sources of the app and of every core/feature module (features may read assets via a Context). */
    private fun sources(): Sequence<File> {
        val modules = listOf(module) + listOf("core", "feature").flatMap { g ->
            File(root, g).listFiles { f -> f.isDirectory }.orEmpty().toList()
        }
        return modules.asSequence().map { File(it, "src/main/kotlin") }.filter { it.isDirectory }
            .flatMap { it.walkTopDown() }.filter { it.extension == "kt" }
    }

    private fun scan(pattern: Regex): Set<String> =
        sources().flatMap { f -> pattern.findAll(f.readText()).map { it.groupValues[1] } }.toSet()

    @Test fun everyRuleFileAnyModuleOpensIsStaged() {
        val referenced = scan(Regex("\"rules/([a-z_]+\\.json)\""))
        assertTrue(referenced.isNotEmpty(), "no rule references found, the scan is broken")
        for (name in referenced) {
            assertTrue(File(root, "rules/$name").isFile, "rules/$name does not exist in the repo")
            assertTrue("\"$name\"" in staging, "rules/$name is opened at run time but is not staged in the app build")
        }
    }

    @Test fun everyListedRuleDirectoryIsStaged() {
        val dirs = scan(Regex("assets\\.list\\(\"rules/([a-z_]+)\"\\)"))
        assertTrue("festivals" in dirs, "the festivals directory listing was not found, the scan is broken")
        for (dir in dirs) {
            val files = File(root, "rules/$dir").listFiles { f -> f.extension == "json" }.orEmpty()
            assertTrue(files.isNotEmpty(), "rules/$dir has no JSON files")
            assertTrue("\"$dir/*.json\"" in staging, "rules/$dir is listed at run time but is not staged")
        }
    }

    /** When a build has already staged the assets, the staged copy must hold every referenced file. */
    @Test fun stagedOutputHoldsTheReferencedFilesWhenPresent() {
        val out = File(module, "build/generated/ruleAssets/rules")
        if (!out.isDirectory) return
        for (name in scan(Regex("\"rules/([a-z_]+\\.json)\""))) {
            assertTrue(File(out, name).isFile, "staged assets lack rules/$name")
        }
        for (dir in scan(Regex("assets\\.list\\(\"rules/([a-z_]+)\"\\)"))) {
            val expected = File(root, "rules/$dir").listFiles { f -> f.extension == "json" }.orEmpty()
                .map { it.name }.toSet()
            val staged = File(out, dir).listFiles().orEmpty().map { it.name }.toSet()
            assertTrue(staged.containsAll(expected), "staged assets lack files of rules/$dir")
        }
    }
}
