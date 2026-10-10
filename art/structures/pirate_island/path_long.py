"""Generates a long pirate island trail (7 wide, 11 long, north-south, the bed wandering a little) and prints the
BuildSpec as JSON.

The longer straight of the STR2 path network: the 3-wide bed of packed sand, gravel and trodden dirt swings half a
block to the east and back between driftwood edges, a rope coil and a crate lie on the verge, a stake carries a
lantern. Side connectors alternate: ``hut_out`` west and ``deco_out`` east at z 3, ``deco_out`` west and ``hut_out``
east at z 8, so a hut and a decoration face each other across the trail. ``path_in`` north, ``path_out`` south."""
import math

from _camp import band, edging, rope_coil, trail
from _style import PALETTE, lantern_post
from buildspec import Piece

W, L = 7, 11
SIZE = (W, 4, L)

p = Piece("pirate_island_path_long", "Pirate Trail (long)", SIZE, PALETTE)

# the centre line wanders: x 3 at both ends (the connectors), up to x 3.8 in the middle
centre = [(3 + 0.8 * math.sin(math.pi * z / (L - 1)), z) for z in [i / 10 for i in range(0, (L - 1) * 10 + 1)]]
bed = band(centre, SIZE)
spurs = {(0, 3), (1, 3), (5, 3), (6, 3), (0, 8), (1, 8), (5, 8), (6, 8)}
trail(p, bed | spurs, salt=161)
edging(p, bed, SIZE, salt=162, skip=spurs | {(1, 2), (1, 4), (5, 2), (5, 4), (1, 7), (1, 9), (5, 7), (5, 9)})
rope_coil(p, 1, 1, 5, layers=2, facing="east")
p.put(6, 1, 6, "pirates_n_ships:cargo_crate")
lantern_post(p, 1, 1, 10, height=1, post="drift")

p.connector(3, 0, 0, "path_in", "north", "minecraft:gravel")
p.connector(3, 0, L - 1, "path_out", "south", "minecraft:gravel")
p.connector(0, 0, 3, "hut_out", "west", "minecraft:coarse_dirt")
p.connector(W - 1, 0, 3, "deco_out", "east", "minecraft:coarse_dirt")
p.connector(0, 0, 8, "deco_out", "west", "minecraft:coarse_dirt")
p.connector(W - 1, 0, 8, "hut_out", "east", "minecraft:coarse_dirt")

p.emit("a long wandering trail of packed sand and gravel between driftwood edges",
       ["Runs along z: path_in at the north end, path_out at the south end.",
        "hut_out west at z 3 and east at z 8, deco_out east at z 3 and west at z 8."])
