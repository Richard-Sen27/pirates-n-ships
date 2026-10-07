#!/usr/bin/env python3
"""Placeholder entity skins on the humanoid rig of work package M1 (rig contract: art/README.md, "Entities").

Run (from the repository root, with the venv of tools/gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_entity_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/entity/<name>.png, 64x64.
The layout is the vanilla player skin layout (Steve, wide arms): every cube of the rig uses Minecraft box UV at the
same offset as the player model, so a variant mob (pirate, sailor, navy soldier, officer) is a new texture painted on
this sheet, and any 64x64 player skin works as a test texture. The second skin layer (hat, jacket, sleeves, trousers)
is transparent unless painted. Deterministic: same input, same bytes. Reuses the palette of gen_placeholder_textures.py
(imported, not edited) and adds skin, hair and cloth colours.
"""
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import P, TEX  # noqa: E402

COLORS = dict(P)
COLORS.update({
    "skin_d": (168, 116, 84, 255), "skin": (204, 150, 112, 255), "skin_l": (226, 178, 140, 255),
    "hair_d": (54, 36, 24, 255), "hair": (82, 56, 36, 255),
    "eye": (44, 70, 120, 255),
    "shirt_d": (196, 192, 180, 255),
    "stripe": (52, 78, 136, 255),
    "trouser_d": (58, 66, 84, 255), "trouser": (78, 88, 110, 255),
    "shoe": (46, 32, 24, 255),
    "bandana_d": (130, 28, 28, 255), "bandana": (178, 40, 36, 255),
})

# Box-UV offsets of the vanilla player skin (wide arms): (u, v, width, height, depth).
HEAD = (0, 0, 8, 8, 8)
HAT = (32, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
RIGHT_ARM = (40, 16, 4, 12, 4)
LEFT_ARM = (32, 48, 4, 12, 4)
RIGHT_LEG = (0, 16, 4, 12, 4)
LEFT_LEG = (16, 48, 4, 12, 4)


class Sheet:
    def __init__(self, name):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rnd = random.Random("pns:entity:" + name)

    def px(self, x, y, c):
        self.img.putpixel((x, y), COLORS[c])

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.px(xx, yy, c)

    def speckle(self, x, y, w, h, c, chance):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if self.rnd.random() < chance:
                    self.px(xx, yy, c)

    def faces(self, box):
        """The six face rectangles (x, y, w, h) of a box-UV cube: top, bottom, right, front, left, back."""
        u, v, w, h, d = box
        return {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
        }

    def sides(self, box):
        f = self.faces(box)
        return [f["right"], f["front"], f["left"], f["back"]]

    def fill_box(self, box, c, shade=None, chance=0.12):
        for (x, y, w, h) in self.faces(box).values():
            self.rect(x, y, w, h, c)
            if shade:
                self.speckle(x, y, w, h, shade, chance)

    def band(self, box, row0, rows, c, shade=None, chance=0.12):
        """Paint rows row0 .. row0+rows-1 (from the top of the side faces) around the four sides of a cube."""
        for (x, y, w, h) in self.sides(box):
            self.rect(x, y + row0, w, rows, c)
            if shade:
                self.speckle(x, y + row0, w, rows, shade, chance)


def sailor():
    s = Sheet("crew_member")

    # head: skin, hair on top, back and the upper sides, a face on the front
    s.fill_box(HEAD, "skin", "skin_d", 0.06)
    f = s.faces(HEAD)
    s.rect(*f["top"], "hair")
    s.speckle(*f["top"], "hair_d", 0.3)
    s.band(HEAD, 0, 2, "hair", "hair_d", 0.3)
    bx, by, bw, bh = f["back"]
    s.rect(bx, by, bw, 5, "hair")
    s.speckle(bx, by, bw, 5, "hair_d", 0.3)
    fx, fy = f["front"][0], f["front"][1]
    s.rect(fx + 1, fy + 3, 2, 1, "hair_d")      # brows
    s.rect(fx + 5, fy + 3, 2, 1, "hair_d")
    s.px(fx + 1, fy + 4, "white")               # eyes
    s.px(fx + 2, fy + 4, "eye")
    s.px(fx + 5, fy + 4, "eye")
    s.px(fx + 6, fy + 4, "white")
    s.rect(fx + 3, fy + 5, 2, 1, "skin_d")      # nose
    s.rect(fx + 2, fy + 6, 4, 2, "skin_d")      # stubble and mouth
    s.speckle(fx + 1, fy + 6, 6, 2, "hair", 0.35)
    s.rect(fx + 3, fy + 6, 2, 1, "red_d")

    # hat layer: a red bandana over the top and the upper rows, knot at the back
    s.rect(*s.faces(HAT)["top"], "bandana")
    s.speckle(*s.faces(HAT)["top"], "bandana_d", 0.2)
    s.band(HAT, 0, 3, "bandana", "bandana_d", 0.2)
    hx, hy, _, _ = s.faces(HAT)["back"]
    s.rect(hx + 3, hy + 3, 2, 2, "bandana_d")   # knot
    s.px(hx + 3, hy + 5, "bandana")
    s.px(hx + 4, hy + 5, "bandana_d")
    for (x, y, w, _) in s.sides(HAT):           # a white dot pattern on the band
        for xx in range(x + 1, x + w, 3):
            s.px(xx, y + 1, "white")

    # shirt: white with blue sailor stripes, a belt with a buckle at the bottom
    s.fill_box(BODY, "white", "shirt_d", 0.08)
    for row in range(1, 10, 3):
        s.band(BODY, row, 1, "stripe")
    s.band(BODY, 10, 2, "brown", "wood_d", 0.2)
    bfx, bfy, _, _ = s.faces(BODY)["front"]
    s.rect(bfx + 3, bfy + 10, 2, 2, "gold")
    s.px(bfx + 3, bfy + 2, "skin")              # open collar
    s.px(bfx + 4, bfy + 2, "skin")
    s.rect(bfx + 3, bfy, 2, 2, "skin")

    # arms: striped sleeves to the elbow, bare forearms and hands
    for arm in (RIGHT_ARM, LEFT_ARM):
        s.fill_box(arm, "skin", "skin_d", 0.06)
        s.rect(*s.faces(arm)["top"], "white")
        s.band(arm, 0, 5, "white", "shirt_d", 0.08)
        s.band(arm, 1, 1, "stripe")
        s.band(arm, 4, 1, "stripe")
        s.band(arm, 5, 1, "shirt_d")            # rolled-up cuff

    # legs: canvas trousers, shoes
    for leg in (RIGHT_LEG, LEFT_LEG):
        s.fill_box(leg, "trouser", "trouser_d", 0.15)
        s.band(leg, 9, 3, "shoe", "black", 0.2)
        s.rect(*s.faces(leg)["bottom"], "shoe")
    return s


TEXTURES = {"crew_member": sailor}


def main():
    out = TEX / "entity"
    out.mkdir(parents=True, exist_ok=True)
    for name, fn in TEXTURES.items():
        fn().img.save(out / f"{name}.png", format="PNG", optimize=False)
        print(f"wrote  entity/{name}")


if __name__ == "__main__":
    main()
