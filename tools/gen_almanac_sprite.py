#!/usr/bin/env python3
"""Placeholder 16x16 item sprite for the Captain's Almanac (ALM1, design.md §3.4).

Run (from the repository root, Python 3 standard library only): python3 tools/gen_almanac_sprite.py
Output: common/src/main/resources/assets/pirates_n_ships/textures/item/captains_almanac.png

A thick leather-bound tome seen from the front, tilted like vanilla's book sprites: a dark brown leather cover with a
lighter tooled border, cream page edges along the bottom and the right, a raised spine band at the left and four
copper corners (the brass corners of the real tome). Also the placed tome's breaking particle. ART14 replaces it with
the Blockbench tome's sprite. Deterministic: same bytes every run.
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures/item/captains_almanac.png"

SIZE = 16
CLEAR = (0, 0, 0, 0)
OUTLINE = (38, 22, 14)
LEATHER_DARK = (78, 44, 24)
LEATHER = (110, 64, 34)
LEATHER_LIGHT = (140, 86, 46)
TOOLING = (168, 120, 64)
PAGE = (232, 220, 186)
PAGE_SHADE = (196, 180, 140)
COPPER = (196, 112, 70)
COPPER_LIGHT = (232, 160, 112)
COPPER_DARK = (130, 70, 44)


def png(pixels):
    """RGBA rows -> PNG bytes (8 bit, no interlace)."""
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def tome():
    img = [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]

    def put(x, y, rgb):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            img[y][x] = rgb + (255,)

    # the book's body: cover x 2..12, y 1..13; pages show at the right (x 13) and the bottom (y 14)
    for y in range(1, 15):
        for x in range(1, 15):
            put(x, y, OUTLINE)
    for y in range(2, 14):
        for x in range(2, 13):
            put(x, y, LEATHER)
    # shading: light upper left, dark lower right
    for i in range(2, 13):
        put(i, 2, LEATHER_LIGHT)
        put(i, 13, LEATHER_DARK)
    for i in range(2, 14):
        put(2, i, LEATHER_LIGHT)
        put(12, i, LEATHER_DARK)
    # the spine band at the left edge
    for y in range(2, 14):
        put(3, y, LEATHER_DARK)
    for y in (4, 11):
        put(2, y, TOOLING)
        put(3, y, TOOLING)
    # a tooled frame and a small compass-rose lozenge in the middle of the cover
    for x in range(5, 11):
        put(x, 4, TOOLING)
        put(x, 11, TOOLING)
    for y in range(4, 12):
        put(5, y, TOOLING)
        put(10, y, TOOLING)
    for (x, y) in ((7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)):
        put(x, y, TOOLING)
    put(7, 7, COPPER_LIGHT)
    put(8, 8, COPPER)
    # page edges: right side and bottom
    for y in range(2, 14):
        put(13, y, PAGE if y % 2 else PAGE_SHADE)
    for x in range(3, 13):
        put(x, 14, PAGE if x % 2 else PAGE_SHADE)
    put(13, 14, PAGE_SHADE)
    # copper corners
    for (cx, cy, dx, dy) in ((2, 2, 1, 1), (12, 2, -1, 1), (2, 13, 1, -1), (12, 13, -1, -1)):
        put(cx, cy, COPPER_LIGHT if (dx, dy) == (1, 1) else COPPER)
        put(cx + dx, cy, COPPER)
        put(cx, cy + dy, COPPER_DARK if dy < 0 else COPPER)
    return img


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(png(tome()))
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
