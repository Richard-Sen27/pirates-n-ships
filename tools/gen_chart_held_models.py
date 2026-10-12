#!/usr/bin/env python3
"""CHART3: the chart held in 3D. Writes the chart's six item models and its two PAL arm animations.

The chart is a parchment on two wooden rollers (a double scroll). Closed it is the two rolls side by side with a cord
round them (`chart`, the normal item); while its owner reads the chart screen it unrolls between both hands over half a
second (`chart_unroll_0` .. `chart_unroll_3`, `chart_open`, chosen by the item property `pirates_n_ships:chart_open`
through overrides on `chart`), and rolls up again when the screen closes. The arms follow the PAL animations
`chart_read` (raise the rolled chart with the hands together, then draw the hands apart to the shoulders) and
`chart_stow` (the same keys backwards), in `assets/pirates_n_ships/chart_animations/`.

Frames: the progress p goes 0 -> 1 in ChartHeldMotion.UNROLL_TICKS (10) ticks. The PAL time is p * 0.5 s. Frame k is
shown from its threshold on (overrides, the last match wins). Each frame's third-person display is solved for the arm
pose at its middle time, so the chart stays level and centred between the hands while the right (holding) arm turns:
the rollers sit in both fists and the face is turned to the reader's eyes. The left-hand entries are solved through
the left-hand chain the same way (they come out as ART17's rule says: the right-hand values, the game mirrors them).

The geometry is built facing south (+z, the map face) around x = z = 8, the rollers along y. Closed, the elements are
turned -45 about z like the earlier rolled chart, so the closed item is held like a tool. The chain used to solve the
displays is vanilla 1.21.1's (ModelPart.translateAndRotate, ItemInHandLayer.renderArmWithItem, ItemTransform.apply,
ItemRenderer.render); ChartHeldModelTest composes the real classes and checks the result.

Usage: python3 tools/gen_chart_held_models.py   (deterministic; rewrites the models and the two animation files)
"""
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, 'common/src/main/resources/assets/pirates_n_ships/models/item')
ANIMS = os.path.join(ROOT, 'common/src/main/resources/assets/pirates_n_ships/chart_animations')

# ------------------------------------------------------------------------------------------------ palette patches
PALETTE = {
    'wood': ('#0', 12, 8), 'wood_dark': ('#0', 8, 8), 'brass': ('#0', 4, 4),
    'paper': ('#2', 12, 12), 'biscuit_light': ('#2', 0, 4),
    'cord': ('#3', 8, 4),
    'map_sea': ('#4', 8, 8), 'map_sea_dark': ('#4', 12, 8), 'map_land': ('#4', 0, 12), 'map_ink': ('#4', 4, 12),
    'flag_red': ('#4', 4, 4),
}
TEXTURES = {
    '0': 'pirates_n_ships:item/palette',
    '2': 'pirates_n_ships:item/palette_3',
    '3': 'pirates_n_ships:item/palette_4',
    '4': 'pirates_n_ships:item/palette_5',
    'particle': 'pirates_n_ships:item/palette_3',
}


def uv(patch):
    tex, u, v = PALETTE[patch]
    return tex, [u + 0.5, v + 0.5, u + 3.5, v + 3.5]


def r4(x):
    v = round(x, 4)
    return 0 if v == 0 else (int(v) if v == int(v) else v)


def box(name, f, t, patch, faces=('north', 'east', 'south', 'west', 'up', 'down'), rot=None):
    tex, coords = uv(patch)
    e = {'name': name, 'from': [r4(c) for c in f], 'to': [r4(c) for c in t]}
    if rot:
        e['rotation'] = rot
    e['faces'] = {d: {'uv': coords, 'texture': tex} for d in faces}
    return e


