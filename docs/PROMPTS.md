# PROMPTS.md: Milestones (run one per session)

**How to run a milestone:** open a session in the repo and say `Implement milestone M03 from docs/PROMPTS.md.` Pick the model and effort shown on that milestone. Run `/clear` between milestones.

**Model legend:**
- **FABLE** = `claude-fable-5-2`, the most capable model. Start at effort high and use xhigh only where noted. Reserve it for correctness-critical work.
- **OPUS** = `claude-opus-5-5`. Set effort high explicitly; its default is medium.
- **SONNET** = `claude-sonnet-5-5`, for simple UI and content plumbing.

---

## R1: Launch

### M00 Repo bootstrap (OPUS, high)
**Read:** ARCHITECTURE.md.
**Build:**
- Gradle multi-module skeleton, exactly as in the module graph.
- `libs.versions.toml` with pinned versions.
- Hilt, detekt (`ForbiddenImport` blocks `android.*` in `:engine`, and blocks `androidx.security.crypto`), Android lint.
- `verification-metadata.xml`.
- GitHub Actions: build, test, lint, detekt, OSV-Scanner, CycloneDX.
- Empty Compose app that shows "Hello" with the theme placeholder.
- Renovate config.
- `docs/DECISIONS.md`.

**Accept:** CI is green, and `./gradlew :engine:jvmTest` runs a sample test.

### M01 Astronomy core (FABLE, high)
**Read:** ENGINE.md §1–§3, §8.
**Build in `:engine/astro`:**
- Time scales: UTC → TT with ΔT; Julian Date.
- Precession and nutation, obliquity.
- Ephemeris interface with both backends from ENGINE.md §2:
  - Base: Astronomy Engine (MIT).
  - Precision: our own DE440 Chebyshev reader, plus `tools/oracle/make_excerpt.py`.
  - Backend selection logic.
- Apparent geocentric ecliptic longitude, latitude and speed for the Sun, Moon, Mercury–Saturn and Rahu/Ketu (mean and true).
- Rise and set for the Sun and Moon, using the conventions in §3.
- `tools/oracle/` Python scripts (Skyfield + DE440) that generate `engine-golden/positions.json` (5,000 vectors) and `riseset.json` (2,000 vectors).

**Accept:**
- Golden tests pass at the tolerances in ENGINE.md §8.
- A benchmark computes 1 year of daily positions in under 300 ms on the JVM.

### M02 Jyotish core (FABLE, high)
**Read:** ENGINE.md §4–§5.
**Build:**
- Ayanamsa: Lahiri (the default), plus KP, Raman and True Chitra.
- Sidereal positions.
- Houses: whole-sign, Placidus, Sripati, equal.
- Nakshatra and pada; the Avakhada chakra.
- Vargas D1–D60.
- Vimshottari dasha to 5 levels, with the year-length setting.
- Yogini dasha.
- Yoga and dosha detectors (PRODUCT.md D5), table-driven from `rules/yogas.json`.
- Gochar.
- `ChartFacts` data class.

**Accept:**
- Golden ayanamsa values pass.
- Dasha dates match the oracle exactly.
- Each yoga rule has 2 positive and 2 negative fixtures.

### M03 Panchang (FABLE, high)
**Read:** ENGINE.md §6.
**Build:**
- The five limbs with exact end times, using root-finding.
- Kshaya and adhika handling.
- Windows: Rahu, Yamaganda, Gulika, Abhijit, Brahma, Durmuhurta, Varjyam, Amrit kaal, Bhadra, Panchak, Dishashool.
- Choghadiya, Hora, Gowri, Do-ghati.
- Tarabala and chandrabala.
- A `PanchangDay` API.

**Accept:**
- End times are within ±60 s of the oracle and the reference spot-checks (ENGINE.md §8).
- 365 days for one city compute in under 400 ms on the JVM.

### M04 Calendar and festival engine (FABLE, xhigh)
**Read:** ENGINE.md §7.
**Build:**
- Lunar months, amanta and purnimanta, with adhika and kshaya masa.
- Samvatsara.
- Regional solar calendars with their sankranti rules: Tamil, Malayalam, Bengali, Odia (R1 ships 2 of them; the others stay behind a flag).
- A data-driven festival rule DSL (`rules/festivals/*.json`). It must cover tithi-at-sunrise, tithi-at-pradosh, nishita, aparahna, Ekadashi Smarta/Vaishnava with parana, and sankranti-based festivals.
- Eclipse search, local circumstances, sutak.

