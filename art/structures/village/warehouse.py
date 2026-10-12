"""Generates the seafarer village's warehouse (11x9 footprint; STR3) and prints the BuildSpec as JSON. The building
connector is on the north (-z) side, in front of the loading way; row z 0 is the apron.

A two-storey store gable-end to the street: a ground storey of cobblestone (mossy at the foot) with stone brick quoins
and barred windows, an upper storey of spruce boards between stripped spruce posts behind a spruce band, a dark
shingle roof with the ridge along z, a loading door in the front gable under a hoist beam with a chain and a crate.
Inside: stacks of cargo crates and barrels, a ladder to the loft floor, sacks of grain (hay), a lantern."""
from _style import DARK, PALETTE, corner_post, jit, lantern_post, window
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 10, 1, 9
FLOOR2, TOP = 4, 7
p = Piece("village_warehouse", "Seafarer Warehouse", (X1 + 1, 15, Z1 + 1), PALETTE)

# floors
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
p.fill(X0 + 1, FLOOR2, Z0 + 1, X1 - 1, FLOOR2, Z1 - 1, "spruce")
# the ground storey: cobblestone going mossy at the foot, stone brick quoins
for y in range(0, FLOOR2):
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if x in (X0, X1) or z in (Z0, Z1):
                mossy = jit(x, y, z, 451) < (600 if y == 0 else 250 if y == 1 else 80)
                p.put(x, y, z, "mossy" if mossy else "cobble")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    for y in range(1, FLOOR2):
        p.put(x, y, z, "bricks" if y % 2 else "bricks_mossy" if y == 1 else "bricks")
# the upper storey: boards between posts, a band at the floor
p.ring(X0, Z0, X1, Z1, FLOOR2, TOP, "boards")
p.fill(X0, FLOOR2, Z0, X1, FLOOR2, Z0, "beam_x")
p.fill(X0, FLOOR2, Z1, X1, FLOOR2, Z1, "beam_x")
p.fill(X0, FLOOR2, Z0 + 1, X0, FLOOR2, Z1 - 1, "beam_z")
p.fill(X1, FLOOR2, Z0 + 1, X1, FLOOR2, Z1 - 1, "beam_z")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (X0, 5), (X1, 5)):
    corner_post(p, x, z, FLOOR2 + 1, TOP)
# the roof: ridge along z, the front gable boarded with a loading door, the back gable boarded
p.roof_ridge_z(Z0, Z1, X0, X1, TOP + 1, DARK, "dark_ridge", gable="boards")
# the loading way: a 3 wide opening with an oak lintel, cobbled apron
for x in (4, 5, 6):
    p.clear(x, 1, Z0)
    p.clear(x, 2, Z0)
    p.put(x, 3, Z0, "frame_x")
    p.put(x, 0, Z0, "cobble")
for x in (3, 7):
    p.fill(x, 1, Z0, x, 2, Z0, "frame_post")
p.fill(3, 0, 0, 7, 0, 0, "cobble")
# the loading door in the front gable, the hoist beam over it with a chain and a crate
p.put(5, TOP + 1, Z0, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]")
p.clear(5, TOP + 2, Z0)
p.put(5, TOP + 3, Z0, "beam_z")
p.put(5, TOP + 3, 0, "beam_z")
p.put(5, TOP + 2, 0, "chain_y")
p.put(5, TOP + 1, 0, "chain_y")
p.put(5, TOP, 0, "crate")
# windows: barred on the ground storey, framed with shutters upstairs
for z in (3, 7):
    for x in (X0, X1):
        p.put(x, 2, z, "minecraft:iron_bars")
    window(p, X0, 6, z, "west", lintel=False)
    window(p, X1, 6, z, "east", lintel=False)
for x in (2, 8):
    window(p, x, 6, Z0, "north")
    p.put(x, 2, Z0, "minecraft:iron_bars")
    window(p, x, 6, Z1, "south", lintel=False)
# inside: stacked cargo, sacks of grain, a ladder to the loft, lanterns
for x, z, h in ((1, 2, 3), (1, 3, 2), (2, 2, 1), (1, 7, 2), (2, 8, 3), (1, 8, 2), (9, 8, 3), (8, 8, 2), (9, 7, 1)):
    for y in range(1, h + 1):
        p.put(x, y, z, "crate" if (x + y + z) % 3 else "cargo_barrel")
for x, z in ((9, 2), (9, 3), (8, 2)):
    p.put(x, 1, z, "cargo_barrel")
    p.put(x, 2, z, "minecraft:barrel[facing=up,open=false]" if z == 2 and x == 9 else "cargo_barrel")
p.put(6, 1, 7, "minecraft:hay_block[axis=y]")
p.put(7, 1, 7, "minecraft:hay_block[axis=x]")
p.put(6, 2, 7, "minecraft:hay_block[axis=y]")
p.fill(4, 1, 8, 4, 3, 8, "minecraft:ladder[facing=north,waterlogged=false]")
p.clear(4, FLOOR2, 8)
p.put(4, FLOOR2, 8, "minecraft:spruce_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]")
p.put(5, 3, 5, "lantern_hanging")
for x, z in ((2, 3), (8, 6), (7, 3), (3, 7)):
    p.put(x, FLOOR2 + 1, z, "crate" if x % 2 else "cargo_barrel")
p.put(5, TOP, 5, "lantern_hanging")
lantern_post(p, 2, 1, 0)
lantern_post(p, 8, 1, 0)

# STR6: a loot chest of household stores by the west wall
p.loot_chest(1, 1, 5, "east", "village/house")
p.connector(5, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("a two-storey warehouse of cobblestone and spruce boards, gable-end to the street, with a hoist beam",
       ["Faces north: the building_in connector is on the -z side in front of the loading way, row z 0 is the apron."])
