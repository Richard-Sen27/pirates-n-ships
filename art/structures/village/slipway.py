"""Generates the seafarer village's slipway (an open building berth with a hull frame under construction; STR3) and
prints the BuildSpec as JSON. The building connector is on the north (-z) side; row z 0 is the yard apron.

The second look of the shipwright: no shed, a gravel and plank slipway running back from the apron with a keel on
blocking, the stem and stern posts standing, five frames rising either side and the first strakes of planking on the
lower frames; staging of planks on fence posts along both sides with a ladder, an A-frame sheer legs at the stern with
a hoist chain, sawhorses, a steam box (a smoker and a cauldron), stacks of logs and planks, a tar cauldron, a grindstone
and a lantern post either side of the way in."""
from _style import PALETTE, jit, lantern_post, stairs_state
from buildspec import Piece

W, L = 11, 15
C = 5                       # the keel's column
p = Piece("village_slipway", "Shipwright's Slipway", (W, 10, L), PALETTE)

# the ground: a gravel slipway with plank ways under the hull, trodden ground beside it
for x in range(W):
    for z in range(L):
        if 3 <= x <= 7:
            key = "spruce" if x in (4, 6) and 2 <= z <= L - 3 else "gravel"
        else:
            r = jit(x, 0, z, 441)
            key = "coarse" if r < 400 else "path" if r < 650 else "gravel" if r < 850 else None
        if key:
            p.put(x, 0, z, key)
p.fill(3, 0, 0, 7, 0, 0, "gravel")

# the keel on blocking, stem (bow, north) and stern posts
for z in range(3, 12, 2):
    p.put(C, 1, z, "minecraft:spruce_slab[type=bottom]")
p.fill(C, 2, 2, C, 2, 12, "minecraft:stripped_oak_log[axis=z]")
p.fill(C, 3, 2, C, 6, 2, "oak_post")
p.put(C, 7, 2, "minecraft:oak_fence")
p.fill(C, 3, 12, C, 5, 12, "oak_post")
# frames: floor timbers and futtocks rising and flaring out, the strakes on the lower ones
for z in (4, 6, 8, 10):
    p.put(C - 1, 2, z, "oak_beam_x")
    p.put(C + 1, 2, z, "oak_beam_x")
    for x, flare in ((C - 2, C - 3), (C + 2, C + 3)):
        p.fill(x, 3, z, x, 4, z, "oak_post")
        p.put(flare, 5, z, "oak_post")
        p.put(flare, 6, z, "minecraft:oak_fence")
for z in range(4, 11):
    if z % 2:
        for x in (C - 2, C + 2):
            p.put(x, 3, z, "oak")
p.put(C - 1, 3, 3, stairs_state("oak", "east"))
p.put(C + 1, 3, 3, stairs_state("oak", "west"))
# staging along both sides: plank walks on fence posts, a ladder up
for x in (0, W - 1):
    for z in (3, 7, 11):
        p.fill(x, 1, z, x, 4, z, "fence")
    p.fill(x, 5, 3, x, 5, 11, "minecraft:spruce_slab[type=bottom]")
p.fill(1, 1, 7, 1, 4, 7, "minecraft:ladder[facing=east,waterlogged=false]")
# the sheer legs at the stern: two legs leaning together, a hoist chain to a timber
for x in (2, 8):
    p.fill(x, 1, 13, x, 6, 13, "minecraft:stripped_spruce_log[axis=y]")
p.fill(3, 7, 13, 7, 7, 13, "beam_x")
p.fill(C, 5, 13, C, 6, 13, "chain_y")
p.put(C, 4, 13, "minecraft:stripped_oak_log[axis=x]")
# the workplace: sawhorses, the steam box, stacks of timber, tar and a grindstone
for z in (1, 3):
    p.put(9, 1, z, "fence")
p.fill(9, 2, 1, 9, 2, 3, "beam_z")
p.put(9, 3, 2, "minecraft:spruce_slab[type=bottom]")
p.put(1, 1, 1, "minecraft:smoker[facing=east,lit=false]")
p.put(1, 1, 2, "minecraft:cauldron")
p.put(2, 1, 1, "barrel_up")
p.fill(1, 1, 9, 1, 1, 12, "minecraft:spruce_log[axis=z]")
p.fill(1, 2, 10, 1, 2, 11, "minecraft:spruce_log[axis=z]")
p.fill(9, 1, 9, 9, 2, 10, "oak")
p.put(9, 1, 11, "minecraft:crafting_table")
p.put(9, 1, 12, "minecraft:grindstone[face=floor,facing=north]")
p.put(2, 1, 14, "minecraft:cauldron")
lantern_post(p, 3, 1, 0)
lantern_post(p, 7, 1, 0)

p.connector(C, 0, 0, "building_in", "north", "minecraft:gravel")
p.emit("an open slipway with a hull frame under construction, staging, sheer legs and a timber yard",
       ["Faces north: the building_in connector is on the -z side, row z 0 is the apron.",
        "The second look of the shipwright (the first is the shed)."])
