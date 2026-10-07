#!/usr/bin/env python3
"""H2 part lists: the four wearable hats as hand-made item models (design.md §4.8, §9).

Run (from the repository root): python3 tools/gen_hat_items.py
Output: common/src/main/resources/assets/pirates_n_ships/models/item/{pirate_hat,bandana,navy_hat,officer_hat}.json

The hats copy the hat cubes of the seafarer mobs (art/models/entity/seafarer_models.js, exported to
geo/<type>.geo.json): the soldier's tricorn, the officer's bicorne and the pirate's bandana. Parts are written in
"head" coordinates: (x, y, z) with y and z as in the geo file (head pivot at y 24, the head cube y 24..32, front -z)
and x mirrored (x = -geo x, so +x is the wearer's right). The mapping into the item model is

    item = (8 + x, y - Y0, 8 + z - Z0)

with Y0, Z0 per hat chosen so the hat's bounding box is centred on (8, 8, 8) (GUI, hands, ground and frame use
block-like transforms). The `head` display entry undoes the centring and puts one item pixel on one head pixel:

    scale 1.6, translation (0, 1.6 * (Y0 - 20), 1.6 * Z0), no rotation.

Why: CustomHeadLayer renders a non-armour head item at the head's centre (translate 4 px up, turn 180 about y,
scale 0.625 / -0.625 / -0.625), then the display transform, then -0.5. An item point v (blocks, centred, after
the display transform) lands at head pixel (-10 v.x, 10 v.y + 4, 10 v.z) in the geo frame above the neck pivot, and
GeckoLib's x mirror makes geo x = -10 v.x. Scale 1.6 makes 10 * 1.6 / 16 = 1. HatModelTest checks the result
against the mob geometry.

Angles: element rotations are vanilla's (0, +-22.5, +-45). Rotations are given in item space directly (right-handed;
GeckoLib's x and y rotations come out negated in this frame, so the geo's tricorn wall `y -28.4` on the wearer's right
is `y +22.5` here, the bicorne's front flap `x -18` is `x +22.5`). The tricorn's 28.4 degree walls become 22.5 degree
walls joined by a short flat front piece (a blunted point), which keeps the walls' ends where the mob's are.

Colours come from the item palettes (tools/gen_item_palette.py), no new texture.

Check render: `python3 tools/gen_hat_items.py --render art/renders/hats.png` (needs Pillow) draws each hat through the
same head chain on an 8 px head next to the mob's own hat cubes (front, left side, three-quarter from above; a small
z-buffer rasteriser, flat shaded, colours sampled from the palettes and the mob skins). It does not run the game.
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/assets/pirates_n_ships/models/item"

SHEETS = {"0": "pirates_n_ships:item/palette", "1": "pirates_n_ships:item/palette_2",
          "2": "pirates_n_ships:item/palette_3", "3": "pirates_n_ships:item/palette_4"}
# colour -> (sheet, u, v) of its 4x4 patch
PAL = {
    "black": ("0", 4, 12),          # hat cloth (mob hat 32,30,36)
    "shade": ("1", 4, 12),          # cast_iron_dark: crown sides and the inside of the brims
    "white": ("2", 0, 8),           # fat: the navy edge and the cockade button
    "gold": ("3", 8, 8),            # spice_gold: the officer's edge and loop (mob gold 222,170,48)
    "gold_l": ("0", 12, 4),         # gold: the button
    "gold_d": ("0", 4, 4),          # brass: the loop's sides
    "red": ("0", 8, 12),            # bandana (mob 150,30,36)
    "red_d": ("3", 12, 8),          # spice_dark: bandana hem and knot
    "bone": ("0", 0, 12),           # bandana dots, the skull
}
FACES = ("north", "east", "south", "west", "up", "down")
T225 = math.radians(22.5)
HEAD_SCALE = 1.6


def r(v):
    return round(v + 0.0, 4)


class Part:
    def __init__(self, name, frm, to, colour, faces=None, rot=None):
        """colour: default face colour; faces: {face: colour or None}; rot: (axis, angle, origin) in head coordinates."""
        self.name, self.frm, self.to, self.rot = name, list(frm), list(to), rot
        self.faces = {f: colour for f in FACES}
        self.faces.update(faces or {})

    def corners(self):
        pts = [[x, y, z] for x in (self.frm[0], self.to[0]) for y in (self.frm[1], self.to[1]) for z in (self.frm[2], self.to[2])]
        if not self.rot:
            return pts
        axis, angle, o = self.rot
        a = math.radians(angle)
        c, s = math.cos(a), math.sin(a)
        out = []
        for x, y, z in pts:
            x, y, z = x - o[0], y - o[1], z - o[2]
            if axis == "x":
                y, z = y * c - z * s, y * s + z * c
            elif axis == "y":
                x, z = x * c + z * s, -x * s + z * c
            else:
                x, y = x * c - y * s, x * s + y * c
            out.append([x + o[0], y + o[1], z + o[2]])
        return out


def bbox(parts):
    pts = [p for part in parts for p in part.corners()]
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]


def face_json(face, colour):
    sheet, u, v = PAL[colour]
    return {"uv": [u + 0.5, v + 0.5, u + 3.5, v + 3.5], "texture": "#" + sheet}


# --- display ------------------------------------------------------------------------------------------------------

def rot_xyz(deg, p):
    """ItemTransform's rotation: Quaternionf.rotationXYZ, i.e. R = Rx * Ry * Rz applied to p."""
    rx, ry, rz = (math.radians(d) for d in deg)
    x, y, z = p
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return [x, y, z]


