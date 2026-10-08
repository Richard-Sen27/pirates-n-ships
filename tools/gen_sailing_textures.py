"""Placeholder textures for the sails (work packages D3a, F5a and F5b).

Uses the palette of gen_placeholder_textures.py (copied here: that script no longer imports, its ITEMS table names
removed functions). Usage:

    tools/.venv/bin/python tools/gen_sailing_textures.py

Output in common/src/main/resources/assets/pirates_n_ships/textures/:
- block/sail_cloth.png (32x32): the cloth of square and triangular sails, drawn by the yard and stay renderers (one
  copy per block). Weathered canvas (ART3): plain weave, double-stitched vertical panel seams every 8 px, one reef
  band with reef points per block, stains and rain streaks. Opaque.
- block/sail_cloth_foot.png (32x32): the bottom block of a hanging cloth (ART5), picked by the renderers through
  sailing/client/SailFoot. Rows 0..22 are sail_cloth.png's pixels; below them a stitched foot tabling and a frayed,
  weathered edge with transparent notches (the cloth renderers draw entityCutoutNoCull).
- block/rope.png (16x16): the stay of a triangular sail, drawn by the stay renderer as a thin beam (u around the
  beam, v along it, one copy per block). Twisted hemp: diagonal strands. Opaque.

The yard and the cleat use vanilla block textures. The sail winch, the yard and the cleat have Blockbench models (F7b, F7e),
and so has the rope item (ART1a), so this script draws no item sprite.
Deterministic: same input, same bytes.
"""
import random
import sys
from pathlib import Path

from PIL import Image

sys.dont_write_bytecode = True  # no __pycache__ next to the tools

TEX = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/pirates_n_ships/textures"
P = {  # the colors of gen_placeholder_textures.py that these textures use
    "brown": (110, 70, 40, 255), "tan": (206, 176, 120, 255), "tan_l": (232, 212, 164, 255),
    "white": (236, 232, 220, 255), "bone": (220, 210, 180, 255),
}


def shade(rgb, d):
    return tuple(max(0, min(255, v + d)) for v in rgb[:3]) + (255,)


