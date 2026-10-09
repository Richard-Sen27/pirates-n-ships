#!/usr/bin/env python3
"""The Captain's Almanac screen's textures (work package ART14; the ALM3 placeholders' geometry is kept).

Run (from the repository root, Python 3 standard library only, no Pillow):

    python3 tools/gen_almanac_textures.py            # writes into common/src/main/resources/assets/pirates_n_ships/textures/gui/almanac/
    python3 tools/gen_almanac_textures.py --out DIR  # writes the same files under DIR (AlmanacTexturesTest compares the two)

Plain textures outside the GUI sprite atlas (almanac/client/AlmanacScreen blits them itself):

- book.png (292x180): the open tome at 1:1. A leather frame (the boards seen around the pages) with a blind-tooled
  line, darker head and tail bands where the spine runs, and brass corner plates with a rivet, the same leather and
  brass as the placed tome (tools/gen_almanac_book_texture.py); two parchment pages with a warm mottled cloud, a fine
  grain, a few faint foxing spots, faint ruled lines on the text lines, a shade along their outer edges and a darker
  gutter towards the spine with a crease; the page stack on the outer sides and under both pages. The geometry
  matches AlmanacScreen's constants: left page x 9..146, right page x 146..283, both y 5..172; the stacks x 6..9 and
  283..286, y 172..175; text boxes x 19..135 and 157..273 from y 15, LINE_H 10 (the ruled lines lie one pixel under
  each text line: y 24, 34, ... 154).
- bookmark.png (24x16): one silk ribbon tab in light greys with stitched edges and a swallowtail end, tinted per
  chapter with its bookmark colour (white parts take the colour fully, the greys shade it). Its left 4 px lie under the
  cover.

Deterministic: the noise is a fixed integer hash and the PNG writer uses stored (uncompressed) deflate blocks, so the
bytes never depend on the zlib build.
"""
import argparse
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no tools/__pycache__ from the import below
from gen_almanac_book_texture import (BRASS, BRASS_D, BRASS_L, BRASS_OUT, LEATHER, LEATHER_D, LEATHER_HI, LEATHER_L,
                                      LEATHER_OUT, PAPER, PAPER_D, PAPER_EDGE, PAPER_L, TOOL, T, Canvas, mix, noise,
                                      scale, smooth)

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures/gui/almanac"

BOOK_W, BOOK_H = 292, 180
SPINE_X = 146
PAGE_TOP, PAGE_BOTTOM = 5, 172
LEFT_PAGE_X, RIGHT_PAGE_END = 9, 283
EDGE = 3
CONTENT_TOP, LINE_H, CONTENT_H = 15, 10, 140
LEFT_CONTENT_X, RIGHT_CONTENT_X, CONTENT_W = 19, 157, 116

INK_RULE = (150, 112, 70, 255)
RULE_STRENGTH = 0.16
FOX = (170, 120, 70, 255)
GUTTER = 18


