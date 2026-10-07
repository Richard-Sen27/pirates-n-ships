#!/usr/bin/env python3
"""Ship HUD texture: textures/gui/ship_hud.png, the compass rose, heading needle and hull strip glyphs (work package HUD1).

Run (from the repository root, Python 3 standard library only, no Pillow):

    python3 tools/gen_ship_hud_texture.py              # writes common/src/main/resources/assets/pirates_n_ships/textures/gui/ship_hud.png
    python3 tools/gen_ship_hud_texture.py --out DIR    # writes DIR/gui/ship_hud.png instead (ShipHudTextureTest compares the two)
    python3 tools/gen_ship_hud_texture.py --preview FILE.png   # also writes an 8x enlargement on a sea-grey ground (not committed)

A plain texture outside the GUI sprite atlas, drawn by ship/hull/client/ShipHud with GuiGraphics.blit by UV. The UV
layout is mirrored in ship/hull/client/ShipHudSheet.java; never move a part without changing both.

    ship_hud.png, 64x64:
      (0, 0)   compass rose, 40x40: a dark disc in a brass ring, tick marks, N (red), E, S, W
      (40, 0)  heading needle, 9x19: a hull seen from above, bow up (rotated about its centre)
      (50, 0)  bow cap, 6x14: the pointed bow end left of the hull strip
      (40, 20) pump glyph, 7x7: a pump handle over a rising drop
      (48, 20) breach tick, 3x6: a red crack

Style as the GUI kit (art/README.md, "GUI kit"): pixel art without anti-aliasing, the kit's palette (imported from
gen_gui_textures.py), deterministic bytes (stored deflate blocks).
"""
import argparse
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ next to the tools
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_gui_textures import (BRASS, BRASS_D, BRASS_L, BRASS_OUT, Canvas, INK, PAPER_L, T, WAX, WAX_D,  # noqa: E402
                              WAX_L, WOOD, WOOD_D, WOOD_HI, WOOD_L, in_triangle, rgb)

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures"
SHEET = "gui/ship_hud.png"

DISC = rgb(16, 22, 34, 200)
DISC_RIM = rgb(28, 40, 62, 220)
TICK = rgb(210, 196, 160)
WATER_L = rgb(150, 200, 240)
WATER = rgb(70, 130, 210)
CELL_FRAME = rgb(138, 106, 46)
CELL_IN = rgb(32, 40, 48, 192)
CROSS = rgb(60, 76, 104, 160)

# 5 px tall letters, '#' = ink
LETTERS = {
    "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
    "E": ["###", "#..", "##.", "#..", "###"],
    "S": ["###", "#..", "###", "..#", "###"],
    "W": ["#...#", "#...#", "#.#.#", "#.#.#", ".#.#."],
}


def letter(c, ch, x0, y0, colour):
    for dy, row in enumerate(LETTERS[ch]):
        for dx, px in enumerate(row):
            if px == "#":
                c.set(x0 + dx, y0 + dy, colour)


def rose():
    c = Canvas(40, 40)
    cx = cy = 19.5
    for y in range(40):
        for x in range(40):
            d = math.hypot(x - cx, y - cy)
            if d < 17.5:
                c.set(x, y, DISC)
            elif d < 18.5:
                c.set(x, y, DISC_RIM)
            elif d < 19.5:
                # brass ring, lit from the top left
                c.set(x, y, BRASS_L if (x - cx) + (y - cy) < -8 else BRASS if (x - cx) + (y - cy) < 8 else BRASS_D)
            elif d < 20.2:
                c.set(x, y, BRASS_OUT)
    # tick marks: cardinal 3 px, intercardinal 2 px, eight more 1 px
    for i in range(16):
        ang = math.pi * 2 * i / 16 - math.pi / 2
        length = 3 if i % 4 == 0 else 2 if i % 2 == 0 else 1
        for k in range(length):
            r = 17.0 - k
            c.set(int(math.floor(cx + math.cos(ang) * r)), int(math.floor(cy + math.sin(ang) * r)), TICK)
    letter(c, "N", 18, 6, WAX_L)
    letter(c, "S", 18, 29, PAPER_L)
    letter(c, "E", 28, 17, PAPER_L)
    letter(c, "W", 7, 17, PAPER_L)
    # the faint cross between the letters
    for k in range(-7, 8):
        if abs(k) > 3:
            c.set(19, 19 + k, CROSS)
            c.set(19 + k, 19, CROSS)
    return c


