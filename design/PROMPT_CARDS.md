## CARD 0 · FOR YOU (the human): how to use these cards
1. **Prefer moving files over screenshots.** If your company policy allows any file transfer (personal email, Google Drive, a private GitHub repo, USB), move the `build-kit/` folder instead. It is more complete, and nothing gets lost to text recognition. These cards are the fallback.
2. **Set up the other laptop.** Install Android Studio and Claude Code, then create an empty folder `parivaar-panchang` and open Claude Code inside it.
3. **Pick the model.** Use `/model` to choose **Fable 5.2** with effort **high** for the setup cards.
4. **Send Cards 1–7.** Paste or attach Cards 1–7 **in order, in ONE message**. Attach the contact-sheet screenshots with Card 7.
5. **Check the docs it writes.** The AI creates `CLAUDE.md` and `docs/*.md`. Read them and correct anything it misread from the screenshots, especially numbers.
6. **Start building.** Send Card 8. From then on, run **one milestone per session**, use `/clear` between milestones, and commit after each one.
7. **Want to see the UI fast?** Send Card 9 after M00. It builds a clickable prototype with fake data that you can run in the emulator.

## CARD 1 · Role, product, non-negotiables
You are the founding engineer and designer for **"Parivaar Panchang"** (a working name). I will send Cards 1–7. **Do not write app code yet.** After Card 7, create `CLAUDE.md` plus `docs/PRODUCT.md`, `ENGINE.md`, `ARCHITECTURE.md`, `SECURITY.md`, `DESIGN.md`, `MONETIZATION.md`, `SOCIAL.md`, `I18N_CONTENT.md`, `PROMPTS.md` and `DECISIONS.md`. Write them as dense bullets with no fluff, then stop.

**PRODUCT:** an Android app combining Vedic astrology, a Hindu calendar (panchang) and devotion, in one place.
- **Model:** free, with ads, plus an optional "Parivaar Plus" plan with no ads.
- **Markets:** India first, plus the diaspora (US, UK, Canada, UAE, Australia, Singapore).
- **Languages:** en, hi, mr and gu in R1; bn, te, ta, kn, ml and or in R2; pa, as and ne in R3.
- **Constraints:** no human astrologers, no wallet, no accounts. Offline-first. Birth data never leaves the device.

**NON-NEGOTIABLES (put these in CLAUDE.md):**
1. **Accuracy beats everything.** Golden tests are mandatory, and tolerances are never loosened to make a test pass.
2. **Privacy.** No personal data in logs, analytics, crash reports, URLs, file names or notifications.
3. **No ads in sacred zones:** aarti, jap, mantra, Gita and Chalisa reading, the puja timer, shraddha screens, and the whole first session.
4. **No fear language and no selling of remedies.** Every interpretation shows the footer "Traditional interpretation, not a scientific prediction."
5. **Licences.** No AGPL or GPL code. **NO Swiss Ephemeris** or any library built on it. Never copy text, translations or art from other apps or books.
6. **`:engine` is pure Kotlin Multiplatform** and has zero Android imports.
7. **All UI text lives in resources or content packs.** Every new permission must be documented in `SECURITY.md`.

**Working style (token-lean):**
- One milestone per session.
- Read only the doc sections a task names.
- Start with a short plan, then implement, then verify with tests.
- End each session with a 5-line summary.
- When something is ambiguous, choose the more private, more accurate, fewer-ads option and log it in `DECISIONS.md`.

## CARD 2 · Features (PRODUCT.md)
**Navigation:** five tabs (Aaj, Calendar, Kundli, Bonds, Bhakti) plus a global search bar.

**Gating key:** F = free · N = free with one native ad below the fold · R = unlocked by one rewarded ad · P = Plan cosmetic perk.

**AAJ (today)**
- The five limbs (tithi, nakshatra, yoga, karana, vaar) with end times, plus paksha, masa (amanta/purnimanta), samvat, ritu and ayana.
- Sun and Moon rise/set; Moon phase.
- Inauspicious windows: Rahu kaal, Yamaganda, Gulika, Durmuhurta, Varjyam, Bhadra, Panchak, with a live countdown.
- Auspicious windows: Abhijit, Brahma muhurta, Amrit kaal.
- Choghadiya, Hora, Gowri / Nalla neram.
- Tarabala and chandrabala.
- **Chandrashtama alerts for each family member:** the Moon in the 8th house from that person's natal Moon.
- **Answer-first header:** one plain line such as "Good for new work until 2:40 PM", then the timings, then the full panchang (progressive disclosure).
- Rashifal by Moon sign (N); share card.

