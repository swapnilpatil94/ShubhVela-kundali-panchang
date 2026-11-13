# PRODUCT.md: Feature catalog (source of truth)

Working name: **Parivaar Panchang**. This is a placeholder until a trademark check is done. Code identifiers use `pp`.

**Promise:** every astrology, calendar and devotion tool in one app, accurate for any city, in 12 languages, free. There are no human astrologers, no fear selling, and no account.

**Gating key**
- **F**: free, no ad on the screen.
- **N**: free, with one native or banner ad below the fold.
- **R**: free after one rewarded ad. Owners of the Plan never see the ad.
- **P**: Plan perk. Cosmetic only; it never locks a feature.

**Release key:** R1 is the launch, then R2 and R3.

**Sacred zones:** these screens never show ads of any kind, including app-open, interstitial and banner.
- Aarti, jap and mantra screens.
- Gita and chalisa reading.
- The puja timer.
- Shraddha and death-anniversary screens.
- The first session of a new user.

## Information architecture
Five bottom tabs:
1. **Aaj** (Today)
2. **Calendar**
3. **Kundli** (Me & Family)
4. **Bonds** (matching and friends)
5. **Bhakti**

A global search bar (⌘-style palette) reaches every tool by name in any language. Settings sit behind the avatar.

## A. Aaj (daily). R1
| ID | Feature | Gate |
|---|---|---|
| A1 | Panchang: vaar, tithi, nakshatra, yoga, karana (each with end time), paksha, masa (amanta and purnimanta), Vikram and Shaka samvat, ritu, ayana | F |
| A2 | Sun and Moon rise and set, Moon phase, Moon sign | F |
| A3 | Inauspicious windows: Rahu kaal, Yamaganda, Gulika, Durmuhurta, Varjyam, Bhadra, Panchak, Dishashool. Show a live countdown to the next one | F |
| A4 | Auspicious windows: Abhijit, Brahma muhurta, Amrit kaal, Vijaya, Godhuli | F |
| A5 | Choghadiya (day and night), Hora, Gowri panchangam / Nalla neram, Do-ghati muhurat, a lagna table for the day | F |
| A6 | Personal strip: Chandrabalam and Tarabalam for the user's own nakshatra, "your day" summary (rules-based), and the next family date | F |
| A7 | Daily rashifal by Moon sign, Sun sign or lagna, chosen by the user. Weekly, monthly and yearly views (see §H) | N |
| A8 | Share card: today's panchang for the city and language, rendered on device in 1:1 and 9:16 | F |

## B. Calendar. R1, regional packs in R2
| ID | Feature | Gate | Rel |
|---|---|---|---|
| B1 | Month view with tithi, nakshatra, festivals and vrats | N | R1 |
| B2 | Festival and vrat engine with region and tradition settings: Ekadashi (Smarta and Vaishnava), Pradosh, Sankashti, Purnima, Amavasya, Masik Shivratri, Sankranti, Navratri, all major festivals | F | R1 |
| B3 | Diaspora-correct dates. Everything is computed for the user's location and timezone, never copied from IST | F | R1 |
| B4 | Grahan (eclipse) list showing visibility at the user's location, contacts and sutak times | F | R1 |
| B5 | Transits and retrogrades calendar, plus Adhik maas notices | F | R1 |
| B6 | Regional calendars: Tamil, Malayalam (Kollam), Bengali (both siddhantas), Odia, Gujarati, Marathi, Telugu, Kannada. Each ships only after review by a regional pandit | F | R1 (2 regions) / R2 |
| B7 | Date converter: Gregorian ↔ Vikram, Shaka, regional | F | R2 |
| B8 | Offline yearly PDF calendar | R | R2 |
| B9 | **Regional wall-calendar view**, in the style of a traditional printed almanac. It's our own design: never use the "Kalnirnay" name, logo or layout. Paper-style month grid in the region's script and numerals (Devanagari, Gujarati, Tamil and others). Sundays and holidays in red. Each cell shows the tithi in local terms (Marathi शु./कृ., Gujarati સુદ/વદ). The local samvat (Shaka; Gujarati VS starting the day after Diwali; Tamil 60-year name) and the month change go in the header. There is a festivals list per month, regional specials (Marathi: Champa Shashthi and the Margashirsha Thursday vrat; Gujarati: a choghadiya strip; Bengali: Panjika fields), and a one-tap wall-PDF print | F | R1 (mr, gu) / R2 |
| B10 | **Tamil daily sheet (தினசரி காலண்டர்)**, a tear-off view swiped day by day. Shows the Tamil date (solar month), the 60-year name, Nalla neram, Gowri nalla neram, Rahu, Yamagandam, Kuligai, Soolam + Parigaram, Thithi, Natchathiram, Chandrashtamam (nakshatras), Naal (mel/keezh/sama nokku), sunrise, and the next big day. A toggle switches between **"printed style" fixed times on a 6:00 AM basis** (what Tamil calendars print) and **actual city sunrise** (more accurate). The same pattern applies to the Telugu, Kannada and Malayalam daily views | F | R2 (R1 if ta ships early) |

