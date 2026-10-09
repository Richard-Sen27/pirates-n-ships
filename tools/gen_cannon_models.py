#!/usr/bin/env python3
"""F7g/CAN2 part lists: the two-block cannon and the swivel gun as Java block model JSON (elements with names).

Writes <out>/<name>.json for the cannon's parts (CAN2: the barrel tilts, so the cannon is split): cannon_carriage
(the block model of an empty gun), cannon_carriage_rammer (powder and loaded: the rammer leans on the carriage),
cannon_quoin (the elevation wedge, slid and squeezed by the renderer), cannon_barrel, cannon_barrel_powder (the priming
quill in the vent), cannon_barrel_loaded (quill and ball), the item model cannon (barrel + quoin + carriage), and
swivel_gun_yoke, swivel_gun_barrel, swivel_gun_barrel_loaded and swivel_gun. Blockbench builds the projects and
renders from these.

Workflow: run it into a scratch folder, run tools/lint_models.py --fix on the parts there (with --mirror cannon_barrel
cannon_barrel_powder, cannon_barrel_powder cannon_barrel_loaded and cannon_carriage cannon_carriage_rammer), then
`gen_cannon_models.py --compose <folder>` rewrites the item model cannon.json from the fixed parts, so the item is
exactly barrel + quoin + carriage (HandMadeModelsTest.cannonSplitsIntoBarrelQuoinAndCarriage).
"""
import json
import math
import sys
from pathlib import Path

T = math.tan(math.radians(22.5))
OUT = Path(sys.argv[-1])

# texture variables (key -> vanilla id), the same set as the one-block cannon of F7f
TEX = {
    "iron": "minecraft:block/anvil",
    "black": "minecraft:block/black_concrete",
    "cheek": "minecraft:block/dark_oak_planks",
    "axle": "minecraft:block/stripped_dark_oak_log",
    "axle_end": "minecraft:block/stripped_dark_oak_log_top",
    "plank": "minecraft:block/spruce_planks",
    "wheel": "minecraft:block/stripped_spruce_log_top",
    "pole": "minecraft:block/stripped_spruce_log",
}


def r(v):
    return round(v, 4)


def wrap(a, b):
    """A position span as a UV span inside 0..16."""
    length = b - a
    if length >= 16:
        return 0.0, 16.0
    s = a % 16
    if s + length > 16:
        if s + length - 16 < 1e-6:
            return s, 16.0
        return 16 - length, 16.0
    return s, s + length


