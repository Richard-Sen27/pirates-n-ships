// Keyframe table and builder for crew_member.bbmodel (M2). Run in Blockbench (risky_eval) with the project open:
//   eval(require('fs').readFileSync('<repo>/art/models/entity/crew_member_animations.js', 'utf8'))
// Values are in file convention (what the exported .animation.json says, degrees and pixels): negative x swings an
// arm or leg forward, positive x leans `waist` forward, positive z lifts the right arm outwards and the left arm
// inwards, positive y swings a raised arm towards the mob's right. Blockbench stores keyframes negated (rotation
// [-x, -y, z], position [-x, y, z]); M2.toInternal does that, so never type file values into the keyframe panel.
// The head is never keyed: CrewMemberModel turns it with the look direction after the animations ran.
window.M2 = window.M2 || {};
M2.bone = n => Group.all.find(g => g.name === n);
M2.toInternal = (ch, v) => ch === 'rotation' ? [-v[0], -v[1], v[2]] : ch === 'position' ? [-v[0], v[1], v[2]] : v.slice();
M2.make = function (name, length, keys) {
  const old = Animation.all.find(a => a.name === name); if (old) old.remove(false);
  const a = new Animation({name, length, loop: 'loop', snapping: 100}).add(false);
  for (const bn in keys) for (const ch in keys[bn]) for (const t in keys[bn][ch]) {
    const v = M2.toInternal(ch, keys[bn][ch][t].map(x => Math.round(x * 1000) / 1000));
    const kf = a.getBoneAnimator(M2.bone(bn)).addKeyframe({channel: ch, time: +t, interpolation: 'linear',
      data_points: [{x: v[0], y: v[1], z: v[2]}]});
    kf.easing = 'easeInOutSine'; // GeckoLib keyframe easing (the GeckoLib plugin exports it as "easing")
  }
  return a;
};


M2.ANIMS = {
  // 4 s: breathing (chest swell), a slow weight shift from the waist, arms swaying slightly out of phase.
  idle: [4.0, {
    body: {scale: {0: [1, 1, 1], 2: [1.02, 1.012, 1.05], 4: [1, 1, 1]}},
    waist: {rotation: {0: [0, 0, -1.2], 2: [-1, 0, 1.2], 4: [0, 0, -1.2]}},
    right_arm: {rotation: {0: [1, 0, 3], 2: [-4, 0, 6], 4: [1, 0, 3]}},
    left_arm: {rotation: {0: [-1, 0, -3], 1: [-2, 0, -4], 3: [3, 0, -6], 4: [-1, 0, -3]}}
  }],
  // 1 s: legs ±32, arms ±28 in opposite phase, the upper body dips 0.6 px at full stride and twists ±3.
  walk: [1.0, {
    right_leg: {rotation: {0: [32, 0, 0], 0.5: [-32, 0, 0], 1: [32, 0, 0]}},
    left_leg: {rotation: {0: [-32, 0, 0], 0.5: [32, 0, 0], 1: [-32, 0, 0]}},
    right_arm: {rotation: {0: [-28, 0, 3], 0.5: [28, 0, 3], 1: [-28, 0, 3]}},
    left_arm: {rotation: {0: [28, 0, -3], 0.5: [-28, 0, -3], 1: [28, 0, -3]}},
    waist: {
      rotation: {0: [3, -3, 0], 0.5: [3, 3, 0], 1: [3, -3, 0]},
      position: {0: [0, -0.6, 0], 0.25: [0, 0, 0], 0.5: [0, -0.6, 0], 0.75: [0, 0, 0], 1: [0, -0.6, 0]}
    }
  }],
  // 2 s: hand over hand. Each arm reaches high and forward, pulls down to the belt (1.2 s, eased) and swings back up
  // (0.8 s); the left arm runs 1 s behind the right, so one hand always pulls. The hands meet in front of the chest
  // (y inwards). The waist leans 10° and dips to 20° in the middle of each pull; feet braced, right foot forward.
  work: [2.0, {
    right_arm: {rotation: {0: [-125, -12, 2], 0.6: [-92, -16, 2], 1.2: [-52, -10, 4], 2: [-125, -12, 2]}},
    left_arm: {rotation: {0: [-70, 13, -3], 0.2: [-52, 10, -4], 1: [-125, 12, -2], 1.6: [-92, 16, -2], 2: [-70, 13, -3]}},
    waist: {rotation: {0: [12, 0, 0], 0.6: [20, 2, 0], 1: [12, 0, 0], 1.6: [20, -2, 0], 2: [12, 0, 0]}},
    right_leg: {rotation: {0: [-16, 0, 3], 2: [-16, 0, 3]}},
    left_leg: {rotation: {0: [14, 0, -3], 2: [14, 0, -3]}}
  }],
  // 4 s: vanilla riding legs (-81 x, ±18 y), hands resting on the thighs, slow breathing.
  sit: [4.0, {
    right_leg: {rotation: {0: [-81, 18, 4], 4: [-81, 18, 4]}},
    left_leg: {rotation: {0: [-81, -18, -4], 4: [-81, -18, -4]}},
    right_arm: {rotation: {0: [-38, -10, 2], 2: [-40, -10, 3], 4: [-38, -10, 2]}},
    left_arm: {rotation: {0: [-38, 10, -2], 2: [-40, 10, -3], 4: [-38, 10, -2]}},
    waist: {rotation: {0: [-3, 0, 0], 2: [-4, 0, 0], 4: [-3, 0, 0]}},
    body: {scale: {0: [1, 1, 1], 2: [1.02, 1.01, 1.05], 4: [1, 1, 1]}}
  }],
  // ART1d, 4 s: lying on the back in a hammock (HammockSeat at the canvas top where the halves meet). The root drops
  // the hips 11 px onto the canvas and moves the body 4.5 px towards the feet so it is centred on the hammock; the upper
  // body is raised 17.5 and the straight legs 22.5 degrees (a shallow V fitted to the canvas profile), arms folded
  // over the belly, slow breathing. The head is not keyed: CrewMemberModel raises it 15 degrees while resting.
  sleep: [4.0, {
    root: {position: {0: [0, -11, -4.5], 4: [0, -11, -4.5]}},
    waist: {rotation: {0: [-72.5, 0, 0], 2: [-73.5, 0, 0], 4: [-72.5, 0, 0]}},
    body: {scale: {0: [1, 1, 1], 2: [1.02, 1.01, 1.05], 4: [1, 1, 1]}},
    right_arm: {rotation: {0: [-25, 0, -25], 2: [-27, 0, -25], 4: [-25, 0, -25]}},
    left_arm: {rotation: {0: [-25, 0, 25], 2: [-27, 0, 25], 4: [-25, 0, 25]}},
    right_leg: {rotation: {0: [-112.5, 0, 2], 4: [-112.5, 0, 2]}},
    left_leg: {rotation: {0: [-112.5, 0, -2], 4: [-112.5, 0, -2]}}
  }]
};

M2.buildAnimations = function () {
  for (const name of ['idle', 'walk', 'work', 'sit', 'sleep']) M2.make(name, M2.ANIMS[name][0], M2.ANIMS[name][1]);
  return Animation.all.map(a => a.name);
};
M2.buildAnimations();
