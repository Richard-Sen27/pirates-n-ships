"""Generates the Ponder scene "ships_bell" (PND2, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 5x4x5 patch of a ship's deck: spruce planks on y 0 (Ponder's base plate), a bulkhead of spruce planks along the south
edge (z 4, y 1..3), a ship's bell on its wall bracket on the bulkhead at [3, 2, 3] (facing north, away from the wall)
and a second bell in its frame on top of a spruce post at [1, 2, 1] (the post at [1, 1, 1]; facing north, toward the
placer). The bulkhead stands on the far side from the default Ponder camera (which looks from the north-west, PND3), so
the bells hang in front of it, not behind it. The scene (almanac/ponder/ShipsBellScene) rings them through the bell's
block entity."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 5, 4, 5
WALL_BELL = (3, 2, 3)
POST = (1, 1, 1)
POST_BELL = (1, 2, 1)

p = Piece("ponder_ships_bell", "Ponder: The Ship's Bell", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
p.fill(0, 1, D - 1, W - 1, 3, D - 1, "minecraft:spruce_planks")
p.put(*WALL_BELL, f"{MOD}:ships_bell[face=wall,facing=north,ringing=false,waterlogged=false]")
p.put(*POST, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
p.put(*POST_BELL, f"{MOD}:ships_bell[face=floor,facing=north,ringing=false,waterlogged=false]")

p.emit("ponder scene: a deck patch with a bulkhead, a ship's bell on the wall and one on a post",
       [f"Bulkhead z {D - 1}, y 1..3. Wall bell {list(WALL_BELL)} facing north.",
        f"Post {list(POST)}, bell on it {list(POST_BELL)} facing north."])
