"""Generates the camp's lookout tower (a crow's nest on four palm stilts, 5x15x6 box) and prints the BuildSpec as JSON.

A building of the ``huts`` pool (STR2): four palm trunks lean in a little toward the top (the upper half of each set
one block in), tied by stripped log braces, carry a plank platform at y 10 with a rope rail, a lantern, a ship's
bell and a small sailcloth sunshade on fence poles; a ladder climbs the inside of the north-west leg to a trapdoor.
At the foot: trodden ground, a crate and a rope coil. ``hut_in`` on the north edge in front of the ladder."""
from _camp import patch, rope_coil
from _style import PALETTE, awning, trapdoor
from buildspec import Piece

W, H, L = 5, 15, 6
Z0, Z1 = 1, 5
DECK = 10

p = Piece("pirate_island_lookout_tower", "Pirate Lookout Tower", (W, H, L), PALETTE)

# legs: the lower half on the corners, the upper half one block in (the tower tapers)
for x, z, ix, iz in ((0, Z0, 1, Z0 + 1), (4, Z0, 3, Z0 + 1), (0, Z1, 1, Z1 - 1), (4, Z1, 3, Z1 - 1)):
    p.fill(x, 0, z, x, 4, z, "palm")
    p.fill(ix, 5, iz, ix, DECK - 1, iz, "palm")
    p.put(ix, 4, iz, "minecraft:stripped_jungle_log[axis=y]")
# braces
for y in (3, 7):
    lo, hi = (0, 4) if y < 5 else (1, 3)
    zlo, zhi = (Z0, Z1) if y < 5 else (Z0 + 1, Z1 - 1)
    p.fill(lo + 1, y, zhi, hi - 1, y, zhi, "beam_x")
    p.fill(lo, y, zlo + 1, lo, y, zhi - 1, "beam_z")
    p.fill(hi, y, zlo + 1, hi, y, zhi - 1, "beam_z")
# the ladder up the north side to a trapdoor in the platform (on a palm post in the middle of the north side)
for y in range(1, DECK):
    p.put(2, y, Z0 + 1, "palm")
    p.put(2, y, Z0, "minecraft:ladder[facing=north,waterlogged=false]")
# the platform, a rail round it, the trapdoor over the ladder
p.fill(0, DECK, Z0, 4, DECK, Z1, "spruce")
for x, z in ((1, 2), (3, 4), (4, Z0)):
    p.put(x, DECK, z, "dark")
p.put(2, DECK, Z0, trapdoor("north", "bottom", True))
for x in range(5):
    for z in (Z0, Z1):
        if (x, z) != (2, Z0):
            p.put(x, DECK + 1, z, "fence")
for z in range(Z0 + 1, Z1):
    p.put(0, DECK + 1, z, "fence")
    p.put(4, DECK + 1, z, "fence")
p.put(4, DECK + 2, Z1, "lantern")
p.put(1, DECK + 1, Z1 - 1, "pirates_n_ships:ships_bell[face=floor,facing=north]")
# a sailcloth sunshade on two poles over the back half
awning(p, 0, Z1 - 2, 4, Z1, DECK + 3, stripes_along="x", posts=((0, Z1), (4, Z1)), ground=DECK + 2, salt=381)
# the foot: trodden ground, a crate, a rope coil
p.fill(1, 0, Z0 + 1, 3, 0, Z1 - 1, "coarse")
patch(p, 0, Z0, 4, Z1, reach=1, salt=382)
p.put(3, 1, Z1 - 1, "pirates_n_ships:cargo_crate")
rope_coil(p, 1, 1, Z1 - 1, layers=3)

p.connector(2, 0, 0, "hut_in", "north", "minecraft:coarse_dirt")

p.emit("a lookout tower: a crow's nest platform on four tapering palm legs with a ladder, a bell and a sunshade",
       ["Faces north: the ladder and the hut_in connector are on the -z side.",
        "y 0 is the foundation row; the platform is y 10."])