**CALENDAR**
- Month view.
- Festival and vrat engine, region- and tradition-aware, **computed for the user's own city worldwide**.
- Eclipses with local visibility and sutak.
- Regional calendars: Tamil, Malayalam, Bengali, Odia, Gujarati, Marathi, Telugu, Kannada.
- Date converter; yearly PDF (R); ICS export to Google Calendar.
- **Regional wall-calendar view**, in the style of a traditional printed almanac. It's our own design: never the "Kalnirnay" name or look.
  - Paper-style month grid in local script and numerals; Sundays in red; local tithi labels (शु./कृ., સુદ/વદ); local samvat; a monthly festivals list; a wall-PDF print.
  - Marathi: Champa Shashthi and the Margashirsha Thursday vrat. Gujarati: a choghadiya strip.
- **Tamil daily sheet (தினசரி காலண்டர்):** Nalla neram, Gowri, Rahu, Yamagandam, Kuligai, Soolam + parigaram, Chandrashtamam, Naal. Toggle: "printed style" fixed 6 AM times vs actual city sunrise (the default).

**MUHURAT:** a finder for marriage, griha pravesh, vehicle, namkaran and more, with a personal tara filter (R).
- **"Should I…?" (free, offline, rule-based, no AI):** covers about 30 intents (vehicle, travel, griha pravesh, new job, haircut and so on). Answer first: verdict, then the best window today, then an expandable "why". Voice input with a labelled mic.

**KUNDLI**
- Profiles for the user, family and friends.
- Chart styles: North, South and East.
- Vimshottari to 5 levels, Yogini.
- D1–D60, Chalit, KP, Ashtakavarga, Shadbala.
- Yogas and doshas, in neutral framing.
- Gochar; Varshphal (R3).
- **Family dates:** nakshatra birthdays, tithi birthdays and shraddha tithi, with yearly reminders.

**REPORTS:** PDF generated on the device, in every language.
- Basic, Life (60–120 pages), Career, Marriage, Year-ahead, Matching, Child, Numerology.
- Viewing in the app is F. Exporting the PDF is R.

**BONDS**
- Ashtakoota out of 36 and 10 Porutham, with Manglik checks.
- Friend Bonds via a privacy-safe Bond Code.
- Bond types (Love, Crush, Bestie, Sibling, Work) shown on five axes.
- Deep-dive (R); squad of 3–8 (R).
- Identity cards: yoni animal, gana, nadi, deity.
- Bond weather; Cosmic Wrapped in December; card themes (R/P).

**BHAKTI**
- Virtual mandir and aarti, using our own recordings.
- Naam-jap mala with haptics and streaks.
- Chalisa, Gita (Sanskrit + our own translations), stotras.
- Vrat tracker and kathas.
- Step-by-step puja vidhi with timers and a samagri checklist.
- **Live Sankalp generator** with today's panchang filled in.
- Greeting cards with the user's name; meditation timer.

**OTHER:** numerology, tarot (1909 public-domain art), baby names by nakshatra, Vastu basics.

**TOOLS:** planet positions, ephemeris, a Conventions screen, and **AR sky view** (R2).

**PLATFORM:**
- Glance widgets and a Wear OS tile.
- Rahu Kaal Quick Settings tile. Live Updates only when the user starts one (fast tracker, puja timer, eclipse tracker); an automatic countdown breaks Google's Live Updates policy.
- Read-aloud (TTS) in every language; "Bada Text" large-text mode.
- **Auto travel mode:** when the location or time zone changes, update every timing and show a note.
- **Proof panel:** show the engine, ayanamsa, sunrise convention and altitude used, and why results may differ from other apps.
- Encrypted backup; app lock; delete-all.

**AI (R3):** "Explain this" uses retrieval only. "Ask my chart" runs through a proxy, receives only computed facts, is labelled AI, and is capped at 3 per day (R).

**NOT BUILT:** live astrologers, a wallet, a remedy store, a social feed, contacts permission, login.

## CARD 3 · Engine part 1 (ENGINE.md)
**Ephemeris interface:** `geoApparent(body, JD_TT)` returns λ and β on the true ecliptic of date, plus distance and dλ/dt.

