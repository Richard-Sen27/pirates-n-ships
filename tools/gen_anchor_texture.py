"""Placeholder texture of the visible anchor entity (64x64, wrought iron).

Reuses the palette, Canvas and noise helpers of gen_placeholder_textures.py without changing that file.
Run: python3 tools/gen_anchor_texture.py
"""

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import Canvas, TEX, noise  # noqa: E402


def iron_tile(i):
    c = Canvas("iron")
    noise(c, f"anchor_{i}", ["iron_d", "steel_d"], chance=0.3)
    noise(c, f"anchor_rust_{i}", ["brown", "amber"], chance=0.04)
    return c


def main():
    out = TEX / "entity"
    out.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", (64, 64))
    for ty in range(4):
        for tx in range(4):
            img.paste(iron_tile(ty * 4 + tx).img, (tx * 16, ty * 16))
    img.save(out / "anchor.png", format="PNG", optimize=False)


if __name__ == "__main__":
    main()
