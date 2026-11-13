plugins { id("panchang.kmp-library") }

kotlin {
    android { namespace = "app.parivaar.panchang.engine" }
    sourceSets {
        commonMain.dependencies { implementation(libs.kotlinx.datetime) }
    }
}

// Golden-vector tests (tag "golden") run via :engine:goldenTest; no vectors exist yet (M01).
tasks.register<Test>("goldenTest") {
    group = "verification"
    description = "Runs engine golden-vector tests."
    val jvmTestTask = tasks.named<Test>("jvmTest")
    testClassesDirs = jvmTestTask.get().testClassesDirs
    classpath = jvmTestTask.get().classpath
    useJUnitPlatform { includeTags("golden") }
    failOnNoDiscoveredTests = false
}
