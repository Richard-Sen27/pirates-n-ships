// The pirate captain's clothing as items (ART9): the item models of captains_coat, captains_breeches and captains_boots
// (art/models/<name>.bbmodel -> models/item/<name>.json). The worn textures come from tools/paint_apparel_armor.js.
// Run in Blockbench (risky_eval):
//   eval(require('fs').readFileSync('<repo>/art/models/captains_clothing.js', 'utf8'))
//   CC.build('<repo>', 'captains_coat')    new java_block tab with that item, palette textures from textures/item/
//   CC.export('<repo>', 'captains_coat')   writes the item model and the project file (the open tab must be it)
// Like the officer's coat (officers_coat.js) every piece lies in the XY plane facing south like a vanilla sprite
// (z 7..9), so item/generated's display transforms hold it like a flat item. Colours are palette patches (charcoal of
// the flag black, gold and brass, the flag reds, leather, white, the brocade's spice gold): no new colour.
window.CC = window.CC || {};

// patch name -> [texture id, u, v] (tools/gen_item_palette.py)
CC.PAL = {
  char_l: ['4', 4, 8], char: ['4', 12, 4], char_d: ['4', 0, 8],
  gold: ['0', 12, 4], brass: ['0', 4, 4], brass_d: ['0', 8, 4],
  red: ['4', 4, 4], red_d: ['4', 8, 4],
  leather: ['0', 0, 8], leather_d: ['0', 4, 8], wood_dark: ['0', 8, 8], black: ['0', 4, 12],
  white: ['4', 12, 0], white_d: ['4', 0, 4], brocade: ['3', 8, 8], horn: ['4', 8, 12]
};
CC.SHEETS = {'0': 'palette', '3': 'palette_4', '4': 'palette_5'};

CC.P = (name, from, to, tex, faces, rot) => ({name, from, to, tex, faces: faces || {}, rot});
CC.mirrorX = p => {
  const o = Object.assign({}, p, {name: p.name.replace(/_r(\d*)$/, '_l$1'), from: [16 - p.to[0], p.from[1], p.from[2]], to: [16 - p.from[0], p.to[1], p.to[2]]});
  o.faces = Object.assign({}, p.faces); delete o.faces.east; delete o.faces.west;
  if (p.faces.east !== undefined) o.faces.west = p.faces.east;
  if (p.faces.west !== undefined) o.faces.east = p.faces.west;
  if (p.rot) o.rot = {angle: -p.rot.angle, axis: p.rot.axis, origin: [16 - p.rot.origin[0], p.rot.origin[1], p.rot.origin[2]]};
  return o;
};
CC.both = p => [p, CC.mirrorX(p)];
CC.shift = (p, dx, suffix) => Object.assign({}, p, {name: p.name + suffix, from: [p.from[0] + dx, p.from[1], p.from[2]], to: [p.to[0] + dx, p.to[1], p.to[2]]});

