"""Hull repair textures of work package G4: the hull patch block.

Run (from the repository root, with the venv of tools/gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_hull_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/block/hull_patch.png
Rough planks nailed over a hole: the placeholder planks of gen_placeholder_textures.py, with black pitch (tar) run
into the seams and a tar stripe across the middle, and iron nail heads at the board ends. Deterministic. Reuses that
script's helpers (imported, not edited). The bilge pump uses vanilla textures (its model is an element model in
datagen), so it has none here.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_placeholder_textures as base  # noqa: E402


def hull_patch():
    cv = base.planks("hull_patch")
    rnd = base.random.Random("pns:hull_patch_tar")
    # pitch in the seams between the boards (the planks helper draws them at y 4, 8, 12)
    for y in (4, 12):
        cv.rect(0, y, 15, y, "wood_d")
        for x in range(16):
            if rnd.random() < 0.5:
                cv.px(x, y, "black")
    # a tar stripe across the middle, a little ragged at its edges
    cv.rect(0, 7, 15, 8, "black")
    for x in range(16):
        if rnd.random() < 0.3:
            cv.px(x, 6, "black")
        if rnd.random() < 0.3:
            cv.px(x, 9, "black")
    # nail heads at both ends of every board
    for y in (2, 6, 10, 14):
        cv.px(1, y, "steel")
        cv.px(14, y, "steel")
    return cv


TEXTURES = {"hull_patch": hull_patch}


def main():
    for name, fn in TEXTURES.items():
        out = base.TEX / "block" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        fn().img.save(out, format="PNG", optimize=False)
        print(f"wrote  block/{name}")


if __name__ == "__main__":
    main()
