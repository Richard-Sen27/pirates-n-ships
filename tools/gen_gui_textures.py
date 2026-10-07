#!/usr/bin/env python3
"""GUI texture kit: the screens' frames, panels, buttons and the stamina bar (work package U1).

Run (from the repository root, Python 3 standard library only, no Pillow):

    python3 tools/gen_gui_textures.py            # writes into common/src/main/resources/assets/pirates_n_ships/textures/gui/sprites/
    python3 tools/gen_gui_textures.py --out DIR  # writes the same files under DIR (GuiTexturesTest compares the two)
    python3 tools/gen_gui_textures.py --preview FILE.png   # also writes a 4x contact sheet of every sprite (not committed)

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
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures/gui/sprites"

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
    args = parser.parse_args(argv)
    files = write_all(args.out)
    if args.preview:
        preview(args.preview)
    print(f"wrote {len(files)} files under {args.out}")


if __name__ == "__main__":
    main(sys.argv[1:])
