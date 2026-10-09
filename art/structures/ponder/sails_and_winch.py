"""Generates the Ponder scene "sails and the sail winch" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec.

A 7x8x7 patch of a ship's deck, bow north: spruce planks on y 0 (Ponder's base plate) with an oak log mast at x 3, z 3
and a square sail rigged on it: an upper yard (five yard blocks along x) on y 6 and a lower yard on y 2. By the yard
rule (PND3, docs/design.md §3.4) both yards stand one block forward of the mast, in the column in front of it (z 2),
never in the mast's own column; their middle blocks [3, y, 2] are in one column with only air between them, so the
cloth drops four blocks (the F5a rule: 2 to 8 blocks, only air or mast blocks between the middles). The mast continues
above the upper yard to y 7. The sail winch stands on deck at [1, 1, 4], aft and to port of the mast, where the
default Ponder camera (from the north-west) sees it past the sail, its crank facing south. The scene
(almanac/ponder/SailsAndWinchScene) gives the upper yard's head [3, 6, 2] its cloth through the real yard rule and
steps the trim furled, half, full."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 7, 8, 7
MAST = 3
YARD_Z = MAST - 1  # the yard rule: one block forward (north, toward the bow) of the mast
LOWER, UPPER = 2, 6
WINCH = (1, 1, 4)

p = Piece("ponder_sails_and_winch", "Ponder: Sails and the Sail Winch", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
for y in range(1, H):
    p.put(MAST, y, MAST, "minecraft:oak_log[axis=y]")
for y in (LOWER, UPPER):
    for x in range(1, 6):
        p.put(x, y, YARD_Z, f"{MOD}:yard[axis=x,trim=furled,waterlogged=false]")
p.put(*WINCH, f"{MOD}:sail_winch[facing=south]")

p.emit("ponder scene: a deck patch with a mast, a square sail between two yards forward of it and a sail winch",
       [f"Bow north. Mast oak logs at x {MAST}, z {MAST}, y 1..7; yards along x (x 1..5) on y {LOWER} and y {UPPER} "
        f"at z {YARD_Z}, one block forward of the mast.",
        f"Upper yard head [{MAST}, {UPPER}, {YARD_Z}]; sail winch {list(WINCH)} facing south."])
