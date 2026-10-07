#!/usr/bin/env python3
"""Placeholder kraken of work package K1a: GeckoLib geometry, animations and texture on the kraken rig contract, and the
two loot item sprites. The Blockbench look (K1b) replaces the entity files and keeps the contract.

Run (from the repository root; standard library only, no Pillow needed):
    python3 tools/gen_kraken.py

Output (under common/src/main/resources/assets/pirates_n_ships/):
    geo/kraken.geo.json               Bedrock geometry 1.12.0, texture 128x64, the contract bones with placeholder cubes
    animations/kraken.animation.json  idle (loop), surface, grab (play once), submerge (play and hold);
                                      geckolib_format_version 2
    textures/entity/kraken.png        128x64: dark red-violet skin, paler underside and suckers, yellow eyes
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
    Tentacles point straight up (+y) at rest; code aims each tentacle_<i>_1 at its hit box and stretches it along y, so
    no animation may key the rotation or scale of root or tentacle_<i>_1. Animations key mantle, eyes, _2 and _3.
    Tentacle i's ring position, seen from above with the kraken facing -z: x = -sin(a) * 22.4, z = -cos(a) * 22.4,
    a = (i + 0.5) * 45 deg: tentacle 0 is front right (-x, -z), the numbers run clockwise seen from above.
    The server's hit boxes use the same ring (Kraken#anchor), so tentacle i's bones belong to hit box i.

Every cube uses per-face UV into a face atlas packed by this script, so the UV layout is the script's business.
Deterministic: same input, same bytes.
"""
import json
import math
import random
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "common/src/main/resources/assets/pirates_n_ships"
TEX_W, TEX_H = 128, 64
TENTACLES = 8
RING_RADIUS = 22.4
RING_Y = 20.0
SEGMENT = 12.0

COLORS = {
    "skin_d": (74, 22, 40, 255), "skin": (104, 34, 54, 255), "skin_l": (132, 50, 70, 255),
    "under": (196, 120, 120, 255), "under_d": (170, 98, 102, 255),
    "sucker": (232, 190, 176, 255), "sucker_d": (150, 82, 90, 255),
    "eye": (236, 196, 52, 255), "pupil": (16, 10, 12, 255),
}


def ring(i):
    a = math.radians((i + 0.5) * 360.0 / TENTACLES)
    return round(-math.sin(a) * RING_RADIUS, 4), RING_Y, round(-math.cos(a) * RING_RADIUS, 4)


def rig():
    """Bone name -> (parent, pivot) in file coordinates."""
    bones = [("root", None, [0, 0, 0]), ("mantle", "root", [0, 30, 0]),
             ("eye_left", "mantle", [12, 40, -20]), ("eye_right", "mantle", [-12, 40, -20])]
    for i in range(TENTACLES):
        x, y, z = ring(i)
        bones.append((f"tentacle_{i}_1", "root", [x, y, z]))
        bones.append((f"tentacle_{i}_2", f"tentacle_{i}_1", [x, y + SEGMENT, z]))
        bones.append((f"tentacle_{i}_3", f"tentacle_{i}_2", [x, y + 2 * SEGMENT, z]))
    return bones


BONES = rig()


def cubes():
    """Bone -> [(origin, size, style)]. Styles: skin (darker back, paler underside), eye, tentacle."""
    out = {
        "mantle": [([-20, 14, -20], [40, 30, 40], "skin"),
                   ([-16, 44, -10], [32, 14, 30], "skin")],
        "eye_left": [([8, 36, -21], [8, 8, 2], "eye")],
        "eye_right": [([-16, 36, -21], [8, 8, 2], "eye")],
    }
    widths = [6, 5, 3]
    lengths = [SEGMENT, SEGMENT, 10]
    for i in range(TENTACLES):
        x, y, z = ring(i)
        for s in range(3):
            w = widths[s]
            out[f"tentacle_{i}_{s + 1}"] = [([round(x - w / 2, 4), y + s * SEGMENT, round(z - w / 2, 4)], [w, lengths[s], w], "tentacle")]
    return out


CUBES = cubes()

FACE_DIMS = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


class Atlas:
    """Shelf packer for face rectangles in whole texels. Identical tentacle segments share one set of faces."""

    def __init__(self, scale):
        self.scale = scale
        self.x = self.y = self.row = 0
        self.rects = []

    def add(self, w, h, style, face):
        tw = max(1, round(w * self.scale))
        th = max(1, round(h * self.scale))
        if self.x + tw > TEX_W:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + th > TEX_H - 2:  # the last two rows hold the eye patches
            raise OverflowError
        r = (self.x, self.y, tw, th, style, face)
        self.rects.append(r)
        self.x += tw
        self.row = max(self.row, th)
        return r


