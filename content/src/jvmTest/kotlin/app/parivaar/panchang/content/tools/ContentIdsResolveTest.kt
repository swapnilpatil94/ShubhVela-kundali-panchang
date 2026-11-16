package app.parivaar.panchang.content.tools

import app.parivaar.panchang.content.ContentIds
import app.parivaar.panchang.content.text.TemplateArg
import app.parivaar.panchang.content.text.TemplateRenderer
import app.parivaar.panchang.content.text.TermKind
import app.parivaar.panchang.content.text.TermResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Every REQUIRED id renders in en, hi, mr, gu; en/hi without fallback, mr/gu with it. */
class ContentIdsResolveTest {
    private val renderer = TemplateRenderer(TermResolver(ContentSource.packs.mapValues { it.value.toContentPack() })) {
        "06:00"
    }

    private fun args(id: String): Map<String, TemplateArg> = ContentIds.ARGS[id].orEmpty().associateWith { name ->
        when (name) {
            "time" -> TemplateArg.Time(Instant.fromEpochSeconds(0))
            "rashi" -> TemplateArg.Term(TermKind.RASHI, 5)
            else -> TemplateArg.Term(TermKind.GRAHA, 7)
        }
    }

    @Test fun requiredCountIs127() = assertEquals(127, ContentIds.REQUIRED.size)

    @Test fun everyRequiredIdRendersInEveryBundledLanguage() {
        for (lang in listOf("en", "hi", "mr", "gu")) {
            for (id in ContentIds.REQUIRED) {
                val r = renderer.render(id, args(id), lang).getOrElse { throw AssertionError("$lang $id", it) }
                assertTrue(r.text.isNotBlank() && '{' !in r.text, "$lang $id")
                val direct = lang == "en" || lang == "hi"
                assertEquals(!direct, r.usedFallback, "$lang $id")
            }
        }
    }
}
