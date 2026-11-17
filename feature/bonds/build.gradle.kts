plugins { id("panchang.android-feature") }

android {
    namespace = "app.parivaar.panchang.feature.bonds"
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.systemProperty("pp.root.dir", "${rootProject.projectDir}")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.kotlin.test.junit5)
}
