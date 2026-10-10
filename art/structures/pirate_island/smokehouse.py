"""Generates the camp's smokehouse with its drying racks (a 5x5 fieldstone-and-board hut that smokes, a rack of fish
beside it, 9x8x7 box) and prints the BuildSpec as JSON.

A building of the ``huts`` pool (STR2): a low hut with a mossy fieldstone base course and dark boards above, a sloping
board roof, a smoker inside behind the door and a smoking campfire in a stone flue on the roof; beside it two drying
racks of driftwood poles with fish strung on them (open spruce trapdoors) and bundles of dried kelp, a barrel of salt
and a chopping block. ``hut_in`` at the door, on the north edge."""
from _camp import patch
from _style import PALETTE, chimney, jit, trapdoor, weatherboard
from buildspec import Piece

W, H, L = 9, 8, 7
X0, X1, Z0, Z1 = 0, 4, 1, 5     # the smokehouse
DOOR = 2

p = Piece("pirate_island_smokehouse", "Pirate Smokehouse", (W, H, L), PALETTE)

# the hut: a fieldstone base course, dark boards above, a single-slope board roof falling to the back
p.fill(X0, 0, Z0, X1, 0, Z1, "cobble")
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "coarse")
p.ring(X0, Z0, X1, Z1, 1, 1, "cobble")
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        if p.get(x, 1, z) == "cobble" and jit(x, 1, z, 391) < 450:
            p.put(x, 1, z, "mossy")
p.ring(X0, Z0, X1, Z1, 2, 3, "dark")
weatherboard(p, X0, 2, Z0, X1, 3, Z1, salt=392)
for x in (X0, X1):
    for z in (Z0, Z1):
        p.fill(x, 1, z, x, 3, z, "post")
p.put(DOOR, 1, Z0, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
p.put(DOOR, 2, Z0, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
p.put(X0, 2, 3, "fence")
for z in range(Z0 - 1, Z1 + 2):
    for x in range(X0, X1 + 1):
        y = 4 if z <= 3 else 3
        if z == 3:
            p.put(x, 4, z, "minecraft:spruce_slab[type=bottom]")
        elif z < 3:
            p.put(x, 4, z, "minecraft:dark_oak_slab[type=bottom]" if jit(x, 4, z, 393) > 150 else
                  "minecraft:spruce_slab[type=bottom]")
        else:
            p.put(x, 3, z, "minecraft:dark_oak_slab[type=top]")
# the flue: fieldstone through the roof with the smoke on top; the smoker inside
chimney(p, 3, 4, 1, 5)
p.put(1, 1, 4, "minecraft:smoker[facing=east,lit=true]")
p.put(1, 1, 2, "minecraft:barrel[facing=up,open=false]")
p.put(3, 1, 2, "minecraft:dried_kelp_block")

# the drying racks: driftwood poles with a bar of chain, fish (open trapdoors) hung on both faces, kelp bundles
for rz in (2, 4):
    p.put(6, 1, rz, "drift")
    p.put(6, 2, rz, "drift")
    p.put(8, 1, rz, "drift")
    p.put(8, 2, rz, "drift")
    p.put(7, 2, rz, "chain_x")
    p.put(7, 1, rz, trapdoor("north" if rz == 2 else "south", "bottom", True))
p.put(7, 3, 2, "minecraft:dried_kelp_block")
p.put(7, 3, 4, "minecraft:spruce_slab[type=bottom]")
# a chopping block, a salt barrel and a basket of fish by the racks
p.put(7, 1, 0, "minecraft:stripped_spruce_log[axis=y]")
p.put(8, 1, 0, "minecraft:barrel[facing=up,open=false]")
p.put(6, 1, 5, "pirates_n_ships:cargo_barrel")
p.fill(5, 0, 1, 8, 0, 5, "coarse")
for x, z in ((5, 3), (8, 3), (6, 1)):
    p.put(x, 0, z, "gravel")
patch(p, X0, Z0, 8, Z1, reach=1, salt=394)

p.connector(DOOR, 0, 0, "hut_in", "north", "minecraft:coarse_dirt")

p.emit("a smokehouse of fieldstone and boards with a smoking flue, and drying racks of fish beside it",
       ["Faces north: the door and the hut_in connector are on the -z side.",
        "y 0 is the foundation row."])
