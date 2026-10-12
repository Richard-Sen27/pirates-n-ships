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
  logs "Tried to load a block entity for block ... but failed" for it;
- **door** (STR6, only in the world's structure pieces, ``data/.../structure/<group>`` but not ``ships``): a door whose
  approach is blocked. On both sides of every door (the cell one step along its ``facing`` and the cell one step
  against it) the door's two rows must be passable: no block there in the template (the generator writes no air, so
  the cell stays as the terrain leaves it), or a block without a collision box (:func:`passable`); a jigsaw there counts
  as its ``final_state``. A bottom slab on the door's lower row is a step up: then the two rows above it
  must be passable. A side outside the template's box is not judged here (the neighbouring piece's business:
  ``DoorAccess`` checks the placed structures in the layout GameTests).

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
    # NB2: the anchor counts the postings, the other three parts carry an empty PartBlockEntity (FIX3)
    "market_notice_board": by_property("part", "bottom_left", f"{MOD}:market_notice_board", f"{MOD}:market_notice_board_part"),
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


# STR6: blocks without a collision box that may stand on a door's approach (carpets, plates, torches, plants, signs,
# banners, rails, buttons, snow of one layer); cobwebs have none but hold a walker fast, so they count as blocking.
PASSABLE_PARTS = ("carpet", "pressure_plate", "torch", "rail", "button", "_sign", "banner", "lever", "tripwire",
                  "redstone_wire", "short_grass", "fern", "dead_bush", "sapling", "dandelion", "poppy", "orchid",
                  "allium", "azure_bluet", "tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "vine",
                  "seagrass")
PASSABLE_BLOCKS = {"minecraft:air", "minecraft:cave_air", "minecraft:water", "minecraft:light", "minecraft:structure_void"}
STEP_BLOCKS = ("_slab",)
DOOR_SIDES = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def passable(name: str | None, props: dict) -> bool:
    """Whether a walker passes through this block: nothing (None), air or water, or a block without a collision box."""
    if name is None or name in PASSABLE_BLOCKS:
        return True
    if name == "minecraft:snow":
        return props.get("layers", "1") == "1"
    path = name.partition(":")[2]
    return name.startswith("minecraft:") and any(part in path for part in PASSABLE_PARTS) and "hanging" not in path


def is_step(name: str | None, props: dict) -> bool:
    """A bottom slab: a half-block step a walker climbs (a stair's collision box reaches the full block)."""
    return name is not None and name.endswith(STEP_BLOCKS) and props.get("type", props.get("half")) == "bottom"


def checks_doors(path: Path) -> bool:
    """The world's structure pieces (``data/<ns>/structure/<group>/``), not the ship templates nor Ponder scenes."""
    parts = path.parts
    return ("data" in parts and "structure" in parts and "ships" not in parts and "ponder" not in parts)


def door_problems(tags: dict) -> list[str]:
    """STR6: every door's approach on both sides is passable (see the module comment, **door**)."""
    size = [v.value for v in tags["size"].value]
    palette = tags["palettes"].value[0].value if "palettes" in tags else tags["palette"].value
    states = []
    for entry in palette:
        e = entry.value
        props = {k: v.value for k, v in e["Properties"].value.items()} if "Properties" in e else {}
        states.append((e["Name"].value, props))
    cells = {}
    for block in tags.get("blocks", nbt.Tag(nbt.LIST, [])).value:
        b = block.value
        pos = tuple(p.value for p in b["pos"].value)
        name, props = states[b["state"].value]
        if name == "minecraft:jigsaw" and "nbt" in b and "final_state" in b["nbt"].value:
            final = b["nbt"].value["final_state"].value
            fname, _, rest = final.partition("[")
            fprops = dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if "=" in kv)
            name, props = (fname if ":" in fname else "minecraft:" + fname), fprops
        cells[pos] = (name, props)
    problems = []
    for (x, y, z), (name, props) in sorted(cells.items(), key=lambda c: (c[0][1], c[0][2], c[0][0])):
        if not name.endswith("_door") or props.get("half") != "lower" or "facing" not in props:
            continue
        dx, dz = DOOR_SIDES[props["facing"]]
        for sign in (1, -1):
            cx, cz = x + dx * sign, z + dz * sign
            if not (0 <= cx < size[0] and 0 <= cz < size[2]):
                continue
            low = cells.get((cx, y, cz), (None, {}))
            rows = (y, y + 1)
            if is_step(*low):
                rows = (y + 1, y + 2)
            blocked = [(r, cells[(cx, r, cz)][0]) for r in rows if not passable(*cells.get((cx, r, cz), (None, {})))]
            if blocked:
                side = "facing" if sign == 1 else "behind"
                what = ", ".join(f"{n} at y {r}" for r, n in blocked)
                problems.append(f"[{x}, {y}, {z}] {name}[facing={props['facing']}]: the cell {side} it [{cx}, {cz}] is "
                                f"blocked ({what})")
    return problems


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
    root = nbt.read_nbt(path.read_bytes())
    problems = check_root(root, generated_by_worldgen(path))
    if checks_doors(path.resolve()):
        problems += door_problems(root.value)
    return problems


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
