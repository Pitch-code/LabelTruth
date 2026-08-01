# LabelTruth

Scan a food barcode or photograph an ingredient label, and find out what is
actually in it: what each ingredient is, why it is there, whether it carries any
recognised concern, and whether it matters for *you* specifically.

**Status: Phase 3.** Scanning, offline dictionary, scoring and UI all work and
build into an installable app. The dictionary holds **27,388 entries** with
13,716 synonyms, covering **food and cosmetics**: 5,326 food ingredients and
additives, and 22,062 cosmetic INCI ingredients.

### Route of exposure changes the answer

The same substance can be safe by one route and banned by another. Titanium
dioxide has been banned in EU **food** since 2022, yet remains a permitted UV
filter in **cosmetics**.

So the dictionary holds separate entries per category, uniqueness is enforced on
`(name, category)` rather than name alone, and every lookup prefers the category
of the product being scanned. A barcode reveals the category by which database
answered: Open Food Facts or Open Beauty Facts. Label scanning cannot know, so
the app asks rather than guessing.

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
| Debug APK (all ABIs, unminified) | 77.9 MB |
| Release AAB (uploaded to Play) | 33.9 MB |
| **Actual download, measured across every device config** | **10.0 – 11.8 MB** |

Play splits the bundle per device, so a user downloads one CPU architecture and
one screen density, not all of them. The debug APK is large because it contains
every variant and is not minified; it is not what anyone installs from the store.

The download figure is measured, not estimated:

```bash
CP=$(find /root/.gradle/caches/modules-2 -name "*.jar" | tr '\n' ':')
AAPT2=$(find "$ANDROID_HOME/build-tools" -name aapt2 -type f | sort -r | head -1)

java -cp "$CP" com.android.tools.build.bundletool.BundleToolMain build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=tools/cache/apks/labeltruth.apks --overwrite --aapt2="$AAPT2"

java -cp "$CP" com.android.tools.build.bundletool.BundleToolMain get-size total \
  --apks=tools/cache/apks/labeltruth.apks
```

The bundletool jar in the Gradle cache has no `Main-Class`, hence `-cp` and the
explicit main class rather than `java -jar`.

The 7.5 MB bundled dictionary is the single largest asset. It is worth confirming
it is actually in the artifact when checking sizes, because a missing asset also
makes the download smaller:

```bash
unzip -l app/build/outputs/bundle/release/app-release.aab | grep ingredients_seed
```

---

## Checks

```bash
./gradlew :app:testDebugUnitTest    # 42 unit tests
./gradlew :app:lintDebug            # 0 errors
python3 tools/check_coverage.py     # dictionary recognition on real labels
```

The unit tests cover the pure logic where this app is most likely to be quietly
wrong: label parsing, text normalisation, scoring and personal alerts. **Almost
every case corresponds to a defect that actually shipped and was fixed**, so they
are regression tests rather than decoration.

Two of them exist to protect honesty rather than correctness:

- `NOT_ASSESSED carries no penalty` — an absence of published concern is not
  evidence of a concern, and most of the dictionary sits in that state
- `summary does not claim no concerns when nothing was assessed` — "no concerns
  found" and "we hold no assessments" are very different claims

Lint is configured to fail on errors, with the deliberate dependency pins
silenced so the report stays readable.

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

The debug build uses application ID `com.labeltruth.app.debug`, so it installs
alongside a release build rather than replacing it.

---

## Architecture

