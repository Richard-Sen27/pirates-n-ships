#!/usr/bin/env python3
"""The Captain's Almanac tome's textures (work package ART14, docs/design.md §3.4).

Run (from the repository root, Python 3 standard library only, no Pillow):

    python3 tools/gen_almanac_book_texture.py            # writes into common/src/main/resources/assets/pirates_n_ships/textures/
    python3 tools/gen_almanac_book_texture.py --out DIR  # writes the same files under DIR/entity and DIR/item (AlmanacTexturesTest)

Writes:

- entity/captains_almanac_book.png (64x32): the placed tome, drawn by almanac/client/AlmanacRenderer on vanilla's
  BookModel, so it follows that model's box UV layout (texOffs and sizes from BookModel.createBodyLayer, faces from
  ModelPart.Cube; north faces map u with +x, south faces with -x, v runs with +y):

      left_lid   (0,0)  6x10x0   north u 0..6   = inside of the top cover   south u 6..12  = outside (hinge at u 6)
      right_lid  (16,0) 6x10x0   north u 16..22 = inside of the bottom cover south u 22..28 = outside (hinge at u 28)
      seam       (12,0) 2x10x0   north u 12..14 = the spine's outside       south u 14..16 = its inside
      left_pages (0,10) 5x8x1    south u 7..12 v 11..19 = the left reading face (spine at u 11), east u 6 = fore-edge,
                                 west u 0 = spine side, down u 1..6 / up u 6..11 at v 10 = head and tail
      right_pages (12,10) 5x8x1  north u 13..18 = the right reading face (spine at u 13), east u 18 = fore-edge,
                                 west u 12, down u 13..18 / up u 18..23 at v 10
      flip_page  (24,10) 5x8x0   north u 24..29 (spine at u 24), south u 29..34 (spine at u 33), v 10..18

  The book lies closed on its right cover, so the left lid's outside is the visible front cover (clasp strap); the
  right lid's outside carries the catch. Corner caps sit on the free corners of both covers.

- item/captains_almanac_tome.png (32x32): the item model's texture (art/models/captains_almanac.bbmodel, one texel per
  model pixel, UV = texel / 2) and the placed tome's breaking particle. Regions (texels):

      cover    (0,0)-(12,14)   front and back cover faces, u along x (spine side at u 0), v along z
      spine    (0,14)-(14,18)  the spine's outer face, u along z, v down the book's thickness
      fore     (0,18)-(13,21)  the pages' fore-edge
      ends     (0,21)-(11,24)  the pages' head and tail
      patches  4x4 at (16,0) leather, (20,0) leather dark, (24,0) brass, (28,0) brass light,
               (16,4) band leather, (20,4) parchment, (24,4) brass dark, (28,4) leather outline;
               faces use the inner 3x3 texels ([u+0.5, v+0.5, u+3.5, v+3.5]) against mipmap bleeding

Palette: leather and brass of the GUI kit (art/README.md), shared with tools/gen_almanac_textures.py (the screen).
Deterministic: the noise is a fixed integer hash and the PNG writer uses stored (uncompressed) deflate blocks, so the
bytes never depend on the zlib build.
"""
import argparse
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures"

T = (0, 0, 0, 0)

LEATHER_OUT = (34, 15, 8, 255)
LEATHER_D = (60, 28, 16, 255)
LEATHER = (84, 40, 23, 255)
LEATHER_L = (106, 54, 31, 255)
LEATHER_HI = (128, 70, 40, 255)
TOOL = (46, 21, 11, 255)
BRASS_OUT = (82, 54, 18, 255)
BRASS_D = (134, 94, 34, 255)
BRASS = (198, 150, 58, 255)
BRASS_L = (238, 204, 112, 255)
LABEL_D = (104, 18, 18, 255)
LABEL = (138, 30, 26, 255)
PAPER_EDGE = (160, 128, 84, 255)
PAPER_D = (196, 170, 124, 255)
PAPER = (226, 206, 162, 255)
PAPER_L = (238, 224, 188, 255)
PAPER_LINE = (204, 182, 140, 255)


