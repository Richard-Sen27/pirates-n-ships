"""Generates the navy outpost watchtower (a 5x4 stone shaft, 10 high, with a roofed lookout platform) and prints the
BuildSpec as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep, and the
lookout platform reaches over it on a course of upside-down stairs.

The shaft: a mossy foundation, corner pilasters with a chiseled plinth and polished andesite bands; the side faces are
set back between the pilasters from a sloped plinth up to a corbel table under the platform; buttresses flank the
door, a blue banner hangs high on the front, arrow slits of iron bars light the stair. Inside, a ladder on the back
wall climbs from the ground through a hatch in the platform (y 10). The platform is planked, railed with spruce fences,
and roofed on four bark posts with a low hipped spruce roof (dark oak eaves) with a lantern hanging in the middle."""
from _style import (PALETTE, age, arrow_slit, banner, bars, buttress, connector, door, fence_run, fort_face, stair,
                    stair_shape)
from buildspec import Piece

W, H, D = 5, 14, 5
Z0 = 1                    # the shaft's front wall (z 1..4)
DECK = 10                 # the lookout platform's floor row
LX, LZ = 2, 3             # the ladder column (against the back wall, facing north)

p = Piece("navy_outpost_watchtower", "Navy Outpost Watchtower", (W, H, D), PALETTE)

# the shaft: stone bricks, chiseled plinth on the front and back, andesite bands, a plank floor
p.fill(0, 0, Z0, W - 1, 0, D - 1, "bricks")
p.fill(1, 0, Z0 + 1, W - 2, 0, D - 2, "spruce")
p.ring(0, Z0, W - 1, D - 1, 1, DECK - 1, "bricks")
p.fill(0, 1, Z0, W - 1, 1, Z0, "chiseled")
p.fill(0, 1, D - 1, W - 1, 1, D - 1, "chiseled")
# the side faces set back between the corner pilasters, the inner skin behind them
for x, inward, skin in ((0, "east", 1), (W - 1, "west", W - 2)):
    fort_face(p, [(x, z) for z in range(Z0 + 1, D - 1)], inward, corbel=DECK - 1)
    p.fill(skin, 2, Z0 + 1, skin, DECK - 1, D - 2, "bricks")
for y in (5,):
    for x, z in ((0, Z0), (W - 1, Z0), (0, D - 1), (W - 1, D - 1)):
        p.put(x, y, z, "andesite")
    p.fill(0, y, Z0, W - 1, y, Z0, "andesite")
    p.fill(0, y, D - 1, W - 1, y, D - 1, "andesite")

# the door and doorstep between buttresses, a blue panel over it, a banner high on the front
door(p, 2, 1, Z0, facing="north")
p.fill(1, 0, 0, 3, 0, 0, "cobble")
p.put(2, 3, Z0, "wool")
for x in (0, W - 1):
    buttress(p, x, 0, 4, "north")
banner(p, 2, 7, 0, "north")

# the ladder up the back wall through a hatch in the platform
for y in range(1, DECK + 1):
    p.put(LX, y, LZ, "ladder_n")

# arrow slits: front and back in the shaft, sides in the inner skin behind the recesses
for y in (4, 7):
    p.put(2, y, D - 1, bars("x"))
arrow_slit(p, 1, 6, 2, "z", grille=True)
arrow_slit(p, W - 2, 3, 3, "z", grille=True)
p.put(2, 8, Z0, bars("x"))
p.put(2, 5, Z0, bars("x"))

# the lookout platform over the whole box (it overhangs the doorstep on a stair course), rails, roof on bark posts
for x in range(W):
    p.put(x, DECK - 1, 0, stair("stone_brick", "south", "top"))
p.fill(0, DECK, 0, W - 1, DECK, D - 1, "spruce")
p.put(LX, DECK, LZ, "ladder_n")
corners = [(0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)]
for x, z in corners:
    p.fill(x, DECK + 1, z, x, DECK + 2, z, "log")
edge = [(x, z) for x in range(W) for z in range(D) if (x in (0, W - 1) or z in (0, D - 1)) and (x, z) not in corners]
fence_run(p, edge, DECK + 1)
# the roof: a low hip of spruce stairs with dark oak eaves at the corners, planks in the middle
for x in range(W):
    for z in range(D):
        if (x, z) in corners:
            p.put(x, DECK + 3, z, stair_shape("dark_oak", "south" if z == 0 else "north"))
        elif z == 0:
            p.put(x, DECK + 3, z, stair_shape("spruce", "south"))
        elif z == D - 1:
            p.put(x, DECK + 3, z, stair_shape("spruce", "north"))
        elif x == 0:
            p.put(x, DECK + 3, z, stair_shape("spruce", "east"))
        elif x == W - 1:
            p.put(x, DECK + 3, z, stair_shape("spruce", "west"))
        else:
            p.put(x, DECK + 3, z, "spruce")
p.put(2, DECK + 2, 2, "lantern_hanging")

age(p, where=lambda x, y, z: y < DECK)

connector(p, 2, 0, 0, "building_in", "north", "minecraft:cobblestone")

p.emit("tall stone watchtower with buttresses, set-back sides, a ladder and a roofed lookout platform",
       ["Door and building_in [2, 0, 0] on the north side; row z 0 is the doorstep.",
        "Lookout platform floor y 10 (hatch at [2, 10, 3]), roof y 13."])