def framed(item_pts, rotation, fill):
    """Scale and translation that make the rotated model's front view `fill` px across and centre it."""
    pts = [rot_xyz(rotation, [p[0] - 8, p[1] - 8, p[2] - 8]) for p in item_pts]
    lo = [min(p[i] for p in pts) for i in range(2)]
    hi = [max(p[i] for p in pts) for i in range(2)]
    scale = round(fill / max(hi[0] - lo[0], hi[1] - lo[1]), 3)
    t = [r(-scale * (lo[i] + hi[i]) / 2) for i in range(2)]
    return {"rotation": rotation, "translation": [t[0], t[1], 0], "scale": [scale] * 3}


def display(item_pts, y0, z0):
    gui = framed(item_pts, [20, 200, 0], 15.0)
    fixed = framed(item_pts, [-15, 0, 0], 13.0)
    ymin = min(p[1] for p in item_pts)
    # ground: ItemEntityRenderer lifts by 0.25 * scale; put the hat's lowest point 1 px above the entity
    g = 0.5
    ground_t = r(16 * (1 / 16 - 0.25 * g - g * (ymin / 16 - 0.5)))
    third = {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3}
    return {
        "thirdperson_righthand": third,
        "thirdperson_lefthand": json.loads(json.dumps(third)),
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, ground_t, 0], "scale": [g] * 3},
        "gui": gui,
        "head": {"rotation": [0, 0, 0], "translation": [0, r(HEAD_SCALE * (y0 - 20)), r(HEAD_SCALE * z0)],
                 "scale": [HEAD_SCALE] * 3},
        "fixed": fixed,
    }


def write(name, parts, note):
    lo, hi = bbox(parts)
    y0 = round((lo[1] + hi[1]) / 2 - 8, 2)
    z0 = round((lo[2] + hi[2]) / 2, 2)
    move = lambda p: [r(8 + p[0]), r(p[1] - y0), r(8 + p[2] - z0)]
    elements, used = [], set()
    for part in parts:
        e = {"name": part.name, "from": move(part.frm), "to": move(part.to)}
        if part.rot:
            axis, angle, o = part.rot
            e["rotation"] = {"angle": angle, "axis": axis, "origin": move(o)}
        faces = {}
        for f in FACES:
            colour = part.faces[f]
            if colour is not None:
                faces[f] = face_json(f, colour)
                used.add(PAL[colour][0])
        e["faces"] = faces
        elements.append(e)
    textures = {k: SHEETS[k] for k in sorted(used)}
    textures["particle"] = SHEETS[sorted(used)[0]]
    item_pts = [move(p) for part in parts for p in part.corners()]
    model = {"credit": "Generated by tools/gen_hat_items.py (" + note + ")", "gui_light": "front",
             "textures": textures, "display": display(item_pts, y0, z0), "elements": elements}
    (OUT / (name + ".json")).write_text(json.dumps(model, indent="\t") + "\n")
    print(f"{name}: {len(elements)} elements, head x {r(lo[0])}..{r(hi[0])} y {r(lo[1])}..{r(hi[1])} "
          f"z {r(lo[2])}..{r(hi[2])}, Y0 {y0}, Z0 {z0}")


