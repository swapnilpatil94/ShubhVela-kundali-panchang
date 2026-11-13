# DESIGN.md: Look, feel, design system

## Brand idea: "Sacred, modern, calm"
The design should feel like a temple courtyard at dawn: warm, unhurried and premium. Avoid the gaudy calendar-poster look and the dark-mystic tarot look.

## Color
Material 3 color roles, generated from the seeds below. The brand palette is the default. Dynamic color is an opt-in setting so the look stays consistent on shared cards.

| Role | Light ("Prabhat", dawn) | Dark ("Ratri", night sky) |
|---|---|---|
| Primary | Saffron `#E8772E` | Marigold `#FFB25B` |
| Secondary | Temple maroon `#8C2F39` | Rose `#F2A7B0` |
| Tertiary | Peacock teal `#1F7A74` | Teal `#7FD6CC` |
| Surface | Ivory `#FFF8EE` | Indigo night `#121331` |
| Accent (use sparingly) | Gold `#C9A227` | Gold `#E7C85A` |

**Semantic time colors** (always paired with an icon and a label, never color alone):
- Auspicious windows: teal.
- Inauspicious windows: muted maroon.
- Neutral: on-surface variant.

**Contrast:** WCAG AA for all text. Never use red/green as the only signal.

## Typography
All fonts use the OFL license. Bundle subsets; use Downloadable Fonts where possible.
- **Latin UI:** Noto Sans. Display headings: *Fraunces* (opsz axis).
- **Devanagari:** Noto Sans Devanagari for UI. *Tiro Devanagari Sanskrit* for shlokas and mantras.
- **Other scripts:** Noto Sans plus Noto Serif for Bengali, Tamil, Telugu, Kannada, Malayalam, Gujarati, Gurmukhi, Oriya, Assamese (Bengali script).
- **Scale:** Material 3 type scale, body text ≥ 16sp.
- **"Bada Text" mode:** +20% text, simplified Aaj layout for older users. Offer it at onboarding.
- **Layout robustness:**
  - Support font scale up to 200% with no clipping.
  - Test every Indic script, since Android 16 ignores `elegantTextHeight`.
  - Never set fixed heights on text containers.

## Shape and motion
- **Library:** Material 3 Expressive (`material3` ≥ 1.5 when stable; until then 1.4 plus the expressive APIs behind opt-in).
- **Shapes:** rounded 20–28dp cards. Lotus-petal shape for hero elements (`MaterialShapes`, or a custom `RoundedPolygon`).
- **Motion:** use the spring `MotionScheme`. It should be gentle: no bounce on sacred screens.
- **Signature animations** (Compose only, no Lottie, to keep the app small):
  - Diya flame flicker.
  - Bead advance on the jap mala.
  - Sun arc across the day timeline on Aaj.
  - Bond score "orbit" reveal.
- **Haptics:**
  - A light tick per jap bead.
  - A double tick at 108.
  - A soft confirm on unlock.

## Key components (core:designsystem)
- `TimeWindowCard`: current or next window, with a countdown ring, start and end times, and an explain sheet.
- `DayArc`: sunrise-to-sunset arc showing the Rahu kaal, Abhijit and other segments.
- `PanchangGrid`: five limbs, each with an end time and a tap-to-explain sheet.
- `ChartView`: North diamond, South square and East chart styles, drawn on Canvas. It must be accessible: a content description lists the planets per house.
- `DashaTimeline`: horizontal timeline that zooms MD → AD → PD.
- `BondCard`: two avatars or initials, a score orbit and 5 axis bars.
- `ShareCardFrame`: 9:16 and 1:1 templates, with the wordmark and short link.
- `RewardedUnlockButton`: shows "Watch ad" next to "Go ad-free ₹49", with the price localized.
- `ExplainSheet`: every term has a sheet with a plain-language explanation, a "How calculated" section and the tradition note.

## Layout
- Edge-to-edge (enforced), with insets handled on every screen.
- Predictive back through Navigation 3.
- Adaptive layout via `NavigationSuiteScaffold` and `ListDetailPaneScaffold` (phones, foldables, tablets).
- Splash: SplashScreen API showing the brand mark only, with no ad.

## Imagery
- **Original deity illustrations only,** commissioned with full rights transfer. The style is a modern flat line-art with gold accents.
- Do not use AI images of deities or copied calendar art.
- Never place deity imagery next to ads.
- **Icons:** Material Symbols Rounded, plus a custom set (planets, nakshatras, 14 yoni animals, rashis, diya, mala).

## Onboarding (≤ 60 s, no account)
1. Language. Auto-suggest from the device locale; show 12 languages in their native names.
2. City: search, or one-tap approximate location.
3. Tradition: North (purnimanta), South/West (amanta), or a regional preset. Offer "Not sure", which picks a preset by city.
4. Optional: the user's own birth details, "for your personal day and kundli". Offer Skip.
5. Optional reminders, with the notification permission requested in context.

## Performance budgets
- Cold start ≤ 1.2 s on a mid-range device and ≤ 2 s on a 3 GB device, using Baseline Profiles.
- 60 fps on scrolling lists.
- The Aaj screen renders entirely from local computation, in ≤ 150 ms after first frame.
- Base AAB download ≤ 20 MB (excluding on-demand language and audio packs).