**Backends:**
- **Base:** Astronomy Engine, `io.github.cosinekitty:astronomy` (MIT). Pure Kotlin, no data files.
- **Precision:** a JPL DE440 excerpt covering 1900–2100. NAIF rules allow commercial use with no fees. Rename the modified kernel and give attribution. Size is about 14 MB.
  - Build it with `jplephem excerpt` (MIT). Keep segments 1–6, 10, 3 (EMB) and 301 (Moon wrt EMB).
  - Read it with our own ~300-line Chebyshev reader, ported from jplephem's logic.
  - Conversions: Moon_geo = (Moon wrt EMB)·(1 + 1/EMRAT); Earth = EMB − Moon_geo/(1 + EMRAT).
  - Then apply light-time, aberration, IAU 2006 precession and IAU 2000B nutation (port from ERFA, BSD-3).
  - Ship the file as a Play Asset Delivery **fast-follow** pack with a SHA-256 check.
- **Selection:** use Precision when the pack is present and the date is inside its range; otherwise Base. The UI shows which backend was used.

**FORBIDDEN:** Swiss Ephemeris and anything built on it (VedAstro, PyJHora, swisseph-based drik-panchanga), Moshier code, GPL ephemeris readers, Vedaksha.

**TIME**
- UTC → TAI via the leap-second table; TT = TAI + 32.184 s.
- ΔT from the Espenak–Meeus polynomials, updatable through a signed pack.
- GMST from IAU 2006; GAST = GMST + the equation of the equinoxes.

**SUNRISE:** use the altitude h0 that matches the chosen convention.
- Default UPPER_LIMB_REFRACTED: h0 = −50′.
- CENTER_REFRACTED: h0 = −34′.
- CENTER_GEOMETRIC: h0 = 0.
- Search: bracket every 10 minutes, then refine with Brent's method to 0.5 s.
- Above 60° latitude, apply a fallback rule and show a note.

**LAHIRI (IAE):** 23°15′00.658″ **true** at JD 2435553.5 (21 Mar 1956, 0h TT).
- A_mean(t) = (23°15′00.658″ − Δψ(t0)) + pA(t) − pA(t0), where Δψ(t0) ≈ +16.79″.
- A_true = A_mean + Δψ(t).
- pA = 5029.0966·T + 1.11113·T² − 0.000006·T³ (arcseconds; T in Julian centuries from J2000).
- Sidereal λ = λ_true − A_true.
- **Golden values (tolerance ≤ 0.1″):**
  - 2019-01-01 true: 24°07′05.986″
  - 2020-01-01 true: 24°07′54.841″
  - 1989-01-01 mean: 23°42′12.34″
  - 1990-01-01 mean: 23°43′02.62″
- **Other ayanamsas:** TRUE_CHITRA fixes Spica at exactly 180°. KP, Raman and Yukteshwar come from their epoch values and must be VERIFIED with fixtures.

**NODES:** mean Ω = 125.0445479 − 1934.1362891·T + 0.0020754·T² + T³/467441 − T⁴/60616000 (degrees). True node comes from the cross product of the Moon's position and velocity vectors. Default to true.

**ASCENDANT:** λ = atan2(cos θ, −(sin θ·cos ε + tan φ·sin ε)), where θ is local apparent sidereal time.

**HOUSES:** whole-sign (default), equal, Sripati, Placidus (used for KP).

**TOLERANCES** (Precision / Base):
| Quantity | Precision | Base |
|---|---|---|
| Sun / Moon position | 0.05″ | Sun 20″, Moon 10″ |
| Limb end times | 5 s | 30 s typical, 120 s worst |
| Rise / set | 5 s | 10 s |
| Dasha dates | exact | exact |

## CARD 4 · Engine part 2: panchang, calendar, validation
**LIMBS**
- Tithi = ⌊(λ☾ − λ☉)/12°⌋ + 1. Tropical; no ayanamsa needed.
- Nakshatra = ⌊λ☾_sid / 13°20′⌋; each pada is 3°20′.
- Yoga = ⌊(λ☾_sid + λ☉_sid) / 13°20′⌋.
- Karana: 60 per lunar month.
  - #1 is Kimstughna.
  - #2–57 cycle through Bava, Balava, Kaulava, Taitila, Gara, Vanija, Vishti.
  - #58–60 are Shakuni, Chatushpada, Naga.
