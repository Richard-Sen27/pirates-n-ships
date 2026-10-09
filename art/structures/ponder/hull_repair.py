"""Generates the Ponder scene "patching a breach" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 7x4x7 patch of sea: a sand seabed on y 0 (Ponder's base plate), sea water on y 1..2, and an open hull section in it
(a spruce bottom on y 1, walls on y 2..3 round a 3x3 hold, no deck, so the hold can be seen). The east wall has a
breach below the waterline at [5, 2, 3]: the block is gone and the sea stands in the hole, and the flood has already
filled the hold's lowest row. The scene (almanac/ponder/HullRepairScene) puts the hull patch into the breach."""
from _sea import WATER, Piece, hull, sea, seabed

W, H, D = 7, 4, 7
BREACH = (5, 2, 3)

p = Piece("ponder_hull_repair", "Ponder: Patching a Breach", (W, H, D), {})
seabed(p)
footprint = hull(p, 1, 1, 5, 5, bottom=1, wall_top=3)
sea(p, 2, dry=[footprint])
p.put(*BREACH, WATER)  # the hole, open to the sea
p.fill(2, 2, 2, 4, 2, 4, WATER)  # flood water in the hold

p.emit("ponder scene: an open hull section on a patch of sea with a breach below the waterline and a flooded hold",
       ["Hull x 1..5, z 1..5: bottom y 1, walls y 2..3. Sea water y 1..2 outside.",
        f"Breach {list(BREACH)} (sea water); flood water in the hold on y 2."])
