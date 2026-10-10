"""STR4: the navy outpost's court (art/README.md, "Natural layout of the outpost (STR4)"): the lanes of gravel and
cobbles that grow inland from the land gate, the parade ground and the lanes' dead ends against the wall. Lanes are
terrain matching (each column on the surface row, so they follow the slope), drawn along a centre line like the
pirate island's trails (``line``, ``arc``, ``band``), and leave every cell they do not need empty: no paved rectangle,
the terrain shows between the kerbs. Mirrored bends and forks are two pieces each (a jigsaw never mirrors).

Connectors: ``lane_in`` on the north edge (y 0), ``lane_out`` where a lane leads on, ``building_out`` and ``deco_out``
on the sides (selection priority 2 and 1, so a lane places its buildings, then its decorations, then the next lane).
Everything is deterministic (``_style.h01``), so rebuilds stay byte-identical."""
from __future__ import annotations

import math

from _style import PALETTE, banner, connector, dry, fence_run, flagpole, h01, lantern_post, stair_shape
from buildspec import Piece

H = 4

# ------------------------------------------------------------------ geometry


def line(x0, z0, x1, z1, step=0.1):
    n = max(1, int(math.hypot(x1 - x0, z1 - z0) / step))
    return [(x0 + (x1 - x0) * i / n, z0 + (z1 - z0) * i / n) for i in range(n + 1)]


def arc(cx, cz, r, a0, a1, step_deg=2.0):
    n = max(1, int(abs(a1 - a0) / step_deg))
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cz + r * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def band(points, size, half_width=1.0):
    """The cells (x, z) inside the box within ``half_width`` (+0.25) of the centre points: a 3 wide bed."""
    sx, _, sz = size
    r = half_width + 0.25
    cells = set()
    for px, pz in points:
        for x in range(int(math.floor(px - r)), int(math.ceil(px + r)) + 1):
            for z in range(int(math.floor(pz - r)), int(math.ceil(pz + r)) + 1):
                if 0 <= x < sx and 0 <= z < sz and (x - px) ** 2 + (z - pz) ** 2 <= r * r:
                    cells.add((x, z))
    return cells


def verge(cells, size):
    sx, _, sz = size
    out = set()
    for x, z in cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (x + dx, z + dz)
            if c not in cells and 0 <= c[0] < sx and 0 <= c[1] < sz:
                out.add(c)
    return out


# ------------------------------------------------------------------ ground


def bed(p, cells, salt=0, y=0):
    """The lane's bed: gravel with cobbles, a few mossy cobbles and polished andesite setts (the fort's kerb stone)."""
    for x, z in sorted(cells):
        if (x, y, z) in p.jigsaws:
            continue
        r = h01(x, y, z, salt)
        key = "gravel" if r < 0.5 else "cobble" if r < 0.78 else "minecraft:mossy_cobblestone" if r < 0.86 else "andesite"
        p.put(x, y, z, key)


def kerb(p, cells, size, salt=0, y=0, skip=()):
    """Kerb stones on the verge beside the bed: polished andesite with gaps, the odd cobble or stone brick, so the
    lane's edge reads without walling it in."""
    for x, z in sorted(verge(cells, size)):
        if (x, z) in skip or (x, y, z) in p.jigsaws or p.get(x, y, z) is not None:
            continue
        r = h01(x, y, z, salt + 7)
        if r < 0.55:
            p.put(x, y, z, "andesite")
        elif r < 0.68:
            p.put(x, y, z, "cobble")
        elif r < 0.76:
            p.put(x, y, z, "bricks_mossy")


def lamp(p, x, z, height=2):
    lantern_post(p, x, 1, z, height=height)
    p.put(x, 0, z, "andesite")


def mouth(cx, cz, face, depth=2):
    """The cells of a side opening from the bed out to the box face (a building's doorstep or a decoration's foot)."""
    dx, dz = {"west": (1, 0), "east": (-1, 0), "south": (0, -1), "north": (0, 1)}[face]
    return {(cx + dx * i, cz + dz * i) for i in range(depth)}


def _piece(name, title, size):
    return Piece(f"navy_outpost_{name}", title, size, PALETTE)


def finish(p, description, notes):
    dry(p)
    p.emit(description, notes)


# ------------------------------------------------------------------ straights


