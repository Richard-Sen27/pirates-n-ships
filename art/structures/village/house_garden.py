"""Generates the seafarer village's house with a kitchen garden (7x6 cottage and a fenced garden behind it; STR3) and
prints the BuildSpec as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep.

Built with ``_houses.cottage``. A side path of stepping stones along the east wall leads to a gate in the garden's
spruce fence; inside, rows of carrots, potatoes, wheat and beetroot on farmland either side of a water channel, a
composter, a scarecrow (a fence post, a hay bale and a carved pumpkin) and a water barrel."""
from _houses import cottage, furnish, peak, tie_beam_lantern
from _style import PALETTE, TILE, flower, jit
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 6, 1, 6           # the cottage
GX1, GZ0, GZ1 = 8, 8, 13              # the garden: x 0..8, z 8..13
p = Piece("village_house_garden", "Seafarer House (garden)", (GX1 + 1, peak(Z0, Z1, 1) + 2, GZ1 + 1), PALETTE)
top = cottage(p, X0, Z0, X1, Z1, storeys=1, roof=TILE, gable="render", chimney_side="west", salt=411)
furnish(p, X0, Z0, X1, Z1, hearth_side="west", bed_colour="yellow", rug="green")
tie_beam_lantern(p, X0, X1, 3, top + 1)

# the side path along the east wall to the garden gate
for z, key in ((1, "gravel"), (2, "path"), (3, "gravel"), (4, "path"), (5, "gravel"), (6, "path"), (7, "path")):
    p.put(7, 0, z, key)
p.put(8, 0, 2, "coarse")
flower(p, 8, 1, 2, salt=412)
p.put(8, 0, 5, "coarse")
p.put(8, 1, 5, "minecraft:barrel[facing=up,open=false]")

# the garden: a fence ring with a gate, farmland rows either side of a water channel
for x in range(0, GX1 + 1):
    for z in (GZ0, GZ1):
        p.put(x, 0, z, "mossy" if jit(x, 0, z, 413) < 300 else "cobble")
        p.put(x, 1, z, "fence")
for z in range(GZ0 + 1, GZ1):
    for x in (0, GX1):
        p.put(x, 0, z, "mossy" if jit(x, 0, z, 413) < 300 else "cobble")
        p.put(x, 1, z, "fence")
p.put(7, 1, GZ0, "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]")
p.put(7, 0, GZ0, "path")
crops = {9: "minecraft:carrots[age=7]", 10: "minecraft:potatoes[age=7]", 12: "minecraft:wheat[age=7]"}
for x in range(1, GX1):
    p.put(x, 0, 11, "minecraft:water[level=0]")
    for z, crop in crops.items():
        p.put(x, 0, z, "minecraft:farmland[moisture=7]")
        p.put(x, 1, z, crop if x != 7 else "minecraft:beetroots[age=3]")
p.put(7, 1, 9, "minecraft:composter[level=4]")
p.put(7, 0, 9, "path")
# the scarecrow in the wheat
p.put(4, 1, 12, "fence")
p.put(4, 2, 12, "minecraft:hay_block[axis=y]")
p.put(4, 3, 12, "minecraft:carved_pumpkin[facing=north]")
p.put(3, 2, 12, "minecraft:spruce_trapdoor[facing=west,half=top,open=true,powered=false,waterlogged=false]")
p.put(5, 2, 12, "minecraft:spruce_trapdoor[facing=east,half=top,open=true,powered=false,waterlogged=false]")

p.connector(3, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("a village cottage with a fenced kitchen garden of vegetable rows behind it",
       ["Faces north: the door and the building_in connector are on the -z side.",
        "The garden lies behind the house (z 8..13), its gate at the end of the side path."])
