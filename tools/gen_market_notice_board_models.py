#!/usr/bin/env python3
"""Part lists of the market notice board models (NB2, design.md §13.2 "Village life", art/README.md).

The market notice board is a 2 wide x 2 high board on two posts, four blocks (`part` bottom_left, bottom_right,
top_left, top_right; left and right as seen by the viewer in front of the notices). The whole board is laid out once in
*board space* (x 0..32, y 0..32, z 0..16; the notices on the north side, unrotated): the viewer's left column is
x 16..32 (east), the right column x 0..16. Each part's model is the board cut to its cell and moved into 0..16:
axis-aligned elements are clipped at the cell's edges (the faces on a cut are left out, they would only touch the
neighbour's), the roof slabs (turned about x) are cut along x only, and a turned notice goes whole to the cell holding
its centre (it may reach a little past the cell). Each part is lit at its own block, like any block.

Writes to common/src/main/resources/assets/pirates_n_ships/models/block/:

- market_notice_board_<part>: the frame (feet and posts in the wood: #planks, #log, #log_top; a spruce board behind
  the notices; rails of #log; a pitched roof of #planks under a #log ridge; brass nails), drawn in every load;
- market_notice_board_<part>_<load> for load empty, some, busy: what is pinned on the board, fixed textures only
  (parchment sandstone_top, white_terracotta, ink black_terracotta, red_concrete pin heads, a nether_wart_block wax
  seal, the wanted poster's red_terracotta header and brown_terracotta portrait). A part with nothing pinned in a load
  gets no file; the block state skips it. empty: lone pins and two torn scraps; some: three notices; busy: the board
  papered over with overlapping notices in layers 0.2 px apart and a wanted poster on top;
- market_notice_board_item: the whole board with the "some" notices at half size inside one block, for the item.

datagen (law/content/MarketNoticeBoardData) writes the wood children of the frames and the item, and a multipart block
state per wood: the part's frame always, its notices for the load, turned by facing.

Also writes art/models/market_notice_board.bbmodel, the board assembled in board space (one group per load, the
frame and the item; only the frame and "busy" visible), to look at in Blockbench. This script is the source.

UVs are vanilla's position UVs of the part's local coordinates wrapped into 0..16 (so the board's texture runs on
across the parts); rails and the ridge turn the log texture by 90 degrees so the grain runs along them. After a change
run `python3 tools/lint_models.py` and rebuild the render (art/README.md, "Market notice board (NB2)").

Run from the repository root: python3 tools/gen_market_notice_board_models.py [--combined DIR]
(--combined writes each load's four parts and the frame as one board-space model per load into DIR, for the lint
across the frame and the notices, which are separate models in the game.)
"""
import argparse
import base64
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "common/src/main/resources/assets/pirates_n_ships/models"
ART = ROOT / "art/models"

ALL = ("north", "east", "south", "west", "up", "down")
PARTS = {"bottom_left": (16, 0), "bottom_right": (0, 0), "top_left": (16, 16), "top_right": (0, 16)}
LOADS = ("empty", "some", "busy")

TEXTURES = {  # key -> default texture (the wood keys hold dark oak, the legacy member's wood)
    "planks": "minecraft:block/dark_oak_planks",
    "log": "minecraft:block/stripped_dark_oak_log",
    "log_top": "minecraft:block/stripped_dark_oak_log_top",
    "board": "minecraft:block/spruce_planks",
    "nail": "minecraft:block/gold_block",
    "parchment": "minecraft:block/sandstone_top",
    "paper": "minecraft:block/white_terracotta",
    "ink": "minecraft:block/black_terracotta",
    "pin": "minecraft:block/red_concrete",
    "seal": "minecraft:block/nether_wart_block",
    "header": "minecraft:block/red_terracotta",
    "portrait": "minecraft:block/brown_terracotta",
}

BOARD_FRONT = 7.0      # the spruce board's front face
LAYER = 0.2            # paper thickness and the distance between two layers of notices
INK = 0.08             # ink stands this far proud of its paper (at the item's half size still past the lint's 0.03)


def r5(v):
    v = round(v, 5)
    return int(v) if v == int(v) else v


