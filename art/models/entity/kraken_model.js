// Model and skin builder for kraken.bbmodel (K1b). Run in Blockbench (risky_eval) in a GeckoLib Animated Model project
// of 128x64 (see art/README.md, "Kraken (K1b)"), then kraken_animations.js, then the export:
//   window.KRK = {REPO: '<repo>', UUID: Project.uuid}; eval(require('fs').readFileSync(KRK.REPO + '/art/models/entity/kraken_model.js', 'utf8'));
//   KRK.build(); KRK.paint(); (kraken_animations.js: KRKA.build()); KRK.export();
// Every entry point checks that the selected project is KRK.UUID: risky_eval acts on whichever tab is selected.
//
// Rig contract (K1a, KrakenRigTest): 1 px per unit, y up, the kraken faces -z, +x (file) is its left. Bones
// root [0,0,0] > mantle [0,30,0] > eye_left [12,40,-20], eye_right [-12,40,-20]; tentacle_<i>_1 under root on a ring of
// radius 22.4 at y 20, angle a = (i + 0.5) * 45 deg, x = -sin(a) * 22.4, z = -cos(a) * 22.4; tentacle_<i>_2 20 px and
// tentacle_<i>_3 40 px above it (KRK.SEG; K1c, was 12), a tentacle is 60 px long. Only cubes are added, never bones;
// no bone has a rest rotation.
//
// Coordinates: the body (head, mantle, fins, beak, eyes) is listed in file coordinates and mirrored in x for the build
// (Blockbench's internal x = -file x, so eye_left sits at internal -12). The tentacles are built directly in internal
// coordinates from one canonical tentacle (axis on x = z = 0, pointing up, its inner face, the sucker side, towards +z):
// tentacle i is that tentacle turned about its axis by the cube rotation y = -a (inner face towards the body's axis)
// and moved onto its ring point; the curled tip cubes add a tilt x towards the inner side (cube rotation [tilt, -a, 0],
// Euler ZYX like GeckoLib's). All eight tentacles share one set of UV rectangles.
//
// UV: per-face rectangles on the 128x64 sheet, packed on shelves (KRK.pack) at two texel scales: the body at KRK.SB, the
// fine parts (tentacles, eyes, beak, lips) at KRK.SF; mirror faces (east of a centred cube, the right eye and fin) share
// the rectangle of their twin with u reversed. Solid parts (suckers, pupils, glints) map onto patches in the bottom-right
// corner. Faces fully inside another cube of the body are left out (texture null).
// The skin is painted per texel from the 3D point of the texel centre (read back from the cube meshes in the rest
// pose) and a value noise of that point: the same model gives the same pixels (byte-identical PNG).
window.KRK = window.KRK || {};
// (not "KR": Blockbench has a global KR of its own)
KRK.REPO = KRK.REPO || '';
KRK.W = 128; KRK.H = 64;
KRK.HS = 0.6; KRK.N = 8; KRK.RING = 22.4; KRK.RING_Y = 20; KRK.SEG = 20;
KRK.check = function () {
  if (!KRK.UUID || !Project || Project.uuid !== KRK.UUID) throw new Error('not the kraken project: ' + (Project && Project.uuid));
};
KRK.ang = i => (i + 0.5) * Math.PI / 4;
// file-coordinate bone pivots (the contract)
KRK.BONES = (function () {
  const B = [['root', null, [0, 0, 0]], ['mantle', 'root', [0, 30, 0]], ['eye_left', 'mantle', [12, 40, -20]], ['eye_right', 'mantle', [-12, 40, -20]]];
  for (let i = 0; i < KRK.N; i++) {
    const a = KRK.ang(i), x = Math.round(-Math.sin(a) * KRK.RING * 10000) / 10000 + 0, z = Math.round(-Math.cos(a) * KRK.RING * 10000) / 10000 + 0;
    B.push(['tentacle_' + i + '_1', 'root', [x, 20, z]]);
    B.push(['tentacle_' + i + '_2', 'tentacle_' + i + '_1', [x, KRK.RING_Y + KRK.SEG, z]]);
    B.push(['tentacle_' + i + '_3', 'tentacle_' + i + '_2', [x, KRK.RING_Y + 2 * KRK.SEG, z]]);
  }
  return B;
})();
KRK.FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
// 4x4 sucker patch (pale disc, dark rim), 2x2 solids
KRK.PATCHES = {sucker: [124, 60, 4], sucker_side: [122, 62, 2], pupil: [122, 60, 2], glint: [120, 62, 2], beak_d: [120, 60, 2]};

