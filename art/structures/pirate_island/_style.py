"""The pirate island camp's shared palette (rough and weathered: spruce and dark oak, stripped logs as posts, wool
canvas, mossy cobblestone, no clean stone bricks), its jigsaw vocabulary and the import path for the generators in
this folder.

**Camp palette (ST4b):** two woods, spruce (weathered boards, stripped posts) and dark oak (dark boards, thatch,
stilts), with jungle logs as palm-trunk posts and stripped jungle as pale driftwood; one stone, cobblestone grading into
mossy cobblestone where it meets the ground; one accent, sailcloth (white wool with red stripes and brown patches). The
fittings at the end of this file (``awning``, ``palisade_post``, ``shutter_window``, ``rope_rail``, ``rafter_ends``,
``campfire_ring``, ``sand_skirt``, ``weatherboard``, ``chimney``, ``lantern_post``) give every piece of the set the
same ramshackle, salvaged look.

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
    # ST4b: palm trunks, driftwood, sailcloth accents, rope, bones
    "palm": "minecraft:jungle_log[axis=y]",
    "palm_x": "minecraft:jungle_log[axis=x]",
    "palm_z": "minecraft:jungle_log[axis=z]",
    "drift": "minecraft:stripped_jungle_log[axis=y]",
    "drift_x": "minecraft:stripped_jungle_log[axis=x]",
    "drift_z": "minecraft:stripped_jungle_log[axis=z]",
    "dark_beam_z": "minecraft:stripped_dark_oak_log[axis=z]",
    "canvas_red": "minecraft:red_wool",
    "chain_x": "minecraft:chain[axis=x]",
    "chain_y": "minecraft:chain[axis=y]",
    "chain_z": "minecraft:chain[axis=z]",
    "bones": "minecraft:bone_block[axis=y]",
    "campfire": "minecraft:campfire[facing=north,lit=true,signal_fire=false]",
    "dead_bush": "minecraft:dead_bush",
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
    piece.put(x, y, z, f"minecraft:spruce_stairs[facing={facing},half=bottom,shape=straight]")


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


def pole_part(i: int, height: int) -> str:
    """The ``part`` block state of the ``i``-th block (from the foot) of a pole ``height`` flagpoles tall (VIS1a's
    FlagpolePart: a lone block is single, then bottom, middle..., top)."""
    if height == 1:
        return "single"
    return "bottom" if i == 0 else "top" if i == height - 1 else "middle"


def flagpole(piece, x, y, z, height, facing):
    """A stack of ``height`` flagpoles standing on (x, y - 1, z); the top one flies the Jolly Roger toward ``facing``.

    The flag lives in the pole's block entity (``FlagpoleState``: kind and flag item); the ``flag`` block state only
    drives the model, so both are set. The block entity goes out as a ``block_entity`` operation through the same list
    as the jigsaws (``Piece.spec`` writes every entry of ``piece.jigsaws`` that way). Every block stores the part of
    the pole it shows (``pole_part``), so the pole looks right before its first tick."""
    for dy in range(height - 1):
        piece.put(x, y + dy, z, f"{MOD}:flagpole[facing={facing},flag=none,part={pole_part(dy, height)}]")
    top = (x, y + height - 1, z)
    piece._check(*top)
    piece.blocks.pop(top, None)
    piece.jigsaws[top] = {
        "block": f"{MOD}:flagpole[facing={facing},flag=jolly_roger,part={pole_part(height - 1, height)}]",
        "data": {"flagpole": {"kind": "jolly_roger", "item": {"id": f"{MOD}:jolly_roger_flag", "count": 1}}},
    }


# ---------------------------------------------------------------- ST4b fittings (the camp's look, art/README.md)

SIDE = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
LEFT_OF = {"north": "east", "south": "west", "east": "south", "west": "north"}  # seen from outside, facing the wall
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
SALVAGE = "minecraft:jungle_planks"   # the odd salvaged board in a wall or deck


def jit(x, y, z, salt=0) -> int:
    """A fixed pseudo-random number 0..999 for a position: irregular but deterministic (byte-identical rebuilds)."""
    v = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (salt * 2654435761)
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return (v ^ (v >> 16)) % 1000


def _inside(piece, x, y, z) -> bool:
    sx, sy, sz = piece.size
    return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz


def trapdoor(facing, half="bottom", is_open=False, wood="spruce"):
    return f"minecraft:{wood}_trapdoor[facing={facing},half={half},open={str(is_open).lower()},powered=false]"


def stair(facing, half="bottom", wood="spruce"):
    return f"minecraft:{wood}_stairs[facing={facing},half={half},shape=straight]"


def lantern_post(piece, x, y, z, height=1, post="fence"):
    """A post ``height`` high standing on (x, y - 1, z) with a lantern on top (the camp's lights)."""
    for dy in range(height):
        piece.put(x, y + dy, z, post)
    piece.put(x, y + height, z, "lantern")


def sand_skirt(piece, x0, z0, x1, z1, y=0, reach=2, salt=0, replace=("sand",), density=1.0):
    """The gradient at the foot of everything: ground cells (row ``y``) around the footprint x0..x1, z0..z1 turn from
    sand into gravel, coarse dirt and the odd mossy stone right beside it, thinning out to a few gravel spots
    ``reach`` blocks away (``density`` scales how many). Only cells that still hold one of ``replace`` change, so
    trails and connectors stay."""
    sx, _, sz = piece.size
    for z in range(max(0, z0 - reach), min(sz - 1, z1 + reach) + 1):
        for x in range(max(0, x0 - reach), min(sx - 1, x1 + reach) + 1):
            if x0 <= x <= x1 and z0 <= z <= z1:
                continue
            if (x, y, z) in piece.jigsaws or piece.get(x, y, z) not in replace:
                continue
            d = max(x0 - x, x - x1, z0 - z, z - z1)
            r = jit(x, y, z, salt) / density
            if d == 1:
                key = "gravel" if r < 420 else "coarse" if r < 740 else "mossy" if r < 820 else None
            else:
                key = "gravel" if r < 200 else "coarse" if r < 290 else None
            if key:
                piece.put(x, y, z, key)


def palisade_post(piece, x, y, z, height=2, wood=None, tip=True):
    """A crude palisade stake standing on (x, y - 1, z): ``height`` logs (palm trunk, stripped spruce or dark oak by
    position unless ``wood`` is given) with a sharpened fence tip when it fits in the box. Returns the top row."""
    if wood is None:
        wood = ("palm", "post", "palm", "dark_pile")[jit(x, 0, z, 7) % 4]
    top = y - 1
    for dy in range(height):
        if _inside(piece, x, y + dy, z):
            piece.put(x, y + dy, z, wood)
            top = y + dy
    if tip and _inside(piece, x, top + 1, z):
        piece.put(x, top + 1, z, "fence")
        top += 1
    return top


def awning(piece, x0, z0, x1, z1, y, stripes_along="x", posts=(), ground=1, salt=0):
    """A sailcloth awning: wool at row ``y`` over x0..x1, z0..z1, white with red stripes running along
    ``stripes_along`` (every other line across it) and a brown patch here and there, held up by fence ``posts``
    ((x, z) pairs) standing from row ``ground`` up to the cloth."""
    for z in range(min(z0, z1), max(z0, z1) + 1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            across = z if stripes_along == "x" else x
            key = "canvas_red" if across % 2 else "canvas"
            if key == "canvas" and jit(x, y, z, salt) < 120:
                key = "canvas_brown"
            piece.put(x, y, z, key)
    for (px, pz) in posts:
        for py in range(ground, y):
            piece.put(px, py, pz, "fence")


def shutter_window(piece, x, y, z, facing, sides=("left", "right"), sill=True, opening="fence", wood="spruce"):
    """A window in the wall cell (x, y, z) whose outside lies toward ``facing``: the opening (fence bars, or ``None``
    for a plain hole), a trapdoor sill under it on the outside, and open trapdoor shutters folded back flat against
    the wall on the ``sides`` asked for (seen from outside; a single shutter reads as a lost one). Outside cells that
    are taken or outside the box are skipped."""
    if opening is None:
        piece.clear(x, y, z)
    else:
        piece.put(x, y, z, opening)
    dx, dz = SIDE[facing]
    ox, oz = x + dx, z + dz
    if sill and _inside(piece, ox, y - 1, oz) and piece.get(ox, y - 1, oz) is None:
        piece.put(ox, y - 1, oz, trapdoor(facing, "top", False, wood))
    for side in sides:
        lx, lz = SIDE[LEFT_OF[facing] if side == "left" else OPPOSITE[LEFT_OF[facing]]]
        if _inside(piece, ox + lx, y, oz + lz) and piece.get(ox + lx, y, oz + lz) is None:
            piece.put(ox + lx, y, oz + lz, trapdoor(facing, "bottom", True, wood))


def rope_rail(piece, x0, z0, x1, z1, y, every=3, post="fence"):
    """A rope rail along a straight line at row ``y``: posts at both ends and every ``every`` blocks, chain (the
    nearest thing vanilla has to a hawser) slung between them."""
    if x0 != x1 and z0 != z1:
        raise ValueError("rope_rail runs along x or z")
    along_x = z0 == z1
    a0, a1 = sorted((x0, x1) if along_x else (z0, z1))
    for a in range(a0, a1 + 1):
        x, z = (a, z0) if along_x else (x0, a)
        is_post = a in (a0, a1) or (a - a0) % every == 0
        piece.put(x, y, z, post if is_post else ("chain_x" if along_x else "chain_z"))


def rafter_ends(piece, cells, y, toward, wood="dark_oak"):
    """Exposed rafter ends under an eave: an upside-down stair (a corbel) in each (x, z) of ``cells`` at row ``y``,
    its full side against the wall that lies toward ``toward``."""
    for (x, z) in cells:
        piece.put(x, y, z, stair(toward, "top", wood))


def campfire_ring(piece, x, y, z, seats=("west", "east", "south"), spit=True):
    """The camp's fire: a 3x3 cobblestone hearth (mossy corners) in ground row ``y``, a lit campfire on it, log seats
    (a driftwood log in the middle of each) two blocks out on the given sides and, with ``spit``, a roasting spit
    (fence posts and a chain bar) over the fire."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            piece.put(x + dx, y, z + dz, "mossy" if dx and dz else "cobble")
    piece.put(x, y + 1, z, "campfire")
    for side in seats:
        sdx, sdz = SIDE[side]
        for t in (-1, 0, 1):
            if sdx:
                piece.put(x + 2 * sdx, y + 1, z + t, "drift_z" if t == 0 else "beam_z")
            else:
                piece.put(x + t, y + 1, z + 2 * sdz, "drift_x" if t == 0 else "beam_x")
    if spit:
        for dx in (-1, 1):
            piece.put(x + dx, y + 1, z, "fence")
            piece.put(x + dx, y + 2, z, "fence")
        piece.put(x, y + 2, z, "chain_x")


def weatherboard(piece, x0, y0, z0, x1, y1, z1, keys=("dark", "spruce"), replace=("dark", "spruce"), salt=0,
                 patch=SALVAGE):
    """Re-boards the wall cells in the box x0..x1, y0..y1, z0..z1 that hold one of ``replace``: vertical boards
    alternating between ``keys`` (by column along the wall) with the odd salvaged board of a third wood, so no wall
    reads as one flat material."""
    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                if piece.get(x, y, z) not in replace:
                    continue
                col = x if x0 != x1 else z
                key = keys[col % len(keys)]
                if patch and jit(x, y, z, salt) < 110:
                    key = patch
                piece.put(x, y, z, key)


def roof_patches(piece, rate=120, salt=0, old="minecraft:dark_oak_", new="minecraft:spruce_"):
    """Patches a thatch roof: about ``rate`` in 1000 of its stairs and slabs (``old`` wood) become the same shape in
    ``new`` wood, so the roof reads as mended rather than as one flat material."""
    for (x, y, z), key in list(piece.blocks.items()):
        state = piece.palette[key]
        if state.startswith(old) and ("_stairs[" in state or "_slab[" in state) and jit(x, y, z, salt) < rate:
            piece.put(x, y, z, new + state[len(old):])


def chimney(piece, x, z, y0, y1, smoke=True):
    """A rough fieldstone chimney from row ``y0`` to ``y1``: mossy cobblestone at the foot grading into cobblestone
    and, with ``smoke``, a lit campfire on top for the smoke (out of reach)."""
    for y in range(y0, y1 + 1):
        r = jit(x, y, z, 3)
        low = y - y0 < 2
        piece.put(x, y, z, "mossy" if (low and r < 800) or r < 250 else "cobble")
    if smoke:
        piece.put(x, y1 + 1, z, "campfire")
