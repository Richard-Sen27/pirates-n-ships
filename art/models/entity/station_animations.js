// Crew station animations on the crew rig (ART7): the helmsman, the gun crew and the capstan. Run in Blockbench
// (risky_eval) with a crew-rig GeckoLib project open, after crew_member_animations.js (which defines M2):
//   eval(require('fs').readFileSync('<repo>/art/models/entity/station_animations.js', 'utf8'))
// Values are in file convention (what the exported .animation.json says, degrees and pixels; see
// crew_member_animations.js): negative x swings an arm or leg forward, positive x leans `waist` forward, positive z
// lifts the right arm outwards and the left arm inwards, negative y turns the right arm inwards (positive y the left);
// root position z negative moves the whole body forward. The head is never keyed (rig contract).
//
// Helm: the helmsman stands at the station spot on the helm's FACING side and faces the wheel (CrewMember turns him).
// In the model frame (feet at the origin, facing -z) the wheel's axle is 11.75 px ahead (block edge 8 px plus the
// axle's 3.75 px, HelmWheelRenderer) and 12.84 px up (13 px above the deck, minus the station seat's 0.16 px); the rim
// radius is 5.9 px. The arm values were solved by forward kinematics through GeckoLib's bone transforms (the same
// composition as CrewStationPoseTest) so the hand locators lie on the rim: helm_hold at 2 and 10 o'clock (0.2 px off),
// helm_turn_right hand over hand clockwise as the helmsman sees it (gripping keys within 1 px of the rim, the hands
// lifted about 2 px off it while they go back for a new grip), helm_turn_left the mirror image.
window.ST = window.ST || {};
ST.bone = n => Group.all.find(g => g.name === n);
ST.toInternal = (ch, v) => ch === 'rotation' ? [-v[0], -v[1], v[2]] : ch === 'position' ? [-v[0], v[1], v[2]] : v.slice();
// keys: [{t, pose: {bone: {channel: [x, y, z]}}}]; every pose of an animation keys the same channels
ST.make = function (name, length, loop, keys) {
  const old = Animation.all.find(a => a.name === name); if (old) old.remove(false);
  const a = new Animation({name, length, loop, snapping: 100}).add(false);
  for (const k of keys) for (const bn in k.pose) for (const ch in k.pose[bn]) {
    const v = ST.toInternal(ch, k.pose[bn][ch].map(x => Math.round(x * 100) / 100));
    const kf = a.getBoneAnimator(ST.bone(bn)).addKeyframe({channel: ch, time: k.t, interpolation: 'linear',
      data_points: [{x: v[0], y: v[1], z: v[2]}]});
    kf.easing = 'easeInOutSine';
  }
  return a;
};
ST.mirror = v => [v[0], -v[1], -v[2]];
// helm pose from [rootZ, lean, twist, rightArm, leftArm]
ST.helm = (rz, lean, twist, ra, la, legs) => ({
  root: {position: [0, 0, rz]},
  waist: {rotation: [lean, twist, 0]},
  right_arm: {rotation: ra},
  left_arm: {rotation: la},
  right_leg: {rotation: legs ? legs[0] : [-6, 0, 2]},
  left_leg: {rotation: legs ? legs[1] : [6, 0, -2]}
});
// helm_turn_left is helm_turn_right mirrored: the arms swap sides with y and z negated, the twist negated
ST.mirrorHelm = p => ST.helm(p.root.position[2], p.waist.rotation[0], -p.waist.rotation[1],
  ST.mirror(p.left_arm.rotation), ST.mirror(p.right_arm.rotation), [ST.mirror(p.left_leg.rotation), ST.mirror(p.right_leg.rotation)]);

