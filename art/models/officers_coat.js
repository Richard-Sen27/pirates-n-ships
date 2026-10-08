// Officer's coat (ART6): the item model (art/models/officers_coat.bbmodel -> models/item/officers_coat.json) and the
// worn texture (textures/models/armor/officers_coat_layer_1.png, the vanilla 64x32 armour layout drawn by vanilla's
// HumanoidArmorLayer). Run in Blockbench (risky_eval):
//   eval(require('fs').readFileSync('<repo>/art/models/officers_coat.js', 'utf8'))
//   OC.build('<repo>')        new java_block tab with the coat, palette textures from textures/item/
//   OC.export('<repo>')       writes the item model and the project file
//   OC.paintArmor('<repo>')   writes the armour texture
// The coat lies in the XY plane facing south like a vanilla sprite (collar up, skirt down, sleeves hanging out at
// 22.5 degrees), z 7..9, so item/generated's display transforms hold it like a flat item. Colours are palette patches
// (navy blue coat, white facings, gold epaulettes, buttons and lace, red cuffs): no new colour.
window.OC = window.OC || {};

// patch name -> [texture id, u, v] (tools/gen_item_palette.py)
OC.PAL = {
  navy_light: ['4', 0, 0], navy: ['4', 4, 0], navy_dark: ['4', 8, 0], white: ['4', 12, 0], white_d: ['4', 0, 4],
  red: ['4', 4, 4], red_d: ['4', 8, 4], gold: ['0', 12, 4], brass: ['0', 4, 4], brass_d: ['0', 8, 4]
};
OC.SHEETS = {'0': 'palette', '4': 'palette_5'};

const P = (name, from, to, tex, faces, rot) => ({name, from, to, tex, faces: faces || {}, rot});
const mirrorX = p => {
  const o = Object.assign({}, p, {name: p.name.replace(/_r(\d*)$/, '_l$1'), from: [16 - p.to[0], p.from[1], p.from[2]], to: [16 - p.from[0], p.to[1], p.to[2]]});
  o.faces = Object.assign({}, p.faces); delete o.faces.east; delete o.faces.west;
  if (p.faces.east !== undefined) o.faces.west = p.faces.east;
  if (p.faces.west !== undefined) o.faces.east = p.faces.west;
  if (p.rot) o.rot = {angle: -p.rot.angle, axis: p.rot.axis, origin: [16 - p.rot.origin[0], p.rot.origin[1], p.rot.origin[2]]};
  return o;
};
const both = p => [p, mirrorX(p)];
const sleeveRot = {angle: -22.5, axis: 'z', origin: [4.6, 12.6, 8]};

OC.PARTS = [
  // body: back panel, shoulders, skirt flaring out below the waist (each 0.05 px inside the next in z)
  P('body', [4.5, 3.0, 7.2], [11.5, 13.0, 8.8], 'navy', {up: 'navy_dark', down: null}),
  P('shoulders', [3.6, 11.6, 7.3], [12.4, 13.4, 8.7], 'navy', {up: 'navy_light'}),
  P('skirt', [3.4, 0.6, 7.25], [12.6, 4.0, 8.75], 'navy', {up: null, east: 'navy_dark', west: 'navy_dark', north: 'navy_dark'}),
  P('skirt_vent', [7.85, 0.6, 7.1], [8.15, 4.0, 7.25], 'navy_dark', {south: null}),
  // collar standing up behind the neck, gold lace along its top
  P('collar', [5.4, 13.0, 7.1], [10.6, 14.6, 8.9], 'navy', {up: 'navy_light'}),
  P('collar_lace', [5.4, 14.1, 8.9], [10.6, 14.6, 8.95], 'gold', {north: null}),
  // open front: white waistcoat between white lapels, gold buttons on the lapels and the waistcoat
  P('waistcoat', [7.05, 4.2, 8.8], [8.95, 13.0, 8.9], 'white', {north: null}),
  ...both(P('lapel_r', [5.5, 5.0, 8.8], [7.0, 13.0, 9.0], 'white', {north: null})),
  ...both(P('button_r1', [6.0, 11.0, 9.0], [6.6, 11.6, 9.15], 'gold', {north: null})),
  ...both(P('button_r2', [6.0, 9.0, 9.0], [6.6, 9.6, 9.15], 'gold', {north: null})),
  ...both(P('button_r3', [6.0, 7.0, 9.0], [6.6, 7.6, 9.15], 'gold', {north: null})),
  P('waist_button_1', [7.7, 10.0, 8.9], [8.3, 10.6, 9.0], 'brass', {north: null}),
  P('waist_button_2', [7.7, 7.6, 8.9], [8.3, 8.2, 9.0], 'brass', {north: null}),
  // gold lace along the skirt hem and the skirt's front edges
  P('hem', [3.4, 0.6, 8.75], [12.6, 1.1, 8.85], 'gold', {north: null}),
  // sleeves hanging out from the shoulders, red cuffs with a gold ring
  ...both(P('sleeve_r', [1.6, 4.2, 7.4], [4.6, 12.6, 8.6], 'navy', {up: 'navy_light', down: null}, sleeveRot)),
  ...both(P('cuff_r', [1.45, 3.4, 7.3], [4.75, 5.4, 8.7], 'red', {up: 'red_d', down: 'red_d'}, sleeveRot)),
  ...both(P('cuff_ring_r', [1.45, 5.4, 7.35], [4.75, 5.8, 8.65], 'gold', {}, sleeveRot)),
  // epaulettes on the shoulders with a fringe below
  ...both(P('epaulette_r', [3.3, 12.9, 7.15], [5.9, 13.9, 8.85], 'gold', {down: 'brass_d'})),
  ...both(P('fringe_r', [3.3, 11.7, 8.7], [5.9, 12.9, 8.85], 'brass', {north: null}))
];

