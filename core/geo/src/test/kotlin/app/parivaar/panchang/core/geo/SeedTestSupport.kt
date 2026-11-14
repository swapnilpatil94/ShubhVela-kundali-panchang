package app.parivaar.panchang.core.geo

import java.io.File

internal object SeedTestSupport {
    val overlayKeys = setOf("bombay", "calcutta")
    val tsv: String by lazy {
        val dir = requireNotNull(System.getProperty("pp.seed.dir")) { "pp.seed.dir not set" }
        File(dir, "places_seed.tsv").readText(Charsets.UTF_8)
    }
    val store: SeedPlaceStore by lazy { SeedPlaceStore(tsv, overlayKeys) }
}
