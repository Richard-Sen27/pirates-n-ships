#!/usr/bin/env python3
"""The kraken's loot item sprites (work package K1a), and the record of the kraken's rig contract.

Until K1b this script also wrote a placeholder kraken (geometry, animations, texture). Since K1b those three files are
Blockbench exports of art/models/entity/kraken.bbmodel (sources kraken_model.js and kraken_animations.js, see
art/README.md, "Kraken (K1b)"), so this script no longer writes them.

Run (from the repository root; standard library only, no Pillow needed). Writes nothing unless a sprite is named:
    python3 tools/gen_kraken.py kraken_beak kraken_ink

Output (under common/src/main/resources/assets/pirates_n_ships/):
    textures/item/kraken_beak.png     16x16 sprite
    textures/item/kraken_ink.png      16x16 sprite (a stoppered vial of black ink)

The rig (file coordinates: 1 unit = 1 px, y up, the kraken faces -z, +x is its left; the hit box is 56 x 56 px):
    root                        pivot [0, 0, 0]
      mantle                    pivot [0, 30, 0]     the head and the sack above it
        eye_left                pivot [12, 40, -20]  (+x)
        eye_right               pivot [-12, 40, -20]
      tentacle_<i>_1            i = 0..7, pivot on a ring of radius 22.4 px at y 20, at the angle (i + 0.5) * 45 deg
        tentacle_<i>_2          pivot 12 px above _1
          tentacle_<i>_3        pivot 12 px above _2
    Tentacles point straight up (+y) at rest (34 px long); code aims each tentacle_<i>_1 at its hit box and stretches it
    along y, so no animation may key the rotation or scale of root or tentacle_<i>_1. Animations key mantle, eyes, _2
    and _3: idle (loop), surface and grab (play once), submerge (hold on last frame). Texture 128x64.
    Tentacle i's ring position, seen from above with the kraken facing -z: x = -sin(a) * 22.4, z = -cos(a) * 22.4,
    a = (i + 0.5) * 45 deg: tentacle 0 is front right (-x, -z), the numbers run clockwise seen from above.
    The server's hit boxes use the same ring (Kraken#anchor), so tentacle i's bones belong to hit box i.
    KrakenRigTest checks all of this.

Deterministic: same input, same bytes.
"""
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "common/src/main/resources/assets/pirates_n_ships"


class Image:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def put(self, x, y, c):
        self.px[y][x] = c

    def png(self):
        raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in self.px)

        def chunk(tag, data):
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def sprite(rows, palette):
    img = Image(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.put(x, y, palette[ch])
    return img


BEAK = [
    "................",
    "................",
    "......aaa.......",
    ".....abbba......",
    "....abbbbba.....",
    "...abbccbbba....",
    "...abbccccbba...",
    "..abbbccccccba..",
    "..abbbbcccccda..",
    "..abbbbbccdda...",
    "...abbbbbdda....",
    "....aabbdda.....",
    "......aaaa......",
    "................",
    "................",
    "................",
]
BEAK_PALETTE = {"a": (30, 20, 22, 255), "b": (70, 52, 46, 255), "c": (120, 96, 80, 255), "d": (176, 150, 120, 255)}

INK = [
    "................",
    "......cccc......",
    "......cddc......",
    ".......cc.......",
    "......aeea......",
    ".....aeggea.....",
    "....aebbbbea....",
    "....abbbbbba....",
    "....abbffbba....",
    "....abbffbba....",
    "....abbbbbba....",
    "....abbbbbba....",
    ".....abbbba.....",
    "......aaaa......",
    "................",
    "................",
]
INK_PALETTE = {"a": (40, 46, 58, 255), "b": (14, 12, 22, 255), "c": (120, 84, 50, 255), "d": (160, 118, 72, 255),
               "e": (170, 200, 214, 255), "f": (70, 40, 90, 255), "g": (210, 230, 238, 255)}


def write_png(path, img):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(img.png())

SPRITES = {"kraken_beak": (BEAK, BEAK_PALETTE), "kraken_ink": (INK, INK_PALETTE)}


def main():
    """Writes the sprites named on the command line (nothing by default)."""
    names = sys.argv[1:]
    if not names:
        print("gen_kraken: name the sprites to write: " + " ".join(SPRITES), file=sys.stderr)
        return
    for name in names:
        rows, palette = SPRITES[name]
        write_png(ASSETS / f"textures/item/{name}.png", sprite(rows, palette))
        print(f"wrote  item/{name}")


if __name__ == "__main__":
    main()
