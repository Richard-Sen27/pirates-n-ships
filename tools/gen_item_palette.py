#!/usr/bin/env python3
"""Item palette textures for the hand-made 3D item models (design.md §4.8, work packages F8a, F8b, F8d).

Run (from the repository root): tools/.venv/bin/python tools/gen_item_palette.py
Output: common/src/main/resources/assets/pirates_n_ships/textures/item/palette.png
        common/src/main/resources/assets/pirates_n_ships/textures/item/palette_2.png
        common/src/main/resources/assets/pirates_n_ships/textures/item/palette_3.png

Three 16x16 sheets of 4x4 px colour patches. Blockbench item models map every face to the patch it needs with UVs
(a face of a 1 px edge may use a 1 px sliver of a patch), so all item models share one set of colours.
Deterministic: same input, same bytes. Never move a patch: existing models point at these UVs.

Why a second sheet instead of a wider one: a model's UVs are fractions of the sprite (FaceBakery maps uv / 16 onto
the sprite's full width and height, whatever its pixel size). Growing palette.png to 32x16 would halve the meaning
of every u in the sword models and move all their patches. A new colour therefore goes into a free cell of
palette_2.png (texture "#1" in a model), or into palette_3.png ("#2"). palette_3 is full: the next new colour
needs a new sheet palette_4.png.

palette.png (texture "#0"; UV of the patch's top-left corner; each patch spans 4x4):

    u:   0             4             8             12
 v 0   steel_light   steel         steel_dark    iron_dark
 v 4   brass_light   brass         brass_dark    gold
 v 8   leather       leather_dark  wood_dark     wood
 v 12  bone          black         red           wire (silver wire, 1 px stripes along v)

palette_2.png (texture "#1"; firearms, ammunition, grappling hook, F8b):

    u:   0                  4               8               12
 v 0   gunmetal_light     gunmetal        gunmetal_dark   flint
 v 4   walnut_light       walnut          walnut_dark     rope_coil (rope, 1 px stripes along v)
 v 8   lead_light         lead            lead_dark       cast_iron_light (highlight)
 v 12  cast_iron          cast_iron_dark  rope            rope_dark

palette_3.png (texture "#2"; provisions: rum, hardtack, lime, salt pork, salted fish, F8d):

    u:   0                  4                8                12
 v 0   lime_light         lime             lime_dark        leaf
 v 4   biscuit_light      biscuit_dark     meat_light       meat_dark
 v 8   fat                rind             fish_back        fish_belly
 v 12  glass_dark         glass_highlight  cork             paper

The striped patches alternate a light and a dark row; a face whose v runs along a grip or a coil shows the winding.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures/item"

WIRE = ((214, 218, 226), (112, 116, 128))
ROPE_COIL = ((196, 164, 108), (128, 98, 58))

SHEETS = {
    "palette.png": [
        [("steel_light", (222, 226, 232)), ("steel", (168, 174, 186)), ("steel_dark", (108, 114, 128)), ("iron_dark", (66, 66, 74))],
        [("brass_light", (238, 204, 112)), ("brass", (198, 150, 58)), ("brass_dark", (134, 94, 34)), ("gold", (252, 216, 78))],
        [("leather", (122, 74, 40)), ("leather_dark", (78, 44, 24)), ("wood_dark", (64, 42, 26)), ("wood", (122, 88, 54))],
        [("bone", (228, 218, 188)), ("black", (26, 24, 28)), ("red", (150, 36, 36)), ("wire", WIRE)],
    ],
    "palette_2.png": [
        [("gunmetal_light", (104, 112, 130)), ("gunmetal", (64, 70, 86)), ("gunmetal_dark", (38, 42, 54)), ("flint", (92, 88, 84))],
        [("walnut_light", (152, 98, 58)), ("walnut", (108, 64, 36)), ("walnut_dark", (70, 40, 22)), ("rope_coil", ROPE_COIL)],
        [("lead_light", (156, 158, 168)), ("lead", (114, 116, 126)), ("lead_dark", (78, 80, 90)), ("cast_iron_light", (118, 118, 126))],
        [("cast_iron", (56, 56, 62)), ("cast_iron_dark", (34, 34, 40)), ("rope", (196, 164, 108)), ("rope_dark", (138, 108, 64))],
    ],
    "palette_3.png": [
        [("lime_light", (172, 214, 86)), ("lime", (110, 162, 48)), ("lime_dark", (64, 106, 30)), ("leaf", (54, 122, 56))],
        [("biscuit_light", (228, 198, 140)), ("biscuit_dark", (178, 138, 84)), ("meat_light", (222, 130, 128)), ("meat_dark", (168, 72, 78))],
        [("fat", (242, 236, 224)), ("rind", (150, 92, 58)), ("fish_back", (86, 104, 124)), ("fish_belly", (198, 206, 212))],
        [("glass_dark", (52, 78, 58)), ("glass_highlight", (122, 156, 124)), ("cork", (176, 134, 86)), ("paper", (228, 214, 170))],
    ],
}


def sheet(patches):
    img = Image.new("RGBA", (16, 16))
    for row, cells in enumerate(patches):
        for col, (_, colour) in enumerate(cells):
            for dy in range(4):
                for dx in range(4):
                    c = colour[dy % 2] if isinstance(colour[0], tuple) else colour
                    img.putpixel((col * 4 + dx, row * 4 + dy), c + (255,))
    return img


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, patches in SHEETS.items():
        sheet(patches).save(OUT_DIR / name, format="PNG", optimize=False)


if __name__ == "__main__":
    main()
