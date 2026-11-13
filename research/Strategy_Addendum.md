# Strategy Addendum: the all-in-one, free + ads astrology & devotion app

**Research date:** 13 Nov 2026.

**This builds on** [Astrology_Android_Research_Report.md](Astrology_Android_Research_Report.md). The build instructions for Fable and Opus are in [build-kit/](build-kit/README.md).

**Labels:**
- [V] verified: primary source checked
- [E] estimated
- [U] unknown

---

## 1. Free alternative to Swiss Ephemeris: yes, and it is commercial-safe

| Option | Licence | Accuracy (tithi/nakshatra end time) | Verdict |
|---|---|---|---|
| **Astronomy Engine** (Kotlin, Don Cross) | **MIT** [V] | Typically ≲10–30 s. Worst case about 2 min. An MIT panchang built on it reports tithi ≤10 s vs Swiss Ephemeris [E] | **Base engine.** Pure Kotlin, no data files, no native code |
| **NASA/JPL DE440 excerpt 1900–2100** + our own ~300-line Chebyshev reader | NAIF rules: commercial use allowed, no fees [V] | ≈0.001″. ΔT dominates, so ~1–5 s | **Precision pack**, ~14 MB, delivered via Play Asset Delivery |
| VSOP87 + ELP/MPP02 own port | Data terms unclear; existing ports are GPL [V] | ~1 s | Skip |
| Moshier | No licence [V] | Good | **Avoid** |
| PyJHora, VedAstro, drik-panchanga clones | AGPL, or depend on Swiss Ephemeris [V] | — | **Avoid** |
| Vedaksha | BUSL, $500 commercial [V] | — | Not free |

**Lahiri ayanamsa without Swiss Ephemeris** uses the official IAE definition: 23°15′00.658″ true at 21 Mar 1956 plus IAU precession. Our research reproduced the published IAE/Swiss Ephemeris values **within 0.03″** [V]. Formula and test values are in [ENGINE.md §4](build-kit/docs/ENGINE.md).

**Validation oracle (free):** Skyfield (MIT) + DE440 + JPL Horizons, generating golden test data that runs in CI.

**Bonus:** a pure-Kotlin engine avoids Play's 16 KB native-library requirement, which blocks non-compliant updates from 1 Feb 2027 [V]. It also lets the same engine generate the SEO website and later run on iOS through Kotlin Multiplatform.

---

## 2. One app for everything (except human astrologers)

**Five tabs and one search bar.**

| Tab | Contents |
|---|---|
| **Aaj** | Panchang, Rahu kaal / Choghadiya / Hora / Gowri, live countdowns, your day, rashifal, share card |
| **Calendar** | Festivals and vrats for your tradition, computed for **your city anywhere in the world**; eclipses with sutak; regional calendars |
| **Kundli** | You and your family: charts in North/South/East styles, dashas, D1–D60, yogas/doshas, KP, Ashtakavarga, transits, **family tithi dates**, reports in every language |
| **Bonds** | Marriage matching (Ashtakoota 36 + 10 Porutham), friend/love/bestie bonds, squad, nakshatra identity cards |
| **Bhakti** | Virtual mandir and aarti, naam-jap mala with streaks, Chalisa/Gita/mantras, vrat tracker and kathas, festival puja guides, greeting cards, meditation |

Also included: muhurat finder, numerology, tarot (public-domain 1909 art), baby names by nakshatra, Vastu basics, an ephemeris table, and later an optional, clearly labelled AI.

The full catalogue, with IDs and gating, is in [PRODUCT.md](build-kit/docs/PRODUCT.md).

**Reports in every language** are generated on the phone as PDFs:
- basic kundli
- full life report (60–120 pages)
- career
- marriage
- year ahead
- Sade Sati/Manglik
- matching
- child
- numerology

There is no server cost and no data leaves the device.

**A pushback worth taking seriously:** "all features" is the right promise, but not the right launch scope. R1 ships the daily, calendar, kundli, matching, Bonds, Bhakti and basic-report core in 4 languages (en, hi, mr, gu). R2 and R3 add the rest. This is sequencing, not cutting: every feature is planned in [PROMPTS.md](build-kit/docs/PROMPTS.md).