# --- tricorn (navy_soldier hat, pirate variant) ---------------------------------------------------------------------

# blunted front: two walls at 22.5 degrees from the ends of a short flat front piece
FRONT_HALF, FRONT_Z, WALL_LEN, BRIM_Y, TOP_Y, EDGE = 1.1, -6.0, 11.3, 30.6, 34.2, 0.8


def wall_centre(side):
    """Centre of the side wall (side +1: the wearer's right, +x) and its rotation about y."""
    s, c = math.sin(T225), math.cos(T225)
    return side * (FRONT_HALF + s * WALL_LEN / 2), FRONT_Z + c * WALL_LEN / 2, 22.5 * side


def wall_box(name, side, y0, y1, colour, inner, edge=False):
    cx, cz, angle = wall_centre(side)
    out_face, in_face = ("east", "west") if side > 0 else ("west", "east")
    faces = {"down": None} if edge else {in_face: inner, "down": inner}
    return Part(name, [cx - 0.35, y0, cz - WALL_LEN / 2], [cx + 0.35, y1, cz + WALL_LEN / 2], colour, faces,
                ("y", angle, [cx, BRIM_Y, cz]))


def tricorn(edge_colour, extras):
    parts = [Part("crown", [-4.2, 31, -4.2], [4.2, 33.8, 4.2], "shade", {"up": "black", "down": None})]
    top = TOP_Y - EDGE if edge_colour else TOP_Y
    # back wall (the mob's leans 10 degrees in; upright here) and its floor plate
    parts.append(Part("back", [-5.9, BRIM_Y, 4.0], [5.9, top, 4.7], "black", {"north": "shade", "down": "shade"}))
    parts.append(Part("back_plate", [-5.6, 30.4, 1.6], [5.6, 30.8, 4.4], "black", {"down": "shade"}))
    for side, n in ((1, "right"), (-1, "left")):
        parts.append(wall_box(n, side, BRIM_Y, top, "black", "shade"))
        cx, cz, angle = wall_centre(side)
        x0, x1 = (cx - 2.6, cx + 0.3) if side > 0 else (cx - 0.3, cx + 2.6)
        parts.append(Part(n + "_plate", [x0, 30.4, cz - WALL_LEN / 2 + 0.5], [x1, 30.8, cz + WALL_LEN / 2 - 0.5],
                          "black", {"down": "shade"}, ("y", angle, [cx, BRIM_Y, cz])))
    parts.append(Part("front", [-FRONT_HALF - 0.15, BRIM_Y, FRONT_Z - 0.35], [FRONT_HALF + 0.15, top, FRONT_Z + 0.35],
                      "black", {"south": "shade", "down": "shade"}))
    parts.append(Part("front_plate", [-1.6, 30.4, FRONT_Z], [1.6, 30.8, FRONT_Z + 2.5], "black", {"down": "shade"}))
    if edge_colour:
        parts.append(Part("back_edge", [-5.9, top, 4.0], [5.9, TOP_Y, 4.7], edge_colour, {"down": None}))
        for side, n in ((1, "right"), (-1, "left")):
            parts.append(wall_box(n + "_edge", side, top, TOP_Y, edge_colour, None, edge=True))
        parts.append(Part("front_edge", [-FRONT_HALF - 0.15, top, FRONT_Z - 0.35],
                          [FRONT_HALF + 0.15, TOP_Y, FRONT_Z + 0.35], edge_colour, {"down": None}))
    return parts + extras


def left_wall_part(name, dx0, dx1, y0, y1, dz0, dz1, colour):
    """A part on the outside of the left wall (the wearer's left, -x), in the wall's own frame: dx outwards from the
    wall's centre line, dz from the wall's centre towards the back."""
    cx, cz, angle = wall_centre(-1)
    return Part(name, [cx - dx1, y0, cz + dz0], [cx - dx0, y1, cz + dz1], colour, None, ("y", angle, [cx, BRIM_Y, cz]))


def navy_hat():
    # the cockade sits 2.3..4.1 px behind the wall's front end like the mob's (2.5..4.3 on a 12.2 px wall)
    front = -WALL_LEN / 2
    return tricorn("white", [
        left_wall_part("cockade", 0.3, 0.6, 31.6, 33.4, front + 2.3, front + 4.1, "black"),
        left_wall_part("cockade_button", 0.55, 0.75, 32.2, 32.8, front + 2.8, front + 3.6, "white"),
    ])


