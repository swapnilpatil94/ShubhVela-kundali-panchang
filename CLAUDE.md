# Parivaar Panchang: Agent Rules (keep this file short)

**What it is:** an Android all-in-one Vedic astrology, Hindu calendar and devotion app. It is free with ads, offline-first, private by design, and offered in 12 languages.

**Single source of truth: `MASTER.md`.** Never read all of it. List the parts with `grep -n "^# Part" MASTER.md`, then read only the part or section your task names. Use the `scout` subagent for lookups.

## Non-negotiables
1. **Accuracy beats everything.**
   - Golden tests are mandatory.
   - Never loosen a tolerance or edit golden data to make a test pass.
2. **Privacy.**
   - Birth data stays on the device. The only exceptions are a Bond Code (derived traits only) and an AI request the user has opted into.
   - No personal data in logs, analytics, Crashlytics, URLs, file names or notifications.
3. **Ads.**
   - No ads in sacred zones: aarti, jap, mantra, Gita and Chalisa, puja timer, shraddha.
   - No ads in the first session, and never over the timing screens.
   - Debug builds use Google test ad IDs only.
4. **Tone.**
   - No fear language and no remedy selling.
   - Every interpretation carries the footer "Traditional interpretation, not a scientific prediction."
5. **Licences.**
   - No AGPL or GPL. **No Swiss Ephemeris**, nor anything built on it.
   - Do not use `androidx.security:security-crypto`.
   - Never copy text, translations or art.
6. `:engine` is pure Kotlin Multiplatform, with no `android.*` imports.
7. **Strings and permissions.**
   - All user text lives in resources or content packs.
   - Any new permission must also be added to MASTER Part 5.
8. **Accounts and secrets are human-only.** See MASTER Part 10 §B.
   - Never enter passwords or 2FA codes.
   - Never accept terms, add payment details or press the final Create/Submit/Publish button.
   - Never read or write `~/.pp-secrets`.

## Workflow
- Run milestones with `/milestone <ID>`. Routing is in MASTER Part 11:
  - `architect` (Fable) writes specs.
  - `engine-dev` (Fable) writes CRITICAL code.
  - `implementer` (Sonnet) builds.
  - `reviewer` (Opus) gates.
- **One milestone per session.** Commit, then `/clear`.
- When something is ambiguous, choose the more private, more accurate, fewer-ads option and log it in `docs/DECISIONS.md`. Do not stop to ask.
- **Token hygiene:**
  - Run the narrowest test first.
  - Read line ranges, not whole files.
  - Never paste file contents into chat.
  - End with a 5-line summary.

## Commands
- `./gradlew assembleDebug`
- `./gradlew :engine:jvmTest :engine:goldenTest`
- `./gradlew :feature:<x>:testDebugUnitTest`
- `./gradlew lint detekt`
- `./gradlew verifyRoborazziDebug`
- `./gradlew :app:bundleRelease`
- `scripts/manifest_audit.sh`