OC.uv = function (name) {
  const p = OC.PAL[name]; if (!p) throw new Error('patch ' + name);
  return {tex: p[0], uv: [p[1] + 0.5, p[2] + 0.5, p[1] + 3.5, p[2] + 3.5]};
};

OC.build = function (repo) {
  setupProject(Formats.java_block);
  Project.name = 'officers_coat';
  const T = {};
  for (const id in OC.SHEETS) {
    const t = new Texture({name: OC.SHEETS[id] + '.png', id}).fromPath(repo + '/common/src/main/resources/assets/pirates_n_ships/textures/item/' + OC.SHEETS[id] + '.png').add(false);
    t.id = id; t.folder = 'item'; t.namespace = 'pirates_n_ships';
    T[id] = t;
  }
  Undo.initEdit({outliner: true, elements: []});
  const made = [];
  for (const p of OC.PARTS) {
    const faces = {};
    for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) {
      const name = p.faces[f] !== undefined ? p.faces[f] : p.tex;
      if (name === null) { faces[f] = {texture: null}; continue; }
      const u = OC.uv(name);
      faces[f] = {uv: u.uv, texture: T[u.tex].uuid};
    }
    const opts = {name: p.name, from: p.from, to: p.to, autouv: 0, faces};
    if (p.rot) { opts.rotation = [0, 0, 0]; opts.rotation[{x: 0, y: 1, z: 2}[p.rot.axis]] = p.rot.angle; opts.origin = p.rot.origin; }
    made.push(new Cube(opts).addTo().init());
  }
  Undo.finishEdit('ART6 officers coat', {outliner: true, elements: made});
  Canvas.updateAll();
  return made.length;
};

// item/generated's display entries (left hand repeats the right hand), as the other flat items
OC.DISPLAY = {
  thirdperson_righthand: {rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55]},
  thirdperson_lefthand: {rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55]},
  firstperson_righthand: {rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68]},
  firstperson_lefthand: {rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68]},
  ground: {rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5]},
  head: {rotation: [0, -180, 0], translation: [0, 13, 7], scale: [1, 1, 1]},
  fixed: {rotation: [0, -180, 0], translation: [0, 0, 0], scale: [1, 1, 1]}
};

OC.export = function (repo) {
  const fs = require('fs');
  const m = JSON.parse(Codecs.java_block.compile());
  const out = {credit: 'Made with Blockbench, source art/models/officers_coat.bbmodel', gui_light: 'front',
    textures: Object.assign(m.textures, {particle: 'pirates_n_ships:item/palette_5'}), display: OC.DISPLAY, elements: m.elements};
  fs.writeFileSync(repo + '/common/src/main/resources/assets/pirates_n_ships/models/item/officers_coat.json', JSON.stringify(out, null, 2) + '\n');
  const proj = Codecs.project.compile({raw: true});
  for (const t of proj.textures) { t.path = ''; t.relative_path = '../../common/src/main/resources/assets/pirates_n_ships/textures/item/' + t.name; }
  fs.writeFileSync(repo + '/art/models/officers_coat.bbmodel', JSON.stringify(proj));
  return {elements: out.elements.length, textures: out.textures};
};

