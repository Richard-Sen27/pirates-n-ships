"""Generates the Ponder scene "hammocks_and_rest" (PND2, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 7x3x7 patch of a ship's 'tween deck: spruce planks on y 0 (Ponder's base plate). Two hammocks hang east-west between
spruce fence posts at the same height: the posts at x 1 and x 4 on z 2 and z 4 (y 1), each hammock's foot at x 2 and
its head at x 3 (facing east). A sea cot stands at z 6, foot [2, 1, 6], head [3, 1, 6] (facing east). The scene
(almanac/ponder/HammocksAndRestScene) sends the crew to their hammocks."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 7, 3, 7
POST = "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]"
ROWS = (2, 4)

p = Piece("ponder_hammocks_and_rest", "Ponder: Hammocks and Rest", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
for z in ROWS:
    p.put(1, 1, z, POST)
    p.put(4, 1, z, POST)
    p.put(2, 1, z, f"{MOD}:hammock[facing=east,part=foot,waterlogged=false]")
    p.put(3, 1, z, f"{MOD}:hammock[facing=east,part=head,waterlogged=false]")
p.put(2, 1, 6, f"{MOD}:sea_cot[facing=east,occupied=false,part=foot,waterlogged=false]")
p.put(3, 1, 6, f"{MOD}:sea_cot[facing=east,occupied=false,part=head,waterlogged=false]")

p.emit("ponder scene: a deck patch with two hammocks between fence posts and a sea cot",
       ["Hammocks on z 2 and z 4: posts x 1 and x 4, foot x 2, head x 3 (facing east), y 1.",
        "Sea cot foot [2, 1, 6], head [3, 1, 6], facing east."])
