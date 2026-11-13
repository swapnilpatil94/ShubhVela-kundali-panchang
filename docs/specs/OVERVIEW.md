# OVERVIEW: contradictions, R1 critical path, owner checklist

Source: MASTER.md v1.0 (13 Nov 2026), read in full once. The older `docs/*.md` copies were not read; MASTER wins over them.
Tie-break used everywhere: more private / more accurate / fewer ads (Part 1 Workflow). Log each resolution in `docs/DECISIONS.md` when its milestone runs.
VERIFY = not decidable from MASTER; needs owner or pandit input before it ships.

## (a) Contradictions and resolutions

**Process and routing**
- C0 Header "never load this whole file" vs App. B Prompt 1 (full read). -> One-time exception for this file only.
- C1 Part 12 "Read:" lines cite ENGINE.md, PRODUCT.md, `docs/PROMPTS.md` etc. -> Read MASTER parts: PRODUCT=2, ENGINE=3, ARCHITECTURE=4, SECURITY=5, DESIGN=6, MONETIZATION=7, SOCIAL=8, I18N_CONTENT=9, OPS=10, ROUTING=11, PROMPTS=App. B.
- C2 Part 12 model labels (M00 OPUS, M13 SONNET high...) vs Part 11 table. -> Part 11 wins (stated precedence).
- C3 `/milestone` step 1 writes a spec whenever one is missing; Part 11 gives no spec for M00, M10, M13, M15b, M16, M17, M18, M18b, M18c. -> Part 11 wins; those build from Part 12 plus the cited parts.
- C4 Part 12 build lists for M08/M09/M13 omit Part 2 §M R1 items and B9. -> Part 2 wins (stated). Placement chosen here: M07 = M9 proof panel (in `ExplainSheet`); M08 = M3, M4, M5, M8, M16; M09 = B9 (mr, gu), M1, M7; M10 = M2; M13 = M6.
- C5 M08 needs explain text and A7 rashifal, but content arrives in M11. -> M08 codes against `:content` interfaces with flagged placeholders; A7/H1/H2 are wired in M11.

**Engine**
- C6 Limb end times: Part 3 §1 (Precision <= 5 s; Base <= 30 s typical, <= 120 s worst) vs M03 accept (±60 s). -> §1 applies against the oracle. ±60 s applies only to published-reference spot checks (§8); log differences in `docs/ENGINE_DIFFS.md`.
- C7 M01 accept says "tolerances in §8"; the numbers are in §1. -> Use the §1 table.
- C8 Golden counts: §8 (10,000 positions; rise/set 10 cities × 400 days) vs M01 (5,000; 2,000). -> §8, the larger set.
- C9 Part 4 "`:engine` has no deps except kotlinx" vs §2 Base backend `io.github.cosinekitty:astronomy`. -> Allowed in `jvmMain`/`androidMain` only; `commonMain` stays kotlinx-only.
- C10 M02 ayanamsa list (KP, Raman, True Chitra) vs §4 (adds YUKTESHWAR; KP_NEW/RAMAN/YUKTESHWAR are VERIFY). -> Enum holds all; only LAHIRI and TRUE_CHITRA are enabled until owner fixtures pass.
- C11 M02 "dasha dates match the oracle" but the §8 oracle emits no dashas. -> Add an independent Python Vimshottari generator to `tools/oracle` (from oracle Moon longitude); tolerance per §1 (exact to the minute).
- C12 M04 "R1 ships 2 of Tamil/Malayalam/Bengali/Odia" vs B9, Part 9, Part 13 (R1 = Marathi + Gujarati). -> R1 regions are mr and gu (amanta; Gujarat year from Kartika Shukla 1). The four solar calendars are built and tested in M04 but stay flagged off until pandit review (M21).
- C13 Rules in R1 scope with no definition in Part 3: Dishashool, Do-ghati, Vijaya, Godhuli (A3-A5, M03), Panchak subtypes, chandrabala "good houses", Manglik partial/full (Part 8). -> VERIFY. Table-driven, hidden behind a flag until fixture and sign-off. Do not invent.
- C14 VERIFY items inside R1 builds: Gowri (ta is R2), PRINTED_6AM tables, Yogini, night Gulika/Rahu, high-latitude rule. -> Engine may implement from `rules/` tables; UI flag off until sign-off. B10 stays R2.
- C15 §8 PanchangDay <= 2 ms vs year <= 400 ms (about 1.1 ms/day). -> Both hold; the year budget is binding.

**Languages and content**
- C16 "12 languages" (Parts 1, 2 L3, 5) vs Part 9 table of 13 (adds ne, R3). -> 12 is the promise and the DPDP notice set; ne is an R3 extra, VERIFY with owner.
- C17 Part 9 bundles "the R1 regional language" (singular); R1 has two. -> Bundle en, hi, mr, gu (offline, no fetch).
- C18 Pack hosting: Parts 5, 9 "Cloudflare R2" vs Part 10 §A "avoid R2". -> Cloudflare Pages `/packs/`, <= 25 MiB per file. ("R2" elsewhere means Release 2.)
- C19 Part 2 §G "R1 core" lists G5 audio; M13 builds G1-G4, G7, G8 only. -> R1 audio is only what G1, G7 and M4 need; the G5 library and G6 come after R1.