```
com.labeltruth.app
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
2. **Synonym** (4,958 mapped aliases)
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

## The dictionary, and its two tiers

The shipped asset `app/src/main/assets/ingredients_seed.json` is **generated**.
Do not hand-edit it. Edit `tools/data/curated.json` and rebuild:

```bash
python3 tools/build_dictionary.py     # regenerate the bundled dictionary
python3 tools/check_coverage.py       # measure recognition on real labels
```

The distinction between the two tiers is the ethical core of the app.

| Tier | Count | What it carries |
|---|---|---|
| **Curated** | 110 | Full description, risk tier, reasoning, sources. Hand-written |
| **Assessed** | 1,107 | Risk tier derived from published EFSA exposure data or EU cosmetics annexes, with the citation attached |
| **Recognised only** | 26,171 | Name, synonyms, allergen and dietary flags. `riskTier = NOT_ASSESSED`, and **no risk narrative at all** |

### How cosmetics data becomes a risk tier

From the CosIng annex reference, which is a statement of EU legal status:

| Regulatory status | Tier |
|---|---|
| Annex II, prohibited in cosmetics | AVOID |
| Classified CMR (carcinogenic, mutagenic, reprotoxic) | AVOID |
| Annex III, restricted to stated limits and conditions | CAUTION |
| Recognised contact allergen | CAUTION |
| Annex IV / V / VI, permitted colourant, preservative or UV filter | **NOT_ASSESSED** |

That last row matters. Being on a positive list means "authorised within
limits", **not** "no known concern", so it is reported as factual regulatory
status in *why it is used* and never as a safety verdict. Methylisothiazolinone
is on Annex V and is a notorious contact allergen the EU later banned from
leave-on products; calling it SAFE because it appears on a permitted list would
be exactly the kind of overclaim this project exists to avoid.

Curated entries always win over generated ones, by id, by name and by synonym.

### How EFSA data becomes a risk tier

Every branch below corresponds to something EFSA actually published, and the
opinion URL is attached to the entry. None of it is our own judgement.

| Published finding | Tier |
|---|---|
| EFSA could not establish an ADI | MODERATE |
| Average intake exceeds the ADI for some group | MODERATE |
| EFSA identified a high overexposure risk | MODERATE |
| High consumers may exceed the ADI | CAUTION |
| EFSA identified a moderate overexposure risk | CAUTION |
| Evaluated, no overexposure identified | SAFE |
| Nothing published we can point at | NOT_ASSESSED |

Where France's ANSES lists an additive as one "of interest", that is noted in the
reasoning but deliberately does **not** change the tier, because it is a
monitoring priority rather than a safety verdict.

### Why "recognised only" is still worth shipping

It costs 0.18 MB compressed and it buys two things. Allergen flags, inherited
down the taxonomy so "cheese" picks up milk from its parent, cover 1,042 entries.
And the app stops saying "not in our database" for thousands of ordinary foods,
which is what destroys trust fastest.

`NOT_ASSESSED` carries **zero** score penalty. An absence of published concern is
not evidence of a concern, and must not be scored as one.

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

LabelTruth is informational and **not medical advice**. Ratings are general
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

- **Most entries are recognised but not assessed.** 5,057 of 5,326 carry no
  published safety assessment, only identity, allergen and dietary data. That is
  a deliberate limit, not a bug: expanding the *assessed* set means reading real
  regulatory opinions, which does not scale automatically.
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

## Naming and positioning decisions

These are load-bearing. Drifting from them creates real policy risk.

| Field | Value | Why it is fixed |
|---|---|---|
| Brand | **LabelTruth** | "Truth" is a claim we can actually back, because every rating cites a regulator |
| Store title | `LabelTruth: Food Scanner` | 24 of 30 characters. Brand carries identity, descriptor carries search |
| `applicationId` | `com.labeltruth.app` | **Permanent once published.** Cannot be changed, ever |
| Store category | **Food & Drink** | Deliberately not Health & Fitness — see below |
| Developer name | Pitch Code | Publisher identity, independent of any single app |

### Why not the earlier name

The project began as "LabelLens". That was dropped because the developer name was
already registered on Play, `com.labelens.app` is a live app doing the same thing
with a one-letter difference, and `labellens.com` and `labellens.net` are both
running ingredient scanners. The logo survived the rename unchanged, because it
contains no letterforms.

### Three things never to do

**1. Do not put "cosmetic" in the store title until INCI data actually ships.**
Yuka already lists as "Food & Cosmetic Scanner", so it is a losing search fight,
and advertising functionality the app does not have is grounds for rejection. The
store title can be changed any time, so this costs nothing to defer.

**2. Do not use "Health" in the app name, or pick a health store category.**
Google Play requires an **Organization account** — which needs a D-U-N-S number,
which needs a registered business — for "Health apps, such as Medical apps and
Human Subjects Research apps". This is a personal account. Staying Food & Drink
is what keeps that requirement from applying.

**3. No disease claims anywhere in the listing.** Describe what the app *shows*,
never what it *does to your health*.

| Never | Instead |
|---|---|
| "Prevents cancer" | "Shows what IARC and EFSA have published about this ingredient" |
| "Cures your IBS" | "Flags ingredients commonly associated with digestive discomfort" |
| "Diagnose your allergies" | "Flags allergens you asked us to watch for" |
| "Doctor approved" | "Sourced from EFSA, WHO and FDA publications" |

---

## Website and privacy policy

Google Play will not publish an app without a live privacy policy URL, so the site
lives in `docs/` and is served free by GitHub Pages.

| File | Becomes |
|---|---|
| `docs/index.html` | `https://pitch-code.github.io/LabelTruth/` |
| `docs/privacy.html` | `https://pitch-code.github.io/LabelTruth/privacy.html` |

Both are single self-contained files: no build step, no dependencies, no JavaScript,
and the logo is inlined as SVG so there are no image requests.

### Switching Pages on, once

1. Repo **Settings** → **Pages** in the left sidebar
2. Under *Build and deployment*, set **Source** to `Deploy from a branch`
3. Choose branch **`main`** and folder **`/docs`**, then **Save**
4. Wait a minute or two, then load the URL above

Pages requires a public repository on GitHub's free plan.

### Store listing copy