// ---- body part list (file coordinates) ---------------------------------------------------------------------------
// [bone, name, from, to, opts]; opts: tag (paint), grp ('b' body scale, 'f' fine scale), mirrorOf, hide
KRK.bodyParts = function () {
  const P = [];
  const add = (bone, name, from, to, o) => P.push({bone, name, from, to, o: Object.assign({tag: 'skin', grp: 'b'}, o || {})});
  // a round layer: a square core and four slabs on its sides (a rounded square of radius r); the slabs reach 0.1 px
  // past the core at both ends so that no slab shares a top or bottom plane with a core (V1). The south slab is the
  // z-mirror twin of the north one, the east slab the x-mirror of the west one (shared UV).
  const layer = (bone, name, y0, y1, r, o) => {
    const R = v => Math.round(v * 100) / 100;
    if (r < 2.5) { add(bone, name + '_core', [-R(0.9 * r), y0, -R(0.9 * r)], [R(0.9 * r), y1, R(0.9 * r)], o); return; }
    const rc = R(0.84 * r), w = R(0.6 * r), inn = R(rc - Math.min(1, 0.1 * r)), rr = R(r);
    add(bone, name + '_core', [-rc, y0, -rc], [rc, y1, rc], Object.assign({role: 'core'}, o));
    add(bone, name + '_n', [-w, y0 - 0.1, -rr], [w, y1 + 0.1, -inn], o);
    add(bone, name + '_s', [-w, y0 - 0.1, inn], [w, y1 + 0.1, rr], Object.assign({twinZ: name + '_n'}, o));
    add(bone, name + '_w', [inn, y0 - 0.1, -w], [rr, y1 + 0.1, w], Object.assign({role: 'slabW', slabN: name + '_n'}, o));
    add(bone, name + '_e', [-rr, y0 - 0.1, -w], [-inn, y1 + 0.1, w], Object.assign({mirrorOf: name + '_w'}, o));
  };
  // head: arm crown (the underside where the tentacles root), bulge with the eyes, neck
  layer('mantle', 'head_0', 12, 16, 10.8);
  layer('mantle', 'head_1', 16, 22, 14.6);
  layer('mantle', 'head_2', 22, 30, 18.4);
  layer('mantle', 'head_3', 30, 46, 21.4);
  layer('mantle', 'head_4', 46, 52, 18.2);
  // mantle: tall, tapering to a pointed crown
  layer('mantle', 'mantle_0', 52, 60, 15.6);
  layer('mantle', 'mantle_1', 60, 68, 13.2);
  layer('mantle', 'mantle_2', 68, 76, 11.3);
  layer('mantle', 'mantle_3', 76, 82, 8.9);
  layer('mantle', 'mantle_4', 82, 87, 6.5);
  layer('mantle', 'mantle_5', 87, 90.4, 4.3);
  layer('mantle', 'mantle_6', 90.4, 92.8, 2.5);
  layer('mantle', 'mantle_7', 92.8, 94.6, 1.1);
  // fins: a rhombus on each side near the top of the mantle, 2 px strips, the inner end buried in the mantle
  const fin = [[62, 64, 14.4], [64, 66, 16.6], [66, 68, 18.8], [68, 70, 20.8], [70, 72, 22.5], [72, 74, 23.7], [74, 76, 24.3],
    [76, 78, 23.8], [78, 80, 22.2], [80, 82, 19.8], [82, 84, 16.4], [84, 86, 12.2]];
  const inner = y => y < 68 ? 10.5 : y < 76 ? 8.4 : y < 82 ? 6.2 : 4.2;
  fin.forEach(([y0, y1, x1], k) => {
    y0 += 0.3; y1 += 0.3;
    const half = 1.1 - 0.06 * Math.abs(k - 6);
    add('mantle', 'fin_left_' + k, [inner(y0), y0, -half], [x1, y1, half], {tag: 'fin', outer: x1});
    add('mantle', 'fin_right_' + k, [-x1, y0, -half], [-inner(y0), y1, half], {tag: 'fin', outer: x1, mirrorOf: 'fin_left_' + k});
  });
  // the beak at the centre of the arm crown (underside), in a pale ring of lips
  layer('mantle', 'lips', 10, 12.4, 6.6, {tag: 'lip', grp: 'f'});
  add('mantle', 'beak_upper', [-2.4, 6.6, -2.4], [2.4, 10.6, 2.4], {tag: 'beak', grp: 'f'});
  add('mantle', 'beak_lower', [-1.9, 5.2, -0.4], [1.9, 7.2, 2.7], {tag: 'beak', grp: 'f'});
  add('mantle', 'beak_hook', [-1.4, 4.2, -2.1], [1.4, 6.9, 0.9], {tag: 'beak', grp: 'f'});
  // eyes: lid rim, iris disc (pale ring painted), pupil, glint; the right eye mirrors the left
  const eye = (bone, s, m) => {
    const X = (a, b) => s > 0 ? [a, b] : [-b, -a];
    const o = (tag, n) => ({tag, grp: 'f', mirrorOf: m ? 'eye_left_' + n : undefined});
    add(bone, bone + '_rim', [X(6.4, 17.6)[0], 34.4, -22.2], [X(6.4, 17.6)[1], 45.6, -16], o('eye_rim', 'rim'));
    add(bone, bone + '_iris', [X(7.2, 16.8)[0], 35.2, -22.8], [X(7.2, 16.8)[1], 44.8, -21.6], o('iris', 'iris'));
    add(bone, bone + '_pupil', [X(10, 14)[0], 38, -23.3], [X(10, 14)[1], 42, -22.5], o('pupil', 'pupil'));
    add(bone, bone + '_glint', [X(12.6, 13.6)[0], 40.4, -23.55], [X(12.6, 13.6)[1], 41.4, -23.1], o('glint', 'glint'));
  };
  eye('eye_left', 1, false);
  eye('eye_right', -1, true);
  return P;
};

