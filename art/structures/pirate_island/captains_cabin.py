"""Generates the captain's cabin in a beached stern (the after part of a wrecked ship hauled up the beach and propped
on log cradles, 9x9x11 box) and prints the BuildSpec as JSON.

A captain's hut variant of the ``huts`` pool (STR2): the hull's sides of dark oak planks curve in to a keel of dark
oak logs, the cabin's floor is at y 2 (like the captain's hut), a bulkhead with a door faces the camp up two steps,
the stern carries three stern windows framed in spruce with a lantern between them, and the old quarterdeck on top
has a rail of dark oak fences, a lantern post and the captain's black banner. Inside: a bed, the map table with a map
tile and a stool, the sea chest, a crate, a rug and a hanging lantern; the captain's post (4, 3, 5) stays free, one
block in from the door (4, 3, 3), as in the captain's hut."""
from _camp import patch, rope_coil
from _style import PALETTE, bed, jit, stool, trapdoor
from buildspec import Piece

W, H, L = 9, 9, 11
X0, X1 = 0, 8
Z0, Z1 = 3, 9          # bulkhead and stern
FLOOR = 2
DECK = 6
DOOR = 4

p = Piece("pirate_island_captains_cabin", "Pirate Captain's Cabin (beached stern)", (W, H, L), PALETTE)

HULL = "dark"
# the cradles: stripped logs across under the hull, propped on stones, the keel along the middle
for z in (4, 8):
    p.fill(1, 0, z, 7, 0, z, "dark_beam_x")
    p.put(1, 1, z, "cobble")
    p.put(7, 1, z, "mossy")
p.fill(DOOR, 0, Z0, DOOR, 0, Z1, "minecraft:dark_oak_log[axis=z]")
p.fill(DOOR, 1, Z0, DOOR, 1, Z1, "minecraft:dark_oak_log[axis=z]")
patch(p, X0, Z0, X1, Z1, reach=1, salt=371)
p.fill(2, 0, 5, 6, 0, 7, "sand")

# the hull: the bottom turns in (upside-down stairs), the sides rise straight to the deck
for z in range(Z0, Z1 + 1):
    p.fill(2, 1, z, 3, 1, z, "minecraft:dark_oak_stairs[facing=west,half=top,shape=straight]")
    p.fill(5, 1, z, 6, 1, z, "minecraft:dark_oak_stairs[facing=east,half=top,shape=straight]")
    p.put(1, FLOOR, z, "minecraft:dark_oak_stairs[facing=west,half=top,shape=straight]")
    p.put(7, FLOOR, z, "minecraft:dark_oak_stairs[facing=east,half=top,shape=straight]")
    for y in range(FLOOR + 1, DECK):
        for x in (X0, X1):
            r = jit(x, y, z, 372)
            p.put(x, y, z, HULL if r > 120 else "minecraft:jungle_planks")
    p.put(X0, DECK - 1, z, "minecraft:stripped_dark_oak_log[axis=z]")
    p.put(X1, DECK - 1, z, "minecraft:stripped_dark_oak_log[axis=z]")
p.fill(2, FLOOR, Z0, 6, FLOOR, Z1, "spruce")
p.put(3, FLOOR, 6, "dark")
p.put(5, FLOOR, 8, "minecraft:jungle_planks")
# the bulkhead: boards, the door in the middle, a small window either side
p.fill(1, FLOOR + 1, Z0, 7, DECK - 1, Z0, "spruce")
for x in (1, 3, 5, 7):
    p.fill(x, FLOOR + 1, Z0, x, DECK - 1, Z0, "dark")
p.put(DOOR, FLOOR + 1, Z0, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
p.put(DOOR, FLOOR + 2, Z0, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
for x in (2, 6):
    p.put(x, FLOOR + 2, Z0, "fence")
p.put(DOOR, DECK - 1, Z0, "minecraft:dark_oak_planks")
# the steps up to the door
p.put(DOOR, 1, Z0 - 2, "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight]")
p.put(DOOR, FLOOR, Z0 - 1, "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight]")
p.put(DOOR, 1, Z0 - 1, "spruce")
# the stern: boards with three stern windows framed in spruce, a lantern between them
p.fill(1, FLOOR + 1, Z1, 7, DECK - 1, Z1, HULL)
for x in (2, 4, 6):
    p.put(x, FLOOR + 2, Z1, "minecraft:glass_pane")
    p.put(x, FLOOR + 1, Z1 + 1, trapdoor("south", "top", False))
    p.put(x, FLOOR + 3, Z1 + 1, trapdoor("south", "bottom", False))
p.put(3, FLOOR + 3, Z1 + 1, "minecraft:lantern[hanging=false]")
p.put(5, FLOOR + 3, Z1 + 1, "minecraft:dark_oak_fence")
p.fill(1, DECK, Z1 + 1, 7, DECK, Z1 + 1, "minecraft:dark_oak_slab[type=bottom]")

# the quarterdeck: planks over the cabin, a rail round it, a lantern post and the banner
p.fill(X0, DECK, Z0, X1, DECK, Z1, "spruce")
for x, z in ((2, 4), (6, 7), (4, 9), (1, 6)):
    p.put(x, DECK, z, "dark")
for x in range(X0, X1 + 1):
    p.put(x, DECK + 1, Z0, "dark_fence")
    p.put(x, DECK + 1, Z1, "dark_fence")
for z in range(Z0 + 1, Z1):
    p.put(X0, DECK + 1, z, "dark_fence")
    p.put(X1, DECK + 1, z, "dark_fence")
p.clear(7, DECK + 1, Z0)                 # the gap in the rail at the top of the ladder
p.put(1, DECK + 2, Z1, "lantern")
p.put(7, DECK + 1, Z1 - 1, "minecraft:black_banner[rotation=8]")
rope_coil(p, 6, DECK + 1, Z0 + 1, layers=2)
p.put(2, DECK + 1, Z1 - 1, "pirates_n_ships:cargo_crate")
# a ladder up the bulkhead to the quarterdeck
for y in range(FLOOR + 1, DECK + 1):
    p.put(7, y, Z0 - 1, "minecraft:ladder[facing=north,waterlogged=false]")

# inside: the bed against the west side, the map table and stool at the stern, the sea chest, a crate, a rug
bed(p, 2, FLOOR + 1, 6, "south")
p.put(2, FLOOR + 1, 5, "minecraft:barrel[facing=up,open=false]")
p.put(2, FLOOR + 2, 5, "lantern")
p.put(5, FLOOR + 1, Z1 - 1, "minecraft:cartography_table")
p.put(5, FLOOR + 2, Z1 - 1, "pirates_n_ships:map_tile[face=floor,facing=north]")
stool(p, 5, FLOOR + 1, Z1 - 2, "north")
p.put(6, FLOOR + 1, 5, "pirates_n_ships:sea_chest[facing=west]")
p.put(6, FLOOR + 1, 4, "pirates_n_ships:cargo_crate")
p.fill(3, FLOOR + 1, 6, 4, FLOOR + 1, 7, "minecraft:red_carpet")
p.put(4, DECK - 1, 6, "lantern_hanging")
p.put(6, DECK - 1, 8, "cobweb")

p.connector(DOOR, 0, 0, "hut_in", "north", "minecraft:sand")

p.emit("captain's cabin in a beached ship's stern on log cradles, stern windows, a quarterdeck with the banner",
       ["Faces north: the steps, the door and the hut_in connector are on the -z side.",
        "y 0 is the foundation row (cradles and keel); the cabin's floor is y 2, the quarterdeck y 6."])
