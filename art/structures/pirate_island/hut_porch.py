"""Generates a pirate's ground hut with a deep porch (7 wide, the porch two deep under a lower course of thatch) and
prints the BuildSpec as JSON. A hut variant of the ``huts`` pool (STR2, ``_huts.hut``): a stool and a barrel on the
porch, rope rails either side of the door, a fieldstone chimney on the west side, gables on the sides."""
from _huts import hut

hut("hut_porch", "Pirate Hut with a Porch", stilts=False, porch=2, ridge="x", salt=340, chimney_at=(0, 5),
    notes=": a ground hut with a deep roped porch, a stool and a barrel, and a patched thatch")
