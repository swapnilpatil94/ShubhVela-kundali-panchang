plugins { id("panchang.android-feature") }

android {
    namespace = "app.parivaar.panchang.feature.horoscope"
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}

dependencies {
    implementation(project(":content"))
    implementation(project(":engine"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.kotlin.test.junit5)
}
