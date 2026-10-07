"""Generates the sunken sloop (the starter sloop's hull heeled over on the seabed and broken amidships) and prints
the BuildSpec as JSON.

The hull is the starter sloop's (art/schematics/starter_sloop.py: the same half-widths, rocker, hold, forecastle,
stern cabin and quarterdeck) modelled in ship coordinates (x across, y up, z from the stem aft), then heeled to
starboard (+x down) by turning every block's cross-section about the keel: the fore half by 22 degrees, the after half
by 27, and the after half moved one block to starboard where it broke off. Each source block is sampled at nine
points, so the turned planking stays closed (stepped, never chequered). Missing planking comes in patches cut
before the turn (shell and deck, not the wales or the keel); the break shows its frames (stripped spruce ribs) with
jagged planking either side. The mast snapped two blocks above the deck and lies across the deck and over the
starboard rail into the sand, the lower yard beside it.

The piece's row y 0 is the seabed: the starboard bilge rests in it, sand banks against the starboard side and silts
the low side of the hold and cabin. The bow points north (-z). Loot: a chest in the stern cabin (wreck loot table),
the sea chest in the forward hold (empty: the sea chest has no loot table), cargo slid to starboard in the hold."""
import math
import random

from _style import PALETTE, bed_disc, crust, drift, loot_chest, sea_chest
from buildspec import Piece

rng = random.Random(0x5105)

# ---------------------------------------------------------------- the sloop in ship coordinates
CX = 4
L = 25
HW = [0, 1, 1, 2, 2, 3, 3] + [4] * 14 + [3, 3, 3, 3]
LOWER = {1: (2, 2, 21), 2: (1, 1, 23)}
FORE_END = 6       # last z of the forecastle
CABIN = 16         # the cabin's front wall
STERN = L - 1
MAST_Z = 10
BREAK = 12         # the frames left standing between the halves


def hw(z, y):
    if not 0 <= z < L:
        return -1
    if y in LOWER:
        red, z0, z1 = LOWER[y]
        if not z0 <= z <= z1:
            return -1
        return HW[z] - red
    return HW[z]


def inside(x, y, z):
    w = hw(z, y)
    return w >= 0 and abs(x - CX) <= w


def edge(x, y, z):
    return inside(x, y, z) and any(not inside(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))


SIDE = {1: "lower", 2: "lower", 3: "wale", 4: "band"}
src = {}   # (x, y, z) -> kind
for z in range(L):
    if 3 <= z <= 20:
        src[(CX, 0, z)] = "keel"
    for x in range(9):
        for y in range(1, 5):
            if not inside(x, y, z):
                continue
            if y == 1 or edge(x, y, z):
                src[(x, y, z)] = SIDE[y]
            elif y == 4:
                src[(x, y, z)] = "deck"
            else:
                src[(x, y, z)] = "hold"
        if not inside(x, 5, z):
            continue
        if z >= CABIN:
            wall = edge(x, 5, z) or z == CABIN
            for y in (5, 6):
                src[(x, y, z)] = "cabin_wall" if wall else "cabin"
            src[(x, 7, z)] = "top" if edge(x, 5, z) else "deck"
            if edge(x, 5, z):
                src[(x, 8, z)] = "rail"
        elif edge(x, 5, z):
            src[(x, 5, z)] = "top"
            if z <= FORE_END:
                src[(x, 6, z)] = "rail"
        elif z <= FORE_END:
            src[(x, 5, z)] = "deck"
# the cabin door is gone; stern and side windows
src[(CX, 5, CABIN)] = "cabin"
src[(CX, 6, CABIN)] = "cabin"
for pos in ((2, 6, STERN), (6, 6, STERN), (0, 6, 19), (8, 6, 19), (1, 6, 21), (7, 6, 21), (1, 6, 23), (7, 6, 23)):
    if pos in src:
        src[pos] = "pane"
# the mast stump: through the deck, snapped three blocks above it
for y in range(1, 8):
    src[(CX, y, MAST_Z)] = "mast"

# the break: frames only at z 12, jagged planking at z 11 and 13
for (x, y, z), kind in list(src.items()):
    if z == BREAK:
        if kind in ("lower", "wale", "band", "top") and rng.random() < 0.75:
            src[(x, y, z)] = "rib"
        elif kind != "keel":
            del src[(x, y, z)]
    elif abs(z - BREAK) == 1 and kind in ("lower", "band", "top", "deck") and rng.random() < 0.45:
        del src[(x, y, z)]

