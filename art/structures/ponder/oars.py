"""Generates the Ponder scene "oars" (OAR1, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 9x5x9 patch of calm sea: a sand seabed (y 0, Ponder's base plate) and water on y 1..2, with a small ship, bow north
(hull x 2..6, z 1..7: a spruce bottom on y 1, walls on y 2 round a dry hold, a deck on y 3). On her deck: an oak railing
along the port (west) edge from z 2 to z 6 (the rail a standing rower rows over), an oak oar bench on the starboard
edge at [6, 4, 3] with its thole pin to starboard (facing east), and the helm aft at [4, 4, 7] facing south. The scene
(almanac/ponder/OarsScene) puts the crew on deck, sends them to the rail and the bench and gives them their oars."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 9, 5, 9
BENCH = (6, 4, 3)
HELM = (4, 4, 7)
RAIL_X = 2
RAIL_Z = (2, 6)


def railing(z: int) -> str:
    nw = "true" if z == RAIL_Z[0] else "false"
    sw = "true" if z == RAIL_Z[1] else "false"
    return (f"{MOD}:oak_railing[east=false,north=false,post_ne=false,post_nw={nw},post_se=false,post_sw={sw},"
            f"south=false,waterlogged=false,west=true]")


p = Piece("ponder_oars", "Ponder: Oars", (W, H, D), {})
seabed(p)
footprint = hull(p, 2, 1, 6, 7, bottom=1, wall_top=2, deck=3)
sea(p, 2, dry=[footprint])
for z in range(RAIL_Z[0], RAIL_Z[1] + 1):
    p.put(RAIL_X, 4, z, railing(z))
p.put(*BENCH, f"{MOD}:oak_oar_bench[facing=east]")
p.put(*HELM, f"{MOD}:helm[facing=south]")

p.emit("ponder scene: a small ship in a calm with a railing to port, an oar bench to starboard and the helm aft",
       ["Bow north. Hull x 2..6, z 1..7: bottom y 1, walls y 2, deck y 3; sea y 1..2.",
        f"Railing on the west edge x {RAIL_X}, y 4, z {RAIL_Z[0]}..{RAIL_Z[1]}; oar bench {list(BENCH)} facing east; "
        f"helm {list(HELM)}."])
