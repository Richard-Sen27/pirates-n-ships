// Skin painter for pirate.bbmodel, sailor.bbmodel, navy_soldier.bbmodel and navy_officer.bbmodel (M3-art).
// Load in Blockbench (risky_eval) before seafarer_models.js:
//   eval(require('fs').readFileSync('<repo>/art/models/entity/seafarer_skins.js', 'utf8'))
// SF.paint(type) returns a 64x64 sheet on the vanilla player skin layout (wide arms, outer layer included). Detail
// cubes of seafarer_models.js read two kinds of areas of the sheet: flat 2x2 colour patches (SF.PATCH_AREA,
// u 56..63, v 16..31, 32 patches in the order of the type's patch list) and painted regions (SF.REGIONS[type]) in the
// free corners of the skin layout. Deterministic: a seeded generator per type, same script, same pixels.
window.SF = window.SF || {};

SF.C = {
  black: [24, 22, 26], white: [236, 232, 220], shirt_d: [196, 192, 180], bone: [220, 210, 180],
  skin_d: [168, 116, 84], skin: [204, 150, 112], skin_l: [226, 178, 140],
  tan_d: [140, 92, 62], tan: [176, 122, 86], tan_l: [196, 142, 104],
  pale_d: [190, 140, 108], pale: [224, 176, 142],
  hair_black: [30, 26, 28], hair_d: [54, 36, 24], hair: [82, 56, 36],
  grey_d: [150, 146, 142], grey: [190, 186, 180], grey_l: [214, 210, 204],
  eye: [44, 70, 120], eye_brown: [74, 50, 30], mouth: [120, 30, 30], scar: [150, 92, 72],
  coat_d: [34, 26, 24], coat: [52, 40, 34], coat_l: [78, 60, 48], coat_ll: [100, 78, 60],
  bandana_d: [110, 20, 26], bandana: [150, 30, 36], bandana_l: [186, 52, 52],
  sash_d: [120, 22, 34], sash: [160, 34, 46], sash_l: [196, 64, 70],
  breeches_d: [118, 96, 68], breeches: [146, 122, 88],
  boot_d: [26, 20, 18], boot: [44, 34, 28], boot_l: [70, 54, 42],
  belt_d: [58, 36, 22], belt: [92, 58, 34],
  brass_d: [156, 104, 22], brass: [222, 170, 48], brass_l: [252, 226, 120],
  steel_d: [92, 98, 110], steel: [150, 156, 168],
  red_stripe: [168, 40, 40], red_stripe_d: [130, 28, 30],
  cap_d: [52, 58, 70], cap: [74, 82, 98], cap_l: [96, 106, 124],
  kerchief_d: [20, 20, 26], kerchief: [40, 40, 50],
  canvas_d: [150, 136, 106], canvas: [182, 168, 136], canvas_l: [204, 192, 162],
  shoe_d: [22, 20, 22], shoe: [38, 34, 36],
  navy_d: [22, 32, 70], navy: [34, 50, 104], navy_l: [50, 70, 132],
  facing_d: [126, 22, 28], facing: [168, 34, 40],
  belt_white: [240, 238, 230], belt_shade: [200, 198, 190],
  hat_d: [16, 14, 18], hat: [32, 30, 36], hat_l: [52, 50, 58],
  gold_d: [156, 104, 22], gold: [222, 170, 48], gold_l: [252, 226, 120],
  cockade: [236, 232, 220], crimson_d: [110, 18, 36], crimson: [150, 28, 50], crimson_l: [184, 56, 74]
};