ST.P = {
  HOLD: ST.helm(-2, 10, 0, [-52.1, -0.3, -10.4], [-52.1, 0.3, 10.4]),
  HOLD_BREATHE: ST.helm(-2, 10.5, 0, [-52.4, -0.3, -10.4], [-52.4, 0.3, 10.4]),
  // right hand from 35 to 82 degrees clockwise of the top, left hand from -85 to -30; back with the hands off the rim
  TURN_R0: ST.helm(-2, 12, 8, [-60.3, -21.1, -13.6], [-38.9, -2.9, -5.6]),
  TURN_R1: ST.helm(-2.5, 6, -2, [-47.3, 2.8, -9.5], [-50.6, -3.8, 21.9]),
  TURN_R2: ST.helm(-1.5, 16, -8, [-39.8, -7.2, 19.0], [-59.6, 20.6, 32.2]),
  TURN_R3: ST.helm(-1.5, 2, -2, [-40.9, -4.1, 0.8], [-47.7, -10.4, 28.8]),
  // gun crew: a hand on the breech, leaning in to sight along the barrel; the other hand on the thigh
  AIM: {root: {position: [0, 0, -1]}, waist: {rotation: [25, 0, 0]}, right_arm: {rotation: [-45, -10, 0]},
    left_arm: {rotation: [-20, 0, -4]}, right_leg: {rotation: [-14, 0, 2]}, left_leg: {rotation: [10, 0, -2]}},
  AIM_LOW: {root: {position: [0, 0, -1]}, waist: {rotation: [28, 0, 0]}, right_arm: {rotation: [-47, -9, 0]},
    left_arm: {rotation: [-22, 0, -4]}, right_leg: {rotation: [-14, 0, 2]}, left_leg: {rotation: [10, 0, -2]}},
  // ramming: both hands on a level rammer staff, the right hand ahead; drawn back, then driven down the bore
  RAM_BACK: {root: {position: [0, 0, 0]}, waist: {rotation: [8, 0, 0]}, right_arm: {rotation: [-70, -15, 0]},
    left_arm: {rotation: [-55, 20, 0]}, right_leg: {rotation: [-16, 0, 3]}, left_leg: {rotation: [14, 0, -3]}},
  RAM_PUSH: {root: {position: [0, 0, -1.5]}, waist: {rotation: [22, 0, 0]}, right_arm: {rotation: [-85, -10, 0]},
    left_arm: {rotation: [-75, 18, 0]}, right_leg: {rotation: [-20, 0, 3]}, left_leg: {rotation: [16, 0, -3]}},
  // firing: the linstock raised, the lunge to the touch hole, standing clear, back to rest
  FIRE_READY: {root: {position: [0, 0, 0]}, waist: {rotation: [0, 0, 0]}, right_arm: {rotation: [-150, 0, 15]},
    left_arm: {rotation: [0, 0, -5]}, right_leg: {rotation: [0, 0, 0]}, left_leg: {rotation: [0, 0, 0]}},
  FIRE_TOUCH: {root: {position: [0, 0, -2]}, waist: {rotation: [30, 0, 0]}, right_arm: {rotation: [-60, -10, 10]},
    left_arm: {rotation: [10, 0, -15]}, right_leg: {rotation: [-30, 0, 2]}, left_leg: {rotation: [18, 0, -2]}},
  FIRE_CLEAR: {root: {position: [0, 0, 1]}, waist: {rotation: [-8, 0, 0]}, right_arm: {rotation: [-110, 0, 25]},
    left_arm: {rotation: [-10, 0, -8]}, right_leg: {rotation: [-10, 0, 0]}, left_leg: {rotation: [8, 0, 0]}},
  FIRE_REST: {root: {position: [0, 0, 0]}, waist: {rotation: [0, 0, 0]}, right_arm: {rotation: [0, 0, 0]},
    left_arm: {rotation: [0, 0, 0]}, right_leg: {rotation: [0, 0, 0]}, left_leg: {rotation: [0, 0, 0]}},
  // capstan: chest against the bars, arms forward on them, walking round in long strides
  CAP_A: {waist: {rotation: [30, 0, 0], position: [0, 0, 0]}, right_arm: {rotation: [-75, -14, 0]},
    left_arm: {rotation: [-75, 14, 0]}, right_leg: {rotation: [-28, 0, 0]}, left_leg: {rotation: [22, 0, 0]}},
  CAP_MID: {waist: {rotation: [33, 0, 0], position: [0, -0.6, 0]}, right_arm: {rotation: [-78, -14, 0]},
    left_arm: {rotation: [-78, 14, 0]}, right_leg: {rotation: [-3, 0, 0]}, left_leg: {rotation: [-3, 0, 0]}},
  CAP_B: {waist: {rotation: [30, 0, 0], position: [0, 0, 0]}, right_arm: {rotation: [-75, -14, 0]},
    left_arm: {rotation: [-75, 14, 0]}, right_leg: {rotation: [22, 0, 0]}, left_leg: {rotation: [-28, 0, 0]}}
};

