# I18N_CONTENT.md: Languages, content library, reports in every language

## Languages
| Code | Language | Script | Release |
|---|---|---|---|
| en | English | Latin | R1 |
| hi | Hindi | Devanagari | R1 |
| mr | Marathi | Devanagari | R1 |
| gu | Gujarati | Gujarati | R1 |
| bn | Bengali | Bengali | R2 |
| te | Telugu | Telugu | R2 |
| ta | Tamil | Tamil | R2 |
| kn | Kannada | Kannada | R2 |
| ml | Malayalam | Malayalam | R2 |
| or | Odia | Oriya | R2 |
| pa | Punjabi | Gurmukhi | R3 |
| as | Assamese | Bengali | R3 |
| ne | Nepali | Devanagari | R3 |

The R1 set covers Hindi, the West Indian languages and the matching regional calendars. Re-check this choice against Play Console country and language data after launch.

## Two kinds of text
1. **UI strings** live in `strings.xml` per locale, with plurals via ICU. Turn on the per-app language picker (`generateLocaleConfig = true`) and the AppCompat backport.
2. **Interpretation content** lives in versioned, signed content packs per language: `content/<lang>/pack.cbor`.
   - English, Hindi and the R1 regional language are bundled in the app.
   - Other languages download through Play Asset Delivery (on-demand) or from R2.

## Terminology tables
Most of these names **are not translated by machine**. Each language has its own traditional name for every item.
- Tithi (30), nakshatra (27), yoga (27), karana (11), vaar (7), masa (12), rashi (12), graha (9), ritu (6), samvatsara (60).
- Festival names per region.

Rules:
- Store them as `terms/<lang>.json`. A native expert enters them once and they are locked.
- The interpretation text references terms by ID, for example `{nak:3}`, and the term is resolved at render time.
- Numerals: choose Latin or native digits per user setting (`android.icu.text.NumberFormat` with the `@numbers=deva` keyword, and similar for other scripts).

## Content library
Write the content ourselves from classical principles. Do not reuse modern copyrighted translations.

| Block | Count | Words each | Words total |
|---|---|---|---|
| Planet in sign | 9×12 = 108 | 120 | 13k |
| Planet in house | 9×12 = 108 | 120 | 13k |
| House lord in house | 12×12 = 144 | 80 | 11.5k |
| Lagna | 12 | 300 | 3.6k |
| Nakshatra (+4 padas) | 27 + 108 | 250 / 60 | 13.2k |
| Dasha and antardasha | 9 + 81 | 200 / 80 | 8.3k |
| Yogas and doshas | ~80 | 150 | 12k |
| Transit (gochar) phrases | 9 planets × 12 houses × 3 variants | 40 | 13k |
| Koota and bond axes | ~150 | 60 | 9k |
| Panchang element explanations, festival guides, vrat kathas | ~400 | 150 | 60k |
| **Total** | | | **≈ 160k words per language** |

### Pipeline
1. Domain writers produce the English and Hindi masters. Hire one paid jyotish consultant plus an editor.
2. The tone review (PRODUCT.md §Tone) is an automated lint (banned-word list) followed by a human pass.
3. Machine translation runs through the Claude API Batch endpoint, at 50% of the normal price.
   - Model: `claude-haiku-5-5` for bulk work; `claude-sonnet-5-5` for festival guides and kathas.
   - The prompt includes the glossary (`terms/<lang>.json`), the style guide and a "do-not-translate" list.
   - API cost is a few dollars per language, so it is negligible.
4. A native reviewer for each language checks 100% of the user-facing text. This is the real cost: estimate ₹0.4–1 per word, so roughly ₹0.6–1.6 L per language (ESTIMATED; get quotes).
5. Build the pack: JSON to CBOR, then an Ed25519 signature, then a version bump.
6. Run the content tests: every ID exists in every language, every term placeholder resolves, and no text is longer than the max length set for the component.

## Reports in every language (on device)
- **Template.** The report is a list of sections (`ReportSpec`). Each section has blocks of text, tables and charts, and is filled from the content pack and the chart facts.
- **Rendering.** Use `android.graphics.pdf.PdfDocument` + `StaticLayout`/`Canvas`. The platform text stack (HarfBuzz/Minikin) shapes Indic conjuncts correctly. **Do not use iText or PdfBox-Android.** Their Indic shaping is broken or the license does not fit.
- **Fonts.** Embed subset Noto fonts for each script.
- **Layout.** A4 page with 18 mm margins, a header carrying the user's name, page numbers in the chosen numeral system, a table of contents, and charts drawn as vectors.
- **Speed and footprint.** A 100-page report renders in under 8 s on a mid-range device, runs in a WorkManager job with a progress UI, and the file stays under 6 MB.
- **Preview.** Use `PdfRenderer` for a preview before sharing.
- **Known limitation.** Indic text in these PDFs *displays* correctly, but copy and search can be wrong: Skia maps one glyph to one codepoint and writes no `/ActualText`. That is acceptable because the reports are for reading and printing. If searchable text is needed later, add an invisible text layer.

## Quality gates
- Screenshot tests across all locales (Roborazzi or Paparazzi) for Aaj, Kundli, Bond card and the report cover.
- A pseudo-locale (`en-XA`) run on CI to catch text expansion and clipping.
