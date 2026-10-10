"""Generates the Ponder scene "ratline_nets" (PND4, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 9x9x9 patch of a ship's deck: spruce planks on y 0 (Ponder's base plate), bow north. An oak railing along the port
(west) edge at x 0, y 1, z 2..6 (railed on its west edge, end posts at both ends) with the invisible net ends of three
railing latches over it at [0, 2, 2], [0, 2, 4] and [0, 2, 6] (RL5: the scene sets each railing's latch when it strings
the net, so the eye bolt appears on the cap then). The mast stands at x 4, z 4 from y 1 to y 6, a mast hoop on its west
face at [3, 6, 4] (six blocks up, facing west, the side the default camera from the north-west sees, PND3), and the
crow's nest on the mast's top at [4, 7, 4], one block over the hoop (RL3's masthead rig). The nets themselves are block
entity links the scene strings (almanac/ponder/RatlineNetsScene); the climbable cells are invisible and left out. No
yards: the yard rule has nothing to place."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 9, 9, 9
RAIL_X = 0
RAIL_Z = (2, 6)
PLATES = ((0, 2, 2), (0, 2, 4), (0, 2, 6))
MAST_X, MAST_Z, MAST_TOP = 4, 4, 6
HOOP = (3, 6, 4)
NEST = (4, 7, 4)


def railing(z: int) -> str:
    nw = "true" if z == RAIL_Z[0] else "false"
    sw = "true" if z == RAIL_Z[1] else "false"
    return (f"{MOD}:oak_railing[east=false,north=false,post_ne=false,post_nw={nw},post_se=false,post_sw={sw},"
            f"south=false,waterlogged=false,west=true]")


p = Piece("ponder_ratline_nets", "Ponder: Ratline Nets", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
for z in range(RAIL_Z[0], RAIL_Z[1] + 1):
    p.put(RAIL_X, 1, z, railing(z))
for plate in PLATES:
    p.put(*plate, f"{MOD}:railing_latch_end[facing=west,waterlogged=false]")
for y in range(1, MAST_TOP + 1):
    p.put(MAST_X, y, MAST_Z, "minecraft:oak_log[axis=y]")
p.put(*HOOP, f"{MOD}:mast_hoop[facing=west,waterlogged=false]")
p.put(*NEST, f"{MOD}:crows_nest[pole=false,waterlogged=false]")

p.emit("ponder scene: a deck with a railing to port with three latch net ends over it, a mast with a hoop and a crow's nest",
       [f"Bow (north) at z 0. Railing x {RAIL_X}, y 1, z {RAIL_Z[0]}..{RAIL_Z[1]}; latch net ends over it at "
        f"{[list(c) for c in PLATES]}.",
        f"Mast x {MAST_X}, z {MAST_Z}, y 1..{MAST_TOP}; hoop {list(HOOP)} facing west; nest {list(NEST)}."])
