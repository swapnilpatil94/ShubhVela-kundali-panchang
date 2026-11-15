# DESIGN_V2.md: ShubhVela "Ratna Glass" design system (v2.0, supersedes MASTER Part 6)

**Visual reference:**
- `design/screens/01.png` … `28.png`: one PNG per screen.
- `design/shubhvela-screens.html`: live, animated version of the same screens.

Where an image and this text disagree, **this text wins**.

All sample data is for Pune, Sat 15 Nov 2026, 8:52 AM. Real values always come from `:engine`.

## 1. Brand
- **Name:** ShubhVela, written शुभवेला in Devanagari.
  - Play title: "ShubhVela: Panchang & Kundli".
  - Plan name: "ShubhVela Plus".
  - The name lives in a string resource (`app_name`) and is never hard-coded.
- **Taglines:** "Har shubh pal, aapke shehar mein." / "Every auspicious moment, wherever you are."
- **Mood:** temple calm with premium tech. Quiet luxury: restraint, a single metal (gold), real imagery.

## 2. Non-negotiable design rules (from the 2026 premium research)
1. **Glass only on floating chrome:** the dock, top app bars, sheets, transport controls and widgets. **Never on content, and never glass-on-glass.**
2. **All content sits on Lacquer**, a solid ink surface. Exactly one **Hero lacquer** per screen (gold hairline) holds the most important answer.
3. **Gold means auspicious or primary action only.** Kumkum means avoid or caution. Okay-grey means neutral. Nothing else is coloured, except the **Vaar Ratna** gem accent.
4. **Answer first:** Abhi answer, then picture, then detail (progressive disclosure).
5. **Text never sits on raw imagery.** Sky headers use a **text-safe zone** (§6) that guarantees ≥7:1. Everywhere else the minimum is ≥4.5:1 (body) and ≥3:1 (large text and icons).
6. **Motion:** calm springs for chrome, expressive motion only for rituals and reveals.
   - Ambient motion is limited to the slow planet spin, the diya flicker and star twinkle.
   - Reduce Motion freezes all of it at a designed static frame.
7. **Haptics are crisp and rare; sound is opt-in** (§9).
8. **Sacred respect:**
   - No deity, Om or sacred symbol in the dock, loaders, ads or swipeable chrome.
   - Deity art is commissioned, static and dignified.
   - Aarti, jap, sankalp, Gita and shraddha screens are **sacred zones**: no ads, and the dock is hidden in the immersive ones.
9. **One system:** one custom line-icon family (no emoji), radii 12/20/28/32, two elevations (lacquer and glass), two font families plus Indic scripts.
10. **Accessibility is part of luxury:**
    - Bada Text (up to 200%), Reduce Transparency, Reduce Motion, Sunao read-aloud.
    - Touch targets ≥ 48dp.
    - TalkBack labels on every visual element, including the orb, the Surya Path and the chart.

## 3. Tokens
### Colour
| Token | Hex | Use |
|---|---|---|
| ink0 | #0A0D18 | app background (never #000) |
| ink1 | #0E1222 | lacquer bottom |
| ink2 | #151A2E | lacquer top |
| ink3 | #1C2238 | pills, tracks |
| line | rgba(237,230,214,.10) | hairlines and borders |
| chandan | #EDE6D6 | primary text |
| chandan2 | #B8AF9C | secondary text |
| chandan3 | #8C8572 | tertiary text and labels (≥ 11sp) |
| white | #F6F1E7 | text over the sky |
| haldi | #E2B33C | auspicious, primary |
| haldiDeep | #B8862A | gold gradients |
| kumkum | #E8636A | avoid, Rahu, caution |
| okay | #9AA0B8 | neutral times |

**Gold button gradient:** #F2CF6E → #D9A52B → #B8862A, with a 1px top highlight.

**Navaratna gems (Vaar Ratna)** colour the gem chip, the active dock tab, today's graha glow and the sky tint. The weekday lord sets the gem:

| Day | Lord | Gem | Hex |
|---|---|---|---|
| Sunday | Surya | Manikya | #D2463F |
| Monday | Chandra | Moti | #EDE6D6 |
| Tuesday | Mangal | Moonga | #E06A4E |
| Wednesday | Budh | Panna | #2FA36B |
| Thursday | Guru | Pukhraj | #E2B33C |
| Friday | Shukra | Heera | #DDE7F2 |
| Saturday | Shani | Neelam | #4C78E0 |

