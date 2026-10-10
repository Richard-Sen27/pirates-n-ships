"""Generates a dead end of the pirate island trail that opens into a small clearing (11 by 11) and prints the
BuildSpec as JSON.

The trail comes in from the north (``path_in``) and widens into a ragged round clearing of trodden sand, gravel and
coarse dirt with a cold fire pit of stones in the middle, two driftwood log seats and a spit, a few rocks and a
driftwood edge round the rim. The clearing is a cul-de-sac of the camp: ``hut_out`` west and east put two buildings on
its rim, ``deco_out`` south a decoration; no trail leaves it."""
import math

from _camp import band, edging, line, rocks, trail
from _style import PALETTE, jit
from buildspec import Piece

S = 11
SIZE = (S, 5, S)
C = 5, 6                # the clearing's centre (x, z)

p = Piece("pirate_island_path_clearing", "Pirate Trail (clearing)", SIZE, PALETTE)

# a ragged disc: the radius wobbles round the rim
disc = set()
for x in range(S):
    for z in range(S):
        a = math.atan2(z - C[1], x - C[0])
        r = 4.0 + 0.6 * math.sin(3 * a + 0.7) + 0.3 * math.cos(5 * a)
        if math.hypot(x - C[0], z - C[1]) <= r:
            disc.add((x, z))
bed = band(line(C[0], 0, C[0], 3), SIZE) | disc
mouths = {(0, 6), (1, 6), (S - 1, 6), (S - 2, 6), (5, S - 1), (5, S - 2)}
trail(p, bed | mouths, salt=221)
edging(p, bed, SIZE, salt=222, rate=600, stakes=60, skip=mouths)

# the fire pit: a ring of stones round a sunken bed of coarse dirt with charred logs, a spit over it, two log seats
cx, cz = C
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        if dx or dz:
            p.put(cx + dx, 0, cz + dz, "mossy" if jit(cx + dx, 0, cz + dz, 223) < 450 else "cobble")
p.put(cx, 0, cz, "coarse")
p.put(cx, 1, cz, "minecraft:campfire[facing=east,lit=false,signal_fire=false]")
p.put(cx - 1, 1, cz, "fence")
p.put(cx + 1, 1, cz, "fence")
p.put(cx - 1, 2, cz, "fence")
p.put(cx + 1, 2, cz, "fence")
p.put(cx, 2, cz, "chain_x")
for x in (cx - 1, cx, cx + 1):
    p.put(x, 1, cz + 2, "drift_x" if x == cx else "beam_x")
p.put(cx - 2, 1, cz - 1, "drift_z")
p.put(cx - 2, 1, cz, "drift_z")
# rocks and a rum keg on the rim
rocks(p, [(2, 3), (8, 9), (9, 8)], salt=224)
p.put(8, 1, 3, "minecraft:barrel[facing=up,open=false]")
p.put(7, 1, 3, "pirates_n_ships:cargo_barrel")

p.connector(C[0], 0, 0, "path_in", "north", "minecraft:gravel")
p.connector(0, 0, 6, "hut_out", "west", "minecraft:coarse_dirt")
p.connector(S - 1, 0, 6, "hut_out", "east", "minecraft:coarse_dirt")
p.connector(5, 0, S - 1, "deco_out", "south", "minecraft:coarse_dirt")

p.emit("a dead end opening into a small clearing round a cold fire pit with a spit and log seats",
       ["path_in at [5, 0, 0] (north); hut_out at [0, 0, 6] (west) and [10, 0, 6] (east); deco_out at [5, 0, 10] "
        "(south). No path_out: a cul-de-sac."])
