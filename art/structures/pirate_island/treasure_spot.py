"""Generates the treasure spot (a 5x5 patch of sand where X marks the spot) and prints the BuildSpec as JSON.

A small piece of the ``huts`` pool: two stripped logs lie crossed on the sand, a dead bush and a skull beside them,
and the **buried treasure marker** (a jigsaw named ``pirates_n_ships:treasure``, README "Pirate island (ST2)") sits
two blocks under the surface below the cross. Unlike the other land pieces, the surface here is row y 2 (rows 0..1
are the sand the treasure is buried in): ``hut_in`` sits on the surface row, so the piece's surface lines up with the
trail it hangs from and the marker ends up two blocks under the ground.

ST4b: a cairn of mossy stones and a palm marker stake stand at two corners, a bleached bone breaks the surface, an
old trapdoor lid lies by the cross, and the sand around the cross is speckled with gravel and coarse dirt (the
column over the marker stays sand, so the treasure is dug out through sand)."""
from _style import PALETTE, jit, palisade_post, trapdoor, treasure
from buildspec import Piece

S = 5
SURFACE = 2
C = S // 2

p = Piece("pirate_island_treasure_spot", "Pirate Treasure Spot", (S, 5, S), PALETTE)

p.fill(0, 0, 0, S - 1, SURFACE, S - 1, "sand")
for x in range(S):
    for z in range(S):
        r = jit(x, SURFACE, z, 81)
        if (x, z) != (C, C) and (x, z) != (C, 0) and r < 220:
            p.put(x, SURFACE, z, "gravel" if r < 160 else "coarse")
p.put(0, SURFACE, 4, "coarse")
p.put(4, SURFACE, 1, "coarse")
p.put(0, SURFACE, 2, "bones")

# X marks the spot: two stripped logs crossed flat on the sand
p.fill(C - 1, SURFACE + 1, C, C + 1, SURFACE + 1, C, "beam_x")
p.put(C, SURFACE + 1, C - 1, "beam_z")
p.put(C, SURFACE + 1, C + 1, "beam_z")
p.put(4, SURFACE + 1, 3, "dead_bush")
p.put(0, SURFACE + 1, 1, "dead_bush")
p.put(1, SURFACE + 1, 3, "minecraft:skeleton_skull[powered=false,rotation=6]")
p.put(3, SURFACE + 1, 3, trapdoor("east"))
# a cairn of mossy stones in one corner, a palm marker stake in the other
p.put(0, SURFACE + 1, 4, "mossy")
p.put(1, SURFACE + 1, 4, "cobble")
p.put(0, SURFACE + 2, 4, "mossy")
palisade_post(p, 4, SURFACE + 1, 4, height=1, wood="palm")

treasure(p, C, SURFACE - 2, C)
p.connector(C, SURFACE, 0, "hut_in", "north", "minecraft:sand")

p.emit("buried treasure: crossed logs on the sand, a cairn and a marker stake, the treasure marker two blocks below",
       ["hut_in at [2, 2, 0] on the surface row (y 2); the treasure marker is at [2, 0, 2].",
        "The world module turns the marker into a buried chest and a treasure map target."])