**Ads and consent**
- C20 First session: Part 7 "no full-screen ad" vs Parts 1, 2 and M15 accept "no ads / no ad requests". -> No ad request of any format in session 1; the UMP consent update still runs.
- C21 Part 2 gates D2 and J1-J4 as F (no ad on screen); Part 7 puts a native on Kundli overview and a banner on tools; Part 10 §C has no Kundli or rashifal unit. -> Part 2 wins: no ads on Kundli or J tools; `pp_banner_tools` is unused in R1; rashifal reuses `pp_native_aaj` (VERIFY with owner).
- C22 A1-A6 are F and "never over the timing screens" vs a native on Aaj. -> One native, placed only after the A7 rashifal block below the fold. No full-screen format opens over Aaj or any timing/countdown screen.
- C23 Mediation: Parts 4, 7 vs Part 10 §C. -> AdMob only at launch (stated). The bidder lists also differ (Part 7 vs Part 10); VERIFY after launch.
- C24 M15 "SSV hooks" vs Part 10 §C (SSV is R3). -> Interface only in R1; no Worker.
- C25 Part 7 "India needs no CMP" vs Part 5 DPDP "consent for analytics and personalized ads". -> UMP for EEA/UK/CH/US; elsewhere an in-app consent for both, default denied. Legal review confirms.
- C26 Play target audience 18+ vs under-18 treatment; age is unknown when the profile is skipped. -> Keep both. Unknown age follows the consent state in C25; VERIFY in legal review.

**Privacy and platform**
- C27 Part 8 notification example carries a name vs Part 1 "no personal data in notifications". -> Lock-screen (public) version has no names; names only in the private version when unlocked (Part 5). Never birth data.
- C28 Part 5 "one-shot coarse location" vs M3 "when the location changes". -> Travel mode triggers on `TIMEZONE_CHANGED` plus an opt-in one-shot coarse fix in the foreground. No background location.
- C29 Permissions used but absent from Part 5: `WRITE_CALENDAR` (M7), `RECEIVE_BOOT_COMPLETED` (§10), `INTERNET`, `ACCESS_NETWORK_STATE`, AD_ID, `<queries>` for Instagram. -> The introducing milestone adds each to Part 5 and `scripts/manifest_audit.sh`. M1 voice uses the system recogniser intent; no `RECORD_AUDIO`.
- C30 Part 5 endpoints "nothing else" vs Firebase/GMA/UMP/Play SDK traffic. -> "Nothing else" means first-party endpoints; SDK traffic is declared in Data safety.
- C31 Referral reward (Parts 7, 8: friend active 3 days) vs no server, no accounts, referrer carries only a random id. -> No mechanism exists. R1 captures the install referrer only; the reward is deferred. VERIFY design with owner.
- C32 Part 10 §A "Workers (Bond teaser)" vs M18b static JS page. -> Static Pages page; no Worker in R1.
- C33 M18b needs the Play signing SHA-256, which exists only after the owner creates the Play app (M18c). -> M18b ships a placeholder; "App Links verify" closes after the first internal upload.
- C34 Listings: M18 (en, hi) vs M18c and Part 10 §E (en, hi, mr, gu). -> M18 drafts en and hi; M18c adds mr and gu after native review.

## (b) R1 critical path

Run order is fixed (App. B Prompt 2), one milestone per session. Dependencies below are derived from module and data needs.
CRITICAL = engine-dev writes the named part; everything else in that milestone is Build (implementer). Spec column per Part 11.

| # | ID | Depends on | Spec | Type | CRITICAL part |
|---|---|---|---|---|---|
| 1 | M00 Bootstrap | - | no | Build | - |
| 2 | M01 Astronomy core | M00 | yes | CRITICAL | backends, time scales, rise/set |
| 3 | M02 Jyotish core | M01 | yes | CRITICAL | ayanamsa, houses, dashas |
| 4 | M03 Panchang | M01, M02 | yes | CRITICAL | limb solver, windows |
| 5 | M04 Calendar and festivals | M03 | yes (xhigh) | CRITICAL | rule evaluator, Ekadashi, solar rules |
| 6 | M05 Geo and tz | M00 | yes | Build | - |
| 7 | M06 Secure data | M00 | yes | CRITICAL | key management, snapshot crypto |
| 8 | M07 Design system, shell, onboarding | M00, M05, M06 | yes | Build | - |
| 9 | M08 Aaj, widgets, tile, notifications | M03, M05, M06, M07 | yes | Build | - |
| 10 | M09 Calendar UI, regional views, ICS, "Should I...?" | M04, M07, M08 | yes | Build | - |
| 11 | M10 Kundli and family | M02, M04, M05, M06, M07 | no | Build | - |
| 12 | M11 Content packs and rashifal | M02, M03, M08 | yes | CRITICAL | signature verify |
| 13 | M12 Bonds and sharing | M02, M06, M07, M11 | yes | CRITICAL | Bond Code codec |
| 14 | M13 Bhakti and Sankalp | M03, M06, M07, M11 | no | Build | - |
| 15 | M14 PDF reports | M10, M11, M12 | yes | Build | - |
| 16 | M15 Ads, UMP, billing | M08-M14 | yes (policy) | Build | - (architect policy pass after review) |
| 17 | M15b Observability and analytics | M15 | no | Build | - |
| 18 | M16 Localization | M11, all UI strings | no | Build | - |
| 19 | M17 Performance, size, a11y | M08-M16 | no | Build | - |
| 20 | M18 Security audit | M00-M17 | no | Build (fixes) | - (architect audit) |
| 21 | M18b Cloudflare site and infra | M11, M12 | no | Build | - |
| 22 | M18c Play release pipeline | M18, M18b | no | Build | - (human presses every final button) |

