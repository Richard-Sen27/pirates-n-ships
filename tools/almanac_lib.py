#!/usr/bin/env python3
"""Shared inputs of tools/gen_almanac.py: our items, the sections of docs/guide.md and its table cells (GUIDE2).

Python 3 standard library only; imported, not run. Since GUIDE2 the Captain's Almanac is the only in-game guide, and
these helpers are what is left of the older page generator. Tested by tools/test_gen_almanac.py.

Inputs (relative to the repository root, see set_root):
    docs/guide.md                                                         the player guide (single source of truth)
    common/src/generated/resources/assets/pirates_n_ships/lang/en_us.json display names -> registry ids
    common/src/{generated,main}/resources/assets/pirates_n_ships/models/item/*.json   item models (checked, below)

Which ids are items: the registered items, i.e. the ids with an "item.pirates_n_ships.<id>" lang key, plus the
"block.pirates_n_ships.<id>" keys that have an item model (block items; a block key without one is a block without an
item and is skipped). Every "item." id must have an item model, or load_items stops naming it. An item model file
without a lang key is ignored when another item model lists it as an "overrides" target (a variant such as
pistol_loaded, selected by an item property predicate), and stops load_items as an orphan otherwise.
"""
import json
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ next to the tools

NS = "pirates_n_ships"


def set_root(root):
    """Points the input paths at a repository root (the real one by default; the tests pass a temp copy)."""
    global ROOT, GUIDE_MD, ASSETS_MAIN, ASSETS_GEN, LANG
    ROOT = Path(root).resolve()
    GUIDE_MD = ROOT / "docs" / "guide.md"
    common = ROOT / "common" / "src"
    ASSETS_MAIN = common / "main" / "resources" / "assets" / NS
    ASSETS_GEN = common / "generated" / "resources" / "assets" / NS
    LANG = ASSETS_GEN / "lang" / "en_us.json"


set_root(Path(__file__).resolve().parent.parent)

# Vanilla items that guide.md tables name in lower case (trade goods).
VANILLA_ITEMS = {
    "sugar": "minecraft:sugar",
    "cod": "minecraft:cod",
    "oak log": "minecraft:oak_log",
    "iron ingot": "minecraft:iron_ingot",
    "wheat": "minecraft:wheat",
    "leather": "minecraft:leather",
    "cocoa beans": "minecraft:cocoa_beans",
    "gunpowder": "minecraft:gunpowder",
}

# Table columns whose cells name items.
ITEM_COLUMNS = {"block", "item", "flag", ""}

SECTION_RE = re.compile(r"^## (\d+)\. (.+?)\s*$")
SUBHEADING_RE = re.compile(r"^### (.+?)\s*$")


def github_slug(heading):
    """The anchor GitHub gives a heading (what guide.md's own links use)."""
    s = heading.strip().lower()
    s = re.sub(r"[^\w\- ]", "", s)
    return s.replace(" ", "-")


def full_id(item):
    return item if ":" in item else f"{NS}:{item}"


# ---------------------------------------------------------------------------------------------------- inputs

def override_target(model):
    """The item model id ("pirates_n_ships:x") an "overrides" entry's model reference names, else None."""
    if not isinstance(model, str):
        return None
    ns, _, path = model.rpartition(":")
    if (ns or "minecraft") != NS or not path.startswith("item/"):
        return None
    return f"{NS}:{path[len('item/'):]}"


def load_items():
    """(display name lower -> id, id -> display name, set of item ids) of our items.

    The items are the registered ones: every "item." lang key, and every "block." key with an item model (generated
    or hand-made). Fails, naming the ids, when an "item." key has no item model (the guide could not show it) or an
    item model has no lang key and is no "overrides" target of another item model (an orphan: a model of nothing, or
    an item without a name). Override targets without a lang key are variants of an item (pistol_loaded), ignored.
    """
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    models = {}
    for base in (ASSETS_MAIN, ASSETS_GEN):
        d = base / "models" / "item"
        if d.is_dir():
            models.update((f"{NS}:{p.stem}", p) for p in d.glob("*.json"))
    variants = set()
    for rid in sorted(models):
        overrides = json.loads(models[rid].read_text(encoding="utf-8")).get("overrides")
        for entry in overrides if isinstance(overrides, list) else []:
            target = override_target(entry.get("model")) if isinstance(entry, dict) else None
            if target is not None and target != rid:
                variants.add(target)
    by_name, names, problems = {}, {}, []
    for key in sorted(lang):
        m = re.fullmatch(rf"(item|block)\.{NS}\.([a-z0-9_]+)", key)
        if not m:
            continue
        rid = f"{NS}:{m.group(2)}"
        if rid not in models:
            if m.group(1) == "item":
                problems.append(f"{rid}: lang key {key} but no item model (models/item/{m.group(2)}.json)")
            continue
        names.setdefault(rid, lang[key])
        by_name.setdefault(lang[key].lower(), rid)
    for rid in sorted(set(models) - set(names) - variants):
        problems.append(f"{rid}: item model but no lang name (item.{NS}.{rid.split(':')[1]} or block.{NS}.…) "
                        f"and no item model lists it under \"overrides\"")
    if problems:
        raise SystemExit("almanac_lib: items the guide cannot name or show:\n  " + "\n  ".join(problems))
    return by_name, names, set(names)


def parse_sections(text):
    """[(number, title, [body lines])] of the "## N. Title" sections, in order."""
    sections, current = [], None
    for line in text.splitlines():
        m = SECTION_RE.match(line)
        if m:
            current = (int(m.group(1)), m.group(2), [])
            sections.append(current)
        elif line.startswith("## "):
            current = None  # "## Contents" and other unnumbered sections are no chapters
        elif current is not None:
            current[2].append(line)
    return sections


# ---------------------------------------------------------------------------------------------------- table cells

class Resolver:
    def __init__(self, by_name):
        self.by_name = by_name

    def one(self, name):
        n = name.strip().lower()
        return self.by_name.get(n) or VANILLA_ITEMS.get(n)

    def cell(self, text):
        """Item ids for a cell that consists only of item names, else None. Keeps a trailing "(...)" note."""
        m = re.fullmatch(r"(.*?)\s*(\([^)]*\))?", text.strip())
        names, note = m.group(1), m.group(2)
        if not names:
            return None
        single = self.one(names)
        if single:
            return [single], note
        parts = [p for p in re.split(r",\s*|\s+and\s+", names) if p]
        if len(parts) < 2:
            return None
        suffix = " ".join(parts[-1].split()[1:])  # "Skull Figurehead" -> "Figurehead"
        ids = []
        for p in parts:
            rid = self.one(p) or (self.one(f"{p} {suffix}") if suffix else None)
            if not rid:
                return None
            ids.append(rid)
        return ids, note


def split_row(line):
    """Cells of a GFM table row; "\\|" inside a cell is not a separator."""
    inner = line.strip()[1:-1] if line.strip().endswith("|") else line.strip()[1:]
    cells, cur, i = [], "", 0
    while i < len(inner):
        if inner[i] == "\\" and i + 1 < len(inner) and inner[i + 1] == "|":
            cur += "\\|"
            i += 2
            continue
        if inner[i] == "|":
            cells.append(cur)
            cur = ""
        else:
            cur += inner[i]
        i += 1
    cells.append(cur)
    return [c.strip() for c in cells]
