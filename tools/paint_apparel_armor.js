#!/usr/bin/env node
// Paints the worn textures of the apparel clothing (ART6, ART9): vanilla's 64x32 armour layer layout, read by vanilla's
// HumanoidArmorLayer, or by apparel/client/CoatArmorModel for the two coats (which adds the tails). Writes
// common/src/main/resources/assets/pirates_n_ships/textures/models/armor/<name>.png. Deterministic: same script, same
// bytes. The captain's pieces take their colours from the pirate captain's skin palette (SF.C in
// art/models/entity/seafarer_skins.js, painter SF.pirate_captain), so they match textures/entity/pirate_captain.png.
//
//   node tools/paint_apparel_armor.js            # write every texture
//   node tools/paint_apparel_armor.js --check    # compare the painter with the committed PNGs (pixels)
//
// Layout (box UV, 64x32):
//   body (16,16) 8x12x4 and arms (40,16) 4x12x4 where vanilla has them (chest: outer model, inflated by 1.0);
//   legs (0,16) 4x12x4 (boots: outer model; breeches: inner model 0.5, with the body for the waist band);
//   coat tails (CoatArmorModel, only on the coats' own materials, in the head area no chest piece uses):
//     right back plate 5x7x1 at (0,0), left back plate at (0,8), right side plate 1x7x6 at (12,0), left at (26,0).
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const repo = path.resolve(__dirname, '..');
global.window = global;
// eslint-disable-next-line no-eval
(0, eval)(fs.readFileSync(path.join(repo, 'art/models/entity/seafarer_skins.js'), 'utf8'));
const SF = global.SF;
const OUT = path.join(repo, 'common/src/main/resources/assets/pirates_n_ships/textures/models/armor');

const W = 64, H = 32;

/** A 64x32 RGBA sheet with the seafarer painter's helpers; colours by name from a palette. */
class Sheet {
  constructor(palette, rnd) { this.d = new Uint8ClampedArray(W * H * 4); this.pal = palette; this.rnd = rnd; }
  px(x, y, c) {
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    const k = (y * W + x) * 4;
    if (c === null) { this.d[k + 3] = 0; return; }
    const v = this.pal[c]; if (!v) throw new Error('colour ' + c);
    this.d[k] = v[0]; this.d[k + 1] = v[1]; this.d[k + 2] = v[2]; this.d[k + 3] = 255;
  }
  rect(x, y, w, h, c) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.px(i, j, c); }
  speckle(x, y, w, h, c, p) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) if (this.rnd() < p) this.px(i, j, c); }
}

/** Box-UV faces of a w x h x d cube at (u, v), named as SF.Sheet#faces (right = -x, front = north, back = south). */
const faces = (u, v, w, h, d) => ({top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h],
  front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h]});

// The coat tails' boxes (CoatArmorModel.createLayer): back plates 5x7x1, side plates 1x7x6.
const TAILS = {
  right: {back: faces(0, 0, 5, 7, 1), side: faces(12, 0, 1, 7, 6)},
  left: {back: faces(0, 8, 5, 7, 1), side: faces(26, 0, 1, 7, 6)}
};

/**
 * Paints both tails: cloth on every face, the lining on the faces towards the legs, a hem row along the bottom, an
 * edge column along the split at the back and the front edge of the side plates, a button near the top of each back.
 * o: {cloth, shade, light, lining, hem, edge, button}
 */
function paintTails(s, o) {
  for (const side of ['right', 'left']) {
    const t = TAILS[side];
    for (const box of [t.back, t.side]) {
      for (const f of Object.values(box)) { s.rect(...f, o.cloth); s.speckle(...f, o.shade, 0.15); s.speckle(...f, o.light, 0.05); }
    }
    // linings: the back plate's north face and the side plate's inner face
    const inner = side === 'right' ? t.side.left : t.side.right;
    for (const f of [t.back.front, inner]) { s.rect(...f, o.lining); s.speckle(...f, o.shade, 0.1); }
    // hem along every bottom row of the side faces, and the bottom faces
    for (const box of [t.back, t.side]) {
      for (const k of ['right', 'front', 'left', 'back']) { const f = box[k]; s.rect(f[0], f[1] + f[3] - 1, f[2], 1, o.hem); }
      s.rect(...box.bottom, o.hem);
    }
    // the split at the back: column 0 of the right tail's south face (its +x end), the last column of the left's
    const b = t.back.back, col = side === 'right' ? 0 : b[2] - 1;
    s.rect(b[0] + col, b[1], 1, b[3], o.edge);
    // the side plates' front edge: the column at the north end of the outer face (ModelPart.Cube runs the west face
    // from south at u to north at u + d, the east face from north at u to south)
    const outer = side === 'right' ? t.side.right : t.side.left, front = side === 'right' ? outer[2] - 1 : 0;
    s.rect(outer[0] + front, outer[1], 1, outer[3], o.edge);
    s.rect(...t.side.front, o.edge);
    // a button near the top of each back plate, at the split
    s.px(b[0] + (side === 'right' ? 1 : b[2] - 2), b[1] + 1, o.button);
  }
}

