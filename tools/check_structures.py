"""Check every structure file of the mod for block entity data that does not belong to its block (FIX3).

Usage:
    python3 tools/check_structures.py                  # every .nbt under common/src (structures, ship templates, Ponder)
    python3 tools/check_structures.py a.nbt [b.nbt ...]
    python3 tools/check_structures.py --vanilla-table  # the vanilla table as JSON (SchemToStructureTest compares it)

A structure template's ``blocks`` entry may carry an ``nbt`` compound, the block entity the template places. Reported:

- **stray**: block entity data on a state that has no block entity (a non-entity block, or one of ours whose rule says
  that state has none);
- **wrong id**: block entity data whose ``id`` is not the block entity that state creates (one block's data copied onto
  another);
- **worldgen**: an entity block whose state creates no block entity, in a template the world generates (anything but
  ``assets/.../ponder``): worldgen leaves a ``DUMMY`` block entity tag at every entity block, and the level chunk then
  logs "Tried to load a block entity for block ... but failed" for it.

The rules: :data:`MOD_RULES` for our blocks (keep in step with each block's ``newBlockEntity``; the registry-backed
``StructureBlockEntityGameTests`` is the authority and checks the same templates in a game) and :data:`VANILLA` for
vanilla's (from ``BlockEntityType``; ``SchemToStructureTest`` compares it with vanilla's registry). Any other block has
no block entity. ``tools/schem_to_structure.py`` runs the same check on every file it writes and refuses a bad one.
Exit code 1 when anything is reported. Standard library only.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Callable

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import schem_to_structure as nbt  # noqa: E402  (same folder: the NBT reader)

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "common" / "src"
MOD = "pirates_n_ships"

Rule = Callable[[dict], "str | None"]


def always(entity: str) -> Rule:
    return lambda props: entity


def by_property(key: str, value: str, entity: str, otherwise: str | None) -> Rule:
    return lambda props: entity if props.get(key) == value else otherwise


# Our entity blocks: registry path -> rule (block state properties -> block entity id, or None for none).
MOD_RULES: dict[str, Rule] = {
    "captains_almanac": always(f"{MOD}:captains_almanac"),
    "bilge_pump": always(f"{MOD}:bilge_pump"),
    "boarding_plank": always(f"{MOD}:boarding_plank"),
    # the upper half's block entity is an empty PartBlockEntity (FIX3)
    "brig_door": by_property("half", "lower", f"{MOD}:brig_door", f"{MOD}:brig_door_upper"),
    # the rear half's block entity is an empty PartBlockEntity (FIX3)
    "cannon": by_property("part", "front", f"{MOD}:cannon", f"{MOD}:cannon_rear"),
    "capstan": always(f"{MOD}:capstan"),
    "cargo_barrel": always(f"{MOD}:cargo_container"),
    "cargo_crate": always(f"{MOD}:cargo_container"),
    "chainplate": always(f"{MOD}:net_fitting"),
    "cleat": always(f"{MOD}:cleat"),
    "flagpole": always(f"{MOD}:flagpole"),
    "harbor_desk": always(f"{MOD}:harbor_desk"),
    "helm": always(f"{MOD}:helm"),
    "map_tile": always(f"{MOD}:map_tile"),
    "mast_hoop": always(f"{MOD}:net_fitting"),
    "mooring_ring": always(f"{MOD}:mooring_ring"),
    "nameplate": always(f"{MOD}:nameplate"),
    "pantry": always(f"{MOD}:pantry"),
    "ratline_net_cell": always(f"{MOD}:ratline_net_cell"),
    # RL5: a railing latch's net end (the railing itself never carries a block entity)
    "railing_latch": always(f"{MOD}:net_fitting"),
    "sea_chest": always(f"{MOD}:sea_chest"),
    # a BedBlock without vanilla's BedBlockEntity: an entity block with no block entity, never in a generated structure
    "sea_cot": lambda props: None,
    "ships_bell": always(f"{MOD}:ships_bell"),
    "swivel_gun": always(f"{MOD}:swivel_gun"),
    "water_barrel": always(f"{MOD}:water_barrel"),
    "yard": always(f"{MOD}:yard"),
}

COLOURS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
           "blue", "brown", "green", "red", "black"]
WOODS = ["oak", "spruce", "birch", "acacia", "cherry", "jungle", "dark_oak", "crimson", "warped", "mangrove", "bamboo"]
HEADS = ["skeleton_skull", "skeleton_wall_skull", "wither_skeleton_skull", "wither_skeleton_wall_skull", "zombie_head",
         "zombie_wall_head", "player_head", "player_wall_head", "creeper_head", "creeper_wall_head", "dragon_head",
         "dragon_wall_head", "piglin_head", "piglin_wall_head"]

# Vanilla 1.21.1 entity blocks: block path -> the block entity type path every state creates (BlockEntityType's
# registrations), None for one that creates none (the moving piston's comes from a static factory, not newBlockEntity).
VANILLA: dict[str, str | None] = {
    "furnace": "furnace", "chest": "chest", "trapped_chest": "trapped_chest", "ender_chest": "ender_chest",
    "jukebox": "jukebox", "dispenser": "dispenser", "dropper": "dropper", "spawner": "mob_spawner",
    "moving_piston": None, "brewing_stand": "brewing_stand", "enchanting_table": "enchanting_table",
    "end_portal": "end_portal", "beacon": "beacon", "daylight_detector": "daylight_detector", "hopper": "hopper",
    "comparator": "comparator", "structure_block": "structure_block", "end_gateway": "end_gateway",
    "command_block": "command_block", "chain_command_block": "command_block", "repeating_command_block": "command_block",
    "shulker_box": "shulker_box", "conduit": "conduit", "barrel": "barrel", "smoker": "smoker",
    "blast_furnace": "blast_furnace", "lectern": "lectern", "bell": "bell", "jigsaw": "jigsaw", "campfire": "campfire",
    "soul_campfire": "campfire", "bee_nest": "beehive", "beehive": "beehive", "sculk_sensor": "sculk_sensor",
    "calibrated_sculk_sensor": "calibrated_sculk_sensor", "sculk_catalyst": "sculk_catalyst",
    "sculk_shrieker": "sculk_shrieker", "chiseled_bookshelf": "chiseled_bookshelf",
    "suspicious_sand": "brushable_block", "suspicious_gravel": "brushable_block", "decorated_pot": "decorated_pot",
    "crafter": "crafter", "trial_spawner": "trial_spawner", "vault": "vault",
    **{f"{w}_sign": "sign" for w in WOODS}, **{f"{w}_wall_sign": "sign" for w in WOODS},
    **{f"{w}_hanging_sign": "hanging_sign" for w in WOODS}, **{f"{w}_wall_hanging_sign": "hanging_sign" for w in WOODS},
    **{h: "skull" for h in HEADS},
    **{f"{c}_banner": "banner" for c in COLOURS}, **{f"{c}_wall_banner": "banner" for c in COLOURS},
    **{f"{c}_shulker_box": "shulker_box" for c in COLOURS},
    **{f"{c}_bed": "bed" for c in COLOURS},
}


def entity_of(name: str, props: dict) -> tuple[bool, str | None]:
    """(is an entity block, the block entity id its state creates or None)."""
    namespace, _, path = name.partition(":")
    if namespace == MOD and path in MOD_RULES:
        return True, MOD_RULES[path](props)
    if namespace == "minecraft" and path in VANILLA:
        return True, None if VANILLA[path] is None else "minecraft:" + VANILLA[path]
    return False, None


def generated_by_worldgen(path: Path) -> bool:
    """Ponder scene templates (assets/.../ponder) are only ever loaded by Ponder, never placed by worldgen."""
    return "ponder" not in path.parts or "assets" not in path.parts


def check_root(root: nbt.Tag, worldgen: bool = True) -> list[str]:
    """Problems of one structure template compound (see the module comment)."""
    tags = root.value
    palettes = []
    if "palettes" in tags:
        palettes = [p.value for p in tags["palettes"].value]
    elif "palette" in tags:
        palettes = [tags["palette"].value]
    problems = []
    for palette in palettes:
        states = []
        for entry in palette:
            e = entry.value
            props = {k: v.value for k, v in e["Properties"].value.items()} if "Properties" in e else {}
            states.append((e["Name"].value, props))
        for block in tags.get("blocks", nbt.Tag(nbt.LIST, [])).value:
            b = block.value
            name, props = states[b["state"].value]
            pos = [p.value for p in b["pos"].value]
            state = name + ("[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]" if props else "")
            entity_block, expected = entity_of(name, props)
            data = b.get("nbt")
            given = data.value["id"].value if data is not None and "id" in data.value else None
            if data is not None and expected is None:
                problems.append(f"{pos} {state}: stray block entity data {given} (this state has no block entity)")
            elif data is not None and given != expected:
                problems.append(f"{pos} {state}: block entity data {given}, but this state's block entity is {expected}")
            elif entity_block and expected is None and worldgen:
                problems.append(f"{pos} {state}: an entity block without a block entity in a generated structure "
                                f"(worldgen logs a failed block entity load)")
    return problems


def check_file(path: Path) -> list[str]:
    return check_root(nbt.read_nbt(path.read_bytes()), generated_by_worldgen(path))


def all_files() -> list[Path]:
    return sorted(p for p in SOURCES.rglob("*.nbt") if "build" not in p.relative_to(SOURCES).parts)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("files", nargs="*", type=Path, help="structure files (default: every .nbt under common/src)")
    parser.add_argument("--vanilla-table", action="store_true", help="print the vanilla table as JSON and exit")
    args = parser.parse_args(argv)
    if args.vanilla_table:
        print(json.dumps({f"minecraft:{k}": None if v is None else f"minecraft:{v}" for k, v in sorted(VANILLA.items())},
                         indent=1))
        return 0
    files = args.files or all_files()
    bad = 0
    entities = 0
    for path in files:
        problems = check_file(path)
        root = nbt.read_nbt(path.read_bytes()).value
        entities += sum(1 for b in root.get("blocks", nbt.Tag(nbt.LIST, [])).value if "nbt" in b.value)
        for problem in problems:
            print(f"{path}: {problem}")
        bad += bool(problems)
    print(f"{len(files)} structure files, {entities} block entities, {bad} with problems")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
