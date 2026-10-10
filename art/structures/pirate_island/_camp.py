"""STR2 helpers for the pirate island's natural layout (art/README.md, "Natural layout (STR2)"): trails drawn along a
centre line (straights, arcs, forks), driftwood edging, ground patches that fade into the terrain instead of a sand
rectangle, and a few small props shared by the trail, building and decoration pieces.

Pieces built with these helpers leave every cell they do not need empty, so the terrain shows through: a trail is its
3-wide bed and a ragged verge, a hut is its footprint and a patch of trodden ground that thins out. Everything is
deterministic (``_style.jit``), so rebuilds stay byte-identical."""
from __future__ import annotations

import math

from _style import jit, palisade_post

# ------------------------------------------------------------------ geometry


def line(x0, z0, x1, z1, step=0.1):
    """Points along a straight segment (floats)."""
    n = max(1, int(math.hypot(x1 - x0, z1 - z0) / step))
    return [(x0 + (x1 - x0) * i / n, z0 + (z1 - z0) * i / n) for i in range(n + 1)]


def arc(cx, cz, r, a0, a1, step_deg=2.0):
    """Points along a circular arc around (cx, cz) from angle ``a0`` to ``a1`` (degrees; 0 = +x, 90 = +z)."""
    n = max(1, int(abs(a1 - a0) / step_deg))
    out = []
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        out.append((cx + r * math.cos(a), cz + r * math.sin(a)))
    return out


def band(points, size, half_width=1.0):
    """The grid cells (x, z) inside the box ``size`` within ``half_width`` (+0.25 to round a 3-wide trail) of the
    centre points: a trail bed along a curve."""
    sx, _, sz = size
    r = half_width + 0.25
    cells = set()
    for (px, pz) in points:
        for x in range(int(math.floor(px - r)), int(math.ceil(px + r)) + 1):
            for z in range(int(math.floor(pz - r)), int(math.ceil(pz + r)) + 1):
                if 0 <= x < sx and 0 <= z < sz and (x - px) ** 2 + (z - pz) ** 2 <= r * r:
                    cells.add((x, z))
    return cells


def neighbours(cells, size, ring=1):
    """Cells within ``ring`` (Chebyshev) of ``cells`` that are inside the box and not in ``cells``."""
    sx, _, sz = size
    out = set()
    for (x, z) in cells:
        for dx in range(-ring, ring + 1):
            for dz in range(-ring, ring + 1):
                c = (x + dx, z + dz)
                if c not in cells and 0 <= c[0] < sx and 0 <= c[1] < sz:
                    out.add(c)
    return out


# ------------------------------------------------------------------ ground

TRAIL_KEYS = ("gravel", "path", "gravel", "coarse", "path", "sand")


def trail(piece, cells, salt=0, y=0):
    """The trail bed: packed sand and gravel, trodden dirt down the middle, row ``y``. Cells holding a jigsaw stay."""
    for (x, z) in sorted(cells):
        if (x, y, z) in piece.jigsaws:
            continue
        r = jit(x, y, z, salt)
        key = "gravel" if r < 330 else "path" if r < 560 else "coarse" if r < 720 else "sand" if r < 900 else "gravel"
        piece.put(x, y, z, key)


def edging(piece, trail_cells, size, salt=0, y=0, rate=520, stakes=45, skip=()):
    """Driftwood edges along a trail: on the verge cells beside the bed, pale stripped jungle logs lie flush in the
    ground (running along the trail) with gaps between them, sand and coarse dirt fill some gaps, and now and then a
    crude stake stands on the verge. ``skip`` cells (connector mouths, doorways) stay empty."""
    verge = neighbours(trail_cells, size)
    for (x, z) in sorted(verge):
        if (x, z) in skip or (x, y, z) in piece.jigsaws or piece.get(x, y, z) is not None:
            continue
        along_z = (x - 1, z) in trail_cells or (x + 1, z) in trail_cells
        along_x = (x, z - 1) in trail_cells or (x, z + 1) in trail_cells
        if along_z and along_x:          # an inside corner: a stone instead of a log
            r = jit(x, y, z, salt + 1)
            piece.put(x, y, z, "mossy" if r < 400 else "coarse" if r < 800 else "gravel")
            continue
        r = jit(x, y, z, salt)
        if r < rate:
            piece.put(x, y, z, "drift_z" if along_z else "drift_x")
        elif r < rate + 200:
            piece.put(x, y, z, "sand")
        elif r < rate + 300:
            piece.put(x, y, z, "coarse")
        if jit(x, y + 1, z, salt + 2) < stakes and piece.inside(x, y + 1, z) and piece.get(x, y + 1, z) is None:
            palisade_post(piece, x, y + 1, z, height=1 + jit(x, 0, z, salt + 3) % 2)


