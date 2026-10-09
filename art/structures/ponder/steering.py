"""Generates the Ponder scene "steering at the helm" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

An 11x5x11 patch of sea: a sand seabed on y 0 (Ponder's base plate), sea water on y 1, and a small boat with the bow
north lying on the water (x 4..6, z 4..9: a spruce bottom on y 2, a deck on y 3) with the helm on deck at [5, 4, 8],
facing south, so the helmsman stands at its stern side and looks to the bow. The boat sits on the surface rather than
in it so that sailing it round its turning circle (almanac/ponder/SteeringScene: to starboard about a point 3.5 blocks
east of its middle) leaves no boat-shaped hole in the sea."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 11, 5, 11

p = Piece("ponder_steering", "Ponder: Steering at the Helm", (W, H, D), {})
seabed(p)
hull(p, 4, 4, 6, 9, bottom=2, wall_top=2, deck=3)
sea(p, 1)
p.put(5, 4, 8, f"{MOD}:helm[facing=south]")

p.emit("ponder scene: a small boat on a patch of sea with its helm on deck near the stern",
       ["Bow (north) at z 4, stern at z 9. Boat x 4..6: bottom y 2 (on the sea), deck y 3.",
        "Helm [5, 4, 8] facing south. Sea water y 1."])
