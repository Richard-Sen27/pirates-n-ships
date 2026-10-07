#!/usr/bin/env python3
"""Placeholder entity skins on the humanoid rig of work package M1 (rig contract: art/README.md, "Entities").

Run (from the repository root, with the venv of tools/gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_entity_textures.py pirate sailor navy_soldier navy_officer   # only these

Output: common/src/main/resources/assets/pirates_n_ships/textures/entity/<name>.png, 64x64. Every texture here is now
a placeholder kept for regeneration on demand: M2's Blockbench skin owns crew_member.png, and M3-art's Blockbench skins
(art/models/entity/seafarer_skins.js) own pirate, sailor, navy_soldier and navy_officer, so a run without names writes
nothing.
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


# --- mob variants (work package M3): the same rig and skin layout, own palettes ----------------------------------
# Added after sailor() so its output stays byte-identical (the new colours don't touch its pixels or its random seed).

COLORS.update({
    "skin_tan_d": (140, 92, 62, 255), "skin_tan": (176, 122, 86, 255),
    "hair_black": (30, 26, 28, 255), "hair_grey": (176, 172, 168, 255), "hair_grey_d": (132, 128, 126, 255),
    "coat_d": (40, 30, 26, 255), "coat": (62, 46, 38, 255), "coat_l": (86, 66, 52, 255),
    "sash_d": (110, 22, 30, 255), "sash": (150, 34, 40, 255),
    "breeches_d": (84, 74, 62, 255), "breeches": (108, 96, 80, 255),
    "boot_d": (22, 18, 16, 255), "boot": (40, 32, 28, 255),
    "cap_d": (36, 62, 96, 255), "cap": (52, 86, 128, 255),
    "canvas_d": (138, 112, 76, 255), "canvas": (164, 136, 96, 255),
    "navy_d": (22, 34, 72, 255), "navy": (34, 52, 104, 255),
    "facing": (156, 30, 34, 255),
    "belt_white": (240, 238, 230, 255), "belt_shade": (204, 200, 190, 255),
    "hat_d": (18, 16, 20, 255), "hat": (32, 30, 36, 255),
})

# Jacket layer of the body (outer skin layer): coat tails hang over the hips there.
JACKET = (16, 32, 8, 12, 4)


def face(s, skin, skin_d, hair, hair_d, beard=None, patch=False):
    """A mob's head: skin, hair on top, back and upper sides, brows, eyes, nose, mouth; optional beard and eyepatch."""
    s.fill_box(HEAD, skin, skin_d, 0.06)
    f = s.faces(HEAD)
    s.rect(*f["top"], hair)
    s.speckle(*f["top"], hair_d, 0.3)
    s.band(HEAD, 0, 2, hair, hair_d, 0.3)
    bx, by, bw, _ = f["back"]
    s.rect(bx, by, bw, 5, hair)
    s.speckle(bx, by, bw, 5, hair_d, 0.3)
    fx, fy = f["front"][0], f["front"][1]
    s.rect(fx + 1, fy + 3, 2, 1, hair_d)        # brows
    s.rect(fx + 5, fy + 3, 2, 1, hair_d)
    s.px(fx + 1, fy + 4, "white")               # eyes
    s.px(fx + 2, fy + 4, "eye")
    s.px(fx + 5, fy + 4, "eye")
    s.px(fx + 6, fy + 4, "white")
    s.rect(fx + 3, fy + 5, 2, 1, skin_d)        # nose
    if beard:
        s.rect(fx + 1, fy + 6, 6, 2, beard)
        s.speckle(fx + 1, fy + 6, 6, 2, hair_d, 0.3)
        s.px(fx, fy + 5, beard)
        s.px(fx + 7, fy + 5, beard)
        for side in ("right", "left"):          # sideburns
            x, y, w, _ = f[side]
            s.rect(x, y + 2, w, 4, beard)
            s.speckle(x, y + 2, w, 4, skin, 0.4)
    s.rect(fx + 3, fy + 6, 2, 1, "red_d")       # mouth
    if patch:                                   # over one eye, the strap runs around the head
        s.rect(fx + 1, fy + 3, 2, 3, "black")
        s.rect(fx + 3, fy + 3, 5, 1, "black")
        for side in ("right", "left", "back"):
            x, y, w, _ = f[side]
            s.rect(x, y + 3, w, 1, "black")


