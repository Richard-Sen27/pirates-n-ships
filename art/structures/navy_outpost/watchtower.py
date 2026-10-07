"""Generates the navy outpost watchtower (a 5x4 stone shaft, 10 high, with a roofed lookout platform) and prints the
BuildSpec as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep, and the
lookout platform reaches over it on a course of upside-down stairs.

Inside, a ladder on the back wall climbs from the ground through a hatch in the platform (y 10). The platform is
planked, railed with spruce fences, and roofed with spruce slabs on four corner posts with a lantern hanging in the
middle. Arrow slits of iron bars light the shaft."""
from _style import PALETTE, connector, door, stair
from buildspec import Piece

W, H, D = 5, 14, 5
Z0 = 1                    # the shaft's front wall (z 1..4)
DECK = 10                 # the lookout platform's floor row
LX, LZ = 2, 3             # the ladder column (against the back wall, facing north)

p = Piece("navy_outpost_watchtower", "Navy Outpost Watchtower", (W, H, D), PALETTE)

# the shaft: stone bricks with chiseled corners at the foot, andesite bands, a plank floor
p.fill(0, 0, Z0, W - 1, 0, D - 1, "bricks")
p.fill(1, 0, Z0 + 1, W - 2, 0, D - 2, "spruce")
p.ring(0, Z0, W - 1, D - 1, 1, DECK - 1, "bricks")
for y in (1, 5):
    p.ring(0, Z0, W - 1, D - 1, y, y, "andesite")
for x, z in ((0, Z0), (W - 1, Z0), (0, D - 1), (W - 1, D - 1)):
    p.put(x, 1, z, "chiseled")
for x, y, z, key in ((0, 3, 2, "bricks_mossy"), (4, 7, 3, "bricks_cracked"), (1, 8, 4, "bricks_mossy")):
    p.put(x, y, z, key)

# the door and doorstep, a blue panel over it
door(p, 2, 1, Z0, facing="north")
p.fill(1, 0, 0, 3, 0, 0, "cobble")
p.put(2, 3, Z0, "wool")

# the ladder up the back wall through a hatch in the platform
for y in range(1, DECK + 1):
    p.put(LX, y, LZ, "ladder_n")

# arrow slits on every side
for y in (4, 7):
    p.put(2, y, Z0, "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]")
    p.put(2, y, D - 1, "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]")
    p.put(0, y, 2, "minecraft:iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]")
    p.put(W - 1, y, 3, "minecraft:iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]")

# the lookout platform over the whole box (it overhangs the doorstep on a stair course), rails, roof on posts
for x in range(W):
    p.put(x, DECK - 1, 0, stair("stone_brick", "south", "top"))
p.fill(0, DECK, 0, W - 1, DECK, D - 1, "spruce")
p.put(LX, DECK, LZ, "ladder_n")
for x in range(W):
    for z in range(D):
        edge_x, edge_z = x in (0, W - 1), z in (0, D - 1)
        if not (edge_x or edge_z):
            continue
        if edge_x and edge_z:
            for y in (DECK + 1, DECK + 2):
                p.put(x, y, z, "post")
            continue
        along_x = edge_z
        conn = "east=true,north=false,south=false,waterlogged=false,west=true" if along_x \
            else "east=false,north=true,south=true,waterlogged=false,west=false"
        p.put(x, DECK + 1, z, f"minecraft:spruce_fence[{conn}]")
p.fill(0, DECK + 3, 0, W - 1, DECK + 3, D - 1, "spruce_slab")
p.put(2, DECK + 2, 2, "lantern_hanging")

connector(p, 2, 0, 0, "building_in", "north", "minecraft:cobblestone")

p.emit("tall stone watchtower with a ladder and a roofed lookout platform",
       ["Door and building_in [2, 0, 0] on the north side; row z 0 is the doorstep.",
        "Lookout platform floor y 10 (hatch at [2, 10, 3]), roof y 13."])
