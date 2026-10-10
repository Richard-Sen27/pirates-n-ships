"""Generates a treasure spot marked by a cairn (5x7x5 box) and prints the BuildSpec as JSON.

A treasure look of the ``huts`` pool (STR2), like ``treasure_spot.py``: the surface is row y 2 (rows 0..1 are the sand
the treasure is buried in), ``hut_in`` on the surface row, the buried treasure marker two blocks under the middle,
the column over it sand. Here a tall cairn of mossy and plain stones stands in the back corner with a driftwood stake
and a skull on top, four stones ring the spot, a broken shovel (a fence and a trapdoor) leans on the cairn and a
gravel trail of footprints leads to it."""
from _camp import rocks
from _style import PALETTE, jit, trapdoor, treasure
from buildspec import Piece

S = 5
SURFACE = 2
C = S // 2

p = Piece("pirate_island_treasure_cairn", "Pirate Treasure Spot (cairn)", (S, 7, S), PALETTE)

p.fill(0, 0, 0, S - 1, SURFACE, S - 1, "sand")
for x in range(S):
    for z in range(S):
        r = jit(x, SURFACE, z, 431)
        if (x, z) != (C, C) and r < 260:
            p.put(x, SURFACE, z, "gravel" if r < 170 else "coarse")
p.put(C, SURFACE, 1, "gravel")
# four stones ring the spot
for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
    p.put(C + dx, SURFACE + 1, C + dz, "minecraft:cobblestone_slab[type=bottom]" if dz else "minecraft:stone_button[face=floor,facing=north,powered=false]")
# the cairn in the back corner: three courses of stones narrowing, a stake and a skull on top
rocks(p, [(3, 4), (4, 3), (4, 4)], y=SURFACE + 1, salt=432)
p.put(4, SURFACE + 1, 4, "mossy")
p.put(4, SURFACE + 2, 4, "cobble")
p.put(4, SURFACE + 3, 4, "drift")
p.put(4, SURFACE + 4, 4, "minecraft:skeleton_skull[powered=false,rotation=6]")
# a broken shovel leaning on the cairn
p.put(3, SURFACE + 1, 3, "fence")
p.put(3, SURFACE + 2, 3, trapdoor("west", "bottom", True))
p.put(0, SURFACE + 1, 3, "dead_bush")

treasure(p, C, SURFACE - 2, C)
p.connector(C, SURFACE, 0, "hut_in", "north", "minecraft:sand")

p.emit("buried treasure under a ring of stones beside a tall cairn with a skull, a broken shovel",
       ["hut_in at [2, 2, 0] on the surface row (y 2); the treasure marker is at [2, 0, 2].",
        "The world module turns the marker into a buried chest and a treasure map target."])
