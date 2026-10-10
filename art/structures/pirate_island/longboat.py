"""Generates a beached longboat with its nets (8x5x12 box) and prints the BuildSpec as JSON.

A piece of the ``huts`` pool (STR2): a ship's longboat hauled up and left lying in the sand, bow inland. A dark oak
keel and bottom sunk into the ground row, sides of dark oak planks with a spruce gunwale, a pointed bow and a square
transom of stairs, two thwarts, a pair of oars (stripped spruce poles) lying in it and one in the sand; beside it
fishing nets drying on driftwood poles (cobwebs), a basket of fish (a barrel), rope coils and a crate. ``hut_in`` on
the north edge, at the stern."""
from _camp import patch, rope_coil
from _style import PALETTE, jit
from buildspec import Piece

W, H, L = 8, 5, 12
X0, X1, ZS, ZB = 1, 5, 2, 10     # the hull: sides at x 1 and 5, transom at z 2, the bow's point at z 10
MID = 3

p = Piece("pirate_island_longboat", "Beached Longboat", (W, H, L), PALETTE)

STAIR = "minecraft:dark_oak_stairs[facing={f},half={h},shape=straight]"
# the ground: sand and coarse dirt round the boat, the keel's groove
patch(p, X0, ZS, X1, ZB, reach=1, salt=421, inner=("sand", "coarse", "sand", "gravel"))
# bottom and keel in the ground row
p.fill(X0 + 1, 0, ZS, X1 - 1, 0, ZB - 2, "dark")
p.fill(MID, 0, ZS, MID, 0, ZB - 1, "minecraft:dark_oak_log[axis=z]")
# the sides: planks with a spruce gunwale (slabs) on top, narrowing to the bow
for z in range(ZS, ZB - 1):
    for x in (X0, X1):
        p.put(x, 1, z, "dark" if jit(x, 1, z, 422) > 120 else "minecraft:jungle_planks")
        p.put(x, 2, z, "minecraft:spruce_slab[type=bottom]")
# the bow: the sides close in to a stem post
p.put(X0 + 1, 1, ZB - 1, STAIR.format(f="east", h="bottom"))
p.put(X1 - 1, 1, ZB - 1, STAIR.format(f="west", h="bottom"))
p.put(X0 + 1, 2, ZB - 1, "minecraft:spruce_slab[type=bottom]")
p.put(X1 - 1, 2, ZB - 1, "minecraft:spruce_slab[type=bottom]")
p.put(MID, 1, ZB - 1, "dark")
p.fill(MID, 1, ZB, MID, 2, ZB, "minecraft:dark_oak_log[axis=y]")
p.put(MID, 0, ZB, "dark_pile")
# the transom: stairs across the stern
for x in range(X0 + 1, X1):
    p.put(x, 1, ZS, STAIR.format(f="south", h="bottom"))
# thwarts and the oars lying across them
for z in (4, 7):
    p.fill(X0 + 1, 1, z, X1 - 1, 1, z, "minecraft:spruce_slab[type=bottom]")
p.fill(2, 1, 5, 2, 1, 6, "minecraft:stripped_spruce_log[axis=z]")
p.put(4, 1, 8, "minecraft:barrel[facing=north,open=false]")
p.put(4, 1, 5, "cobweb")
rope_coil(p, 4, 1, 3, layers=2)
# an oar in the sand beside the boat, nets drying on driftwood poles, fish, rope, a crate
p.fill(0, 1, 4, 0, 1, 7, "minecraft:stripped_spruce_log[axis=z]")
p.put(0, 1, 8, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false]")
for z in (3, 6, 9):
    p.fill(7, 1, z, 7, 2, z, "drift")
for z in (4, 5, 7, 8):
    p.put(7, 2, z, "cobweb")
p.put(7, 1, 5, "cobweb")
p.put(7, 3, 6, "fence")
p.put(6, 1, 1, "minecraft:barrel[facing=up,open=false]")
rope_coil(p, 7, 1, 1, layers=3)
p.put(1, 1, 0, "pirates_n_ships:cargo_crate")

p.connector(MID, 0, 0, "hut_in", "north", "minecraft:sand")

p.emit("a beached longboat with thwarts and oars, nets drying on driftwood poles, rope coils and a fish basket",
       ["Faces north: the stern and the hut_in connector are on the -z side; the bow points inland (south).",
        "y 0 is the foundation row: the keel and the bottom are sunk into it."])
