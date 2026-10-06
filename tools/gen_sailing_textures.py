"""Placeholder textures for the sails (work packages D3a, F5a and F5b).

Uses the palette of gen_placeholder_textures.py (copied here: that script no longer imports, its ITEMS table names
removed functions). Usage:

    tools/.venv/bin/python tools/gen_sailing_textures.py

Output in common/src/main/resources/assets/pirates_n_ships/textures/:
- block/sail_cloth.png (32x32): the cloth of square and triangular sails, drawn by the yard and stay renderers (one
  copy per block). Weathered off-white canvas: vertical panel seams, a faint weave, a few stains. Opaque.
- block/rope.png (16x16): the stay of a triangular sail, drawn by the stay renderer as a thin beam (u around the
  beam, v along it, one copy per block). Twisted hemp: diagonal strands. Opaque.
- item/rope.png: the rope item, a coil.

The yard and the cleat use vanilla block textures; the sail winch has a Blockbench model (F7b).
Deterministic: same input, same bytes.
"""
import random
import sys
from pathlib import Path

from PIL import Image

sys.dont_write_bytecode = True  # no __pycache__ next to the tools

TEX = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/pirates_n_ships/textures"
P = {  # the colors of gen_placeholder_textures.py that these textures use
    "outline": (34, 24, 20, 255),
    "brown": (110, 70, 40, 255), "tan": (206, 176, 120, 255), "tan_l": (232, 212, 164, 255),
    "white": (236, 232, 220, 255), "bone": (220, 210, 180, 255),
}
CLEAR = (0, 0, 0, 0)


class Canvas:
    """A 16x16 drawing surface (the subset of gen_placeholder_textures.Canvas used here)."""

    def __init__(self):
        self.img = Image.new("RGBA", (16, 16), CLEAR)

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.img.putpixel((x, y), P[c])

    def line(self, x0, y0, x1, y1, c):
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(steps + 1):
            self.px(x0 + round((x1 - x0) * i / steps), y0 + round((y1 - y0) * i / steps), c)

    def disc(self, cx, cy, r, c):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + r:
                    self.px(x, y, c)

    def outline(self):
        """Dark outline around every opaque pixel."""
        src = self.img.copy()
        for y in range(16):
            for x in range(16):
                if src.getpixel((x, y))[3] == 0 and any(
                        0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3] > 0
                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    self.img.putpixel((x, y), P["outline"])


def shade(rgb, d):
    return tuple(max(0, min(255, v + d)) for v in rgb[:3]) + (255,)


def cloth():
    """32x32 weathered canvas. Seams every 8 px (one cloth panel per quarter block), tileable in both directions."""
    rnd = random.Random(0x5A11)
    base = P["white"]
    img = Image.new("RGBA", (32, 32), base)
    for y in range(32):
        for x in range(32):
            d = rnd.randint(-6, 4)
            if (x + y) % 2 == 0:  # faint weave
                d -= 3
            img.putpixel((x, y), shade(base, d))
    for x in (0, 8, 16, 24):  # panel seams, double-stitched
        for y in range(32):
            img.putpixel((x, y), shade(P["bone"], rnd.randint(-8, 0)))
            if y % 4 == 1:
                img.putpixel(((x + 1) % 32, y), shade(P["bone"], -14))
    for _ in range(5):  # weathering stains
        cx, cy, r = rnd.randrange(32), rnd.randrange(32), rnd.randint(2, 4)
        for y in range(cy - r, cy + r + 1):
            for x in range(cx - r, cx + r + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and rnd.random() < 0.7:
                    px, py = x % 32, y % 32
                    img.putpixel((px, py), shade(img.getpixel((px, py)), -10))
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


def rope_item():
    """A coil of rope with a loose end."""
    c = Canvas()
    for r, col in ((6, "brown"), (5, "tan"), (4, "brown"), (3, "tan_l"), (2, "brown")):
        c.disc(7, 8, r, col)
    c.disc(7, 8, 1, "tan")
    c.line(12, 10, 14, 14, "tan")
    c.line(13, 10, 15, 14, "brown")
    c.outline()
    return c


def main():
    out = TEX / "block"
    out.mkdir(parents=True, exist_ok=True)
    cloth().save(out / "sail_cloth.png", format="PNG", optimize=False)
    rope().save(out / "rope.png", format="PNG", optimize=False)
    items = TEX / "item"
    items.mkdir(parents=True, exist_ok=True)
    rope_item().img.save(items / "rope.png", format="PNG", optimize=False)


if __name__ == "__main__":
    main()
