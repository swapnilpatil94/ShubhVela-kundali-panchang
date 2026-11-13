# OPS.md: Accounts, infrastructure, observability, release

## A. Accounts and identity
**Owner account for everything:** `[REDACTED_EMAIL_ADDRESS_4]`.

| Service | Status | Use |
|---|---|---|
| Google Play Console | Registered | App listing, testing tracks, billing, vitals |
| AdMob | Exists | Ads, UMP consent messages, app-ads.txt |
| Firebase / Google Cloud | Create the project `parivaar-panchang` | Crashlytics, Analytics, Performance, Remote Config; service account for the Play API |
| Cloudflare (free plan, account "getByeterush") | Exists | Pages (site, privacy policy, app-ads.txt, assetlinks.json, content packs), Workers (Bond teaser, AI proxy in R3), Email Routing, Web Analytics |
| GitHub (private repo) | Recommended | Code, CI (GitHub Actions free minutes) |

**Public contact email**
- The Play listing, privacy policy and DPDP grievance contact all show this address publicly.
- Better: buy a domain (Cloudflare Registrar sells at cost) and set up `support@<DOMAIN>` through Cloudflare Email Routing (free), forwarding to the Gmail address.
- Until the domain exists, use the Gmail address.
- **Never** hard-code the email in app code except on the About and Privacy screens (one string resource).

**A domain is required** (`<DOMAIN>`). It is needed for:
- App Links: `/.well-known/assetlinks.json`
- `app-ads.txt` (AdMob checks the developer website set in Play)
- The privacy policy and terms URLs
- The SEO site

Host it on Cloudflare Pages (free; no card needed). **Avoid R2 for now:** it needs a payment method even on the free tier. Serve signed content packs from Pages instead (limit 25 MiB per file).

## B. Safety rules for AI agents working with accounts (binding)
1. **Agents may:**
   - Read the consoles in the user's logged-in Chrome.
   - Prepare form values.
   - Write the exact click-by-click steps.
   - Run CLIs once the human has authorised them through OAuth in the browser (`firebase login`, `wrangler login`).
2. **Only the human may:**
   - Enter passwords or 2FA codes.
   - Accept terms or agreements.
   - Add payment or tax information.
   - Create a service-account key.
   - Generate or hold the upload keystore and its passwords.
   - Press the final **Create / Submit / Publish / Roll out** button on Play, AdMob, Firebase or Cloudflare.
3. **Secrets never enter the repo, chat or logs.** Keep them in `~/.pp-secrets/` (outside the repo) and reference them through environment variables or `local.properties` / `keystore.properties`, which are git-ignored.

| Secret | Created by | Where it lives |
|---|---|---|
| Upload keystore (`upload.jks`) + passwords | Human (`keytool`) | `~/.pp-secrets/`, plus a backup in a password manager |
| Play Developer API service-account JSON | Human (Google Cloud → IAM; then grant it in Play Console → Users & permissions) | `~/.pp-secrets/play-api.json` |
| Content-pack Ed25519 private key | Script (`tools/sign/keygen`), run by the human | `~/.pp-secrets/content-signing.key` (the public key goes in the app) |
| Cloudflare API token (Pages deploy only) | Human, in the dashboard, scoped token | Env var `CLOUDFLARE_API_TOKEN` / GitHub secret |
| AdMob app ID and ad unit IDs | Human, in AdMob | Not secret: `gradle.properties` (release) |
| `google-services.json` | Human, downloaded from Firebase | `app/` (safe to commit; restrict the API key by package + SHA-256 in Google Cloud) |

## C. AdMob production integration
- **SDKs:**
  - Google Mobile Ads. Use the Next-Gen SDK if it is stable at build time; otherwise the GMA SDK.
  - UMP SDK for consent.
- **App registration:** add the Android app in AdMob, then **link it to the Play listing** after the first publish.
- **Ad units** (create in AdMob; names below):

  | Unit name | Format |
  |---|---|
  | `pp_native_aaj` | Native advanced |
  | `pp_native_calendar` | Native |
  | `pp_banner_calendar` | Adaptive banner |
  | `pp_banner_tools` | Adaptive banner |
  | `pp_inter_break` | Interstitial |
  | `pp_rewarded_unlock` | Rewarded |
  | `pp_rewarded_inter_bonus` | Rewarded interstitial |
  | `pp_appopen` | App open |

- **Debug vs release:**
  - **Debug builds always use Google's published test ad unit IDs** and register test devices. The real IDs appear only in the `release` build type, through BuildConfig fields from `gradle.properties`.
  - **Never tap your own live ads.** That gets the AdMob account suspended, and the suspension is account-wide.
- **Privacy & messaging** (configured in AdMob):
  - A GDPR message for EEA/UK/CH.
  - A US state regulations message.
  - In the app, call `ConsentInformation.requestConsentInfoUpdate` on every launch, before any ad request.
- **Blocking controls** (detail in MONETIZATION.md):
  - Block these categories: Gambling, Alcohol, Dating, Get-rich-quick, Sexual, Sensationalism, Social casino.
  - Set max content rating to PG.
  - Block competitor astrology and remedy URLs, and review the Ad review center weekly.
- **`app-ads.txt`:**
  - Serve it at `https://<DOMAIN>/app-ads.txt`, containing the publisher line from AdMob.
  - The Play listing's "Website" must be `https://<DOMAIN>`.
