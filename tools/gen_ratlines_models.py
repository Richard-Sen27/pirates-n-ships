#!/usr/bin/env python3
"""Part lists of the ratlines models (RL1, design.md §4.8 "Visual backlog 2", item 1).

Writes three Java models to common/src/main/resources/assets/pirates_n_ships/models/:
- block/ratlines.json: the hung net, facing north, hanging on the south side (z 13.5..15.5) like a ladder.
- block/ratlines_slope.json: the same net laid at 45 degrees, rising to the north from the bottom edge of the south side
  to the top edge of the north side; every part turns -45 about x round the block centre.
- item/ratlines.json: the net flat, centred in the block, with the held-item transforms of vanilla's item/generated.

A net is two vertical shrouds (2 x 2 px) and four horizontal ratlines (1.5 x 1.5 px) 4 px apart, seized to the shrouds
with a knot (2.5 x 2 px) at each crossing; all on pirates_n_ships:block/rope. On the slope the ratlines lie 4 px apart in
height (5.66 px along the slope), level with the block's collision treads (RatlinesRules.boxes), and stand 0.5 px above
the diagonal on its upper side, so a player's feet rest on them. Shroud ends are left out so stacked or chained blocks
meet without a seam.

The Blockbench project art/models/ratlines.bbmodel (groups `ratlines`, `ratlines_slope`) and art/models/ratlines_item.bbmodel
were built cube by cube from this output; after a change here run `python3 tools/lint_models.py` and rebuild them
(art/README.md, "Ratlines (RL1)").

Run from the repository root: python3 tools/gen_ratlines_models.py
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "common/src/main/resources/assets/pirates_n_ships/models"
ROPE = "pirates_n_ships:block/rope"
SQRT2 = math.sqrt(2)

SHROUD_X = ((2, 4), (12, 14))
RUNG_X = (1, 15)
KNOT_PAD = 0.25  # a knot overhangs its shroud by this much on either side (x)
RUNG_HALF = 0.75  # half the height of a ratline
KNOT_HALF = 1.0  # half the height of a knot


def r5(v):
    v = round(v, 5)
    return int(v) if v == int(v) else v


def span(a, b):
    """A UV interval for the coordinates a..b: kept if inside 0..16, else shifted in (a span over 16 is the whole texture)."""
    if b - a >= 16:
        return 0, 16
    if a < 0:
        return 0, b - a
    if b > 16:
        return 16 - (b - a), 16
    return a, b


def uv(face, f, t):
    """Vanilla's position UV of a face (north/south x and 16-y, east/west z and 16-y, up/down x and z)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face in ("north", "south"):
        u, v = span(x0, x1), span(16 - y1, 16 - y0)
    elif face in ("east", "west"):
        u, v = span(z0, z1), span(16 - y1, 16 - y0)
    else:
        u, v = span(x0, x1), span(z0, z1)
    return [r5(u[0]), r5(v[0]), r5(u[1]), r5(v[1])]


def cube(name, f, t, faces, rot=None):
    el = {"name": name, "from": [r5(c) for c in f], "to": [r5(c) for c in t]}
    if rot is not None:
        el["rotation"] = rot
    el["faces"] = {face: {"uv": uv(face, f, t), "texture": "#0"} for face in faces}
    return el


ALL = ("north", "east", "south", "west", "up", "down")
SIDES = ("north", "east", "south", "west")


def net(shroud_z, rung_z, knot_z, rung_ys, shroud_y, rot=None):
    """The parts of one net: shrouds over shroud_y, a ratline and two knots at each height in rung_ys."""
    out = []
    for i, (x0, x1) in enumerate(SHROUD_X):
        out.append(cube(f"shroud_{i}", (x0, shroud_y[0], shroud_z[0]), (x1, shroud_y[1], shroud_z[1]), SIDES, rot))
    for k, c in enumerate(rung_ys):
        out.append(cube(f"ratline_{k}", (RUNG_X[0], c - RUNG_HALF, rung_z[0]), (RUNG_X[1], c + RUNG_HALF, rung_z[1]), ALL, rot))
        for i, (x0, x1) in enumerate(SHROUD_X):
            out.append(cube(f"knot_{k}_{i}", (x0 - KNOT_PAD, c - KNOT_HALF, knot_z[0]),
                            (x1 + KNOT_PAD, c + KNOT_HALF, knot_z[1]), ALL, rot))
    return out


def block_model(elements, source):
    return {
        "credit": f"Made with Blockbench, source art/models/{source}.bbmodel",
        "parent": "minecraft:block/block",
        "textures": {"0": ROPE, "particle": ROPE},
        "elements": elements,
    }


WALL_YS = (2, 6, 10, 14)


def wall():
    return net((13.5, 15.5), (12.75, 14.25), (12.6, 15.75), WALL_YS, (0, 16))


def slope():
    half = 8 * SQRT2  # half the diagonal of a block side
    ys = [8 + (t - 8) * SQRT2 for t in WALL_YS]  # heights 2, 6, 10, 14 on the diagonal, measured along it
    rot = {"angle": -45, "axis": "x", "origin": [8, 8, 8]}
    return net((5.5, 7.5), (7.0, 8.5), (5.35, 8.65), ys, (8 - half, 8 + half), rot)


def item():
    elements = net((7, 9), (6.75, 9.25), (6.6, 9.4), WALL_YS, (0, 16))
    return {
        "credit": "Made with Blockbench, source art/models/ratlines_item.bbmodel",
        "gui_light": "front",
        "textures": {"0": ROPE, "particle": ROPE},
        "display": {
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        },
        "elements": elements,
    }


def write(path, model):
    path.write_text(json.dumps(model, indent="\t") + "\n")


def main():
    write(MODELS / "block/ratlines.json", block_model(wall(), "ratlines"))
    write(MODELS / "block/ratlines_slope.json", block_model(slope(), "ratlines"))
    write(MODELS / "item/ratlines.json", item())


if __name__ == "__main__":
    main()
