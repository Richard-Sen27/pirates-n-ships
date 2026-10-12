"""Generates a small village market square (15x15, a dead end; STR3) and prints the BuildSpec as JSON.

The street comes in from the north and opens into a paved square with rounded corners; it leads nowhere else, so
buildings stand round it on the west, east and south sides. In the middle a market stall: four posts under a striped
awning of red and white wool, a counter of barrels and crates with produce (melons, pumpkins, hay, a basket of
apples, a cask of fish on ice). Lantern posts at the corners, benches. At the back of the square, behind the stall and
facing it and the street, the 2x2 market notice board on its posts (NB2; anchor, the viewer's bottom left, at x 8)."""
from _streets import corner_lanterns, lay, line, square_paving, square_verge
from _style import PALETTE, bench
from buildspec import Piece

S = 15
C = 7
p = Piece("village_street_square_market", "Seafarer Square (market)", (S, 7, S), PALETTE)

street = lay(p, line(C, -4, C, 4), salt=371)
cells = square_paving(p, 1, 3, S - 2, S - 2, salt=372, street=street)
square_verge(p, cells, salt=373)

# the stall: posts at the corners of x 5..9, z 7..10, the awning on top, the counter on the street side
X0, X1, Z0, Z1 = 5, 9, 7, 10
for x in (X0, X1):
    for z in (Z0, Z1):
        p.fill(x, 1, z, x, 3, z, "post")
for z in range(Z0, Z1 + 1):          # stripes running across the stall, shifted every row
    for x in range(X0, X1 + 1):
        p.put(x, 4, z, "red" if (x + z) % 2 == 0 else "white")
p.put(6, 1, Z0, "cargo_barrel")
p.put(7, 1, Z0, "minecraft:spruce_slab[type=top]")
p.put(7, 2, Z0, "minecraft:packed_ice")
p.put(8, 1, Z0, "crate")
p.put(8, 2, Z0, "minecraft:melon")
p.put(6, 2, Z0, "minecraft:carved_pumpkin[facing=north]")
p.put(X0, 1, 8, "minecraft:hay_block[axis=y]")
p.put(X1, 1, 9, "barrel_up")
p.put(7, 1, Z1, "minecraft:composter[level=0]")
p.put(6, 1, 9, "minecraft:spruce_stairs[facing=south,half=bottom]")
p.put(7, 3, 8, "lantern_hanging")

# NB2: the market notice board at the centre-back, notices north towards the stall and the street; the viewer's left is
# east, so the anchor (bottom left) is at x 8 and the board runs west to x 7 and up to y 2
BOARD = "pirates_n_ships:market_notice_board[facing=north,part=%s]"
p.put(8, 1, 12, BOARD % "bottom_left")
p.put(7, 1, 12, BOARD % "bottom_right")
p.put(8, 2, 12, BOARD % "top_left")
p.put(7, 2, 12, BOARD % "top_right")

corner_lanterns(p, 2, 4, S - 3, S - 3)
bench(p, 2, 1, 8, "east")
bench(p, S - 3, 1, 8, "west")
p.put(S - 3, 1, 5, "crate")
p.put(S - 3, 2, 5, "barrel_up")

p.connector(C, 0, 0, "street_in", "north", "minecraft:cobblestone")
p.connector(0, 0, 8, "building_out", "west", "minecraft:dirt_path")
p.connector(S - 1, 0, 8, "building_out", "east", "minecraft:dirt_path")
p.connector(C, 0, S - 1, "building_out", "south", "minecraft:dirt_path")
p.connector(0, 0, 12, "deco_out", "west", "minecraft:dirt_path")
p.connector(S - 1, 0, 12, "deco_out", "east", "minecraft:dirt_path")

p.emit("a small paved market square with a striped stall in the middle and the market notice board at the back, "
       "a dead end ringed by buildings",
       ["street_in at [7, 0, 0] (north); building_out at [0, 0, 8] (west), [14, 0, 8] (east) and [7, 0, 14] "
        "(south); deco_out at [0, 0, 12] (west) and [14, 0, 12] (east)."])
