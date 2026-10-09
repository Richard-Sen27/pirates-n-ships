"""Generates the Ponder scene "assembling a ship" (PND1, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

A 9x5x9 patch of sea: a sand seabed on y 0 (Ponder's base plate), sea water on y 1..2, and a small box hull floating in
it with the bow north: a spruce bottom on y 1 and walls on y 2 around a dry hold (both below the sea surface), a
spruce deck on y 3 and a slab bulwark round its edge on y 4. The helm stands on deck near the stern at [4, 4, 6],
facing south, so the helmsman behind it looks north to the bow. A cargo barrel at [4, 4, 3] is the block the scene
places aboard after assembly (hidden until then). The scene (almanac/ponder/ShipAssemblyScene) shows the hull, places
the helm, makes the hull one moving section and glues the barrel onto it."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 9, 5, 9

p = Piece("ponder_ship_assembly", "Ponder: Assembling a Ship", (W, H, D), {})
seabed(p)
footprint = hull(p, 2, 1, 6, 7, bottom=1, wall_top=2, deck=3)
sea(p, 2, dry=[footprint])
# a slab bulwark round the deck edge
p.ring(2, 1, 6, 7, 4, 4, "minecraft:spruce_slab[type=bottom,waterlogged=false]")
p.put(4, 4, 6, f"{MOD}:helm[facing=south]")
p.put(4, 4, 3, f"{MOD}:cargo_barrel")

p.emit("ponder scene: a small box hull floating on a patch of sea, its helm on deck, a barrel to place aboard later",
       ["Bow (north) at z 1. Hull x 2..6, z 1..7: bottom y 1, walls y 2, deck y 3, slab bulwark y 4.",
        "Sea water y 1..2 outside the hull. Helm [4, 4, 6] facing south; barrel (placed later) [4, 4, 3]."])
