"""The navy outpost's shared palette (a naval fort: stone bricks and polished andesite masonry, oak and spruce
woodwork, blue wool and banners as the navy's colour, lanterns on iron bars), its jigsaw vocabulary and the fittings
the generators in this folder share. Also puts ``art/structures`` on the import path for ``buildspec``.

Jigsaw names (art/README.md, "Navy outpost (ST3)"): the curtain wall runs both ways from the fort gate along x, and a
jigsaw cannot mirror a piece, only turn it. A wall piece turned round to continue westward would point its guns
inland, so each direction has its own pair of names: ``wall_east_out``/``wall_east_in`` for the run that grows east and
``wall_west_out``/``wall_west_in`` for the one that grows west. Every wall piece carries all four (the east pair on
z 2, the west pair on z 3), so the run keeps its seaward side (-z) whichever way it grows."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import EMPTY_POOL, MOD  # noqa: E402

GROUP = "navy_outpost"

# name -> (target, pool it pulls from, joint); the *_in side never spawns anything
CONNECTORS = {
    "wall_east_out": ("wall_east_in", f"{MOD}:{GROUP}/walls", "aligned"),
    "wall_east_in": ("wall_east_out", EMPTY_POOL, "aligned"),
    "wall_west_out": ("wall_west_in", f"{MOD}:{GROUP}/walls", "aligned"),
    "wall_west_in": ("wall_west_out", EMPTY_POOL, "aligned"),
    "building_out": ("building_in", f"{MOD}:{GROUP}/buildings", "rollable"),
    "building_in": ("building_out", EMPTY_POOL, "rollable"),
    "quay_out": ("quay_in", f"{MOD}:{GROUP}/quay", "aligned"),
    "quay_in": ("quay_out", EMPTY_POOL, "aligned"),
}

# the curtain wall's section, shared by the fort gate, the wall and the tower so the walkway runs through
PARAPET_Z = 0          # the seaward face: solid to the parapet row, merlons above
BODY_Z = (1, 4)        # the solid wall body under the walkway
WALK = 4               # the walkway's top row (people stand on y 5)
MERLON = WALK + 2      # merlons stand on the parapet row (y 5)
EAST_Z, WEST_Z = 2, 3  # the rows of the two jigsaw pairs on the wall's ends (foundation row)

PALETTE = {
    # masonry
    "bricks": "minecraft:stone_bricks",
    "bricks_mossy": "minecraft:mossy_stone_bricks",
    "bricks_cracked": "minecraft:cracked_stone_bricks",
    "chiseled": "minecraft:chiseled_stone_bricks",
    "andesite": "minecraft:polished_andesite",
    "cobble": "minecraft:cobblestone",
    "gravel": "minecraft:gravel",
    "brick_slab": "minecraft:stone_brick_slab[type=bottom]",
    "andesite_slab": "minecraft:polished_andesite_slab[type=bottom]",
    # timber
    "oak": "minecraft:oak_planks",
    "spruce": "minecraft:spruce_planks",
    "spruce_slab": "minecraft:spruce_slab[type=bottom]",
    "post": "minecraft:stripped_spruce_log[axis=y]",
    "beam_x": "minecraft:stripped_spruce_log[axis=x]",
    "beam_z": "minecraft:stripped_spruce_log[axis=z]",
    "oak_post": "minecraft:stripped_oak_log[axis=y]",
    "fence": "minecraft:spruce_fence",
    # the navy's colour and lights
    "wool": "minecraft:blue_wool",
    "white_wool": "minecraft:white_wool",
    "carpet": "minecraft:blue_carpet",
    "bars": "minecraft:iron_bars",
    "lantern": "minecraft:lantern[hanging=false]",
    "lantern_hanging": "minecraft:lantern[hanging=true]",
    "ladder_s": "minecraft:ladder[facing=south]",
    "ladder_n": "minecraft:ladder[facing=north]",
}

DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def connector(piece, x, y, z, name: str, facing: str, final_state: str):
    """A navy outpost connector (see CONNECTORS); ``facing`` is where the jigsaw points (out of the piece)."""
    target, pool, joint = CONNECTORS[name]
    piece._jigsaw(x, y, z, facing, f"{MOD}:{name}", f"{MOD}:{target}", pool, final_state, joint)


def stair(block: str, facing: str, half: str = "bottom") -> str:
    return f"minecraft:{block}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]"


def lantern_post(piece, x, y, z, height=2):
    """An iron bar post ``height`` high standing on (x, y - 1, z) with a lantern on top."""
    for dy in range(height):
        piece.put(x, y + dy, z, "bars")
    piece.put(x, y + height, z, "lantern")


def door(piece, x, y, z, facing="north", wood="spruce", hinge="left"):
    piece.put(x, y, z, f"minecraft:{wood}_door[facing={facing},half=lower,hinge={hinge},open=false,powered=false]")
    piece.put(x, y + 1, z, f"minecraft:{wood}_door[facing={facing},half=upper,hinge={hinge},open=false,powered=false]")


def brig_door(piece, x, y, z, facing, hinge="left", locked=False):
    """A brig door (both halves carry ``locked``; the owner is set when a player places one, so a generated door has
    none and is left unlocked)."""
    for half, dy in (("lower", 0), ("upper", 1)):
        piece.put(x, y + dy, z, f"{MOD}:brig_door[facing={facing},half={half},hinge={hinge},"
                                f"locked={str(locked).lower()},open=false]")


def table(piece, x, y, z):
    """A one-block table: a fence leg under a pressure plate top."""
    piece.put(x, y, z, "fence")
    piece.put(x, y + 1, z, "minecraft:spruce_pressure_plate[powered=false]")


def stool(piece, x, y, z, facing):
    """A spruce stair seat whose back is toward ``facing``."""
    piece.put(x, y, z, stair("spruce", facing))


def bed(piece, x, y, z, facing, colour="blue"):
    """A bed whose foot is at (x, y, z) and whose head lies one block toward ``facing``."""
    dx, dz = DIRS[facing]
    piece.put(x, y, z, f"minecraft:{colour}_bed[facing={facing},occupied=false,part=foot]")
    piece.put(x + dx, y, z + dz, f"minecraft:{colour}_bed[facing={facing},occupied=false,part=head]")


def cannon(piece, x, y, z, facing="north"):
    """The two-block cannon (combat/cannon/CannonBlock): the master (part=front, the muzzle end with the block entity)
    at (x, y, z), the rear one block behind it, both with the muzzle's ``facing`` and nothing loaded."""
    dx, dz = DIRS[OPPOSITE[facing]]
    piece.put(x, y, z, f"{MOD}:cannon[facing={facing},load=empty,part=front]")
    piece.put(x + dx, y, z + dz, f"{MOD}:cannon[facing={facing},load=empty,part=rear]")


