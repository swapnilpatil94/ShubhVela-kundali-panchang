plugins { id("panchang.android-feature") }

android {
    namespace = "app.parivaar.panchang.feature.aaj"
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.systemProperty("pp.root.dir", "${rootProject.projectDir}")
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:geo"))
    implementation(project(":engine"))
    implementation(libs.kotlinx.coroutines.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.kotlin.test.junit5)
}
