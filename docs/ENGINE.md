# ENGINE.md: Calculation engine spec (free, commercial-safe, no Swiss Ephemeris)

`:engine` is pure Kotlin Multiplatform. Every public function is deterministic:
`(Instant, Place, CalcSettings) → result`.

Anything marked **VERIFY** must be checked against fixtures or sign-off from the owner/pandit before it ships.

## §1 Accuracy targets
| Quantity | Target (Precision backend) | Target (Base backend) |
|---|---|---|
| Sun/Moon apparent ecliptic longitude | ≤ 0.05″ vs oracle | Moon ≤ 10″, Sun ≤ 20″ |
| Planets | ≤ 0.1″ | ≤ 60″ |
| Tithi/nakshatra/yoga/karana end times | ≤ 5 s | ≤ 30 s typical, ≤ 120 s worst case |
| Sunrise/sunset (same convention as oracle) | ≤ 5 s | ≤ 10 s |
| Lahiri ayanamsa | ≤ 0.1″ vs IAE reference values | same |
| Dasha dates | exact (to the minute) under the chosen year length | same |

Published panchangs disagree with each other by about a minute because of sunrise conventions. Report and document these differences; don't chase them.

## §2 Ephemeris backends (licences VERIFIED 13 Nov 2026)
```kotlin
interface Ephemeris {
    fun geoApparent(body: Body, tt: JulianDay): EclipticPos   // λ, β (true ecliptic of date), distance, dλ/dt
    val validRange: ClosedRange<JulianDay>
}
```

### Base backend: Astronomy Engine
- Package: `io.github.cosinekitty:astronomy` 2.1.x, by Don Cross. **MIT licence.**
- Pure Kotlin/JVM, with no data files. It uses truncated VSOP87 for the planets and the 1954 Brown lunar theory for the Moon.
- Use it in `jvmMain`/`androidMain`. For iOS, either vendor its source into `commonMain` (MIT allows this; keep the notice), or use the Precision backend only.
- Also use it for:
  - eclipse search: `searchLunarEclipse`, `searchLocalSolarEclipse`
  - moon phases: `searchMoonPhase`
  - rise/set with a custom altitude

### Precision backend: JPL DE440 excerpt (our own code, in `commonMain`)
- **Licence.** NASA's NAIF rules allow commercial use with no fees. A modified kernel must be renamed and must carry its own attribution.
- **Building the data file** (`tools/oracle/make_excerpt.py`):
  1. Run `python -m jplephem excerpt 1899/12/1 2100/2/1 de440.bsp` (jplephem is MIT).
  2. Keep segments 1–6 (barycentres), 10 (Sun), 3 (EMB) and 301 (Moon wrt EMB).
  3. Convert to a compact binary file, `pp-de440-1900-2100.bin`, holding float64 Chebyshev coefficients. Expect about 14 MB.
- **Reader.** Write a Chebyshev reader of about 300 lines, porting the jplephem logic.
  - Geocentric Moon = (Moon wrt EMB) · (1 + 1/EMRAT).
  - Earth = EMB − Moon_geo / (1 + EMRAT).
  - Then apply light-time, annual aberration, precession (IAU 2006, P03), nutation (IAU 2000B, ported from ERFA under BSD-3; keep the notice) and the frame rotation to the true ecliptic of date.
- **Delivery.** Ship the file as a Play Asset Delivery *fast-follow* pack and verify it on load with a SHA-256 check.

### Backend selection
1. Use the Precision backend when the pack is present and the date falls inside 1900–2100.
2. Otherwise use the Base backend.
3. The UI's "How calculated" sheet names the backend that was used.

