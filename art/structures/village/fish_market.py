"""Generates the seafarer village's fish market stall (9x7; STR3) and prints the BuildSpec as JSON. The building
connector is on the north (-z) side; row z 0 is where the customers stand.

An open stall on four stripped spruce posts under a striped red and white awning at the front and a spruce slab roof
behind it: a counter of barrels and slabs with the catch on ice (packed ice, blue ice, dried kelp, sea pickles), a
cauldron of water, a scale (a lightning rod on a slab), crates behind, a fish rack of open trapdoors on a rope, a
hanging sign "Fresh Fish" on a post and a lantern."""
from _style import PALETTE, hanging_sign, jit, lantern_post
from buildspec import Piece

W, L = 9, 7
p = Piece("village_fish_market", "Fish Market Stall", (W, 6, L), PALETTE)

# the floor: cobbles under the stall, trodden ground in front
for x in range(W):
    for z in range(L):
        r = jit(x, 0, z, 471)
        if 1 <= x <= W - 2 and z >= 2:
            p.put(x, 0, z, "cobble" if r < 650 else "mossy" if r < 800 else "andesite")
        elif r < 750:
            p.put(x, 0, z, "path" if r < 400 else "gravel" if r < 600 else "coarse")
# the posts, the awning (front) and the slab roof (back)
for x in (1, W - 2):
    for z in (2, L - 1):
        p.fill(x, 1, z, x, 3, z, "post")
for x in range(0, W):
    p.put(x, 4, 1, "red" if x % 2 == 0 else "white")
    p.put(x, 4, 2, "white" if x % 2 == 0 else "red")
    p.fill(x, 4, 3, x, 4, L - 1, "slab")
p.fill(1, 4, 2, 1, 4, 2, "post")
p.fill(W - 2, 4, 2, W - 2, 4, 2, "post")
p.fill(2, 3, 2, W - 3, 3, 2, "beam_x")
# the counter with the catch on ice
for x in range(2, W - 2):
    p.put(x, 1, 2, "cargo_barrel" if x % 2 else "minecraft:barrel[facing=north,open=false]")
    p.put(x, 2, 2, "minecraft:spruce_slab[type=bottom]")
p.put(2, 2, 2, "minecraft:packed_ice")
p.put(3, 2, 2, "minecraft:dried_kelp_block")
p.put(4, 2, 2, "minecraft:blue_ice")
p.put(5, 2, 2, "minecraft:spruce_slab[type=bottom]")
p.put(5, 3, 2, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
p.put(6, 2, 2, "minecraft:packed_ice")
p.put(6, 3, 2, "minecraft:sea_pickle[pickles=3,waterlogged=false]")
# behind the counter: crates, a cauldron of water, the fish rack, a stool
p.put(2, 1, 5, "crate")
p.put(2, 2, 5, "crate")
p.put(3, 1, 5, "minecraft:water_cauldron[level=3]")
p.put(6, 1, 5, "minecraft:barrel[facing=up,open=false]")
p.put(4, 1, 4, "minecraft:spruce_stairs[facing=south,half=bottom]")
p.fill(2, 3, L - 1, 6, 3, L - 1, "chain_x")
for x in (3, 5):
    p.put(x, 2, L - 1, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]")
p.put(4, 3, 4, "lantern_hanging")
# the sign hanging from the awning's west end
hanging_sign(p, 0, 3, 1, 8, ["Fresh Fish", "", "daily catch"])
lantern_post(p, W - 1, 1, 1)

p.connector(4, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("an open fish market stall under a striped awning, the catch on ice on a barrel counter",
       ["Faces north: the building_in connector is on the -z side, row z 0 is the customers' side."])