---

## 3. Audiences and how ads are tuned for each

| Audience | What they come for | Ad treatment | Monetization lever |
|---|---|---|---|
| **Ritual keepers** (often women 30–65) | Daily panchang, vrats, festivals, family dates | Native ads only; no interstitials in the first week | Volume (daily use); greeting cards spread the app |
| **Marriage-matching families** | Kundli matching, muhurat | Interstitial after the match result; rewarded unlock for the matching PDF | High-intent rewarded views |
| **Gen-Z** (18–28) | Bonds, nakshatra/yoni/gana cards, love checks, Wrapped | Rewarded ads for deep-dives and themes; no app-open ads | Rewarded views + virality |
| **Diaspora** (US/UK/CA/UAE/AU/SG) | Correct local festival and Ekadashi dates | Same caps | eCPM roughly 8–10× India's; local-currency Plan |
| **Serious learners** | D-charts, KP, Shadbala | Banners only | Highest Plan conversion |
| **Devotees** | Jap, aarti, Gita | **No ads in sacred zones**; native ad after the session ends | Retention and streaks |
| **Under-18** (detected from their own profile) | Bonds, horoscope | Non-personalized ads only; analytics off | DPDP compliance (from May 2027) |

**What sits behind a rewarded ad** (everything stays free, the ad just pays for it):
- PDF exports
- bond deep-dives and squads
- the personalised muhurat filter
- card themes
- the yearly PDF calendar
- AI questions (R3)

**What is never gated:** any timing, calendar, chart, matching score, or reading done inside the app.

**Policy facts that shape this:**
- AdMob removed the ability to block the "Astrology & Esoteric" ad category on 23 Oct 2026 [V], so competitors' fear-based astrology ads must be blocked one advertiser at a time.
- Rewarded ads must be opt-in, with the reward disclosed [V].
- Rewards for ratings are banned [V].
- Referral rewards are allowed only for your own app [E]. We give 7 ad-free days after the friend has been active for 3 days.

---

## 4. "Remove ads" plan, by country

The Plan removes ads and unlocks every rewarded feature permanently. **It never paywalls a calculation.**

| Market | Monthly | Yearly | Lifetime |
|---|---|---|---|
| India | ₹49 (prepaid ₹59) | ₹399 (prepaid ₹449) | ₹999 |
| Nepal / Bangladesh / Sri Lanka | ≈$0.99 | ≈$4.99 | ≈$11.99 |
| US | $1.99 | $11.99 | $29.99 |
| Canada | CA$2.49 | CA$14.99 | CA$34.99 |
| UK | £1.49 | £9.99 | £24.99 |
| Eurozone | €1.99 | €11.99 | €27.99 |
| Australia / NZ | A$2.99 | A$17.99 | A$39.99 |
| UAE / Gulf | AED 6.99 | AED 44.99 | AED 109.99 |
| Singapore | S$2.98 | S$15.98 | S$38.98 |
| Malaysia | RM4.90 | RM29.90 | RM69.90 |

**Where these prices come from:**
- Comparable apps: Drik Panchang is $0.99/mo and $9.99/yr in the US, ₹99/mo and ₹799/yr on iOS in India [V]. Truecaller is ₹99/mo [E]. YouTube Lite is ₹89 [E].
- The rule: yearly price ≥ 2× the yearly ad revenue of a heavy user. [E]

**How they are set and adjusted:**
- India gets prepaid passes because many users avoid UPI autopay mandates.
- Play pricing templates were removed in Oct 2025, so prices are set per product through the Play Developer API from a checked-in `prices.json` [V].
- Recalibrate after 2 weeks of real AdMob country data.

---

## 5. Gen-Z and "modern religion": features people will love and share

