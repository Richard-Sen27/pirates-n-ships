"""Generates a straight seafarer village street (7 wide, 7 long, running north-south) and prints the BuildSpec as JSON.

Connectors: ``street_in`` on the north end (towards the dock head), ``street_out`` on the south end (the next street
or a terminator), one ``building_out`` on each side. Stone brick kerbs line the cobbled roadway; a lantern post stands
on the west verge, a barrel on the east one."""
from _style import PALETTE, lantern_post
from buildspec import Piece

W = L = 7

p = Piece("village_street", "Seafarer Street", (W, 4, L), PALETTE)

# the roadway: a cobblestone crown with a few gravel and mossy patches between stone brick kerbs (weathered here and
# there), dirt path verges
p.fill(2, 0, 0, W - 3, 0, L - 1, "cobble")
for x, z, key in ((2, 1, "gravel"), (4, 2, "mossy"), (3, 4, "gravel"), (4, 5, "gravel"), (2, 3, "mossy"),
                  (2, 6, "mossy"), (3, 0, "gravel")):
    p.put(x, 0, z, key)
for x in (1, W - 2):
    p.fill(x, 0, 0, x, 0, L - 1, "bricks")
for x, z, key in ((1, 1, "bricks_mossy"), (1, 4, "bricks_cracked"), (5, 2, "bricks_mossy"), (5, 6, "bricks_cracked"),
                  (5, 4, "bricks_mossy")):
    p.put(x, 0, z, key)
p.fill(0, 0, 0, 0, 0, L - 1, "path")
p.fill(W - 1, 0, 0, W - 1, 0, L - 1, "path")
for x, z in ((0, 0), (W - 1, 5)):
    p.put(x, 0, z, "coarse")

# a lantern post on the west verge, a barrel with a flower pot on the east verge (both clear of the building doors at
# z 3)
lantern_post(p, 0, 1, 5)
p.put(W - 1, 1, 6, "minecraft:barrel[facing=up]")
p.put(W - 1, 2, 6, "minecraft:flower_pot")

p.connector(3, 0, 0, "street_in", "north", "minecraft:cobblestone")
p.connector(3, 0, L - 1, "street_out", "south", "minecraft:cobblestone")
p.connector(0, 0, 3, "building_out", "west", "minecraft:dirt_path")
p.connector(W - 1, 0, 3, "building_out", "east", "minecraft:dirt_path")

p.emit("cobbled village street with stone brick kerbs and dirt path verges",
       ["Runs along z: street_in at the north end, street_out at the south end.",
        "building_out connectors on both verges at z 3; buildings attach with their north (door) side."])
