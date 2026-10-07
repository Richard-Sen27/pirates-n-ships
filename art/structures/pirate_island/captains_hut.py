"""Generates the pirate captain's hut (9x5 rooms on stilts, a porch, a dark oak thatch roof) and prints the BuildSpec
as JSON. The steps, the door and the hut connector are on the north (-z) side.

The floor is raised one block on stripped log stilts (rows y 0..1; y 0 is the foundation row in the ground, so a
block of stilt shows), the porch runs along the front with a rail, and two stairs lead up from the sand at the hut
connector. Inside: a bed, a cartography table with a map tile on it and a stool, the captain's sea chest, a
hanging lantern and a cobweb."""
from _style import PALETTE, THATCH, bed, stool
from buildspec import Piece

X0, X1 = 0, 8
Z0, Z1 = 3, 7          # the room's walls
PORCH = 1              # the porch is z 1..2
FLOOR = 2              # the floor boards; walls y 3..4, roof plate y 5
TOP = 4

p = Piece("pirate_island_captains_hut", "Pirate Captain's Hut", (X1 + 1, 9, Z1 + 2), PALETTE)

p.fill(X0, 0, 0, X1, 0, Z1 + 1, "sand")

# stilts under the room's corners and middles and under the porch's front corners, on the sand
for x in (X0, 4, X1):
    for z in (Z0, Z1):
        p.fill(x, 0, z, x, FLOOR - 1, z, "dark_post")
for x in (1, 7):
    p.fill(x, 0, PORCH, x, FLOOR - 1, PORCH, "dark_post")

# the floor and the porch
p.fill(X0, FLOOR, Z0, X1, FLOOR, Z1, "spruce")
p.fill(1, FLOOR, PORCH, 7, FLOOR, Z0 - 1, "spruce")
p.fill(1, FLOOR, PORCH, 7, FLOOR, PORCH, "beam_x")
# two steps up from the sand at the connector
p.put(4, 1, 0, "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
p.put(4, FLOOR, PORCH, "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")

# walls: dark oak planks between stripped spruce posts
p.ring(X0, Z0, X1, Z1, FLOOR + 1, TOP, "dark")
for x in (X0, 4, X1):
    for z in (Z0, Z1):
        p.fill(x, FLOOR + 1, z, x, TOP, z, "post")
p.put(4, FLOOR + 1, Z0, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
p.put(4, FLOOR + 2, Z0, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
for x, z in ((2, Z0), (6, Z0), (X0, 5), (X1, 5), (2, Z1), (6, Z1)):
    p.put(x, FLOOR + 2, z, "minecraft:spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]"
          if z in (Z0, Z1) else "fence")

# porch: posts holding the front eave, a rail on both sides of the steps
for x in (1, 7):
    p.fill(x, FLOOR + 1, Z0 - 1, x, TOP, Z0 - 1, "post")
for x in (1, 2, 3, 5, 6, 7):
    p.put(x, FLOOR + 1, PORCH, "fence")

# roof: ridge along x, eave over the porch's back half (z 2) and the back (z 8)
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, THATCH, "thatch_ridge", "dark", plate="beam_x")
for z in (Z0, Z1):
    p.put(X0, TOP + 1, z, "post")
    p.put(X1, TOP + 1, z, "post")

# inside: the bed in the west corner, the map table and stool, the sea chest by the east wall
bed(p, 1, FLOOR + 1, 5, "south")
p.put(1, FLOOR + 1, 4, "minecraft:barrel[facing=up,open=false]")
p.put(1, FLOOR + 2, 4, "lantern")
p.put(5, FLOOR + 1, 6, "minecraft:cartography_table")
p.put(5, FLOOR + 2, 6, "pirates_n_ships:map_tile[face=floor,facing=north]")
stool(p, 5, FLOOR + 1, 5, "north")
p.put(7, FLOOR + 1, 6, "pirates_n_ships:sea_chest[facing=west]")
p.put(7, FLOOR + 1, 4, "pirates_n_ships:cargo_crate")
p.put(4, 7, 5, "lantern_hanging")
p.put(7, TOP, 6, "cobweb")

p.connector(4, 0, 0, "hut_in", "north", "minecraft:sand")

p.emit("captain's hut on stilts with a porch, bed, map table and sea chest under a dark oak thatch roof",
       ["Faces north: the steps, the door and the hut_in connector are on the -z side.",
        "y 0 is the foundation row (stilt feet in the ground); the floor is y 2."])