1. **Bond invite, privacy-first.** Your friend enters *their own* birth details on *their* phone. Only a ~20-character "Bond Code" is exchanged. It carries the Moon nakshatra, signs and Manglik flag, never the birth date, time or place. It needs no server and no login.
2. **Bond types:** Love, Crush, Bestie, Sibling, Parent, Work, Roommate. The same Ashtakoota math is re-weighted into plain-language axes: Vibe, Talk, Trust, Fights, Growth. The lowest band is called "Growth Bond". **No negative labels**, which is the lesson from Co–Star's backlash over harsh copy [V].
3. **Authentic Vedic identity cards** that no Western app has: your **Yoni animal** (14 traditional animals), your **Gana** (Deva/Manushya/Rakshasa), Nadi, your nakshatra's deity and personality. These are real koota components, so they are both shareable and authentic.
4. **Squad compatibility** for 3–8 friends, and **"bond weather"** each day.
5. **Cosmic Wrapped** every December: your year in transits, jap streaks and closest bonds, as 6 story cards. Spotify Wrapped 2025 got 500M+ shares in 24 h [V]. No astrology app does this yet [E].
6. **Naam-jap mala** with haptic beads, streaks and *sankalp* goals, plus a **virtual mandir** with diya and aarti, built as calm, beautiful, offline experiences.
7. **Bhajan "jam" mode** that rides the 2025–26 *bhajan clubbing* trend [V]: big lyrics, own or royalty-free audio, and (R3) a calendar of meetups.
8. **Story-native cards** at 1080×1920 for Instagram Stories and WhatsApp Status. Every card carries a small wordmark and a link.
9. **Festival greeting cards with your name**, pre-loaded 7 days before each festival. This is the Crafto/ShareChat growth pattern [V].
10. **Family dates:** "Maa's janma-nakshatra birthday is Friday", "Dada ji's shraddha is on the 14th". We found no app that does this as a family feature [E/RV]. It is the deepest retention hook.

**Market signal:** Indian astrology app downloads grew 75% YoY to 15 crore in Jan–Sep 2026 [V, Sensor Tower]. In the same period, a *status, bhajan and wallpaper* app grew MAU 109% while Sri Mandir fell 20% [V]. **Shareable, free utility is winning over paid services.**

---

## 6. Why people will genuinely love it (product principles)

1. **Correct wherever you are.** A family in New Jersey gets New Jersey's Ekadashi, not India's.
2. **Transparent.** Every number has a "How was this calculated?" sheet, and conventions can be changed. We publish our accuracy methodology.
3. **Respectful.** No ads on aarti, jap or shraddha screens. No full-screen ad on first use. No fear language, ever.
4. **Honest.** No remedies sold, no "dosha alarms", no wallet, no per-minute anything. A "traditional interpretation" note on every reading.
5. **Private.** No account. Birth data never leaves the phone, and it is encrypted. Backup is end-to-end encrypted.
6. **Your language, fully.** 12 languages, native terms for every tithi and nakshatra, reports in your script.
7. **Fast and light.** Works offline, opens instantly, and runs well on a ₹8,000 phone.
8. **Beautiful.** "Sacred, modern, calm": saffron, ivory and night-indigo, Material 3 Expressive, gentle motion, original art. See [DESIGN.md](build-kit/docs/DESIGN.md).
9. **Family-first.** One app for the whole family's dates, charts and matches.
10. **Generous.** Features that competitors charge ₹299–₹2,145 for are free here, at worst one ad away.

---

## 7. Marketing plan (low-cost, festival-timed)

**Positioning:** *"Har parivaar ka panchang. Sahi, har shehar mein. Free."* ("Every family's panchang. Correct, in every city. Free.")

**Brand pillars:** Accurate, Respectful, Free, Yours.

