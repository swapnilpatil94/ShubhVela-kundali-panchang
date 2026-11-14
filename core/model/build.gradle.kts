plugins { id("panchang.kmp-library") }

kotlin {
    android { namespace = "app.parivaar.panchang.core.model" }
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            // CalcSettings lives in the engine (M06 D11).
            api(project(":engine"))
        }
    }
}
