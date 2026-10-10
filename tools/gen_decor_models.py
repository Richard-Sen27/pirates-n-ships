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
import uuid
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
# WOOD3: the texture variables of a wood family's parent model (art/README.md, "Wood families"): the main wood's
# planks, stripped log and its end take a named key that datagen's child per wood sets; every other texture keeps a
# numeric key in first-use order. Only models with `wood = True` (stern window, chart table, sea cot) name them.
WOOD_KEYS = {"dark_oak_planks": "planks", "stripped_dark_oak_log": "log", "stripped_dark_oak_log_top": "log_top"}


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
        self.wood = False

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
            if m.wood and alias in WOOD_KEYS:
                keys[tid] = WOOD_KEYS[alias]
            else:
                keys[tid] = str(sum(1 for k in keys.values() if k.isdigit()))
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
# BELL1: the bell swings about the pin of its yoke, drawn by ShipsBellRenderer. The mounts (post frame, wall bracket)
# are the block models; the bell (yoke and body) and the clapper (with the lanyard) are stand-alone models built for
# the post, hanging from the pin at (y PIN_Y, z 8) and turned about the x axis there. On the wall bracket the renderer
# draws them WALL_DROP px lower (the pin sits under the arm). The item model is post + bell + clapper at rest.

PIN_Y = 13.0
PIN_Z = 8.0
WALL_DROP = 1.2


def bell_body():
    """The yoke round the pin and the bell hanging from it: crossed-box sections (crown, top, shoulder, waist with a
    band, lip with a black mouth) on the vertical centre line, the crown 0.8 px under the pin."""
    h = PIN_Y - 0.8
    parts = [box("yoke", (6.6, PIN_Y - 0.7, PIN_Z - 0.7), (9.4, PIN_Y + 0.7, PIN_Z + 0.7), "brass_dark", cull=False),
             box("crown", (7.25, h - 1, 7.25), (8.75, h, 8.75), "brass_dark", hide=("up",))]
    parts += crossed("top", 8, 8, 4, h - 2, h - 1, "gold_block")
    parts += crossed("shoulder", 8, 8, 5, h - 3, h - 2, "gold_block")
    parts += crossed("waist", 8, 8, 5.4, h - 6, h - 3, "gold_block")
    parts += crossed("band", 8, 8, 5.6, h - 4.6, h - 4.1, "brass_dark")
    parts += crossed("lip", 8, 8, 6.6, h - 7, h - 6, "brass_dark", ftex={"down": "black_concrete"})
    return parts


def bell_clapper():
    """The iron clapper just below the mouth and the rope lanyard hanging from it, turned about the pin like the bell
    (the renderer lets it trail the bell a little)."""
    h = PIN_Y - 0.8
    return [
        box("clapper", (7.3, h - 7.6, 7.3), (8.7, h - 6.4, 8.7), "iron_dark", hide=("up",)),
        box("lanyard", (7.7, 3, 7.7), (8.3, h - 7.6, 8.3), "rope", hide=("up",)),
        box("lanyard_knot", (7.45, 2.2, 7.45), (8.55, 3, 8.55), "rope"),
    ]


def yoke_hangers(pin_y, top):
    """Two iron straps from `top` down past the pin, either side of the yoke, and the brass pin through them along x."""
    return [
        box("hanger_left", (5.6, pin_y - 0.6, 7.4), (6.4, top, 8.6), "iron_dark"),
        box("hanger_right", (9.6, pin_y - 0.6, 7.4), (10.4, top, 8.6), "iron_dark"),
        box("pin", (5.3, pin_y - 0.4, PIN_Z - 0.4), (10.7, pin_y + 0.4, PIN_Z + 0.4), "brass"),
    ]


BELL_DISPLAY = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]}}


