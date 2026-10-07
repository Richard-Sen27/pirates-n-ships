// Keyframe builder for kraken.bbmodel (K1b). Run in Blockbench (risky_eval) with the kraken project selected, after
// kraken_model.js (KRK.build, KRK.paint):
//   eval(require('fs').readFileSync(KRK.REPO + '/art/models/entity/kraken_animations.js', 'utf8')); KRKA.build();
// Never keys root or any tentacle_<i>_1: KrakenModel aims and stretches those in code (KrakenRigTest checks).
// The fins are cubes of `mantle` (the rig has no fin bones), so they spread, fold and ripple through the mantle's
// x scale; the mantle breathes through its y scale.
// Tentacle curls: segment _2/_3 of tentacle i turns about the horizontal axis tangent to the ring, so its tip moves
// towards the body's axis (the sucker side; positive "curl") or away from it; "side" turns it about the radial axis
// (sway along the ring). The rotation is composed as a quaternion in Blockbench's internal space and stored as the
// internal ZYX Euler angles of the keyframe; the export writes the file values (x and y negated, as the codec does).
// Smooth curves are sampled every `step` seconds with linear keys (a sum of waves has no exact GeckoLib easing).
window.KRKA = window.KRKA || {};
KRKA.bone = n => Group.all.find(g => g.name === n);
KRKA.D = Math.PI / 180;
// internal Euler (degrees) of tentacle i bent by curl and side (degrees)
KRKA.bend = function (i, curl, side) {
  const a = KRK.ang(i);
  const tangent = new THREE.Vector3(Math.cos(a), 0, Math.sin(a));
  const radial = new THREE.Vector3(Math.sin(a), 0, -Math.cos(a));
  const q = new THREE.Quaternion().setFromAxisAngle(tangent, curl * KRKA.D)
    .multiply(new THREE.Quaternion().setFromAxisAngle(radial, side * KRKA.D));
  const e = new THREE.Euler().setFromQuaternion(q, 'ZYX');
  return [e.x / KRKA.D, e.y / KRKA.D, e.z / KRKA.D];
};
KRKA.r3 = v => v.map(x => Math.round(x * 1000) / 1000 + 0);
// keys: {bone: {channel: [[time, internal vector, easing], ...]}}
KRKA.make = function (name, length, loop, keys) {
  const old = Animation.all.find(a => a.name === name); if (old) old.remove(false);
  const anim = new Animation({name, length, loop, snapping: 100}).add(false);
  for (const bn in keys) for (const ch in keys[bn]) for (const [t, vec, easing] of keys[bn][ch]) {
    const v = KRKA.r3(vec);
    const kf = anim.getBoneAnimator(KRKA.bone(bn)).addKeyframe({channel: ch, time: Math.round(t * 10000) / 10000,
      interpolation: 'linear', data_points: [{x: v[0], y: v[1], z: v[2]}]});
    kf.easing = easing;
  }
  return anim;
};
KRKA.sample = function (length, step, f) {
  const out = [];
  const n = Math.round(length / step);
  for (let k = 0; k <= n; k++) out.push([k * step, f(k * step), 'linear']);
  return out;
};
KRKA.TAU = Math.PI * 2;
KRKA.idle = function () {
  const L = 4, T = KRKA.TAU, keys = {};
  // breathing (y, one cycle) and the fins rippling (x, two cycles)
  keys.mantle = {scale: KRKA.sample(L, 0.5, t => {
    const b = (1 - Math.cos(T * t / L)) / 2, r = Math.sin(2 * T * t / L);
    return [1 + 0.02 * b + 0.03 * r, 1 + 0.035 * b, 1 + 0.02 * b + 0.006 * r];
  })};
  // a slow blink at 3 s
  const blink = [[0, [1, 1, 1], 'linear'], [3, [1, 1, 1], 'linear'], [3.12, [1, 0.12, 1], 'easeInQuad'], [3.3, [1, 1, 1], 'easeOutQuad'], [4, [1, 1, 1], 'linear']];
  keys.eye_left = {scale: blink};
  keys.eye_right = {scale: blink};
  // tentacles sway in a slow ellipse, neighbours a quarter apart, the tip segment a quarter behind its middle
  for (let i = 0; i < KRK.N; i++) {
    const ph = (i % 4) / 4;
    for (const [seg, curl, side, lag] of [[2, 8, 5, 0], [3, 12, 7, 0.25]]) {
      keys['tentacle_' + i + '_' + seg] = {rotation: KRKA.sample(L, 0.5, t => {
        const w = T * (t / L - ph - lag);
        return KRKA.bend(i, curl * Math.sin(w), side * Math.cos(w));
      })};
    }
  }
  return keys;
};
// surface, 2 s: the mantle rises out of the arm crown with folded fins that spread, the eyes open, the arms unfurl
KRKA.surface = function () {
  const keys = {
    mantle: {
      position: [[0, [0, -3, 0], 'linear'], [1.2, [0, 0.8, 0], 'easeOutSine'], [2, [0, 0, 0], 'easeInOutSine']],
      scale: [[0, [0.84, 0.9, 0.86], 'linear'], [1.0, [1.08, 1.04, 1.04], 'easeOutSine'], [2, [1, 1, 1], 'easeInOutSine']]
    },
    eye_left: {scale: [[0, [1, 0.12, 1], 'linear'], [0.8, [1, 0.12, 1], 'linear'], [1.1, [1, 1, 1], 'easeOutQuad']]},
    eye_right: {scale: [[0, [1, 0.12, 1], 'linear'], [0.8, [1, 0.12, 1], 'linear'], [1.1, [1, 1, 1], 'easeOutQuad']]}
  };
  for (let i = 0; i < KRK.N; i++) {
    keys['tentacle_' + i + '_2'] = {rotation: [[0, KRKA.bend(i, 45, 0), 'linear'], [1.6, KRKA.bend(i, 0, 0), 'easeOutSine'], [2, KRKA.bend(i, 0, 0), 'linear']]};
    keys['tentacle_' + i + '_3'] = {rotation: [[0, KRKA.bend(i, 70, 0), 'linear'], [1.8, KRKA.bend(i, 0, 0), 'easeOutSine'], [2, KRKA.bend(i, 0, 0), 'linear']]};
  }
  return keys;
};
// grab, 1 s: every arm's middle and tip curl in hard (the sucker side closes) and let go
KRKA.grab = function () {
  const keys = {};
  for (let i = 0; i < KRK.N; i++) {
    for (const [seg, deg] of [[2, 38], [3, 62]]) {
      keys['tentacle_' + i + '_' + seg] = {rotation: [[0, KRKA.bend(i, 0, 0), 'linear'], [0.35, KRKA.bend(i, deg, 0), 'easeOutQuad'],
        [0.55, KRKA.bend(i, deg, 0), 'linear'], [1, KRKA.bend(i, 0, 0), 'easeInOutSine']]};
    }
  }
  return keys;
};
// submerge, 2 s, held: the fins fold, the mantle contracts and sinks, the eyes close, the arms draw in
KRKA.submerge = function () {
  const keys = {
    mantle: {
      position: [[0, [0, 0, 0], 'linear'], [2, [0, -3, 0], 'easeInOutSine']],
      scale: [[0, [1, 1, 1], 'linear'], [1.2, [0.84, 0.88, 0.9], 'easeInOutSine'], [2, [0.82, 0.86, 0.88], 'easeOutSine']]
    },
    eye_left: {scale: [[0, [1, 1, 1], 'linear'], [0.6, [1, 0.12, 1], 'easeInQuad'], [2, [1, 0.12, 1], 'linear']]},
    eye_right: {scale: [[0, [1, 1, 1], 'linear'], [0.6, [1, 0.12, 1], 'easeInQuad'], [2, [1, 0.12, 1], 'linear']]}
  };
  for (let i = 0; i < KRK.N; i++) {
    keys['tentacle_' + i + '_2'] = {rotation: [[0, KRKA.bend(i, 0, 0), 'linear'], [2, KRKA.bend(i, 20, 0), 'easeInOutSine']]};
    keys['tentacle_' + i + '_3'] = {rotation: [[0, KRKA.bend(i, 0, 0), 'linear'], [2, KRKA.bend(i, 32, 0), 'easeInOutSine']]};
  }
  return keys;
};
KRKA.build = function () {
  KRK.check();
  KRKA.make('idle', 4, 'loop', KRKA.idle());
  KRKA.make('surface', 2, 'once', KRKA.surface());
  KRKA.make('grab', 1, 'once', KRKA.grab());
  KRKA.make('submerge', 2, 'hold', KRKA.submerge());
  return Animation.all.map(a => a.name + ':' + a.loop + ':' + a.length);
};