def span(a, b):
    """A UV interval for a..b, wrapped into 0..16 (a span of 16 or more is the whole texture)."""
    if b - a >= 16:
        return 0, 16
    shift = (a // 16) * 16
    a, b = a - shift, b - shift
    if b > 16:
        return 16 - (b - a), 16
    return a, b


def uv(face, f, t):
    """Vanilla's position UV of a face (north/south x and 16-y, east/west z and 16-y, up/down x and z)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face in ("north", "south"):
        u, v = span(x0, x1), span(16 - y1, 16 - y0) if y1 <= 16 else span(32 - y1, 32 - y0)
    elif face in ("east", "west"):
        u, v = span(z0, z1), span(16 - y1, 16 - y0) if y1 <= 16 else span(32 - y1, 32 - y0)
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


class El:
    """An element in board space: faces {face: texture key}, grain faces turned along x, an optional rotation."""

    def __init__(self, name, f, t, faces, grain=(), rot=None):
        self.name, self.f, self.t, self.faces, self.grain, self.rot = name, list(f), list(t), dict(faces), grain, rot

    def centre(self):
        return [(a + b) / 2 for a, b in zip(self.f, self.t)]

    def json(self, dx=0.0, dy=0.0, scale=1.0, keep=None):
        f = [self.f[0] - dx, self.f[1] - dy, self.f[2]]
        t = [self.t[0] - dx, self.t[1] - dy, self.t[2]]
        faces = {}
        for face, tex in self.faces.items():
            if keep is not None and face not in keep:
                continue
            if face in self.grain:
                faces[face] = {"uv": grain_x(face, f, t), "rotation": 90, "texture": "#" + tex}
            else:
                faces[face] = {"uv": uv(face, f, t), "texture": "#" + tex}
        out = {"name": self.name, "from": [r5(c * scale) for c in f], "to": [r5(c * scale) for c in t]}
        if self.rot:
            axis, angle, origin = self.rot
            o = [origin[0] - dx, origin[1] - dy, origin[2]]
            out["rotation"] = {"angle": angle, "axis": axis, "origin": [r5(c * scale) for c in o]}
        out["faces"] = faces
        return out


def clip(el, cx, cy):
    """The part of `el` in the cell at (cx, cy) as local JSON, or None; see the module doc for turned elements."""
    if el.rot and el.rot[0] != "x":
        c = el.centre()
        if cx <= c[0] < cx + 16 and cy <= c[1] < cy + 16:
            return el.json(cx, cy)
        return None
    x0, x1 = max(el.f[0], cx), min(el.t[0], cx + 16)
    if el.rot:   # turned about x: cut along x only, the whole element must lie in the cell's row
        c = el.centre()
        if not (cy <= c[1] < cy + 16) or x1 <= x0:
            return None
        y0, y1 = el.f[1], el.t[1]
    else:
        y0, y1 = max(el.f[1], cy), min(el.t[1], cy + 16)
        if x1 <= x0 or y1 <= y0:
            # a flat element (zero size in x or y) belongs to the cell its plane lies in
            if not ((el.f[0] == el.t[0] and cx <= el.f[0] < cx + 16 and y1 > y0)
                    or (el.f[1] == el.t[1] and cy <= el.f[1] < cy + 16 and x1 > x0)):
                return None
    keep = {"north", "south"}
    if x0 == el.f[0]:
        keep.add("west")
    if x1 == el.t[0]:
        keep.add("east")
    if y0 == el.f[1]:
        keep.add("down")
    if y1 == el.t[1]:
        keep.add("up")
    part = El(el.name, (x0, y0, el.f[2]), (x1, y1, el.t[2]), el.faces, el.grain, el.rot)
    return part.json(cx, cy, keep=keep)


# ---------------------------------------------------------------------------------------------------- the frame

def frame():
    log_post = {"north": "log", "south": "log", "east": "log", "west": "log", "up": "log_top", "down": "log_top"}
    rail = {f: "log" for f in ALL}
    foot = {f: "planks" for f in ALL}
    els = []
    for side, (a, b) in (("left", (27.5, 30.5)), ("right", (1.5, 4.5))):
        els.append(El(f"foot_{side}", (a - 1, 0, 5.5), (b + 1, 1.5, 10.5), foot))
        els.append(El(f"post_{side}", (a, 1.5, 6.5), (b, 30, 9.5), log_post))
    els.append(El("board", (4.5, 7, BOARD_FRONT), (27.5, 27, 8.5), {"north": "board", "south": "board"}))
    for name, y in (("rail_bottom", 5), ("rail_top", 27)):
        els.append(El(name, (4.5, y, 6.5), (27.5, y + 2, 9), rail, grain=("north", "south", "up", "down")))
    # the roof: two plank slabs turned 22.5 degrees about x at the ridge (16, 31, 8), 0.4 px past the feet; their ends
    # stop 0.1 and 0.2 px short of the ridge beam's, so no two end faces share a plane
    ridge = (16, 31, 8)
    slab = {f: "planks" for f in ALL}
    els.append(El("roof_north", (0.1, 30.5, 2), (31.9, 31.5, 8), slab, rot=("x", -22.5, ridge)))
    els.append(El("roof_south", (0.2, 30.5, 8), (31.8, 31.5, 14), slab, rot=("x", 22.5, ridge)))
    els.append(El("ridge", (0, 31, 7.25), (32, 32, 8.75), {"north": "log", "south": "log", "up": "log", "down": "log",
                                                            "east": "log_top", "west": "log_top"},
                  grain=("north", "south", "up", "down")))
    # brass nails on the rails' fronts
    for i, x in enumerate((6, 15.7, 25.4)):
        for name, y in (("b", 5.7), ("t", 27.7)):
            els.append(El(f"nail_{name}{i}", (x, y, 6.2), (x + 0.6, y + 0.6, 6.5),
                          {"north": "nail", "east": "nail", "west": "nail", "up": "nail", "down": "nail"}))
    return els


# ---------------------------------------------------------------------------------------------------- notices

def notice(name, x0, y0, w, h, layer, paper="parchment", lines=3, tilt=0.0, pin=True, seal=False):
    """A notice: a paper `layer` layers in front of the board, ink lines, a pin head at the top, maybe a seal."""
    front = BOARD_FRONT - LAYER * (layer + 1)
    rot = None
    if tilt:
        rot = ("z", tilt, (x0 + w / 2, y0 + h / 2, front))
    els = [El(name, (x0, y0, front), (x0 + w, y0 + h, front + LAYER), {"north": paper}, rot=rot)]
    ink_z = front - INK
    margin = 1.0 if w >= 4 else 0.6
    top = y0 + h - 1.6
    for i in range(lines):
        y = top - i * 1.2
        if y < y0 + 0.8:
            break
        length = w - 2 * margin - (1.0 if i % 2 else 0.0)
        els.append(El(f"{name}_ink{i}", (x0 + margin, y - 0.4, ink_z), (x0 + margin + length, y, ink_z), {"north": "ink"}, rot=rot))
    if pin:
        px = x0 + w / 2 - 0.5
        els.append(El(f"{name}_pin", (px, y0 + h - 1.2, front - 0.6), (px + 1, y0 + h - 0.2, front),
                      {"north": "pin", "east": "pin", "west": "pin", "up": "pin", "down": "pin"}, rot=rot))
    if seal:
        sx = x0 + w - 2.2
        els.append(El(f"{name}_seal", (sx, y0 + 0.6, front - 0.3), (sx + 1.4, y0 + 2.0, front),
                      {"north": "seal", "east": "seal", "west": "seal", "up": "seal", "down": "seal"}, rot=rot))
    return els


def pin(name, x, y):
    return El(name, (x, y, BOARD_FRONT - 0.6), (x + 1, y + 1, BOARD_FRONT),
              {"north": "pin", "east": "pin", "west": "pin", "up": "pin", "down": "pin"})


def empty():
    els = [pin("pin0", 7, 23), pin("pin1", 12.5, 18), pin("pin2", 19.5, 24.5), pin("pin3", 24, 15), pin("pin4", 9, 10.5),
           pin("pin5", 17, 11)]
    # two torn scraps left under their pins
    els += notice("scrap0", 6.5, 21.6, 2, 1.6, 0, paper="paper", lines=0, pin=False)
    els += notice("scrap1", 23.5, 13.6, 2, 1.6, 0, lines=0, pin=False)
    return els


def some():
    els = []
    els += notice("notice0", 18, 15, 6, 7, 0, lines=4)
    els += notice("notice1", 7.5, 17, 5, 6, 0, paper="paper", lines=3, tilt=22.5)
    els += notice("notice2", 10, 8.5, 5, 5, 0, lines=2, seal=True)
    return els


def busy():
    els = []
    # the bottom layer: notices round the edges
    els += notice("n0", 5.5, 19.5, 6, 6.5, 0, lines=4)
    els += notice("n1", 20.5, 19, 6.5, 7, 0, paper="paper", lines=4)
    els += notice("n2", 5.5, 8, 5, 5.5, 0, paper="paper", lines=3)
    els += notice("n3", 21, 8, 6, 6.5, 0, lines=3, seal=True)
    els += notice("n4", 13.5, 7.5, 5, 3.5, 0, paper="paper", lines=1, pin=False)
    # the second layer: notices overlapping the first, two of them turned
    els += notice("n5", 9, 12, 5, 6, 1, paper="paper", lines=3, tilt=-22.5)
    els += notice("n6", 19.5, 12.5, 5, 5.5, 1, lines=3, tilt=22.5)
    els += notice("n7", 13, 21.5, 6, 5, 1, lines=2)
    # the wanted poster on top: a red header, the portrait, the reward lines and a seal
    x0, y0, w, h, layer = 12, 9.5, 8, 11.5, 2
    front = BOARD_FRONT - LAYER * (layer + 1)
    face = {"north": "parchment"}
    els.append(El("wanted", (x0, y0, front), (x0 + w, y0 + h, front + LAYER), face))
    ink_z = front - INK
    els.append(El("wanted_header", (x0 + 0.8, y0 + h - 2.2, ink_z), (x0 + w - 0.8, y0 + h - 0.8, ink_z), {"north": "header"}))
    els.append(El("wanted_portrait", (x0 + 2, y0 + 3.6, ink_z), (x0 + w - 2, y0 + h - 2.8, ink_z), {"north": "portrait"}))
    for i, (a, b) in enumerate(((x0 + 1, x0 + w - 1), (x0 + 1.8, x0 + w - 1.8))):
        y = y0 + 2.9 - i * 1.1
        els.append(El(f"wanted_ink{i}", (a, y - 0.4, ink_z), (b, y, ink_z), {"north": "ink"}))
    for i, x in enumerate((x0 + 0.4, x0 + w - 1.4)):
        els.append(El(f"wanted_pin{i}", (x, y0 + h - 0.9, front - 0.6), (x + 1, y0 + h + 0.1, front),
                      {"north": "pin", "east": "pin", "west": "pin", "up": "pin", "down": "pin"}))
    els.append(El("wanted_seal", (x0 + w - 2.4, y0 + 0.5, front - 0.3), (x0 + w - 0.8, y0 + 2.1, front),
                  {"north": "seal", "east": "seal", "west": "seal", "up": "seal", "down": "seal"}))
    return els


# ---------------------------------------------------------------------------------------------------- models

ITEM_DISPLAY = {
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, -135, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "gui": {"rotation": [25, -160, 0], "translation": [0, 0.5, 0], "scale": [0.9, 0.9, 0.9]},
    "head": {"rotation": [0, -180, 0], "scale": [0.6, 0.6, 0.6]},
    "fixed": {"rotation": [0, -180, 0], "scale": [0.9, 0.9, 0.9]},
}


def model(elements, display=None, occlusion=True):
    used = []
    for e in elements:
        for f in e["faces"].values():
            k = f["texture"][1:]
            if k not in used:
                used.append(k)
    textures = {k: v for k, v in TEXTURES.items() if k in used}
    textures["particle"] = "#planks" if "planks" in textures else "#" + used[0]
    out = {"credit": "Made with Blockbench, source tools/gen_market_notice_board_models.py",
           "parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if not occlusion:
        out["ambientocclusion"] = False   # thin layered paper: no dark ambient-occlusion patches between layers
    if display:
        out["display"] = display
    return out


def part_models():
    """{model name: model JSON} for every part's frame and notices."""
    out = {}
    layers = {"frame": frame(), "empty": empty(), "some": some(), "busy": busy()}
    for part, (cx, cy) in PARTS.items():
        for layer, els in layers.items():
            clipped = [c for c in (clip(e, cx, cy) for e in els) if c is not None]
            if not clipped:
                continue
            name = f"market_notice_board_{part}" if layer == "frame" else f"market_notice_board_{part}_{layer}"
            out[name] = model(clipped, occlusion=layer == "frame")
    return out


def item_model():
    els = [e.json(scale=0.5) for e in frame() + some()]
    return model(els, ITEM_DISPLAY)


# ---------------------------------------------------------------------------------------------------- Blockbench

def det_uuid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "pirates_n_ships/market_notice_board/" + "/".join(parts)))


