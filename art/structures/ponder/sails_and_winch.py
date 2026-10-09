"""Generates the Ponder scene "sails and the sail winch" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec.

A 7x8x7 patch of a ship's deck: spruce planks on y 0 (Ponder's base plate) with an oak log mast at x 3, z 3 and a
square sail rigged on it: an upper yard (five yard blocks along x) on y 6 and a lower yard on y 2, their middle blocks
in the mast column, so the cloth drops four blocks (the F5a rule: 2 to 8 blocks, only mast blocks between the
middles). The mast continues above the upper yard to y 7. The sail winch stands on deck at [5, 1, 5], its crank
facing south. The scene (almanac/ponder/SailsAndWinchScene) gives the upper yard's head [3, 6, 3] its cloth through
the real yard rule and steps the trim furled, half, full."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 7, 8, 7
MAST = 3
LOWER, UPPER = 2, 6

p = Piece("ponder_sails_and_winch", "Ponder: Sails and the Sail Winch", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
for y in range(1, H):
    p.put(MAST, y, MAST, "minecraft:oak_log[axis=y]")
for y in (LOWER, UPPER):
    for x in range(1, 6):
        p.put(x, y, MAST, f"{MOD}:yard[axis=x,trim=furled,waterlogged=false]")
p.put(5, 1, 5, f"{MOD}:sail_winch[facing=south]")

p.emit("ponder scene: a deck patch with a mast, a square sail between two yards and a sail winch",
       [f"Mast oak logs at x {MAST}, z {MAST}, y 1..7; yards along x (x 1..5) on y {LOWER} and y {UPPER}.",
        f"Upper yard head [{MAST}, {UPPER}, {MAST}]; sail winch [5, 1, 5] facing south."])
