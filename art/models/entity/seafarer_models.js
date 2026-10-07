// Model builder and exporter for pirate.bbmodel, sailor.bbmodel, navy_soldier.bbmodel and navy_officer.bbmodel
// (M3-art). Run in Blockbench (risky_eval) after seafarer_skins.js:
//   eval(require('fs').readFileSync('<repo>/art/models/entity/seafarer_models.js', 'utf8'))
//   SF.make('pirate', '<repo>')    opens a new GeckoLib project tab, builds, paints and animates it
//   SF.exportAll('<repo>')         writes geo, texture and project file of the open project
// Every type keeps the crew member's rig exactly (M2.BONES and M2.CONTRACT of crew_member_model.js: same bones,
// pivots and box-UV contract cubes) and adds detail cubes inside the contract bones, so the shared
// crew_member.animation.json drives it. Coordinates are file (Bedrock) coordinates as in crew_member_model.js: the model
// faces -z, its right side is -x; M2.mirror flips each element for Blockbench. A detail face is a colour patch name
// (SF.PATCHES[type]), 'R:<region>' (SF.REGIONS[type], a painted area of the sheet) or null (left out).
window.SF = window.SF || {};

const T = (bone, name, from, to, tex, extra) => Object.assign({bone, name, from, to, tex}, extra || {});
const sides = (r, rest) => Object.assign({north: r, south: r, east: r, west: r}, rest || {});
// both sides of the body: c for the right (-x) side; the left copy mirrors x, the z and y rotations and swaps the
// east and west faces (SF.mirror, the same flip M2.mirror does for Blockbench)
SF.mirror = function (c) {
  const o = Object.assign({}, c);
  o.from = [-c.to[0], c.from[1], c.from[2]]; o.to = [-c.from[0], c.to[1], c.to[2]];
  if (c.origin) o.origin = [-c.origin[0], c.origin[1], c.origin[2]];
  if (c.rot) o.rot = [c.rot[0], -c.rot[1], -c.rot[2]];
  if (c.faces) {
    o.faces = Object.assign({}, c.faces); delete o.faces.east; delete o.faces.west;
    if (c.faces.west !== undefined) o.faces.east = c.faces.west;
    if (c.faces.east !== undefined) o.faces.west = c.faces.east;
  }
  return o;
};
const pair = (c, rn, ln) => [Object.assign({}, c, {name: rn}), Object.assign(SF.mirror(c), {name: ln})];

SF.DETAILS = {};

