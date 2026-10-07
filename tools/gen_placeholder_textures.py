#!/usr/bin/env python3
"""Placeholder textures (16x16 pixel art) for the basic items and blocks (work package C8).

Run (from the repository root):
    python3 -m venv tools/.venv
    tools/.venv/bin/pip install -r tools/requirements.txt
    tools/.venv/bin/python tools/gen_placeholder_textures.py            # write every texture
    tools/.venv/bin/python tools/gen_placeholder_textures.py --only <name>      # just these
    tools/.venv/bin/python tools/gen_placeholder_textures.py --list     # print the names

Output: common/src/main/resources/assets/pirates_n_ships/textures/{item,block}/<name>.png
Deterministic: same input, same bytes. One function per texture, registered in ITEMS / BLOCKS.
Replacing a texture by hand: put its name into PROTECTED (or a line in tools/protected_textures.txt),
then this script never overwrites it. The helm, nameplate,
flagpole, cargo crate, cargo barrel, pantry, water barrel, brig bars and brig door (block and item) have hand-made
Blockbench models with vanilla textures (art/models/) and no textures here; the rapier, cutlass, saber,
pistol, musket, lead shot, cannonball, grappling hook, shackles, rum, hardtack, lime, salt pork, salted fish, cloth,
spices, tobacco, the doubloon and the brig key have hand-made item models textured from the item palettes
(tools/gen_item_palette.py).
"""
import argparse
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures"
PROTECTED_FILE = Path(__file__).resolve().parent / "protected_textures.txt"
# Names of hand-made textures this script must not overwrite.
PROTECTED = set()

# Palette: every texture uses only these colors.
P = {
    "outline": (34, 24, 20, 255),
    "steel_d": (92, 98, 110, 255), "steel": (150, 156, 168, 255), "steel_l": (210, 214, 222, 255),
    "iron_d": (58, 58, 64, 255), "iron": (96, 96, 104, 255),
    "gold_d": (156, 104, 22, 255), "gold": (222, 170, 48, 255), "gold_l": (252, 226, 120, 255),
    "wood_d": (84, 54, 30, 255), "wood": (124, 84, 48, 255), "wood_l": (164, 118, 70, 255),
    "plank_d": (120, 88, 52, 255), "plank": (156, 120, 74, 255), "plank_l": (184, 148, 96, 255),
    "leaf_d": (64, 96, 34, 255), "leaf": (104, 140, 52, 255), "lime_l": (170, 210, 80, 255),
    "brown": (110, 70, 40, 255), "tan": (206, 176, 120, 255), "tan_l": (232, 212, 164, 255),
    "red_d": (120, 30, 30, 255), "red": (176, 52, 44, 255), "pink": (216, 132, 120, 255),
    "white": (236, 232, 220, 255), "bone": (220, 210, 180, 255), "grey": (128, 124, 120, 255),
    "blue_d": (36, 60, 110, 255), "blue": (60, 100, 170, 255), "blue_l": (110, 160, 220, 255),
    "teal": (50, 140, 130, 255), "amber": (176, 96, 32, 255), "amber_l": (220, 140, 60, 255),
    "black": (24, 22, 26, 255),
}
CLEAR = (0, 0, 0, 0)


class Canvas:
    def __init__(self, fill=None):
        self.img = Image.new("RGBA", (16, 16), P[fill] if fill else CLEAR)

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.img.putpixel((x, y), P[c])

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.px(x0, y0, c)
            if x0 == x1 and y0 == y1:
                return
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def disc(self, cx, cy, r, c):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.px(x, y, c)

    def outline(self):
        """Dark outline around every opaque pixel (items: transparent background)."""
        src = self.img.copy()
        for y in range(16):
            for x in range(16):
                if src.getpixel((x, y))[3] == 0 and any(
                        0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3] > 0
                        and src.getpixel((x + dx, y + dy)) != P["outline"]
                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    self.img.putpixel((x, y), P["outline"])
        return self

    def border(self, c="outline"):
        """Dark frame for block faces."""
        for i in range(16):
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                self.px(x, y, c)
        return self


def noise(cv, name, colors, chance=0.25, area=(0, 0, 15, 15)):
    rnd = random.Random("pns:" + name)
    for y in range(area[1], area[3] + 1):
        for x in range(area[0], area[2] + 1):
            if rnd.random() < chance:
                cv.px(x, y, rnd.choice(colors))


def planks(name, base="plank", dark="plank_d", light="plank_l"):
    cv = Canvas(base)
    noise(cv, name, [dark, light], 0.18)
    for y in (4, 8, 12):
        cv.rect(0, y, 15, y, dark)
    return cv


# ---------------------------------------------------------------- items

# ---------------------------------------------------------------- blocks

# Every item sprite became a hand-made item model; the brig door's went in F8g.
ITEMS = {}
BLOCKS = {}


def protected():
    names = set(PROTECTED)
    if PROTECTED_FILE.exists():
        names |= {l.strip() for l in PROTECTED_FILE.read_text().splitlines() if l.strip() and not l.startswith("#")}
    return names


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", nargs="*", help="only these texture names")
    ap.add_argument("--list", action="store_true", help="print kind/name of every texture and exit")
    args = ap.parse_args()
    jobs = [("item", n, f) for n, f in ITEMS.items()] + [("block", n, f) for n, f in BLOCKS.items()]
    if args.list:
        for kind, name, _ in jobs:
            print(f"{kind}/{name}")
        return
    skip = protected()
    for kind, name, fn in jobs:
        if args.only and name not in args.only:
            continue
        if name in skip:
            print(f"skip   {kind}/{name} (protected)")
            continue
        out = TEX / kind / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        fn().img.save(out, format="PNG", optimize=False)
        print(f"wrote  {kind}/{name}")


if __name__ == "__main__":
    main()
