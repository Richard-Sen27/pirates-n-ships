#!/usr/bin/env python3
"""Placeholder textures for flags (work packages E1c, F6). Reuses the palette and helpers of
gen_placeholder_textures.py (not edited here).

Run (from the repository root, with the venv described in gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_flag_textures.py            # write every texture
    tools/.venv/bin/python tools/gen_flag_textures.py --list     # print the names

Item textures (16x16): item/<flag>.png, a flag on a stick.
Block textures (32x16): block/flag_<kind>.png, the cloth on the flagpole (FlagClothModel). The cloth is 24 model
pixels long and 16 high, mapped 1:1 (square pixels):
    columns  0..23  the cloth, column 0 at the pole's center, column 23 at the tip. Columns 0..1 sit inside the
                    pole and are never seen; column 2 is the dark hoist edge against the pole; 3..23 the field.
    columns 24..31  an edge swatch in the cloth's base color, sampled by the thin top and bottom edges.
The front face shows the texture as drawn (hoist on the left), the back face mirrors it.
Deterministic, and honors tools/protected_textures.txt like the base script.
"""
import argparse
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import CLEAR, P, TEX, Canvas, protected  # noqa: E402

W, H = 32, 16
HOIST = 2          # dark hoist column
F0, F1 = 3, 23     # field columns
SWATCH = 24        # first edge swatch column


class Cloth(Canvas):
    """A 32x16 canvas with the cloth layout above."""

    def __init__(self, base, hoist="wood_d"):
        self.img = Image.new("RGBA", (W, H), CLEAR)
        self.rect(0, 0, F1, H - 1, base)
        self.rect(0, 0, HOIST, H - 1, hoist)
        self.rect(SWATCH, 0, W - 1, H - 1, base)

    def px(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.img.putpixel((x, y), P[c])

    def field(self, x0, y0, x1, y1, c):
        """A rectangle clipped to the field (never paints the hoist or the swatch)."""
        self.rect(max(x0, F0), y0, min(x1, F1), y1, c)


def flag_merchant():
    cv = Cloth("white")
    cv.field(F0, 6, F1, 9, "red")
    return cv


def flag_navy():
    cv = Cloth("blue")
    cv.field(F0, 7, F1, 8, "white")    # horizontal arm
    cv.field(9, 0, 10, 15, "white")    # vertical arm, toward the hoist (Nordic cross)
    return cv


def flag_jolly_roger():
    cv = Cloth("black")
    # skull, centered on column 13
    cv.field(10, 2, 16, 6, "bone")
    cv.field(11, 1, 15, 1, "bone")
    cv.field(11, 7, 15, 8, "bone")
    cv.field(11, 4, 12, 5, "black")    # eyes
    cv.field(14, 4, 15, 5, "black")
    cv.px(13, 6, "black")              # nose
    cv.px(12, 8, "black")              # teeth gaps
    cv.px(14, 8, "black")
    # crossbones
    cv.line(9, 10, 17, 14, "bone")
    cv.line(17, 10, 9, 14, "bone")
    for x, y in ((8, 10), (9, 9), (18, 10), (17, 9), (8, 14), (9, 15), (18, 14), (17, 15)):
        cv.px(x, y, "bone")
    return cv


def flag_custom():
    """Stands in for any banner: the block model can't show banner patterns, so this is a generic heraldic cloth."""
    cv = Cloth("tan_l")
    cv.field(F0, 0, F1, 0, "gold")
    cv.field(F0, 15, F1, 15, "gold")
    cv.field(F1, 0, F1, 15, "gold")
    cv.field(11, 4, 15, 11, "red")     # a red lozenge-ish charge
    cv.field(12, 3, 14, 12, "red")
    cv.field(13, 2, 13, 13, "red")
    cv.field(13, 7, 13, 8, "gold")
    return cv


def item_flag(base, design):
    cv = Canvas()
    cv.rect(3, 1, 3, 15, "wood")
    cv.px(3, 1, "wood_l")
    cv.rect(4, 2, 13, 9, base)
    design(cv)
    return cv.outline()


def merchant_flag():
    return item_flag("white", lambda cv: cv.rect(4, 5, 13, 6, "red"))


def navy_flag():
    def d(cv):
        cv.rect(4, 5, 13, 5, "white")
        cv.rect(7, 2, 7, 9, "white")
    return item_flag("blue", d)


def jolly_roger_flag():
    def d(cv):
        cv.rect(7, 3, 10, 5, "bone")
        cv.px(8, 4, "black")
        cv.px(10, 4, "black")
        cv.rect(8, 6, 9, 6, "bone")
        for x, y in ((6, 7), (11, 7), (7, 8), (10, 8), (6, 9), (11, 9)):
            cv.px(x, y, "bone")
    return item_flag("black", d)


ITEMS = {"merchant_flag": merchant_flag, "navy_flag": navy_flag, "jolly_roger_flag": jolly_roger_flag}
BLOCKS = {"flag_merchant": flag_merchant, "flag_navy": flag_navy, "flag_jolly_roger": flag_jolly_roger,
          "flag_custom": flag_custom}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", action="store_true", help="print kind/name of every texture and exit")
    args = ap.parse_args()
    jobs = [("item", n, f) for n, f in ITEMS.items()] + [("block", n, f) for n, f in BLOCKS.items()]
    if args.list:
        for kind, name, _ in jobs:
            print(f"{kind}/{name}")
        return
    skip = protected()
    for kind, name, fn in jobs:
        if name in skip:
            print(f"skip   {kind}/{name} (protected)")
            continue
        out = TEX / kind / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        fn().img.save(out, format="PNG", optimize=False)
        print(f"wrote  {kind}/{name}")


if __name__ == "__main__":
    main()
