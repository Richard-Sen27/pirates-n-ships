"""Generates the deck tavern (a 9x6 grog shack behind a plank terrace, 11x9x12 box) and prints the BuildSpec as JSON.

A tavern variant of the ``huts`` pool (STR2): a terrace of spruce slabs half a block above the ground in front, with
a rope rail on palm posts, two barrel tables with stools and a rum cask on a trestle; behind it the shack with an open
serving hatch under a striped sailcloth awning, a thatch with its ridge front to back, kegs and a bar inside. A
hanging sign ("The Drunken Kraken") on a post at the terrace's steps, barrels stacked against the side, trodden ground
fading into the terrain. ``hut_in`` at the steps, on the north edge."""
from _camp import crate_stack, hanging_sign, patch, rope_coil
from _style import (PALETTE, THATCH, awning, chimney, roof_patches, rope_rail, shutter_window, stool, weatherboard)
from buildspec import Piece

W = 11
X0, X1 = 0, W - 1
DECK0, DECK1 = 0, 4        # the terrace rows
Z0, Z1 = 5, 10             # the shack's walls
TOP = 3                    # the shack's floor is y 1 (level with the terrace), walls y 2..3
STEP = 5

p = Piece("pirate_island_tavern_deck", "Pirate Tavern (deck)", (W, 11, Z1 + 2), PALETTE)

# the terrace: spruce slabs on the ground row's posts, half a block up, a rope rail on palm posts round it
p.fill(X0, 0, DECK0 + 1, X1, 0, DECK1, "coarse")
p.fill(X0 + 1, 1, DECK0, X1 - 1, 1, DECK1, "minecraft:spruce_slab[type=bottom]")
for x, z in ((3, 1), (7, 3), (5, 2), (2, 4)):
    p.put(x, 1, z, "minecraft:dark_oak_slab[type=bottom]")
for x in (X0, X1):
    p.fill(x, 1, DECK0, x, 2, DECK0, "palm")
    rope_rail(p, x, DECK0 + 1, x, DECK1, 1, every=2)
rope_rail(p, X0 + 1, DECK0, STEP - 1, DECK0, 1, every=2)
rope_rail(p, STEP + 1, DECK0, X1 - 1, DECK0, 1, every=2)
p.put(STEP, 1, DECK0, "minecraft:spruce_slab[type=bottom]")
# two barrel tables with stools, a rum cask on a trestle
for tx, tz in ((3, 2), (8, 2)):
    p.put(tx, 1, tz, "minecraft:barrel[facing=up,open=false]")
    p.put(tx, 2, tz, "minecraft:spruce_pressure_plate[powered=false]")
    stool(p, tx - 1, 1, tz, "west")
    stool(p, tx + 1, 1, tz, "east")
p.put(1, 1, 4, "fence")
p.put(2, 1, 4, "fence")
p.put(1, 2, 4, "minecraft:barrel[facing=east,open=false]")
p.put(2, 2, 4, "minecraft:barrel[facing=east,open=false]")

# the shack: plinth, floor at y 1, board walls, a serving hatch over the terrace under an awning
p.ring(X0, Z0, X1, Z1, 0, 0, "cobble")
for x, z in ((X0, Z0), (X1, Z1), (4, Z1), (X1, 7)):
    p.put(x, 0, z, "mossy")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "coarse")
p.fill(X0, 1, Z0, X1, 1, Z1, "spruce")
p.ring(X0, Z0, X1, Z1, 2, TOP, "dark")
weatherboard(p, X0, 2, Z0, X1, TOP, Z1, salt=411)
for x in (X0, X1):
    for z in (Z0, Z1):
        p.fill(x, 2, z, x, TOP, z, "post")
# the door and the hatch in the front wall
p.put(STEP, 2, Z0, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
p.put(STEP, 3, Z0, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
for x in (2, 3, 7, 8):
    p.clear(x, 3, Z0)
    p.put(x, 2, Z0, "minecraft:spruce_slab[type=top]")
awning(p, 1, Z0 - 1, 9, Z0 - 1, 4, stripes_along="z", salt=412)
shutter_window(p, X0, 3, 8, "west", wood="dark_oak")
shutter_window(p, X1, 3, 8, "east", sides=("right",), wood="dark_oak")

# roof: the ridge front to back, the gables boarded with a king post
p.roof_ridge_z(Z0, Z1 + 1, X0, X1, TOP + 1, THATCH, "thatch_ridge")
for z in (Z0, Z1):
    for i in range(5):
        for x in range(i + 1, W - 1 - i):
            p.put(x, TOP + 1 + i, z, "dark" if (x + i) % 2 else "spruce")
    p.fill(5, TOP + 3, z, 5, TOP + 5, z, "dark_post")
p.put(4, TOP + 2, Z0, "fence")
p.put(6, TOP + 2, Z0, "fence")
roof_patches(p, salt=413)
chimney(p, 9, Z1, 0, 8)

# inside: a bar across the shack, kegs behind it, a lantern
p.fill(1, 2, Z0 + 2, 9, 2, Z0 + 2, "minecraft:spruce_slab[type=top]")
p.put(STEP, 2, Z0 + 2, "barrel")
for x in (1, 2, 3, 7, 8):
    p.put(x, 2, Z1 - 1, "minecraft:barrel[facing=north,open=false]")
p.put(4, 2, Z1 - 1, "pirates_n_ships:cargo_barrel")
p.put(1, 3, Z1 - 1, "minecraft:barrel[facing=north,open=false]")
p.put(3, 3, Z0 + 2, "lantern")
p.put(5, 6, 8, "lantern_hanging")

# the sign on a post at the steps, barrels against the side, a rope coil
p.fill(4, 1, 0, 4, 3, 0, "palm")
p.put(4, 4, 0, "fence")
p.put(3, 4, 0, "fence")
hanging_sign(p, 3, 3, 0, 8, ["The", "Drunken", "Kraken", ""])
crate_stack(p, X1, 1, Z1 + 1, salt=414)
rope_coil(p, X0, 1, Z1 + 1, layers=3)
patch(p, X0, DECK0, X1, Z1, reach=1, salt=415)

# STR6: the shack's loot chest by the west wall
p.loot_chest(1, 2, 8, "east", "pirate_island/tavern")
p.connector(STEP, 0, 0, "hut_in", "north", "minecraft:coarse_dirt")

p.emit("grog shack behind a plank terrace with barrel tables, a serving hatch under an awning and a hanging sign",
       ["Faces north: the terrace steps and the hut_in connector are on the -z side.",
        "y 0 is the foundation row; the terrace and the shack's floor are at y 1."])
