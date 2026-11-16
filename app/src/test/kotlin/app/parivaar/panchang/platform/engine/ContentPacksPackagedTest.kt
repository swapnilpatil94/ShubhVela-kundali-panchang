package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.content.ContentIds
import app.parivaar.panchang.platform.content.BundledContentPacks
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mirrors [RuleAssetsPackagedTest] for M11 content packs: every pack path the app opens is staged by
 * `stageContentPacks` into the asset source set, and a staged copy (when a build made one) verifies and loads.
 */
class ContentPacksPackagedTest {
    private val module =
        File(System.getProperty("user.dir")).let { if (File(it, "build.gradle.kts").exists()) it else File(it, "app") }
    private val root = module.parentFile
    private val build = File(module, "build.gradle.kts").readText()

    @Test fun stagingTaskAndAssetSourceAreConfigured() {
        assertTrue("register<Sync>(\"stageContentPacks\")" in build, "stageContentPacks task missing")
        assertTrue("dependsOn(\":content:buildPacks\")" in build, "stageContentPacks must depend on buildPacks")
        assertTrue("\"content/bundled.pub\", \"content/*/pack.ppc\"" in build, "pack files are not staged")
        assertTrue("generated/contentAssets" in build.substringAfter("android {"), "content assets dir not a source")
        assertTrue("dependsOn(stageRuleAssets, stageContentPacks)" in build, "asset tasks do not depend on staging")
    }

    @Test fun everyBundledLanguageHasASourcePack() {
        for (lang in BundledContentPacks.LANGS) assertTrue(File(root, "content-src/$lang/pack.json").isFile, lang)
    }

    @Test fun stagedPacksVerifyAndLoadWhenPresent() {
        val out = File(module, "build/generated/contentAssets")
        if (!File(out, BundledContentPacks.PUBLIC_KEY).isFile) return
        val packs = BundledContentPacks.load { p -> File(out, p).takeIf { it.isFile }?.readBytes() }
        assertEquals(BundledContentPacks.LANGS.toSet(), packs.keys)
        assertTrue(packs.getValue("en").entries.keys.containsAll(ContentIds.REQUIRED))
        assertTrue(packs.getValue("hi").entries.keys.containsAll(ContentIds.REQUIRED))
    }

    @Test fun missingAssetsLoadNothingAndNeverThrow() {
        assertEquals(emptyMap(), BundledContentPacks.load { null })
        val junk = BundledContentPacks.load { p ->
            if (p == BundledContentPacks.PUBLIC_KEY) ByteArray(32) else ByteArray(3)
        }
        assertEquals(emptyMap(), junk)
    }
}