def lane():
    size = (7, H, 7)
    p = _piece("lane", "Navy Outpost Lane", size)
    cells = band(line(3, 0, 3, 6), size)
    m = mouth(0, 3, "west") | mouth(6, 3, "east")
    bed(p, cells | m, salt=11)
    kerb(p, cells | m, size, salt=12)
    lamp(p, 5, 1)
    p.put(1, 1, 5, "minecraft:barrel[facing=up,open=false]")
    p.put(1, 0, 5, "cobble")
    connector(p, 3, 0, 0, "lane_in", "north", "minecraft:gravel")
    connector(p, 3, 0, 6, "lane_out", "south", "minecraft:gravel")
    connector(p, 0, 0, 3, "building_out", "west", "minecraft:cobblestone")
    connector(p, 6, 0, 3, "building_out", "east", "minecraft:cobblestone")
    finish(p, "a short lane of gravel and cobbles between andesite kerbs, a lantern post and a barrel",
           ["lane_in [3, 0, 0], lane_out [3, 0, 6], building_out [0, 0, 3] and [6, 0, 3]."])


def lane_long():
    size = (7, H, 11)
    p = _piece("lane_long", "Navy Outpost Long Lane", size)
    cells = band(line(3, 0, 3, 3) + line(3, 3, 3.6, 6) + line(3.6, 6, 3, 10), size)
    m = mouth(0, 3, "west") | mouth(6, 8, "east") | mouth(6, 3, "east") | mouth(0, 8, "west")
    bed(p, cells | m, salt=21)
    kerb(p, cells | m, size, salt=22)
    lamp(p, 1, 5)
    p.put(5, 1, 5, "pirates_n_ships:cargo_crate")
    p.put(5, 0, 5, "cobble")
    connector(p, 3, 0, 0, "lane_in", "north", "minecraft:gravel")
    connector(p, 3, 0, 10, "lane_out", "south", "minecraft:gravel")
    connector(p, 0, 0, 3, "building_out", "west", "minecraft:cobblestone")
    connector(p, 6, 0, 8, "building_out", "east", "minecraft:cobblestone")
    connector(p, 6, 0, 3, "deco_out", "east", "minecraft:gravel")
    connector(p, 0, 0, 8, "deco_out", "west", "minecraft:gravel")
    finish(p, "a long lane wandering half a block aside and back, a lantern post and a crate",
           ["lane_in [3, 0, 0], lane_out [3, 0, 10]; building_out [0, 0, 3] and [6, 0, 8]; "
            "deco_out [6, 0, 3] and [0, 0, 8]."])


# ------------------------------------------------------------------ bends, forks, the junction


def bend(left: bool):
    """A quarter bend (9x9) on a radius of 6: in on the north edge, out on the east (``left``) or west edge; buildings
    on the outside of the bend (the side edge and the south edge), a lantern and a bench in the inside corner."""
    size = (9, H, 9)
    side = "left" if left else "right"
    p = _piece(f"lane_bend_{side}", f"Navy Outpost Lane (bend {side})", size)
    if left:
        centre = line(2, 0, 2, 0.5) + arc(8, 0.5, 6, 180, 90) + line(8, 6.5, 8, 6.5)
        x_in, out, out_face, b1, b2, corner = 2, (8, 6), "east", (0, 4, "west"), (3, 8, "south"), (7, 1)
    else:
        centre = line(6, 0, 6, 0.5) + arc(0, 0.5, 6, 0, 90) + line(0, 6.5, 0, 6.5)
        x_in, out, out_face, b1, b2, corner = 6, (0, 6), "west", (8, 4, "east"), (5, 8, "south"), (1, 1)
    cells = band(centre, size)
    m = mouth(b1[0], b1[1], b1[2]) | mouth(b2[0], b2[1], b2[2])
    bed(p, cells | m, salt=31 if left else 41)
    kerb(p, cells | m, size, salt=32 if left else 42)
    cx, cz = corner
    lamp(p, cx, cz)
    p.put(cx + (-1 if left else 1), 1, cz, stair_shape("spruce", "east" if left else "west"))
    p.put(cx + (-1 if left else 1), 0, cz, "cobble")
    connector(p, x_in, 0, 0, "lane_in", "north", "minecraft:gravel")
    connector(p, out[0], 0, out[1], "lane_out", out_face, "minecraft:gravel")
    connector(p, b1[0], 0, b1[1], "building_out", b1[2], "minecraft:cobblestone")
    connector(p, b2[0], 0, b2[1], "building_out", b2[2], "minecraft:cobblestone")
    finish(p, f"a quarter bend of the lane ({side}), a lantern and a bench in the inside corner",
           [f"lane_in [{x_in}, 0, 0], lane_out [{out[0]}, 0, {out[1]}] ({out_face}); building_out on the outside."])