def pack(scale):
    atlas = Atlas(scale)
    faces = []
    shared = {}  # (style, size) -> key of the first cube with that shape
    for bone, cs in CUBES.items():
        for i, (origin, size, style) in enumerate(cs):
            if style == "eye":
                continue
            key = (style, tuple(size))
            if style == "tentacle" and key in shared:
                continue
            shared.setdefault(key, (bone, i))
            for face, (a, b) in FACE_DIMS.items():
                faces.append(((bone, i), face, size[a], size[b], style))
    faces.sort(key=lambda f: (-f[3], -f[2]))
    uv = {}
    for key, face, w, h, style in faces:
        uv.setdefault(key, {})[face] = atlas.add(w, h, style, face)
    for bone, cs in CUBES.items():
        for i, (origin, size, style) in enumerate(cs):
            if style == "tentacle":
                uv[(bone, i)] = uv[shared[(style, tuple(size))]]
    return atlas, uv


def build_atlas():
    scale = 1.0
    while True:
        try:
            return pack(scale)
        except OverflowError:
            scale = round(scale - 0.05, 2)


EYE_PATCH = (TEX_W - 4, TEX_H - 2)  # 2x2: iris
PUPIL_PATCH = (TEX_W - 1, TEX_H - 1)


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


def paint(atlas):
    img = Image(TEX_W, TEX_H)
    rnd = random.Random("pns:entity:kraken")

    def px(x, y, c):
        img.put(x, y, COLORS[c])

    def speckle(base, light, dark, x, y):
        roll = rnd.random()
        px(x, y, light if roll < 0.12 else dark if roll < 0.26 else base)

    for (x0, y0, w, h, style, face) in atlas.rects:
        for yy in range(h):
            for xx in range(w):
                x, y = x0 + xx, y0 + yy
                if style == "tentacle":
                    # suckers on the inner (south, toward the body when the tentacle curls) face
                    if face == "south" and xx in range(1, max(2, w - 1)) and yy % 3 == 1:
                        px(x, y, "sucker" if xx % 2 else "sucker_d")
                    elif face == "south":
                        px(x, y, "under")
                    else:
                        speckle("skin", "skin_l", "skin_d", x, y)
                else:
                    if face == "down":
                        px(x, y, "under_d" if rnd.random() < 0.2 else "under")
                    elif face == "up":
                        speckle("skin", "skin_l", "skin_d", x, y)
                    else:
                        split = round(h * 0.7)
                        if yy < split:
                            speckle("skin", "skin_l", "skin_d", x, y)
                        else:
                            px(x, y, "under")
    for dx in range(2):
        for dy in range(2):
            px(EYE_PATCH[0] + dx, EYE_PATCH[1] + dy, "eye")
    px(PUPIL_PATCH[0], PUPIL_PATCH[1], "pupil")
    return img


def face_uv(rect, face):
    x, y, w, h = rect[0], rect[1], rect[2], rect[3]
    if face in ("up", "down"):
        return {"uv": [x + w, y + h], "uv_size": [-w, -h]}
    return {"uv": [x, y], "uv_size": [w, h]}


def geometry(uv):
    bones = []
    for name, parent, pivot in BONES:
        bone = {"name": name}
        if parent:
            bone["parent"] = parent
        bone["pivot"] = pivot
        cs = []
        for i, (origin, size, style) in enumerate(CUBES.get(name, [])):
            if style == "eye":
                iris = {"uv": [EYE_PATCH[0] + 0.25, EYE_PATCH[1] + 0.25], "uv_size": [1.5, 1.5]}
                faces = {f: dict(iris) for f in FACE_DIMS}
                faces["north"] = {"uv": [EYE_PATCH[0], EYE_PATCH[1]], "uv_size": [2, 2]}
                cs.append({"origin": origin, "size": size, "uv": faces})
                # the slit pupil, a hair in front of the eye
                pupil = {"uv": [PUPIL_PATCH[0] + 0.25, PUPIL_PATCH[1] + 0.25], "uv_size": [0.5, 0.5]}
                cs.append({"origin": [origin[0] + 3, origin[1] + 1, origin[2] - 0.1], "size": [2, 6, 0.1],
                           "uv": {f: dict(pupil) for f in FACE_DIMS}})
                continue
            cs.append({"origin": origin, "size": size, "uv": {f: face_uv(r, f) for f, r in uv[(name, i)].items()}})
        if cs:
            bone["cubes"] = cs
        bones.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.kraken",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 8,
                "visible_bounds_height": 6,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }


