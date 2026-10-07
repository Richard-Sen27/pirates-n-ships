"""Generates the end of a pirate island trail (7 wide, 3 long), the piece of the ``terminators`` pool that closes a
``path_out`` once the jigsaw depth runs out, and prints the BuildSpec as JSON.

The trail fades into sand behind a row of palisade stakes with a torch post in the middle. ``path_in`` is on the
north end."""
from _style import PALETTE, stake, torch_post
from buildspec import Piece

W, L = 7, 3

p = Piece("pirate_island_path_end", "Pirate Trail End", (W, 4, L), PALETTE)

p.fill(0, 0, 0, W - 1, 0, L - 1, "sand")
p.fill(2, 0, 0, 4, 0, 0, "gravel")
p.put(3, 0, 1, "coarse")
p.put(2, 0, 1, "path")

for x in (0, 1, 5, 6):
    stake(p, x, 1, L - 1)
p.put(2, 1, L - 1, "post")
p.put(4, 1, L - 1, "post")
torch_post(p, 3, 1, L - 1)

p.connector(3, 0, 0, "path_in", "north", "minecraft:gravel")

p.emit("the end of a trail: palisade stakes and a torch post",
       ["Terminator: path_in at [3, 0, 0], nothing else."])
