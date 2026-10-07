"""Generates the cargo field (what a merchantman spilled on its way down) and prints the BuildSpec as JSON.

A ragged bed of sand, gravel and clay on row y 0 (the seabed row) with a scatter of mossy and plain cobblestone
ballast; cargo crates and barrels half sunk in it (some set into the bed with sand banked over their edges, some
sitting on it, one stack of two toppled against a third), kegs lying on their sides, a broken yard in two pieces
with its chain, a cannon half buried in a sand drift (the cannon only turns horizontally, so it stands upright,
its rear end in the sand), loose planks and a hatch cover, and a chest with the wreck loot table under a fallen
crate. No orientation matters; the field is round. Rows y 1..3 stand in water."""
import random

from _style import PALETTE, barrel, bed_disc, drift, loot_chest, trapdoor, underpin
from buildspec import Piece

rng = random.Random(0xCA460)
p = Piece("wreck_cargo_field", "Cargo Field", (15, 4, 15), PALETTE)

# ---------------------------------------------------------------- cargo set into the bed (row y 0), sand over the edges
for x, z, key in ((3, 4, "crate"), (9, 2, "cargo_barrel"), (11, 9, "crate"), (5, 11, "cargo_barrel"),
                  (2, 8, "crate"), (12, 5, "cargo_barrel")):
    p.put(x, 0, z, key)
    for dx, dz in ((1, 0), (0, 1)):
        if rng.random() < 0.7:
            drift(p, x + dx, 1, z + dz)

# cargo sitting on the bed, half buried by drifts
for x, z, key in ((4, 4, "crate"), (8, 3, "cargo_barrel"), (10, 10, "crate"), (6, 12, "crate")):
    p.put(x, 1, z, key)
    drift(p, x - 1, 1, z) if rng.random() < 0.6 else drift(p, x, 1, z + 1)

# a toppled stack: two crates on a third, the top one slid off
p.put(7, 1, 7, "crate")
p.put(7, 2, 7, "crate")
p.put(8, 1, 7, "crate")
p.put(8, 1, 8, "cargo_barrel")
p.put(7, 3, 7, "dark_slab")          # a torn crate lid
drift(p, 6, 1, 7)
drift(p, 7, 1, 6)

# kegs lying on their sides
p.put(3, 1, 10, barrel("east"))
p.put(4, 1, 10, barrel("east"))
p.put(12, 1, 3, barrel("north"))
drift(p, 3, 1, 11)

# ---------------------------------------------------------------- the broken yard, two pieces, and its chain
for x in range(1, 5):
    p.put(x, 1, 1, "pirates_n_ships:yard[axis=x]")
for z in range(2, 5):
    p.put(6, 1, z, "pirates_n_ships:yard[axis=z]")
p.put(5, 1, 1, "spruce_beam_x")        # the splintered end
p.put(6, 1, 5, "chain_z")
p.put(6, 1, 6, "chain_z")

# ---------------------------------------------------------------- a cannon, its rear end buried in a drift
p.put(11, 1, 13, "pirates_n_ships:cannon[facing=west,load=empty,part=front]")
p.put(12, 1, 13, "pirates_n_ships:cannon[facing=west,load=empty,part=rear]")
for x, z, y in ((13, 13, 1), (12, 12, 1), (12, 14, 1), (13, 12, 1), (13, 14, 1), (13, 13, 2)):
    drift(p, x, y, z)
p.put(10, 1, 12, "cobble_slab")       # a spilled shot locker's stones
p.put(9, 1, 13, "mossy")

# ---------------------------------------------------------------- planks, a hatch cover, ballast
for x, z, key in ((9, 6, "dark_slab"), (10, 6, "dark_slab"), (2, 5, "spruce_slab"), (13, 8, "spruce_slab"),
                  (5, 8, "dark_beam_z"), (5, 9, "dark_beam_z")):
    p.put(x, 1, z, key)
p.put(9, 1, 11, trapdoor("spruce", "north"))
p.put(1, 1, 6, trapdoor("dark_oak", "east"))
for x, z in ((2, 12), (8, 10), (12, 7), (3, 2), (10, 1), (13, 10), (1, 9), (6, 9)):
    p.put(x, 1, z, "mossy" if rng.random() < 0.55 else rng.choice(("cobble", "tuff")))
p.put(9, 1, 10, "prismarine")           # crusted ballast
p.put(4, 2, 4, "mossy_wall")            # a stone on the crate

# ---------------------------------------------------------------- the chest, a crate fallen against it
loot_chest(p, 6, 1, 10, "south")
p.put(6, 2, 10, "dark_slab")            # a plank across its lid
drift(p, 7, 1, 10)

bed_disc(p, rng, 7, 7, 7.5, 7.5, ragged=0.3)
underpin(p, rng)

p.emit("cargo spilled on the seabed: crates and barrels half sunk in sand, a broken yard, a cannon, a chest",
       ["y 0 is the seabed row (its own bed of sand, gravel and clay); everything else stands in water.",
        "Chest (loot table pirates_n_ships:chests/wreck) at [6, 1, 10]."])