Rahu is Gomed #B07A4E and Ketu is Lehsunia #B9C2A8; these two are used only on graha screens.

**Moods:**

| Mood | When | Look |
|---|---|---|
| Ratna Night | Default, dark | The tokens above |
| Utsav | Automatic on festival days | Ground #5A1020 → #16040A, lacquer #3A0C17 → #260810, a marigold toran on top |
| Chandan | Light mode | Background #FBF4E6 → #F3E6CC; lacquer #FFFBF2 with border #E3CFA2; text #2A1A12; gold #8C6214 / #B8862A; kumkum #B3202A |
| Paper | Regional wall-calendar and daily-sheet views | Background #FFFDF6; Sundays and holidays #C0392B |

### Type
**Fonts:** Cormorant Garamond for display and numerals (lining numerals). Manrope for UI. Tiro Devanagari Hindi for Hindi UI, Tiro Devanagari Sanskrit for shlokas and sankalp. Noto Sans Gujarati, Tamil, Bengali, Telugu, Kannada, Malayalam, Oriya and Gurmukhi for other scripts. All are OFL.

**Scale (sp):**

| Style | Font | Size and weight | Notes |
|---|---|---|---|
| display-xl | Cormorant | 36 / 500 | |
| display-l | Cormorant | 30 / 500–600 | |
| display-m | Cormorant | 24 | |
| display-s | Cormorant | 19–20 | |
| num-xl | Cormorant | 34–42 | |
| body | Manrope | 13 / 500 | |
| meta | Manrope | 11.5 / 500 | |
| eyebrow | Manrope | 10 / 700 | letter-spacing 0.2em, uppercase |
| label | Manrope | 9.5 / 800 | |
| Devanagari headline | Tiro | 19–30 | |

Everything scales with Bada Text. Never use fixed heights on text containers.

### Shape, space, elevation
- **Radii:** pills 999; chips 12; lacquer 20; sheets 30 (top); dock 32; phone-like tiles 24–30.
- **Spacing:** 4, 8, 10, 12, 14, 16, 24. Screen horizontal padding is 16.
- **Elevation, two levels only:**
  - **Lacquer:** a 1px inner top highlight plus an ambient shadow of 0 10 24 at 35% black.
  - **Glass:** blur 24, saturation 170%, tint gradient rgba(28,34,56,.58) → rgba(14,18,34,.66), 1px border rgba(237,230,214,.16), inner top highlight 22% white, shadow 0 18 36 at 50%.

## 4. Surfaces (Compose)
- **`Lacquer(hero=false, warn=false)`:** vertical gradient ink2 → ink1, a `line` border, radius 20.
  - `hero` adds a 1px gold border (alpha .55).
  - `warn` adds a kumkum border (alpha .45).
- **`GlassSurface`:**
  - Use **Haze** (`dev.chrisbanes.haze`, API 23+) for the backdrop blur, noise and tint.
  - Fallback ladder:

    | Android level | Treatment |
    |---|---|
    | API 33+ | AGSL specular highlight that follows the device tilt |
    | API 31–32 | blur plus a static highlight |
    | API ≤ 30 | opaque ink3 plus a 1px gold hairline |
    | Reduce Transparency on (any level) | solid ink3 plus a gold hairline |

- **Tilt specular:**
  - Source: gyroscope (`TYPE_GAME_ROTATION_VECTOR` or `TYPE_GYROSCOPE`), low-pass filtered and clamped to ±10°.
  - The highlight moves on the gold button, the gem chip and glass.
  - Unregister the sensor when the screen is off or in the background, and in battery saver.

## 5. Navigation: floating glass dock with the live Aaj orb
- **Dock:** a glass capsule 64dp tall with 12dp side and bottom margins, radius 32.
  - Five columns, from left to right: **Calendar · Kundli · (Aaj orb) · Bonds · Bhakti**.
  - The orb column is 80dp; the others share the rest.
  - Labels are always visible (8.5sp / 800).
- **Side tabs:**
  - Inactive: chandan3 line icon at 22dp.
  - Active: icon stroke = Vaar Ratna gem lightened 25%, a soft glow, translateY −1dp, and a 16×3dp gradient bar 9dp below the label.