## C. Muhurat. R2
| ID | Feature | Gate |
|---|---|---|
| C1 | Muhurat finder for vivah, griha pravesh, vehicle, property, namkaran, mundan, annaprashan, upanayana, business start and travel, over a chosen date range | N |
| C2 | Personalised filter using the user's or couple's nakshatra (tara and chandra bala) | R (once per search) |
| C3 | Yearly muhurat lists | F |

## D. Kundli (Me & Family). R1 core, R2 advanced
| ID | Feature | Gate | Rel |
|---|---|---|---|
| D1 | Profiles for self, family and friends (manual entry), stored only on the device. Includes place search, a historical-timezone overlay and a manual offset | F | R1 |
| D2 | Birth chart in North, South and East styles; planets table with sign, degree, nakshatra, pada and lord; Avakhada chakra | F | R1 |
| D3 | Vimshottari dasha down to the sookshma level, with "current period" highlighted | F | R1 |
| D4 | Divisional charts D2–D60 (Shodashvarga) and Bhava chalit | F | R2 |
| D5 | Yogas and doshas: Manglik, Kaal Sarp, Sade Sati status, Kemadruma, and common Raj/Dhana yogas. Framing must stay neutral (see §Tone) | F | R1 |
| D6 | Ashtakavarga (BAV and SAV) and Shadbala | F | R2 |
| D7 | KP system: Placidus cusps, sub-lords, significators, ruling planets, KP horary (1–249) | F | R2 |
| D8 | Yogini dasha, Jaimini Chara karakas and Karakamsha, Ishta devata (shown as tradition) | F | R2 |
| D9 | Gochar: transits over the natal chart, and a Sade Sati timeline | F | R1 |
| D10 | Varshphal (Tajik annual chart, Muntha) | F | R3 |
| D11 | Family dates: janma-nakshatra and tithi birthdays, plus the shraddha tithi worked out from the date of passing. Each recurs yearly with reminders | F | R1 |
| D12 | Western (tropical) chart toggle | F | R3 |

## E. Reports
All reports are generated as PDFs on the device, in every supported language. Gate: viewing in the app is always **F**. A PDF export costs one **R**; Plan owners export without ads.

| ID | Report | Rel |
|---|---|---|
| E1 | Basic kundli report (8–12 pages) | R1 |
| E2 | Complete life report, "Brihat" style (60–120 pages): every house, planet, yoga, dasha, nakshatra | R2 |
| E3 | Career and education | R2 |
| E4 | Marriage and relationships | R2 |
| E5 | Year ahead (based on transits and dasha) | R2 |
| E6 | Sade Sati and Manglik, both in non-fear framing | R2 |
| E7 | Matching report (from §F) | R1 |
| E8 | Child kundli, written for parents | R2 |
| E9 | Numerology report | R2 |

Health, legal and financial *predictions* are excluded on purpose. Reports may show only traditional associations, with a disclaimer.

