#!/usr/bin/env node
// Paints seafarer skins outside Blockbench (PRT1a): loads art/models/entity/seafarer_skins.js, runs SF.paint(type) and
// writes common/src/main/resources/assets/pirates_n_ships/textures/entity/<type>.png (64x64 RGBA). Meant for the texture
// variants that have no model of their own (SF.VARIANTS, e.g. harbor_master on the sailor's model); the four modelled
// types are still exported from Blockbench with their model (seafarer_models.js). Deterministic: same script, same bytes.
//
//   node tools/paint_seafarer_skins.js harbor_master          # write these textures
//   node tools/paint_seafarer_skins.js --check sailor pirate  # compare the painter with the committed PNGs (pixels)
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const repo = path.resolve(__dirname, '..');
global.window = global;
// eslint-disable-next-line no-eval
(0, eval)(fs.readFileSync(path.join(repo, 'art/models/entity/seafarer_skins.js'), 'utf8'));
const SF = global.SF;

const texture = type => path.join(repo, 'common/src/main/resources/assets/pirates_n_ships/textures/entity', type + '.png');

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

/** RGBA pixels (64x64) to PNG bytes: filter 0 on every row, zlib level 9. */
function encode(rgba, w, h) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    Buffer.from(rgba.buffer, rgba.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
}

/** PNG bytes (8-bit RGBA or RGB, not interlaced) to RGBA pixels. */
function decode(png) {
  let pos = 8, w = 0, h = 0, colour = 6;
  const idat = [];
  while (pos < png.length) {
    const len = png.readUInt32BE(pos), type = png.toString('ascii', pos + 4, pos + 8), data = png.subarray(pos + 8, pos + 8 + len);
    if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); colour = data[9]; if (data[8] !== 8 || data[12] !== 0) throw new Error('unsupported PNG'); }
    if (type === 'IDAT') idat.push(data);
    pos += 12 + len;
  }
  const bpp = colour === 6 ? 4 : colour === 2 ? 3 : 0;
  if (!bpp) throw new Error('unsupported PNG colour type ' + colour);
  const raw = zlib.inflateSync(Buffer.concat(idat)), stride = w * bpp, out = new Uint8ClampedArray(w * h * 4);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)], line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? line[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
      const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
      const pred = f === 0 ? 0 : f === 1 ? a : f === 2 ? b : f === 3 ? (a + b) >> 1 : (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
      line[i] = (line[i] + pred) & 0xFF;
    }
    for (let x = 0; x < w; x++) {
      for (let k = 0; k < 4; k++) out[(y * w + x) * 4 + k] = k < bpp ? line[x * bpp + k] : 255;
    }
    prev = line;
  }
  return out;
}

const args = process.argv.slice(2);
const check = args[0] === '--check';
const types = check ? args.slice(1) : args;
if (!types.length) {
  console.error('usage: node tools/paint_seafarer_skins.js [--check] <type>...');
  process.exit(2);
}
let failed = false;
for (const type of types) {
  const sheet = SF.paint(type);
  if (check) {
    const committed = decode(fs.readFileSync(texture(type)));
    let diff = 0;
    for (let i = 0; i < committed.length; i += 4) {
      const ca = committed[i + 3], pa = sheet.d[i + 3];
      if (ca === 0 && pa === 0) continue;
      if (ca !== pa || committed[i] !== sheet.d[i] || committed[i + 1] !== sheet.d[i + 1] || committed[i + 2] !== sheet.d[i + 2]) diff++;
    }
    console.log(type + ': ' + (diff ? diff + ' pixels differ' : 'identical'));
    if (diff) failed = true;
  } else {
    fs.writeFileSync(texture(type), encode(sheet.d, 64, 64));
    console.log('wrote ' + path.relative(repo, texture(type)));
  }
}
process.exit(failed ? 1 : 0);