def post_frame():
    return [
        box("base", (2.5, 0, 6), (13.5, 1.5, 10), "dark_oak_planks"),
        box("upright_left", (3, 1.5, 7), (4.5, 14, 9), "stripped_dark_oak_log", hide=("down",)),
        box("upright_right", (11.5, 1.5, 7), (13, 14, 9), "stripped_dark_oak_log", hide=("down",)),
        box("beam", (2, 14, 6.5), (14, 15.5, 9.5), "dark_oak_log", grain="x",
            ftex={"east": "dark_oak_log_top", "west": "dark_oak_log_top"}),
        box("beam_cap", (7, 15.5, 7), (9, 16, 9), "brass_dark", hide=("down",)),
    ] + yoke_hangers(PIN_Y, 14.05)


def wall_bracket():
    pin_y = PIN_Y - WALL_DROP
    return [
        box("backboard", (5, 5, 15), (11, 15, 16), "dark_oak_planks"),
        box("backboard_trim", (5.5, 5.5, 14.7), (10.5, 6.25, 15), "stripped_dark_oak_log", grain="x", hide=("south",)),
        box("arm", (7, 13, 7), (9, 15, 14.7), "stripped_dark_oak_log", grain="z", hide=("south",)),
        box("arm_end", (6.75, 12.75, 6.6), (9.25, 15.25, 7), "brass_dark", hide=("south",)),
        box("crosshead", (5.2, 12.75, 7.2), (10.8, 13.45, 8.8), "brass_dark"),
        bar_between("brace", (11, 14.7), (13, 12.7), 1.2, 1.2, "stripped_dark_oak_log", "x"),
    ] + yoke_hangers(pin_y, 12.8)


def bells():
    post = Model("ships_bell_post", "gold_block").add(post_frame())
    wall = Model("ships_bell_wall", "gold_block").add(wall_bracket())
    body = Model("ships_bell_bell", "gold_block").add(bell_body())
    clapper = Model("ships_bell_clapper", "gold_block").add(bell_clapper())
    item = Model("ships_bell", "gold_block", BELL_DISPLAY).add(bell_body()).add(bell_clapper()).add(post_frame())
    out = [post, wall, body, clapper, item]
    for m in out:
        m.project = "ships_bell"
    for m in (body, clapper):
        for e in m.elements:
            e["cull"] = False
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
        m.wood = True
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
    m.wood = True
    return [m]


# ---------------------------------------------------------------- sea cot
# ART20: a bed frame of log rails 3 px tall round a 5 px mattress (y 3..8), the blanket (top 8.6) and a pillow on top,
# so the cot lies at vanilla's bed height; cots side by side join into one berth (block state `left` / `right`, the
# name board's row pattern): each half is a core model plus, per side, either the edge (post, rail, the blanket's
# drape) or the fill that runs the mattress, blanket, pillow and the head- or footboard across to the block's side.
# Facing north the cot's left (seen from its foot, looking along `facing`) is west, x 0; its right is east, x 16.

COT_SEAM = 0.0                 # head z -16..0, foot z 0..16 in the combined frame
COT_RAIL = (2.0, 5.0)          # side rails, 3 px
COT_BOTTOM = (2.0, 3.0)        # the bottom boards under the mattress
COT_MATTRESS = (3.0, 8.0)      # 5 px
COT_BLANKET = (8.0, 8.6)       # the lying surface, under vanilla's 9 px
COT_FOLD = (8.6, 9.0)
COT_PILLOW = (8.0, 9.25)
COT_PUFF = (9.25, 9.6)
COT_HEAD_Z = (-16.0, -14.0)    # the head posts; the headboard z -15.5..-14.5
COT_FOOT_Z = (14.0, 16.0)
COT_BLANKET_Z = (-5.0, 14.5)   # the blanket from below the pillow to the footboard
COT_IN = (-14.5, 14.5)         # inside the head- and footboard
COT_HEAD_TOP, COT_FOOT_TOP = 13.0, 10.0   # the boards' tops, under their cap rails


