"""Helpers shared by the Ponder scene generators that need the sea (PND1, docs/almanac.md "Scenes"): a seabed base plate
and sea water around whatever the scene built. Not a piece (the leading underscore keeps build_structures.py off it)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from buildspec import MOD, Piece  # noqa: E402,F401  (re-exported for the scene generators)

WATER = "minecraft:water[level=0]"


def seabed(p: Piece, key: str = "minecraft:sand"):
    """The whole y 0 layer (Ponder's base plate) as seabed."""
    sx, _, sz = p.size
    p.fill(0, 0, 0, sx - 1, 0, sz - 1, key)


def sea(p: Piece, top: int, dry=(), x0: int = 0, z0: int = 0, x1=None, z1=None):
    """Sea water in every empty cell of rows 1..top (the sea surface is the top of row ``top``) inside the given box,
    except inside the ``dry`` boxes ((x0, z0, x1, z1) column ranges: a hull's hold stays air)."""
    sx, _, sz = p.size
    x1 = sx - 1 if x1 is None else x1
    z1 = sz - 1 if z1 is None else z1
    for y in range(1, top + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                if any(a <= x <= c and b <= z <= d for (a, b, c, d) in dry):
                    continue
                if p.get(x, y, z) is None:
                    p.put(x, y, z, WATER)


def hull(p: Piece, x0: int, z0: int, x1: int, z1: int, bottom: int, wall_top: int, deck=None,
         plank: str = "minecraft:spruce_planks"):
    """A box hull: a bottom row, walls up to ``wall_top`` around a hollow hold, and optionally a full deck row. Returns
    the hull's footprint (x0, z0, x1, z1) for ``sea(dry=...)``."""
    p.fill(x0, bottom, z0, x1, bottom, z1, plank)
    if wall_top > bottom:
        p.ring(x0, z0, x1, z1, bottom + 1, wall_top, plank)
    if deck is not None:
        p.fill(x0, deck, z0, x1, deck, z1, plank)
    return (x0, z0, x1, z1)