SF.rng = function (seed) {
  let h = 1779033703 ^ seed.length;
  for (let i = 0; i < seed.length; i++) { h = Math.imul(h ^ seed.charCodeAt(i), 3432918353); h = (h << 13) | (h >>> 19); }
  let a = h >>> 0;
  return function () {
    a = (a + 0x6D2B79F5) >>> 0; let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
};

// Box-UV offsets of the vanilla player skin (wide arms): [u, v, width, height, depth].
SF.HEAD = [0, 0, 8, 8, 8]; SF.HAT = [32, 0, 8, 8, 8];
SF.BODY = [16, 16, 8, 12, 4]; SF.JACKET = [16, 32, 8, 12, 4];
SF.RARM = [40, 16, 4, 12, 4]; SF.RSLEEVE = [40, 32, 4, 12, 4];
SF.LARM = [32, 48, 4, 12, 4]; SF.LSLEEVE = [48, 48, 4, 12, 4];
SF.RLEG = [0, 16, 4, 12, 4]; SF.RPANTS = [0, 32, 4, 12, 4];
SF.LLEG = [16, 48, 4, 12, 4]; SF.LPANTS = [0, 48, 4, 12, 4];
SF.ARMS = [SF.RARM, SF.LARM]; SF.SLEEVES = [SF.RSLEEVE, SF.LSLEEVE];
SF.LEGS = [SF.RLEG, SF.LLEG]; SF.PANTS = [SF.RPANTS, SF.LPANTS];
SF.PATCH_AREA = [56, 16, 8, 16];

SF.Sheet = class {
  constructor(name) { this.d = new Uint8ClampedArray(64 * 64 * 4); this.rnd = SF.rng('pns:seafarer:' + name); }
  px(x, y, c) {
    if (x < 0 || y < 0 || x > 63 || y > 63) return;
    const k = (y * 64 + x) * 4;
    if (c === null) { this.d[k + 3] = 0; return; }
    const v = SF.C[c]; if (!v) throw new Error('colour ' + c);
    this.d[k] = v[0]; this.d[k + 1] = v[1]; this.d[k + 2] = v[2]; this.d[k + 3] = 255;
  }
  rect(x, y, w, h, c) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.px(i, j, c); }
  speckle(x, y, w, h, c, p) { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) if (this.rnd() < p) this.px(i, j, c); }
  faces(b) {
    const [u, v, w, h, d] = b;
    return { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h],
      left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h] };
  }
  sides(b) { const f = this.faces(b); return [f.right, f.front, f.left, f.back]; }
  fill(b, c, shade, p) { for (const r of Object.values(this.faces(b))) { this.rect(...r, c); if (shade) this.speckle(...r, shade, p || 0.12); } }
  band(b, row, rows, c, shade, p) { for (const r of this.sides(b)) { this.rect(r[0], r[1] + row, r[2], rows, c); if (shade) this.speckle(r[0], r[1] + row, r[2], rows, shade, p || 0.12); } }
  // a vertical seam line (one column) on every side face at column col (from that face's left edge)
  column(r, col, row, rows, c) { for (let j = 0; j < rows; j++) this.px(r[0] + col, r[1] + row + j, c); }
  // a region filled with c, optional speckle, then the top row in edge (for brims and flaps)
  region(r, c, shade, p, edge) { this.rect(...r, c); if (shade) this.speckle(...r, shade, p || 0.15); if (edge) this.rect(r[0], r[1], r[2], 1, edge); }
  patches(list) {
    list.forEach((c, i) => this.rect(56 + 2 * (i % 4), 16 + 2 * Math.floor(i / 4), 2, 2, c));
  }
  canvas() {
    const cv = document.createElement('canvas'); cv.width = 64; cv.height = 64;
    const ctx = cv.getContext('2d'); ctx.putImageData(new ImageData(this.d, 64, 64), 0, 0); return cv;
  }
};

// --- shared parts -----------------------------------------------------------------------------------------------

