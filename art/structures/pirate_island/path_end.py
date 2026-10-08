"""Generates the end of a pirate island trail (7 wide, 3 long), the piece of the ``terminators`` pool that closes a
``path_out`` once the jigsaw depth runs out, and prints the BuildSpec as JSON.

The trail fades into sand behind a row of palisade stakes. ``path_in`` is on the north end.

ST4b: the stakes are uneven (palm, stripped spruce and dark oak with fence tips), the middle one a palm warning post
with a skull on it, a lantern on the stake beside it, a crate and a dead bush in front, gravel and coarse dirt at the
foot of the row."""
from _style import PALETTE, jit, palisade_post, sand_skirt
from buildspec import Piece

W, L = 7, 3

p = Piece("pirate_island_path_end", "Pirate Trail End", (W, 4, L), PALETTE)

p.fill(0, 0, 0, W - 1, 0, L - 1, "sand")
p.fill(2, 0, 0, 4, 0, 0, "gravel")
p.put(3, 0, 1, "coarse")
p.put(2, 0, 1, "path")
p.put(4, 0, 1, "gravel")

for x in (0, 1, 2, 5, 6):
    palisade_post(p, x, 1, L - 1, height=1 + jit(x, 1, L - 1, 71) % 2)
palisade_post(p, 4, 1, L - 1, height=1)
p.put(4, 3, L - 1, "lantern")
# the warning post: a palm stake with a skull on it
p.fill(3, 1, L - 1, 3, 2, L - 1, "palm")
p.put(3, 3, L - 1, "minecraft:skeleton_skull[powered=false,rotation=8]")
sand_skirt(p, 0, L - 1, W - 1, L - 1, reach=1, salt=72, density=0.6)
p.put(5, 1, 0, "pirates_n_ships:cargo_crate")
p.put(1, 1, 0, "dead_bush")

p.connector(3, 0, 0, "path_in", "north", "minecraft:gravel")

p.emit("the end of a trail: uneven palisade stakes, a skull on a warning post, a lantern",
       ["Terminator: path_in at [3, 0, 0], nothing else."])
