// Musket animations on the crew rig (M6): musket_aim (hold), musket_reload (5 s, once), musket_shove (0.5 s, once).
// Run in Blockbench (risky_eval) after crew_member_animations.js, with a crew-rig GeckoLib project open:
//   eval(require('fs').readFileSync('<repo>/art/models/entity/musket_animations.js', 'utf8'))
// Values are in file convention (see crew_member_animations.js). The poses come from the player's P3/F8h firearm poses
// (art/animations/player_poses.js, F9.G: ra/la arm rotations, ri/riPos the gun in the hand, twist/lean of the torso),
// mapped onto this rig: ri/riPos key `right_hand` (the held-item locator, so the musket turns with it), twist and lean
// key `waist` (y and x, pivoted at the hips like the player builder's torso). The arms are children of `waist` here,
// so the waist's turn is taken out of the arm angles (MSK.arm) to leave the arms where the player poses put them. Only
// right_arm, left_arm, right_hand and waist are keyed: the legs keep walking (body controller) and the head follows
// the look (code). In game the aim pose gets the look pitch added to both arms (SeafarerModel), like the player's.
window.MSK = window.MSK || {};
MSK.bone = n => Group.all.find(g => g.name === n);
MSK.toInternal = (ch, v) => ch === 'rotation' ? [-v[0], -v[1], v[2]] : [-v[0], v[1], v[2]];
MSK.arm = (a, p) => [a[0] - (p.lean || 0), a[1] - (p.twist || 0), a[2]];
MSK.expand = function (p) {
  const e = {
    right_arm: {rotation: MSK.arm(p.ra, p)},
    left_arm: {rotation: MSK.arm(p.la, p)},
    right_hand: {rotation: p.ri || [0, 0, 0]},
    waist: {rotation: [p.lean || 0, p.twist || 0, 0]}
  };
  if (p.riPos) e.right_hand.position = p.riPos;
  return e;
};
MSK.make = function (name, length, loop, keys) {
  const old = Animation.all.find(a => a.name === name); if (old) old.remove(false);
  const a = new Animation({name, length, loop, snapping: 100}).add(false);
  for (const k of keys) {
    const e = MSK.expand(k.pose);
    for (const bn in e) for (const ch in e[bn]) {
      const v = MSK.toInternal(ch, e[bn][ch].map(x => Math.round(x * 100) / 100));
      const kf = a.getBoneAnimator(MSK.bone(bn)).addKeyframe({channel: ch, time: k.t, interpolation: 'linear',
        data_points: [{x: v[0], y: v[1], z: v[2]}]});
      kf.easing = 'easeInOutSine';
    }
  }
  return a;
};
// Every reload pose keys right_hand's position (default [0, 0, 0]) so the channel never interpolates against a gap.
MSK.withPos = keys => keys.map(k => ({t: k.t, pose: Object.assign({riPos: [0, 0, 0]}, k.pose)}));

MSK.P = {
  REST: {ra: [-10, 0, 0], la: [0, 0, 0], ri: [0, 0, 0], twist: 0, lean: 0},
  // aim (player MUSKET_AIM_RISE / MUSKET_AIM): right hand at the lock, left hand under the barrel, cheek to the stock
  AIM_RISE: {ra: [-55, -12, 0], la: [-55, 25, 0], ri: [50, 0, 0], twist: 8, lean: 3},
  AIM: {ra: [-84, -17, 0], la: [-91, 37, 0], ri: [84, 0, 17], twist: 15, lean: 5},
  // reload (player M_*): butt on the ground, the left hand pours and rams twice, raise, cock
  UP: {ra: [-20, -35, 0], la: [0, 0, 0], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 0, lean: 2},
  POUR: {ra: [-20, -35, 0], la: [-100, 70, 0], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 5, lean: 2},
  POUR2: {ra: [-20, -35, 0], la: [-97, 74, 5], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 5, lean: 2},
  ROD_TOP: {ra: [-20, -35, 0], la: [-125, 65, 0], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 5, lean: 0},
  ROD_DOWN: {ra: [-20, -35, 0], la: [-95, 70, 0], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 5, lean: 4},
  STOW: {ra: [-20, -35, 0], la: [-30, 10, -10], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 0, lean: 2},
  RAISE: {ra: [-45, -35, 0], la: [-90, 15, 0], ri: [15, 0, 0], twist: 8, lean: 3},
  COCK: {ra: [-45, -35, 0], la: [-66, 52, 10], ri: [15, 0, 0], twist: 8, lean: 4},
  COCK2: {ra: [-47, -35, 0], la: [-61, 49, 8], ri: [9, 0, 0], twist: 8, lean: 4},
  // shove: musket across the chest (port arms), turned butt first and drawn back, then the butt driven forward with
  // the right shoulder (ri about -80 turns the barrel back along the forearm, so the butt leads)
  PORT: {ra: [-45, -35, 0], la: [-90, 15, 0], ri: [15, 0, 0], twist: 8, lean: 2},
  BUTT_BACK: {ra: [-35, -15, 5], la: [-55, 35, 0], ri: [-70, 0, 0], twist: 22, lean: -3},
  BUTT_HIT: {ra: [-88, -5, 0], la: [-62, 40, 0], ri: [-82, 0, 0], twist: -15, lean: 10}
};