def cover(c):
    """The boards around the pages: grained leather, an outline, a tooled line, head and tail bands, brass corners."""
    for y in range(BOOK_H):
        for x in range(BOOK_W):
            m = smooth(x, y, 9, 71)
            n = noise(x, y, 7)
            col = mix(LEATHER_D, LEATHER_L, 0.35 + 0.5 * m)
            if n < 30:
                col = scale(col, 0.86)
            elif n > 236:
                col = scale(col, 1.12)
            c.set(x, y, col)
    # the spine's head and tail: a darker band with a raised highlight above the gutter
    for y in list(range(0, PAGE_TOP)) + list(range(PAGE_BOTTOM + EDGE, BOOK_H)):
        for x in range(SPINE_X - 8, SPINE_X + 8):
            d = abs(x + 0.5 - SPINE_X) / 8.0
            c.set(x, y, mix(LEATHER_HI if d < 0.25 else LEATHER, LEATHER_D, d))
    # a blind-tooled line two pixels in, with a light edge under it
    for x in range(2, BOOK_W - 2):
        c.set(x, 2, TOOL)
        c.set(x, 3, scale(c.get(x, 3), 1.15))
        c.set(x, BOOK_H - 3, TOOL)
    for y in range(2, BOOK_H - 2):
        c.set(2, y, TOOL)
        c.set(3, y, scale(c.get(3, y), 1.15))
        c.set(BOOK_W - 3, y, TOOL)
    # the outline and a highlight along the top
    for x in range(BOOK_W):
        c.set(x, 0, LEATHER_OUT)
        c.set(x, BOOK_H - 1, LEATHER_OUT)
        if 0 < x < BOOK_W - 1:
            c.set(x, 1, scale(c.get(x, 1), 1.2))
    for y in range(BOOK_H):
        c.set(0, y, LEATHER_OUT)
        c.set(BOOK_W - 1, y, LEATHER_OUT)
    for cx, cy, dx, dy in ((0, 0, 1, 1), (BOOK_W - 1, 0, -1, 1), (0, BOOK_H - 1, 1, -1), (BOOK_W - 1, BOOK_H - 1, -1, -1)):
        corner(c, cx, cy, dx, dy)


def corner(c, cx, cy, dx, dy):
    """A brass corner plate: a triangle with 18 px legs, a dark outline, lit along its outer edges, a rivet."""
    legs = 18
    for i in range(legs):
        for j in range(legs - i):
            if i == 0 or j == 0 or i + j == legs - 1:
                col = BRASS_OUT
            elif i == 1 or j == 1:
                col = BRASS_L
            elif i + j >= legs - 3:
                col = BRASS_D
            else:
                col = BRASS if noise(i, j, 81) > 40 else scale(BRASS, 0.94)
            c.set(cx + dx * i, cy + dy * j, col)
    c.set(cx, cy, T)
    # the rivet
    for i, j, col in ((4, 4, BRASS_L), (5, 4, BRASS), (4, 5, BRASS), (5, 5, BRASS_D), (6, 5, BRASS_OUT), (5, 6, BRASS_OUT)):
        c.set(cx + dx * i, cy + dy * j, col)


def page_colour(x, y, seed):
    """Warm mottled parchment: a broad cloud, a finer cloud and a grain."""
    m = 0.62 * smooth(x, y, 26, seed) + 0.38 * smooth(x + 11, y + 7, 9, seed + 1)
    t = max(0.0, min(1.0, 0.66 + (m - 0.5) * 0.95))
    col = mix(PAPER_D, PAPER, t / 0.6) if t < 0.6 else mix(PAPER, PAPER_L, (t - 0.6) / 0.4)
    n = noise(x, y, seed + 2)
    if n < 18:
        col = scale(col, 0.965)
    elif n > 244:
        col = scale(col, 1.025)
    return col