def flagpole(piece, x, y, z, height=3, flies="east"):
    """A pole of ``height`` stacked flagpoles on (x, y - 1, z); the top one flies the navy flag toward ``flies`` (its
    block entity holds the hoisted flag as FlagpoleBlockEntity saves it: ``flagpole.kind`` and ``flagpole.item``)."""
    for dy in range(height - 1):
        piece.put(x, y + dy, z, f"{MOD}:flagpole[facing={flies},flag=none]")
    top = (x, y + height - 1, z)
    piece._check(*top)
    piece.blocks.pop(top, None)
    # Piece emits every entry of ``jigsaws`` as a block_entity operation; the flag rides along there (it is no jigsaw)
    piece.jigsaws[top] = {
        "block": f"{MOD}:flagpole[facing={flies},flag=navy]",
        "data": {"flagpole": {"kind": "navy", "item": {"id": f"{MOD}:navy_flag", "count": 1}}},
    }


def banner(piece, x, y, z, facing, colour="blue"):
    """A plain wall banner hanging on the block behind it (the side opposite ``facing``)."""
    piece.put(x, y, z, f"minecraft:{colour}_wall_banner[facing={facing}]")


def curtain(piece, x0, x1, merlons=None, gaps=()):
    """The curtain wall's section over x0..x1: foundation and solid body up to the walkway (y WALK) on z 0..4, the
    seaward parapet (z 0) one row higher, and merlons on it at ``merlons`` (default every even x). ``gaps`` are the x
    where the parapet is cut down to the walkway (embrasures for guns). The face gets a chiseled plinth at y 1 and a
    polished andesite string course at the walkway's height; the walkway is paved with polished andesite."""
    z0, z1 = BODY_Z
    piece.fill(x0, 0, PARAPET_Z, x1, WALK, z1, "bricks")
    piece.fill(x0, 1, PARAPET_Z, x1, 1, PARAPET_Z, "chiseled")
    piece.fill(x0, WALK, PARAPET_Z, x1, WALK, PARAPET_Z, "andesite")
    piece.fill(x0, WALK, z0, x1, WALK, z1, "andesite")
    for x in range(x0, x1 + 1):
        if x in gaps:
            continue
        piece.put(x, WALK + 1, PARAPET_Z, "bricks")
        if (x % 2 == 0) if merlons is None else (x in merlons):
            piece.put(x, MERLON, PARAPET_Z, "bricks")


def weather(piece, spots):
    """Mossy or cracked bricks at the given (x, y, z, key) spots, only where there is a plain brick already."""
    for x, y, z, key in spots:
        if piece.get(x, y, z) == "bricks":
            piece.put(x, y, z, key)
