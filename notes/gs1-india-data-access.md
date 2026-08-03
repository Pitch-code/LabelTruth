# GS1 India data access

Working notes and a ready-to-send enquiry for getting Indian product data into
LabelTruth. Kept out of `docs/` on purpose: that folder is published by GitHub
Pages, and this is correspondence material, not a public page.

## The problem this is meant to solve

Open Food Facts is the app's only product source, and its Indian coverage is
thin in two distinct ways.

**Products missing entirely.** Barcode `8906107054309`, scanned from a real
product, returns `status 0` from both Open Food Facts and Open Beauty Facts. A
competitor found it, because it runs its own catalogue.

**Records too thin to be useful.** Milky Mist Cow Ghee is *in* Open Food Facts,
but with a product name and a single ingredient line and no nutrition panel. For
a one-ingredient food, the nutrition panel is the entire answer, so the scan
returns almost nothing. Fetching more fields, which we now do, cannot conjure
data nobody entered.

Neither problem is fixable in our code. Both are data supply.

## Why GS1 India

Every Indian retail barcode beginning `890` is issued through GS1 India, a
standards body set up with the Ministry of Commerce and Industry along with CII,
FICCI, ASSOCHAM and BIS. Its product database, **DataKart**, is the registry
behind those numbers, and GS1 India's own material describes the premium tier as
carrying nutritional information, allergens and ingredients, which is exactly the
set we are missing. It also powers their consumer-facing Smart Consumer app.

There is precedent for access being granted to a small project rather than only
to large retailers: the open-source `Toxic-Ingredients-Total-Scanner`, built for a
database course at Poznan University of Technology, states that access to its
product database was granted by GS1 Polska. GS1 is a federation of national
organisations with broadly similar structures, so an approach is worth making.

## What we actually need

Read access by GTIN, returning as much of the following as exists:

| Field | Why |
|---|---|
| Product name, brand | To confirm we found the right product |
| Net quantity | Users asked for pack size |
| Category | Distinguishes food from cosmetic, which changes the analysis |
| Ingredient list | The core of the app |
| Nutrition per 100 g | The whole answer for single-ingredient foods |
| Declared allergens | A second, independent allergen source |
| Product image | The single biggest perceived-quality gap |

Volume is low: one lookup per scan, no bulk download needed, and results can be
cached per barcode on the device.

## Questions to ask before committing to anything

1. Is API access available to an independent developer, or only to brand owners
   and registered solution providers?
2. What does it cost, and is there a tier for pre-revenue apps?
3. **Licensing.** May the data be shown to end users in a consumer app? May it be
   cached on the device? This is the question that matters most: an API we cannot
   display results from is useless.
4. Rate limits, and whether a sandbox exists for development.
5. Actual coverage: how many GTINs carry ingredients and nutrition, as opposed to
   just a name?
6. Attribution requirements.

## The honest obstacle

DataKart is aimed at businesses. Access may well require a registered company,
which LabelTruth does not have. That is the same wall as the Play Console
Organization account, and if it applies, registering a business becomes the
unlock for several things at once rather than a separate errand.

Worth asking before assuming. It costs an email.

## Draft enquiry

Send from `labeltruth.support@gmail.com` via the contact form or enquiry address
on gs1india.org.

> **Subject:** Product data access for a consumer ingredient-transparency app
>
> Hello,
>
> I am an independent Android developer in India, building an app called
> LabelTruth. It lets a shopper scan a barcode or photograph an ingredient list
> and see what each ingredient is, with every safety statement traced to a named
> published source such as EFSA, WHO, the EU Cosmetics Regulation or FSSAI. It
> does not generate health claims and it does not use AI to invent information
> about products.
>
> My difficulty is product data. I currently rely on Open Food Facts, whose
> Indian coverage is limited: many products are absent, and many records hold
> only a product name without nutrition or a full ingredient list. Indian
> shoppers therefore get a much poorer result than European ones, which is the
> opposite of what I want to build.
>
> I would like to ask whether DataKart offers read access by GTIN to a developer
> at my scale. Specifically:
>
> 1. Is API access available to an independent developer, or is it limited to
>    brand owners and registered solution providers?
> 2. What are the access options and costs, and is there a tier suitable for an
>    app with no revenue yet?
> 3. May data retrieved by GTIN be displayed to end users in a consumer app, and
>    cached on the device for products the user has scanned? What attribution do
>    you require?
> 4. Are there rate limits, and is a sandbox available for development?
> 5. Of the GTINs in DataKart, roughly how many carry ingredient and nutrition
>    detail rather than a name alone?
>
> Volume would be modest: a single lookup per scan, no bulk download required.
>
> I am happy to provide the app for review, and to comply with whatever
> attribution and display terms you specify. If a registered business entity is
> a prerequisite, I would appreciate knowing that, as it affects how I plan.
>
> Thank you for your time.
>
> Veera
> LabelTruth
> labeltruth.support@gmail.com
> https://pitch-code.github.io/LabelTruth/

## If the answer is no, or the price is out of reach

In rough order of value:

1. **Let users submit missing products to Open Food Facts** from the label they
   just photographed. It is the only route that improves the shared data for the
   next person, and it needs an Open Food Facts write account rather than a
   commercial agreement.
2. **Read the printed nutrition panel** with the document scanner already added
   for ingredient lists. The panel is on the pack even when it is in no database,
   which makes this the most reliable option available and independent of anyone
   else's goodwill.
3. **Ask other GS1 national organisations** what their terms are, if only to
   learn what is normal before negotiating.

Option 2 deserves emphasis: it is the only one of the three that depends on
nobody but us.
