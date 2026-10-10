"""Generates the captain's hut with a lookout (the captain's hut of ``captains_hut.py`` with a lookout platform on palm
stilts beside it, 13x14x9 box) and prints the BuildSpec as JSON.

A captain's hut variant of the ``huts`` pool (STR2, ``_captains.build``): east of the hut four palm trunks carry a
platform of planks at y 10 with a fence rail, a lantern and the captain's black banner on a mast; a ladder runs up
the mast's north side to an open trapdoor in the platform, braces of stripped logs tie the legs, a rope coil and a
crate stand at the foot. The hut, its porch and the captain's post (4, 3, 5) are the captain's hut's."""
from _camp import patch, rope_coil
from _captains import build
from _style import trapdoor

TX0, TX1, TZ0, TZ1 = 10, 12, 3, 5      # the tower's legs
MAST = 11, 4                           # the mast in the middle, the ladder on its north side
DECK = 10


def lookout(p):
    for x in (TX0, TX1):
        for z in (TZ0, TZ1):
            p.fill(x, 0, z, x, DECK - 1, z, "palm")
    # cross braces of stripped logs every three rows (the ladder side open)
    for y in (3, 6):
        p.fill(TX0 + 1, y, TZ1, TX1 - 1, y, TZ1, "beam_x")
        p.fill(TX0, y, TZ0 + 1, TX0, y, TZ1 - 1, "beam_z")
        p.fill(TX1, y, TZ0 + 1, TX1, y, TZ1 - 1, "beam_z")
    mx, mz = MAST
    p.fill(mx, 0, mz, mx, DECK + 3, mz, "palm")
    for y in range(1, DECK):
        p.put(mx, y, mz - 1, "minecraft:ladder[facing=north,waterlogged=false]")
    # the platform, a trapdoor over the ladder, a rope rail round it
    p.fill(TX0 - 1, DECK, TZ0 - 1, TX1, DECK, TZ1 + 1, "spruce")
    p.put(TX0 - 1, DECK, TZ0 - 1, "dark")
    p.put(TX1, DECK, TZ1 + 1, "minecraft:jungle_planks")
    p.put(mx, DECK, mz - 1, trapdoor("north", "bottom", True))
    p.clear(mx, DECK, mz)
    p.put(mx, DECK, mz, "palm")
    for (x0, z0, x1, z1) in ((TX0 - 1, TZ0 - 1, TX1, TZ0 - 1), (TX0 - 1, TZ1 + 1, TX1, TZ1 + 1),
                             (TX0 - 1, TZ0, TX0 - 1, TZ1), (TX1, TZ0, TX1, TZ1)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, z) != (mx, mz - 1):
                    p.put(x, DECK + 1, z, "fence")
    p.put(TX1, DECK + 2, TZ1 + 1, "lantern")
    p.put(mx, DECK + 4, mz, "minecraft:black_banner[rotation=4]")
    # the foot of the tower: trodden ground, a rope coil, a crate
    patch(p, TX0, TZ0, TX1, TZ1, reach=1, salt=361)
    p.fill(TX0, 0, TZ0, TX1, 0, TZ1, "coarse")
    for x in (TX0, TX1):
        for z in (TZ0, TZ1):
            p.put(x, 0, z, "palm")
    p.put(mx, 0, mz, "palm")
    rope_coil(p, TX0 + 1, 1, TZ1, layers=3)
    p.put(TX1, 1, TZ0 - 1, "pirates_n_ships:cargo_crate")


p = build("pirate_island_captains_lookout", "Pirate Captain's Hut with a Lookout", (13, 15, 9), extras=lookout)
p.emit("captain's hut on palm stilts with a lookout platform on a mast beside it, a ladder and the captain's banner",
       ["Faces north: the steps, the door and the hut_in connector are on the -z side.",
        "y 0 is the foundation row; the hut's floor is y 2, the lookout's platform y 10."])
