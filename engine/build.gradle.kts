plugins { id("panchang.kmp-library") }

kotlin {
    android { namespace = "app.parivaar.panchang.engine" }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            // Rule JSON, tree API only (Json.parseToJsonElement); no compiler plugin (M02 B1).
            implementation(libs.kotlinx.serialization.json)
        }
        // Astronomy Engine (MIT) is the base backend; jvm/android only, never commonMain (M01 C9).
        jvmMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies { implementation(libs.astronomy) }
        }
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies { implementation(libs.astronomy) }
        }
        jvmTest.dependencies {
            implementation(project(":engine-golden"))
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("pp.rulesDir", rootProject.file("rules").absolutePath)
    inputs.dir(rootProject.file("rules"))
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform { excludeTags("golden", "benchmark") }
}

// Golden-vector tests (tag "golden") and benchmarks (tag "benchmark") reuse the jvmTest classes.
fun registerTaggedTest(taskName: String, tag: String, desc: String) =
    tasks.register<Test>(taskName) {
        group = "verification"
        description = desc
        val jvmTestTask = tasks.named<Test>("jvmTest")
        testClassesDirs = jvmTestTask.get().testClassesDirs
        classpath = jvmTestTask.get().classpath
        useJUnitPlatform { includeTags(tag) }
        // No golden fixtures yet at B1: B3-B5 add them, then this must stay true.
        failOnNoDiscoveredTests = true
    }

registerTaggedTest("goldenTest", "golden", "Runs engine golden-vector tests.")
registerTaggedTest("benchmarkTest", "benchmark", "Runs engine benchmarks.")
