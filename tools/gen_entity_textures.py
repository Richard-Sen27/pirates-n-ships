#!/usr/bin/env python3
"""Entity skins on the humanoid rig (rig contract: art/README.md, "Entities").

Run (from the repository root, with any Python that has Pillow, e.g. the venv of tools/gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_entity_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/entity/<name>.png, 64x64.
The layout is the vanilla player skin layout (Steve, wide arms): every contract cube of the rig uses Minecraft box UV at
the same offset as the player model, so a variant mob (pirate, sailor, navy soldier, officer) is a new texture painted
on this sheet, and any 64x64 player skin works as a test texture. The second skin layer (hat, jacket, sleeves,
trousers) is transparent unless painted. Deterministic: same input, same bytes. Reuses the palette of
gen_placeholder_textures.py (imported, not edited) and adds skin, hair and cloth colours.

This script is the source of crew_member.png (work package M2); the Blockbench project
art/models/entity/crew_member.bbmodel embeds a copy of its output (re-import it there after a change). The sailor
details of the model (bandana knot, neckerchief, belt, knife, rolled sleeves and trousers, toes, earring) are extra
cubes with per-face UVs onto the 2x2 colour patches in the unused strip u 56..63, v 16..47 of the skin layout
(PATCHES below). Never move a patch: the model's UVs point at them. New colours go into the free slots after the
last one.
"""
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import P, TEX  # noqa: E402

COLORS = dict(P)
COLORS.update({
    "skin_d": (160, 108, 76, 255), "skin": (198, 142, 102, 255), "skin_l": (220, 170, 130, 255),
    "hair_d": (46, 30, 20, 255), "hair": (74, 50, 32, 255),
    "eye": (44, 70, 120, 255), "mouth": (118, 72, 56, 255),
    "shirt_l": (242, 240, 230, 255), "shirt": (228, 224, 210, 255), "shirt_d": (190, 186, 172, 255),
    "stripe": (40, 62, 116, 255), "stripe_d": (30, 46, 88, 255),
    "trouser_l": (110, 104, 86, 255), "trouser": (88, 84, 70, 255), "trouser_d": (66, 62, 52, 255),
    "patch": (128, 92, 60, 255),
    "bandana_l": (200, 58, 48, 255), "bandana": (172, 40, 36, 255), "bandana_d": (120, 26, 26, 255),
    "kerchief": (214, 168, 52, 255), "kerchief_d": (166, 122, 34, 255),
    "belt": (92, 58, 34, 255), "belt_d": (62, 38, 22, 255),
    "brass": (214, 170, 72, 255), "brass_d": (150, 112, 40, 255),
    "sheath": (52, 34, 22, 255), "handle": (140, 96, 56, 255),
})