SF.head = function (s, o) {
  s.fill(SF.HEAD, o.skin, o.skin_d, 0.05);
  const f = s.faces(SF.HEAD);
  s.rect(...f.top, o.hair); s.speckle(...f.top, o.hair_d, 0.3);
  s.band(SF.HEAD, 0, o.hairRows || 2, o.hair, o.hair_d, 0.3);
  const bk = f.back; s.rect(bk[0], bk[1], bk[2], o.backRows || 5, o.hair); s.speckle(bk[0], bk[1], bk[2], o.backRows || 5, o.hair_d, 0.3);
  for (const side of ['right', 'left']) { const r = f[side]; s.rect(r[0] + (side === 'right' ? 0 : 1), r[1] + 2, 3, 2, o.hair); s.px(r[0] + 4 + (side === 'right' ? 0 : -1), r[1] + 4, o.skin_d); }
  const [fx, fy] = f.front;
  s.rect(fx + 1, fy + 3, 2, 1, o.brow || o.hair_d); s.rect(fx + 5, fy + 3, 2, 1, o.brow || o.hair_d);
  s.px(fx + 1, fy + 4, 'white'); s.px(fx + 2, fy + 4, o.eye || 'eye');
  s.px(fx + 5, fy + 4, o.eye || 'eye'); s.px(fx + 6, fy + 4, 'white');
  s.rect(fx + 3, fy + 4, 2, 2, o.skin_l || o.skin); s.rect(fx + 3, fy + 5, 2, 1, o.skin_d);
  s.rect(fx + 3, fy + 6, 2, 1, 'mouth');
  s.px(fx + 1, fy + 5, o.skin_d); s.px(fx + 6, fy + 5, o.skin_d);
  return f;
};

SF.hands = function (s, skin, skin_d) {
  for (const a of SF.ARMS) { s.band(a, 10, 2, skin, skin_d, 0.1); s.rect(...s.faces(a).bottom, skin); }
};

// --- pirate -----------------------------------------------------------------------------------------------------
// Tanned, black hair, stubble, a scar, a black eyepatch over the right eye with its strap around the head, a dark red
// bandana with bone-white dots (hat layer; knot and tails are cubes), a long dark coat (jacket and sleeve layers,
// open front over a white shirt; tails, cuffs and lapels are cubes), red sash and belt (cubes), breeches, boots.
SF.PIRATE_PATCHES = ['bandana', 'bandana_d', 'coat', 'coat_d', 'coat_l', 'coat_ll', 'sash', 'sash_d', 'belt', 'belt_d',
  'brass', 'brass_d', 'boot', 'boot_d', 'boot_l', 'black', 'white', 'shirt_d', 'gold', 'hair_black', 'sash_l', 'steel'];
