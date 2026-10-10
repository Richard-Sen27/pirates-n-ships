"""Generates a treasure spot among the ribs of a rotted boat (7x6x7 box) and prints the BuildSpec as JSON.

A treasure look of the ``huts`` pool (STR2), like ``treasure_spot.py``: the surface is row y 2 (rows 0..1 are the sand
the treasure is buried in), ``hut_in`` on the surface row, the buried treasure marker two blocks under the middle,
the column over it sand. Here the bleached ribs of an old boat (dark oak fences curving up from a half-buried keel)
stand round the spot, a skeleton (bone blocks) lies against one of them with a skull, a broken barrel and a rusted
chain lie in the sand."""
from _style import PALETTE, jit, treasure
from buildspec import Piece

S = 7
SURFACE = 2
C = S // 2

p = Piece("pirate_island_treasure_wreck", "Pirate Treasure Spot (boat ribs)", (S, 6, S), PALETTE)

p.fill(0, 0, 0, S - 1, SURFACE, S - 1, "sand")
for x in range(S):
    for z in range(S):
        r = jit(x, SURFACE, z, 441)
        if (x, z) != (C, C) and r < 200:
            p.put(x, SURFACE, z, "gravel" if r < 120 else "coarse")
# the keel half buried along x, north of the spot, and the ribs curving up from it on both sides
p.fill(1, SURFACE, C - 2, 5, SURFACE, C - 2, "minecraft:dark_oak_log[axis=x]")
for x in (1, 3, 5):
    p.put(x, SURFACE + 1, C - 1, "dark_fence")
    p.put(x, SURFACE + 2, C - 1, "dark_fence")
    if x != 3:                      # the middle rib on the near side is gone: the way in
        p.put(x, SURFACE + 1, C - 3, "dark_fence")
        p.put(x, SURFACE + 2, C - 3, "dark_fence")
        p.put(x, SURFACE + 3, C - 1, "minecraft:dark_oak_trapdoor[facing=south,half=bottom,open=true,powered=false]")
# ribs on the far side of the spot, broken shorter
for x, h in ((1, 2), (5, 1), (2, 1)):
    for dy in range(h):
        p.put(x, SURFACE + 1 + dy, C + 2, "dark_fence")
# the skeleton against a rib, a skull, a broken barrel, a rusted chain
p.put(5, SURFACE + 1, C + 1, "bones")
p.put(5, SURFACE + 1, C, "minecraft:bone_block[axis=x]")
p.put(4, SURFACE + 1, C + 2, "minecraft:skeleton_skull[powered=false,rotation=4]")
p.put(1, SURFACE + 1, C + 1, "minecraft:barrel[facing=west,open=false]")
p.put(0, SURFACE + 1, C + 1, "minecraft:spruce_trapdoor[facing=west,half=bottom,open=false,powered=false]")
p.put(2, SURFACE + 1, 5, "chain_x")
p.put(6, SURFACE + 1, 1, "dead_bush")

treasure(p, C, SURFACE - 2, C)
p.connector(C, SURFACE, 0, "hut_in", "north", "minecraft:sand")

p.emit("buried treasure among the bleached ribs of an old boat, with a skeleton, a skull and a broken barrel",
       ["hut_in at [3, 2, 0] on the surface row (y 2); the treasure marker is at [3, 0, 3].",
        "The world module turns the marker into a buried chest and a treasure map target."])
