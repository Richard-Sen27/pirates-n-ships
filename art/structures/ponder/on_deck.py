"""Generates the Ponder scene "on_deck" (PND4, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

An 11x7x11 patch of calm sea: a sand seabed (y 0, Ponder's base plate) and water on y 1..2, with our ship, bow north
(hull x 3..8, z 1..9: a spruce bottom on y 1, walls on y 2 round a dry hold, a deck on y 3), an oak railing all along
her port (west) gunwale at x 3, y 4, z 1..9 (end posts at the bow and the stern), the side the default camera from the
north-west sees (PND3), and two floor chainplates on that railing's top at [3, 5, 3] and [3, 5, 6], the ends of the
boarding net the scene strings (RAIL5). West of her, across one block of water, a pirate longboat: a raft of spruce
planks on y 2 at x 0..1, z 3..6. The leaners stand in the railing's cells at z 1 and z 8."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 11, 7, 11
X = 3
RAIL_Z = (1, 9)
PLATES = ((X, 5, 3), (X, 5, 6))
BOAT = (0, 3, 1, 6)


def railing(z: int) -> str:
    nw = "true" if z == RAIL_Z[0] else "false"
    sw = "true" if z == RAIL_Z[1] else "false"
    return (f"{MOD}:oak_railing[east=false,north=false,post_ne=false,post_nw={nw},post_se=false,post_sw={sw},"
            f"south=false,waterlogged=false,west=true]")


p = Piece("ponder_on_deck", "Ponder: On Deck", (W, H, D), {})
seabed(p)
footprint = hull(p, 3, 1, 8, 9, bottom=1, wall_top=2, deck=3)
p.fill(BOAT[0], 2, BOAT[1], BOAT[2], 2, BOAT[3], "minecraft:spruce_planks")
sea(p, 2, dry=[footprint])
for z in range(RAIL_Z[0], RAIL_Z[1] + 1):
    p.put(X, 4, z, railing(z))
for plate in PLATES:
    p.put(*plate, f"{MOD}:chainplate[face=floor,facing=north,waterlogged=false]")

p.emit("ponder scene: a ship with a railing to port and two chainplates on it, a pirate longboat alongside",
       ["Bow north. Hull x 3..8, z 1..9: bottom y 1, walls y 2, deck y 3; sea y 1..2.",
        f"Railing x {X}, y 4, z {RAIL_Z[0]}..{RAIL_Z[1]}; chainplates {[list(c) for c in PLATES]}; "
        f"longboat x {BOAT[0]}..{BOAT[2]}, z {BOAT[1]}..{BOAT[3]}, y 2."])
