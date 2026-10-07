"""Generates the pirate island's beach camp (the start piece: a 13x13 sandy clearing) and prints the BuildSpec as JSON.

The sea is to the north: ``jetty_out`` on the north edge hangs the jetty from the beach (the jetty deck is level with
the sand, row y 0, which sits one block above sea level). Gravel and dirt trails run from the jetty landing to a
campfire ring with log seats, and on to ``path_out`` east and west and ``hut_out`` inland (south). The Jolly Roger
flies from a flagpole on the beach, the loot heap lies in the north-west corner, and the fence's shack (a 5x5
lean-to) stands in the north-east corner with the harbor master's desk as the fence's counter, open to the camp
(west), and a notice board on its south side.

ST4b: a crude palisade of uneven palm, spruce and dark oak stakes closes the camp's inland sides (gaps for the three
trails, taller gate posts, a skull on the south gate), the loot heap lies under a striped sailcloth lean-to on fence
poles, the fire has a roasting spit and driftwood seats, the shack's walls are vertical dark and spruce boards with
salvaged patches between palm front posts, rafter ends under its eave, a shuttered window and a striped awning over
the notice board on its south side, lantern posts light the jetty landing and the trail, and gravel, coarse dirt and
mossy stones gather at the foot of the shack, the flag and the palisade."""
from _style import (PALETTE, awning, campfire_ring, flagpole, jit, lantern_post, palisade_post, rafter_ends,
                    sand_skirt, shutter_window, weatherboard)
from buildspec import Piece

S = 13
FIRE_X, FIRE_Z = 5, 6                  # the campfire
SX0, SX1, SZ0, SZ1 = 8, 12, 0, 4       # the fence's shack (front, open, at x 8; back wall at x 12)
TRAIL_Z = 9                            # the east-west trail between the two path_out connectors
GATE_X = 6                             # the inland trail (hut_out)

p = Piece("pirate_island_camp_start", "Pirate Beach Camp", (S, 8, S), PALETTE)

# the beach, with coarse patches and a few stones
p.fill(0, 0, 0, S - 1, 0, S - 1, "sand")
for x, z, key in ((2, 5, "coarse"), (10, 11, "coarse"), (1, 11, "gravel"), (11, 7, "coarse"), (3, 10, "coarse"),
                  (8, 6, "gravel")):
    p.put(x, 0, z, key)

# trails: from the jetty landing to the fire, the east-west trail and the way inland
for z in range(0, FIRE_Z - 1):
    p.put(GATE_X, 0, z, "gravel" if z % 2 == 0 else "path")
for x in range(S):
    p.put(x, 0, TRAIL_Z, ("gravel", "path", "coarse")[x % 3])
for z in range(TRAIL_Z + 1, S):
    p.put(GATE_X, 0, z, "path")

# the campfire ring: a cobblestone hearth with mossy corners, a roasting spit, log seats on three sides (open toward
# the jetty), a keg of rum beside the south seat
campfire_ring(p, FIRE_X, 0, FIRE_Z, seats=("west", "east", "south"), spit=True)
p.put(FIRE_X + 2, 1, FIRE_Z + 2, "minecraft:barrel[facing=east,open=false]")

# the Jolly Roger on a mossy footing, flying east over open sand
p.put(3, 0, 2, "mossy")
flagpole(p, 3, 1, 2, 5, "east")
sand_skirt(p, 3, 2, 3, 2, reach=1, salt=91, density=0.7)

# the loot heap: barrels, a cargo crate, a chest, under a sailcloth lean-to (high at the back, x 0)
for x, y, z in ((0, 1, 1), (0, 2, 1), (1, 1, 1), (0, 1, 2)):
    p.put(x, y, z, "barrel")
p.put(0, 1, 3, "pirates_n_ships:cargo_crate")
p.put(1, 1, 3, "pirates_n_ships:cargo_crate")
p.put(0, 2, 3, "pirates_n_ships:cargo_barrel")
p.put(1, 1, 2, "minecraft:chest[facing=east,type=single]")
p.put(1, 2, 3, "minecraft:barrel[facing=north,open=false]")
awning(p, 0, 0, 1, 4, 4, stripes_along="z", posts=((0, 0), (0, 4)), salt=92)
awning(p, 2, 0, 2, 4, 3, stripes_along="z", posts=((2, 0), (2, 4)), salt=93)

# the fence's shack: a lean-to of weathered planks on palm and stripped posts, the roof rising from the open front
# (x 8) to the back wall (x 12) in half-block steps, a floor of planks
p.fill(SX0 + 1, 0, SZ0 + 1, SX1 - 1, 0, SZ1 - 1, "spruce")
p.put(10, 0, 2, "dark")
roof = {SX0 - 1: (4, "thatch_low"), SX0: (4, "thatch_high"), SX0 + 1: (5, "thatch_low"), SX0 + 2: (5, "thatch_high"),
        SX0 + 3: (6, "thatch_low"), SX0 + 4: (6, "thatch_high")}
for x, (y, key) in roof.items():
    p.fill(x, y, SZ0, x, y, SZ1, key)
for x, z in ((SX0 + 1, 1), (SX0 + 3, 3), (SX0 + 4, 0)):          # mended with spruce
    y, key = roof[x]
    p.put(x, y, z, "minecraft:spruce_slab[type=bottom]" if key == "thatch_low"
          else "minecraft:spruce_slab[type=top]")
