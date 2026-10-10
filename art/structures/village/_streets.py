"""STR3 helpers for the seafarer village's street network (art/README.md, "Natural layout of the village (STR3)"):
streets drawn along a centre line, so straights, bends, forks, crossroads and squares share one cross-section, and the
builders of the mirrored pairs (jigsaw rotates but never mirrors, so a left and a right bend are two pieces).

The cross-section (ST1's street): a cobbled crown 3 wide (cobblestone with mossy cobblestone and andesite, no gravel,
so a street laid over an inlet stays a cobbled causeway), stone brick kerbs either side (some mossy or cracked), and
dirt path verges with coarse patches, 7 wide in all. Each cell takes its kind from its distance to the nearest centre
line, so where two streets meet the crowns merge and the kerbs round the corners. Cells the street does not need stay
empty and the terrain shows through. Everything is deterministic (``_style.jit``)."""
from __future__ import annotations

import math

from _style import PALETTE, bench, flower, jit, lantern_post, tree
from buildspec import Piece

CROWN, KERB, VERGE = 1.3, 2.3, 3.3


# ------------------------------------------------------------------ geometry

def line(x0, z0, x1, z1, step=0.1):
    """Points along a straight segment (floats)."""
    n = max(1, int(math.hypot(x1 - x0, z1 - z0) / step))
    return [(x0 + (x1 - x0) * i / n, z0 + (z1 - z0) * i / n) for i in range(n + 1)]


def arc(cx, cz, r, a0, a1, step_deg=1.0):
    """Points along a circular arc around (cx, cz) from angle ``a0`` to ``a1`` (degrees; 0 = +x, 90 = +z)."""
    n = max(1, int(abs(a1 - a0) / step_deg))
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cz + r * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def distances(size, points):
    """{(x, z): distance from the cell to the nearest centre point} for every cell of the box within VERGE."""
    sx, _, sz = size
    out = {}
    for x in range(sx):
        for z in range(sz):
            d = min(math.hypot(x - px, z - pz) for (px, pz) in points)
            if d <= VERGE:
                out[(x, z)] = d
    return out


# ------------------------------------------------------------------ the street

def crown_key(x, z, salt):
    r = jit(x, 0, z, salt)
    return "cobble" if r < 720 else "mossy" if r < 860 else "andesite"


def kerb_key(x, z, salt):
    r = jit(x, 0, z, salt + 1)
    return "bricks" if r < 760 else "bricks_mossy" if r < 880 else "bricks_cracked"


def verge_key(x, z, salt):
    r = jit(x, 0, z, salt + 2)
    return "path" if r < 720 else "coarse" if r < 900 else None


def lay(piece, points, salt=0, full_faces=True):
    """Lays the street along ``points`` on row y 0 and returns {(x, z): kind} (``crown``, ``kerb``, ``verge``). A verge
    cell may stay empty (the terrain shows), except on the box faces when ``full_faces`` (the connectors' mouths)."""
    sx, _, sz = piece.size
    kinds = {}
    for (x, z), d in sorted(distances(piece.size, points).items()):
        if (x, 0, z) in piece.jigsaws:
            continue
        if d <= CROWN:
            kinds[(x, z)] = "crown"
            piece.put(x, 0, z, crown_key(x, z, salt))
        elif d <= KERB:
            kinds[(x, z)] = "kerb"
            piece.put(x, 0, z, kerb_key(x, z, salt))
        else:
            kinds[(x, z)] = "verge"
            key = verge_key(x, z, salt)
            on_face = x in (0, sx - 1) or z in (0, sz - 1)
            if key is None and full_faces and on_face:
                key = "path"
            if key is not None:
                piece.put(x, 0, z, key)
    return kinds


def footpath(piece, cells, salt=0):
    """A trodden way (dirt path, gravel, coarse dirt) over ``cells`` that are still empty: from a verge to a connector
    that does not sit on the street itself."""
    for (x, z) in cells:
        if (x, 0, z) in piece.jigsaws or piece.get(x, 0, z) is not None:
            continue
        r = jit(x, 0, z, salt + 3)
        piece.put(x, 0, z, "path" if r < 600 else "gravel" if r < 800 else "coarse")