SF.DETAILS.pirate = [
  T('hat', 'bandana_knot', [-1, 26.6, 4.55], [1, 28.4, 5.6], 'bandana_d', {faces: {south: 'bandana'}}),
  T('hat', 'bandana_tail_right', [-1.6, 24.0, 4.7], [-0.5, 26.8, 5.2], 'bandana', {rot: [0, 0, -14], origin: [-1, 26.8, 4.95]}),
  T('hat', 'bandana_tail_left', [0.4, 24.4, 4.7], [1.4, 26.8, 5.2], 'bandana_d', {rot: [0, 0, 18], origin: [0.9, 26.8, 4.95]}),
  T('head', 'eyepatch', [-3.1, 26.9, -4.3], [-0.9, 28.9, -4.0], 'black'),
  T('head', 'earring', [4, 25.4, -0.2], [4.4, 26.2, 0.4], 'gold'),
  ...pair(T('body', 'x', [-2.7, 16.5, -2.62], [-1.6, 24, -2.25], 'coat_l', {faces: {north: 'R:lapel', up: 'coat_ll'}}), 'lapel_right', 'lapel_left'),
  ...pair(T('body', 'x', [-4.3, 4, 2.3], [-0.15, 12.4, 2.85], 'coat_d', {faces: {south: 'R:tail_back', up: null}, rot: [-9, 0, 0], origin: [-2.2, 12.4, 2.55]}), 'coat_tail_right', 'coat_tail_left'),
  ...pair(T('body', 'x', [-4.85, 4, -2.3], [-4.3, 12.4, 2.85], 'coat_d', {faces: {west: 'R:tail_side', up: null}, rot: [0, 0, -5], origin: [-4.55, 12.4, 0]}), 'coat_skirt_right', 'coat_skirt_left'),
  T('body', 'sash', [-4.45, 12.7, -2.45], [4.45, 14.5, 2.45], 'sash', {faces: {up: 'sash_d', down: 'sash_d'}}),
  T('body', 'sash_knot', [2.2, 12.4, -2.85], [3.6, 14.1, -2.4], 'sash_l', {faces: {down: 'sash_d'}}),
  T('body', 'sash_end_a', [2.3, 9.8, -2.75], [3.1, 12.4, -2.45], 'sash', {rot: [0, 0, 6], origin: [2.7, 12.4, -2.6]}),
  T('body', 'sash_end_b', [3.1, 10.4, -2.72], [3.8, 12.4, -2.47], 'sash_d', {rot: [0, 0, -8], origin: [3.45, 12.4, -2.6]}),
  T('body', 'belt', [-4.4, 11.6, -2.4], [4.4, 12.7, 2.4], 'belt', {faces: {down: 'belt_d'}}),
  T('body', 'buckle', [-1, 11.4, -2.6], [1, 12.9, -2.35], 'brass', {faces: {down: 'brass_d', east: 'brass_d', west: 'brass_d'}}),
  T('body', 'buckle_hole', [-0.45, 11.85, -2.65], [0.45, 12.45, -2.55], 'belt_d'),
  ...pair(T('right_arm', 'x', [-8.5, 14.0, -2.5], [-3.5, 16.8, 2.5], 'coat_l', {faces: sides('R:cuff', {down: 'coat_d'})}), 'cuff_right', 'cuff_left').map((c, i) => Object.assign(c, {bone: i ? 'left_arm' : 'right_arm'})),
  ...pair(T('right_leg', 'x', [-4.35, 6.6, -2.45], [0.35, 8.3, 2.45], 'boot_d', {faces: sides('R:boot_cuff')}), 'boot_cuff_right', 'boot_cuff_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'})),
  ...pair(T('right_leg', 'x', [-3.6, 0, -2.6], [-0.2, 1.3, -2.0], 'boot', {faces: {down: 'boot_d', up: 'boot_l'}}), 'toe_right', 'toe_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'}))
];

SF.DETAILS.sailor = [
  T('hat', 'cap_brim', [-4.7, 29.4, -4.7], [4.7, 31.2, 4.7], 'cap_d', {faces: sides('R:cap_brim')}),
  T('hat', 'cap_crown', [-4.35, 31.2, -4.35], [4.35, 33.5, 4.35], 'cap', {faces: sides('R:cap_crown', {up: 'R:cap_top', down: null})}),
  T('hat', 'cap_fold', [-3.3, 33.5, -1.5], [3.3, 34.3, 3.9], 'cap', {faces: sides('R:cap_peak', {down: null}), rot: [-8, 0, 0], origin: [0, 33.5, 3.9]}),
  T('body', 'neckerchief_band', [-3.2, 23.2, -2.45], [3.2, 24, 2.45], 'kerchief_d', {faces: {up: null}}),
  T('body', 'neckerchief_flap', [-2, 22.2, -2.6], [2, 23.2, -2.2], 'kerchief'),
  T('body', 'neckerchief_tip', [-1.1, 21.0, -2.6], [1.1, 22.2, -2.2], 'kerchief'),
  T('body', 'neckerchief_point', [-0.45, 20.0, -2.6], [0.45, 21.0, -2.2], 'kerchief_d'),
  T('body', 'neckerchief_knot', [-0.7, 22.4, -2.85], [0.7, 23.5, -2.5], 'kerchief_d'),
  T('body', 'rope_belt', [-4.35, 11.9, -2.35], [4.35, 12.8, 2.35], 'tan', {faces: {down: 'canvas_d'}}),
  T('body', 'rope_knot', [1.1, 11.6, -2.65], [2.3, 13.0, -2.3], 'bone'),
  T('body', 'rope_end_a', [1.2, 9.9, -2.55], [1.7, 11.6, -2.35], 'tan', {rot: [0, 0, 5], origin: [1.45, 11.6, -2.45]}),
  T('body', 'rope_end_b', [1.8, 10.3, -2.55], [2.25, 11.6, -2.35], 'bone', {rot: [0, 0, -6], origin: [2.0, 11.6, -2.45]}),
  ...pair(T('right_arm', 'x', [-8.4, 17.6, -2.4], [-3.6, 18.9, 2.4], 'white', {faces: {down: 'red_stripe'}}), 'cuff_right', 'cuff_left').map((c, i) => Object.assign(c, {bone: i ? 'left_arm' : 'right_arm'})),
  ...pair(T('right_leg', 'x', [-4.3, 3.9, -2.4], [-0.1, 5.3, 2.4], 'canvas_d', {faces: sides('R:slops')}), 'hem_right', 'hem_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'})),
  ...pair(T('right_leg', 'x', [-4.05, 0, -2.5], [0.25, 1.3, 2.25], 'shoe', {faces: {down: 'shoe_d', up: 'shoe_d'}}), 'shoe_right', 'shoe_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'})),
  ...pair(T('right_leg', 'x', [-2.4, 0.5, -2.62], [-1.4, 1.25, -2.45], 'brass', {faces: {down: 'brass_d'}}), 'buckle_right', 'buckle_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'}))
];