def mirror_x(elements):
    """The east side's copy of west-side parts: x -> 16 - x, east and west faces swap (hidden ones too)."""
    swap = {"east": "west", "west": "east"}
    out = []
    for e in elements:
        e = dict(e)
        e["from"], e["to"] = [16 - e["to"][0], e["from"][1], e["from"][2]], [16 - e["from"][0], e["to"][1], e["to"][2]]
        e["hide"] = {swap.get(d, d) for d in e["hide"]}
        e["ftex"] = {swap.get(d, d): t for d, t in e["ftex"].items()}
        e["uv"] = {swap.get(d, d): u for d, u in e["uv"].items()}
        e["name"] = e["name"].replace("_w", "_e") if e["name"].endswith("_w") else e["name"] + "_e"
        assert e["rot"] is None, e["name"]
        out.append(e)
    return out


def split_seam(elements):
    """(head, foot): parts crossing the seam are cut there and the cut faces left out, so the halves meet seamlessly."""
    head, foot = [], []
    for e in elements:
        z0, z1 = e["from"][2], e["to"][2]
        if z1 <= COT_SEAM:
            head.append(e)
        elif z0 >= COT_SEAM:
            foot.append(e)
        else:
            h = dict(e, to=[e["to"][0], e["to"][1], COT_SEAM], hide=set(e["hide"]) | {"south"}, name=e["name"] + "_head")
            f = dict(e, **{"from": [e["from"][0], e["from"][1], COT_SEAM]}, hide=set(e["hide"]) | {"north"},
                     name=e["name"] + "_foot")
            head.append(h)
            foot.append(f)
    return head, foot


def cot_core():
    """The middle x 2.5..13.5 of the whole cot: boards, mattress, blanket, pillow. Its sides are inside the edge parts
    (posts, rails, drape) or inside the joined neighbour's mattress, so they are drawn and never seen."""
    hz0, hz1 = -15.5, -14.5
    fz0, fz1 = 14.5, 15.5
    return [
        box("headboard", (2.5, COT_BOTTOM[0], hz0), (13.5, COT_HEAD_TOP, hz1), "dark_oak_planks", hide=("east", "west", "up")),
        box("headboard_panel", (4, 6, hz0 - 0.3), (12, 11.5, hz0), "stripped_dark_oak_log", hide=("south",)),
        box("headboard_rail", (2.5, COT_HEAD_TOP, -15.75), (13.5, COT_HEAD_TOP + 0.75, -14.25), "stripped_dark_oak_log",
            grain="x", hide=("east", "west")),
        box("footboard", (2.5, COT_BOTTOM[0], fz0), (13.5, COT_FOOT_TOP, fz1), "dark_oak_planks", hide=("east", "west", "up")),
        box("footboard_panel", (4, 4, fz1), (12, 8, fz1 + 0.3), "stripped_dark_oak_log", hide=("north",)),
        box("footboard_rail", (2.5, COT_FOOT_TOP, 14.25), (13.5, COT_FOOT_TOP + 0.75, 15.75), "stripped_dark_oak_log",
            grain="x", hide=("east", "west")),
        box("bottom", (2.5, COT_BOTTOM[0], COT_IN[0]), (13.5, COT_BOTTOM[1], COT_IN[1]), "dark_oak_planks",
            hide=("north", "south", "east", "west", "up")),
        # the mattress' up face shows at the head, under the pillow's ends; the blanket covers the rest
        box("mattress", (2.5, COT_MATTRESS[0], COT_IN[0]), (13.5, COT_MATTRESS[1], COT_IN[1]), "white_wool",
            hide=("north", "south", "down")),
        box("blanket", (2.5, COT_BLANKET[0], COT_BLANKET_Z[0]), (13.5, COT_BLANKET[1], COT_BLANKET_Z[1]), "red_wool",
            hide=("south", "down")),
        box("sheet_fold", (2.5, COT_FOLD[0], COT_BLANKET_Z[0]), (13.5, COT_FOLD[1], COT_BLANKET_Z[0] + 1.5), "white_wool",
            hide=("down",)),
        box("pillow", (2.5, COT_PILLOW[0], -14.25), (13.5, COT_PILLOW[1], -10.25), "white_wool", hide=("down",)),
        box("pillow_puff", (4, COT_PUFF[0], -13.75), (12, COT_PUFF[1], -10.75), "white_wool", hide=("down",)),
    ]