ST.build = function () {
  const P = ST.P, M = ST.mirrorHelm;
  ST.make('helm_hold', 4.0, 'loop', [{t: 0, pose: P.HOLD}, {t: 2, pose: P.HOLD_BREATHE}, {t: 4, pose: P.HOLD}]);
  ST.make('helm_turn_right', 1.0, 'loop', [{t: 0, pose: P.TURN_R0}, {t: 0.3, pose: P.TURN_R1}, {t: 0.6, pose: P.TURN_R2},
    {t: 0.8, pose: P.TURN_R3}, {t: 1.0, pose: P.TURN_R0}]);
  ST.make('helm_turn_left', 1.0, 'loop', [{t: 0, pose: M(P.TURN_R0)}, {t: 0.3, pose: M(P.TURN_R1)}, {t: 0.6, pose: M(P.TURN_R2)},
    {t: 0.8, pose: M(P.TURN_R3)}, {t: 1.0, pose: M(P.TURN_R0)}]);
  ST.make('cannon_aim', 2.0, 'loop', [{t: 0, pose: P.AIM}, {t: 1, pose: P.AIM_LOW}, {t: 2, pose: P.AIM}]);
  ST.make('cannon_load', 1.5, 'loop', [{t: 0, pose: P.RAM_BACK}, {t: 0.6, pose: P.RAM_PUSH}, {t: 0.85, pose: P.RAM_PUSH},
    {t: 1.5, pose: P.RAM_BACK}]);
  ST.make('cannon_fire', 0.75, 'once', [{t: 0, pose: P.FIRE_READY}, {t: 0.25, pose: P.FIRE_TOUCH}, {t: 0.4, pose: P.FIRE_TOUCH},
    {t: 0.6, pose: P.FIRE_CLEAR}, {t: 0.75, pose: P.FIRE_REST}]);
  ST.make('capstan_push', 1.2, 'loop', [{t: 0, pose: P.CAP_A}, {t: 0.3, pose: P.CAP_MID}, {t: 0.6, pose: P.CAP_B},
    {t: 0.9, pose: P.CAP_MID}, {t: 1.2, pose: P.CAP_A}]);
  return Animation.all.map(a => a.name + ':' + a.loop + ':' + a.length);
};
ST.NAMES = ['helm_hold', 'helm_turn_right', 'helm_turn_left', 'cannon_aim', 'cannon_load', 'cannon_fire', 'capstan_push'];

// Contact sheet of the station animations: one row per animation, `frames` evenly spaced frames (first to last) from
// the front three-quarter (left block of frames) and from the mob's right side (right block). Proxy props for the shot
// only (not saved): the helm wheel (rim, spokes, post) where the wheel stands (rows helm_*), a cylinder for the gun
// (rows cannon_*). Written to art/renders/<file>.
ST.render = function (repo, file, frames) {
  const W = 200, H = 260, names = ST.NAMES, views = [[-34, 30, -46], [58, 22, -8]];
  const r = new THREE.WebGLRenderer({preserveDrawingBuffer: true, alpha: true, antialias: true});
  r.setSize(W, H); r.setClearColor(0x000000, 0);
  const out = document.createElement('canvas'); out.width = W * frames * views.length + 150; out.height = H * names.length;
  const ctx = out.getContext('2d'); ctx.fillStyle = '#d7dde3'; ctx.fillRect(0, 0, out.width, out.height);
  ctx.fillStyle = '#223'; ctx.font = '16px sans-serif';
  const grid = typeof three_grid !== 'undefined' ? three_grid.visible : null;
  if (grid !== null) three_grid.visible = false;
  const brown = new THREE.MeshLambertMaterial({color: 0x7a5230}), iron = new THREE.MeshLambertMaterial({color: 0x333438});
  const wheel = new THREE.Group();
  const rim = new THREE.Mesh(new THREE.TorusGeometry(5.9, 0.6, 8, 32), brown); wheel.add(rim);
  for (let i = 0; i < 4; i++) { const s = new THREE.Mesh(new THREE.BoxGeometry(0.8, 15, 0.8), brown); s.rotation.z = i * Math.PI / 4; wheel.add(s); }
  wheel.position.set(0, 12.84, -11.75);
  const post = new THREE.Mesh(new THREE.BoxGeometry(3, 12.84, 3), brown); post.position.set(0, 6.42, -14);
  const gun = new THREE.Mesh(new THREE.CylinderGeometry(3.2, 3.6, 22, 12), iron); gun.rotation.z = Math.PI / 2; gun.position.set(0, 9.5, -12);
  const light = new THREE.AmbientLight(0xffffff, 0.6);
  names.forEach((name, row) => {
    const a = Animation.all.find(x => x.name === name);
    const props = name.startsWith('helm') ? [wheel, post] : name.startsWith('cannon') ? [gun] : [];
    props.forEach(p => scene.add(p)); scene.add(light);
    a.select();
    for (let i = 0; i < frames; i++) {
      Timeline.setTime(a.length * i / (frames - 1)); Animator.preview();
      views.forEach((v, j) => {
        const cam = new THREE.PerspectiveCamera(40, W / H, 1, 1000);
        cam.position.set(v[0], v[1], v[2]); cam.lookAt(new THREE.Vector3(0, 14, -6));
        r.render(scene, cam); ctx.drawImage(r.domElement, 150 + (j * frames + i) * W, row * H);
      });
    }
    props.forEach(p => scene.remove(p)); scene.remove(light);
    ctx.fillStyle = '#223'; ctx.fillText(name, 8, row * H + H / 2);
    ctx.fillText(a.length + ' s, ' + a.loop, 8, row * H + H / 2 + 20);
  });
  if (grid !== null) three_grid.visible = grid;
  r.dispose();
  Animator.showDefaultPose();
  require('fs').writeFileSync(repo + '/art/renders/' + file, Buffer.from(out.toDataURL('image/png').split(',')[1], 'base64'));
  return file;
};
ST.build();
