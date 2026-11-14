pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Astronomy Engine (MIT) is not on Maven Central; JitPack serves only this group (M01 B1).
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("io.github.cosinekitty") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "parivaar-panchang"

include(
    ":app",
    ":engine",
    ":engine-golden",
    ":content",
    ":widget",
    ":core:model",
    ":core:data",
    ":core:geo",
    ":core:designsystem",
    ":core:ads",
    ":core:billing",
    ":core:share",
    ":core:pdf",
    ":core:notify",
    ":core:analytics",
    ":feature:onboarding",
    ":feature:aaj",
    ":feature:calendar",
    ":feature:muhurat",
    ":feature:kundli",
    ":feature:reports",
    ":feature:bonds",
    ":feature:bhakti",
    ":feature:horoscope",
    ":feature:tools",
    ":feature:settings",
)
// :web (R2) and worker/ (R3) are intentionally not included yet.