def patch(piece, x0, z0, x1, z1, reach=2, salt=0, y=0, inner=("coarse", "gravel", "sand"), density=1.0):
    """Trodden ground around a footprint x0..x1, z0..z1 that fades into the terrain: the cells right beside it are
    mostly coarse dirt, gravel and sand, further out only a few, then nothing (the terrain shows). Cells already set
    or holding a jigsaw stay."""
    sx, _, sz = piece.size
    for z in range(max(0, z0 - reach), min(sz - 1, z1 + reach) + 1):
        for x in range(max(0, x0 - reach), min(sx - 1, x1 + reach) + 1):
            if x0 <= x <= x1 and z0 <= z <= z1:
                continue
            if (x, y, z) in piece.jigsaws or piece.get(x, y, z) is not None:
                continue
            d = max(x0 - x, x - x1, z0 - z, z - z1)
            r = jit(x, y, z, salt) / density
            chance = 900 if d == 1 else 380 if d == 2 else 140
            if r < chance:
                piece.put(x, y, z, inner[jit(x, y, z, salt + 5) % len(inner)])
            elif r < chance + 60:
                piece.put(x, y, z, "mossy")


def floor(piece, x0, z0, x1, z1, key="sand", y=0):
    piece.fill(x0, y, z0, x1, y, z1, key)


# ------------------------------------------------------------------ props


def rocks(piece, cells, y=0, salt=0):
    """A few beach rocks: cobblestone, mossy cobblestone and the odd andesite, with a stone slab cap here and there."""
    for i, (x, z) in enumerate(cells):
        r = jit(x, y, z, salt)
        key = "mossy" if r < 380 else "cobble" if r < 700 else "minecraft:andesite"
        piece.put(x, y, z, key)
        if r % 3 == 0 and piece.inside(x, y + 1, z) and piece.get(x, y + 1, z) is None:
            piece.put(x, y + 1, z, "minecraft:cobblestone_slab[type=bottom]" if r % 2 else "mossy")


def palm(piece, x, y, z, height=5, lean="east", fronds=True):
    """A palm on (x, y - 1, z): a jungle log trunk ``height`` tall that leans one block toward ``lean`` halfway up,
    a crown of jungle leaves (persistent) and a cocoa pod or two under it. Returns the crown's centre."""
    from _style import SIDE
    dx, dz = SIDE[lean]
    cx, cz = x, z
    for i in range(height):
        if i == height // 2 + 1:
            cx, cz = x + dx, z + dz
        piece.put_inside(cx, y + i, cz, "palm")
    top = y + height
    leaf = "minecraft:jungle_leaves[distance=1,persistent=true,waterlogged=false]"
    piece.put_inside(cx, top, cz, leaf)
    if fronds:
        for (fx, fz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            piece.put_inside(cx + fx, top, cz + fz, leaf)
            piece.put_inside(cx + 2 * fx, top - 1, cz + 2 * fz, leaf)
        for (fx, fz) in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
            if jit(cx + fx, top, cz + fz, 9) < 600:
                piece.put_inside(cx + fx, top, cz + fz, leaf)
    return (cx, top, cz)


def crate_stack(piece, x, y, z, salt=0):
    """A crate with a barrel or a second crate on it, or a lone barrel."""
    r = jit(x, y, z, salt)
    if r < 400:
        piece.put(x, y, z, "pirates_n_ships:cargo_crate")
        if piece.inside(x, y + 1, z):
            piece.put(x, y + 1, z, "barrel" if r < 200 else "pirates_n_ships:cargo_crate")
    elif r < 750:
        piece.put(x, y, z, "pirates_n_ships:cargo_barrel")
    else:
        piece.put(x, y, z, "minecraft:barrel[facing=north,open=false]")


def rope_coil(piece, x, y, z, layers=2, facing="north"):
    piece.put(x, y, z, f"pirates_n_ships:rope_coil[facing={facing},layers={layers}]")


def hanging_sign(piece, x, y, z, rotation, lines, wood="spruce"):
    """A hanging sign (hung from the block above) at (x, y, z) whose text faces ``rotation`` (0 south, 8 north),
    with up to four lines of plain text on both sides. The block entity goes out through ``piece.jigsaws`` like the
    flagpole's (``Piece.spec`` writes every entry there as a ``block_entity`` operation); ``Id`` overrides the lab's
    default (the block's name) with the block entity type, ``minecraft:hanging_sign``."""
    import json as _json
    messages = [_json.dumps(text) for text in (list(lines) + ["", "", "", ""])[:4]]
    side = {"messages": messages, "color": "black", "has_glowing_text": False}
    piece._check(x, y, z)
    piece.blocks.pop((x, y, z), None)
    piece.jigsaws[(x, y, z)] = {
        "block": f"minecraft:{wood}_hanging_sign[attached=false,rotation={rotation},waterlogged=false]",
        "data": {"Id": "minecraft:hanging_sign", "front_text": side, "back_text": side, "is_waxed": False},
    }


def skull_post(piece, x, y, z, rotation=8, height=2):
    """A palm stake ``height`` high with a skeleton skull on top (a warning to visitors)."""
    for dy in range(height):
        piece.put(x, y + dy, z, "palm")
    piece.put(x, y + height, z, f"minecraft:skeleton_skull[powered=false,rotation={rotation}]")
