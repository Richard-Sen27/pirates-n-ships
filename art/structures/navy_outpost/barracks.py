"""Generates the navy outpost barracks (11x7 footprint, one storey under a spruce gable roof) and prints the BuildSpec
as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep.

Walls of polished andesite on a stone brick plinth, stripped spruce corner posts and a beam course under the eaves.
Inside: bunks along the back wall (blue beds, the outer two pairs doubled up), a sea chest at each bunk pair, a table
with stools in the middle, a weapon rack (a row of fences with pressure plates) and a lantern hanging from the
ridge."""
from _style import PALETTE, bed, connector, door, lantern_post, stool, table
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 10, 1, 7          # walls
TOP = 3                               # last wall row

p = Piece("navy_outpost_barracks", "Navy Outpost Barracks", (X1 + 1, 9, Z1 + 2), PALETTE)

# foundation, floor and walls
p.ring(X0, Z0, X1, Z1, 0, 0, "bricks")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "oak")
p.ring(X0, Z0, X1, Z1, 1, TOP, "andesite")
p.ring(X0, Z0, X1, Z1, 1, 1, "bricks")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    p.fill(x, 1, z, x, TOP, z, "post")

# the roof: spruce shingles, ridge along x, eaves over the doorstep (z 0) and the back (z 8), andesite gables
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, "minecraft:spruce_stairs", "spruce_slab", "andesite", plate="beam_x")

# the door, a doorstep of cobblestone between two lantern posts, a blue panel over the door
door(p, 5, 1, Z0, facing="north")
p.fill(3, 0, 0, 7, 0, 0, "cobble")
p.put(5, TOP, Z0, "wool")
lantern_post(p, 3, 1, 0)
lantern_post(p, 7, 1, 0)

# windows: two each side of the door and in the back wall, one in each gable
for x in (2, 8):
    p.put(x, 2, Z0, "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]")
    p.put(x, 2, Z1, "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]")
for x in (X0, X1):
    p.put(x, 2, 4, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")
    p.put(x, 5, 4, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")

# bunks: beds with the head at the back wall, upper bunks on the outer pairs, a chest between each pair
for x in (1, 3, 7, 9):
    bed(p, x, 1, Z1 - 2, "south")
for x in (1, 9):
    bed(p, x, 2, Z1 - 2, "south")
for x, facing in ((2, "north"), (8, "north")):
    p.put(x, 1, Z1 - 1, f"minecraft:chest[facing={facing},type=single,waterlogged=false]")

# the mess table with stools, the weapon rack along the front wall west of the door
table(p, 5, 1, 4)
stool(p, 4, 1, 4, "west")
stool(p, 6, 1, 4, "east")
for x in (1, 2, 3):
    west = "true" if x > 1 else "false"
    east = "true" if x < 3 else "false"
    p.put(x, 1, Z0 + 1, f"minecraft:spruce_fence[east={east},north=false,south=false,waterlogged=false,west={west}]")
    p.put(x, 2, Z0 + 1, "minecraft:stone_pressure_plate[powered=false]")
p.put(9, 1, Z0 + 1, "minecraft:barrel[facing=up,open=false]")
p.put(8, 1, Z0 + 1, "pirates_n_ships:cargo_crate")
p.put(5, 7, 4, "lantern_hanging")


connector(p, 5, 0, 0, "building_in", "north", "minecraft:cobblestone")

p.emit("andesite barracks under a spruce roof with bunks, sea chests, a mess table and a weapon rack",
       ["Door and building_in [5, 0, 0] on the north side; row z 0 is the doorstep."])
