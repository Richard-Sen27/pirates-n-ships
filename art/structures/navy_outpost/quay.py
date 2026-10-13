"""Generates the navy outpost's stone quay (a 5 wide masonry deck on pillars, 18 long) and prints the BuildSpec as JSON.

It runs north (seaward) from its ``quay_in`` connector at the south end, which hangs it from the fort gate's sea gate:
the deck is at y 5, level with the gate's paving, and the sea surface is row y 4, like the village pier. PIER1: the deck stands on stone
pillars (a cobblestone footing at y 0, stone bricks above) every four blocks along both edges, joined by an edge
course at the waterline row, with open water between them and under the middle. Columns x 0 and x 6 hold the two
berth markers (one per side, at sea level, at the berth's centre one block out from the deck, pointing north).

On the deck: mooring rings and cleats along both edges, bollards (stone brick walls under upside-down stairs), a
crane at the seaward end (a spruce post with a fence jib to the west edge and a lantern hanging from it; nothing
reaches over the berths), lantern
posts, a ladder down to the water at the end, and a few crates waiting to be loaded."""
from _style import dry, PALETTE, age, banner, chain, connector, lantern_post, stair, stair_shape
from buildspec import Piece

W, H, L = 7, 9, 18
DECK = 5
SEA = DECK - 1
WEST, EAST = 1, W - 2          # the deck's edge columns
BERTH_Z = 9                    # berth centre along the quay

p = Piece("navy_outpost_quay", "Navy Outpost Quay", (W, H, L), PALETTE)

# PIER1: the quay stands on stone pillars, not on a solid mole. A pillar every four blocks along both edges (and one
# behind the ladder), each on a cobblestone footing at the seabed row (y 0; the PileFootings pass carries it down to
# the real sea floor where the water is deeper), a plain edge course at the waterline under each deck kerb joining
# them, open water between the pillars and under the deck's middle
p.palette["mossy"] = "minecraft:mossy_cobblestone"
PILLAR_ZS = tuple(range(1, L, 4))
for z in PILLAR_ZS:
    for x in (WEST, EAST):
        p.put(x, 0, z, "mossy" if (x + z) % 3 else "cobble")
        p.fill(x, 1, z, x, SEA, z, "bricks")
        p.put(x, SEA - 1, z, "bricks_mossy")
        if (x + z) % 4 == 0:
            p.put(x, 2, z, "bricks_cracked")
p.put(2, 0, 1, "cobble")                         # the ladder's backing pillar
p.fill(2, 1, 1, 2, SEA, 1, "bricks")
p.fill(WEST, SEA, 0, WEST, SEA, L - 1, "bricks")      # the edge courses under the deck kerbs
p.fill(EAST, SEA, 0, EAST, SEA, L - 1, "bricks")
for z in range(0, L, 2):
    if z not in PILLAR_ZS:
        p.put(WEST, SEA, z, "bricks_mossy")
        p.put(EAST, SEA, z, "bricks_mossy")

# the deck: polished andesite kerbs, stone brick paving, a chiseled landing at the sea end
p.fill(WEST, DECK, 0, WEST, DECK, L - 1, "andesite")
p.fill(EAST, DECK, 0, EAST, DECK, L - 1, "andesite")
p.fill(WEST + 1, DECK, 0, EAST - 1, DECK, L - 1, "bricks")
p.fill(WEST + 1, DECK, 0, EAST - 1, DECK, 0, "chiseled")

# bollards at the berths' bow and stern, mooring rings and cleats between
for z in (4, 14):
    for x in (WEST, EAST):
        p.put(x, DECK + 1, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,"
                              "west=none]")
        p.put(x, DECK + 2, z, stair("stone_brick", "south", "top"))
for z in (2, 9, 16):
    p.put(WEST, DECK + 1, z, "pirates_n_ships:mooring_ring[face=floor,facing=west,waterlogged=false]")
    p.put(EAST, DECK + 1, z, "pirates_n_ships:mooring_ring[face=floor,facing=east,waterlogged=false]")
for z in (6, 12):
    p.put(WEST, DECK + 1, z, "pirates_n_ships:cleat[face=floor,facing=north]")
    p.put(EAST, DECK + 1, z, "pirates_n_ships:cleat[face=floor,facing=north]")

# the crane at the seaward end: a spruce post, a fence jib out to the west edge with a lantern hanging from its end
CX, CZ = 4, 1
for y in range(DECK + 1, H):
    p.put(CX, y, CZ, "log")
p.put(CX, DECK + 1, CZ - 1, stair("dark_oak", "south"))
p.put(CX, DECK + 1, CZ + 1, stair("dark_oak", "north"))
p.put(CX + 1, DECK + 1, CZ, stair("dark_oak", "west"))
p.put(CX + 1, H - 1, CZ, stair_shape("dark_oak", "west", "top"))      # the jib's heel
chain(p, 2, DECK + 1, H - 2, CZ)                                      # the hoist chain down to the deck
p.put(CX + 1, DECK + 2, CZ, "pirates_n_ships:cargo_crate")            # the counterweight
banner(p, CX, H - 1, CZ + 1, "south")
for x in range(WEST, CX):
    west = "true" if x > WEST else "false"
    p.put(x, H - 1, CZ, f"minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west={west}]")
p.put(WEST, H - 2, CZ, "lantern_hanging")

# lantern posts at the seaward corners, the ladder down to the water at the end
lantern_post(p, WEST, DECK + 1, 0)
lantern_post(p, EAST, DECK + 1, 0)
for y in range(SEA - 1, DECK + 1):
    p.put(2, y, 0, "ladder_n")

# the deck's rhythm: andesite bands across it, a chiseled course every eight blocks
for z in (4, 8, 12):
    p.fill(WEST + 1, DECK, z, EAST - 1, DECK, z, "andesite")
for z in (8,):
    p.put(3, DECK, z, "chiseled")
# a capstan and a coil of chain amidships, a harbour light at the seaward end of each kerb
p.put(3, DECK + 1, 10, "log")
p.put(3, DECK + 2, 10, "minecraft:dark_oak_pressure_plate[powered=false]")
p.put(3, DECK + 1, 6, "minecraft:chain[axis=x,waterlogged=false]")
p.put(3, DECK + 1, 13, "minecraft:chain[axis=z,waterlogged=false]")

# cargo waiting at the landward end
for x, y, z in ((2, DECK + 1, 15), (2, DECK + 1, 16), (2, DECK + 2, 16)):
    p.put(x, y, z, "pirates_n_ships:cargo_crate")
p.put(4, DECK + 1, 16, "pirates_n_ships:cargo_barrel")
p.put(4, DECK + 1, 15, "minecraft:barrel[facing=up,open=false]")
p.put(4, DECK + 2, 15, "lantern")

age(p, ground=SEA, where=lambda x, y, z: y < DECK)

p.berth(0, SEA, BERTH_Z, "north")
p.berth(W - 1, SEA, BERTH_Z, "north")
connector(p, 3, DECK, L - 1, "quay_in", "south", "minecraft:stone_bricks")

dry(p)
p.emit("stone quay with mooring rings, cleats, bollards, a crane and two berths",
       ["Runs north from quay_in (south end, deck y 5 = the fort gate's paving row).",
        "Sea level is row y 4; berth markers at [0, 4, 9] and [6, 4, 9], bow north.",
        "STR7: a quay_extension section hangs from the seaward end in line with quay_in when the water is shallow."])
