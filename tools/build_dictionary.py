#!/usr/bin/env python3
"""
Builds the bundled ingredient dictionary shipped in the app.

    python3 tools/build_dictionary.py

Inputs
    tools/data/curated.json     hand-written entries, fully sourced
    Open Food Facts taxonomies  downloaded once and cached in tools/cache/

Output
    app/src/main/assets/ingredients_seed.json

The dictionary has two tiers, and the distinction matters:

  ASSESSED     A risk tier and the reasoning behind it, every one traceable to
               a published source. These come from tools/data/curated.json, or
               are derived from EFSA evaluation data that Open Food Facts
               carries, in which case the EFSA opinion URL is the citation.

  RECOGNISED   Name, synonyms, allergen flags and dietary flags, but
               riskTier = NOT_ASSESSED and no risk narrative at all. The app
               says "no published assessment" rather than inventing one.

Recognising an ingredient without assessing it is still worth a lot: allergen
and vegan/vegetarian flags are useful on their own, and it stops the app
saying "not in our database" for thousands of perfectly ordinary ingredients.

Curated entries always win over generated ones.

Data source: Open Food Facts, Open Database License (ODbL). Attribution is a
licence condition and is shown in the app and on the website.
"""

from __future__ import annotations

import json
import re
import sys
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / "cache"
CURATED = ROOT / "tools" / "data" / "curated.json"
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "ingredients_seed.json"

OFF_STATIC = "https://static.openfoodfacts.org/data/taxonomies"
OFF_RAW = ("https://raw.githubusercontent.com/openfoodfacts/"
           "openfoodfacts-server/main/taxonomies")

# Note the paths differ: ingredients.txt lives under food/, additives.txt does
# not. The JSON exports carry no synonyms at all, which is why both raw
# taxonomies are needed as well.
SOURCES = {
    "additives.json": f"{OFF_STATIC}/additives.json",
    "ingredients.json": f"{OFF_STATIC}/ingredients.json",
    "ingredients.txt": f"{OFF_RAW}/food/ingredients.txt",
    "additives.txt": f"{OFF_RAW}/additives.txt",
}

# Open Food Facts allergen ids mapped to the 14 declarable allergens the app
# uses in HealthProfile.ALL_ALLERGENS.
ALLERGEN_MAP = {
    "en:gluten": "gluten",
    "en:crustaceans": "crustaceans",
    "en:eggs": "eggs",
    "en:fish": "fish",
    "en:peanuts": "peanuts",
    "en:soybeans": "soybeans",
    "en:milk": "milk",
    "en:nuts": "nuts",
    "en:celery": "celery",
    "en:mustard": "mustard",
    "en:sesame-seeds": "sesame",
    "en:sulphur-dioxide-and-sulphites": "sulphites",
    "en:lupin": "lupin",
    "en:molluscs": "molluscs",
}

# EFSA reports exposure by population group. Collapse the young-person groups
# onto the app's single "children" caution, and ignore adults and elderly,
# which carry no specific caution in the UI.
YOUNG_GROUPS = {"infants", "toddlers", "children", "adolescents"}

FUNCTIONAL_CLASS_LABELS = {
    "en:colour": "colour",
    "en:preservative": "preservative",
    "en:antioxidant": "antioxidant",
    "en:emulsifier": "emulsifier",
    "en:stabiliser": "stabiliser",
    "en:thickener": "thickener",
    "en:sweetener": "sweetener",
    "en:acid": "acid",
    "en:acidity-regulator": "acidity regulator",
    "en:anticaking-agent": "anti-caking agent",
    "en:flavour-enhancer": "flavour enhancer",
    "en:raising-agent": "raising agent",
    "en:glazing-agent": "glazing agent",
    "en:humectant": "humectant",
    "en:firming-agent": "firming agent",
    "en:gelling-agent": "gelling agent",
    "en:bulking-agent": "bulking agent",
    "en:flour-treatment-agent": "flour treatment agent",
    "en:sequestrant": "sequestrant",
    "en:foaming-agent": "foaming agent",
    "en:antifoaming-agent": "anti-foaming agent",
    "en:carrier": "carrier",
    "en:propellant": "propellant",
    "en:packaging-gas": "packaging gas",
}

ALL_DIETS = ["vegan", "vegetarian", "halal", "kosher", "gluten_free"]


# --------------------------------------------------------------------------- #
# helpers
# --------------------------------------------------------------------------- #

