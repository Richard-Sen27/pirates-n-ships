"""Placeholder textures for the sails and the sail winch (work packages D3a and F5a).

Reuses the canvas and palette of gen_placeholder_textures.py (not edited). Usage:

    tools/.venv/bin/python tools/gen_sailing_textures.py

Output in common/src/main/resources/assets/pirates_n_ships/textures/block/:
- fore_and_aft_sail_<trim>.png: the one-block fore-and-aft sail (until F5b). Opaque (the plate models use the default
  solid render type): a dark yard along the top, then canvas for the set part of the sail and dark rigging background
  for the rest.
- sail_cloth.png (32x32): the cloth of square sails, drawn between two yards by the yard renderer (one copy per block).
  Weathered off-white canvas: vertical panel seams, a faint weave, a few stains. Opaque.
- sail_winch.png.

Square sails have no block textures of their own any more: the yard uses vanilla stripped spruce log textures.
Deterministic: same input, same bytes.
"""
import random
import sys
from pathlib import Path

from PIL import Image

sys.dont_write_bytecode = True  # no __pycache__ next to the tools
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import P, Canvas, TEX  # noqa: E402

SAILS = {
    "fore_and_aft_sail": ("white", "grey"),
}


def sail(name, trim):
    light, dark = SAILS[name]
    c = Canvas("wood_d")
    c.rect(0, 0, 15, 1, "wood")  # yard
    if trim == "furled":
        c.rect(1, 2, 14, 3, dark)
        c.rect(1, 2, 14, 2, light)
    else:
        bottom = 8 if trim == "half" else 15
        for y in range(2, bottom + 1):  # triangle-ish: wider toward the foot
            w = min(15, 4 + (y - 2) * 12 // 13)
            c.rect(1, y, w, y, light)
    return c


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


def winch():
    c = Canvas("plank")
    c.border("wood_d")
    c.disc(7, 7, 4, "iron")
    c.disc(7, 7, 2, "iron_d")
    c.rect(2, 7, 13, 8, "tan")  # rope
    return c


def main():
    out = TEX / "block"
    out.mkdir(parents=True, exist_ok=True)
    for name in SAILS:
        for trim in ("furled", "half", "full"):
            sail(name, trim).img.save(out / f"{name}_{trim}.png", format="PNG", optimize=False)
    cloth().save(out / "sail_cloth.png", format="PNG", optimize=False)
    winch().img.save(out / "sail_winch.png", format="PNG", optimize=False)


if __name__ == "__main__":
    main()