`play/store-listing.md` holds the ready-to-paste app name, short and full
description, category, Health apps declaration, Data safety answers and content
rating answers — all within Play's character limits, all free of health claims.

---

## Google Play launch checklist

This is a **personal** developer account, so the closed-testing requirement
applies and it, not the code, sets the launch date.

- [ ] Rename the GitHub repository to `LabelTruth` (GitHub redirects the old URL)
- [ ] Register the Play developer account (**$25 one-time**, non-refundable; verification takes hours to days)
- [ ] Enable 2FA / passkey on the developer Google account
- [ ] Create the `labeltruth.support@gmail.com` mailbox used in the privacy policy and listing
- [ ] Enable GitHub Pages (Settings → Pages → `main` / `/docs`) to publish the privacy policy
- [ ] Generate an upload keystore and **back it up somewhere you cannot lose it**; enable Play App Signing
- [ ] Set store title to `LabelTruth: Food Scanner` and category to **Food & Drink**
- [ ] Answer the Health apps declaration honestly — this app is **not** a medical app
- [ ] Complete the Data safety form (barcode sent to a third-party API; no personal data collected)
- [ ] Complete the content rating questionnaire
- [ ] Upload store assets: icon (`brand/play-store-icon-512.png`), feature graphic, screenshots
- [ ] Add a real contact URL to the Open Food Facts `User-Agent` before production
- [ ] Upload the AAB to **internal testing** first, and read the automatic pre-launch report
- [ ] Run **closed testing with at least 12 testers, opted in for 14 continuous days** (recruit 18–25, people drop out)
- [ ] Apply for production access

The 14 days is calendar time and nothing shortens it, so start closed testing
with a rough-but-working build rather than waiting for polish.

---

## What we deliberately did not copy

Two competitors were installed and worked through in detail: **KnowTox** and
**Toxly**. Several of their ideas were adopted. These were rejected on purpose.

| Their pattern | Why we are not doing it |
|---|---|
| **Free scan quota** (5/month, or ad-gated) | Rationing the core function of a trust app teaches users to distrust it. Ship v1 unlimited. |
| **Daily streaks**, "4 more days to +1 ad-free scan" | Engagement farming. Nobody should scan food daily to keep a streak alive. This is a utility, not a game. |
| **Ads on the results screen** | The verdict is the moment the user is deciding something. Selling that attention undermines the product's only real asset. |
| **"AI" badges** on generated descriptions | Honest of them, but it advertises that the content was guessed. We inverted it: a **SOURCED** badge marks entries backed by a citation. |
| **Health goals** onboarding (immune support, energy, sleep) | We cannot connect a food label to "brain function" with any published evidence. Asking implies we can. |
| **Skin type** onboarding | Meaningless until cosmetics ship, and asking for data we cannot use is a dark pattern. |

### The monetisation model, decided

Settled deliberately, because it constrains the UI:

> **Scanning is unlimited and free, permanently. Revenue comes from extras.**

| Free | Premium (v1.1) |
|---|---|
| Unlimited scanning | Unlimited scanning |
| Full results, with sources | Full results, with sources |
| Unlimited bookmarks | Unlimited bookmarks |
| Recent scan history | Full history + export |
| Small ads on Home only, never on a result | No ads |

The reference design that prompted the redesign showed a **"5 analyses remaining"**
counter and a **FREE** plan badge. Both were left out. Metering the one thing the
app exists to do is the fastest way to lose a user, and a trust app that rations
trust is contradictory.

There is also a narrower reason nothing on the home screen advertises an
upgrade: **no purchase exists yet.** Showing a plan badge or an upsell before
there is anything to buy is misleading, and Play's policies expect promoted
purchases to be real.

One thing worth recording: Toxly's ingredient list showed **"For external use only"**,
**"Keep out of"** and **"CI 12085 Directions"** as if they were ingredients. That is
the parser leaking label text. `IngredientListParser` now filters hygiene and
household boilerplate specifically, and `tools/check_coverage.py` has a hand wash
label as a regression case.

---

## Competitive position

This category is crowded. Apps shipped in roughly the last year include Labelens,
Labeless, BrandLens, ClearLabel, CheckEt, CheckWise, HonestWorld, IngrediCheck,
FoodReveal, MavYa, Lettuce, NutriScan, SafeChoice, TruthIn, DecodeFood, Vireo and
Processed, with **Yuka** as the incumbent.

Almost all of them describe themselves as "AI-powered", meaning they ask a
language model what an ingredient does and print the answer. That is fast to
build and impossible to trust.

The differentiation here is the opposite, and it is already implemented:

- Every rating traces to a **named** EFSA, WHO/IARC, FDA or EU source, shown in-app
- The dictionary is **offline**, so results are instant and work in a shop with no signal
- Unrecognised ingredients say **"not in our database yet"** instead of guessing
- On-device OCR reads **any** package, so coverage is not capped by a barcode database

Protect these. They are the whole argument for the app existing.

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