def hat_layer(s, c, shade, rows):
    """A hat or cap painted on the hat layer: the top and the upper rows of the four sides."""
    t = s.faces(HAT)["top"]
    s.rect(*t, c)
    s.speckle(*t, shade, 0.15)
    s.band(HAT, 0, rows, c, shade, 0.15)


def boots(s, c, shade, rows):
    for leg in (RIGHT_LEG, LEFT_LEG):
        s.band(leg, 12 - rows, rows, c, shade, 0.15)
        s.rect(*s.faces(leg)["bottom"], c)


def coat_arms(s, c, shade, cuff, skin, skin_d):
    """Full coat sleeves with a cuff, bare hands on the last two rows."""
    for arm in (RIGHT_ARM, LEFT_ARM):
        s.fill_box(arm, c, shade, 0.1)
        s.band(arm, 8, 2, cuff)
        s.band(arm, 10, 2, skin, skin_d, 0.1)
        s.rect(*s.faces(arm)["bottom"], skin)


def pirate():
    """Pirate: tanned, black beard, eyepatch, dark red bandana, dark open coat over a white shirt, red sash, boots."""
    s = Sheet("pirate")
    face(s, "skin_tan", "skin_tan_d", "hair_black", "black", beard="hair_black", patch=True)
    hat_layer(s, "sash_d", "black", 3)
    hx, hy, _, _ = s.faces(HAT)["back"]
    s.rect(hx + 3, hy + 3, 2, 3, "sash_d")      # knot and tails
    s.px(hx + 2, hy + 5, "sash")
    for (x, y, w, _) in s.sides(HAT):
        for xx in range(x + 1, x + w, 3):
            s.px(xx, y + 1, "bone")
    s.fill_box(BODY, "coat", "coat_d", 0.15)
    bfx, bfy, _, _ = s.faces(BODY)["front"]
    s.rect(bfx + 3, bfy, 2, 8, "white")         # shirt between the open coat fronts
    s.speckle(bfx + 3, bfy, 2, 8, "shirt_d", 0.2)
    s.rect(bfx + 3, bfy, 2, 2, "skin_tan")
    for row in range(bfy, bfy + 12):            # coat edges
        s.px(bfx + 2, row, "coat_l")
        s.px(bfx + 5, row, "coat_l")
    s.band(BODY, 7, 2, "sash", "sash_d", 0.25)
    s.band(BODY, 9, 1, "brown", "wood_d", 0.2)
    s.rect(bfx + 1, bfy + 9, 2, 1, "gold")
    for (x, y, w, _) in s.sides(JACKET):
        s.rect(x, y + 10, w, 2, "coat_d")
    coat_arms(s, "coat", "coat_d", "coat_l", "skin_tan", "skin_tan_d")
    for leg in (RIGHT_LEG, LEFT_LEG):
        s.fill_box(leg, "breeches", "breeches_d", 0.15)
    boots(s, "boot", "boot_d", 6)
    return s


def deckhand():
    """Sailor mob: the crew member's cut in other colours (red stripes, blue knit cap, canvas trousers, bare feet)."""
    s = Sheet("sailor")
    face(s, "skin", "skin_d", "hair", "hair_d", beard="hair")
    hat_layer(s, "cap", "cap_d", 3)
    for (x, y, w, _) in s.sides(HAT):           # rolled brim
        s.rect(x, y + 2, w, 1, "cap_d")
    s.fill_box(BODY, "white", "shirt_d", 0.08)
    for row in range(1, 10, 3):
        s.band(BODY, row, 1, "red")
    s.band(BODY, 10, 2, "brown", "wood_d", 0.2)
    bfx, bfy, _, _ = s.faces(BODY)["front"]
    s.rect(bfx + 3, bfy, 2, 2, "skin")
    for arm in (RIGHT_ARM, LEFT_ARM):
        s.fill_box(arm, "skin", "skin_d", 0.06)
        s.rect(*s.faces(arm)["top"], "white")
        s.band(arm, 0, 4, "white", "shirt_d", 0.08)
        s.band(arm, 1, 1, "red")
        s.band(arm, 4, 1, "shirt_d")
    for leg in (RIGHT_LEG, LEFT_LEG):
        s.fill_box(leg, "canvas", "canvas_d", 0.15)
        s.band(leg, 10, 2, "skin", "skin_d", 0.1)
        s.rect(*s.faces(leg)["bottom"], "skin_d")
    return s