// ---- canonical tentacle (internal coordinates, axis x = z = 0, inner side +z) ---------------------------------------
// segment cubes: [seg (1..3), name, y0 or null (chained), length, width, tilt]
// (K1c: segments 20 px apart, so _1 spans y 17..41, _2 40..61, _3 60.4 to the curled tip near y 80)
KRK.TSEG = [[1, 'a', 17, 13, 7, 0], [1, 'b', 29.6, 11.4, 6.2, 0], [2, 'a', 40, 10.6, 5.4, 0], [2, 'b', 50.4, 10.6, 4.6, 0],
  [3, 'a', 60.4, 10, 3.8, 0], [3, 'b', null, 5, 3.2, 14], [3, 'c', null, 4.4, 2.5, 32], [3, 'd', null, 3, 1.7, 56]];
// suckers per segment cube: local heights from the cube's base and size
KRK.TSUCK = {'1a': [[5, 1.2], [7.8, 1.2], [10.6, 1.2]], '1b': [[1.6, 1.1], [4.3, 1.1], [7, 1.1], [9.7, 1.05]],
  '2a': [[1.6, 1], [4.2, 1], [6.8, 1], [9.3, 1]], '2b': [[1.2, 0.9], [3.7, 0.9], [6.2, 0.9], [8.7, 0.9]],
  '3a': [[1.2, 0.8], [3.4, 0.8], [5.6, 0.8], [7.8, 0.8]], '3b': [[0.9, 0.7], [3, 0.7]], '3c': [[1.4, 0.6], [3, 0.55]]};
KRK.tentacleCanon = function () {
  const out = [];
  let end = null; // chained base: [y, z] of the previous cube's end on the curl
  for (const [seg, n, y0, len, w, tilt] of KRK.TSEG) {
    const t = tilt * Math.PI / 180;
    const base = y0 !== null ? [y0, 0] : [end[0] - 0.4 * Math.cos(t), end[1] - 0.4 * Math.sin(t)];
    const o = [0, base[0], base[1]];
    const h = w / 2;
    out.push({seg, name: 'seg' + seg + n, origin: o, tilt, from: [-h, o[1], o[2] - h], to: [h, o[1] + len, o[2] + h], tag: 'tent',
      hide: seg + n === '1a' ? [] : ['down']});
    end = [o[1] + len * Math.cos(t), o[2] + len * Math.sin(t)];
    (KRK.TSUCK[seg + n] || []).forEach(([at, s], k) => {
      const off = Math.max(0.42 * s + 0.15, w * 0.24);
      for (const side of [-1, 1]) {
        const cx = side * off, cy = o[1] + at;
        out.push({seg, name: 'suck' + seg + n + k + (side < 0 ? 'r' : 'l'), origin: o, tilt,
          from: [cx - s / 2, cy - s / 2, o[2] + h - 0.1], to: [cx + s / 2, cy + s / 2, o[2] + h + 0.35], tag: 'sucker',
          hide: ['north']});
      }
    });
  }
  return out;
};