**Accept:**
- Festival dates for 2024–2030 match `engine-golden/festivals_<region>.json`. Those files are curated by humans; the TODO(owner) is to get them reviewed by a pandit.
- Diaspora test: New York and London Ekadashi dates are computed locally.

### M05 Geo and timezones (OPUS, high)
**Read:** ENGINE.md §9.
**Build:**
- `tools/geo/` script that turns GeoNames `cities5000` plus a diaspora list into a SQLite FTS database. Include localized names where they exist, keep the file under 6 MB, and add the CC-BY attribution.
- `:core:geo` search.
- The historical India/Pakistan/Bangladesh tz overlay table, with a manual offset UI model.

**Accept:**
- Search returns "Prayagraj/Allahabad" for both queries.
- A 1950 Mumbai birth uses Bombay Time (+4:51) unless the user overrides it.

### M06 Secure data layer (FABLE, high)
**Read:** SECURITY.md (all of it).
**Build in `:core:data`:**
- Room 3 + SQLCipher with a Keystore-wrapped passphrase.
- DataStore-Tink.
- Repositories for Profile, Place, Bond and Settings.
- The encrypted snapshot, Block Store key backup, restore flow and encrypted export/import.
- "Delete all data".

**Accept:**
- Instrumented tests cover encrypt/decrypt, snapshot restore with a simulated new device, and rejection of tampered imports.
- The backup rules XML exactly as specified.

### M07 Design system, shell, onboarding (OPUS, high)
**Read:** DESIGN.md.
**Build:**
- The theme (light and dark), fonts, and the components listed in DESIGN.md.
- `NavigationSuiteScaffold` with 5 tabs, Navigation 3, edge-to-edge, predictive back.
- Onboarding, 5 steps.
- Global search palette.
- Settings → Conventions screen (J4).

**Accept:**
- Roborazzi screenshots for en, hi, mr and gu, at font scales 1.0 and 2.0.
- Cold-start baseline measured.

### M08 Aaj, widgets, notifications (OPUS, high)
**Read:** PRODUCT.md §A, §L; ENGINE.md §10.
**Build:**
- Aaj screen (A1–A8).
- `DayArc` and `TimeWindowCard` with live countdowns.
- Explain sheets.
- Glance widgets (L1).
- Notification channels and schedulers (L2).
- Panchang share card (A8).

**Accept:**
- The screen renders offline.
- Widgets update at element transitions.
- Notification tests use a fake clock.

### M09 Calendar (OPUS, high)
**Read:** PRODUCT.md §B.
**Build:** B1–B5, plus B6 for the 2 R1 regions, with a day detail sheet and festival guide pages.

**Accept:** month render takes under 100 ms after warm-up, and screenshot tests are in place.

### M10 Kundli and family (OPUS, high)
**Read:** PRODUCT.md §D.
**Build:**
- Profile CRUD.
- D2, D3, D5 and D9.
- `ChartView` in North, South and East styles.
- `DashaTimeline`.
- Family dates (D11) with reminders.

**Accept:**
- `ChartView` accessibility descriptions are in place.
- The family-date finder matches the engine golden fixtures.

### M11 Content packs and rashifal (OPUS, high)
**Read:** I18N_CONTENT.md.
**Build:**
- `:content`: CBOR packs, Ed25519 verification, the term resolver, the template renderer.
- Rules engine for horoscopes (H1, H2) that selects phrases from transits.
- A sample pack for en and hi; other languages are placeholder.
- Content lint: banned words, missing IDs, length limits.

**Accept:**
- A tampered pack is rejected.
- Every content ID resolves in every bundled language.

### M12 Bonds and sharing (OPUS, high)
**Read:** SOCIAL.md, PRODUCT.md §F.
**Build:**
- F1 marriage matching: North and South.
- Bond Code codec.
- App Links and the `.well-known` file template.
- F2, F3, F6 and F9.
- Card renderer and share intents for WhatsApp, Instagram Stories and the chooser.
- Play Install Referrer for referrals.

