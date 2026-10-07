"""Generates a navy outpost curtain wall segment (7 long along x) with a gun-port platform and prints the BuildSpec
as JSON.

The wall runs sideways from the fort gate along x; the sea is to the north (-z). Section (shared with the gate and the
tower, see _style.curtain): solid body on z 1..4 up to the walkway (y 4, people stand on y 5); the seaward skin on z 0
has a pilaster at each end (two walls meet pilaster to pilaster, a double buttress every seven blocks) and a recessed
bay between them on a sloped, mossy plinth, with two arrow slits, under a corbel table that carries the parapet and the
slab-capped merlons. In the middle the parapet is cut for an embrasure, and a cannon stands behind it on a dark oak gun
deck, muzzle north (master at [3, 5, 1], rear at [3, 5, 2]), with a powder barrel beside it and shot stores.

The landward face (z 4) has an arched store niche with powder and shot, and a weathered timber hoarding: spruce deck
slabs on dark oak brackets widen the walkway over the path, with a fence rail; a ladder (z 5) climbs to it, and a
lantern stands at its head. z 5..6 is a gravel path along the foot of the wall.

Connectors (foundation row): wall_east_in [0, 0, 2] (west face) and wall_east_out [6, 0, 2] (east face) for the run
that grows east, wall_west_out [0, 0, 3] and wall_west_in [6, 0, 3] for the one that grows west (see _style)."""
from _style import (EAST_Z, PALETTE, WALK, WEST_Z, age, cannon, connector, curtain, fence_run, lantern_post,
                    rafter_ends, stair_shape)
from buildspec import Piece

W, H, D = 7, 8, 7
GUN_X = 3
LADDER_X = 5
HOARD_Z = 5                      # the hoarding over the path, level with the walkway

p = Piece("navy_outpost_wall", "Navy Outpost Curtain Wall", (W, H, D), PALETTE)

curtain(p, 0, W - 1, gaps=(GUN_X,), slits=(1, W - 2))

# the landward path at the wall's foot
p.fill(0, 0, 5, W - 1, 0, D - 1, "gravel")
p.fill(0, 0, 5, W - 1, 0, 5, "cobble")

# the gun-port platform: a dark oak deck under the cannon, the cannon facing the sea, powder and shot beside it
p.fill(GUN_X - 1, WALK, 1, GUN_X + 1, WALK, 3, "dark_oak")
cannon(p, GUN_X, WALK + 1, 1, "north")
p.put(GUN_X - 1, WALK + 1, 2, "pirates_n_ships:cargo_barrel")
p.put(GUN_X + 1, WALK + 1, 3, "minecraft:barrel[facing=up,open=false]")
p.put(GUN_X + 1, WALK + 1, 2, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,"
                              "waterlogged=false]")   # the shot locker's lid

# an arched store niche in the landward face under the hoarding: a keg of powder and two crates of shot
for x in range(1, 4):
    for y in (1, 2):
        p.clear(x, y, 4)
p.put(1, 2, 4, stair_shape("stone_brick", "west", "top"))
p.put(3, 2, 4, stair_shape("stone_brick", "east", "top"))
p.put(2, 3, 4, "chiseled")
p.put(1, 1, 4, "pirates_n_ships:cargo_crate")
p.put(2, 1, 4, "minecraft:barrel[facing=north,open=false]")
p.put(3, 1, 4, "pirates_n_ships:cargo_crate")

# the hoarding: dark oak brackets on the landward face, a spruce deck level with the walkway, a fence rail
rafter_ends(p, [(x, HOARD_Z) for x in (0, 2, 4, 6)], WALK - 1, "south")
for x in range(W):
    if x != LADDER_X:
        p.put(x, WALK, HOARD_Z, "spruce_slab_top")
fence_run(p, [(x, HOARD_Z) for x in range(W) if x != LADDER_X], WALK + 1)

# the ladder up the landward face to the hoarding, a lantern post at the head of it
for y in range(1, WALK + 1):
    p.put(LADDER_X, y, 5, "ladder_s")
lantern_post(p, 6, WALK + 1, 4, height=1)

age(p)

connector(p, 0, 0, EAST_Z, "wall_east_in", "west", "minecraft:stone_bricks")
connector(p, W - 1, 0, EAST_Z, "wall_east_out", "east", "minecraft:stone_bricks")
connector(p, 0, 0, WEST_Z, "wall_west_out", "west", "minecraft:stone_bricks")
connector(p, W - 1, 0, WEST_Z, "wall_west_in", "east", "minecraft:stone_bricks")

p.emit("stone curtain wall with pilasters, a recessed bay, corbelled parapet, a cannon and a timber hoarding",
       ["Sea to the north. Walkway top y 4; cannon master [3, 5, 1] facing north, rear [3, 5, 2].",
        "East run: wall_east_in [0, 0, 2] -> wall_east_out [6, 0, 2]; west run: wall_west_in [6, 0, 3] -> "
        "wall_west_out [0, 0, 3]."])