{
  const P = CC.P, both = CC.both;
  const sleeveRot = {angle: -22.5, axis: 'z', origin: [4.6, 12.6, 8]};
  CC.PARTS = {};
  // The coat: the officer's coat's cut in charcoal with a longer, wider skirt (the knee-long tails), a gold-edged open
  // front over a brocade waistcoat with a white jabot, brass buttons, a red sash with its knot and ends on the wearer's
  // left hip (+x, seen from the front), a leather baldric over the right shoulder with a brass buckle, crimson cuffs.
  CC.PARTS.captains_coat = [
    P('body', [4.5, 3.0, 7.2], [11.5, 13.0, 8.8], 'char', {up: 'char_l', down: null}),
    P('shoulders', [3.6, 11.6, 7.3], [12.4, 13.4, 8.7], 'char', {up: 'char_l'}),
    P('skirt', [3.0, -0.4, 7.25], [13.0, 4.0, 8.75], 'char', {up: null, east: 'char_d', west: 'char_d', north: 'char_d'}),
    P('skirt_vent', [7.85, -0.4, 7.1], [8.15, 4.0, 7.25], 'char_d', {south: null}),
    P('collar', [5.4, 13.0, 7.1], [10.6, 14.4, 8.9], 'char', {up: 'char_l'}),
    P('waistcoat', [7.05, 4.2, 8.8], [8.95, 12.4, 8.9], 'brocade', {north: null}),
    P('jabot', [7.2, 11.0, 8.9], [8.8, 13.4, 9.25], 'white', {north: null, down: 'white_d'}),
    ...both(P('lapel_r', [5.5, 4.2, 8.8], [7.0, 13.0, 9.0], 'char_l', {north: null})),
    ...both(P('edge_r', [6.75, 0.0, 8.85], [7.05, 12.95, 9.05], 'gold', {north: null})),
    ...both(P('button_r1', [5.8, 11.0, 9.0], [6.4, 11.6, 9.15], 'brass', {north: null})),
    ...both(P('button_r2', [5.8, 9.0, 9.0], [6.4, 9.6, 9.15], 'brass', {north: null})),
    ...both(P('button_r3', [5.8, 4.6, 9.0], [6.4, 5.2, 9.15], 'brass', {north: null})),
    P('hem', [3.0, -0.4, 8.75], [13.0, 0.1, 8.85], 'gold', {north: null}),
    // the sash round the waist (in front of the coat and the waistcoat), the knot and two ends on the left hip
    P('sash', [4.4, 6.0, 9.1], [11.6, 7.4, 9.3], 'red', {north: null, down: 'red_d'}),
    P('sash_knot', [10.2, 5.6, 9.3], [11.4, 7.6, 9.6], 'red_d', {north: null}),
    P('sash_end_1', [10.4, 2.4, 9.3], [11.0, 5.6, 9.45], 'red', {north: null}),
    P('sash_end_2', [11.1, 3.0, 9.3], [11.7, 5.6, 9.45], 'red_d', {north: null}),
    // the baldric from the right shoulder (-x) down to the left hip, the buckle on its middle
    P('baldric', [3.0, 8.6, 9.35], [13.0, 9.6, 9.5], 'leather', {north: null, up: 'leather_d', down: 'leather_d'},
      {angle: -45, axis: 'z', origin: [8, 9.1, 8]}),
    P('buckle', [7.4, 8.5, 9.5], [8.6, 9.7, 9.65], 'brass', {north: null}, {angle: -45, axis: 'z', origin: [8, 9.1, 8]}),
    // sleeves hanging out from the shoulders, wide crimson cuffs with a gold ring, the shirt at the wrist
    ...both(P('sleeve_r', [1.6, 4.4, 7.4], [4.6, 12.6, 8.6], 'char', {up: 'char_l', down: null}, sleeveRot)),
    ...both(P('cuff_r', [1.35, 3.4, 7.3], [4.85, 6.0, 8.7], 'red', {up: 'red_d', down: 'white'}, sleeveRot)),
    ...both(P('cuff_ring_r', [1.35, 6.0, 7.35], [4.85, 6.4, 8.65], 'gold', {}, sleeveRot))
  ];
  // The breeches: a leather waistband with a brass buckle, two dark legs to below the knee with brass knee buttons,
  // white stockings below.
  const leg = [
    P('leg_r', [4.0, 4.0, 7.3], [7.8, 13.0, 8.7], 'wood_dark', {up: null, down: null, west: 'leather_d'}),
    P('knee_r', [3.9, 4.0, 7.2], [7.9, 4.7, 8.8], 'black', {up: null}),
    P('knee_button_r', [4.3, 4.15, 8.8], [4.9, 4.55, 8.95], 'brass', {north: null}),
    P('stocking_r', [4.2, 0.6, 7.4], [7.6, 4.0, 8.6], 'white', {up: null, down: 'white_d', west: 'white_d'}),
    P('shoe_r', [4.0, 0.0, 7.3], [7.8, 0.6, 8.9], 'black', {})
  ];
  CC.PARTS.captains_breeches = [
    P('seat', [4.05, 10.0, 7.25], [11.95, 13.0, 8.75], 'wood_dark', {up: null, down: null}),
    P('waistband', [3.9, 13.0, 7.2], [12.1, 14.4, 8.8], 'leather', {up: 'leather_d'}),
    P('buckle', [7.4, 13.1, 8.8], [8.6, 14.3, 8.95], 'brass', {north: null}),
    P('fly', [7.9, 10.4, 8.75], [8.1, 13.0, 8.85], 'black', {north: null}),
    ...leg, ...leg.map(CC.mirrorX)
  ];
  // The boots: a pair seen from the side, toes to the left; tall dark shafts with lighter leather bucket tops turned
  // down, a strap with a brass buckle over the instep, black soles and heels. The right-hand boot sits 1 px behind.
  const boot = [
    P('shaft', [3.0, 3.0, 7.3], [6.4, 10.0, 8.7], 'horn', {up: null, down: null}),
    P('cuff', [2.6, 9.6, 7.0], [6.8, 12.6, 9.0], 'leather', {up: 'black', down: 'leather_d'}),
    P('cuff_rim', [2.6, 12.6, 7.0], [6.8, 12.9, 9.0], 'leather_d', {down: null}),
    P('foot', [0.6, 1.0, 7.3], [6.4, 3.0, 8.7], 'horn', {}),
    P('strap', [2.8, 1.9, 7.2], [3.6, 3.1, 8.8], 'leather_d', {down: null}),
    P('buckle', [2.95, 2.2, 8.8], [3.45, 2.8, 8.95], 'brass', {north: null}),
    P('sole', [0.5, 0.4, 7.25], [6.5, 1.0, 8.75], 'black', {up: null}),
    P('heel', [5.2, 0.0, 7.3], [6.5, 0.4, 8.7], 'black', {up: null})
  ];
  CC.PARTS.captains_boots = [
    ...boot.map(p => Object.assign({}, p, {name: p.name + '_a'})),
    ...boot.map(p => {
      const q = CC.shift(p, 8.0, '_b');
      return Object.assign(q, {from: [q.from[0], q.from[1], q.from[2] - 1.0], to: [q.to[0], q.to[1], q.to[2] - 1.0]});
    })
  ];
}

