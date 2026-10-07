// Keyframe table and builder for shark.bbmodel (M4-art). Run in Blockbench (risky_eval) with the project open, after
// shark_model.js:
//   eval(require('fs').readFileSync('<repo>/art/models/entity/shark_animations.js', 'utf8'))
// Values are in file convention (what the exported .animation.json says, degrees and pixels): positive x on `jaw`
// drops its front (opens the mouth); y on the body and tail sways them sideways (symmetric, so the sign only sets the
// phase); z on the fins rolls them (fin_left and fin_right get opposite signs, so both tips
// rise together). Blockbench stores keyframes negated (rotation [-x, -y, z], position [-x, y, z]); SHA.toInternal
// does that. `head` and `root` rotation are never keyed: SharkModel turns them in code (SharkRigTest checks).
// Waves: SHA.wave keys a sine at its quarter points (zero crossings and peaks) with GeckoLib easings that make the
// curve a true sine: into a peak easeOutSine, into a zero crossing easeInSine.
window.SHA = window.SHA || {};
SHA.bone = n => Group.all.find(g => g.name === n);
SHA.toInternal = (ch, v) => ch === 'rotation' ? [-v[0], -v[1], v[2]] : ch === 'position' ? [-v[0], v[1], v[2]] : v.slice();
// amp: [x, y, z] peak; lag: quarters behind (0..3); returns {time: [vector, easing]}
SHA.wave = function (length, amp, lag) {
  const out = {};
  const seq = [0, 1, 0, -1];
  for (let k = 0; k <= 4; k++) {
    const s = seq[((k - lag) % 4 + 4) % 4];
    out[+(length * k / 4).toFixed(4)] = [amp.map(a => a * s), s === 0 ? 'easeInSine' : 'easeOutSine'];
  }
  return out;
};
SHA.make = function (name, length, loop, keys) {
  const old = Animation.all.find(a => a.name === name); if (old) old.remove(false);
  const a = new Animation({name, length, loop, snapping: 100}).add(false);
  for (const bn in keys) for (const ch in keys[bn]) for (const t in keys[bn][ch]) {
    const [vec, easing] = keys[bn][ch][t];
    const v = SHA.toInternal(ch, vec.map(x => Math.round(x * 1000) / 1000 + 0));
    const kf = a.getBoneAnimator(SHA.bone(bn)).addKeyframe({channel: ch, time: +t, interpolation: 'linear',
      data_points: [{x: v[0], y: v[1], z: v[2]}]});
    kf.easing = easing;
  }
  return a;
};
SHA.ANIMS = {
  // 1 s: the tail beats in an S-curve (tail_2 a quarter behind tail_1), the body yaws against it, the pectoral fins
  // roll, the dorsal fin trails the body. Played faster with speed by the Shark's body controller.
  swim: [1.0, 'loop', {
    body: {rotation: SHA.wave(1.0, [0, -3, 0], 0)},
    tail_1: {rotation: SHA.wave(1.0, [0, 13, 0], 0)},
    tail_2: {rotation: SHA.wave(1.0, [0, 20, 0], 1)},
    fin_left: {rotation: SHA.wave(1.0, [0, 0, 6], 1)},
    fin_right: {rotation: SHA.wave(1.0, [0, 0, -6], 1)},
    fin_dorsal: {rotation: SHA.wave(1.0, [0, 0, 2.5], 1)}
  }],
  // 3 s: the same sway, slow and small; the jaw breathes.
  idle: [3.0, 'loop', {
    body: {rotation: SHA.wave(3.0, [0, -1.5, 0], 0)},
    tail_1: {rotation: SHA.wave(3.0, [0, 6, 0], 0)},
    tail_2: {rotation: SHA.wave(3.0, [0, 9, 0], 1)},
    fin_left: {rotation: SHA.wave(3.0, [0, 0, 3.5], 1)},
    fin_right: {rotation: SHA.wave(3.0, [0, 0, -3.5], 1)},
    jaw: {rotation: {0: [[0, 0, 0], 'easeInOutSine'], 1.5: [[4, 0, 0], 'easeInOutSine'], 3: [[0, 0, 0], 'easeInOutSine']}}
  }],
  // 0.5 s, once: the jaw opens to 35 degrees, holds, snaps shut; the head dips and thrusts forward, the body lunges.
  // Only positions on head and body (their rotations belong to the code and to swim/idle).
  bite: [0.5, 'once', {
    jaw: {rotation: {0: [[0, 0, 0], 'linear'], 0.18: [[35, 0, 0], 'easeOutSine'], 0.26: [[35, 0, 0], 'linear'],
      0.32: [[0, 0, 0], 'easeInQuad'], 0.5: [[0, 0, 0], 'linear']}},
    head: {position: {0: [[0, 0, 0], 'linear'], 0.18: [[0, -0.4, -0.3], 'easeOutSine'], 0.32: [[0, -0.6, -0.8], 'easeInQuad'],
      0.5: [[0, 0, 0], 'easeInOutSine']}},
    body: {position: {0: [[0, 0, 0], 'linear'], 0.32: [[0, 0, -1.5], 'easeInQuad'], 0.5: [[0, 0, 0], 'easeInOutSine']}}
  }]
};
SHA.build = function () {
  for (const name of ['swim', 'idle', 'bite']) SHA.make(name, SHA.ANIMS[name][0], SHA.ANIMS[name][1], SHA.ANIMS[name][2]);
  return Animation.all.map(a => a.name);
};
