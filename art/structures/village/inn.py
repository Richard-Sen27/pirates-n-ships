"""Generates the seafarer village's inn (9x7 footprint, two floors under a red tile roof; STR3) and prints the
BuildSpec as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep.

The smaller of the village's two taverns, built with ``_houses.cottage`` (two storeys, boarded gables): a hanging sign
"The Salty Anchor" on a fence bracket at the north-west corner, a lantern post at the other corner; downstairs a bar of a slab counter on
barrels with kegs behind it, two tables with stools and a hearth; a ladder up to two beds, a chest and a wash stand."""
from _houses import cottage, peak, tie_beam_lantern
from _style import PALETTE, TILE, bed, hanging_sign, table
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 8, 1, 7
p = Piece("village_inn", "The Salty Anchor (inn)", (X1 + 1, peak(Z0, Z1, 2) + 2, Z1 + 2), PALETTE)
top = cottage(p, X0, Z0, X1, Z1, storeys=2, roof=TILE, gable="boards", door_x=4, salt=431)

# the sign on a bracket west of the door, a lantern east of it
p.put(0, 3, 0, "fence")
hanging_sign(p, 0, 2, 0, 4, ["The Salty", "Anchor", "~ Inn ~"])
p.put(8, 2, 0, "minecraft:lantern[hanging=false,waterlogged=false]")
p.put(8, 1, 0, "fence")

# the bar along the back: a slab counter on barrels, kegs behind it
for x in range(2, 6):
    p.put(x, 1, 5, "cargo_barrel" if x % 2 else "minecraft:barrel[facing=north,open=false]")
    p.put(x, 2, 5, "minecraft:spruce_slab[type=bottom]")
for x in (2, 3, 5):
    p.put(x, 1, 6, "minecraft:barrel[facing=north,open=false]")
p.put(2, 2, 6, "minecraft:barrel[facing=north,open=false]")
p.put(4, 2, 5, "minecraft:lantern[hanging=false,waterlogged=false]")
# two tables with stools
for x in (2, 6):
    table(p, x, 1, 3)
    p.put(x + 1, 1, 3, "minecraft:spruce_stairs[facing=east,half=bottom]")
p.put(6, 1, 2, "minecraft:spruce_stairs[facing=south,half=bottom]")
p.put(4, 1, 3, "minecraft:red_carpet")
# the ladder up in the back west corner, the upper room
p.fill(1, 1, 6, 1, 4, 6, "minecraft:ladder[facing=north,waterlogged=false]")
p.put(1, 5, 6, "minecraft:spruce_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]")
bed(p, 4, 5, 5, "south", "red")
bed(p, 6, 5, 5, "south", "red")
p.put(7, 5, 2, "minecraft:chest[facing=west,type=single,waterlogged=false]")
p.put(1, 5, 2, "minecraft:cauldron")
p.put(2, 5, 3, "minecraft:red_carpet")
tie_beam_lantern(p, X0, X1, 4, top + 1)

p.connector(4, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("a small two-storey inn with a hanging sign, a bar downstairs and beds upstairs",
       ["Faces north: the door and the building_in connector are on the -z side.",
        "The hanging sign 'The Salty Anchor' hangs from a fence bracket at the north-west corner."])
