"""Particle textures for seeing the wind and the waves (work packages WD1, WD2).

Usage (standard library only):

    python3 tools/gen_sea_effects_textures.py

Output in common/src/main/resources/assets/pirates_n_ships/textures/particle/ (the vanilla particle atlas stitches
every textures/particle/*.png of every namespace, so no particle JSON is needed; the streaks are spawned straight into
the particle engine by sailing/effects/client/SeaEffectsClient):
- wind_streak_0.png .. wind_streak_3.png (64x16, WD2): white brush strokes, u along the streak (u = 0 the tail,
  u = 1 the head, the direction it flies), v across it. Each stroke tapers from a hair-thin, faint tail to a fuller,
  opaque head with a soft tip. 0 is nearly straight, 1 a gentle arc, 2 a soft S, 3 the "whoosh": a straight run that
  curls up and back over itself at the head. SeaEffectsClient draws the quad with the sprite's 4:1 aspect, so the
  curves are not squashed. White, so the particle colour and alpha set the final look.
- foam_streak.png (32x16): a ragged band of foam, long along u (the direction the waves run), dappled with holes,
  faded at the ends and the edges. Off-white. The same pixels as WD1's (then written with Pillow).

Deterministic: same input, same bytes.
"""
import math
import random
import struct
import sys
import zlib
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ next to the tools

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/pirates_n_ships/textures/particle"

STREAK_W, STREAK_H = 64, 16


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def png(width, height, pixels):
    """pixels[y][x] = (r, g, b, a) -> PNG bytes (8 bit RGBA, no interlace)."""
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


# ---- wind streaks (WD2): centre lines in pixels, x toward the head, y down ----

def straight(s):
    return 1.5 + s * 60.0, 8.6 - 1.0 * math.sin(math.pi * s)


def arc(s):
    return 1.5 + s * 60.0, 10.5 - 4.0 * math.sin(math.pi * s * 0.9)


def wave(s):
    return 1.5 + s * 60.0, 8.0 + 2.6 * math.sin(2.0 * math.pi * s)


CURL_RUN = 0.62
CURL_START = (47.0, 11.0)
CURL_RADIUS = 4.4


def whoosh(s):
    """A straight, slightly rising run, then a tightening curl up and back over itself."""
    x0, y0 = 1.5, 12.5
    x1, y1 = CURL_START
    if s <= CURL_RUN:
        k = s / CURL_RUN
        return x0 + k * (x1 - x0), y0 + k * (y1 - y0)
    k = (s - CURL_RUN) / (1.0 - CURL_RUN)
    theta = k * 1.4 * 2.0 * math.pi
    r = CURL_RADIUS * (1.0 - 0.45 * k)
    # the circle starts at its bottom point heading +x and turns upward (y grows downward in the image)
    cx, cy = x1, y1 - CURL_RADIUS
    return cx + r * math.sin(theta), cy + r * math.cos(theta)


STREAKS = [
    # (centre line, share of the length the tip tapers over, the head is a curl)
    (straight, 0.07, False),
    (arc, 0.07, False),
    (wave, 0.07, False),
    (whoosh, 0.22, True),
]


def stroke(path, tip, curl):
    samples = 480
    pts = [path(i / (samples - 1)) for i in range(samples)]
    # arc-length parameter t in [0, 1] of every sample
    acc = [0.0]
    for i in range(1, samples):
        acc.append(acc[-1] + math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]))
    ts = [a / acc[-1] for a in acc]

    def half_thickness(t):
        grow = 0.25 + 0.75 * smooth(t / (0.55 if curl else 0.8))
        return grow * math.sqrt(smooth((1.0 - t) / tip))

    def opacity(t):
        # a faint, hair-thin tail; full toward the head; a soft tip
        return (0.15 + 0.85 * smooth(t / 0.7)) * smooth(t / 0.06) * smooth((1.0 - t) / (tip * 0.8))

    pixels = []
    for y in range(STREAK_H):
        row = []
        for x in range(STREAK_W):
            px, py = x + 0.5, y + 0.5
            best, bt = 1e9, 0.0
            for (sx, sy), t in zip(pts, ts):
                d = (sx - px) ** 2 + (sy - py) ** 2
                if d < best:
                    best, bt = d, t
            cover = max(0.0, min(1.0, half_thickness(bt) + 0.5 - math.sqrt(best)))
            row.append((255, 255, 255, int(round(255 * cover * opacity(bt)))))
        pixels.append(row)
    return pixels


# ---- foam (WD1, unchanged) ----

def foam_streak():
    w, h = 32, 16
    rnd = random.Random(0xF0A7)
    # coarse value noise, stretched along u
    gw, gh = 9, 6
    grid = [[rnd.random() for _ in range(gw)] for _ in range(gh)]

    def noise(u, v):
        gx, gy = u * (gw - 1), v * (gh - 1)
        x0, y0 = int(gx), int(gy)
        x1, y1 = min(gw - 1, x0 + 1), min(gh - 1, y0 + 1)
        fx, fy = smooth(gx - x0), smooth(gy - y0)
        a = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
        b = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
        return a * (1 - fy) + b * fy

    pixels = [[None] * w for _ in range(h)]
    for x in range(w):
        u = (x + 0.5) / w
        ends = smooth(u / 0.25) * smooth((1.0 - u) / 0.25)
        for y in range(h):
            v = (y + 0.5) / h
            edge = smooth((0.5 - abs(v - 0.5)) / 0.3)
            n = noise(u, v) * 0.7 + rnd.random() * 0.3
            a = ends * edge * smooth((n - 0.3) / 0.4)
            shade = 236 + int(rnd.random() * 16)
            pixels[y][x] = (shade, min(255, shade + 4), min(255, shade + 6), int(round(255 * a)))
    return w, h, pixels


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for i, (path, tip, curl) in enumerate(STREAKS):
        (OUT / f"wind_streak_{i}.png").write_bytes(png(STREAK_W, STREAK_H, stroke(path, tip, curl)))
    old = OUT / "wind_streak.png"
    if old.exists():
        old.unlink()  # WD1's single wisp, replaced by the variants
    (OUT / "foam_streak.png").write_bytes(png(*foam_streak()))
    print("wrote", len(STREAKS), "wind streaks and foam_streak.png to", OUT)


if __name__ == "__main__":
    main()