# missing planking: patches in the shell and the deck (source centre, radius); where the side planking is gone, every
# third frame still stands behind it
SHELL = ("lower", "band", "top", "deck", "cabin_wall")
for (px, py, pz), r in (((0, 3, 6), 1.8), ((8, 2, 16), 1.3), ((4, 4, 15), 1.8), ((3, 7, 21), 1.5), ((0, 5, 20), 1.2)):
    for (x, y, z), kind in list(src.items()):
        if kind in SHELL and math.dist((x, y, z), (px, py, pz)) <= r + 0.5 * rng.random():
            if kind != "deck" and edge(x, y, z) and z % 3 == 0:
                src[(x, y, z)] = "rib"
            elif y <= 3 and kind != "deck" and inside(x, y, z) and not edge(x, y, z):
                src[(x, y, z)] = "hold"
            else:
                src[(x, y, z)] = None
src = {p: k for p, k in src.items() if k is not None}

# ---------------------------------------------------------------- heel the halves
PRIORITY = {"keel": 9, "wale": 8, "rib": 8, "mast": 7, "lower": 7, "band": 7, "top": 6, "cabin_wall": 6, "deck": 5,
            "pane": 4, "rail": 3}
VOIDS = ("hold", "cabin")

solid = {}   # target (x, y, z) -> kind
void = {}    # target (x, y, z) -> hold | cabin
for z in range(L):
    angle, dx = (22, 0) if z <= BREAK else (27, 1)
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    for tx in range(-8, 18):
        for ty in range(-8, 14):
            # turn the target block's centre back into the upright hull and take the block there
            u1, v1 = tx - dx - CX, ty + 0.5
            u, v = u1 * c - v1 * s, u1 * s + v1 * c
            kind = src.get((math.floor(u + CX + 0.5), math.floor(v), z))
            if kind in VOIDS:
                void[(tx, ty, z)] = kind
            elif kind is not None:
                solid[(tx, ty, z)] = kind
    # close the planking where two blocks only meet at an edge: fill the corner outside the hull, else the lower one
    for (tx, ty, zz), kind in sorted(solid.items()):
        if zz != z or kind in ("rail", "pane"):
            continue
        for ddx in (-1, 1):
            other = solid.get((tx + ddx, ty + 1, z))
            if other is None or other in ("rail", "pane") or (tx + ddx, ty, z) in solid or (tx, ty + 1, z) in solid:
                continue
            corners = [(tx + ddx, ty, z), (tx, ty + 1, z)]
            outside = [q for q in corners if q not in void]
            q = (outside or corners)[0]
            solid[q] = kind if PRIORITY[kind] <= PRIORITY[other] else other
            void.pop(q, None)

# piece coordinates: two blocks of seabed margin on each side, one at each end
MX, MZ = 1, 1
x_lo = min(t[0] for t in solid)
y_lo = min(t[1] for t in solid)
x_hi = max(t[0] for t in solid)
y_hi = max(t[1] for t in solid)


def to_piece(t):
    return t[0] - x_lo + MX, t[1] - y_lo, t[2] + MZ


SIZE = (x_hi - x_lo + 1 + 2 * MX, y_hi - y_lo + 1, L + 2 * MZ)
p = Piece("wreck_sunken_sloop", "Sunken Sloop", SIZE, PALETTE)

solid = {to_piece(t): k for t, k in solid.items()}
void = {to_piece(t): k for t, k in void.items()}

# ---------------------------------------------------------------- blocks
for (x, y, z), kind in sorted(solid.items()):
    if kind == "keel":
        key = "dark_beam_z"
    elif kind == "lower":
        key = crust(rng, "dark", 0.2 if y <= 2 else 0.06)
    elif kind == "wale":
        key = crust(rng, "dark_beam_z", 0.05)
    elif kind == "band":
        key = crust(rng, "spruce", 0.04)
    elif kind in ("top", "cabin_wall"):
        key = crust(rng, "dark", 0.03)
    elif kind == "deck":
        key = "spruce"
    elif kind == "rail":
        if rng.random() < 0.35:
            continue
        key = "fence"
    elif kind == "pane":
        if rng.random() < 0.4:
            continue
        key = "pane"
    elif kind == "mast":
        key = "mast"
    elif kind == "rib":
        key = "spruce_post"
    else:
        raise ValueError(kind)
    p.put(x, y, z, key)

# silt: the low side of the hold and the cabin fills with sand
by_z = {}
for (x, y, z), kind in void.items():
    by_z.setdefault((z, kind), []).append(y)
for (x, y, z), kind in list(void.items()):
    if y <= min(by_z[(z, kind)]) + (1 if kind == "hold" else 0):
        p.put(x, y, z, "sand")
        del void[(x, y, z)]


def floor_cells(kind, z_range):
    """Void cells of ``kind`` with a block under them, inside ``z_range``."""
    return sorted(pos for pos, k in void.items()
                  if k == kind and pos[2] in z_range and p.get(pos[0], pos[1] - 1, pos[2]) is not None)