def pirate_hat():
    z = FRONT_Z - 0.35
    eye = {"south": None}
    return tricorn(None, [
        Part("bone_a", [-1.4, 31.75, z - 0.17], [1.4, 32.1, z + 0.02], "bone", None, ("z", 45, [0, 31.9, z])),
        Part("bone_b", [-1.4, 31.75, z - 0.17], [1.4, 32.1, z + 0.02], "bone", None, ("z", -45, [0, 31.9, z])),
        Part("skull", [-0.9, 32.2, z - 0.25], [0.9, 33.6, z - 0.05], "bone", {"south": None}),
        Part("jaw", [-0.55, 31.6, z - 0.25], [0.55, 32.2, z - 0.05], "bone", {"south": None}),
        Part("eye_right", [0.15, 32.6, z - 0.3], [0.65, 33.1, z - 0.25], "black", eye),
        Part("eye_left", [-0.65, 32.6, z - 0.3], [-0.15, 33.1, z - 0.25], "black", eye),
    ])


# --- bicorne (navy_officer hat) -------------------------------------------------------------------------------------

def officer_hat():
    parts = [Part("crown", [-4.3, 30.6, -3.2], [4.3, 33.2, 3.2], "shade", {"up": "black", "down": None})]
    for side, n in ((-1, "front"), (1, "back")):
        z0 = -4.75 if side < 0 else 4.05
        rot = ("x", 22.5 if side < 0 else -22.5, [0, 30.4, -4.4 if side < 0 else 4.4])
        out_face, in_face = ("north", "south") if side < 0 else ("south", "north")
        for piece, x0, x1, top in (("centre", -3, 3, 36.4), ("right", 3, 5.6, 35.0), ("left", -5.6, -3, 35.0),
                                   ("right_tip", 5.6, 7.6, 33.4), ("left_tip", -7.6, -5.6, 33.4)):
            parts.append(Part(n + "_" + piece, [x0, 30.4, z0], [x1, top - EDGE, z0 + 0.7], "black",
                              {in_face: "shade", "down": "shade"}, rot))
            parts.append(Part(n + "_" + piece + "_edge", [x0, top - EDGE, z0], [x1, top, z0 + 0.7], "gold",
                              {"down": None}, rot))
    parts.append(Part("end_right", [4.3, 30.4, -4.05], [7.6, 31.6, 4.05], "black", {"down": "shade"}))
    parts.append(Part("end_left", [-7.6, 30.4, -4.05], [-4.3, 31.6, 4.05], "black", {"down": "shade"}))
    flap = ("x", 22.5, [0, 30.4, -4.4])
    parts.append(Part("cockade", [-1.4, 33.6, -5.0], [1.4, 35.2, -4.8], "black", {"south": None}, flap))
    parts.append(Part("loop", [-0.8, 31.6, -5.05], [0.8, 35.6, -4.75], "gold",
                      {"east": "gold_d", "west": "gold_d", "south": None}, flap))
    parts.append(Part("button", [-0.45, 32.0, -5.2], [0.45, 32.9, -5.0], "gold_l", {"south": None}, flap))
    return parts


# --- bandana (pirate) -----------------------------------------------------------------------------------------------

def bandana():
    w = 4.6  # just outside the 0.5 hat layer of a player skin
    parts = [
        Part("band", [-w, 30.0, -w], [w, 32.6, w], "red", {"down": None}),
        Part("hem", [-w, 29.1, -w], [w, 30.0, w], "red_d", {"down": None, "up": None}),
        Part("knot", [-1, 27.0, w - 0.05], [1, 29.4, w + 1.0], "red_d", {"south": "red", "north": None}),
        Part("tail_right", [0.5, 24.2, w + 0.2], [1.6, 27.2, w + 0.7], "red", None, ("z", 22.5, [1.05, 27.2, w + 0.45])),
        Part("tail_left", [-1.4, 24.4, w + 0.2], [-0.4, 27.2, w + 0.7], "red_d", None, ("z", -22.5, [-0.9, 27.2, w + 0.45])),
    ]
    # bone-white dots, three per side, 0.05 px proud of the band
    for i, c in enumerate((-3.0, 0.0, 3.0)):
        y0, y1 = 30.9, 31.5
        parts.append(Part(f"dot_n{i}", [c - 0.3, y0, -w - 0.05], [c + 0.3, y1, -w], "bone", {"south": None}))
        parts.append(Part(f"dot_s{i}", [c - 0.3, y0, w], [c + 0.3, y1, w + 0.05], "bone", {"north": None}))
        parts.append(Part(f"dot_e{i}", [w, y0, c - 0.3], [w + 0.05, y1, c + 0.3], "bone", {"west": None}))
        parts.append(Part(f"dot_w{i}", [-w - 0.05, y0, c - 0.3], [-w, y1, c + 0.3], "bone", {"east": None}))
    return parts


