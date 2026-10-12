"""Generates the seafarer village's tall house (7x7 footprint, two floors under a red tile roof; STR3) and prints the
BuildSpec as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep.

Built with ``_houses.cottage`` (two storeys): the ground floor holds the kitchen (hearth, table and stool, barrel,
crafting table, a cupboard of barrels), a ladder up through the boards in the back corner; the upper floor two beds, a
chest, a bookshelf and a lantern. Flower pots on the upper front sills."""
from _houses import cottage, peak, tie_beam_lantern
from _style import PALETTE, TILE, bed, table
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 6, 1, 7
p = Piece("village_house_tall", "Seafarer House (tall)", (X1 + 1, peak(Z0, Z1, 2) + 2, Z1 + 2), PALETTE)
top = cottage(p, X0, Z0, X1, Z1, storeys=2, roof=TILE, gable="boards", salt=401)

# ground floor: kitchen
table(p, 1, 1, 4)
p.put(2, 1, 4, "minecraft:spruce_stairs[facing=east,half=bottom]")
p.put(1, 1, 2, "minecraft:crafting_table")
p.put(1, 2, 2, "minecraft:flower_pot")
p.put(3, 1, 6, "barrel_up")
p.put(4, 1, 6, "minecraft:barrel[facing=north,open=false]")
p.put(3, 2, 6, "lantern")
p.put(3, 1, 4, "minecraft:brown_carpet")
# the ladder through the upper floor in the back corner
p.fill(1, 1, 6, 1, 4, 6, "minecraft:ladder[facing=north,waterlogged=false]")
p.put(1, 5, 6, "minecraft:spruce_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]")
# upper floor: two beds, a chest, a bookshelf, a lantern
bed(p, 5, 5, 5, "south", "blue")
bed(p, 3, 5, 5, "south", "white")
p.loot_chest(5, 5, 2, "west", "village/house")
p.put(1, 5, 2, "minecraft:bookshelf")
p.put(2, 5, 3, "minecraft:white_carpet")
tie_beam_lantern(p, X0, X1, 4, top + 1)
# flower pots on the upper front sills
p.put(1, 6, 0, "minecraft:potted_red_tulip")
p.put(5, 6, 0, "minecraft:potted_cornflower")

p.connector(3, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("a tall two-storey village house: white render on stripped spruce posts, a spruce band, red tile roof",
       ["Faces north: the door and the building_in connector are on the -z side.",
        "y 0 is the foundation row (plinth and floor), level with the terrain surface."])
