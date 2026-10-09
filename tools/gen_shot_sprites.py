#!/usr/bin/env python3
"""Flat 16x16 item sprites for chain shot and grapeshot (CAN3, design.md §8.2).

Run (from the repository root, Python 3 standard library only): python3 tools/gen_shot_sprites.py
Output: common/src/main/resources/assets/pirates_n_ships/textures/item/{chain_shot,grapeshot}.png

Small ammunition items may be flat sprites (art/README.md, small items); datagen writes their item/generated models
(CannonData: m.flatItem). Colours are taken from the item palettes (tools/gen_item_palette.py) so the sprites sit next
to the hand-made cannonball: cast_iron_light / cast_iron / cast_iron_dark and lead_light from palette_2 for the iron,
burlap_light / burlap_dark / cord / twine from palette_4 for the canvas bag.

- chain_shot: two iron balls on the 45 degree diagonal (top-left and bottom-right, like a tool sprite), lit from the
  upper left, joined by three chain links.
- grapeshot: a round canvas bag tied at the neck with twine, the small balls inside bulging through the cloth as a
  hex pattern of shaded bumps.

Every pixel is decided by sampling its centre against circles, so the output is deterministic (same bytes every run).
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures/item"

# palette_2.png
CAST_IRON_LIGHT = (118, 118, 126)
CAST_IRON = (56, 56, 62)
CAST_IRON_DARK = (34, 34, 40)
LEAD_LIGHT = (156, 158, 168)
# palette_4.png
BURLAP_LIGHT = (184, 150, 98)
BURLAP_DARK = (128, 98, 60)
CORD = (96, 66, 40)
TWINE = (206, 184, 128)

SIZE = 16


def png(pixels):
    """RGBA rows -> PNG bytes (8 bit, no interlace)."""
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def blank():
    return [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]


def put(img, x, y, rgb):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img[y][x] = rgb + (255,)


def ball(img, cx, cy, r):
    """An iron ball: dark rim, body, light upper-left side and a highlight."""
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = (dx * dx + dy * dy) ** 0.5
            if d > r:
                continue
            light = -(dx + dy) / (r * 1.414)  # +1 at the upper left, -1 at the lower right
            if d > r - 0.9:
                c = CAST_IRON_DARK
            elif light > -0.1:
                c = CAST_IRON_LIGHT
            else:
                c = CAST_IRON
            put(img, x, y, c)
    put(img, int(cx - 1.6), int(cy - 1.6), LEAD_LIGHT)  # the glint


def chain_shot():
    img = blank()
    # chain links between the balls along the diagonal: edge-on (2 px) and flat (ring) links in turn
    for i, (x, y) in enumerate([(6, 6), (7, 7), (8, 8), (9, 9)]):
        if i % 2 == 0:
            put(img, x, y, CAST_IRON_LIGHT)
            put(img, x + 1, y, CAST_IRON_DARK)
            put(img, x, y + 1, CAST_IRON_DARK)
        else:
            put(img, x, y, CAST_IRON_DARK)
    ball(img, 4.0, 4.0, 3.4)
    ball(img, 12.0, 12.0, 3.4)
    return img


def grapeshot():
    img = blank()
    cx, cy, r = 8.0, 9.6, 5.9
    bumps = []
    for row in range(-3, 4):
        for col in range(-3, 4):
            bx = cx + col * 2.6 + (1.3 if row % 2 else 0.0)
            by = cy + row * 2.25
            bumps.append((bx, by))
    for y in range(SIZE):
        for x in range(SIZE):
            px, py = x + 0.5, y + 0.5
            dx, dy = px - cx, py - cy
            d = (dx * dx + dy * dy) ** 0.5
            if d > r:
                continue
            if d > r - 0.9:
                put(img, x, y, CORD)
                continue
            # the nearest ball bulging through the cloth: light on its upper left, dark between the balls
            bx, by = min(bumps, key=lambda b: (px - b[0]) ** 2 + (py - b[1]) ** 2)
            ex, ey = px - bx, py - by
            e = (ex * ex + ey * ey) ** 0.5
            if e > 1.15:
                c = BURLAP_DARK
            elif ex + ey < -0.4:
                c = TWINE
            else:
                c = BURLAP_LIGHT
            put(img, x, y, c)
    # the neck tied with twine and the gathered cloth above it
    for x in range(6, 10):
        put(img, x, 3, CORD)
        put(img, x, 4, TWINE)
    for x, c in [(6, CORD), (7, BURLAP_LIGHT), (8, BURLAP_LIGHT), (9, CORD)]:
        put(img, x, 2, c)
    for x, c in [(5, CORD), (6, BURLAP_LIGHT), (7, TWINE), (8, BURLAP_DARK), (9, BURLAP_LIGHT), (10, CORD)]:
        put(img, x, 1, c)
    put(img, 10, 4, TWINE)
    put(img, 11, 5, TWINE)  # the loose end of the tie
    return img


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, img in [("chain_shot", chain_shot()), ("grapeshot", grapeshot())]:
        (OUT_DIR / f"{name}.png").write_bytes(png(img))
        print("wrote", OUT_DIR / f"{name}.png")


if __name__ == "__main__":
    main()