// --- officer's coat (ART6, tails ART9) ------------------------------------------------------------------------------
// Body and arms exactly as ART6's OC.paintArmor (art/models/officers_coat.js; same seed, same order): navy coat open
// over a white waistcoat, white lapels with gold buttons, a gold waist band, a darker back vent, gold epaulettes on the
// arm tops and the shoulder band, red cuffs ringed in gold. Then the tails: navy, white lining, gold hem and edges.
const OC_COL = {
  navy_d: [22, 32, 70], navy: [34, 50, 104], navy_l: [50, 70, 132], white: [236, 232, 220], white_d: [200, 198, 190],
  gold_d: [156, 104, 22], gold: [222, 170, 48], gold_l: [252, 226, 120], red_d: [126, 22, 28], red: [168, 34, 40]
};

function officersCoat() {
  let seed = 0x0ff1ce;
  const rnd = () => { seed = (seed * 1103515245 + 12345) >>> 0; return (seed >>> 8) / 16777216; };
  const s = new Sheet(OC_COL, rnd);
  const b = faces(16, 16, 8, 12, 4);
  for (const k in b) { s.rect(...b[k], 'navy'); s.speckle(...b[k], 'navy_d', 0.15); s.speckle(...b[k], 'navy_l', 0.05); }
  const [fx, fy] = b.front;
  s.rect(fx + 3, fy, 2, 12, 'white'); s.speckle(fx + 3, fy, 2, 12, 'white_d', 0.15);
  s.rect(fx + 3, fy, 2, 1, 'white_d');
  for (let r = 0; r < 11; r++) { s.px(fx + 2, fy + r, 'white'); s.px(fx + 5, fy + r, 'white'); }
  for (const r of [2, 4, 6, 8]) { s.px(fx + 2, fy + r, 'gold_l'); s.px(fx + 5, fy + r, 'gold_l'); }
  for (const r of [3, 6]) s.px(fx + 4, fy + r, 'gold');
  for (const k of ['front', 'right', 'left', 'back']) { const f = b[k]; s.rect(f[0], f[1] + 11, f[2], 1, 'gold'); }
  s.rect(fx + 3, fy + 11, 2, 1, 'white_d');
  const [bx, by] = b.back; s.rect(bx + 3, by + 6, 1, 6, 'navy_d'); s.rect(bx + 4, by + 6, 1, 6, 'navy_d');
  s.px(bx + 2, by + 6, 'gold'); s.px(bx + 5, by + 6, 'gold');
  s.rect(b.top[0], b.top[1] + 3, 8, 1, 'navy_l'); s.rect(b.top[0] + 2, b.top[1] + 3, 4, 1, 'white');
  const a = faces(40, 16, 4, 12, 4);
  for (const k in a) { s.rect(...a[k], 'navy'); s.speckle(...a[k], 'navy_d', 0.15); }
  s.rect(...a.top, 'gold'); s.speckle(...a.top, 'gold_d', 0.3);
  for (const k of ['front', 'right', 'left', 'back']) {
    const f = a[k];
    s.rect(f[0], f[1], f[2], 1, 'gold'); for (let x = f[0]; x < f[0] + f[2]; x += 2) s.px(x, f[1] + 1, 'gold_d');
    s.rect(f[0], f[1] + 8, f[2], 3, 'red'); s.speckle(f[0], f[1] + 8, f[2], 3, 'red_d', 0.2);
    s.rect(f[0], f[1] + 8, f[2], 1, 'gold'); s.rect(f[0], f[1] + 11, f[2], 1, 'red_d');
  }
  s.px(a.front[0] + 1, a.front[1] + 9, 'gold_l'); s.px(a.front[0] + 2, a.front[1] + 9, 'gold_l');
  s.rect(...a.bottom, 'white_d');
  paintTails(s, {cloth: 'navy', shade: 'navy_d', light: 'navy_l', lining: 'white', hem: 'gold', edge: 'gold', button: 'gold_l'});
  return s;
}

