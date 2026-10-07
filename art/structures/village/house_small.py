"""Generates the seafarer village's small house (7x7 footprint, one floor under a red tile roof) and prints the
BuildSpec as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep.

Look (ST4a): a mossy cobblestone plinth, white render between stripped spruce corner and mid posts, framed windows
with shutters and sills, a framed door under a hood, a spruce wall plate with rafter ends under both eaves, boarded
gables with a vent, a brick chimney on the east gable over the hearth."""
from _style import (PALETTE, TILE, bed, chimney, corner_post, door_frame, hearth, plinth, rafter_ends, table,
                    trim_band, window)
from buildspec import Piece

W, D = 7, 7                 # walls x 0..6, z 1..7
Z0, Z1 = 1, D               # wall rows
X0, X1 = 0, W - 1
TOP = 3                     # last wall row (walls y 1..3)

p = Piece("village_house_small", "Seafarer House (small)", (W, 9, D + 2), PALETTE)

# floor; walls: white render between stripped spruce corner posts
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
p.ring(X0, Z0, X1, Z1, 1, TOP, "render")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    corner_post(p, x, z, 1, TOP)
# the stone plinth (foundation row and the first wall course), darker towards the ground
plinth(p, X0, Z0, X1, Z1, y=1)

# roof: ridge along x, eaves over the doorstep (z 0) and the back (z 8); render gables over a spruce band
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, TILE, "tile_ridge", "render", plate="beam_x")
trim_band(p, X0, Z0, X1, Z1, TOP + 1)
p.put(X0, TOP + 3, 4, "pane")                    # attic lights in the gables
p.put(X1, TOP + 3, 4, "pane")
rafter_ends(p, [(x, TOP, 0) for x in (0, 2, 4, 6)], "north")
rafter_ends(p, [(x, TOP, Z1 + 1) for x in (0, 3, 6)], "south")

# the framed door with a cobblestone step (the connector becomes that step)
door_frame(p, 3, 1, Z0, "north", posts=False)      # the shutters either side frame it
p.connector(3, 0, 0, "building_in", "north", "minecraft:cobblestone")

# windows: either side of the door, both gable walls, the back
for x in (1, 5):
    window(p, x, 2, Z0, "north", lintel=False, shutters=False)   # the plate above is the lintel; no room for shutters
for z in (3, 5):
    window(p, X0, 2, z, "west", lintel=False)
    window(p, X1, 2, z, "east", lintel=False)
window(p, 3, 2, Z1, "south", lintel=False)

# the chimney on the east gable, the hearth at its foot
chimney(p, X1, 2, 1, 7)
hearth(p, X1 - 1, 1, 2, "west")

# inside: a bed in the back corner, a table with a stool and a lantern, a barrel and a chest, a crafting table with a
# flower pot, a rug
bed(p, 5, 1, 5, "south")
table(p, 1, 1, 4)
p.put(2, 1, 4, "minecraft:spruce_stairs[facing=east,half=bottom]")
p.put(1, 1, 6, "minecraft:barrel[facing=up]")
p.put(1, 2, 6, "lantern")
p.put(3, 1, 6, "minecraft:chest[facing=north]")
p.put(1, 1, 2, "minecraft:crafting_table")
p.put(1, 2, 2, "minecraft:flower_pot")
p.put(4, 1, 6, "minecraft:bookshelf")
p.put(3, 1, 4, "minecraft:red_carpet")
p.fill(X0 + 1, TOP + 1, 4, X1 - 1, TOP + 1, 4, "beam_x")     # a tie beam carrying the lantern
p.put(3, TOP, 4, "lantern_hanging")

p.emit("Caribbean colonial cottage: white render on stripped spruce posts, mossy plinth, red tile roof, brick chimney",
       ["Faces north: the door and the building_in connector are on the -z side.",
        "y 0 is the foundation row (plinth and floor), level with the terrain surface."])
