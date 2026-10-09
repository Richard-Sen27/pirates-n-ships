"""Generates the Ponder scene "galley_and_provisions" (PND2, docs/almanac.md "Scenes") and prints the BuildSpec.

A 7x3x7 patch of a ship's deck: spruce planks on y 0 (Ponder's base plate), a bulkhead of spruce planks along the west
edge (x 0, y 1..2), the pantry against it at [1, 1, 3] with its doors facing east, and an empty water barrel at
[1, 1, 5]. The cells round the pantry stay free for the crew's meal seats (crew/galley MealRules: the four sides first).
The scene (almanac/ponder/GalleyAndProvisionsScene) fills the barrel through its real fill levels and seats the crew."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 7, 3, 7
PANTRY = (1, 1, 3)
BARREL = (1, 1, 5)

p = Piece("ponder_galley_and_provisions", "Ponder: The Galley and Provisions", (W, H, D), {})
p.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:spruce_planks")
p.fill(0, 1, 0, 0, 2, D - 1, "minecraft:spruce_planks")
p.put(*PANTRY, f"{MOD}:pantry[facing=east,load=0]")
p.put(*BARREL, f"{MOD}:water_barrel[fill=0,load=0]")

p.emit("ponder scene: a deck patch with a bulkhead, the pantry and an empty water barrel",
       [f"Bulkhead x 0, y 1..2. Pantry {list(PANTRY)} facing east, water barrel {list(BARREL)} empty."])
