"""Generates the pirate island's jetty (a rough 3 wide, 16 long plank deck on crooked posts) and prints the BuildSpec as
JSON.

The jetty runs north (seaward) from its ``jetty_in`` connector at the south end, which hangs it from the beach camp:
the deck is at y 5, level with the camp's sand, the posts stand on mossy cobblestone footings on the seabed (y 0) and
the sea surface is row y 4. The deck is x 1..3, patched with dark oak, a salvaged jungle board and a few half-rotten
slabs; columns x 0 and x 4 hold the two berth markers (one per side, at sea level, one block out from the deck,
pointing north: ships lie alongside, bow to the sea) and stay clear of everything else. The posts are crooked: bark
and stripped logs mixed, some leaning on a stair brace.

ST4b: the seaward posts run on through the deck as palm bollards with a rope rail between them on the west side, a
net hangs on the seaward rail post beside the lantern, a crude crane (a palm mast, a fence jib braced by a stair, a
chain hook with a crate on it) stands at the landward end over a second stretch of rope rail, barrels and a lantern
post on the other side; the middle (the berths) stays open."""
from _style import PALETTE, SALVAGE, lantern_post, rope_rail, stair
from buildspec import Piece

W, L = 5, 16
DECK = 5
SEA = DECK - 1
WEST, EAST = 1, W - 2          # the deck's edge columns
BERTH_Z = 7                    # berth centre along the jetty

p = Piece("pirate_island_jetty", "Pirate Jetty", (W, 9, L), PALETTE)

# posts on footings every five blocks along both edges, a cross beam at sea level between each pair
for i, z in enumerate(range(1, L, 5)):
    for x in (WEST, EAST):
        p.put(x, 0, z, "mossy" if (x + i) % 2 else "cobble")
        p.fill(x, 1, z, x, SEA, z, "pile" if (x + i) % 2 else "post")
    p.put(2, SEA, z, "beam_x")
# crooked posts: two of them stand on a footing under the deck's middle and lean out to their edge halfway up, held
# by an upside-down stair brace (leaning inward keeps the berth columns clear)
for x, z, facing in ((WEST, 6, "west"), (EAST, 11, "east")):
    for y in (0, 1, 2):
        p.clear(x, y, z)
    p.put(2, 0, z, "mossy")
    p.fill(2, 1, z, 2, 2, z, "dark_pile")
    p.fill(x, 3, z, x, SEA, z, "dark_pile")
    p.put(2, 3, z, f"minecraft:spruce_stairs[facing={facing},half=top,shape=straight]")

# deck: planks with dark oak patches, a salvaged board and two sagging boards (slabs)
p.fill(WEST, DECK, 0, EAST, DECK, L - 1, "spruce")
for x, z in ((1, 3), (2, 4), (3, 8), (2, 10), (1, 13), (3, 14), (2, 1)):
    p.put(x, DECK, z, "dark")
for x, z in ((3, 2), (1, 11)):
    p.put(x, DECK, z, SALVAGE)
for x, z in ((3, 5), (1, 9)):
    p.put(x, DECK, z, "minecraft:spruce_slab[type=top]")

# the seaward end: a lantern on a post, a short rail with a net hung on it, a ladder down the middle
p.put(EAST, DECK + 1, 0, "fence")
p.put(EAST, DECK + 2, 0, "fence")
p.put(EAST, DECK + 3, 0, "lantern")
p.put(WEST, DECK + 1, 0, "fence")
p.put(WEST, DECK + 2, 0, "cobweb")
for y in range(SEA - 1, DECK + 1):
    p.put(2, y, 0, "minecraft:ladder[facing=north]")
p.put(2, SEA - 1, 1, "post")     # the ladder's backing below the cross beam row

# the seaward posts carry on as palm bollards, the west pair joined by a rope rail
p.put(WEST, DECK + 1, 1, "palm")
p.put(EAST, DECK + 1, 1, "palm")
rope_rail(p, WEST, 2, WEST, 5, DECK + 1, every=9)
p.put(WEST, DECK + 1, 6, "palm")

# a cleat and a mooring ring at the berths
p.put(WEST, DECK + 1, BERTH_Z, "pirates_n_ships:cleat[face=floor,facing=north]")
p.put(EAST, DECK + 1, BERTH_Z + 3, "pirates_n_ships:mooring_ring[face=floor,facing=east]")

# the landward end: a crude crane on the east edge (a palm mast, a fence jib braced by an upside-down stair, a chain
# hook with a crate on it), a rope rail to the landing; barrels and a lantern post on the west edge
p.fill(EAST, DECK + 1, 13, EAST, DECK + 3, 13, "palm")
p.put(EAST, DECK + 3, 12, "fence")
p.put(EAST, DECK + 3, 11, "fence")
p.put(EAST, DECK + 2, 12, stair("south", "top", "dark_oak"))
p.put(EAST, DECK + 2, 11, "chain_y")
p.put(EAST, DECK + 1, 11, "pirates_n_ships:cargo_crate")
rope_rail(p, EAST, 14, EAST, 15, DECK + 1)
p.put(WEST, DECK + 1, 12, "pirates_n_ships:cargo_barrel")
p.put(WEST, DECK + 1, 13, "barrel")
p.put(WEST, DECK + 2, 13, "minecraft:barrel[facing=south,open=false]")
lantern_post(p, WEST, DECK + 1, L - 2)

p.berth(0, SEA, BERTH_Z, "north")
p.berth(W - 1, SEA, BERTH_Z, "north")
p.connector(2, DECK, L - 1, "jetty_in", "south", "minecraft:spruce_planks")

p.emit("rough plank jetty on crooked posts with palm bollards, rope rails, a crude crane, a cleat, a mooring ring, "
       "a lantern and two berths",
       ["Runs north from jetty_in (south end, deck y 5 = the camp's sand row).",
        "Sea level is row y 4; berth markers at [0, 4, 7] and [4, 4, 7], bow north."])