def fork(left: bool):
    """A fork (9x11): the lane runs on south and a branch sweeps off east (``left``) or west on a radius of 5; a
    building on the other side, a decoration there too, a lantern and crates in the fork's crotch."""
    size = (9, H, 11)
    side = "left" if left else "right"
    p = _piece(f"lane_fork_{side}", f"Navy Outpost Lane (fork {side})", size)
    x = 3 if left else 5
    trunk = line(x, 0, x, 10)
    if left:
        branch = arc(8, 3, 5, 180, 90) + line(8, 8, 8.5, 8)
        out, out_face, deco, bld, crotch = (8, 8), "east", (0, 8, "west"), (0, 3, "west"), (6, 10)
    else:
        branch = arc(0, 3, 5, 0, 90) + line(0, 8, -0.5, 8)
        out, out_face, deco, bld, crotch = (0, 8), "west", (8, 8, "east"), (8, 3, "east"), (2, 10)
    cells = band(trunk, size) | band(branch, size)
    m = mouth(deco[0], deco[1], deco[2]) | mouth(bld[0], bld[1], bld[2])
    bed(p, cells | m, salt=51 if left else 61)
    kerb(p, cells | m, size, salt=52 if left else 62)
    cx, cz = crotch
    if (cx, cz) not in cells:
        lamp(p, cx, cz)
    for c in ((5, 9) if left else (3, 9),):
        if c not in cells:
            p.put(c[0], 0, c[1], "cobble")
            p.put(c[0], 1, c[1], "pirates_n_ships:cargo_crate")
    connector(p, x, 0, 0, "lane_in", "north", "minecraft:gravel")
    connector(p, x, 0, 10, "lane_out", "south", "minecraft:gravel")
    connector(p, out[0], 0, out[1], "lane_out", out_face, "minecraft:gravel")
    connector(p, deco[0], 0, deco[1], "deco_out", deco[2], "minecraft:gravel")
    connector(p, bld[0], 0, bld[1], "building_out", bld[2], "minecraft:cobblestone")
    finish(p, f"a fork of the lane ({side}): on south, and a branch to the {out_face}",
           [f"lane_in [{x}, 0, 0], lane_out [{x}, 0, 10] and [{out[0]}, 0, {out[1]}] ({out_face})."])


def junction():
    """A T (11x7): the lane splits east and west with rounded corners; a building on the far side, a signpost of
    spruce with a lantern at the T's head."""
    size = (11, H, 7)
    p = _piece("lane_junction", "Navy Outpost Lane Junction", size)
    cells = band(line(5, 0, 5, 3) + arc(1, 3, 4, 0, 90)[:0] + line(0, 4, 10, 4), size)
    for cx, cz in ((3, 2), (7, 2)):            # round the corners
        cells.add((cx, cz))
    m = mouth(5, 6, "south")
    bed(p, cells | m, salt=71)
    kerb(p, cells | m, size, salt=72)
    for x in (2, 8):
        p.put(x, 0, 6, "andesite")
        p.put(x, 1, 6, "minecraft:barrel[facing=up,open=false]")
    p.put(7, 0, 1, "andesite")
    p.fill(7, 1, 1, 7, 2, 1, "post")
    p.put(7, 3, 1, "lantern")
    connector(p, 5, 0, 0, "lane_in", "north", "minecraft:gravel")
    connector(p, 0, 0, 4, "lane_out", "west", "minecraft:gravel")
    connector(p, 10, 0, 4, "lane_out", "east", "minecraft:gravel")
    connector(p, 5, 0, 6, "building_out", "south", "minecraft:cobblestone")
    finish(p, "a T-junction of the lane with a lantern post and barrels",
           ["lane_in [5, 0, 0], lane_out [0, 0, 4] (west) and [10, 0, 4] (east), building_out [5, 0, 6]."])


