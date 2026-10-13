"""Generates the navy outpost quay's extension section (STR7: 7 by 11 by 12, the quay's 5 wide masonry deck on pillars carried 12
blocks further out, two berths) and prints the BuildSpec as JSON.

The port structure hangs it from the quay's seaward end (and a second one from this
section's own ``pier_end``) where the water at the quay's end is shallower than a ship needs (PortStructure, PierExtensions). Its
``pier_extension_in`` connector at the south end (x 3, deck row) lies in line with the quay's ``quay_in``: the deck is y 7, level
with the quay's, the sea surface row y 6, and PIER1: the deck stands on stone pillars from y 0 (six below the surface, the
berths' carved floor) every four blocks, joined by an edge course at the waterline, open water between them. Berth markers in columns x 0 and x 6 at z 6, pointing north."""
from _style import PALETTE, age, connector, dry, lantern_post, stair
from buildspec import Piece

W, H, L = 7, 11, 12
DECK = 7
SEA = DECK - 1
WEST, EAST = 1, W - 2
BERTH_Z = 6

p = Piece("navy_outpost_quay_extension", "Navy Outpost Quay Extension", (W, H, L), PALETTE)

# PIER1: stone pillars on cobblestone footings at the seabed row, as the quay's, joined by an edge course at the
# waterline; open water between them and under the middle
p.palette["mossy"] = "minecraft:mossy_cobblestone"
PILLAR_ZS = (1, 5, 9, L - 1)
for z in PILLAR_ZS:
    for x in (WEST, EAST):
        p.put(x, 0, z, "mossy" if (x + z) % 3 else "cobble")
        p.fill(x, 1, z, x, SEA, z, "bricks")
        p.put(x, SEA - 1, z, "bricks_mossy")
        if (x + z) % 4 == 0:
            p.put(x, 3, z, "bricks_cracked")
p.put(2, 0, 1, "cobble")                         # the ladder's backing pillar
p.fill(2, 1, 1, 2, SEA, 1, "bricks")
p.fill(WEST, SEA, 0, WEST, SEA, L - 1, "bricks")
p.fill(EAST, SEA, 0, EAST, SEA, L - 1, "bricks")
for z in range(0, L, 2):
    if z not in PILLAR_ZS:
        p.put(WEST, SEA, z, "bricks_mossy")
        p.put(EAST, SEA, z, "bricks_mossy")

# deck: andesite kerbs, bricks between, a chiseled course at the seaward end and an andesite band amidships
p.fill(WEST, DECK, 0, WEST, DECK, L - 1, "andesite")
p.fill(EAST, DECK, 0, EAST, DECK, L - 1, "andesite")
p.fill(WEST + 1, DECK, 0, EAST - 1, DECK, L - 1, "bricks")
p.fill(WEST + 1, DECK, 0, EAST - 1, DECK, 0, "chiseled")
p.fill(WEST + 1, DECK, 6, EAST - 1, DECK, 6, "andesite")

# bollards at the berths' bow and stern, a mooring ring and a cleat between
for z in (2, 10):
    for x in (WEST, EAST):
        p.put(x, DECK + 1, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,"
                              "west=none]")
        p.put(x, DECK + 2, z, stair("stone_brick", "south", "top"))
p.put(WEST, DECK + 1, BERTH_Z, "pirates_n_ships:mooring_ring[face=floor,facing=west,waterlogged=false]")
p.put(EAST, DECK + 1, BERTH_Z, "pirates_n_ships:mooring_ring[face=floor,facing=east,waterlogged=false]")
p.put(WEST, DECK + 1, 8, "pirates_n_ships:cleat[face=floor,facing=north]")
p.put(EAST, DECK + 1, 4, "pirates_n_ships:cleat[face=floor,facing=north]")

# lantern posts at the seaward corners, the ladder down to the water at the end (the middle stays open for the next)
lantern_post(p, WEST, DECK + 1, 0)
lantern_post(p, EAST, DECK + 1, 0)
for y in range(SEA - 1, DECK + 1):
    p.put(2, y, 0, "ladder_n")

age(p, ground=SEA, where=lambda x, y, z: y < DECK)

p.berth(0, SEA, BERTH_Z, "north")
p.berth(W - 1, SEA, BERTH_Z, "north")
connector(p, 3, DECK, L - 1, "pier_extension_in", "south", "minecraft:stone_bricks")
connector(p, 3, DECK, 0, "pier_end", "north", "minecraft:chiseled_stone_bricks")

dry(p)
p.emit("stone quay section with bollards, a mooring ring, a cleat and two berths",
       ["Hangs north from pier_extension_in (south end, deck y 7 = the quay's deck row) where the quay's end is shallow.",
        "Sea level is row y 6; berth markers at [0, 6, 6] and [6, 6, 6], bow north; pier_end at [3, 7, 0] for the next."])
