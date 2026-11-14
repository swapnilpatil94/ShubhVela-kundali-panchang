plugins { id("panchang.android-library") }

android {
    namespace = "app.parivaar.panchang.core.data"
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.systemProperty("pp.root.dir", "${rootProject.projectDir}")
        }
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.kotlin.test.junit5)
}
