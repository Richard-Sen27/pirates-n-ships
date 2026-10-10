"""Generates a short straight pirate island trail (7 wide, 7 long, running north-south) and prints the BuildSpec as
JSON.

A 3-wide bed of packed sand, gravel and trodden dirt between driftwood edges (pale stripped jungle logs lying flush in
the verge, with gaps), a crude stake or two, a barrel and a lantern stake on the verges, and a short spur of trodden
ground out to each side connector, where the huts attach. Cells the trail does not need stay empty, so the terrain
shows through (no sand rectangle). Connectors: ``path_in`` on the north end (toward the camp), ``path_out`` on the
south end (the next trail piece or a terminator), one ``hut_out`` on each side.

STR2: rebuilt from the ST4b palisade trail (a sand rectangle between stake pairs) into the natural-layout trail; the
connectors are unchanged."""
from _camp import band, edging, line, trail
from _style import PALETTE, lantern_post
from buildspec import Piece

W = L = 7
SIZE = (W, 4, L)

p = Piece("pirate_island_path", "Pirate Trail", SIZE, PALETTE)

bed = band(line(3, 0, 3, L - 1), SIZE)
spurs = {(0, 3), (1, 3), (5, 3), (6, 3)}
trail(p, bed | spurs, salt=61)
edging(p, bed, SIZE, salt=62, skip=spurs | {(1, 2), (1, 4), (5, 2), (5, 4)})
p.put(5, 1, 0, "barrel")
lantern_post(p, 1, 1, 5, height=1, post="drift")

p.connector(3, 0, 0, "path_in", "north", "minecraft:gravel")
p.connector(3, 0, L - 1, "path_out", "south", "minecraft:gravel")
p.connector(0, 0, 3, "hut_out", "west", "minecraft:coarse_dirt")
p.connector(W - 1, 0, 3, "hut_out", "east", "minecraft:coarse_dirt")

p.emit("a short trail of packed sand and gravel between driftwood edges",
       ["Runs along z: path_in at the north end, path_out at the south end.",
        "hut_out connectors on both sides at z 3; huts attach with their north (door) side."])
