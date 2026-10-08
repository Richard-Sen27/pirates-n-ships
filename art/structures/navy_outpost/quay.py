"""Generates the navy outpost's stone quay (a 5 wide masonry mole, 18 long) and prints the BuildSpec as JSON.

It runs north (seaward) from its ``quay_in`` connector at the south end, which hangs it from the fort gate's sea gate:
the deck is at y 5, level with the gate's paving, and the sea surface is row y 4, like the village pier. The mole is
solid stone bricks from the seabed row (y 0) to the deck, mossy at the waterline. Columns x 0 and x 6 hold the two
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

# the mole: stone bricks from the seabed to the deck, mossy at the waterline, a chiseled course under the deck edge
p.fill(WEST, 0, 0, EAST, DECK - 1, L - 1, "bricks")
p.fill(WEST, SEA - 1, 0, EAST, SEA, L - 1, "bricks_mossy")
p.fill(WEST + 1, SEA - 1, 1, EAST - 1, SEA, L - 1, "bricks")
for z in range(0, L, 3):
    p.put(WEST, 2, z, "bricks_cracked")
    p.put(EAST, 1, (z + 1) % L, "bricks_cracked")

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
        "Sea level is row y 4; berth markers at [0, 4, 9] and [6, 4, 9], bow north."])
