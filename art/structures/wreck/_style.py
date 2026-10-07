"""The wrecks' shared palette (drowned timber: dark oak and spruce planks, stripped logs, mossy and plain cobblestone
ballast, tuff and prismarine speckles for the barnacle crust, iron bars and chains), the loot chest and a few
seabed helpers for the generators in this folder (art/README.md, "Wrecks (ST5)").

Wrecks are single pieces placed on the sea floor, not jigsaw pieces: no connectors. y 0 is the **seabed row** (the
top row of the terrain's sand or gravel; the piece's own thin bed replaces it), everything above stands in water.
Every block that has a ``waterlogged`` property is written with ``waterlogged=true``.

Randomness (missing planks, speckles, the bed's ragged edge) comes from ``random.Random(<fixed seed>)`` per piece,
so a rebuild gives the same blocks."""
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import buildspec  # noqa: E402

MOD = buildspec.MOD
LOOT_TABLE = f"{MOD}:chests/wreck"

PALETTE = {
    # seabed
    "sand": "minecraft:sand",
    "gravel": "minecraft:gravel",
    "clay": "minecraft:clay",
    # ballast and crust
    "cobble": "minecraft:cobblestone",
    "mossy": "minecraft:mossy_cobblestone",
    "tuff": "minecraft:tuff",
    "prismarine": "minecraft:prismarine",
    "dark_prismarine": "minecraft:dark_prismarine",
    # timber
    "dark": "minecraft:dark_oak_planks",
    "spruce": "minecraft:spruce_planks",
    "dark_post": "minecraft:stripped_dark_oak_log[axis=y]",
    "dark_beam_x": "minecraft:stripped_dark_oak_log[axis=x]",
    "dark_beam_z": "minecraft:stripped_dark_oak_log[axis=z]",
    "spruce_post": "minecraft:stripped_spruce_log[axis=y]",
    "spruce_beam_x": "minecraft:stripped_spruce_log[axis=x]",
    "spruce_beam_z": "minecraft:stripped_spruce_log[axis=z]",
    "mast": "minecraft:spruce_log[axis=y]",
    "mast_x": "minecraft:spruce_log[axis=x]",
    "mast_z": "minecraft:spruce_log[axis=z]",
    # fittings (waterlogged wherever the block allows it)
    "fence": "minecraft:spruce_fence[waterlogged=true]",
    "dark_fence": "minecraft:dark_oak_fence[waterlogged=true]",
    "bars": "minecraft:iron_bars[waterlogged=true]",
    "chain_y": "minecraft:chain[axis=y,waterlogged=true]",
    "chain_x": "minecraft:chain[axis=x,waterlogged=true]",
    "chain_z": "minecraft:chain[axis=z,waterlogged=true]",
    "pane": "minecraft:glass_pane[waterlogged=true]",
    "lantern": "minecraft:lantern[hanging=false,waterlogged=true]",
    "lantern_hanging": "minecraft:lantern[hanging=true,waterlogged=true]",
    "crate": f"{MOD}:cargo_crate",
    "cargo_barrel": f"{MOD}:cargo_barrel",
    "dark_slab": "minecraft:dark_oak_slab[type=bottom,waterlogged=true]",
    "dark_slab_top": "minecraft:dark_oak_slab[type=top,waterlogged=true]",
    "spruce_slab": "minecraft:spruce_slab[type=bottom,waterlogged=true]",
    "cobble_slab": "minecraft:cobblestone_slab[type=bottom,waterlogged=true]",
    "mossy_wall": "minecraft:mossy_cobblestone_wall[waterlogged=true]",
}


def stairs(block: str, facing: str, half: str = "bottom") -> str:
    return f"minecraft:{block}_stairs[facing={facing},half={half},shape=straight,waterlogged=true]"


def trapdoor(block: str, facing: str, half: str = "bottom", open_: bool = False) -> str:
    return (f"minecraft:{block}_trapdoor[facing={facing},half={half},open={'true' if open_ else 'false'},"
            f"powered=false,waterlogged=true]")


def barrel(facing: str) -> str:
    """A vanilla barrel (no inventory loot; a keg lying on its side when ``facing`` is horizontal)."""
    return f"minecraft:barrel[facing={facing},open=false]"


def loot_chest(piece, x, y, z, facing):
    """A vanilla chest (waterlogged) with the wreck loot table in its block entity data; no ``LootTableSeed``, so the
    loot is rolled when a player first opens it. Written as a ``block_entity`` operation through ``piece.jigsaws``
    (``Piece.spec`` writes every entry there that way)."""
    piece._check(x, y, z)
    piece.blocks.pop((x, y, z), None)
    piece.jigsaws[(x, y, z)] = {
        "block": f"minecraft:chest[facing={facing},type=single,waterlogged=true]",
        "data": {"LootTable": LOOT_TABLE},
    }


def sea_chest(piece, x, y, z, facing):
    """The mod's sea chest, empty: its block entity has no loot table (``SeaChestBlockEntity`` is a plain container)."""
    piece.put(x, y, z, f"{MOD}:sea_chest[facing={facing}]")


def crust(rng: random.Random, base: str, amount: float = 0.12) -> str:
    """``base`` or, with chance ``amount``, a barnacle speckle (tuff, prismarine or, rarely, dark prismarine)."""
    r = rng.random()
    if r >= amount:
        return base
    r /= amount
    if r < 0.5:
        return "tuff"
    if r < 0.9:
        return "prismarine"
    return "dark_prismarine"


def seabed(rng: random.Random) -> str:
    """A seabed block: mostly sand, some gravel, a little clay."""
    r = rng.random()
    if r < 0.68:
        return "sand"
    if r < 0.92:
        return "gravel"
    return "clay"


def bed_disc(piece, rng: random.Random, cx: float, cz: float, rx: float, rz: float, ragged: float = 0.25):
    """The piece's thin bed: seabed blocks on row y 0 inside a ragged ellipse, never over a block already there."""
    sx, _, sz = piece.size
    for z in range(sz):
        for x in range(sx):
            d = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
            if d <= 1.0 - ragged * rng.random() and piece.get(x, 0, z) is None and (x, 0, z) not in piece.jigsaws:
                piece.put(x, 0, z, seabed(rng))


def drift(piece, x, y, z):
    """A sand drift block where nothing stands yet (sand banked against a wreck)."""
    if piece.get(x, y, z) is None and (x, y, z) not in piece.jigsaws:
        piece.put(x, y, z, "sand")


def underpin(piece, rng: random.Random):
    """Seabed under every block on row y 1 that has nothing under it (the bed's ragged edge never leaves one
    floating)."""
    for (x, y, z) in sorted(list(piece.blocks) + list(piece.jigsaws)):
        if y == 1 and piece.get(x, 0, z) is None:
            piece.put(x, 0, z, seabed(rng))