### Forbidden
Never add any of these: Swiss Ephemeris or its ports (VedAstro, PyJHora, any drik-panchanga that depends on swisseph), Moshier code (no licence), GPL ephemeris readers (Project Pluto, JDEread, Liu's ElpMpp02), or Vedaksha (BUSL licence).

### Reference only (for reading the logic; never copy the code)
These are MIT-licensed:
- SODIYAL/panchanga (TypeScript, built on Astronomy Engine)
- Kalagana (Python)
- soniakeys/meeus (Go)

## §3 Time, ΔT, rise/set
- **Time scales.**
  - UTC → TAI uses the leap-second table. TT = TAI + 32.184 s.
  - UT1 ≈ UTC + DUT1, which is ignored (under 0.9 s).
  - ΔT before 1972 and for future dates comes from the Espenak–Meeus polynomials.
  - The ΔT and leap-second tables are updatable through a signed content pack.
- **Sidereal time.** GMST follows IAU 2006 (ERA-based). GAST = GMST + equation of the equinoxes.
- **Rise/set.** Find when the body's apparent altitude equals h0. Bracket the crossing by sampling every 10 min, then refine with Brent's method to 0.5 s.
- **Sunrise convention** (`CalcSettings.sunrise`):
  - `UPPER_LIMB_REFRACTED` (default): h0 = −50′ (34′ refraction + 16′ semidiameter). Matches the current Drik Panchang default.
  - `CENTER_REFRACTED`: h0 = −34′.
  - `CENTER_GEOMETRIC` (traditional "Hindu rising"): h0 = 0, with no refraction.
  - Altitude, pressure and temperature are optional; the default is sea level, 1010 hPa and 10 °C.
- **Moonrise.** Use the Moon's own semidiameter and horizontal parallax (topocentric).
- **Polar and high latitudes** (above 60°, relevant for Canadian and Nordic diaspora users): when the Sun never rises or never sets, return `NoEvent` and fall back to the convention set in `CalcSettings.highLatitudeRule`. The options are nearest-day or local-noon ± 6 h, and the screen shows a note. **VERIFY** this with the pandit.

## §4 Ayanamsa and sidereal positions
- **Lahiri (default)** follows the IAE definition: **23°15′00.658″ true at 1956-03-21 0h TT (JD 2435553.5)**.
  ```
  A_mean(t) = (23°15′00.658″ − Δψ(t0)) + pA(t) − pA(t0)    // Δψ(t0) ≈ +16.79″
  A_true(t) = A_mean(t) + Δψ(t)
  pA (IAU 1976, matches IAE best) = 5029.0966 T + 1.11113 T² − 0.000006 T³  [″], T = Julian centuries TT from J2000
  ```
- **Sidereal longitude** = λ_true_of_date − A_true. Nutation cancels out, so this equals λ_mean − A_mean.
- **Golden values** (from IAE, via the Swiss Ephemeris docs, Appendix E):

  | Date | Value |
  |---|---|
  | 2019-01-01 0h TT, true | 24°07′05.986″ |
  | 2020-01-01 0h TT, true | 24°07′54.841″ |
  | 1989-01-01, mean | 23°42′12.34″ |
  | 1990-01-01, mean | 23°43′02.62″ |

  Tolerance is ≤ 0.1″.
- **Other ayanamsas:**
  - `TRUE_CHITRA`: Spica's sidereal longitude is fixed at exactly 180° (computed from the star's catalogue position plus proper motion).
  - `KP_NEW`, `RAMAN`, `YUKTESHWAR`: defined by their published epoch values. **VERIFY** each against owner-supplied fixtures before enabling it.
- **Rahu/Ketu.**
  - Mean node (Meeus 47.7): Ω = 125.0445479 − 1934.1362891T + 0.0020754T² + T³/467441 − T⁴/60616000 (degrees).
  - True node: from the osculating orbit, using the cross product of the Moon's geocentric position and velocity vectors.
  - The setting `node = TRUE | MEAN` defaults to TRUE.
  - Ketu = Rahu + 180°.

## §5 Chart, houses, vargas, dashas, yogas
- **Ascendant.** λ_asc = atan2(cos θ, −(sin θ·cos ε + tan φ·sin ε)), where θ is the local apparent sidereal time, ε the true obliquity and φ the geographic latitude. Normalise the result and make it sidereal.
  - Fixtures must cover 8 quadrants and latitudes from −45° to 65°.
- **MC (midheaven).** Computed from θ and ε.
- **House systems:**
  - `WHOLE_SIGN` (default for D1 display in the North and South styles).
  - `EQUAL`.
  - `SRIPATI`: Porphyry divisions, with bhava madhya at the cusps and sandhi at the midpoints.
  - `PLACIDUS` (for KP): iterative semi-arc method, failing gracefully above about 66° latitude.
- **Nakshatra.** n = ⌊λ_sid / 13°20′⌋, and pada = ⌊(λ_sid mod 13°20′) / 3°20′⌋ + 1.
  - Lords repeat through Ketu, Venus, Sun, Moon, Mars, Rahu, Jupiter, Saturn, Mercury.
  - The traditional attributes table lives in `rules/nakshatra.json`: deity, gana, yoni, nadi, varna, vashya, symbol.
