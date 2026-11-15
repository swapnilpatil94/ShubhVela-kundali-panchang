plugins { id("panchang.android-compose") }

android {
    namespace = "app.parivaar.panchang.core.designsystem"
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}

dependencies {
    // ThemeMode lives with the settings model (M07 B2).
    api(project(":core:model"))
    testImplementation(libs.kotlin.test.junit5)
}
