"""Generates the stern section of a larger ship, broken off and half buried, and prints the BuildSpec as JSON.

The transom faces north (-z, row z 1) with the rudder hung on it (row z 0, iron gudgeon straps either side); the
broken end is at the south (+z), where both sides fall away in jagged steps and the frames show. The transom is
framed: stripped dark oak quarter posts, a trim band at the cabin floor and at the poop deck, the cabin windows
(glass panes between a mullion, two of them broken out), the nameplate above the rudder head and the taffrail on
top. The sides carry a wale and a trim band over dark oak below and spruce above, with a quarter window each.

Row y 0 is the seabed row: the hull's bottom sits in it, sand fills the hold up to the cabin floor (y 2) and banks
against both sides; it drifts in over the cabin floor at the broken end. Inside the great cabin: a chest with the
wreck loot table, a table and chair, a keg, a lantern hanging from the poop deck (y 6, half collapsed)."""
import random

from _style import PALETTE, barrel, bed_disc, crust, drift, loot_chest, stairs, trapdoor, underpin
from buildspec import Piece

rng = random.Random(0x57E2)
p = Piece("wreck_stern", "Wreck Stern", (9, 8, 11), PALETTE)
T = 1            # the transom's row
FLOOR = 2        # the cabin floor (and the wale)
POOP = 6         # the poop deck (and the upper trim band)
RAIL = 7

# how high each side still stands, by z (the break runs aft to fore: lower toward +z)
WEST = {z: RAIL for z in range(T, 5)} | {5: 6, 6: 5, 7: 4, 8: 3, 9: 3, 10: 1}
EAST = {z: RAIL for z in range(T, 4)} | {4: 6, 5: 6, 6: 5, 7: 4, 8: 3, 9: 2, 10: 2}


def width(y):
    """The hull's half-open x range at row y (narrow at the bottom)."""
    return {0: (2, 6), 1: (1, 7)}.get(y, (0, 8))


def side(x, y, z):
    """The side planking at (x, y, z): dark oak below the wale, the wale, spruce above, the trim band, the rail."""
    if y == FLOOR:
        return "dark_beam_z"
    if y == POOP:
        return "dark_beam_z"
    if y == RAIL:
        return "fence"
    if y < FLOOR:
        return crust(rng, "dark", 0.2)
    return crust(rng, "spruce", 0.05)


# ---------------------------------------------------------------- the hull
for z in range(T + 1, 11):
    x0, x1 = width(0)
    for x in range(x0, x1 + 1):                          # the bottom, the keel in the middle
        p.put(x, 0, z, "dark_beam_z" if x == 4 else crust(rng, "dark", 0.2))
    for y in range(1, RAIL + 1):
        x0, x1 = width(y)
        if y <= WEST[z]:
            p.put(x0, y, z, side(x0, y, z))
        if y <= EAST[z]:
            p.put(x1, y, z, side(x1, y, z))
    # silt in the hold up to the floor; the floor, sanded over at the broken end
    for x in range(2, 7):
        p.put(x, 1, z, "sand")
    for x in range(1, 8):
        if z == 10 and x in (1, 2, 6, 7):
            continue
        p.put(x, FLOOR, z, "sand" if z >= 9 and rng.random() < 0.6 else "spruce")
# the frames where the sides broke away (stripped spruce ribs a row above the jagged edge)
for z, (wy, ey) in ((6, (6, 6)), (9, (4, 3))):
    p.put(0, wy, z, "spruce_post")
    p.put(8, ey, z, "spruce_post")
# quarter windows in the sides, a frame post standing proud aft of the windows
p.put(0, 4, 3, "pane")
p.put(8, 4, 3, "pane")
for y in range(FLOOR + 1, POOP):
    p.put(0, y, 4, "dark_post")
    p.put(8, y, 4, "dark_post")

# the poop deck over the cabin, half fallen in toward the break
for z in range(T + 1, 6):
    for x in range(1, 8):
        if z == 5 and x in (2, 3, 6) or z == 4 and x == 6:
            continue
        p.put(x, POOP, z, "dark" if x in (1, 7) else "spruce")
p.put(5, POOP - 1, 5, "spruce_slab")                      # a deck plank hanging down where it broke
p.put(5, FLOOR + 1, 7, "spruce_slab")                     # and one that fell

# ---------------------------------------------------------------- the transom (row z 1)
for y in range(0, RAIL + 1):
    x0, x1 = width(y)
    for x in range(x0, x1 + 1):
        if y == RAIL:
            key = "dark_post" if x in (0, 4, 8) else "fence"
        elif x in (0, 8) and y >= FLOOR:
            key = "dark_post"                              # the quarter posts
        elif y in (FLOOR, POOP):
            key = "dark_beam_x"                           # the trim bands
        elif y in (3, 4) and x in (2, 3, 5, 6):
            key = "pane"                                  # the cabin windows
        elif y in (3, 4) and x == 4:
            key = "dark_post"                             # the mullion
        elif y == 5:
            key = "spruce"
        else:
            key = crust(rng, "dark", 0.2 if y < FLOOR else 0.04)
        p.put(x, y, T, key)
p.clear(3, 4, T)                                          # broken out
p.clear(5, 3, T)
for x in (2, 3, 5, 6):                                    # sills under the windows, a hood over them
    p.put(x, FLOOR, 0, trapdoor("dark_oak", "north", "top"))
    p.put(x, 5, 0, trapdoor("spruce", "north", "bottom"))

# the rudder on the transom's centre line, its straps either side, the nameplate above its head
for y in range(0, 4):
    p.put(4, y, 0, "dark")
p.put(4, 4, 0, "dark_post")
for y in (1, 3):
    p.put(3, y, 0, "bars")
    p.put(5, y, 0, "bars")
p.put(4, 5, 0, "pirates_n_ships:nameplate[facing=north]")

# ---------------------------------------------------------------- the great cabin (floor y 2, rows 3..5)
loot_chest(p, 6, 3, 2, "south")
p.put(3, 3, 4, "dark_fence")                              # the table
p.put(3, 4, 4, "minecraft:dark_oak_pressure_plate[powered=false]")
p.put(2, 3, 4, stairs("spruce", "west"))                   # a chair, the other one fallen over
p.put(4, 3, 5, stairs("spruce", "north", "top"))
p.put(1, 3, 2, barrel("east"))                             # a keg
p.put(1, 4, 2, "lantern")
p.put(4, 5, 3, "lantern_hanging")
for x, z in ((2, 8), (3, 8), (5, 9), (4, 9), (6, 8)):       # sand drifting in at the broken end
    drift(p, x, 3, z)
p.put(6, 3, 7, "crate")

# ---------------------------------------------------------------- half buried: sand banked against the sides and the end
for z in range(0, 11):
    drift(p, 0, 1, z)
    drift(p, 8, 1, z)
    drift(p, 1, 0, z)
    drift(p, 7, 0, z)
    if rng.random() < 0.7:
        drift(p, 0, 2, z) if z % 2 else drift(p, 8, 2, z)
for x in (1, 2, 6, 7):
    drift(p, x, 1, 0)
    drift(p, x, 0, 0)
for x in range(1, 8):
    drift(p, x, 3 if x in (3, 4, 5) else 2, 10)

bed_disc(p, rng, 4, 5, 5, 6, ragged=0.15)
underpin(p, rng)

p.emit("the stern section of a larger ship, half buried: framed transom with cabin windows, rudder, nameplate, "
       "great cabin with a chest",
       ["y 0 is the seabed row; the transom faces north (-z), the broken end is at the south.",
        "Chest (loot table pirates_n_ships:chests/wreck) at [6, 3, 2]."])
