plugins { id("panchang.android-feature") }

android {
    namespace = "app.parivaar.panchang.feature.settings"
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":engine"))
    testImplementation(libs.kotlin.test.junit5)
}