- **Vargas (D1–D60).** Use the Parashara rules, table-driven from `rules/vargas.json`.
  - D2 has a setting (Parashara hora or Kashinath); D3 has a setting (Parashara or Jagannath).
  - D60 needs birth time to within ±4 s per amsha, so show a sensitivity note.
- **Vimshottari dasha.**
  - Years per lord: Ketu 7, Venus 20, Sun 6, Moon 10, Mars 7, Rahu 18, Jupiter 16, Saturn 19, Mercury 17 (120 total).
  - The balance at birth is the unelapsed fraction of the Moon's nakshatra.
  - Sub-periods are proportional, down to 5 levels.
  - Year-length setting: `365.25` (default), `365.2425` or `360`.
- **Yogini dasha.** Eight yoginis lasting 1–8 years (36 years total). The start is the yogini at index (nakshatra number + 3) mod 8. **VERIFY** against fixtures.
- **Yogas and doshas.** Rules live in a data DSL in `rules/yogas.json`.
  - Conditions use predicates over ChartFacts: `house_of`, `lord_of`, `in_kendra`, `aspect`, `conjunct`, `exalted`, `own_sign`, and so on.
  - Manglik: Mars in houses 1, 2, 4, 7, 8 or 12, counted from lagna, Moon and Venus, with a cancellation list. The UI states which variant was used.
  - Every rule carries the IDs of its tradition source and its description content.
- **Ashtakavarga and Shadbala (R2).** Classical tables in `rules/`, verified against an owner-supplied fixture set.
- **KP (R2).**
  - Placidus cusps.
  - Star lord and sub lord from a 249-division table generated by code, proportional to Vimshottari years.
  - Significators levels 1–4, and ruling planets.

## §6 Panchang (see PRODUCT.md §A)
- **Five limbs:**
  - Tithi = ⌊(λ☾ − λ☉) / 12°⌋ + 1. This is tropical, so no ayanamsa is needed.
  - Yoga = ⌊(λ☾_sid + λ☉_sid) / 13°20′⌋.
  - Karana = half-tithi, 60 per month:
    - #1 = Kimstughna
    - #2–57 = Bava, Balava, Kaulava, Taitila, Gara, Vanija and Vishti, cycling
    - #58–60 = Shakuni, Chatushpada, Naga
- **End times.** Bracket each boundary by sampling every 2 h, then root-find with Brent's method to 1 s.
- **Day ownership.** Report every limb that starts or ends within sunrise-to-next-sunrise.
  - A **kshaya** limb starts and ends inside the day.
  - An **adhika** limb spans two sunrises.
  - The limb prevailing at sunrise names the day.
- **Day division.** Day = sunrise→sunset in 8 equal parts; night = sunset→next sunrise in 8 parts.
  - Weekday index (1-based part numbers):

    | Window | Sun | Mon | Tue | Wed | Thu | Fri | Sat |
    |---|---|---|---|---|---|---|---|
    | Rahu kaal | 8 | 2 | 7 | 5 | 6 | 4 | 3 |
    | Yamaganda | 5 | 4 | 3 | 2 | 1 | 7 | 6 |
    | Gulika | 7 | 6 | 5 | 4 | 3 | 2 | 1 |

  - Night Gulika/Rahu are optional. **VERIFY** them.
- **Muhurta.** Daytime is split into 15 muhurtas and nighttime into 15.
  - Abhijit = the 8th day muhurta. The Wednesday exception is a setting.
  - Brahma muhurta = the 14th night muhurta, which ends one muhurta before sunrise.
  - Durmuhurta indices per weekday are in `rules/durmuhurta.json`. **VERIFY**.
- **Choghadiya.** Eight parts by day and eight by night.
  - Names map to lords: Udveg (Sun), Char (Venus), Labh (Mercury), Amrit (Moon), Kaal (Saturn), Shubh (Jupiter), Rog (Mars).
  - The day sequence starts at the weekday lord and cycles Udveg→Char→Labh→Amrit→Kaal→Shubh→Rog.
  - Night sequences (one per weekday) are in `rules/choghadiya.json`.
  - All 14 sequences are fixtures. **VERIFY**.
- **Hora.** 12 day horas plus 12 night horas, each of unequal length.
  - The first hora belongs to the weekday lord.
  - They follow the Chaldean order: Sun→Venus→Mercury→Moon→Saturn→Jupiter→Mars.
