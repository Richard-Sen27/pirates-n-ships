"""Generates the pirate island's beach camp (the start piece: a 13x13 sandy clearing) and prints the BuildSpec as JSON.

The sea is to the north: ``jetty_out`` on the north edge hangs the jetty from the beach (the jetty deck is level with
the sand, row y 0, which sits one block above sea level). Gravel and dirt trails run from the jetty landing to a
campfire ring with log seats, and on to ``path_out`` east and west and ``hut_out`` inland (south). The Jolly Roger
flies from a flagpole on the beach, the loot heap lies in the north-west corner, and the fence's shack (a 5x5
lean-to) stands in the north-east corner with the harbor master's desk as the fence's counter, open to the camp
(west), and a notice board on its south side."""
from _style import PALETTE, flagpole, torch_post
from buildspec import Piece

S = 13
FIRE_X, FIRE_Z = 5, 6                  # the campfire
SX0, SX1, SZ0, SZ1 = 8, 12, 0, 4       # the fence's shack (front, open, at x 8; back wall at x 12)
TRAIL_Z = 9                            # the east-west trail between the two path_out connectors

p = Piece("pirate_island_camp_start", "Pirate Beach Camp", (S, 8, S), PALETTE)

# the beach, with coarse patches and a few stones
p.fill(0, 0, 0, S - 1, 0, S - 1, "sand")
for x, z, key in ((2, 5, "coarse"), (10, 11, "coarse"), (1, 11, "gravel"), (11, 7, "coarse"), (3, 10, "coarse"),
                  (8, 6, "gravel")):
    p.put(x, 0, z, key)

# trails: from the jetty landing to the fire, the east-west trail and the way inland
for z in range(0, FIRE_Z - 1):
    p.put(6, 0, z, "gravel" if z % 2 == 0 else "path")
for x in range(S):
    p.put(x, 0, TRAIL_Z, ("gravel", "path", "coarse")[x % 3])
for z in range(TRAIL_Z + 1, S):
    p.put(6, 0, z, "path")

# the campfire ring: a cobblestone hearth with mossy corners, logs as seats on three sides (open toward the jetty)
p.fill(FIRE_X - 1, 0, FIRE_Z - 1, FIRE_X + 1, 0, FIRE_Z + 1, "cobble")
for dx in (-1, 1):
    for dz in (-1, 1):
        p.put(FIRE_X + dx, 0, FIRE_Z + dz, "mossy")
p.put(FIRE_X, 1, FIRE_Z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
p.fill(FIRE_X - 2, 1, FIRE_Z - 1, FIRE_X - 2, 1, FIRE_Z + 1, "beam_z")
p.fill(FIRE_X + 2, 1, FIRE_Z - 1, FIRE_X + 2, 1, FIRE_Z + 1, "beam_z")
p.fill(FIRE_X - 1, 1, FIRE_Z + 2, FIRE_X + 1, 1, FIRE_Z + 2, "beam_x")

# the Jolly Roger on a mossy footing, flying east over open sand
p.put(3, 0, 2, "mossy")
flagpole(p, 3, 1, 2, 5, "east")

# the loot heap: barrels, a cargo crate, a chest
for x, y, z in ((0, 1, 1), (0, 2, 1), (1, 1, 1), (0, 1, 2)):
    p.put(x, y, z, "barrel")
p.put(0, 1, 3, "pirates_n_ships:cargo_crate")
p.put(1, 1, 3, "pirates_n_ships:cargo_crate")
p.put(0, 2, 3, "pirates_n_ships:cargo_barrel")
p.put(1, 1, 2, "minecraft:chest[facing=east,type=single,waterlogged=false]")

# the fence's shack: a lean-to of weathered planks on stripped posts, the roof rising from the open front (x 8) to the
# back wall (x 12) in half-block steps, a floor of planks
p.fill(SX0 + 1, 0, SZ0 + 1, SX1 - 1, 0, SZ1 - 1, "spruce")
p.put(10, 0, 2, "dark")
roof = {SX0 - 1: (4, "thatch_low"), SX0: (4, "thatch_high"), SX0 + 1: (5, "thatch_low"), SX0 + 2: (5, "thatch_high"),
        SX0 + 3: (6, "thatch_low"), SX0 + 4: (6, "thatch_high")}
for x, (y, key) in roof.items():
    p.fill(x, y, SZ0, x, y, SZ1, key)
top = {x: (y if key == "thatch_high" else y - 1) for x, (y, key) in roof.items()}   # walls reach under the roof
for x in range(SX0 + 1, SX1):
    for z in (SZ0, SZ1):
        p.fill(x, 1, z, x, top[x], z, "spruce" if (x + z) % 2 else "dark")
p.fill(SX1, 1, SZ0 + 1, SX1, top[SX1], SZ1 - 1, "spruce")
for z in (SZ0, SZ1):
    p.fill(SX0, 1, z, SX0, top[SX0], z, "post")
    p.fill(SX1, 1, z, SX1, top[SX1], z, "post")
p.put(SX1, 3, 2, "dark")
# the counter: the harbor master's desk facing the camp, a plank counter top beside it, a gap to step behind
p.put(SX0 + 1, 1, 2, "pirates_n_ships:harbor_desk[facing=west]")
p.put(SX0 + 1, 1, 1, "minecraft:spruce_slab[type=top,waterlogged=false]")
p.put(SX0 + 1, 2, 1, "lantern")
# the fence's stock and a cobweb in the corner
p.put(11, 1, 1, "pirates_n_ships:cargo_crate")
p.put(11, 2, 1, "pirates_n_ships:cargo_crate")
p.put(11, 1, 3, "pirates_n_ships:cargo_barrel")
p.put(11, 4, 1, "cobweb")
p.put(10, 4, 3, "cobweb")
# the notice board against the shack's south wall, facing the camp's trail
p.put(10, 1, SZ1 + 1, "pirates_n_ships:notice_board[facing=south]")

# torches on fence posts at the jetty landing and along the trail
torch_post(p, 5, 1, 0)
torch_post(p, 7, 1, 0)
torch_post(p, 11, 1, TRAIL_Z + 1)
torch_post(p, 1, 1, TRAIL_Z - 1)
p.put(9, 1, 11, "minecraft:dead_bush")
p.put(2, 1, 7, "minecraft:dead_bush")

p.connector(6, 0, 0, "jetty_out", "north", "minecraft:gravel")
p.connector(0, 0, TRAIL_Z, "path_out", "west", "minecraft:gravel")
p.connector(S - 1, 0, TRAIL_Z, "path_out", "east", "minecraft:gravel")
p.connector(6, 0, S - 1, "hut_out", "south", "minecraft:dirt_path")

p.emit("pirate beach camp: campfire ring, Jolly Roger, loot heap and the fence's lean-to shack",
       ["Start piece. Sea to the north: jetty_out at [6, 0, 0], path_out at [0, 0, 9] and [12, 0, 9], "
        "hut_out at [6, 0, 12].",
        "The sand (y 0) sits one block above sea level; the jetty's deck continues it."])