- Longest chain: M00 -> M01 -> M02 -> M03 -> M04 -> M09 -> M15 -> M17 -> M18 -> M18c -> closed test (12 testers × 14 days) -> production access -> staged rollout 10/50/100%.
- Gate for "production ready": golden tests green, festival fixtures signed off, M18 audit with no open High items.
- Long-lead owner items to start now: pandit fixtures, jyotish writer, native reviewers, domain, tester recruitment.

## (c) TODO(owner) checklist

- [ ] Domain — blocks M12, M18b, M18c — buy `<DOMAIN>`, create the Cloudflare Pages project, set up `support@<DOMAIN>` Email Routing
- [ ] Firebase project — blocks M15, M15b — create `parivaar-panchang`, add the Android app, supply `google-services.json`, restrict the API key by package + SHA-256
- [ ] AdMob ad units — blocks M15, M18b, M18c — app ID and the 8 unit IDs (Part 10 §C names), GDPR and US-states messages, blocking controls, publisher line for `app-ads.txt`
- [ ] Keystore — blocks M18c — generate `upload.jks` with `keytool`, store it outside the repo with a password-manager backup, write `keystore.properties`, enroll in Play App Signing
- [ ] Play service account — blocks M15, M18c — create the service-account key, grant it in Play Console, supply its path via env or `local.properties`
- [ ] Facebook App ID — blocks M12 — Meta app ID for the Instagram Stories `source_application` field
- [ ] Jyotish writer — blocks M11, M14, M16 — hire a consultant plus editor; deliver en and hi content masters (about 160k words each) that pass the tone rules
- [ ] Native reviewers — blocks M11, M16, M18c — hi, mr, gu reviewers to lock `terms/<lang>.json` and review 100% of user-facing text and store listings
- [ ] Pandit fixtures — blocks M02, M03, M04, M09, M10 — signed-off fixtures: festivals 2024-2030 × 7 cities per region, Ekadashi/parana, every VERIFY table in Part 3, the mr and gu almanac views
- [ ] Deity art — blocks M13, M18c — commissioned original illustrations with full rights transfer (no AI images, no copied art)
- [ ] Audio recordings — blocks M08, M13 — own or royalty-free recordings with licence proof (Sanskrit mantras for read-aloud, aarti, Om)
- [ ] Legal review — blocks M15, M18, M18b, M18c — lawyer sign-off on privacy policy and terms (en, hi, mr, gu), DPDP minors' consent, the on-device fiduciary question, C25/C26
- [ ] App name and applicationId — blocks M00, M18c — trademark-cleared name and a package ID (MASTER gives neither; VERIFY)
- [ ] GitHub private repo — blocks M00 — create the repo, enable Actions and Renovate, add CI secrets
- [ ] Content-pack signing key — blocks M11, M18b — run `tools/sign/keygen`; keep the private key outside the repo; the public key goes in the app
- [ ] Cloudflare access — blocks M18b — run `wrangler login`, create a Pages-deploy-only API token, set DNS
- [ ] Play Console app — blocks M18c — press Create, complete declarations (18+, Data safety, IARC), confirm account type (personal after Nov 2023 or organisation; VERIFY)
- [ ] Closed-test testers — blocks M18c — at least 12 testers opted in for 14 continuous days; recruit early
- [ ] Historical timezone sources — blocks M05 — sources and fixtures to verify `rules/tz_overlay.json`
- [ ] Reference almanacs — blocks M03, M04 — Rashtriya Panchang and IAE tables for 2026-27 for manual spot checks
- [ ] Owner decisions on VERIFY resolutions — blocks M03 (C13, C14), M12 (C31), M15 (C21, C25, C26), M16 (C16) — confirm or correct each
- [ ] Post-publish steps — blocks M18c — link AdMob to the Play app, verify `app-ads.txt`, press Roll out at each rollout stage