## F. Bonds (matching and social). R1
| ID | Feature | Gate |
|---|---|---|
| F1 | Marriage matching. North: Ashtakoota out of 36, with all 8 kootas explained. South: Dashakoota / 10 Porutham. Also Manglik compatibility, Rajju, Vedha, Nadi | F |
| F2 | **Bond invite**: the friend enters their own details on their own phone. The app exchanges a *Bond Code* that carries no birth data (see SOCIAL.md) | F |
| F3 | Bond types: Love, Crush, Bestie, Sibling, Parent, Work, Roommate. Each type re-weights the same kootas into plain-language axes such as Vibe, Talk, Trust, Fights and Growth | F |
| F4 | Bond deep-dive (all axes, a "growth areas" section, tips) | R |
| F5 | Squad: group compatibility for 3–8 friends | R |
| F6 | Identity cards: Nakshatra personality, **Yoni animal** (from the 14 traditional animals), **Gana** (Deva, Manushya, Rakshasa), Nadi, Moon-sign vibe, ruling deity of the nakshatra | F |
| F7 | Daily "bond weather" for the top 3 bonds | N |
| F8 | **Cosmic Wrapped** in December: the user's year in transits, streaks and bonds, as a 6-card 9:16 story set | F |
| F9 | Card themes (Holi, Diwali, Minimal, Neon, Mandala) | 1 free; the rest are R for 24 h or **P** |

## G. Bhakti (modern devotion). R1 core
| ID | Feature | Gate |
|---|---|---|
| G1 | Virtual mandir: choose a deity, light a diya, do aarti with an animation and bell haptics. Sacred zone | F |
| G2 | Naam-jap counter: 108-bead mala, haptic bead ticks, daily goal, streaks, the "sankalp" ritual. Sacred zone | F |
| G3 | Texts: Hanuman Chalisa, aartis, stotras and mantras in every script. Bhagavad Gita (Sanskrit, transliteration, our own translations). Daily shloka card. Sacred zone | F |
| G4 | Vrat tracker and vrat kathas we write ourselves; festival guides (vidhi, samagri checklist) | F |
| G5 | Audio: our own recordings or royalty-free tracks. Never commercial label recordings | F |
| G6 | Bhajan "jam" mode: large lyrics with auto-scroll; events calendar for meetups (R3) | F |
| G7 | Meditation and dhyan timer with Om; Brahma muhurta alarm | F |
| G8 | Festival and greeting cards personalised with the user's name, for WhatsApp and Status | F |

## H. Horoscope. R1
| ID | Feature | Gate |
|---|---|---|
| H1 | Rashifal (daily, weekly, monthly, yearly) for 12 signs. Generated from real transits by a rules engine that picks from a phrase library written by humans, per language | N |
| H2 | Personal daily guidance from transits over the natal Moon and lagna, plus tara bala | F |

## I. Other systems. R2
| ID | Feature | Gate |
|---|---|---|
| I1 | Numerology: Vedic mulank/bhagyank, Chaldean, Pythagorean, Lo Shu grid, name-number check | F |
| I2 | Tarot: daily card plus 3-card spread, using 1909 Rider–Waite–Smith public-domain art scanned from an original-print source | F; spreads beyond 3 cards are R |
| I3 | Baby names by nakshatra pada (namakshar), using our own curated database of names and meanings | F |
| I4 | Vastu basics: direction guide and checklists | F |
| I5 | Lal Kitab chart and debts (content only) | R3 |

## J. Tools. R1/R2
| ID | Feature | Gate |
|---|---|---|
| J1 | Planet positions now, ephemeris table, retrograde tracker | F |
| J2 | Sunrise and panchang for any city and date | F |
| J3 | Lagna calculator; finders for tithi birthdays and shraddha dates | F |
| J4 | Conventions screen. Settings: ayanamsa, sunrise definition, amanta/purnimanta, dasha year, Ekadashi rule, chart style. Every screen links "How was this calculated?" here | F |

## K. AI (optional). R3
| ID | Feature | Gate |
|---|---|---|
| K1 | "Explain this": retrieval from our own content library. No LLM is involved | F |
| K2 | "Ask about my chart": an LLM behind a proxy. It receives computed facts only (no name, no place), every answer is labelled AI, at most 3 per day | R per question; Plan owners get 10/day |

