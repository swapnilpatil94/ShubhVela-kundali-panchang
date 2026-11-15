# REDESIGN_PROMPT.md: move the existing app to ShubhVela "Ratna Glass"

## Before you paste (2 minutes)
1. In the app repo, commit everything first so the redesign can be undone:
   ```bash
   git add -A && git commit -m "Before Ratna Glass redesign"
   ```
2. Put `shubhvela_design_bundle.txt` in the repo root and unpack it:
   ```bash
   python3 shubhvela_design_bundle.txt .
   ```
   It creates `design/DESIGN_V2.md`, `design/REDESIGN_PROMPT.md`, `design/shubhvela-screens.html` and `design/screens/01–28.png`, with every checksum verified.
3. Start Claude Code in the repo (`claude`, or `claude --chrome`). Keep the main model on **Sonnet** (`/model sonnet`), run `/clear`, then paste the prompt below **once**.

---

## THE PROMPT (copy everything inside the box)
```
We are re-skinning this app to the new ShubhVela "Ratna Glass" design.
Business logic, the engine and data stay exactly as they are; only UI, theme, navigation and assets change.

Sources of truth (read in this order, nothing else):
1. CLAUDE.md
2. design/DESIGN_V2.md (all of it). It supersedes MASTER.md Part 6.
3. The reference PNGs in design/screens/. Open ONLY the PNG for the screen you are working on, to save tokens.
   design/shubhvela-screens.html is the animated version; don't read its source unless a detail is unclear.

Do this as milestone M07R, using the routing in MASTER.md Part 11:

STEP 1 · Plan (architect subagent, Fable)
Write docs/specs/M07R.md (≤150 lines) containing:
 (a) an inventory of the screens and components that already exist in this repo, mapped to the reference numbers 01–28 (mark which exist and which are new);
 (b) the token and theme file plan;
 (c) the component list with Kotlin signatures, from DESIGN_V2 §8;
 (d) the order of work, R1 to R9 below;
 (e) acceptance tests;
 (f) TODO(owner) assets.
Also update docs:
 - Rename the user-facing brand to "ShubhVela" (app_name, store copy, plan name "ShubhVela Plus", share-card wordmark "shubhvela.app").
 - Do NOT change applicationId or package names.
 - Replace MASTER.md Part 6 with a 10-line summary that points to design/DESIGN_V2.md.
 - Log each decision in docs/DECISIONS.md.

STEP 2 · Build (implementer subagent, Sonnet). One sub-step at a time, building and testing after each:
 R1 Tokens: Color, Typography (Cormorant Garamond, Manrope, Tiro Devanagari Hindi/Sanskrit, Noto Indic), Shapes, Spacing, MotionScheme, haptics map, the Vaar Ratna gem resolver (weekday → gem). Moods: Ratna Night (default), Utsav (festival days, automatic), Chandan (light), Paper (regional calendars).
 R2 Surfaces: Lacquer (+hero, +warn) and GlassSurface using Haze, with the fallback ladder and Reduce Transparency. Glass only on chrome.
 R3 Navigation: the floating glass dock with the live Aaj orb (DESIGN_V2 §5). The ring shows the time left in the current choghadiya and turns gold or kumkum; the diya is lit or unlit; the active tab takes the gem tint; SEGMENT_TICK haptic; hide on scroll; hidden in immersive sacred screens; full TalkBack labels.
 R4 Sky header: graded image slot per graha, the reading shade, the TEXT-SAFE ZONE luminance cap (≥7:1), the hora planet slot, the GemChip. Use placeholder gradients until the owner supplies the images.
 R5 Planets: the AGSL sphere renderer (CRITICAL; give the shader math to the engine-dev subagent), lit from the engine Sun vector; Moon phase and position angle; Saturn ring tilt; WebP-frame fallback below API 33; pause rules.
 R6 Re-skin every EXISTING screen to match its reference PNG (Aaj = 04–08, Calendar = 09–12, and any others already built). Aaj order is AbhiCard → SuryaPath → NextList → SkyEventCard (only on event days) → PanchangList. Move the Kaal Chakra to Pro view, widgets and the watch only.
 R7 Screenshot tests (Roborazzi) for each re-skinned screen in en and hi, at font scale 1.0 and 2.0. Add a test that samples the pixels under the sky-header text and asserts ≥7:1.
 R8 Accessibility: Bada Text 200% with no clipping; Reduce Motion gives static frames; Sunao read-aloud entry points; content descriptions for the orb, the SuryaPath and the chart.
 R9 Performance: Baseline Profile refresh. Macrobenchmark for cold start (≤1.2 s mid-range) and Aaj scroll (no janky frames); one animated planet per screen, at most.

STEP 3 · Review (reviewer subagent, Opus)
Compare each re-skinned screen's screenshot with its reference PNG and with the DESIGN_V2 rules: glass only on chrome; one hero per screen; gold only for auspicious; no emoji and no Om in chrome; ads never near timings or in sacred zones; contrast; sacred zones. Return PASS or a numbered list of fixes. Allow at most 2 fix loops.

Rules:
- Never change :engine outputs or business logic.
- All strings in resources.
- Never type passwords or press final console buttons.
- Debug builds use test ad IDs only.
- If a reference shows sample numbers, bind them to real engine data.
- Ambiguity → choose the more readable, calmer, more private option and log it in DECISIONS.md.
- Token hygiene: read line ranges, open one PNG at a time, never paste file contents, and end each sub-step with a 3-line summary.

When M07R passes: commit "M07R: Ratna Glass re-skin". Update docs/STATUS.md. Show me the TODO(owner) list (sky images, planet textures, icon set, deity art, audio). Then STOP and wait for me.
```

Optional, so it runs until done with a cap: paste this right after the prompt:
```
/goal docs/specs/M07R.md acceptance criteria pass and the reviewer returns PASS for all re-skinned screens, or stop after 120 turns
```

---

## After M07R: what to do next
1. **Check it yourself.** Run the app (`./gradlew :app:installDebug`) and compare it with `design/screens/*.png`. If something looks off, tell Claude something like: "Screen 04: the Surya Path labels overlap. Match the PNG."
2. **Continue the remaining milestones as before** (`/clear`, then `/milestone M10`, and so on). Each new screen must use the DESIGN_V2 components and its reference PNG (13–28). Add this line to each milestone request: "Use design/DESIGN_V2.md components; match design/screens/NN.png."
3. **Do the owner tasks while it builds:**
   - Pick and grade 9 sky images (NASA/ESA-Webb) and record the credits.
   - Download the Solar System Scope 2K textures and the NASA Moon map.
   - Commission the icon set and the deity art.
   - Record the aartis.
   Drop the files into `assets-src/` and tell Claude: "Wire the real sky images and textures (R4/R5 placeholders)."
4. **5-second test with 5–8 people** on the Aaj screen: "Is it a good time now? When should you avoid starting something?" Then fix whatever confuses them.