**Accept:**
- Codec fuzz test.
- A deep link with a malformed code is rejected.
- Cards contain no EXIF data and no birth data (test asserts this).

### M13 Bhakti (SONNET, high)
**Read:** PRODUCT.md §G.
**Build:** G1–G4, G7 and G8. Sacred-zone flags go on routes.

**Accept:**
- Jap haptics and streaks persist.
- A sacred-zone test asserts that no ad composable appears on these routes.

### M14 PDF reports (OPUS, high)
**Read:** I18N_CONTENT.md §Reports.
**Build:**
- `ReportSpec`.
- `PdfDocument` renderer with embedded Noto subsets.
- E1 and E7.
- WorkManager job with progress, plus a preview.

**Accept:**
- Devanagari, Gujarati and Tamil conjunct test pages render correctly. Screenshot them via `PdfRenderer`.
- A 12-page report takes under 2 s.

### M15 Ads, consent, billing (OPUS, high, then FABLE review at high)
**Read:** MONETIZATION.md.
**Build:**
- `:core:ads` AdGate with placements, caps, sacred-zone guard, rewarded unlocks with server-side verification hooks, UMP, age-restricted treatment and the category/URL block checklist.
- `:core:billing` Plan with subscriptions, prepaid passes and lifetime purchase.
- `play/prices.json` and a script that pushes prices through the Play Developer API.
- Remote Config ad knobs.
- Holdout flag.

**Accept:**
- Unit tests cover the cap logic.
- No ad requests in the first session.
- Plan entitlement removes every ad surface.
- FABLE reviews Play and AdMob policy compliance against MONETIZATION.md.

### M16 Localization pass (SONNET, medium)
**Read:** I18N_CONTENT.md.
**Build:**
- `tools/translate/` script that uses Claude Batch (`claude-haiku-5-5`) with the glossary and style guide, writing output to `content/<lang>/` and `values-<lang>/`.
- Pseudo-locale CI.

**Accept:** no missing strings in R1 locales, and TODO(owner) marks for native review.

### M17 Performance, size, accessibility (OPUS, high)
**Build:**
- Baseline Profiles and Macrobenchmark for startup, Aaj and Calendar scroll.
- R8 keep-rule audit.
- App size report.
- TalkBack pass.

**Accept:** the DESIGN.md budgets are met, and accessibility scanner issues are zero (or waived with a reason).

### M18 Release hardening (FABLE, high)
**Read:** SECURITY.md.
**Build:**
- MASVS checklist walk-through, with fixes.
- `scripts/manifest_audit.sh`.
- Data-safety answers in `play/data_safety.md`.
- Privacy policy draft in `play/privacy_policy.md`, marked for legal review.
- Store listing copy in en and hi, written to the tone rules.

**Accept:** a written audit report is saved to `docs/AUDIT_R1.md` with no open High items.

---

## R2 (after launch metrics are healthy)
- **M19 (FABLE):** Muhurat engine (C1–C3).
- **M20 (FABLE):** D4, D6, D7, D8 (vargas UI, Ashtakavarga, Shadbala, KP).
- **M21 (FABLE):** remaining regional calendars (B6), each gated by a pandit-reviewed fixture.
- **M22 (OPUS):** E2–E9 reports.
- **M23 (OPUS):** I1–I4 (numerology, tarot, baby names, vastu).
- **M24 (OPUS):** `:web` static SEO site (city × date × term pages, diaspora pages).
- **M25 (OPUS):** F4, F5, F7, F8 (deep-dive, squad, bond weather, Cosmic Wrapped).
- **M26 (SONNET):** R2 languages.

## R3
- **M27 (FABLE):** AI proxy Worker in TypeScript with `@anthropic-ai/sdk`.
  - Model `claude-haiku-5-5` for answers. Prompt caching on the system prompt and the chart facts.
  - Play Integrity verification, per-install caps, no body logging.
  - K1 and K2 in the app, with an upload preview.
- **M28 (OPUS):** Varshphal, Western chart, Lal Kitab content.
- **M29 (OPUS):** iOS through KMP. `:engine` and `:content` are reused, with a Compose Multiplatform or SwiftUI shell.