# ------------------------------------------------------------------------------------------------ geometry
SHEET_Y = (-1.9, 17.9)          # the visible parchment, 0.05 inside the rolls' ends
ROLL_Y = (-2.0, 18.0)
ROD_Y = (-3.6, 19.6)
KNOB_Y = ((-4.8, -3.0), (19.0, 20.8))
ROD_R = 0.55
KNOB_R = 1.0
ROLL_R_CLOSED = 1.3              # the parchment wound on one roller when closed
ROLL_R_OPEN = 0.9                # what is left on it when the chart is open
HALF_OPEN = 15.0                 # roller axes at x = 8 +- 15 when open (30 px of chart between them)
FACE_Z = 8.1                     # the sheet's south (map) face; inlays stack 0.05 px per layer above it


def plus(name, cx, r, y0, y1, patch, rot=None):
    """A round part as two crossed boxes (a plus-shaped section), the second 0.05 px shorter at both ends."""
    k = 0.7 * r
    return [box(name + '_a', (cx - r, y0, 8 - k), (cx + r, y1, 8 + k), patch, rot=rot),
            box(name + '_b', (cx - k, y0 + 0.05, 8 - r), (cx + k, y1 - 0.05, 8 + r), patch, rot=rot)]


def roller(side, cx, roll_r, rot=None):
    out = plus('roll_' + side, cx, roll_r, ROLL_Y[0], ROLL_Y[1], 'biscuit_light', rot)
    out += plus('rod_' + side, cx, ROD_R, ROD_Y[0], ROD_Y[1], 'wood', rot)
    for i, (y0, y1) in enumerate(KNOB_Y):
        out += plus('knob_%s_%d' % (side, i), cx, KNOB_R, y0, y1, 'wood_dark', rot)
    return out


# The map on the face, in u = x - 8 (sheet centre 0) and y; layer 1 = 0.05 px proud of the face, each layer 0.05 more.
# (name, u0, y0, u1, y1, layer, patch[, z rotation])
MAP = [
    # ink frame (verticals stop at the horizontals)
    ('frame_s', -13.2, 0.0, 13.2, 0.3, 1, 'map_ink'),
    ('frame_n', -13.2, 15.7, 13.2, 16.0, 1, 'map_ink'),
    ('frame_w', -13.2, 0.3, -12.9, 15.7, 1, 'map_ink'),
    ('frame_e', 12.9, 0.3, 13.2, 15.7, 1, 'map_ink'),
    # the large island and its shallows
    ('land_a0', -11.0, 4.0, -5.0, 11.0, 1, 'map_land'),
    ('land_a1', -9.0, 11.0, -6.0, 13.0, 1, 'map_land'),
    ('land_a2', -5.0, 5.0, -3.0, 9.0, 1, 'map_land'),
    ('shoal_a', -12.0, 5.0, -11.3, 10.0, 1, 'map_sea'),
    ('coast_a0', -11.0, 4.0, -10.75, 11.0, 2, 'map_ink'),
    ('coast_a1', -10.75, 4.0, -5.0, 4.25, 2, 'map_ink'),
    ('coast_a2', -10.75, 10.75, -9.0, 11.0, 2, 'map_ink'),
    ('coast_a3', -9.0, 11.0, -8.75, 13.0, 2, 'map_ink'),
    ('coast_a4', -8.75, 12.75, -6.0, 13.0, 2, 'map_ink'),
    ('coast_a5', -3.25, 5.0, -3.0, 9.0, 2, 'map_ink'),
    # the small island with the X
    ('land_b0', 5.0, 3.0, 9.0, 6.0, 1, 'map_land'),
    ('land_b1', 6.0, 6.0, 8.0, 7.5, 1, 'map_land'),
    ('shoal_b', 4.4, 3.0, 5.0, 5.0, 1, 'map_sea'),
    ('coast_b0', 5.0, 3.0, 9.0, 3.25, 2, 'map_ink'),
    ('coast_b1', 8.75, 3.25, 9.0, 6.0, 2, 'map_ink'),
    ('x_0', 6.2, 4.45, 7.8, 4.75, 3, 'flag_red', 45),
    ('x_1', 6.2, 4.45, 7.8, 4.75, 4, 'flag_red', -45),
    # the course
    ('route_0', -1.7, 7.0, -1.3, 7.4, 1, 'map_ink'),
    ('route_1', -0.2, 6.6, 0.2, 7.0, 1, 'map_ink'),
    ('route_2', 1.3, 6.1, 1.7, 6.5, 1, 'map_ink'),
    ('route_3', 2.8, 5.7, 3.2, 6.1, 1, 'map_ink'),
    ('route_4', 4.3, 5.3, 4.7, 5.7, 1, 'map_ink'),
    # the compass rose
    ('rose_ns', 9.85, 10.0, 10.15, 15.0, 1, 'map_ink'),
    ('rose_n', 9.8, 15.0, 10.2, 15.5, 1, 'flag_red'),
    ('rose_ew', 7.5, 12.35, 12.5, 12.65, 2, 'map_ink'),
    ('rose_d0', 8.8, 12.4, 11.2, 12.6, 3, 'map_sea_dark', 45),
    ('rose_d1', 8.8, 12.4, 11.2, 12.6, 4, 'map_sea_dark', -45),
]


