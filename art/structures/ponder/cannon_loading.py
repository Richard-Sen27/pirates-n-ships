"""Generates the Ponder scene "loading and firing the cannon" (ALM4, docs/almanac.md "Scenes") and prints the BuildSpec
as JSON.

A 7x3x7 patch of a ship's deck: spruce deck planks on y 0 (Ponder's base plate), a bulwark along the north edge (z 0:
a stripped spruce rail on y 1, a slab cap on y 2) cut for a gun port at x 3 (a top slab as its lintel), the cannon
behind it with the muzzle north through the port (master at [3, 1, 1], rear at [3, 1, 2], nothing loaded), a powder
barrel to the gun's left at [1, 1, 2] and the shot chest to its right at [5, 1, 2]. The scene
(almanac/ponder/CannonLoadingScene) steps the master's ``load`` state through the real CannonLoad values.

Unlike the jigsaw pieces this NBT is not a world structure: tools/schem_to_structure.py writes the ``ponder`` group to
``assets/pirates_n_ships/ponder/<piece>.nbt``, where Ponder loads the scene's schematic from."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402

W, H, D = 7, 3, 7
GUN_X = 3

PALETTE = {
    "deck": "minecraft:spruce_planks",
    "rail": "minecraft:stripped_spruce_log[axis=x]",
    "cap": "minecraft:spruce_slab[type=bottom,waterlogged=false]",
    "lintel": "minecraft:spruce_slab[type=top,waterlogged=false]",
    "powder": f"{MOD}:cargo_barrel",
    "shot": "minecraft:chest[facing=south,type=single,waterlogged=false]",
}

p = Piece("ponder_cannon_loading", "Ponder: Loading and Firing the Cannon", (W, H, D), PALETTE)

# the deck (Ponder shows y 0 as the base plate)
p.fill(0, 0, 0, W - 1, 0, D - 1, "deck")

# the bulwark along the bow side, cut for the gun port
for x in range(W):
    if x == GUN_X:
        p.put(x, 2, 0, "lintel")
    else:
        p.put(x, 1, 0, "rail")
        p.put(x, 2, 0, "cap")

# the cannon, muzzle north through the port: master (front) at z 1, rear behind it at z 2
p.put(GUN_X, 1, 1, f"{MOD}:cannon[facing=north,load=empty,part=front,shot=ball,waterlogged=false]")
p.put(GUN_X, 1, 2, f"{MOD}:cannon[facing=north,load=empty,part=rear,shot=ball,waterlogged=false]")

# the gun's stores beside it
p.put(GUN_X - 2, 1, 2, "powder")
p.put(GUN_X + 2, 1, 2, "shot")

p.emit("ponder scene: a deck patch with a bulwark, a cannon at its gun port, a powder barrel and a shot chest",
       [f"Bow (north) at z 0. Cannon master [{GUN_X}, 1, 1] facing north, rear [{GUN_X}, 1, 2].",
        f"Powder barrel [{GUN_X - 2}, 1, 2], shot chest [{GUN_X + 2}, 1, 2]."])