// tricorn: three upturned brim walls around a low crown (back wall along x, two front walls meeting at a point)
SF.tricornWall = function (name, sign) {
  const cx = 2.9 * sign, cz = -1.0, a = 28.4 * sign;
  return [
    T('hat', name, [cx - 0.35, 30.6, cz - 6.1], [cx + 0.35, 34.2, cz + 6.1], 'hat_d',
      {faces: {west: sign < 0 ? 'R:brim_out' : 'R:brim_in', east: sign < 0 ? 'R:brim_in' : 'R:brim_out', up: 'belt_white', north: 'belt_white', south: 'hat_d'}, rot: [0, a, 0], origin: [cx, 30.6, cz]}),
    T('hat', name + '_plate', [cx - (sign < 0 ? 0.3 : 2.6), 30.4, cz - 5.6], [cx + (sign < 0 ? 2.6 : 0.3), 30.8, cz + 5.6], 'hat_d',
      {faces: {up: 'hat'}, rot: [0, a, 0], origin: [cx, 30.6, cz]})
  ];
};
SF.DETAILS.navy_soldier = [
  T('hat', 'tricorn_crown', [-4.2, 31, -4.2], [4.2, 33.8, 4.2], 'hat', {faces: sides('R:crown', {up: 'R:crown_top', down: null})}),
  T('hat', 'tricorn_back', [-5.9, 30.6, 4.0], [5.9, 34.2, 4.7], 'hat_d', {faces: {south: 'R:brim_out', north: 'R:brim_in', up: 'belt_white'}, rot: [-10, 0, 0], origin: [0, 30.6, 4.35]}),
  T('hat', 'tricorn_back_plate', [-5.6, 30.4, 1.6], [5.6, 30.8, 4.4], 'hat_d', {faces: {up: 'hat'}}),
  ...SF.tricornWall('tricorn_right', -1),
  ...SF.tricornWall('tricorn_left', 1),
  T('hat', 'cockade', [3.15, 31.6, -4.6], [3.45, 33.4, -2.8], 'black', {rot: [0, 28.4, 0], origin: [2.9, 30.6, -1.0]}),
  T('hat', 'cockade_button', [3.4, 32.2, -4.1], [3.6, 32.8, -3.3], 'white', {rot: [0, 28.4, 0], origin: [2.9, 30.6, -1.0]}),
  T('head', 'queue', [-0.6, 22.6, 4.0], [0.6, 25.6, 4.6], 'grey', {faces: {down: 'grey_d'}}),
  T('head', 'queue_bow', [-1.3, 25.0, 4.05], [1.3, 26.0, 4.75], 'black'),
  T('body', 'collar', [-4.4, 22.9, -2.45], [4.4, 24, 2.45], 'facing_d', {faces: sides('R:collar', {up: 'facing_d', down: null})}),
  T('body', 'belt_plate', [-1, 17.7, -2.62], [1, 19.6, -2.3], 'brass', {faces: {down: 'brass_d', east: 'brass_d', west: 'brass_d'}}),
  T('body', 'cartridge_box', [-3.6, 11.4, 2.9], [-0.6, 13.8, 3.9], 'black', {faces: {up: 'shoe'}}),
  T('body', 'cartridge_badge', [-2.5, 12.2, 3.9], [-1.7, 13.0, 4.0], 'brass'),
  ...pair(T('body', 'x', [-4.3, 6, 2.3], [-0.15, 12.4, 2.85], 'navy_d', {faces: {south: 'R:tail_back', north: 'facing', up: null}, rot: [-8, 0, 0], origin: [-2.2, 12.4, 2.55]}), 'coat_tail_right', 'coat_tail_left'),
  ...pair(T('body', 'x', [-4.85, 6, -2.3], [-4.3, 12.4, 2.85], 'navy_d', {faces: {west: 'R:tail_side', east: 'facing', up: null}, rot: [0, 0, -4], origin: [-4.55, 12.4, 0]}), 'coat_skirt_right', 'coat_skirt_left'),
  ...pair(T('right_arm', 'x', [-8.45, 13.9, -2.45], [-3.55, 16.5, 2.45], 'facing_d', {faces: sides('R:cuff')}), 'cuff_right', 'cuff_left').map((c, i) => Object.assign(c, {bone: i ? 'left_arm' : 'right_arm'})),
  ...pair(T('right_leg', 'x', [-4.0, 0, -2.6], [0.2, 1.1, 2.2], 'shoe', {faces: {down: 'shoe_d'}}), 'shoe_right', 'shoe_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'})),
  ...pair(T('right_leg', 'x', [-2.4, 0.4, -2.72], [-1.4, 1.05, -2.55], 'brass', {faces: {down: 'brass_d'}}), 'buckle_right', 'buckle_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'})),
  ...pair(T('right_leg', 'x', [-4.4, 4.6, -2.5], [0.4, 5.4, 2.5], 'shoe_d', {faces: sides('R:gaiter')}), 'gaiter_top_right', 'gaiter_top_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'}))
];

