"""Generates a small village market square (15x15, a dead end; STR3) and prints the BuildSpec as JSON.

The street comes in from the north and opens into a paved square with rounded corners; it leads nowhere else, so
buildings stand round it on the west, east and south sides. In the middle a market stall: four posts under a striped
awning of red and white wool, a counter of barrels and crates with produce (melons, pumpkins, hay, a basket of
apples, a cask of fish on ice). Lantern posts at the corners, benches.

LIFE1: two stalls (provisions west, the chandler east) with an aisle between them, two benches south-east and a
free 2x2 spot at x 3..4, z 11..12 for the market notice board (NB2), which stands on its back row (z 12, anchor at
x 4), the notices facing the stalls."""
from _streets import corner_lanterns, lay, line, square_paving, square_verge
from _style import PALETTE, bench
from buildspec import Piece

S = 15
C = 7
p = Piece("village_street_square_market", "Seafarer Square (market)", (S, 7, S), PALETTE)

street = lay(p, line(C, -4, C, 4), salt=371)
cells = square_paving(p, 1, 3, S - 2, S - 2, salt=372, street=street)
square_verge(p, cells, salt=373)

# LIFE1: two stalls with a vendor each (mob/village/ResidentPosts), an aisle between them from the street to the south
# building, and a free 2x2 spot for the market notice board (NB2) south of the west stall at x 3..4, z 11..12
Z0, Z1 = 6, 9


def stall(x0, x1):
    for x in (x0, x1):
        for z in (Z0, Z1):
            p.fill(x, 1, z, x, 3, z, "post")
    for z in range(Z0, Z1 + 1):          # stripes running across the stall, shifted every row
        for x in range(x0, x1 + 1):
            p.put(x, 4, z, "red" if (x + z) % 2 == 0 else "white")


# west: provisions (salt pork, hardtack, rum); the counter on the aisle side, the vendor behind it at (4, 1, 7)
stall(2, 5)
p.put(5, 1, 7, "minecraft:spruce_slab[type=top]")
p.put(5, 1, 8, "cargo_barrel")
p.put(5, 2, 8, "minecraft:carved_pumpkin[facing=east]")
p.put(2, 1, 7, "crate")
p.put(2, 1, 8, "barrel_up")
p.put(2, 2, 7, "minecraft:hay_block[axis=y]")
p.put(3, 3, 8, "lantern_hanging")
# east: the chandler (rope, cloth, nails); the vendor behind the counter at (10, 1, 8)
stall(9, 12)
p.put(9, 1, 8, "minecraft:spruce_slab[type=top]")
p.put(9, 1, 7, "crate")
p.put(9, 2, 7, "pirates_n_ships:rope_coil[facing=north,layers=2]")
p.put(12, 1, 7, "barrel_up")
p.put(12, 1, 8, "minecraft:white_wool")
p.put(11, 3, 8, "lantern_hanging")

# NB2: the market notice board in LIFE1's free spot, on z 12 with z 11 free in front of it, notices north towards the
# stalls and the aisle; the viewer's left is east, so the anchor (bottom left) is at x 4 and the board runs west to x 3
BOARD = "pirates_n_ships:market_notice_board[facing=north,part=%s]"
p.put(4, 1, 12, BOARD % "bottom_left")
p.put(3, 1, 12, BOARD % "bottom_right")
p.put(4, 2, 12, BOARD % "top_left")
p.put(3, 2, 12, BOARD % "top_right")

corner_lanterns(p, 2, 4, S - 3, S - 3)
bench(p, 10, 1, 12, "north")
bench(p, 11, 1, 12, "north")

p.connector(C, 0, 0, "street_in", "north", "minecraft:cobblestone")
p.connector(0, 0, 8, "building_out", "west", "minecraft:dirt_path")
p.connector(S - 1, 0, 8, "building_out", "east", "minecraft:dirt_path")
p.connector(C, 0, S - 1, "building_out", "south", "minecraft:dirt_path")
p.connector(0, 0, 12, "deco_out", "west", "minecraft:dirt_path")
p.connector(S - 1, 0, 12, "deco_out", "east", "minecraft:dirt_path")

p.emit("a small paved market square with a striped stall in the middle, a dead end ringed by buildings",
       ["street_in at [7, 0, 0] (north); building_out at [0, 0, 8] (west), [14, 0, 8] (east) and [7, 0, 14] "
        "(south); deco_out at [0, 0, 12] (west) and [14, 0, 12] (east)."])