MSK.build = function () {
  const P = MSK.P;
  MSK.make('musket_aim', 0.35, 'hold', [{t: 0, pose: P.REST}, {t: 0.15, pose: P.AIM_RISE}, {t: 0.35, pose: P.AIM}]);
  MSK.make('musket_reload', 5.0, 'once', MSK.withPos([{t: 0, pose: P.REST}, {t: 0.4, pose: P.UP}, {t: 0.75, pose: P.POUR},
    {t: 1.05, pose: P.POUR2}, {t: 1.35, pose: P.POUR}, {t: 1.7, pose: P.ROD_TOP}, {t: 2.1, pose: P.ROD_DOWN},
    {t: 2.5, pose: P.ROD_TOP}, {t: 2.9, pose: P.ROD_DOWN}, {t: 3.3, pose: P.ROD_TOP}, {t: 3.6, pose: P.STOW},
    {t: 4.0, pose: P.RAISE}, {t: 4.3, pose: P.COCK}, {t: 4.45, pose: P.COCK2}, {t: 4.65, pose: P.RAISE}, {t: 5.0, pose: P.REST}]));
  MSK.make('musket_shove', 0.5, 'once', [{t: 0, pose: P.PORT}, {t: 0.15, pose: P.BUTT_BACK}, {t: 0.25, pose: P.BUTT_HIT},
    {t: 0.5, pose: P.REST}]);
  return Animation.all.map(a => a.name + ':' + a.loop + ':' + a.length);
};
// Strip of an animation: `frames` evenly spaced frames (first to last), front three-quarter view on top, the mob's
// left side below; written to art/renders/<file>. The human's viewport never moves.
MSK.render = function (repo, name, frames, file) {
  const W = 240, H = 320, a = Animation.all.find(x => x.name === name);
  const views = [[-38, 30, -52], [-62, 24, 0]];
  const r = new THREE.WebGLRenderer({preserveDrawingBuffer: true, alpha: true, antialias: false});
  r.setSize(W, H); r.setClearColor(0x000000, 0);
  const out = document.createElement('canvas'); out.width = W * frames; out.height = H * views.length;
  const ctx = out.getContext('2d'); ctx.fillStyle = '#d7dde3'; ctx.fillRect(0, 0, out.width, out.height);
  const grid = typeof three_grid !== 'undefined' ? three_grid.visible : null;
  if (grid !== null) three_grid.visible = false;
  a.select();
  for (let i = 0; i < frames; i++) {
    Timeline.setTime(a.length * i / (frames - 1)); Animator.preview();
    views.forEach((p, j) => {
      const cam = new THREE.PerspectiveCamera(38, W / H, 1, 1000);
      cam.position.set(p[0], p[1], p[2]); cam.lookAt(new THREE.Vector3(0, 16, -4));
      r.render(scene, cam); ctx.drawImage(r.domElement, i * W, j * H);
    });
  }
  if (grid !== null) three_grid.visible = grid;
  r.dispose();
  Animator.showDefaultPose();
  require('fs').writeFileSync(repo + '/art/renders/' + file, Buffer.from(out.toDataURL('image/png').split(',')[1], 'base64'));
  return file;
};
MSK.build();