// bicorne worn athwart: a low crown, a stepped front and back flap leaning towards each other, end plates
SF.bicorneFlap = function (side) {
  const z0 = side < 0 ? -4.75 : 4.05, z1 = z0 + 0.7, rot = [side < 0 ? 18 : -18, 0, 0], origin = [0, 30.4, side < 0 ? -4.4 : 4.4];
  const out = side < 0 ? 'north' : 'south', inn = side < 0 ? 'south' : 'north', n = side < 0 ? 'front' : 'back';
  const f = {up: 'hat_d', down: 'hat_d', east: 'hat_d', west: 'hat_d'}; f[out] = 'R:flap'; f[inn] = 'R:flap_in';
  const piece = (name, x0, x1, top) => T('hat', 'bicorne_' + n + '_' + name, [x0, 30.4, z0], [x1, top, z1], 'hat_d', {faces: Object.assign({}, f), rot, origin});
  return [piece('centre', -3, 3, 36.4), piece('right', -5.6, -3, 35.0), piece('left', 3, 5.6, 35.0),
    piece('right_tip', -7.6, -5.6, 33.4), piece('left_tip', 5.6, 7.6, 33.4)];
};
SF.DETAILS.navy_officer = [
  T('hat', 'bicorne_crown', [-4.3, 30.6, -3.6], [4.3, 33.4, 3.6], 'hat', {faces: sides('R:crown', {up: 'R:crown_top', down: null})}),
  ...SF.bicorneFlap(-1),
  ...SF.bicorneFlap(1),
  T('hat', 'bicorne_end_right', [-7.6, 30.4, -4.05], [-4.3, 31.6, 4.05], 'hat_d', {faces: {up: 'hat'}}),
  T('hat', 'bicorne_end_left', [4.3, 30.4, -4.05], [7.6, 31.6, 4.05], 'hat_d', {faces: {up: 'hat'}}),
  T('hat', 'bicorne_loop', [-0.8, 31.6, -5.05], [0.8, 35.6, -4.75], 'gold', {faces: {east: 'gold_d', west: 'gold_d'}, rot: [18, 0, 0], origin: [0, 30.4, -4.4]}),
  T('hat', 'bicorne_button', [-0.45, 32.0, -5.2], [0.45, 32.9, -5.0], 'gold_l', {rot: [18, 0, 0], origin: [0, 30.4, -4.4]}),
  T('hat', 'bicorne_cockade', [-1.4, 33.6, -5.0], [1.4, 35.2, -4.8], 'black', {rot: [18, 0, 0], origin: [0, 30.4, -4.4]}),
  T('head', 'queue', [-0.6, 22.6, 4.0], [0.6, 25.6, 4.6], 'white', {faces: {down: 'belt_shade'}}),
  T('head', 'queue_bow', [-1.3, 25.0, 4.05], [1.3, 26.0, 4.75], 'black'),
  T('body', 'collar', [-4.4, 22.9, -2.45], [4.4, 24, 2.45], 'navy', {faces: {up: 'gold', down: null}}),
  T('body', 'sash', [-4.45, 11.8, -2.45], [4.45, 13.8, 2.45], 'crimson_d', {faces: sides('R:sash')}),
  T('body', 'sash_knot', [3.7, 11.3, -2.1], [4.95, 13.7, 0.5], 'crimson_l', {faces: {down: 'crimson_d'}}),
  T('body', 'sash_end_a', [4.45, 8.0, -1.7], [4.95, 11.4, -0.5], 'crimson', {rot: [0, 0, 4], origin: [4.7, 11.4, -1.1]}),
  T('body', 'sash_end_b', [4.45, 8.6, -0.4], [4.95, 11.4, 0.7], 'crimson_d', {rot: [0, 0, -3], origin: [4.7, 11.4, 0.15]}),
  T('body', 'tassel_a', [4.35, 7.0, -1.8], [5.05, 8.0, -0.4], 'gold', {rot: [0, 0, 4], origin: [4.7, 11.4, -1.1]}),
  T('body', 'tassel_b', [4.35, 7.6, -0.5], [5.05, 8.6, 0.8], 'gold_d', {rot: [0, 0, -3], origin: [4.7, 11.4, 0.15]}),
  ...pair(T('body', 'x', [-4.3, 5, 2.3], [-0.15, 12.4, 2.85], 'navy_d', {faces: {south: 'R:tail_back', north: 'white', up: null}, rot: [-8, 0, 0], origin: [-2.2, 12.4, 2.55]}), 'coat_tail_right', 'coat_tail_left'),
  ...pair(T('body', 'x', [-4.85, 5, -2.3], [-4.3, 12.4, 2.85], 'navy_d', {faces: {west: 'R:tail_side', east: 'white', up: null}, rot: [0, 0, -4], origin: [-4.55, 12.4, 0]}), 'coat_skirt_right', 'coat_skirt_left'),
  ...pair(T('right_arm', 'x', [-8.6, 23.9, -2.6], [-4.2, 24.7, 2.6], 'gold', {faces: {up: 'R:epaulette', down: 'gold_d'}}), 'epaulette_right', 'epaulette_left').map((c, i) => Object.assign(c, {bone: i ? 'left_arm' : 'right_arm'})),
  ...pair(T('right_arm', 'x', [-8.95, 21.6, -2.6], [-8.6, 23.9, 2.6], 'gold_d', {faces: sides('R:fringe', {up: null})}), 'fringe_right', 'fringe_left').map((c, i) => Object.assign(c, {bone: i ? 'left_arm' : 'right_arm'})),
  ...pair(T('right_arm', 'x', [-8.6, 21.8, -2.9], [-5.0, 23.9, -2.6], 'gold_d', {faces: sides('R:fringe', {up: null})}), 'fringe_front_right', 'fringe_front_left').map((c, i) => Object.assign(c, {bone: i ? 'left_arm' : 'right_arm'})),
  ...pair(T('right_arm', 'x', [-8.6, 21.8, 2.6], [-5.0, 23.9, 2.9], 'gold_d', {faces: sides('R:fringe', {up: null})}), 'fringe_back_right', 'fringe_back_left').map((c, i) => Object.assign(c, {bone: i ? 'left_arm' : 'right_arm'})),
  ...pair(T('right_arm', 'x', [-8.45, 13.9, -2.45], [-3.55, 16.7, 2.45], 'navy_d', {faces: sides('R:cuff')}), 'cuff_right', 'cuff_left').map((c, i) => Object.assign(c, {bone: i ? 'left_arm' : 'right_arm'})),
  ...pair(T('right_leg', 'x', [-4.35, 6.4, -2.45], [0.35, 8.3, 2.45], 'boot_d', {faces: sides('R:boot_top')}), 'boot_top_right', 'boot_top_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'})),
  ...pair(T('right_leg', 'x', [-3.6, 0, -2.6], [-0.2, 1.2, -2.0], 'boot', {faces: {down: 'boot_d', up: 'boot_l'}}), 'toe_right', 'toe_left').map((c, i) => Object.assign(c, {bone: i ? 'left_leg' : 'right_leg'}))
];