SF.REGIONS = {};
SF.REGIONS.pirate = {
  tail_back: [56, 32, 4, 9], tail_side: [60, 32, 4, 9], tail_in: [56, 41, 8, 2],
  cuff: [36, 16, 8, 3], boot_cuff: [12, 16, 8, 3], lapel: [0, 16, 4, 4]
};
SF.pirate = function () {
  const s = new SF.Sheet('pirate'), R = SF.REGIONS.pirate;
  const f = SF.head(s, {skin: 'tan', skin_d: 'tan_d', skin_l: 'tan_l', hair: 'hair_black', hair_d: 'black', eye: 'eye_brown', brow: 'black'});
  const [fx, fy] = f.front;
  s.rect(fx, fy + 4, 8, 4, 'tan'); s.speckle(fx, fy + 5, 8, 3, 'tan_d', 0.25);
  s.rect(fx + 3, fy + 4, 2, 1, 'tan_l'); s.rect(fx + 3, fy + 5, 2, 1, 'tan_d');
  s.px(fx + 5, fy + 4, 'eye_brown'); s.px(fx + 6, fy + 4, 'white');
  s.rect(fx + 5, fy + 3, 2, 1, 'black');
  s.rect(fx + 2, fy + 6, 4, 1, 'hair_black'); s.px(fx + 1, fy + 6, 'hair_d'); s.px(fx + 6, fy + 6, 'hair_d');
  s.rect(fx + 2, fy + 7, 4, 1, 'hair_d'); s.rect(fx + 3, fy + 7, 2, 1, 'mouth');
  s.speckle(fx, fy + 5, 2, 3, 'hair_d', 0.5); s.speckle(fx + 6, fy + 5, 2, 3, 'hair_d', 0.5);
  for (const side of ['right', 'left']) s.speckle(f[side][0], f[side][1] + 5, 4, 3, 'hair_d', 0.4);
  s.px(fx + 7, fy + 4, 'scar'); s.px(fx + 6, fy + 5, 'scar');
  s.rect(fx + 1, fy + 3, 2, 3, 'black'); s.px(fx, fy + 3, 'black'); s.px(fx + 3, fy + 3, 'black');
  for (const side of ['right', 'left', 'back']) { const r = f[side]; s.rect(r[0], r[1] + 3, r[2], 1, 'black'); }
  // bandana on the hat layer
  const h = s.faces(SF.HAT);
  s.rect(...h.top, 'bandana'); s.speckle(...h.top, 'bandana_d', 0.25);
  s.band(SF.HAT, 0, 3, 'bandana', 'bandana_d', 0.2);
  for (const r of s.sides(SF.HAT)) { s.rect(r[0], r[1] + 2, r[2], 1, 'bandana_d'); for (let x = r[0] + 1; x < r[0] + r[2]; x += 3) s.px(x, r[1] + 1, 'bone'); }
  for (let i = 0; i < 6; i++) s.px(h.top[0] + 1 + ((i * 5) % 7), h.top[1] + 1 + i, 'bone');
  // shirt under the coat
  s.fill(SF.BODY, 'white', 'shirt_d', 0.12);
  const b = s.faces(SF.BODY), [bx, by] = b.front;
  s.rect(bx + 3, by, 2, 3, 'tan'); s.px(bx + 3, by + 3, 'tan_d'); s.px(bx + 4, by + 3, 'tan');
  s.px(bx + 2, by + 4, 'shirt_d'); s.px(bx + 5, by + 4, 'shirt_d');
  s.band(SF.BODY, 7, 5, 'breeches', 'breeches_d', 0.2);
  // coat on the jacket layer: open front, lapels and gold buttons
  s.fill(SF.JACKET, 'coat', 'coat_d', 0.1); for (const r of Object.values(s.faces(SF.JACKET))) s.speckle(...r, 'coat_l', 0.04);
  const j = s.faces(SF.JACKET), [jx, jy] = j.front;
  s.rect(...j.bottom, null);
  s.rect(jx + 2, jy, 4, 12, null);
  for (let r = 0; r < 12; r++) { s.px(jx + 1, jy + r, 'coat_l'); s.px(jx + 6, jy + r, 'coat_l'); }
  for (const r of [2, 5, 8]) { s.px(jx, jy + r, 'brass'); s.px(jx + 7, jy + r, 'brass'); }
  s.column(j.back, 3, 6, 6, 'coat_d'); s.column(j.back, 4, 6, 6, 'coat_d');
  s.px(j.back[0] + 2, j.back[1] + 6, 'brass'); s.px(j.back[0] + 5, j.back[1] + 6, 'brass');
  // sleeves: shirt under coat sleeves, hands
  for (const a of SF.ARMS) s.fill(a, 'white', 'shirt_d', 0.1);
  SF.hands(s, 'tan', 'tan_d');
  for (const sl of SF.SLEEVES) { s.fill(sl, 'coat', 'coat_d', 0.18); s.band(sl, 9, 3, null); s.rect(...s.faces(sl).bottom, null); s.band(sl, 0, 1, 'coat_l'); }
  // legs: breeches and boots
  for (const l of SF.LEGS) {
    s.fill(l, 'breeches', 'breeches_d', 0.2); s.band(l, 6, 6, 'boot', 'boot_d', 0.25); s.band(l, 11, 1, 'boot_d');
    s.rect(...s.faces(l).bottom, 'boot_d'); s.column(s.faces(l).front, 1, 7, 4, 'boot_l');
  }
  // regions
  s.region(R.tail_back, 'coat', 'coat_d', 0.2); s.rect(R.tail_back[0], R.tail_back[1] + 8, 4, 1, 'coat_ll');
  s.px(R.tail_back[0] + 1, R.tail_back[1] + 1, 'brass');
  s.region(R.tail_side, 'coat', 'coat_d', 0.2); s.rect(R.tail_side[0], R.tail_side[1] + 8, 4, 1, 'coat_ll');
  s.region(R.tail_in, 'coat_d', 'black', 0.2);
  s.region(R.cuff, 'coat_l', 'coat', 0.15, 'coat_ll'); s.px(R.cuff[0] + 2, R.cuff[1] + 1, 'brass'); s.px(R.cuff[0] + 6, R.cuff[1] + 1, 'brass');
  s.region(R.boot_cuff, 'boot_l', 'boot', 0.25); s.rect(R.boot_cuff[0], R.boot_cuff[1] + 2, 8, 1, 'boot');
  s.region(R.lapel, 'coat_l', 'coat', 0.1); s.rect(R.lapel[0], R.lapel[1], 1, 4, 'coat_ll');
  s.patches(SF.PIRATE_PATCHES);
  return s;
};