def pick(cells, target):
    return min(cells, key=lambda c: (math.dist(c, target), c))


# the cabin's chest, aft against the stern; the sea chest in the forward hold; cargo slid to starboard aft
cabin = floor_cells("cabin", range(CABIN + 2 + MZ, STERN + MZ))
cx, cy, cz = pick(cabin, (SIZE[0], 0, STERN))
loot_chest(p, cx, cy, cz, "north")
del void[(cx, cy, cz)]
fore = floor_cells("hold", range(4 + MZ, BREAK - 2 + MZ))
sx_, sy_, sz_ = pick(fore, (SIZE[0] // 2, 0, 7 + MZ))
sea_chest(p, sx_, sy_, sz_, "west")
del void[(sx_, sy_, sz_)]
aft = floor_cells("hold", range(BREAK + 2 + MZ, CABIN + MZ))
for i, key in enumerate(("cargo_barrel", "crate", "cargo_barrel")):
    if not aft:
        break
    c = pick(aft, (SIZE[0], 0, BREAK + 2 + MZ + i))
    p.put(*c, key)
    aft.remove(c)

# ---------------------------------------------------------------- the fallen mast and the yard
top = {}   # (x, z) -> highest block
for (x, y, z) in p.blocks:
    top[(x, z)] = max(top.get((x, z), 0), y)


def lay_x(z, x0, x1, key):
    """A spar lying along x on row z from x0 (high end) to x1, sloping down in steps onto whatever is under it: the
    line from its high end to the seabed (y 1), raised wherever the hull is in the way."""
    y0 = top.get((x0, z), 0) + 1
    prev = None
    for x in range(x0, x1 + 1):
        t = (x - x0) / (x1 - x0)
        y = max(round(y0 + (1 - y0) * t), top.get((x, z), 0) + 1)
        # a step of more than one block (or a rise over the hull) is closed with a block of the column, so the spar
        # stays one piece face to face
        rows = [y] if prev is None or abs(prev - y) <= 1 and prev >= y else range(min(prev, y), max(prev, y) + 1)
        for yy in rows:
            if yy < SIZE[1] and p.get(x, yy, z) is None:
                p.put(x, yy, z, key)
        if prev is not None and prev > y and p.get(x, prev, z) is None and prev - y == 1:
            p.put(x, prev, z, key)
        prev = y


# the mast snapped at the stump and fell to starboard across the after deck, its head in the sand
MAST_ROW = BREAK + MZ + 3
high = max(range(SIZE[0]), key=lambda x: (top.get((x, MAST_ROW), 0), -x))
lay_x(MAST_ROW, high, SIZE[0] - 1, "mast_x")
# the lower yard, torn off, lies in the sand along the starboard side
yard_x = SIZE[0] - 1
for z in range(BREAK + MZ - 5, BREAK + MZ):
    if p.get(yard_x, 1, z) is None:
        p.put(yard_x, 1, z, "pirates_n_ships:yard[axis=z]")
p.put(yard_x, 1, BREAK + MZ, "chain_z")

# ---------------------------------------------------------------- the seabed: bed, drifts, spilled ballast
for z in range(SIZE[2]):
    xs = [x for (x, y, zz) in p.blocks if zz == z and y <= 2 and not p.blocks[(x, y, zz)].startswith("sand")]
    if not xs:
        continue
    east = max(xs)
    for dx, h in ((1, 2), (2, 1)):
        for y in range(1, h + 1):
            if east + dx < SIZE[0] and rng.random() < 0.85:
                drift(p, east + dx, y, z)
    west = min(xs)
    if west - 1 >= 0 and rng.random() < 0.5:
        drift(p, west - 1, 1, z)
bed_disc(p, rng, (SIZE[0] - 1) / 2, (SIZE[2] - 1) / 2, SIZE[0] / 2 + 0.5, SIZE[2] / 2 + 0.5, ragged=0.2)
for x, z in ((SIZE[0] - 2, BREAK + MZ - 1), (SIZE[0] - 3, BREAK + MZ + 1), (SIZE[0] - 2, BREAK + MZ + 2),
             (1, BREAK + MZ)):
    if p.get(x, 1, z) is None:
        p.put(x, 1, z, "mossy" if rng.random() < 0.6 else "cobble")

p.emit("the starter sloop's hull heeled over to starboard on the seabed, broken amidships, mast fallen across the deck",
       ["y 0 is the seabed row; the bow points north (-z); the starboard (east) side lies in the sand.",
        "Chest (loot table pirates_n_ships:chests/wreck) in the stern cabin, sea chest in the forward hold."])
