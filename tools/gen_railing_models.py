#!/usr/bin/env python3
"""Part lists of the railing models (RAIL1, RAIL6; design.md §4.8 "Railings (RAIL1/RAIL2)", "Railing rework (RAIL6)").

Writes the hand-made parent models of the railing family to common/src/main/resources/assets/pirates_n_ships/models/
block/ and their Blockbench project art/models/railing.bbmodel (one group per exported model, all at the same place,
only the first visible). RAIL6 (the human, 2026-10-10: "it obviously just looks like a fence ... very bulky ... the
few openings also seem to me so small") turned the fence into an open ship's rail: a cap rail on top, a toe rail at
the deck, two slim turned balusters per block, turned posts with a finial only where a run ends or turns.

All in the north frame (the outboard edge is z 0; the block state turns it to the other edges by y rotation, without
uvlock, so the cap's grain follows the rail on every edge):

- The **cap rail** (every railing, gate leaf and bulwark): a stripped-log handrail y 14..16 on the balusters' line
  (z 1), 4 px wide (z -1..3, overhanging the 2 px balusters 1 px on each side and the block's outer face by 1 px), in
  two steps so it reads rounded: the body y 14..15.5, z -1..3 and a band y 15.5..16, z -0.45..2.45 on it. The grain runs
  along the rail (the long faces' log turned 90 degrees), the ends show the log's end grain (#log_top). Its top stays
  at y 16, where RL5's latch stands and RAIL5's leaners rest their arms.
- block/railing_edge: the cap rail and a plank **toe rail** at the deck (y 0..2, z 0..2). No middle rail.
- block/railing_baluster_west and _east: one **turned baluster** each, at x 4 and x 12 (so the spacing runs on evenly
  across blocks, 8 px apart, a 6 px opening between two shafts): a stripped-log shaft 2 px square from the toe rail to
  the cap (y 2..14, z 0..2) with a turned bulge at y 6..9 (a 3 px belly
  y 6.5..8.5 between 2.5 px collars). Two models because the block state leaves out a baluster
  where a post stands at that end of the edge (the west one with the edge's x-0 corner post, the east one with its
  x-16 corner post), so a post never stands against a baluster.
- block/railing_post: the **turned post** where a run ends or turns (RailingRules.posts): a stripped-log post 3 px
  square centred on the corner of the two rail lines (x and z -0.5..2.5), from the deck to y 16.5 (half a pixel over
  the cap, so the cap rails' tops never fight it), a 2 px finial y 16.5..18.5 and a 1 px tip to y 19.5. The bulwark
  and the rope railing use it too.
- block/railing_port: the port's two posts and a 4 px plank sill between them (x 2.5..13.5, y 0..4, z 0..2).
- block/railing_item: posts at both ends, the cap, the toe rail and one baluster in the middle, centred (z 6..10).
- RAIL3 gate leaf, block/railing_gate_left and _right (between the port's posts, u 0..11 from the hinge post's inner
  face at x 2.5): the cap rail, a plank bottom rail on the sill (y 4..6, z 0..2), one turned baluster in the middle
  (x 8) and iron hinge straps (#iron) round the cap and the bottom rail at the hinge end, reaching 1 px onto the post;
  a latch bar on the cap's inboard face at the other end, its tongue in the latch post, with a grip. left = hinged on
  the west post looking outboard (north), right mirrored. _open: the same leaf turned 90 degrees outboard about the
  hinge corner of the cap (x 2.5 or 13.5, z -1). block/railing_gate_item: posts, sill and the closed left leaf,
  centred.
- RAIL4 bulwark: block/bulwark_edge, a plank wall (x 0..16, y 0..14, z 0.05..2) under the same cap rail; its posts are
  block/railing_post. block/bulwark_port: the jambs x 0..3 and 13..16 (wall and cap ends) and the sill x 3..13; the
  notch is open from the sill up through the cap. block/bulwark_port_lid (x 3..13, y 4..16, z 0.05..1.05, two iron
  straps and their hinge knuckles) and _lid_open (swung up and out about y 16, z 0). block/bulwark_item and
  bulwark_port_item, centred.
- RAIL6 rope railing: block/rope_railing_edge, a 2 px stripped-log stanchion at the edge's x-0 end (x 0..2, z 0..2,
  y 0..16.25, inside the post where the run ends there) and two rope lines along the edge (#rope: pirates_n_ships:block/rope_line, a
  two-strand twist drawn for a 1.5 px strip by tools/gen_rope_line_texture.py; the rigging's block/rope breaks into a
  checker at that thickness and stripped birch reads as a pale wooden rail; 1.5 px thick on the line z 0.25..1.75): the top line y 14.5..16 (its top at the cap's y 16, so
  RL5's latch stands on it) and a middle line y 7..8.5. block/rope_railing_item: posts at both ends and the two lines,
  centred.
- RL5 latch (see below): block/railing_latch on the cap, unchanged by RAIL6 (the cap's top stays at y 16 and the
  washer, z 0.3..2, stands on the cap's band, z -0.5..2.5).

Texture variables #planks, #log and #log_top (vanilla oak planks, stripped oak log and its top here); the wood family's
datagen (ship/decor/wood/WoodFamily) writes one child per wood that overrides them. #iron (anvil), #metal (the
chainplate's palette) and #rope (rope_line) stay fixed.

UVs are vanilla's position UVs wrapped into 0..16 (as tools/gen_ratlines_models.py). After a change run
`python3 tools/lint_models.py` and rebuild the renders (art/README.md, "Railing rework (RAIL6)").

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
LOG_TOP = "minecraft:block/stripped_oak_log_top"
IRON = "minecraft:block/anvil"
METAL = "pirates_n_ships:item/palette_2"  # RL5: the chainplate's iron
ROPE = "pirates_n_ships:block/rope_line"  # RAIL6: the rope railing's lines (tools/gen_rope_line_texture.py), never #log

INSET = 0.05  # the bulwark wall's outer face stands this far in from the block's edge
RAIL_DEPTH = 2  # the balusters' and the toe rail's depth (z 0..2), the line at z 1
LINE = RAIL_DEPTH / 2
CAP = (14, 16)
CAP_STEP = 15.5  # the cap's body ends here, the band runs on to the top
CAP_HALF = 2  # the body z -1..3
BAND_HALF = 1.45  # the band z -0.45..2.45, inside a post's faces (a post is 1.5 either side of the line)
TOE_TOP = 2
BALUSTER_X = (4, 12)
BALUSTER_HALF = 1
BULGE = (6, 9)
BULGE_HALF = 1.5
COLLAR_HALF = 1.25  # the bulge's collars above and below its belly, 2.5 px
COLLAR = 0.5
POST_HALF = 1.5  # the post x and z -0.5..2.5
POST_TOP = 16.5
FINIAL_HALF = 1
FINIAL_TOP = 18.5
TIP_HALF = 0.5
TIP_TOP = 19.5
SILL_TOP = 4
SILL = (LINE - POST_HALF + 2 * POST_HALF, 16 - (LINE - POST_HALF + 2 * POST_HALF))  # x 2.5..13.5, post to post

ALL = ("north", "east", "south", "west", "up", "down")
SIDES = ("north", "east", "south", "west")


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


def uv(face, f, t, turned=False):
    """Vanilla's position UV of a face (north/south x and 16-y, east/west z and 16-y, up/down x and z). turned: the
    rectangle transposed for a face drawn with a 90 degree texture rotation (the log's grain along the face's width)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face in ("north", "south"):
        u, v = span(x0, x1), span(16 - y1, 16 - y0)
    elif face in ("east", "west"):
        u, v = span(z0, z1), span(16 - y1, 16 - y0)
    else:
        u, v = span(x0, x1), span(z0, z1)
    if turned:
        u, v = v, u
    return [r5(u[0]), r5(v[0]), r5(u[1]), r5(v[1])]


