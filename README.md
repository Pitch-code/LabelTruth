# LabelLens

Scan a food barcode or photograph an ingredient label, and find out what is
actually in it: what each ingredient is, why it is there, whether it carries any
recognised concern, and whether it matters for *you* specifically.

**Status: Phase 1.** The scanning pipeline, offline ingredient dictionary,
scoring engine and UI all work and build into an installable app. The dictionary
currently holds 110 curated entries; expanding it is the main Phase 2 work.

---

## How the data is split, and why

This is the central design decision of the project.

| Data | Size | Where it lives | Why |
|---|---|---|---|
| **Ingredient dictionary** — what "Sodium Benzoate" is | Small, slow-changing | **Bundled in the app** | Instant, works offline, no server cost, no privacy exposure |
| **Barcode → product** | Millions of products, changes daily | **Online** ([Open Food Facts](https://world.openfoodfacts.org)) | Impossible to ship, and stale within days |
| **Reading a printed label** | n/a | **On-device** (ML Kit OCR) | Works even when a product is in no database at all |

```
Camera ──┬── barcode found ──→ Open Food Facts ──→ ingredient text ──┐
         │                                                           │
         └── no barcode? ────→ on-device OCR ─────→ ingredient text ──┤
                                                                      ▼
                                            parse → match → score → verdict
                                          (all against the bundled dictionary)
```

Nothing about the user is uploaded. Camera frames are processed on-device and
never leave the phone. The only network request is the barcode you scanned.

---

## Build sizes

Measured from an actual release build:

| Artifact | Size |
|---|---|
| Debug APK (all ABIs, unminified) | 80 MB |
| Release AAB (uploaded to Play) | 34 MB |
| **Actual download on a modern 64-bit phone** | **~14 MB** |

Play splits the bundle per device, so users only download the one CPU
architecture they need. 14 MB is unremarkable for an app store listing.

---

## Building it

Requirements: JDK 17 or 21, and the Android SDK with platform 36 + build-tools 36.

```bash
# Point the build at your SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew :app:assembleDebug     # debug APK for testing on your own phone
./gradlew :app:bundleRelease     # AAB for uploading to Play Console
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Installing it on your phone

1. Enable **Developer options** (tap Build number 7 times in Settings → About phone).
2. Turn on **USB debugging**, connect by cable, and run `adb install -r app-debug.apk`.
3. Or copy the APK to the phone and open it, allowing installation from unknown sources.

The debug build uses application ID `com.labellens.app.debug`, so it installs
alongside a release build rather than replacing it.

---

## Architecture

```
com.labellens.app
├── core/            TextNormalizer  — normalisation, E-number extraction, Levenshtein
├── domain/
│   ├── IngredientListParser        — splits raw label text into ingredient tokens
│   ├── IngredientMatcher           — token → dictionary entry, 5 strategies
│   ├── ScoreEngine                 — 0-100 score + personalised alerts
│   └── model/                      — Ingredient, RiskTier, Analysis, HealthProfile
├── data/
│   ├── local/                      — Room: ingredients, synonyms, scan history
│   ├── seed/                       — imports the bundled JSON dictionary on first run
│   ├── remote/                     — Open Food Facts client (OkHttp)
│   ├── prefs/                      — health profile (DataStore, device-only)
│   └── repo/                       — AnalysisRepository ties it together
└── ui/                             — Compose: scanner, result sheet, detail, history, profile
```

### Matching strategy

Real labels are messy, so a token is resolved in descending order of certainty:

1. **Exact** normalised name
2. **Synonym** (479 mapped aliases)
3. **E-number** found anywhere in the token — `Preservative (E 211)` → `E211`
4. **Containment**, longest match wins — `skimmed MILK powder` → `Milk`
5. **Fuzzy** (Levenshtein ≤ 2) to survive OCR errors — `SODIUM BENZQATE` → `Sodium Benzoate`

Anything still unresolved is shown as *"Not in our database yet"* rather than
guessed at.

### Verified parser cases

These real-world label strings were checked against the parser during
development and all tokenise correctly:

- `Sugar, palm oil, HAZELNUTS 13%, skimmed MILK powder 8.7%, low-fat cocoa 7.4%, emulsifiers: lecithin [SOYA]; vanillin. Gluten free`
- `Acid: Citric Acid (E330), Colours (E102, E133), Preservative: Potassium Sorbate, Sweeteners (Aspartame, Acesulfame K). Contains a source of phenylalanine.`
- `INGREDIENTS: WHEAT FLOUR, VEGETABLE OIL (PALM), SALT 1.2%, SODIUM BENZQATE (E 211)`

Handled specifically: percentages, nested and square brackets, functional-class
prefixes (`Emulsifier:`), sentence-ending full stops versus decimal points,
label boilerplate (`Gluten free`, `May contain...`), and the same additive
appearing twice under different names.

---

## Scoring

Starts at 100 and deducts per ingredient by tier, weighted by position in the
list, since ingredients are declared in descending order of quantity.

| Tier | Penalty |
|---|---|
| No known concern | 0 |
| Minor concern | 4 |
| Moderate concern | 12 |
| Best avoided | 28 |
| Unrecognised | 1 |

Deliberately simple and explainable: every deducted point traces to a named
ingredient, so the app can always show its reasoning. An opaque score would be
both less trustworthy and much harder to defend if challenged.

---

## Data sources and licensing

Attribution is a licence condition, not a courtesy.

- **Product data:** [Open Food Facts](https://world.openfoodfacts.org), under the
  [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/).
  The API client sends an identifying `User-Agent`, as Open Food Facts requires.
- **Ingredient safety positions:** EFSA scientific opinions, WHO/IARC monographs,
  US FDA determinations, and EU Regulations 1333/2008 (additives), 1169/2011
  (allergens) and 1334/2008 (flavourings). Every dictionary entry carries its own
  source, shown in the app's ingredient detail screen.

**Every risk rating in the dictionary is traceable to a published regulatory or
scientific position.** None are invented, and none may be added without a source.

---

## Health and safety disclaimer

LabelLens is informational and **not medical advice**. Ratings are general
guidance and cannot account for an individual's medical history. Users are told
to check the physical packaging for allergen information and to consult a
healthcare professional. A disclaimer is shown on first launch and must be
acknowledged, and it repeats on every result and detail screen. This is required
by Google Play's health-related policies as well as being the honest thing to do.

---

## Security posture

The strongest decision available was to **store nothing**: no account, no
analytics, no user ID, no cloud sync. Health profile and scan history stay on the
device. If there is no user database, there is nothing to breach.

Implemented:

- No API keys or secrets in the app
- `cleartextTrafficPermitted="false"` — all traffic must be HTTPS, so a
  downgrade attack on a hostile network cannot silently succeed
- R8 minification and obfuscation on release builds
- Backup and device-transfer extraction disabled, so nothing leaks off-device
- Release signing config read from a git-ignored `keystore.properties`

Still to do before launch:

- Move the Open Food Facts call behind our own caching proxy, which then allows
  certificate pinning against a certificate we control
- Play Integrity API, to let that backend reject tampered app copies
- Enable Play App Signing, and turn on 2FA/passkeys on the Play Console account

---

## Known limitations

- **The dictionary is 110 entries.** Comprehensive coverage needs Phase 2, which
  imports the Open Food Facts ingredient taxonomy, EU E-number lists and FDA
  inventories. There is no complete list of all ingredients anywhere, so the
  honest target is broad coverage plus an explicit "not recognised" state.
- **Runtime behaviour is not yet verified on a physical device.** The project
  compiles and packages cleanly, and the parsing and matching logic was validated
  against real label strings, but the camera, OCR and barcode flows need testing
  on real hardware. That is the immediate next step.
- Food only. Cosmetics (INCI) is planned for Phase 3.
- English only.
- The seed dictionary is imported into Room on first launch. Once it reaches tens
  of thousands of rows this should become a pre-built SQLite file loaded with
  Room's `createFromAsset`, so there is no import at all.

---

## Google Play launch checklist

This account is a **personal** developer account, which means the closed-testing
requirement applies and it sets the timeline.

- [ ] Register the Play developer account (**$25 one-time**; verification can take days)
- [ ] Enable 2FA / passkey on the account
- [ ] Host a privacy policy at a public URL — **Play requires this**, and GitHub Pages is fine
- [ ] Generate an upload keystore, and enable Play App Signing
- [ ] Complete the Data safety form (declare: barcode sent to a third-party API; no data collected)
- [ ] Complete the content rating questionnaire
- [ ] Upload store assets: icon (`brand/play-store-icon-512.png`), feature graphic, screenshots
- [ ] Upload the AAB to **internal testing** first, and check the automatic pre-launch report
- [ ] Run **closed testing with at least 12 testers, opted in for 14 continuous days**
- [ ] Apply for production access

The 14 days is calendar time and nothing shortens it, so start closed testing
with a rough-but-working build rather than waiting for polish.

---

## Brand assets

| File | Use |
|---|---|
| `brand/logo-icon.svg` | Master icon artwork |
| `brand/play-store-icon-512.png` | Play Store listing icon (512×512, required) |
| `brand/logo-wordmark.svg` / `.png` | Wordmark for the listing and website |
| `app/src/main/res/mipmap-anydpi-v26/` | Adaptive launcher icon (vector, with themed-icon layer) |

Design intent: green for health and trust (red is reserved strictly for genuine
risk, so it keeps its meaning), rounded forms to read as safe rather than
clinical, corner brackets that say "scanner" without words, and a checkmark for
the reassurance the user is actually after.
