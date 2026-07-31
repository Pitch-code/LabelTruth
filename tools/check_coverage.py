#!/usr/bin/env python3
"""
Estimates how much of a real ingredient list the bundled dictionary can
recognise.

    python3 tools/check_coverage.py

This measures the *dictionary*, not the app. It reimplements the same matching
order the app uses (exact, synonym, E-number, containment) so the numbers are
comparable, but passing here is not a substitute for testing on a device.
"""

from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "app" / "src" / "main" / "assets" / "ingredients_seed.json"

LABELS = {
    "Nutella (live Open Food Facts record)":
        "Sugar, palm oil, HAZELNUTS 13%, skimmed MILK powder 8.7%, low-fat cocoa "
        "7.4%, emulsifiers: lecithin [SOYA]; vanillin. Gluten free",
    "Soft drink, EU style declaration":
        "Ingredients: Water, Sugar, Glucose-Fructose Syrup, Modified Maize Starch, "
        "Acid: Citric Acid (E330), Colours (E102, E133), Preservative: Potassium "
        "Sorbate, Flavourings, Sweeteners (Aspartame, Acesulfame K). Contains a "
        "source of phenylalanine.",
    "Snack, all caps with an OCR error":
        "INGREDIENTS: WHEAT FLOUR, VEGETABLE OIL (PALM), SALT 1.2%, SODIUM "
        "BENZQATE (E 211), MONOSODIUM GLUTAMATE",
    "Biscuit":
        "Wheat Flour, Sugar, Palm Oil, Glucose Syrup, Raising Agents (Sodium "
        "Bicarbonate, Ammonium Bicarbonate), Salt, Emulsifier (Soya Lecithin), "
        "Flavouring",
    "Instant noodles":
        "Noodles: Wheat Flour, Palm Oil, Salt, Thickener (E412), Acidity "
        "Regulators (E501, E500), Colour (E160a). Seasoning: Salt, Sugar, "
        "Monosodium Glutamate, Onion Powder, Garlic Powder, Black Pepper, "
        "Disodium Inosinate, Disodium Guanylate",
    "Yoghurt":
        "Whole Milk, Strawberries, Sugar, Modified Maize Starch, Pectin, "
        "Concentrated Lemon Juice, Colour (Carmine), Natural Flavouring",
    "Hand wash, hygiene label with usage text mixed in":
        "Ingredients: Aqua, Ammonium Lauryl Sulfate, Sodium Laureth Sulfate, "
        "Glycerin, Cocamide MEA, Parfum, Sodium Chloride, Citric Acid, "
        "Tetrasodium EDTA, CI 11710. Directions: Wet hands, apply and rinse. "
        "For external use only. Keep out of reach of children. Warnings: avoid "
        "contact with eyes. Mfg by XYZ Ltd. Net wt 200ml.",
}

# --- mirrors TextNormalizer -------------------------------------------------
_diacritics = re.compile(r"[\u0300-\u036f]")
_non_alnum = re.compile(r"[^a-z0-9 ]")
_spaces = re.compile(r"\s+")


def normalize(text: str) -> str:
    d = unicodedata.normalize("NFD", text.lower().strip())
    s = _diacritics.sub("", d).replace("-", " ").replace("_", " ")
    return _spaces.sub(" ", _non_alnum.sub(" ", s)).strip()


E_NUM = re.compile(r"\be\s?-?\s?(\d{3})([a-z]{0,3})\b", re.IGNORECASE)


def extract_e_number(text: str) -> str | None:
    m = E_NUM.search(text)
    return f"E{m.group(1)}{m.group(2).lower()}" if m else None


# --- mirrors IngredientListParser -------------------------------------------
CLASSES = (
    r"acidity regulator(s)?|flavour enhancer(s)?|flavor enhancer(s)?|"
    r"anti[ -]?caking agent(s)?|flour treatment agent(s)?|raising agent(s)?|"
    r"glazing agent(s)?|gelling agent(s)?|bulking agent(s)?|firming agent(s)?|"
    r"colour(s)?|color(s)?|colouring(s)?|coloring(s)?|preservative(s)?|"
    r"emulsifier(s)?|antioxidant(s)?|stabiliser(s)?|stabilizer(s)?|"
    r"thickener(s)?|thickening agent(s)?|sweetener(s)?|humectant(s)?|"
    r"carrier(s)?|propellant(s)?|acid|noodles|seasoning"
)
PREFIX = re.compile(rf"^({CLASSES})\s*[:\-]\s*", re.IGNORECASE)
CLASS_ONLY = re.compile(rf"^({CLASSES})$", re.IGNORECASE)
LEAD = re.compile(r"^\s*(ingredients?|composition|contains)\s*[:\-]\s*", re.IGNORECASE)
PCT = re.compile(r"\d+([.,]\d+)?\s*%")
# Mirrors IngredientListParser.boilerplate, including the hygiene and household
# phrases. A competitor's app displayed "For external use only" and "Keep out
# of" as if they were ingredients; these cases exist to prove we do not.
BOILER = re.compile(
    r"^(gluten free|dairy free|sugar free|contains a source of.*|may contain.*|"
    r"suitable for.*|allergy advice.*|produced in.*|best before.*|"
    r"store .*|keep .*|use by.*|once opened.*|packed in.*|made in.*|"
    r"for external use only|external use only|"
    r"direction(s)?.*|instruction(s)?.*|how to use.*|usage.*|"
    r"warning(s)?.*|caution.*|precaution(s)?.*|"
    r"avoid contact.*|in case of.*|if swallowed.*|if irritation.*|"
    r"discontinue use.*|rinse.*|wet hands.*|apply .*|"
    r"not to be taken.*|for best results.*|"
    r"batch no.*|mfg.*|mfd.*|exp.*|lot no.*|net (wt|weight).*|"
    r"marketed by.*|manufactured by.*|imported by.*|customer care.*|"
    r"consumer complaint.*|shelf life.*|"
    r"recyclable|please recycle.*|dispose of.*)$", re.IGNORECASE)


