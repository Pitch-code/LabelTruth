# Google Play store listing — LabelTruth

Copy-paste ready. Every field is written to stay inside Play's limits and clear of
health claims. See the README section "Naming and positioning decisions" for why
these constraints exist.

---

## App name

Limit: 30 characters. **Used: 24.**

```
LabelTruth: Food Scanner
```

Do **not** add "cosmetic" until INCI data actually ships. It would exceed the limit,
it duplicates Yuka's listing title, and advertising functionality the app lacks is
grounds for rejection.

---

## Short description

Limit: 80 characters. **Used: 76.**

```
Scan any food label. Every ingredient explained, with sources you can check.
```

This is the highest-leverage text in the whole listing — it appears in search
results and above the fold. It leads with the action, then the differentiator.

---

## Full description

Limit: 4000 characters. **Used: 2949.**

```
Ever turned a packet over, read "Emulsifier (E471)" and just put it in the trolley
anyway? LabelTruth reads the label for you and explains, in plain language, what is
actually in your food.

Scan the barcode, or point your camera straight at the printed ingredient list. You
get a clear score, then every ingredient broken down one by one.


WHAT YOU GET FOR EVERY INGREDIENT

• What it actually is, in plain language
• Why it is in the product — preservative, emulsifier, colour, sweetener
• Whether any recognised concern has been published about it, and why
• Allergen and dietary flags
• Groups advised to take extra care
• Acceptable daily intake, where an authority has set one
• The source for each claim, so you can read it yourself


SOURCES YOU CAN CHECK, NOT GUESSES

Most scanner apps ask an AI what an ingredient does and print whatever comes back.
LabelTruth does not. Every rating traces to a named published source: the European
Food Safety Authority, the World Health Organization and IARC, the US Food and Drug
Administration, and EU regulations on additives, allergens and flavourings. Each
entry shows its own sources and you can read them yourself.

And when an ingredient is not in our dictionary yet, the app says exactly that
instead of inventing an answer.


WORKS WITHOUT SIGNAL

The ingredient dictionary is built into the app, so results appear instantly.
Label scanning works with no internet connection at all — handy in a supermarket
where the signal disappears.

Reading the printed label also means you are not limited to products that happen to
be in a barcode database. If it has an ingredient list, it can be scanned.


MADE PERSONAL

Tick the allergens you avoid, your dietary preferences, and any health
considerations that apply to you. Scans are then checked against your profile, so
instead of a generic rating you are told what matters for you.

Your profile stays on your phone.


NO ACCOUNT. NO TRACKING. NO ADS.

• No sign-up, no login, no email address needed
• No analytics, no advertising identifiers, no ads
• Camera images are read on your device and never uploaded
• Your profile and scan history never leave your phone
• Uninstalling removes everything

The only thing the app ever sends is a barcode number you chose to scan, so the
product can be looked up.


HONEST ABOUT COVERAGE

There is no complete list of every food ingredient anywhere in the world, and any
app claiming to have one is overstating. Our dictionary is built from authoritative
public sources and grows steadily. Where something is not recognised yet, we tell
you.


IMPORTANT

LabelTruth is for information only. It is not medical advice and not a diagnosis,
and it cannot account for your individual medical history. Always check the physical
packaging for allergen information, and speak to a doctor, dietitian or pharmacist
about your own health.

Product data from Open Food Facts, used under the Open Database License.
```

---

## Categorisation

| Field | Value |
|---|---|
| Category | **Food & Drink** |
| Tags | Food, Nutrition, Groceries, Shopping |
| Contains ads | **No** |
| In-app purchases | **No** |

Do not choose Health & Fitness or Medical. Play requires an Organization account
(and therefore a D-U-N-S number) for health apps. This is a personal account.

**Monetisation warning:** the moment you add ads or purchases, Play publishes your
full legal address on the listing. If you plan to monetise, register a business and
move to an Organization account first.

---

## Health apps declaration

Found under App content. The honest answers:

| Question | Answer |
|---|---|
| Is this a medical app? | **No** — it does not diagnose, treat, prevent or monitor disease |
| Human subjects research? | **No** |
| Does it access Health Connect? | **No** |

It reports published regulatory information about food ingredients. Reference
material, not medical functionality.

---

## Data safety

Play requires this and cross-checks it against actual app behaviour.

| Question | Answer |
|---|---|
| Collects or shares user data? | **No** |
| Data encrypted in transit? | **Yes** — cleartext is disabled app-wide |
| Users can request deletion? | Nothing is collected; uninstalling removes local data |

**The one judgement call.** Scanning a barcode transmits that barcode number to
Open Food Facts. Google defines "collection" as transmitting data off the device,
but a barcode is not one of Play's listed personal data types, is not linked to the
user, and carries no identifiers. So the recommended answer is **no data collected**,
with the third-party lookup disclosed plainly in the privacy policy — which is what
transparency actually requires.

If Google queries it during review, declare it under *App activity → Other actions*
and point to the privacy policy. Do not argue; just declare it.

Never claimed: no location, no personal info, no photos or videos (camera frames are
never transmitted), no contacts, no identifiers.

---

## Content rating questionnaire

Expected outcome: suitable for everyone.

- Violence, sexual content, profanity, drugs, gambling → **No** to all
- User-generated content shared between users → **No**
- Shares user location → **No**
- Allows purchases → **No**

---

## Required URLs

| Field | Value |
|---|---|
| Privacy policy | `https://pitch-code.github.io/LabelTruth/privacy.html` |
| Website | `https://pitch-code.github.io/LabelTruth/` |
| Support email | `labeltruth.support@gmail.com` |

The privacy policy URL is mandatory. Both are served by GitHub Pages from `docs/`
in this repository.

---

## Graphic assets

| Asset | Spec | Status |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit | ✅ `brand/play-store-icon-512.png` |
| Feature graphic | 1024 × 500 PNG or JPEG | ✅ `brand/feature-graphic-1024x500.png` |
| Phone screenshots | 2–8 images, min 320px shortest side | ❌ needs a real device |

Screenshots must come from the app actually running, which is blocked until it has
been installed on hardware. Suggested set, in this order:

1. The scanner with the viewfinder over a real product
2. A results sheet showing the score ring and ingredient list
3. An ingredient detail page **with its sources visible** — this is the differentiator
4. The profile screen with allergens selected
5. A personal alert firing on a scan
