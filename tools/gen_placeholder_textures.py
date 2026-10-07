#!/usr/bin/env python3
"""Placeholder textures (16x16 pixel art) for the basic items and blocks (work package C8).

Run (from the repository root):
    python3 -m venv tools/.venv
    tools/.venv/bin/pip install -r tools/requirements.txt
    tools/.venv/bin/python tools/gen_placeholder_textures.py            # write every texture
    tools/.venv/bin/python tools/gen_placeholder_textures.py --only doubloon rum      # just these
    tools/.venv/bin/python tools/gen_placeholder_textures.py --list     # print the names

Output: common/src/main/resources/assets/pirates_n_ships/textures/{item,block}/<name>.png
Deterministic: same input, same bytes. One function per texture, registered in ITEMS / BLOCKS.
Replacing a texture by hand: put its name into PROTECTED (or a line in tools/protected_textures.txt),
then this script never overwrites it. Textures owned by other packages (test_block) are never written. The helm, nameplate and flagpole have
hand-made Blockbench models with vanilla textures (art/models/) and no textures here; the rapier, cutlass, saber,
pistol, musket, lead shot, cannonball and grappling hook have hand-made item models textured from the item palettes
(tools/gen_item_palette.py).
"""
import argparse
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures"
PROTECTED_FILE = Path(__file__).resolve().parent / "protected_textures.txt"
# Names of hand-made textures this script must not overwrite (e.g. "cargo_crate").
PROTECTED = set()
FOREIGN_PREFIXES = ("test_block",)

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

def doubloon():
    cv = Canvas()
    cv.disc(7.5, 7.5, 5.6, "gold_d")
    cv.disc(7.5, 7.5, 4.4, "gold")
    cv.rect(7, 5, 8, 10, "gold_l")
    cv.rect(5, 7, 10, 8, "gold_l")
    return cv.outline()