def split_top(text: str) -> list[str]:
    out, buf, depth = [], [], 0
    for i, ch in enumerate(text):
        if ch in "([{":
            depth += 1; buf.append(ch)
        elif ch in ")]}":
            depth = max(0, depth - 1); buf.append(ch)
        elif depth == 0 and ch in ",;":
            out.append("".join(buf)); buf = []
        elif (depth == 0 and ch == "." and not
              (i and text[i-1].isdigit() and i+1 < len(text) and text[i+1].isdigit())):
            out.append("".join(buf)); buf = []
        else:
            buf.append(ch)
    if "".join(buf).strip():
        out.append("".join(buf))
    return out


BRACKET = re.compile(r"[(\[][^()\[\]]*[)\]]")


def tidy(tok: str) -> str:
    s = _spaces.sub(" ", PCT.sub(" ", tok)).strip()
    s = PREFIX.sub("", s).strip().strip(",.;:- ")
    s = _spaces.sub(" ", s)
    s = re.sub(r"^(and|or|with|from|of)\s+", "", s, flags=re.IGNORECASE)
    if len(s) < 2 or len(s) > 80 or not any(c.isalpha() for c in s):
        return ""
    if BOILER.match(s) or CLASS_ONLY.match(s):
        return ""
    return s


def parse(raw: str) -> list[str]:
    cleaned = LEAD.sub("", raw.replace("\n", " "))
    cleaned = re.sub(r"[*†‡•·]", " ", cleaned)
    tokens: list[str] = []
    for seg in split_top(cleaned):
        bodies = re.findall(r"[(\[]([^()\[\]]*)[)\]]", seg)
        outer = BRACKET.sub(" ", seg)
        for cand in [outer] + [b for body in bodies for b in split_top(body)]:
            t = tidy(cand)
            if t:
                tokens.append(t)
    seen, uniq = set(), []
    for t in tokens:
        if t.lower() not in seen:
            seen.add(t.lower()); uniq.append(t)
    return uniq


def main() -> int:
    doc = json.loads(SEED.read_text(encoding="utf-8"))
    entries = doc["ingredients"]

    by_name, by_syn, by_e = {}, {}, {}
    for e in entries:
        by_name[normalize(e["name"])] = e
        if e.get("eNumber"):
            by_e[e["eNumber"]] = e
        for s in e.get("synonyms", []):
            by_syn.setdefault(normalize(s), e)

    index = sorted(
        {**by_syn, **by_name}.items(), key=lambda kv: -len(kv[0])
    )

    def match(tok: str):
        n = normalize(tok)
        if n in by_name:
            return by_name[n], "exact"
        if n in by_syn:
            return by_syn[n], "synonym"
        num = extract_e_number(tok)
        if num and num in by_e:
            return by_e[num], "e-number"
        words = n.split()
        if len(words) >= 2:
            for key, e in index:
                kw = key.split()
                if len(kw) > len(words) or len(key) < 4:
                    continue
                for i in range(len(words) - len(kw) + 1):
                    if words[i:i + len(kw)] == kw:
                        return e, "containment"
        return None, "none"

    grand_tok = grand_hit = grand_assessed = 0
    print(f"Dictionary: {len(entries)} entries, {len(by_syn)} synonym keys\n")

    for title, raw in LABELS.items():
        toks = parse(raw)
        hits, assessed, misses = 0, 0, []
        for t in toks:
            e, how = match(t)
            if e:
                hits += 1
                if e["riskTier"] != "NOT_ASSESSED":
                    assessed += 1
            else:
                misses.append(t)
        grand_tok += len(toks); grand_hit += hits; grand_assessed += assessed
        pct = 100 * hits // len(toks) if toks else 0
        print(f"{title}")
        print(f"   tokens {len(toks):3}   recognised {hits:3} ({pct:3}%)   "
              f"assessed {assessed:3}")
        if misses:
            print(f"   not recognised: {', '.join(misses)}")
        print()

    print("=" * 62)
    print(f"OVERALL  {grand_hit}/{grand_tok} recognised "
          f"({100 * grand_hit // grand_tok}%), {grand_assessed} assessed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
