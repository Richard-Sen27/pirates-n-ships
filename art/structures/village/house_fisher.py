"""Generates the seafarer village's fisherman's cottage (a 7x6 cottage under a dark shingle roof with a boat yard on
its east side; STR3) and prints the BuildSpec as JSON. The door and the building connector are on the north (-z)
side; row z 0 is the doorstep.

Built with ``_houses.cottage``. In the yard a rowing boat lies keel-up on two trestles for caulking, a net hangs
drying on a frame of posts and a rope line, and the gear waits by the wall: a barrel of fish, a lobster pot (a
trapdoor-lidded barrel), oars, a coil of rope, a tar cauldron and a lantern post. Inside: a bed, a smoker for the
catch, a chest and a table."""
from _houses import cottage, furnish, peak, tie_beam_lantern
from _style import DARK, PALETTE, jit, lantern_post
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 6, 1, 6
W = 11
p = Piece("village_house_fisher", "Fisherman's Cottage", (W, peak(Z0, Z1, 1) + 2, Z1 + 2), PALETTE)
top = cottage(p, X0, Z0, X1, Z1, storeys=1, roof=DARK, gable="boards", door_x=2, salt=421)
p.put(1, 1, 2, "minecraft:smoker[facing=east,lit=false]")
furnish(p, X0, Z0, X1, Z1, door_x=2, bed_colour="light_blue", rug="blue")
tie_beam_lantern(p, X0, X1, 3, top + 1)

# the yard: trodden ground of gravel and coarse dirt
for x in range(7, W):
    for z in range(0, Z1 + 2):
        r = jit(x, 0, z, 422)
        if r < 850:
            p.put(x, 0, z, "gravel" if r < 400 else "coarse" if r < 700 else "path")
# the trestles (a beam on two fence legs) and the boat keel-up on them, bow north
for z in (2, 5):
    p.put(8, 1, z, "fence")
    p.put(10, 1, z, "fence")
    p.fill(8, 2, z, 10, 2, z, "beam_x")
for z in range(2, 6):
    p.put(8, 3, z, "minecraft:spruce_slab[type=bottom]")
    p.put(10, 3, z, "minecraft:spruce_slab[type=bottom]")
    p.put(9, 3, z, "spruce")
p.put(9, 3, 1, "minecraft:spruce_stairs[facing=south,half=bottom]")
p.put(9, 3, 6, "minecraft:spruce_stairs[facing=north,half=bottom]")
p.fill(9, 4, 2, 9, 4, 5, "minecraft:dark_oak_slab[type=bottom]")     # the keel
# the net frame along the yard's back: two posts, a rope (chain) between them, the net hung over it
for x in (7, 10):
    p.fill(x, 1, 7, x, 3, 7, "fence")
p.fill(8, 3, 7, 9, 3, 7, "chain_x")
p.put(8, 2, 7, "net")
p.put(9, 2, 7, "net")
# the gear by the wall
p.put(7, 1, 1, "cargo_barrel")
p.put(7, 1, 3, "minecraft:barrel[facing=up,open=false]")
p.put(7, 2, 3, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]")
p.put(7, 1, 5, "minecraft:cauldron")
p.put(7, 1, 6, "pirates_n_ships:rope_coil[facing=north,layers=2]")
p.put(8, 1, 3, "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]")
lantern_post(p, 10, 1, 0)

p.connector(2, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("a fisherman's cottage with a dark shingle roof, a rowing boat keel-up on trestles and a net drying",
       ["Faces north: the door and the building_in connector are on the -z side (x 2).",
        "The boat yard lies east of the cottage (x 7..10)."])
