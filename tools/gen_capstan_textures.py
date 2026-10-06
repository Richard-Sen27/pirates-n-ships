"""Placeholder textures for the capstan (work package D3b, spike 3 part 2).

Reuses the canvas and palette of gen_placeholder_textures.py (not edited). Usage:

    tools/.venv/bin/python tools/gen_capstan_textures.py

Output: common/src/main/resources/assets/pirates_n_ships/textures/block/capstan_side.png and capstan_top.png
(the capstan uses the vanilla column model: side all around, top on both ends).
Side: a dark wooden drum with iron bands and a chain wound around it. Top: the drum seen from above with four bar
sockets.
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ next to the tools
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_placeholder_textures import Canvas, TEX  # noqa: E402


def side():
    c = Canvas("wood")
    c.border("wood_d")
    c.rect(1, 2, 14, 2, "iron")    # upper band
    c.rect(1, 13, 14, 13, "iron")  # lower band
    for y in (5, 7, 9, 11):        # chain turns
        for x in range(1, 15, 2):
            c.px(x, y, "iron_d")
            c.px(x + 1, y, "steel")
    return c


def top():
    c = Canvas("plank")
    c.border("wood_d")
    c.disc(7, 7, 6, "wood")
    c.disc(7, 7, 2, "iron_d")
    for x, y in ((7, 1), (7, 13), (1, 7), (13, 7)):  # bar sockets
        c.rect(x, y, x + 1, y + 1, "black")
    return c


def main():
    out = TEX / "block"
    out.mkdir(parents=True, exist_ok=True)
    side().img.save(out / "capstan_side.png", format="PNG", optimize=False)
    top().img.save(out / "capstan_top.png", format="PNG", optimize=False)


if __name__ == "__main__":
    main()
