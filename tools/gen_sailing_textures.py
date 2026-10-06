"""Placeholder textures for the sail blocks and the sail winch (work package D3a, spike 3).

Reuses the canvas and palette of gen_placeholder_textures.py (not edited). Usage:

    tools/.venv/bin/python tools/gen_sailing_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/block/<sail>_<trim>.png and sail_winch.png.
Each sail texture is opaque (the plate models use the default solid render type): a dark yard along the top, then
canvas for the set part of the sail and dark rigging background for the rest. Furled: a rolled bundle under the yard.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import Canvas, TEX  # noqa: E402

SAILS = {
    "small_square_sail": ("white", "bone"),
    "large_square_sail": ("tan_l", "tan"),
    "fore_and_aft_sail": ("white", "grey"),
}


def sail(name, trim):
    light, dark = SAILS[name]
    c = Canvas("wood_d")
    c.rect(0, 0, 15, 1, "wood")  # yard
    if trim == "furled":
        c.rect(1, 2, 14, 3, dark)
        c.rect(1, 2, 14, 2, light)
    else:
        bottom = 8 if trim == "half" else 15
        if name == "fore_and_aft_sail":
            for y in range(2, bottom + 1):  # triangle-ish: wider toward the foot
                w = min(15, 4 + (y - 2) * 12 // 13)
                c.rect(1, y, w, y, light)
        else:
            c.rect(1, 2, 14, bottom, light)
            for x in (4, 8, 12):  # seams
                c.rect(x, 2, x, bottom, dark)
    return c


def winch():
    c = Canvas("plank")
    c.border("wood_d")
    c.disc(7, 7, 4, "iron")
    c.disc(7, 7, 2, "iron_d")
    c.rect(2, 7, 13, 8, "tan")  # rope
    return c


def main():
    out = TEX / "block"
    out.mkdir(parents=True, exist_ok=True)
    for name in SAILS:
        for trim in ("furled", "half", "full"):
            sail(name, trim).img.save(out / f"{name}_{trim}.png", format="PNG", optimize=False)
    winch().img.save(out / "sail_winch.png", format="PNG", optimize=False)


if __name__ == "__main__":
    main()