- End times: sample every 2 h, then use Brent's method to 1 s.
- Mark kshaya and adhika cases. The limb prevailing at sunrise names the day.

**RAHU / YAMAGANDA / GULIKA:** split sunrise→sunset into 8 equal parts and take the part number for the weekday.
| Window | Sun | Mon | Tue | Wed | Thu | Fri | Sat |
|---|---|---|---|---|---|---|---|
| Rahu kaal | 8 | 2 | 7 | 5 | 6 | 4 | 3 |
| Yamaganda | 5 | 4 | 3 | 2 | 1 | 7 | 6 |
| Gulika | 7 | 6 | 5 | 4 | 3 | 2 | 1 |

**MUHURTA:** split the day into 15 parts and the night into 15 parts.
- Abhijit is day part 8.
- Brahma muhurta is night part 14.
- Durmuhurta, Varjyam, Amrit kaal and Gowri come from rule JSON files and must be VERIFIED.

**CHOGHADIYA**
- Lords: Udveg = Sun, Char = Venus, Labh = Mercury, Amrit = Moon, Kaal = Saturn, Shubh = Jupiter, Rog = Mars.
- The day sequence starts with the weekday lord and cycles Udveg → Char → Labh → Amrit → Kaal → Shubh → Rog.
- Night sequences live in JSON. All 14 sequences are fixtures (VERIFY).

**HORA:** 12 day horas plus 12 night horas. The first hora belongs to the weekday lord, then the Chaldean order: Sun, Venus, Mercury, Moon, Saturn, Jupiter, Mars.

**PANCHAK:** when the Moon's sidereal longitude is 300–360°.

**TARA:** ((nakshatra_today − nakshatra_natal) mod 27) mod 9 + 1.

**VIMSHOTTARI:** Ketu 7, Venus 20, Sun 6, Moon 10, Mars 7, Rahu 18, Jupiter 16, Saturn 19, Mercury 17 (years; 120 total).
- Year length 365.25 days by default; 360 is an option.
- The starting balance is the REMAINING (unelapsed) fraction of the Moon's nakshatra × the lord's years.

**MONTHS:** amanta or purnimanta.
- A month with no sankranti is adhika; a month with two is kshaya.
- Samvatsara follows the 60-year cycle; VS = Shaka + 135.

**SOLAR CALENDAR RULES**
- Tamil: if sankranti is before sunset, the month starts that day; otherwise the next day.
- Malayalam: if before the end of aparahna (3/5 of daylight), that day; otherwise the next day.
- Bengali: sunrise to midnight means the next day; midnight to sunrise means the day after that.
- Odia: the same day.

**FESTIVAL DSL** (JSON files, one per region). Rule types:
- `tithi_at` with kala = sunrise, pradosh, nishita, aparahna, madhyahna, moonrise or arunodaya
- `nakshatra_in_solar_month`
- `sankranti`
- `weekday_tithi_combo`
- `ekadashi` (smarta or vaishnava)
- `fixed_gregorian`

**EKADASHI**
- Smarta: the day Ekadashi prevails at sunrise.
- Vaishnava: skip the day if Dashami touches arunodaya (the 96 minutes before sunrise).
- Parana: after Hari vasara and before Dwadashi ends (VERIFY).

**PRINTED-STYLE MODE:** Tamil printed calendars use fixed times on a 6:00 AM basis. Rahu kalam: Sun 4:30, Mon 7:30, Tue 3:00, Wed 12:00, Thu 1:30, Fri 10:30, Sat 9:00 (90 min each). Offer it as an option; actual sunrise stays the default. Soolam/parigaram: Sun W/jaggery, Mon E/curd, Tue N/milk, Wed N/milk, Thu S/oil, Fri W/jaggery, Sat E/curd (VERIFY).

**ECLIPSE:** apply sutak only if the eclipse is visible locally: 12 h for a solar eclipse, 9 h for a lunar eclipse.

**FAMILY DATES**
- Nakshatra birthday: in the birth solar or lunar month, the day the natal nakshatra prevails at sunrise.
- Shraddha: the tithi at aparahna in the month of death.

**TIME ZONES**
- Use the platform tzdata, plus an overlay covering:
  - Bombay Time +4:51 until 1955
  - Calcutta Time +5:53:20 until 1948
  - War time +6:30 in 1942–45
  - Pre-1947 Karachi, Lahore and Dhaka