# ------------------------------------------------------------------ the parade ground


def parade_ground():
    """The parade ground (13x13): a square of cobbles and andesite setts inside a polished andesite border, the navy
    ensign on a five-block pole on a stepped pedestal in the middle, a well under a little spruce roof in one corner,
    lantern posts and benches; the lane runs on south, buildings on the east and west."""
    S = 13
    size = (S, 9, S)
    p = _piece("parade_ground", "Navy Outpost Parade Ground", size)
    for x in range(1, S - 1):
        for z in range(1, S - 1):
            edge = x in (1, S - 2) or z in (1, S - 2)
            r = h01(x, 0, z, 81)
            p.put(x, 0, z, "andesite" if edge else "cobble" if r < 0.55 else "andesite" if r < 0.8 else "gravel")
    approach = band(line(6, 0, 6, 1), size) | band(line(6, 11, 6, 12), size) | band(line(0, 6, 1, 6), size, 0.5) \
        | band(line(11, 6, 12, 6), size, 0.5)
    bed(p, approach - {(x, z) for x in range(1, S - 1) for z in range(1, S - 1)}, salt=82)
    # the pedestal and the pole, the ensign flying east
    p.fill(5, 1, 5, 7, 1, 7, "bricks")
    for (x, z), facing in {(6, 4): "south", (6, 8): "north", (4, 6): "east", (8, 6): "west"}.items():
        p.put(x, 1, z, stair_shape("stone_brick", facing))
    p.put(6, 2, 6, "chiseled")
    flagpole(p, 6, 3, 6, height=5, flies="east")
    # the well (south-east corner): a curb round the water, posts and a slab roof, a chain over it
    wx, wz = 9, 9
    p.put(wx, 0, wz, "minecraft:water[level=0]")
    for x in range(wx - 1, wx + 2):
        for z in range(wz - 1, wz + 2):
            if (x, z) != (wx, wz):
                p.put(x, 1, z, "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    for x, z in ((wx - 1, wz - 1), (wx + 1, wz - 1), (wx - 1, wz + 1), (wx + 1, wz + 1)):
        p.fill(x, 2, z, x, 3, z, "fence")
    p.fill(wx - 1, 4, wz - 1, wx + 1, 4, wz + 1, "spruce_slab")
    p.put(wx, 3, wz, "minecraft:chain[axis=y,waterlogged=false]")
    p.put(wx, 2, wz, "lantern_hanging")
    # lantern posts in the other corners, benches facing the flag, a rack of muskets (fences with plates)
    for x, z in ((2, 2), (10, 2), (2, 10)):
        lantern_post(p, x, 1, z, height=2)
    for x in (4, 5):
        p.put(x, 1, 10, stair_shape("spruce", "south"))
    for x in (7, 8):
        p.put(x, 1, 2, stair_shape("spruce", "north"))
    fence_run(p, [(2, z) for z in (4, 5)], 1)
    for z in (4, 5):
        p.put(2, 2, z, "minecraft:stone_pressure_plate[powered=false]")
    banner(p, 3, 2, 8, "east")
    p.put(2, 1, 8, "bricks")
    p.put(2, 2, 8, "bricks")
    connector(p, 6, 0, 0, "lane_in", "north", "minecraft:gravel")
    connector(p, 6, 0, S - 1, "lane_out", "south", "minecraft:gravel")
    connector(p, 0, 0, 6, "building_out", "west", "minecraft:cobblestone")
    connector(p, S - 1, 0, 6, "building_out", "east", "minecraft:cobblestone")
    finish(p, "the parade ground: a paved square with the navy ensign on a pole, a well, lantern posts and benches",
           ["lane_in [6, 0, 0], lane_out [6, 0, 12], building_out [0, 0, 6] and [12, 0, 6]; the flag at [6, 7, 6]."])


# ------------------------------------------------------------------ dead ends against the wall


def end_store():
    """The lane ends at a lean-to store shed (spruce boards on a stone footing, a slab roof, a door) with crates."""
    size = (7, 6, 6)
    p = _piece("lane_end_store", "Navy Outpost Lane End (store)", size)
    cells = band(line(3, 0, 3, 1), size)
    bed(p, cells, salt=91)
    kerb(p, cells, size, salt=92, skip={(2, 2), (3, 2), (4, 2)})
    p.fill(1, 0, 2, 5, 0, 5, "cobble")
    p.fill(1, 1, 2, 5, 1, 5, "bricks")
    p.fill(1, 2, 2, 5, 3, 5, "spruce")
    for x, z in ((1, 2), (5, 2), (1, 5), (5, 5)):
        p.fill(x, 1, z, x, 3, z, "post")
    p.fill(2, 1, 3, 4, 3, 4, "spruce")
    for x in (2, 3, 4):
        for y in (1, 2, 3):
            for z in (3, 4):
                p.clear(x, y, z)
    p.put(3, 1, 2, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    p.put(3, 2, 2, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    for x in range(0, 7):
        p.put(x, 4, 1, stair_shape("spruce", "south"))
        p.put(x, 4, 2, "spruce_slab")
        for z in (3, 4, 5):
            p.put(x, 4, z, "spruce_slab") if x in (0, 6) else p.put(x, 4, z, "spruce_slab")
    p.put(2, 1, 4, "pirates_n_ships:cargo_crate")
    p.put(4, 1, 4, "minecraft:barrel[facing=up,open=false]")
    p.put(4, 2, 4, "lantern")
    p.put(2, 1, 3, "pirates_n_ships:cargo_barrel")
    p.put(5, 1, 1, "pirates_n_ships:cargo_crate")
    p.put(5, 0, 1, "cobble")
    connector(p, 3, 0, 0, "lane_in", "north", "minecraft:gravel")
    finish(p, "the lane's end at a lean-to store shed with crates and barrels", ["lane_in [3, 0, 0]."])


def end_privy():
    """The lane ends at the garrison's privy: a narrow plank booth with a door and a slab roof, a hay bale and a
    bucket rack beside it, a lantern on a post."""
    size = (7, 5, 5)
    p = _piece("lane_end_privy", "Navy Outpost Lane End (privy)", size)
    cells = band(line(3, 0, 3, 2), size)
    bed(p, cells, salt=101)
    kerb(p, cells, size, salt=102)
    p.fill(2, 0, 3, 4, 0, 4, "cobble")
    for x, z in ((2, 3), (4, 3), (2, 4), (4, 4)):
        p.fill(x, 1, z, x, 2, z, "post")
    p.fill(3, 1, 4, 3, 2, 4, "spruce")
    p.put(3, 1, 3, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    p.put(3, 2, 3, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    p.put(3, 0, 4, "minecraft:composter[level=0]")
    p.fill(2, 3, 3, 4, 3, 4, "spruce_slab")
    p.put(5, 1, 4, "minecraft:hay_block[axis=x]")
    p.put(1, 1, 4, "minecraft:barrel[facing=up,open=false]")
    lamp(p, 5, 2, height=1)
    connector(p, 3, 0, 0, "lane_in", "north", "minecraft:gravel")
    finish(p, "the lane's end at the garrison's privy, a hay bale and a lantern", ["lane_in [3, 0, 0]."])


def end_woodpile():
    """The lane ends at a woodpile: split logs stacked under a slab lean-to on posts, a chopping block, a barrel."""
    size = (7, 5, 5)
    p = _piece("lane_end_woodpile", "Navy Outpost Lane End (woodpile)", size)
    cells = band(line(3, 0, 3, 2), size)
    bed(p, cells, salt=111)
    kerb(p, cells, size, salt=112)
    p.fill(1, 0, 3, 5, 0, 4, "gravel")
    for x in range(1, 6):
        for y in (1, 2):
            p.put(x, y, 4, "minecraft:spruce_log[axis=x]" if (x + y) % 3 else "minecraft:oak_log[axis=x]")
        p.put(x, 3, 4, "spruce_slab")
    for x in (1, 5):
        p.fill(x, 1, 3, x, 2, 3, "fence")
        p.put(x, 3, 3, "spruce_slab")
    p.fill(2, 3, 3, 4, 3, 3, "spruce_slab")
    p.put(2, 1, 3, "minecraft:stripped_oak_log[axis=y]")          # the chopping block
    p.put(4, 1, 3, "minecraft:barrel[facing=up,open=false]")
    connector(p, 3, 0, 0, "lane_in", "north", "minecraft:gravel")
    finish(p, "the lane's end at a woodpile under a slab lean-to with a chopping block", ["lane_in [3, 0, 0]."])
