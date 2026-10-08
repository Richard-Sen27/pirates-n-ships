"""Generates the pirate captain's hut (9x5 rooms on stilts, a porch, a dark oak thatch roof) and prints the BuildSpec
as JSON. The steps, the door and the hut connector are on the north (-z) side.

The floor is raised one block on stilts (rows y 0..1; y 0 is the foundation row in the ground, so a block of stilt
shows), the porch runs along the front with a rope rail, and two stairs lead up from the sand at the hut connector.
Inside: a bed, a cartography table with a map tile on it and a stool, the captain's sea chest, a hanging lantern and
a cobweb.

ST4b: palm trunk stilts at the corners, a sill beam of stripped dark oak round the floor, walls of vertical dark and
spruce boards between stripped corner posts, a door framed by dark posts, shuttered windows (sills on the back, where
the porch does not need the room), gables on a tie beam with a king post and a vent, rafter ends under the back eave,
a patched thatch roof, rope rails and a skull on the porch, a lantern post at the steps, salvage stowed between the
stilts, a rug and the captain's black banner inside, and gravel and coarse dirt round the stilts."""
from _style import (PALETTE, THATCH, bed, jit, lantern_post, rafter_ends, roof_patches, rope_rail, sand_skirt,
                    shutter_window, stool, weatherboard)
from buildspec import Piece

X0, X1 = 0, 8
Z0, Z1 = 3, 7          # the room's walls
PORCH = 1              # the porch is z 1..2
FLOOR = 2              # the floor boards; walls y 3..4, roof plate y 5
TOP = 4
DOOR = 4

p = Piece("pirate_island_captains_hut", "Pirate Captain's Hut", (X1 + 1, 9, Z1 + 2), PALETTE)

p.fill(X0, 0, 0, X1, 0, Z1 + 1, "sand")
# the shade under the hut: coarse dirt and gravel among the sand, the usual skirt in front and behind
for x in range(X0, X1 + 1):
    for z in range(PORCH, Z1 + 1):
        r = jit(x, 0, z, 41)
        if r < 330:
            p.put(x, 0, z, "coarse" if r < 220 else "gravel")
sand_skirt(p, X0, PORCH, X1, Z1, reach=1, salt=42)

# stilts: palm trunks under the room's corners and the porch's front corners, stripped dark oak in the middles
for x in (X0, X1):
    for z in (Z0, Z1):
        p.fill(x, 0, z, x, FLOOR - 1, z, "palm")
for z in (Z0, Z1):
    p.fill(DOOR, 0, z, DOOR, FLOOR - 1, z, "dark_post")
for x in (1, 7):
    p.fill(x, 0, PORCH, x, FLOOR - 1, PORCH, "palm")
# salvage stowed between the stilts
p.put(2, 1, 5, "minecraft:barrel[facing=east,open=false]")
p.put(6, 1, 4, "pirates_n_ships:cargo_crate")
p.put(6, 1, 6, "minecraft:barrel[facing=up,open=false]")

# the floor with a sill beam round its edge, and the porch
p.fill(X0, FLOOR, Z0, X1, FLOOR, Z1, "spruce")
p.fill(X0, FLOOR, Z0, X1, FLOOR, Z0, "dark_beam_x")
p.fill(X0, FLOOR, Z1, X1, FLOOR, Z1, "dark_beam_x")
p.fill(X0, FLOOR, Z0 + 1, X0, FLOOR, Z1 - 1, "dark_beam_z")
p.fill(X1, FLOOR, Z0 + 1, X1, FLOOR, Z1 - 1, "dark_beam_z")
p.fill(1, FLOOR, PORCH, 7, FLOOR, Z0 - 1, "spruce")
p.put(3, FLOOR, 2, "minecraft:jungle_planks")
p.fill(1, FLOOR, PORCH, 7, FLOOR, PORCH, "beam_x")
# two steps up from the sand at the connector
p.put(DOOR, 1, 0, "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight]")
p.put(DOOR, FLOOR, PORCH, "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight]")

