"""Generates the Ponder scene "crows_nest" (PND2, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 7x8x7 patch of a ship's deck: spruce planks on y 0 (Ponder's base plate), bow north, an oak mast at x 3, z 3 from
y 1 to y 6 with the crow's nest on its top at [3, 7, 3]. Ratlines lead up to it from the deck aft of the mast: three
sloped links rising north ([3, 1, 6], [3, 2, 5], [3, 3, 4]), then nets hung on the mast's south face ([3, 4, 4] to
[3, 6, 4]) and one hung on the nest's side ([3, 7, 4]), so the climb runs from the deck to the nest's rim. The scene
(almanac/ponder/CrowsNestScene) posts a lookout in the nest."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 7, 8, 7
MAST_X, MAST_Z = 3, 3
NEST = (3, 7, 3)

p = Piece("ponder_crows_nest", "Ponder: The Crow's Nest", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
for y in range(1, 7):
    p.put(MAST_X, y, MAST_Z, "minecraft:oak_log[axis=y]")
p.put(*NEST, f"{MOD}:crows_nest[waterlogged=false]")
for (x, y, z) in ((3, 1, 6), (3, 2, 5), (3, 3, 4)):
    p.put(x, y, z, f"{MOD}:ratlines[facing=north,kind=slope,waterlogged=false]")
for y in range(4, 8):
    p.put(3, y, 4, f"{MOD}:ratlines[facing=south,kind=wall,waterlogged=false]")

p.emit("ponder scene: a deck with a mast, the crow's nest on its top and ratlines up to it",
       [f"Bow (north) at z 0. Mast x {MAST_X}, z {MAST_Z}, y 1..6; nest {list(NEST)}.",
        "Ratlines: sloped [3, 1, 6], [3, 2, 5], [3, 3, 4] rising north; hung on the south face x 3, z 4, y 4..7."])