def page(c, x0, x1, left):
    """One parchment page: the mottled ground, ruled lines, foxing, outer shade, gutter shade and crease."""
    seed = 3 if left else 5
    content_x = LEFT_CONTENT_X if left else RIGHT_CONTENT_X
    rules = {CONTENT_TOP + LINE_H * k + LINE_H - 1 for k in range(CONTENT_H // LINE_H)}
    spots = []
    for k in range(5):
        sx = x0 + 6 + noise(k, 1, seed + 20) * (x1 - x0 - 12) // 255
        sy = PAGE_TOP + 6 + noise(k, 2, seed + 20) * (PAGE_BOTTOM - PAGE_TOP - 12) // 255
        spots.append((sx, sy, 1.2 + noise(k, 3, seed + 20) / 255.0 * 2.2))
    for y in range(PAGE_TOP, PAGE_BOTTOM):
        for x in range(x0, x1):
            col = page_colour(x, y, seed)
            if y in rules and content_x - 2 <= x < content_x + CONTENT_W + 2:
                col = mix(col, INK_RULE, RULE_STRENGTH)
            for sx, sy, r in spots:
                d = ((x - sx) ** 2 + (y - sy) ** 2) ** 0.5
                if d < r:
                    col = mix(col, FOX, 0.10 * (1 - d / r) + 0.04)
            to_spine = (x1 - 1 - x) if left else (x - x0)
            outer = (x1 - x0 - 1) - to_spine
            f = 1.0
            if to_spine < GUTTER:
                g = (GUTTER - to_spine) / GUTTER
                f -= 0.20 * g * g
            if outer < 4:
                f -= (4 - outer) * 0.022
            edge_y = min(y - PAGE_TOP, PAGE_BOTTOM - 1 - y)
            if edge_y < 3:
                f -= (3 - edge_y) * 0.02
            c.set(x, y, scale(col, f))
    # the crease right at the spine
    for y in range(PAGE_TOP, PAGE_BOTTOM):
        x = x1 - 1 if left else x0
        c.set(x, y, scale(c.get(x, y), 0.86))


def edges(c):
    """The page stack: fine lines of page edges on the outer sides and under both pages."""
    for y in range(PAGE_TOP + 1, PAGE_BOTTOM + EDGE):
        for i in range(EDGE):
            n = noise(i, y, 91)
            col = (PAPER_EDGE, PAPER_D, PAPER)[i] if n > 40 else PAPER_D
            if y >= PAGE_BOTTOM + i:
                col = PAPER_EDGE
            c.set(LEFT_PAGE_X - EDGE + i, y, col)
            c.set(RIGHT_PAGE_END + EDGE - 1 - i, y, col)
    for x in range(LEFT_PAGE_X - EDGE + 1, RIGHT_PAGE_END + EDGE - 1):
        if abs(x + 0.5 - SPINE_X) < 3:
            continue
        for i in range(EDGE):
            n = noise(x, i, 93)
            col = (PAPER, PAPER_D, PAPER_EDGE)[i] if n > 40 else PAPER_D
            c.set(x, PAGE_BOTTOM + i, col)
    # where the two stacks meet under the spine the pages are drawn in
    for i in range(EDGE):
        for x in range(SPINE_X - 3, SPINE_X + 3):
            c.set(x, PAGE_BOTTOM + i, scale(PAPER_EDGE, 0.8 + 0.06 * abs(x + 0.5 - SPINE_X)))


def book():
    c = Canvas(BOOK_W, BOOK_H)
    cover(c)
    page(c, LEFT_PAGE_X, SPINE_X, True)
    page(c, SPINE_X, RIGHT_PAGE_END, False)
    edges(c)
    return c


def bookmark():
    """A silk ribbon in light greys (tinted in game): stitched along both long edges, a sheen, a swallowtail end."""
    w, h = 24, 16
    c = Canvas(w, h)
    outline, edge, stitch = (78, 78, 78, 255), (196, 196, 196, 255), (150, 150, 150, 255)
    for y in range(h):
        notch = 5 - min(abs(y - (h - 1) / 2.0), 5)
        end = w - int(notch)
        for x in range(end):
            # the sheen: lightest a third of the way down, a little darker towards the lower edge
            t = abs(y - 5.0) / 10.0
            col = mix((252, 252, 252, 255), (212, 212, 212, 255), min(1.0, t))
            if noise(x, y, 11) < 18:
                col = scale(col, 0.96)
            if y in (1, h - 2):
                col = edge
            if y in (2, h - 3) and x >= 5 and x < end - 2 and x % 3 != 1:
                col = stitch
            c.set(x, y, col)
        c.set(end - 1, y, outline)
        if end - 2 >= 0 and 0 < y < h - 1:
            c.set(end - 2, y, scale(c.get(end - 2, y), 0.88))
    for x in range(w):
        c.set(x, 0, outline)
        c.set(x, h - 1, outline)
    return c


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=OUT)
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for name, canvas in (("book.png", book()), ("bookmark.png", bookmark())):
        (args.out / name).write_bytes(canvas.png())
        print(f"{name}: {canvas.w}x{canvas.h}")


if __name__ == "__main__":
    main()
