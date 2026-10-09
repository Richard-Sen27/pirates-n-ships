"""Generates the Ponder scene "flags_and_allegiance" (PND2, docs/almanac.md "Scenes") and prints the BuildSpec.

A 13x5x13 patch of open sea: the base plate (y 0) is sea water, and two ships float on it with their bows north: ours on
the west (x 1..4, z 3..9) with a bare two-block flagpole on her quarterdeck ([2, 3, 8] the foot, [2, 4, 8] the head),
and a navy patrol on the east (x 8..11, z 3..9) with her own two-block pole ([10, 3, 8], [10, 4, 8]) under the navy
flag. Each is a spruce bottom on y 1 and a deck on y 2. The scene (almanac/ponder/FlagsAndAllegianceScene) hoists,
swaps and strikes our colours through the pole head's real state and shows the patrol later."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 13, 5, 13
PLANK = "minecraft:spruce_planks"
WATER = "minecraft:water[level=0]"
OURS = (1, 3, 4, 9)
THEIRS = (8, 3, 11, 9)
OUR_POLE = (2, 3, 8)
NAVY_POLE = (10, 3, 8)

p = Piece("ponder_flags_and_allegiance", "Ponder: Flags and Allegiance", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, WATER)
for (x0, z0, x1, z1) in (OURS, THEIRS):
    p.fill(x0, 1, z0, x1, 2, z1, PLANK)
x, y, z = OUR_POLE
p.put(x, y, z, f"{MOD}:flagpole[facing=north,flag=none,part=bottom,waterlogged=false]")
p.put(x, y + 1, z, f"{MOD}:flagpole[facing=north,flag=none,part=top,waterlogged=false]")
x, y, z = NAVY_POLE
p.put(x, y, z, f"{MOD}:flagpole[facing=north,flag=none,part=bottom,waterlogged=false]")
p.put(x, y + 1, z, f"{MOD}:flagpole[facing=north,flag=navy,part=top,waterlogged=false]")

p.emit("ponder scene: our ship with a bare two-block flagpole and a navy patrol with hers under the navy flag",
       ["Sea water y 0 (the base plate). Bows north. Ours x 1..4, z 3..9; the patrol x 8..11, z 3..9: y 1..2.",
        f"Our pole {list(OUR_POLE)} and above; the navy pole {list(NAVY_POLE)} and above (navy flag at the head)."])
