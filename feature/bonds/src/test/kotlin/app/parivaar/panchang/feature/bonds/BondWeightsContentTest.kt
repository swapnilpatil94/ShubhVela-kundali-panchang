package app.parivaar.panchang.feature.bonds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** content/bond_weights.json: friend types never weigh Bhakoot or Nadi; every label has a string; calm wording. */
class BondWeightsContentTest {
    private val text = File(System.getProperty("pp.root.dir"), "content/bond_weights.json").readText()

    @Test fun friendTypesHaveZeroBhakootAndNadiWeight() {
        for (type in listOf("BESTIE", "SIBLING")) {
            assertTrue(Regex("\"$type\"").containsMatchIn(text))
        }
        val types = text.substringAfter("\"types\"")
        for (type in listOf("BESTIE", "SIBLING")) {
            val block = types.substringAfter("\"$type\"").substringBefore("\"exclude\"",
                types.substringAfter("\"$type\""))
            val weights = types.substringAfter("\"$type\"").substringAfter("\"weights\"").substringBefore("}\n    }")
            assertTrue("\"BHAKOOT\"" !in weights && "\"NADI\"" !in weights, "$type weighs Bhakoot or Nadi")
            assertTrue(block.isNotEmpty())
        }
    }

    @Test fun everyLabelKeyHasAStringAndTheLowestBandIsGrowthBond() {
        val keys = Regex("\"labelKey\": \"([a-z._]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(5, keys.size)
        assertEquals(BondTexts.labelKeys, keys.toSet())
        assertEquals("bond.label.growth_bond", keys.last())
        assertTrue(Regex("\"version\": 1").containsMatchIn(text))
    }

    @Test fun bondStringsUseNoFearOrVerdictWords() {
        val xml = File(System.getProperty("pp.root.dir"), "feature/bonds/src/main/res/values/strings.xml").readText()
            .lowercase().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .let { x -> Regex(">([^<]*)<").findAll(x).joinToString("\n") { it.groupValues[1] } }
        for (w in listOf("danger", "bad match", "fail", "pass ", "remedy", "18 gunas", "doom", "warning")) {
            assertTrue(w !in xml, "banned wording: $w")
        }
    }
}
