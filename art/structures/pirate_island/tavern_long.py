"""Generates the long tavern (a 13x7 drinking hall with an open bar front, 13x10x11 box) and prints the BuildSpec as
JSON.

A tavern variant of the ``huts`` pool (STR2): a cobblestone plinth going mossy, a board course with an open rail above
between palm posts along the front, closed board walls on the sides and the back, a long thatch with its ridge along
x and boarded gables with a king post. Inside: a long bar of barrels and a plank counter with kegs behind it, three
tables with stools, lanterns. Outside: a hanging sign on a palm post and fence arm ("The Rusty Cutlass"), barrels
and a rum keg by the door, a fieldstone chimney at the back and trodden ground fading into the terrain. ``hut_in`` at
the doorway on the north edge, one row in front of the eave."""
from _camp import crate_stack, hanging_sign, patch
from _style import (PALETTE, THATCH, chimney, rafter_ends, roof_patches, shutter_window, stool, table, weatherboard)
from buildspec import Piece

W = 13
X0, X1, Z0, Z1 = 0, W - 1, 2, 8
TOP = 2
DOOR = 6
MID = Z0 + 3          # the middle of the side walls

p = Piece("pirate_island_tavern_long", "Pirate Tavern (long hall)", (W, 10, Z1 + 2), PALETTE)

# plinth and floor
p.ring(X0, Z0, X1, Z1, 0, 0, "cobble")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (3, Z0), (9, Z1), (X0, MID), (X1, MID + 1), (6, Z1)):
    p.put(x, 0, z, "mossy")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
for x, dz in ((2, 1), (5, 2), (8, 5), (10, 1), (4, 5)):
    p.put(x, 0, Z0 + dz, "dark")
p.fill(DOOR - 1, 0, 0, DOOR + 1, 0, Z0 - 1, "coarse")
patch(p, X0, Z0, X1, Z1, reach=2, salt=401)

# walls: the front a board course under an open rail, the sides and back closed
p.ring(X0, Z0, X1, Z1, 1, TOP, "dark")
p.fill(X0 + 1, 2, Z0, X1 - 1, 2, Z0, "fence")
weatherboard(p, X0, 1, Z0, X1, TOP, Z1, salt=402)
for x in (X0, 4, 8, X1):
    p.fill(x, 1, Z0, x, TOP, Z0, "palm")
for x in (X0, X1):
    p.fill(x, 1, Z1, x, TOP, Z1, "palm")
    p.fill(x, 1, MID, x, TOP, MID, "post")
p.fill(DOOR - 1, 1, Z0, DOOR - 1, TOP, Z0, "dark_post")
p.fill(DOOR + 1, 1, Z0, DOOR + 1, TOP, Z0, "dark_post")
p.clear(DOOR, 1, Z0)
p.clear(DOOR, 2, Z0)
shutter_window(p, X0, 2, Z0 + 1, "west", wood="dark_oak")
shutter_window(p, X1, 2, Z1 - 1, "east", wood="dark_oak")
shutter_window(p, 4, 2, Z1, "south", wood="dark_oak")
shutter_window(p, 9, 2, Z1, "south", sides=("left",), wood="dark_oak")

# roof: ridge along x, eaves over the front (z 1) and the back (z 9); gables on a tie beam
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, THATCH, "thatch_ridge", "dark", plate="beam_x")
roof_patches(p, salt=403)
for x in (X0, X1):
    p.fill(x, TOP + 1, Z0 + 1, x, TOP + 1, Z1 - 1, "dark_beam_z")
    weatherboard(p, x, TOP + 2, Z0, x, TOP + 5, Z1, keys=("spruce", "dark"), salt=404 + x)
    p.fill(x, TOP + 2, MID, x, TOP + 4, MID, "dark_post")
    p.put(x, TOP + 2, MID - 1, "fence")
    p.put(x, TOP + 2, MID + 1, "fence")
rafter_ends(p, [(0, Z0 - 1), (4, Z0 - 1), (8, Z0 - 1), (12, Z0 - 1)], TOP, "south")
rafter_ends(p, [(0, Z1 + 1), (6, Z1 + 1), (12, Z1 + 1)], TOP, "north")
chimney(p, 2, Z1 + 1, 0, 7)
p.put(2, 1, Z1, "mossy")
p.put(2, 2, Z1, "cobble")

# the bar: barrels at the ends, a plank counter between, kegs and rum behind it
BAR = Z1 - 3
p.put(3, 1, BAR, "barrel")
p.fill(4, 1, BAR, 9, 1, BAR, "minecraft:spruce_slab[type=top]")
p.put(10, 1, BAR, "barrel")
p.put(5, 2, BAR, "lantern")
p.put(8, 2, BAR, "minecraft:skeleton_skull[powered=false,rotation=8]")
for x in range(4, 11):
    p.put(x, 1, BAR + 1, "minecraft:barrel[facing=north,open=false]" if x % 3 else "pirates_n_ships:cargo_barrel")
for x in (4, 7, 10):
    p.put(x, 2, BAR + 1, "minecraft:barrel[facing=east,open=false]")
# three tables with stools in front of the bar
for tx in (2, 6, 10):
    table(p, tx, 1, Z0 + 2)
    stool(p, tx - 1, 1, Z0 + 2, "west")
    stool(p, tx + 1, 1, Z0 + 2, "east")
p.put(2, 1, Z1 - 2, "pirates_n_ships:cargo_crate")
for x in (3, 9):
    p.put(x, 6, MID, "lantern_hanging")
p.put(11, 3, Z1 - 1, "cobweb")

# the sign: a palm post at the door's east side with a fence arm, the sign hanging from it facing the trail
p.fill(8, 1, 0, 8, 4, 0, "palm")
p.put(7, 4, 0, "fence")
hanging_sign(p, 7, 3, 0, 8, ["", "The Rusty", "Cutlass", ""])
# barrels and a rum keg outside the front, a crate stack by the back corner
p.put(1, 1, Z0 - 1, "barrel")
p.put(2, 1, Z0 - 1, "pirates_n_ships:cargo_barrel")
p.put(1, 2, Z0 - 1, "minecraft:barrel[facing=north,open=false]")
p.put(11, 1, Z0 - 1, "minecraft:barrel[facing=up,open=false]")
p.put(10, 1, 0, "pirates_n_ships:cargo_crate")
crate_stack(p, X1, 1, Z1 + 1, salt=405)

p.connector(DOOR, 0, 0, "hut_in", "north", "minecraft:coarse_dirt")

p.emit("long tavern hall with an open bar front, a hanging sign, a long bar, three tables and barrels outside",
       ["Faces north: the doorway and the hut_in connector are on the -z side.",
        "y 0 is the foundation row (plinth and floor), level with the terrain surface."])