# Box-UV offsets of the vanilla player skin (wide arms): (u, v, width, height, depth).
HEAD = (0, 0, 8, 8, 8)
HAT = (32, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
RIGHT_ARM = (40, 16, 4, 12, 4)
LEFT_ARM = (32, 48, 4, 12, 4)
RIGHT_LEG = (0, 16, 4, 12, 4)
LEFT_LEG = (16, 48, 4, 12, 4)

# Solid 2x2 colour patches for the detail cubes, in the unused strip u 56..63, v 16..47 (4 per row).
# Patch i covers u = 56 + 2 * (i % 4), v = 16 + 2 * (i // 4); the model maps a face to [u + 0.5, v + 0.5], size 1x1.
PATCHES = [
    "bandana", "bandana_d", "kerchief", "kerchief_d",
    "belt", "belt_d", "brass", "brass_d",
    "sheath", "handle", "shirt", "shirt_d",
    "trouser", "trouser_d", "skin", "skin_d",
    "gold", "stripe",
]


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

    def patches(self):
        for i, c in enumerate(PATCHES):
            self.rect(56 + 2 * (i % 4), 16 + 2 * (i // 4), 2, 2, c)


def head(s):
    s.fill_box(HEAD, "skin", "skin_d", 0.05)
    f = s.faces(HEAD)
    s.rect(*f["top"], "hair")
    s.speckle(*f["top"], "hair_d", 0.3)
    s.band(HEAD, 0, 3, "hair", "hair_d", 0.3)
    for side in ("right", "left"):              # sideburns next to the face, an ear in the middle
        x, y, w, _ = f[side]
        front_col = x + w - 1 if side == "right" else x
        s.rect(front_col, y + 3, 1, 3, "hair")
        s.rect(x + 3, y + 4, 2, 2, "skin_d")
        s.px(x + 3, y + 4, "skin_l")
    bx, by, bw, _ = f["back"]
    s.rect(bx, by, bw, 6, "hair")
    s.speckle(bx, by, bw, 6, "hair_d", 0.35)
    s.rect(bx + 1, by + 6, bw - 2, 1, "hair_d")  # short neck hair
    fx, fy = f["front"][0], f["front"][1]
    s.rect(fx, fy, 8, 3, "hair")                 # under the bandana
    s.rect(fx + 1, fy + 3, 2, 1, "hair_d")       # brows
    s.rect(fx + 5, fy + 3, 2, 1, "hair_d")
    s.px(fx + 1, fy + 4, "white")                # eyes
    s.px(fx + 2, fy + 4, "eye")
    s.px(fx + 5, fy + 4, "eye")
    s.px(fx + 6, fy + 4, "white")
    s.px(fx + 3, fy + 4, "skin_l")               # nose
    s.rect(fx + 3, fy + 5, 2, 1, "skin_d")
    s.rect(fx + 2, fy + 6, 4, 1, "hair")         # moustache
    s.speckle(fx + 1, fy + 7, 6, 1, "skin_d", 0.6)    # stubble
    s.rect(fx + 3, fy + 7, 2, 1, "mouth")
    s.px(fx, fy + 5, "skin_d")
    s.px(fx + 7, fy + 5, "skin_d")


def bandana(s):
    """Hat layer: a red bandana with white dots over the top and the upper three rows; knot cubes sit at the back."""
    f = s.faces(HAT)
    s.rect(*f["top"], "bandana")
    s.speckle(*f["top"], "bandana_d", 0.15)
    s.band(HAT, 0, 3, "bandana", "bandana_d", 0.12)
    for (x, y, w, _) in s.sides(HAT):
        s.rect(x, y + 2, w, 1, "bandana_d")      # rolled lower edge
    tx, ty, tw, th = f["top"]
    for yy in range(ty + 1, ty + th, 3):
        for xx in range(tx + 1 + (yy % 2), tx + tw, 3):
            s.px(xx, yy, "white")
    for (x, y, w, _) in s.sides(HAT):
        for xx in range(x + 1, x + w, 3):
            s.px(xx, y + 1, "white")
    bx, by, _, _ = f["back"]
    s.rect(bx + 3, by + 3, 2, 1, "bandana_d")    # folds gathered under the knot


def shirt(s):
    """A Breton sailor shirt: off-white with navy stripes on every other row, an open V neck."""
    s.fill_box(BODY, "shirt", "shirt_d", 0.08)
    f = s.faces(BODY)
    for row in (2, 4, 6, 8):
        s.band(BODY, row, 1, "stripe")
        s.speckle(f["front"][0], f["front"][1] + row, 8, 1, "stripe_d", 0.15)
    s.band(BODY, 10, 2, "trouser", "trouser_d", 0.2)   # trouser top under the belt
    s.rect(*f["bottom"], "trouser")
    s.rect(*f["top"], "shirt_l")
    fx, fy = f["front"][0], f["front"][1]
    s.rect(fx + 2, fy, 4, 1, "skin")             # V neck
    s.rect(fx + 3, fy + 1, 2, 1, "skin_d")
    s.px(fx + 1, fy, "shirt_d")
    s.px(fx + 6, fy, "shirt_d")
    for (x, y, w, _) in (f["right"], f["left"]):  # shaded flanks
        s.speckle(x, y, w, 10, "shirt_d", 0.25)


def arms(s):
    """Striped sleeves down to a rolled cuff (a cube at rows 6..7), bare forearms and hands below."""
    for arm in (RIGHT_ARM, LEFT_ARM):
        f = s.faces(arm)
        s.fill_box(arm, "skin", "skin_d", 0.07)
        s.rect(*f["top"], "shirt_l")
        s.band(arm, 0, 7, "shirt", "shirt_d", 0.08)
        s.band(arm, 2, 1, "stripe")
        s.band(arm, 4, 1, "stripe")
        s.band(arm, 6, 1, "shirt_d")
        s.band(arm, 7, 1, "skin_d")              # shadow under the cuff
        s.band(arm, 11, 1, "skin_d", "skin", 0.4)  # knuckles
        s.rect(*f["bottom"], "skin_d")


def legs(s):
    """Canvas slops rolled below the knee (a cube at rows 7..8), bare shins and feet."""
    for i, leg in enumerate((RIGHT_LEG, LEFT_LEG)):
        f = s.faces(leg)
        s.fill_box(leg, "skin", "skin_d", 0.07)
        s.rect(*f["top"], "trouser")
        s.band(leg, 0, 8, "trouser", "trouser_d", 0.18)
        s.band(leg, 8, 1, "skin_d")              # shadow under the roll
        s.band(leg, 11, 1, "skin_d")             # toes and soles
        s.rect(*f["bottom"], "skin_d")
        fx, fy = f["front"][0], f["front"][1]
        if i == 1:                                # a patch on the left knee
            s.rect(fx + 1, fy + 4, 2, 2, "patch")
            s.px(fx + 1, fy + 4, "trouser_l")
        s.rect(fx + (3 if i == 0 else 0), fy, 1, 8, "trouser_d")  # inner seam


def sailor():
    s = Sheet("crew_member")
    head(s)
    bandana(s)
    shirt(s)
    arms(s)
    legs(s)
    s.patches()
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
