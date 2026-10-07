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
    # ST4c: the second wood (dark oak trim: shutters, sills, brackets, rafter ends, gun decks), bark posts, chains
    "dark_oak": "minecraft:dark_oak_planks",
    "dark_post": "minecraft:stripped_dark_oak_log[axis=y]",
    "log": "minecraft:spruce_log[axis=y]",
    "chain": "minecraft:chain[axis=y,waterlogged=false]",
    "brick_slab_top": "minecraft:stone_brick_slab[type=top]",
    "spruce_slab_top": "minecraft:spruce_slab[type=top]",
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


def curtain(piece, x0, x1, merlons=None, gaps=(), pilasters=None, slits=()):
    """The curtain wall's section over x0..x1: foundation and solid body up to the walkway (y WALK) on z 1..4, paved
    with polished andesite, and the seaward skin on z 0 (``fort_face``): pilasters at ``pilasters`` (default both ends,
    so walls meet flush pilaster to pilaster), recessed bays between them on a sloped plinth under a corbel table at
    the walkway's height. On the corbels the parapet (z 0, y WALK + 1) and the merlons at ``merlons`` (default every
    even x) with slab caps. ``gaps`` are the x where the parapet is cut down to the walkway (embrasures for guns);
    ``slits`` the x of arrow slits in the recessed body face."""
    zb0, zb1 = BODY_Z
    piece.fill(x0, 0, zb0, x1, WALK, zb1, "bricks")
    piece.fill(x0, WALK, zb0, x1, WALK, zb1, "andesite")
    pil = {x0, x1} if pilasters is None else set(pilasters)
    fort_face(piece, [(x, PARAPET_Z) for x in range(x0, x1 + 1)], "south",
              pilasters={(x, PARAPET_Z) for x in pil}, corbel=WALK)
    for x in pil:
        piece.put(x, WALK, PARAPET_Z, "andesite")
    for x in slits:
        arrow_slit(piece, x, 2, zb0, "x")
    cells = []
    for x in range(x0, x1 + 1):
        if x in gaps:
            continue
        piece.put(x, WALK + 1, PARAPET_Z, "bricks")
        if (x % 2 == 0) if merlons is None else (x in merlons):
            cells.append((x, PARAPET_Z))
    _merlons(piece, cells, MERLON)   # (the parameter ``merlons`` shadows the fitting of that name here)


def weather(piece, spots):
    """Mossy or cracked bricks at the given (x, y, z, key) spots, only where there is a plain brick already."""
    for x, y, z, key in spots:
        if piece.get(x, y, z) == "bricks":
            piece.put(x, y, z, key)


# ---------------------------------------------------------------------------------------------- ST4c fittings
# The fort's look (design.md §10.1 "Look of the buildings"): one stone (stone bricks with their mossy, cracked and
# chiseled variants, polished andesite for bands, kerbs and walkways), two woods (spruce for structure, roofs and
# hoardings, dark oak for trim: shutters, sills, brackets, rafter ends, gun decks) and the navy's blue as the accent.
# Walls get depth from recessed bays between pilasters (``fort_face``), buttresses, corbels and string courses;
# masonry is weathered from the ground up by ``age``. Everything is deterministic (a hash of the position, never
# ``random``), so a rebuild is byte-identical.

H_AXIS = {"north": "x", "south": "x", "east": "z", "west": "z"}   # the axis a wall that faces that way runs along


def h01(x, y, z, salt=0) -> float:
    """A stable pseudo-random number in [0, 1) for a position."""
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (salt * 2654435761)
    n &= 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return n / 2 ** 32


def inside(piece, x, y, z) -> bool:
    sx, sy, sz = piece.size
    return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz


def stair_shape(block: str, facing: str, half: str = "bottom", shape: str = "straight") -> str:
    return f"minecraft:{block}_stairs[facing={facing},half={half},shape={shape},waterlogged=false]"


def bars(axis: str) -> str:
    """Iron bars joined to their neighbours along ``axis`` (a grille in a wall that runs along that axis)."""
    if axis == "x":
        return "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]"
    return "minecraft:iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]"


def pane(axis: str) -> str:
    if axis == "x":
        return "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]"
    return "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]"


def fence(wood="spruce", north=False, south=False, east=False, west=False) -> str:
    def b(v):
        return "true" if v else "false"
    return (f"minecraft:{wood}_fence[east={b(east)},north={b(north)},south={b(south)},waterlogged=false,"
            f"west={b(west)}]")


def fence_run(piece, cells, y, wood="spruce"):
    """A rail of fences over ``cells`` [(x, z)] on row ``y``, each joined to its neighbours in the run."""
    run = set(cells)
    for x, z in cells:
        piece.put(x, y, z, fence(wood, north=(x, z - 1) in run, south=(x, z + 1) in run,
                                 east=(x + 1, z) in run, west=(x - 1, z) in run))