- **Gowri panchangam / Nalla neram (Tamil).** Weekday tables in `rules/gowri.json`. **VERIFY** with the Tamil reviewer.
- **Varjyam and Amrit kaal.** Each nakshatra has a ghati offset and a duration of 4 ghatis, scaled to the actual nakshatra length. Tables are in `rules/varjyam.json`. **VERIFY**.
- **Panchak.** The Moon's sidereal longitude is in 300°–360°, that is, the Moon is in Kumbha or Meena.
  - Subtypes by weekday are in rules.
- **Bhadra.** Periods of the Vishti karana. The Moon's rashi gives the loka (Svarga, Patala or Prithvi), shown as a tradition note.
- **Printed-style timing mode** (`CalcSettings.timingStyle = ACTUAL_SUNRISE | PRINTED_6AM`).
  - Many Tamil and South Indian printed calendars give Rahu kalam, Yamagandam and Kuligai as fixed clock times on a 6:00–18:00 basis. Rahu kalam by weekday: Sun 4:30, Mon 7:30, Tue 3:00, Wed 12:00, Thu 1:30, Fri 10:30, Sat 9:00, each lasting 90 min.
  - Offer this mode for familiarity. Label it clearly and keep ACTUAL_SUNRISE as the accurate default. **VERIFY** the Yamagandam and Kuligai tables against printed calendars.
- **Tamil daily fields.**
  - Soolam and parigaram by weekday: Sun West/jaggery, Mon East/curd, Tue North/milk, Wed North/milk, Thu South/oil, Fri West/jaggery, Sat East/curd. **VERIFY** before shipping.
  - Naal (mel/keezh/sama nokku) comes from a nakshatra table.
  - Chandrashtamam lists the nakshatras of the natal rashi whose 8th house holds today's Moon.
- **Regional numerals and labels.**
  - Digits use ICU numbering systems: deva, gujr, tamldec, beng, telu, knda, mlym, orya, guru.
  - Tithi labels per locale, e.g. Marathi शु./कृ. and Gujarati સુદ/વદ.
  - Samvat per region: Shaka, Vikram (Gujarat year starts Kartika Shukla 1), the Tamil and Telugu 60-year names, and Bengali Bangabda.
