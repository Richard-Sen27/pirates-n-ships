"""Generates a straight seafarer village street (7 wide, 7 long, running north-south) and prints the BuildSpec as JSON.

Connectors: ``street_in`` on the north end (towards the dock head), ``street_out`` on the south end (the next street
or a terminator), one ``building_out`` on each side. A lantern post stands on the west verge."""
from _style import PALETTE, lantern_post
from buildspec import Piece

W = L = 7

p = Piece("village_street", "Seafarer Street", (W, 4, L), PALETTE)

# the roadway: cobblestone with a few gravel and mossy patches, dirt path verges
p.fill(1, 0, 0, W - 2, 0, L - 1, "cobble")
for x, z, key in ((2, 1, "gravel"), (4, 2, "mossy"), (3, 4, "gravel"), (5, 5, "gravel"), (1, 3, "mossy"),
                  (2, 6, "mossy")):
    p.put(x, 0, z, key)
p.fill(0, 0, 0, 0, 0, L - 1, "path")
p.fill(W - 1, 0, 0, W - 1, 0, L - 1, "path")

lantern_post(p, 0, 1, 5)

p.connector(3, 0, 0, "street_in", "north", "minecraft:cobblestone")
p.connector(3, 0, L - 1, "street_out", "south", "minecraft:cobblestone")
p.connector(0, 0, 3, "building_out", "west", "minecraft:dirt_path")
p.connector(W - 1, 0, 3, "building_out", "east", "minecraft:dirt_path")

p.emit("cobbled village street with dirt path verges",
       ["Runs along z: street_in at the north end, street_out at the south end.",
        "building_out connectors on both verges at z 3; buildings attach with their north (door) side."])