# --- check render ---------------------------------------------------------------------------------------------------

GEO = ROOT / "common/src/main/resources/assets/pirates_n_ships/geo"
TEXTURES = ROOT / "common/src/main/resources/assets/pirates_n_ships/textures"
QUAD = {"north": (0, 2, 6, 4), "south": (1, 5, 7, 3), "west": (0, 1, 3, 2), "east": (4, 6, 7, 5),
        "down": (0, 4, 5, 1), "up": (2, 3, 7, 6)}  # corner indices (x bit 4, y bit 2, z bit 1)


def _rot(axis, deg, p, o=(0, 0, 0)):
    return Part("", p, p, None, None, (axis, deg, list(o))).corners()[0]


def _box(f, t):
    return [[(f, t)[i][0], (f, t)[j][1], (f, t)[k][2]] for i in (0, 1) for j in (0, 1) for k in (0, 1)]


def _item_quads(name, texel):
    m = json.loads((OUT / (name + ".json")).read_text())
    head = m["display"]["head"]
    quads = []
    for e in m["elements"]:
        pts = _box(e["from"], e["to"])
        if "rotation" in e:
            rr = e["rotation"]
            pts = [_rot(rr["axis"], rr["angle"], p, rr["origin"]) for p in pts]
        out = []
        for p in pts:
            v = rot_xyz(head["rotation"], [(p[i] / 16 - 0.5) * head["scale"][i] for i in range(3)])
            v = [v[i] + head["translation"][i] / 16 for i in range(3)]
            out.append([-10 * v[0], 28 + 10 * v[1], 10 * v[2]])  # geo frame (see the module docstring)
        for face, f in e["faces"].items():
            uv = f["uv"]
            col = texel(m["textures"][f["texture"][1:]].split(":")[1], (uv[0] + uv[2]) / 32, (uv[1] + uv[3]) / 32)
            quads.append(([out[i] for i in QUAD[face]], col))
    return quads


def _mob_quads(mob, layer_rows, texel):
    g = json.loads((GEO / (mob + ".geo.json")).read_text())
    hat = next(b for b in g["minecraft:geometry"][0]["bones"] if b["name"] == "hat")
    quads = []
    for c in hat.get("cubes", []):
        infl = c.get("inflate", 0)
        o, s = c["origin"], c["size"]
        f = [o[0] - infl, o[1] - infl, o[2] - infl]
        t = [o[0] + s[0] + infl, o[1] + s[1] + infl, o[2] + s[2] + infl]
        if infl:
            if not layer_rows:
                continue
            f[1] = t[1] - layer_rows * (s[1] + 2 * infl) / s[1]
        # GeckoLib: x mirrored, rotations (-x, -y, z) about the mirrored pivot; back to file x afterwards
        pts = _box([-t[0], f[1], f[2]], [-f[0], t[1], t[2]])
        if "rotation" in c:
            r, pv = c["rotation"], c["pivot"]
            piv = [-pv[0], pv[1], pv[2]]
            pts = [_rot("x", -r[0], _rot("y", -r[1], _rot("z", r[2], p, piv), piv), piv) for p in pts]
        pts = [[-p[0], p[1], p[2]] for p in pts]
        for face, idx in QUAD.items():
            # index bit x = 0 is file +x after the mirror: swap east and west for the UV lookup
            fname = {"east": "west", "west": "east"}.get(face, face)
            uv = c["uv"].get(fname) if isinstance(c["uv"], dict) else None
            if isinstance(c["uv"], dict) and uv is None:
                continue
            col = texel("entity/" + mob, (uv["uv"][0] + uv["uv_size"][0] / 2) / 64,
                        (uv["uv"][1] + uv["uv_size"][1] / 2) / 64) if uv else (150, 30, 36)
            quads.append(([pts[i] for i in idx], col))
    return quads


