"""Generates a pirate's thatched ground hut (7 wide, the ridge running front to back, a fieldstone chimney at the back
corner) and prints the BuildSpec as JSON. A hut variant of the ``huts`` pool (STR2, ``_huts.hut``): boarded gables
front and back with a king post, no porch, a lantern post by the door, crates against the side."""
from _camp import crate_stack
from _huts import hut


def side_stores(p, floor, z0, z1):
    """Crates and a rope coil against the west wall, outside."""
    crate_stack(p, 0, 1, z1 + 1, salt=351)


hut("hut_thatch", "Pirate Thatched Hut", stilts=False, porch=0, ridge="z", salt=350, chimney_at=(6, 3),
    extras=side_stores, notes=": a ground hut under a steep front-to-back thatch with a fieldstone chimney")
