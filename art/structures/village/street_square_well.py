"""Generates a small village square with a well (15x15; STR3) and prints the BuildSpec as JSON.

The street comes in from the north, opens into a paved square with rounded corners (a stone brick border, cobblestone
with andesite and stone brick flags) and leaves on the south side. In the middle a well: a stone brick curb round the
water, a low wall, two posts carrying a little gabled roof with a lantern on a chain. Benches face the well, lantern
posts stand at the corners, a barrel with flowers and a crate wait by the edge. Buildings on the west and east sides,
decorations beside them."""
from _streets import corner_lanterns, lay, line, square_paving, square_verge
from _style import PALETTE, bench, stairs_state
from buildspec import Piece

S = 15
C = 7
WZ = 8          # the well's centre row
p = Piece("village_street_square_well", "Seafarer Square (well)", (S, 8, S), PALETTE)

street = lay(p, line(C, -4, C, S + 3), salt=361)
cells = square_paving(p, 1, 3, S - 2, S - 3, salt=362, street=street)
square_verge(p, cells, salt=363)

# the well
p.fill(C - 1, 0, WZ - 1, C + 1, 0, WZ + 1, "bricks")
p.put(C, 0, WZ, "minecraft:water[level=0]")
for x in range(C - 1, C + 2):
    for z in range(WZ - 1, WZ + 2):
        if (x, z) != (C, WZ):
            p.put(x, 1, z, "minecraft:stone_brick_wall")
for x in (C - 1, C + 1):
    p.fill(x, 2, WZ, x, 3, WZ, "fence")
p.fill(C - 1, 4, WZ, C + 1, 4, WZ, "beam_x")
p.fill(C - 1, 4, WZ - 1, C + 1, 4, WZ - 1, stairs_state("spruce", "south"))
p.fill(C - 1, 4, WZ + 1, C + 1, 4, WZ + 1, stairs_state("spruce", "north"))
p.fill(C - 1, 5, WZ, C + 1, 5, WZ, "slab")
p.put(C, 3, WZ, "chain_y")
p.put(C, 2, WZ, "lantern_hanging")

# benches round the well, lanterns at the corners, a barrel with flowers and a crate by the edge
bench(p, C, 1, WZ - 3, "south")
bench(p, C - 3, 1, WZ, "east")
bench(p, C + 3, 1, WZ, "west")
corner_lanterns(p, 2, 4, S - 3, S - 4)
p.put(S - 3, 1, 6, "barrel_up")
p.put(S - 3, 2, 6, "minecraft:flower_pot")
p.put(2, 1, 10, "crate")

p.connector(C, 0, 0, "street_in", "north", "minecraft:cobblestone")
p.connector(C, 0, S - 1, "street_out", "south", "minecraft:cobblestone")
p.connector(0, 0, WZ, "building_out", "west", "minecraft:dirt_path")
p.connector(S - 1, 0, WZ, "building_out", "east", "minecraft:dirt_path")
p.connector(0, 0, 12, "deco_out", "west", "minecraft:dirt_path")
p.connector(S - 1, 0, 4, "deco_out", "east", "minecraft:dirt_path")

p.emit("a small paved village square with a roofed well, benches and lantern posts",
       ["street_in at [7, 0, 0] (north), street_out at [7, 0, 14] (south); building_out at [0, 0, 8] (west) and "
        "[14, 0, 8] (east); deco_out at [0, 0, 12] (west) and [14, 0, 4] (east)."])
