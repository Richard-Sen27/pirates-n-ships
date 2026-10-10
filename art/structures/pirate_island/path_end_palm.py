"""Generates a pirate island trail end under a palm (7 wide, 5 long), a piece of the ``terminators`` pool (STR2),
and prints the BuildSpec as JSON.

The trail ends at a leaning palm with a ring of rocks at its foot, a rope coil and a broken crate under it and a
lantern hung from a driftwood stake. ``path_in`` on the north end, nothing else."""
from _camp import band, edging, line, palm, rocks, rope_coil, trail
from _style import PALETTE, lantern_post
from buildspec import Piece

W, L = 7, 5
SIZE = (W, 8, L)

p = Piece("pirate_island_path_end_palm", "Pirate Trail End (palm)", SIZE, PALETTE)

bed = band(line(3, 0, 3, 2), SIZE)
trail(p, bed, salt=241)
edging(p, bed, SIZE, salt=242, stakes=0)
p.put(3, 0, 4, "sand")
palm(p, 3, 1, 4, height=5, lean="north")
rocks(p, [(2, 4), (4, 4), (4, 3)], salt=243)
rope_coil(p, 2, 1, 3, layers=1, facing="east")
p.put(5, 0, 3, "coarse")
p.put(5, 1, 3, "pirates_n_ships:cargo_crate")
lantern_post(p, 1, 1, 3, height=1, post="drift")

p.connector(3, 0, 0, "path_in", "north", "minecraft:gravel")

p.emit("the trail ending under a leaning palm with rocks, a rope coil and a crate",
       ["Terminator: path_in at [3, 0, 0], nothing else."])