def log(msg: str) -> None:
    print(msg, file=sys.stderr)


def download_all() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    for name, url in SOURCES.items():
        dest = CACHE / name
        if dest.exists() and dest.stat().st_size > 10_000:
            log(f"  cached   {name} ({dest.stat().st_size // 1024} KB)")
            continue
        log(f"  fetching {name} ...")
        req = urllib.request.Request(
            url, headers={"User-Agent": "LabelTruth-dictionary-builder/1.0"}
        )
        with urllib.request.urlopen(req, timeout=180) as r, open(dest, "wb") as f:
            f.write(r.read())
        log(f"  saved    {name} ({dest.stat().st_size // 1024} KB)")


_diacritics = re.compile(r"[\u0300-\u036f]")
_non_alnum = re.compile(r"[^a-z0-9 ]")
_spaces = re.compile(r"\s+")


def normalize(text: str) -> str:
    """Mirrors TextNormalizer.normalize in the app. Must stay in step."""
    decomposed = unicodedata.normalize("NFD", text.lower().strip())
    stripped = _diacritics.sub("", decomposed)
    stripped = stripped.replace("-", " ").replace("_", " ")
    return _spaces.sub(" ", _non_alnum.sub(" ", stripped)).strip()


def en(entry: dict, field: str) -> str | None:
    value = entry.get(field, {})
    if isinstance(value, dict):
        got = value.get("en")
        if isinstance(got, str) and got.strip():
            return got.strip()
    return None


def en_list(entry: dict, field: str) -> list[str]:
    """Several OFF fields hold a comma-joined list of tag ids."""
    raw = en(entry, field)
    if not raw:
        return []
    return [p.strip() for p in raw.split(",") if p.strip()]


def strip_tag(tag: str) -> str:
    return tag.split(":", 1)[1] if ":" in tag else tag


# --------------------------------------------------------------------------- #
# raw taxonomy parsing, for the synonyms and allergens the JSON omits
# --------------------------------------------------------------------------- #

def parse_taxonomy_text(path: Path) -> dict[str, dict]:
    """
    Reads the raw taxonomy. Blocks are separated by blank lines and look like:

        < en: parent name
        en: canonical name, synonym one, synonym two
        allergens:en: en:soybeans
        vegan:en: no

    Returns a map keyed on the normalised canonical English name.
    """
    out: dict[str, dict] = {}
    block: list[str] = []

    def flush(lines: list[str]) -> None:
        canonical = None
        synonyms: list[str] = []
        allergens: list[str] = []
        for line in lines:
            if line.startswith("#") or line.startswith("<"):
                continue
            if line.startswith("en:") and ":" in line:
                body = line[3:].strip()
                if not body:
                    continue
                parts = [p.strip() for p in body.split(",") if p.strip()]
                if parts and canonical is None:
                    canonical, synonyms = parts[0], parts[1:]
            elif line.startswith("allergens:en:"):
                allergens += [
                    p.strip() for p in line.split(":", 2)[2].split(",") if p.strip()
                ]
        if canonical:
            out[normalize(canonical)] = {
                "canonical": canonical,
                "synonyms": synonyms,
                "allergens": allergens,
            }

    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.rstrip()
        if not line.strip():
            flush(block)
            block = []
        else:
            block.append(line)
    flush(block)
    return out


# --------------------------------------------------------------------------- #
# risk derivation, strictly from published EFSA data
# --------------------------------------------------------------------------- #

def efsa_citation(entry: dict) -> list[dict]:
    url = en(entry, "efsa_evaluation_url")
    if not url:
        return []
    date = en(entry, "efsa_evaluation_date")
    title = "EFSA scientific opinion"
    if date:
        title += f" ({date})"
    return [{"title": title, "url": url}]


def young_groups_in(entry: dict, field: str) -> list[str]:
    groups = {strip_tag(g).strip() for g in en_list(entry, field)}
    if not groups or groups == {"no-group"}:
        return []
    return sorted(g for g in groups if g in YOUNG_GROUPS)


def humanise(groups: list[str]) -> str:
    names = [g.replace("-", " ") for g in groups]
    if len(names) == 1:
        return names[0]
    return ", ".join(names[:-1]) + " and " + names[-1]