def cube(name, f, t, faces):
    """faces: {face: texture key} for the faces that are drawn; a key ending in '@90' is drawn turned 90 degrees."""
    out = {}
    for face, tex in faces.items():
        turned = tex.endswith("@90")
        key = tex[:-3] if turned else tex
        entry = {"uv": uv(face, f, t, turned), "texture": "#" + key}
        if turned:
            entry["rotation"] = 90
        out[face] = entry
    return {"name": name, "from": [r5(c) for c in f], "to": [r5(c) for c in t], "faces": out}


def box(c, half, y0, y1):
    """A box `half` either side of (c[0], c[1]) in x and z, from y0 to y1."""
    return (c[0] - half, y0, c[1] - half), (c[0] + half, y1, c[1] + half)


# ---------------------------------------------------------------------- the cap rail, the railing (RAIL1, RAIL6)

def cap_parts(x, line=LINE, prefix="", ends=True):
    """The cap rail along x[0]..x[1] on the line z = line: the body and the band on it. ends: the end faces drawn (the
    overhang past a post shows them; inside a post or a neighbour's cap they hide)."""
    end = {"east": "log_top", "west": "log_top"} if ends else {}
    long_ = "log@90"
    return [
        cube(prefix + "cap", (x[0], CAP[0], line - CAP_HALF), (x[1], CAP_STEP, line + CAP_HALF),
             {"north": long_, "south": long_, "up": long_, "down": long_, **end}),
        cube(prefix + "cap_band", (x[0], CAP_STEP, line - BAND_HALF), (x[1], CAP[1], line + BAND_HALF),
             {"north": long_, "south": long_, "up": long_, **end}),
    ]


