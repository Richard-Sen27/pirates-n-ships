"""Generates a pirate's canvas tent (7x6 footprint, an A-frame of wool over a stripped log ridge pole) and prints the
BuildSpec as JSON. The opening and the hut connector are on the north (-z) side; row z 0 is the sand in front.

Inside: a bed roll, a chest and a barrel with a lantern. Vanilla has no wool stairs or slabs, so the canvas is
stepped wool: white with grey patches, a brown hem along the ground."""
from _style import PALETTE, bed, torch_post
from buildspec import Piece

W = 7
Z0, Z1 = 1, 6               # canvas from the front (open) gable to the back gable
RIDGE = 4

p = Piece("pirate_island_tent", "Pirate Tent", (W, 6, Z1 + 1), PALETTE)

p.fill(0, 0, 0, W - 1, 0, Z1, "sand")
p.fill(1, 0, Z0, W - 2, 0, Z1, "coarse")
p.put(3, 0, 0, "coarse")

# the slopes: one wool course per row up to the ridge pole
for z in range(Z0, Z1 + 1):
    for y in range(1, RIDGE):
        for x in (y - 1, W - y):
            if y == 1:
                key = "canvas_brown"
            else:
                key = "canvas_grey" if (x + 2 * z + y) % 5 == 0 else "canvas"
            p.put(x, y, z, key)
p.fill(3, RIDGE, Z0, 3, RIDGE, Z1, "beam_z")
# the back gable closed, the front gable open in the middle (an opening 3 wide below, 1 wide above)
for y in range(1, RIDGE):
    p.fill(y, y, Z1, W - 1 - y, y, Z1, "canvas")
for x, y in ((1, 1), (5, 1), (2, 2), (4, 2), (3, 3)):
    p.put(x, y, Z0, "canvas")

# inside: the bed roll along the east slope, a chest at the back, a barrel with a lantern
bed(p, 4, 1, 3, "south")
p.put(2, 1, 5, "minecraft:chest[facing=east,type=single,waterlogged=false]")
p.put(2, 1, 3, "barrel")
p.put(3, 1, 5, "lantern")

torch_post(p, 6, 1, 0)

p.connector(3, 0, 0, "hut_in", "north", "minecraft:sand")

p.emit("canvas tent of stepped wool over a ridge pole, bed roll and chest",
       ["Faces north: the opening and the hut_in connector are on the -z side.",
        "y 0 is the foundation row, level with the terrain surface."])
