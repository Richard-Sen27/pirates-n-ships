"""Generates the end of a seafarer village street (7 wide, 3 long) and prints the BuildSpec as JSON.

The terminator piece (pool ``village/terminators``): the fallback for a street's ``street_out`` once the jigsaw depth
runs out, so streets end in a cobbled turning place instead of an open edge. Connector: ``street_in`` on the north
end. A lantern post and two barrels stand at the far edge."""
from _style import PALETTE, lantern_post
from buildspec import Piece

W, L = 7, 3

p = Piece("village_street_end", "Seafarer Street End", (W, 4, L), PALETTE)

# the roadway widens into a small cobbled place, rounded off at the far corners with dirt path
p.fill(1, 0, 0, W - 2, 0, L - 1, "cobble")
p.fill(0, 0, 0, 0, 0, L - 2, "path")
p.fill(W - 1, 0, 0, W - 1, 0, L - 2, "path")
p.put(0, 0, L - 1, "coarse")
p.put(W - 1, 0, L - 1, "coarse")
for x, z, key in ((2, 1, "mossy"), (4, 2, "gravel")):
    p.put(x, 0, z, key)

lantern_post(p, 3, 1, L - 1)
p.put(1, 1, L - 1, "minecraft:barrel[facing=up,open=false]")
p.put(5, 1, L - 1, "minecraft:barrel[facing=up,open=false]")

p.connector(3, 0, 0, "street_in", "north", "minecraft:cobblestone")

p.emit("cobbled street end with a lantern post and barrels",
       ["Terminator: attaches to a street's street_out with its north end (street_in) and spawns nothing."])