// --- sailor -----------------------------------------------------------------------------------------------------
// Fair, brown hair and a short beard; a grey-blue knitted cap (cubes on `hat`, ribbed), a red and white striped shirt,
// a black neckerchief and a rope belt (cubes), wide canvas slops to mid-calf with rolled hems, black shoes with
// brass buckles (cubes).
SF.SAILOR_PATCHES = ['kerchief', 'kerchief_d', 'canvas', 'canvas_d', 'canvas_l', 'tan', 'bone', 'shoe', 'shoe_d',
  'brass', 'brass_d', 'cap', 'cap_d', 'skin', 'skin_d', 'white', 'red_stripe'];
SF.REGIONS.sailor = {
  cap_top: [32, 0, 8, 8], cap_brim: [24, 0, 8, 3], cap_crown: [24, 3, 8, 3], cap_peak: [24, 6, 8, 2],
  slops: [56, 32, 8, 3]
};
SF.sailor = function () {
  const s = new SF.Sheet('sailor'), R = SF.REGIONS.sailor;
  const f = SF.head(s, {skin: 'skin', skin_d: 'skin_d', skin_l: 'skin_l', hair: 'hair', hair_d: 'hair_d', hairRows: 3});
  const [fx, fy] = f.front;
  s.rect(fx + 1, fy + 6, 6, 2, 'hair'); s.speckle(fx + 1, fy + 6, 6, 2, 'hair_d', 0.35); s.px(fx, fy + 6, 'hair'); s.px(fx + 7, fy + 6, 'hair');
  s.rect(fx + 3, fy + 6, 2, 1, 'mouth'); s.px(fx + 2, fy + 5, 'hair'); s.px(fx + 5, fy + 5, 'hair');
  for (const side of ['right', 'left']) s.speckle(f[side][0], f[side][1] + 4, 4, 4, 'hair', 0.5);
  // shirt: red and white stripes, open collar
  s.fill(SF.BODY, 'white', 'shirt_d', 0.08);
  for (let r = 1; r < 11; r += 2) s.band(SF.BODY, r, 1, 'red_stripe', 'red_stripe_d', 0.15);
  const [bx, by] = s.faces(SF.BODY).front;
  s.rect(bx + 3, by, 2, 2, 'skin'); s.px(bx + 3, by + 2, 'skin_d');
  s.band(SF.BODY, 11, 1, 'canvas', 'canvas_d', 0.3);
  for (const a of SF.ARMS) {
    s.fill(a, 'skin', 'skin_d', 0.06); s.rect(...s.faces(a).top, 'white');
    s.band(a, 0, 6, 'white', 'shirt_d', 0.08); s.band(a, 1, 1, 'red_stripe'); s.band(a, 3, 1, 'red_stripe'); s.band(a, 5, 1, 'shirt_d');
  }
  for (const a of SF.ARMS) { const fr = s.faces(a).right; s.speckle(fr[0], fr[1] + 6, 4, 4, 'hair', 0.15); }
  // slops: wide canvas trousers to mid-calf; the trousers layer flares them at the hem; shoes
  for (const l of SF.LEGS) {
    s.fill(l, 'canvas', 'canvas_d', 0.18); s.band(l, 8, 3, 'skin', 'skin_d', 0.08); s.band(l, 11, 1, 'shoe');
    s.rect(...s.faces(l).bottom, 'shoe_d');
    s.column(s.faces(l).front, 2, 1, 6, 'canvas_d');
  }
  for (const p of SF.PANTS) { s.band(p, 4, 4, 'canvas', 'canvas_d', 0.2); s.band(p, 7, 1, 'canvas_d'); }
  // knitted cap regions: ribs are alternating columns
  const knit = (r, rib) => { s.region(r, 'cap', 'cap_d', 0.15); for (let x = r[0]; x < r[0] + r[2]; x += rib) s.rect(x, r[1], 1, r[3], 'cap_d'); };
  knit(R.cap_brim, 2); s.rect(R.cap_brim[0], R.cap_brim[1], 8, 1, 'cap_l');
  knit(R.cap_crown, 3); s.region(R.cap_top, 'cap', 'cap_d', 0.25);
  for (let i = 0; i < 4; i++) { s.rect(R.cap_top[0] + i, R.cap_top[1] + i, 8 - 2 * i, 1, 'cap_d'); }
  s.region(R.cap_peak, 'cap_d', 'cap', 0.3);
  s.region(R.slops, 'canvas_l', 'canvas', 0.2); s.rect(R.slops[0], R.slops[1] + 2, 8, 1, 'canvas_d');
  s.patches(SF.SAILOR_PATCHES);
  return s;
};

