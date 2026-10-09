"""Generates the Ponder scene "the anchor and the capstan" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec.

A 9x7x9 patch of sea: a sand seabed on y 0 (Ponder's base plate), sea water on y 1..4, and a ship's waist floating in
it with the bow north: a spruce bottom on y 3, walls on y 4 round a dry hold, a deck on y 5 and the capstan in the
middle of the deck at [3, 6, 4]. The capstan stands on the centre line, so the anchor hangs to starboard (east,
``HullSide``): in the first cell outside the hull, x 6. The scene (almanac/ponder/AnchorAndCapstanScene) sets chain
blocks into the column x 6, z 4 (y 5 down to 3, waterlogged in the sea) link by link as the anchor runs out and puts
the sea back as the capstan heaves it in."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 9, 7, 9
CAPSTAN = (3, 6, 4)
CHAIN_X, CHAIN_Z = 6, 4

p = Piece("ponder_anchor_and_capstan", "Ponder: The Anchor and the Capstan", (W, H, D), {})
seabed(p)
footprint = hull(p, 1, 1, 5, 7, bottom=3, wall_top=4, deck=5)
sea(p, 2)  # under the keel
sea(p, 4, dry=[footprint])  # round the hull; the hold stays dry
p.put(*CAPSTAN, f"{MOD}:capstan[anchor=raised]")

p.emit("ponder scene: a ship's waist on a patch of sea with a capstan on deck and the anchor chain's path to starboard",
       ["Bow (north) at z 1. Hull x 1..5, z 1..7: bottom y 3, walls y 4, deck y 5. Sea water y 1..4.",
        f"Capstan {list(CAPSTAN)}; the chain runs at x {CHAIN_X}, z {CHAIN_Z} (set by the scene)."])