- **Aaj orb:** 64dp circle raised 17dp above the dock.
  - Fill: radial ink gradient. Ring: 1.5dp gold, plus a 6dp ink gap ring.
  - The orb is the **live status of the day**:

    | Part | Meaning |
    |---|---|
    | Progress ring (r = 29dp, stroke 3dp) | Fraction of the **current choghadiya** left, starting at 12 o'clock, with a dot at the end |
    | Ring colour | Gold if the current period is shubh (Amrit, Shubh, Labh) or neutral (Char); **kumkum** if avoid (Rog, Kaal, Udveg) or inside Rahu Kaal |
    | Centre diya | Lit flame when good; unlit wick when avoid |

  - **Glow pulse** (3.2 s breathe) only while Aaj is selected. It is frozen under Reduce Motion.
  - **TalkBack label:** "Aaj. Good time now, until 9:33 AM."
  - The orb refreshes when a choghadiya ends, driven by the same scheduler as the widgets.
- **Behaviour:**
  - A `SEGMENT_TICK` haptic on each tab change.
  - Spring indicator motion (`MotionScheme.standard`).
  - The dock hides on scroll down and shows on scroll up.
  - It is **hidden** in immersive sacred screens (aarti, jap) and on the full-screen reveal.
- **Light mode:** an ivory orb with a #C99A3A ring; the active icon is #B8862A with no glow.

## 6. Sky header (the "Brahmanda" sky)
The top ~330dp of **Aaj, Kundli, Graha detail, onboarding, the identity card and the Plus screen** shows today's sky. A Brahmanda nebula is tinted by the Vaar Ratna gem and carries a warm Hiranyagarbha core.
- **Assets:**
  - 9 colour-graded deep-space images, one per graha. Use NASA Hubble/JWST (credit "NASA") or ESA/Webb (CC BY 4.0; credit "ESA/Webb"). Verify the terms for each image.
  - WebP at 1080×1100, 80–120 KB each, shipped as a Play Asset Delivery fast-follow pack.
  - The procedural nebula in the reference HTML is a stand-in only.
- **Composition:**
  1. A left-to-right reading shade: from (8,10,20) at .78 alpha, to .55 at 55% width, to .12 on the right.
  2. A vertical fade into ink0: .25 → .10 at 45% → .78 at 72% → 1.0 at the bottom.
- **Text-safe zone (mandatory):**
  - In the region x ≤ 84% and y ≤ 62% of the header, cap the background's relative luminance at **0.075**. Scale RGB in linear light, and feather the cap over 14dp at the zone edges.
  - This guarantees ≥7:1 for #F6F1E7 text.
  - Bake the cap into the graded assets, and also enforce it at runtime with an AGSL pass on API 33+, so that future images can't break it.
  - **Test:** a screenshot test samples the brightest pixel under every text glyph box and asserts ≥7:1.
- **Header type:**
  - eyebrow 11sp / 800, #F6F1E7
  - greeting display 30–31sp / 600, white
  - meta 12.5sp / 600
  - all with a text shadow of 0 1 2 at 85–90% black
- **Hora planet:** the planet of the current hora (from the engine) floats at the top right at 96–130dp, partly off-canvas, never under text. It rotates slowly and pauses off-screen and in battery saver.
- **Gem chip:** a glass-dark pill containing a 16dp faceted gem (conic gradient plus a tilt specular) and the text "Shanivaar · Neelam".

## 7. Real planets
- **Renderer:** an AGSL `RuntimeShader` (API 33+) maps an equirectangular texture onto a sphere.
  - **Lighting** comes from the true Sun direction (engine). Terminator softness is per body: Lambert for gas giants; Lommel-Seeliger for the Moon and Mercury, plus faint earthshine on the Moon.
  - **Rings (Saturn):** an analytic ring plane at the **real tilt for the date** (from the engine), shadows in both directions, and Cassini division bands.
  - The Moon shows its **true phase and position angle** for the user's latitude.
- **Textures:** Solar System Scope, 2K (CC BY 4.0; attribution required), and NASA SVS CGI Moon Kit (credit "NASA/GSFC Scientific Visualization Studio"). Place the credits on the About screen.
- **Below API 33:** 24 pre-rendered WebP rotation frames per planet, or a static frame.
- **Budget:** at most one large animated planet per screen, at ≤ 30 fps (the spin is slow), paused when not visible.

