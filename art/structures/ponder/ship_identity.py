"""Generates the Ponder scene "ship_identity" (PND4, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

An 11x6x11 patch of calm sea: a sand seabed (y 0, Ponder's base plate) and water on y 1..2, with a ship, bow north
(hull x 3..7, z 2..9: a spruce bottom on y 1, walls on y 2 round a dry hold, a deck on y 3) and her helm aft at
[5, 4, 8] facing south. On her port (west) side, the side the default camera from the north-west sees (PND3), three
name boards on the deck row's outer face at x 2, y 3, z 4..6, facing west (the viewer's left is north, so the row's
master is the board at z 4); saved alone (no joins), the scene joins them as they are placed. A mermaid figurehead
hangs on the bow at [5, 3, 1], facing north."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 11, 6, 11
HELM = (5, 4, 8)
BOARDS = ((2, 3, 4), (2, 3, 5), (2, 3, 6))
FIGUREHEAD = (5, 3, 1)

p = Piece("ponder_ship_identity", "Ponder: Ship Identity", (W, H, D), {})
seabed(p)
footprint = hull(p, 3, 2, 7, 9, bottom=1, wall_top=2, deck=3)
sea(p, 2, dry=[footprint])
p.put(*HELM, f"{MOD}:helm[facing=south]")
for board in BOARDS:
    p.put(*board, f"{MOD}:nameplate[facing=west,left=false,right=false,waterlogged=false]")
p.put(*FIGUREHEAD, f"{MOD}:figurehead_mermaid[facing=north]")

p.emit("ponder scene: a small ship in a calm with three name boards on her port side and a figurehead on the bow",
       ["Bow north. Hull x 3..7, z 2..9: bottom y 1, walls y 2, deck y 3; sea y 1..2.",
        f"Helm {list(HELM)}; name boards {[list(b) for b in BOARDS]} facing west; figurehead {list(FIGUREHEAD)}."])