def navy_body(s, trim):
    """Blue coat with a coloured collar and cuffs, white waistcoat with gold buttons, white breeches, black boots."""
    s.fill_box(BODY, "navy", "navy_d", 0.12)
    bfx, bfy, _, _ = s.faces(BODY)["front"]
    s.rect(bfx + 2, bfy, 4, 10, "white")
    s.speckle(bfx + 2, bfy, 4, 10, "belt_shade", 0.15)
    for row in range(bfy + 2, bfy + 10, 2):
        s.px(bfx + 3, row, "gold")
    s.rect(bfx + 2, bfy, 4, 1, trim)
    s.band(BODY, 11, 1, "navy_d")
    for (x, y, w, _) in s.sides(JACKET):
        s.rect(x, y + 10, w, 2, "navy_d")
    coat_arms(s, "navy", "navy_d", trim, "skin", "skin_d")
    for leg in (RIGHT_LEG, LEFT_LEG):
        s.fill_box(leg, "white", "belt_shade", 0.1)
    boots(s, "boot", "boot_d", 5)


def navy_soldier():
    """Navy soldier: blue coat with red facings, white cross belts, grey queue, black tricorn with a white edge."""
    s = Sheet("navy_soldier")
    face(s, "skin", "skin_d", "hair_grey", "hair_grey_d")
    hx, hy, _, _ = s.faces(HEAD)["back"]
    s.rect(hx + 3, hy + 5, 2, 3, "black")       # queue ribbon
    navy_body(s, "facing")
    for name in ("front", "back"):              # cross belts with a plate where they cross
        x, y, _, _ = s.faces(BODY)[name]
        for i in range(8):
            s.px(x + i, y + i, "belt_white")
            s.px(x + 7 - i, y + i, "belt_white")
        s.rect(x + 3, y + 3, 2, 2, "steel_l")
    hat_layer(s, "hat", "hat_d", 3)             # tricorn
    for (x, y, w, _) in s.sides(HAT):
        s.rect(x, y + 2, w, 1, "belt_white")
        s.px(x + w // 2, y, "hat_d")
    fx, fy, _, _ = s.faces(HAT)["front"]
    s.px(fx + 1, fy + 1, "facing")              # cockade
    return s


def navy_officer():
    """Navy officer: blue coat with gold trim and epaulettes, sword belt, black bicorne with a gold edge."""
    s = Sheet("navy_officer")
    face(s, "skin_l", "skin", "hair_grey", "hair_grey_d")
    navy_body(s, "gold")
    bfx, bfy, _, _ = s.faces(BODY)["front"]
    for row in range(bfy, bfy + 12):
        s.px(bfx + 1, row, "gold")
        s.px(bfx + 6, row, "gold")
    s.band(BODY, 8, 1, "gold_d")
    for arm in (RIGHT_ARM, LEFT_ARM):           # epaulettes
        s.rect(*s.faces(arm)["top"], "gold")
        s.band(arm, 0, 1, "gold_l")
        s.band(arm, 1, 1, "gold_d")
    hat_layer(s, "hat", "hat_d", 3)             # bicorne, worn athwart
    for (x, y, w, _) in s.sides(HAT):
        s.rect(x, y + 2, w, 1, "gold")
    for side in ("right", "left"):
        x, y, w, _ = s.faces(HAT)[side]
        s.rect(x + 1, y, w - 2, 2, "hat_d")
    fx, fy, _, _ = s.faces(HAT)["front"]
    s.rect(fx + 3, fy, 2, 2, "gold_l")
    return s


TEXTURES = {"crew_member": sailor, "pirate": pirate, "sailor": deckhand,
            "navy_soldier": navy_soldier, "navy_officer": navy_officer}

# Written only when named on the command line: the crew member's skin is the Blockbench texture of M2 and the four
# mob skins are the Blockbench textures of M3-art; these placeholders must not overwrite them.
NOT_BY_DEFAULT = {"crew_member", "pirate", "sailor", "navy_soldier", "navy_officer"}


def main():
    """Writes the placeholder textures (all but NOT_BY_DEFAULT), or only those named on the command line."""
    out = TEX / "entity"
    out.mkdir(parents=True, exist_ok=True)
    for name in sys.argv[1:] or [n for n in TEXTURES if n not in NOT_BY_DEFAULT]:
        TEXTURES[name]().img.save(out / f"{name}.png", format="PNG", optimize=False)
        print(f"wrote  entity/{name}")


if __name__ == "__main__":
    main()
