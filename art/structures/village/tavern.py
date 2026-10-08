"""Generates the seafarer village tavern (11x9 footprint, two floors under a red tile roof) and prints the BuildSpec
as JSON. The door and the building connector are on the north (-z) side; row z 0 is the porch.

Ground floor: the bar along the back with barrels behind it, two tables with stools, a hanging lantern, a hearth under
the chimney, a staircase along the west wall. Upper floor: two beds, a table, a french door onto the gallery rail over
the porch. Above the door a dark oak panel is left for a sign.

Look (ST4a): a mossy cobblestone plinth, white render between stripped spruce corner and mid posts and the stripped oak
door posts, a spruce band at the upper floor, framed windows (tall on the ground floor) with shutters and sills, a
gallery rail on brackets over the porch, rafter ends under both eaves, boarded gables over a spruce band, a brick
chimney on the east gable."""
from _style import (PALETTE, TILE, bed, chimney, corner_post, door, door_frame, hearth, lantern_post, plinth,
                    rafter_ends, table, trim_band, window)
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 10, 1, 9          # walls
FLOOR2 = 5                            # the upper floor's boards (ground floor walls y 1..4)
TOP = 8                               # last wall row of the upper floor

p = Piece("village_tavern", "Seafarer Tavern", (X1 + 1, 15, Z1 + 2), PALETTE)

# floors
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "oak")
p.fill(X0 + 1, FLOOR2, Z0 + 1, X1 - 1, FLOOR2, Z1 - 1, "spruce")

# walls: render on both floors between stripped spruce posts (corners, the middle of the back and the side walls)
# and the stripped oak door posts; the stone plinth below, a spruce band at the upper floor
p.ring(X0, Z0, X1, Z1, 1, TOP, "render")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (X0, 5), (X1, 5), (5, Z1)):
    corner_post(p, x, z, 1, TOP)
for x in (4, 6):
    corner_post(p, x, Z0, 1, TOP, "oak_post")     # the door posts, part of the oak door frame
plinth(p, X0, Z0, X1, Z1, y=1, keep=("post", "oak_post"))
trim_band(p, X0, Z0, X1, Z1, FLOOR2)

# roof: ridge along x, eaves over the porch (z 0) and the back (z 10); boarded gables over a spruce band
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, TILE, "tile_ridge", "boards", plate="beam_x")
trim_band(p, X0, Z0, X1, Z1, TOP + 1, only=("boards",))
for x in (X0, X1):
    p.put(x, TOP + 2, 4, "pane")
    p.put(x, TOP + 2, 6, "pane")
rafter_ends(p, [(x, TOP, 0) for x in (0, 2, 8, 10)], "north")
rafter_ends(p, [(x, TOP, Z1 + 1) for x in (0, 2, 4, 6, 8, 10)], "south")

# the door, the porch, the sign panel above the door, a lantern either side of the way in
door_frame(p, 5, 1, Z0, "north", hood=False, posts=False)
p.fill(5, 3, Z0, 5, 4, Z0, "dark")
p.fill(3, 0, 0, 7, 0, 0, "cobble")
lantern_post(p, 4, 1, 0)
lantern_post(p, 6, 1, 0)

# a gallery rail over the porch (the box leaves one row, so it is a rail on a ledge, not a walk-out balcony): a slab
# ledge on upside-down stair brackets, a fence rail, a french door behind it from the upper floor
p.fill(1, FLOOR2, 0, 9, FLOOR2, 0, "minecraft:spruce_slab[type=top]")
rafter_ends(p, [(x, FLOOR2 - 1, 0) for x in (1, 3, 7, 9)], "north")
p.fill(1, FLOOR2 + 1, 0, 9, FLOOR2 + 1, 0, "fence")
door(p, 5, FLOOR2 + 1, Z0)

