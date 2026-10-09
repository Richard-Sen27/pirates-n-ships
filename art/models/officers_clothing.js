// The navy officer's breeches and boots as items (ART13): the item models of officers_breeches and officers_boots
// (art/models/<name>.bbmodel -> models/item/<name>.json), built with the captain's clothing helpers
// (captains_clothing.js: CC.build, CC.export, the flat XY layout facing south, item/generated's display entries). The
// worn textures come from tools/paint_apparel_armor.js. Run in Blockbench (risky_eval):
//   eval(require('fs').readFileSync('<repo>/art/models/officers_clothing.js', 'utf8'))
//   OF.init('<repo>')                          loads captains_clothing.js and adds the officer's parts to CC.PARTS
//   OF.build('<repo>', 'officers_breeches')    new java_block tab with that item
//   OF.export('<repo>', 'officers_breeches')   writes the item model and the project file (the open tab must be it)
// The look is ART6's officer (SF.navy_officer): white breeches with a navy stripe down the outer seam, a navy waist
// band with a brass buckle, gold buttons at the fall front and the knee; tall black boots with turned-down dark
// leather tops and brass buckles on the instep straps. Palette patches only (navy and whites of palette_5, the flag
// black for the boots, gold and brass of palette): no new colour.
window.OF = window.OF || {};

OF.init = function (repo) {
  (0, eval)(require('fs').readFileSync(repo + '/art/models/captains_clothing.js', 'utf8'));
  Object.assign(CC.PAL, {navy_light: ['4', 0, 0], navy: ['4', 4, 0], navy_dark: ['4', 8, 0]});
  const P = CC.P;
  // The breeches: a navy waist band with a brass buckle, a white seat with two gold buttons at the fall front, white
  // legs to below the knee with a navy stripe on the outer seam (the wearer's right leg is on the left, -x) and a knee
  // band with a gold button, white stockings and black shoes below.
  const leg = [
    P('leg_r', [4.0, 4.0, 7.3], [7.8, 13.0, 8.7], 'white', {up: null, down: null, west: 'white_d'}),
    P('stripe_r', [4.0, 4.7, 8.7], [4.7, 13.0, 8.8], 'navy', {north: null, down: null, up: null}),
    P('knee_r', [3.9, 4.0, 7.2], [7.9, 4.7, 8.8], 'white_d', {up: null}),
    P('knee_button_r', [4.4, 4.15, 8.8], [5.0, 4.55, 8.95], 'gold', {north: null}),
    P('stocking_r', [4.2, 0.6, 7.4], [7.6, 4.0, 8.6], 'white', {up: null, down: 'white_d', west: 'white_d'}),
    P('shoe_r', [4.0, 0.0, 7.3], [7.8, 0.6, 8.9], 'black', {})
  ];
  CC.PARTS.officers_breeches = [
    P('seat', [4.05, 10.0, 7.25], [11.95, 13.0, 8.75], 'white', {up: null, down: null}),
    P('waistband', [3.9, 13.0, 7.2], [12.1, 14.4, 8.8], 'navy', {up: 'navy_dark'}),
    P('buckle', [7.4, 13.1, 8.8], [8.6, 14.3, 8.95], 'brass', {north: null}),
    ...CC.both(P('fall_button_r', [6.2, 11.4, 8.75], [6.8, 12.0, 8.85], 'gold', {north: null})),
    ...leg, ...leg.map(CC.mirrorX)
  ];
  // The boots: a pair seen from the side, toes to the left, as the captain's; black shafts and feet, dark leather tops
  // turned down, a black strap over the instep with a brass buckle, black soles and heels. The right-hand boot sits
  // 1 px behind.
  const boot = [
    P('shaft', [3.0, 3.0, 7.3], [6.4, 10.0, 8.7], 'char', {up: null, down: null}),
    P('cuff', [2.6, 9.6, 7.0], [6.8, 12.6, 9.0], 'horn', {up: 'black', down: 'char_d'}),
    P('cuff_rim', [2.6, 12.6, 7.0], [6.8, 12.9, 9.0], 'char_l', {down: null}),
    P('foot', [0.6, 1.0, 7.3], [6.4, 3.0, 8.7], 'char', {}),
    P('strap', [2.8, 1.9, 7.2], [3.6, 3.1, 8.8], 'black', {down: null}),
    P('buckle', [2.75, 2.0, 8.8], [3.65, 3.0, 8.95], 'brass', {north: null}),
    P('sole', [0.5, 0.4, 7.25], [6.5, 1.0, 8.75], 'black', {up: null}),
    P('heel', [5.2, 0.0, 7.3], [6.5, 0.4, 8.7], 'black', {up: null})
  ];
  CC.PARTS.officers_boots = [
    ...boot.map(p => Object.assign({}, p, {name: p.name + '_a'})),
    ...boot.map(p => {
      const q = CC.shift(p, 8.0, '_b');
      return Object.assign(q, {from: [q.from[0], q.from[1], q.from[2] - 1.0], to: [q.to[0], q.to[1], q.to[2] - 1.0]});
    })
  ];
  return Object.keys(CC.PARTS);
};

OF.build = (repo, item) => CC.build(repo, item);
OF.export = (repo, item) => CC.export(repo, item);
