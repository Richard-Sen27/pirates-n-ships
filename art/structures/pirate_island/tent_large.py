"""Generates a large pirate tent (an open-fronted mess tent, 9x7x9 box) and prints the BuildSpec as JSON.

A tent variant of the ``huts`` pool (STR2): an A-frame of striped sailcloth (red and white courses with brown
patches) four courses high over a stripped log ridge pole, the upper courses running on as a fly over the open front
on two fence poles, the front gable open three wide. Inside: two bed rolls along the slopes, a table with stools, a
chest, a barrel with a lantern, a hanging lantern from the ridge and a rug. Outside: a pennant, a guy stake, a crate
stack, trodden ground fading into the terrain. ``hut_in`` at the door, on the north edge."""
from _camp import crate_stack, patch
from _style import PALETTE, bed, jit, palisade_post, stool, table
from buildspec import Piece

W, H, L = 9, 7, 9
Z0, Z1 = 2, 8          # canvas from the front gable to the back gable
RIDGE = 5

p = Piece("pirate_island_tent_large", "Pirate Tent (large)", (W, H, L), PALETTE)


def cloth(x, y, z):
    """Striped sailcloth: the courses alternate red and white, with a brown patch here and there."""
    if y == 1:
        return "canvas_brown"
    if jit(x, y, z, 311) < 110:
        return "canvas_brown"
    return "canvas_red" if y % 2 == 0 else "canvas"


p.fill(1, 0, Z0, W - 2, 0, Z1, "coarse")
for x, z in ((2, 4), (6, 6), (4, 7), (3, 3)):
    p.put(x, 0, z, "gravel")
# the slopes, one course per row up to the ridge pole
for z in range(Z0, Z1 + 1):
    for y in range(1, RIDGE):
        p.put(y - 1, y, z, cloth(y - 1, y, z))
        p.put(W - y, y, z, cloth(W - y, y, z))
p.fill(4, RIDGE, 0, 4, RIDGE, Z1, "beam_z")
# the fly: the two upper courses run on over the front on fence poles
for z in (0, 1):
    for y in (3, 4):
        p.put(y - 1, y, z, cloth(y - 1, y, z))
        p.put(W - y, y, z, cloth(W - y, y, z))
    p.put(2, 1, 0, "fence")
    p.put(2, 2, 0, "fence")
    p.put(6, 1, 0, "fence")
    p.put(6, 2, 0, "fence")
p.put(4, RIDGE - 1, 0, "lantern_hanging")
# the back gable closed, the front gable open three wide below
for y in range(1, RIDGE):
    for x in range(y, W - y):
        p.put(x, y, Z1, "canvas" if jit(x, y, Z1, 312) > 150 else "canvas_grey")
        if not (3 <= x <= 5 and y <= 2):
            p.put(x, y, Z0, "canvas")
# a pennant on the ridge pole's back end
p.put(4, RIDGE + 1, Z1, "minecraft:red_banner[rotation=4]")

# inside: bed rolls on both sides, a table with stools, a chest, a barrel and a lantern, a rug
bed(p, 2, 1, 6, "south", colour="red")
bed(p, 6, 1, 6, "south", colour="brown")
table(p, 4, 1, 5)
stool(p, 3, 1, 5, "west")
stool(p, 5, 1, 5, "east")
p.put(4, 1, 7, "minecraft:chest[facing=north,type=single]")
p.put(2, 1, 4, "barrel")
p.put(2, 2, 4, "lantern")
p.put(4, 4, 6, "lantern_hanging")
p.fill(3, 1, 3, 5, 1, 3, "minecraft:red_carpet")
p.put(6, 1, 4, "minecraft:white_carpet")

# outside: a guy stake, a crate stack, the ground
palisade_post(p, 0, 1, 1, height=1, wood="drift", tip=False)
crate_stack(p, 8, 1, 1, salt=313)
crate_stack(p, 7, 1, 0, salt=314)
patch(p, 0, Z0, W - 1, Z1, reach=2, salt=315)

p.connector(4, 0, 0, "hut_in", "north", "minecraft:coarse_dirt")

p.emit("a large open-fronted mess tent of striped sailcloth with two bed rolls, a table and a chest",
       ["Faces north: the open front, the fly and the hut_in connector are on the -z side.",
        "y 0 is the foundation row, level with the terrain surface."])