def map_elements(visible):
    """The map's inlays that lie wholly on the visible face (|u| <= visible); rotated ones need their whole box in."""
    out = []
    for m in MAP:
        name, u0, y0, u1, y1, layer, patch = m[:7]
        angle = m[7] if len(m) > 7 else 0
        if angle:
            cu, cy = (u0 + u1) / 2, (y0 + y1) / 2
            reach = math.hypot(u1 - u0, y1 - y0) / 2
            if abs(cu) + reach > visible:
                continue
        elif u1 < -visible or u0 > visible:
            continue
        a, b = max(u0, -visible), min(u1, visible)
        if b - a < 0.2:
            continue
        z0 = FACE_Z + 0.05 * (layer - 1)
        rot = None
        if angle:
            rot = {'angle': angle, 'axis': 'z', 'origin': [r4(8 + (u0 + u1) / 2), r4((y0 + y1) / 2), r4(z0)]}
        out.append(box('map_' + name, (8 + a, y0, z0), (8 + b, y1, z0 + 0.05), patch,
                       faces=('east', 'south', 'west', 'up', 'down'), rot=rot))
    return out


def frame_elements(half, roll_r):
    """A frame with the roller axes at x = 8 +- half: rollers, the sheet between them and the map on it."""
    out = roller('left', 8 - half, roll_r) + roller('right', 8 + half, roll_r)
    if half - roll_r > 0.3:
        out.append(box('sheet', (8 - half, SHEET_Y[0], 7.9), (8 + half, SHEET_Y[1], FACE_Z), 'paper',
                       faces=('north', 'south', 'up', 'down')))
        out += map_elements(half - roll_r - 0.05)
    return out


def closed_elements():
    rot = {'angle': -45, 'axis': 'z', 'origin': [8, 8, 8]}
    half = ROLL_R_CLOSED
    out = roller('left', 8 - half, ROLL_R_CLOSED, rot) + roller('right', 8 + half, ROLL_R_CLOSED, rot)
    # the cord round both rolls and its knot on the front
    out.append(box('tie', (8 - 2 * half - 0.2, 7.6, 8 - half - 0.2), (8 + 2 * half + 0.2, 8.4, 8 + half + 0.2), 'cord', rot=rot))
    out.append(box('knot', (7.4, 7.4, 8 + half + 0.2), (8.6, 8.6, 8 + half + 0.8), 'cord', rot=rot))
    out.append(box('cord_end', (7.7, 5.6, 8 + half + 0.25), (8.1, 7.4, 8 + half + 0.6), 'cord',
                   faces=('north', 'east', 'south', 'west', 'down'), rot=rot))
    return out


# ------------------------------------------------------------------------------------------------ matrices
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def ident():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def trans(x, y, z):
    m = ident()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def scale(s):
    m = ident()
    m[0][0] = m[1][1] = m[2][2] = s
    return m


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    return [sum(m[i][k] * p[k] for k in range(3)) + m[i][3] for i in range(3)]