def bb_texture(key, path):
    name = path.split("/")[-1]
    out = {
        "name": name + ".png", "path": "", "relative_path": f"../vanilla/{name}.png", "folder": "block",
        "namespace": "minecraft", "id": key, "group": "", "scope": 0, "width": 16, "height": 16, "uv_width": 16,
        "uv_height": 16, "particle": key == "planks", "use_as_default": False, "layers_enabled": False,
        "sync_to_project": "", "file_format": "png", "render_mode": "default", "render_sides": "auto",
        "wrap_mode": "limited", "pbr_channel": "color", "fps": 7, "frame_time": 1, "frame_order_type": "loop",
        "frame_order": "", "frame_interpolate": False, "visible": True, "internal": False, "saved": True,
        "uuid": det_uuid("texture", key),
    }
    png = ROOT / "art/vanilla" / (name + ".png")
    if png.exists():   # embedded, so the project opens with its textures (tools/extract_vanilla_textures.py)
        out["source"] = "data:image/png;base64," + base64.b64encode(png.read_bytes()).decode("ascii")
    return out


def bb_element(group, index, el, visible):
    keys = list(TEXTURES)
    faces = {}
    for face in ALL:
        if face in el["faces"]:
            f = el["faces"][face]
            faces[face] = {"uv": f["uv"], "texture": keys.index(f["texture"][1:])}
            if "rotation" in f:
                faces[face]["rotation"] = f["rotation"]
        else:
            faces[face] = {"uv": [0, 0, 1, 1], "texture": None}
    out = {
        "name": el["name"], "box_uv": False, "render_order": "default", "rescale": False, "locked": False,
        "shade_direction_override": "", "light_emission": 0, "export": True, "scope": 0,
        "allow_mirror_modeling": True, "from": el["from"], "to": el["to"], "autouv": 0, "color": index % 8,
        "origin": [8, 8, 8], "rotation": [0, 0, 0], "faces": faces, "type": "cube", "visibility": visible,
        "uuid": det_uuid(group, str(index), el["name"]),
    }
    if "rotation" in el:
        r = el["rotation"]
        out["origin"] = r["origin"]
        out["rotation"] = [r["angle"] if r["axis"] == a else 0 for a in "xyz"]
    return out


