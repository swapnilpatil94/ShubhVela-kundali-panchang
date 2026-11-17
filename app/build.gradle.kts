plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.cyclonedx)
    id("panchang.quality")
}

// The rule files the engine reads at run time, staged into assets/rules (single source: /rules).
val stageRuleAssets = tasks.register<Sync>("stageRuleAssets") {
    from(rootProject.file("rules")) {
        include(
            "durmuhurta.json", "choghadiya.json", "should_i.json", "vargas.json", "yogas.json", "nakshatra.json",
            "koota.json",
            "festivals/*.json",
        )
        into("rules")
    }
    // M12: the versioned bond axis weights and label bands, read at run time as assets/content/bond_weights.json.
    from(rootProject.file("content")) {
        include("bond_weights.json")
        into("content")
    }
    into(layout.buildDirectory.dir("generated/ruleAssets"))
}

// M11: the signed bundled content packs (built by :content:buildPacks with a throwaway key, D3), staged into
// assets/content/<lang>/pack.ppc plus assets/content/bundled.pub (public key only).
val stageContentPacks = tasks.register<Sync>("stageContentPacks") {
    dependsOn(":content:buildPacks")
    from(rootProject.layout.projectDirectory.dir("content/build/generated/contentPacks")) {
        include("content/bundled.pub", "content/*/pack.ppc")
    }
    into(layout.buildDirectory.dir("generated/contentAssets"))
}

tasks.configureEach {
    val staging = name == "stageRuleAssets" || name == "stageContentPacks"
    if (!staging && Regex("(?i).*(lint|assets).*").matches(name)) dependsOn(stageRuleAssets, stageContentPacks)
}

android {
    // The perf build type uses the release stubs (no debug gate) so it behaves like a shipped build.
    sourceSets.maybeCreate("perf").kotlin.directories.add("src/release/kotlin")
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/ruleAssets").get().asFile)
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/contentAssets").get().asFile)
    namespace = "app.parivaar.panchang"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // TODO(owner): VERIFY final applicationId before first Play upload (it can never change afterwards).
        applicationId = "app.parivaar.panchang"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        // Release-like speed (not debuggable) with the debug key and PpPerf timings, for measuring startup on a device.
        create("perf") {
            initWith(getByName("debug"))
            isDebuggable = false
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.all { it.useJUnitPlatform() } }
    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.kotlin.test.junit5)

    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:geo"))
    implementation(project(":core:model"))
    implementation(project(":engine"))
    implementation(project(":feature:aaj"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:kundli"))
    implementation(project(":feature:horoscope"))
    implementation(project(":content"))
    implementation(project(":feature:bonds"))
    implementation(project(":core:share"))
    implementation(project(":feature:bhakti"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))
}