// --- navy -------------------------------------------------------------------------------------------------------
SF.navyCoat = function (s, trim, trim_d) {
  s.fill(SF.BODY, 'white', 'belt_shade', 0.12);
  const b = s.faces(SF.BODY), [bx, by] = b.front;
  for (let r = 1; r < 9; r += 2) s.px(bx + 4, by + r, 'gold');
  s.band(SF.BODY, 9, 3, 'white', 'belt_shade', 0.2);
  // coat on the base body sides and back too (the jacket layer adds the open front edges)
  s.rect(...b.right, 'navy'); s.rect(...b.left, 'navy'); s.rect(...b.back, 'navy'); s.rect(...b.top, 'navy');
  for (const k of ['right', 'left', 'back']) s.speckle(...b[k], 'navy_d', 0.15);
  s.fill(SF.JACKET, 'navy', 'navy_d', 0.15);
  const j = s.faces(SF.JACKET), [jx, jy] = j.front;
  s.rect(...j.bottom, null); s.rect(...j.top, null);
  s.rect(jx + 2, jy, 4, 12, null);
  for (let r = 0; r < 12; r++) { s.px(jx + 1, jy + r, r < 6 ? trim : 'navy_l'); }
  for (let r = 0; r < 12; r++) { s.px(jx + 6, jy + r, r < 6 ? trim : 'navy_l'); }
  for (const r of [1, 3, 5]) { s.px(jx, jy + r, 'gold'); s.px(jx + 7, jy + r, 'gold'); }
  for (const a of SF.ARMS) { s.fill(a, 'navy', 'navy_d', 0.12); s.band(a, 0, 1, 'navy_l'); }
  SF.hands(s, 'pale', 'pale_d');
  for (const l of SF.LEGS) { s.fill(l, 'white', 'belt_shade', 0.1); s.rect(...s.faces(l).top, 'white'); }
};

// Navy soldier: blue coat with red facings over a white waistcoat, white cross belts with a brass plate (cube),
// red cuffs and turnbacks (cubes), white breeches, black gaiters and shoes, powdered queue, black tricorn (cubes)
// edged in white with a black cockade.
SF.NAVY_SOLDIER_PATCHES = ['navy', 'navy_d', 'facing', 'facing_d', 'belt_white', 'belt_shade', 'brass', 'brass_d',
  'shoe', 'shoe_d', 'hat', 'hat_d', 'white', 'black', 'grey', 'grey_d', 'steel'];