def shifted(els, dx):
    """Board-space elements moved dx px along x (the render shows the three loads side by side)."""
    out = []
    for e in els:
        j = e.json(dx=-dx)
        out.append(j)
    return out


def project(render=False):
    if render:   # the three loads side by side, empty on the left as seen from the front (north)
        groups = [(f"{load}_board", shifted(frame() + els, dx), True)
                  for load, els, dx in (("empty", empty(), 40), ("some", some(), 0), ("busy", busy(), -40))]
    else:
        groups = [("frame", [e.json() for e in frame()], True), ("empty", [e.json() for e in empty()], False),
                  ("some", [e.json() for e in some()], False), ("busy", [e.json() for e in busy()], True),
                  ("item", item_model()["elements"], False)]
    elements, bb_groups, outliner = [], [], []
    for name, els, shown in groups:
        ids = []
        for i, el in enumerate(els):
            e = bb_element(name, i, el, shown)
            elements.append(e)
            ids.append(e["uuid"])
        g = {"name": name, "uuid": det_uuid("group", name), "export": True, "locked": False, "scope": 0,
             "selected": False, "visibility": shown, "_static": {"properties": {}, "temp_data": {}},
             "origin": [8, 8, 8], "rotation": [0, 0, 0], "color": 0, "children": [], "reset": False, "shade": True,
             "mirror_uv": False, "autouv": 0, "isOpen": False, "primary_selected": False}
        bb_groups.append(g)
        outliner.append({"uuid": g["uuid"], "isOpen": False, "children": ids})
    return {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": "market_notice_board", "parent": "", "ambientocclusion": False, "front_gui_light": False,
        "visible_box": [2, 2, 0], "variable_placeholders": "", "multi_file_ruleset": "",
        "variable_placeholder_buttons": [], "unhandled_root_fields": {},
        "resolution": {"width": 16, "height": 16},
        "elements": elements, "groups": bb_groups, "outliner": outliner,
        "textures": [bb_texture(k, p) for k, p in TEXTURES.items()],
    }


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent="\t") + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--combined", help="also write one board-space model per load (frame and notices) into this folder")
    ap.add_argument("--render-project", help="also write a Blockbench project with the three loads side by side here")
    args = ap.parse_args()
    models = part_models()
    models["market_notice_board_item"] = item_model()
    block = MODELS / "block"
    for old in block.glob("market_notice_board*.json"):
        if old.stem not in models:
            old.unlink()
    for name, data in sorted(models.items()):
        write(block / f"{name}.json", data)
        print(f"{name}: {len(data['elements'])} elements")
    write(ART / "market_notice_board.bbmodel", project())
    if args.render_project:
        write(Path(args.render_project), project(render=True))
    if args.combined:
        for load, els in (("empty", empty()), ("some", some()), ("busy", busy())):
            write(Path(args.combined) / f"market_notice_board_{load}.json", model([e.json() for e in frame() + els]))


if __name__ == "__main__":
    main()
