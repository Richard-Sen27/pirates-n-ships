"""Generates the navy outpost brig (a 9x8 stone lock-up with a flat roof) and prints the BuildSpec as JSON. The door
and the building connector are on the north (-z) side; row z 0 is the doorstep.

Inside, a guard room across the front (the guard's table with a stool, a chest and a barrel, a hanging lantern) and
two cells along the back, divided by a stone wall. Each cell is fronted by a row of brig bars with a brig door in the
middle (both halves closed and unlocked: a generated door has no owner, see BrigDoorBlock), straw bedding and a
barred window in the back wall. The roof is flat stone behind a low parapet with lanterns on the front corners."""
from _style import PALETTE, brig_door, connector, door, lantern_post, stool, table
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 8, 1, 8           # walls
TOP = 3                               # last wall row
ROOF = TOP + 1
CELL_Z = 4                            # the row of bars fronting the cells (cells on z 5..7)
BARS = "pirates_n_ships:brig_bars[east=true,north=false,south=false,waterlogged=false,west=true]"
IRON = "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]"

p = Piece("navy_outpost_brig", "Navy Outpost Brig", (X1 + 1, 8, Z1 + 1), PALETTE)

# foundation, floor, walls with a chiseled plinth and an andesite band, flat roof behind a parapet
p.fill(X0, 0, Z0, X1, 0, Z1, "bricks")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, CELL_Z - 1, "andesite")
p.ring(X0, Z0, X1, Z1, 1, TOP, "bricks")
p.ring(X0, Z0, X1, Z1, 1, 1, "chiseled")
p.ring(X0, Z0, X1, Z1, TOP, TOP, "andesite")
p.fill(X0, ROOF, Z0, X1, ROOF, Z1, "bricks")
p.ring(X0, Z0, X1, Z1, ROOF + 1, ROOF + 1, "brick_slab")
for x in (X0, X1):
    p.put(x, ROOF + 1, Z0, "bricks")
    p.put(x, ROOF + 2, Z0, "lantern")
for x, y, z, key in ((2, 2, Z1, "bricks_mossy"), (X1, 2, 5, "bricks_cracked"), (X0, 2, 3, "bricks_mossy")):
    p.put(x, y, z, key)

# the door, the doorstep, a blue panel over the door, barred windows in front
door(p, 4, 1, Z0, facing="north")
p.fill(3, 0, 0, 5, 0, 0, "cobble")
p.put(4, TOP, Z0, "wool")
for x in (2, 6):
    p.put(x, 2, Z0, IRON)
lantern_post(p, 2, 1, 0, height=1)
lantern_post(p, 6, 1, 0, height=1)

# the cells: a divider wall, bars with a brig door in each front, bedding and a barred window at the back
p.fill(4, 1, CELL_Z, 4, TOP, Z1, "bricks")
for x0 in (1, 5):
    for x in range(x0, x0 + 3):
        for y in range(1, TOP + 1):
            p.put(x, y, CELL_Z, BARS)
    brig_door(p, x0 + 1, 1, CELL_Z, facing="south")
    p.put(x0 + 1, 2, Z1, IRON)
    p.put(x0, 1, Z1 - 1, "minecraft:hay_block[axis=x]")
    p.put(x0 + 2, 1, Z1 - 1, "minecraft:white_carpet")
    p.put(x0 + 2, 1, CELL_Z + 1, "minecraft:cauldron")

# the guard room: table and stool, chest, barrel with the keys' lantern, a lantern from the ceiling
table(p, 2, 1, 2)
stool(p, 1, 1, 2, "west")
p.put(7, 1, 2, "minecraft:chest[facing=west,type=single,waterlogged=false]")
p.put(6, 1, 2, "minecraft:barrel[facing=up,open=false]")
p.put(6, 2, 2, "lantern")
p.put(4, TOP, 3, "lantern_hanging")

connector(p, 4, 0, 0, "building_in", "north", "minecraft:cobblestone")

p.emit("stone brig with a guard room and two cells of brig bars with brig doors",
       ["Door and building_in [4, 0, 0] on the north side; row z 0 is the doorstep.",
        "Cell fronts on z 4: brig bars with a brig door (closed, unlocked) at x 2 and x 6."])
