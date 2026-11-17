package app.parivaar.panchang.feature.bonds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M12 ships no ads: no ad module, composable or SDK is referenced from the Bonds routes. */
class NoAdsInBondsTest {
    @Test fun bondsSourcesReferenceNoAds() {
        val root = File(System.getProperty("pp.root.dir"))
        val files = File(root, "feature/bonds/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList() +
            File(root, "app/src/main/kotlin/app/parivaar/panchang/shell/BondsWiring.kt")
        assertTrue(files.size > 3)
        for (f in files) {
            val t = f.readText()
            for (banned in listOf("core.ads", "AdView", "NativeAd", "Interstitial", "Rewarded", "gms.ads")) {
                assertTrue(banned !in t, "${f.name} references $banned")
            }
        }
        val gradle = File(root, "feature/bonds/build.gradle.kts").readText()
        assertTrue(":core:ads" !in gradle)
    }
}
