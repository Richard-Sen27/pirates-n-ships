#!/usr/bin/env python3
"""Part lists of the railing models (RAIL1, design.md §4.8 "Railings (RAIL1/RAIL2)").

Writes the four hand-made parent models of the railing family to
common/src/main/resources/assets/pirates_n_ships/models/block/ and their Blockbench project art/models/railing.bbmodel
(one group per exported model, all at the same place, only the first visible):

- block/railing_edge: the rail along the north edge of the block (x 0..16, z 0..2): a top rail (y 14..16), a lower
  rail (y 7..9) and four balusters 1 px square every 4 px (x centres 2, 6, 10, 14, so the spacing runs on across
  blocks) standing on the deck up to the top rail. The rails stand 0.05 px off the outer face (z 0.05), so a post's
  outer face never fights them; their end faces are left out (a neighbour's rail or a post always covers them).
- block/railing_post: the post at the north-west corner (x 0..3, z 0..3): a stripped-log post y 0..16 and a 1 px plank
  cap to y 17.
- block/railing_port: the railing port facing north: the two posts at the ends of the north edge and a 4 px sill
  between them (x 3..13, y 0..4, z 0.05..2), no rails.
- block/railing_item: the item: one edge with a post at either end, centred in the block (z 6.5..9.5).
- RAIL3, the railing gate's leaf (the gate's posts and sill are block/railing_port, drawn by the same block state):
  block/railing_gate_left and _right: the closed leaf between the posts (x 3..13): the edge's top and lower rail and
  the two balusters between them (x centres 6 and 10) standing on the sill (y 4) up to the top rail, two iron hinge
  straps (#iron, vanilla anvil) round the rails at the hinge post's end, reaching 1 px onto the post, and a latch bar
  with a handle on the inboard face at the other end, its tongue running into the latch post; left = hinged on the
  west post (looking outboard, north), right = mirrored on the east post. block/railing_gate_left_open and
  _right_open: the same leaf turned 90 degrees outboard (to the north, out of the block) about the hinge post's inner
  outer corner (x 3 or 13, z 0), like a fence gate's leaf turn. block/railing_gate_item: the port's posts and sill with
  the closed left leaf, centred like the railing's item.

Every model uses two texture variables, #planks and #log (vanilla oak planks and stripped oak log here); the wood
family's datagen (ship/decor/wood/WoodFamily) writes one child per wood that overrides them. The block state turns the
north-frame parts by y rotation (multipart): an edge model per railed edge, a post model per post corner.

UVs are vanilla's position UVs wrapped into 0..16 (as tools/gen_ratlines_models.py). After a change run
`python3 tools/lint_models.py` and rebuild the renders (art/README.md, "Railings (RAIL1)").

Run from the repository root: python3 tools/gen_railing_models.py
"""
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "common/src/main/resources/assets/pirates_n_ships/models"
ART = ROOT / "art/models"

PLANKS = "minecraft:block/oak_planks"
LOG = "minecraft:block/stripped_oak_log"
IRON = "minecraft:block/anvil"

INSET = 0.05  # the rails' and the sill's outer face stand this far in from the block's edge
RAIL_DEPTH = 2
POST = 3
POST_TOP = 16
CAP_TOP = 17
TOP_RAIL = (14, 16)
LOWER_RAIL = (7, 9)
BALUSTER_X = (2, 6, 10, 14)
BALUSTER_TOP = TOP_RAIL[0]
SILL_TOP = 4

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


def cube(name, f, t, faces):
    """faces: {face: texture key} for the faces that are drawn."""
    return {"name": name, "from": [r5(c) for c in f], "to": [r5(c) for c in t],
            "faces": {face: {"uv": uv(face, f, t), "texture": "#" + tex} for face, tex in faces.items()}}


def rail_parts(z0, prefix=""):
    """A rail along x (0..16) whose outer face is at z0: top rail, lower rail and the balusters."""
    out = []
    rail_faces = {f: "planks" for f in ("north", "south", "up", "down")}
    out.append(cube(prefix + "top_rail", (0, TOP_RAIL[0], z0 + INSET), (16, TOP_RAIL[1], z0 + RAIL_DEPTH), rail_faces))
    out.append(cube(prefix + "lower_rail", (0, LOWER_RAIL[0], z0 + INSET), (16, LOWER_RAIL[1], z0 + RAIL_DEPTH), rail_faces))
    side = {f: "planks" for f in ("north", "east", "south", "west")}
    for i, c in enumerate(BALUSTER_X):
        out.append(cube(f"{prefix}baluster_{i}", (c - 0.5, 0, z0 + 0.5), (c + 0.5, BALUSTER_TOP, z0 + 1.5), side))
    return out


