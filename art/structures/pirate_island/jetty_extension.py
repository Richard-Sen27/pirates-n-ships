"""Generates the pirate island jetty's extension section (STR7: 5 by 11 by 12, the jetty's rough 3 wide plank deck
carried 12 blocks further out on taller crooked posts, two berths) and prints the BuildSpec as JSON.

The port structure hangs it from the jetty's seaward end (and a second one from this
section's own ``pier_end``) where the water at the jetty's end is shallower than a ship needs (PortStructure, PierExtensions). Its
``pier_extension_in`` connector at the south end (x 2, deck row) lies in line with the jetty's ``jetty_in``: the deck is y 7, level
with the jetty's, the sea surface row y 6, and the posts stand on footings at y 0, six below the surface, where the
berths' carved floor lies. Berth markers in columns x 0 and x 4 at z 6, pointing north; they stay clear."""
from _style import PALETTE, SALVAGE
from buildspec import Piece

W, L = 5, 12
DECK = 7
SEA = DECK - 1
WEST, EAST = 1, W - 2          # the deck's edge columns
BERTH_Z = 6

p = Piece("pirate_island_jetty_extension", "Pirate Jetty Extension", (W, DECK + 4, L), PALETTE)

# posts on footings every five blocks along both edges (bark and stripped mixed), a cross beam at sea level
for i, z in enumerate(range(1, L, 5)):
    for x in (WEST, EAST):
        p.put(x, 0, z, "mossy" if (x + i) % 2 else "cobble")
        p.fill(x, 1, z, x, SEA, z, "pile" if (x + i) % 2 else "post")
    p.put(2, SEA, z, "beam_x")
# one crooked post leaning in from the middle, braced by an upside-down stair (the berth columns stay clear)
p.put(2, 0, 8, "mossy")
p.fill(2, 1, 8, 2, 4, 8, "dark_pile")
p.fill(EAST, 5, 8, EAST, SEA, 8, "dark_pile")
p.put(2, 5, 8, "minecraft:spruce_stairs[facing=east,half=top,shape=straight]")

# deck: planks with dark oak patches, a salvaged board and a sagging one
p.fill(WEST, DECK, 0, EAST, DECK, L - 1, "spruce")
for x, z in ((3, 2), (2, 5), (1, 9)):
    p.put(x, DECK, z, "dark")
p.put(3, DECK, 7, SALVAGE)
p.put(2, DECK, 10, "minecraft:spruce_slab[type=top]")

# the seaward end: a lantern on a post at the east corner, a ladder down the west edge, the middle open for the next
p.put(EAST, DECK + 1, 0, "fence")
p.put(EAST, DECK + 2, 0, "fence")
p.put(EAST, DECK + 3, 0, "lantern")
for y in range(SEA - 1, DECK + 1):
    p.put(WEST, y, 0, "minecraft:ladder[facing=north]")   # backed by the west post at z 1

# palm bollards at the berths, a cleat and a mooring ring
p.put(WEST, DECK + 1, 3, "palm")
p.put(EAST, DECK + 1, 9, "palm")
p.put(WEST, DECK + 1, BERTH_Z, "pirates_n_ships:cleat[face=floor,facing=north]")
p.put(EAST, DECK + 1, BERTH_Z - 2, "pirates_n_ships:mooring_ring[face=floor,facing=east]")

p.berth(0, SEA, BERTH_Z, "north")
p.berth(W - 1, SEA, BERTH_Z, "north")
p.connector(2, DECK, L - 1, "pier_extension_in", "south", "minecraft:spruce_planks")
p.connector(2, DECK, 0, "pier_end", "north", "minecraft:spruce_planks")

p.emit("rough plank jetty section on tall crooked posts with palm bollards, a cleat, a mooring ring and two berths",
       ["Hangs north from pier_extension_in (south end, deck y 7 = the jetty's deck row) where the jetty's end is shallow.",
        "Sea level is row y 6; berth markers at [0, 6, 6] and [4, 6, 6], bow north; pier_end at [2, 7, 0] for the next."])
