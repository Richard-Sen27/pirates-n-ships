#!/usr/bin/env python3
"""Placeholder textures (16x16 pixel art) of the law package (work package E1a): the bounty proof.

Reuses the canvas, palette and protection list of gen_placeholder_textures.py, so the style matches.
Run (from the repository root, with the venv from gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_law_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/item/<name>.png
Deterministic. Names listed in PROTECTED / tools/protected_textures.txt are never overwritten.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import TEX, Canvas, noise, protected  # noqa: E402


def bounty_proof():
    """A rolled death warrant: parchment sheet, black lines, red wax seal."""
    cv = Canvas()
    cv.rect(3, 2, 12, 13, "tan_l")
    noise(cv, "bounty_proof", ["tan"], chance=0.18, area=(3, 2, 12, 13))
    cv.rect(2, 1, 13, 2, "tan")        # top roll
    cv.rect(2, 13, 13, 14, "tan")      # bottom roll
    cv.px(2, 1, "brown")
    cv.px(13, 14, "brown")
    for y in (4, 6, 8):
        cv.line(5, y, 10, y, "black")
    cv.line(5, 10, 8, 10, "black")
    cv.disc(10, 11, 1.8, "red")
    cv.px(10, 11, "red_d")
    cv.px(9, 10, "pink")
    return cv.outline()


ITEMS = {"bounty_proof": bounty_proof}


def main():
    skip = protected()
    for name, fn in ITEMS.items():
        if name in skip:
            print(f"skip   item/{name} (protected)")
            continue
        out = TEX / "item" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        fn().img.save(out, format="PNG", optimize=False)
        print(f"wrote  item/{name}")


if __name__ == "__main__":
    main()