| Phase | When | Plays |
|---|---|---|
| Pre-launch | Now to launch | WhatsApp Channel and Instagram/Shorts with a daily city panchang card in Hindi plus one regional language. A programmatic SEO site from the same engine (city × date × term, diaspora pages). Play pre-registration. A waitlist that collects city only. |
| Launch | Time it to a festival peak (Holi, Chaitra Navratri, Akshaya Tritiya) | A festival card pack. PR angle: *"The free app that publishes its math"*. Regional micro-creators (50–500K followers, checked for fake followers). |
| Growth | Ongoing | Bond-invite loop. Referral (7 ad-free days). Eclipse "sutak in your city" cards as free news hooks. Temple and community WhatsApp-admin partnerships, with free white-label calendars. |
| Big moments | Shravan → Navratri → Diwali; December | Navratri/Diwali card packs and the Plan's festival discount. **Cosmic Wrapped** in December. Diaspora campaigns around Diwali in the US/UK/Canada. |
| Gen-Z | Ongoing | Reels: nakshatra personalities, "yoni animal reveal", friend-bond reactions. Campus bhajan-jam partnerships. |
| ASO | Ongoing | Target the fragmented "panchang" and regional-calendar keywords first [V], and "kundli" later. Localized listings in every language. |

**Budget discipline:**
- Organic first. Ads can't pay back India installs: an install is worth about ₹4 in ads against a ₹22–60 CPI.
- Paid spend only for diaspora installs, and only after measured LTV covers CPI.
- Creator budget is roughly ₹50k–2L per festival, set by what the tests show [E].

**Measure weekly:**
- organic share of installs
- WhatsApp-attributed installs
- D1/D7/D30 retention
- bond invites sent per user
- ARPDAU by country
- Plan conversion

---

## 8. Accuracy and language: how we earn trust

- **Two backends.** Astronomy Engine is the base. DE440 is the precision pack for 1900–2100.
- **Golden tests in CI** cover 10k positions, rise/set for 10 cities, limb end times, the ayanamsa values, and festival dates for 2024–2030 in 7 cities including New York, London and Sydney.
- **Regional festival rules** are signed off by a paid pandit per region before they ship.
- **Historical time-zone overlay** covers Bombay Time (+4:51) until 1955, Calcutta Time (+5:53:20) until 1948, and war time (+6:30) for 1942–45. The offset used is always shown and can be overridden.
- **Content** is written in-house from classical principles; modern copyrighted translations are never copied.
  - Bulk translation runs on Claude Haiku 5.5 via Batch with a locked glossary of native terms.
  - A native reviewer checks 100% of the translated text.
  - The API cost is a few dollars per language. Human review is the real cost, about ₹0.6–1.6 L per language [E].
- **Public-domain texts:**
  - Gita: Arnold 1885, Besant 1895 and Telang 1882 are public domain [V/E]. Gita Press or ISKCON editions are not.
  - Tarot: the 1909 Rider–Waite–Smith art is public domain. US Games' recolouring is not [V/E].
  - Audio must be your own recordings. Label recordings are copyrighted [V].

---

## 9. Budget you'll need beyond AI coding (estimates)

| Item | Estimate |
|---|---|
| Jyotish consultant + editor (English/Hindi content masters, ~160k words) | ₹3–6 L [E] |
| Native reviewers: 3 more R1 languages; 8 more over R2–R3 | ₹2–5 L for R1; ₹5–13 L later [E] |
| Pandit sign-off on regional festival rules | ₹20–50k per region [E] |
| Original deity illustrations and icon set | ₹1–3 L [E] |
| Own audio recordings | ₹0.5–2 L [E] |
| Legal (privacy/DPDP, content licences, trademark) | ₹1–2.5 L [E] |
| Play Console ($25 once), Cloudflare ($5/mo), domain | Negligible |
| **Total to launch R1** | **≈ ₹8–20 L** plus founder time [E] |

---

## 10. Honest risks of the all-in-one approach

- **Scope.** The 5-tab structure, global search and R1/R2/R3 sequencing keep the app usable. Don't let feature count clutter the Aaj screen.
- **Content cost and quality.** This is the real bottleneck, not code.
- **Minors.** Gen-Z features attract under-18s, so DPDP's verifiable-parental-consent and no-targeted-ads rules apply from 13 May 2027 [V]. Built-in age handling plus legal review are required.
- **Copying.** Drik Panchang and AstroSage can copy features. They can't easily copy restraint (no fear, no upsell) because their revenue depends on it. Family data, diaspora correctness, an authentic Gen-Z layer and 12-language depth form the moat.
- **Validation still matters.** Run the distribution tests (WhatsApp channel + SEO pages) during weeks 1–8 of the build. They cost almost nothing and tell you which language and region to launch first.

