"""Generates the navy outpost barracks (11x7 footprint, one storey under a spruce gable roof) and prints the BuildSpec
as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep.

Outside: a mossy stone brick plinth, a timber frame of spruce log corner posts, stripped spruce posts and a beam course
under the eaves, polished andesite infill, dark oak door posts under a blue panel; shuttered windows with trapdoor sills
on both long walls; the roof's eaves (a dark oak course) over dark oak rafter ends, two lanterns hanging under the front
eave beside the door; spruce gables with a tie beam and a window; on the back wall a stone chimney breast whose stack
rises past the eave with smoke from its top.

Inside: bunks along the back wall (blue beds, the outer two pairs doubled up), a sea chest at each bunk pair, the
hearth between the bunks with a blue banner over it, a mess table with stools, a weapon rack (a row of fences with
pressure plates), stores and a lantern hanging from the ridge."""
from _style import (PALETTE, banner, bed, chimney, connector, door, rafter_ends, stair_shape, stool, table,
                    window, age)
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 10, 1, 7          # walls
TOP = 3                               # last wall row
HEARTH_X = 5

p = Piece("navy_outpost_barracks", "Navy Outpost Barracks", (X1 + 1, 9, Z1 + 2), PALETTE)

# foundation, floor, plinth and the andesite infill
p.ring(X0, Z0, X1, Z1, 0, 0, "bricks")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
p.ring(X0, Z0, X1, Z1, 1, 1, "bricks")
p.ring(X0, Z0, X1, Z1, 2, TOP, "andesite")
# the timber frame: bark corner posts, stripped posts between the bays, dark oak door posts
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    p.fill(x, 1, z, x, TOP, z, "log")
for z in (Z0, Z1):
    for x in (3, 7):
        p.fill(x, 2, z, x, TOP, z, "post")
for x in (X0, X1):
    for z in (3, 5):
        p.fill(x, 2, z, x, TOP, z, "post")
for x in (4, 6):
    p.fill(x, 1, Z0, x, TOP, Z0, "dark_post")

# the roof: spruce shingles, ridge along x, eaves over the doorstep (z 0) and the back (z 8) in dark oak, spruce gables
# with a tie beam at the eaves and a window
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, "minecraft:spruce_stairs", "spruce_slab", "spruce", plate="beam_x")
for x in range(X0, X1 + 1):
    p.put(x, TOP + 1, Z0 - 1, stair_shape("dark_oak", "south"))
    p.put(x, TOP + 1, Z1 + 1, stair_shape("dark_oak", "north"))
for x in (X0, X1):
    for z in range(Z0 + 1, Z1):
        p.put(x, TOP + 1, z, "beam_z")
    p.put(x, TOP + 2, 4, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")
rafter_ends(p, [(x, Z0 - 1) for x in (1, 3, 7, 9)], TOP, "north")
rafter_ends(p, [(x, Z1 + 1) for x in (1, 3, 7, 9)], TOP, "south")

# the door, a doorstep of cobblestone, a blue panel over the door, lanterns hanging under the eave beside it
door(p, 5, 1, Z0, facing="north")
p.fill(3, 0, 0, 7, 0, 0, "cobble")
p.put(5, TOP, Z0, "wool")
p.put(4, TOP, 0, "lantern_hanging")
p.put(6, TOP, 0, "lantern_hanging")

# windows with shutters and sills: two each side of the door, two in the back wall, one in each gable
for x in (2, 8):
    window(p, x, 2, Z0, "north")
    window(p, x, 2, Z1, "south")
for x in (X0, X1):
    p.put(x, 2, 4, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")

# the chimney: a breast on the back wall, the stack rising past the eave with smoke; the hearth inside
p.fill(HEARTH_X - 1, 0, Z1 + 1, HEARTH_X + 1, TOP, Z1 + 1, "bricks")
p.put(HEARTH_X - 1, TOP + 1, Z1 + 1, stair_shape("stone_brick", "east"))
p.put(HEARTH_X + 1, TOP + 1, Z1 + 1, stair_shape("stone_brick", "west"))
p.put(HEARTH_X, 1, Z1 + 1, "chiseled")
chimney(p, HEARTH_X, Z1 + 1, TOP + 1, 8)
p.fill(HEARTH_X - 1, 1, Z1, HEARTH_X + 1, TOP, Z1, "bricks")
p.put(HEARTH_X, 1, Z1, "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")
p.put(HEARTH_X, 2, Z1, "chiseled")
banner(p, HEARTH_X, TOP, Z1 - 1, "north")

# bunks: beds with the head at the back wall, upper bunks on the outer pairs, a chest between each pair
for x in (1, 3, 7, 9):
    bed(p, x, 1, Z1 - 2, "south")
for x in (1, 9):
    bed(p, x, 2, Z1 - 2, "south")
for x, facing in ((2, "north"), (8, "north")):
    p.put(x, 1, Z1 - 1, f"minecraft:chest[facing={facing},type=single,waterlogged=false]")

# the mess table with stools, the weapon rack along the front wall west of the door, stores, a runner to the door
table(p, 5, 1, 4)
stool(p, 4, 1, 4, "west")
stool(p, 6, 1, 4, "east")
for x in (1, 2, 3):
    west = "true" if x > 1 else "false"
    east = "true" if x < 3 else "false"
    p.put(x, 1, Z0 + 1, f"minecraft:spruce_fence[east={east},north=false,south=false,waterlogged=false,west={west}]")
    p.put(x, 2, Z0 + 1, "minecraft:stone_pressure_plate[powered=false]")
p.put(9, 1, Z0 + 1, "minecraft:barrel[facing=up,open=false]")
p.put(9, 2, Z0 + 1, "lantern")
p.put(8, 1, Z0 + 1, "pirates_n_ships:cargo_crate")
p.put(5, 1, Z0 + 1, "carpet")
p.put(5, 7, 4, "lantern_hanging")

age(p, where=lambda x, y, z: not (X0 < x < X1 and Z0 < z < Z1))

connector(p, 5, 0, 0, "building_in", "north", "minecraft:cobblestone")

p.emit("timber-framed barracks on a stone plinth under a spruce roof, with a chimney, bunks, a hearth and a mess table",
       ["Door and building_in [5, 0, 0] on the north side; row z 0 is the doorstep."])
