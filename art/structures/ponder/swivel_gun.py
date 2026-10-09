"""Generates the Ponder scene "swivel_gun" (PND2, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 7x3x7 patch of a ship's deck: spruce planks on y 0 (Ponder's base plate), a rail of spruce fences along the bow side
(z 0, y 1) and a swivel gun mounted on its middle post at [3, 2, 0], empty, its yoke the placed block. A cargo barrel
for the powder stands at [1, 1, 2] and a chest for the shot at [5, 1, 2]. The scene (almanac/ponder/SwivelGunScene)
loads the gun through its real ``load`` states and turns it through its block entity's aim."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 7, 3, 7
GUN = (3, 2, 0)


def fence(x: int) -> str:
    east = "true" if x < W - 1 else "false"
    west = "true" if x > 0 else "false"
    return f"minecraft:spruce_fence[east={east},north=false,south=false,waterlogged=false,west={west}]"


p = Piece("ponder_swivel_gun", "Ponder: The Swivel Gun", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
for x in range(W):
    p.put(x, 1, 0, fence(x))
p.put(*GUN, f"{MOD}:swivel_gun[load=empty,piece=yoke,waterlogged=false]")
p.put(1, 1, 2, f"{MOD}:cargo_barrel")
p.put(5, 1, 2, "minecraft:chest[facing=south,type=single,waterlogged=false]")

p.emit("ponder scene: a deck patch with a fence rail, a swivel gun on its middle post, powder and shot beside it",
       [f"Bow (north) at z 0. Fence rail z 0, y 1; swivel gun {list(GUN)}.",
        "Powder barrel [1, 1, 2], shot chest [5, 1, 2]."])
