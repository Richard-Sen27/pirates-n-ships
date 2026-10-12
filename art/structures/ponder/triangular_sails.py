"""Generates the Ponder scene "triangular sails" (SAIL5, docs/almanac.md "Scenes") and prints the BuildSpec.

A 9x9x9 patch of a ship's deck, bow north: spruce planks on y 0 (Ponder's base plate) with an oak log mast at x 4, z 6
(y 1..8). By the yard rule (PND3, docs/design.md §3.4) the rig stands one block forward of the mast, in the column in
front of it (z 5): the head cleat high on the mast's north face at [4, 7, 5] and the clew cleat low on it at [4, 2, 5],
both on the wall facing north; the tack cleat on the deck forward at [4, 1, 1]. The stay runs from the head to the
tack (the scene gives the cleats their rope ends): drop 5, the tack 4 forward, so the cloth is 10 blocks of sail (the
F5b rule). The scene (almanac/ponder/TriangularSailsScene) later replaces the clew cleat by a boom facing north (the
SAIL5 boom), whose arm reaches the 4 blocks forward to the tack and swings with its sheet."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 9, 9, 9
MAST_X, MAST_Z = 4, 6
RIG_Z = MAST_Z - 1  # the yard rule: one block forward (north, toward the bow) of the mast
HEAD = (MAST_X, 7, RIG_Z)
CLEW = (MAST_X, 2, RIG_Z)
TACK = (MAST_X, 1, 1)

p = Piece("ponder_triangular_sails", "Ponder: Triangular Sails", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
for y in range(1, H):
    p.put(MAST_X, y, MAST_Z, "minecraft:oak_log[axis=y]")
for pos in (HEAD, CLEW):
    p.put(*pos, f"{MOD}:cleat[face=wall,facing=north,trim=furled,waterlogged=false]")
p.put(*TACK, f"{MOD}:cleat[face=floor,facing=north,trim=furled,waterlogged=false]")

p.emit("ponder scene: a deck patch with a mast and the three cleats of a triangular sail forward of it",
       [f"Bow north. Mast oak logs at x {MAST_X}, z {MAST_Z}, y 1..{H - 1}; head cleat {list(HEAD)} and clew cleat "
        f"{list(CLEW)} on the mast's north face, one block forward of it.",
        f"Tack cleat {list(TACK)} on the deck, 4 blocks forward of the clew; drop 5, area 10."])