def render(path):
    from PIL import Image, ImageDraw

    sheets = {}

    def texel(name, u, v):
        if name not in sheets:
            sheets[name] = Image.open(TEXTURES / (name + ".png")).convert("RGB")
        img = sheets[name]
        return img.getpixel((min(int(u * img.width), img.width - 1), min(int(v * img.height), img.height - 1)))

    head = [(_box([-4, 24, -4], [4, 32, 4]), (196, 150, 112))]
    head = [([head[0][0][i] for i in idx], head[0][1]) for idx in QUAD.values()]
    w, h, px = 200, 170, 11
    light = (0.35, 0.8, -0.45)

    def view(quads, yaw, pitch):
        img = Image.new("RGB", (w, h), (232, 234, 238))
        zbuf = [[1e9] * w for _ in range(h)]
        pix = img.load()
        for pts, col in quads:
            q = []
            for p in pts:
                x, y, z = _rot("y", -yaw, [p[0], p[1] - 30, p[2]])
                x, y, z = _rot("x", pitch, [x, y, z])
                q.append((w / 2 + x * px, h * 0.6 - y * px, z))
            n = [(q[1][1] - q[0][1]) * (q[2][2] - q[0][2]) - (q[1][2] - q[0][2]) * (q[2][1] - q[0][1]),
                 (q[1][2] - q[0][2]) * (q[2][0] - q[0][0]) - (q[1][0] - q[0][0]) * (q[2][2] - q[0][2]),
                 (q[1][0] - q[0][0]) * (q[2][1] - q[0][1]) - (q[1][1] - q[0][1]) * (q[2][0] - q[0][0])]
            ln = math.sqrt(sum(c * c for c in n)) or 1
            shade = 0.6 + 0.4 * abs(n[0] * light[0] - n[1] * light[1] + n[2] * light[2]) / ln
            c = tuple(int(v * shade) for v in col)
            for tri in ((q[0], q[1], q[2]), (q[0], q[2], q[3])):
                (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = tri
                den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
                if abs(den) < 1e-9:
                    continue
                for yy in range(max(0, int(min(y0, y1, y2))), min(h, int(max(y0, y1, y2)) + 1)):
                    for xx in range(max(0, int(min(x0, x1, x2))), min(w, int(max(x0, x1, x2)) + 1)):
                        a = ((y1 - y2) * (xx + 0.5 - x2) + (x2 - x1) * (yy + 0.5 - y2)) / den
                        b = ((y2 - y0) * (xx + 0.5 - x2) + (x0 - x2) * (yy + 0.5 - y2)) / den
                        if a < 0 or b < 0 or a + b > 1:
                            continue
                        z = a * z0 + b * z1 + (1 - a - b) * z2 - 1e-3 * (col != (196, 150, 112))
                        if z < zbuf[yy][xx]:
                            zbuf[yy][xx] = z
                            pix[xx, yy] = c
        return img

    rows = [("navy_hat", "navy_soldier", 0), ("pirate_hat", "navy_soldier", 0),
            ("officer_hat", "navy_officer", 0), ("bandana", "pirate", 3)]
    views = [(0, 0), (90, 0), (-35, -25)]
    sheet = Image.new("RGB", (w * 2 * len(views), h * len(rows)), (255, 255, 255))
    for r_, (item, mob, layer) in enumerate(rows):
        for c_, (yaw, pitch) in enumerate(views):
            sheet.paste(view(head + _item_quads(item, texel), yaw, pitch), (c_ * 2 * w, r_ * h))
            sheet.paste(view(head + _mob_quads(mob, layer, texel), yaw, pitch), ((c_ * 2 + 1) * w, r_ * h))
    d = ImageDraw.Draw(sheet)
    for x in range(1, len(views) * 2):
        d.line([(x * w, 0), (x * w, h * len(rows))], fill=(255, 255, 255) if x % 2 else (90, 90, 90), width=1 if x % 2 else 2)
    sheet.save(path)


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "--render":
        render(sys.argv[2])
        return
    write("navy_hat", navy_hat(), "the navy soldier's tricorn, geo/navy_soldier.geo.json")
    write("pirate_hat", pirate_hat(), "the soldier's tricorn in black with a skull")
    write("officer_hat", officer_hat(), "the navy officer's bicorne, geo/navy_officer.geo.json")
    write("bandana", bandana(), "the pirate's bandana, geo/pirate.geo.json")


if __name__ == "__main__":
    main()