- **Server-side verification (R3, for AI credits only):** a Worker endpoint that verifies the AdMob SSV signature using Google's public keys.
- **Mediation:**
  - **Launch with AdMob only** (fewer SDKs, fewer tokens, smaller app).
  - Two to four weeks after launch, add bidding partners (Meta Audience Network, InMobi, AppLovin, Pangle) through AdMob mediation groups.
  - For each adapter: add it in Gradle, check its SDK Index status, and update the Data safety form.

## D. Observability and analytics (privacy-first)
- **Firebase Crashlytics:**
  - Custom keys: `app_version`, `backend` (base/precision), `locale`, `tradition`, `theme`.
  - Breadcrumbs: route names only.
  - Never include names, dates, places or free text.
  - Alerts: email on new and regressed issues, plus velocity alerts.
- **Firebase Analytics:**
  - `google_analytics_adid_collection_enabled=false`.
  - Consent Mode v2 defaults set to *denied* in the EEA until UMP grants consent.
  - Collection is off for users under 18.
  - Events come from a fixed allow-list in `core/analytics/Events.kt`, and every parameter is an enum. The allow-list:

    | Event | Parameters |
    |---|---|
    | `onboarding_complete` | lang, tradition |
    | `feature_open` | feature_id from PRODUCT.md IDs |
    | `share_card` | card_type, target |
    | `reward_unlock` | unlock_type, result |
    | `report_export` | report_id |
    | `bond_created` | bond_type |
    | `notif_opt_in` | category |
    | `plan_view` / `plan_purchase` | plan_type |
    | `should_i_query` | intent_id |
    | `travel_mode_update` | — |

  - Screen views are automatic, using route class names.
- **Firebase Performance Monitoring:**
  - The automatic startup trace.
  - Custom traces: `panchang_compute`, `month_compute`, `report_render`, `card_render`.
  - URL patterns only, never queries.
- **Remote Config:**
  - Ad knobs (caps, placements on or off, the holdout percentage).
  - Feature flags (regions, AR, Wear).
  - Minimum fetch interval 12 h.
- **Play Console:**
  - Android vitals. Keep below the "bad behaviour" thresholds: user-perceived crash rate **< 1.09%**, ANR **< 0.47%**. Target < 0.3% and < 0.2%.
  - Pre-launch report.
  - Reviews in every language: reply within 48 h.
- **AdMob ↔ Firebase linking** gives ad revenue per user and ARPDAU by country.
- **Cloudflare:**
  - Web Analytics (cookieless) on the site.
  - Workers observability logs for the AI proxy and teaser. Metadata only, never request bodies.
- **Weekly KPI review** (`docs/KPI.md`):
  - DAU and MAU.
  - D1, D7 and D30 retention.
  - Organic share of installs.
  - Shares per DAU and bond invites.
  - ARPDAU (IN vs diaspora).
  - Rewarded opt-in.
  - Plan conversion.
  - Crash and ANR rates.
  - Top 5 review themes.

## E. Release to production (Play)
1. **Build:**
   - AAB with R8, resource shrinking and Baseline Profiles.
   - `versionCode = yyMMddNN`.
   - Enroll in Play App Signing; the upload key stays with the human.
2. **Create the app in Play Console** (the human presses Create). Settings:
   - App, Free, default language English (en-IN); then add hi, mr and gu listings.
3. **App content declarations:**

   | Declaration | Answer |
   |---|---|
   | Privacy policy URL | `https://<DOMAIN>/privacy` |
   | Ads | Yes |
   | App access | No login, so all features are accessible |
   | Content rating (IARC) | Answer honestly: no violence, no gambling, user-to-user sharing = limited |
   | Target audience | **18+** |
   | Data safety | Per SECURITY.md (AdMob, Firebase; optional location; on-device data; delete in the app) |
   | Advertising ID | Yes, for ads |
   | Government / financial / health / news | No |

4. **Store listing per language:**
   - Title ≤ 30 chars, short description ≤ 80, full description ≤ 4,000.
   - Icon 512×512, feature graphic 1024×500, 4–8 phone screenshots (1080×1920; reuse the contact-sheet style with real data).
   - Copy follows the tone rules. Never claim "100% accurate predictions"; claims like "precise astronomical calculations" are fine.
5. **Testing tracks:**
   - Internal testing (up to 100 testers), then **closed testing**.
   - A personal account created after 13 Nov 2023 needs **≥ 12 testers continuously opted in for 14 days** before it can apply for production. Internal testing doesn't count.
   - Then apply for production access (review is usually ≤ 7 days, not guaranteed).
   - Organisation accounts are exempt.
   - Recruit testers from family and community WhatsApp groups early.
6. **Production:**
   - Staged rollout: 10% → 50% → 100%, watching vitals and Crashlytics at each step.
   - In-app updates (flexible).
   - In-app review prompt after day 7, with no incentives.
7. **After publishing:**
   - Link AdMob to the Play app and verify `app-ads.txt`.
   - Recheck the Data safety form after each SDK change.
8. **Automation:**
   - Use Gradle Play Publisher with the service-account JSON for `publishBundle` to the internal and closed tracks.
   - Production promotion stays manual (the human presses Roll out).