def face_uv(d, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if d in ("north", "south"):
        u = wrap(x0, x1)
        v = wrap(16 - y1, 16 - y0)
    elif d in ("east", "west"):
        u = wrap(z0, z1)
        v = wrap(16 - y1, 16 - y0)
    else:
        u = wrap(x0, x1)
        v = wrap(z0, z1)
    return [r(u[0]), r(v[0]), r(u[1]), r(v[1])]


DIRS = ("north", "east", "south", "west", "up", "down")


class Model:
    def __init__(self):
        self.elements = []

    def box(self, name, f, t, tex, rot=None, faces=None, uvrot=None):
        """tex: a texture key or a dict per face (None hides the face); rot: (axis, angle, origin)."""
        e = {"name": name, "from": [r(v) for v in f], "to": [r(v) for v in t]}
        if rot:
            e["rotation"] = {"angle": rot[1], "axis": rot[0], "origin": [r(v) for v in rot[2]]}
        fs = {}
        for d in DIRS:
            k = tex.get(d) if isinstance(tex, dict) else tex
            if k is None:
                continue
            face = {"uv": face_uv(d, f, t)}
            if uvrot and d in uvrot:
                face["rotation"] = uvrot[d]
            face["texture"] = "#" + k
            fs[d] = face
        e["faces"] = fs
        self.elements.append(e)
        return self

    def oct(self, name, axis, c, D, a0, a1, side, end0=None, end1=None, eps=0.04, four=True, uvrot=None):
        """A regular octagon prism of width D along axis from a0 to a1, centre c = (u, v) in the other two axes.

        axis z: c = (x, y); axis x: c = (y, z); axis y: c = (x, z). end0 is the face at a0, end1 at a1
        (texture key, None to hide; default side)."""
        if end0 is None and end0 != "":
            end0 = side
        if end1 is None:
            end1 = side
        end0 = None if end0 == "-" else end0
        end1 = None if end1 == "-" else end1
        w = D * T
        lo, hi = {"z": ("north", "south"), "x": ("west", "east"), "y": ("down", "up")}[axis]

        def mk(du, dv, inset):
            p0, p1 = a0 + inset, a1 - inset
            if axis == "z":
                return ([c[0] - du / 2, c[1] - dv / 2, p0], [c[0] + du / 2, c[1] + dv / 2, p1])
            if axis == "x":
                return ([p0, c[0] - du / 2, c[1] - dv / 2], [p1, c[0] + du / 2, c[1] + dv / 2])
            return ([c[0] - du / 2, p0, c[1] - dv / 2], [c[0] + du / 2, p1, c[1] + dv / 2])

        tex = {d: side for d in DIRS}
        tex[lo] = end0
        tex[hi] = end1
        mid = (a0 + a1) / 2
        if axis == "z":
            origin = [c[0], c[1], mid]
        elif axis == "x":
            origin = [mid, c[0], c[1]]
        else:
            origin = [c[0], mid, c[1]]
        f, t = mk(D, w, 0)
        self.box(name + "_a", f, t, tex, uvrot=uvrot)
        f, t = mk(w, D, 0)
        self.box(name + "_b", f, t, tex, uvrot=uvrot)
        if four:
            ins = min(eps, (a1 - a0) / 4)
            f, t = mk(D, w, ins)
            self.box(name + "_c", f, t, tex, rot=(axis, 45, origin), uvrot=uvrot)
            self.box(name + "_d", f, t, tex, rot=(axis, -45, origin), uvrot=uvrot)
        return self

    def json(self, display=None, credit=None):
        used = []
        for e in self.elements:
            for face in e["faces"].values():
                k = face["texture"][1:]
                if k not in used:
                    used.append(k)
        order = [k for k in TEX if k in used]
        index = {k: str(i) for i, k in enumerate(order)}
        elements = []
        for e in self.elements:
            e = json.loads(json.dumps(e))
            for face in e["faces"].values():
                face["texture"] = "#" + index[face["texture"][1:]]
            elements.append(e)
        textures = {index[k]: TEX[k] for k in order}
        textures["particle"] = TEX["iron"]
        out = {"credit": credit, "parent": "minecraft:block/block", "textures": textures, "elements": elements}
        if display:
            out["display"] = display
        return out


# ---------------------------------------------------------------- cannon (master frame, muzzle north)
AX = (8, 14)  # barrel axis x, y
# CAN2: the cheeks are 1.3 px thick (were 1.8), so the base ring (D 8.3) clears them when the breech drops
CHEEK_L = (2.5, 3.8)
CHEEK_R = (12.2, 13.5)
# the stool bed under the breech (the quoin rests on it) and the quoin's rest box; CannonBarrelPose has the same numbers
STOOL_TOP = 5.0
QUOIN = ([6.4, STOOL_TOP, 18.8], [9.6, 9.6, 25.6])


def cannon_barrel(m):
    # sections from the muzzle (z -15) back to the breech (z 24): (name, z0, z1, D)
    sections = [
        ("muzzle_lip", -15, -14.2, 7.0),
        ("muzzle_swell", -14.2, -12.6, 6.5),
        ("astragal", -12.6, -12.0, 6.3),
        ("chase_front", -12.0, -4.0, 5.8),
        ("chase_rear", -4.0, 1.6, 6.2),
        ("chase_ring", 1.6, 2.4, 7.0),
        ("reinforce2", 2.4, 12.4, 6.9),
        ("vent_ring", 12.4, 13.2, 7.7),
        ("reinforce1", 13.2, 21.4, 7.5),
        ("base_ring", 21.4, 22.6, 8.3),
        ("breech", 22.6, 24.0, 7.2),
        ("neck", 24.0, 24.9, 2.2),
        ("cascabel", 24.9, 26.0, 3.2),
    ]
    for name, z0, z1, D in sections:
        m.oct(name, "z", AX, D, z0, z1, "iron")
    # bore: a black disc 0.02 px in front of the muzzle
    m.oct("bore", "z", AX, 3.6, -15.02, -15.0, "black", end1="-", eps=0.0)
    # vent (touch hole) on the vent ring
    m.box("vent", [7.6, 17.7, 12.6], [8.4, 18.0, 13.0], "black")
    # trunnions along x through the pivot (8, 14, 8)
    m.oct("trunnion", "x", (14, 8), 2.4, 2.4, 13.6, "iron")
    return m


def wheel(m, name, x0, x1, cy, cz, D, outer):
    """A wooden truck (disc about x) with an iron tire and a hub cap on the outer side (outer = -1 or +1)."""
    m.oct("wheel_" + name, "x", (cy, cz), D - 0.3, x0, x1, "plank", end0="wheel", end1="wheel")
    m.oct("tire_" + name, "x", (cy, cz), D, x0 + 0.15, x1 - 0.15, "iron", end0="-", end1="-")
    hx = (x0 - 0.4, x0) if outer < 0 else (x1, x1 + 0.4)
    m.oct("hub_" + name, "x", (cy, cz), 1.6, hx[0], hx[1], "iron", four=False)


def cannon_carriage(m):
    for side, (x0, x1) in (("l", CHEEK_L), ("r", CHEEK_R)):
        # cheeks stepping down toward the rear (z 1..31)
        m.box("cheek_front_" + side, [x0, 3.5, 1], [x1, 12.6, 13], "cheek")
        m.box("cheek_step1_" + side, [x0, 3.5, 13], [x1, 11, 19], "cheek")
        m.box("cheek_step2_" + side, [x0, 3.5, 19], [x1, 9, 25], "cheek")
        m.box("cheek_step3_" + side, [x0, 3.5, 25], [x1, 7, 31], "cheek")
        # cap square over the trunnion
        m.box("cap_top_" + side, [x0 - 0.1, 15.2, 6.4], [x1 + 0.1, 15.6, 9.6], "iron")
        m.box("cap_front_" + side, [x0 - 0.1, 12.6, 6.4], [x1 + 0.1, 15.2, 6.8], "iron")
        m.box("cap_back_" + side, [x0 - 0.1, 12.6, 9.2], [x1 + 0.1, 15.2, 9.6], "iron")
        # iron straps down the outer side
        sx = (x0 - 0.15, x0) if side == "l" else (x1, x1 + 0.15)
        m.box("strap_front_" + side, [sx[0], 3.5, 2.2], [sx[1], 12.65, 2.8], "iron")
        m.box("strap_mid_" + side, [sx[0], 3.5, 11.2], [sx[1], 12.65, 11.8], "iron")
        m.box("strap_rear_" + side, [sx[0], 3.5, 27.2], [sx[1], 7.05, 27.8], "iron")
        # breeching rope eye and ring at the rear of each cheek
        cx = (x0 + x1) / 2
        m.box("eye_" + side, [cx - 0.3, 5.0, 31], [cx + 0.3, 5.6, 31.7], "iron")
        m.oct("ring_" + side, "x", (4.4, 31.35), 1.2, cx - 0.15, cx + 0.15, "iron", eps=0.01)
    # bed planks (split at the block border), front and rear transoms
    m.box("bed_front", [CHEEK_L[1], 3.5, 1.5], [CHEEK_R[0], 4.3, 16], "plank")
    m.box("bed_rear", [CHEEK_L[1], 3.5, 16], [CHEEK_R[0], 4.3, 30.5], "plank")
    m.box("transom", [CHEEK_L[1], 4.3, 1.5], [CHEEK_R[0], 8.5, 3.2], "cheek")
    m.box("rear_transom", [CHEEK_L[1], 4.3, 28.8], [CHEEK_R[0], 6.5, 30.5], "cheek")
    # the stool bed under the breech, low enough for the breech at the top elevation (CAN2); the quoin is its own part
    m.box("stool_bed", [6.0, 4.3, 18.5], [10.0, STOOL_TOP, 26.5], "plank")
    # axletrees (grain along x) and the four trucks: front larger (D 7) than rear (D 6)
    grain = {"north": 90, "south": 90, "up": 90, "down": 90}
    axle_tex = {"north": "axle", "south": "axle", "up": "axle", "down": "axle", "east": "axle_end", "west": "axle_end"}
    m.box("axletree_front", [1.6, 2.6, 3.0], [14.4, 4.6, 5.0], axle_tex, uvrot=grain)
    m.box("axletree_rear", [1.6, 2.0, 26.0], [14.4, 4.0, 28.0], axle_tex, uvrot=grain)
    wheel(m, "fl", 0.4, 1.6, 3.6, 4.0, 7.2, -1)
    wheel(m, "fr", 14.4, 15.6, 3.6, 4.0, 7.2, +1)
    wheel(m, "bl", 0.4, 1.6, 3.0, 27.0, 6.0, -1)
    wheel(m, "br", 14.4, 15.6, 3.0, 27.0, 6.0, +1)
    return m


def quoin(m):
    """The wedge under the breech, handle to the rear; its bottom face sits on the stool bed and is left out."""
    tex = {d: "plank" for d in DIRS}
    tex["down"] = None
    m.box("quoin", QUOIN[0], QUOIN[1], tex)
    m.box("quoin_handle", [7.5, 8.1, 25.6], [8.5, 9.1, 27.0], "axle")
    return m


def quill(m):
    """The priming quill in the vent (powder and loaded): a thin light stick standing in the touch hole."""
    tex = {d: "pole" for d in DIRS}
    tex["up"] = tex["down"] = "wheel"
    m.box("quill", [7.75, 17.75, 12.65], [8.25, 19.4, 12.95], tex)
    return m


def rammer(m):
    """The rammer leaning against the right cheek's steps, foot on the deck, head up and back (+x 22.5)."""
    foot = (14.15, 0.3, 15.0)
    rot = ("x", 22.5, list(foot))
    cx, cz = foot[0], foot[2]
    pole = {d: "pole" for d in DIRS}
    pole["up"] = pole["down"] = "wheel"
    m.box("rammer_pole_a", [cx - 0.4, foot[1], cz - 0.28], [cx + 0.4, 13.4, cz + 0.28], pole, rot=rot)
    m.box("rammer_pole_b", [cx - 0.28, foot[1], cz - 0.4], [cx + 0.28, 13.4, cz + 0.4], pole, rot=rot)
    head = {d: "plank" for d in DIRS}
    head["up"] = head["down"] = "wheel"
    m.box("rammer_head_a", [cx - 0.75, 13.2, cz - 1.05], [cx + 0.75, 15.4, cz + 1.05], head, rot=rot)
    m.box("rammer_head_b", [cx - 1.05, 13.2, cz - 0.75], [cx + 0.75 + 0.3, 15.4, cz + 0.75], head, rot=rot)
    return m


def ball(m, axis_xy, z_face, D):
    """A ball in the muzzle: a grey disc in front of the bore and a smaller cap, so a black ring stays visible."""
    m.oct("ball", "z", axis_xy, D, z_face - 0.35, z_face, "iron", end1="-", eps=0.01)
    m.oct("ball_cap", "z", axis_xy, D * 0.55, max(-16.0, z_face - 0.35 - D * 0.22), z_face - 0.35, "iron", end1="-", eps=0.01)
    return m


def barrel(variant):
    """The barrel part of a load state: the tube, plus the priming quill once powdered and the ball once loaded."""
    m = cannon_barrel(Model())
    if variant in ("powder", "loaded"):
        quill(m)
    if variant == "loaded":
        ball(m, AX, -15.02, 2.9)
    return m


def carriage(rammer_on):
    m = cannon_carriage(Model())
    if rammer_on:
        rammer(m)
    return m


def compose(folder):
    """Rewrites <folder>/cannon.json (the item) as barrel + quoin + carriage from the (lint-fixed) part files."""
    key = {v: k for k, v in TEX.items()}
    m = Model()
    for n in ("cannon_barrel", "cannon_quoin", "cannon_carriage"):
        part = json.loads((folder / (n + ".json")).read_text())
        for e in part["elements"]:
            for face in e["faces"].values():
                face["texture"] = "#" + key[part["textures"][face["texture"][1:]]]
            m.elements.append(e)
    item = json.loads((folder / "cannon.json").read_text())
    data = m.json(item.get("display"), item.get("credit"))
    (folder / "cannon.json").write_text(json.dumps(data, indent=2) + "\n")
    print("cannon", len(m.elements), "composed from the parts")


# ---------------------------------------------------------------- swivel gun (pivot (8, 6, 8), muzzle north)
SP = (8, 6)


def swivel_yoke(m):
    m.oct("pintle", "y", (8, 8), 1.8, 0.0, 3.0, "iron")
    m.oct("collar", "y", (8, 8), 3.0, 0.0, 0.8, "iron")
    m.box("yoke_bar", [4.2, 2.8, 7.2], [11.8, 3.8, 8.8], "iron")
    for side, (x0, x1) in (("l", (4.2, 5.2)), ("r", (10.8, 11.8))):
        m.box("arm_" + side, [x0, 3.8, 7.25], [x1, 6.0, 8.75], "iron")
        m.oct("arm_end_" + side, "x", (6.0, 8.0), 1.9, x0, x1, "iron")
    return m


def swivel_barrel(m, loaded=False):
    sections = [
        ("muzzle_lip", -6.0, -5.4, 3.6),
        ("muzzle_swell", -5.4, -4.6, 3.2),
        ("chase", -4.6, 2.0, 2.8),
        ("chase_ring", 2.0, 2.6, 3.4),
        ("reinforce", 2.6, 12.0, 3.2),
        ("base_ring", 12.0, 12.8, 3.9),
        ("breech", 12.8, 14.0, 3.4),
        ("socket", 14.0, 15.2, 1.7),
    ]
    for name, z0, z1, D in sections:
        m.oct(name, "z", SP, D, z0, z1, "iron")
    m.oct("bore", "z", SP, 1.7, -6.02, -6.0, "black", end1="-", eps=0.0)
    m.oct("trunnion", "x", (6.0, 8.0), 1.3, 5.0, 11.0, "iron")
    grain = {"east": 90, "west": 90}
    tiller = {"north": "wheel", "south": "wheel", "east": "pole", "west": "pole", "up": "pole", "down": "pole"}
    m.box("tiller_a", [7.55, 5.5, 15.2], [8.45, 6.5, 20.0], tiller, uvrot=grain)
    m.box("tiller_b", [7.5, 5.55, 15.2], [8.5, 6.45, 20.0], tiller, uvrot=grain)
    m.box("tiller_knob", [7.35, 5.35, 19.2], [8.65, 6.65, 20.0], "iron")
    if loaded:
        ball(m, SP, -6.02, 1.4)
    return m


def gui_translation(rot, scale, centre):
    """Display translation (px) that puts the model's bounding-box centre into the slot's centre."""
    rx, ry, rz = (math.radians(a) for a in rot)
    p = [(c - 8) * scale for c in centre]
    # vanilla applies rotation as XYZ intrinsic (Quaternion from rotationXYZ)
    def Rx(v, a):
        return [v[0], v[1] * math.cos(a) - v[2] * math.sin(a), v[1] * math.sin(a) + v[2] * math.cos(a)]

    def Ry(v, a):
        return [v[0] * math.cos(a) + v[2] * math.sin(a), v[1], -v[0] * math.sin(a) + v[2] * math.cos(a)]

    def Rz(v, a):
        return [v[0] * math.cos(a) - v[1] * math.sin(a), v[0] * math.sin(a) + v[1] * math.cos(a), v[2]]

    v = Rx(Ry(Rz(p, rz), ry), rx)
    return [r(-v[0]), r(-v[1]), r(-v[2])]


# CAN2: the cannon's parts are exported from the three cannon projects (groups barrel, quoin, carriage)
PROJECT = {"cannon_barrel": "cannon", "cannon_quoin": "cannon", "cannon_carriage": "cannon",
           "cannon_barrel_powder": "cannon_powder", "cannon_carriage_rammer": "cannon_powder",
           "cannon_barrel_loaded": "cannon_loaded"}


def write(name, model, display=None):
    data = model.json(display, "Made with Blockbench, source art/models/%s.bbmodel" % PROJECT.get(name, name))
    (OUT / (name + ".json")).write_text(json.dumps(data, indent=2) + "\n")
    xs = [v for e in model.elements for v in (e["from"][0], e["to"][0])]
    ys = [v for e in model.elements for v in (e["from"][1], e["to"][1])]
    zs = [v for e in model.elements for v in (e["from"][2], e["to"][2])]
    print(name, len(model.elements), "x", min(xs), max(xs), "y", min(ys), max(ys), "z", min(zs), max(zs))


def disp(rot, trans, s):
    return {"rotation": rot, "translation": trans, "scale": [s, s, s]}


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--compose":
        compose(OUT)
        sys.exit(0)
    OUT.mkdir(parents=True, exist_ok=True)
    # cannon item: bounding box x 0..16, y 0..18.2, z -16..32 (centre (8, 9.1, 8))
    cc = (8, 9.1, 8)
    cannon_display = {
        "gui": disp([30, 225, 0], [0.5, -0.7, 0], 0.33),
        "ground": disp([0, 0, 0], [0, 2, 0], 0.15),
        "fixed": disp([0, 90, 0], [0, -0.5, 0], 0.33),
        "head": disp([0, 0, 0], [0, 0, 0], 0.3),
        "thirdperson_righthand": disp([75, 45, 0], [0, 2.5, 0], 0.25),
        "thirdperson_lefthand": disp([75, 45, 0], [0, 2.5, 0], 0.25),
        "firstperson_righthand": disp([0, 45, 0], [0, 1, 0], 0.2),
        "firstperson_lefthand": disp([0, 225, 0], [0, 1, 0], 0.2),
    }
    write("cannon_barrel", barrel("empty"))
    write("cannon_barrel_powder", barrel("powder"))
    write("cannon_barrel_loaded", barrel("loaded"))
    write("cannon_quoin", quoin(Model()))
    write("cannon_carriage", carriage(False))
    write("cannon_carriage_rammer", carriage(True))
    whole = barrel("empty")
    quoin(whole)
    cannon_carriage(whole)
    write("cannon", whole, cannon_display)
    write("swivel_gun_yoke", swivel_yoke(Model()))
    write("swivel_gun_barrel", swivel_barrel(Model()))
    write("swivel_gun_barrel_loaded", swivel_barrel(Model(), loaded=True))
    whole = swivel_yoke(Model())
    swivel_barrel(whole)
    # swivel item: bounding box x 4.2..11.8, y 0..7.8, z -6..20 (centre (8, 3.9, 7))
    sc = (8, 3.9, 7)
    swivel_display = {
        "gui": disp([30, 225, 0], [-0.45, 1.8, 0], 0.65),
        "ground": disp([0, 0, 0], [0, 3, 0], 0.3),
        "fixed": disp([0, 90, 0], [0, 2.2, 0], 0.6),
        "head": disp([0, 0, 0], [0, 0, 0], 0.6),
        "thirdperson_righthand": disp([75, 45, 0], [0, 2.5, 0], 0.375),
        "thirdperson_lefthand": disp([75, 45, 0], [0, 2.5, 0], 0.375),
        "firstperson_righthand": disp([0, 45, 0], [0, 2, 0], 0.4),
        "firstperson_lefthand": disp([0, 225, 0], [0, 2, 0], 0.4),
    }
    write("swivel_gun", whole, swivel_display)