CC.uv = function (name) {
  const p = CC.PAL[name]; if (!p) throw new Error('patch ' + name);
  return {tex: p[0], uv: [p[1] + 0.5, p[2] + 0.5, p[1] + 3.5, p[2] + 3.5]};
};

CC.build = function (repo, item) {
  const parts = CC.PARTS[item]; if (!parts) throw new Error('item ' + item);
  setupProject(Formats.java_block);
  Project.name = item;
  const used = new Set(parts.flatMap(p => [p.tex, ...Object.values(p.faces)]).filter(n => n).map(n => CC.uv(n).tex));
  const T = {};
  for (const id of Object.keys(CC.SHEETS).filter(i => used.has(i))) {
    const t = new Texture({name: CC.SHEETS[id] + '.png', id}).fromPath(repo + '/common/src/main/resources/assets/pirates_n_ships/textures/item/' + CC.SHEETS[id] + '.png').add(false);
    t.id = id; t.folder = 'item'; t.namespace = 'pirates_n_ships';
    T[id] = t;
  }
  Undo.initEdit({outliner: true, elements: []});
  const made = [];
  for (const p of parts) {
    const faces = {};
    for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) {
      const name = p.faces[f] !== undefined ? p.faces[f] : p.tex;
      if (name === null) { faces[f] = {texture: null}; continue; }
      const u = CC.uv(name);
      faces[f] = {uv: u.uv, texture: T[u.tex].uuid};
    }
    const opts = {name: p.name, from: p.from, to: p.to, autouv: 0, faces};
    if (p.rot) { opts.rotation = [0, 0, 0]; opts.rotation[{x: 0, y: 1, z: 2}[p.rot.axis]] = p.rot.angle; opts.origin = p.rot.origin; }
    made.push(new Cube(opts).addTo().init());
  }
  Undo.finishEdit('ART9 ' + item, {outliner: true, elements: made});
  Canvas.updateAll();
  return {uuid: Project.uuid, elements: made.length};
};

// item/generated's display entries (left hand repeats the right hand), as the officer's coat
CC.DISPLAY = {
  thirdperson_righthand: {rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55]},
  thirdperson_lefthand: {rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55]},
  firstperson_righthand: {rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68]},
  firstperson_lefthand: {rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68]},
  ground: {rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5]},
  head: {rotation: [0, -180, 0], translation: [0, 13, 7], scale: [1, 1, 1]},
  fixed: {rotation: [0, -180, 0], translation: [0, 0, 0], scale: [1, 1, 1]}
};

CC.export = function (repo, item) {
  if (Project.name !== item) throw new Error('open tab is ' + Project.name + ', not ' + item);
  const fs = require('fs');
  const m = JSON.parse(Codecs.java_block.compile());
  const out = {credit: 'Made with Blockbench, source art/models/' + item + '.bbmodel', gui_light: 'front',
    textures: Object.assign(m.textures, {particle: 'pirates_n_ships:item/palette_5'}), display: CC.DISPLAY, elements: m.elements};
  fs.writeFileSync(repo + '/common/src/main/resources/assets/pirates_n_ships/models/item/' + item + '.json', JSON.stringify(out, null, 2) + '\n');
  const proj = Codecs.project.compile({raw: true});
  for (const t of proj.textures) { t.path = ''; t.relative_path = '../../common/src/main/resources/assets/pirates_n_ships/textures/item/' + t.name; delete t.source; }
  fs.writeFileSync(repo + '/art/models/' + item + '.bbmodel', JSON.stringify(proj));
  return {elements: out.elements.length, textures: out.textures};
};
