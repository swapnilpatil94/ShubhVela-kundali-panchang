plugins {
    alias(libs.plugins.kotlin.jvm)
    id("panchang.quality")
}
// Fixtures (JSON vectors) land in src/main/resources/golden/ in M01. Oracle scripts live in tools/oracle (dev only).
