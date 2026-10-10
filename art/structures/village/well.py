"""Generates the seafarer village's well (7x7; STR3, a building) and prints the BuildSpec as JSON. The building
connector is on the north (-z) side; row z 0 is where the villagers come to draw water.

A stone brick curb round the water, a low stone brick wall, two posts carrying a little gabled spruce roof with a
lantern on a chain, on a small cobbled yard that frays into the ground; a bench, a trough (a cauldron of water), a
barrel of buckets, a lantern post and a flower bed by the yard's back edge."""
from _style import PALETTE, bench, flower, jit, lantern_post, stairs_state
from buildspec import Piece

S = 7
C = 3
p = Piece("village_well", "Village Well", (S, 7, S), PALETTE)

for x in range(S):
    for z in range(S):
        edge = x in (0, S - 1) or z in (0, S - 1)
        r = jit(x, 0, z, 501)
        if not edge or r < 650:
            p.put(x, 0, z, "cobble" if r < 450 else "andesite" if r < 650 else "mossy" if not edge else "path")
p.fill(C - 1, 0, C - 1, C + 1, 0, C + 1, "bricks")
p.put(C, 0, C, "minecraft:water[level=0]")
for x in range(C - 1, C + 2):
    for z in range(C - 1, C + 2):
        if (x, z) != (C, C):
            p.put(x, 1, z, "minecraft:stone_brick_wall")
for x in (C - 1, C + 1):
    p.fill(x, 2, C, x, 3, C, "fence")
p.fill(C - 1, 4, C, C + 1, 4, C, "beam_x")
p.fill(C - 1, 4, C - 1, C + 1, 4, C - 1, stairs_state("spruce", "south"))
p.fill(C - 1, 4, C + 1, C + 1, 4, C + 1, stairs_state("spruce", "north"))
p.fill(C - 1, 5, C, C + 1, 5, C, "slab")
p.put(C, 3, C, "chain_y")
p.put(C, 2, C, "lantern_hanging")
# round the well: a bench, the trough, buckets, a lantern post, a flower bed at the back
bench(p, 0, 1, C, "east")
p.put(S - 1, 1, C, "minecraft:water_cauldron[level=3]")
p.put(S - 1, 1, C + 1, "minecraft:barrel[facing=up,open=false]")
lantern_post(p, S - 1, 1, 1)
for x in range(1, S - 1):
    p.put(x, 0, S - 1, "grass")
    if x != C:
        flower(p, x, 1, S - 1, salt=502)

p.connector(C, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("a village well with a stone brick curb and a little gabled roof on a small cobbled yard",
       ["Faces north: the building_in connector is on the -z side."])