def radial(cx, cz, x, z, r_from):
    """Cells from (x, z) toward (cx, cz) until the distance to the centre drops to ``r_from``."""
    cells = []
    d = math.hypot(x - cx, z - cz)
    steps = int(d * 4)
    for i in range(steps + 1):
        px = x + (cx - x) * i / steps
        pz = z + (cz - z) * i / steps
        if math.hypot(px - cx, pz - cz) < r_from:
            break
        c = (round(px), round(pz))
        if c not in cells:
            cells.append(c)
    return cells


def green(piece, cells, salt=0, flowers=350):
    """A village green on ``cells``: grass with a scatter of flowers."""
    for (x, z) in cells:
        if (x, 0, z) in piece.jigsaws or piece.get(x, 0, z) is not None:
            continue
        piece.put(x, 0, z, "grass")
        if jit(x, 1, z, salt) < flowers and piece.inside(x, 1, z) and piece.get(x, 1, z) is None:
            flower(piece, x, 1, z, salt)


def mirror(left, w):
    """Mirrors x for the right-hand twin of a piece built for the left (``w`` is the box's width)."""
    return (lambda x: x) if left else (lambda x: w - 1 - x)


def flip(facing, left):
    """Mirrors an east/west facing for the right-hand twin."""
    if left:
        return facing
    return {"east": "west", "west": "east"}.get(facing, facing)


# ------------------------------------------------------------------ the mirrored pairs

def bend(left: bool):
    """A gentle quarter bend (11x11): ``street_in`` on the north edge, the street sweeps round on a radius of 7 and
    leaves through ``street_out`` on the east edge (``left``: walking in, heading south, east is on the left) or the
    west edge. The inside corner holds a small green with an oak and a bench; on the outside a trodden forecourt leads
    to a ``building_out`` on the west (east) face and another on the south face."""
    W = L = 11
    size = (W, 7, L)
    side = "left" if left else "right"
    mx = mirror(left, W)
    p = Piece(f"village_street_bend_{side}", f"Seafarer Street (bend {side})", size, PALETTE)
    cx, cz, r = mx(10), 0, 7
    a0, a1 = (180, 90) if left else (0, 90)
    points = line(mx(3), -4, mx(3), 0) + arc(cx, cz, r, a0, a1) + line(mx(10), 7, mx(10) + (4 if left else -4), 7)
    kinds = lay(p, points, salt=301 if left else 311)
    # the inside corner: a green with an oak and a bench looking at the street
    inside = [(x, z) for x in range(W) for z in range(L) if (x, z) not in kinds and math.hypot(x - cx, z - cz) < r - 3]
    green(p, inside, salt=302)
    tree(p, mx(9), 1, 1, height=3, crown=2, salt=303)
    bench(p, mx(7), 1, 1, flip("west", left))
    # the outside: a lantern post on the verge, a trodden forecourt to the two buildings
    house_w, house_s = (0, 6), (5, 10)
    for (hx, hz) in (house_w, house_s):
        footpath(p, radial(cx, cz, mx(hx), hz, r + VERGE - 0.5), salt=304)
    lantern_post(p, mx(1), 1, 3)
    p.connector(mx(3), 0, 0, "street_in", "north", "minecraft:cobblestone")
    p.connector(mx(10), 0, 7, "street_out", flip("east", left), "minecraft:cobblestone")
    p.connector(mx(house_w[0]), 0, house_w[1], "building_out", flip("west", left), "minecraft:dirt_path")
    p.connector(mx(house_s[0]), 0, house_s[1], "building_out", "south", "minecraft:dirt_path")
    out = flip("east", left)
    p.emit(f"a gentle quarter bend of the cobbled street ({side}), a green with an oak in the inside corner",
           [f"street_in at [{mx(3)}, 0, 0] (north), street_out at [{mx(10)}, 0, 7] ({out}), building_out at "
            f"[{mx(0)}, 0, 6] ({flip('west', left)}) and [{mx(5)}, 0, 10] (south)."])


