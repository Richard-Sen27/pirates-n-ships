"""Generates the seafarer village's beacon tower (a small lighthouse, 7x7 footprint, 21 high; STR3) and prints the
BuildSpec as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep.

A square tower of white render banded with red brick (the village's accent) between stone brick quoins, on a stepped
cobblestone base going mossy at the foot; small framed windows up the stair side, a framed door under a hood. Inside a
ladder climbs to a hatch in the gallery floor: a gallery of full stone bricks on upside-down stair corbels with a
fence rail, the lantern room glazed all round (glass panes between posts) about a glowstone light and a sea lantern, a
pyramid roof of brick stairs with a lightning rod. A barrel and a coil of rope by the door.

STR6 (the human, 2026-10-12: "replace half slabs at Leuchtturm with full blocks, looks strange and detached"): the
gallery floor was a ring of bottom slabs, which read as a thin plate floating on the corbels; it is a course of full
stone bricks now. The lantern room's door is a pane: it opened straight onto the light, there is no room inside."""
from _style import PALETTE, door_frame, jit, stairs_state, window
from buildspec import Piece

S = 7
X0, X1, Z0, Z1 = 1, 5, 1, 5          # the tower's walls
TOP = 13                             # the last wall row
GALLERY = TOP + 1
p = Piece("village_lighthouse", "Beacon Tower", (S, 21, S), PALETTE)

# the stepped base: a cobblestone apron round the tower, mossy at the foot
for x in range(S):
    for z in range(S):
        if x in (0, S - 1) or z in (0, S - 1):
            if (x, z) != (3, 0):
                p.put(x, 0, z, "mossy" if jit(x, 0, z, 481) < 550 else "cobble")
for x in (0, S - 1):
    for z in (0, S - 1):
        p.put(x, 1, z, stairs_state("cobblestone", "south" if z == 0 else "north"))
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
# the walls: render banded with brick, stone brick quoins
for y in range(1, TOP + 1):
    band = "brick" if y in (4, 5, 9, 10) else "render"
    if y == 1:
        band = "plinth_mossy"
    elif y == 2:
        band = "plinth"
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if x in (X0, X1) or z in (Z0, Z1):
                quoin = x in (X0, X1) and z in (Z0, Z1)
                p.put(x, y, z, ("bricks" if y % 2 else "bricks_mossy" if y < 4 else "bricks") if quoin else band)
door_frame(p, 3, 1, Z0, "north", hood=True, posts=False)
for y in (6, 11):
    window(p, 3, y, Z0, "north", shutters=False)
for y in (4, 8, 12):
    window(p, X0, y, 3, "west", lintel=False, sill=False)
    window(p, X1, y, 3, "east", lintel=False, sill=False)
# the ladder up the back wall, a hatch through the gallery floor
p.fill(3, 1, Z1 - 1, 3, TOP, Z1 - 1, "minecraft:ladder[facing=north,waterlogged=false]")
# the gallery: a floor of full stone bricks all round on corbels, a fence rail
p.fill(0, GALLERY, 0, S - 1, GALLERY, S - 1, "bricks")
p.put(3, GALLERY, Z1 - 1, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]")
for x in range(X0, X1 + 1):
    p.put(x, TOP, 0, stairs_state("stone_brick", "south", "top"))
    p.put(x, TOP, S - 1, stairs_state("stone_brick", "north", "top"))
for z in range(Z0, Z1 + 1):
    p.put(0, TOP, z, stairs_state("stone_brick", "east", "top"))
    p.put(S - 1, TOP, z, stairs_state("stone_brick", "west", "top"))
for x in range(S):
    for z in range(S):
        if x in (0, S - 1) or z in (0, S - 1):
            p.put(x, GALLERY + 1, z, "fence")
# the lantern room: glass panes between corner posts round the light, a brick pyramid roof
LR = GALLERY + 1
LZ = 2                               # the lantern room's centre row (north of the hatch)
for x in range(2, 5):
    for z in range(LZ - 1, LZ + 2):
        if x in (2, 4) or z in (LZ - 1, LZ + 1):
            corner = x in (2, 4) and z in (LZ - 1, LZ + 1)
            p.fill(x, LR, z, x, LR + 2, z, "post" if corner else "pane")
p.put(3, LR, LZ, "minecraft:glowstone")
p.put(3, LR + 1, LZ, "minecraft:sea_lantern")
ROOF = LR + 3
for x in range(1, 6):
    for z in range(LZ - 2, LZ + 3):
        ring = max(abs(x - 3), abs(z - LZ))
        if ring == 2:
            facing = "south" if z == LZ - 2 else "north" if z == LZ + 2 else "east" if x == 1 else "west"
            p.put(x, ROOF, z, stairs_state("brick", facing))
        elif ring == 1:
            facing = "south" if z == LZ - 1 else "north" if z == LZ + 1 else "east" if x == 2 else "west"
            p.put(x, ROOF + 1, z, stairs_state("brick", facing))
p.put(3, ROOF + 1, LZ, "brick")
p.put(3, ROOF + 2, LZ, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
# by the door
p.put(5, 1, 0, "minecraft:barrel[facing=up,open=false]")
p.put(1, 1, 0, "pirates_n_ships:rope_coil[facing=north,layers=2]")

p.connector(3, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("a small lighthouse: a render tower banded with brick, a gallery with a rail, a glazed lantern room",
       ["Faces north: the door and the building_in connector are on the -z side."])
