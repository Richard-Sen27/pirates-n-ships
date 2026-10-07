"""Generates the seafarer village's dock head (the start piece: an 11x11 stone quay with the harbor master's hut) and
prints the BuildSpec as JSON.

The sea is to the north: ``pier_out`` on the north edge hangs the pier from the quay (the pier deck is level with the
paving, row y 0, which sits one block above sea level), ``street_out`` on the south edge leads inland. The hut (east
half) has its door on the north side; inside, the harbor master's desk faces the door (its customer side north), and
the notice board stands on the quay beside the door, its notices facing the quay."""
from _style import DARK, PALETTE, door, lantern_post
from buildspec import Piece

S = 11
HX0, HX1, HZ0, HZ1 = 5, 10, 4, 9      # the hut's walls
TOP = 3                               # last wall row

p = Piece("village_dock_head", "Seafarer Dock Head", (S, 8, S), PALETTE)

# the quay: stone bricks with weathered patches, a chiseled course along the sea edge
p.fill(0, 0, 0, S - 1, 0, S - 1, "bricks")
p.fill(0, 0, 0, S - 1, 0, 0, "chiseled")
for x, z, key in ((1, 3, "bricks_mossy"), (3, 6, "bricks_cracked"), (0, 9, "bricks_mossy"), (4, 1, "bricks_cracked"),
                  (2, 8, "bricks_cracked"), (9, 1, "bricks_mossy"), (1, 10, "bricks_cracked")):
    p.put(x, 0, z, key)

# the harbor master's hut: render walls on posts, plank floor, dark roof with the ridge along x
p.fill(HX0 + 1, 0, HZ0 + 1, HX1 - 1, 0, HZ1 - 1, "spruce")
p.ring(HX0, HZ0, HX1, HZ1, 1, TOP, "render")
for x, z in ((HX0, HZ0), (HX1, HZ0), (HX0, HZ1), (HX1, HZ1)):
    p.fill(x, 1, z, x, TOP, z, "post")
p.roof_ridge_x(HX0, HX1, HZ0, HZ1, TOP + 1, DARK, "dark_ridge", "render", plate="beam_x")
door(p, 7, 1, HZ0)
for x, y, z in ((9, 2, HZ0), (HX1, 2, 6), (HX1, 2, 8), (HX0, 2, 7), (8, 2, HZ1)):
    p.put(x, y, z, "pane")

# inside: the desk across the room facing the door, cargo behind the harbor master, a lantern on the crates
p.put(7, 1, 6, "pirates_n_ships:harbor_desk[facing=north]")
p.put(6, 1, 8, "pirates_n_ships:cargo_barrel")
p.put(9, 1, 8, "pirates_n_ships:cargo_crate")
p.put(9, 2, 8, "pirates_n_ships:cargo_crate")
p.put(9, 3, 8, "lantern")
p.put(6, 1, 5, "minecraft:barrel[facing=up]")
p.put(9, 1, 5, "minecraft:lectern[facing=west,has_book=false,powered=false]")

# the notice board on the quay beside the hut door, facing the quay and the pier
p.put(9, 1, 2, "pirates_n_ships:notice_board[facing=north]")

# the pier landing between two lantern posts, mooring rings along the sea edge
lantern_post(p, 3, 1, 0)
lantern_post(p, 7, 1, 0)
p.put(1, 1, 0, "pirates_n_ships:mooring_ring[face=floor,facing=north,waterlogged=false]")
p.put(9, 1, 0, "pirates_n_ships:mooring_ring[face=floor,facing=north,waterlogged=false]")
lantern_post(p, 4, 1, 9)

# cargo waiting on the west side of the quay
for x, y, z in ((0, 1, 5), (0, 1, 6), (1, 1, 6), (0, 2, 6)):
    p.put(x, y, z, "pirates_n_ships:cargo_crate")
for x, y, z in ((0, 1, 8), (1, 1, 8), (0, 2, 8)):
    p.put(x, y, z, "pirates_n_ships:cargo_barrel")
p.put(1, 1, 3, "minecraft:barrel[facing=up]")
p.put(2, 1, 3, "minecraft:barrel[facing=north]")

p.connector(5, 0, 0, "pier_out", "north", "minecraft:chiseled_stone_bricks")
p.connector(2, 0, S - 1, "street_out", "south", "minecraft:stone_bricks")

p.emit("stone quay with the harbor master's hut, a notice board, lanterns and cargo",
       ["Start piece. Sea to the north: pier_out at [5, 0, 0], street_out at [2, 0, 10].",
        "The paving (y 0) sits one block above sea level; the pier's deck continues it."])
