"""Generates the end of a seafarer village street (7 wide, 3 long) and prints the BuildSpec as JSON.

The terminator piece (pool ``village/terminators``): the fallback for a street's ``street_out`` once the jigsaw depth
runs out, so streets end in a cobbled turning place instead of an open edge. Connector: ``street_in`` on the north
end. A lantern post between two benches and two barrels stand at the
far edge."""
from _style import PALETTE, lantern_post
from buildspec import Piece

W, L = 7, 3

p = Piece("village_street_end", "Seafarer Street End", (W, 4, L), PALETTE)

# the roadway widens into a small cobbled place, rounded off at the far corners with dirt path
p.fill(1, 0, 0, W - 2, 0, L - 1, "cobble")
p.fill(1, 0, 0, 1, 0, L - 2, "bricks")            # the street's kerbs run in and turn along the far edge
p.fill(W - 2, 0, 0, W - 2, 0, L - 2, "bricks")
p.fill(1, 0, L - 1, W - 2, 0, L - 1, "bricks")
p.put(4, 0, L - 1, "bricks_mossy")
p.fill(0, 0, 0, 0, 0, L - 2, "path")
p.fill(W - 1, 0, 0, W - 1, 0, L - 2, "path")
p.put(0, 0, L - 1, "coarse")
p.put(W - 1, 0, L - 1, "coarse")
for x, z, key in ((2, 1, "mossy"), (4, 1, "gravel")):
    p.put(x, 0, z, key)

# along the far edge: a lantern post between two benches, a barrel at either end (one with a flower pot)
lantern_post(p, 3, 1, L - 1)
p.put(2, 1, L - 1, "minecraft:spruce_stairs[facing=south,half=bottom]")
p.put(4, 1, L - 1, "minecraft:spruce_stairs[facing=south,half=bottom]")
p.put(1, 1, L - 1, "minecraft:barrel[facing=up,open=false]")
p.put(5, 1, L - 1, "minecraft:barrel[facing=up,open=false]")
p.put(5, 2, L - 1, "minecraft:flower_pot")

p.connector(3, 0, 0, "street_in", "north", "minecraft:cobblestone")

p.emit("cobbled street end with kerbs, a lantern post, benches and barrels",
       ["Terminator: attaches to a street's street_out with its north end (street_in) and spawns nothing."])
