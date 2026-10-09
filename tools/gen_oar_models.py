#!/usr/bin/env python3
"""Part lists of the oar models (OAR1, design.md §5.3b "Oars").

Writes the hand-made models of the oars to common/src/main/resources/assets/pirates_n_ships/models/ and their
Blockbench projects to art/models/:

- block/oar_bench (art/models/oar_bench.bbmodel): the oar bench in its north frame (facing=north, the outboard side
  north): a thwart running athwartships (x 4..12, the whole block long, top at 8 px) on four stripped-log legs with
  two stretchers and a spine, and at its outboard (north) end a gunwale board (y 6..9) carrying two thole pins with a
  3 px gap between them where the oar lies. Texture variables #planks and #log (oak here); the wood family's datagen
  writes one child per wood. The item uses the block model.
- item/oar (art/models/oar.bbmodel): the oar a rower holds, 3 blocks long along the model's +y (y -16..32): a round-ish
  grip, the loom with a leather button where it lies in the thole pins, the shaft, the throat and the blade with a
  centre rib. Oak planks and stripped oak log. Its display entries hold it in the hand like a long pole lying across
  the rower's body: the third-person entries are what the rowing animation was solved for (crew/npc/OarRigTest);
  the left-hand entry equals the right one, vanilla mirrors it (ART17).

UVs are vanilla's position UVs wrapped into 0..16 (as tools/gen_railing_models.py). After a change run
`python3 tools/lint_models.py` and `./gradlew :common:test --tests '*OarRigTest' --tests '*HandMadeModelsTest'`.

Run from the repository root: python3 tools/gen_oar_models.py
"""
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "common/src/main/resources/assets/pirates_n_ships/models"
ART = ROOT / "art/models"

PLANKS = "minecraft:block/oak_planks"
LOG = "minecraft:block/stripped_oak_log"

ALL = ("north", "east", "south", "west", "up", "down")
SIDES = ("north", "east", "south", "west")


def r5(v):
    v = round(v, 5)
    return int(v) if v == int(v) else v


def wrap(a, b):
    """A UV interval for a..b inside 0..16: shifted by whole blocks (a span over 16 is the whole texture)."""
    if b - a >= 16:
        return 0, 16
    while a < 0:
        a, b = a + 16, b + 16
    while b > 16:
        a, b = a - 16, b - 16
    if a < 0:
        return 0, b - a
    return a, b