// ---- full part list (internal coordinates) -----------------------------------------------------------------------
KRK.mirrorFace = f => f === 'east' ? 'west' : f === 'west' ? 'east' : f;
KRK.parts = function () {
  const P = [];
  for (const p of KRK.bodyParts()) {
    const from = [-p.to[0], p.from[1], p.from[2]], to = [-p.from[0], p.to[1], p.to[2]];
    P.push({bone: p.bone, name: p.name, from, to, rot: [0, 0, 0], origin: [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2],
      tag: p.o.tag, grp: p.o.grp, outer: p.o.outer, mirrorOf: p.o.mirrorOf, twinZ: p.o.twinZ, role: p.o.role, slabN: p.o.slabN, hide: (p.o.hide || []).map(KRK.mirrorFace), body: true});
  }
  const canon = KRK.tentacleCanon();
  for (let i = 0; i < KRK.N; i++) {
    const a = KRK.ang(i), phi = -a;
    const A = [Math.sin(a) * KRK.RING, 0, -Math.cos(a) * KRK.RING];
    const c = Math.cos(phi), s = Math.sin(phi);
    const ry = v => [c * v[0] + s * v[2], v[1], -s * v[0] + c * v[2]];
    const yaw = Math.round(-((i + 0.5) * 45) * 1000) / 1000;
    for (const q of canon) {
      const o2 = ry(q.origin).map((v, k) => v + A[k]);
      const d = o2.map((v, k) => v - q.origin[k]);
      P.push({bone: 'tentacle_' + i + '_' + q.seg, name: 't' + i + '_' + q.name, from: q.from.map((v, k) => v + d[k]),
        to: q.to.map((v, k) => v + d[k]), rot: [q.tilt, yaw < -180 ? yaw + 360 : yaw, 0], origin: o2, tag: q.tag, grp: 'f',
        hide: q.hide, tentacle: i, canon: q.name, canonFrom: q.from, canonTo: q.to});
    }
  }
  return P;
};
KRK.dims = (from, to, f) => {
  const d = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
  return f === 'north' || f === 'south' ? [d[0], d[1]] : f === 'east' || f === 'west' ? [d[2], d[1]] : [d[0], d[2]];
};
// a body face is hidden when its rectangle, nudged 0.01 px outwards, lies inside another unrotated body cube
KRK.covered = function (p, f, body) {
  const ax = f === 'east' || f === 'west' ? 0 : f === 'up' || f === 'down' ? 1 : 2;
  const sgn = f === 'east' || f === 'up' || f === 'south' ? 1 : -1;
  const lo = p.from.slice(), hi = p.to.slice();
  const plane = (sgn > 0 ? p.to[ax] : p.from[ax]) + sgn * 0.01;
  lo[ax] = hi[ax] = plane;
  return body.some(q => q !== p && q.bone === p.bone && [0, 1, 2].every(k => q.from[k] <= lo[k] + 1e-6 && q.to[k] >= hi[k] - 1e-6));
};
KRK.layout = function (P) {
  const body = P.filter(p => p.body && p.tag !== 'pupil' && p.tag !== 'glint');
  const prim = [], refs = [], hidden = {};
  for (const p of P) {
    hidden[p.name] = (p.hide || []).slice();
    if (p.body) for (const f of KRK.FACES) if (!hidden[p.name].includes(f) && KRK.covered(p, f, body)) hidden[p.name].push(f);
  }
  // a face reuses another face's rectangle: [target cube, target face, flip] with flip 'u' or 'v' (mirror) or ''
  const vis = (n, f) => !hidden[n].includes(f);
  const twin = (p, f) => {
    if (p.tentacle > 0) return ['t0_' + p.canon, f, ''];
    if (p.mirrorOf) return [p.mirrorOf, KRK.mirrorFace(f), 'u'];
    if (p.twinZ) return [p.twinZ, f === 'north' ? 'south' : f === 'south' ? 'north' : f, f === 'up' || f === 'down' ? 'v' : 'u'];
    if (!p.body) return null;
    // round layers repeat their sides four times: the core's west side is its north side, the side slab's outer
    // face is the front slab's front, the side slab's front is the front slab's side
    if (p.role === 'core' && f === 'west' && vis(p.name, 'north')) return [p.name, 'north', ''];
    if (p.role === 'core' && f === 'east' && vis(p.name, 'north')) return [p.name, 'north', 'u'];
    if (p.role === 'slabW' && f === 'west') return [p.slabN, 'north', ''];
    if (p.role === 'slabW' && f === 'north') return [p.slabN, 'west', ''];
    if (f === 'east' && Math.abs(p.from[0] + p.to[0]) < 1e-6 && vis(p.name, 'west')) return [p.name, 'west', 'u'];
    if (f === 'south' && Math.abs(p.from[2] + p.to[2]) < 1e-6 && vis(p.name, 'north')) return [p.name, 'north', 'u'];
    return null;
  };
  for (const p of P) {
    if (KRK.PATCHES[p.tag]) continue;
    for (const f of KRK.FACES) {
      if (!vis(p.name, f)) continue;
      const t = twin(p, f);
      if (t) { refs.push([p.name, f].concat(t)); continue; }
      prim.push([p.name, f, KRK.dims(p.from, p.to, f), p.grp]);
    }
  }
  return {prim, refs, hidden};
};
// shelf packer: body faces at scale sb, fine faces at sf (texels per px); the bottom-right 8x4 corner holds patches
KRK.pack = function (P, sb, sf) {
  const {prim, refs, hidden} = KRK.layout(P);
  const items = prim.map(([n, f, d, g]) => {
    const s = g === 'f' ? sf : (f === 'up' || f === 'down') ? Math.round(sb * KRK.HS * 100) / 100 : sb;
    return {n, f, w: Math.max(1, Math.round(d[0] * s)), h: Math.max(1, Math.round(d[1] * s))};
  });
  items.sort((a, b) => b.h - a.h || b.w - a.w || (a.n + a.f < b.n + b.f ? -1 : 1));
  let x = 0, y = 0, row = 0;
  const rects = {};
  const blocked = (x0, y0, w, h) => x0 + w > KRK.W - 8 && y0 + h > KRK.H - 4;
  for (const it of items) {
    if (x + it.w > KRK.W || blocked(x, y, it.w, it.h)) { x = 0; y += row; row = 0; }
    if (y + it.h > KRK.H || blocked(x, y, it.w, it.h)) return null;
    rects[it.n + '.' + it.f] = [x, y, x + it.w, y + it.h];
    x += it.w; row = Math.max(row, it.h);
  }
  const primary = Object.assign({}, rects);
  const refOf = {};
  for (const [n, f, tn, tf, flip] of refs) refOf[n + '.' + f] = [tn + '.' + tf, flip];
  const resolve = (key, depth) => {
    if (rects[key]) return rects[key];
    if (!refOf[key] || depth > 8) throw new Error('no rectangle for ' + key);
    const [t, flip] = refOf[key], r = resolve(t, depth + 1);
    return (rects[key] = flip === 'u' ? [r[2], r[1], r[0], r[3]] : flip === 'v' ? [r[0], r[3], r[2], r[1]] : r.slice());
  };
  for (const key in refOf) resolve(key, 0);
  return {rects, primary, hidden, used: y + row};
};
KRK.fit = function (P) {
  for (let sf = 1; sf > 0.5; sf = Math.round((sf - 0.1) * 100) / 100) {
    for (let sb = sf; sb > 0.3; sb = Math.round((sb - 0.02) * 100) / 100) {
      const r = KRK.pack(P, sb, sf);
      if (r) return Object.assign(r, {sb, sf});
    }
  }
  throw new Error('no fit');
};

