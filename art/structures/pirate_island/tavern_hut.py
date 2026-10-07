"""Generates the pirate island's tavern hut (9x7 footprint, an open-sided beach bar under a palm-thatch look roof of
dark oak) and prints the BuildSpec as JSON. The doorway and the hut connector are on the north (-z) side; row z 0 is
the sand in front, under the roof's front eave.

Inside: a bar of barrels and a plank counter across the back with kegs and rum (cargo barrels) behind it, two tables
with stools, lanterns on the counter and hanging from the ridge, cobwebs in the corners."""
from _style import PALETTE, THATCH, stool, table, torch_post
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 8, 1, 7     # walls
TOP = 2                         # last wall row; the roof plate is row 3

p = Piece("pirate_island_tavern_hut", "Pirate Tavern Hut", (X1 + 1, 8, Z1 + 2), PALETTE)

# foundation: a mossy cobblestone plinth under the walls, a patched plank floor
p.fill(X0, 0, 0, X1, 0, Z1 + 1, "sand")
p.ring(X0, Z0, X1, Z1, 0, 0, "mossy")
for x in range(X0 + 1, X1):
    p.put(x, 0, Z0, "cobble" if x % 3 else "mossy")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
for x, z in ((2, 2), (5, 3), (6, 6), (3, 5), (7, 2)):
    p.put(x, 0, z, "dark")

# walls: a plank course at the bottom, an open rail above (an open-sided hut), stripped log posts
p.ring(X0, Z0, X1, Z1, 1, 1, "spruce")
p.ring(X0, Z0, X1, Z1, 2, 2, "fence")
p.fill(X0 + 1, 1, Z1, X1 - 1, 2, Z1, "dark")              # the back wall is closed behind the bar
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (X0, 4), (X1, 4), (3, Z0), (5, Z0)):
    p.fill(x, 1, z, x, TOP, z, "post")
# the doorway between the two front posts
for y in (1, 2):
    p.clear(4, y, Z0)

# roof: ridge along x, eaves over the sand in front (z 0) and the back (z 8), dark oak gables
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, THATCH, "thatch_ridge", "dark", plate="beam_x")
for z in (Z0, Z1):
    p.put(X0, TOP + 1, z, "post")
    p.put(X1, TOP + 1, z, "post")

# the bar: barrels at the ends, a plank counter between, a gap at the west end to step behind
p.put(3, 1, 4, "barrel")
p.fill(4, 1, 4, 6, 1, 4, "minecraft:spruce_slab[type=top,waterlogged=false]")
p.put(7, 1, 4, "barrel")
p.put(5, 2, 4, "lantern")
for x, y in ((1, 1), (2, 1), (1, 2)):
    p.put(x, y, 6, "pirates_n_ships:cargo_barrel")
for x in (4, 5, 6, 7):
    p.put(x, 1, 6, "minecraft:barrel[facing=north,open=false]")
p.put(7, 2, 6, "minecraft:barrel[facing=north,open=false]")

# two tables with stools, a hanging lantern from the ridge
for tx in (2, 6):
    table(p, tx, 1, 2)
    stool(p, tx - 1, 1, 2, "west")
    stool(p, tx + 1, 1, 2, "east")
p.put(4, 6, 4, "lantern_hanging")
p.put(1, 3, 6, "cobweb")
p.put(7, 4, 6, "cobweb")

torch_post(p, 2, 1, 0)
torch_post(p, 6, 1, 0)

p.connector(4, 0, 0, "hut_in", "north", "minecraft:sand")

p.emit("open-sided beach tavern: bar of barrels, tables, lanterns, dark oak thatch roof",
       ["Faces north: the doorway and the hut_in connector are on the -z side.",
        "y 0 is the foundation row, level with the terrain surface."])
