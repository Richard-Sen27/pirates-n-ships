"""Generates a long seafarer village street (7 wide, 13 long, north-south; STR3) and prints the BuildSpec as JSON.

The longer straight of the STR3 street network: the cobbled crown between stone brick kerbs and dirt path verges
(``_streets.lay``), a lantern post and a barrel with a flower pot on the verges. Side connectors alternate, so a
building and a decoration face each other across the street: ``building_out`` west and ``deco_out`` east at z 3,
``deco_out`` west and ``building_out`` east at z 9. ``street_in`` north, ``street_out`` south."""
from _streets import lay, line
from _style import PALETTE, lantern_post
from buildspec import Piece

W, L = 7, 13
p = Piece("village_street_long", "Seafarer Street (long)", (W, 4, L), PALETTE)

lay(p, line(3, -4, 3, L + 3), salt=341)
lantern_post(p, W - 1, 1, 6)
p.put(0, 1, 6, "barrel_up")
p.put(0, 2, 6, "minecraft:flower_pot")
p.put(0, 1, L - 1, "minecraft:spruce_stairs[facing=west,half=bottom]")

p.connector(3, 0, 0, "street_in", "north", "minecraft:cobblestone")
p.connector(3, 0, L - 1, "street_out", "south", "minecraft:cobblestone")
p.connector(0, 0, 3, "building_out", "west", "minecraft:dirt_path")
p.connector(W - 1, 0, 3, "deco_out", "east", "minecraft:dirt_path")
p.connector(0, 0, 9, "deco_out", "west", "minecraft:dirt_path")
p.connector(W - 1, 0, 9, "building_out", "east", "minecraft:dirt_path")

p.emit("a long cobbled street between stone brick kerbs and dirt path verges",
       ["Runs along z: street_in at the north end, street_out at the south end.",
        "building_out west at z 3 and east at z 9, deco_out east at z 3 and west at z 9."])