// V1 by construction: two unrotated cubes of one bone frame must not have same-facing faces in one plane (closer than
// 0.05 px) overlapping in area. Checks the body in file space and the canonical tentacle (untilted cubes).
KRK.lint = function () {
  const check = (list) => {
    const bad = [];
    for (let i = 0; i < list.length; i++) for (let j = i + 1; j < list.length; j++) {
      const p = list[i], q = list[j];
      for (let ax = 0; ax < 3; ax++) for (const side of [0, 1]) {
        const a = side ? p.to[ax] : p.from[ax], b = side ? q.to[ax] : q.from[ax];
        if (Math.abs(a - b) >= 0.05) continue;
        const o = [0, 1, 2].filter(k => k !== ax).every(k => Math.min(p.to[k], q.to[k]) - Math.max(p.from[k], q.from[k]) > 1e-4);
        if (o) bad.push(p.name + '/' + q.name + ' ' + 'xyz'[ax] + (side ? '+' : '-'));
      }
    }
    return bad;
  };
  const body = KRK.bodyParts().map(p => ({name: p.name, from: p.from, to: p.to}));
  const tent = KRK.tentacleCanon().filter(q => q.tilt === 0);
  return check(body).concat(check(tent));
};

KRK.build = function () {
  KRK.check();
  const P = KRK.parts();
  const lint = KRK.lint();
  if (lint.length) throw new Error('V1: ' + lint.join(', '));
  const fit = KRK.fit(P);
  KRK.P = P; KRK.primary = fit.primary; KRK.fitInfo = {sb: fit.sb, sf: fit.sf, used: fit.used};
  Undo.initEdit({outliner: true, elements: [], selection: true});
  for (const e of [...Outliner.elements]) e.remove();
  for (const g of [...Group.all]) g.remove(false);
  const G = {};
  for (const [n, p, o] of KRK.BONES) G[n] = new Group({name: n, origin: [-o[0] + 0, o[1], o[2]]}).addTo(p ? G[p] : undefined).init();
  const made = [];
  for (const p of P) {
    const faces = {};
    for (const f of KRK.FACES) {
      if (fit.hidden[p.name].includes(f)) { faces[f] = {uv: [0, 0, 0, 0], texture: null}; continue; }
      const pt = KRK.PATCHES[p.tag === 'beak' ? 'none' : p.tag];
      if (pt) {
        const [u, v, n] = p.tag === 'sucker' && f !== 'south' ? KRK.PATCHES.sucker_side : pt;
        faces[f] = {uv: n === 4 ? [u, v, u + 4, v + 4] : [u + 0.5, v + 0.5, u + 1.5, v + 1.5]};
        continue;
      }
      faces[f] = {uv: fit.rects[p.name + '.' + f].slice()};
    }
    const c = new Cube({name: p.name, color: made.length % 8, from: p.from, to: p.to, box_uv: false, rotation: p.rot, origin: p.origin, faces})
      .addTo(G[p.bone]).init();
    made.push(c);
  }
  KRK.byName = {};
  for (const p of P) KRK.byName[p.name] = p;
  Undo.finishEdit('KRK build', {outliner: true, elements: made});
  Canvas.updateAll();
  return {cubes: Cube.all.length, groups: Group.all.length, fit: KRK.fitInfo};
};

