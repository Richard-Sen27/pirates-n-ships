#!/usr/bin/env python3
"""Part lists of the ratline net models (RL2, design.md §4.8 "Ratline nets between fittings").

Writes three Java models to common/src/main/resources/assets/pirates_n_ships/models/:
- block/chainplate.json: an iron strap with two bolts and an eye ring, made for the floor facing north (the strap
  along z); the block state turns it like a button (wall: x 90). The ring's centre is (8, 4.8, 8): 0.3 blocks off the
  face it is mounted on (NetFitting.RING_FROM_SUPPORT), where the drawn net is tied.
- block/mast_hoop.json: an iron band round the mast block behind it (the mast is to the south, z 16..32) with an eye
  ring standing out of it to the north; ring centre (8, 8, 11.2), 0.3 blocks off the mast's face, at half height.
- item/ratline_net.json: a rolled net (two crossed boxes of rope coil, two lashings, the two stick ends, a loose flap
  with two rungs), built upright and turned z -45 like the flag bundles so the handheld slots hold it like a tool.

Rings are flat octagons of eight bars (the mooring ring recipe, art/README.md ART5): four axis-aligned bars and the top
and bottom bars turned +-45 degrees about x round the ring centre, those 0.05 px shorter at both ends and 0.05 px
thinner across, so no two faces share a plane. All faces on the palette sheets (palette_2 iron and rope, palette wood).

The Blockbench projects art/models/chainplate.bbmodel, mast_hoop.bbmodel and ratline_net.bbmodel were built cube by
cube from this output; after a change here run `python3 tools/lint_models.py` and rebuild them (art/README.md,
"Ratline nets (RL2)").

Run from the repository root: python3 tools/gen_ratline_net_models.py
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "common/src/main/resources/assets/pirates_n_ships/models"
P1 = "pirates_n_ships:item/palette"
P2 = "pirates_n_ships:item/palette_2"
TAN = math.tan(math.radians(22.5))

# palette patches: (texture key, u, v) of the 4x4 patch's corner
PATCH = {
    "cast_iron": ("#0", 0, 12), "cast_iron_dark": ("#0", 4, 12), "cast_iron_light": ("#0", 12, 8),
    "lead_light": ("#0", 0, 8), "lead": ("#0", 4, 8), "lead_dark": ("#0", 8, 8),
    "rope": ("#0", 8, 12), "rope_dark": ("#0", 12, 12), "rope_coil": ("#0", 12, 4),
    "wood": ("#1", 12, 8), "wood_dark": ("#1", 8, 8),
}
ALL = ("north", "east", "south", "west", "up", "down")


def r5(v):
    v = round(v, 5)
    return int(v) if v == int(v) else v


def face(patch):
    tex, u, v = PATCH[patch]
    return {"uv": [u + 0.5, v + 0.5, u + 3.5, v + 3.5], "texture": tex}


def cube(name, f, t, colours, rot=None, skip=()):
    """colours: one patch for all faces, or a dict face -> patch with 'side' as the default."""
    if isinstance(colours, str):
        colours = {"side": colours}
    el = {"name": name, "from": [r5(c) for c in f], "to": [r5(c) for c in t]}
    if rot is not None:
        el["rotation"] = rot
    el["faces"] = {fc: face(colours.get(fc, colours["side"])) for fc in ALL if fc not in skip}
    return el


def ring_yz(prefix, cy, cz, outer, inner, x0, x1, colours):
    """A flat octagonal ring in the y-z plane round (cy, cz), x0..x1 thick."""
    half = outer * TAN
    out = [
        cube(f"{prefix}_top", (x0, cy + inner, cz - half), (x1, cy + outer, cz + half), colours),
        cube(f"{prefix}_bottom", (x0, cy - outer, cz - half), (x1, cy - inner, cz + half), colours),
        cube(f"{prefix}_north", (x0, cy - half, cz - outer), (x1, cy + half, cz - inner), colours),
        cube(f"{prefix}_south", (x0, cy - half, cz + inner), (x1, cy + half, cz + outer), colours),
    ]
    origin = [8, r5(cy), r5(cz)]
    for name, lo, hi in (("top", cy + inner, cy + outer), ("bottom", cy - outer, cy - inner)):
        for angle in (45, -45):
            out.append(cube(f"{prefix}_{name}_{'p' if angle > 0 else 'm'}45", (x0 + 0.05, lo, cz - half + 0.05),
                            (x1 - 0.05, hi, cz + half - 0.05), colours,
                            {"angle": angle, "axis": "x", "origin": origin}))
    return out


IRON = {"side": "cast_iron", "up": "cast_iron_light", "down": "cast_iron_dark"}
RING = {"side": "lead", "up": "lead_light", "down": "lead_dark"}


def chainplate():
    return [
        cube("strap", (6.5, 0, 3), (9.5, 0.8, 13), IRON),
        cube("strap_bevel", (7, 0.8, 3.5), (9, 1.1, 12.5), IRON, skip=("down",)),
        cube("bolt_north", (7.4, 1.1, 4), (8.6, 1.6, 5.2), {"side": "cast_iron_dark", "up": "cast_iron_light"}, skip=("down",)),
        cube("bolt_south", (7.4, 1.1, 10.8), (8.6, 1.6, 12), {"side": "cast_iron_dark", "up": "cast_iron_light"}, skip=("down",)),
        cube("lug", (7.5, 1.1, 6.6), (8.5, 3.2, 9.4), {"side": "cast_iron_dark", "up": "cast_iron"}, skip=("down", "up")),
    ] + ring_yz("ring", 4.8, 8, 2.2, 1.4, 7.4, 8.6, RING)


def mast_hoop():
    band = {"side": "cast_iron", "up": "cast_iron_light", "down": "cast_iron_dark"}
    rivet = {"side": "cast_iron_dark", "north": "cast_iron_light"}
    return [
        cube("band_front", (-0.4, 6.5, 15.6), (16.4, 9.5, 16), band),
        cube("band_west", (-0.4, 6.5, 16), (0, 9.5, 32), band, skip=("north",)),
        cube("band_east", (16, 6.5, 16), (16.4, 9.5, 32), band, skip=("north",)),
        cube("rivet_west", (2.6, 7.4, 15.2), (3.8, 8.6, 15.6), rivet, skip=("south",)),
        cube("rivet_east", (12.2, 7.4, 15.2), (13.4, 8.6, 15.6), rivet, skip=("south",)),
        cube("lug", (7.5, 7, 12.6), (8.5, 9, 15.6), {"side": "cast_iron_dark", "up": "cast_iron"}, skip=("south",)),
    ] + ring_yz("ring", 8, 11.2, 2.0, 1.25, 7.4, 8.6, RING)


def net_roll():
    rot = {"angle": -45, "axis": "z", "origin": [8, 8, 8]}
    coil = {"side": "rope_coil", "up": "rope", "down": "rope"}
    lash = "rope_dark"
    stick = {"side": "wood", "up": "wood_dark", "down": "wood_dark"}
    return [
        cube("roll_wide", (5, 3.5, 6.2), (11, 12.5, 9.8), coil, rot),
        cube("roll_deep", (6.2, 3.55, 5), (9.8, 12.45, 11), coil, rot),
        cube("lash_low_wide", (4.7, 5, 5.9), (11.3, 6, 10.1), lash, rot),
        cube("lash_low_deep", (5.9, 5.05, 4.7), (10.1, 5.95, 11.3), lash, rot),
        cube("lash_high_wide", (4.7, 10, 5.9), (11.3, 11, 10.1), lash, rot),
        cube("lash_high_deep", (5.9, 10.05, 4.7), (10.1, 10.95, 11.3), lash, rot),
        cube("stick_left", (6.3, 1.5, 7.5), (7.3, 14.5, 8.5), stick, rot),
        cube("stick_right", (8.7, 1.5, 7.5), (9.7, 14.5, 8.5), stick, rot),
        cube("flap", (11, 4.5, 7.8), (13.6, 11.5, 8.2), "rope", rot, skip=("west", "east")),
        cube("flap_rung_low", (11, 6.3, 7.6), (13.9, 6.9, 8.4), lash, rot, skip=("west",)),
        cube("flap_rung_high", (11, 9.1, 7.6), (13.9, 9.7, 8.4), lash, rot, skip=("west",)),
        cube("flap_edge", (13.6, 4.5, 7.7), (14.1, 11.5, 8.3), "rope_dark", rot),
    ]


def block_model(elements, source, display):
    textures = {"0": P2, "particle": P2}
    return {
        "credit": f"Made with Blockbench, source art/models/{source}.bbmodel",
        "parent": "minecraft:block/block",
        "textures": textures,
        "display": display,
        "elements": elements,
    }


def dsp(rot, tr, sc):
    return {"rotation": rot, "translation": tr, "scale": [sc, sc, sc]}


# block items: the fitting held and shown like a small block, centred for the GUI slot
CHAINPLATE_DISPLAY = {
    "gui": dsp([30, 225, 0], [0, 2, 0], 1.1),
    "ground": dsp([0, 0, 0], [0, 3, 0], 0.5),
    "fixed": dsp([-90, 0, 0], [0, 0, -6], 1),
    "thirdperson_righthand": dsp([75, 45, 0], [0, 2.5, 0], 0.5),
    "thirdperson_lefthand": dsp([75, 45, 0], [0, 2.5, 0], 0.5),
    "firstperson_righthand": dsp([0, 45, 0], [0, 3, 0], 0.6),
    "firstperson_lefthand": dsp([0, 225, 0], [0, 3, 0], 0.6),
}
HOOP_DISPLAY = {
    "gui": dsp([30, 225, 0], [3.2, 0, 0], 0.55),
    "ground": dsp([0, 0, 0], [0, 3, -3], 0.4),
    "fixed": dsp([0, 0, 0], [0, 0, -6], 0.55),
    "thirdperson_righthand": dsp([75, 45, 0], [0, 2.5, -3], 0.4),
    "thirdperson_lefthand": dsp([75, 45, 0], [0, 2.5, -3], 0.4),
    "firstperson_righthand": dsp([0, 45, 0], [0, 3, -3], 0.4),
    "firstperson_lefthand": dsp([0, 225, 0], [0, 3, -3], 0.4),
}


def item_model():
    return {
        "credit": "Made with Blockbench, source art/models/ratline_net.bbmodel",
        "gui_light": "front",
        "textures": {"0": P2, "1": P1, "particle": P2},
        "display": {
            "thirdperson_righthand": dsp([0, -90, 55], [0, 4, 0.5], 0.85),
            "thirdperson_lefthand": dsp([0, 90, -55], [0, 4, 0.5], 0.85),
            "firstperson_righthand": dsp([0, -90, 25], [1.13, 3.2, 1.13], 0.68),
            "firstperson_lefthand": dsp([0, 90, -25], [1.13, 3.2, 1.13], 0.68),
            "ground": dsp([0, 0, 0], [0, 2, 0], 0.5),
            "gui": dsp([0, 0, 0], [0, 0, 0], 1),
            "head": dsp([0, 180, 0], [0, 13, 7], 1),
            "fixed": dsp([0, 180, 0], [0, 0, 0], 1),
        },
        "elements": net_roll(),
    }


def write(path, model):
    path.write_text(json.dumps(model, indent="\t") + "\n")


def main():
    write(MODELS / "block/chainplate.json", block_model(chainplate(), "chainplate", CHAINPLATE_DISPLAY))
    write(MODELS / "block/mast_hoop.json", block_model(mast_hoop(), "mast_hoop", HOOP_DISPLAY))
    write(MODELS / "item/ratline_net.json", item_model())


if __name__ == "__main__":
    main()
