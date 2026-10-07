#!/usr/bin/env python3
"""GUI texture kit: the screens' frames, panels, buttons and the stamina bar (work package U1).

Run (from the repository root, Python 3 standard library only, no Pillow):

    python3 tools/gen_gui_textures.py            # writes into common/src/main/resources/assets/pirates_n_ships/textures/gui/sprites/
    python3 tools/gen_gui_textures.py --out DIR  # writes the same files under DIR (GuiTexturesTest compares the two)
    python3 tools/gen_gui_textures.py --preview FILE.png   # also writes a 4x contact sheet of every sprite (not committed)
    python3 tools/gen_gui_textures.py --out DIR --textures-out DIR2   # also the chart textures, under DIR2 (ChartTexturesTest)

The chart textures (work package MAP1: textures/gui/chart/sheet.png with the compass rose, doodles, marker and ship
icons, the chart item's textures/item/chart.png, and MAP2's map tile textures block/map_tile.png and item/map_tile.png) are plain textures outside the sprite atlas. A run without
--out writes them into the mod's textures folder; with --out they are written only under --textures-out, so
GuiTexturesTest's run (--out alone) sees just the atlas sprites.

Every sprite is a PNG in vanilla's GUI sprite atlas (textures/gui/sprites/**, id pirates_n_ships:<path>) plus a
.png.mcmeta with its GuiSpriteScaling: nine_slice for panels and buttons (vanilla tiles the edges and the centre, so
every edge and centre pattern repeats cleanly), stretch for icons and the stamina fills. The Java side names the ids
in core/client/gui/GuiSprites; the JUnit test GuiTexturesTest checks that every id has both files, that the borders
fit, and that a fresh run of this script writes byte-identical files.

Style (art/README.md, "GUI kit"): 16x16-scale pixel art, no anti-aliasing, two or three shades per material: dark
wood, brass, parchment, red wax, navy blue. Deterministic: the noise is a fixed integer hash, and the PNG writer
uses stored (uncompressed) deflate blocks, so the bytes never depend on the zlib build.
"""
import argparse
import json
import math
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures"
OUT = TEXTURES / "gui/sprites"

T = (0, 0, 0, 0)


def rgb(r, g, b, a=255):
    return (r, g, b, a)


# ---------------------------------------------------------------- palette (art/README.md lists the same values)
OUTLINE = rgb(24, 15, 8)
WOOD_D = rgb(52, 34, 20)
WOOD = rgb(76, 51, 31)
WOOD_L = rgb(100, 68, 42)
WOOD_HI = rgb(132, 94, 58)
BOARD = rgb(60, 40, 24)
BOARD_D = rgb(50, 33, 20)
BRASS_OUT = rgb(82, 54, 18)
BRASS_D = rgb(134, 94, 34)
BRASS = rgb(198, 150, 58)
BRASS_L = rgb(238, 204, 112)
PAPER_EDGE = rgb(160, 128, 84)
PAPER_D = rgb(196, 170, 124)
PAPER = rgb(222, 201, 156)
PAPER_L = rgb(238, 224, 188)
WAX_D = rgb(104, 18, 18)
WAX = rgb(156, 32, 28)
WAX_L = rgb(206, 72, 56)
NAVY_D = rgb(28, 40, 74)
NAVY = rgb(48, 70, 124)
NAVY_L = rgb(92, 120, 180)
INSET_D = rgb(20, 13, 8)
INSET = rgb(34, 23, 14)
GREY_OUT = rgb(52, 48, 42)
GREY_D = rgb(84, 79, 70)
GREY = rgb(112, 106, 96)
GREY_L = rgb(140, 134, 122)
SHADOW = rgb(0, 0, 0, 80)
# Stamina fill: deep red to amber in eight bands, each with a highlight (top) and a shadow (bottom) shade
FILL_BANDS = [(112, 18, 16), (136, 26, 18), (160, 40, 20), (182, 60, 22), (200, 84, 26), (216, 110, 30),
              (228, 138, 38), (240, 166, 48)]


def shade(c, f, a=None):
    return (min(255, int(c[0] * f)), min(255, int(c[1] * f)), min(255, int(c[2] * f)), c[3] if a is None else a)


def lift(c, d):
    return (min(255, c[0] + d), min(255, c[1] + d), min(255, c[2] + d), 255)


def noise(x, y, seed):
    """A fixed integer hash in 0..255 (no random module: the same bytes on every Python)."""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


