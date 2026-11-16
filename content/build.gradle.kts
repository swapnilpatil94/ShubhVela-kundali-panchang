plugins { id("panchang.kmp-library") }

kotlin {
    android { namespace = "app.parivaar.panchang.content" }
    sourceSets {
        commonMain.dependencies {
            // Graha/Rashi, Gochar, Bala and ChartCalculator for the rashifal rules (docs/specs/M11.md C4).
            api(project(":engine"))
            // Pack payload, tree API only (Json.parseToJsonElement); no compiler plugin (M11 D2).
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// --- M11 I2/I3 (implementer): source packs, build-time packing/signing, content lint. ---
// Source packs (original text, M11 I4) and the generated, signed bundled packs (M11 I2, D3).
val contentSrc = rootProject.layout.projectDirectory.dir("content-src")
val packsOut = layout.buildDirectory.dir("generated/contentPacks")

tasks.withType<Test>().configureEach {
    systemProperty("pp.contentSrc", contentSrc.asFile.absolutePath)
    inputs.dir(contentSrc)
}

val jvmMainClasspath = files(
    kotlin.jvm().compilations.getByName("main").output.allOutputs,
    kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles,
)

/**
 * D3: a fresh Ed25519 key pair in memory on every run (JDK built-in), signs the bundled packs, writes only the public
 * key. The private key is never written anywhere; integrity only (APK signing gives authenticity).
 */
tasks.register<JavaExec>("buildPacks") {
    group = "build"
    description = "Packs and signs the bundled content packs with a throwaway key (M11 D3)."
    classpath = jvmMainClasspath
    mainClass = "app.parivaar.panchang.content.tools.PackBuilderKt"
    inputs.dir(contentSrc)
    outputs.dir(packsOut)
    args(contentSrc.asFile.absolutePath, packsOut.get().asFile.absolutePath)
}

/** M11 I3: content lint over content-src; any error fails the build. Output lines are `lang id rule` only. */
val contentLint = tasks.register<JavaExec>("contentLint") {
    group = "verification"
    description = "Lints the content packs (banned words, missing ids, lengths, placeholders)."
    classpath = jvmMainClasspath
    mainClass = "app.parivaar.panchang.content.tools.ContentLintKt"
    inputs.dir(contentSrc)
    val stamp = layout.buildDirectory.file("contentLint/ok.txt")
    outputs.file(stamp)
    args(contentSrc.asFile.absolutePath, stamp.get().asFile.absolutePath)
}

tasks.named("check") { dependsOn(contentLint) }
