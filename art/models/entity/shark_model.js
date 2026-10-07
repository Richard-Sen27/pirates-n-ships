// Model and skin builder for shark.bbmodel (M4-art). Run in Blockbench (risky_eval) in a GeckoLib Animated Model
// project of 64x32 (see art/README.md, "Shark (M4-art)"), then shark_animations.js, then the export:
//   window.SH = {REPO: '<repo>'}; eval(require('fs').readFileSync(SH.REPO + '/art/models/entity/shark_model.js', 'utf8'));
//   SH.build(); SH.paint(); (shark_animations.js: SHA.build()); SH.export();
// Check that `Project` is the shark project first: risky_eval acts on whichever tab is selected.
// Coordinates here are Blockbench's internal ones: the shark faces -z, internal x = -(file x), so the shark's left
// (file +x, bone fin_left) is at internal -x. The model is symmetric, so only the fins care. Cube rotations are
// Blockbench's (degrees); only the pectoral fins turn (y +25 sweeps the left fin's tip back, z +15 lowers it).
// UV: every face gets its own per-face rectangle on the 64x32 sheet, packed by SH.pack at one texel scale for the
// whole model; mirror faces (east/west of a centred cube, the right fins) share the rectangle of their twin with u
// reversed. Small solid parts (teeth, eyes, gill slits) map onto 2x2 patches in the bottom-right corner (v 30..31).
// The skin is painted from the 3D position of each texel centre (read back from the cube meshes in the rest pose),
// with a hash noise of the texel position: the same model gives the same pixels (byte-identical PNG).
window.SH = window.SH || {};
SH.REPO = SH.REPO || '';
SH.W = 64; SH.H = 32;
SH.BONES = [
  ['root', null, [0, 5, 0]],
  ['body', 'root', [0, 5, 0]],
  ['head', 'body', [0, 5, -8]],
  ['jaw', 'head', [0, 3, -9]],
  ['tail_1', 'body', [0, 5, 6]],
  ['tail_2', 'tail_1', [0, 5, 14]],
  ['fin_left', 'body', [-5, 2, -4]],
  ['fin_right', 'body', [5, 2, -4]],
  ['fin_dorsal', 'body', [0, 10, -2]]];
