"""Generates the seafarer village pier's extension section (STR7: 7 by 11 by 12, the pier's 5 wide plank deck carried
12 blocks further out on taller spruce piles, two berths) and prints the BuildSpec as JSON.

The port structure hangs it from the pier's seaward end (and a second one from this section's own
``pier_end``) where the water at the pier's end is shallower than a ship needs (PortStructure, PierExtensions). Its
``pier_extension_in`` connector at the south end (x 3, deck row) lies in line with the pier's ``pier_in``, so the deck runs on
level with the pier's: the deck is y 7, the sea surface row y 6, and the piles stand on cobblestone footings at y 0,
six below the surface, where the berths' carved floor lies (draught 2 + berth depth 3, one more for the floor). The
berth markers sit in columns x 0 and x 6 at z 6 (one per side, one block out from the deck, pointing north)."""
from _style import PALETTE, lantern_post
from buildspec import Piece

W, L = 7, 12
DECK = 7
SEA = DECK - 1
WEST, EAST = 1, W - 2          # the deck's edge columns
BERTH_Z = 6                    # berth centre along the section

p = Piece("village_pier_extension", "Seafarer Pier Extension", (W, DECK + 4, L), PALETTE)

# piles on footings every four blocks along both edges and every eight on the centre line, cross beams at sea level
for z in range(1, L, 4):
    for x in (WEST, 3, EAST):
        if x == 3 and z % 8 != 1:
            continue
        p.put(x, 0, z, "mossy" if (x + z) % 3 else "cobble")
        p.fill(x, 1, z, x, SEA, z, "pile")
    for x in range(WEST + 1, EAST):
        if p.get(x, SEA, z) is None:
            p.put(x, SEA, z, "beam_x")

# wales along both edges at the waterline, between the piles
for x in (WEST, EAST):
    for z in range(L):
        if p.get(x, SEA, z) is None:
            p.put(x, SEA, z, "beam_z")

# deck: stringers along the edges, planks between, a few weathered boards
p.fill(WEST, DECK, 0, WEST, DECK, L - 1, "beam_z")
p.fill(EAST, DECK, 0, EAST, DECK, L - 1, "beam_z")
p.fill(WEST + 1, DECK, 0, EAST - 1, DECK, L - 1, "spruce")
for x, z in ((4, 2), (2, 7), (3, 10)):
    p.put(x, DECK, z, "dark")

# the seaward end like the pier's: two lantern posts, a ladder down beside the centre pile, the deck open between them
lantern_post(p, WEST, DECK + 1, 0)
lantern_post(p, EAST, DECK + 1, 0)
for y in range(SEA - 1, DECK + 1):
    p.put(2, y, 0, "minecraft:ladder[facing=north]")
p.fill(2, 1, 1, 2, SEA - 1, 1, "pile")      # the ladder's backing

# a mooring ring and a cleat on each side at the berths
for z, kind in ((3, "ring"), (9, "cleat")):
    if kind == "ring":
        p.put(WEST, DECK + 1, z, "pirates_n_ships:mooring_ring[face=floor,facing=west,waterlogged=false]")
        p.put(EAST, DECK + 1, z, "pirates_n_ships:mooring_ring[face=floor,facing=east,waterlogged=false]")
    else:
        p.put(WEST, DECK + 1, z, "pirates_n_ships:cleat[face=floor,facing=north]")
        p.put(EAST, DECK + 1, z, "pirates_n_ships:cleat[face=floor,facing=north]")

p.berth(0, SEA, BERTH_Z, "north")
p.berth(W - 1, SEA, BERTH_Z, "north")
p.connector(3, DECK, L - 1, "pier_extension_in", "south", "minecraft:spruce_planks")
p.connector(3, DECK, 0, "pier_end", "north", "minecraft:spruce_planks")

p.emit("plank pier section on tall spruce piles with a mooring ring, a cleat and two berths",
       ["Hangs north from pier_extension_in (south end, deck y 7 = the pier's deck row) where the pier's end is shallow.",
        "Sea level is row y 6; berth markers at [0, 6, 6] and [6, 6, 6], bow north; pier_end at [3, 7, 0] for the next."])