def post_parts(x0, z0, name):
    """A 3 x 3 px post with its corner at (x0, z0): the stripped-log post and the plank cap."""
    return [
        cube(name, (x0, 0, z0), (x0 + POST, POST_TOP, z0 + POST), {f: "log" for f in ("north", "east", "south", "west")}),
        cube(name + "_cap", (x0, POST_TOP, z0), (x0 + POST, CAP_TOP, z0 + POST),
             {f: "planks" for f in ("north", "east", "south", "west", "up")}),
    ]


def edge():
    return rail_parts(0)


def post():
    return post_parts(0, 0, "post")


def port():
    out = post_parts(0, 0, "post_west") + post_parts(16 - POST, 0, "post_east")
    out.append(cube("sill", (POST, 0, INSET), (16 - POST, SILL_TOP, RAIL_DEPTH),
                    {f: "planks" for f in ("north", "south", "up")}))
    return out


def item():
    z0 = 8 - RAIL_DEPTH / 2  # the rail centred in the block
    pz = 8 - POST / 2
    return rail_parts(z0) + post_parts(0, pz, "post_west") + post_parts(16 - POST, pz, "post_east")


# ---------------------------------------------------------------------- the gate (RAIL3)

LEAF = 16 - 2 * POST  # the leaf's length between the posts (u 0..10)
GATE_BALUSTER_U = (3, 7)  # x centres 6 and 10, the edge's spacing
STRAP_U = (-1, 3)  # the straps reach 1 px onto the hinge post
STRAP_W = (-0.25, 2.25)
STRAP_Y = ((13.75, 16.25), (6.75, 9.25))
LATCH = ((8, 11.5), (11, 12), (2, 2.6))  # u, y, w of the latch bar
HANDLE = ((8, 8.75), (10.5, 12.5), (2.6, 3.2))


def leaf_parts():
    """The leaf in its own frame: (name, (u0, u1), (y0, y1), (w0, w1), texture, end faces drawn closed).

    u runs along the edge from the hinge post's inner face (0) to the latch post's (10), w is the depth from the
    block's outer face (0) inboard.
    """
    out = [
        ("top_rail", (0, LEAF), TOP_RAIL, (INSET, RAIL_DEPTH), "planks", False),
        ("lower_rail", (0, LEAF), LOWER_RAIL, (INSET, RAIL_DEPTH), "planks", False),
    ]
    for i, u in enumerate(GATE_BALUSTER_U):
        out.append((f"baluster_{i}", (u - 0.5, u + 0.5), (SILL_TOP, BALUSTER_TOP), (0.5, 1.5), "planks", True))
    for i, y in enumerate(STRAP_Y):
        out.append((f"hinge_strap_{i}", STRAP_U, y, STRAP_W, "iron", True))
    out.append(("latch", LATCH[0], LATCH[1], LATCH[2], "iron", True))
    out.append(("latch_handle", HANDLE[0], HANDLE[1], HANDLE[2], "iron", True))
    return out


def leaf(hinge_left, open_, dz=0.0):
    """The leaf in the block's north frame: closed between the posts, or open, turned 90 degrees outboard (to -z)
    about the hinge post's inner outer corner. Closed, the rails' end faces against the posts are left out."""
    out = []
    for name, (u0, u1), (y0, y1), (w0, w1), tex, ends in leaf_parts():
        if not open_:
            x = (POST + u0, POST + u1) if hinge_left else (16 - POST - u1, 16 - POST - u0)
            z = (w0 + dz, w1 + dz)
            faces = ("north", "south", "up", "down") + (("east", "west") if ends else ())
        else:
            x = (POST + w0, POST + w1) if hinge_left else (16 - POST - w1, 16 - POST - w0)
            z = (-u1 + dz, -u0 + dz)
            faces = ALL
        if name.startswith("baluster"):
            # the top rail covers a baluster's top, and closed the sill its foot (as on the edge)
            faces = tuple(f for f in faces if f != "up" and (open_ or f != "down"))
        out.append(cube(name, (x[0], y0, z[0]), (x[1], y1, z[1]), {f: tex for f in faces}))
    return out


