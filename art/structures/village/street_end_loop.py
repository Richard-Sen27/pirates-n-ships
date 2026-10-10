"""Generates a street end as a turning loop (11x11; STR3, a terminator) and prints the BuildSpec as JSON.

The street runs into a cobbled ring round a planted island: a stone brick kerb on both sides of the ring, grass and
flowers in the middle round a lantern post, dirt path outside. Carts turn here. Connector: ``street_in`` on the north
end."""
import math

from _streets import KERB, crown_key, green, kerb_key, lay, line, verge_key
from _style import PALETTE, lantern_post
from buildspec import Piece

S = 11
C = 5
p = Piece("village_street_end_loop", "Seafarer Street End (loop)", (S, 5, S), PALETTE)

street = lay(p, line(C, -4, C, 2), salt=391)
island = []
for x in range(S):
    for z in range(S):
        r = math.hypot(x - C, z - 6)
        if street.get((x, z)) == "crown" and r > 1.6:
            continue
        if r <= 1.6:
            island.append((x, z))
        elif r <= 2.6:
            p.put(x, 0, z, kerb_key(x, z, 392))
        elif r <= 3.9:
            p.put(x, 0, z, crown_key(x, z, 392))
        elif r <= 4.9:
            p.put(x, 0, z, kerb_key(x, z, 393))
        elif r <= 5.6 and (x, z) not in street:
            key = verge_key(x, z, 394)
            if key:
                p.put(x, 0, z, key)
green(p, island, salt=395, flowers=600)
p.clear(C, 1, 6)
p.put(C, 0, 6, "grass")
lantern_post(p, C, 1, 6)

p.connector(C, 0, 0, "street_in", "north", "minecraft:cobblestone")

p.emit("a cobbled turning loop round a planted island with a lantern post",
       ["Terminator: attaches to a street's street_out with its north end (street_in) and spawns nothing."])