def toe_parts(x=(0, 16), line=LINE, prefix=""):
    return [cube(prefix + "toe_rail", (x[0], 0, line - LINE), (x[1], TOE_TOP, line + LINE),
                 {"north": "planks", "south": "planks", "up": "planks"})]


def baluster_parts(x, line=LINE, y0=TOE_TOP, prefix="baluster"):
    """A turned baluster at x on the line: the 2 px shaft from y0 to the cap and the 3 px bulge."""
    f, t = box((x, line), BALUSTER_HALF, y0, CAP[0])
    return [cube(prefix, f, t, {s: "log" for s in SIDES})] + bulge_parts(x, line, prefix)


def bulge_parts(x, line, prefix):
    """The baluster's turned bulge: a 3 px belly between two 2.5 px collars, so it reads round, not as a block."""
    out = []
    for name, half, (y0, y1) in (("_collar_low", COLLAR_HALF, (BULGE[0], BULGE[0] + COLLAR)),
                                  ("_bulge", BULGE_HALF, (BULGE[0] + COLLAR, BULGE[1] - COLLAR)),
                                  ("_collar_high", COLLAR_HALF, (BULGE[1] - COLLAR, BULGE[1]))):
        bf, bt = box((x, line), half, y0, y1)
        ends = {k: "log_top" for k in ("up", "down") if not (name == "_collar_low" and k == "up" or name == "_collar_high" and k == "down")}
        out.append(cube(prefix + name, bf, bt, {**{s: "log" for s in SIDES}, **ends}))
    return out


def post_parts(c, name="post", y0=0):
    """A turned post centred on (c[0], c[1]): the 3 px post to just over the cap, the finial and its tip."""
    pf, pt = box(c, POST_HALF, y0, POST_TOP)
    ff, ft = box(c, FINIAL_HALF, POST_TOP, FINIAL_TOP)
    tf, tt = box(c, TIP_HALF, FINIAL_TOP, TIP_TOP)
    return [
        cube(name, pf, pt, {**{s: "log" for s in SIDES}, "up": "log_top"}),
        cube(name + "_finial", ff, ft, {**{s: "log" for s in SIDES}, "up": "log_top"}),
        cube(name + "_tip", tf, tt, {**{s: "log" for s in SIDES}, "up": "log_top"}),
    ]


def edge():
    return cap_parts((0, 16)) + toe_parts()


def baluster(west):
    return baluster_parts(BALUSTER_X[0] if west else BALUSTER_X[1])


def post():
    return post_parts((LINE, LINE))


def port(dz=0.0):
    out = post_parts((LINE, LINE + dz), "post_west") + post_parts((16 - LINE, LINE + dz), "post_east")
    out.append(cube("sill", (SILL[0], 0, dz), (SILL[1], SILL_TOP, dz + RAIL_DEPTH),
                    {f: "planks" for f in ("north", "south", "up")}))
    return out