def gate_item():
    pz = 8 - POST / 2  # the posts centred as on the railing's item
    dz = 8 - RAIL_DEPTH / 2  # the leaf's rails centred
    out = post_parts(0, pz, "post_west") + post_parts(16 - POST, pz, "post_east")
    out.append(cube("sill", (POST, 0, dz + INSET), (16 - POST, SILL_TOP, dz + RAIL_DEPTH),
                    {f: "planks" for f in ("north", "south", "up")}))
    return out + leaf(True, False, dz)


def used_textures(elements):
    keys = []
    for e in elements:
        for f in e["faces"].values():
            k = f["texture"][1:]
            if k not in keys:
                keys.append(k)
    return keys


def block_model(elements):
    textures = {}
    for k, v in (("planks", PLANKS), ("log", LOG), ("iron", IRON)):
        if k in used_textures(elements):
            textures[k] = v
    textures["particle"] = "#planks"
    return {
        "credit": "Made with Blockbench, source art/models/railing.bbmodel",
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": elements,
    }


# ---------------------------------------------------------------------- the Blockbench project

def det_uuid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "pirates_n_ships/railing/" + "/".join(parts)))


TEXTURES = [  # (key, vanilla file name)
    ("planks", "oak_planks"),
    ("log", "stripped_oak_log"),
    ("iron", "anvil"),
]


def bb_texture(index, key, name):
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
        else:
            faces[face] = {"uv": [0, 0, 1, 1], "texture": None}
    return {
        "name": el["name"], "box_uv": False, "render_order": "default", "rescale": False, "locked": False,
        "shade_direction_override": "", "light_emission": 0, "export": True, "scope": 0,
        "allow_mirror_modeling": True, "from": el["from"], "to": el["to"], "autouv": 0, "color": index % 8,
        "origin": [8, 8, 8], "faces": faces, "type": "cube", "uuid": det_uuid(group, str(index), el["name"]),
    }


def bb_group(name, children, visible):
    return {
        "name": name, "uuid": det_uuid("group", name), "export": True, "locked": False, "scope": 0,
        "selected": False, "visibility": visible, "_static": {"properties": {}, "temp_data": {}},
        "origin": [8, 8, 8], "rotation": [0, 0, 0], "color": 0, "children": [], "reset": False, "shade": True,
        "mirror_uv": False, "autouv": 0, "isOpen": False, "primary_selected": False,
    }


def project(groups):
    elements, bb_groups, outliner = [], [], []
    for gi, (name, els) in enumerate(groups):
        ids = []
        for i, el in enumerate(els):
            e = bb_element(name, i, el)
            e["visibility"] = gi == 0
            elements.append(e)
            ids.append(e["uuid"])
        g = bb_group(name, ids, gi == 0)
        bb_groups.append(g)
        outliner.append({"uuid": g["uuid"], "isOpen": False, "children": ids})
    return {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": "railing", "parent": "", "ambientocclusion": True, "front_gui_light": False,
        "visible_box": [1, 1, 0], "variable_placeholders": "", "multi_file_ruleset": "",
        "variable_placeholder_buttons": [], "unhandled_root_fields": {},
        "resolution": {"width": 16, "height": 16},
        "elements": elements, "groups": bb_groups, "outliner": outliner,
        "textures": [bb_texture(i, k, n) for i, (k, n) in enumerate(TEXTURES)],
    }


def write(path, model):
    path.write_text(json.dumps(model, indent="\t") + "\n")


def main():
    models = [("railing_edge", edge()), ("railing_post", post()), ("railing_port", port()), ("railing_item", item()),
              ("railing_gate_left", leaf(True, False)), ("railing_gate_right", leaf(False, False)),
              ("railing_gate_left_open", leaf(True, True)), ("railing_gate_right_open", leaf(False, True)),
              ("railing_gate_item", gate_item())]
    for name, els in models:
        write(MODELS / f"block/{name}.json", block_model(els))
    write(ART / "railing.bbmodel", project(models))
    for name, els in models:
        print(f"{name}: {len(els)} elements")


if __name__ == "__main__":
    main()
