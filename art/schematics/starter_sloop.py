"""Generates the starter sloop BuildSpec (bow toward -Z) and prints it as JSON.

Also the base of the armed sloops (WS4c, TPL2): ``navy_sloop_armed.py`` and ``pirate_sloop_armed.py`` import
:func:`armed_spec`, which adds four guns and a shot locker (:func:`armed`) and a crow's nest with ratlines up to it
(:func:`lookout`); the flag moves from the masthead to an ensign staff on the taffrail. Run on its own this file
still prints the starter sloop byte for byte: the player's own ship has no nest (it is the player's to rig).
"""
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

# close the bottom: the rocker of layer 1 ends before layer 2 does, which left the hold open to the sea aft (x 3..5,
# z 25; SH1b). Every hollow hold cell of layer 2 gets a bottom plank under it.
for zs in range(OFF, STERN + 1):
    for x in range(0, 9):
        if inside(x, 2, zs) and (x, 2, zs) not in blocks and (x, 1, zs) not in blocks:
            put(x, 1, zs, side_block(1))

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

# armed variant (WS4c): two cannons a side in the waist, a shot locker in the hold
GUN_ZS = (13, 15)            # shifted z of the gun pairs, either side of the winch (z 14), abaft the mast (z 13)
LOCKER = (CX, 3, 14)         # beside the mast step in the hold, under the winch: within reach of all four guns
ARMED_PALETTE = {
    "gun_w": "pirates_n_ships:cannon[facing=west,load=empty,part=front]",
    "gun_rear_w": "pirates_n_ships:cannon[facing=west,load=empty,part=rear]",
    "gun_e": "pirates_n_ships:cannon[facing=east,load=empty,part=front]",
    "gun_rear_e": "pirates_n_ships:cannon[facing=east,load=empty,part=rear]",
    "locker": "minecraft:barrel[facing=up,open=false]",
    "locker_stand": "minecraft:spruce_planks",
}


def armed(base):
    """The hull of ``base`` with four guns and the shot locker; nothing else changes.

    Each gun stands on the waist deck (y 5) facing outboard, its muzzle (master, part=front) next to the bulwark and
    its carriage's rear one block inboard; the bulwark block in front of the muzzle is cut out as the gun port (the
    waist bulwark is one block high, the barrel sits at 12..20 px and would run into it). The locker is a vanilla
    barrel (any container within cannons.crew.supply_range = 4 of a gun feeds its crew; a barrel opens under the
    deck where a chest would not) on a plank stand on the hold floor.
    """
    out = dict(base)
    for zs in GUN_ZS:
        for x, side in ((0, "w"), (8, "e")):
            step = 1 if x == 0 else -1
            assert out.get((x, 5, zs)) == "top", (x, zs)  # the waist bulwark
            for cell in ((x + step, 5, zs), (x + 2 * step, 5, zs)):
                assert cell not in out, cell  # free deck
            out.pop((x, 5, zs))
            out[(x + step, 5, zs)] = "gun_" + side
            out[(x + 2 * step, 5, zs)] = "gun_rear_" + side
    lx, ly, lz = LOCKER
    assert (lx, ly, lz) not in out and (lx, ly - 1, lz) not in out
    out[(lx, ly, lz)] = "locker"
    out[(lx, ly - 1, lz)] = "locker_stand"
    return out


# crow's nest and ratlines (TPL2): the nest sits on the mast top (the mast runs to y 19) where the flag was, so the
# flag moves to a two-block ensign staff on the taffrail rail. A run of ratlines on each side of the mast climbs from
# the quarterdeck beside the helm to the masthead: sloped (rising north, toward the bow) from x 3 / x 5 at z 22, y 8 up
# to z 14, y 16, all of it aft of the square sail's cloth (which stands off the yards' plane z 13 by at most 1.5
# blocks, bellied aft) and outside the jib's plane x 4; then hung on the mast's west / east face at z 13 from y 17 to
# 19, above the upper yard (y 16), so neither run touches a yard, the cloth between the yards or the stay. A sloped
# run cannot climb past a yard: the 6 px yard beam fills the mast's sides at y 10 and 16, so only above the upper yard
# does the run go up the mast. From the top of the slope a climber jumps onto the hung net (it hangs over the upper
# yard's end) and climbs it facing the mast; at the top he steps sideways into the nest.
NEST = (CX, 20, MZ)
ENSIGN = [(CX, 9, STERN), (CX, 10, STERN)]   # on the taffrail rail (CX, 8, STERN)
SLOPE_START = (22, 8)                        # (z, y) of each run's foot on the quarterdeck (deck at y 7)
SLOPE_TOP = (14, 16)                         # (z, y) of the last sloped link, right abaft the upper yard
HUNG_YS = range(17, 20)                      # hung links on the mast face, above the upper yard
RAT_SIDES = ((CX - 1, "w"), (CX + 1, "e"))   # x of each run and the face of the mast it hangs on (port, starboard)
LOOKOUT_PALETTE = {
    "nest": "pirates_n_ships:crows_nest",
    "ensign_bottom": "pirates_n_ships:flagpole[facing=north,flag=none,part=bottom]",
    "ensign_top": "pirates_n_ships:flagpole[facing=north,flag=none,part=top]",
    "rat_slope": "pirates_n_ships:ratlines[facing=north,kind=slope,waterlogged=false]",
    "rat_w": "pirates_n_ships:ratlines[facing=west,kind=wall,waterlogged=false]",
    "rat_e": "pirates_n_ships:ratlines[facing=east,kind=wall,waterlogged=false]",
}