SF.REGIONS.navy_soldier = {
  brim_out: [0, 0, 8, 4], brim_in: [0, 4, 8, 4], crown: [24, 0, 8, 3], crown_top: [32, 0, 8, 8],
  tail_back: [56, 32, 4, 7], tail_side: [60, 32, 4, 7], cuff: [36, 16, 8, 3], collar: [12, 16, 8, 2],
  gaiter: [24, 3, 8, 3]
};
SF.navy_soldier = function () {
  const s = new SF.Sheet('navy_soldier'), R = SF.REGIONS.navy_soldier;
  const f = SF.head(s, {skin: 'pale', skin_d: 'pale_d', skin_l: 'skin_l', hair: 'grey', hair_d: 'grey_d', brow: 'grey_d', hairRows: 2, backRows: 6});
  const [bkx, bky] = f.back; s.rect(bkx + 3, bky + 5, 2, 3, 'black');
  for (const side of ['right', 'left']) { const r = f[side]; s.px(r[0] + (side === 'right' ? 1 : 2), r[1] + 3, 'grey_l'); s.px(r[0] + (side === 'right' ? 1 : 2), r[1] + 4, 'grey'); }
  SF.navyCoat(s, 'facing', 'facing_d');
  // cross belts on the jacket layer front and back (over the waistcoat gap, too)
  for (const k of ['front', 'back']) {
    const [x, y] = s.faces(SF.JACKET)[k];
    for (let i = 0; i < 8; i++) { s.px(x + i, y + 1 + i, 'belt_white'); s.px(x + 7 - i, y + 1 + i, 'belt_white'); }
    for (let i = 0; i < 8; i += 3) { s.px(x + i, y + 2 + i, 'belt_shade'); }
  }
  for (const k of ['right', 'left']) { const r = s.faces(SF.JACKET)[k]; s.px(r[0] + 1, r[1] + 9, 'belt_white'); s.px(r[0] + 2, r[1] + 9, 'belt_white'); }
  // gaiters: black, buttoned on the outside, over the trousers layer; shoes on the legs
  for (const l of SF.LEGS) { s.band(l, 10, 2, 'shoe', 'shoe_d', 0.2); s.rect(...s.faces(l).bottom, 'shoe_d'); }
  for (const p of SF.PANTS) { s.band(p, 5, 6, 'shoe', 'shoe_d', 0.15); s.band(p, 5, 1, 'shoe_d'); s.rect(...s.faces(p).bottom, null); s.rect(...s.faces(p).top, null); }
  s.column(s.faces(SF.RPANTS).right, 1, 6, 5, 'steel'); s.column(s.faces(SF.LPANTS).left, 2, 6, 5, 'steel');
  // tricorn regions
  s.region(R.brim_out, 'hat', 'hat_d', 0.2, 'belt_white'); s.region(R.brim_in, 'hat_d', 'hat', 0.2, 'belt_white');
  s.region(R.crown, 'hat', 'hat_d', 0.2); s.region(R.crown_top, 'hat', 'hat_d', 0.25);
  s.region(R.tail_back, 'navy', 'navy_d', 0.15); s.rect(R.tail_back[0] + 3, R.tail_back[1], 1, 7, 'facing');
  s.region(R.tail_side, 'navy', 'navy_d', 0.15); s.rect(R.tail_side[0], R.tail_side[1], 1, 7, 'facing');
  s.region(R.cuff, 'facing', 'facing_d', 0.15, 'facing_d'); s.px(R.cuff[0] + 1, R.cuff[1] + 1, 'white'); s.px(R.cuff[0] + 5, R.cuff[1] + 1, 'white');
  s.region(R.collar, 'facing', 'facing_d', 0.15);
  s.region(R.gaiter, 'shoe', 'shoe_d', 0.2);
  s.patches(SF.NAVY_SOLDIER_PATCHES);
  return s;
};

// Navy officer: the navy coat with gold lace and buttons, gold epaulettes with fringe (cubes on the arms), blue cuffs
// with gold rings (cubes), a crimson waist sash with tasselled ends (cubes), white breeches, tall black boots with
// turned-down tops (cubes), powdered hair, a black bicorne athwart (cubes) edged in gold with a gold loop.
SF.NAVY_OFFICER_PATCHES = ['navy', 'navy_d', 'gold', 'gold_d', 'gold_l', 'crimson', 'crimson_d', 'crimson_l', 'hat',
  'hat_d', 'boot', 'boot_d', 'boot_l', 'white', 'black', 'belt_shade', 'cockade'];
