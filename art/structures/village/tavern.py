"""Generates the seafarer village tavern (11x9 footprint, two floors under a red tile roof) and prints the BuildSpec
as JSON. The door and the building connector are on the north (-z) side; row z 0 is the porch.

Ground floor: the bar along the back with barrels behind it, two tables with stools, a hanging lantern, a staircase
along the west wall. Upper floor: two beds and a table. Above the door a dark oak panel is left for a sign."""
from _style import PALETTE, TILE, bed, door, lantern_post, table
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 10, 1, 9          # walls
FLOOR2 = 5                            # the upper floor's boards (ground floor walls y 1..4)
TOP = 8                               # last wall row of the upper floor

p = Piece("village_tavern", "Seafarer Tavern", (X1 + 1, 15, Z1 + 2), PALETTE)

# foundation and floors
p.ring(X0, Z0, X1, Z1, 0, 0, "cobble")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "oak")
p.fill(X0 + 1, FLOOR2, Z0 + 1, X1 - 1, FLOOR2, Z1 - 1, "spruce")

# walls: render on both floors, stripped oak posts at the corners and beside the door, a beam course at the floor
p.ring(X0, Z0, X1, Z1, 1, TOP, "render")
p.ring(X0, Z0, X1, Z1, FLOOR2, FLOOR2, "oak_beam_x")
for z in (Z0, Z1):
    p.put(X0, FLOOR2, z, "oak_post")
    p.put(X1, FLOOR2, z, "oak_post")
for z in range(Z0 + 1, Z1):
    p.put(X0, FLOOR2, z, "oak_beam_z")
    p.put(X1, FLOOR2, z, "oak_beam_z")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (4, Z0), (6, Z0)):
    p.fill(x, 1, z, x, TOP, z, "oak_post")

# roof: ridge along x, eaves over the porch (z 0) and the back (z 10)
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, TILE, "tile_ridge", "render", plate="oak_beam_x")

# door, porch and the sign panel above the door
door(p, 5, 1, Z0)
p.fill(3, 0, 0, 7, 0, 0, "cobble")
p.connector(5, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.fill(5, 3, Z0, 5, 4, Z0, "dark")
lantern_post(p, 3, 1, 0)
lantern_post(p, 7, 1, 0)

# windows: two high on each floor in front and back, one per floor on the gable walls
for x in (2, 8):
    p.fill(x, 2, Z0, x, 3, Z0, "pane")
    p.fill(x, 2, Z1, x, 3, Z1, "pane")
for x in (2, 5, 8):
    p.fill(x, 7, Z0, x, 7, Z0, "pane")
    p.fill(x, 7, Z1, x, 7, Z1, "pane")
for z in (3, 7):
    p.fill(X1, 2, z, X1, 3, z, "pane")
p.fill(X0, 2, 3, X0, 3, 3, "pane")
for z in (3, 6):
    p.put(X0, 7, z, "pane")
    p.put(X1, 7, z, "pane")

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

# tables with stools in the front room, a hanging lantern from the upper floor
for x in (3, 7):
    table(p, x, 1, 3)
    p.put(x - 1, 1, 3, "minecraft:spruce_stairs[facing=west,half=bottom]")
    p.put(x + 1, 1, 3, "minecraft:spruce_stairs[facing=east,half=bottom]")
p.put(5, FLOOR2 - 1, 4, "lantern_hanging")
p.put(9, 1, 2, "minecraft:barrel[facing=up]")

# stairs up along the west wall (rising north), a hole in the upper floor above them, a rail beside the hole
for i, z in enumerate(range(Z1 - 1, Z1 - 6, -1)):
    p.put(1, 1 + i, z, "minecraft:spruce_stairs[facing=north,half=bottom]")
for z in range(Z1 - 4, Z1):
    p.clear(1, FLOOR2, z)
p.put(1, FLOOR2, Z1 - 5, "minecraft:spruce_stairs[facing=north,half=bottom]")
for z in range(Z1 - 4, Z1):
    p.put(2, FLOOR2 + 1, z, "fence")

# upper floor: two beds against the east wall, a table, a chest, a lantern
bed(p, 9, FLOOR2 + 1, 3, "north")
bed(p, 9, FLOOR2 + 1, 7, "south")
table(p, 6, FLOOR2 + 1, 5)
p.put(5, FLOOR2 + 1, 5, "minecraft:spruce_stairs[facing=west,half=bottom]")
p.put(9, FLOOR2 + 1, 5, "minecraft:chest[facing=west]")
p.put(9, FLOOR2 + 2, 5, "lantern")
p.put(4, FLOOR2 + 1, 2, "minecraft:barrel[facing=up]")

p.emit("two-storey colonial tavern: white render on stripped oak, red tile roof, a bar with barrels",
       ["Faces north: the door and the building_in connector are on the -z side, row z 0 is the porch.",
        "The dark oak panel above the door (x 5, y 3..4) is left for a sign."])