// ---- skin --------------------------------------------------------------------------------------------------------
KRK.C = {
  skin_d: [70, 24, 22], skin: [112, 44, 36], skin_l: [150, 72, 52], pale: [206, 154, 132], pale_d: [180, 124, 108],
  fin_edge: [44, 14, 14], spot: [226, 186, 138], spot_ring: [52, 16, 14],
  inner: [214, 164, 142], sucker: [240, 218, 200], sucker_rim: [96, 36, 32],
  lip: [176, 104, 98], lip_d: [128, 64, 64], beak: [20, 16, 16], beak_e: [62, 52, 46],
  rim: [88, 32, 28], rim_l: [128, 56, 46], iris_pale: [238, 226, 176], iris: [210, 168, 64], iris_d: [112, 74, 30],
  pupil: [10, 8, 10], glint: [250, 250, 240]
};
KRK.hash3 = function (a, b, c) {
  let h = (a * 374761393 + b * 668265263 + c * 1440662683) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h = h ^ (h >>> 16);
  return (h >>> 0) / 4294967296;
};
KRK.noise = function (p, cell) {
  const q = p.map(v => v / cell), i = q.map(Math.floor), f = q.map((v, k) => { const t = v - i[k]; return t * t * (3 - 2 * t); });
  let s = 0;
  for (let dx = 0; dx < 2; dx++) for (let dy = 0; dy < 2; dy++) for (let dz = 0; dz < 2; dz++) {
    const w = (dx ? f[0] : 1 - f[0]) * (dy ? f[1] : 1 - f[1]) * (dz ? f[2] : 1 - f[2]);
    s += w * KRK.hash3(i[0] + dx, i[1] + dy, i[2] + dz);
  }
  return s;
};
KRK.mix = (a, b, t) => [0, 1, 2].map(i => a[i] + (b[i] - a[i]) * t);
KRK.smooth = (e0, e1, x) => { const t = Math.min(1, Math.max(0, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };
KRK.mottle = function (p) {
  const C = KRK.C, m = 0.8 * KRK.noise(p, 3.6) + 0.2 * KRK.noise(p.map(v => v + 17.3), 1.8);
  return m < 0.5 ? KRK.mix(C.skin_d, C.skin, KRK.smooth(0.28, 0.5, m)) : KRK.mix(C.skin, C.skin_l, KRK.smooth(0.55, 0.75, m));
};
// colour of a texel at internal point p, outward normal n; part record pr
KRK.colour = function (pr, p, n) {
  const C = KRK.C, y = p[1];
  switch (pr.tag) {
    case 'skin': {
      let c = KRK.mottle(p);
      const under = Math.max(KRK.smooth(23, 13, y), n[1] < -0.5 ? 0.75 : 0);
      c = KRK.mix(c, KRK.noise(p, 2.2) < 0.4 ? C.pale_d : C.pale, under);
      if (y > 69 && n[1] > -0.5) {
        // the crown pattern: rows of pale spots with dark rings, offset every other row
        const r = Math.max(1, Math.hypot(p[0], p[2])), th = Math.atan2(p[0], p[2]);
        const rowH = 4.6, k = Math.floor((y - 69) / rowH), per = 8;
        const fth = th / (2 * Math.PI) * per + 0.5 * k, ft = fth - Math.floor(fth);
        const arc = (ft - 0.5) * 2 * Math.PI * r / per, dy = ((y - 69) / rowH - k - 0.5) * rowH;
        const d = Math.hypot(arc, dy), rad = Math.min(1.5, 0.22 * r);
        if (d < rad) c = C.spot; else if (d < rad + 0.7) c = C.spot_ring;
      }
      return c;
    }
    case 'fin': {
      let c = KRK.mottle(p);
      const edge = KRK.smooth(pr.outer - 3, pr.outer - 0.4, Math.abs(p[0]));
      return KRK.mix(c, C.fin_edge, edge * 0.9);
    }
    case 'tent': {
      const ax = [Math.sin(KRK.ang(pr.tentacle)) * KRK.RING, -Math.cos(KRK.ang(pr.tentacle)) * KRK.RING];
      const inw = [-ax[0], -ax[1]], L = Math.hypot(...inw);
      const dot = (n[0] * inw[0] + n[2] * inw[1]) / L;
      let c = KRK.mottle(p);
      if (dot > 0.7) return KRK.noise(p, 1.5) < 0.35 ? C.pale_d : C.inner;
      if (n[1] > 0.7 || n[1] < -0.7) c = KRK.mix(c, C.inner, 0.25);
      // the sides lighten towards the sucker face; banding gets darker towards the tip
      const lat = ((p[0] - ax[0]) * inw[0] + (p[2] - ax[1]) * inw[1]) / L;
      c = KRK.mix(c, C.inner, KRK.smooth(0.2, 3.4, lat) * 0.5 * (Math.abs(dot) < 0.3 ? 1 : 0));
      return KRK.mix(c, C.skin_d, KRK.smooth(55, 78, y) * 0.5);
    }
    case 'lip': {
      const a = Math.atan2(p[0], p[2]);
      return Math.abs(Math.sin(a * 6)) < 0.25 ? C.lip_d : C.lip;
    }
    case 'beak': return KRK.hash3(Math.round(p[0] * 4), Math.round(p[1] * 4), Math.round(p[2] * 4)) < 0.18 || y < 5 ? C.beak_e : C.beak;
    case 'eye_rim': {
      const ex = p[0] < 0 ? -12 : 12, d = Math.hypot(p[0] - ex, y - 40);
      return n[2] < -0.5 && d > 4.9 ? KRK.mix(C.rim, C.rim_l, KRK.smooth(5.0, 5.6, d)) : C.rim;
    }
    case 'iris': {
      if (n[2] > -0.5) return C.iris_d;
      const ex = p[0] < 0 ? -12 : 12, d = Math.hypot(p[0] - ex, y - 40);
      return d < 2.4 ? C.iris_d : d < 3.6 ? C.iris : d < 4.4 ? C.iris_pale : C.iris_d;
    }
  }
  return [255, 0, 255];
};
KRK.FACE_ORDER = ['east', 'west', 'up', 'down', 'south', 'north'];
KRK.corners = function (cube) {
  const g = cube.mesh.geometry, pos = g.attributes.position, uv = g.attributes.uv;
  cube.mesh.updateMatrixWorld(true);
  const inv = new THREE.Matrix4().copy(Project.model_3d.matrixWorld).invert();
  const out = {};
  KRK.FACE_ORDER.forEach((f, k) => {
    const list = [];
    for (let i = 4 * k; i < 4 * k + 4; i++) {
      const p = new THREE.Vector3(pos.getX(i), pos.getY(i), pos.getZ(i));
      cube.mesh.localToWorld(p); p.applyMatrix4(inv);
      list.push({p: [p.x, p.y, p.z], uv: [uv.getX(i) * KRK.W, (1 - uv.getY(i)) * KRK.H]});
    }
    out[f] = list;
  });
  return out;
};
KRK.paintPixels = function () {
  Animator.showDefaultPose && Animator.showDefaultPose();
  const px = new Uint8ClampedArray(KRK.W * KRK.H * 4);
  const owner = new Array(KRK.W * KRK.H).fill(null);
  let overlaps = 0;
  const C = KRK.C;
  const put = (u, v, c) => { const k = (v * KRK.W + u) * 4; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = 255; };
  const solid = (u, v, n, c) => { for (let j = 0; j < n; j++) for (let i = 0; i < n; i++) put(u + i, v + j, c); };
  const [su, sv] = KRK.PATCHES.sucker;
  for (let j = 0; j < 4; j++) for (let i = 0; i < 4; i++) put(su + i, sv + j, (i === 0 || j === 0 || i === 3 || j === 3) ? C.sucker_rim : C.sucker);
  solid(...KRK.PATCHES.sucker_side.slice(0, 2), 2, C.sucker_rim);
  solid(...KRK.PATCHES.pupil.slice(0, 2), 2, C.pupil);
  solid(...KRK.PATCHES.glint.slice(0, 2), 2, C.glint);
  solid(...KRK.PATCHES.beak_d.slice(0, 2), 2, C.beak);
  for (const cube of Cube.all) {
    const pr = KRK.byName[cube.name];
    if (!pr || KRK.PATCHES[pr.tag]) continue;
    const cs = KRK.corners(cube);
    for (const f of KRK.FACES) {
      if (cube.faces[f].texture === null || !cs[f] || !KRK.primary[cube.name + '.' + f]) continue;
      const list = cs[f];
      const us = list.map(c => c.uv[0]), vs = list.map(c => c.uv[1]);
      const u0 = Math.min(...us), u1 = Math.max(...us), v0 = Math.min(...vs), v1 = Math.max(...vs);
      const at = (uu, vv) => list.find(c => Math.abs(c.uv[0] - uu) < 1e-3 && Math.abs(c.uv[1] - vv) < 1e-3).p;
      const A = at(u0, v0), B = at(u1, v0), Cc = at(u0, v1), D = at(u1, v1);
      const e1 = [B[0] - A[0], B[1] - A[1], B[2] - A[2]], e2 = [Cc[0] - A[0], Cc[1] - A[1], Cc[2] - A[2]];
      const nn = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
      const len = Math.hypot(...nn) || 1;
      const n = nn.map(a => a / len);
      const ctr = list.reduce((a, c) => [a[0] + c.p[0] / 4, a[1] + c.p[1] / 4, a[2] + c.p[2] / 4], [0, 0, 0]);
      const all = KRK.FACES.map(ff => cs[ff]).flat();
      const cc = all.reduce((a, c) => [a[0] + c.p[0] / all.length, a[1] + c.p[1] / all.length, a[2] + c.p[2] / all.length], [0, 0, 0]);
      const out = (ctr[0] - cc[0]) * n[0] + (ctr[1] - cc[1]) * n[1] + (ctr[2] - cc[2]) * n[2];
      const nw = out < 0 ? n.map(a => -a) : n;
      const ru = Math.round(u1 - u0), rv = Math.round(v1 - v0);
      for (let j = 0; j < rv; j++) for (let i = 0; i < ru; i++) {
        const s = (i + 0.5) / ru, t = (j + 0.5) / rv;
        const p = [0, 1, 2].map(k => A[k] * (1 - s) * (1 - t) + B[k] * s * (1 - t) + Cc[k] * (1 - s) * t + D[k] * s * t);
        const U = Math.round(u0) + i, V = Math.round(v0) + j;
        if (owner[V * KRK.W + U] !== null) { overlaps++; continue; }
        owner[V * KRK.W + U] = cube.name + '.' + f;
        put(U, V, KRK.colour(pr, p, nw).map(Math.round));
      }
    }
  }
  let free = 0;
  for (let k = 0; k < KRK.W * KRK.H; k++) if (px[k * 4 + 3] === 0) free++;
  return {px, overlaps, free};
};
KRK.toCanvas = function (px) {
  const cv = document.createElement('canvas'); cv.width = KRK.W; cv.height = KRK.H;
  const ctx = cv.getContext('2d'); const img = ctx.createImageData(KRK.W, KRK.H); img.data.set(px); ctx.putImageData(img, 0, 0);
  return cv;
};
KRK.paint = function () {
  KRK.check();
  const {px, overlaps, free} = KRK.paintPixels();
  const url = KRK.toCanvas(px).toDataURL('image/png');
  let tex = Texture.all.find(t => t.name === 'kraken.png');
  if (!tex) tex = new Texture({name: 'kraken.png'}).fromDataURL(url).add(false);
  else tex.fromDataURL(url);
  for (const cube of Cube.all) for (const f in cube.faces) if (cube.faces[f].texture !== null) cube.faces[f].texture = tex.uuid;
  Canvas.updateAll();
  KRK.png = url;
  return {overlaps, free, fit: KRK.fitInfo};
};

// ---- export (art/README.md recipe) ------------------------------------------------------------------------------
KRK.ASSETS = 'common/src/main/resources/assets/pirates_n_ships/';
KRK.ANIMS = ['idle', 'surface', 'grab', 'submerge'];
KRK.round = function r(v) {
  if (typeof v === 'number') return Math.round(v * 10000) / 10000 + 0;
  if (Array.isArray(v)) return v.map(r);
  if (v && typeof v === 'object') { const o = {}; for (const k in v) o[k] = r(v[k]); return o; }
  return v;
};
KRK.export = function () {
  KRK.check();
  const fs = require('fs'), root = KRK.REPO + '/';
  Animator.showDefaultPose();
  const geo = KRK.round(Codecs.bedrock.compile({raw: true}));
  fs.writeFileSync(root + KRK.ASSETS + 'geo/kraken.geo.json', autoStringify(geo));
  const anim = KRK.round(Animator.buildFile(null, KRK.ANIMS));
  fs.writeFileSync(root + KRK.ASSETS + 'animations/kraken.animation.json', autoStringify(anim));
  fs.writeFileSync(root + KRK.ASSETS + 'textures/entity/kraken.png', Buffer.from(KRK.png.split(',')[1], 'base64'));
  const proj = Codecs.project.compile({raw: true});
  for (const t of proj.textures) {
    t.path = ''; t.relative_path = '../../../' + KRK.ASSETS + 'textures/entity/kraken.png';
    t.width = KRK.W; t.height = KRK.H; // the image may still be loading right after KRK.paint
  }
  fs.writeFileSync(root + 'art/models/entity/kraken.bbmodel', JSON.stringify(proj));
  return {geoBones: geo['minecraft:geometry'][0].bones.length, anims: Object.keys(anim.animations)};
};

// ---- renders: art/renders/kraken.png -----------------------------------------------------------------------------
// views: [label, camera position (internal), look-at y, animation name or null, time, splay]. In game the code aims
// every tentacle_<i>_1 at its hit box (KrakenAim: the arm along the aim, its sucker face turned towards the body's
// axis); splay (degrees from straight up, outwards) poses each first segment the same way for the picture (only the
// preview meshes, nothing is keyed). 50 is the attack rest, 155 the lurking rest (hanging 25 deg out).
KRK.VIEWS = [['side (attack rest: arms 50 deg out)', [-270, 70, 0], 48, null, 0, 50],
  ['front', [0, 70, -270], 48, null, 0, 50], ['three-quarter, from a deck', [-175, 175, -195], 44, null, 0, 50],
  ['grab at 0.45 s', [-175, 175, -195], 44, 'grab', 0.45, 50], ['lurking (arms hang 25 deg out)', [-230, 30, -120], 30, null, 0, 155],
  ['from below: beak, suckers', [-110, -150, -165], 25, null, 0, 50]];
// the rotation KrakenAim gives tentacle i for the unit direction d (internal = GeckoLib's baked frame): the rest frame
// (up, inner normal towards the axis) onto (d, the inner direction made perpendicular to d)
KRK.aimQuat = function (i, d) {
  const a = KRK.ang(i), px = Math.sin(a) * KRK.RING, pz = -Math.cos(a) * KRK.RING, r = Math.hypot(px, pz);
  const u0 = new THREE.Vector3(0, 1, 0), n0 = new THREE.Vector3(-px / r, 0, -pz / r);
  const u = d.clone().normalize(), n = n0.clone().addScaledVector(u, -n0.dot(u));
  if (n.length() < 1e-4) n.set(0, 0, -1).addScaledVector(u, u.z);
  n.normalize();
  const b0 = new THREE.Vector3().crossVectors(u0, n0), b = new THREE.Vector3().crossVectors(u, n);
  const m0 = new THREE.Matrix4().makeBasis(u0, n0, b0), m = new THREE.Matrix4().makeBasis(u, n, b);
  return new THREE.Quaternion().setFromRotationMatrix(m.multiply(m0.transpose()));
};
KRK.splay = function (deg) {
  const t = deg * Math.PI / 180;
  for (let i = 0; i < KRK.N; i++) {
    const g = Group.all.find(x => x.name === 'tentacle_' + i + '_1'), a = KRK.ang(i);
    const d = new THREE.Vector3(Math.sin(a) * Math.sin(t), Math.cos(t), -Math.cos(a) * Math.sin(t));
    g.mesh.quaternion.premultiply(KRK.aimQuat(i, d));
    g.mesh.updateMatrixWorld(true);
  }
};
KRK.render = function (file, views) {
  KRK.check();
  const W = 480, H = 540, vs = views || KRK.VIEWS;
  const r = new THREE.WebGLRenderer({preserveDrawingBuffer: true, alpha: true, antialias: false});
  r.setSize(W, H); r.setClearColor(0x000000, 0);
  const out = document.createElement('canvas'); out.width = W * vs.length; out.height = H;
  const ctx = out.getContext('2d'); ctx.fillStyle = '#d7dde3'; ctx.fillRect(0, 0, out.width, out.height);
  const grid = typeof three_grid !== 'undefined' ? three_grid.visible : null;
  if (grid !== null) three_grid.visible = false;
  vs.forEach(([label, p, ly, anim, t, splay], i) => {
    Animator.showDefaultPose();
    if (anim) { const a = Animation.all.find(x => x.name === anim); a.select(); Timeline.setTime(t); Animator.preview(); }
    if (splay) KRK.splay(splay);
    const cam = new THREE.PerspectiveCamera(38, W / H, 1, 2000);
    cam.position.set(p[0], p[1], p[2]); cam.lookAt(new THREE.Vector3(0, ly, 0));
    r.render(scene, cam); ctx.drawImage(r.domElement, i * W, 0);
    ctx.fillStyle = '#333'; ctx.font = '16px sans-serif'; ctx.fillText(label, i * W + 10, 22);
  });
  Animator.showDefaultPose();
  if (grid !== null) three_grid.visible = grid;
  r.dispose();
  require('fs').writeFileSync(KRK.REPO + '/art/renders/' + (file || 'kraken.png'), Buffer.from(out.toDataURL('image/png').split(',')[1], 'base64'));
  return file || 'kraken.png';
};