ITEM_DZ = 8 - LINE  # the line through the block's centre (z 8)


def item():
    line = LINE + ITEM_DZ
    return (cap_parts((0, 16), line) + toe_parts(line=line) + baluster_parts(8, line)
            + post_parts((LINE, line), "post_west") + post_parts((16 - LINE, line), "post_east"))


# ---------------------------------------------------------------------- the gate (RAIL3, RAIL6)

HINGE_X = SILL[0]  # the hinge post's inner face; the leaf runs u 0..LEAF from it
LEAF = SILL[1] - SILL[0]
LEAF_RAIL = (SILL_TOP, SILL_TOP + 2)  # the leaf's bottom rail, on the sill
HINGE_PIVOT_W = LINE - CAP_HALF  # the open leaf turns about the cap's outer corner at the hinge post (w -1)
STRAP_U = (-1, 4)  # the straps reach 1 px onto the hinge post


def leaf_parts():
    """The leaf in its own frame: (name, (u0, u1), (y0, y1), (w0, w1), texture faces).

    u runs along the edge from the hinge post's inner face (0) to the latch post's (LEAF), w is the depth from the
    block's outer face (0) inboard. Faces are given closed; open, every face is drawn.
    """
    c = LEAF / 2
    long_ = "log@90"
    cap_ends = {"east": "log_top", "west": "log_top"}
    rail = {"north": "planks", "south": "planks", "up": "planks", "down": "planks"}
    out = [
        ("cap", (0, LEAF), (CAP[0], CAP_STEP), (LINE - CAP_HALF, LINE + CAP_HALF),
         {"north": long_, "south": long_, "up": long_, "down": long_, **cap_ends}),
        ("cap_band", (0, LEAF), (CAP_STEP, CAP[1]), (LINE - BAND_HALF, LINE + BAND_HALF),
         {"north": long_, "south": long_, "up": long_, **cap_ends}),
        ("bottom_rail", (0, LEAF), LEAF_RAIL, (0, RAIL_DEPTH), rail),
        ("baluster", (c - BALUSTER_HALF, c + BALUSTER_HALF), (LEAF_RAIL[1], CAP[0]), (LINE - BALUSTER_HALF, LINE + BALUSTER_HALF),
         {s: "log" for s in SIDES}),
    ]
    turned = {**{s: "log" for s in SIDES}, "up": "log_top", "down": "log_top"}
    for name, half, y in (("baluster_collar_low", COLLAR_HALF, (BULGE[0], BULGE[0] + COLLAR)),
                          ("baluster_bulge", BULGE_HALF, (BULGE[0] + COLLAR, BULGE[1] - COLLAR)),
                          ("baluster_collar_high", COLLAR_HALF, (BULGE[1] - COLLAR, BULGE[1]))):
        hidden = "up" if name.endswith("low") else "down" if name.endswith("high") else None
        out.append((name, (c - half, c + half), y, (LINE - half, LINE + half), {k: v for k, v in turned.items() if k != hidden}))
    strap = {f: "iron" for f in ALL}
    # slim bands round the cap's body and the bottom rail, 1 px tall
    out.append(("hinge_strap_0", STRAP_U, (CAP[0] + 0.25, CAP[0] + 1.25), (LINE - CAP_HALF - 0.25, LINE + CAP_HALF + 0.25), strap))
    out.append(("hinge_strap_1", STRAP_U, (LEAF_RAIL[0] + 0.5, LEAF_RAIL[0] + 1.5), (-0.25, RAIL_DEPTH + 0.25), strap))
    inboard = LINE + CAP_HALF
    out.append(("latch", (LEAF - 3, LEAF + 0.5), (14.5, 15.5), (inboard, inboard + 0.6), strap))
    out.append(("latch_handle", (LEAF - 3, LEAF - 2.25), (12.5, 15.5), (inboard + 0.6, inboard + 1.2), strap))
    return out