- **Tarabala and chandrabala.**
  - Tara = ((today's nakshatra − natal nakshatra) mod 27) mod 9 + 1.
  - Chandrabala = the transit Moon's house counted from the natal Moon. Good houses are listed in rules.

## §7 Calendar and festivals
- **Lunar months.** A month is new moon to new moon (amanta) or full moon to full moon (purnimanta, the setting for North India).
  - The month is named by the solar sign the Sun enters during that month.
  - No sankranti in the month → **adhika** masa (the month repeats).
  - Two sankrantis in the month → **kshaya** masa (rare).
- **Samvatsara.** The 60-year Jovian cycle as used in South India, plus Vikram Samvat = Shaka + 135.
  - Year starts: Chaitra Shukla 1 (amanta regions), or Kartika for Gujarat. This is a setting per region.
- **Regional solar calendars.** The rule decides on which civil day a sankranti starts the month:
  - Tamil: same day if the sankranti is before sunset, otherwise the next day.
  - Malayalam: same day if before the end of aparahna (3/5 of daylight), otherwise the next day. The year starts in Simha (Chingam).
  - Bengali: sankranti between sunrise and midnight → the next day; between midnight and sunrise → the day after that. Offer two siddhanta options (Surya Siddhanta / Bishuddha), where Bishuddha uses modern astronomy. **VERIFY** what Surya Siddhanta mode requires; it may need its own mean-motion model and is R2 or later.
  - Odia: the sankranti day itself.
- **Festival DSL.** One JSON file per region (`rules/festivals/*.json`):
  ```json
  {"id":"janmashtami","name":"term:fest_janmashtami",
   "rule":{"type":"tithi_at","tithi":23,"masa":"bhadrapada","system":"purnimanta",
           "kala":"nishita","tie":"prefer_with_rohini"},
   "regions":["north","west"],"vratRules":["fast_until_nishita"]}
  ```
  - `type` values: `tithi_at` (kala ∈ `sunrise`, `pradosh`, `nishita`, `aparahna`, `madhyahna`, `moonrise`, `arunodaya`), `nakshatra_in_solar_month`, `sankranti`, `weekday_tithi_combo`, `fixed_gregorian`, `ekadashi` (smarta/vaishnava).
  - The rule engine returns the date, plus the `muhurat` window and the `parana` window where relevant.
  - Every rule has 2024–2030 fixtures for Delhi, Mumbai, Chennai, Kolkata, New York, London and Sydney.
- **Ekadashi.**
  - Smarta: Ekadashi prevailing at sunrise.
  - Vaishnava: if Dashami prevails during arunodaya (the 96 min before sunrise), the fast moves to the next day.
  - Parana falls on the next day, after Hari vasara ends (the first quarter of Dwadashi) and before Dwadashi ends.
  - All of this is **VERIFY** with the pandit.
- **Eclipses.**
  - Global search, then local circumstances (contacts, magnitude, visibility above the horizon).
  - Sutak applies only if the eclipse is visible: 12 h before a solar eclipse, 9 h before a lunar eclipse. Exemptions for the elderly, children and the sick are shown as a tradition note.
- **Family dates.**
  - Janma-nakshatra birthday: the day in the solar month of birth (Tamil/Malayalam) or the lunar month (setting) when the natal nakshatra prevails at sunrise.
  - Tithi birthday: the lunar month and tithi of birth.
  - Shraddha: the tithi prevailing at aparahna in the lunar month of death.
  - Each type follows its rule from the festival DSL.

## §8 Validation and tolerances
- **Oracle.** `tools/oracle` generates `engine-golden/*.json` using Skyfield (MIT) + DE440 + jplephem:
  - Positions: 10,000 random epochs between 1900 and 2100.
  - Rise/set: 10 cities × 400 days.
  - Limb end times: 10 cities × 2 years.
  - Ayanamsa: the §4 golden values.
- **Spot checks.** Use the JPL Horizons API (ecliptic-of-date quantity 31) for 50 epochs. Do manual checks against the Rashtriya Panchang and IAE tables for 2026–27, and log any differences, with the reason, in `docs/ENGINE_DIFFS.md`.
- **Boundary cases (must-have fixtures):**
  - kshaya and adhika tithis
  - adhika masa years (2026 Jyeshtha, 2029…; **VERIFY** the list)
  - a sankranti within ±10 min of sunset or midnight
  - births in 1941–45 (war time) and in pre-1955 Mumbai and Kolkata
  - DST transition days (US, UK, AU)
  - latitudes above 60°
  - the dateline (Fiji, Auckland)
- **CI.** `:engine:goldenTest` runs on every PR. A change in any festival date fails the build unless `engine-golden/CHANGELOG.md` documents it.
- **Performance.** A full PanchangDay takes ≤ 2 ms; a full year for one city takes ≤ 400 ms on the JVM. Cache per (date, place, settings).

## §9 Places and time zones
- **Places.** GeoNames `cities5000`, plus a diaspora supplement and alternate names in Indic scripts. Licence: CC-BY 4.0, so the attribution goes on the About screen. Stored as SQLite FTS5 (≤ 6 MB). Each row includes an IANA timezone ID.
- **Time zones.** Current zones come from the platform's tzdata through kotlinx-datetime. Write a test that asserts the device returns the expected offsets for sample dates.
- **Historical overlay.** `rules/tz_overlay.json` covers:
  - Bombay Time (+4:51) until 1955.
  - Calcutta Time (+5:53:20) until 1948.
  - Madras time.
  - War time (+6:30) for 1942–45.
  - Pre-1947 Karachi, Lahore and Dhaka.
  - LMT fallback before 1900.

  The UI always shows the offset used and allows a manual offset. **VERIFY** the overlay against historical sources and fixtures.
- **Unknown birth time.** Offer a "noon chart" mode: hide the lagna, houses and D-charts above D9, and say so.

## §10 Scheduling time-based notifications
- **Scheduling.** Compute the next 14 days of events, then schedule with AlarmManager `setWindow`: the event time minus the lead time, with a 2-min window.
  - Reschedule daily through WorkManager, and on `TIME_SET`, `TIMEZONE_CHANGED`, `BOOT_COMPLETED` and on a city change.
- **Exact alarms.** Only when the user turns on "Exact alerts", which requests `SCHEDULE_EXACT_ALARM`.
  - `USE_EXACT_ALARM` may be allowed for "calendar apps that show event notifications". **VERIFY** the Play policy before using it.
- **Lock-screen privacy.** Notification text follows the tone rules and contains no birth data. Lock-screen visibility is PRIVATE.
