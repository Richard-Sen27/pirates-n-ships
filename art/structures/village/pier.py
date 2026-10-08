"""Generates the seafarer village pier (a 5 wide, 20 long plank deck on spruce piles) and prints the BuildSpec as JSON.

The pier runs north (seaward) from its ``pier_in`` connector at the south end, which hangs it from the dock head's
quay: the deck is at y 5, level with the quay paving, the piles stand on cobblestone footings on the seabed (y 0) and
the sea surface is row y 4. The deck is x 1..5; columns x 0 and x 6 hold the two berth markers (one per side, at sea
level, at the berth's centre one block out from the deck, pointing north: ships lie alongside, bow to the sea)."""
from _style import PALETTE, lantern_post
from buildspec import Piece

W, L = 7, 20
DECK = 5
SEA = DECK - 1
WEST, EAST = 1, W - 2          # the deck's edge columns
BERTH_Z = 9                    # berth centre along the pier

p = Piece("village_pier", "Seafarer Pier", (W, 9, L), PALETTE)

# piles on cobblestone footings (mostly mossy) every four blocks along both edges and the centre line, cross beams
# under the deck at sea level
for z in range(1, L, 4):
    for x in (WEST, 3, EAST):
        if x == 3 and z % 8 != 1:
            continue
        p.put(x, 0, z, "mossy" if (x + z) % 3 else "cobble")
        p.fill(x, 1, z, x, SEA, z, "pile")
    for x in range(WEST + 1, EAST):
        if p.get(x, SEA, z) is None:
            p.put(x, SEA, z, "beam_x")

# wales: a stripped spruce timber along both edges at the waterline, between the piles
for x in (WEST, EAST):
    for z in range(L):
        if p.get(x, SEA, z) is None:
            p.put(x, SEA, z, "beam_z")

# deck: stripped spruce stringers along the edges, planks between
p.fill(WEST, DECK, 0, WEST, DECK, L - 1, "beam_z")
p.fill(EAST, DECK, 0, EAST, DECK, L - 1, "beam_z")
p.fill(WEST + 1, DECK, 0, EAST - 1, DECK, L - 1, "spruce")
for x, z in ((2, 2), (4, 5), (3, 8), (2, 12), (4, 14), (3, 17)):      # weathered replacement boards
    p.put(x, DECK, z, "dark")

# the seaward end: a rail between two lantern posts, open in the middle over a ladder down the centre pile
lantern_post(p, WEST, DECK + 1, 0)
lantern_post(p, EAST, DECK + 1, 0)
p.put(WEST + 1, DECK + 1, 0, "fence")
p.put(EAST - 1, DECK + 1, 0, "fence")
for y in range(SEA - 1, DECK + 1):
    p.put(3, y, 0, "minecraft:ladder[facing=north]")

# mooring rings and cleats along both edges
for z in (3, 15):
    p.put(WEST, DECK + 1, z, "pirates_n_ships:mooring_ring[face=floor,facing=west,waterlogged=false]")
    p.put(EAST, DECK + 1, z, "pirates_n_ships:mooring_ring[face=floor,facing=east,waterlogged=false]")
for z in (7, 11):
    p.put(WEST, DECK + 1, z, "pirates_n_ships:cleat[face=floor,facing=north]")
    p.put(EAST, DECK + 1, z, "pirates_n_ships:cleat[face=floor,facing=north]")

# the landward end, clear of the berths: a lantern post and a rail on either edge, cargo waiting to be loaded
for x in (WEST, EAST):
    lantern_post(p, x, DECK + 1, L - 2)
    p.put(x, DECK + 1, L - 1, "fence")
p.put(WEST, DECK + 1, L - 4, "minecraft:barrel[facing=up]")
p.put(WEST, DECK + 1, L - 3, "pirates_n_ships:cargo_barrel")
p.put(EAST, DECK + 1, L - 4, "pirates_n_ships:cargo_crate")
p.put(EAST, DECK + 1, L - 3, "pirates_n_ships:cargo_crate")
p.put(EAST, DECK + 2, L - 3, "pirates_n_ships:cargo_crate")

p.berth(0, SEA, BERTH_Z, "north")
p.berth(W - 1, SEA, BERTH_Z, "north")
p.connector(3, DECK, L - 1, "pier_in", "south", "minecraft:spruce_planks")

p.emit("plank pier on spruce piles with mooring rings, cleats and two berths",
       ["Runs north from pier_in (south end, deck y 5 = the dock head's quay row).",
        "Sea level is row y 4; berth markers at [0, 4, 9] and [6, 4, 9], bow north."])