// --- building ---------------------------------------------------------------------------------------------------

SF.faceUv = function (type, key) {
  if (key.startsWith('R:')) {
    const r = SF.REGIONS[type][key.slice(2)]; if (!r) throw new Error(type + ' region ' + key);
    return [r[0], r[1], r[0] + r[2], r[1] + r[3]];
  }
  const i = SF.PATCHES[type].indexOf(key); if (i < 0) throw new Error(type + ' patch ' + key);
  const u = 56 + 2 * (i % 4), v = 16 + 2 * Math.floor(i / 4);
  return [u + 0.5, v + 0.5, u + 1.5, v + 1.5];
};

SF.build = function (type) {
  Undo.initEdit({outliner: true, elements: [], selection: true});
  for (const e of [...Outliner.elements]) e.remove();
  for (const g of [...Group.all]) g.remove(false);
  const G = {};
  for (const [n, p, o] of M2.BONES) G[n] = new Group({name: n, origin: [-o[0], o[1], o[2]]}).addTo(p ? G[p] : undefined).init();
  const made = [];
  for (const c0 of M2.CONTRACT) {
    const c = M2.mirrorContract(c0);
    made.push(new Cube({name: c.name, from: c.from, to: c.to, inflate: c.inflate, box_uv: true, uv_offset: c.box}).addTo(G[c.bone]).init());
  }
  const names = new Set();
  for (const c of SF.DETAILS[type].map(SF.mirror)) {
    if (names.has(c.name)) throw new Error('duplicate cube ' + c.name); names.add(c.name);
    const faces = {};
    for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) {
      const t = (c.faces && c.faces[f] !== undefined) ? c.faces[f] : c.tex;
      faces[f] = t === null ? {uv: [0, 0, 0, 0], texture: null} : {uv: SF.faceUv(type, t), texture: 0};
    }
    made.push(new Cube({name: c.name, from: c.from, to: c.to, inflate: c.inflate || 0, box_uv: false, rotation: c.rot || [0, 0, 0],
      origin: c.origin || [(c.from[0] + c.to[0]) / 2, (c.from[1] + c.to[1]) / 2, (c.from[2] + c.to[2]) / 2], faces}).addTo(G[c.bone]).init());
  }
  const tex = Texture.all[0];
  if (tex) for (const cube of Cube.all) for (const f in cube.faces) if (cube.faces[f].texture !== null) cube.faces[f].texture = tex.uuid;
  Undo.finishEdit('M3-art build ' + type, {outliner: true, elements: made});
  Canvas.updateAll();
  return {cubes: Cube.all.length, groups: Group.all.length};
};

