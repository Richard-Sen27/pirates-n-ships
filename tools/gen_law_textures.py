#!/usr/bin/env python3
"""Placeholder textures (16x16 pixel art) of the law package (work package E1a). Empty since F8f: the bounty proof is a
hand-made 3D item model textured from the item palettes (tools/gen_item_palette.py).

Reuses the canvas, palette and protection list of gen_placeholder_textures.py, so the style matches.
Run (from the repository root, with the venv from gen_placeholder_textures.py):
    tools/.venv/bin/python tools/gen_law_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/item/<name>.png
Deterministic. Names listed in PROTECTED / tools/protected_textures.txt are never overwritten.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import TEX, protected  # noqa: E402


# The bounty proof became a hand-made 3D item model (art/models/bounty_proof.bbmodel, F8f); no law sprites are left.
ITEMS = {}


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