def rot_part(m):
    return [[m[i][j] for j in range(3)] for i in range(3)]


def transpose3(r):
    return [[r[j][i] for j in range(3)] for i in range(3)]


def mul3(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mulv3(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def norm(v):
    n = math.sqrt(sum(c * c for c in v))
    return [c / n for c in v]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def euler_xyz(r):
    """Angles (degrees) of R = Rx(a) Ry(b) Rz(c), Quaternionf.rotationXYZ's order."""
    b = math.asin(max(-1.0, min(1.0, r[0][2])))
    a = math.atan2(-r[1][2], r[2][2])
    c = math.atan2(-r[0][1], r[0][0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


# ------------------------------------------------------------------------------------------------ the arm pose
# Vanilla model frame (y down, the player faces -z, the right arm on -x). Arm pivots and the hand point inside the fist
# (the arm box's centre column, 1 px above its end): PlayerModel / HumanoidModel, wide arms.
RIGHT_PIVOT = (-5.0, 2.0, 0.0)
LEFT_PIVOT = (5.0, 2.0, 0.0)
RIGHT_HAND = (-1.0, 9.0, 0.0)
LEFT_HAND = (1.0, 9.0, 0.0)
EYES = (0.0, -3.0, -4.0)        # between the eyes, on the face

READ_PITCH = -65.0               # both arms forward and down, the hands in front of the chest
TOGETHER_YAW = 27.0              # the inward turn that brings the fists together (4.4 px apart: the arms touch) round the rolled chart
RAISE_TIME = 0.15                # s: arms up with the fists together
LENGTH = 0.5                     # s: the whole unroll (ChartHeldMotion.UNROLL_TICKS = 10 ticks)
REST_RIGHT = (-18.0, 0.0, 0.0)   # vanilla's holding-an-item arm; PAL hands over to it
REST_LEFT = (0.0, 0.0, 0.0)


def arm_angles(t):
    """(right, left) arm rotations in degrees (vanilla angles, PAL file values) at time t of chart_read."""
    if t <= RAISE_TIME:
        f = t / RAISE_TIME
        right = [REST_RIGHT[0] + (READ_PITCH - REST_RIGHT[0]) * f, -TOGETHER_YAW * f, 0.0]
        left = [REST_LEFT[0] + (READ_PITCH - REST_LEFT[0]) * f, TOGETHER_YAW * f, 0.0]
        return right, left
    f = min(1.0, (t - RAISE_TIME) / (LENGTH - RAISE_TIME))
    return [READ_PITCH, -TOGETHER_YAW * (1 - f), 0.0], [READ_PITCH, TOGETHER_YAW * (1 - f), 0.0]


def arm_matrix(pivot, angles):
    """ModelPart.translateAndRotate in block units: translate(pivot / 16), rotationZYX(z, y, x)."""
    x, y, z = (math.radians(a) for a in angles)
    return mat_mul(trans(*(c / 16 for c in pivot)), mat_mul(rz(z), mat_mul(ry(y), rx(x))))


def hand(pivot, local, angles):
    return apply(arm_matrix(pivot, angles), [c / 16 for c in local])


def item_frame(pivot, angles, left):
    """ItemInHandLayer.renderArmWithItem up to renderItem: the frame the display transform starts in."""
    m = arm_matrix(pivot, angles)
    m = mat_mul(m, rx(math.radians(-90)))
    m = mat_mul(m, ry(math.radians(180)))
    return mat_mul(m, trans((-1 if left else 1) / 16, 0.125, -0.625))


def solve_display(t, left, half_model):
    """The third-person display entry of a frame shown at PAL time t, for the right or the left hand.

    The chart's centre goes to the midpoint of the fists, its model +x to the reader's right (the player's right, -x),
    its face (+z) to the eyes. Scale: the chart is as wide as the open fists are apart (fixed for every frame)."""
    right, leftArm = arm_angles(t)
    hr, hl = hand(RIGHT_PIVOT, RIGHT_HAND, right), hand(LEFT_PIVOT, LEFT_HAND, leftArm)
    centre = [(a + b) / 2 for a, b in zip(hr, hl)]
    eyes = [c / 16 for c in EYES]
    face = norm([e - c for e, c in zip(eyes, centre)])
    xdir = norm([-1.0, 0.0, 0.0])
    # make the face orthogonal to x (the reader's right stays horizontal)
    face = norm([f - xdir[i] * sum(a * b for a, b in zip(face, xdir)) for i, f in enumerate(face)])
    ydir = cross(face, xdir)
    want = [[xdir[i], ydir[i], face[i]] for i in range(3)]          # columns = model axes in the model frame
    holder = item_frame(LEFT_PIVOT if left else RIGHT_PIVOT, leftArm if left else right, left)
    hr_open, hl_open = hand(RIGHT_PIVOT, RIGHT_HAND, arm_angles(LENGTH)[0]), hand(LEFT_PIVOT, LEFT_HAND, arm_angles(LENGTH)[1])
    s = math.dist(hr_open, hl_open) * 16 / (2 * HALF_OPEN)
    r_item = mul3(transpose3(rot_part(holder)), want)               # = ItemTransform rotation (left: mirrored)
    a, b, c = euler_xyz(r_item)
    if left:
        b, c = -b, -c                                               # ItemTransform.apply negates y and z for a left hand
    # translation: holder * T(t) * R * S * T(-0.5) maps the model centre (8, 8, 8)/16 to `centre`
    inv_r = transpose3(rot_part(holder))
    local = mulv3(inv_r, [centre[i] - holder[i][3] for i in range(3)])
    tx, ty, tz = (v * 16 for v in local)
    if left:
        tx = -tx                                                    # and the x translation
    return {'rotation': [r4(round(a, 2)), r4(round(b, 2)), r4(round(c, 2))],
            'translation': [r4(round(tx, 2)), r4(round(ty, 2)), r4(round(tz, 2))],
            'scale': [r4(round(s, 3))] * 3}


def roller_half(t):
    """Half the distance between the roller axes (model px) at PAL time t: the fists' half distance / scale."""
    right, leftArm = arm_angles(t)
    hr, hl = hand(RIGHT_PIVOT, RIGHT_HAND, right), hand(LEFT_PIVOT, LEFT_HAND, leftArm)
    hr_open, hl_open = hand(RIGHT_PIVOT, RIGHT_HAND, arm_angles(LENGTH)[0]), hand(LEFT_PIVOT, LEFT_HAND, arm_angles(LENGTH)[1])
    return HALF_OPEN * math.dist(hr, hl) / math.dist(hr_open, hl_open)


# ------------------------------------------------------------------------------------------------ the frames
# (model, override threshold of chart_open, the PAL time its pose is solved for)
FRAMES = [
    ('chart_unroll_0', 0.01, RAISE_TIME),
    ('chart_unroll_1', 0.3, 0.2),
    ('chart_unroll_2', 0.5, 0.3),
    ('chart_unroll_3', 0.7, 0.42),
    ('chart_open', 1.0, 0.5),
]

# first person: the open chart low in the view and tilted away like vanilla's map (ItemInHandRenderer's
# applyItemArmTransform puts the item origin at (+-0.56, -0.52, -0.72); the translation takes it to the middle)
FIRST_PERSON = {'rotation': [-45, 0, 0], 'translation': [-8.96, 2.24, -0.48], 'scale': [0.42, 0.42, 0.42]}

CLOSED_DISPLAY = {
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.34, 0.34, 0.34]},
    'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.34, 0.34, 0.34]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.42, 0.42, 0.42]},
    'firstperson_lefthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.42, 0.42, 0.42]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.32, 0.32, 0.32]},
    'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.62, 0.62, 0.62]},
    'head': {'rotation': [0, -180, 0], 'translation': [0, 13, 7], 'scale': [0.62, 0.62, 0.62]},
    'fixed': {'rotation': [0, -180, 0], 'translation': [0, 0, 0], 'scale': [0.62, 0.62, 0.62]},
}
OPEN_OTHER = {
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.25, 0.25, 0.25]},
    'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.48, 0.48, 0.48]},
    'head': {'rotation': [0, -180, 0], 'translation': [0, 13, 7], 'scale': [0.5, 0.5, 0.5]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.48, 0.48, 0.48]},
}
SLOTS = ['thirdperson_righthand', 'thirdperson_lefthand', 'firstperson_righthand', 'firstperson_lefthand',
         'ground', 'gui', 'head', 'fixed']