def derive_risk(entry: dict) -> tuple[str, str, list[str]]:
    """
    Returns (riskTier, riskReason, cautionGroups).

    Every branch corresponds to something EFSA actually published, and the
    EFSA opinion URL is attached separately as the citation. Nothing here is
    a judgement of our own.
    """
    has_efsa = bool(en(entry, "efsa_evaluation_url"))
    overexposure = (en(entry, "efsa_evaluation_overexposure_risk") or "").replace("en:", "")
    adi_established = (en(entry, "efsa_evaluation_adi_established") or "").replace("en:", "")
    mean_over = young_groups_in(entry, "efsa_evaluation_exposure_mean_greater_than_adi")
    p95_over = young_groups_in(entry, "efsa_evaluation_exposure_95th_greater_than_adi")
    anses = en(entry, "anses_additives_of_interest") == "yes"

    caution = ["children"] if (mean_over or p95_over) else []
    notes = []
    if anses:
        notes.append(
            "The French agency ANSES also lists it among additives of interest "
            "for closer monitoring."
        )

    def finish(tier: str, reason: str) -> tuple[str, str, list[str]]:
        return tier, " ".join([reason] + notes), caution

    if adi_established == "no":
        return finish(
            "MODERATE",
            "EFSA reviewed this additive but was unable to establish an "
            "acceptable daily intake.",
        )
    if mean_over:
        return finish(
            "MODERATE",
            "EFSA found that average intake exceeds the acceptable daily "
            f"intake for {humanise(mean_over)}.",
        )
    if overexposure == "high":
        return finish(
            "MODERATE",
            "EFSA identified a high risk of exposure above the acceptable "
            "daily intake.",
        )
    if p95_over:
        return finish(
            "CAUTION",
            "EFSA found that high consumers may exceed the acceptable daily "
            f"intake for {humanise(p95_over)}.",
        )
    if overexposure == "moderate":
        return finish(
            "CAUTION",
            "EFSA identified a moderate risk of exposure above the "
            "acceptable daily intake.",
        )
    if overexposure == "no":
        return finish(
            "SAFE",
            "EFSA evaluated this additive and did not identify a risk of "
            "exposure above the acceptable daily intake.",
        )
    if has_efsa:
        return finish(
            "SAFE",
            "EFSA has evaluated this additive and it is authorised for use in "
            "food in the EU.",
        )
    # Nothing published that we can point at, so we claim nothing.
    return "NOT_ASSESSED", " ".join(notes), caution


def diets_from(entry: dict) -> list[str]:
    """
    Only claim dietary suitability where OFF states it outright. "maybe" is
    left off, because a maybe is exactly when a vegan needs to check the
    packaging rather than trust us.
    """
    vegan = en(entry, "vegan")
    vegetarian = en(entry, "vegetarian")
    if vegan == "yes":
        return list(ALL_DIETS)
    if vegetarian == "yes":
        return ["vegetarian", "halal", "kosher", "gluten_free"]
    if vegan == "no" or vegetarian == "no":
        return ["gluten_free"]
    return []


# --------------------------------------------------------------------------- #
# builders
# --------------------------------------------------------------------------- #

E_NAME = re.compile(r"^\s*E\s?(\d{3})([a-z]{0,3})\s*[-–—:]\s*(.+)$", re.IGNORECASE)


def canonical_e_number(raw: str) -> str | None:
    """
    EU convention is E150d, not E150D. This must match
    TextNormalizer.extractENumber exactly, because Room compares text with
    BINARY collation and a casing mismatch would silently fail to look up.
    """
    match = re.match(r"^\s*E?\s?(\d{3})([a-z]{0,3})\s*$", raw.strip(), re.IGNORECASE)
    if not match:
        return None
    return f"E{match.group(1)}{match.group(2).lower()}"


def tidy_name(name: str) -> str:
    """OFF names are mostly lowercase. Capitalise the first letter only, so
    'acai berry' reads as 'Acai berry' without mangling internal casing."""
    name = name.strip()
    if name and name[0].islower():
        return name[0].upper() + name[1:]
    return name