- Always show the offset in use, and allow a manual override.

**PLACES:** GeoNames cities5000 (CC-BY attribution) in SQLite FTS, at most 6 MB.

**VALIDATION**
- `tools/oracle` (Skyfield + DE440, MIT) generates:
  - 10,000 position vectors
  - rise/set for 10 cities over 400 days
  - limb end times for 10 cities over 2 years
- Spot-check against JPL Horizons. Hand-check against the Rashtriya Panchang / IAE.
- Festival fixtures for 2024–2030 in Delhi, Mumbai, Chennai, Kolkata, New York, London and Sydney, signed off by a pandit.
- CI fails on any change to a festival date.

## CARD 5 · Architecture and security
**STACK** (verify the latest stable versions and pin them in the version catalog)
| Area | Choice |
|---|---|
| Language and build | Kotlin 2.4, AGP 9.5 |
| SDK levels | minSdk 24, targetSdk 36 |
| UI | Compose BOM 2026.09, Material 3 1.4 → 1.5 (Expressive), material3-adaptive, Navigation 3 |
| DI and async | Hilt, Coroutines/Flow |
| Serialization and time | kotlinx.serialization (JSON + CBOR), kotlinx-datetime 0.8 |
| Storage | **Room 3 + SQLCipher** 4.20; DataStore 1.3 + datastore-tink; Tink 1.24 |
| Background and widgets | WorkManager 2.12, Glance 1.2 |
| Billing and ads | Play Billing 8+; Google Mobile Ads + mediation; UMP 4.0 |
| Telemetry and backup | Firebase Crashlytics (Analytics optional, Ad ID off); Play Integrity 1.6 (AI proxy only); Block Store 16.4 |
| Performance | Baseline Profiles; R8 full mode |
| Testing | JUnit/kotlin-test, Turbine, Robolectric, Roborazzi, Macrobenchmark |
| CI | GitHub Actions + detekt + OSV-Scanner + CycloneDX SBOM |

**MODULES**
- `:engine`: KMP, pure Kotlin. Sub-packages `astro`, `jyotish`, `panchang`, `calendar`, `match`, `numerology`.
- `:engine-golden`
- `:content`: signed content packs, term resolver, templates.
- Core modules: `:core:model`, `:core:data`, `:core:geo`, `:core:designsystem`, `:core:ads`, `:core:billing`, `:core:share`, `:core:pdf`, `:core:notify`, `:core:analytics`.
- Feature modules: `:feature:{onboarding, aaj, calendar, muhurat, kundli, reports, bonds, bhakti, horoscope, tools, settings}`.
- `:widget`, `:wear` (R2), `:app`.
- `:web` (R2): static SEO site generated from `:engine`.
- `worker/` (R3): Cloudflare Worker AI proxy.
- Features never depend on other features.

**SECURITY**
- **Database key.** Generate a 32-byte random passphrase. Wrap it with an AES-GCM key held in Android Keystore (use StrongBox if the device has it). Store the wrapped key in `noBackupFilesDir`.
- **Do not use `androidx.security:security-crypto`.** It is deprecated.
- **Backup.**
  - Exclude the database and its key in `dataExtractionRules` and `fullBackupContent`.
  - Back up only `snapshot.bin`, encrypted with Tink AEAD under a key K_b. Set `disableIfNoEncryptionCapabilities=true`.
  - Store K_b in Block Store with cloud backup, but only if end-to-end encryption is available.
  - On restore, re-key everything.
  - Fallback: an encrypted export file through SAF, protected by a passphrase (PBKDF2 600k iterations or Argon2id).
- **Network.** Disable cleartext and enable `certificateTransparency`. Don't pin certificates. The only endpoints are R2 content packs, Remote Config and the AI proxy.
- **AI proxy.** Validate a Play Integrity standard token with `requestHash = SHA-256(body)`. Enforce per-install caps and log no request bodies. Send only computed chart facts, never names, places or birth dates.
- **Components.** Set `exported` explicitly on every component. Use `intentMatchingFlags=enforceIntentFilter`. Validate deep links against an allow-list. Use no WebView; use Custom Tabs instead.
- **Permissions.**
  - Allowed: notifications (requested in context), optional coarse location, Photo Picker.
  - Not allowed: contacts, SMS or phone.
  - Exact alarms only when the user turns them on; otherwise windowed alarms.
