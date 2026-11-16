package app.parivaar.panchang.platform.content

import app.parivaar.panchang.content.pack.ContentPack
import app.parivaar.panchang.content.pack.PackLoader
import app.parivaar.panchang.content.pack.PackResult
import app.parivaar.panchang.content.pack.PackSource
import app.parivaar.panchang.content.pack.TrustedKeys

/**
 * Loads the bundled packs from assets (`content/<lang>/pack.ppc`, key `content/bundled.pub`, staged by
 * `stageContentPacks`). Every pack is verified before parsing; a pack that fails is skipped (its language then falls
 * back to English). [open] returns null for a missing asset. Nothing is logged.
 */
object BundledContentPacks {
    val LANGS: List<String> = listOf("en", "hi", "mr", "gu")
    const val PUBLIC_KEY = "content/bundled.pub"

    fun path(lang: String): String = "content/$lang/pack.ppc"

    fun load(open: (String) -> ByteArray?): Map<String, ContentPack> {
        val pub = open(PUBLIC_KEY) ?: return emptyMap()
        val loader = PackLoader(TrustedKeys(pub, TrustedKeys.PROD))
        return LANGS.mapNotNull { lang ->
            val bytes = open(path(lang)) ?: return@mapNotNull null
            (loader.load(bytes, lang, PackSource.BUNDLED) as? PackResult.Ok)?.let { lang to it.pack }
        }.toMap()
    }
}
