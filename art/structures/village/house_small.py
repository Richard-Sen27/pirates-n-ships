"""Generates the seafarer village's small house (7x7 footprint, one floor under a red tile roof) and prints the
BuildSpec as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep."""
from _style import PALETTE, TILE, bed, door, table
from buildspec import Piece

W, D = 7, 7                 # walls x 0..6, z 1..7
Z0, Z1 = 1, D               # wall rows
X0, X1 = 0, W - 1
TOP = 3                     # last wall row (walls y 1..3)

p = Piece("village_house_small", "Seafarer House (small)", (W, 9, D + 2), PALETTE)

# foundation: a cobblestone plinth under the walls, a plank floor inside
p.ring(X0, Z0, X1, Z1, 0, 0, "cobble")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")

# walls: white render between stripped spruce corner posts
p.ring(X0, Z0, X1, Z1, 1, TOP, "render")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    p.fill(x, 1, z, x, TOP, z, "post")

# roof: ridge along x, eaves over the doorstep (z 0) and the back (z 8), gables of render
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, TILE, "tile_ridge", "render", plate="beam_x")

# door with a cobblestone step (the connector becomes that step)
door(p, 3, 1, Z0)
p.connector(3, 0, 0, "building_in", "north", "minecraft:cobblestone")

# windows: either side of the door, both gable walls, the back
for x in (1, 5):
    p.put(x, 2, Z0, "pane")
for z in (3, 5):
    p.put(X0, 2, z, "pane")
    p.put(X1, 2, z, "pane")
p.put(3, 2, Z1, "pane")

# inside: a bed in the back corner, a table with a stool and a lantern, a barrel and a chest
bed(p, 5, 1, 5, "south")
table(p, 1, 1, 4)
p.put(2, 1, 4, "minecraft:spruce_stairs[facing=east,half=bottom]")
p.put(1, 1, 6, "minecraft:barrel[facing=up]")
p.put(1, 2, 6, "lantern")
p.put(3, 1, 6, "minecraft:chest[facing=north]")
p.put(5, 2, 3, "minecraft:flower_pot")
p.put(5, 1, 3, "minecraft:crafting_table")

p.emit("Caribbean colonial cottage: white render, stripped spruce posts, red tile roof",
       ["Faces north: the door and the building_in connector are on the -z side.",
        "y 0 is the foundation row (plinth and floor), level with the terrain surface."])
