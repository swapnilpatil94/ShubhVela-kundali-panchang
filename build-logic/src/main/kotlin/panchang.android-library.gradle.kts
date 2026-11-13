import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("panchang.quality")
}

extensions.configure<LibraryExtension> {
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = false
    }
}
