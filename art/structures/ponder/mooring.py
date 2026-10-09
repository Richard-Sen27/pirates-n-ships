"""Generates the Ponder scene "mooring" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 9x5x9 harbour corner: a stone-brick quay on the west (x 0..2, up to y 3) with a mooring ring on its top at
[2, 4, 4], a sand seabed under the water east of it (y 0 is Ponder's base plate) and sea water on y 1..2. A small ship
lies off the quay against the scene's east edge (x 6..8, bow north): a spruce bottom on y 1 and walls on y 2 round a
dry hold, a deck on y 3 and its own mooring ring on deck at [6, 4, 4]. The scene (almanac/ponder/MooringScene) brings
the ship one block in alongside the quay, then throws the grappling hook onto the quay and ties the line off at the
ship's ring; the ship starts at the east edge so the cells it leaves are the scene's border, not a hole in the sea."""
from _sea import MOD, Piece, hull, sea

W, H, D = 9, 5, 9
QUAY = "minecraft:stone_bricks"
QUAY_RING = (2, 4, 4)
SHIP_RING = (6, 4, 4)

p = Piece("ponder_mooring", "Ponder: Mooring", (W, H, D), {})
p.fill(0, 0, 0, 2, 3, D - 1, QUAY)
p.fill(3, 0, 0, W - 1, 0, D - 1, "minecraft:sand")
footprint = hull(p, 6, 1, 8, 7, bottom=1, wall_top=2, deck=3)
sea(p, 2, dry=[footprint])
p.put(*QUAY_RING, f"{MOD}:mooring_ring[face=floor,facing=east,waterlogged=false]")
p.put(*SHIP_RING, f"{MOD}:mooring_ring[face=floor,facing=west,waterlogged=false]")

p.emit("ponder scene: a quay with a mooring ring and a small ship lying off it with its own ring on deck",
       [f"Quay stone bricks x 0..2, y 0..3; quay ring {list(QUAY_RING)}.",
        f"Ship x 6..8, z 1..7: bottom y 1, walls y 2, deck y 3; ship ring {list(SHIP_RING)}. Sea water y 1..2."])