SF.texturePath = (repo, type) => repo + '/common/src/main/resources/assets/pirates_n_ships/textures/entity/' + type + '.png';
SF.geoPath = (repo, type) => repo + '/common/src/main/resources/assets/pirates_n_ships/geo/' + type + '.geo.json';

SF.paintTexture = function (type) {
  const url = SF.paint(type).canvas().toDataURL('image/png');
  let tex = Texture.all[0];
  if (!tex) { tex = new Texture({name: type + '.png'}).fromDataURL(url).add(false); }
  else tex.fromDataURL(url);
  tex.name = type + '.png';
  return tex;
};

SF.make = function (type, repo) {
  setupProject(Formats.geckolib_model);
  Project.name = type; Project.geometry_name = type; Project.model_identifier = type;
  Project.texture_width = 64; Project.texture_height = 64; Project.visible_box = [3, 3, 1.5];
  const fs = require('fs');
  SF.paintTexture(type);
  eval(fs.readFileSync(repo + '/art/models/entity/crew_member_model.js', 'utf8'));
  const res = SF.build(type);
  eval(fs.readFileSync(repo + '/art/models/entity/crew_member_animations.js', 'utf8'));
  eval(fs.readFileSync(repo + '/art/models/entity/musket_animations.js', 'utf8'));
  return res;
};