def leaf(hinge_left, open_, dz=0.0):
    """The leaf in the block's north frame: closed between the posts, or open, turned 90 degrees outboard (to -z)
    about the cap's outer corner at the hinge post (x 2.5 or 13.5, z -1), every face drawn."""
    out = []
    for name, (u0, u1), (y0, y1), (w0, w1), faces in leaf_parts():
        if not open_:
            x = (HINGE_X + u0, HINGE_X + u1) if hinge_left else (16 - HINGE_X - u1, 16 - HINGE_X - u0)
            z = (w0 + dz, w1 + dz)
            if name == "bottom_rail":
                faces = {f: t for f, t in faces.items() if f != "down"}  # on the sill
        else:
            a, b = w0 - HINGE_PIVOT_W, w1 - HINGE_PIVOT_W
            x = (HINGE_X + a, HINGE_X + b) if hinge_left else (16 - HINGE_X - b, 16 - HINGE_X - a)
            z = (HINGE_PIVOT_W - u1 + dz, HINGE_PIVOT_W - u0 + dz)
            faces = dict(faces)
            base = next(iter(faces.values())).replace("@90", "")
            for end in ("east", "west"):
                faces.setdefault(end, "log_top" if name.startswith("cap") else base)
            # a quarter turn about y: the ends (+u, -u) face north and south, the long faces east and west; the top's
            # grain already runs along the open leaf (z) unturned
            turn = {"east": "north", "west": "south", "south": "east" if hinge_left else "west",
                    "north": "west" if hinge_left else "east", "up": "up", "down": "down"}
            faces = {turn[f]: (tex.replace("@90", "") if f in ("up", "down") else tex) for f, tex in faces.items()}
        out.append(cube(name, (x[0], y0, z[0]), (x[1], y1, z[1]), faces))
    return out


def gate_item():
    return port(ITEM_DZ) + leaf(True, False, ITEM_DZ)


# ---------------------------------------------------------------------- the bulwark (RAIL4, RAIL6)

WALL_TOP = CAP[0]  # the plank wall; the cap rail runs on top of it
RIM_TOP = CAP[1]
OPENING = (3, 13)  # the gun port's notch, x
LID_DEPTH = 1.0
STRAP_X = (4.5, 10.5)  # the lid's two iron straps, 1 px wide
STRAP_PROUD = 0.5  # how far the straps stand off the lid's outer face
HINGE_AXIS = (16, 0)  # (y, z) of the lid's hinge: the notch's top outer edge


def wall_parts(dz, x=(0, 16), prefix="", ends=()):
    """The plank wall from x[0] to x[1] (its outer face at dz + the inset) and the cap rail on it."""
    wall_faces = {f: "planks" for f in ("north", "south") + ends}
    return ([cube(prefix + "wall", (x[0], 0, dz + INSET), (x[1], WALL_TOP, dz + RAIL_DEPTH), wall_faces)]
            + cap_parts(x, LINE + dz, prefix))


def bulwark_edge():
    return wall_parts(0)


def bulwark_port(dz=0.0):
    """The gun port's frame: the two jambs with their cap rail ends (end faces drawn, so a lone port is closed at its
    ends; against a neighbour's wall they hide inside it) and the sill."""
    out = wall_parts(dz, (0, OPENING[0]), "west_", ("west", "east"))
    out += wall_parts(dz, (OPENING[1], 16), "east_", ("west", "east"))
    out.append(cube("sill", (OPENING[0], 0, dz + INSET), (OPENING[1], SILL_TOP, dz + RAIL_DEPTH),
                    {f: "planks" for f in ("north", "south", "up")}))
    return out


def lid_parts():
    """The shut lid in the north frame: (name, from, to, texture, faces drawn shut)."""
    out = [("lid", (OPENING[0], SILL_TOP, INSET), (OPENING[1], RIM_TOP, INSET + LID_DEPTH), "planks", ("north", "south", "up"))]
    for i, x in enumerate(STRAP_X):
        out.append((f"strap_{i}", (x, SILL_TOP + 2, INSET - STRAP_PROUD), (x + 1, RIM_TOP + 0.45, INSET), "iron",
                    ("north", "east", "west", "up", "down")))
        out.append((f"knuckle_{i}", (x - 0.25, RIM_TOP - 0.5, -0.7), (x + 1.25, RIM_TOP + 0.5, 0.3), "iron", ALL))
    return out


