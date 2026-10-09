#!/usr/bin/env python3
"""Part lists of the name board models (NAME2, design.md §4.8 "Ship name boards").

Writes the four hand-made parent models of the name board to
common/src/main/resources/assets/pirates_n_ships/models/block/ and their Blockbench project art/models/name_board.bbmodel
(one group per exported model, all at the same place, only the first visible):

- block/name_board: the part every board draws, facing north and lying against the south side of its block: a plank
  panel x 0..16, y 2..14, z 14.5..16 and the frame's top and bottom strips (stripped log, grain along x) x 0..16,
  y 14..15 and 1..2, z 14..16, so the frame stands 0.5 px proud of the panel. Board 14 px tall (y 1..15), 2 px thick.
  The panel's ends and top and bottom are always covered (by a neighbour's panel, a frame end or a strip) and left out.
- block/name_board_cap_left: the frame's end on the viewer's left (east for a north-facing board, x 15..16, y 2..14,
  z 14..15.95, 0.05 px off the back so its back never fights the panel's), drawn where the row ends on that side.
- block/name_board_cap_right: the same on the viewer's right (west, x 0..1).
- block/name_board_item: the item, a small board standing in the middle of the block (panel x 2..14, y 4..12,
  z 7.5..8.5, planks on both faces; frame x 1..15, y 3..13, z 7..9), with gui, fixed and ground display entries
  (the hands keep block/block's; the board is symmetric along x, ART17).

Every model uses two texture variables, #planks and #log (vanilla oak planks and stripped oak log here); datagen
(ship/decor/ShipDecorModule through wood/WoodFamily) writes the oak children the block state and the item use.
Boards joined in a row draw the frame's ends only at the row's ends (multipart: name_board always, a cap per free
end), so the frame runs round the whole row.

UVs are vanilla's position UVs wrapped into 0..16; the frame's horizontal faces turn the log texture by 90 degrees
so the grain runs along the strip. After a change run `python3 tools/lint_models.py` and rebuild the render
(art/README.md, "Name boards (NAME2)").

Run from the repository root: python3 tools/gen_name_board_models.py
"""
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "common/src/main/resources/assets/pirates_n_ships/models"
ART = ROOT / "art/models"

PLANKS = "minecraft:block/oak_planks"
LOG = "minecraft:block/stripped_oak_log"

BOTTOM, TOP = 1, 15          # the board's edges
FRAME = 1                    # frame width
FRAME_FRONT = 14             # the frame's front face (north-facing model)
PANEL_FRONT = 14.5           # the panel's front face
BACK = 16                    # against the hull
CAP_BACK = BACK - 0.05       # a cap stops short of the back, so its back never fights the panel's

ALL = ("north", "east", "south", "west", "up", "down")


def r5(v):
    v = round(v, 5)
    return int(v) if v == int(v) else v


def span(a, b):
    """A UV interval for a..b, kept inside 0..16 (a span over 16 is the whole texture)."""
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


