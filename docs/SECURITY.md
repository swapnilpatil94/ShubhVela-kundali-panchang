# SECURITY.md: Security and privacy architecture (OWASP MASVS v2)

**Threat model:** birth data for the user, their family and friends is sensitive personal data under India's DPDP Act. Protect it from these threats:
1. Other apps on the device.
2. Device theft or forensic extraction.
3. Backup and transfer leaks.
4. Network interception.
5. Leaks through analytics, crash reports or ad SDKs.
6. Abuse of the AI proxy.

## Data storage (STORAGE, CRYPTO)
- **Profiles, family, bonds and notes:** Room 3 with the **SQLCipher** driver (`net.zetetic:sqlcipher-android` ≥ 4.20, which supports 16 KB pages).
  - The database passphrase is 32 random bytes.
  - It is wrapped by an AES-256-GCM key in Android Keystore (TEE), with StrongBox only if available.
  - The wrapped blob is stored in `noBackupFilesDir`.
- **Settings and small secrets:** DataStore with `datastore-tink` (`AeadSerializer`) and Tink `AndroidKeysetManager` (`android-keystore://` master key).
- **Do NOT use `androidx.security:security-crypto`.** It is deprecated.
- **Nothing personal outside the encrypted stores:**
  - Logs.
  - Crashlytics keys and breadcrumbs.
  - Analytics events and parameters.
  - Notification text on the lock screen. Use `VISIBILITY_PRIVATE` and show names only when unlocked.
  - Widget content. No birth details.
  - File names.
- **PDFs and share images:**
  - Write them to `cacheDir/share/`.
  - Expose them only through `FileProvider` with a time-limited grant.
  - Delete them on the next app start.
  - Strip EXIF metadata.
- **Optional privacy features:**
  - App lock: a Keystore key bound to `setUserAuthenticationParameters`, opened with BiometricPrompt.
  - "Privacy screen" toggle: `FLAG_SECURE` on profile, report and family screens, plus `setRecentsScreenshotEnabled(false)`.
- **Clipboard:** set `ClipDescription.EXTRA_IS_SENSITIVE` whenever birth data is copied.

## Backup and device migration (no data loss, no plaintext)
1. **Exclude live data from backup.** Exclude the database and its wrapped key from `<cloud-backup>` and `<device-transfer>` in `dataExtractionRules`, and mirror the exclusion in `fullBackupContent` for Android ≤11.
2. **Back up an encrypted snapshot instead.**
   - Each day, and on every change (debounced), write `files/backup/snapshot.bin`: the family data as CBOR, encrypted with Tink AEAD under a random backup key K_b.
   - This file is the only thing included in backup.
   - Set `disableIfNoEncryptionCapabilities="true"`, so the snapshot is only backed up when the backup itself is end-to-end encrypted.
3. **Store K_b in Block Store.** Call `setShouldBackupToCloud(true)` only when `isEndToEndEncryptionAvailable()` returns true.
4. **Restore on a new device.**
   - Read K_b from Block Store.
   - Decrypt the snapshot.
   - Create a new database with a fresh passphrase.
   - Rotate K_b.
5. **Fallback for devices with no screen lock or no Google account.** The user can export an encrypted family file through the Storage Access Framework:
   - Key derivation: PBKDF2-HMAC-SHA256 at ≥ 600k iterations on API 26+, or Argon2id through a vetted library.
   - Cipher: AES-GCM.
   - The user sets a passphrase, and the app shows a strength meter.

## Network (NETWORK)
- In `network_security_config`:
  - `cleartextTrafficPermitted="false"`.
  - `<certificateTransparency enabled="true"/>`.
  - No user CAs in release builds; use `debug-overrides` for debug only.
- **No certificate pinning.** The Cloudflare edge rotates certificates, so pins would break. Certificate Transparency and Play Integrity cover this instead.
- **Endpoints:**
  - Content packs: Cloudflare R2.
  - Remote Config.
  - AI proxy (R3).
  - Nothing else.
