"""Generates a small pirate tent (a closed two-course pup tent, 5x5x6 box) and prints the BuildSpec as JSON.

A tent variant of the ``huts`` pool (STR2): grey and brown patched canvas on a stripped log ridge pole, the front gable
closed but for the door flap, one bed roll along the east slope, a sea bag (a barrel) and a lantern, a guy stake and
a crate outside, trodden ground that fades into the terrain. ``hut_in`` at the door, on the north edge."""
from _camp import patch, crate_stack
from _style import PALETTE, bed, jit, palisade_post
from buildspec import Piece

W, H, L = 5, 5, 6
Z0, Z1 = 1, 5

p = Piece("pirate_island_tent_small", "Pirate Tent (small)", (W, H, L), PALETTE)


def cloth(x, y, z):
    r = jit(x, y, z, 301)
    if y == 1:
        return "canvas_brown" if r < 600 else "canvas_grey"
    return "canvas_grey" if r < 380 else "canvas_brown" if r < 520 else "canvas"


p.fill(1, 0, Z0, 3, 0, Z1, "coarse")
for z in range(Z0, Z1 + 1):
    p.put(0, 1, z, cloth(0, 1, z))
    p.put(4, 1, z, cloth(4, 1, z))
    p.put(1, 2, z, cloth(1, 2, z))
    p.put(3, 2, z, cloth(3, 2, z))
p.fill(2, 3, 0, 2, 3, Z1, "beam_z")           # the ridge pole sticks out over the door
# the gables: the back closed, the front closed but for the flap (x 2, y 1)
for x, y in ((1, 1), (3, 1), (2, 2)):
    p.put(x, y, Z1, cloth(x, y, Z1 + 7))
p.put(1, 1, Z0, cloth(1, 1, Z0 + 7))
p.put(3, 1, Z0, cloth(3, 1, Z0 + 7))
p.put(2, 2, Z0, "canvas")
# inside: the bed roll, a sea bag, a lantern, a rug
bed(p, 3, 1, 3, "south", colour="brown")
p.put(1, 1, 4, "minecraft:barrel[facing=up,open=false]")
p.put(1, 1, 3, "lantern")
p.put(2, 1, 4, "minecraft:red_carpet")
# outside: a guy stake and a crate
palisade_post(p, 0, 1, 0, height=1, wood="drift", tip=False)
crate_stack(p, 4, 1, 0, salt=302)
patch(p, 0, Z0, W - 1, Z1, reach=1, salt=303)

p.connector(2, 0, 0, "hut_in", "north", "minecraft:coarse_dirt")

p.emit("a small closed pup tent of patched grey and brown canvas with a bed roll and a sea bag",
       ["Faces north: the door flap and the hut_in connector are on the -z side.",
        "y 0 is the foundation row, level with the terrain surface."])
