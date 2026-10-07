#!/usr/bin/env python3
"""Placeholder shark of work package M4: GeckoLib geometry, animations and texture on the shark rig contract
(art/README.md, "Entities", "Shark rig").

Run (from the repository root, with the venv of tools/gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_shark.py

Output (under common/src/main/resources/assets/pirates_n_ships/):
    geo/shark.geo.json              Bedrock geometry 1.12.0, texture 64x32, the contract bones with placeholder cubes
    animations/shark.animation.json swim (loop), idle (loop), bite (play once); geckolib_format_version 2
    textures/entity/shark.png       64x32: grey-blue back, white belly, dark fin tips, black eyes, teeth

Every cube uses per-face UV into a face atlas packed by this script (FACES below is built at run time), so the UV
layout is the script's business: the contract fixes bones, pivots, the texture size and the animation names, not
where faces sit on the sheet. A Blockbench model replacing this one brings its own layout and texture.
Deterministic: same input, same bytes.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "common/src/main/resources/assets/pirates_n_ships"
TEX_W, TEX_H = 64, 32

COLORS = {
    "back_d": (70, 84, 100, 255), "back": (94, 110, 126, 255), "back_l": (112, 128, 144, 255),
    "belly": (232, 236, 238, 255), "belly_d": (208, 214, 218, 255),
    "fin": (82, 96, 112, 255), "fin_tip": (44, 52, 62, 255),
    "eye": (12, 12, 14, 255), "gill": (58, 70, 84, 255),
    "mouth": (120, 40, 44, 255), "tooth": (246, 244, 236, 255),
}

# --- the rig: bone name -> (parent, pivot) in Bedrock file coordinates (1 unit = 1 px, y up, the shark faces -z,
# +x is the shark's left). Keep in step with art/README.md "Shark rig" and SharkRigTest.
BONES = [
    ("root", None, [0, 5, 0]),
    ("body", "root", [0, 5, 0]),
    ("head", "body", [0, 5, -8]),
    ("jaw", "head", [0, 3, -9]),
    ("tail_1", "body", [0, 5, 6]),
    ("tail_2", "tail_1", [0, 5, 14]),
    ("fin_left", "body", [5, 2, -4]),
    ("fin_right", "body", [-5, 2, -4]),
    ("fin_dorsal", "body", [0, 10, -2]),
]

# Cubes per bone: (origin, size, paint). Paint styles: "body" (countershaded sides, back on top, belly below),
# "fin", "eye", "jaw", "head" (body style plus closed-mouth teeth on the front).
CUBES = {
    "body": [([-5, 0, -8], [10, 10, 14], "body")],
    "head": [([-4, 3, -18], [8, 6, 10], "head"),
             ([3.6, 6, -16], [0.6, 1, 1], "eye"),
             ([-4.2, 6, -16], [0.6, 1, 1], "eye")],
    "jaw": [([-3.5, 1, -17], [7, 2, 8], "jaw")],
    "tail_1": [([-3.5, 2, 6], [7, 7, 8], "body")],
    "tail_2": [([-2, 3, 14], [4, 5, 6], "body"),
               ([-0.5, 7, 18], [1, 7, 4], "fin"),
               ([-0.5, 0, 18], [1, 4, 3], "fin")],
    "fin_left": [([5, 1.5, -6], [8, 1, 5], "fin")],
    "fin_right": [([-13, 1.5, -6], [8, 1, 5], "fin")],
    "fin_dorsal": [([-0.5, 10, -4], [1, 8, 6], "fin")],
}

FACE_DIMS = {  # face -> (width axis, height axis) of the cube size (x, y, z)
    "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2),
}


class Atlas:
    """Shelf packer for face rectangles in whole texels; small faces share solid patches."""

    def __init__(self, scale):
        self.scale = scale
        self.x = self.y = self.row = 0
        self.rects = []  # (x, y, w, h, cube_style, face, cube_size)

    def add(self, w, h, style, face, size):
        tw = max(1, round(w * self.scale))
        th = max(1, round(h * self.scale))
        if self.x + tw > TEX_W:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + th > TEX_H - 1:  # the last row holds the solid patches (EYE_PATCH)
            raise OverflowError
        r = (self.x, self.y, tw, th, style, face, size)
        self.rects.append(r)
        self.x += tw
        self.row = max(self.row, th)
        return r


def pack(scale):
    """Packs every face (tallest first, for tighter shelves); returns the atlas and (bone, cube) -> face -> rect."""
    atlas = Atlas(scale)
    faces = []
    for bone, cubes in CUBES.items():
        for i, (origin, size, style) in enumerate(cubes):
            if style == "eye":  # tiny cubes use the solid EYE_PATCH
                continue
            for face, (a, b) in FACE_DIMS.items():
                faces.append((bone, i, face, size[a], size[b], style, size))
    faces.sort(key=lambda f: (-f[4], -f[3]))
    uv = {}
    for bone, i, face, w, h, style, size in faces:
        uv.setdefault((bone, i), {})[face] = atlas.add(w, h, style, face, size)
    return atlas, uv


def build_atlas():
    scale = 1.0
    while True:
        try:
            return pack(scale)
        except OverflowError:
            scale = round(scale - 0.05, 2)


EYE_PATCH = (TEX_W - 1, TEX_H - 1)


def paint(atlas):
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    rnd = random.Random("pns:entity:shark")

    def px(x, y, c):
        img.putpixel((x, y), COLORS[c])

    def speckle(base, light, dark, x, y):
        roll = rnd.random()
        px(x, y, light if roll < 0.12 else dark if roll < 0.24 else base)

    for (x0, y0, w, h, style, face, size) in atlas.rects:
        for yy in range(h):
            for xx in range(w):
                x, y = x0 + xx, y0 + yy
                if style == "fin":
                    edge = xx in (0, w - 1) or yy in (0, h - 1)
                    px(x, y, "fin_tip" if edge and face in ("east", "west", "up") else "fin")
                elif style == "jaw":
                    if face == "up":  # inside of the lower jaw: teeth around the rim
                        rim = xx in (0, w - 1) or yy in (0, h - 1)
                        px(x, y, "tooth" if rim and (xx + yy) % 2 == 0 else "mouth")
                    elif face == "north":
                        px(x, y, "tooth" if yy == 0 and xx % 2 == 0 else "belly")
                    else:
                        px(x, y, "belly" if face != "east" and face != "west" or yy > 0 else "belly_d")
                else:  # body and head: countershading
                    if face == "up":
                        speckle("back", "back_l", "back_d", x, y)
                    elif face == "down":
                        if style == "head":  # roof of the mouth, teeth around the rim
                            rim = xx in (0, w - 1) or yy in (0, h - 1)
                            px(x, y, "tooth" if rim and (xx + yy) % 2 == 1 else "mouth")
                        else:
                            px(x, y, "belly_d" if rnd.random() < 0.15 else "belly")
                    else:
                        split = round(h * 0.55)
                        if yy < split:
                            speckle("back", "back_l", "back_d", x, y)
                        elif yy == split:
                            px(x, y, "back_l")
                        else:
                            px(x, y, "belly")
                        if style == "head" and face == "north" and yy == h - 1:
                            px(x, y, "tooth" if xx % 2 == 0 else "mouth")
                if style == "body" and face in ("east", "west") and size[2] == 14:
                    # gill slits: three dark lines a third of the way along the side
                    gx = [round(w * f) for f in (0.2, 0.27, 0.34)]
                    if xx in gx and round(h * 0.25) <= yy < round(h * 0.6):
                        px(x, y, "gill")
    img.putpixel(EYE_PATCH, COLORS["eye"])
    return img


def face_uv(rect, face):
    x, y, w, h = rect[0], rect[1], rect[2], rect[3]
    if face in ("up", "down"):  # Blockbench writes these flipped (negative size); GeckoLib reads both
        return {"uv": [x + w, y + h], "uv_size": [-w, -h]}
    return {"uv": [x, y], "uv_size": [w, h]}


def geometry(uv):
    bones = []
    for name, parent, pivot in BONES:
        bone = {"name": name}
        if parent:
            bone["parent"] = parent
        bone["pivot"] = pivot
        cubes = []
        for i, (origin, size, style) in enumerate(CUBES.get(name, [])):
            if style == "eye":
                patch = {"uv": [EYE_PATCH[0] + 0.25, EYE_PATCH[1] + 0.25], "uv_size": [0.5, 0.5]}
                faces = {f: dict(patch) for f in FACE_DIMS}
            else:
                faces = {f: face_uv(r, f) for f, r in uv[(name, i)].items()}
            cubes.append({"origin": origin, "size": size, "uv": faces})
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.shark",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 4,
                "visible_bounds_height": 2,
                "visible_bounds_offset": [0, 0.5, 0],
            },
            "bones": bones,
        }],
    }


def keys(frames, easing="easeInOutSine"):
    """{time: [x, y, z]} -> GeckoLib keyframes."""
    return {str(float(round(t, 4))): {"vector": v, "easing": easing} for t, v in frames}


def sway(length, axis, amplitude, phase=0.0, steps=4):
    """A loop of `steps` keyframes swinging `axis` (0 x, 1 y, 2 z) by +-amplitude, shifted by `phase` (0..1)."""
    out = []
    for i in range(steps + 1):
        t = length * i / steps
        # sine at quarter points: 0, +A, 0, -A, 0, shifted by phase in quarters
        q = (i + round(phase * steps)) % steps
        value = [0, amplitude, 0, -amplitude][q]
        v = [0, 0, 0]
        v[axis] = value
        out.append((t, v))
    return keys(out)


def animations():
    swim = {
        "loop": True,
        "animation_length": 1.0,
        "bones": {
            "body": {"rotation": sway(1.0, 1, 3, phase=0.5)},
            "tail_1": {"rotation": sway(1.0, 1, 14)},
            "tail_2": {"rotation": sway(1.0, 1, 20, phase=0.25)},
            "fin_left": {"rotation": sway(1.0, 2, 6)},
            "fin_right": {"rotation": sway(1.0, 2, -6)},
        },
    }
    idle = {
        "loop": True,
        "animation_length": 3.0,
        "bones": {
            "body": {"rotation": sway(3.0, 1, 1.5, phase=0.5)},
            "tail_1": {"rotation": sway(3.0, 1, 6)},
            "tail_2": {"rotation": sway(3.0, 1, 9, phase=0.25)},
            "fin_left": {"rotation": sway(3.0, 2, 4)},
            "fin_right": {"rotation": sway(3.0, 2, -4)},
            "jaw": {"rotation": keys([(0.0, [0, 0, 0]), (1.5, [4, 0, 0]), (3.0, [0, 0, 0])])},
        },
    }
    # positive x opens the jaw (file convention: the front of the jaw drops)
    bite = {
        "animation_length": 0.5,
        "bones": {
            "jaw": {"rotation": keys([(0.0, [0, 0, 0]), (0.2, [38, 0, 0]), (0.3, [0, 0, 0]), (0.5, [0, 0, 0])],
                                     easing="easeOutQuad")},
            "body": {"position": keys([(0.0, [0, 0, 0]), (0.2, [0, 0, -1.5]), (0.5, [0, 0, 0])])},
        },
    }
    return {"format_version": "1.8.0", "geckolib_format_version": 2,
            "animations": {"swim": swim, "idle": idle, "bite": bite}}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent="\t") + "\n")


def main():
    atlas, uv = build_atlas()
    write_json(ASSETS / "geo/shark.geo.json", geometry(uv))
    write_json(ASSETS / "animations/shark.animation.json", animations())
    out = ASSETS / "textures/entity/shark.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    paint(atlas).save(out, format="PNG", optimize=False)
    print(f"shark: atlas scale {atlas.scale}, {len(atlas.rects)} faces", file=sys.stderr)


if __name__ == "__main__":
    main()
