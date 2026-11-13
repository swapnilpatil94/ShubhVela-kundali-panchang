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