- **Content packs.** Sign them with Ed25519 and pin the public key in the app.
- **Privacy UI.** App lock with biometrics, a FLAG_SECURE privacy mode, and lock-screen notifications set to PRIVATE.
- **Minors.** Derive age from the user's own profile. Under 18: non-personalized ads only, analytics off, AI off.
- **DPDP (from 13 May 2027).** Show notice and get consent in every language. Provide export and delete-all. Keep a breach runbook.
- **CI.** Crypto round-trip tests, restore on a simulated new device, rejection of tampered packs or bond codes, a manifest audit script, and a MobSF scan before each release.

## CARD 6 · Monetization and social
**ADS**
- **Stack.** Google Mobile Ads with mediation through MAX or AdMob. Bidders: InMobi, Meta, Pangle, Unity, Liftoff.
- **Content settings.** `maxAdContentRating = PG`.
  - Block: gambling, alcohol, dating, get-rich-quick, sexual content, sensationalism, social casino.
  - Allow religion.
  - Block fear-selling astrology advertisers by URL. AdMob dropped the "Astrology" category on 23 Oct 2026, so it can no longer be blocked as a category.
- **Native:** one per screen, placed below the fold.
- **Banner:** calendar and tools screens only.
- **Interstitial:** only at natural breaks, at least 3 minutes apart, at most 3 a day, none on day 1.
- **Rewarded:** at most 6 a day. Use server-side verification (SSV). If an ad fails to load, grant the reward anyway.
- **App-open:** only from the 3rd session on, at most 1 a day, never when the app is opened from a notification or widget.
- **Rewarded unlocks:**
  - PDF export
  - bond deep-dive for 7 days
  - squad
  - personal muhurat filter
  - card theme for 24 hours
  - yearly PDF
  - one AI question
  - "calm mode": no interstitials for 3 hours
- **Ad-free offer.** Every "Watch ad" button sits next to "Go ad-free ₹49".

**PLAN "Parivaar Plus"**
- Removes all ads, keeps every unlock on, includes all themes, and gives 10 AI questions a day.
- Options: monthly, yearly, India prepaid passes, and lifetime.
- Set prices per country through the Play API from `play/prices.json`.

| Market | Monthly | Yearly | Lifetime |
|---|---|---|---|
| India | ₹49 (prepaid ₹59) | ₹399 (prepaid ₹449) | ₹999 |
| US | $1.99 | $11.99 | $29.99 |
| UK | £1.49 | £9.99 | £24.99 |
| Canada | CA$2.49 | CA$14.99 | CA$34.99 |
| Australia | A$2.99 | A$17.99 | A$39.99 |
| UAE | AED 6.99 | AED 44.99 | AED 109.99 |
| Eurozone | €1.99 | €11.99 | €27.99 |
| Singapore | S$2.98 | S$15.98 | S$38.98 |
| Malaysia | RM4.90 | RM29.90 | RM69.90 |
| Nepal / Bangladesh / Sri Lanka | ~$0.99 | ~$4.99 | ~$11.99 |

- **Offers:** a 7-day yearly trial in tier-1 countries, a Navratri–Diwali offer at 30% off yearly, and a referral reward of 7 ad-free days once the invited friend has been active for 3 days.
- **Never** reward ratings.

**BOND CODE (no server)**
- Contents (traits only): `{v, name or initials, Moon nakshatra 0–26, pada 1–4, Moon rashi, lagna (optional), Manglik 0/1/2, Venus sign, Sun sign}`.
- Encoding: CBOR, then base64url, plus a CRC-8. About 20 characters.
- Delivery: a link of the form `https://<domain>/b#<code>`. The fragment never reaches the server. Use App Links with `autoVerify`.
- The share screen shows exactly what will be shared and lets the user drop the lagna.
- **Never share** a birth date, birth time, place, or a profile the user entered manually.

**SCORING:** Ashtakoota (Varna 1, Vashya 2, Tara 3, Yoni 4, Graha Maitri 5, Gana 6, Bhakoot 7, Nadi 8). Per-type weights map onto five axes: Vibe, Talk, Trust, Fights, Growth. Friend bonds ignore Nadi and Bhakoot.
- Labels: Cosmic Twins, Soul Squad, Spicy Combo, Growth Bond, Opposites Attract. There is no negative label.

