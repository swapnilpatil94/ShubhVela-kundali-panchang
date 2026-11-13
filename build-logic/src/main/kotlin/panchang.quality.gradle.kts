// Detekt for every module. :engine adds config/detekt/engine.yml on top (no android.* imports).
plugins {
    id("dev.detekt")
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    source.setFrom(files("src"))
    val extra = if (project.path == ":engine") listOf("engine.yml") else emptyList()
    config.setFrom((listOf("detekt.yml") + extra).map { rootProject.file("config/detekt/$it") })
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    // detekt 1.23.x supports JVM targets up to 22; sources are analysed syntactically.
    jvmTarget = "17"
}
