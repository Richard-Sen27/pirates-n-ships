"""Generates the starter sloop BuildSpec (bow toward -Z) and prints it as JSON."""
import json

CX = 4          # centre line
OFF = 3         # z offset of the hull (room for the bowsprit)
L = 25          # hull length

# half-width of the hull at the waterline and above, by hull z (0 = stem)
HW = [0, 1, 1, 2, 2, 3, 3] + [4] * 14 + [3, 3, 3, 3]
assert len(HW) == L

# lower layers: half-width reduction and z range (rocker)
LOWER = {1: (2, 2, 21), 2: (1, 1, 23)}

FORE_END = 9    # last shifted z of the forecastle
CABIN = 19      # first shifted z of the cabin / quarterdeck
STERN = OFF + L - 1

blocks = {}


def put(x, y, z, b):
    blocks[(x, y, z)] = b


def hw(zs, y):
    z = zs - OFF
    if not 0 <= z < L:
        return -1
    if y in LOWER:
        red, z0, z1 = LOWER[y]
        if not z0 <= z <= z1:
            return -1
        return HW[z] - red
    return HW[z]


def inside(x, y, zs):
    w = hw(zs, y)
    return w >= 0 and abs(x - CX) <= w


def edge(x, y, zs):
    return inside(x, y, zs) and any(
        not inside(x + dx, y, zs + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def side_block(y):
    return {1: "lower", 2: "lower", 3: "wale", 4: "band"}.get(y, "top")


# keel
for zs in range(OFF + 3, OFF + 21):
    put(CX, 0, zs, "keel")

# hull layers 1..4: shell, hollow hold (y 2..3), deck at 4
for y in range(1, 5):
    for zs in range(OFF, STERN + 1):
        for x in range(0, 9):
            if not inside(x, y, zs):
                continue
            if y == 1 or edge(x, y, zs):
                put(x, y, zs, side_block(y))
            elif y == 4:
                put(x, y, zs, "deck")

# y 5: bulwarks everywhere, forecastle floor forward, cabin walls aft
for zs in range(OFF, STERN + 1):
    for x in range(0, 9):
        if not inside(x, 5, zs):
            continue
        if edge(x, 5, zs):
            put(x, 5, zs, "top")
        elif zs <= FORE_END:
            put(x, 5, zs, "deck")

# forecastle rail
for zs in range(OFF, FORE_END + 1):
    for x in range(0, 9):
        if edge(x, 5, zs):
            put(x, 6, zs, "rail")
# stem head rises above the rail
put(CX, 6, OFF, "top")

# cabin (y 5..6) under the quarterdeck (floor y 7)
for zs in range(CABIN, STERN + 1):
    for x in range(0, 9):
        if not inside(x, 5, zs):
            continue
        if edge(x, 5, zs) or zs == CABIN:
            put(x, 6, zs, "top")
            if zs == CABIN:
                put(x, 5, zs, "top")
        put(x, 7, zs, "top" if edge(x, 5, zs) else "deck")
        if edge(x, 5, zs):
            put(x, 8, zs, "rail")
# quarterdeck front: open rail
for x in range(0, 9):
    if inside(x, 5, CABIN) and not edge(x, 5, CABIN):
        put(x, 8, CABIN, "rail")

# cabin door and windows
put(CX, 5, CABIN, "door_lower")
put(CX, 6, CABIN, "door_upper")
for x in (2, 6):
    put(x, 6, STERN, "pane")
for zs in (22, 25):
    put(0, 6, zs, "pane") if inside(0, 6, zs) else None
    put(8, 6, zs, "pane") if inside(8, 6, zs) else None
for zs in (24, 26):
    put(1, 6, zs, "pane")
    put(7, 6, zs, "pane")

# stairs up to the quarterdeck on both sides
for x in (1, 7):
    put(x, 5, CABIN - 3, "stair")
    put(x, 5, CABIN - 2, "top")
    put(x, 6, CABIN - 2, "stair")
    put(x, 5, CABIN - 1, "top")
    put(x, 6, CABIN - 1, "top")
    put(x, 7, CABIN - 1, "stair")
    # gap in the quarterdeck front rail above the landing
    blocks.pop((x, 8, CABIN), None)

# step up to the forecastle
put(CX, 5, FORE_END + 1, "stair_fwd")

# bowsprit and figurehead
for zs in range(0, OFF):
    put(CX, 6, zs, "sprit")
put(CX, 4, OFF - 1, "figure")

# mast with the square sail (lower yard 9, upper yard 7, 6 apart)
MZ = 13
for y in range(1, 20):
    put(CX, y, MZ, "mast")
for x in range(0, 9):
    put(x, 10, MZ, "yard")
for x in range(1, 8):
    put(x, 16, MZ, "yard")
put(CX, 20, MZ, "flag")

# jib: the head (A) and foot (C) cleats on the mast, the tack (B) on the bowsprit tip
put(CX, 15, MZ - 1, "cleat_mast")
put(CX, 8, MZ - 1, "cleat_mast")
put(CX, 7, 0, "cleat_floor")

# hatch with a ladder down the mast into the hold
put(CX, 4, MZ - 1, "hatch")
put(CX, 3, MZ - 1, "ladder")
put(CX, 2, MZ - 1, "ladder")

# stations and gear
put(CX, 5, MZ + 1, "winch")
put(CX, 6, 7, "capstan")
put(6, 5, 17, "pump")
put(1, 5, 11, "barrel")
put(7, 5, 11, "barrel")
put(1, 5, 12, "crate")
put(2, 2, 16, "crate")
put(6, 2, 16, "crate")
put(2, 2, 17, "barrel")
put(6, 2, 17, "water")
put(2, 5, 17, "water")
put(CX, 8, 22, "helm")
put(CX, 5, STERN - 1, "chest")
put(CX, 4, STERN + 1, "plate")
for x in (1, 7):
    put(x, 9, STERN, "lantern")
for x in (1, 7):
    put(x, 7, FORE_END, "lantern")

palette = {
    "lower": "minecraft:dark_oak_planks",
    "wale": "minecraft:stripped_dark_oak_log[axis=z]",
    "band": "minecraft:spruce_planks",
    "top": "minecraft:dark_oak_planks",
    "keel": "minecraft:stripped_dark_oak_log[axis=z]",
    "deck": "minecraft:spruce_planks",
    "rail": "minecraft:spruce_fence",
    "pane": "minecraft:glass_pane",
    "door_lower": "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]",
    "door_upper": "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]",
    "stair": "minecraft:spruce_stairs[facing=south,half=bottom]",
    "stair_fwd": "minecraft:spruce_stairs[facing=north,half=bottom]",
    "sprit": "minecraft:spruce_log[axis=z]",
    "mast": "minecraft:spruce_log[axis=y]",
    "hatch": "minecraft:spruce_trapdoor[facing=north,half=top,open=false]",
    "ladder": "minecraft:ladder[facing=north]",
    "lantern": "minecraft:lantern[hanging=false]",
    "yard": "pirates_n_ships:yard[axis=x]",
    "flag": "pirates_n_ships:flagpole",
    "cleat_mast": "pirates_n_ships:cleat[face=wall,facing=north]",
    "cleat_floor": "pirates_n_ships:cleat[face=floor,facing=north]",
    "figure": "pirates_n_ships:figurehead_lion[facing=north]",
    "winch": "pirates_n_ships:sail_winch[facing=south]",
    "capstan": "pirates_n_ships:capstan",
    "pump": "pirates_n_ships:bilge_pump[facing=west]",
    "barrel": "pirates_n_ships:cargo_barrel",
    "crate": "pirates_n_ships:cargo_crate",
    "water": "pirates_n_ships:water_barrel[fill=4]",
    "helm": "pirates_n_ships:helm[facing=south]",
    "chest": "pirates_n_ships:sea_chest[facing=north]",
    "plate": "pirates_n_ships:nameplate[facing=south]",
}

# merge runs along x into box operations
ops = []
for (y, zs) in sorted({(y, z) for (_, y, z) in blocks}):
    xs = sorted(x for (x, yy, z) in blocks if yy == y and z == zs)
    start = prev = xs[0]
    for x in xs[1:] + [None]:
        if x is not None and x == prev + 1 and blocks[(x, y, zs)] == blocks[(start, y, zs)]:
            prev = x
            continue
        ops.append({"type": "box", "from": [start, y, zs], "to": [prev, y, zs], "block": blocks[(start, y, zs)]})
        if x is not None:
            start = prev = x

spec = {
    "id": "pns_starter_sloop",
    "name": "Starter Sloop",
    "minecraftVersion": "1.21.1",
    "size": {"x": 9, "y": 21, "z": STERN + 2},
    "palette": palette,
    "operations": ops,
    "metadata": {"style": "single-mast sloop with forecastle and stern cabin",
                 "notes": ["Bow toward -Z. Helm faces south.",
                           "Square sail: yards at y=10 (9 long) and y=16 (7 long) on the mast at z=13.",
                           "Jib: use a rope on the mast-head cleat, then on the bowsprit cleat."]},
}
print(json.dumps(spec))
