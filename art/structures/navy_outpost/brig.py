"""Generates the navy outpost brig (a 9x8 stone lock-up with a flat roof) and prints the BuildSpec as JSON. The door
and the building connector are on the north (-z) side; row z 0 is the doorstep.

Outside: the fort's masonry (_style.fort_face) on the sides and the back, pilasters at the corners and where the
inner walls meet the outer ones, recessed bays on a sloped mossy plinth under corbels, the cells' barred windows in the
recesses; a parapet with slab-capped merlons round the flat roof, lanterns on the front corners. The front has corner
buttresses, barred windows and a stone portal round the door (chiseled keystone, slab hood) under the navy's blue,
lantern posts either side.

Inside, a guard room across the front (the guard's table with a stool, a chest and a barrel, a lantern, the keys' hooks
of chain) and two cells along the back, divided by a stone wall. Each cell is fronted by a row of brig bars with a brig
door in the middle (both halves closed and unlocked: a generated door has no owner, see BrigDoorBlock), straw bedding,
a cauldron, shackle chains from the ceiling and a barred window in the back wall."""
from _style import (dry, PALETTE, age, bars, brig_door, buttress, chain, connector, door, fort_face, lantern_post, merlons,
                    stool, table)
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 8, 1, 8           # walls
TOP = 3                               # last wall row
ROOF = TOP + 1
CELL_Z = 4                            # the row of bars fronting the cells (cells on z 5..7)
BARS = "pirates_n_ships:brig_bars[east=true,north=false,south=false,waterlogged=false,west=true]"
IRON = bars("x")

p = Piece("navy_outpost_brig", "Navy Outpost Brig", (X1 + 1, 8, Z1 + 1), PALETTE)

# foundation, floor, the front wall with a chiseled plinth and an andesite band, flat roof
p.fill(X0, 0, Z0, X1, 0, Z1, "bricks")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, CELL_Z - 1, "andesite")
p.ring(X0, Z0, X1, Z1, 1, TOP, "bricks")
p.fill(X0, 1, Z0, X1, 1, Z0, "chiseled")
p.fill(X0, TOP, Z0, X1, TOP, Z0, "andesite")
p.fill(X0, ROOF, Z0, X1, ROOF, Z1, "bricks")
p.fill(X0, ROOF, Z0, X1, ROOF, Z0, "andesite")

# sides and back: pilasters at the corners and the inner walls, recessed bays, the inner skin behind the recesses
fort_face(p, [(X0, z) for z in range(Z0 + 1, Z1 + 1)], "east", pilasters={(X0, CELL_Z), (X0, Z1)}, corbel=TOP)
fort_face(p, [(X1, z) for z in range(Z0 + 1, Z1 + 1)], "west", pilasters={(X1, CELL_Z), (X1, Z1)}, corbel=TOP)
fort_face(p, [(x, Z1) for x in range(X0, X1 + 1)], "north", pilasters={(X0, Z1), (4, Z1), (X1, Z1)}, corbel=TOP)
for z in range(Z0 + 1, Z1):
    if z != CELL_Z:
        p.put(X0 + 1, 2, z, "bricks")
        p.put(X1 - 1, 2, z, "bricks")
for x in range(X0 + 1, X1):
    if x != 4:
        p.put(x, 2, Z1 - 1, "bricks")

# the parapet: slab-capped merlons round the roof, lanterns on the front corners
ring = sorted({(x, Z0) for x in range(X0, X1 + 1)} | {(x, Z1) for x in range(X0, X1 + 1)}
              | {(X0, z) for z in range(Z0, Z1 + 1)} | {(X1, z) for z in range(Z0, Z1 + 1)})
p_cells = [(x, z) for x, z in ring if (x + z) % 2 == 1 and (x, z) not in ((X0, Z0), (X1, Z0))]
for x, z in ring:
    p.put(x, ROOF + 1, z, "brick_slab")
merlons(p, p_cells, ROOF + 1)
for x in (X0, X1):
    p.put(x, ROOF + 1, Z0, "bricks")
    p.put(x, ROOF + 2, Z0, "lantern")

# the front: corner buttresses, the door in a stone portal under a blue panel, barred windows, lantern posts
for x in (X0, X1):
    buttress(p, x, 0, TOP, "north")
door(p, 4, 1, Z0, facing="north")
p.fill(3, 0, 0, 5, 0, 0, "cobble")
for x in (3, 5):
    p.fill(x, 1, 0, x, 2, 0, "bricks")
    p.put(x, 1, 0, "chiseled")
p.put(3, TOP, 0, "minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]")
p.put(5, TOP, 0, "minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]")
p.put(4, TOP, 0, "chiseled")
p.fill(3, ROOF, 0, 5, ROOF, 0, "brick_slab")
p.put(4, TOP, Z0, "wool")
for x in (2, 6):
    p.put(x, 2, Z0, IRON)
lantern_post(p, 1, 1, 0, height=1)
lantern_post(p, 7, 1, 0, height=1)

# the cells: a divider wall, bars with a brig door in each front, bedding, a cauldron, shackles, a barred window
p.fill(4, 1, CELL_Z, 4, TOP, Z1, "bricks")
for x0 in (1, 5):
    for x in range(x0, x0 + 3):
        for y in range(1, TOP + 1):
            p.put(x, y, CELL_Z, BARS)
    brig_door(p, x0 + 1, 1, CELL_Z, facing="south")
    p.put(x0 + 1, 2, Z1 - 1, IRON)
    p.put(x0, 1, Z1 - 1, "minecraft:hay_block[axis=x]")
    p.put(x0 + 2, 1, Z1 - 1, "minecraft:white_carpet")
    p.put(x0 + 2, 1, CELL_Z + 1, "minecraft:cauldron")
    chain(p, 3 if x0 == 1 else 5, 2, TOP, CELL_Z + 2)        # shackles on the divider wall

# the guard room: table and stool, chest, barrel with the keys' lantern, key hooks of chain, a lantern from the ceiling
table(p, 2, 1, 2)
stool(p, 1, 1, 2, "west")
p.put(7, 1, 2, "minecraft:chest[facing=west,type=single,waterlogged=false]")
p.put(6, 1, 2, "minecraft:barrel[facing=up,open=false]")
p.put(6, 2, 2, "lantern")
p.put(7, 1, 3, "pirates_n_ships:cargo_crate")
chain(p, 1, TOP, TOP, 3)
p.put(4, TOP, 3, "lantern_hanging")

age(p, where=lambda x, y, z: not (X0 < x < X1 and Z0 < z < Z1 and y == 0))

connector(p, 4, 0, 0, "building_in", "north", "minecraft:cobblestone")

dry(p)
p.emit("stone brig with buttresses, recessed bays and a crenellated roof, a guard room and two cells of brig bars",
       ["Door and building_in [4, 0, 0] on the north side; row z 0 is the doorstep.",
        "Cell fronts on z 4: brig bars with a brig door (closed, unlocked) at x 2 and x 6."])
