"""Generates the camp's powder store (a small, squat stone magazine, 5x5x7 box) and prints the BuildSpec as JSON.

A building of the ``huts`` pool (STR2), kept small and away from the fires: thick walls of cobblestone going mossy at
the foot, a stone slab roof with a turf of moss on it, an iron door in a frame of stone bricks, a barred vent at the
back, and inside powder kegs (barrels and cargo barrels) on a plank floor; outside a sand bank against the walls, a
skull on a stake as a warning and a bucket of sand by the door. ``hut_in`` at the door, on the north edge."""
from _camp import patch, skull_post
from _style import PALETTE, jit
from buildspec import Piece

W, H, L = 5, 5, 7
X0, X1, Z0, Z1 = 0, 4, 1, 5
DOOR = 2

p = Piece("pirate_island_powder_store", "Pirate Powder Store", (W, H, L), PALETTE)

p.fill(X0, 0, Z0, X1, 0, Z1, "cobble")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
for y in (1, 2):
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if x in (X0, X1) or z in (Z0, Z1):
                r = jit(x, y, z, 401)
                p.put(x, y, z, "mossy" if (y == 1 and r < 600) or r < 200 else "cobble")
# the roof: stone slabs over a cobblestone course, moss growing on it
p.fill(X0, 3, Z0, X1, 3, Z1, "cobble")
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        r = jit(x, 4, z, 402)
        p.put(x, 4, z, "minecraft:moss_carpet" if r < 350 else "minecraft:cobblestone_slab[type=bottom]")
# the door in a frame of stone bricks, a barred vent at the back
for y in (1, 2):
    p.put(DOOR - 1, y, Z0, "minecraft:stone_bricks")
    p.put(DOOR + 1, y, Z0, "minecraft:mossy_stone_bricks")
p.put(DOOR, 3, Z0, "minecraft:stone_bricks")
p.put(DOOR, 1, Z0, "minecraft:iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
p.put(DOOR, 2, Z0, "minecraft:iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
p.put(DOOR, 2, Z1, "minecraft:iron_bars")
# inside: powder kegs
p.put(1, 1, 2, "minecraft:barrel[facing=up,open=false]")
p.put(1, 1, 3, "minecraft:barrel[facing=east,open=false]")
p.put(1, 1, 4, "pirates_n_ships:cargo_barrel")
p.put(3, 1, 4, "pirates_n_ships:cargo_barrel")
p.put(3, 1, 3, "minecraft:barrel[facing=up,open=false]")
p.put(1, 2, 4, "minecraft:barrel[facing=up,open=false]")
# outside: a sand bank against the walls, a warning skull, a bucket of sand (a cauldron)
patch(p, X0, Z0, X1, Z1, reach=1, salt=403, inner=("sand", "sand", "gravel"))
skull_post(p, 0, 1, 0, rotation=8, height=2)
p.put(4, 1, 0, "minecraft:cauldron")
p.put(4, 1, Z1 + 1, "sand")

p.connector(DOOR, 0, 0, "hut_in", "north", "minecraft:coarse_dirt")

p.emit("a small squat powder store of mossy cobblestone with an iron door, powder kegs and a warning skull",
       ["Faces north: the iron door and the hut_in connector are on the -z side.",
        "y 0 is the foundation row."])
