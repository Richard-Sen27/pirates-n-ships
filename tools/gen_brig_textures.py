"""Brig textures of work package E1b: the locked variant of the brig door's lower half.

Run (from the repository root, with the venv of tools/gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_brig_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/block/brig_door_bottom_locked.png
The unlocked door bottom from gen_placeholder_textures.py with a padlock over the latch. Deterministic.
Reuses that script's helpers (imported, not edited).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_placeholder_textures as base  # noqa: E402


def brig_door_bottom_locked():
    cv = base.brig_door(False)
    # Hasp plate across the latch, then a padlock hanging from it
    cv.rect(10, 1, 14, 3, "iron_d")
    cv.rect(11, 2, 13, 2, "steel")
    # Shackle (U-bow)
    cv.rect(10, 4, 10, 6, "steel_l")
    cv.rect(13, 4, 13, 6, "steel_l")
    cv.rect(11, 4, 12, 4, "steel_l")
    # Body
    cv.rect(9, 6, 14, 10, "gold")
    cv.rect(9, 10, 14, 10, "gold_d")
    cv.rect(9, 6, 9, 10, "gold_d")
    cv.rect(10, 6, 13, 6, "gold_l")
    # Keyhole
    cv.px(11, 8, "black"); cv.px(12, 8, "black"); cv.px(11, 9, "black")
    return cv


TEXTURES = {"brig_door_bottom_locked": brig_door_bottom_locked}


def main():
    for name, fn in TEXTURES.items():
        out = base.TEX / "block" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        fn().img.save(out, format="PNG", optimize=False)
        print(f"wrote  block/{name}")


if __name__ == "__main__":
    main()
