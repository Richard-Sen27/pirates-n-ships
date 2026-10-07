"""Generates the seafarer village shipwright's shed (11x9, open to the north and the sides, a dark roof with the ridge
along z) and prints the BuildSpec as JSON. The building connector is on the north (-z) side; row z 0 is the yard
apron in front of the open shed.

Inside: a half-built hull frame of stripped logs (keel, stem, three ribs, a strake of planks), sawhorses, a stack of
logs, stacked planks and a workbench along the closed back wall."""
from _style import DARK, PALETTE, lantern_post
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 10, 1, 9          # the posts' outline
EAVE = 5                              # top of the posts
BEAM = 6                              # the beams on the posts; the roof starts above them

p = Piece("village_shipwright", "Shipwright's Shed", (X1 + 1, 13, Z1 + 1), PALETTE)

# floor: planks with a gravel slipway down the middle, footings under the posts
p.fill(X0, 0, Z0, X1, 0, Z1, "spruce")
p.fill(4, 0, Z0, 6, 0, Z1 - 1, "gravel")
p.fill(3, 0, 0, 7, 0, 0, "gravel")

# posts and beams: open front and sides, a closed plank back wall
posts = [(x, z) for x in (X0, X1) for z in (Z0, 5, Z1)] + [(3, Z1), (7, Z1)]
for x, z in posts:
    p.put(x, 0, z, "cobble")
    p.fill(x, 1, z, x, BEAM - 1, z, "post")
p.fill(X0, BEAM, Z0, X0, BEAM, Z1, "beam_z")
p.fill(X1, BEAM, Z0, X1, BEAM, Z1, "beam_z")
for z in (Z0, 5, Z1):
    p.fill(X0 + 1, BEAM, z, X1 - 1, BEAM, z, "beam_x")
for x in range(X0 + 1, X1):
    if (x, Z1) not in posts:
        p.fill(x, 1, Z1, x, BEAM - 1, Z1, "spruce")

# roof: ridge along z over the whole shed (it overhangs the apron at z 0), gable closed at the back
p.roof_ridge_z(0, Z1, X0, X1, BEAM + 1, DARK, "dark_ridge", gable="spruce", gable_ends=(False, True))
p.put(1, BEAM - 1, Z0, "lantern_hanging")
p.put(9, BEAM - 1, Z0, "lantern_hanging")
p.put(5, BEAM - 1, 5, "lantern_hanging")

# the hull frame: keel along z on the slipway, stem post at the bow (north), three ribs, a strake of planks
p.fill(5, 1, 3, 5, 1, 8, "minecraft:stripped_oak_log[axis=z]")
p.fill(5, 2, 3, 5, 4, 3, "oak_post")
for z in (4, 6, 8):
    p.fill(3, 1, z, 4, 1, z, "oak_beam_x")        # floor timbers either side of the keel
    p.fill(6, 1, z, 7, 1, z, "oak_beam_x")
    p.fill(3, 2, z, 3, 4, z, "oak_post")          # the frames rising from them
    p.fill(7, 2, z, 7, 4, z, "oak_post")
for z in (5, 7):                                 # the garboard strake between the frames
    p.fill(3, 1, z, 4, 1, z, "oak")
    p.fill(6, 1, z, 7, 1, z, "oak")
p.put(3, 2, 5, "oak")
p.put(7, 2, 5, "oak")

# sawhorses (a beam on two fence legs) with a plank on one, a stack of logs, stacked planks, the workbench
for x in (1, 9):
    p.put(x, 1, 2, "fence")
    p.put(x, 1, 4, "fence")
    p.fill(x, 2, 2, x, 2, 4, "beam_z")
p.put(1, 3, 3, "minecraft:spruce_slab[type=bottom]")
p.fill(9, 1, 6, 9, 1, 8, "minecraft:spruce_log[axis=z]")
p.fill(9, 2, 6, 9, 2, 7, "minecraft:spruce_log[axis=z]")
p.fill(8, 1, 8, 8, 1, 8, "minecraft:spruce_log[axis=z]")
p.fill(1, 1, 6, 1, 2, 6, "oak")
p.put(1, 1, 7, "oak")
p.put(1, 1, 8, "minecraft:crafting_table")
p.put(2, 1, 8, "minecraft:smithing_table")
p.put(8, 1, 1, "minecraft:barrel[facing=up]")
p.put(8, 2, 1, "lantern")

# the apron: a lantern post either side of the way in, the connector in the middle
lantern_post(p, 3, 1, 0)
lantern_post(p, 7, 1, 0)
p.connector(5, 0, 0, "building_in", "north", "minecraft:gravel")

p.emit("open shipwright's shed on stripped spruce posts with a dark roof and a half-built hull",
       ["Faces north: the building_in connector is on the -z side, row z 0 is the apron.",
        "The keel lies on a gravel slipway along z; the shipwright NPC and orders come with WG1 / §4.1."])
