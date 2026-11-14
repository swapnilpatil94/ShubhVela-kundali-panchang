plugins { id("panchang.android-library") }

val geoAssetsDir = layout.buildDirectory.dir("generated/geoAssets")

val copyGeoOverlay = tasks.register<Copy>("copyGeoOverlay") {
    from(rootProject.file("rules/tz_overlay.json"))
    into(geoAssetsDir.map { it.dir("geo") })
}

android {
    namespace = "app.parivaar.panchang.core.geo"
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    sourceSets.getByName("main").assets.directories.add(geoAssetsDir.get().asFile.path)
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.systemProperty("pp.golden.dir", "${rootProject.projectDir}/engine-golden/src/main/resources/golden")
            it.systemProperty("pp.seed.dir", "${projectDir}/src/main/assets/geo")
            it.systemProperty("pp.rules.dir", "${rootProject.projectDir}/rules")
        }
    }
}

// Overlay JSON is generated into assets from the repo-level rules dir (single source of truth).
tasks.matching {
    it.name != "copyGeoOverlay" &&
        ((it.name.startsWith("merge") && it.name.endsWith("Assets")) || it.name.contains("Lint", ignoreCase = true))
}.configureEach {
    dependsOn(copyGeoOverlay)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":engine"))
    implementation(libs.kotlinx.serialization.json)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.kotlin.test.junit5)
}