def needle():
    """A hull from above, bow up: 9 wide, 19 tall, outlined in ink."""
    c = Canvas(9, 19)
    # half-widths per row (outline included): pointed bow, full beam, rounded stern
    half = [0, 1, 1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 2]
    for y, h in enumerate(half):
        for x in range(4 - h, 5 + h):
            edge = x in (4 - h, 4 + h) or y in (0, 18)
            c.set(x, y, INK if edge else (WOOD_L if x < 4 else WOOD))
    # deck planking line, the mast and a red pennant at the bow
    for y in range(4, 17):
        if c.get(4, y) != INK:
            c.set(4, y, WOOD_HI if y % 3 else WOOD_D)
    c.set(4, 9, BRASS_L)
    c.set(4, 10, BRASS)
    c.set(4, 1, WAX_L)
    c.set(4, 2, WAX)
    return c


def bow():
    """The bow cap left of the strip: a frame triangle pointing left."""
    c = Canvas(6, 14)
    tip, top, bottom = (0, 6.5), (6, -0.5), (6, 13.5)
    for y in range(14):
        for x in range(6):
            if in_triangle(x + 0.5, y + 0.5, tip, top, bottom):
                c.set(x, y, CELL_IN)
    for y in range(14):
        row = [x for x in range(6) if c.get(x, y)[3]]
        if row:
            c.set(row[0], y, CELL_FRAME)
        if y in (0, 13):
            for x in row:
                c.set(x, y, CELL_FRAME)
    return c


def pump():
    """A pump handle (brass T) over a drop rising out of the water line."""
    rows = [
        ".BBBBB.",
        "...B...",
        "..LWL..",
        ".LWWWL.",
        ".WWWWW.",
        "..WWW..",
        "LLLLLLL",
    ]
    colours = {"B": BRASS_L, "W": WATER, "L": WATER_L}
    c = Canvas(7, 7)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colours:
                c.set(x, y, colours[ch])
    return c


def breach():
    rows = ["DW.", "WLD", ".WD", "DWL", "WD.", "D.."]
    colours = {"W": WAX, "L": WAX_L, "D": WAX_D}
    c = Canvas(3, 6)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colours:
                c.set(x, y, colours[ch])
    return c


PARTS = [  # (u, v, canvas factory); ShipHudSheet.java has the same table
    (0, 0, rose),
    (40, 0, needle),
    (50, 0, bow),
    (40, 20, pump),
    (48, 20, breach),
]


def sheet():
    c = Canvas(64, 64, T)
    for u, v, fn in PARTS:
        part = fn()
        for y in range(part.h):
            for x in range(part.w):
                col = part.get(x, y)
                if col[3]:
                    c.set(u + x, v + y, col)
    return c


def preview(c, path, scale=8):
    out = Canvas(c.w * scale, c.h * scale, rgb(70, 84, 92))
    for y in range(c.h):
        for x in range(c.w):
            col = c.get(x, y)
            if not col[3]:
                continue
            for sy in range(scale):
                for sx in range(scale):
                    out.set(x * scale + sx, y * scale + sy, col)
    Path(path).write_bytes(out.png())


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=TEXTURES, help="textures root to write gui/ship_hud.png under")
    parser.add_argument("--preview", type=Path)
    args = parser.parse_args(argv)
    c = sheet()
    target = args.out / SHEET
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(c.png())
    if args.preview:
        preview(c, args.preview)
    print(f"wrote {target}")


if __name__ == "__main__":
    main(sys.argv[1:])