def tobacco():
    cv = Canvas()
    for i in range(9):
        cv.line(3 + i // 2, 13 - i, 8 + i // 3, 4 - i // 3 + i // 2, "amber" if i % 2 else "brown")
    cv.line(4, 12, 11, 3, "amber_l")
    cv.rect(5, 9, 9, 10, "tan")
    return cv.outline()


def spices():
    cv = Canvas()
    cv.rect(4, 7, 11, 13, "tan")
    cv.rect(5, 5, 10, 6, "tan_l")
    cv.rect(4, 7, 11, 7, "brown")
    cv.rect(6, 3, 9, 4, "red")
    cv.px(7, 2, "red_d"); cv.px(8, 4, "amber_l")
    noise(cv, "spices", ["amber", "red"], 0.25, (5, 9, 10, 12))
    return cv.outline()


def cloth():
    cv = Canvas()
    cv.rect(2, 4, 13, 11, "white")
    for y in (5, 8, 11):
        cv.rect(2, y, 13, y, "bone")
    cv.rect(11, 4, 13, 11, "blue_l")
    cv.rect(12, 4, 12, 11, "blue")
    return cv.outline()


def rum():
    cv = Canvas()
    cv.rect(5, 7, 10, 13, "amber")
    cv.rect(6, 8, 7, 12, "amber_l")
    cv.rect(7, 3, 8, 6, "amber")
    cv.rect(7, 2, 8, 2, "wood_l")
    cv.rect(5, 9, 10, 10, "tan_l")
    return cv.outline()


def hardtack():
    cv = Canvas()
    cv.rect(3, 4, 12, 11, "tan")
    cv.rect(3, 4, 12, 4, "tan_l")
    for x, y in ((5, 6), (8, 6), (11, 6), (5, 9), (8, 9), (11, 9)):
        cv.px(x - 1, y, "brown")
    return cv.outline()


def salted_fish():
    cv = Canvas()
    cv.rect(3, 6, 10, 9, "grey")
    cv.rect(4, 6, 9, 6, "steel_l")
    cv.line(11, 7, 13, 5, "grey"); cv.line(11, 8, 13, 10, "grey")
    cv.px(4, 7, "black")
    noise(cv, "salted_fish", ["white"], 0.3, (5, 7, 9, 9))
    return cv.outline()


def salt_pork():
    cv = Canvas()
    cv.rect(3, 4, 12, 11, "pink")
    cv.rect(3, 4, 12, 5, "white")
    cv.rect(3, 8, 12, 8, "white")
    noise(cv, "salt_pork", ["white", "red"], 0.12, (3, 9, 12, 11))
    return cv.outline()


def lime():
    cv = Canvas()
    cv.disc(7.5, 8.5, 4.8, "leaf")
    cv.disc(6.5, 7.5, 2.5, "lime_l")
    cv.px(8, 3, "wood"); cv.px(9, 3, "leaf_d"); cv.px(10, 2, "leaf_d")
    return cv.outline()


def shackles():
    cv = Canvas()
    for cx in (4, 11):
        cv.disc(cx, 10, 3, "iron")
        cv.disc(cx, 10, 1.5, CLEAR_KEY)
    for x in range(6, 10):
        cv.px(x, 5 + (x % 2), "steel")
    cv.px(5, 6, "steel"); cv.px(10, 6, "steel")
    return cv.outline()


CLEAR_KEY = "_clear"
P[CLEAR_KEY] = CLEAR


def brig_door_item():
    cv = Canvas()
    cv.rect(4, 1, 11, 14, "wood")
    cv.rect(4, 3, 11, 3, "iron"); cv.rect(4, 12, 11, 12, "iron")
    cv.rect(6, 5, 9, 8, "black")
    cv.rect(7, 5, 7, 8, "iron"); cv.rect(8, 5, 8, 8, "iron_d")
    cv.px(10, 9, "steel")
    return cv.outline()


# ---------------------------------------------------------------- blocks

def brig_bars():
    cv = Canvas()
    for x in (1, 5, 10, 14):
        cv.rect(x, 0, x + 1, 15, "iron")
        cv.rect(x, 0, x, 15, "steel")
    cv.rect(0, 1, 15, 2, "wood"); cv.rect(0, 13, 15, 14, "wood")
    cv.rect(0, 1, 15, 1, "wood_l"); cv.rect(0, 14, 15, 14, "wood_d")
    return cv


def brig_bars_edge():
    cv = Canvas("iron")
    cv.rect(7, 0, 8, 15, "steel")
    return cv


def brig_door(top):
    cv = planks("brig_door_" + ("top" if top else "bottom"), "wood", "wood_d", "wood_l")
    for x in (0, 15):
        cv.rect(x, 0, x, 15, "iron_d")
    if top:
        cv.rect(0, 2, 15, 3, "iron")
        cv.rect(3, 6, 12, 13, "black")
        for x in (4, 7, 10):
            cv.rect(x, 6, x + 1, 13, "iron")
            cv.rect(x, 6, x, 13, "steel")
        cv.rect(0, 0, 15, 0, "iron_d")
    else:
        cv.rect(0, 12, 15, 13, "iron")
        cv.rect(12, 1, 13, 3, "steel"); cv.px(12, 2, "black")
        cv.rect(0, 15, 15, 15, "iron_d")
    return cv


def cargo_crate():
    cv = planks("cargo_crate")
    cv.line(1, 1, 14, 14, "wood"); cv.line(1, 2, 13, 14, "wood_d")
    cv.rect(1, 1, 14, 1, "wood"); cv.rect(1, 14, 14, 14, "wood")
    cv.rect(1, 1, 1, 14, "wood"); cv.rect(14, 1, 14, 14, "wood")
    return cv.border()


def barrel_side(name, band="iron"):
    cv = Canvas("plank")
    for x in range(0, 16, 3):
        cv.rect(x, 0, x, 15, "plank_d")
    noise(cv, name, ["plank_l"], 0.1)
    for y in (2, 13):
        cv.rect(0, y, 15, y + 1, band)
        cv.rect(0, y, 15, y, "steel")
    return cv.border()


def barrel_top(name, inner):
    cv = Canvas("plank")
    noise(cv, name, ["plank_d", "plank_l"], 0.15)
    cv.border("iron").rect(1, 1, 14, 1, "iron")
    cv.rect(2, 2, 13, 13, inner) if inner else None
    for y in (5, 10):
        cv.rect(1, y, 14, y, "plank_d")
    return cv


def cargo_barrel_side(): return barrel_side("cargo_barrel_side")
def cargo_barrel_top(): return barrel_top("cargo_barrel_top", None)


def water_barrel_side():
    cv = barrel_side("water_barrel_side", "blue_d")
    cv.rect(6, 6, 9, 9, "blue"); cv.rect(7, 7, 8, 8, "blue_l")
    return cv


def water_barrel_top():
    cv = barrel_top("water_barrel_top", None)
    cv.disc(7.5, 7.5, 3.5, "blue")
    cv.disc(6.5, 6.5, 1.5, "blue_l")
    return cv


def pantry_side():
    cv = planks("pantry_side", "wood", "wood_d", "wood_l")
    for y0 in (1, 8):
        cv.rect(2, y0, 13, y0 + 5, "wood_d")
        cv.rect(3, y0 + 3, 5, y0 + 5, "tan")        # bread
        cv.rect(7, y0 + 2, 8, y0 + 5, "red")        # apple / jar
        cv.rect(10, y0 + 3, 12, y0 + 5, "amber")
        cv.rect(2, y0 + 5, 13, y0 + 5, "wood_l")
    return cv.border()


def pantry_top():
    return planks("pantry_top", "wood", "wood_d", "wood_l").border()


ITEMS = {
    "doubloon": doubloon, "tobacco": tobacco, "spices": spices, "cloth": cloth, "rum": rum,
    "hardtack": hardtack, "salted_fish": salted_fish, "salt_pork": salt_pork, "lime": lime,
    "shackles": shackles, "brig_door": brig_door_item,
}
BLOCKS = {
    "brig_bars": brig_bars, "brig_bars_edge": brig_bars_edge,
    "brig_door_top": lambda: brig_door(True), "brig_door_bottom": lambda: brig_door(False),
    "cargo_crate": cargo_crate, "cargo_barrel_side": cargo_barrel_side, "cargo_barrel_top": cargo_barrel_top,
    "pantry_side": pantry_side, "pantry_top": pantry_top,
    "water_barrel_side": water_barrel_side, "water_barrel_top": water_barrel_top,
}


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
        if name in skip or name.startswith(FOREIGN_PREFIXES):
            print(f"skip   {kind}/{name} (protected)")
            continue
        out = TEX / kind / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        fn().img.save(out, format="PNG", optimize=False)
        print(f"wrote  {kind}/{name}")


if __name__ == "__main__":
    main()
