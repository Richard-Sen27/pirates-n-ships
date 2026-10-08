"""Particle textures for seeing the wind and the waves (work package WD1).

Usage:

    tools/.venv/bin/python tools/gen_sea_effects_textures.py

Output in common/src/main/resources/assets/pirates_n_ships/textures/particle/ (the vanilla particle atlas stitches
every textures/particle/*.png of every namespace, so no particle JSON is needed; the streaks are spawned straight into
the particle engine by sailing/effects/client/SeaEffectsClient):
- wind_streak.png (32x8): a white wisp, u along the streak (u = 0 the tail, u = 1 the head, the direction it flies),
  v across it. The opacity rises slowly from the tail and falls off quickly at the head; across it a soft core with a
  fainter second strand. White, so the particle colour and alpha set the final look.
- foam_streak.png (32x16): a ragged band of foam, long along u (the direction the waves run), dappled with holes,
  faded at the ends and the edges. Off-white.

Deterministic: same input, same bytes.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.dont_write_bytecode = True  # no __pycache__ next to the tools

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/pirates_n_ships/textures/particle"


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def wind_streak():
    w, h = 32, 8
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for x in range(w):
        u = (x + 0.5) / w
        along = smooth(u / 0.7) * smooth((1.0 - u) / 0.12)  # long tail, short head
        for y in range(h):
            v = (y + 0.5) / h - 0.5
            core = math.exp(-(v / 0.16) ** 2)
            strand = 0.45 * math.exp(-((v - 0.28) / 0.08) ** 2) * smooth((u - 0.25) / 0.4)
            a = along * min(1.0, core + strand)
            px[x, y] = (255, 255, 255, int(round(255 * a)))
    return img


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

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for x in range(w):
        u = (x + 0.5) / w
        ends = smooth(u / 0.25) * smooth((1.0 - u) / 0.25)
        for y in range(h):
            v = (y + 0.5) / h
            edge = smooth((0.5 - abs(v - 0.5)) / 0.3)
            n = noise(u, v) * 0.7 + rnd.random() * 0.3
            a = ends * edge * smooth((n - 0.3) / 0.4)
            shade = 236 + int(rnd.random() * 16)
            px[x, y] = (shade, min(255, shade + 4), min(255, shade + 6), int(round(255 * a)))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    wind_streak().save(OUT / "wind_streak.png", optimize=False)
    foam_streak().save(OUT / "foam_streak.png", optimize=False)
    print("wrote", OUT / "wind_streak.png", "and", OUT / "foam_streak.png")


if __name__ == "__main__":
    main()