## 8. Components (build once in `:core:designsystem`)
| Component | Spec |
|---|---|
| `GemChip(day)` | Weekday gem |
| `AbhiCard(state, label, until, detail)` | Hero lacquer. Diya (lit or unlit) on the left. A Devanagari line ("अभी शुभ समय है" / "थोड़ा रुकें" for wait) above an English display line. States: good (gold), wait (kumkum border), neutral |
| `SuryaPath(choghadiyas, now, sunrise, sunset, abhijit)` | A sunrise-to-sunset arc in 8 segments coloured good/okay/avoid. Labels sit outside the arc. The real sun dot (glow) is at "now", with sunrise/sunset captions. Tap a segment to open the explain sheet |
| `ChoghadiyaPatti(day/night)` | Rows: 4dp colour stripe · time range (tabular numerals) · name in its colour · plain meaning · tags (Rahu, Yamaganda, Gulika) · NOW pill on the current row (row tint haldi at 10%) |
| `NextList` | 2–3 rows: Rahu Kaal, the best window, "See all timings ›" |
| `SkyEventCard(event)` | **Shown only when a real event happens this week** (station, sign change, eclipse). A 96dp Brahmanda thumbnail with the planet, an eyebrow "This week in the sky", a display line, and "Nth from your Moon · tap to read" |
| `PanchangList` | Tithi, nakshatra, yoga and karana, each with an end time |
| `ExplainSheet(term)` | Glass sheet: meaning, then a "How we calculated it" lacquer block, then Change conventions and Read aloud |
| `ShouldIVerdict` | Search lacquer with a labelled mic ("Bolo"), then a hero verdict (gold check medallion, the window in num style, what to avoid), then a "Why" list (✓ gold / • okay), then intent pills |
| `MoonMonthGrid` | 7 columns. Each cell: date (Sundays in kumkum), a 13dp real-phase moon, and the tithi label or festival (gold). Today has a gold border and a 16% fill |
| `RegionalWallGrid(mr/gu)` | Paper mood. Native numerals and native tithi labels (शु./कृ., સુદ/વદ), Sundays and festivals in red, festival list, wall-PDF action |
| `TamilDailySheet` | Red top band; Tamil month and day number in a large serif; field rows (Nalla neram, Gowri, Rahu, Yamagandam, Kuligai, Thithi · Natchathiram, Soolam · Parigaram, Chandrashtamam, Naal). Toggle: printed style (6 AM) or actual sunrise |
| `FestivalSteps` | Utsav mood. Numbered steps (a done step shows a gold check), with timers |
| `KundliChart(style)` | North, South or East. Gold hairlines; planets as two-letter glyphs in gem colours with a 14% halo; house numbers in chandan3 |
| `NavagrahaGrid` | 3×3 in temple directions: NW Ketu, N Guru, NE Budh, W Shani, C Surya, E Shukra, SW Rahu, S Mangal, SE Chandra. Gem-coloured rims; today's lord glows; Rahu and Ketu shown as eclipse discs |
| `GrahaHeader` | Sky header of that graha, the large real planet, then the name and the status in gem colour |
| `FamilyAvatars`, `FamilyDateRow` | Family profiles and their upcoming dates |
| `RewardedSheet` | Glass sheet: watch-ad gold button + "Go ad-free" ghost button + "reading is always free" |
| `BondOrbit(score)` | Two light points on counter-rotating rings, the score in num-xl gold, the label in display gold, axis bars (gold, or okay for "growth area") |
| `IdentityCard` | Yoni line art from the custom icon set, gana, nadi, deity, symbol |
| `ShareCardFrame` | 9:16 Brahmanda background, real planets, wordmark "shubhvela.app". **Never shows birth data** |
| `AartiPlayer` | Immersive. Panch-deep with flames, lyric lacquer (current line gold, neighbours chandan3), glass transport (prev, gold play/pause orb, bell) |
| `JapMala` | 108 beads at r = 118dp. Lit beads use a gold radial; the current bead glows; count in num 64sp |
| `SankalpCard` | Sanskrit text with the **engine-filled words in gold**, plus copy, listen and share |
| `PlanOption` | Lacquer, or hero when selected; price in num |
| `ToggleRow`, `SettingRow` | Gold track when on |
| Ad slots | Solid lacquer, labelled "Ad". **Never glass, never near timings or in sacred zones** |

