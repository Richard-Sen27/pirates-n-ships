#!/usr/bin/env python3
"""Whistle order icons: GUI sprites for the orders that have no fitting item (work package ART19).

Run (from the repository root, Python 3 standard library only, no Pillow):

    python3 tools/gen_order_icons.py                       # writes into common/src/main/resources/assets/pirates_n_ships/textures/gui/sprites/order/
    python3 tools/gen_order_icons.py --out DIR             # writes DIR/order/... instead (OrderIconsTest compares the two)
    python3 tools/gen_order_icons.py --preview FILE.png    # also writes an 8x enlargement on a dark and a brass ground (not committed)

Every icon is a 16x16 PNG in vanilla's GUI sprite atlas (textures/gui/sprites/order/<name>.png, id
pirates_n_ships:order/<name>, drawn with GuiGraphics.blitSprite at 16x16) plus a .png.mcmeta with stretch scaling.
station/order/WhistleOrder names the ids (WhistleOrder.Icon.sprite); the whistle wheel and the helm screen's Crew tab
draw them where the other orders draw their item. The order/ folder belongs to this script alone (gen_gui_textures.py
leaves it alone, and this script removes stale files in it).

    order/anchor.png   a stock anchor: ring, shank, wooden stock, two arms with flukes. Dark iron with a light rim, so
                       it reads both on the wheel's dark sectors and on the helm screen's brass buttons.

Style as the GUI kit and the ship HUD (art/README.md, "GUI kit"): pixel art without anti-aliasing, two or three shades
per material, the kit's palette (imported from gen_gui_textures.py), deterministic bytes (stored deflate blocks).
"""
import argparse
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ next to the tools
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_gui_textures import (OUT as SPRITES, PAPER_L, T, WOOD_D, WOOD_HI, Canvas, rgb)  # noqa: E402

FOLDER = "order"

# Dark iron in three shades, and the light rim around the whole shape
IRON_D = rgb(38, 42, 50)
IRON = rgb(64, 70, 82)
IRON_L = rgb(104, 112, 128)
RIM = rgb(PAPER_L[0], PAPER_L[1], PAPER_L[2], 230)

# '.' empty (gets the rim where it touches the shape), 'o' empty and never rim (the ring's eye),
# R/r ring (light/dark), K/k shank (light/dark), S/w stock (top/bottom), A/a arm (top/body), F/f fluke (tip/body)
ANCHOR = [
    "................",
    "......RRRR......",
    ".....RooooR.....",
    ".....roooor.....",
    "......rrrr......",
    "..SSSSSSSSSSSS..",
    "..wwwwwKkwwwww..",
    ".......Kk.......",
    ".......Kk.......",
    ".......Kk.......",
    ".F.....Kk.....F.",
    ".Ff....Kk....fF.",
    "..fA...Kk...Af..",
    "...aAA.Kk.AAa...",
    "....aaaKkaaa....",
    "................",
]
ANCHOR_COLOURS = {
    "R": IRON_L, "r": IRON,
    "K": IRON_L, "k": IRON_D,
    "S": WOOD_HI, "w": WOOD_D,
    "A": IRON, "a": IRON_D,
    "F": IRON_L, "f": IRON,
}


def icon(rows, colours):
    """A 16x16 canvas from a pattern, with a 1 px light rim (8-neighbourhood) around every filled pixel."""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "an order icon is 16x16"
    c = Canvas(16, 16, T)
    for y, row in enumerate(rows):
        for x, k in enumerate(row):
            if k in colours:
                c.set(x, y, colours[k])
    for y, row in enumerate(rows):
        for x, k in enumerate(row):
            if k != ".":
                continue
            touches = any(rows[ny][nx] in colours
                          for ny in range(max(0, y - 1), min(16, y + 2))
                          for nx in range(max(0, x - 1), min(16, x + 2)))
            if touches:
                c.set(x, y, RIM)
    return c


def anchor():
    return icon(ANCHOR, ANCHOR_COLOURS)


ICONS = {"anchor": anchor}


def mcmeta():
    return json.dumps({"gui": {"scaling": {"type": "stretch"}}}, indent=2) + "\n"


def write_all(out_dir):
    folder = out_dir / FOLDER
    folder.mkdir(parents=True, exist_ok=True)
    written = set()
    for name, fn in sorted(ICONS.items()):
        png = folder / (name + ".png")
        png.write_bytes(fn().png())
        meta = folder / (name + ".png.mcmeta")
        meta.write_bytes(mcmeta().encode("utf-8"))
        written |= {png, meta}
    for f in sorted(folder.rglob("*")):  # this folder belongs to this script alone
        if f.is_file() and f not in written:
            f.unlink()
    return written


def preview(path, scale=8):
    grounds = [rgb(24, 28, 32), rgb(198, 150, 58)]  # the wheel's sector, a brass button
    names = sorted(ICONS)
    out = Canvas(len(names) * 18 * scale, len(grounds) * 18 * scale, rgb(70, 84, 92))
    for gi, ground in enumerate(grounds):
        for ni, name in enumerate(names):
            c = ICONS[name]()
            ox, oy = ni * 18 * scale, gi * 18 * scale
            for y in range(18 * scale):
                for x in range(18 * scale):
                    out.set(ox + x, oy + y, ground)
            for y in range(16):
                for x in range(16):
                    col = c.get(x, y)
                    if not col[3]:
                        continue
                    a = col[3] / 255
                    mixed = tuple(int(col[i] * a + ground[i] * (1 - a)) for i in range(3)) + (255,)
                    for sy in range(scale):
                        for sx in range(scale):
                            out.set(ox + (x + 1) * scale + sx, oy + (y + 1) * scale + sy, mixed)
    Path(path).write_bytes(out.png())


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=SPRITES, help="sprite root to write order/<name>.png under")
    parser.add_argument("--preview", type=Path)
    args = parser.parse_args(argv)
    files = write_all(args.out)
    if args.preview:
        preview(args.preview)
    print(f"wrote {len(files)} files under {args.out / FOLDER}")


if __name__ == "__main__":
    main(sys.argv[1:])