// --- the pirate captain's clothing (ART9) ---------------------------------------------------------------------------
// The colours of SF.pirate_captain: charcoal coat (cc), gold edging, brass buttons, gold brocade waistcoat with a white
// jabot, red sash (sash), leather baldric (belt) with a brass buckle, crimson cuffs ringed in gold; dark breeches
// (belt_d over black); boots (boot) with lighter bucket tops (boot_l).
const captainRnd = name => SF.rng('pns:apparel:' + name);

function captainsCoat() {
  const s = new Sheet(SF.C, captainRnd('captains_coat'));
  const b = faces(16, 16, 8, 12, 4);
  for (const f of Object.values(b)) { s.rect(...f, 'cc'); s.speckle(...f, 'cc_d', 0.12); s.speckle(...f, 'cc_l', 0.05); }
  const [fx, fy] = b.front;
  // the open front: brocade waistcoat with gold buttons, a white jabot at the neck, gold edging and brass buttons
  s.rect(fx + 2, fy, 4, 12, 'brocade'); s.speckle(fx + 2, fy, 4, 12, 'brocade_d', 0.3); s.speckle(fx + 2, fy, 4, 12, 'brocade_l', 0.15);
  s.rect(fx + 3, fy, 2, 3, 'white'); s.px(fx + 3, fy + 1, 'shirt_d'); s.px(fx + 4, fy + 2, 'shirt_d');
  for (const r of [4, 6, 10]) s.px(fx + 4, fy + r, 'gold_l');
  for (let r = 0; r < 12; r++) { s.px(fx + 1, fy + r, 'gold_d'); s.px(fx + 6, fy + r, 'gold_d'); }
  for (const r of [2, 4, 6, 10]) { s.px(fx, fy + r, 'brass'); s.px(fx + 7, fy + r, 'brass'); }
  for (const k of ['right', 'left', 'back']) { const f = b[k]; s.rect(f[0], f[1] + 11, f[2], 1, 'gold_d'); }
  // the back vent with two brass buttons
  const [bx, by] = b.back;
  s.rect(bx + 3, by + 5, 1, 7, 'cc_d'); s.rect(bx + 4, by + 5, 1, 7, 'cc_d');
  s.px(bx + 2, by + 5, 'brass'); s.px(bx + 5, by + 5, 'brass');
  // the red sash at the waist (rows 8..9) all round, its knot on the left hip
  for (const k of ['right', 'front', 'left', 'back']) { const f = b[k]; s.rect(f[0], f[1] + 8, f[2], 2, 'sash'); s.speckle(f[0], f[1] + 8, f[2], 2, 'sash_d', 0.3); s.rect(f[0], f[1] + 8, f[2], 1, 'sash_l'); }
  const [lx, ly] = b.left;
  s.rect(lx + 1, ly + 7, 2, 4, 'sash_d'); s.px(lx + 1, ly + 8, 'sash_l'); s.px(lx + 2, ly + 11, 'sash'); s.px(lx + 1, ly + 11, 'sash_d');
  // the baldric over the right shoulder to the left hip: the front from the right shoulder (column 0) down to the
  // left hip (column 7), the back from the right shoulder (column 7 seen from behind) down to the left hip (column 0)
  for (let r = 0; r < 12; r++) {
    const c = Math.round(r * 7 / 11);
    s.px(fx + c, fy + r, 'belt'); if (c + 1 < 8) s.px(fx + c + 1, fy + r, 'belt_d');
    s.px(bx + 7 - c, by + r, 'belt'); if (c + 1 < 8) s.px(bx + 6 - c, by + r, 'belt_d');
  }
  s.px(fx + 4, fy + 6, 'brass'); s.px(fx + 4, fy + 7, 'brass_d');
  s.rect(b.top[0], b.top[1], 2, 4, 'belt');
  // collar along the top's front edge
  s.rect(b.top[0], b.top[1] + 3, 8, 1, 'cc_l'); s.rect(b.top[0] + 3, b.top[1] + 3, 2, 1, 'white');
  // sleeves: charcoal, wide crimson cuffs ringed in gold with a brass button, the shirt at the wrist
  const a = faces(40, 16, 4, 12, 4);
  for (const f of Object.values(a)) { s.rect(...f, 'cc'); s.speckle(...f, 'cc_d', 0.15); }
  for (const k of ['front', 'right', 'left', 'back']) {
    const f = a[k];
    s.rect(f[0], f[1], f[2], 1, 'cc_l');
    // the wide cuff over the forearm's last third: a gold ring on top, crimson, a dark gold edge at the wrist
    s.rect(f[0], f[1] + 8, f[2], 4, 'crimson'); s.speckle(f[0], f[1] + 8, f[2], 4, 'crimson_d', 0.2);
    s.rect(f[0], f[1] + 8, f[2], 1, 'gold'); s.rect(f[0], f[1] + 11, f[2], 1, 'gold_d');
  }
  s.px(a.front[0] + 1, a.front[1] + 10, 'brass'); s.px(a.front[0] + 2, a.front[1] + 10, 'brass');
  s.rect(...a.bottom, 'crimson_d');
  paintTails(s, {cloth: 'cc', shade: 'cc_d', light: 'cc_l', lining: 'crimson_d', hem: 'gold_d', edge: 'gold_d', button: 'brass'});
  return s;
}