def uv(face, f, t):
    """Vanilla's position UV of a face (north/south x and 16-y, east/west z and 16-y, up/down x and z)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face in ("north", "south"):
        u, v = wrap(x0, x1), wrap(16 - y1, 16 - y0)
    elif face in ("east", "west"):
        u, v = wrap(z0, z1), wrap(16 - y1, 16 - y0)
    else:
        u, v = wrap(x0, x1), wrap(z0, z1)
    return [r5(u[0]), r5(v[0]), r5(u[1]), r5(v[1])]


def cube(name, f, t, tex, faces=ALL):
    return {"name": name, "from": [r5(c) for c in f], "to": [r5(c) for c in t],
            "faces": {face: {"uv": uv(face, f, t), "texture": "#" + tex} for face in faces}}


# ---------------------------------------------------------------------- the oar bench (north frame)

THWART_TOP = 8
GUNWALE_TOP = 9


def bench():
    out = [cube("thwart", (4, 6, 2), (12, THWART_TOP, 16), "planks", ("east", "south", "west", "up", "down"))]
    legs = (("leg_inboard_west", 4.5, 12.5), ("leg_inboard_east", 9.5, 12.5),
            ("leg_outboard_west", 4.5, 3.5), ("leg_outboard_east", 9.5, 3.5))
    for name, x, z in legs:
        out.append(cube(name, (x, 0, z), (x + 2, 6, z + 2), "log", SIDES))
    out.append(cube("stretcher_inboard", (6.5, 2, 13), (9.5, 3.5, 14), "planks", ("north", "south", "up", "down")))
    out.append(cube("stretcher_outboard", (6.5, 2, 4), (9.5, 3.5, 5), "planks", ("north", "south", "up", "down")))
    out.append(cube("spine", (7, 3.5, 5), (9, 4.5, 13), "planks", ("east", "west", "up", "down")))
    out.append(cube("gunwale", (3, 6, 0), (13, GUNWALE_TOP, 2), "planks", ALL))
    out.append(cube("thole_pin_west", (5, GUNWALE_TOP, 0.5), (6.5, 13, 1.5), "log", ("north", "east", "south", "west", "up")))
    out.append(cube("thole_pin_east", (9.5, GUNWALE_TOP, 0.5), (11, 13, 1.5), "log", ("north", "east", "south", "west", "up")))
    out.append(cube("gunwale_knee", (7, 2, 0.5), (9, 6, 2), "log", ("north", "east", "west", "down")))
    return out


# ---------------------------------------------------------------------- the oar (item frame, along +y)

def oar():
    return [
        cube("grip", (7.25, -15, 7.25), (8.75, -11, 8.75), "log", ("north", "east", "south", "west", "up")),
        cube("grip_knob", (7, -16.0, 7), (9, -15, 9), "log", ("north", "east", "south", "west", "down")),
        cube("loom", (7, -11, 7), (9, 8, 9), "planks", ("north", "east", "south", "west")),
        cube("button", (6.75, -6, 6.75), (9.25, -4, 9.25), "log", ALL),
        cube("shaft", (7.25, 8, 7.25), (8.75, 22, 8.75), "planks", ("north", "east", "south", "west")),
        cube("throat", (7, 22, 7.5), (9, 23.5, 8.5), "planks", ("north", "east", "south", "west")),
        cube("shoulder", (6, 23.5, 7.5), (10, 24.5, 8.5), "planks", ("north", "east", "south", "west", "down")),
        cube("blade", (5, 24.5, 7.6), (11, 32, 8.4), "planks", ALL),
        cube("rib", (7.5, 23.5, 7.3), (8.5, 30, 8.7), "log", ("north", "east", "south", "west", "up")),
    ]


# Display entries of the oar. The third-person entries carry the oar across the body: the shaft's +y turned to the
# hand's side and tilted down outboard; the rowing animation's right_hand/left_hand keys sweep it (OarRigTest).
# The loom at model y -8 lies in the fist: translation = scale x (0, 8 - (-8), 0) px; the shaft runs along the hand's +y.
OAR_SCALE = 1.4
OAR_THIRD_PERSON = {"rotation": [0, 0, 0], "translation": [0, round(16 * OAR_SCALE, 4), 0], "scale": [OAR_SCALE] * 3}
OAR_DISPLAY = {
    "thirdperson_righthand": OAR_THIRD_PERSON,
    "thirdperson_lefthand": OAR_THIRD_PERSON,
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
    "head": {"rotation": [0, -180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
}


def used_textures(elements):
    keys = []
    for e in elements:
        for f in e["faces"].values():
            k = f["texture"][1:]
            if k not in keys:
                keys.append(k)
    return keys


def textures_of(elements):
    textures = {}
    for k in ("planks", "log"):
        if k in used_textures(elements):
            textures[k] = PLANKS if k == "planks" else LOG
    textures["particle"] = "#planks"
    return textures


def block_model(source, elements):
    return {
        "credit": f"Made with Blockbench, source art/models/{source}.bbmodel",
        "parent": "minecraft:block/block",
        "textures": textures_of(elements),
        "elements": elements,
    }


def item_model(source, elements, display):
    return {
        "credit": f"Made with Blockbench, source art/models/{source}.bbmodel",
        "textures": textures_of(elements),
        "gui_light": "front",
        "elements": elements,
        "display": display,
    }


# ---------------------------------------------------------------------- the Blockbench projects

TEXTURES = [  # (key, vanilla file name)
    ("planks", "oak_planks"),
    ("log", "stripped_oak_log"),
]


def det_uuid(project, *parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"pirates_n_ships/{project}/" + "/".join(parts)))


def bb_texture(project, key, name):
    return {
        "name": name + ".png", "path": "", "relative_path": f"../vanilla/{name}.png", "folder": "block",
        "namespace": "minecraft", "id": key, "group": "", "scope": 0, "width": 16, "height": 16, "uv_width": 16,
        "uv_height": 16, "particle": key == "planks", "use_as_default": False, "layers_enabled": False,
        "sync_to_project": "", "file_format": "png", "render_mode": "default", "render_sides": "auto",
        "wrap_mode": "limited", "pbr_channel": "color", "fps": 7, "frame_time": 1, "frame_order_type": "loop",
        "frame_order": "", "frame_interpolate": False, "visible": True, "internal": False, "saved": True,
        "uuid": det_uuid(project, "texture", key),
    }


def bb_element(project, index, el):
    tex_index = {k: i for i, (k, _) in enumerate(TEXTURES)}
    faces = {}
    for face in ALL:
        if face in el["faces"]:
            f = el["faces"][face]
            faces[face] = {"uv": f["uv"], "texture": tex_index[f["texture"][1:]]}
        else:
            faces[face] = {"uv": [0, 0, 1, 1], "texture": None}
    return {
        "name": el["name"], "box_uv": False, "render_order": "default", "rescale": False, "locked": False,
        "shade_direction_override": "", "light_emission": 0, "export": True, "scope": 0,
        "allow_mirror_modeling": True, "from": el["from"], "to": el["to"], "autouv": 0, "color": index % 8,
        "origin": [8, 8, 8], "faces": faces, "type": "cube", "uuid": det_uuid(project, str(index), el["name"]),
    }


def project(name, elements, display=None):
    els = [bb_element(name, i, el) for i, el in enumerate(elements)]
    out = {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": name, "parent": "", "ambientocclusion": True, "front_gui_light": display is not None,
        "visible_box": [1, 1, 0], "variable_placeholders": "", "multi_file_ruleset": "",
        "variable_placeholder_buttons": [], "unhandled_root_fields": {},
        "resolution": {"width": 16, "height": 16},
        "elements": els, "groups": [], "outliner": [e["uuid"] for e in els],
        "textures": [bb_texture(name, k, n) for k, n in TEXTURES],
    }
    if display is not None:
        out["display"] = display
    return out


def write(path, model):
    path.write_text(json.dumps(model, indent="\t") + "\n")


def main():
    b = bench()
    o = oar()
    write(MODELS / "block/oar_bench.json", block_model("oar_bench", b))
    write(MODELS / "item/oar.json", item_model("oar", o, OAR_DISPLAY))
    write(ART / "oar_bench.bbmodel", project("oar_bench", b))
    write(ART / "oar.bbmodel", project("oar", o, OAR_DISPLAY))
    print(f"oar_bench: {len(b)} elements")
    print(f"oar: {len(o)} elements")


if __name__ == "__main__":
    main()