# walls: vertical dark and spruce boards between stripped corner posts, the door between dark frame posts
p.ring(X0, Z0, X1, Z1, FLOOR + 1, TOP, "dark")
weatherboard(p, X0, FLOOR + 1, Z0, X1, TOP, Z0, salt=1)
weatherboard(p, X0, FLOOR + 1, Z1, X1, TOP, Z1, salt=2)
weatherboard(p, X0, FLOOR + 1, Z0, X0, TOP, Z1, salt=3)
weatherboard(p, X1, FLOOR + 1, Z0, X1, TOP, Z1, salt=4)
for x in (X0, X1):
    for z in (Z0, Z1):
        p.fill(x, FLOOR + 1, z, x, TOP, z, "post")
for x in (DOOR - 1, DOOR + 1):
    p.fill(x, FLOOR + 1, Z0, x, TOP, Z0, "dark_post")
p.fill(DOOR, FLOOR + 1, Z1, DOOR, TOP, Z1, "dark_post")
p.put(DOOR, FLOOR + 1, Z0, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
p.put(DOOR, FLOOR + 2, Z0, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")

# porch: palm posts holding the front eave, a rope rail on both sides of the steps, a skull on the east rail post
for x in (1, 7):
    p.fill(x, FLOOR + 1, Z0 - 1, x, TOP, Z0 - 1, "palm")
rope_rail(p, 1, PORCH, 3, PORCH, FLOOR + 1)
rope_rail(p, 5, PORCH, 7, PORCH, FLOOR + 1)
p.put(7, FLOOR + 2, PORCH, "minecraft:skeleton_skull[powered=false,rotation=8]")

# windows: shutters on the front (no sills: the porch is one block deep), shutters and sills on the back
for x in (2, 6):
    shutter_window(p, x, TOP, Z0, "north", sill=False, wood="dark_oak")
    shutter_window(p, x, TOP, Z1, "south", wood="dark_oak")

# roof: ridge along x, eave over the porch's back half (z 2) and the back (z 8), mended with spruce
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, THATCH, "thatch_ridge", "dark", plate="beam_x")
roof_patches(p, salt=51)
for z in (Z0, Z1):
    p.put(X0, TOP + 1, z, "post")
    p.put(X1, TOP + 1, z, "post")
# the gables: a tie beam, vertical boards, a king post and a vent
for x in (X0, X1):
    p.fill(x, TOP + 1, Z0 + 1, x, TOP + 1, Z1 - 1, "dark_beam_z")
    weatherboard(p, x, TOP + 2, Z0, x, TOP + 3, Z1, keys=("spruce", "dark"), salt=5 + x)
    p.put(x, TOP + 2, 5, "fence")
    p.put(x, TOP + 3, 5, "dark_post")
    p.put(x, TOP, 5, "fence")                 # the side window
rafter_ends(p, [(X0, Z1 + 1), (DOOR, Z1 + 1), (X1, Z1 + 1)], TOP, "north")

# a lantern post at the foot of the steps, a barrel on the other side
lantern_post(p, 3, 1, 0)
p.put(5, 1, 0, "barrel")

# inside: the bed in the west corner, the map table and stool, the sea chest by the east wall, a rug, the banner
bed(p, 1, FLOOR + 1, 5, "south")
p.put(1, FLOOR + 1, 4, "minecraft:barrel[facing=up,open=false]")
p.put(1, FLOOR + 2, 4, "lantern")
p.put(5, FLOOR + 1, 6, "minecraft:cartography_table")
p.put(5, FLOOR + 2, 6, "pirates_n_ships:map_tile[face=floor,facing=north]")
stool(p, 5, FLOOR + 1, 5, "north")
p.put(7, FLOOR + 1, 6, "pirates_n_ships:sea_chest[facing=west]")
p.put(7, FLOOR + 1, 4, "pirates_n_ships:cargo_crate")
p.fill(2, FLOOR + 1, 5, 3, FLOOR + 1, 6, "minecraft:red_carpet")
p.put(DOOR, TOP, Z1 - 1, "minecraft:black_wall_banner[facing=north]")
p.put(4, 7, 5, "lantern_hanging")
p.put(7, TOP, 6, "cobweb")

p.connector(DOOR, 0, 0, "hut_in", "north", "minecraft:sand")

p.emit("captain's hut on palm stilts with a roped porch, shuttered windows, bed, map table and sea chest under a "
       "patched dark oak thatch roof",
       ["Faces north: the steps, the door and the hut_in connector are on the -z side.",
        "y 0 is the foundation row (stilt feet in the ground); the floor is y 2."])