/** Layer 1 of captains_clothing: the boots on vanilla's outer model (legs only). */
function captainsBoots() {
  const s = new Sheet(SF.C, captainRnd('captains_boots'));
  const l = faces(0, 16, 4, 12, 4);
  for (const k of ['right', 'front', 'left', 'back']) {
    const f = l[k];
    s.rect(f[0], f[1] + 4, f[2], 8, 'boot'); s.speckle(f[0], f[1] + 4, f[2], 8, 'boot_d', 0.25);
    // the bucket top: a lighter turned-down cuff, its lower edge darker
    s.rect(f[0], f[1] + 4, f[2], 3, 'belt'); s.speckle(f[0], f[1] + 4, f[2], 3, 'boot_l', 0.3); s.rect(f[0], f[1] + 6, f[2], 1, 'belt_d');
    s.rect(f[0], f[1] + 11, f[2], 1, 'boot_d');
  }
  s.px(l.front[0] + 1, l.front[1] + 8, 'boot_l'); s.px(l.front[0] + 2, l.front[1] + 9, 'boot_l');
  // the strap and buckle at the instep
  s.rect(l.front[0], l.front[1] + 9, 4, 1, 'belt_d'); s.px(l.front[0] + 2, l.front[1] + 9, 'brass');
  s.rect(...l.bottom, 'boot_d');
  return s;
}

/** Layer 2 of captains_clothing: the breeches on vanilla's inner model (body for the waist band, legs). */
function captainsBreeches() {
  const s = new Sheet(SF.C, captainRnd('captains_breeches'));
  const l = faces(0, 16, 4, 12, 4);
  for (const k of ['right', 'front', 'left', 'back']) {
    const f = l[k];
    s.rect(f[0], f[1], f[2], 8, 'belt_d'); s.speckle(f[0], f[1], f[2], 8, 'black', 0.2);
    s.rect(f[0], f[1] + 7, f[2], 1, 'black');
    // stockings below the knee
    s.rect(f[0], f[1] + 8, f[2], 4, 'white'); s.speckle(f[0], f[1] + 8, f[2], 4, 'shirt_d', 0.2);
  }
  // brass buttons at the outer knee: the right leg's right face; the left leg mirrors it (vanilla)
  s.px(l.right[0] + 1, l.right[1] + 7, 'brass'); s.px(l.right[0] + 2, l.right[1] + 7, 'brass');
  s.rect(...l.top, 'belt_d'); s.rect(...l.bottom, 'shirt_d');
  // the waist band on the body's lower rows, a brass buckle in front
  const b = faces(16, 16, 8, 12, 4);
  for (const k of ['right', 'front', 'left', 'back']) {
    const f = b[k];
    s.rect(f[0], f[1] + 8, f[2], 4, 'belt_d'); s.speckle(f[0], f[1] + 8, f[2], 4, 'black', 0.2);
    s.rect(f[0], f[1] + 8, f[2], 1, 'belt');
  }
  s.rect(...b.bottom, 'belt_d');
  s.rect(b.front[0] + 3, b.front[1] + 8, 2, 1, 'brass');
  return s;
}

const TEXTURES = {
  officers_coat_layer_1: officersCoat,
  captains_coat_layer_1: captainsCoat,
  captains_clothing_layer_1: captainsBoots,
  captains_clothing_layer_2: captainsBreeches
};

// --- PNG ------------------------------------------------------------------------------------------------------------
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xFFFFFFFF;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encode(rgba, w, h) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    Buffer.from(rgba.buffer, rgba.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

const check = process.argv[2] === '--check';
let failed = false;
fs.mkdirSync(OUT, {recursive: true});
for (const [name, paint] of Object.entries(TEXTURES)) {
  const file = path.join(OUT, name + '.png'), png = encode(paint().d, W, H);
  if (check) {
    const same = fs.existsSync(file) && fs.readFileSync(file).equals(png);
    console.log(name + ': ' + (same ? 'identical' : 'differs'));
    if (!same) failed = true;
  } else {
    fs.writeFileSync(file, png);
    console.log('wrote ' + path.relative(repo, file));
  }
}
process.exit(failed ? 1 : 0);