def rollers(name, t):
    """Half the roller distance of a frame: rolled up on the first, in the fists on the others."""
    if name == 'chart_unroll_0':
        return ROLL_R_CLOSED
    return HALF_OPEN if name == 'chart_open' else max(ROLL_R_CLOSED, roller_half(t))


def model(name, display, elements, overrides=None):
    m = {'credit': 'Made with Blockbench, source art/models/%s.bbmodel (tools/gen_chart_held_models.py)' % name,
         'gui_light': 'front', 'textures': TEXTURES,
         'display': {k: display[k] for k in SLOTS}, 'elements': elements}
    if overrides:
        m['overrides'] = overrides
    return m


def frames():
    """{name: model json} for the closed chart and the five unroll frames."""
    out = {}
    overrides = []
    for name, threshold, t in FRAMES:
        half = rollers(name, t)
        frac = (half - ROLL_R_CLOSED) / (HALF_OPEN - ROLL_R_CLOSED)
        roll_r = ROLL_R_CLOSED + (ROLL_R_OPEN - ROLL_R_CLOSED) * frac
        display = dict(OPEN_OTHER)
        display['thirdperson_righthand'] = solve_display(t, False, half)
        display['thirdperson_lefthand'] = solve_display(t, True, half)
        display['firstperson_righthand'] = FIRST_PERSON
        display['firstperson_lefthand'] = FIRST_PERSON
        out[name] = model(name, display, frame_elements(half, roll_r))
        overrides.append({'predicate': {'pirates_n_ships:chart_open': threshold}, 'model': 'pirates_n_ships:item/' + name})
    out['chart'] = model('chart', CLOSED_DISPLAY, closed_elements(), overrides)
    return out


