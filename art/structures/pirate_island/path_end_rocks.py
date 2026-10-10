"""Generates a pirate island trail end where the trail peters out among rocks (7 wide, 4 long), a piece of the
``terminators`` pool (STR2), and prints the BuildSpec as JSON.

The bed narrows and fades into the ground, a few beach rocks and a dead bush close it, a driftwood log lies across the
last stretch. ``path_in`` on the north end, nothing else."""
from _camp import band, edging, line, rocks, trail
from _style import PALETTE
from buildspec import Piece

W, L = 7, 4
SIZE = (W, 3, L)

p = Piece("pirate_island_path_end_rocks", "Pirate Trail End (rocks)", SIZE, PALETTE)

bed = band(line(3, 0, 3, 1), SIZE) | {(3, 2), (2, 2)}
trail(p, bed, salt=231)
edging(p, bed, SIZE, salt=232, stakes=0)
rocks(p, [(1, 3), (2, 3), (4, 3), (5, 2), (6, 3)], salt=233)
p.put(3, 0, 3, "coarse")
p.fill(2, 1, 2, 4, 1, 2, "drift_x")
p.put(5, 1, 1, "dead_bush")
p.put(5, 0, 1, "sand")

p.connector(3, 0, 0, "path_in", "north", "minecraft:gravel")

p.emit("the trail petering out among rocks behind a fallen driftwood log",
       ["Terminator: path_in at [3, 0, 0], nothing else."])
