"""Generates a street end that stops at a little green (7x6; STR3, a terminator) and prints the BuildSpec as JSON.

The cobbles run two rows in, the kerbs turn across the end, and beyond them a patch of grass with flowers round a young
oak, a bench on either side looking back up the street and a lantern post. Connector: ``street_in`` on the north end."""
from _streets import green, lay, line
from _style import PALETTE, bench, lantern_post, tree
from buildspec import Piece

W, L = 7, 6
p = Piece("village_street_end_green", "Seafarer Street End (green)", (W, 7, L), PALETTE)

lay(p, line(3, -4, 3, 1), salt=381)
p.fill(1, 0, 2, W - 2, 0, 2, "bricks")
p.put(2, 0, 2, "bricks_mossy")
for x in (0, W - 1):
    p.put(x, 0, 2, "path")
green(p, [(x, z) for x in range(W) for z in range(3, L) if (x, z) not in ((0, L - 1), (W - 1, L - 1))], salt=382, flowers=450)
tree(p, 3, 1, 4, height=3, crown=2, salt=383)
bench(p, 1, 1, 3, "north")
bench(p, W - 2, 1, 3, "north")
lantern_post(p, 0, 1, 3)

p.connector(3, 0, 0, "street_in", "north", "minecraft:cobblestone")

p.emit("a street end at a little green with a young oak, flowers, benches and a lantern",
       ["Terminator: attaches to a street's street_out with its north end (street_in) and spawns nothing."])
