// Feature modules depend on core modules only, never on each other.
plugins {
    id("panchang.android-compose")
}

dependencies {
    "implementation"(project(":core:designsystem"))
    "implementation"(project(":core:model"))
}
