"""Generates the Ponder scene "cargo and weight" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 9x6x11 patch of sea: a sand seabed on y 0 (Ponder's base plate), sea water on y 1..3, and a ship nine blocks long
floating in it with the bow north: a spruce bottom on y 2 (sea under its keel on y 1, so it can settle without
touching the seabed), walls on y 3 round a dry hold and a deck on y 4. Four cargo crates (empty, ``load=0``) wait to
be placed: two aft on deck at [3, 5, 8] and [5, 5, 8], two amidships at [3, 5, 5] and [5, 5, 5]. The scene
(almanac/ponder/CargoAndWeightScene) makes the ship one section, loads the aft crates (``load`` 4), trims her by the
stern and sets her lower, then restows the cargo amidships."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 9, 6, 11
CRATE = f"{MOD}:cargo_crate[load=0]"
AFT = ((3, 5, 8), (5, 5, 8))
MIDSHIPS = ((3, 5, 5), (5, 5, 5))

p = Piece("ponder_cargo_and_weight", "Ponder: Cargo and Weight", (W, H, D), {})
seabed(p)
footprint = hull(p, 2, 1, 6, 9, bottom=2, wall_top=3, deck=4)
sea(p, 1)  # under the keel
sea(p, 3, dry=[footprint])
for pos in AFT + MIDSHIPS:
    p.put(*pos, CRATE)

p.emit("ponder scene: a ship of nine blocks on a patch of sea with cargo crate places aft and amidships",
       ["Bow (north) at z 1. Hull x 2..6, z 1..9: bottom y 2, walls y 3, deck y 4. Sea water y 1..3.",
        f"Crates aft {[list(c) for c in AFT]}, amidships {[list(c) for c in MIDSHIPS]} (placed by the scene)."])
