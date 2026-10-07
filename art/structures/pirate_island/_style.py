"""The pirate island camp's shared palette (rough and weathered: spruce and dark oak, stripped logs as posts, wool
canvas, mossy cobblestone, no clean stone bricks), its jigsaw vocabulary and the import path for the generators in
this folder.

The group's connectors (art/README.md, "Pirate island (ST2)") are added to ``buildspec.CONNECTORS`` here, so
``Piece.connector`` knows them; ``treasure`` adds the buried treasure marker."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import buildspec  # noqa: E402

MOD = buildspec.MOD
POOL = f"{MOD}:pirate_island"

buildspec.CONNECTORS.update({
    "path_out": ("path_in", f"{POOL}/paths", "aligned"),
    "path_in": ("path_out", buildspec.EMPTY_POOL, "aligned"),
    "hut_out": ("hut_in", f"{POOL}/huts", "rollable"),
    "hut_in": ("hut_out", buildspec.EMPTY_POOL, "rollable"),
    "jetty_out": ("jetty_in", f"{POOL}/jetty", "aligned"),
    "jetty_in": ("jetty_out", buildspec.EMPTY_POOL, "aligned"),
})

PALETTE = {
    # ground
    "sand": "minecraft:sand",
    "gravel": "minecraft:gravel",
    "path": "minecraft:dirt_path",
    "coarse": "minecraft:coarse_dirt",
    "cobble": "minecraft:cobblestone",
    "mossy": "minecraft:mossy_cobblestone",
    # timber
    "spruce": "minecraft:spruce_planks",
    "dark": "minecraft:dark_oak_planks",
    "post": "minecraft:stripped_spruce_log[axis=y]",
    "beam_x": "minecraft:stripped_spruce_log[axis=x]",
    "beam_z": "minecraft:stripped_spruce_log[axis=z]",
    "dark_post": "minecraft:stripped_dark_oak_log[axis=y]",
    "dark_beam_x": "minecraft:stripped_dark_oak_log[axis=x]",
    "pile": "minecraft:spruce_log[axis=y]",
    "dark_pile": "minecraft:dark_oak_log[axis=y]",
    "fence": "minecraft:spruce_fence",
    "dark_fence": "minecraft:dark_oak_fence",
    # canvas
    "canvas": "minecraft:white_wool",
    "canvas_grey": "minecraft:light_gray_wool",
    "canvas_brown": "minecraft:brown_wool",
    # roofs
    "thatch_ridge": "minecraft:dark_oak_slab[type=bottom]",
    "thatch_low": "minecraft:dark_oak_slab[type=bottom]",
    "thatch_high": "minecraft:dark_oak_slab[type=top]",
    # fittings
    "torch": "minecraft:torch",
    "lantern": "minecraft:lantern[hanging=false]",
    "lantern_hanging": "minecraft:lantern[hanging=true]",
    "cobweb": "minecraft:cobweb",
    "barrel": "minecraft:barrel[facing=up,open=false]",
}

THATCH = "minecraft:dark_oak_stairs"   # the palm-thatch look of the huts' roofs


def torch_post(piece, x, y, z, height=1, post="fence"):
    """A fence post ``height`` high standing on (x, y - 1, z) with a torch on top."""
    for dy in range(height):
        piece.put(x, y + dy, z, post)
    piece.put(x, y + height, z, "torch")


def stake(piece, x, y, z):
    """A palisade stake: a stripped spruce log with a sharpened fence tip."""
    piece.put(x, y, z, "post")
    piece.put(x, y + 1, z, "fence")


def table(piece, x, y, z):
    """A one-block table: a fence leg under a pressure plate top."""
    piece.put(x, y, z, "fence")
    piece.put(x, y + 1, z, "minecraft:spruce_pressure_plate[powered=false]")


def stool(piece, x, y, z, facing):
    """A stair as a stool; ``facing`` is the side of its back."""
    piece.put(x, y, z, f"minecraft:spruce_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]")


def bed(piece, x, y, z, facing, colour="brown"):
    """A bed (a bed roll) whose foot is at (x, y, z) and whose head lies one block toward ``facing``."""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    piece.put(x, y, z, f"minecraft:{colour}_bed[facing={facing},occupied=false,part=foot]")
    piece.put(x + dx, y, z + dz, f"minecraft:{colour}_bed[facing={facing},occupied=false,part=head]")


def treasure(piece, x, y, z):
    """The buried treasure marker (README, "Pirate island (ST2)"): a jigsaw named ``pirates_n_ships:treasure``, target
    and pool ``minecraft:empty``, which becomes sand; the world module turns it into a buried chest."""
    piece._jigsaw(x, y, z, "north", f"{MOD}:treasure", buildspec.EMPTY_POOL, buildspec.EMPTY_POOL, "minecraft:sand",
                  "aligned")


def flagpole(piece, x, y, z, height, facing):
    """A stack of ``height`` flagpoles standing on (x, y - 1, z); the top one flies the Jolly Roger toward ``facing``.

    The flag lives in the pole's block entity (``FlagpoleState``: kind and flag item); the ``flag`` block state only
    drives the model, so both are set. The block entity goes out as a ``block_entity`` operation through the same list
    as the jigsaws (``Piece.spec`` writes every entry of ``piece.jigsaws`` that way)."""
    for dy in range(height - 1):
        piece.put(x, y + dy, z, f"{MOD}:flagpole[facing={facing},flag=none]")
    top = (x, y + height - 1, z)
    piece._check(*top)
    piece.blocks.pop(top, None)
    piece.jigsaws[top] = {
        "block": f"{MOD}:flagpole[facing={facing},flag=jolly_roger]",
        "data": {"flagpole": {"kind": "jolly_roger", "item": {"id": f"{MOD}:jolly_roger_flag", "count": 1}}},
    }