def build_additives(additives: dict, text_index: dict) -> list[dict]:
    out = []
    for tag, entry in sorted(additives.items()):
        name_en = en(entry, "name")
        if not name_en:
            continue

        raw_number = en(entry, "e_number")
        e_number = canonical_e_number(raw_number) if raw_number else None

        # "E330 - Citric acid" -> plain name plus a reliable E-number
        plain = name_en
        match = E_NAME.match(name_en)
        if match:
            plain = match.group(3).strip()
            if not e_number:
                e_number = canonical_e_number(f"{match.group(1)}{match.group(2)}")

        plain = tidy_name(plain)
        if not e_number or not plain:
            continue

        # Some entries have no real name, only a placeholder like
        # "E101a food additive". Displaying "E101a food additive (E101a)" is
        # just noise, so present those as "Additive E101a" instead. They are
        # still worth keeping, because the E-number itself remains matchable.
        placeholder = (
            re.fullmatch(r"e\s?\d{3}[a-z]{0,3}(\s+food\s+additive)?", plain, re.IGNORECASE)
            # Also catches OFF's stand-in entries such as "E15x - E15x food
            # additive", where the E-number itself is not a real one.
            or re.search(r"food\s+additive", plain, re.IGNORECASE)
        )
        display = f"Additive {e_number}" if placeholder else f"{plain} ({e_number})"

        classes = [
            FUNCTIONAL_CLASS_LABELS.get(c, strip_tag(c).replace("-", " "))
            for c in en_list(entry, "additives_classes")
        ]
        why = f"Used as a {', '.join(dict.fromkeys(classes))}." if classes else ""

        tier, reason, caution = derive_risk(entry)
        adi = en(entry, "efsa_evaluation_adi")

        synonyms = {plain, e_number, e_number.replace("E", "E "), f"{e_number} {plain}"}
        if match:
            synonyms.add(name_en)

        # Real synonyms from the raw taxonomy, which matter a great deal
        # because labels use common names, not the official ones.
        #
        # In additives.txt the E-number comes first on the line and the names
        # follow, e.g. "en: E503, Ammonium carbonates". So the block is keyed on
        # the number, and the "synonyms" are in fact the usable names.
        raw = (text_index.get(normalize(e_number))
               or text_index.get(normalize(name_en))
               or text_index.get(normalize(plain)))
        if raw:
            for syn in raw["synonyms"]:
                cleaned = E_NAME.match(syn)
                syn = cleaned.group(3).strip() if cleaned else syn.strip()
                if 2 <= len(syn) <= 70:
                    synonyms.add(syn)

        out.append({
            "id": strip_tag(tag),
            "name": display,
            "eNumber": e_number,
            "synonyms": sorted(s for s in synonyms if s),
            "category": "food",
            "whatItIs": en(entry, "description") or "",
            "whyUsed": why,
            "riskTier": tier,
            "riskReason": reason,
            "allergens": [],
            "dietary": diets_from(entry),
            "cautionGroups": caution,
            "adi": (f"{adi} mg per kg body weight per day (EFSA)" if adi else None),
            "sources": efsa_citation(entry),
            "_tier": "assessed" if tier != "NOT_ASSESSED" else "recognised",
        })
    return out


def resolve_allergens(
    tag: str,
    ingredients: dict,
    text_index: dict,
    memo: dict[str, list[str]],
    depth: int = 0,
) -> list[str]:
    """
    Allergens are declared on the most general node, so "wheat flour" inherits
    gluten from "wheat". Walk up the parent chain and union what we find.
    """
    if tag in memo:
        return memo[tag]
    if depth > 12 or tag not in ingredients:
        return []
    memo[tag] = []  # guards against cycles in the taxonomy

    entry = ingredients[tag]
    found: set[str] = set()

    name_en = en(entry, "name")
    if name_en:
        raw = text_index.get(normalize(name_en))
        if raw:
            for a in raw["allergens"]:
                mapped = ALLERGEN_MAP.get(a.strip())
                if mapped:
                    found.add(mapped)

    for parent in entry.get("parents", []) or []:
        found.update(resolve_allergens(parent, ingredients, text_index, memo, depth + 1))

    memo[tag] = sorted(found)
    return memo[tag]


