#!/usr/bin/env python3
"""Shared inputs of tools/gen_almanac.py: our registered items (GUIDE2), blocks and entity types (ALM10).

Python 3 standard library only; imported, not run. Since GUIDE2 the Captain's Almanac is the only in-game guide, and
this is what is left of the older page generator. Tested by tools/test_gen_almanac.py.

Inputs (relative to the repository root, see set_root):
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
    global ROOT, ASSETS_MAIN, ASSETS_GEN, LANG
    ROOT = Path(root).resolve()
    common = ROOT / "common" / "src"
    ASSETS_MAIN = common / "main" / "resources" / "assets" / NS
    ASSETS_GEN = common / "generated" / "resources" / "assets" / NS
    LANG = ASSETS_GEN / "lang" / "en_us.json"


set_root(Path(__file__).resolve().parent.parent)


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
        raise SystemExit("almanac_lib: items the Almanac cannot name or show:\n  " + "\n  ".join(problems))
    return by_name, names, set(names)


def load_registry():
    """(our block ids, our entity type ids), "pirates_n_ships:<id>", from the lang's "block.pirates_n_ships.<id>" and
    "entity.pirates_n_ships.<id>" keys: every registered block and entity type of ours has its name there (ALM10's 3D
    spotlights name them)."""
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    blocks, entities = set(), set()
    for key in lang:
        m = re.fullmatch(rf"(block|entity)\.{NS}\.([a-z0-9_]+)", key)
        if m:
            (blocks if m.group(1) == "block" else entities).add(f"{NS}:{m.group(2)}")
    return blocks, entities