# ------------------------------------------------------------------------------------------------ PAL animations
def pal_keys(times, fn):
    return {('%.2f' % t).rstrip('0').rstrip('.') if t else '0.0': [r4(round(v, 2)) for v in fn(t)] for t in times}


def key_time(t):
    s = ('%.3f' % t).rstrip('0')
    return s + '0' if s.endswith('.') else s


def animation(name, reverse):
    times = [0.0, 0.05, 0.1, RAISE_TIME, 0.25, 0.35, 0.45, LENGTH]
    bones = {}
    for bone, idx in (('right_arm', 0), ('left_arm', 1)):
        keys = {}
        for t in times:
            src = LENGTH - t if reverse else t
            keys[key_time(t)] = [r4(round(v, 2)) for v in arm_angles(src)[idx]]
        bones[bone] = {'rotation': keys}
    return {'format_version': '1.8.0', 'animations': {name: {
        'animation_length': LENGTH, 'loop': False if reverse else 'hold_on_last_frame', 'bones': bones}}}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(data, indent='\t') + '\n')


def main():
    for name, m in frames().items():
        write(os.path.join(MODELS, name + '.json'), m)
    write(os.path.join(ANIMS, 'chart_read.json'), animation('chart_read', False))
    write(os.path.join(ANIMS, 'chart_stow.json'), animation('chart_stow', True))
    for name, threshold, t in FRAMES:
        print(name, 'half %.2f' % rollers(name, t),
              solve_display(t, False, 0)['rotation'], solve_display(t, True, 0)['rotation'])


if __name__ == '__main__':
    main()
