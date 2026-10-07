"""The seafarer village's shared palette (Caribbean colonial: spruce and oak, stripped logs, cobblestone and stone
bricks, white render, red tile or dark roofs) and the import path for the generators in this folder."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

PALETTE = {
    # ground and masonry
    "cobble": "minecraft:cobblestone",
    "mossy": "minecraft:mossy_cobblestone",
    "gravel": "minecraft:gravel",
    "path": "minecraft:dirt_path",
    "coarse": "minecraft:coarse_dirt",
    "bricks": "minecraft:stone_bricks",
    "bricks_mossy": "minecraft:mossy_stone_bricks",
    "bricks_cracked": "minecraft:cracked_stone_bricks",
    "chiseled": "minecraft:chiseled_stone_bricks",
    # timber
    "spruce": "minecraft:spruce_planks",
    "oak": "minecraft:oak_planks",
    "dark": "minecraft:dark_oak_planks",
    "post": "minecraft:stripped_spruce_log[axis=y]",
    "beam_x": "minecraft:stripped_spruce_log[axis=x]",
    "beam_z": "minecraft:stripped_spruce_log[axis=z]",
    "oak_post": "minecraft:stripped_oak_log[axis=y]",
    "oak_beam_x": "minecraft:stripped_oak_log[axis=x]",
    "oak_beam_z": "minecraft:stripped_oak_log[axis=z]",
    "pile": "minecraft:spruce_log[axis=y]",
    "fence": "minecraft:spruce_fence",
    # walls and roofs
    "render": "minecraft:calcite",
    "sandstone": "minecraft:smooth_sandstone",
    "pane": "minecraft:glass_pane",
    "tile_ridge": "minecraft:brick_slab[type=bottom]",
    "dark_ridge": "minecraft:dark_oak_slab[type=bottom]",
    # lights
    "lantern": "minecraft:lantern[hanging=false]",
    "lantern_hanging": "minecraft:lantern[hanging=true]",
}

TILE = "minecraft:brick_stairs"        # red tile roofs (houses, tavern)
DARK = "minecraft:dark_oak_stairs"     # dark shingle roofs (harbor hut, shipwright's shed)


def lantern_post(piece, x, y, z, height=2):
    """A spruce fence post ``height`` high standing on (x, y - 1, z) with a lantern on top."""
    for dy in range(height):
        piece.put(x, y + dy, z, "fence")
    piece.put(x, y + height, z, "lantern")


def door(piece, x, y, z, facing="north", wood="spruce", hinge="left"):
    piece.put(x, y, z, f"minecraft:{wood}_door[facing={facing},half=lower,hinge={hinge},open=false]")
    piece.put(x, y + 1, z, f"minecraft:{wood}_door[facing={facing},half=upper,hinge={hinge},open=false]")


def table(piece, x, y, z):
    """A one-block table: a fence leg under a pressure plate top."""
    piece.put(x, y, z, "fence")
    piece.put(x, y + 1, z, "minecraft:spruce_pressure_plate")


def bed(piece, x, y, z, facing, colour="red"):
    """A bed whose foot is at (x, y, z) and whose head lies one block toward ``facing``."""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    piece.put(x, y, z, f"minecraft:{colour}_bed[facing={facing},part=foot]")
    piece.put(x + dx, y, z + dz, f"minecraft:{colour}_bed[facing={facing},part=head]")
