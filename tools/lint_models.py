#!/usr/bin/env python3
"""Z-fighting lint for the hand-made Java block/item models (art/README.md, "Z-fighting lint (V1)").

For every hand-made model JSON it finds pairs of faces of *different* elements that lie in the same plane (within
--tolerance, default 0.03 px) and overlap in area (touching along an edge does not count):

- **fight**: both faces point the same way. The depth buffer cannot order them, so they flicker. A fight is
  *visible* when the two faces show something different over the overlap (another texture, another colour patch
  of a palette sheet, the same texture mapped differently, another tint or shading); a fight between faces that
  look the same is a warning only.
- **hidden**: the faces point opposite ways and at least one of them is fully covered by the other (a part sitting
  on another part). Back-face culling keeps such a pair from flickering; a covered face is never seen and only
  wastes a quad. `covered: both` means the two faces cover each other exactly. Warning only.

Faces are compared in world space after the element rotation (axis, angle, origin, rescale) the way vanilla's
FaceBakery applies it, so rotated elements are checked too: two faces are coplanar when their normals are parallel
and their planes are within the tolerance, and the overlap is the intersection of the two face polygons in that
plane. No pair is left unchecked. HandMadeModelsTest.noVisibleZFighting runs the same rule in Java.

--fix rewrites the model JSON and its Blockbench project (art/models/<name>.bbmodel, elements matched in outliner
order) until no visible fight is left, one face pair at a time:
1. an inlay (a face inside the other face whose whole element lies inside the other element: a keyhole in its
   plate, writing on a page, a bore disc on a muzzle, a speck on a heap) moves out to 0.05 px proud;
2. a part that runs on through the other element (a post through its cap, a tiller into its knob) and ends flush
   with it moves its end 0.05 px inward, so the other element's face shows;
3. of two partly overlapping faces (a joint), one moves 0.05 px inward behind the other: the rotated one, else one
   not moved yet, else the smaller; an element too thin for that moves outward or as a whole instead.
Faces are never deleted by --fix (a covered face is reported as hidden; deleting it is a manual decision).
Only `from`/`to` change (UVs, rotations and display entries stay); values are rounded to 5 decimals.

--fix --mirror BASE VARIANT (the loaded guns, P6/V1b): fixes BASE first, gives every VARIANT element that is
identical to the BASE element of the same name the same moves, then fixes the rest of VARIANT with those elements
frozen, so the two models stay identical outside the parts the variant changes.

Usage: python3 tools/lint_models.py [--tolerance 0.03] [--json] [--summary] [--warnings] [--exclude name ...]
                                    [--fix [--mirror BASE VARIANT ...]] [files...]
Without files it checks every model under common/src/main/resources/assets/pirates_n_ships/models/{block,item}.
Exit code 1 when a visible fight is left.
"""
import argparse
import glob
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, 'common/src/main/resources/assets/pirates_n_ships/models')
PROJECTS = os.path.join(ROOT, 'art/models')
PROJECT_NAMES = {'item/brig_door': 'brig_door_item', 'item/hull_patch': 'hull_patch_item', 'item/hammock': 'hammock_item'}
AREA_EPS = 1e-3          # px^2: smaller overlaps are edge contacts
PARALLEL_EPS = 1e-6      # 1 - |n1 . n2|
UV_EPS = 0.02            # texture units: mapped UVs closer than this look the same
STEP = 0.05              # px: the plane distance --fix sets between two fighting faces
# Palette sheets (textures/item/palette*.png) are 16x16 sheets of uniform 4x4 colour patches, except these
# striped ones (sheet, u, v).
PALETTE_STRIPED = {('palette', 12, 12), ('palette_2', 12, 4), ('palette_4', 8, 0), ('palette_4', 12, 12)}

DIRS = {
    'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0),
    'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0),
}
# the from/to coordinate a face lies on: (index, 'from' | 'to')
FACE_COORD = {'west': (0, 'from'), 'east': (0, 'to'), 'down': (1, 'from'), 'up': (1, 'to'),
              'north': (2, 'from'), 'south': (2, 'to')}


