"""Generates a straight pirate island trail (7 wide, 7 long, running north-south) and prints the BuildSpec as JSON.

A sand and gravel trail between palisade stakes (stripped spruce logs with sharpened fence tips), open on both sides
in the middle where the huts attach. Connectors: ``path_in`` on the north end (toward the camp), ``path_out`` on the
south end (the next trail or a terminator), one ``hut_out`` on each side."""
from _style import PALETTE, stake
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

# palisade stakes along both sides, a gap in the middle for the huts; torches on two of them
for x in (0, W - 1):
    for z in (0, 1, 5, 6):
        stake(p, x, 1, z)
p.put(0, 3, 1, "torch")
p.put(W - 1, 3, 5, "torch")
p.put(0, 1, 2, "minecraft:dead_bush")

p.connector(3, 0, 0, "path_in", "north", "minecraft:gravel")
p.connector(3, 0, L - 1, "path_out", "south", "minecraft:gravel")
p.connector(0, 0, 3, "hut_out", "west", "minecraft:coarse_dirt")
p.connector(W - 1, 0, 3, "hut_out", "east", "minecraft:coarse_dirt")

p.emit("sand and gravel trail between palisade stakes with torches",
       ["Runs along z: path_in at the north end, path_out at the south end.",
        "hut_out connectors on both sides at z 3; huts attach with their north (door) side."])