## L. Platform features. R1
- **L1:** widgets (Today panchang, next Rahu kaal countdown, festival countdown, jap streak).
- **L2:** notifications (vrat eve, family dates, grahan, sunrise and Brahma muhurta). The user opts in per category. Default is at most 2 per day. Countdowns go on the Quick Settings tile and widgets, never as auto-started Live Updates (see M10).
- **L3:** per-app language with 12 languages: en, hi, bn, mr, te, ta, gu, kn, ml, or, pa, as. R1 ships en, hi plus 2. Regional digits are optional.
- **L4:** encrypted backup (Block Store + Auto Backup) and an encrypted export file.
- **L5:** app lock (biometric) and privacy screen.
- **L6:** delete all data.

## M. Differentiators (gap research done 13 Nov 2026; none of the 7 competitors offer these)
| ID | Feature | Gate | Rel |
|---|---|---|---|
| M1 | **"Should I…?"**: about 30 intents (vehicle, travel, griha pravesh, new job, haircut, signing). Rule-based and offline, with no AI. Answer first: verdict, then best window today, then an expandable "why". The text input has a labelled mic for voice | F | R1 |
| M2 | **Chandrashtama and tara-bala alerts per family member.** Triggered when the Moon is in the 8th from that person's natal Moon. Gentle wording | F | R1 |
| M3 | **Auto travel mode.** When the location or timezone changes, show "You're in Dubai: timings updated". Uses coarse location, opt-in | F | R1 |
| M4 | **Read-aloud** of Aaj, explain sheets and kathas via Android TTS. Check `isLanguageAvailable`/`isNetworkConnectionRequired` at runtime. Ship our own audio for Sanskrit mantras | F | R1 |
| M5 | **Answer-first Aaj header**, e.g. "Good for new work until 2:40 PM". Three tiers of progressive disclosure: verdict → timings → full panchang | F | R1 |
| M6 | **Puja mode.** Step checklist with timers, a jap counter and a live Sankalp auto-filled with samvatsara, ayana, ritu, masa, paksha, tithi, vaar, nakshatra and place. A *-dvipa* option covers diaspora users | F | R1 |
| M7 | **ICS export** of festivals and family dates. A one-off `.ics` file via the share sheet needs no permission. An optional device-calendar write needs `WRITE_CALENDAR`, requested in context. A hosted subscription feed per region is R2 | F | R1 |
| M8 | **Quick Settings tile** showing the next Rahu Kaal and current choghadiya | F | R1 |
| M9 | **Proof panel** on every "How calculated" sheet: backend, ayanamsa, sunrise convention, altitude, and why results can differ from other apps by about 1 minute | F | R1 |
| M10 | **User-started Live Updates only**: vrat/fast progress until parana, the puja timer, a pinned eclipse tracker. Requires `POST_PROMOTED_NOTIFICATIONS`. Auto-started countdowns for "calendar events" break Google's policy, so **do not** auto-start a Rahu Kaal countdown | F | R2 |
| M11 | **Wear OS tile + widget** (Tiles 1.6; Wear Widgets built on Glance/Remote Compose for Wear OS 4+), plus a complication | F | R2 |
| M12 | **AR Sky View**: `TYPE_ROTATION_VECTOR` with an accuracy cone and calibration prompt; snaps to the Moon. Shows the nakshatra and the meaning for you. Uses the Astronomy Engine alt/az | F | R2 |
| M13 | **Personalised wall-calendar PDF** for the user's city, tradition, language and family dates. A great gift for parents | R | R2 |
| M14 | **Eclipse live simulation**, local contacts and sutak | F | R2 |
| M15 | **Open-source `:engine` + festival rules** (Apache-2.0 code, CC BY 4.0 rules data). Brand, content and app stay closed | — | R2 |
| M16 | **Profile switcher on Aaj** for shared family phones (18% of Indian users go online on someone else's phone, IAMAI 2025) | F | R1 |

## Tone rules (enforced in content review)
- Never use fear words: "danger", "destroy", "death" and similar. Doshas are "traditional considerations" and are always paired with context.
- Low match scores are framed as "growth areas".
- Footer on every interpretation screen: "Traditional interpretation, not a scientific prediction."
- Never sell remedies, gemstones or pujas. Never attach urgency to a dosha.

## Explicit non-goals
- Live astrologers.
- A wallet or credits system.
- A remedy store.
- A social feed or comments.
- Reading the contacts permission.
- Accounts or login.