def cot_edge_w():
    """The cot's west side where it ends: corner posts with knobs, the side rail and the blanket's drape."""
    e = []
    for end, (z0, z1), top in (("head", COT_HEAD_Z, COT_HEAD_TOP + 1), ("foot", COT_FOOT_Z, COT_FOOT_TOP + 1)):
        e += [
            box(end + "_post_w", (0.5, 0, z0), (2.5, top, z1), "stripped_dark_oak_log"),
            box(end + "_knob_w", (0.75, top, z0 + 0.25), (2.25, top + 0.75, z1 - 0.25), "dark_oak_planks", hide=("down",)),
        ]
    e += [
        box("rail_w", (0.5, COT_RAIL[0], COT_HEAD_Z[1]), (2.5, COT_RAIL[1], COT_FOOT_Z[0]), "stripped_dark_oak_log",
            grain="z", hide=("north", "south")),
        # the blanket hangs over the mattress' side down onto the rail
        box("drape_w", (2.2, COT_RAIL[1], COT_BLANKET_Z[0]), (2.5, COT_BLANKET[1], COT_BLANKET_Z[1]), "red_wool",
            hide=("east", "south", "down")),
    ]
    return e


def cot_fill_w():
    """The cot's west side where a neighbour joins it: everything of the core runs on to x 0 (the faces there meet
    the neighbour's and are left out); no post, no rail."""
    e = []
    for el in cot_core():
        if el["name"] in ("headboard_panel", "footboard_panel", "pillow_puff"):
            continue
        f = dict(el, **{"from": [0, el["from"][1], el["from"][2]]}, to=[2.5, el["to"][1], el["to"][2]],
                 name=el["name"] + "_w", hide=set(el["hide"]) | {"west", "east"})
        e.append(f)
    return e


def cot_halves():
    """{suffix: (head elements, foot elements)} for '' (core), '_left', '_right', '_left_joined', '_right_joined'."""
    parts = {"": cot_core(), "_left": cot_edge_w(), "_right": mirror_x(cot_edge_w()),
             "_left_joined": cot_fill_w(), "_right_joined": mirror_x(cot_fill_w())}
    return {k: split_seam(v) for k, v in parts.items()}


# ART20: the gui looks along the cot from its foot, so the pillow and the headboard stand at the back (ART2's y 225
# looked from the head: the headboard hid the pillow and the sunken mattress showed as white slivers); first person
# holds it crosswise and higher (ART2's lay flat at the screen's bottom edge, mostly out of view); the item frame shows
# it from above, head up. A left-hand entry repeats the right one (ART17; the cot is symmetric along x).
COT_ITEM_DISPLAY = {
    "gui": {"rotation": [30, -35, 0], "translation": [0, 0.5, 0], "scale": [0.45, 0.45, 0.45]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [-90, 180, 0], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_righthand": {"rotation": [0, -60, 0], "translation": [0, 6, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_lefthand": {"rotation": [0, -60, 0], "translation": [0, 6, 0], "scale": [0.3, 0.3, 0.3]},
}


def cots():
    halves = cot_halves()
    out = []
    for suffix, (head, foot) in halves.items():
        out.append(Model("sea_cot_head" + suffix, "dark_oak_planks").add(shift(head, dz=16)))
        out.append(Model("sea_cot_foot" + suffix, "dark_oak_planks").add(foot))
    whole = []
    for suffix in ("", "_left", "_right"):
        head, foot = halves[suffix]
        whole += head + foot
    mi = Model("sea_cot_item", "dark_oak_planks", COT_ITEM_DISPLAY).add(shift(whole, dz=8))
    out.append(mi)
    for m in out:
        m.project = "sea_cot"
        m.wood = True
    for e in mi.elements:
        e["cull"] = False
    return out


def all_models():
    return lanterns() + bells() + rope_coils() + windows() + chart_table() + cots()


# ---------------------------------------------------------------- the sea cot's Blockbench project (ART20)
# ART20 writes art/models/sea_cot.bbmodel from the part list (as tools/gen_railing_models.py does for the railing):
# one group per exported model, the item first and visible; textures by file from art/vanilla/ like ART2's projects.

def det_uuid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "pirates_n_ships/sea_cot/" + "/".join(parts)))


