#!/usr/bin/env python3
"""Placeholder textures (16x16 pixel art) for flags (work package E1c). Reuses the canvas, palette and helpers of
gen_placeholder_textures.py (not edited here).

Run (from the repository root, with the venv described in gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_flag_textures.py            # write every texture
    tools/.venv/bin/python tools/gen_flag_textures.py --list     # print the names

Item textures: item/<flag>.png, a flag on a stick.
Block textures: block/flag_<kind>.png, the cloth shown on the flagpole. The flag model is vanilla's glass pane side
template (a 2 px panel from the pole to the north edge of the block): only columns 9..15 of the texture are used,
column 9 next to the pole and column 15 at the tip; the cloth sits in rows 1..8 (top half of the block), the rest
is transparent (cutout). block/flag_edge.png (the panel's thin edges) is fully transparent.
Deterministic, and honors tools/protected_textures.txt like the base script.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import Canvas, TEX, protected  # noqa: E402

# Cloth area on the block texture
X0, X1, Y0, Y1 = 9, 15, 1, 8


def cloth(base, rope="wood_d"):
    cv = Canvas()
    cv.rect(X0, Y0, X1, Y1, base)
    cv.rect(X0, Y0, X0, Y1, rope)  # hoist edge against the pole
    return cv


def flag_merchant():
    cv = cloth("white")
    cv.rect(X0 + 1, 4, X1, 5, "red")
    return cv


def flag_navy():
    cv = cloth("blue")
    cv.rect(X0 + 1, 4, X1, 4, "white")
    cv.rect(11, Y0, 11, Y1, "white")
    return cv


def flag_jolly_roger():
    cv = cloth("black")
    cv.rect(11, 2, 13, 4, "bone")
    cv.px(11, 3, "black")
    cv.px(13, 3, "black")
    cv.px(12, 5, "bone")
    for x, y in ((10, 6), (14, 6), (11, 7), (13, 7), (12, 6)):
        cv.px(x, y, "bone")
    cv.px(10, 8, "bone")
    cv.px(14, 8, "bone")
    return cv


def flag_custom():
    """Stands in for any banner: the block model can't show banner patterns, so this is a generic heraldic cloth."""
    cv = cloth("tan_l")
    cv.rect(X0 + 1, Y0, X1, Y0, "gold")
    cv.rect(X0 + 1, Y1, X1, Y1, "gold")
    cv.rect(11, 3, 13, 6, "red")
    cv.rect(12, 2, 12, 7, "red")
    return cv


def flag_edge():
    return Canvas()


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
          "flag_custom": flag_custom, "flag_edge": flag_edge}


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
