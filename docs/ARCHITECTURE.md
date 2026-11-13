# ARCHITECTURE.md: Modules, stack, conventions

## Stack
Versions were verified on 13 Nov 2026. Pin them in `gradle/libs.versions.toml` and let Renovate bump them.

| Concern | Choice |
|---|---|
| Language/build | Kotlin 2.4.21, AGP 9.5.x, Gradle version catalog, KSP |
| Platform levels | minSdk 24, targetSdk/compileSdk 36 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 1.4 → 1.5 (Expressive), material3-adaptive 1.3, activity-compose 1.13 |
| Navigation | Navigation 3 (1.2.x) with type-safe keys |
| DI | Hilt (app); plain constructor injection in KMP modules |
| Async | Coroutines + Flow; `StateFlow` UI state; no LiveData |
| Serialization | kotlinx.serialization (JSON + CBOR) |
| Date/time | kotlinx-datetime 0.8 in engine; `java.time` (desugared) at the edges |
| Database | Room 3 (3.0.x, KMP) + SQLCipher driver |
| Preferences | DataStore 1.3 + datastore-tink; Tink 1.24 |
| Background | WorkManager 2.12; AlarmManager (windowed) for time alerts |
| Widgets | Glance 1.2 + glance-material3 |
| Billing | Play Billing Library 8+ (verify latest) |
| Ads | Google Mobile Ads (Next-Gen if stable) + mediation; UMP 4.0 |
| Telemetry | Firebase BoM (Crashlytics; Analytics optional, Ad ID off); Remote Config with ≥ 12 h fetch interval |
| Integrity | Play Integrity 1.6 (AI proxy only) |
| Backup | Auto Backup rules + Block Store 16.4 |
| Performance | Baseline Profiles (plugin 1.5), Macrobenchmark, R8 full mode |
| Testing | JUnit5/kotlin-test, Turbine, Robolectric, Roborazzi, Compose UI test, Macrobenchmark |
| CI | GitHub Actions: build, unit tests, golden-engine tests, lint, detekt, OSV-Scanner, CycloneDX SBOM |

## Module graph
```
:engine            KMP (commonMain, jvm, android; iosArm64 later). PURE KOTLIN, no deps except kotlinx-datetime/serialization.
                   astro/ (ephemeris, time scales, precession, nutation, rise/set)
                   jyotish/ (ayanamsa, houses, vargas, dashas, yogas, ashtakavarga, shadbala, KP)
                   panchang/ (limbs, windows, choghadiya, hora, muhurta rules)
                   calendar/ (lunar months, regional solar calendars, festival rule engine)
                   match/ (ashtakoota, porutham, bond axes)
                   numerology/, tarot/ (deterministic draws)
:engine-golden     test fixtures (JSON vectors) + oracle scripts (tools/oracle, Python/Skyfield, dev only)
:content           KMP: content-pack loader, signature verify, term resolver, template renderer
:core:model        shared domain models (Profile, Place, ChartFacts, Bond, Settings)
:core:data         Room3+SQLCipher, DataStore-Tink, repositories, backup snapshot
:core:geo          offline place search (SQLite FTS of GeoNames subset) + historical tz overlay
:core:designsystem theme, typography, components (DESIGN.md)
:core:ads          AdGate facade: placements, caps, rewarded unlocks, consent, age treatment
:core:billing      Plan entitlements (Flow<Entitlement>)
:core:share        card renderer, share intents, App Links, Bond Code codec
:core:pdf          ReportSpec → PdfDocument renderer
:core:notify       schedulers, channels, notification templates
:core:analytics    allow-listed events only
:feature:onboarding :feature:aaj :feature:calendar :feature:muhurat :feature:kundli
:feature:reports :feature:bonds :feature:bhakti :feature:horoscope :feature:tools :feature:settings
:widget            Glance widgets
:app               Application, nav graph, DI wiring, manifest
:web (R2)          JVM: static SEO site generator using :engine (+ :content), deploy to Cloudflare Pages
worker/ (R3)       Cloudflare Worker (TypeScript) AI proxy, @anthropic-ai/sdk
```

**Rules:**
- Feature modules depend on core modules only, never on each other.
- `:engine` has zero Android imports. Enforce this with detekt `ForbiddenImport`.

## Conventions
- **UI pattern.** Each screen is `XxxRoute` (wires the ViewModel) plus `XxxScreen(state, onEvent)`, which is stateless and previewable. UI state is a single immutable data class.
- **ViewModels.** Inject use cases. Long computations run on `Dispatchers.Default`, and results are cached in memory per (profile, date, place, settings).
- **Engine API.** Use pure functions or small immutable classes, and always pass time as `Instant` + `Place` + `CalcSettings`.
- **Errors.** Model them with `Result`/sealed types. Never swallow exceptions; log a code to Crashlytics with no PII.
- **Strings.** Keep every string in resources. Do not put user-visible text in Kotlin code.
- **Testing per feature.**
  - Engine logic: ≥ 90% line coverage.
  - Every feature: one screenshot test and one ViewModel test.
- **Definition of done** for every task:
  - Build passes.
  - Tests pass.
  - Lint and detekt are clean.
  - No new permissions without updating SECURITY.md.
  - Strings are in place for all R1 locales; placeholders are allowed but must be flagged.