// --- worn texture -------------------------------------------------------------------------------------------------
// Vanilla's armour layer 1 (64x32): the outer humanoid model inflated by 1.0; the chest slot shows only the body
// (box uv 16,16, 8x12x4) and both arms (40,16, 4x12x4; the left arm mirrors the right arm's pixels). The navy
// officer's look: blue coat open over a white waistcoat, white lapels with gold buttons, a gold-laced hem, gold
// epaulettes on the shoulder tops, red cuffs ringed in gold. Seeded speckle, deterministic.
OC.COL = {
  navy_d: [22, 32, 70], navy: [34, 50, 104], navy_l: [50, 70, 132], white: [236, 232, 220], white_d: [200, 198, 190],
  gold_d: [156, 104, 22], gold: [222, 170, 48], gold_l: [252, 226, 120], red_d: [126, 22, 28], red: [168, 34, 40]
};
OC.paintArmor = function (repo) {
  const W = 64, H = 32, d = new Uint8ClampedArray(W * H * 4);
  let seed = 0x0ff1ce;
  const rnd = () => { seed = (seed * 1103515245 + 12345) >>> 0; return (seed >>> 8) / 16777216; };
  const px = (x, y, c) => { const v = OC.COL[c], k = (y * W + x) * 4; d[k] = v[0]; d[k + 1] = v[1]; d[k + 2] = v[2]; d[k + 3] = 255; };
  const rect = (x, y, w, h, c) => { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) px(i, j, c); };
  const speckle = (x, y, w, h, c, p) => { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) if (rnd() < p) px(i, j, c); };
  const faces = (u, v, w, h, dp) => ({top: [u + dp, v, w, dp], bottom: [u + dp + w, v, w, dp], right: [u, v + dp, dp, h],
    front: [u + dp, v + dp, w, h], left: [u + dp + w, v + dp, dp, h], back: [u + 2 * dp + w, v + dp, w, h]});
  // body
  const b = faces(16, 16, 8, 12, 4);
  for (const k in b) { rect(...b[k], 'navy'); speckle(...b[k], 'navy_d', 0.15); speckle(...b[k], 'navy_l', 0.05); }
  const [fx, fy] = b.front;
  rect(fx + 3, fy, 2, 12, 'white'); speckle(fx + 3, fy, 2, 12, 'white_d', 0.15);
  rect(fx + 3, fy, 2, 1, 'white_d');
  for (let r = 0; r < 11; r++) { px(fx + 2, fy + r, 'white'); px(fx + 5, fy + r, 'white'); }
  for (const r of [2, 4, 6, 8]) { px(fx + 2, fy + r, 'gold_l'); px(fx + 5, fy + r, 'gold_l'); }
  for (const r of [3, 6]) px(fx + 4, fy + r, 'gold');
  for (const k of ['front', 'right', 'left', 'back']) { const f = b[k]; rect(f[0], f[1] + 11, f[2], 1, 'gold'); }
  rect(fx + 3, fy + 11, 2, 1, 'white_d');
  const [bx, by] = b.back; rect(bx + 3, by + 6, 1, 6, 'navy_d'); rect(bx + 4, by + 6, 1, 6, 'navy_d');
  px(bx + 2, by + 6, 'gold'); px(bx + 5, by + 6, 'gold');
  // collar on the body top's front edge
  rect(b.top[0], b.top[1] + 3, 8, 1, 'navy_l'); rect(b.top[0] + 2, b.top[1] + 3, 4, 1, 'white');
  // arms: sleeves, gold epaulette on the top and the shoulder band, red cuff ringed in gold
  const a = faces(40, 16, 4, 12, 4);
  for (const k in a) { rect(...a[k], 'navy'); speckle(...a[k], 'navy_d', 0.15); }
  rect(...a.top, 'gold'); speckle(...a.top, 'gold_d', 0.3);
  for (const k of ['front', 'right', 'left', 'back']) {
    const f = a[k];
    rect(f[0], f[1], f[2], 1, 'gold'); for (let x = f[0]; x < f[0] + f[2]; x += 2) px(x, f[1] + 1, 'gold_d');
    rect(f[0], f[1] + 8, f[2], 3, 'red'); speckle(f[0], f[1] + 8, f[2], 3, 'red_d', 0.2);
    rect(f[0], f[1] + 8, f[2], 1, 'gold'); rect(f[0], f[1] + 11, f[2], 1, 'red_d');
  }
  px(a.front[0] + 1, a.front[1] + 9, 'gold_l'); px(a.front[0] + 2, a.front[1] + 9, 'gold_l');
  rect(...a.bottom, 'white_d');
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
  cv.getContext('2d').putImageData(new ImageData(d, W, H), 0, 0);
  const path = repo + '/common/src/main/resources/assets/pirates_n_ships/textures/models/armor/officers_coat_layer_1.png';
  require('fs').mkdirSync(path.replace(/\/[^\/]+$/, ''), {recursive: true});
  require('fs').writeFileSync(path, Buffer.from(cv.toDataURL('image/png').split(',')[1], 'base64'));
  return path;
};