def grain_x(face, f, t):
    """A log face whose grain runs along x: a strip of the texture as long as the face, turned 90 degrees."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    across = (16 - y1, 16 - y0) if face in ("north", "south") else (z0, z1)
    a, b = span(*across)
    lo, hi = span(x0, x1)
    return [r5(a), r5(lo), r5(b), r5(hi)]


def cube(name, f, t, faces, grain=()):
    """faces: {face: texture key} for the faces that are drawn; grain: faces whose log grain runs along x."""
    out = {}
    for face, tex in faces.items():
        if face in grain:
            out[face] = {"uv": grain_x(face, f, t), "rotation": 90, "texture": "#" + tex}
        else:
            out[face] = {"uv": uv(face, f, t), "texture": "#" + tex}
        if face == "south" and t[2] == 16:
            out[face]["cullface"] = "south"
    return {"name": name, "from": [r5(c) for c in f], "to": [r5(c) for c in t], "faces": out}


def board():
    strip_faces = {"north": "log", "south": "log", "up": "log", "down": "log", "east": "log", "west": "log"}
    return [
        cube("panel", (0, BOTTOM + FRAME, PANEL_FRONT), (16, TOP - FRAME, BACK), {"north": "planks", "south": "planks"}),
        cube("frame_top", (0, TOP - FRAME, FRAME_FRONT), (16, TOP, BACK), strip_faces, grain=("north", "south", "up", "down")),
        cube("frame_bottom", (0, BOTTOM, FRAME_FRONT), (16, BOTTOM + FRAME, BACK), strip_faces, grain=("north", "south", "up", "down")),
    ]


def cap(x0, name):
    # the inner side shows the step from the frame's front down to the panel
    return [cube(name, (x0, BOTTOM + FRAME, FRAME_FRONT), (x0 + FRAME, TOP - FRAME, CAP_BACK),
                 {"north": "log", "east": "log", "west": "log"})]


def cap_left():
    return cap(16 - FRAME, "frame_left")


def cap_right():
    return cap(0, "frame_right")


def item():
    both = {"north": "planks", "south": "planks"}
    log = {f: "log" for f in ALL}
    sides = {"north": "log", "south": "log", "east": "log", "west": "log"}
    return [
        cube("panel", (2, 4, 7.5), (14, 12, 8.5), both),
        cube("frame_top", (1, 12, 7), (15, 13, 9), log, grain=("north", "south", "up", "down")),
        cube("frame_bottom", (1, 3, 7), (15, 4, 9), log, grain=("north", "south", "up", "down")),
        cube("frame_left", (14, 4, 7), (15, 12, 9), sides),
        cube("frame_right", (1, 4, 7), (2, 12, 9), sides),
    ]


ITEM_DISPLAY = {
    "gui": {"rotation": [15, -30, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
}


def used_textures(elements):
    keys = []
    for e in elements:
        for f in e["faces"].values():
            k = f["texture"][1:]
            if k not in keys:
                keys.append(k)
    return keys


def block_model(elements, display=None):
    textures = {}
    for k in ("planks", "log"):
        if k in used_textures(elements):
            textures[k] = PLANKS if k == "planks" else LOG
    textures["particle"] = "#planks" if "planks" in textures else "#log"
    model = {
        "credit": "Made with Blockbench, source art/models/name_board.bbmodel",
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": elements,
    }
    if display:
        model["display"] = display
    return model


# ---------------------------------------------------------------------- the Blockbench project

def det_uuid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "pirates_n_ships/name_board/" + "/".join(parts)))


TEXTURES = [  # (key, vanilla file name)
    ("planks", "oak_planks"),
    ("log", "stripped_oak_log"),
]


def bb_texture(key, name):
    return {
        "name": name + ".png", "path": "", "relative_path": f"../vanilla/{name}.png", "folder": "block",
        "namespace": "minecraft", "id": key, "group": "", "scope": 0, "width": 16, "height": 16, "uv_width": 16,
        "uv_height": 16, "particle": key == "planks", "use_as_default": False, "layers_enabled": False,
        "sync_to_project": "", "file_format": "png", "render_mode": "default", "render_sides": "auto",
        "wrap_mode": "limited", "pbr_channel": "color", "fps": 7, "frame_time": 1, "frame_order_type": "loop",
        "frame_order": "", "frame_interpolate": False, "visible": True, "internal": False, "saved": True,
        "uuid": det_uuid("texture", key),
    }


def bb_element(group, index, el):
    tex_index = {k: i for i, (k, _) in enumerate(TEXTURES)}
    faces = {}
    for face in ALL:
        if face in el["faces"]:
            f = el["faces"][face]
            faces[face] = {"uv": f["uv"], "texture": tex_index[f["texture"][1:]]}
            if "rotation" in f:
                faces[face]["rotation"] = f["rotation"]
            if "cullface" in f:
                faces[face]["cullface"] = f["cullface"]
        else:
            faces[face] = {"uv": [0, 0, 1, 1], "texture": None}
    return {
        "name": el["name"], "box_uv": False, "render_order": "default", "rescale": False, "locked": False,
        "shade_direction_override": "", "light_emission": 0, "export": True, "scope": 0,
        "allow_mirror_modeling": True, "from": el["from"], "to": el["to"], "autouv": 0, "color": index % 8,
        "origin": [8, 8, 8], "faces": faces, "type": "cube", "uuid": det_uuid(group, str(index), el["name"]),
    }


def bb_group(name, visible):
    return {
        "name": name, "uuid": det_uuid("group", name), "export": True, "locked": False, "scope": 0,
        "selected": False, "visibility": visible, "_static": {"properties": {}, "temp_data": {}},
        "origin": [8, 8, 8], "rotation": [0, 0, 0], "color": 0, "children": [], "reset": False, "shade": True,
        "mirror_uv": False, "autouv": 0, "isOpen": False, "primary_selected": False,
    }


def project(groups, visible):
    elements, bb_groups, outliner = [], [], []
    for name, els in groups:
        shown = name in visible
        ids = []
        for i, el in enumerate(els):
            e = bb_element(name, i, el)
            e["visibility"] = shown
            elements.append(e)
            ids.append(e["uuid"])
        g = bb_group(name, shown)
        bb_groups.append(g)
        outliner.append({"uuid": g["uuid"], "isOpen": False, "children": ids})
    return {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": "name_board", "parent": "", "ambientocclusion": True, "front_gui_light": False,
        "visible_box": [1, 1, 0], "variable_placeholders": "", "multi_file_ruleset": "",
        "variable_placeholder_buttons": [], "unhandled_root_fields": {},
        "resolution": {"width": 16, "height": 16},
        "elements": elements, "groups": bb_groups, "outliner": outliner,
        "textures": [bb_texture(k, n) for k, n in TEXTURES],
        "display": ITEM_DISPLAY,
    }


def write(path, model):
    path.write_text(json.dumps(model, indent="\t") + "\n")


def main():
    models = [("name_board", board(), None), ("name_board_cap_left", cap_left(), None),
              ("name_board_cap_right", cap_right(), None), ("name_board_item", item(), ITEM_DISPLAY)]
    for name, els, display in models:
        write(MODELS / f"block/{name}.json", block_model(els, display))
    write(ART / "name_board.bbmodel", project([(n, e) for n, e, _ in models],
                                              {"name_board", "name_board_cap_left", "name_board_cap_right"}))
    for name, els, _ in models:
        print(f"{name}: {len(els)} elements")


if __name__ == "__main__":
    main()