---

## 11. What we were missing: differentiators across features, not just content
**Sources.** All 7 competitors (Drik Panchang, AstroSage, Prokerala, Sri Mandir, Nithra, Melooha, Co–Star) were checked against their current Play and App Store listings and official sites on 13 Nov 2026. "Not found" means it was not in the listing; that does not prove the feature is absent.

| Differentiator | Who has it today | Our version |
|---|---|---|
| **"Should I…?" answers** (buy a vehicle, travel, new job…) | Only as **paid AI chat**: Co–Star ₹299 for 5 questions; Melooha "Buy Questions" [V] | **Free, offline, rule-based.** Verdict first, then today's best window, then the reasons. It cannot hallucinate |
| **Personal Chandrashtama / Tara alerts per family member** | Nithra shows it generically. Prokerala has it as a web calculator only [V] | A gentle "Papa: Chandrashtama today" alert |
| **Auto travel mode** | Nobody. Sri Mandir reviewers complain about wrong-location panchang [V] | "You're in Dubai: timings updated" |
| **Read-aloud in 12 languages + labelled voice input** | Nobody [V] | For elders and low-literacy users. 1 in 5 Indian internet users already use voice [V, IAMAI] |
| **Live Sankalp** auto-filled with today's panchang | Drik offers a static template with "Amuka" blanks [V] | Fully filled in for your place and day |
| **Puja mode** (steps + timers + jap + sankalp) | Drik and Sri Mandir: text only [V] | One guided flow |
| **Wear OS tile/watch** | None of the 7 [V] | Next Rahu Kaal, tithi and sunrise on the wrist |
| **AR Sky View**: see the Moon and its nakshatra tonight | None [V] | Gen-Z shareable; honest compass-accuracy cone |
| **Proof panel + open-source engine** | Drik publishes only its sunrise convention [V] | The complete method, published |
| **Family tithi dates exported to Google Calendar** | Drik exports the panchang, not family tithi dates [V] | One tap; the share-sheet `.ics` needs no permission |
| **Personalised wall-calendar PDF** (city, tradition, language, family dates) | Drik offers city PDFs only [V] | A gift for parents |
| Widgets, eclipse pages, city PDFs, offline mode | **Drik has these** [V] | **Parity is mandatory**, not optional |

**The important correction:** Drik Panchang is strong. It works offline, has widgets, ICS export, PDFs and eclipse pages. We win on **answers, family, voice, travel, transparency, devotion flow and Gen-Z**, while matching Drik's utility.

**Policy catch:** Android 16 "Live Updates" can't be used for automatic Rahu Kaal or calendar countdowns. Google lists "upcoming calendar events" as an inappropriate use [V]. Countdowns therefore go on a **Quick Settings tile**, widgets and the watch. Live Updates are used only for things the user starts: the vrat fast tracker, the puja timer and the eclipse tracker.

**Platform note:** Google Assistant was replaced by Gemini on Android in Sep 2026, so App Actions are effectively dead [V/E]. Use App Shortcuts and widgets for now. Revisit Android AppFunctions when they leave preview.

## 12. How we communicate so users instantly get it (clarity principles)
User complaints in competitor reviews are things like "screen just shows some nos which are not much sense to most people", AstroSage being "overwhelming", and full-screen ads on the panchang [V].

1. **Answer first** (inverted pyramid): "Good for new work until 2:40 PM" comes before "Tithi: Shukla Dashami".
2. **Three tiers** (progressive disclosure): verdict → key timings → full panchang and Sanskrit terms. Experts still get everything.
3. **Every term has a one-line gloss and an ⓘ.** For example: "Rahu Kaal: traditionally avoided for starting new things."
4. **Elders first:**
   - Bada Text mode.
   - At least 16sp, adjustable up to 200%.
   - High contrast and big touch targets.
   - Read-aloud.
   - A **labelled** mic. Google's Next Billion Users research found that a bare mic icon confuses people [V].