// [bone, name, from, to, opts]; opts: tag (paint style: skin, jaw, mouth, fin_p, fin_d, fin_c; patches tooth, eye, gill),
// rot + origin, hide (faces left out), mirrorOf (a cube mirrored in x: shares its twin's UV).
SH.PARTS = (function () {
  const P = [];
  const add = (bone, name, from, to, o) => P.push([bone, name, from, to, Object.assign({tag: 'skin'}, o || {})]);
  // body: three sections, each a wide box plus a narrower, taller core (flat belly, rounded back)
  add('body', 'body_front', [-5, 1, -8], [5, 8.6, -2]);
  add('body', 'body_front_core', [-4, 0.4, -7.95], [4, 9.8, -2.05]);
  add('body', 'body_mid', [-4.6, 1.3, -2], [4.6, 8.7, 3]);
  add('body', 'body_mid_core', [-3.6, 0.8, -1.95], [3.6, 10, 2.95]);
  add('body', 'body_rear', [-3.8, 1.9, 3], [3.8, 8.4, 6]);
  add('body', 'body_rear_core', [-2.9, 1.5, 3.05], [2.9, 9.3, 5.95]);
  // five gill slits per side, thin pale strips 0.06 px proud of the gill section
  [[-7.6, 3.8, 7.2], [-6.8, 3.6, 7.5], [-6.0, 3.5, 7.5], [-5.2, 3.6, 7.3], [-4.4, 3.8, 6.9]].forEach(([z, y0, y1], i) => {
    add('body', 'gill_left_' + i, [-5.06, y0, z], [-4.9, y1, z + 0.3], {tag: 'gill', hide: ['east']});
    add('body', 'gill_right_' + i, [4.9, y0, z], [5.06, y1, z + 0.3], {tag: 'gill', hide: ['west']});
  });
  // head: skull, snout in three steps, the throat behind the jaw hinge, the palate (pink mouth line)
  add('head', 'head_base', [-4.2, 3, -13], [4.2, 9.2, -8], {hide: ['south']});
  add('head', 'head_top', [-3.3, 3.2, -12.95], [3.3, 9.6, -8.05], {hide: ['south', 'down']});
  add('head', 'throat', [-3.6, 1, -9], [3.6, 3, -8], {hide: ['south', 'up']});
  add('head', 'snout', [-3.2, 3.8, -17], [3.2, 8.3, -13], {hide: ['south']});
  add('head', 'snout_top', [-2.4, 4.2, -16.95], [2.4, 8.8, -13.05], {hide: ['south', 'down']});
  add('head', 'snout_tip', [-2.1, 4.4, -18.6], [2.1, 7.4, -17], {hide: ['south']});
  add('head', 'palate', [-2.8, 3, -16.6], [2.8, 3.8, -13], {tag: 'mouth', hide: ['south', 'up']});
  add('head', 'eye_left', [-3.4, 6.2, -15], [-3.15, 7.1, -14], {tag: 'eye'});
  add('head', 'eye_right', [3.15, 6.2, -15], [3.4, 7.1, -14], {tag: 'eye'});
  for (const z of [-16.2, -15.4, -14.6, -13.8]) {
    add('head', 'tooth_up_l' + P.length, [-3.15, 3.15, z], [-2.85, 3.8, z + 0.45], {tag: 'tooth', hide: ['up']});
    add('head', 'tooth_up_r' + P.length, [2.85, 3.15, z], [3.15, 3.8, z + 0.45], {tag: 'tooth', hide: ['up']});
  }
  for (const x of [-2, -1, 0, 1, 2]) {
    add('head', 'tooth_up_f' + P.length, [x - 0.22, 3.15, -16.95], [x + 0.22, 3.8, -16.6], {tag: 'tooth', hide: ['up']});
  }
  // jaw: lower jaw hinged at its back end, pink floor, teeth that hide inside the palate when the mouth is shut
  add('jaw', 'jaw_main', [-3.2, 1.2, -15.6], [3.2, 3, -9], {tag: 'jaw'});
  add('jaw', 'jaw_front', [-2.4, 1.6, -16.6], [2.4, 3, -15.6], {tag: 'jaw', hide: ['south']});
  for (const z of [-15.2, -14.3, -13.4, -12.5]) {
    add('jaw', 'tooth_low_l' + P.length, [-2.5, 3, z], [-2.2, 3.55, z + 0.4], {tag: 'tooth', hide: ['down']});
    add('jaw', 'tooth_low_r' + P.length, [2.2, 3, z], [2.5, 3.55, z + 0.4], {tag: 'tooth', hide: ['down']});
  }
  for (const x of [-1.5, -0.5, 0.5, 1.5]) {
    add('jaw', 'tooth_low_f' + P.length, [x - 0.2, 3, -16.5], [x + 0.2, 3.55, -16.15], {tag: 'tooth', hide: ['down']});
  }
  // pectoral fins: swept back (y) and drooping (z) about the fin pivot
  const pec = {tag: 'fin_p', rot: [0, 25, 15], origin: [-5, 2, -4]};
  add('fin_left', 'pec_left_base', [-9.5, 1.6, -6.6], [-4.5, 2.4, -2.2], pec);
  add('fin_left', 'pec_left_tip', [-13.5, 1.7, -5.2], [-9.5, 2.3, -2.4], pec);
  add('fin_right', 'pec_right_base', [4.5, 1.6, -6.6], [9.5, 2.4, -2.2], {tag: 'fin_p', rot: [0, -25, -15], origin: [5, 2, -4], mirrorOf: 'pec_left_base'});
  add('fin_right', 'pec_right_tip', [9.5, 1.7, -5.2], [13.5, 2.3, -2.4], {tag: 'fin_p', rot: [0, -25, -15], origin: [5, 2, -4], mirrorOf: 'pec_left_tip'});
  // first dorsal fin: stepped layers, each shorter and further back (leading edge swept, trailing edge upright)
  const fd = (n, y0, y1, z0, z1, w) => add('fin_dorsal', n, [-w, y0, z0], [w, y1, z1], {tag: 'fin_d', hide: ['down']});
  fd('dorsal_base', 9.6, 11.5, -5.2, 0.8, 0.6);
  fd('dorsal_1', 11.5, 13, -4.4, 0.6, 0.5);
  fd('dorsal_2', 13, 14.5, -3.4, 0.6, 0.45);
  fd('dorsal_3', 14.5, 15.8, -2.2, 0.8, 0.4);
  fd('dorsal_tip', 15.8, 16.8, -0.9, 1.2, 0.35);
  // tail_1: the tapering rear, second dorsal and anal fin
  add('tail_1', 'tail1_front', [-2.9, 2.1, 6], [2.9, 8.3, 10], {hide: ['north']});
  add('tail_1', 'tail1_rear', [-2.1, 2.8, 10], [2.1, 7.6, 14]);
  add('tail_1', 'dorsal2', [-0.35, 7.4, 11], [0.35, 8.4, 13], {tag: 'fin_d', hide: ['down']});
  add('tail_1', 'dorsal2_tip', [-0.3, 8.4, 11.8], [0.3, 9.2, 13.4], {tag: 'fin_d', hide: ['down']});
  add('tail_1', 'anal', [-0.35, 2, 11.5], [0.35, 2.9, 13.2], {tag: 'fin_d', hide: ['up']});
  add('tail_1', 'anal_tip', [-0.3, 1.3, 12.3], [0.3, 2, 13.6], {tag: 'fin_d', hide: ['up']});
  // tail_2: the caudal peduncle with its keel and the asymmetric tail fin (upper lobe longer, both swept back)
  add('tail_2', 'peduncle', [-1.5, 3.4, 14], [1.5, 6.6, 17.5], {hide: ['north']});
  add('tail_2', 'keel', [-1, 3.9, 17.5], [1, 6.1, 19]);
  const fc = (n, y0, y1, z0, z1, w, hide) => add('tail_2', n, [-w, y0, z0], [w, y1, z1], {tag: 'fin_c', hide});
  fc('lobe_up_1', 6.1, 8, 16.8, 19.6, 0.45, ['down']);
  fc('lobe_up_2', 8, 10, 17.8, 20.4, 0.42, ['down']);
  fc('lobe_up_3', 10, 12, 18.8, 21.2, 0.38, ['down']);
  fc('lobe_up_4', 12, 13.6, 19.8, 21.8, 0.34, ['down']);
  fc('lobe_low_1', 2, 3.9, 17, 19.4, 0.42, ['up']);
  fc('lobe_low_2', 0.6, 2, 18, 19.8, 0.36, ['up']);
  return P;
})();
SH.FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
SH.PATCHES = {tooth: [56, 30], eye: [58, 30], gill: [60, 30]};
SH.dims = (from, to, f) => {
  const d = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
  return f === 'north' || f === 'south' ? [d[0], d[1]] : f === 'east' || f === 'west' ? [d[2], d[1]] : [d[0], d[2]];
};
// Faces that own a rectangle (primaries) and faces that reuse one mirrored (east of a centred cube = west reversed;
// every face of a mirrorOf cube = its twin's mirror face reversed).
SH.layout = function () {
  const prim = [], refs = [];
  for (const [bone, name, from, to, o] of SH.PARTS) {
    if (SH.PATCHES[o.tag]) continue;
    const hide = o.hide || [];
    const centred = Math.abs(from[0] + to[0]) < 1e-6;
    for (const f of SH.FACES) {
      if (hide.includes(f)) continue;
      if (o.mirrorOf) { refs.push([name, f, o.mirrorOf, f === 'east' ? 'west' : f === 'west' ? 'east' : f]); continue; }
      if (centred && f === 'east' && !hide.includes('west')) { refs.push([name, f, name, 'west']); continue; }
      prim.push([name, f, SH.dims(from, to, f)]);
    }
  }
  return {prim, refs};
};
// Shelf packer at scale s (texels per pixel); the bottom-right 8x2 corner is reserved for the patches (6x2 used).
SH.pack = function (s) {
  const {prim, refs} = SH.layout();
  const items = prim.map(([n, f, d]) => ({n, f, w: Math.max(1, Math.round(d[0] * s)), h: Math.max(1, Math.round(d[1] * s))}));
  items.sort((a, b) => b.h - a.h || b.w - a.w || (a.n + a.f < b.n + b.f ? -1 : 1));
  let x = 0, y = 0, row = 0;
  const rects = {};
  for (const it of items) {
    if (x + it.w > SH.W) { x = 0; y += row; row = 0; }
    const limitW = (y + it.h > SH.H - 2) ? SH.W - 8 : SH.W;
    if (x + it.w > limitW) { x = 0; y += row; row = 0; }
    if (y + it.h > SH.H || (y + it.h > SH.H - 2 && x + it.w > SH.W - 8)) return null;
    rects[it.n + '.' + it.f] = [x, y, x + it.w, y + it.h];
    x += it.w; row = Math.max(row, it.h);
  }
  for (const [n, f, tn, tf] of refs) {
    const r = rects[tn + '.' + tf];
    rects[n + '.' + f] = [r[2], r[1], r[0], r[3]];
  }
  return rects;
};
SH.fit = function () {
  for (let s = 1; s > 0.3; s = Math.round((s - 0.05) * 100) / 100) { const r = SH.pack(s); if (r) return {s, rects: r}; }
  throw new Error('no fit');
};
SH.build = function () {
  const {s, rects} = SH.fit();
  SH.scale = s; SH.rects = rects;
  Undo.initEdit({outliner: true, elements: [], selection: true});
  for (const e of [...Outliner.elements]) e.remove();
  for (const g of [...Group.all]) g.remove(false);
  const G = {};
  for (const [n, p, o] of SH.BONES) G[n] = new Group({name: n, origin: o.slice()}).addTo(p ? G[p] : undefined).init();
  const made = [];
  for (const [bone, name, from, to, o] of SH.PARTS) {
    const faces = {};
    for (const f of SH.FACES) {
      if ((o.hide || []).includes(f)) { faces[f] = {uv: [0, 0, 0, 0], texture: null}; continue; }
      if (SH.PATCHES[o.tag]) { const [u, v] = SH.PATCHES[o.tag]; faces[f] = {uv: [u + 0.5, v + 0.5, u + 1.5, v + 1.5]}; continue; }
      faces[f] = {uv: rects[name + '.' + f].slice()};
    }
    const c = new Cube({name, from, to, box_uv: false, rotation: o.rot || [0, 0, 0],
      origin: o.origin || [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2], faces}).addTo(G[bone]).init();
    c.sh_tag = o.tag;
    made.push(c);
  }
  Undo.finishEdit('SH build', {outliner: true, elements: made});
  Canvas.updateAll();
  return {cubes: Cube.all.length, groups: Group.all.length, scale: s};
};

