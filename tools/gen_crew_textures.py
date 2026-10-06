#!/usr/bin/env python3
"""Fill-level textures of the water barrel top (work package E2: pantry and water barrel).

Run (from the repository root, with the venv of tools/gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_crew_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/block/water_barrel_top_fill<0..3>.png
(fill 4, a full barrel, keeps the existing water_barrel_top.png). Deterministic, reuses the C8 helpers.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import TEX, barrel_top  # noqa: E402


def water_barrel_top_fill(level):
    """level 0 = empty (dark inside), 1..3 = a growing pool of water."""
    cv = barrel_top(f"water_barrel_top_fill{level}", "wood_d")
    radius = {0: 0, 1: 1.5, 2: 2.5, 3: 3.5}[level]
    if radius:
        cv.disc(7.5, 7.5, radius + 1, "blue_d")
        cv.disc(7.5, 7.5, radius, "blue")
        if level >= 2:
            cv.disc(6.5, 6.5, 1, "blue_l")
    return cv


def main():
    for level in range(4):
        name = f"water_barrel_top_fill{level}"
        out = TEX / "block" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        water_barrel_top_fill(level).img.save(out, format="PNG", optimize=False)
        print(f"wrote  block/{name}")


if __name__ == "__main__":
    main()