- **AI proxy (Cloudflare Worker):**
  - Play Integrity *standard* request, with `requestHash = SHA-256(body)`.
  - The Worker verifies the token's package name, the hash, `PLAY_RECOGNIZED`, the licensing verdict and the device integrity labels.
  - Per-install daily cap, using `recentDeviceActivity` combined with Durable Object counters.
  - Provider keys live only as Worker secrets.
  - The proxy logs no request bodies.
  - Send computed chart facts only. Never names, places or the DOB string.
  - Run in telemetry-only mode for 2 weeks before enforcing.

## Platform (PLATFORM)
- Set `android:exported` explicitly on every component. Only the launcher activity, the App Links activity, Glance receivers and the billing/ads SDK components are exported.
- `android:intentMatchingFlags="enforceIntentFilter"` (Android 16 safer intents).
- **Deep links:**
  - Allow-list the host and path.
  - Parse the Bond Code with strict length and CRC checks.
  - A deep link never deletes, exports or purchases anything.
- **WebView:** none in the app. Use Custom Tabs for the privacy policy and help pages (ad SDKs use WebView internally).
- **Tapjacking:** `filterTouchesWhenObscured` on purchase, delete and export buttons.
- **Permissions:**
  - `POST_NOTIFICATIONS`, requested in context.
  - `SCHEDULE_EXACT_ALARM` only if the user turns on "exact alerts"; the default is WorkManager or windowed alarms. See the exact-alarm policy note in ENGINE.md, Notifications.
  - Photo Picker for avatars, with no media permissions.
  - **No** contacts, location (city is searched manually; there is an optional one-shot coarse location using `ACCESS_COARSE_LOCATION` with in-context rationale), phone or SMS permissions.

## Code and supply chain (CODE, RESILIENCE)
- **Platform levels:** targetSdk 36, minSdk 24.
- **Shrinking:** R8 full mode, resource shrinking, keep rules reviewed.
- **Dependencies:**
  - Gradle version catalog with `verification-metadata.xml` (sha256 + pgp).
  - Renovate.
  - OSV-Scanner in CI.
  - CycloneDX SBOM per release.
  - Check Play SDK Index warnings.
- **Signing and updates:** Play App Signing; upload key stored offline; in-app updates (flexible).
- **Secrets:** none in the repo or the APK.
  - The `google-services.json` API key is restricted by package name and SHA-256.
  - The AdMob app ID is public.
- **Input validation:** every imported file (backup, bond code, content pack) gets a schema check, a size limit, and signature verification.
  - Content packs are signed with Ed25519 through Tink. The public key is pinned in the app, and the app refuses unsigned packs.
- **No root or jailbreak detection.** It adds nothing for this threat model.

## Privacy and compliance (PRIVACY)
- **Data safety form:**
  - AdMob: approximate location (IP), device identifiers (Ad ID, App Set ID), interactions and diagnostics.
  - Crashlytics: crash logs and an installation ID.
  - Analytics (if used): interactions.
  - The AI proxy (R3) collects personal info only if K2 is used. Declare it as ephemeral if the Worker stores nothing.
- **Analytics:**
  - Firebase Analytics with Ad ID collection disabled; use App Set ID.
  - Event names come from a fixed allow-list in `core/analytics/Events.kt`, and parameters are enums only.
- **Consent and age:**
  - UMP consent flows.
  - Age is derived from the user's own profile. Under 18 means:
    - non-personalized ads only;
    - analytics off;
    - AI off;
    - the bond share screen shows initials only.
- **DPDP (most obligations from 13 May 2027):**
  - Notice in plain language in all 12 languages.
  - Consent for analytics and personalized ads.
  - In-app "Delete all my data" and "Export my data".
  - A grievance contact.
  - Breach runbook: notify the Board and affected users within the timelines in the DPDP Rules.
- **Legal review needed:** the verifiable parental consent requirement for minors, and whether processing that happens only on the device creates fiduciary duties.

## Security tests (in CI)
- **Unit tests:**
  - Crypto round-trips.
  - Restoring a snapshot on a "new device" (a simulated new key).
  - Rejection of tampered packs and bond codes.
- **Lint and static analysis:**
  - Lint rules: `LogDetector` (no `Log.*` with profile fields), plus a custom detector that bans `security-crypto` imports.
  - MobSF static scan of the release APK before each release.
- **Manifest audit:** a script asserts the list of exported components and the permissions.