**SHARE CARDS**
- Sizes: 1080×1920 and 1080×1080, rendered on the device, with EXIF stripped, a wordmark and a short link.
- WhatsApp: `ACTION_SEND` with `setPackage com.whatsapp`, then `w4b`, then the system chooser.
- Instagram: `com.instagram.share.ADD_TO_STORY` with a Facebook App ID and a FileProvider URI.
- Do **not** use Firebase Dynamic Links (shut down in 2025). Use App Links + Install Referrer.

## CARD 7 · Design, languages, reports
**DESIGN THEME:** "Sacred, modern, calm." I have attached the contact-sheet screenshots; treat them as the visual reference.
- **Light theme ("Prabhat"):** saffron #E8772E (primary), temple maroon #8C2F39, peacock teal #1F7A74 (auspicious), ivory #FFF8EE (surface), gold #C9A227 (accent).
- **Dark theme ("Ratri"):** indigo night #121331 (surface), marigold #FFB25B (primary), rose #F2A7B0, teal #7FD6CC, gold #E7C85A.
- Never rely on color alone. Contrast must meet WCAG AA.
- **Fonts (all OFL):**
  - Noto Sans plus the Noto Indic families for UI text.
  - Fraunces for display text.
  - Tiro Devanagari Sanskrit for shlokas.
  - Body text at least 16sp. Support font scaling up to 200% with no clipping. Never fix the height of text containers.
- **Look and motion:**
  - Material 3 Expressive, 20–28dp rounded cards.
  - Spring motion that stays gentle on sacred screens.
  - Animations built in Compose only (no Lottie): diya flicker, mala beads, sun arc, bond orbit.
  - Haptics: a tick for each bead and a double tick at 108.
- **Components:**
  - TimeWindowCard (with a countdown ring)
  - DayArc
  - PanchangGrid
  - ChartView in North, South and East styles (Canvas, accessible)
  - DashaTimeline
  - BondCard
  - ShareCardFrame
  - RewardedUnlockButton
  - ExplainSheet (plain meaning, "how calculated", read-aloud)
- **Layout:** edge-to-edge, predictive back, NavigationSuiteScaffold and ListDetailPaneScaffold.
- **Onboarding:** under 60 seconds. Steps: language, then city, then tradition, then optional birth details, then reminders.
- **Budgets:**
  - Cold start ≤ 1.2 s on mid-range devices and ≤ 2 s on 3 GB devices.
  - Base AAB ≤ 20 MB.
  - The Aaj screen renders in ≤ 150 ms from local computation.
- **Art:** deity art must be originally commissioned. No AI-generated deity images, and never place deity images next to ads.

**LANGUAGES**
- UI strings go in `strings.xml`, with the per-app language picker.
- Interpretations ship in signed CBOR content packs per language.
- Terms tables (`terms/<lang>.json`) hold the native names of tithis, nakshatras, yogas, karanas, masas, rashis, grahas and festivals. They are entered by a native expert and then locked.
- Users can choose native or Latin digits.

**CONTENT** (about 160k words per language)
- Planet in sign and house, lords, lagna, nakshatras and padas, dashas, yogas, gochar phrases, kootas, festival guides and kathas.
- Master copies are written in English and Hindi by a hired jyotish writer.
- Translation runs through the Claude Batch API: Haiku 5.5 for bulk text, Sonnet 5.5 for kathas. Inputs are the glossary, a style guide and a do-not-translate list.
- A native reviewer then checks 100% of the output.

**REPORTS (PDF)**
- Rendered with `android.graphics.pdf.PdfDocument` + `StaticLayout`, which shapes Indic text correctly. **Do not use iText or PdfBox.**
- Embed subset Noto fonts. Page size A4. Include a table of contents and vector charts.
- Generate in a WorkManager job with a progress indicator.
- Performance: 100 pages in under 8 s, file under 6 MB.
- Known limitation: copying or searching Indic text from the PDF may be imperfect.
- **Public-domain texts:**
  - Gita: Sanskrit from the `gita/gita` dataset (Unlicense); translations by Arnold (1885), Besant (1895) and Telang (1882), or our own.
  - Chalisa and aarti lyrics are traditional and public domain.
  - Audio must be our own recordings. No label recordings.

## CARD 8 · Build order and model routing (start building)
Create the docs from Cards 1–7, then run the milestones below. Do **one milestone per session**, `/clear` between them, and commit after each one.

