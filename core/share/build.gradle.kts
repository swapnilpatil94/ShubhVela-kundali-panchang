plugins { id("panchang.android-library") }

android {
    namespace = "app.parivaar.panchang.core.share"
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.systemProperty("pp.root.dir", "${rootProject.projectDir}")
        }
    }
}

dependencies {
    // BondTraits (core:model) and vashyaChoices (engine, via core:model's api) for the Bond Code codec (M12).
    api(project(":core:model"))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.serialization.json)
}
