"""Generates the Ponder scene "boarding" (PND2, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 13x5x13 patch of open sea: the base plate (y 0, which Ponder fades in first) is sea water, and two ships float on
it with their bows north: ours on the west (x 1..4, z 3..9) and a pirate ship at the east edge (x 9..12, z 3..9). Each
is a spruce bottom on y 1, a deck on y 2 and a gunwale of planks round the deck's edge on y 3; the pirate ship flies
the Jolly Roger from a flagpole at [11, 3, 8]. The scene (almanac/ponder/BoardingScene) hauls the pirate ship three
blocks west (an element transform), so her gunwale stands at x 6 and the gap at x 5 is one block wide; she starts at
the edge, and the sea is only the base plate, so the water she leaves shows no hole. The boarding plank is then laid
from the top of our gunwale at [4, 3, 6]: two cells on y 4, [5, 4, 6] and [6, 4, 6], the tip resting on her gunwale
(rail top to rail top)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 13, 5, 13
PLANK = "minecraft:spruce_planks"
WATER = "minecraft:water[level=0]"
OURS = (1, 3, 4, 9)
THEIRS = (9, 3, 12, 9)
POLE = (11, 3, 8)

p = Piece("ponder_boarding", "Ponder: Boarding", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, WATER)
for (x0, z0, x1, z1) in (OURS, THEIRS):
    p.fill(x0, 1, z0, x1, 2, z1, PLANK)
    p.ring(x0, z0, x1, z1, 3, 3, PLANK)
p.put(*POLE, f"{MOD}:flagpole[facing=north,flag=jolly_roger,part=single,waterlogged=false]")

p.emit("ponder scene: two ships with gunwales lying apart on open sea, the eastern one under the Jolly Roger",
       ["Sea water y 0 (the base plate). Bows north. Ours x 1..4, z 3..9; theirs x 9..12, z 3..9: y 1..2, gunwale y 3.",
        f"Jolly Roger pole {list(POLE)}. The plank (set by the scene) runs east on y 4 from x 5."])