def ratline_cells():
    """The ratlines of both runs: (x, y, z) -> palette key, foot first."""
    out = {}
    (z0, y0), (z1, y1) = SLOPE_START, SLOPE_TOP
    assert z0 - z1 == y1 - y0  # 45 degrees: one up, one forward (north) per link
    for x, side in RAT_SIDES:
        for i in range(z0 - z1 + 1):
            out[(x, y0 + i, z0 - i)] = "rat_slope"
        for y in HUNG_YS:
            out[(x, y, MZ)] = "rat_" + side
    return out


def lookout(base):
    """``base`` with the crow's nest on the mast top, the ratlines up to it and the flag on the taffrail."""
    out = dict(base)
    nx, ny, nz = NEST
    assert out.get(NEST) == "flag" and out.get((nx, ny - 1, nz)) == "mast", "the nest goes where the flag was"
    out[NEST] = "nest"
    for (x, y, z), key in zip(ENSIGN, ("ensign_bottom", "ensign_top")):
        assert (x, y, z) not in out, (x, y, z)
    assert out.get((ENSIGN[0][0], ENSIGN[0][1] - 1, ENSIGN[0][2])) == "rail"
    out[ENSIGN[0]] = "ensign_bottom"
    out[ENSIGN[1]] = "ensign_top"
    for cell, key in ratline_cells().items():
        assert cell not in out, cell  # free: no yard, no rail, nothing in the way
        out[cell] = key
    # the foot stands on the quarterdeck, the hung links hang on mast logs, the top link sits right above the yard
    for x, _ in RAT_SIDES:
        assert out.get((x, SLOPE_START[1] - 1, SLOPE_START[0])) == "deck"
        assert out.get((x, SLOPE_TOP[1], MZ)) == "yard"
        for y in HUNG_YS:
            assert out.get((CX, y, MZ)) == "mast"
    return out


def spec(cells, pal, spec_id, name, style, notes):
    """The BuildSpec of ``cells``: runs along x merged into box operations, the palette keys in use only."""
    ops = []
    for (y, zs) in sorted({(y, z) for (_, y, z) in cells}):
        xs = sorted(x for (x, yy, z) in cells if yy == y and z == zs)
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1 and cells[(x, y, zs)] == cells[(start, y, zs)]:
                prev = x
                continue
            ops.append({"type": "box", "from": [start, y, zs], "to": [prev, y, zs], "block": cells[(start, y, zs)]})
            if x is not None:
                start = prev = x
    used = set(cells.values())
    return {
        "id": spec_id,
        "name": name,
        "minecraftVersion": "1.21.1",
        "size": {"x": 9, "y": 21, "z": STERN + 2},
        "palette": {k: v for k, v in pal.items() if k in used},
        "operations": ops,
        "metadata": {"style": style, "notes": notes},
    }


NOTES = ["Bow toward -Z. Helm faces south.",
         "Square sail: yards at y=10 (9 long) and y=16 (7 long) on the mast at z=13.",
         "Jib: use a rope on the mast-head cleat, then on the bowsprit cleat."]
ARMED_NOTES = NOTES + ["Guns: two a side in the waist at z=13 and z=15, muzzles at x=1 (west) and x=7 (east) "
                       "through gun ports cut in the bulwark; shot locker (barrel) in the hold at [4, 3, 14].",
                       "Crow's nest on the mast top at [4, 20, 13]; ratlines on both sides of the mast, sloped from "
                       "the quarterdeck at z=22 up to z=14, y=16, then hung on the mast from y=17 to 19; the flag on "
                       "an ensign staff on the taffrail at [4, 9..10, 27]."]


def armed_spec(spec_id, name):
    return spec(lookout(armed(blocks)), {**palette, **ARMED_PALETTE, **LOOKOUT_PALETTE}, spec_id, name,
                "single-mast sloop with forecastle and stern cabin, four guns in the waist, a crow's nest",
                ARMED_NOTES)


if __name__ == "__main__":
    print(json.dumps(spec(blocks, palette, "pns_starter_sloop", "Starter Sloop",
                          "single-mast sloop with forecastle and stern cabin", NOTES)))