// ---- skin -------------------------------------------------------------------------------------------------------
SH.C = {
  back_d: [66, 80, 96], back: [92, 108, 124], back_l: [114, 130, 146], speck: [74, 88, 104],
  belly: [234, 237, 238], belly_d: [210, 216, 220], fin_tip: [40, 48, 58],
  gill: [190, 200, 208], gill_d: [70, 82, 96], eye: [10, 10, 12], tooth: [248, 246, 238],
  mouth: [206, 118, 128], mouth_d: [160, 78, 92], nostril: [52, 60, 70]
};
SH.hash = function (a, b, seed) {
  let h = (a * 374761393 + b * 668265263 + seed * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h = h ^ (h >>> 16);
  return (h >>> 0) / 4294967296;
};
SH.mix = (a, b, t) => [0, 1, 2].map(i => a[i] + (b[i] - a[i]) * t);
SH.smooth = (e0, e1, x) => { const t = Math.min(1, Math.max(0, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };
// colour of a texel at internal position p with outward normal n, texel size q (px along the face's u and v axes)
SH.colour = function (tag, p, n, q, u, v) {
  const C = SH.C, ax = Math.abs(p[0]), y = p[1], z = p[2];
  const r = SH.hash(u, v, 7), r2 = SH.hash(u, v, 11);
  const side = (bellyLine) => {
    const t = SH.smooth(bellyLine - 1.1, bellyLine + 1.1, y);
    const backTone = SH.mix(C.back_l, C.back, SH.smooth(bellyLine + 1, bellyLine + 4, y));
    let c = SH.mix(C.belly, backTone, t);
    if (t > 0.6 && r < 0.1) c = SH.mix(c, C.speck, 0.45);
    return c;
  };
  const skin = () => {
    const line = z > 9 ? 4.5 : 4.2;
    if (n[1] > 0.5) { let c = SH.mix(C.back, C.back_d, SH.smooth(2, 0.5, ax)); if (r < 0.12) c = SH.mix(c, C.speck, 0.45); return c; }
    if (n[1] < -0.5) {
      if (y < 3.1 && y > 2.9 && ax < 3.25 && z < -9) return z > -12 ? C.mouth_d : C.mouth;
      if (y > 4.3 && y < 4.5 && z < -17.5 && z > -18.3 && ax > 0.8 && ax < 1.7) return C.nostril;
      return r < 0.2 ? C.belly_d : C.belly;
    }
    return side(line);
  };
  switch (tag) {
    case 'skin': return skin();
    case 'jaw': return n[1] > 0.5 ? (z > -12 ? C.mouth_d : C.mouth) : skin();
    case 'mouth': return z > -14.5 ? C.mouth_d : C.mouth;
    case 'fin_p': {
      const reach = SH.smooth(8.5, 12, ax);
      let c = n[1] > 0.3 ? SH.mix(C.back, C.back_d, 0.25) : n[1] < -0.3 ? C.belly_d : C.back_d;
      if (n[1] > 0.3 && r < 0.1) c = SH.mix(c, C.speck, 0.6);
      return SH.mix(c, C.fin_tip, reach * 0.85);
    }
    case 'fin_d': {
      const below = y < 4;
      let c = Math.abs(n[0]) > 0.5 ? (below ? SH.mix(C.belly_d, C.back, 0.5) : C.back) : C.back_d;
      if (Math.abs(n[0]) > 0.5 && r < 0.1) c = SH.mix(c, C.speck, 0.6);
      const tip = below ? SH.smooth(2.3, 1.2, y) : Math.max(SH.smooth(14, 16.5, y), SH.smooth(8.5, 9.5, y) * (z > 10 ? 1 : 0));
      return SH.mix(c, C.fin_tip, tip * 0.85);
    }
    case 'fin_c': {
      let c = Math.abs(n[0]) > 0.5 ? C.back : C.back_d;
      if (Math.abs(n[0]) > 0.5 && r < 0.1) c = SH.mix(c, C.speck, 0.6);
      const tip = Math.max(SH.smooth(9.5, 12, y), SH.smooth(2.5, 1.2, y), SH.smooth(20.5, 22.5, z) * 0.7);
      return SH.mix(c, C.fin_tip, tip * 0.85);
    }
  }
  return [255, 0, 255];
};
SH.FACE_ORDER = ['east', 'west', 'up', 'down', 'south', 'north'];
// For each face: its 4 corners as {p (internal model space), uv (sheet px)}, read from the cube's mesh in rest pose
// (Blockbench's cube geometry holds 4 vertices per face in FACE_ORDER).
SH.corners = function (cube) {
  const g = cube.mesh.geometry, pos = g.attributes.position, uv = g.attributes.uv;
  cube.mesh.updateMatrixWorld(true);
  const inv = new THREE.Matrix4().copy(Project.model_3d.matrixWorld).invert();
  const out = {};
  SH.FACE_ORDER.forEach((f, k) => {
    const list = [];
    for (let i = 4 * k; i < 4 * k + 4; i++) {
      const p = new THREE.Vector3(pos.getX(i), pos.getY(i), pos.getZ(i));
      cube.mesh.localToWorld(p); p.applyMatrix4(inv);
      list.push({p: [p.x, p.y, p.z], uv: [uv.getX(i) * SH.W, (1 - uv.getY(i)) * SH.H]});
    }
    out[f] = list;
  });
  return out;
};
SH.paintPixels = function () {
  Animator.showDefaultPose && Animator.showDefaultPose();
  const px = new Uint8ClampedArray(SH.W * SH.H * 4);
  const owner = new Array(SH.W * SH.H).fill(null);
  let conflicts = 0;
  const C = SH.C;
  const putPatch = (u, v, c) => { for (let j = 0; j < 2; j++) for (let i = 0; i < 2; i++) { const k = ((v + j) * SH.W + u + i) * 4; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = 255; } };
  putPatch(...SH.PATCHES.tooth, C.tooth); putPatch(...SH.PATCHES.eye, C.eye); putPatch(...SH.PATCHES.gill, C.gill);
  for (const cube of Cube.all) {
    const tag = cube.sh_tag || SH.PARTS.find(p => p[1] === cube.name)[4].tag;
    if (SH.PATCHES[tag]) continue;
    const cs = SH.corners(cube);
    for (const f of SH.FACES) {
      if (cube.faces[f].texture === null || !cs[f]) continue;
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
      const all = SH.FACES.map(ff => cs[ff]).flat();
      const cc = all.reduce((a, c) => [a[0] + c.p[0] / all.length, a[1] + c.p[1] / all.length, a[2] + c.p[2] / all.length], [0, 0, 0]);
      const out = (ctr[0] - cc[0]) * n[0] + (ctr[1] - cc[1]) * n[1] + (ctr[2] - cc[2]) * n[2];
      const nw = out < 0 ? n.map(a => -a) : n;
      const ru = Math.round(u1 - u0), rv = Math.round(v1 - v0);
      const q = [Math.hypot(...e1) / ru, Math.hypot(...e2) / rv];
      for (let j = 0; j < rv; j++) for (let i = 0; i < ru; i++) {
        const s = (i + 0.5) / ru, t = (j + 0.5) / rv;
        const p = [0, 1, 2].map(k => A[k] * (1 - s) * (1 - t) + B[k] * s * (1 - t) + Cc[k] * (1 - s) * t + D[k] * s * t);
        const U = Math.round(u0) + i, V = Math.round(v0) + j;
        const col = SH.colour(tag, p, nw, q, U, V).map(Math.round);
        const k = (V * SH.W + U) * 4;
        if (owner[V * SH.W + U] !== null) {
          if (px[k] !== col[0] || px[k + 1] !== col[1] || px[k + 2] !== col[2]) conflicts++;
          continue;
        }
        owner[V * SH.W + U] = cube.name + '.' + f;
        px[k] = col[0]; px[k + 1] = col[1]; px[k + 2] = col[2]; px[k + 3] = 255;
      }
    }
  }
  return {px, conflicts};
};
SH.toCanvas = function (px) {
  const cv = document.createElement('canvas'); cv.width = SH.W; cv.height = SH.H;
  const ctx = cv.getContext('2d'); const img = ctx.createImageData(SH.W, SH.H); img.data.set(px); ctx.putImageData(img, 0, 0);
  return cv;
};
SH.paint = function () {
  const {px, conflicts} = SH.paintPixels();
  const url = SH.toCanvas(px).toDataURL('image/png');
  let tex = Texture.all.find(t => t.name === 'shark.png');
  if (!tex) { tex = new Texture({name: 'shark.png'}).fromDataURL(url).add(false); }
  else tex.fromDataURL(url);
  for (const cube of Cube.all) for (const f in cube.faces) if (cube.faces[f].texture !== null) cube.faces[f].texture = tex.uuid;
  Canvas.updateAll();
  SH.png = url;
  return {conflicts, scale: SH.scale};
};

// ---- export (art/README.md recipe) ------------------------------------------------------------------------------
SH.ASSETS = 'common/src/main/resources/assets/pirates_n_ships/';
SH.round = function r(v) {
  if (typeof v === 'number') return Math.round(v * 10000) / 10000 + 0;
  if (Array.isArray(v)) return v.map(r);
  if (v && typeof v === 'object') { const o = {}; for (const k in v) o[k] = r(v[k]); return o; }
  return v;
};
SH.export = function () {
  const fs = require('fs'), root = SH.REPO + '/';
  Animator.showDefaultPose();
  const geo = SH.round(Codecs.bedrock.compile({raw: true}));
  fs.writeFileSync(root + SH.ASSETS + 'geo/shark.geo.json', autoStringify(geo));
  const anim = SH.round(Animator.buildFile(null, ['swim', 'idle', 'bite']));
  fs.writeFileSync(root + SH.ASSETS + 'animations/shark.animation.json', autoStringify(anim));
  fs.writeFileSync(root + SH.ASSETS + 'textures/entity/shark.png', Buffer.from(SH.png.split(',')[1], 'base64'));
  const proj = Codecs.project.compile({raw: true});
  for (const t of proj.textures) { t.path = ''; t.relative_path = '../../../' + SH.ASSETS + 'textures/entity/shark.png'; }
  fs.writeFileSync(root + 'art/models/entity/shark.bbmodel', JSON.stringify(proj));
  return {geoBones: geo['minecraft:geometry'][0].bones.length, anims: Object.keys(anim.animations)};
};
