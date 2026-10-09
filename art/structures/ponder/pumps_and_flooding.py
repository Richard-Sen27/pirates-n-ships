"""Generates the Ponder scene "pumps and flooding" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 9x5x9 patch of sea: a sand seabed on y 0 (Ponder's base plate), sea water on y 1..3, and an open hull section in it,
cut away above so the hold can be seen: a spruce bottom on y 1 and walls on y 2..4 round a dry 3x3 hold (x 3..5,
z 3..5). The sea surface is the top of y 3, so the hold's rows y 2 and 3 lie below the waterline. The bilge pump stands
on the hold floor at [3, 2, 3], facing south. The scene (almanac/ponder/PumpsAndFloodingScene) knocks out the east
wall block [6, 2, 4] (a breach below the waterline), floods the hold row by row, pumps and patches."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 9, 5, 9

p = Piece("ponder_pumps_and_flooding", "Ponder: Pumps and Flooding", (W, H, D), {})
seabed(p)
footprint = hull(p, 2, 2, 6, 6, bottom=1, wall_top=4)
sea(p, 3, dry=[footprint])
p.put(3, 2, 3, f"{MOD}:bilge_pump[facing=south,waterlogged=false]")

p.emit("ponder scene: an open hull section on a patch of sea with a bilge pump on the hold floor",
       ["Hull x 2..6, z 2..6: bottom y 1, walls y 2..4, no deck. Sea water y 1..3 outside the hull.",
        "Bilge pump [3, 2, 3] facing south; the breach (made by the scene) [6, 2, 4]."])