## 9. Motion and haptics
- **Chrome:** `MotionScheme.standard()` springs.
- **Reveal and ritual:** expressive springs. The daily reveal fades the Moon from 0 → 1 over 900 ms.
- **Loops:** star twinkle 0.8–3 s; diya flicker 1.6 s; orb breathe 3.2 s; planet spin at 2–9 texture px/s. All stop under Reduce Motion or when off-screen.
- **Haptics:**

  | Moment | Feedback |
  |---|---|
  | Tab change | `SEGMENT_TICK` |
  | Jap bead | `SEGMENT_FREQUENT_TICK` (fallback `CLOCK_TICK`) |
  | 108 complete | `CONFIRM` |
  | Bell | `CONFIRM`, plus an opt-in bell sound |
  | Reward granted | `CONFIRM` |
  | Error | `REJECT` |

  Never use buzzy one-shot vibrations, and respect the system haptics setting.

## 10. Screen catalogue (each one maps to its PNG reference)
| # | Screen | Key elements |
|---|---|---|
| 01 | Onboarding · language | Moon sky header, 10 + 2 languages in native script, offline/privacy note |
| 02 | Onboarding · city & tradition | Search lacquer, sunrise preview, tradition options (one hero) |
| 03 | Daily reveal | Gem chip, real Moon, tithi in italic display, नक्षत्र line, शुभवेला in gold, festival, "Begin today". No dock |
| 04 | Aaj home | Sky header (gem + hora planet), AbhiCard, SuryaPath, NextList, dock |
| 05 | Aaj scrolled | SkyEventCard (conditional), PanchangList, family reminder, Ad slot |
| 06 | Choghadiya Patti | Day/Night tabs |
| 07 | Explain sheet | — |
| 08 | Should I…? | — |
| 09 | Calendar · moons | MoonMonthGrid, today hero (festival), banner Ad |
| 10 | Marathi wall view | Paper mood |
| 11 | Tamil daily sheet | Paper mood |
| 12 | Festival day | Utsav mood, FestivalSteps |
| 13 | Kundli · birth sky | Sky of the birth weekday, chart, dasha |
| 14 | Navagraha | — |
| 15 | Graha detail | Glass action sheet |
| 16 | Parivaar | Family profiles and dates |
| 17 | Reports | RewardedSheet |
| 18 | Bond result | — |
| 19 | Identity card | — |
| 20 | Story card | — |
| 21 | Bhakti home | — |
| 22 | Aarti | Immersive |
| 23 | Jap mala | — |
| 24 | Sankalp | — |
| 25 | Plus plan | — |
| 26 | Settings | Conventions, comfort, privacy |
| 27 | Chandan light Aaj | Bada Text on |
| 28 | Widgets | Glance Abhi widget, SuryaPath widget, Kaal Chakra (Pro) widget, Wear OS face, Live Update (user-started vrat only) |

**The Kaal Chakra dial** is used only for the watch face, the Pro widget and an optional "Pro view" in Aaj.

## 11. Assets to obtain (TODO owner)
| Asset | Detail |
|---|---|
| Sky images | 9 graded deep-space images (NASA / ESA-Webb), credits recorded in `assets/CREDITS.md` |
| Planet textures | 2K Solar System Scope textures (CC BY 4.0) and the NASA SVS Moon map |
| Icon set | Custom line icons: tabs, the 14 yoni animals, the 9 grahas, 12 rashis, diya, mala, bell, lotus |
| Deity art | Commissioned, with full rights transfer; static |
| Audio | Studio aarti recordings (our own); bell and conch samples (our own or CC0) |

## 12. Performance budgets
- Cold start ≤ 1.2 s on mid-range devices and ≤ 2 s on 3 GB devices. Use the SplashScreen API, and let the reveal stand in for a spinner.
- 60 fps scrolling. Planets ≤ 30 fps.
- Sky header images are decoded at display size.
- Base AAB stays ≤ 20 MB. Skies and textures ship in asset packs.
