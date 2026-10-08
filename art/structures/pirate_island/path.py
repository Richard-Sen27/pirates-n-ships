"""Generates a straight pirate island trail (7 wide, 7 long, running north-south) and prints the BuildSpec as JSON.

A sand and gravel trail between crude palisade stakes, open on both sides in the middle where the huts attach.
Connectors: ``path_in`` on the north end (toward the camp), ``path_out`` on the south end (the next trail or a
terminator), one ``hut_out`` on each side.

ST4b: the stakes are palm trunks, stripped spruce and dark oak of uneven height with sharpened fence tips (one
leaning on a stair), lashed into pairs, a lantern and a torch on two of them; gravel and coarse dirt gather at their
feet, a barrel and a dead bush stand on the verges."""
from _style import PALETTE, jit, palisade_post, sand_skirt, stair
from buildspec import Piece

W = L = 7

p = Piece("pirate_island_path", "Pirate Trail", (W, 4, L), PALETTE)

# the trail: gravel and dirt down the middle, sand and coarse dirt to the sides
p.fill(0, 0, 0, W - 1, 0, L - 1, "sand")
for z in range(L):
    for x in (2, 3, 4):
        p.put(x, 0, z, ("gravel", "path", "gravel", "coarse")[(x * 3 + z) % 4] if x != 3 or z % 3 else "gravel")
for x, z in ((1, 2), (5, 4), (0, 3), (6, 3)):
    p.put(x, 0, z, "coarse")

# palisade stakes along both sides in two pairs, a gap in the middle for the huts; uneven heights
for x in (0, W - 1):
    for z in (0, 1, 5, 6):
        palisade_post(p, x, 1, z, height=1 if (x, z) in ((0, 1), (W - 1, 5)) else 1 + jit(x, 1, z, 61) % 2)
sand_skirt(p, 0, 0, 0, 1, reach=1, salt=62, density=0.45)
sand_skirt(p, 0, 5, 0, 6, reach=1, salt=63, density=0.45)
sand_skirt(p, W - 1, 0, W - 1, 1, reach=1, salt=64, density=0.45)
sand_skirt(p, W - 1, 5, W - 1, 6, reach=1, salt=65, density=0.45)
# a stake that has started to lean, propped on an upside-down stair on the verge
p.put(1, 1, 6, stair("west", "top"))
# lights: a lantern on the west pair, a torch on the east pair
for x, z, light in ((0, 1, "lantern"), (W - 1, 5, "torch")):
    top = max(y for (xx, y, zz) in p.blocks if (xx, zz) == (x, z))
    if top + 1 < 4:
        p.put(x, top + 1, z, light)
p.put(1, 1, 0, "barrel")
p.put(0, 1, 2, "dead_bush")
p.put(5, 1, 2, "dead_bush")

p.connector(3, 0, 0, "path_in", "north", "minecraft:gravel")
p.connector(3, 0, L - 1, "path_out", "south", "minecraft:gravel")
p.connector(0, 0, 3, "hut_out", "west", "minecraft:coarse_dirt")
p.connector(W - 1, 0, 3, "hut_out", "east", "minecraft:coarse_dirt")

p.emit("sand and gravel trail between uneven palisade stakes with a lantern and a torch",
       ["Runs along z: path_in at the north end, path_out at the south end.",
        "hut_out connectors on both sides at z 3; huts attach with their north (door) side."])
