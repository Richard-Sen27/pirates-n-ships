#!/usr/bin/env python3
"""Placeholder textures of the Captain's Almanac screen (work package ALM3; ART14 replaces them).

Run (from the repository root, Python 3 standard library only, no Pillow):

    python3 tools/gen_almanac_textures.py            # writes into common/src/main/resources/assets/pirates_n_ships/textures/gui/almanac/
    python3 tools/gen_almanac_textures.py --out DIR  # writes the same files under DIR (AlmanacTexturesTest compares the two)

Plain textures outside the GUI sprite atlas (almanac/client/AlmanacScreen blits them itself):

- book.png (292x180): the open book at 1:1: a leather cover with brass corners, two parchment pages with a page-edge
  stack on the outer and bottom sides and a shadow at the spine. The geometry matches AlmanacScreen's constants:
  left page x 9..146, right page x 146..283, both y 5..172; the edge stacks x 6..9 and 283..286, y 172..175.
- bookmark.png (24x16): one bookmark tab in grey shades, tinted per chapter with its bookmark colour. Its left 4 px
  lie under the cover.

Palette from art/README.md ("GUI kit"). Deterministic: the noise is a fixed integer hash and the PNG writer uses stored
(uncompressed) deflate blocks, so the bytes never depend on the zlib build.
"""
import argparse
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures/gui/almanac"

T = (0, 0, 0, 0)

LEATHER_OUT = (40, 18, 10, 255)
LEATHER_D = (70, 32, 20, 255)
LEATHER = (92, 44, 28, 255)
LEATHER_L = (112, 58, 36, 255)
BRASS_OUT = (82, 54, 18, 255)
BRASS_D = (134, 94, 34, 255)
BRASS = (198, 150, 58, 255)
BRASS_L = (238, 204, 112, 255)
PAPER_EDGE = (160, 128, 84, 255)
PAPER_D = (196, 170, 124, 255)
PAPER = (232, 214, 172, 255)
PAPER_L = (240, 228, 196, 255)
SPINE = (120, 90, 60, 255)

BOOK_W, BOOK_H = 292, 180
SPINE_X = 146
PAGE_TOP, PAGE_BOTTOM = 5, 172
LEFT_PAGE_X, RIGHT_PAGE_END = 9, 283
EDGE = 3


def noise(x, y, seed):
    """A fixed integer hash in 0..255 (no random module: the same bytes on every Python)."""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def scale(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


class Canvas:
    def __init__(self, w, h, fill=T):
        self.w, self.h = w, h
        self.p = [[fill] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c

    def png(self):
        raw = bytearray()
        for row in self.p:
            raw.append(0)
            for c in row:
                raw.extend(bytes(c))
        return png_bytes(self.w, self.h, bytes(raw))


def stored_zlib(data):
    """A zlib stream of stored deflate blocks (no compression; byte-identical everywhere)."""
    out = bytearray(b"\x78\x01")
    pos = 0
    while True:
        block = data[pos:pos + 65535]
        pos += len(block)
        final = 1 if pos >= len(data) else 0
        out.append(final)
        out.extend(struct.pack("<HH", len(block), len(block) ^ 0xFFFF))
        out.extend(block)
        if final:
            break
    out.extend(struct.pack(">I", zlib.adler32(data) & 0xFFFFFFFF))
    return bytes(out)


def png_bytes(w, h, raw):
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", stored_zlib(raw)) + chunk(b"IEND", b"")


def cover(c):
    """Leather with a grain, a dark outline and brass corner plates."""
    for y in range(BOOK_H):
        for x in range(BOOK_W):
            n = noise(x, y, 7)
            c.set(x, y, LEATHER_D if n < 40 else LEATHER_L if n > 230 else LEATHER)
    for x in range(BOOK_W):
        c.set(x, 1, LEATHER_L)
        c.set(x, 0, LEATHER_OUT)
        c.set(x, BOOK_H - 1, LEATHER_OUT)
    for y in range(BOOK_H):
        c.set(0, y, LEATHER_OUT)
        c.set(BOOK_W - 1, y, LEATHER_OUT)
    for cx, cy, dx, dy in ((0, 0, 1, 1), (BOOK_W - 1, 0, -1, 1), (0, BOOK_H - 1, 1, -1), (BOOK_W - 1, BOOK_H - 1, -1, -1)):
        # a brass corner plate: a triangle with 9 px legs, an outline and a highlight
        for i in range(9):
            for j in range(9 - i):
                col = BRASS_L if i == 1 or j == 1 else BRASS if i + j < 6 else BRASS_D
                if i == 0 or j == 0 or i + j == 8:
                    col = BRASS_OUT
                c.set(cx + dx * i, cy + dy * j, col)
        c.set(cx, cy, T)
        c.set(cx + 3 * dx, cy + 3 * dy, BRASS_L)


def page(c, x0, x1, left):
    """One parchment page, darker toward the spine and slightly toward its other edges."""
    width = x1 - x0
    for y in range(PAGE_TOP, PAGE_BOTTOM):
        for x in range(x0, x1):
            to_spine = (x1 - 1 - x) if left else (x - x0)
            f = 1.0
            if to_spine < 14:
                f -= (14 - to_spine) * 0.014
            outer = width - 1 - to_spine
            if outer < 4:
                f -= (4 - outer) * 0.02
            edge_y = min(y - PAGE_TOP, PAGE_BOTTOM - 1 - y)
            if edge_y < 3:
                f -= (3 - edge_y) * 0.02
            n = noise(x, y, 3 if left else 5)
            c.set(x, y, scale(PAPER_D if n < 10 else PAPER_L if n > 236 else PAPER, f))


def edges(c):
    """The page-edge stack: stripes on the outer sides and under both pages, and the spine line."""
    for y in range(PAGE_TOP + 1, PAGE_BOTTOM + EDGE):
        for i in range(EDGE):
            col = PAPER_EDGE if i % 2 == 0 else PAPER_D
            c.set(LEFT_PAGE_X - EDGE + i, y, col)
            c.set(RIGHT_PAGE_END + EDGE - 1 - i, y, col)
    for x in range(LEFT_PAGE_X - EDGE + 1, RIGHT_PAGE_END + EDGE - 1):
        for i in range(EDGE):
            c.set(x, PAGE_BOTTOM + i, PAPER_D if i % 2 == 0 else PAPER_EDGE)
    for y in range(PAGE_TOP, PAGE_BOTTOM + EDGE):
        c.set(SPINE_X - 1, y, SPINE)
        c.set(SPINE_X, y, scale(SPINE, 0.85))


def book():
    c = Canvas(BOOK_W, BOOK_H)
    cover(c)
    page(c, LEFT_PAGE_X, SPINE_X, True)
    page(c, SPINE_X, RIGHT_PAGE_END, False)
    edges(c)
    return c


def bookmark():
    """A ribbon tab in grey shades (tinted in game), its right end cut in a swallowtail."""
    w, h = 24, 16
    c = Canvas(w, h)
    out, dark, mid, light = (70, 70, 70, 255), (170, 170, 170, 255), (215, 215, 215, 255), (250, 250, 250, 255)
    for y in range(h):
        notch = 3 - min(abs(y - (h - 1) / 2.0), 3)
        end = w - int(notch)
        for x in range(end):
            col = light if y <= 2 else dark if y >= h - 3 else mid
            if noise(x, y, 11) < 20:
                col = scale(col, 0.94)
            c.set(x, y, col)
        c.set(end - 1, y, out)
    for x in range(w):
        c.set(x, 0, out)
        c.set(x, h - 1, out)
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
