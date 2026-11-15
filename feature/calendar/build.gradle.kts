plugins { id("panchang.android-feature") }

android {
    namespace = "app.parivaar.panchang.feature.calendar"
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform { excludeTags("benchmark") }
            it.systemProperty("pp.root.dir", "${rootProject.projectDir}")
        }
    }
}

dependencies {
    implementation(project(":feature:aaj"))
    implementation(project(":core:data"))
    implementation(project(":core:geo"))
    implementation(project(":engine"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.kotlin.test.junit5)
}

// Benchmarks (tag "benchmark") reuse the unit-test classes and run alone: `gradle :feature:calendar:benchmarkTest`.
afterEvaluate {
    val unit = tasks.named<Test>("testDebugUnitTest")
    tasks.register<Test>("benchmarkTest") {
        group = "verification"
        description = "Runs the calendar benchmarks (run alone)."
        testClassesDirs = files(unit.map { it.testClassesDirs })
        classpath = files(unit.map { it.classpath })
        systemProperty("pp.root.dir", "${rootProject.projectDir}")
        useJUnitPlatform { includeTags("benchmark") }
        failOnNoDiscoveredTests = true
    }
}