def build_ingredients(ingredients: dict, text_index: dict) -> list[dict]:
    out = []
    memo: dict[str, list[str]] = {}

    for tag, entry in sorted(ingredients.items()):
        name_en = en(entry, "name")
        if not name_en or len(name_en) < 3 or len(name_en) > 70:
            continue
        # Additives are handled from additives.json, which has far better data.
        if en(entry, "e_number"):
            continue
        # Open Food Facts carries some very product-specific entries such as
        # "50-63% unsalted vegetable fat with plant sterols esters". They never
        # appear on a label worded that way, so they are only noise here.
        if name_en[0].isdigit() or "%" in name_en:
            continue

        raw = text_index.get(normalize(name_en), {})
        synonyms = [s for s in raw.get("synonyms", []) if 2 <= len(s) <= 70]
        allergens = resolve_allergens(tag, ingredients, text_index, memo)

        tier, reason, caution = derive_risk(entry)

        out.append({
            "id": strip_tag(tag),
            "name": tidy_name(name_en),
            "eNumber": None,
            "synonyms": sorted(set(synonyms)),
            "category": "food",
            "whatItIs": en(entry, "description") or "",
            "whyUsed": "",
            "riskTier": tier,
            "riskReason": reason,
            "allergens": allergens,
            "dietary": diets_from(entry),
            "cautionGroups": caution,
            "adi": None,
            "sources": efsa_citation(entry),
            "_tier": "assessed" if tier != "NOT_ASSESSED" else "recognised",
        })
    return out


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #

def main() -> int:
    log("Downloading Open Food Facts taxonomies")
    download_all()

    additives = json.loads((CACHE / "additives.json").read_text(encoding="utf-8"))
    ingredients = json.loads((CACHE / "ingredients.json").read_text(encoding="utf-8"))
    text_index = parse_taxonomy_text(CACHE / "ingredients.txt")
    additive_text = parse_taxonomy_text(CACHE / "additives.txt")
    log(f"  raw ingredient blocks: {len(text_index)}")
    log(f"  raw additive blocks:   {len(additive_text)}")

    curated_doc = json.loads(CURATED.read_text(encoding="utf-8"))
    curated = curated_doc["ingredients"]
    for item in curated:
        item["_tier"] = "curated"
    log(f"  curated entries: {len(curated)}")

    generated = (build_additives(additives, additive_text)
                 + build_ingredients(ingredients, text_index))
    log(f"  generated entries: {len(generated)}")

    # Curated wins, by id and by normalised name.
    merged: list[dict] = list(curated)
    taken_ids = {i["id"] for i in curated}
    taken_names = {normalize(i["name"]) for i in curated}
    # A curated entry owns its synonyms too, so nothing generated can steal them.
    taken_names |= {
        normalize(s) for i in curated for s in i.get("synonyms", [])
    }

    dropped = 0
    for item in generated:
        if item["id"] in taken_ids or normalize(item["name"]) in taken_names:
            dropped += 1
            continue
        taken_ids.add(item["id"])
        taken_names.add(normalize(item["name"]))
        merged.append(item)

    log(f"  dropped as already curated: {dropped}")

    # Synonyms are a primary key in Room, so a collision would throw at import.
    # First writer wins, and curated entries are written first.
    claimed: dict[str, str] = {}
    for item in merged:
        claimed.setdefault(normalize(item["name"]), item["id"])
    total_synonyms = 0
    for item in merged:
        kept = []
        for syn in item.get("synonyms", []):
            key = normalize(syn)
            if not key or key == normalize(item["name"]):
                continue
            if claimed.setdefault(key, item["id"]) != item["id"]:
                continue
            kept.append(syn)
        item["synonyms"] = kept
        total_synonyms += len(kept)

    counts = {"curated": 0, "assessed": 0, "recognised": 0}
    for item in merged:
        counts[item.pop("_tier")] += 1

    merged.sort(key=lambda i: i["id"])
    payload = {
        "version": 2,
        "generated_by": "tools/build_dictionary.py",
        "attribution": (
            "Ingredient and additive data from Open Food Facts, used under the "
            "Open Database License (ODbL). Risk assessments derive from EFSA "
            "evaluations cited per entry, or from the curated set."
        ),
        "counts": {
            "total": len(merged),
            "curated": counts["curated"],
            "assessed_from_efsa": counts["assessed"],
            "recognised_only": counts["recognised"],
            "synonyms": total_synonyms,
        },
        "ingredients": merged,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(payload, ensure_ascii=False, indent=1, sort_keys=False) + "\n",
        encoding="utf-8",
    )

    log("")
    log(f"Wrote {OUTPUT.relative_to(ROOT)}")
    log(f"  total entries        {len(merged)}")
    log(f"  curated              {counts['curated']}")
    log(f"  assessed from EFSA   {counts['assessed']}")
    log(f"  recognised only      {counts['recognised']}")
    log(f"  synonyms             {total_synonyms}")
    log(f"  size                 {OUTPUT.stat().st_size // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
