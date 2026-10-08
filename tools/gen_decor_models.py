#!/usr/bin/env python3
"""Part lists of the ship decor block models (ART2, design.md §4.8): ship's lantern, ship's bell, rope coil, stern
window, chart table and sea cot.

Writes the Java block model JSON of every variant to common/src/main/resources/assets/pirates_n_ships/models/block/.
The Blockbench projects in art/models/ (one per block, one group per exported model) were rebuilt cube by cube from
this output, so the committed models and the projects agree; after a change here, run `python3 tools/lint_models.py`
and rebuild the project (art/README.md, "Ship decor (ART2)").

Run from the repository root: python3 tools/gen_decor_models.py
Conventions: pixels, the model faces north (FACING = north needs no rotation); a wall-mounted model hangs on the
south side (z 16). UVs are vanilla's position UVs wrapped into 0..16; log faces along x or z are turned so the grain
follows the part; palette faces map the inner 3 x 3 px of a 4 x 4 patch of textures/item/palette*.png.
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/assets/pirates_n_ships/models/block"
DIRS = ("north", "east", "south", "west", "up", "down")
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
T225 = math.tan(math.radians(22.5))

# texture aliases: name -> (texture id, palette patch (u, v) or None)
PAL = {
    "brass_light": ("pirates_n_ships:item/palette", (0, 4)),
    "brass": ("pirates_n_ships:item/palette", (4, 4)),
    "brass_dark": ("pirates_n_ships:item/palette", (8, 4)),
    "iron_dark": ("pirates_n_ships:item/palette", (12, 0)),
    "steel": ("pirates_n_ships:item/palette", (4, 0)),
}
OWN = {"rope": "pirates_n_ships:block/rope", "map_tile": "pirates_n_ships:block/map_tile"}
LOGS = {"stripped_dark_oak_log", "stripped_spruce_log", "dark_oak_log", "stripped_oak_log"}


def tex_id(name):
    if name in PAL:
        return PAL[name][0]
    if name in OWN:
        return OWN[name]
    return "minecraft:block/" + name


def r5(v):
    v = round(v, 5)
    return int(v) if v == int(v) else v


def wrap(a, b):
    """Shift the interval a..b into 0..16 (a span over 16 maps the whole texture)."""
    if b - a >= 16:
        return 0.0, 16.0
    while a < 0:
        a, b = a + 16, b + 16
    while b > 16:
        a, b = a - 16, b - 16
    return a, b


def default_uv(d, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if d == "north":
        u, v = (16 - x1, 16 - x0), (16 - y1, 16 - y0)
    elif d == "south":
        u, v = (x0, x1), (16 - y1, 16 - y0)
    elif d == "east":
        u, v = (16 - z1, 16 - z0), (16 - y1, 16 - y0)
    elif d == "west":
        u, v = (z0, z1), (16 - y1, 16 - y0)
    elif d == "up":
        u, v = (x0, x1), (z0, z1)
    else:
        u, v = (x0, x1), (16 - z1, 16 - z0)
    u, v = wrap(*u), wrap(*v)
    return [u[0], v[0], u[1], v[1]]


class Model:
    def __init__(self, name, particle, display=None):
        self.name, self.particle, self.display = name, particle, display
        self.elements = []
        self.render_type = None

    def add(self, elements):
        self.elements += elements
        return self


def box(name, f, t, tex, hide=(), ftex=None, rot=None, grain=None, uv=None, cull=True):
    """One element. tex: texture alias for every face; hide: faces left out; ftex: per-face texture overrides;
    rot: (axis, angle, origin); grain: 'x' or 'z' turns log faces so the grain runs along that axis; uv: per-face
    uv overrides."""
    return {"name": name, "from": list(f), "to": list(t), "tex": tex, "hide": set(hide), "ftex": dict(ftex or {}),
            "rot": rot, "grain": grain, "uv": dict(uv or {}), "cull": cull}


def shift(elements, dx=0.0, dy=0.0, dz=0.0):
    out = []
    for e in elements:
        e = dict(e)
        e["from"] = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2] + dz]
        e["to"] = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2] + dz]
        if e["rot"]:
            axis, angle, o = e["rot"]
            e["rot"] = (axis, angle, [o[0] + dx, o[1] + dy, o[2] + dz])
        out.append(e)
    return out


def rotate_all(elements, axis, angle, origin):
    out = []
    for e in elements:
        assert e["rot"] is None, e["name"]
        e = dict(e)
        e["rot"] = (axis, angle, list(origin))
        e["cull"] = False
        out.append(e)
    return out


def compile_model(m):
    textures, keys = {}, {}

    def key(alias):
        tid = tex_id(alias)
        if tid not in keys:
            keys[tid] = str(len(keys))
            textures[keys[tid]] = tid
        return keys[tid]

    elements = []
    for e in m.elements:
        f, t = e["from"], e["to"]
        faces = {}
        for d in DIRS:
            if d in e["hide"]:
                continue
            alias = e["ftex"].get(d, e["tex"])
            if alias is None:
                continue
            axis = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}[d]
            if f[1] == t[1] and d != "up":
                continue        # zero-height decal: only the up face
            face = {}
            if d in e["uv"]:
                face["uv"] = [r5(x) for x in e["uv"][d]]
            elif alias in PAL:
                pu, pv = PAL[alias][1]
                face["uv"] = [pu + 0.5, pv + 0.5, pu + 3.5, pv + 3.5]
            else:
                uv = default_uv(d, f, t)
                g = e["grain"]
                turn = alias in LOGS and g and (
                    (g == "x" and d in ("north", "south", "up", "down")) or (g == "z" and d in ("east", "west", "up", "down")))
                if alias in LOGS and g == "z" and d in ("up", "down"):
                    turn = False   # position UV already runs along z on up/down faces
                if turn:
                    w, h = uv[2] - uv[0], uv[3] - uv[1]
                    a, b = wrap(uv[1], uv[1] + w)
                    c, dd = wrap(uv[0], uv[0] + h)
                    uv = [c, a, dd, b]
                    face["rotation"] = 90
                face = {"uv": [r5(x) for x in uv], **face}
            face["texture"] = "#" + key(alias)
            if e["cull"] and not e["rot"]:
                ax = axis
                coord = f[ax] if d in ("north", "west", "down") else t[ax]
                bound = 0 if d in ("north", "west", "down") else 16
                others = [i for i in range(3) if i != ax]
                inside = all(0 <= f[i] and t[i] <= 16 for i in others)
                if coord == bound and inside:
                    face["cullface"] = d
            faces[d] = face
        el = {"name": e["name"], "from": [r5(x) for x in f], "to": [r5(x) for x in t]}
        if e["rot"]:
            axis, angle, o = e["rot"]
            el["rotation"] = {"angle": angle, "axis": axis, "origin": [r5(x) for x in o]}
        el["faces"] = faces
        elements.append(el)
    key(m.particle)
    out = {"credit": "Made with Blockbench, source art/models/%s.bbmodel" % m.project,
           "parent": "minecraft:block/block"}
    if m.render_type:
        out["render_type"] = m.render_type
    textures["particle"] = tex_id(m.particle)
    out["textures"] = textures
    out["elements"] = elements
    if m.display:
        out["display"] = m.display
    return out


# ---------------------------------------------------------------- helpers for round parts

def crossed(name, cx, cz, d, y0, y1, tex, ratio=0.7, **kw):
    """Two crossed boxes D x ratio*D: a chamfered round section about the vertical axis at (cx, cz)."""
    a, b = d / 2, d * ratio / 2
    return [box(name + "_a", (cx - a, y0, cz - b), (cx + a, y1, cz + b), tex, **kw),
            box(name + "_b", (cx - b, y0, cz - a), (cx + b, y1, cz + a), tex, **kw)]


def ring8(name, r, w, y0, y1, tex, inset=0.05):
    """An octagonal ring of eight bars about the block's vertical centre line: centre-line apothem r, bar width w.
    The four diagonal bars (the north and south bar turned by +-45 degrees) sit `inset` lower at the top and higher
    at the bottom, so their up and down faces never share a plane with the axis-aligned bars they overlap."""
    half = (r + w / 2) * T225          # half the outer side length: the bars meet at the outer corners
    out = []
    n = ((8 - half, y0, 8 - r - w / 2), (8 + half, y1, 8 - r + w / 2))
    s = ((8 - half, y0, 8 + r - w / 2), (8 + half, y1, 8 + r + w / 2))
    e = ((8 + r - w / 2, y0, 8 - half), (8 + r + w / 2, y1, 8 + half))
    wb = ((8 - r - w / 2, y0, 8 - half), (8 - r + w / 2, y1, 8 + half))
    for tag, (f, t) in (("n", n), ("e", e), ("s", s), ("w", wb)):
        out.append(box("%s_%s" % (name, tag), f, t, tex))
    for tag, (f, t), ang in (("ne", n, 45), ("nw", n, -45), ("sw", s, 45), ("se", s, -45)):
        f2 = (f[0], f[1] + inset, f[2])
        t2 = (t[0], t[1] - inset, t[2])
        out.append(box("%s_%s" % (name, tag), f2, t2, tex, rot=("y", ang, [8, (y0 + y1) / 2, 8])))
    return out


def bar_between(name, p0, p1, w, h, tex, axis):
    """A bar from p0 to p1 in the plane normal to `axis` ('x': bar in the yz plane, built along z and turned about x),
    for 45 or 22.5 degree slopes. w: width along `axis`, h: thickness."""
    if axis == "x":
        (y0, z0), (y1, z1) = p0, p1
        length = math.hypot(y1 - y0, z1 - z0)
        cy, cz = (y0 + y1) / 2, (z0 + z1) / 2
        angle = math.degrees(math.atan2(y1 - y0, -(z1 - z0)))    # positive: the north end goes up
        angle = round(angle / 22.5) * 22.5
        return box(name, (8 - w / 2, cy - h / 2, cz - length / 2), (8 + w / 2, cy + h / 2, cz + length / 2), tex,
                   rot=("x", angle, [8, cy, cz]), grain="z")
    raise ValueError(axis)


# ---------------------------------------------------------------- ship's lantern

def lantern_body(dy):
    e = [
        box("base", (4.5, 0, 4.5), (11.5, 1, 11.5), "brass_dark"),
        box("glass", (5, 1, 5), (11, 7, 11), "lantern", hide=("up", "down"),
            uv={d: [0, 3, 6, 9] for d in ("north", "east", "south", "west")}),
    ]
    for i, (x, z) in enumerate(((4.6, 4.6), (10.4, 4.6), (4.6, 10.4), (10.4, 10.4))):
        e.append(box("post%d" % i, (x, 1, z), (x + 1, 7, z + 1), "brass", hide=("up", "down")))
    e += [
        box("top_plate", (4.5, 7, 4.5), (11.5, 8, 11.5), "brass_dark"),
        box("cap", (5.5, 8, 5.5), (10.5, 9, 10.5), "brass", hide=("down",)),
        box("cap_top", (6.5, 9, 6.5), (9.5, 10, 9.5), "brass_dark", hide=("down",)),
        box("ring_left", (6.5, 10, 7.6), (7.25, 12, 8.4), "brass", hide=("up", "down")),
        box("ring_right", (8.75, 10, 7.6), (9.5, 12, 8.4), "brass", hide=("up", "down")),
        box("ring_top", (6.5, 12, 7.6), (9.5, 12.75, 8.4), "brass"),
    ]
    return shift(e, dy=dy)


LANTERN_GUI = {"gui": {"rotation": [30, 225, 0], "translation": [0, 1.2, 0], "scale": [1, 1, 1]},
               "fixed": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.8, 0.8, 0.8]},
               "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
               "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
               "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
               "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]}}


def lanterns():
    floor = Model("ship_lantern", "lantern", LANTERN_GUI).add(lantern_body(0))
    ceiling = Model("ship_lantern_ceiling", "lantern").add(lantern_body(2)).add([
        box("hook", (7.6, 14.75, 7.6), (8.4, 15.5, 8.4), "brass", hide=("up", "down")),
        box("rose", (6, 15.5, 6), (10, 16, 10), "brass_dark"),
    ])
    wall = Model("ship_lantern_wall", "lantern").add(lantern_body(1)).add([
        box("arm", (7.6, 13.75, 7.6), (8.4, 14.55, 15.25), "brass_dark", hide=("south",)),
        box("arm_tip", (7.6, 14.55, 7.6), (8.4, 15.05, 8.4), "brass_dark", hide=("down",)),
        bar_between("brace", (10.5, 15.25), (13.75, 11.75), 0.6, 0.6, "brass", "x"),
        box("wall_block", (6.5, 9.5, 15), (9.5, 15.5, 16), "stripped_dark_oak_log"),
        box("wall_rose", (7, 10, 14.7), (9, 15, 15), "brass_dark", hide=("south",)),
    ])
    for m in (floor, ceiling, wall):
        m.project = "ship_lantern"
    return [floor, ceiling, wall]


# ---------------------------------------------------------------- ship's bell

def bell(h, ringing):
    """The bell hanging from y = h on the vertical centre line; ringing swings it 22.5 degrees about x (the lip
    swings north, away from a wall), the clapper and the lanyard stay plumb."""
    parts = [box("crown", (7.25, h - 1, 7.25), (8.75, h, 8.75), "brass_dark", hide=("up",))]
    parts += crossed("top", 8, 8, 4, h - 2, h - 1, "gold_block")
    parts += crossed("shoulder", 8, 8, 5, h - 3, h - 2, "gold_block")
    parts += crossed("waist", 8, 8, 5.4, h - 6, h - 3, "gold_block")
    parts += crossed("band", 8, 8, 5.6, h - 4.6, h - 4.1, "brass_dark")
    parts += crossed("lip", 8, 8, 6.6, h - 7, h - 6, "brass_dark", ftex={"down": "black_concrete"})
    if ringing:
        parts = rotate_all(parts, "x", 22.5, (8, h, 8))
    clapper = [
        box("clapper", (7.3, h - 7.6, 7.3), (8.7, h - 6.4, 8.7), "iron_dark", hide=("up",)),
        box("lanyard", (7.7, 3, 7.7), (8.3, h - 7.6, 8.3), "rope", hide=("up",)),
        box("lanyard_knot", (7.45, 2.2, 7.45), (8.55, 3, 8.55), "rope"),
    ]
    return parts + clapper


BELL_DISPLAY = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]}}


def post_frame():
    return [
        box("base", (2.5, 0, 6), (13.5, 1.5, 10), "dark_oak_planks"),
        box("upright_left", (3, 1.5, 7), (4.5, 14, 9), "stripped_dark_oak_log", hide=("down",)),
        box("upright_right", (11.5, 1.5, 7), (13, 14, 9), "stripped_dark_oak_log", hide=("down",)),
        box("beam", (2, 14, 6.5), (14, 15.5, 9.5), "dark_oak_log", grain="x",
            ftex={"east": "dark_oak_log_top", "west": "dark_oak_log_top"}),
        box("beam_cap", (7, 15.5, 7), (9, 16, 9), "brass_dark", hide=("down",)),
        box("pin", (7.5, 14.25, 6.2), (8.5, 15.25, 6.5), "brass", hide=("south",)),
        box("pin_back", (7.5, 14.25, 9.5), (8.5, 15.25, 9.8), "brass", hide=("north",)),
    ]


def wall_bracket():
    return [
        box("backboard", (5, 5, 15), (11, 15, 16), "dark_oak_planks"),
        box("backboard_trim", (5.5, 5.5, 14.7), (10.5, 6.25, 15), "stripped_dark_oak_log", grain="x", hide=("south",)),
        box("arm", (7, 13, 7), (9, 15, 14.7), "stripped_dark_oak_log", grain="z", hide=("south",)),
        box("arm_end", (6.75, 12.75, 6.6), (9.25, 15.25, 7), "brass_dark", hide=("south",)),
        bar_between("brace", (8.75, 14.7), (13, 10.45), 1.2, 1.2, "stripped_dark_oak_log", "x"),
    ]


def bells():
    out = []
    for ringing in (False, True):
        sfx = "_ringing" if ringing else ""
        floor = Model("ships_bell" + sfx, "gold_block", None if ringing else BELL_DISPLAY).add(post_frame()).add(bell(14, ringing))
        wall = Model("ships_bell_wall" + sfx, "gold_block").add(wall_bracket()).add(bell(13, ringing))
        for m in (floor, wall):
            m.project = "ships_bell"
        out += [floor, wall]
    return out


# ---------------------------------------------------------------- rope coil

COIL_H = 4


def coil(y, top):
    e = []
    e += ring8("outer", 5.9, 1.6, y, y + 2, "rope")
    e += ring8("inner", 4.25, 1.6, y, y + 2, "rope")
    e += crossed("heart", 8, 8, 6.9, y, y + 1.8, "rope", ratio=0.72, hide=("down",) if y > 0 else ())
    e += ring8("upper", 5.05, 1.6, y + 2, y + 3.9, "rope")
    if top:
        if y == 0:
            e.append(box("end", (10.3, 0, 0.4), (11.9, 1.6, 2.6), "rope"))
        else:
            e.append(box("end", (10.3, y, 0.4), (11.9, y + 1.6, 2.6), "rope", hide=("down",)))
            e.append(box("end_drop", (10.3, 0, 0.4), (11.9, y, 2), "rope", hide=("up",)))
        e.append(box("whipping", (10.25, 0.6, 0.35), (11.95, 1.3, 0.75), "white_wool", hide=("up", "down"))
                 if y > 0 else box("whipping", (10.25, 0.05, 0.35), (11.95, 1.65, 1.0), "white_wool", hide=("down",)))
    return e


COIL_DISPLAY = {"gui": {"rotation": [30, 225, 0], "translation": [0, 2.5, 0], "scale": [0.75, 0.75, 0.75]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
                "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -2], "scale": [0.6, 0.6, 0.6]}}


def rope_coils():
    out = []
    for n in range(1, 5):
        m = Model("rope_coil_layers%d" % n, "rope", COIL_DISPLAY if n == 2 else None)
        for i in range(n):
            m.add([dict(e, name="c%d_%s" % (i + 1, e["name"])) for e in coil(i * COIL_H, i == n - 1)])
        m.project = "rope_coil"
        out.append(m)
    return out


# ---------------------------------------------------------------- stern window

def window_frame():
    return [
        box("stile_left", (0, 0, 0), (2, 16, 16), "stripped_dark_oak_log"),
        box("stile_right", (14, 0, 0), (16, 16, 16), "stripped_dark_oak_log"),
        box("head", (2, 13, 0), (14, 16, 16), "stripped_dark_oak_log", grain="x"),
        box("bottom_rail", (2, 0, 0), (14, 3, 16), "stripped_dark_oak_log", grain="x"),
        box("mullion", (7.5, 3, 2.5), (8.5, 13, 5.5), "dark_oak_planks", hide=("up", "down")),
        box("transom_left", (2, 7.5, 2.5), (7.5, 8.5, 5.5), "dark_oak_planks", hide=("east", "west")),
        box("transom_right", (8.5, 7.5, 2.5), (14, 8.5, 5.5), "dark_oak_planks", hide=("east", "west")),
        box("pane_tl", (2, 8.5, 3.95), (7.5, 13, 4.05), "glass", hide=("east", "west", "up", "down"),
            uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}),
        box("pane_tr", (8.5, 8.5, 3.95), (14, 13, 4.05), "glass", hide=("east", "west", "up", "down"),
            uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}),
        box("pane_bl", (2, 3, 3.95), (7.5, 7.5, 4.05), "glass", hide=("east", "west", "up", "down"),
            uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}),
        box("pane_br", (8.5, 3, 3.95), (14, 7.5, 4.05), "glass", hide=("east", "west", "up", "down"),
            uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}),
        # outside (north): architrave, hood and the brass sill on two corbels
        box("casing_left", (0.25, 3, -0.75), (2, 13, 0), "dark_oak_planks", hide=("south",)),
        box("casing_right", (14, 3, -0.75), (15.75, 13, 0), "dark_oak_planks", hide=("south",)),
        box("hood", (0.25, 13, -1), (15.75, 14, 0), "dark_oak_planks", hide=("south",)),
        box("hood_top", (0, 14, -1.5), (16, 15, 0), "stripped_dark_oak_log", grain="x", hide=("south",)),
        box("sill", (0.5, 2, -1.75), (15.5, 3, 0), "brass", hide=("south",), ftex={"north": "brass_dark", "down": "brass_dark"}),
        box("corbel_left", (2.5, 0.5, -1.25), (4, 2, 0), "dark_oak_planks", hide=("south", "up")),
        box("corbel_right", (12, 0.5, -1.25), (13.5, 2, 0), "dark_oak_planks", hide=("south", "up")),
        # inside (south): the window stool
        box("stool", (1, 2, 16), (15, 3, 17.25), "dark_oak_planks", hide=("north",)),
    ]


def shutters():
    e = [
        box("shutter_left", (2, 3, 1), (8, 13, 2), "dark_oak_trapdoor", hide=("east", "up", "down", "west")),
        box("shutter_right", (8, 3, 1), (14, 13, 2), "dark_oak_trapdoor", hide=("west", "up", "down", "east")),
    ]
    for side, (x0, x1) in (("left", (2.6, 7.4)), ("right", (8.6, 13.4))):
        for row, y in (("top", 10.5), ("bottom", 4.75)):
            e.append(box("strap_%s_%s" % (side, row), (x0, y, 0.7), (x1, y + 0.75, 1), "anvil", hide=("south",)))
    e.append(box("latch", (7.25, 7.6, 0.6), (8.75, 8.4, 1), "iron_dark", hide=("south",)))
    return e


def windows():
    plain = Model("stern_window", "dark_oak_planks").add(window_frame())
    closed = Model("stern_window_shutters", "dark_oak_planks").add(window_frame()).add(shutters())
    for m in (plain, closed):
        m.project = "stern_window"
        m.render_type = "minecraft:cutout"
    return [plain, closed]


# ---------------------------------------------------------------- chart table

def chart_table():
    e = []
    for i, (x, z) in enumerate(((0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5))):
        e += [
            box("leg%d_foot" % i, (x, 0, z), (x + 2, 1.5, z + 2), "stripped_dark_oak_log"),
            box("leg%d_shaft" % i, (x + 0.4, 1.5, z + 0.4), (x + 1.6, 10, z + 1.6), "stripped_dark_oak_log", hide=("up", "down")),
            box("leg%d_top" % i, (x, 10, z), (x + 2, 12, z + 2), "stripped_dark_oak_log", hide=("up",)),
        ]
    e += [
        box("apron_north", (2.5, 10, 0.75), (13.5, 12, 1.75), "dark_oak_planks", hide=("up", "east", "west")),
        box("apron_south", (2.5, 10, 14.25), (13.5, 12, 15.25), "dark_oak_planks", hide=("up", "east", "west")),
        box("apron_west", (0.75, 10, 2.5), (1.75, 12, 13.5), "dark_oak_planks", hide=("up", "north", "south")),
        box("apron_east", (14.25, 10, 2.5), (15.25, 12, 13.5), "dark_oak_planks", hide=("up", "north", "south")),
        box("drawer", (4, 10.3, 0.45), (12, 11.7, 0.75), "stripped_spruce_log", grain="x", hide=("south",)),
        box("drawer_knob", (7.5, 10.75, 0.05), (8.5, 11.25, 0.45), "brass", hide=("south",)),
        box("stretcher_west", (1, 2.5, 2.1), (2, 3.5, 13.9), "stripped_dark_oak_log", grain="z", hide=("north", "south")),
        box("stretcher_east", (14, 2.5, 2.1), (15, 3.5, 13.9), "stripped_dark_oak_log", grain="z", hide=("north", "south")),
        box("stretcher_middle", (2, 2.5, 7.5), (14, 3.5, 8.5), "stripped_dark_oak_log", grain="x", hide=("east", "west")),
        box("top", (-1, 12, -1), (17, 13.5, 17), "dark_oak_planks"),
        box("fiddle_north", (-1, 13.5, -1), (17, 14.25, -0.25), "stripped_dark_oak_log", grain="x", hide=("down",)),
        box("fiddle_south", (-1, 13.5, 16.25), (17, 14.25, 17), "stripped_dark_oak_log", grain="x", hide=("down",)),
        box("fiddle_west", (-1, 13.5, -0.25), (-0.25, 14.25, 16.25), "stripped_dark_oak_log", grain="z", hide=("down", "north", "south")),
        box("fiddle_east", (16.25, 13.5, -0.25), (17, 14.25, 16.25), "stripped_dark_oak_log", grain="z", hide=("down", "north", "south")),
    ]
    # the chart, turned 22.5 degrees, with ink on it (zero-height strips 0.05 px above the paper)
    c = (8, 13.55, 8)
    rot = ("y", 22.5, list(c))
    e.append(box("chart", (2.5, 13.5, 3.5), (13.5, 13.6, 12.5), "map_tile", hide=("down",), rot=rot,
                 uv={"up": [0, 0, 16, 16], "north": [0, 0, 16, 0.5], "south": [0, 15.5, 16, 16],
                     "east": [15.5, 0, 16, 16], "west": [0, 0, 0.5, 16]}))
    ink = [
        ("coast1", (3.2, 4.2), (6.4, 4.6)), ("coast2", (6.0, 4.6), (6.4, 6.8)), ("coast3", (6.4, 6.4), (8.8, 6.8)),
        ("coast4", (8.4, 6.8), (8.8, 8.2)), ("coast5", (8.8, 7.8), (10.6, 8.2)), ("coast6", (10.2, 4.0), (10.6, 7.8)),
        ("rose_ns", (11.6, 9.4), (12.0, 11.8)), ("rose_ew", (10.6, 10.4), (13.0, 10.8)),
    ]
    for name, (x0, z0), (x1, z1) in ink:
        e.append(box("ink_" + name, (x0, 13.65, z0), (x1, 13.65, z1), "black_concrete", rot=rot))
    for i, x in enumerate((3.6, 5.4, 7.2)):
        e.append(box("course%d" % i, (x, 13.65, 11.0), (x + 1.1, 13.65, 11.3), "red_concrete", rot=rot))
    e += [
        box("weight_base", (10.5, 13.6, 3.5), (12.5, 14.4, 5.5), "packed_ice", hide=("down",)),
        box("weight_knob", (11, 14.4, 4), (12, 15, 5), "packed_ice", hide=("down",)),
        box("divider_a", (3.8, 13.65, 9), (4.2, 13.95, 13), "brass", rot=("y", 22.5, [4, 13.8, 9]), hide=("down",)),
        box("divider_b", (3.8, 13.7, 9), (4.2, 14.0, 13), "brass", rot=("y", -22.5, [4, 13.85, 9])),
        box("divider_head", (3.6, 14.05, 8.6), (4.4, 14.35, 9.4), "brass_dark"),
        box("inkwell", (13.2, 13.5, 13.2), (15, 14.75, 15), "black_concrete", hide=("down",)),
        box("inkwell_neck", (13.6, 14.75, 13.6), (14.6, 15.1, 14.6), "black_concrete", hide=("down",)),
        box("quill", (13.9, 14.3, 13.9), (14.3, 18.0, 14.3), "white_wool", rot=("z", -22.5, [14.1, 14.6, 14.1]), hide=("down",)),
    ]
    m = Model("chart_table", "dark_oak_planks",
              {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.56, 0.56, 0.56]}}).add(e)
    m.project = "chart_table"
    return [m]


# ---------------------------------------------------------------- sea cot

def cot_parts():
    """Both halves in one frame, facing north: the head lies at z -16..0, the foot at z 0..16."""
    e = []
    for side, x in (("w", 0.5), ("e", 13.5)):
        e += [
            box("head_post_" + side, (x, 0, -16), (x + 2, 15, -14), "stripped_dark_oak_log"),
            box("head_knob_" + side, (x + 0.25, 15, -15.75), (x + 1.75, 15.75, -14.25), "dark_oak_planks", hide=("down",)),
            box("foot_post_" + side, (x, 0, 14), (x + 2, 12.5, 16), "stripped_dark_oak_log"),
            box("foot_knob_" + side, (x + 0.25, 12.5, 14.25), (x + 1.75, 13.25, 15.75), "dark_oak_planks", hide=("down",)),
        ]
    e += [
        box("headboard", (2.5, 2, -15.5), (13.5, 14, -14.5), "dark_oak_planks", hide=("east", "west", "up")),
        box("headboard_panel", (4, 5, -15.8), (12, 12, -15.5), "stripped_dark_oak_log", hide=("south",)),
        box("headboard_rail", (2.5, 14, -15.75), (13.5, 14.75, -14.25), "stripped_dark_oak_log", grain="x", hide=("east", "west")),
        box("footboard", (2.5, 2, 14.5), (13.5, 11, 15.5), "dark_oak_planks", hide=("east", "west", "up")),
        box("footboard_panel", (4, 4, 15.5), (12, 9.5, 15.8), "stripped_dark_oak_log", hide=("north",)),
        box("footboard_rail", (2.5, 11, 14.25), (13.5, 11.75, 15.75), "stripped_dark_oak_log", grain="x", hide=("east", "west")),
    ]
    for half, (z0, z1) in (("head", (-14, 0)), ("foot", (0, 14))):
        seam = ("south",) if half == "head" else ("north",)
        e += [
            box("side_w_" + half, (1, 2, z0), (2.5, 9.5, z1), "dark_oak_planks", hide=seam + ("up",)),
            box("side_e_" + half, (13.5, 2, z0), (15, 9.5, z1), "dark_oak_planks", hide=seam + ("up",)),
            box("rail_w_" + half, (0.75, 9.5, z0), (2.75, 10.25, z1), "stripped_dark_oak_log", grain="z", hide=seam),
            box("rail_e_" + half, (13.25, 9.5, z0), (15.25, 10.25, z1), "stripped_dark_oak_log", grain="z", hide=seam),
        ]
    e += [
        box("bottom_head", (2.5, 2, -14.5), (13.5, 3, 0), "dark_oak_planks", hide=("south", "north", "east", "west", "up")),
        box("bottom_foot", (2.5, 2, 0), (13.5, 3, 14.5), "dark_oak_planks", hide=("north", "south", "east", "west", "up")),
        box("mattress_head", (2.5, 3, -14.5), (13.5, 8.5, 0), "white_wool", hide=("south", "north", "east", "west", "down")),
        box("blanket_head", (2.5, 8.5, -5), (13.5, 9.1, 0), "red_wool", hide=("south", "east", "west", "down")),
        box("blanket_foot", (2.5, 8.5, 0), (13.5, 9.1, 14.5), "red_wool", hide=("north", "south", "east", "west", "down")),
        box("sheet_fold", (2.5, 9.1, -5), (13.5, 9.5, -3.5), "white_wool", hide=("east", "west", "down")),
        box("pillow", (4, 8.5, -14.25), (12, 10.25, -10.5), "white_wool", hide=("down",)),
        box("pillow_puff", (4.5, 10.25, -13.75), (11.5, 10.75, -11), "white_wool", hide=("down",)),
    ]
    return e


COT_ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.15, 0.15, 0.15]},
    "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
}


def cots():
    parts = cot_parts()
    head = [e for e in parts if e["to"][2] <= 0]
    foot = [e for e in parts if e["from"][2] >= 0]
    assert len(head) + len(foot) == len(parts), "a cot part crosses the seam"
    mh = Model("sea_cot_head", "dark_oak_planks").add(shift(head, dz=16))
    mf = Model("sea_cot_foot", "dark_oak_planks").add(foot)
    mi = Model("sea_cot_item", "dark_oak_planks", COT_ITEM_DISPLAY).add(shift(head + foot, dz=8))
    for m in (mh, mf, mi):
        m.project = "sea_cot"
    for e in mi.elements:
        e["cull"] = False
    return [mh, mf, mi]


def all_models():
    return lanterns() + bells() + rope_coils() + windows() + chart_table() + cots()


def main():
    for m in all_models():
        data = compile_model(m)
        (OUT / (m.name + ".json")).write_text(json.dumps(data, indent=2) + "\n")
        print("%-28s %3d elements, %d textures" % (m.name, len(data["elements"]), len(data["textures"]) - 1))


if __name__ == "__main__":
    main()