SF.REGIONS.navy_officer = {
  flap: [0, 0, 8, 5], flap_in: [0, 5, 8, 3], crown: [24, 0, 8, 3], crown_top: [32, 0, 8, 8],
  fringe: [36, 16, 8, 2], epaulette: [12, 16, 8, 4], tail_back: [56, 32, 4, 8], tail_side: [60, 32, 4, 8],
  cuff: [24, 3, 8, 3], sash: [56, 40, 8, 2], boot_top: [56, 42, 8, 2]
};
SF.navy_officer = function () {
  const s = new SF.Sheet('navy_officer'), R = SF.REGIONS.navy_officer;
  const f = SF.head(s, {skin: 'skin_l', skin_d: 'skin', skin_l: 'skin_l', hair: 'grey_l', hair_d: 'grey', brow: 'grey_d', hairRows: 2, backRows: 6});
  const [bkx, bky] = f.back; s.rect(bkx + 3, bky + 5, 2, 3, 'black');
  const [fx, fy] = f.front; s.px(fx + 2, fy + 6, 'skin'); s.px(fx + 5, fy + 6, 'skin');
  for (const side of ['right', 'left']) { const r = f[side]; s.px(r[0] + (side === 'right' ? 1 : 2), r[1] + 3, 'white'); s.px(r[0] + (side === 'right' ? 1 : 2), r[1] + 4, 'grey_l'); }
  SF.navyCoat(s, 'gold', 'gold_d');
  const j = s.faces(SF.JACKET);
  for (const k of ['right', 'left']) { const r = j[k]; s.rect(r[0], r[1] + 11, r[2], 1, 'gold_d'); }
  s.rect(j.back[0], j.back[1] + 11, 8, 1, 'gold_d');
  for (const r of [7, 9, 11]) { s.px(j.front[0], j.front[1] + r, 'gold'); s.px(j.front[0] + 7, j.front[1] + r, 'gold'); }
  for (const a of SF.ARMS) { const fr = s.faces(a); s.rect(fr.front[0], fr.front[1] + 7, 4, 1, 'gold'); }
  for (const l of SF.LEGS) { s.band(l, 5, 7, 'boot', 'boot_d', 0.2); s.column(s.faces(l).front, 1, 6, 5, 'boot_l'); s.rect(...s.faces(l).bottom, 'boot_d'); }
  s.region(R.flap, 'hat', 'hat_d', 0.2, 'gold'); s.rect(R.flap[0], R.flap[1] + 1, 8, 1, 'gold_d');
  s.region(R.flap_in, 'hat_d', 'hat', 0.2, 'gold');
  s.region(R.crown, 'hat', 'hat_d', 0.2); s.region(R.crown_top, 'hat', 'hat_d', 0.25);
  s.region(R.fringe, 'gold', null, 0); for (let x = 0; x < 8; x += 2) s.rect(R.fringe[0] + x, R.fringe[1], 1, 2, 'gold_d');
  s.region(R.epaulette, 'gold', 'gold_d', 0.2, 'gold_l');
  s.region(R.tail_back, 'navy', 'navy_d', 0.15); s.rect(R.tail_back[0] + 3, R.tail_back[1], 1, 8, 'gold'); s.rect(R.tail_back[0], R.tail_back[1] + 7, 4, 1, 'gold');
  s.region(R.tail_side, 'navy', 'navy_d', 0.15); s.rect(R.tail_side[0], R.tail_side[1], 1, 8, 'gold'); s.rect(R.tail_side[0], R.tail_side[1] + 7, 4, 1, 'gold');
  s.region(R.cuff, 'navy', 'navy_d', 0.15, 'gold'); s.rect(R.cuff[0], R.cuff[1] + 2, 8, 1, 'gold'); s.px(R.cuff[0] + 3, R.cuff[1] + 1, 'gold_l');
  s.region(R.sash, 'crimson', 'crimson_d', 0.3); s.rect(R.sash[0], R.sash[1], 8, 1, 'crimson_l');
  s.region(R.boot_top, 'boot_l', 'boot', 0.3);
  s.patches(SF.NAVY_OFFICER_PATCHES);
  return s;
};

SF.TYPES = ['pirate', 'sailor', 'navy_soldier', 'navy_officer'];
SF.PATCHES = {pirate: SF.PIRATE_PATCHES, sailor: SF.SAILOR_PATCHES, navy_soldier: SF.NAVY_SOLDIER_PATCHES, navy_officer: SF.NAVY_OFFICER_PATCHES};
SF.paint = type => SF[type]();