class Canvas:
    def __init__(self, w, h, fill=T):
        self.w, self.h = w, h
        self.p = [[fill] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c

    def get(self, x, y):
        return self.p[y][x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)

    def ring(self, x, y):
        return min(x, y, self.w - 1 - x, self.h - 1 - y)

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


# ---------------------------------------------------------------- sprites
SPRITES = {}


def sprite(name, scaling):
    def register(fn):
        SPRITES[name] = (fn, scaling)
        return fn
    return register


def nine(w, h, border):
    return {"type": "nine_slice", "width": w, "height": h, "border": border}


STRETCH = {"type": "stretch"}


def grain(x, y, horizontal, seed, base, dark):
    """Wood grain along x (horizontal) or y, repeating every 16 px along the grain so tiled edges meet."""
    along, across = (x, y) if horizontal else (y, x)
    n = noise(along % 16 // 3, across, seed)
    return dark if n < 70 else base


def stud(c, x, y):
    """A 4x4 brass stud with its top-left corner at x, y."""
    pattern = ["OLLO", "LLBD", "LBBD", "ODDO"]
    colours = {"O": BRASS_OUT, "L": BRASS_L, "B": BRASS, "D": BRASS_D}
    for dy, row in enumerate(pattern):
        for dx, k in enumerate(row):
            c.set(x + dx, y + dy, colours[k])


@sprite("panel/frame", nine(32, 32, 8))
def frame():
    """Dark-wood board frame: outline, bevelled planks with grain, a light inner bevel, brass corner studs."""
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = c.ring(x, y)
            top_left = (y == d) or (x == d)
            horizontal = y == d or (31 - y) == d
            if d == 0:
                col = OUTLINE
            elif d == 1:
                col = WOOD_L if top_left else WOOD_D
            elif d <= 4:
                col = grain(x, y, horizontal, 1, WOOD, WOOD_D)
            elif d == 5:
                col = WOOD_D
            elif d == 6:
                col = WOOD_HI
            elif d == 7:
                col = INSET_D if top_left else WOOD_D
            else:
                col = grain(x, y, True, 2, BOARD, BOARD_D)
            c.set(x, y, col)
    for (x, y) in ((0, 0), (31, 0), (0, 31), (31, 31)):
        c.set(x, y, T)
    for (x, y) in ((2, 2), (26, 2), (2, 26), (26, 26)):
        stud(c, x, y)
    return c


@sprite("panel/header", nine(32, 16, 4))
def header():
    """A wooden plaque with a brass rim: the title band."""
    c = Canvas(32, 16)
    for y in range(16):
        for x in range(32):
            d = c.ring(x, y)
            if d == 0:
                col = BRASS_OUT
            elif d == 1:
                col = BRASS_L if (y == 1 or x == 1) else BRASS_D
            elif d == 2:
                col = BRASS_D
            else:
                col = grain(x, y, True, 3, WOOD_L, WOOD)
            c.set(x, y, col)
    for (x, y) in ((0, 0), (31, 0), (0, 15), (31, 15)):
        c.set(x, y, T)
    return c


def parchment_canvas(w, h, seed, base, light, dark, edge):
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            d = c.ring(x, y)
            n = noise(x, y, seed)
            if d == 0:
                col = T if n < 80 else edge
            elif d == 1:
                col = edge if n < 90 else dark
            elif d == 2:
                col = dark if n < 110 else base
            else:
                col = light if n < 30 else dark if n < 44 else base
            c.set(x, y, col)
    for (x, y) in ((0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1),
                   (0, h - 1), (1, h - 1), (0, h - 2), (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)):
        c.set(x, y, T)
    return c


@sprite("panel/parchment", nine(32, 32, 4))
def parchment():
    """Parchment with slightly uneven, darker edges and a speckled centre."""
    return parchment_canvas(32, 32, 4, PAPER, PAPER_L, PAPER_D, PAPER_EDGE)


def card_canvas(base, light, outline, seed):
    """A notice card (24x24): a 1 px outline, a flat speckled body, a drop shadow 1 px right and down."""
    c = Canvas(24, 24)
    for y in range(23):
        for x in range(23):
            on_edge = x == 0 or y == 0 or x == 22 or y == 22
            n = noise(x, y, seed)
            c.set(x, y, outline if on_edge else (light if n < 26 else base))
    for i in range(1, 24):
        c.set(23, i, SHADOW)
        c.set(i, 23, SHADOW)
    for (x, y) in ((0, 0), (22, 0), (0, 22), (22, 22)):
        c.set(x, y, T)
    # a slightly curled bottom-right corner
    c.set(21, 21, outline)
    return c


@sprite("panel/card", nine(24, 24, 5))
def card():
    return card_canvas(PAPER_L, rgb(246, 236, 206), PAPER_EDGE, 5)


@sprite("panel/card_hover", nine(24, 24, 5))
def card_hover():
    return card_canvas(rgb(248, 238, 208), rgb(252, 246, 226), BRASS, 5)


@sprite("panel/card_own", nine(24, 24, 5))
def card_own():
    """The viewer's own bounty: a reddish card with a wax-red outline."""
    return card_canvas(rgb(236, 204, 186), rgb(244, 220, 204), WAX, 5)


def button_canvas(out, top, upper, lower, bottom, left, right):
    """A 32x16 button: outline with cut corners, bevel rows/columns, two-tone face."""
    c = Canvas(32, 16)
    for y in range(16):
        for x in range(32):
            d = c.ring(x, y)
            if d == 0:
                col = out
            elif y == 1:
                col = top
            elif y == 14:
                col = bottom
            elif x == 1:
                col = left
            elif x == 30:
                col = right
            else:
                col = upper if y < 8 else lower
            c.set(x, y, col)
    for (x, y) in ((0, 0), (31, 0), (0, 15), (31, 15)):
        c.set(x, y, T)
    return c


@sprite("widget/button", nine(32, 16, 3))
def button():
    return button_canvas(BRASS_OUT, BRASS_L, BRASS, rgb(182, 134, 48), BRASS_D, BRASS_L, BRASS_D)


@sprite("widget/button_hover", nine(32, 16, 3))
def button_hover():
    return button_canvas(BRASS_OUT, rgb(252, 230, 150), rgb(222, 178, 80), rgb(208, 160, 64), BRASS, rgb(252, 230, 150), BRASS)


@sprite("widget/button_pressed", nine(32, 16, 3))
def button_pressed():
    """Pressed or selected: the bevel turns inward and the face darkens."""
    return button_canvas(BRASS_OUT, BRASS_D, rgb(166, 120, 42), rgb(176, 128, 46), BRASS, BRASS_D, BRASS)


@sprite("widget/button_disabled", nine(32, 16, 3))
def button_disabled():
    return button_canvas(GREY_OUT, GREY_L, GREY, rgb(102, 96, 86), GREY_D, GREY_L, GREY_D)


@sprite("widget/field", nine(16, 14, 2))
def field():
    """A text field: a dark inset with a brass rim and a shadow along the top and left."""
    c = Canvas(16, 14)
    for y in range(14):
        for x in range(16):
            d = c.ring(x, y)
            if d == 0:
                col = BRASS_D
            elif d == 1:
                col = INSET_D if (y == 1 or x == 1) else INSET
            else:
                col = INSET
            c.set(x, y, col)
    return c


@sprite("widget/field_focused", nine(16, 14, 2))
def field_focused():
    c = field()
    for y in range(14):
        for x in range(16):
            if c.ring(x, y) == 0:
                c.set(x, y, BRASS_L)
    return c


def tag_canvas(body, outline, light):
    """A name tag (16x11): a chamfered left end with a punched hole, the body, an outline."""
    c = Canvas(16, 11)

    def inside(x, y):
        if not (0 <= x < 16 and 0 <= y < 11):
            return False
        return x >= max(0, 3 - min(y, 10 - y))

    for y in range(11):
        for x in range(16):
            if not inside(x, y):
                continue
            edge = not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            c.set(x, y, outline if edge else (light if y == 1 else body))
    c.set(3, 5, WOOD_D)
    c.set(15, 0, T)
    c.set(15, 10, T)
    return c


@sprite("widget/tag", nine(16, 11, 3))
def tag():
    return tag_canvas(PAPER_L, PAPER_EDGE, rgb(248, 240, 214))


@sprite("widget/tag_hover", nine(16, 11, 3))
def tag_hover():
    return tag_canvas(rgb(250, 228, 160), BRASS_D, rgb(254, 240, 190))


@sprite("widget/divider", nine(16, 3, {"left": 2, "top": 0, "right": 2, "bottom": 0}))
def divider():
    """A thin brass rule (light, mid, dark rows) with dark end caps."""
    c = Canvas(16, 3)
    for x in range(16):
        c.set(x, 0, BRASS_L)
        c.set(x, 1, BRASS)
        c.set(x, 2, BRASS_D)
    for y in range(3):
        c.set(0, y, BRASS_OUT)
        c.set(15, y, BRASS_OUT)
    return c


@sprite("widget/divider_vertical", nine(3, 16, {"left": 0, "top": 2, "right": 0, "bottom": 2}))
def divider_vertical():
    c = Canvas(3, 16)
    for y in range(16):
        c.set(0, y, BRASS_L)
        c.set(1, y, BRASS)
        c.set(2, y, BRASS_D)
    for x in range(3):
        c.set(x, 0, BRASS_OUT)
        c.set(x, 15, BRASS_OUT)
    return c


@sprite("widget/scroll_track", nine(6, 16, 2))
def scroll_track():
    c = Canvas(6, 16)
    for y in range(16):
        for x in range(6):
            d = c.ring(x, y)
            c.set(x, y, OUTLINE if d == 0 else (INSET_D if x == 1 or y == 1 else INSET))
    return c


def knob_canvas(light, mid, dark, out):
    c = Canvas(6, 16)
    for y in range(16):
        for x in range(6):
            d = c.ring(x, y)
            if d == 0:
                col = out
            elif x == 1 or y == 1:
                col = light
            elif x == 4 or y == 14:
                col = dark
            else:
                col = dark if y % 3 == 0 else mid  # grip lines
            c.set(x, y, col)
    return c


@sprite("widget/scroll_knob", nine(6, 16, {"left": 2, "top": 3, "right": 2, "bottom": 3}))
def scroll_knob():
    return knob_canvas(BRASS_L, BRASS, BRASS_D, BRASS_OUT)


@sprite("widget/scroll_knob_hover", nine(6, 16, {"left": 2, "top": 3, "right": 2, "bottom": 3}))
def scroll_knob_hover():
    return knob_canvas(rgb(252, 230, 150), rgb(222, 178, 80), BRASS, BRASS_OUT)


def from_pattern(rows, colours):
    c = Canvas(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, k in enumerate(row):
            c.set(x, y, colours.get(k, T))
    return c


GOLD = rgb(252, 216, 78)


@sprite("icon/coin", STRETCH)
def coin():
    """A gold doubloon, 9x9."""
    return from_pattern([
        "..OOOOO..",
        ".OLLLGGO.",
        "OLLGGGGDO",
        "OLGGDGGDO",
        "OLGDGDGDO",
        "OLGGDGGDO",
        "OGGGGGDDO",
        ".ODDDDDO.",
        "..OOOOO..",
    ], {"O": BRASS_OUT, "L": BRASS_L, "G": GOLD, "D": BRASS})


@sprite("icon/coin_stack", STRETCH)
def coin_stack():
    """Three doubloons on top of each other (edge view), 9x9."""
    return from_pattern([
        "..OOOOO..",
        ".OLLGGGO.",
        "OLGGGGGDO",
        "ODDDDDDDO",
        "OLGGGGGDO",
        "ODDDDDDDO",
        "OLGGGGGDO",
        "ODDDDDDDO",
        ".OOOOOOO.",
    ], {"O": BRASS_OUT, "L": BRASS_L, "G": GOLD, "D": BRASS})


@sprite("icon/wax_seal", STRETCH)
def wax_seal():
    """A red wax seal with an embossed ring, 9x9 (players' notices)."""
    return from_pattern([
        "..OOOO...",
        ".OLLMMOO.",
        "OLMDDDMMO",
        "OLDMMMDMO",
        "OMDMLMDMO",
        "OMDMMMDDO",
        ".OMDDDMO.",
        ".OOMMMOO.",
        "...OOO...",
    ], {"O": WAX_D, "L": WAX_L, "M": WAX, "D": WAX_D})


@sprite("icon/navy_anchor", STRETCH)
def navy_anchor():
    """A navy-blue anchor on a round badge, 9x9 (the navy's notices)."""
    return from_pattern([
        "..OOOOO..",
        ".OBBWBBO.",
        "OBBWWWBBO",
        "OBBBWBBBO",
        "OBBBWBBBO",
        "OBWBWBWBO",
        "OBBWWWBBO",
        ".OBBBBBO.",
        "..OOOOO..",
    ], {"O": NAVY_D, "B": NAVY, "W": NAVY_L})


@sprite("icon/pin", STRETCH)
def pin():
    """A brass pin head with its shadow, 5x5."""
    return from_pattern([
        ".OO..",
        "OLBO.",
        "OBDOS",
        ".OOS.",
        "..S..",
    ], {"O": BRASS_OUT, "L": BRASS_L, "B": BRASS, "D": BRASS_D, "S": SHADOW})


# ---------------------------------------------------------------- stamina bar
def trough_canvas(top, left, bottom_right):
    """The stamina trough (16x7): a 1 px brass frame with cut corners around a flat dark inside."""
    c = Canvas(16, 7)
    for y in range(7):
        for x in range(16):
            if y == 0:
                col = top
            elif x == 0:
                col = left
            elif y == 6 or x == 15:
                col = bottom_right
            else:
                col = INSET
            c.set(x, y, col)
    for (x, y) in ((0, 0), (15, 0), (0, 6), (15, 6)):
        c.set(x, y, T)
    return c


@sprite("hud/stamina_trough", nine(16, 7, 1))
def stamina_trough():
    return trough_canvas(BRASS_L, BRASS, BRASS_D)


@sprite("hud/stamina_trough_alert", nine(16, 7, 1))
def stamina_trough_alert():
    """Drawn over the trough while a refused action flashes."""
    c = trough_canvas(WAX_L, WAX, WAX_D)
    for y in range(1, 6):
        for x in range(1, 15):
            c.set(x, y, rgb(156, 32, 28, 110))
    return c


def band(i):
    return FILL_BANDS[i]


@sprite("hud/stamina_fill", STRETCH)
def stamina_fill():
    """The horizontal fill (80x5, drawn 1:1 from the left): deep red on the left to amber on the right."""
    c = Canvas(80, 5)
    for x in range(80):
        b = band(x // 10)
        base = rgb(*b)
        c.set(x, 0, lift(base, 34))
        for y in (1, 2, 3):
            c.set(x, y, base)
        c.set(x, 4, shade(base, 0.72))
    return c


@sprite("hud/stamina_fill_vertical", STRETCH)
def stamina_fill_vertical():
    """The vertical fill (5x20, drawn 1:1 from the bottom): deep red at the bottom to amber at the top."""
    c = Canvas(5, 20)
    for y in range(20):
        b = band(min(7, (19 - y) * 8 // 20))
        base = rgb(*b)
        c.set(0, y, lift(base, 34))
        for x in (1, 2, 3):
            c.set(x, y, base)
        c.set(4, y, shade(base, 0.72))
    return c


@sprite("hud/riposte", STRETCH)
def riposte():
    """The riposte marker, a white-gold spark (3x9), blinks at both ends of the bar."""
    return from_pattern([
        ".W.",
        ".W.",
        "WLW",
        "LWL",
        "WLW",
        "LWL",
        "WLW",
        ".W.",
        ".W.",
    ], {"W": rgb(255, 250, 230), "L": BRASS_L})


@sprite("hud/lockout", STRETCH)
def lockout():
    """The parry lockout, a small grey padlock (5x7) next to the bar."""
    return from_pattern([
        ".OOO.",
        "O...O",
        "O...O",
        "OOOOO",
        "OLGGO",
        "OGDGO",
        "OOOOO",
    ], {"O": GREY_OUT, "L": GREY_L, "G": GREY, "D": GREY_OUT})


# ---------------------------------------------------------------- chart (work package MAP1)
# Not in the GUI sprite atlas: plain textures drawn with GuiGraphics.blit by UV, written under textures/ (see
# write_extras). The UV layout of gui/chart/sheet.png is mirrored in chart/render/ChartSheet.java; never move a part.
#
#   sheet.png, 128x64:
#     (0, 0)   compass rose, 32x32 (the screen's corner rose)
#     (32, 0)  sea serpent doodle, 32x16
#     (32, 16) whale doodle, 32x16
#     (0, 32)  small compass rose doodle, 16x16
#     (64, 0)  marker icons, 9x9 each, 10 px apart: X, anchor, skull, port, danger
#     (64, 10) selection ring, 11x11
#     (76, 10) own ship (bow up = north), 9x9
#     (86, 10) other player's ship, 9x9
#   item/chart.png, 16x16: the chart item, a rolled parchment with a red wax seal (placeholder until a Blockbench model)
#   block/map_tile.png, item/map_tile.png, 16x16: the map tile's parchment face and item (work package MAP2, placeholders)
EXTRAS = {}

INK = rgb(44, 32, 24)
INK_SOFT = rgb(44, 32, 24, 150)
SEA_INK = rgb(58, 94, 112)
SERPENT_D = rgb(46, 84, 60)
SERPENT = rgb(74, 120, 84)
SERPENT_L = rgb(118, 158, 104)
WHALE_D = rgb(52, 62, 84)
WHALE = rgb(84, 98, 124)
WHALE_L = rgb(150, 162, 180)
SPRAY = rgb(150, 190, 210)
BONE = rgb(236, 228, 206)
AMBER = rgb(226, 160, 40)


def extra(path):
    def register(fn):
        EXTRAS[path] = fn
        return fn
    return register


def blit(dst, src, ox, oy):
    for y in range(src.h):
        for x in range(src.w):
            col = src.get(x, y)
            if col[3]:
                dst.set(ox + x, oy + y, col)


def in_triangle(px, py, a, b, c):
    def side(p, q, r):
        return (p[0] - r[0]) * (q[1] - r[1]) - (q[0] - r[0]) * (p[1] - r[1])
    d1, d2, d3 = side((px, py), a, b), side((px, py), b, c), side((px, py), c, a)
    neg = d1 < 0 or d2 < 0 or d3 < 0
    pos = d1 > 0 or d2 > 0 or d3 > 0
    return not (neg and pos)


def outline(c, colour):
    """Every transparent pixel next (4-neighbour) to a drawn pixel of another colour becomes colour."""
    marks = []
    for y in range(c.h):
        for x in range(c.w):
            if c.get(x, y)[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < c.w and 0 <= ny < c.h and c.get(nx, ny)[3] and c.get(nx, ny) != colour:
                    marks.append((x, y))
                    break
    for (x, y) in marks:
        c.set(x, y, colour)


def rose(size, points, long_r, short_r, ring):
    """A compass rose: `points` long points (north first, in red) and as many short diagonal ones, two-tone, inked."""
    c = Canvas(size, size)
    cx = cy = (size - 1) / 2
    if ring:
        for y in range(size):
            for x in range(size):
                d = math.hypot(x - cx, y - cy)
                if ring - 0.5 <= d < ring + 0.5:
                    c.set(x, y, INK_SOFT)
    star = []
    for i in range(points * 2):
        ang = math.pi * 2 * i / (points * 2) - math.pi / 2
        r = long_r if i % 2 == 0 else short_r
        w = long_r * 0.26 if i % 2 == 0 else short_r * 0.3
        tip = (cx + math.cos(ang) * r, cy + math.sin(ang) * r)
        left = (cx + math.cos(ang - math.pi / 2) * w, cy + math.sin(ang - math.pi / 2) * w)
        right = (cx + math.cos(ang + math.pi / 2) * w, cy + math.sin(ang + math.pi / 2) * w)
        star.append((i, tip, left, right))
    # short points first, so the long ones lie on top
    for i, tip, left, right in sorted(star, key=lambda s: (s[0] % 2 == 0, s[0])):
        north = i == 0
        for y in range(size):
            for x in range(size):
                if in_triangle(x, y, tip, left, (cx, cy)):
                    c.set(x, y, WAX if north else INK)
                elif in_triangle(x, y, tip, right, (cx, cy)):
                    c.set(x, y, WAX_L if north else PAPER_L)
    outline(c, INK)
    m = int(cx)
    c.set(m, m, BRASS)
    c.set(m + 1, m, BRASS_D)
    c.set(m, m + 1, BRASS_D)
    c.set(m + 1, m + 1, BRASS)
    return c


def serpent():
    """A sea serpent (32x16): two humps and a head rising from the waves, facing east."""
    c = Canvas(32, 16)
    water = 11
    for x in range(2, 22):
        t = ((x - 2) % 10) / 10
        lift = math.sin(math.pi * t)
        if lift <= 0.05:
            continue
        yc = int(round(water - 1 - 6 * lift))
        for y in range(yc - 1, min(water, yc + 2)):
            c.set(x, y, SERPENT_L if y == yc - 1 else SERPENT)
        if yc + 1 < water:
            c.set(x, yc + 1, SERPENT_D)
    # neck and head
    for y in range(4, water):
        for x in (22, 23, 24):
            c.set(x, y, SERPENT if x != 22 else SERPENT_L)
    for y in range(1, 6):
        for x in range(23, 29):
            if (x - 25.5) ** 2 / 9 + (y - 3.2) ** 2 / 4.2 <= 1:
                c.set(x, y, SERPENT_L if y < 3 else SERPENT)
    outline(c, INK)
    c.set(26, 2, BONE)
    c.set(29, 4, WAX)
    c.set(30, 5, WAX)
    # waves under the body
    for x in range(0, 32):
        k = x % 6
        if k in (0, 1):
            c.set(x, water + 1, SEA_INK)
        elif k in (2, 3):
            c.set(x, water, SEA_INK)
    for x in range(3, 29, 6):
        c.set(x, water + 3, SEA_INK)
        c.set(x + 1, water + 3, SEA_INK)
    return c


def whale():
    """A whale (32x16) with its spout, facing west, the tail flukes east."""
    c = Canvas(32, 16)
    for y in range(16):
        for x in range(32):
            if (x - 13) ** 2 / 110 + (y - 10) ** 2 / 14 <= 1 and y <= 13:
                col = WHALE_L if y >= 12 else WHALE if y >= 8 else WHALE_D
                c.set(x, y, col)
    for (x, y) in ((24, 9), (25, 9), (25, 8), (26, 8), (26, 7), (27, 7), (27, 6), (28, 6), (28, 10), (27, 10),
                   (26, 10), (27, 11), (28, 11), (29, 12)):
        c.set(x, y, WHALE_D)
    outline(c, INK)
    c.set(5, 9, BONE)
    for y in (11, 12):
        for x in range(4, 9, 2):
            c.set(x, y, WHALE_D)
    for (x, y) in ((9, 4), (9, 3), (8, 2), (7, 1), (10, 2), (11, 1), (9, 1), (6, 2), (12, 2)):
        c.set(x, y, SPRAY)
    for x in range(0, 32):
        if x % 7 in (0, 1):
            c.set(x, 15, SEA_INK)
        elif x % 7 in (2, 3):
            c.set(x, 14, SEA_INK)
    return c


MARKERS = [
    ([  # X marks the spot
        "R.......R",
        "RW.....WR",
        ".RW...WR.",
        "..RW.WR..",
        "...RWR...",
        "..RW.WR..",
        ".RW...WR.",
        "RW.....WR",
        "R.......R",
    ], {"R": WAX_D, "W": WAX}),
    ([  # anchor
        "...OOO...",
        "...O.O...",
        "...OOO...",
        ".OOOOOOO.",
        "....O....",
        "....O....",
        "O...O...O",
        ".O..O..O.",
        "..OOOOO..",
    ], {"O": NAVY_D}),
    ([  # skull
        "..OOOOO..",
        ".OWWWWWO.",
        "OWWWWWWWO",
        "OWOOWOOWO",
        "OWOOWOOWO",
        "OWWWOWWWO",
        ".OWWWWWO.",
        "..OWOWO..",
        "..OOOOO..",
    ], {"O": INK, "W": BONE}),
    ([  # port: a house with a red roof
        "....O....",
        "...ORO...",
        "..ORRRO..",
        ".ORRRRRO.",
        "OOOOOOOOO",
        ".OBBOBBO.",
        ".OBBOBBO.",
        ".OBBBBBO.",
        ".OOOOOOO.",
    ], {"O": INK, "R": WAX, "B": BRASS}),
    ([  # danger: a warning triangle
        "....O....",
        "...OYO...",
        "...OYO...",
        "..OYOYO..",
        "..OYOYO..",
        ".OYYOYYO.",
        ".OYYYYYO.",
        "OYYYOYYYO",
        "OOOOOOOOO",
    ], {"O": INK, "Y": AMBER}),
]

SHIP = [
    "....O....",
    "...OBO...",
    "..OBBBO..",
    "OSSSSSSSO",
    "..OBBBO..",
    "OSSSSSSSO",
    "..OBBBO..",
    "..OBBBO..",
    "..OOOOO..",
]

RING = [
    "...OOOOO...",
    "..O.....O..",
    ".O.......O.",
    "O.........O",
    "O.........O",
    "O.........O",
    "O.........O",
    "O.........O",
    ".O.......O.",
    "..O.....O..",
    "...OOOOO...",
]


@extra("gui/chart/sheet.png")
def chart_sheet():
    c = Canvas(128, 64)
    blit(c, rose(32, 4, 15, 10, 13), 0, 0)
    blit(c, serpent(), 32, 0)
    blit(c, whale(), 32, 16)
    blit(c, rose(16, 4, 7, 5, 0), 0, 32)
    for i, (rows, colours) in enumerate(MARKERS):
        assert len(rows) == 9 and all(len(r) == 9 for r in rows), i
        blit(c, from_pattern(rows, colours), 64 + 10 * i, 0)
    blit(c, from_pattern(RING, {"O": BRASS_L}), 64, 10)
    blit(c, from_pattern(SHIP, {"O": INK, "B": WOOD_L, "S": PAPER_L}), 76, 10)
    blit(c, from_pattern(SHIP, {"O": INK, "B": WOOD_L, "S": NAVY_L}), 86, 10)
    return c


@extra("item/chart.png")
def chart_item():
    """A rolled parchment lying diagonally, a red wax seal on a ribbon in the middle (16x16)."""
    c = Canvas(16, 16)
    ax, ay, bx, by = 3.5, 12.5, 12.5, 3.5
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            t = ((px - ax) * dx + (py - ay) * dy) / (length * length)
            # signed distance across the roll: negative is the upper-left (lit) side
            s = ((px - ax) * dy - (py - ay) * dx) / length
            if t < -0.06 or t > 1.06 or abs(s) > 2.6:
                continue
            if t < 0.02 or t > 0.98:
                col = PAPER_EDGE if abs(s) > 1 else PAPER_D
            elif 0.44 <= t <= 0.56:
                col = WAX_L if s < -1 else WAX if s < 1 else WAX_D
            else:
                col = PAPER_L if s < -1.2 else PAPER if s < 1.2 else PAPER_D
            c.set(x, y, col)
    outline(c, OUTLINE)
    for (x, y) in ((7, 8), (8, 7), (8, 8)):
        c.set(x, y, WAX_D)
    c.set(7, 7, WAX_L)
    return c


@extra("block/map_tile.png")
def map_tile_block():
    """The map tile's blank parchment face (MAP2, 16x16, placeholder until a Blockbench model): speckled paper with a
    darker worn edge. The drawing itself is the block entity renderer's."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 61)
            col = PAPER_D if c.ring(x, y) == 0 else (PAPER_D if n < 30 else PAPER_L if n > 220 else PAPER)
            c.set(x, y, col)
    return c


LAND_INK = rgb(150, 142, 98)
SEA_WASH = rgb(160, 184, 180)


@extra("item/map_tile.png")
def map_tile_item():
    """The map tile item (MAP2, 16x16): parchment in a spruce frame with a sketched coast and a red X."""
    c = Canvas(16, 16)
    for y in range(1, 15):
        for x in range(1, 15):
            ring = min(x - 1, y - 1, 14 - x, 14 - y)
            if ring == 0:
                c.set(x, y, OUTLINE)
            elif ring == 1:
                c.set(x, y, WOOD_L if (x + y) % 5 else WOOD)
            else:
                # a coast running from the top towards the bottom-right: land to the left, sea to the right
                coast = 6 + (y - 3) // 2 + (1 if y in (6, 7) else 0)
                if x < coast:
                    col = LAND_INK
                elif x == coast:
                    col = INK
                else:
                    col = SEA_WASH if (x + 2 * y) % 7 else PAPER
                c.set(x, y, col)
    for (x, y) in ((4, 9), (6, 11), (6, 9), (4, 11), (5, 10)):
        c.set(x, y, WAX)
    return c


def write_extras(out_dir):
    written = set()
    for path, fn in sorted(EXTRAS.items()):
        f = out_dir / path
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_bytes(fn().png())
        written.add(f)
    return written


# ---------------------------------------------------------------- output
def mcmeta(scaling):
    return json.dumps({"gui": {"scaling": scaling}}, indent=2, sort_keys=False) + "\n"


def write_all(out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    written = set()
    for name, (fn, scaling) in sorted(SPRITES.items()):
        canvas = fn()
        if scaling["type"] == "nine_slice":
            assert (canvas.w, canvas.h) == (scaling["width"], scaling["height"]), name
        png = out_dir / (name + ".png")
        png.parent.mkdir(parents=True, exist_ok=True)
        png.write_bytes(canvas.png())
        meta = out_dir / (name + ".png.mcmeta")
        meta.write_bytes(mcmeta(scaling).encode("utf-8"))
        written.add(png)
        written.add(meta)
    # Remove sprites that no longer exist (this directory belongs to this script alone)
    for f in sorted(out_dir.rglob("*")):
        if f.is_file() and f not in written:
            f.unlink()
    return written


def preview(path, scale=4):
    names = sorted(SPRITES)
    canvases = [SPRITES[n][0]() for n in names]
    cell = max(max(c.w, c.h) for c in canvases) + 4
    cols = 6
    rows = (len(canvases) + cols - 1) // cols
    sheet = Canvas(cols * cell * scale, rows * cell * scale, rgb(90, 110, 90))
    for i, c in enumerate(canvases):
        ox, oy = (i % cols) * cell * scale, (i // cols) * cell * scale
        for y in range(c.h):
            for x in range(c.w):
                col = c.get(x, y)
                if col[3] == 0:
                    continue
                for sy in range(scale):
                    for sx in range(scale):
                        sheet.set(ox + x * scale + sx, oy + y * scale + sy, col)
    Path(path).write_bytes(sheet.png())


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=OUT)
    parser.add_argument("--preview", type=Path)
    parser.add_argument("--textures-out", type=Path,
                        help="where the chart textures go (gui/chart/sheet.png, item/chart.png); default: the mod's "
                             "textures folder, but only when --out is not given")
    args = parser.parse_args(argv)
    files = write_all(args.out)
    textures = args.textures_out or (TEXTURES if args.out == OUT else None)
    if textures is not None:
        files |= write_extras(textures)
    if args.preview:
        preview(args.preview)
    print(f"wrote {len(files)} files under {args.out}")


if __name__ == "__main__":
    main(sys.argv[1:])