def bb_project(name, compiled):
    """A java_block project of `compiled` [(model name, compiled JSON)], textures in first-use order."""
    tex_ids, textures = [], []
    for _, data in compiled:
        for el in data["elements"]:
            for f in el["faces"].values():
                tid = data["textures"][f["texture"][1:]]
                if tid not in tex_ids:
                    key = f["texture"][1:]
                    tex_ids.append(tid)
                    file = tid.split("/")[-1]
                    textures.append({
                        "name": file + ".png", "path": "", "relative_path": "../vanilla/%s.png" % file,
                        "folder": "block", "namespace": "minecraft", "id": key if not key.isdigit() else str(len(textures)),
                        "group": "", "scope": 0, "width": 16, "height": 16, "uv_width": 16, "uv_height": 16,
                        "particle": key == "planks", "use_as_default": False, "layers_enabled": False,
                        "sync_to_project": "", "file_format": "png", "render_mode": "default", "render_sides": "auto",
                        "wrap_mode": "limited", "pbr_channel": "color", "fps": 7, "frame_time": 1,
                        "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
                        "internal": False, "saved": True, "uuid": det_uuid("texture", file)})
    elements, groups, outliner = [], [], []
    for gi, (gname, data) in enumerate(compiled):
        ids = []
        for i, el in enumerate(data["elements"]):
            faces = {}
            for d in DIRS:
                if d in el["faces"]:
                    f = el["faces"][d]
                    faces[d] = {"uv": f["uv"], "texture": tex_ids.index(data["textures"][f["texture"][1:]])}
                    if "rotation" in f:
                        faces[d]["rotation"] = f["rotation"]
                else:
                    faces[d] = {"uv": [0, 0, 1, 1], "texture": None}
            e = {"name": el["name"], "box_uv": False, "render_order": "default", "rescale": False, "locked": False,
                 "shade_direction_override": "", "light_emission": 0, "export": True, "scope": 0,
                 "allow_mirror_modeling": True, "from": el["from"], "to": el["to"], "autouv": 0, "color": i % 8,
                 "origin": el["rotation"]["origin"] if "rotation" in el else [8, 8, 8], "faces": faces,
                 "type": "cube", "uuid": det_uuid(gname, str(i), el["name"]), "visibility": gi == 0}
            elements.append(e)
            ids.append(e["uuid"])
        g = {"name": gname, "uuid": det_uuid("group", gname), "export": True, "locked": False, "scope": 0,
             "selected": False, "visibility": gi == 0, "_static": {"properties": {}, "temp_data": {}},
             "origin": [8, 8, 8], "rotation": [0, 0, 0], "color": 0, "children": [], "reset": False, "shade": True,
             "mirror_uv": False, "autouv": 0, "isOpen": False, "primary_selected": False}
        groups.append(g)
        outliner.append({"uuid": g["uuid"], "isOpen": False, "children": ids})
    project = {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": name, "parent": "", "ambientocclusion": True, "front_gui_light": False,
        "visible_box": [1, 1, 0], "variable_placeholders": "", "multi_file_ruleset": "",
        "variable_placeholder_buttons": [], "unhandled_root_fields": {},
        "resolution": {"width": 16, "height": 16},
        "elements": elements, "groups": groups, "outliner": outliner, "textures": textures,
    }
    display = next((data["display"] for _, data in compiled if "display" in data), None)
    if display:
        project["display"] = display
    return project


def main():
    cot = []
    for m in all_models():
        data = compile_model(m)
        (OUT / (m.name + ".json")).write_text(json.dumps(data, indent=2) + "\n")
        print("%-28s %3d elements, %d textures" % (m.name, len(data["elements"]), len(data["textures"]) - 1))
        if m.project == "sea_cot":
            cot.append((m.name, data))
    cot.sort(key=lambda c: c[0] != "sea_cot_item")
    (ROOT / "art/models/sea_cot.bbmodel").write_text(json.dumps(bb_project("sea_cot", cot), indent="\t") + "\n")


if __name__ == "__main__":
    main()
