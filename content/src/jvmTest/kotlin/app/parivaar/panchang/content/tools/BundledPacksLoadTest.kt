package app.parivaar.panchang.content.tools

import app.parivaar.panchang.content.ContentIds
import app.parivaar.panchang.content.pack.PackLoader
import app.parivaar.panchang.content.pack.PackResult
import app.parivaar.panchang.content.pack.PackSource
import app.parivaar.panchang.content.pack.TrustedKeys
import app.parivaar.panchang.content.text.TermResolver
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The packs `buildPacks` writes load through [PackLoader] with the bundled public key, exactly as the app does. */
class BundledPacksLoadTest {
    @Test fun builtPacksVerifyAndResolve() {
        val out = Files.createTempDirectory("ppc").toFile()
        try {
            PackBuilder.buildAll(ContentSource.root, out)
            val dir = File(out, "content")
            val loader = PackLoader(TrustedKeys(File(dir, "bundled.pub").readBytes(), TrustedKeys.PROD))
            val packs = SourcePacks.BUNDLED.associateWith { lang ->
                val r = loader.load(File(dir, "$lang/pack.ppc").readBytes(), lang, PackSource.BUNDLED)
                assertIs<PackResult.Ok>(r, lang).pack
            }
            assertEquals(127, packs.getValue("en").entries.size)
            assertEquals(ContentPackKeys.sourceKeys("hi"), packs.getValue("hi").entries.keys)
            val resolver = TermResolver(packs)
            for (id in ContentIds.REQUIRED) {
                assertTrue(!resolver.text(id, "hi").isFallback)
                assertTrue(resolver.text(id, "gu").isFallback)
            }
            // A bundled pack is never accepted as a downloaded one (PROD is empty until the owner adds a key).
            val en = File(dir, "en/pack.ppc").readBytes()
            assertEquals(PackResult.UntrustedKey, loader.load(en, "en", PackSource.REMOTE))
        } finally {
            out.deleteRecursively()
        }
    }
}

private object ContentPackKeys {
    fun sourceKeys(lang: String): Set<String> = ContentSource.packs.getValue(lang).entries.keys
}