5. **Shared phones:** a profile switcher on the Aaj screen and an optional app lock. 18% of Indian users go online on someone else's phone [V, IAMAI 2025].
6. **Calm ads:** never on the Aaj top area, never full-screen over timings.
7. **Honest framing everywhere:** "Traditional rules, calculated for you. Not AI, not a guarantee."

## 13. Free and open content: yes, push it as a differentiator (done legally)
| Content | Status | How we use it |
|---|---|---|
| Aarti and chalisa **lyrics** (Hanuman Chalisa by Tulsidas, Om Jai Jagdish Hare, traditional aartis, Gayatri, Mahamrityunjaya, Vishnu Sahasranama, Sundarkand…) | Traditional texts, public domain [V/E] | Typeset by us in all scripts, with transliteration and our own meaning |
| **Bhagavad Gita** Sanskrit and transliteration | The `gita/gita` dataset is released under the **Unlicense** [V]. Avoid `vedicscriptures` (GPL-3.0) [V] | Sanskrit from the Unlicense data. Translations from Arnold (1885), Besant (1895) and Telang (1882), which are public domain, or our own |
| **Audio recordings** | **Not free.** Label recordings are copyrighted. We found no reliable CC0 aarti library, and some archive.org "public domain" tags are false [V] | **The "Open Aarti" project:** record our own aartis with temple singers, ship them free and ad-free, and release them **CC BY** so they spread on YouTube and WhatsApp with our name attached |
| **Calculation engine and festival rules** | Our own code | **Open-source `:engine`** under Apache-2.0, and the festival rules dataset under CC BY 4.0. The message to users: *"Our math is public. Verify it."* Keep the app, brand, design and content closed |
| Tarot art | 1909 Rider–Waite–Smith is public domain; US Games' recolouring is not [V/E] | Scan from an original print |

**Why open-sourcing the engine is safe:** competitors can copy math. They can't copy our brand, restraint, family data, content in 12 languages or the trust built from publishing the method. Open-sourcing also helps:
- **Pandits and communities** can submit corrections to the regional festival rules, which improves accuracy for free.
- **Developers** can link to and embed the engine, which brings in backlinks and press.

The recordings are your most viral asset. A free, beautifully recorded aarti that works offline, with no ads in the sacred zone, is something families will forward.

---

**Sources for §11–13:**
- [Drik Panchang](https://play.google.com/store/apps/details?id=com.drikp.core), [AstroSage](https://play.google.com/store/apps/details?id=com.ojassoft.astrosage), [Sri Mandir](https://play.google.com/store/apps/details?id=com.mandir), [Nithra](https://play.google.com/store/apps/details?id=nithra.tamilcalender), [Melooha](https://play.google.com/store/apps/details?id=com.melooha.app), [Co–Star](https://play.google.com/store/apps/details?id=com.costarastrology) (Play listings)
- [Drik sunrise method](https://www.drikpanchang.com/panchang/sunrise/panchang-sunrise.html), [Drik sankalpa](https://www.drikpanchang.com/puja-vidhi/sankalpa/deepavali/deepavali-lakshmi-puja-sankalpa.html), [Prokerala chandrashtama](https://www.prokerala.com/astrology/chandrashtama.php)
- [Live Updates docs](https://developer.android.com/develop/ui/views/notifications/live-update), [Wear Tiles](https://developer.android.com/jetpack/androidx/releases/wear-tiles)
- [Google NBU voice playbook](https://static.googleusercontent.com/media/nextbillionusers.google/en//tools/voice-playbook.pdf), [IAMAI 2025 (Business Standard)](https://www.business-standard.com/industry/news/india-to-exceed-900mn-internet-users-by-2025-125011600669_1.html)
- NN/g on the [inverted pyramid](https://www.nngroup.com/articles/inverted-pyramid/) and [progressive disclosure](https://www.nngroup.com/articles/progressive-disclosure/)
- [gita/gita dataset (via ravisiyer/gita-data)](https://github.com/ravisiyer/gita-data), [vedicscriptures (GPL-3.0)](https://github.com/vedicscriptures/bhagavad-gita), [Hanuman Chalisa on Wikidata](https://www.wikidata.org/wiki/Q2663608)
