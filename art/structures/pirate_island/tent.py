"""Generates a pirate's canvas tent (7x6x7 box, an A-frame of wool over a stripped log ridge pole) and prints the
BuildSpec as JSON. The opening and the hut connector are on the north (-z) side; row z 0 is the sand in front.

Inside: a bed roll, a chest and a barrel with a lantern. Vanilla has no wool stairs or slabs, so the canvas is
stepped wool: old sailcloth, white with grey and brown patches and a red sailcloth patch, a brown hem along the
ground. ST4b: the ridge pole runs on over a fly in row z 0 that shades the door flap on two fence poles, a lantern
hangs from the pole's end, a red pennant flies from its back end, guy stakes and a crate stand at the front corners,
a rug lies inside, and the sand at the tent's foot turns to gravel and coarse dirt."""
from _style import PALETTE, bed, jit, palisade_post, sand_skirt
from buildspec import Piece

W = 7
Z0, Z1 = 1, 6               # canvas from the front (open) gable to the back gable
RIDGE = 4

p = Piece("pirate_island_tent", "Pirate Tent", (W, 6, Z1 + 1), PALETTE)

p.fill(0, 0, 0, W - 1, 0, Z1, "sand")
p.fill(1, 0, Z0, W - 2, 0, Z1, "coarse")
p.put(3, 0, 0, "coarse")
sand_skirt(p, 1, Z0, W - 2, Z1, reach=1, salt=11)
p.put(2, 0, 0, "gravel")


def cloth(x, y, z):
    """The canvas key for a slope cell: a brown hem on the ground course, a red sailcloth patch on the middle course
    near the front, grey and brown patches elsewhere."""
    if y == 1:
        return "canvas_brown"
    r = jit(x, y, z, 5)
    if y == 2 and z in (2, 3):
        return "canvas_red"
    return "canvas_grey" if r < 170 else "canvas_brown" if r < 230 else "canvas"


# the slopes: one wool course per row up to the ridge pole
for z in range(Z0, Z1 + 1):
    for y in range(1, RIDGE):
        for x in (y - 1, W - y):
            p.put(x, y, z, cloth(x, y, z))
# the ridge pole runs on over the fly at the front
p.fill(3, RIDGE, 0, 3, RIDGE, Z1, "beam_z")
# the back gable closed (a patched panel), the front gable open in the middle (an opening 3 wide below, 1 wide above)
for y in range(1, RIDGE):
    p.fill(y, y, Z1, W - 1 - y, y, Z1, "canvas")
p.put(2, 1, Z1, "canvas_grey")
p.put(4, 2, Z1, "canvas_brown")
for x, y in ((1, 1), (5, 1), (2, 2), (4, 2), (3, 3)):
    p.put(x, y, Z0, "canvas")

# the fly: the slopes' upper courses carry on one block over the door flap, on two fence poles
for x, y in ((1, 2), (5, 2), (2, 3), (4, 3)):
    p.put(x, y, 0, cloth(x, y, 0))
p.put(1, 1, 0, "fence")
p.put(5, 1, 0, "fence")
p.put(3, RIDGE - 1, 0, "lantern_hanging")
# a red pennant on the ridge pole's back end
p.put(3, RIDGE + 1, Z1, "minecraft:red_banner[rotation=4]")

# a guy stake at one front corner, a crate at the other
palisade_post(p, 0, 1, 0, height=1, wood="drift", tip=False)
p.put(6, 1, 0, "pirates_n_ships:cargo_crate")

# inside: the bed roll along the east slope, a chest at the back, a barrel with a lantern, a rug
bed(p, 4, 1, 3, "south")
p.put(2, 1, 5, "minecraft:chest[facing=east,type=single]")
p.put(2, 1, 3, "barrel")
p.put(2, 2, 3, "lantern")
p.put(3, 1, 5, "lantern")
p.fill(3, 1, 3, 3, 1, 4, "minecraft:red_carpet")
p.put(2, 1, 4, "minecraft:brown_carpet")

p.connector(3, 0, 0, "hut_in", "north", "minecraft:sand")

p.emit("canvas tent of patched, striped wool over a ridge pole with a fly, bed roll, chest and rug",
       ["Faces north: the opening and the hut_in connector are on the -z side.",
        "y 0 is the foundation row, level with the terrain surface."])