# windows: tall ones on the ground floor, single ones upstairs; shutters and sills where the box leaves room
for x in (2, 8):
    window(p, x, 2, Z0, "north", height=2)
    window(p, x, 7, Z0, "north", sill=False, lintel=False)
    window(p, x, 2, Z1, "south", height=2)
    window(p, x, 7, Z1, "south", lintel=False)
window(p, X0, 2, 3, "west", height=2)
window(p, X0, 2, 7, "west", height=2)
window(p, X1, 2, 7, "east", height=2)
for z in (3, 7):
    window(p, X0, 7, z, "west", lintel=False)
window(p, X1, 7, 7, "east", lintel=False)

# the chimney on the east gable, the hearth at its foot
chimney(p, X1, 3, 1, 13)
hearth(p, X1 - 1, 1, 3, "west")

# the bar: a counter of stripped spruce across the back with barrels and kegs behind it
p.fill(3, 1, 6, 9, 1, 6, "beam_x")
for x in range(4, 10):
    p.put(x, 1, 8, "minecraft:barrel[facing=north]")
for x in (4, 6, 8):
    p.put(x, 2, 8, "minecraft:barrel[facing=north]")
p.put(9, 2, 8, "pirates_n_ships:cargo_barrel")
p.put(5, 2, 8, "pirates_n_ships:cargo_barrel")
p.put(7, 2, 8, "lantern")
for x in (5, 8):
    p.put(x, 1, 5, "minecraft:spruce_stairs[facing=north,half=bottom]")
p.put(3, 2, 6, "minecraft:flower_pot")

# tables with stools in the front room, a hanging lantern from a tie beam, a barrel by the door
for x in (3, 7):
    table(p, x, 1, 3)
    p.put(x - 1, 1, 3, "minecraft:spruce_stairs[facing=west,half=bottom]")
    p.put(x + 1, 1, 3, "minecraft:spruce_stairs[facing=east,half=bottom]")
p.put(5, FLOOR2 - 1, 4, "lantern_hanging")
p.put(9, 1, 2, "minecraft:barrel[facing=up]")
p.put(1, 1, 2, "pirates_n_ships:cargo_crate")

# stairs up along the west wall (rising north), a hole in the upper floor above them, a rail beside the hole
for i, z in enumerate(range(Z1 - 1, Z1 - 6, -1)):
    p.put(1, 1 + i, z, "minecraft:spruce_stairs[facing=north,half=bottom]")
for z in range(Z1 - 4, Z1):
    p.clear(1, FLOOR2, z)
p.put(1, FLOOR2, Z1 - 5, "minecraft:spruce_stairs[facing=north,half=bottom]")
for z in range(Z1 - 4, Z1):
    p.put(2, FLOOR2 + 1, z, "fence")

# upper floor: two beds against the east wall, a table, a chest, a lantern, a barrel
bed(p, 9, FLOOR2 + 1, 3, "north")
bed(p, 9, FLOOR2 + 1, 7, "south")
table(p, 6, FLOOR2 + 1, 5)
p.put(5, FLOOR2 + 1, 5, "minecraft:spruce_stairs[facing=west,half=bottom]")
p.put(9, FLOOR2 + 1, 5, "minecraft:chest[facing=west]")
p.put(9, FLOOR2 + 2, 5, "lantern")
p.put(4, FLOOR2 + 1, 2, "minecraft:barrel[facing=up]")
p.put(6, FLOOR2 + 1, 8, "minecraft:red_carpet")
p.put(7, FLOOR2 + 1, 8, "minecraft:red_carpet")
p.fill(X0 + 1, TOP + 1, 5, X1 - 1, TOP + 1, 5, "beam_x")          # a tie beam under the ridge
p.put(6, TOP, 5, "lantern_hanging")

p.connector(5, 0, 0, "building_in", "north", "minecraft:cobblestone")

p.emit("two-storey colonial tavern: white render on spruce and oak posts, mossy plinth, gallery, red tile roof",
       ["Faces north: the door and the building_in connector are on the -z side, row z 0 is the porch.",
        "The dark oak panel above the door (x 5, y 3..4) is left for a sign."])
