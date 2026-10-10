"""Generates a pirate's hut on palm stilts (7 wide, a one-block porch, gables on the sides) and prints the BuildSpec
as JSON. A hut variant of the ``huts`` pool (STR2, ``_huts.hut``): steps up from the trail to a roped porch, salvage
stowed between the stilts, a patched thatch with its ridge along x."""
from _huts import hut

hut("hut_stilts", "Pirate Hut on Stilts", stilts=True, porch=1, ridge="x", salt=330,
    notes=": a one-room hut on palm stilts with a roped porch and a patched thatch")