def swing(f, t):
    """A box turned 90 degrees about the hinge axis (along x at y 16, z 0), its bottom swung up and out (to -z)."""
    hy, hz = HINGE_AXIS
    y = (hy - (t[2] - hz), hy - (f[2] - hz))
    z = (hz + (f[1] - hy), hz + (t[1] - hy))
    return (f[0], y[0], z[0]), (t[0], y[1], z[1])


def lid(open_, dz=0.0):
    out = []
    for name, f, t, tex, faces in lid_parts():
        if open_:
            f, t = swing(f, t)
            faces = ALL
        out.append(cube(name, (f[0], f[1], f[2] + dz), (t[0], t[1], t[2] + dz), {k: tex for k in faces}))
    return out


def bulwark_item():
    line = LINE + ITEM_DZ
    return wall_parts(ITEM_DZ) + post_parts((LINE, line), "post_west") + post_parts((16 - LINE, line), "post_east")


def bulwark_port_item():
    return bulwark_port(ITEM_DZ) + lid(False, ITEM_DZ)


# ---------------------------------------------------------------------- the rope railing (RAIL6)

ROPE_HALF = 0.75
ROPE_TOP = (CAP[1] - 2 * ROPE_HALF, CAP[1])  # y 14.5..16: its top where the cap's is, for RL5's latch
ROPE_MID = (7, 7 + 2 * ROPE_HALF)
STANCHION_X = (0, RAIL_DEPTH)  # inside the post where a run ends at x 0
STANCHION_TOP = CAP[1] + 0.25  # a little over the top line, under a post's top


def rope_parts(line=LINE, x=(0, 16)):
    # each face samples a strip 16 texels along the line and 1.5 across it: the twist runs along the line
    faces = {f: "rope" for f in ("north", "south", "up", "down")}
    return [cube(f"rope_{n}", (x[0], y[0], line - ROPE_HALF), (x[1], y[1], line + ROPE_HALF), faces)
            for n, y in (("top", ROPE_TOP), ("middle", ROPE_MID))]


def rope_railing_edge():
    stanchion = cube("stanchion", (STANCHION_X[0], 0, LINE - 1), (STANCHION_X[1], STANCHION_TOP, LINE + 1),
                     {**{s: "log" for s in SIDES}, "up": "log_top"})
    return [stanchion] + rope_parts()


def rope_railing_item():
    line = LINE + ITEM_DZ
    return rope_parts(line) + post_parts((LINE, line), "post_west") + post_parts((16 - LINE, line), "post_east")


# ---------------------------------------------------------------------- the railing latch (RL5)

LATCH_CENTRE_Z = 1.5  # RailingLatchBlock.RING_INSET (px)
LATCH_CAP = 0  # the cap's top in the latch block's own frame (y 16 of the railing below)
LATCH_RING_Y = (0.55, 2.95)  # outer; the centre 1.75 = RailingLatchBlock.RING_HEIGHT (px)
LATCH_RING_X = (7.6, 8.4)
LATCH_WASHER_Z = (0.3, 2.0)  # on the cap's band (RAIL6: z -0.45..2.45, the top at y 16) and the rope railing's top line
LATCH_BAR = 0.6
LATCH_RING_HALF = 1.2
# the chainplate's palette patches: plate sides, plate top, bolt sides, ring sides, ring top, ring bottom
UV_PLATE = [0.5, 12.5, 3.5, 15.5]
UV_PLATE_UP = [12.5, 8.5, 15.5, 11.5]
UV_BOLT = [4.5, 12.5, 7.5, 15.5]
UV_RING = [4.5, 8.5, 7.5, 11.5]
UV_RING_UP = [0.5, 8.5, 3.5, 11.5]
UV_RING_DOWN = [8.5, 8.5, 11.5, 11.5]


def patch(name, f, t, faces):
    """A cube with fixed palette UVs: faces {face: uv} on #metal."""
    return {"name": name, "from": [r5(c) for c in f], "to": [r5(c) for c in t],
            "faces": {face: {"uv": u, "texture": "#metal"} for face, u in faces.items()}}