def cloth():
    """32x32 sailcloth, one tile per block, seamless in both directions (the stay renderer repeats it by world
    position, the yard renderer once per block). ART3:
    - plain weave: alternate texels lighter and darker, plus a thread variation per row and per column;
    - vertical panel seams every 8 px (x = 0, 8, 16, 24): a dark fold line, a light overlap ridge beside it and a
      row of stitches on both sides every 3 px (double-stitched);
    - one reef band per block (rows 12..14): a doubled strip of darker canvas, stitched along both edges, with a reef
      point (a short tan tie, knotted on the band and hanging 4 px) in the middle of every panel;
    - weathering: soft darker stains and faint vertical rain streaks.
    The furled bundle samples rows 0..7 (u 0..1, v 0..0.25): plain seamed canvas, no band.
    Opaque."""
    rnd = random.Random(0x5A11)
    base = (234, 226, 206, 255)
    img = Image.new("RGBA", (32, 32), base)
    row = [rnd.randint(-3, 3) for _ in range(32)]
    col = [rnd.randint(-3, 3) for _ in range(32)]
    for y in range(32):
        for x in range(32):
            d = (3 if (x + y) % 2 == 0 else -3) + row[y] + col[x] + rnd.randint(-2, 1)
            img.putpixel((x, y), shade(base, d))
    for x in (2, 13, 21, 29):  # faint rain streaks
        for y in range(32):
            if rnd.random() < 0.7:
                img.putpixel((x, y), shade(img.getpixel((x, y)), -5))
    for _ in range(4):  # weathering stains
        cx, cy, r = rnd.randrange(32), rnd.randrange(32), rnd.randint(2, 4)
        for y in range(cy - r, cy + r + 1):
            for x in range(cx - r, cx + r + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and rnd.random() < 0.75:
                    px, py = x % 32, y % 32
                    img.putpixel((px, py), shade(img.getpixel((px, py)), -9))
    for x in (0, 8, 16, 24):  # panel seams
        for y in range(32):
            img.putpixel((x, y), shade(img.getpixel((x, y)), -22))
            img.putpixel((x + 1, y), shade(img.getpixel((x + 1, y)), 8))
            if y % 3 == 1:
                img.putpixel(((x - 1) % 32, y), shade(base, -34))
                img.putpixel((x + 2, y), shade(base, -34))
    for y in (12, 13, 14):  # reef band: doubled canvas
        for x in range(32):
            img.putpixel((x, y), shade(img.getpixel((x, y)), -20 if y != 13 else -26))
    for x in range(32):  # stitched along both edges
        if x % 2 == 0:
            img.putpixel((x, 11), shade(base, -36))
            img.putpixel((x, 15), shade(base, -36))
    for x in (4, 12, 20, 28):  # reef points
        img.putpixel((x, 13), shade(P["brown"], 10))
        img.putpixel((x + 1, 13), shade(P["brown"], 10))
        for i, y in enumerate(range(14, 19)):
            tx = x + (1 if i % 2 else 0)
            img.putpixel((tx, y), shade(P["tan"], -10 if i % 2 else 4))
            if y == 18:
                img.putpixel((tx, y), shade(P["tan"], -30))
    return img


def cloth_foot():
    """32x32 foot tile (ART5): sail_cloth.png's tile with its bottom rows turned into the sail's foot. Rows 0..22 stay
    the plain tile's pixels, so a renderer may switch between the two tiles anywhere above the last 9 rows.
    - foot tabling (rows 24..27): a doubled hem of darker canvas, stitched along both edges (rows 23 and 28);
    - frayed edge (rows 28..31): the canvas turns grimy towards the edge, and every column ends a few texels
      short of the edge (0 to 4, less at the seams, where the stitching holds it): transparent notches;
    - loose threads: a few single dark texels hang below a notch.
    Seamless left to right like the plain tile."""
    img = cloth()
    base = (234, 226, 206, 255)
    rnd = random.Random(0xF007)
    for y in range(24, 28):  # tabling: doubled canvas
        for x in range(32):
            img.putpixel((x, y), shade(img.getpixel((x, y)), -18 if y in (24, 27) else -26))
    for x in range(32):  # stitched along both edges of the tabling
        if x % 2 == 1:
            img.putpixel((x, 23), shade(base, -36))
            img.putpixel((x, 28), shade(base, -38))
    grime = (138, 126, 102)
    for y, k in zip(range(28, 32), (0.3, 0.45, 0.6, 0.75)):  # weathered: grimier towards the edge
        for x in range(32):
            r, g, b, _ = shade(img.getpixel((x, y)), rnd.randint(-4, 4))
            img.putpixel((x, y), tuple(round(c + (m - c) * k) for c, m in zip((r, g, b), grime)) + (255,))
    depth = []
    for x in range(32):  # fray depth per column, held by the seam stitching
        seam = x % 8 in (0, 1)
        depth.append(rnd.choice((0, 1)) if seam else rnd.choice((0, 1, 1, 2, 2, 3, 4)))
    for x in range(32):
        for y in range(32 - depth[x], 32):
            img.putpixel((x, y), (0, 0, 0, 0))
        if depth[x] >= 2 and rnd.random() < 0.35:  # a loose thread hangs from the notch
            img.putpixel((x, 32 - depth[x]), shade(base, -46))
    return img


def rope():
    """16x16 twisted hemp, tileable: three strands as diagonal bands of tan with dark grooves between them."""
    rnd = random.Random(0x209E)
    img = Image.new("RGBA", (16, 16), P["tan"])
    for y in range(16):
        for x in range(16):
            k = (x + y) % 16  # strands run diagonally; period 16 keeps the tile seamless
            band = k % 6 if k < 12 else (k - 12) * 6 // 4
            if band == 0:
                c = shade(P["brown"], rnd.randint(-6, 4))  # groove between strands
            elif band in (1, 5):
                c = shade(P["tan"], -22 + rnd.randint(-4, 4))
            else:
                c = shade(P["tan_l"] if band == 3 else P["tan"], rnd.randint(-8, 4))
            img.putpixel((x, y), c)
    return img


def main():
    out = TEX / "block"
    out.mkdir(parents=True, exist_ok=True)
    cloth().save(out / "sail_cloth.png", format="PNG", optimize=False)
    cloth_foot().save(out / "sail_cloth_foot.png", format="PNG", optimize=False)
    rope().save(out / "rope.png", format="PNG", optimize=False)


if __name__ == "__main__":
    main()