def noise(x, y, seed):
    """A fixed integer hash in 0..255 (no random module: the same bytes on every Python)."""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def smooth(x, y, cell, seed):
    """Value noise in 0..1: the hash on a grid of {@code cell} pixels, bilinearly blended (smoothstep)."""
    gx, gy = x // cell, y // cell
    fx, fy = (x % cell) / cell, (y % cell) / cell
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = noise(gx, gy, seed), noise(gx + 1, gy, seed)
    c, d = noise(gx, gy + 1, seed), noise(gx + 1, gy + 1, seed)
    top = a + (b - a) * fx
    bottom = c + (d - c) * fx
    return (top + (bottom - top) * fy) / 255.0


def scale(c, f):
    return (max(0, min(255, int(round(c[0] * f)))), max(0, min(255, int(round(c[1] * f)))),
            max(0, min(255, int(round(c[2] * f)))), c[3])


def mix(a, b, t):
    return (int(round(a[0] + (b[0] - a[0]) * t)), int(round(a[1] + (b[1] - a[1]) * t)),
            int(round(a[2] + (b[2] - a[2]) * t)), 255)


class Canvas:
    def __init__(self, w, h, fill=T):
        self.w, self.h = w, h
        self.p = [[fill] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c

    def get(self, x, y):
        return self.p[y][x]

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


def leather(x, y, seed):
    """Grained leather: a few darker pores and lighter scuffs on the mid tone."""
    n = noise(x, y, seed)
    return LEATHER_D if n < 34 else LEATHER_L if n > 226 else LEATHER


def parchment(x, y, seed):
    """Mottled parchment: a soft warm cloud plus a fine grain."""
    m = smooth(x + 3, y + 5, 3, seed)
    n = noise(x, y, seed + 1)
    c = mix(PAPER, PAPER_L, m) if m > 0.5 else mix(PAPER_D, PAPER, 0.55 + m * 0.9)
    if n < 22:
        c = scale(c, 0.95)
    return c


# ---------------------------------------------------------------------------------------------- the placed tome (64x32)

def lid_outside(c, u0, hinge_left, front):
    """A cover's outside, 6x10: board edge, joint at the hinge, a tooled frame, brass caps on the free corners; the
    front cover carries the clasp strap, the back cover its catch."""
    for j in range(10):
        for i in range(6):  # i 0 = hinge, 5 = free edge
            u = u0 + (i if hinge_left else 5 - i)
            col = leather(u, j, 21)
            if i == 0:
                col = LEATHER_D  # the joint
            elif j in (0, 9) or i == 5:
                col = LEATHER if noise(u, j, 22) > 50 else LEATHER_D  # the board's edge
            elif (i in (1, 4) and 1 <= j <= 8) or (j in (1, 8) and 1 <= i <= 4):
                col = TOOL  # the blind-tooled frame
            else:
                col = LEATHER_HI if noise(u, j, 23) > 200 else LEATHER_L  # the panel inside it
            c.set(u, j, col)
    for j0, dj in ((0, 1), (9, -1)):
        corner = [(5, j0, BRASS_L), (4, j0, BRASS), (5, j0 + dj, BRASS), (4, j0 + dj, BRASS_D)]
        for i, j, col in corner:
            c.set(u0 + (i if hinge_left else 5 - i), j, col)
    if front:
        # the strap of the clasp, coming over the fore-edge from the back cover, with its rivet
        for i, j, col in ((5, 4, BRASS_L), (5, 5, BRASS), (4, 4, BRASS), (4, 5, BRASS_D), (3, 4, BRASS_OUT), (3, 5, BRASS_OUT)):
            c.set(u0 + (i if hinge_left else 5 - i), j, col)
    else:
        for i, j, col in ((5, 4, BRASS_L), (5, 5, BRASS_D)):
            c.set(u0 + (i if hinge_left else 5 - i), j, col)


def lid_inside(c, u0, hinge_left):
    """A cover's inside, 6x10: the leather turn-in around the edge, a parchment pastedown under the pages."""
    for j in range(10):
        for i in range(6):
            u = u0 + (i if hinge_left else 5 - i)
            if j in (0, 9) or i == 5:
                col = LEATHER_D if noise(u, j, 31) > 60 else LEATHER
            elif i == 0:
                col = LEATHER_OUT
            else:
                col = scale(parchment(u, j, 33), 0.86)
            c.set(u, j, col)


def spine(c):
    """The spine (seam): outside with two raised bands and a red title label, inside plain dark leather."""
    rows = [LEATHER_OUT, LEATHER, LEATHER_HI, LEATHER_D, LABEL, LABEL_D, LEATHER_HI, LEATHER_D, LEATHER, LEATHER_OUT]
    for j, base in enumerate(rows):
        for k in range(2):
            col = base
            if base in (LEATHER, LEATHER_HI) and k == 1:
                col = scale(base, 0.85)
            if base == LABEL and k == 0:
                col = BRASS  # a gilt dot on the label
            c.set(12 + k, j, col)
            c.set(14 + k, j, LEATHER_D if j not in (0, 9) else LEATHER_OUT)


def reading_face(c, u0, v0, spine_right, seed):
    """A page face, 5x8: parchment with three faint ruled lines, the gutter shade at the spine, a deckled edge."""
    for j in range(8):
        for i in range(5):  # i 0 = spine
            u = u0 + (4 - i if spine_right else i)
            col = parchment(u, v0 + j, seed)
            if j in (2, 4, 6) and 1 <= i <= 3:
                col = mix(col, PAPER_LINE, 0.7)
            if i == 0:
                col = mix(col, PAPER_EDGE, 0.45)
            elif i == 4:
                col = mix(col, PAPER_D, 0.5 if noise(u, j, seed + 7) > 110 else 0.15)
            if j in (0, 7) and i == 4:
                col = mix(col, PAPER_EDGE, 0.4)
            c.set(u, v0 + j, col)


def page_block(c, u0, reading_u, spine_right, seed):
    """One half of the page block: texOffs (u0, 10), 5x8x1."""
    # head and tail (down and up faces, 5x1 at v 10): the deckled page stack
    for k in range(10):
        n = noise(u0 + 1 + k, 10, seed + 3)
        c.set(u0 + 1 + k, 10, PAPER_D if n < 100 else PAPER if n < 200 else PAPER_EDGE)
    for j in range(8):
        n = noise(u0, j, seed + 4)
        c.set(u0, 11 + j, PAPER_EDGE)  # west: the spine side, in the gutter
        c.set(u0 + 6, 11 + j, PAPER_D if n < 120 else PAPER_EDGE if n < 190 else PAPER)  # east: the fore-edge
    hidden_u = u0 + 1 if reading_u != u0 + 1 else u0 + 7
    for j in range(8):
        for i in range(5):
            c.set(hidden_u + i, 11 + j, parchment(hidden_u + i, j, seed + 9))
    reading_face(c, reading_u, 11, spine_right, seed)


def tome_texture():
    c = Canvas(64, 32)
    lid_inside(c, 0, False)          # left lid north: hinge at u 6 (x 0 -> u 6)
    lid_outside(c, 6, True, True)    # left lid south: hinge at u 6
    spine(c)
    lid_inside(c, 16, True)          # right lid north: hinge at u 16
    lid_outside(c, 22, False, False)  # right lid south: hinge at u 28
    page_block(c, 0, 7, True, 41)    # left pages: south face u 7..12 is read, spine at u 11
    page_block(c, 12, 13, False, 43)  # right pages: north face u 13..18 is read, spine at u 13
    reading_face(c, 24, 10, False, 45)  # flip page north, spine at u 24
    reading_face(c, 29, 10, True, 47)   # flip page south, spine at u 33
    return c


# ---------------------------------------------------------------------------------------------- the item (32x32)

def item_cover(c):
    """12x14: board edge, joint by the spine, a blind-tooled double frame, a gilt lozenge in the middle."""
    w, h = 12, 14
    for y in range(h):
        for x in range(w):
            col = leather(x, y, 51)
            if x == w - 1 or y in (0, h - 1):
                col = LEATHER_D
            elif x == 0:
                col = LEATHER_OUT
            elif x == 1:
                col = scale(LEATHER_D, 1.15)
            elif (x in (2, w - 3) and 2 <= y <= h - 3) or (y in (2, h - 3) and 2 <= x <= w - 3):
                col = TOOL
            elif (x in (3, w - 4) and 3 <= y <= h - 4) or (y in (3, h - 4) and 3 <= x <= w - 4):
                col = LEATHER_HI if noise(x, y, 52) > 70 else LEATHER_L
            c.set(x, y, col)
    # a gilt lozenge frame in the middle of the panel (cols 4..7, rows 5..9)
    lozenge = (".LB.", "LTTD", "BTTD", "BTTD", ".DD.")
    colours = {"L": BRASS_L, "B": BRASS, "D": BRASS_D, "T": TOOL}
    for j, row in enumerate(lozenge):
        for i, ch in enumerate(row):
            if ch != ".":
                c.set(4 + i, 5 + j, colours[ch])


# raised bands of the item model (z of each band centre, model px from the book's top end at z 1)
BAND_Z = (3.5, 7.0, 10.5)


def item_spine(c):
    """14x4 at (0,14): u along the spine, v across it; a red label between the first two bands, a gilt dot and a blind line beside each band."""
    w, h = 14, 4
    for y in range(h):
        for x in range(w):
            col = leather(x, 14 + y, 53)
            if y in (0, h - 1):
                col = LEATHER_D
            if x in (0, w - 1):
                col = LEATHER_OUT
            c.set(x, 14 + y, col)
    for bz in BAND_Z:
        for x in (int(bz) - 1, int(bz) + 1):
            for y in (1, 2):
                c.set(x, 14 + y, BRASS_D if y == 1 else LEATHER_HI)
    for x in range(4, 6):
        for y in (1, 2):
            c.set(x, 14 + y, LABEL if y == 1 else LABEL_D)


def item_pages(c, x0, y0, w, h, seed):
    """Page edges, w x h: page lines along u, a darker line under the covers, deckled light flecks."""
    for y in range(h):
        for x in range(w):
            n = noise(x0 + x, y0 + y, seed)
            col = PAPER if (y + (x // 5)) % 2 == 0 else PAPER_D
            if n > 215:
                col = PAPER_L
            elif n < 25:
                col = PAPER_EDGE
            if x in (0, w - 1):
                col = mix(col, PAPER_EDGE, 0.5)
            c.set(x0 + x, y0 + y, col)


def patch(c, x0, y0, col, shade=False):
    for y in range(4):
        for x in range(4):
            v = col
            if shade:
                v = scale(col, 1.06 if x + y < 3 else 0.94 if x + y > 4 else 1.0)
            c.set(x0 + x, y0 + y, v)


def item_texture():
    c = Canvas(32, 32)
    item_cover(c)
    item_spine(c)
    item_pages(c, 0, 18, 13, 3, 61)
    item_pages(c, 0, 21, 11, 3, 62)
    patch(c, 16, 0, LEATHER)
    patch(c, 20, 0, LEATHER_D)
    patch(c, 24, 0, BRASS, True)
    patch(c, 28, 0, BRASS_L, True)
    patch(c, 16, 4, LEATHER_HI)
    patch(c, 20, 4, PAPER)
    patch(c, 24, 4, BRASS_D, True)
    patch(c, 28, 4, LEATHER_OUT)
    return c


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=OUT)
    args = ap.parse_args()
    for folder, name, canvas in (("entity", "captains_almanac_book.png", tome_texture()),
                                 ("item", "captains_almanac_tome.png", item_texture())):
        (args.out / folder).mkdir(parents=True, exist_ok=True)
        (args.out / folder / name).write_bytes(canvas.png())
        print(f"{folder}/{name}: {canvas.w}x{canvas.h}")


if __name__ == "__main__":
    main()