SF.round = function (v) {
  if (Array.isArray(v)) return v.map(SF.round);
  if (v && typeof v === 'object') { const o = {}; for (const k in v) o[k] = SF.round(v[k]); return o; }
  if (typeof v === 'number') return Math.round(v * 10000) / 10000;
  return v;
};

SF.exportAll = function (repo) {
  const fs = require('fs'), type = Project.name;
  const geo = SF.round(Codecs.bedrock.compile({raw: true}));
  fs.writeFileSync(SF.geoPath(repo, type), autoStringify(geo));
  const tex = Texture.all[0];
  const png = tex.canvas.toDataURL('image/png').split(',')[1];
  fs.writeFileSync(SF.texturePath(repo, type), Buffer.from(png, 'base64'));
  const proj = Codecs.project.compile({raw: true});
  for (const t of proj.textures) { t.path = ''; t.relative_path = '../../../common/src/main/resources/assets/pirates_n_ships/textures/entity/' + type + '.png'; }
  fs.writeFileSync(repo + '/art/models/entity/' + type + '.bbmodel', JSON.stringify(proj));
  return {type, cubes: Cube.all.length, animations: Animation.all.map(a => a.name)};
};

// Render strip art/renders/<file>: front three-quarter, the mob's left side, back three-quarter, in the default pose.
SF.VIEWS = [[-32, 26, -50], [60, 22, 0], [36, 26, 46]];
SF.render = function (repo, file, views) {
  const W = 360, H = 480, vs = views || SF.VIEWS;
  const r = new THREE.WebGLRenderer({preserveDrawingBuffer: true, alpha: true, antialias: false});
  r.setSize(W, H); r.setClearColor(0x000000, 0);
  const out = document.createElement('canvas'); out.width = W * vs.length; out.height = H;
  const ctx = out.getContext('2d'); ctx.fillStyle = '#d7dde3'; ctx.fillRect(0, 0, out.width, out.height);
  const grid = typeof three_grid !== 'undefined' ? three_grid.visible : null;
  if (grid !== null) three_grid.visible = false;
  Animator.showDefaultPose();
  vs.forEach((p, i) => {
    const cam = new THREE.PerspectiveCamera(38, W / H, 1, 1000);
    cam.position.set(p[0], p[1], p[2]); cam.lookAt(new THREE.Vector3(0, 17, 0));
    r.render(scene, cam); ctx.drawImage(r.domElement, i * W, 0);
  });
  if (grid !== null) three_grid.visible = grid;
  r.dispose();
  require('fs').writeFileSync(repo + '/art/renders/' + file, Buffer.from(out.toDataURL('image/png').split(',')[1], 'base64'));
  return file;
};