top = {x: (y if key == "thatch_high" else y - 1) for x, (y, key) in roof.items()}   # walls reach under the roof
for x in range(SX0 + 1, SX1):
    for z in (SZ0, SZ1):
        p.fill(x, 1, z, x, top[x], z, "spruce" if (x + z) % 2 else "dark")
p.fill(SX1, 1, SZ0 + 1, SX1, top[SX1], SZ1 - 1, "spruce")
weatherboard(p, SX0 + 1, 1, SZ0, SX1 - 1, 6, SZ0, keys=("dark", "spruce"), salt=94)
weatherboard(p, SX0 + 1, 1, SZ1, SX1 - 1, 6, SZ1, keys=("spruce", "dark"), salt=95)
weatherboard(p, SX1, 1, SZ0 + 1, SX1, 6, SZ1 - 1, keys=("spruce", "dark"), salt=96)
for z in (SZ0, SZ1):
    p.fill(SX0, 1, z, SX0, top[SX0], z, "palm")
    p.fill(SX1, 1, z, SX1, top[SX1], z, "post")
# a sill beam along the foot of the back and side walls
p.fill(SX0 + 1, 1, SZ0, SX1 - 1, 1, SZ0, "dark_beam_x")
p.fill(SX1, 1, SZ0 + 1, SX1, 1, SZ1 - 1, "dark_beam_z")
rafter_ends(p, [(SX0 - 1, SZ0), (SX0 - 1, 2), (SX0 - 1, SZ1)], 3, "east")
# windows: a barred one toward the sea, a barred one in the back wall, a shuttered one on the south side
p.put(10, 2, SZ0, "fence")
p.put(SX1, 3, 2, "fence")
shutter_window(p, 11, 2, SZ1, "south", sides=("right",), wood="dark_oak")
# the counter: the harbor master's desk facing the camp, a plank counter top beside it, a gap to step behind
p.put(SX0 + 1, 1, 2, "pirates_n_ships:harbor_desk[facing=west]")
p.put(SX0 + 1, 1, 1, "minecraft:spruce_slab[type=top]")
p.put(SX0 + 1, 2, 1, "lantern")
# the fence's stock and a cobweb in the corner
p.put(11, 1, 1, "pirates_n_ships:cargo_crate")
p.put(11, 2, 1, "pirates_n_ships:cargo_crate")
p.put(11, 1, 3, "pirates_n_ships:cargo_barrel")
p.put(11, 4, 1, "cobweb")
p.put(10, 4, 3, "cobweb")
# the notice board against the shack's south wall, facing the camp's trail, under a striped sailcloth awning
p.put(10, 1, SZ1 + 1, "pirates_n_ships:notice_board[facing=south]")
awning(p, SX0 + 1, SZ1 + 1, SX1, SZ1 + 1, 3, stripes_along="z", salt=97)
sand_skirt(p, SX0, SZ0, SX1, SZ1, reach=2, salt=98)

# the palisade: uneven stakes along the inland edges with gaps for the trails, taller posts at the gates
stakes = ([(x, S - 1) for x in range(0, S) if abs(x - GATE_X) > 1]
          + [(0, z) for z in range(5, S - 1) if z != TRAIL_Z]
          + [(S - 1, z) for z in range(6, S - 1) if z != TRAIL_Z])
gate_posts = {(GATE_X - 2, S - 1), (GATE_X + 2, S - 1), (0, TRAIL_Z - 1), (0, TRAIL_Z + 1), (S - 1, TRAIL_Z - 1),
              (S - 1, TRAIL_Z + 1)}
for x, z in stakes:
    height = 3 if (x, z) in gate_posts else 1 + jit(x, 1, z, 99) % 2
    palisade_post(p, x, 1, z, height=height)
    sand_skirt(p, x, z, x, z, reach=1, salt=100 + x + z, density=0.35)
p.put(GATE_X + 2, 5, S - 1, "minecraft:skeleton_skull[powered=false,rotation=8]")
p.put(GATE_X - 2, 5, S - 1, "torch")

# lantern posts at the jetty landing and along the trail
lantern_post(p, 5, 1, 0)
lantern_post(p, 7, 1, 0)
lantern_post(p, 11, 1, TRAIL_Z + 1)
lantern_post(p, 1, 1, TRAIL_Z - 1)
p.put(9, 1, 11, "dead_bush")
p.put(2, 1, 7, "dead_bush")

p.connector(GATE_X, 0, 0, "jetty_out", "north", "minecraft:gravel")
p.connector(0, 0, TRAIL_Z, "path_out", "west", "minecraft:gravel")
p.connector(S - 1, 0, TRAIL_Z, "path_out", "east", "minecraft:gravel")
p.connector(GATE_X, 0, S - 1, "hut_out", "south", "minecraft:dirt_path")

p.emit("pirate beach camp: palisade, campfire ring with a spit, Jolly Roger, loot heap under a sail, the fence's "
       "lean-to shack",
       ["Start piece. Sea to the north: jetty_out at [6, 0, 0], path_out at [0, 0, 9] and [12, 0, 9], "
        "hut_out at [6, 0, 12].",
        "The sand (y 0) sits one block above sea level; the jetty's deck continues it."])
