plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.cyclonedx)
    id("panchang.quality")
}

android {
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
    buildFeatures { compose = true }
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
    implementation(project(":feature:bonds"))
    implementation(project(":feature:bhakti"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))
}
