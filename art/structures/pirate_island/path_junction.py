"""Generates a T-junction of the pirate island trail (11 wide, 7 long) and prints the BuildSpec as JSON.

The trail comes in from the north (``path_in``), meets a cross trail at z 4 with rounded corners and leaves both west
and east (two ``path_out``); a ``hut_out`` on the south edge puts a building at the head of the T. A signpost of
driftwood with a lantern and a skull, a barrel and rocks stand at the head of the T on either side of the hut's
mouth."""
from _camp import arc, band, edging, line, rocks, skull_post, trail
from _style import PALETTE, lantern_post
from buildspec import Piece

W, L = 11, 7
SIZE = (W, 5, L)
X = 5

p = Piece("pirate_island_path_junction", "Pirate Trail (junction)", SIZE, PALETTE)

centre = line(X, 0, X, 2) + arc(X - 2, 2, 2, 0, 90) + arc(X + 2, 2, 2, 180, 90) + line(0, 4, W - 1, 4)
bed = band(centre, SIZE)
mouth = {(X, 5), (X, 6)}
trail(p, bed | mouth, salt=211)
edging(p, bed, SIZE, salt=212, skip=mouth | {(X - 1, 6), (X + 1, 6)})
# the head of the T: a signpost with a skull on one side of the hut's mouth, a barrel and rocks on the other
skull_post(p, X - 2, 1, 6, rotation=8, height=2)
p.put(X - 2, 0, 6, "coarse")
p.put(X + 2, 0, 6, "gravel")
p.put(X + 2, 1, 6, "minecraft:barrel[facing=up,open=false]")
rocks(p, [(X + 3, 6), (W - 1, 6)], salt=213)
lantern_post(p, 3, 1, 0, height=1, post="drift")
p.put(7, 0, 1, "coarse")
p.put(7, 1, 1, "dead_bush")

p.connector(X, 0, 0, "path_in", "north", "minecraft:gravel")
p.connector(0, 0, 4, "path_out", "west", "minecraft:gravel")
p.connector(W - 1, 0, 4, "path_out", "east", "minecraft:gravel")
p.connector(X, 0, L - 1, "hut_out", "south", "minecraft:coarse_dirt")

p.emit("a T-junction of the trail with rounded corners, a skull signpost and a building at its head",
       ["path_in at [5, 0, 0] (north); path_out at [0, 0, 4] (west) and [10, 0, 4] (east); hut_out at [5, 0, 6] "
        "(south)."])
