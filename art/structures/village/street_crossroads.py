"""Generates a crossroads of the seafarer village street (11x11; STR3) and prints the BuildSpec as JSON.

The street comes in from the north (``street_in``) and leaves west, east and south (three ``street_out``); the kerbs
round the four corners (``_streets.lay``). In the corners: a lantern post, a barrel with a flower pot, a bench and a
signpost of stacked fence posts with a lantern."""
from _streets import lay, line
from _style import PALETTE, bench, lantern_post
from buildspec import Piece

S = 11
C = 5
p = Piece("village_street_crossroads", "Seafarer Street (crossroads)", (S, 4, S), PALETTE)

lay(p, line(C, -4, C, S + 3) + line(-4, C, S + 3, C), salt=351)
for x, z in ((0, 0), (1, 0), (0, 1), (S - 1, S - 1), (S - 2, S - 1), (S - 1, S - 2)):
    p.put(x, 0, z, "coarse")
for x, z in ((1, 1), (S - 2, S - 2)):
    p.put(x, 0, z, "path")
lantern_post(p, 1, 1, 1)
lantern_post(p, S - 2, 1, S - 2)
p.put(S - 1, 0, 0, "coarse")
p.put(S - 1, 1, 0, "barrel_up")
p.put(S - 1, 2, 0, "minecraft:flower_pot")
p.put(0, 0, S - 1, "coarse")
bench(p, 0, 1, S - 1, "east")

p.connector(C, 0, 0, "street_in", "north", "minecraft:cobblestone")
p.connector(0, 0, C, "street_out", "west", "minecraft:cobblestone")
p.connector(S - 1, 0, C, "street_out", "east", "minecraft:cobblestone")
p.connector(C, 0, S - 1, "street_out", "south", "minecraft:cobblestone")

p.emit("a cobbled crossroads with rounded kerbs, lantern posts and a bench in the corners",
       ["street_in at [5, 0, 0] (north); street_out at [0, 0, 5] (west), [10, 0, 5] (east) and [5, 0, 10] (south)."])
