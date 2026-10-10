"""Generates a sailcloth lean-to (7x5x6 box) and prints the BuildSpec as JSON.

A tent variant of the ``huts`` pool (STR2): an old striped sail stretched from a back wall of stacked driftwood logs
down to two fence poles at the front, open on the front and the sides. Under it: a bed roll, a sea chest of a crate
and a barrel, a rope coil, a lantern on the back wall, a stool by a cold fire of stones outside. ``hut_in`` at the
front, on the north edge."""
from _camp import patch, rocks, rope_coil
from _style import PALETTE, bed, jit, stool
from buildspec import Piece

W, H, L = 7, 5, 6
BACK = 5

p = Piece("pirate_island_tent_lean", "Pirate Lean-to", (W, H, L), PALETTE)

p.fill(1, 0, 2, W - 2, 0, BACK - 1, "coarse")
for x, z in ((2, 3), (4, 2)):
    p.put(x, 0, z, "gravel")
# the back wall: driftwood logs stacked three high between two palm posts
for y in (1, 2, 3):
    p.fill(1, y, BACK, W - 2, y, BACK, "drift_x" if y != 2 else "beam_x")
for x in (0, W - 1):
    p.fill(x, 1, BACK, x, 3, BACK, "palm")
p.put(3, 3, BACK, "minecraft:spruce_planks")
# the sail: three courses falling from the wall's top to the front poles, striped along x
for z, y in ((BACK - 1, 4), (BACK - 2, 3), (BACK - 3, 3), (BACK - 4, 2)):
    for x in range(W):
        key = "canvas_red" if z % 2 else "canvas"
        if jit(x, y, z, 321) < 140:
            key = "canvas_brown"
        p.put(x, y, z, key)
for x in (0, W - 1):
    p.put(x, 1, BACK - 4, "fence")
p.put(1, 2, BACK - 1, "lantern")
p.put(1, 1, BACK - 1, "pirates_n_ships:cargo_crate")
# under the sail: a bed roll along the wall, a barrel, a rope coil
bed(p, 4, 1, 3, "south", colour="brown")
p.put(5, 1, BACK - 1, "minecraft:barrel[facing=up,open=false]")
rope_coil(p, 2, 1, 2, layers=2, facing="east")
# outside: a cold fire of stones and a stool
rocks(p, [(5, 0), (6, 1)], salt=322)
p.put(4, 0, 0, "coarse")
p.put(4, 1, 0, "minecraft:campfire[facing=north,lit=false,signal_fire=false]")
stool(p, 3, 1, 0, "west")
patch(p, 0, 1, W - 1, BACK, reach=1, salt=323)

p.connector(2, 0, 0, "hut_in", "north", "minecraft:coarse_dirt")

p.emit("a lean-to of striped sailcloth against a driftwood wall with a bed roll, a crate and a cold fire",
       ["Faces north: open front and the hut_in connector on the -z side.",
        "y 0 is the foundation row, level with the terrain surface."])
