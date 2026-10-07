"""Generates a navy outpost curtain wall segment (7 long along x) with a gun-port platform and prints the BuildSpec
as JSON.

The wall runs sideways from the fort gate along x; the sea is to the north (-z). Section (shared with the gate and the
tower, see _style.curtain): solid body on z 0..4 up to the walkway (y 4, people stand on y 5), the seaward parapet on
z 0 with merlons. In the middle the parapet is cut for an embrasure, and a cannon stands behind it on the walkway,
muzzle north (master at [3, 5, 1], rear at [3, 5, 2]), with a powder barrel beside it. A ladder on the landward face
(z 5) climbs to the walkway; z 5..6 is a gravel path along the foot of the wall.

Connectors (foundation row): wall_east_in [0, 0, 2] (west face) and wall_east_out [6, 0, 2] (east face) for the run
that grows east, wall_west_out [0, 0, 3] and wall_west_in [6, 0, 3] for the one that grows west (see _style)."""
from _style import EAST_Z, PALETTE, WALK, WEST_Z, cannon, connector, curtain, lantern_post, weather
from buildspec import Piece

W, H, D = 7, 8, 7
GUN_X = 3

p = Piece("navy_outpost_wall", "Navy Outpost Curtain Wall", (W, H, D), PALETTE)

curtain(p, 0, W - 1, gaps=(GUN_X,))
weather(p, ((1, 2, 0, "bricks_mossy"), (5, 3, 0, "bricks_cracked"), (2, 1, 0, "bricks_mossy"),
            (4, 2, 0, "bricks_cracked"), (6, 3, 0, "bricks_mossy")))

# the landward path at the wall's foot
p.fill(0, 0, 5, W - 1, 0, D - 1, "gravel")
p.fill(0, 0, 5, W - 1, 0, 5, "cobble")

# the gun-port platform: an oak deck under the cannon, the cannon facing the sea, powder beside it
p.fill(GUN_X - 1, WALK, 1, GUN_X + 1, WALK, 3, "oak")
cannon(p, GUN_X, WALK + 1, 1, "north")
p.put(GUN_X - 1, WALK + 1, 2, "pirates_n_ships:cargo_barrel")
p.put(GUN_X + 1, WALK + 1, 3, "minecraft:barrel[facing=up,open=false]")

# the ladder up the landward face, a lantern post at the head of it
for y in range(1, WALK + 1):
    p.put(5, y, 5, "ladder_s")
lantern_post(p, 6, WALK + 1, 4, height=1)

connector(p, 0, 0, EAST_Z, "wall_east_in", "west", "minecraft:stone_bricks")
connector(p, W - 1, 0, EAST_Z, "wall_east_out", "east", "minecraft:stone_bricks")
connector(p, 0, 0, WEST_Z, "wall_west_out", "west", "minecraft:stone_bricks")
connector(p, W - 1, 0, WEST_Z, "wall_west_in", "east", "minecraft:stone_bricks")

p.emit("stone curtain wall with a walkway, crenellations and a cannon on a gun-port platform",
       ["Sea to the north. Walkway top y 4; cannon master [3, 5, 1] facing north, rear [3, 5, 2].",
        "East run: wall_east_in [0, 0, 2] -> wall_east_out [6, 0, 2]; west run: wall_west_in [6, 0, 3] -> "
        "wall_west_out [0, 0, 3]."])
