"""Generates the mast stump (a ship's mast still standing out of the seabed with its yard and torn rigging) and prints
the BuildSpec as JSON.

The mast steps through a broken square of deck planking half buried in the bed (row y 0 is the seabed row), braced
by a stripped log partner ring; it is spruce log below and a stripped fished section above an iron band (chains),
and it snapped above the top: a splintered fence stub. Halfway up, the remains of the fighting top (trapdoors on
beams, two of four gone). The yard still hangs across the mast, one arm snapped and hanging straight down; a lantern hangs from
it and torn rigging trails from both arms (chains), a shroud of fences runs from the top down to the deck's edge in
steps. A cleat on the mast with a coil of rope (chain), and at the mast's foot a chest with the wreck loot table,
half covered by sand and ballast."""
import random

from _style import PALETTE, bed_disc, crust, drift, loot_chest, trapdoor, underpin
from buildspec import Piece

rng = random.Random(0x3A57)
p = Piece("wreck_mast_stump", "Mast Stump", (7, 12, 7), PALETTE)
M = 3          # the mast's column (x and z)
TOP = 6        # the fighting top's floor row
YARD = 8       # the yard's row

# ---------------------------------------------------------------- the deck square around the mast's foot
for x in range(1, 6):
    for z in range(1, 6):
        if (x, z) in ((1, 1), (5, 5), (5, 1)) or rng.random() < 0.15:
            continue                                    # planks gone
        p.put(x, 0, z, crust(rng, "spruce" if (x + z) % 3 else "dark", 0.1))
for x in range(2, 5):                                    # the mast partners: a ring of stripped logs
    p.put(x, 1, 2, "dark_beam_x")
    p.put(x, 1, 4, "dark_beam_x")
p.put(2, 1, 3, "dark_beam_z")
p.put(4, 1, 3, "dark_beam_z")
p.put(4, 1, 4, "mossy")                                  # ballast heaved up through the planking
p.put(1, 1, 5, "cobble")

# ---------------------------------------------------------------- the mast
for y in range(0, 5):
    p.put(M, y, M, "mast")
p.put(M, 5, M, "chain_y")                                # the iron band where the fish joins it
for y in range(TOP, 11):
    p.put(M, y, M, "spruce_post")
p.put(M, 11, M, "fence")                                 # the splintered stub

# the fighting top: beams across, trapdoors around (two of four gone)
p.put(2, TOP, M, "dark_beam_x")
p.put(4, TOP, M, "dark_beam_x")
p.put(M, TOP, 2, "dark_beam_z")
p.put(M, TOP, 4, "dark_beam_z")
p.put(2, TOP, 2, trapdoor("dark_oak", "south", "top"))
p.put(4, TOP, 4, trapdoor("dark_oak", "north", "top"))

# ---------------------------------------------------------------- the yard: west arm whole, east arm snapped and hanging
for x in range(0, M):
    p.put(x, YARD, M, "pirates_n_ships:yard[axis=x]")
p.put(M + 1, YARD, M, "pirates_n_ships:yard[axis=x]")
p.put(M + 2, YARD, M, "spruce_beam_x")                  # the stub of the broken arm
p.put(M + 2, YARD - 1, M, "spruce_post")                 # the rest of it hanging straight down
p.put(M + 2, YARD - 2, M, "fence")                       # its splinter
p.put(1, YARD - 1, M, "lantern_hanging")
# torn rigging trailing from both arms
for y in (YARD - 1, YARD - 2, YARD - 3):
    p.put(0, y, M, "chain_y")
for y in (YARD - 3, YARD - 4):
    p.put(M + 2, y, M, "chain_y")
# a shroud from the top down to the deck's edge, stepping out toward the north
p.put(M, TOP - 1, M - 1, "fence")
p.put(M, TOP - 2, M - 1, "fence")
p.put(M, TOP - 2, M - 2, "fence")
p.put(M, TOP - 3, M - 2, "fence")
p.put(M, TOP - 4, M - 2, "fence")
p.put(M, TOP - 4, M - 3, "fence")
p.put(M, TOP - 5, M - 3, "fence")

# ---------------------------------------------------------------- the cleat and a coil of rope on the mast
p.put(M, 3, M + 1, "pirates_n_ships:cleat[face=wall,facing=south]")
p.put(M, 2, M + 1, "chain_y")

# ---------------------------------------------------------------- the chest at its foot, half buried
loot_chest(p, M + 2, 1, M + 1, "west")
drift(p, M + 3, 1, M + 1)
drift(p, M + 2, 1, M + 2)
drift(p, M + 3, 2, M + 1)
p.put(M + 2, 2, M + 2, "cobble_slab")
drift(p, 0, 1, 4)
drift(p, 0, 1, 5)
drift(p, 1, 1, 0)

bed_disc(p, rng, 3, 3, 3.5, 3.5, ragged=0.25)
underpin(p, rng)

p.emit("a mast standing out of the seabed with its yard, a lantern and torn rigging, a chest at its foot",
       ["y 0 is the seabed row (a broken square of deck planking in its own bed); everything else stands in water.",
        "Chest (loot table pirates_n_ships:chests/wreck) at [5, 1, 4]."])