def age(piece, ground=0, salt=0, where=None):
    """Weathers the masonry where stone meets ground: plain stone bricks (and their stairs, slabs and walls) turn mossy
    often on the ground row (and below it), less often one and two rows up, rarely above; cracked bricks are
    scattered over the whole height, a little more often low down. ``where(x, y, z)`` limits it (paving, floors)."""
    for (x, y, z), key in sorted(piece.blocks.items()):
        if where is not None and not where(x, y, z):
            continue
        state = piece.palette[key]
        d = y - ground
        moss = 0.6 if d <= 0 else {1: 0.3, 2: 0.1}.get(d, 0.025)
        r = h01(x, y, z, salt)
        if state == "minecraft:stone_bricks":
            if r < moss:
                piece.put(x, y, z, "bricks_mossy")
            elif r < moss + (0.12 if d <= 1 else 0.06):
                piece.put(x, y, z, "bricks_cracked")
        elif state.startswith(("minecraft:stone_brick_stairs", "minecraft:stone_brick_slab",
                                "minecraft:stone_brick_wall")) and r < moss:
            piece.put(x, y, z, state.replace("minecraft:stone_brick", "minecraft:mossy_stone_brick", 1))


def merlons(piece, cells, y, cap=True, key="bricks"):
    """Merlons at ``cells`` [(x, z)] on row ``y``, each capped with a stone brick slab when the box has room."""
    for x, z in cells:
        piece.put(x, y, z, key)
        if cap and inside(piece, x, y + 1, z):
            piece.put(x, y + 1, z, "brick_slab")


_merlons = merlons


def string_course(piece, cells, y, key="andesite"):
    """A horizontal band (polished andesite by default) over ``cells`` [(x, z)] on row ``y``."""
    for x, z in cells:
        piece.put(x, y, z, key)


def fort_face(piece, cells, inward, pilasters=(), ground=0, corbel=WALK):
    """The outer skin of a fort wall: pilasters stand full from ``ground`` to ``corbel`` (chiseled one row up); the bays
    between them have a base course on the ground row, a sloped plinth (a stair rising toward the wall), a recess open
    up to the corbel row, and an upside-down stair corbel on ``corbel`` that carries whatever stands above. The wall
    body behind the recess (one block toward ``inward``) is the caller's. ``cells`` are (x, z) on the face line."""
    for x, z in cells:
        if (x, z) in pilasters:
            piece.fill(x, ground, z, x, corbel, z, "bricks")
            piece.put(x, ground + 1, z, "chiseled")
            continue
        piece.put(x, ground, z, "bricks")
        piece.put(x, ground + 1, z, stair_shape("stone_brick", inward))
        for y in range(ground + 2, corbel):
            piece.clear(x, y, z)
        piece.put(x, corbel, z, stair_shape("stone_brick", inward, "top"))


def arrow_slit(piece, x, y, z, axis, height=2, grille=False):
    """A slit ``height`` tall in a wall that runs along ``axis``: left open, or barred with iron bars (``grille``)."""
    for dy in range(height):
        if grille:
            piece.put(x, y + dy, z, bars(axis))
        else:
            piece.clear(x, y + dy, z)


def buttress(piece, x, z, top, outward, ground=0):
    """A buttress standing in front of a wall (the wall lies opposite ``outward``): stone bricks from ``ground`` to
    ``top`` - 1, chiseled one row up, under a stair that slopes down away from the wall."""
    piece.fill(x, ground, z, x, top - 1, z, "bricks")
    piece.put(x, ground + 1, z, "chiseled")
    piece.put(x, top, z, stair_shape("stone_brick", OPPOSITE[outward]))


def window(piece, x, y, z, outward, glass=True, shutters=True, sill=True, wood="dark_oak"):
    """A window in the wall cell (x, y, z) that looks ``outward``: a glass pane (or iron bars), and on the outside a
    trapdoor sill under it and open trapdoor shutters folded back against the wall on both sides, where the box has
    room and the cell is free."""
    axis = H_AXIS[outward]
    piece.put(x, y, z, pane(axis) if glass else bars(axis))
    dx, dz = DIRS[outward]
    ox, oz = x + dx, z + dz
    trap = f"minecraft:{wood}_trapdoor[facing={outward},half={{half}},open={{open}},powered=false,waterlogged=false]"
    if sill and inside(piece, ox, y - 1, oz) and piece.get(ox, y - 1, oz) is None:
        piece.put(ox, y - 1, oz, trap.format(half="top", open="false"))
    if shutters:
        ax, az = (1, 0) if axis == "x" else (0, 1)
        for s in (-1, 1):
            sx, sz = ox + s * ax, oz + s * az
            if inside(piece, sx, y, sz) and piece.get(sx, y, sz) is None:
                piece.put(sx, y, sz, trap.format(half="bottom", open="true"))


def rafter_ends(piece, cells, y, outward, wood="dark_oak"):
    """Rafter ends under the eaves: upside-down stairs at ``cells`` [(x, z)] on row ``y``, their full side against
    the wall (opposite ``outward``)."""
    for x, z in cells:
        piece.put(x, y, z, stair_shape(wood, OPPOSITE[outward], "top"))


def chimney(piece, x, z, y0, y1, smoke=True):
    """A stone brick chimney stack from ``y0`` to ``y1`` - 1 with a polished andesite band under the top, crowned on
    ``y1`` by a lit campfire (smoke) or a stone brick wall cap."""
    piece.fill(x, y0, z, x, y1 - 1, z, "bricks")
    piece.put(x, y1 - 1, z, "andesite")
    if smoke:
        piece.put(x, y1, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    else:
        piece.put(x, y1, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,"
                            "west=none]")


def chain(piece, x, y0, y1, z):
    """A chain from ``y0`` up to ``y1`` (hanging from the block above it)."""
    piece.fill(x, y0, z, x, y1, z, "chain")
