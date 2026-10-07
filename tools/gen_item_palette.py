#!/usr/bin/env python3
"""Item palette texture for the hand-made 3D item models (design.md §4.8, work package F8a).

Run (from the repository root): tools/.venv/bin/python tools/gen_item_palette.py
Output: common/src/main/resources/assets/pirates_n_ships/textures/item/palette.png

A 16x16 sheet of 4x4 px colour patches. Blockbench item models map every face to the patch it needs with UVs
(a face of a 1 px edge may use a 1 px sliver of a patch), so all item models share one set of colours.
Deterministic: same input, same bytes. Never move a patch: existing models point at these UVs. Add new colours
only in free cells (there are none left; grow the sheet to 32x16 and keep the first 16 columns as they are).

Layout (UV of the patch's top-left corner; each patch spans 4x4):

    u:   0             4             8             12
 v 0   steel_light   steel         steel_dark    iron_dark
 v 4   brass_light   brass         brass_dark    gold
 v 8   leather       leather_dark  wood_dark     wood
 v 12  bone          black         red           wire (silver wire, 1 px stripes along v)

The wire patch alternates a light and a dark row; a grip face whose v runs along the grip shows the winding.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures/item/palette.png"

PATCHES = [
    [("steel_light", (222, 226, 232)), ("steel", (168, 174, 186)), ("steel_dark", (108, 114, 128)), ("iron_dark", (66, 66, 74))],
    [("brass_light", (238, 204, 112)), ("brass", (198, 150, 58)), ("brass_dark", (134, 94, 34)), ("gold", (252, 216, 78))],
    [("leather", (122, 74, 40)), ("leather_dark", (78, 44, 24)), ("wood_dark", (64, 42, 26)), ("wood", (122, 88, 54))],
    [("bone", (228, 218, 188)), ("black", (26, 24, 28)), ("red", (150, 36, 36)), ("wire", None)],
]
WIRE = ((214, 218, 226), (112, 116, 128))


def main():
    img = Image.new("RGBA", (16, 16))
    for row, patches in enumerate(PATCHES):
        for col, (_, colour) in enumerate(patches):
            for dy in range(4):
                for dx in range(4):
                    c = colour if colour else WIRE[dy % 2]
                    img.putpixel((col * 4 + dx, row * 4 + dy), c + (255,))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, format="PNG", optimize=False)


if __name__ == "__main__":
    main()