def keys(frames, easing="easeInOutSine"):
    return {str(float(round(t, 4))): {"vector": v, "easing": easing} for t, v in frames}


def sway(length, axis, amplitude, phase=0.0, steps=4):
    out = []
    for i in range(steps + 1):
        t = length * i / steps
        q = (i + round(phase * steps)) % steps
        v = [0, 0, 0]
        v[axis] = [0, amplitude, 0, -amplitude][q]
        out.append((t, v))
    return keys(out)


def animations():
    idle_bones = {"mantle": {"position": keys([(0.0, [0, 0, 0]), (2.0, [0, 0.8, 0]), (4.0, [0, 0, 0])])}}
    for i in range(TENTACLES):
        phase = (i % 4) / 4
        idle_bones[f"tentacle_{i}_2"] = {"rotation": sway(4.0, 0, 12, phase)}
        idle_bones[f"tentacle_{i}_3"] = {"rotation": sway(4.0, 2, 18, phase + 0.25)}
    idle = {"loop": True, "animation_length": 4.0, "bones": idle_bones}

    surface_bones = {"mantle": {"scale": keys([(0.0, [0.9, 0.8, 0.9]), (0.8, [1.05, 1.1, 1.05]), (1.5, [1, 1, 1])])},
                     "eye_left": {"scale": keys([(0.0, [1, 0.1, 1]), (1.0, [1, 0.1, 1]), (1.2, [1, 1, 1])])},
                     "eye_right": {"scale": keys([(0.0, [1, 0.1, 1]), (1.0, [1, 0.1, 1]), (1.2, [1, 1, 1])])}}
    for i in range(TENTACLES):
        surface_bones[f"tentacle_{i}_2"] = {"rotation": keys([(0.0, [-40, 0, 0]), (1.5, [0, 0, 0])])}
        surface_bones[f"tentacle_{i}_3"] = {"rotation": keys([(0.0, [-50, 0, 0]), (1.5, [0, 0, 0])])}
    surface = {"animation_length": 1.5, "bones": surface_bones}

    # one tentacle (0) rises and curls over: the grab
    grab = {"animation_length": 1.0, "bones": {
        "tentacle_0_2": {"rotation": keys([(0.0, [0, 0, 0]), (0.4, [-35, 0, 0]), (1.0, [0, 0, 0])], easing="easeOutQuad")},
        "tentacle_0_3": {"rotation": keys([(0.0, [0, 0, 0]), (0.4, [-60, 0, 0]), (1.0, [0, 0, 0])], easing="easeOutQuad")},
    }}

    sub_bones = {"mantle": {"scale": keys([(0.0, [1, 1, 1]), (1.5, [1.05, 0.85, 1.05])]),
                            "position": keys([(0.0, [0, 0, 0]), (1.5, [0, -2, 0])])},
                 "eye_left": {"scale": keys([(0.0, [1, 1, 1]), (0.4, [1, 0.1, 1])])},
                 "eye_right": {"scale": keys([(0.0, [1, 1, 1]), (0.4, [1, 0.1, 1])])}}
    for i in range(TENTACLES):
        sub_bones[f"tentacle_{i}_2"] = {"rotation": keys([(0.0, [0, 0, 0]), (1.5, [30, 0, 0])])}
        sub_bones[f"tentacle_{i}_3"] = {"rotation": keys([(0.0, [0, 0, 0]), (1.5, [45, 0, 0])])}
    submerge = {"loop": "hold_on_last_frame", "animation_length": 1.5, "bones": sub_bones}

    return {"format_version": "1.8.0", "geckolib_format_version": 2,
            "animations": {"idle": idle, "surface": surface, "grab": grab, "submerge": submerge}}


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


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent="\t") + "\n")


def write_png(path, img):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(img.png())


def main():
    atlas, uv = build_atlas()
    write_json(ASSETS / "geo/kraken.geo.json", geometry(uv))
    write_json(ASSETS / "animations/kraken.animation.json", animations())
    write_png(ASSETS / "textures/entity/kraken.png", paint(atlas))
    write_png(ASSETS / "textures/item/kraken_beak.png", sprite(BEAK, BEAK_PALETTE))
    write_png(ASSETS / "textures/item/kraken_ink.png", sprite(INK, INK_PALETTE))
    print(f"kraken: atlas scale {atlas.scale}, {len(atlas.rects)} faces", file=sys.stderr)


if __name__ == "__main__":
    main()
