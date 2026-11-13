import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("panchang.quality")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvm()
    android {
        compileSdk = 36
        minSdk = 24
    }
    // iosArm64 is added later (R2+).
    sourceSets {
        commonTest.dependencies {
            implementation(libs.findLibrary("kotlin-test").get())
        }
        named("jvmTest") {
            dependencies { implementation(libs.findLibrary("kotlin-test-junit5").get()) }
        }
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