def face_corners(d, f, t):
    """The four corners of a face in vanilla's vertex order (FaceInfo), which take the UV corners (u1,v1),
    (u1,v2), (u2,v2), (u2,v1) at face rotation 0."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        'north': [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        'south': [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        'east': [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
        'west': [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        'up': [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        'down': [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
    }[d]


def is_rotated(rot):
    return bool(rot) and float(rot.get('angle', 0)) != 0


def rotation_key(rot):
    if not is_rotated(rot):
        return None
    return (rot['axis'], float(rot['angle']), tuple(round(float(v), 4) for v in rot.get('origin', [8, 8, 8])),
            bool(rot.get('rescale', False)))


def transforms(rot):
    """Vanilla FaceBakery.applyElementRotation as (point, normal) functions: a right-handed rotation about the
    positive axis through the origin, then the optional rescale of the two other axes."""
    if not is_rotated(rot):
        return (lambda p: tuple(p)), (lambda n: n)
    axis, angle = rot['axis'], float(rot['angle'])
    o = rot.get('origin', [8, 8, 8])
    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)
    k = 1.0 / math.cos(a) if rot.get('rescale') else 1.0
    scale = {'x': (1, k, k), 'y': (k, 1, k), 'z': (k, k, 1)}[axis]

    def turn(x, y, z):
        if axis == 'x':
            return x, c * y - s * z, s * y + c * z
        if axis == 'y':
            return c * x + s * z, y, -s * x + c * z
        return c * x - s * y, s * x + c * y, z

    def point(p):
        x, y, z = turn(p[0] - o[0], p[1] - o[1], p[2] - o[2])
        return (x * scale[0] + o[0], y * scale[1] + o[1], z * scale[2] + o[2])

    def normal(n):
        x, y, z = turn(*n)
        x, y, z = x / scale[0], y / scale[1], z / scale[2]   # normals transform with the inverse scale
        return norm((x, y, z))
    return point, normal


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    length = math.sqrt(dot(a, a))
    return (a[0] / length, a[1] / length, a[2] / length)


def poly_area(poly):
    area = 0.0
    for i in range(len(poly)):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % len(poly)]
        area += x0 * y1 - x1 * y0
    return area / 2


def ccw(poly):
    return poly if poly_area(poly) >= 0 else list(reversed(poly))


def clip(subject, clipper):
    """Sutherland-Hodgman: the subject polygon clipped by a convex counter-clockwise clipper."""
    out = subject
    for i in range(len(clipper)):
        if not out:
            break
        a, b = clipper[i], clipper[(i + 1) % len(clipper)]
        inp, out = out, []

        def side(p):
            return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])

        for j in range(len(inp)):
            p, q = inp[j], inp[(j + 1) % len(inp)]
            sp, sq = side(p), side(q)
            if sp >= 0:
                out.append(p)
            if (sp >= 0) != (sq >= 0):
                t = sp / (sp - sq)
                out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out


def resolve(textures, ref, depth=0):
    if ref.startswith('#') and depth < 10 and ref[1:] in textures:
        return resolve(textures, textures[ref[1:]], depth + 1)
    return ref


def default_uv(d, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        'down': [x0, 16 - z1, x1, 16 - z0], 'up': [x0, z0, x1, z1],
        'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
        'west': [z0, 16 - y1, z1, 16 - y0], 'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[d]


class Face:
    def __init__(self, idx, elem, d, data, textures):
        self.idx = idx
        self.elem = elem.get('name', '#%d' % idx)
        self.dir = d
        rot = elem.get('rotation')
        self.rotated = is_rotated(rot)
        self.rot_key = rotation_key(rot)
        self.box = (tuple(elem['from']), tuple(elem['to']))
        point, normal = transforms(rot)
        self.corners = [point(p) for p in face_corners(d, elem['from'], elem['to'])]
        self.normal = normal(DIRS[d])
        self.plane = dot(self.normal, self.corners[0])
        self.texture = resolve(textures, data.get('texture', ''))
        self.uv = data.get('uv') or default_uv(d, elem['from'], elem['to'])
        self.tint = data.get('tintindex', -1)
        self.shade = elem.get('shade', True)
        u1, v1, u2, v2 = self.uv
        uv_corners = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
        shift = (int(data.get('rotation', 0)) % 360) // 90
        self.vertex_uv = [uv_corners[(i + shift) % 4] for i in range(4)]
        e1, e2 = sub(self.corners[3], self.corners[0]), sub(self.corners[1], self.corners[0])
        self.area = math.sqrt(dot(e1, e1) * dot(e2, e2))

    def uv_at(self, p):
        """The UV at a point of the face plane (bilinear = affine on the parallelogram)."""
        a = self.corners[0]
        e1, e2 = sub(self.corners[3], a), sub(self.corners[1], a)
        rel = sub(p, a)
        s = dot(rel, e1) / dot(e1, e1) if dot(e1, e1) else 0.0
        t = dot(rel, e2) / dot(e2, e2) if dot(e2, e2) else 0.0
        ua, ub, uc = self.vertex_uv[0], self.vertex_uv[1], self.vertex_uv[3]
        return (ua[0] + s * (uc[0] - ua[0]) + t * (ub[0] - ua[0]),
                ua[1] + s * (uc[1] - ua[1]) + t * (ub[1] - ua[1]))

    def palette_patch(self):
        """(sheet, u, v) of the uniform palette patch the whole face maps into, else None."""
        if not self.texture.startswith('pirates_n_ships:item/palette'):
            return None
        name = self.texture.split('/')[-1]
        u1, v1, u2, v2 = self.uv
        pu, pv = int(min(u1, u2) // 4) * 4, int(min(v1, v2) // 4) * 4
        if max(u1, u2) > pu + 4 + 1e-6 or max(v1, v2) > pv + 4 + 1e-6:
            return None
        key = (name, pu, pv)
        return None if key in PALETTE_STRIPED else key


def looks_same(a, b, points):
    if a.texture != b.texture or a.tint != b.tint or a.shade != b.shade:
        return False
    pa = a.palette_patch()
    if pa is not None and pa == b.palette_patch():
        return True
    for p in points:
        ua, ub = a.uv_at(p), b.uv_at(p)
        if abs(ua[0] - ub[0]) > UV_EPS or abs(ua[1] - ub[1]) > UV_EPS:
            return False
    return True


def faces_of(model):
    textures = model.get('textures', {})
    out = []
    for i, e in enumerate(model.get('elements', [])):
        for d, data in e.get('faces', {}).items():
            out.append(Face(i, e, d, data, textures))
    return out


def r4(v):
    return round(v + 0.0, 4)


def plane_label(n, plane):
    for k, axis in enumerate('xyz'):
        if abs(abs(n[k]) - 1) < 1e-9:
            return '%s=%s' % (axis, r4(plane * n[k]))
    return 'n=(%s, %s, %s) d=%s' % (r4(n[0]), r4(n[1]), r4(n[2]), r4(plane))


def pairs(faces, tolerance):
    """Coplanar, area-overlapping face pairs of different elements: (a, b, same_direction, gap, area, points)."""
    groups = {}
    for f in faces:
        n = f.normal
        # orient so that opposite normals share a group
        sgn = 1
        for v in n:
            if abs(v) > 1e-9:
                sgn = 1 if v > 0 else -1
                break
        key = tuple(round(v * sgn, 5) for v in n)
        groups.setdefault(key, []).append((f.plane * sgn, f))
    for items in groups.values():
        items.sort(key=lambda x: x[0])
        for i in range(len(items)):
            pi, a = items[i]
            for j in range(i + 1, len(items)):
                pj, b = items[j]
                if pj - pi > tolerance + 1e-6:
                    break
                if a.idx == b.idx:
                    continue
                nd = dot(a.normal, b.normal)
                if abs(nd) < 1 - PARALLEL_EPS:
                    continue
                gap = dot(a.normal, b.corners[0]) - a.plane
                if abs(gap) > tolerance:
                    continue
                n = a.normal
                ref = (1, 0, 0) if abs(n[0]) < 0.9 else (0, 1, 0)
                eu = norm(cross(n, ref))
                ev = cross(n, eu)
                pa = ccw([(dot(p, eu), dot(p, ev)) for p in a.corners])
                pb = ccw([(dot(p, eu), dot(p, ev)) for p in b.corners])
                if abs(poly_area(pa)) < AREA_EPS or abs(poly_area(pb)) < AREA_EPS:
                    continue
                inter = clip(pa, pb)
                if len(inter) < 3:
                    continue
                area = abs(poly_area(inter))
                if area < AREA_EPS:
                    continue
                pts = [tuple(a.plane * n[k] + q[0] * eu[k] + q[1] * ev[k] for k in range(3)) for q in inter]
                yield a, b, nd > 0, gap, area, pts


def check(model, tolerance):
    faces = faces_of(model)
    fights, hidden = [], []
    for a, b, same, gap, area, pts in pairs(faces, tolerance):
        box = [[r4(min(p[k] for p in pts)) for k in range(3)], [r4(max(p[k] for p in pts)) for k in range(3)]]
        entry = {
            'a': a.elem, 'a_index': a.idx, 'a_face': a.dir, 'b': b.elem, 'b_index': b.idx, 'b_face': b.dir,
            'plane': plane_label(a.normal, a.plane), 'gap': r4(gap), 'area': r4(area), 'overlap': box,
            'rotated': a.rotated or b.rotated, 'a_texture': a.texture, 'b_texture': b.texture,
            '_faces': (a, b),
        }
        if same:
            entry['visible'] = not looks_same(a, b, pts)
            fights.append(entry)
        else:
            cov_a = area >= a.area - AREA_EPS
            cov_b = area >= b.area - AREA_EPS
            if cov_a or cov_b:
                entry['covered'] = 'both' if cov_a and cov_b else ('a' if cov_a else 'b')
                hidden.append(entry)
    return fights, hidden


# ---------------------------------------------------------------------------------------------------------- fix

def contains_box(outer, inner, tol):
    (of, ot), (inf, int_) = outer, inner
    return all(of[k] - tol <= inf[k] and int_[k] <= ot[k] + tol for k in range(3))


def rnd(v):
    v = round(v, 5)
    return int(v) if v == int(v) else v


def move_face(model_elem, project_elem, face_dir, delta, whole=False):
    """Moves a face `delta` px along its outward normal (in the element's own frame); `whole` moves the element."""
    k, end = FACE_COORD[face_dir]
    sign = 1 if end == 'to' else -1
    old = (list(model_elem['from']), list(model_elem['to']))
    pold = project_elem and (list(project_elem['from']), list(project_elem['to']))
    for elem in filter(None, (model_elem, project_elem)):
        if whole:
            elem['from'][k] = rnd(elem['from'][k] + sign * delta)
            elem['to'][k] = rnd(elem['to'][k] + sign * delta)
        elif end == 'to':
            elem['to'][k] = rnd(elem['to'][k] + delta)
        else:
            elem['from'][k] = rnd(elem['from'][k] - delta)
    if project_elem:
        sync_project(model_elem, project_elem, old, pold)


def sync_project(model_elem, project_elem, old, pold):
    """A project coordinate that rounded to the model's old value takes the model's new one, so a re-export of
    the project writes exactly the fixed model JSON."""
    for end, key in enumerate(('from', 'to')):
        for k in range(3):
            if old[end][k] != model_elem[key][k] and rnd(pold[end][k]) == old[end][k]:
                project_elem[key][k] = model_elem[key][k]


def thickness(elem, face_dir):
    k = FACE_COORD[face_dir][0]
    return elem['to'][k] - elem['from'][k]


def fix_model(model, project_elems, tolerance, log, frozen=frozenset()):
    """Applies the --fix rules until no visible fight is left; returns the list of actions.

    Elements whose index is in `frozen` never move (--mirror: the variant's elements shared with its base)."""
    moved = set()           # (element index, face)
    actions = []
    for _ in range(200):
        fights, _ = check(model, tolerance)
        fights = [f for f in fights if f['visible']]
        if not fights:
            return actions
        touched = set()
        for fight in fights:
            a, b = fight['_faces']
            if (a.idx, a.dir) in touched or (b.idx, b.dir) in touched:
                continue
            area = fight['area']
            contained = [f for f in (a, b) if area >= f.area - AREA_EPS]
            action = None
            inlay = None
            movable = [f for f in (a, b) if f.idx not in frozen]
            if not movable:
                raise RuntimeError('fight between two frozen elements: %s[%d].%s / %s[%d].%s'
                                   % (a.elem, a.idx, a.dir, b.elem, b.idx, b.dir))
            contained = [f for f in contained if f.idx not in frozen]
            if contained:
                x = min(contained, key=lambda f: f.area)
                y = b if x is a else a
                inlay = x.rot_key == y.rot_key and contains_box(y.box, x.box, tolerance)
            if inlay:
                # a detail set into the other element (keyhole, writing, bore, a speck of grain): bring it out
                gap = dot(x.normal, x.corners[0]) - y.plane
                delta = STEP - gap
                whole = thickness(model['elements'][x.idx], x.dir) < 1e-6
                move_face(model['elements'][x.idx], project_elems and project_elems[x.idx], x.dir, delta, whole)
                action = ('proud', x, y, delta)
            else:
                def priority(f):
                    other = b if f is a else a
                    return (0 if f.rotated and not other.rotated else 1,
                            1 if (f.idx, f.dir) in moved else 0, f.area)
                if contained:
                    # a part that runs on through the other (a post through its cap): hide its end inside
                    x = min(contained, key=lambda f: f.area)
                else:
                    x = min(movable, key=priority)
                y = b if x is a else a
                gap = dot(x.normal, x.corners[0]) - y.plane
                elem = model['elements'][x.idx]
                t = thickness(elem, x.dir)
                delta = -STEP - gap
                if t < 1e-6:
                    move_face(elem, project_elems and project_elems[x.idx], x.dir, delta, True)
                    action = ('recess (whole element)', x, y, delta)
                elif t + delta < STEP:
                    delta = STEP - gap
                    move_face(elem, project_elems and project_elems[x.idx], x.dir, delta)
                    action = ('proud (too thin to recess)', x, y, delta)
                else:
                    move_face(elem, project_elems and project_elems[x.idx], x.dir, delta)
                    action = ('recess', x, y, delta)
            kind, x, y, delta = action
            moved.add((x.idx, x.dir))
            touched.add((x.idx, x.dir))
            touched.add((y.idx, y.dir))
            actions.append({'action': kind, 'element': x.elem, 'index': x.idx, 'face': x.dir,
                            'against': y.elem, 'against_index': y.idx, 'delta': r4(delta)})
            log('  %s %s[%d].%s against %s[%d].%s by %s' % (kind, x.elem, x.idx, x.dir, y.elem, y.idx, y.dir,
                                                         r4(delta)))
    raise RuntimeError('fix did not converge')


def project_path(name):
    base = PROJECT_NAMES.get(name, name.split('/')[-1])
    return os.path.join(PROJECTS, base + '.bbmodel')


def outliner_order(project):
    def flat(nodes):
        out = []
        for x in nodes:
            if isinstance(x, str):
                out.append(x)
            else:
                out += flat(x.get('children', []))
        return out
    by_id = {e['uuid']: e for e in project['elements']}
    order = [by_id[u] for u in flat(project.get('outliner', [])) if u in by_id]
    return [e for e in order if e.get('export', True) and e.get('type', 'cube') == 'cube']


def dump_like(original_text, data):
    if original_text.startswith('{\n\t'):
        out = json.dumps(data, indent='\t', ensure_ascii=False)
    elif original_text.startswith('{\n  '):
        out = json.dumps(data, indent=2, ensure_ascii=False)
    elif original_text.startswith('{"') and '": ' not in original_text[:40]:
        out = json.dumps(data, ensure_ascii=False, separators=(',', ':'))   # a compact Blockbench save
    else:
        out = json.dumps(data, ensure_ascii=False)
    return out + ('\n' if original_text.endswith('\n') else '')


# --------------------------------------------------------------------------------------------------------- main

def default_files():
    return sorted(glob.glob(os.path.join(MODELS, 'block', '*.json')) + glob.glob(os.path.join(MODELS, 'item', '*.json')))


def model_name(path):
    return os.path.basename(os.path.dirname(path)) + '/' + os.path.splitext(os.path.basename(path))[0]


def describe(name, x):
    tag = 'VISIBLE' if x.get('visible') else 'same-look'
    return ('%s: %s %s[%d].%s / %s[%d].%s %s gap %s overlap %s..%s area %s%s%s' % (
        name, tag, x['a'], x['a_index'], x['a_face'], x['b'], x['b_index'], x['b_face'], x['plane'], x['gap'],
        x['overlap'][0], x['overlap'][1], x['area'],
        (' textures %s / %s' % (x['a_texture'], x['b_texture'])) if x.get('visible') else '',
        ' (rotated)' if x['rotated'] else ''))


def shared_elements(base_path, variant_path):
    """{variant index: base index} of the variant's elements that are identical to the base element of that name."""
    with open(base_path) as fh:
        base = json.load(fh)['elements']
    with open(variant_path) as fh:
        variant = json.load(fh)['elements']
    by_name = {}
    for i, e in enumerate(base):
        by_name.setdefault(e.get('name'), []).append(i)
    out = {}
    for i, e in enumerate(variant):
        match = by_name.get(e.get('name'), [])
        if len(match) == 1 and base[match[0]] == e:
            out[i] = match[0]
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description='Z-fighting lint for the hand-made models.')
    ap.add_argument('files', nargs='*')
    ap.add_argument('--tolerance', type=float, default=0.03, help='max plane distance in px (default 0.03)')
    ap.add_argument('--json', action='store_true', help='print the full report as JSON')
    ap.add_argument('--summary', action='store_true', help='one line per model')
    ap.add_argument('--warnings', action='store_true', help='also list same-look fights and hidden faces')
    ap.add_argument('--exclude', nargs='*', default=[], help='model names (item/pistol or pistol) to skip')
    ap.add_argument('--fix', action='store_true', help='fix visible fights in the model JSON and its project')
    ap.add_argument('--mirror', nargs=2, action='append', default=[], metavar=('BASE', 'VARIANT'),
                    help='with --fix: fix BASE, give the VARIANT\'s elements that are identical to the base the same '
                         'moves, then fix the rest of the variant without moving them (repeatable)')
    args = ap.parse_args(argv)
    if args.mirror and not args.fix:
        ap.error('--mirror needs --fix')
    files = list(args.files or ([] if args.mirror else default_files()))
    mirrored = {}           # variant path -> (base path, {variant index: base index} before the base is fixed)
    for base, variant in args.mirror:
        mirrored[os.path.abspath(variant)] = (os.path.abspath(base), shared_elements(base, variant))
        files = [f for f in files if os.path.abspath(f) not in (os.path.abspath(base), os.path.abspath(variant))]
        files += [base, variant]
    base_moves = {}         # base path -> [(from, to) before, (from, to) after] per element
    report = {}
    for f in files:
        name = model_name(f)
        if name in args.exclude or name.split('/')[-1] in args.exclude:
            continue
        with open(f) as fh:
            text = fh.read()
        model = json.loads(text)
        if args.fix:
            ppath = project_path(name)
            ptext, project, pelems = None, None, None
            if os.path.exists(ppath):
                with open(ppath) as fh:
                    ptext = fh.read()
                project = json.loads(ptext)
                pelems = outliner_order(project)
                if len(pelems) != len(model['elements']):
                    print('%s: project has %d elements, model %d; fixing the model only'
                          % (name, len(pelems), len(model['elements'])), file=sys.stderr)
                    pelems = None
            else:
                print('%s: no project %s; fixing the model only' % (name, ppath), file=sys.stderr)
            print('%s:' % name, file=sys.stderr)
            frozen = frozenset()
            mirror = mirrored.get(os.path.abspath(f))
            mirror_moved = False
            if mirror:
                before, after = base_moves[mirror[0]]
                frozen = frozenset(mirror[1])
                for vi, bi in mirror[1].items():
                    if before[bi] == after[bi]:
                        continue
                    mirror_moved = True
                    print('  mirror %s[%d] from the base' % (model['elements'][vi].get('name', '?'), vi),
                          file=sys.stderr)
                    elem = model['elements'][vi]
                    old = (list(elem['from']), list(elem['to']))
                    pold = pelems and (list(pelems[vi]['from']), list(pelems[vi]['to']))
                    elem['from'], elem['to'] = list(after[bi][0]), list(after[bi][1])
                    if pelems:
                        sync_project(elem, pelems[vi], old, pold)
            snapshot = [(list(e['from']), list(e['to'])) for e in model['elements']]
            actions = fix_model(model, pelems, args.tolerance, lambda s: print(s, file=sys.stderr), frozen)
            base_moves[os.path.abspath(f)] = (snapshot, [(list(e['from']), list(e['to'])) for e in model['elements']])
            if actions or mirror_moved:
                with open(f, 'w') as fh:
                    fh.write(dump_like(text, model))
                if pelems is not None:
                    with open(ppath, 'w') as fh:
                        fh.write(dump_like(ptext, project))
        fights, hidden = check(model, args.tolerance)
        report[name] = {'fights': fights, 'hidden': hidden}
    for r in report.values():
        for x in r['fights'] + r['hidden']:
            x.pop('_faces', None)
    visible_total = sum(1 for r in report.values() for x in r['fights'] if x['visible'])
    same_total = sum(len(r['fights']) for r in report.values()) - visible_total
    hidden_total = sum(len(r['hidden']) for r in report.values())
    if args.json:
        print(json.dumps({'tolerance': args.tolerance, 'visible': visible_total, 'same_look': same_total,
                          'hidden': hidden_total, 'models': report}, indent=1))
        return 1 if visible_total else 0
    for name, r in report.items():
        vis = [x for x in r['fights'] if x['visible']]
        same = [x for x in r['fights'] if not x['visible']]
        if args.summary:
            if vis or (args.warnings and (same or r['hidden'])):
                print('%s: %d visible, %d same-look, %d hidden' % (name, len(vis), len(same), len(r['hidden'])))
            continue
        for x in vis + (same if args.warnings else []):
            print(describe(name, x))
        if args.warnings:
            for x in r['hidden']:
                print('%s: hidden(%s) %s[%d].%s / %s[%d].%s %s area %s' % (
                    name, x['covered'], x['a'], x['a_index'], x['a_face'], x['b'], x['b_index'], x['b_face'],
                    x['plane'], x['area']))
    print('%d models, %d visible fights, %d same-look fights (warning), %d hidden-face pairs (warning)'
          % (len(report), visible_total, same_total, hidden_total))
    return 1 if visible_total else 0


if __name__ == '__main__':
    sys.exit(main())
