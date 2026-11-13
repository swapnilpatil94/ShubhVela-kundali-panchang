plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugin.agp)
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.compose)
    implementation(libs.plugin.detekt)
    implementation(libs.plugin.ksp)
    implementation(libs.plugin.hilt)
}