**MODELS**
- **FABLE 5.2** (effort high): the engine, security work, the release audit and the AI proxy.
- **OPUS 5.5** (effort set to high; the default is medium): features and UI.
- **SONNET 5.5:** simple screens and localization plumbing.
- **HAIKU 5.5:** bulk translation through the Batch API.

**MILESTONES**
| ID | Milestone | Model | Done when |
|---|---|---|---|
| M00 | Bootstrap: modules, catalog, detekt (forbid `android.*` in `:engine` and forbid `security-crypto`), CI, verification metadata | OPUS | CI is green |
| M01 | Astronomy core: time, ΔT, both backends, rise/set, `tools/oracle` with Skyfield | FABLE | Golden tests pass |
| M02 | Jyotish core: ayanamsa, houses, nakshatra, vargas, dashas, yogas DSL | FABLE | Golden tests pass |
| M03 | Panchang: limbs, all windows, choghadiya, hora | FABLE | Results within ±60 s of reference |
| M04 | Calendar and festival DSL, regional calendars, Ekadashi, eclipses, family dates (effort xhigh) | FABLE | Festival fixtures for 2024–30 pass |
| M05 | Geo database and timezone overlay | OPUS | — |
| M06 | Secure data layer and backup/restore | FABLE | — |
| M07 | Design system, app shell, onboarding, Conventions screen | OPUS | — |
| M08 | Aaj screen (answer-first header), widgets, Quick Settings tile, notifications, chandrashtama alerts, travel mode, read-aloud (check TTS availability at runtime) | OPUS | — |
| M09 | Calendar screens, ICS export (share-sheet file, no permission), "Should I…?" engine and screen | OPUS | — |
| M10 | Kundli and family dates | OPUS | — |
| M11 | Content packs and rashifal engine | OPUS | — |
| M12 | Bonds and sharing | OPUS | — |
| M13 | Bhakti, including the Sankalp generator | SONNET | — |
| M14 | PDF reports | OPUS | — |
| M15 | Ads, consent and billing, followed by a policy review | OPUS, then FABLE | — |
| M16 | Localization pipeline | SONNET | — |
| M17 | Performance and accessibility | OPUS | — |
| M18 | Release hardening audit (MASVS), data safety form, privacy policy draft | FABLE | — |

**R2:** muhurat, advanced charts (D4–D60, Ashtakavarga, Shadbala, KP), remaining regions, the full set of reports, numerology/tarot/names/vastu, the SEO web generator, deep-dive/squad/Wrapped, AR sky view (rotation-vector sensor, accuracy cone), the Wear OS tile and widget (Tiles 1.6), user-started Live Updates, a personalised wall-calendar PDF, and an open-source `:engine` (Apache-2.0).

**R3:** the AI proxy (Haiku 5.5 with prompt caching), Varshphal, a Western chart, and iOS via KMP.

**Definition of done for every milestone:**
- The build passes.
- Tests pass.
- lint and detekt are clean.
- The acceptance criteria are met.
- You write a 5-line summary.
- You log TODO(owner) items, such as pandit review, native-language review, legal review and art.

**Now start M00.**

## CARD 9 · Quick clickable UI prototype (to review the design)
Using the attached contact-sheet screenshots and DESIGN.md, build a **`prototype` product flavor** of `:app` with **fake, hard-coded sample data**. No engine and no ads.

**Screens:**
- Onboarding (language)
- "Should I…?" answer screen
- Aaj in both light and dark themes, with a live countdown ring and the DayArc
- Calendar month, with a day sheet
- Kundli, with a North-style chart (Canvas)
- Parivaar family dates
- Report screen, with the rewarded-unlock bottom sheet
- Bond result, with the orbit animation
- Story share card preview
- Nakshatra identity card
- Aarti (dark, with diya flicker)
- Naam-jap mala (tap to advance a bead, with haptics)
- Sankalp
- Explain sheet

**Requirements:**
- A bottom navigation bar with 5 tabs.
- A theme toggle in the top bar.
- A Bada Text toggle.
- Hindi and English strings.

**Accept when:** `./gradlew :app:installPrototypeDebug` runs in the emulator, all screens can be reached, and Roborazzi screenshots of every screen are saved to `design/screens/`.

**Model:** OPUS 5.5, effort high.