def latch():
    z = LATCH_CENTRE_Z
    sides = ("north", "east", "south", "west")
    out = [
        patch("washer", (7, LATCH_CAP, LATCH_WASHER_Z[0]), (9, LATCH_CAP + 0.4, LATCH_WASHER_Z[1]),
              {**{f: UV_PLATE for f in sides}, "up": UV_PLATE_UP}),
        patch("shank", (7.7, LATCH_CAP + 0.4, z - 0.3), (8.3, LATCH_RING_Y[0], z + 0.3), {f: UV_BOLT for f in sides}),
    ]
    y0, y1 = LATCH_RING_Y
    z0, z1 = z - LATCH_RING_HALF, z + LATCH_RING_HALF
    x0, x1 = LATCH_RING_X
    bar = LATCH_BAR
    ring = {**{f: UV_RING for f in sides}, "up": UV_RING_UP, "down": UV_RING_DOWN}
    out.append(patch("ring_bottom", (x0, y0, z0), (x1, y0 + bar, z1), ring))
    out.append(patch("ring_top", (x0, y1 - bar, z0), (x1, y1, z1), ring))
    out.append(patch("ring_outer", (x0, y0 + bar, z0), (x1, y1 - bar, z0 + bar), {f: UV_RING for f in sides}))
    out.append(patch("ring_inner", (x0, y0 + bar, z1 - bar), (x1, y1 - bar, z1), {f: UV_RING for f in sides}))
    return out


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
    for k, v in (("planks", PLANKS), ("log", LOG), ("log_top", LOG_TOP), ("iron", IRON), ("metal", METAL), ("rope", ROPE)):
        if k in used_textures(elements):
            textures[k] = v
    textures["particle"] = "#planks" if "planks" in textures else "#log" if "log" in textures else "#" + next(iter(textures))
    return {
        "credit": "Made with Blockbench, source art/models/railing.bbmodel",
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": elements,
    }


# ---------------------------------------------------------------------- the Blockbench project

def det_uuid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "pirates_n_ships/railing/" + "/".join(parts)))


TEXTURES = [  # (key, file name; vanilla's block textures, RL5 the mod's palette, RAIL6 its rope)
    ("planks", "oak_planks"),
    ("log", "stripped_oak_log"),
    ("iron", "anvil"),
    ("metal", "palette_2"),
    ("log_top", "stripped_oak_log_top"),
    ("rope", "rope_line"),
]
OWN = {"metal": "item", "rope": "block"}  # the mod's own textures, by folder


def bb_texture(index, key, name):
    own = key in OWN
    return {
        "name": name + ".png", "path": "",
        "relative_path": (f"../../common/src/main/resources/assets/pirates_n_ships/textures/{OWN[key]}/{name}.png" if own
                          else f"../vanilla/{name}.png"),
        "folder": OWN.get(key, "block"),
        "namespace": "pirates_n_ships" if own else "minecraft", "id": key, "group": "", "scope": 0, "width": 16, "height": 16, "uv_width": 16,
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
    models = [("railing_edge", edge()), ("railing_baluster_west", baluster(True)), ("railing_baluster_east", baluster(False)),
              ("railing_post", post()), ("railing_port", port()), ("railing_item", item()),
              ("railing_gate_left", leaf(True, False)), ("railing_gate_right", leaf(False, False)),
              ("railing_gate_left_open", leaf(True, True)), ("railing_gate_right_open", leaf(False, True)),
              ("railing_gate_item", gate_item()),
              ("bulwark_edge", bulwark_edge()), ("bulwark_port", bulwark_port()),
              ("bulwark_port_lid", lid(False)), ("bulwark_port_lid_open", lid(True)), ("bulwark_item", bulwark_item()),
              ("bulwark_port_item", bulwark_port_item()), ("railing_latch", latch()),
              ("rope_railing_edge", rope_railing_edge()), ("rope_railing_item", rope_railing_item())]
    for name, els in models:
        write(MODELS / f"block/{name}.json", block_model(els))
    stale = MODELS / "block/bulwark_stanchion.json"  # RAIL6: the bulwark's posts are the railing's
    if stale.exists():
        stale.unlink()
    write(ART / "railing.bbmodel", project(models))
    for name, els in models:
        print(f"{name}: {len(els)} elements")


if __name__ == "__main__":
    main()