def fork(left: bool):
    """A fork (11x13): the street runs on south to ``street_out`` and a branch sweeps off on a radius of 7 to a second
    ``street_out`` on the east edge (``left``) or the west edge; a ``building_out`` and a ``deco_out`` on the other
    side, a flower bed with a young oak inside the branch, a lantern in the crotch."""
    W, L = 11, 13
    size = (W, 7, L)
    side = "left" if left else "right"
    mx = mirror(left, W)
    p = Piece(f"village_street_fork_{side}", f"Seafarer Street (fork {side})", size, PALETTE)
    cx, cz, r = mx(10), 2, 7
    a0, a1 = (180, 90) if left else (0, 90)
    points = (line(mx(3), -4, mx(3), L + 3) + arc(cx, cz, r, a0, a1)
              + line(mx(10), 9, mx(10) + (4 if left else -4), 9))
    kinds = lay(p, points, salt=321 if left else 331)
    inside = [(x, z) for x in range(W) for z in range(L) if (x, z) not in kinds and math.hypot(x - cx, z - cz) < r - 3]
    green(p, inside, salt=322)
    tree(p, mx(9), 1, 1, height=3, crown=2, salt=323)
    # the crotch between the trunk and the branch: a lantern post
    for z in range(L - 1, 8, -1):
        if (mx(7), z) not in kinds and p.get(mx(7), 0, z) is None:
            p.put(mx(7), 0, z, "coarse")
            lantern_post(p, mx(7), 1, z)
            break
    p.connector(mx(3), 0, 0, "street_in", "north", "minecraft:cobblestone")
    p.connector(mx(3), 0, L - 1, "street_out", "south", "minecraft:cobblestone")
    p.connector(mx(10), 0, 9, "street_out", flip("east", left), "minecraft:cobblestone")
    p.connector(mx(0), 0, 4, "building_out", flip("west", left), "minecraft:dirt_path")
    p.connector(mx(0), 0, 10, "deco_out", flip("west", left), "minecraft:dirt_path")
    p.emit(f"a fork of the cobbled street ({side}): on south, and a branch sweeping off to the {flip('east', left)}",
           [f"street_in at [{mx(3)}, 0, 0], street_out at [{mx(3)}, 0, {L - 1}] (south) and [{mx(10)}, 0, 9] "
            f"({flip('east', left)}), building_out at [{mx(0)}, 0, 4] and deco_out at [{mx(0)}, 0, 10] "
            f"({flip('west', left)})."])


# ------------------------------------------------------------------ squares and ends

def square_paving(piece, x0, z0, x1, z1, salt=0, corner=2.5, street=None):
    """A paved square from (x0, z0) to (x1, z1) with rounded corners: a stone brick border, cobblestone with andesite
    and stone brick flags inside; where the ``street`` (the kinds ``lay`` returned) runs in, its crown runs on through
    the border. Returns the paved cells."""
    cells = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            cx = min(max(x, x0 + corner), x1 - corner)
            cz = min(max(z, z0 + corner), z1 - corner)
            if math.hypot(x - cx, z - cz) <= corner + 0.3:
                cells.add((x, z))
    for (x, z) in sorted(cells):
        if (x, 0, z) in piece.jigsaws:
            continue
        edge = any((x + dx, z + dz) not in cells for dx in (-1, 0, 1) for dz in (-1, 0, 1))
        if edge and street is not None and street.get((x, z)) == "crown":
            piece.put(x, 0, z, crown_key(x, z, salt))
        elif edge:
            piece.put(x, 0, z, kerb_key(x, z, salt))
        else:
            r = jit(x, 0, z, salt + 4)
            piece.put(x, 0, z, "cobble" if r < 560 else "andesite" if r < 720 else "bricks" if r < 900 else "mossy")
    return cells


def square_verge(piece, cells, salt=0):
    """Dirt path and coarse dirt round a square's border, thinning out (the terrain shows)."""
    sx, _, sz = piece.size
    for x in range(sx):
        for z in range(sz):
            if (x, z) in cells or piece.get(x, 0, z) is not None or (x, 0, z) in piece.jigsaws:
                continue
            if any((x + dx, z + dz) in cells for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
                key = verge_key(x, z, salt)
                if key:
                    piece.put(x, 0, z, key)


def corner_lanterns(piece, x0, z0, x1, z1):
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        lantern_post(piece, x, 1, z)
