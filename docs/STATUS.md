# Status

One line per finished milestone: `ID · date · summary · open TODO(owner) items`.

- Kit v1 · 2026-11-13 · build kit unpacked (29 files), orchestrator setup done · see docs/specs/OVERVIEW.md
- M00 · 2026-11-13 · Gradle KMP/Compose/Hilt skeleton, detekt+lint, CI workflows, SBOM; engine jvmTest, assembleDebug, lint, detekt pass · TODO(owner): applicationId, Android platform 37, first ./gradlew on a fast network, run CI on GitHub
- M01 · 2026-11-14 · Astronomy core in :engine (time scales, IAU 2006/2000B, DE440 reader + Astronomy Engine base backend, apparent positions, nodes, Sun/Moon rise/set); golden G1–G3 pass on 10,000 epochs and 4,000 city-days, benchmark 6 ms / 17 ms vs 300 ms; reviewer PASS · TODO(owner): pandit sign-off on node definitions and high-latitude rules, ΔT step at the leap-table horizon, confirm `*` tolerances and the amended fragile rule, pre-1900 ΔT rows vs NASA page, ERFA vectors vs t_erfa_c.c, upstream MIT LICENSE text for Astronomy Engine, 14 MB bin in plain git vs LFS, JitPack vs vendored source, Base Moon light-time
