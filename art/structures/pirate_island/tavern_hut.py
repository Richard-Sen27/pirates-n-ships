"""Generates the pirate island's tavern hut (9x7 footprint, an open-sided beach bar under a palm-thatch look roof of
dark oak) and prints the BuildSpec as JSON. The doorway and the hut connector are on the north (-z) side; row z 0 is
the sand in front, under the roof's front eave.

Inside: a bar of barrels and a plank counter across the back with kegs and rum (cargo barrels) behind it, two tables
with stools, lanterns on the counter and hanging from the ridge, cobwebs in the corners.

ST4b: palm trunk corner posts and stripped door posts, a plinth of cobblestone going mossy at the corners, a board
course of alternating dark and spruce boards (with a salvaged jungle board or two) under the open rail, the gables on
a stripped dark oak tie beam with vertical boards, a king post and vents, rafter ends under both eaves, a lantern
over the doorway, a shuttered window in the closed back wall, a fieldstone chimney with a smoking top at the back,
and gravel and coarse dirt at the hut's foot."""
from _style import (PALETTE, THATCH, chimney, rafter_ends, roof_patches, sand_skirt, shutter_window, stool, table,
                    weatherboard, lantern_post)
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 8, 1, 7     # walls
TOP = 2                         # last wall row; the roof plate is row 3
MID = 4                         # the middle of the side walls (z) and of the front (x)

p = Piece("pirate_island_tavern_hut", "Pirate Tavern Hut", (X1 + 1, 8, Z1 + 2), PALETTE)

# foundation: a cobblestone plinth under the walls, mossy at the corners and here and there, a patched plank floor
p.fill(X0, 0, 0, X1, 0, Z1 + 1, "sand")
p.ring(X0, Z0, X1, Z1, 0, 0, "cobble")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (2, Z0), (6, Z1), (X0, 3), (X1, 5), (4, Z1)):
    p.put(x, 0, z, "mossy")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
for x, z in ((2, 2), (5, 3), (6, 6), (3, 5), (7, 2)):
    p.put(x, 0, z, "dark")
p.put(4, 0, 2, "minecraft:jungle_planks")
sand_skirt(p, X0, Z0, X1, Z1, reach=1, salt=21)

# walls: a board course at the bottom, an open rail above (an open-sided hut), palm trunk corners, stripped posts
p.ring(X0, Z0, X1, Z1, 1, 1, "spruce")
p.ring(X0, Z0, X1, Z1, 2, 2, "fence")
p.fill(X0 + 1, 1, Z1, X1 - 1, 2, Z1, "dark")              # the back wall is closed behind the bar
weatherboard(p, X0, 1, Z0, X1, 1, Z0, salt=1)
weatherboard(p, X0, 1, Z1, X1, 2, Z1, keys=("spruce", "dark"), salt=2)
weatherboard(p, X0, 1, Z0, X0, 1, Z1, salt=3)
weatherboard(p, X1, 1, Z0, X1, 1, Z1, salt=4)
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    p.fill(x, 1, z, x, TOP, z, "palm")
for x, z in ((X0, MID), (X1, MID), (3, Z0), (5, Z0)):
    p.fill(x, 1, z, x, TOP, z, "post")
# the doorway between the two front posts
for y in (1, 2):
    p.clear(MID, y, Z0)

# roof: ridge along x, eaves over the sand in front (z 0) and the back (z 8)
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, THATCH, "thatch_ridge", "dark", plate="beam_x")
roof_patches(p, salt=31)
for z in (Z0, Z1):
    p.put(X0, TOP + 1, z, "palm")
    p.put(X1, TOP + 1, z, "palm")
# the gables: a tie beam along the top of the side wall, vertical boards above with a king post and two vents
for x in (X0, X1):
    p.fill(x, TOP + 1, Z0 + 1, x, TOP + 1, Z1 - 1, "dark_beam_z")
    weatherboard(p, x, TOP + 2, Z0, x, TOP + 4, Z1, keys=("spruce", "dark"), salt=5 + x)
    p.fill(x, TOP + 2, MID, x, TOP + 4, MID, "dark_post")
    p.put(x, TOP + 2, MID - 1, "fence")
    p.put(x, TOP + 2, MID + 1, "fence")
# rafter ends under both eaves (the back ones beside the window's shutters)
rafter_ends(p, [(0, 0), (3, 0), (5, 0), (8, 0)], TOP, "south")
rafter_ends(p, [(0, Z1 + 1), (1, Z1 + 1), (8, Z1 + 1)], TOP, "north")

# the back: a shuttered window behind the bar, a fieldstone chimney with its breast in the wall behind the kegs
shutter_window(p, MID, 2, Z1, "south", wood="dark_oak")
chimney(p, 7, Z1 + 1, 0, 5)
p.put(7, 1, Z1, "mossy")
p.put(7, 2, Z1, "cobble")

# the bar: barrels at the ends, a plank counter between, a gap at the west end to step behind
p.put(3, 1, 4, "barrel")
p.fill(4, 1, 4, 6, 1, 4, "minecraft:spruce_slab[type=top]")
p.put(7, 1, 4, "barrel")
p.put(5, 2, 4, "lantern")
p.put(6, 2, 4, "minecraft:skeleton_skull[powered=false,rotation=8]")
for x, y in ((1, 1), (2, 1), (1, 2)):
    p.put(x, y, 6, "pirates_n_ships:cargo_barrel")
for x in (4, 5, 6, 7):
    p.put(x, 1, 6, "minecraft:barrel[facing=north,open=false]")
p.put(7, 2, 6, "minecraft:barrel[facing=north,open=false]")
p.put(5, 2, 6, "minecraft:barrel[facing=east,open=false]")

# two tables with stools, a hanging lantern from the ridge, one over the doorway
for tx in (2, 6):
    table(p, tx, 1, 2)
    stool(p, tx - 1, 1, 2, "west")
    stool(p, tx + 1, 1, 2, "east")
p.put(4, 6, 4, "lantern_hanging")
p.put(MID, TOP, 0, "lantern_hanging")
p.put(1, 3, 6, "cobweb")
p.put(7, 4, 6, "cobweb")

lantern_post(p, 1, 1, 0)
p.put(7, 1, 0, "barrel")
p.put(7, 2, 0, "minecraft:barrel[facing=west,open=false]")

p.connector(MID, 0, 0, "hut_in", "north", "minecraft:sand")

p.emit("open-sided beach tavern: bar of barrels, tables, lanterns, dark oak thatch roof, boarded gables, chimney",
       ["Faces north: the doorway and the hut_in connector are on the -z side.",
        "y 0 is the foundation row, level with the terrain surface."])
