#!/usr/bin/env python3
"""Item palette textures for the hand-made 3D item models (design.md §4.8, work packages F8a, F8b, F8d, F8e, ART1b).

Run (from the repository root): tools/.venv/bin/python tools/gen_item_palette.py
Output: common/src/main/resources/assets/pirates_n_ships/textures/item/palette.png
        common/src/main/resources/assets/pirates_n_ships/textures/item/palette_2.png
        common/src/main/resources/assets/pirates_n_ships/textures/item/palette_3.png
        common/src/main/resources/assets/pirates_n_ships/textures/item/palette_4.png
        common/src/main/resources/assets/pirates_n_ships/textures/item/palette_5.png

Five 16x16 sheets of 4x4 px colour patches. Blockbench item models map every face to the patch it needs with UVs
(a face of a 1 px edge may use a 1 px sliver of a patch), so all item models share one set of colours.
Deterministic: same input, same bytes. Never move a patch: existing models point at these UVs.

Why a second sheet instead of a wider one: a model's UVs are fractions of the sprite (FaceBakery maps uv / 16 onto
the sprite's full width and height, whatever its pixel size). Growing palette.png to 32x16 would halve the meaning
of every u in the sword models and move all their patches. A new colour therefore goes into a free cell of
palette_2.png (texture "#1" in a model), palette_3.png ("#2") or palette_4.png ("#3"). palette_4 is full: the next
new colour needs a new sheet palette_5.png.

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

palette_4.png (texture "#3"; trade goods: cloth, spices, tobacco, F8e):

    u:   0                  4                8                12
 v 0   cloth_light        cloth_dark       cloth_weave      cloth_shadow
 v 4   burlap_light       burlap_dark      cord             twine
 v 8   spice_red          spice_orange     spice_gold       spice_dark
 v 12  tobacco_light      tobacco_dark     midrib           burlap_weave

palette_5.png (texture "#4"; flag bundles and the map tile, ART1b; kraken beak, ART1c):

    u:   0                  4                8                12
 v 0   navy_light         navy             navy_dark        flag_white
 v 4   flag_white_shade   flag_red         flag_red_dark    flag_black
 v 8   flag_black_shade   flag_black_light map_sea          map_sea_dark
 v 12  map_land           map_ink          horn             horn_light

The navy, merchant and Jolly Roger colours follow the flown cloth (tools/gen_flag_textures.py: blue with a white
cross, white with a red stripe, black with a bone skull); horn and horn_light
(the two former spare cells) colour the kraken beak (ART1c). palette_5 is full: a new colour needs palette_6.png.

cloth_weave (undyed cloth, lighter and light rows) and burlap_weave (light and darker sacking rows) are striped.
cloth_shadow colours the cloth bolt's end rings. Spare, not used by the F8e models: spice_dark.

The striped patches alternate a light and a dark row; a face whose v runs along a grip or a coil shows the winding.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures/item"

WIRE = ((214, 218, 226), (112, 116, 128))
ROPE_COIL = ((196, 164, 108), (128, 98, 58))
CLOTH_WEAVE = ((246, 242, 228), (226, 218, 196))
BURLAP_WEAVE = ((184, 150, 98), (156, 124, 80))

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
    "palette_4.png": [
        [("cloth_light", (226, 218, 196)), ("cloth_dark", (178, 168, 142)), ("cloth_weave", CLOTH_WEAVE), ("cloth_shadow", (140, 130, 108))],
        [("burlap_light", (184, 150, 98)), ("burlap_dark", (128, 98, 60)), ("cord", (96, 66, 40)), ("twine", (206, 184, 128))],
        [("spice_red", (168, 48, 30)), ("spice_orange", (214, 112, 38)), ("spice_gold", (226, 174, 56)), ("spice_dark", (112, 36, 24))],
        [("tobacco_light", (150, 100, 50)), ("tobacco_dark", (98, 62, 30)), ("midrib", (198, 164, 104)), ("burlap_weave", BURLAP_WEAVE)],
    ],
    "palette_5.png": [
        [("navy_light", (82, 120, 188)), ("navy", (52, 84, 150)), ("navy_dark", (30, 50, 98)), ("flag_white", (240, 236, 224))],
        [("flag_white_shade", (204, 198, 182)), ("flag_red", (180, 52, 44)), ("flag_red_dark", (122, 32, 30)), ("flag_black", (42, 40, 46))],
        [("flag_black_shade", (22, 20, 24)), ("flag_black_light", (70, 68, 76)), ("map_sea", (124, 164, 180)), ("map_sea_dark", (84, 124, 146))],
        [("map_land", (178, 172, 110)), ("map_ink", (58, 44, 34)), ("horn", (72, 54, 48)), ("horn_light", (172, 146, 116))],
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
