(async () => {
const fs = require('fs');
const W = globalThis.CHART_ROOT || (typeof __filename === 'string' && __filename.endsWith('render_chart_held.js') ? require('path').resolve(require('path').dirname(__filename), '..') : null);
if (!W) throw new Error('set globalThis.CHART_ROOT to the repository root before evaluating tools/render_chart_held.js');
const MODELS = W + '/common/src/main/resources/assets/pirates_n_ships/models/item/';
const TEX = W + '/common/src/main/resources/assets/pirates_n_ships/textures/item/';
const OUT = globalThis.CHART_OUT || (W + '/art/renders/chart_held.png');
const D = Math.PI / 180;
const texCache = {};
async function tex(name) {
  if (texCache[name]) return texCache[name];
  const file = TEX + name.split(':')[1].replace('item/', '') + '.png';
  const img = new Image();
  img.src = 'data:image/png;base64,' + fs.readFileSync(file).toString('base64');
  await new Promise(r => { img.onload = r; });
  const t = new THREE.Texture(img); t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.needsUpdate = true;
  texCache[name] = t; return t;
}
const hidden = new THREE.MeshBasicMaterial({visible: false});
async function modelGroup(name) {
  const m = JSON.parse(fs.readFileSync(MODELS + name + '.json', 'utf8'));
  const g = new THREE.Group();
  const order = ['east', 'west', 'up', 'down', 'south', 'north'];
  for (const e of m.elements) {
    const [x0, y0, z0] = e.from, [x1, y1, z1] = e.to;
    const geo = new THREE.BoxGeometry(Math.max(x1 - x0, 0.001), Math.max(y1 - y0, 0.001), Math.max(z1 - z0, 0.001));
    const uvAttr = geo.attributes.uv;
    const mats = [];
    for (let fi = 0; fi < 6; fi++) {
      const f = e.faces[order[fi]];
      if (!f) { mats.push(hidden); continue; }
      const t = await tex(m.textures[f.texture.substring(1)]);
      mats.push(new THREE.MeshLambertMaterial({map: t}));
      const [u1, v1, u2, v2] = f.uv;
      const us = [u1, u2, u1, u2], vs = [v1, v1, v2, v2];
      for (let k = 0; k < 4; k++) uvAttr.setXY(fi * 4 + k, us[k] / 16, 1 - vs[k] / 16);
    }
    uvAttr.needsUpdate = true;
    const mesh = new THREE.Mesh(geo, mats);
    mesh.position.set((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
    if (e.rotation) {
      const o = e.rotation.origin; const piv = new THREE.Group(); piv.position.set(o[0], o[1], o[2]);
      mesh.position.sub(new THREE.Vector3(o[0], o[1], o[2]));
      piv.rotation[e.rotation.axis] = e.rotation.angle * D; piv.add(mesh); g.add(piv);
    } else g.add(mesh);
  }
  return {group: g, json: m};
}
function mat(a) { return a; }
const M4 = () => new THREE.Matrix4();
const T = (x, y, z) => M4().makeTranslation(x, y, z);
const RX = a => M4().makeRotationX(a), RY = a => M4().makeRotationY(a), RZ = a => M4().makeRotationZ(a);
const S3 = (x, y, z) => M4().makeScale(x, y, z);
const mul = (...ms) => ms.reduce((a, b) => a.clone().multiply(b));
const anim = n => JSON.parse(fs.readFileSync(W + '/common/src/main/resources/assets/pirates_n_ships/chart_animations/' + n + '.json', 'utf8')).animations[n];
const READ = anim('chart_read');
function key(bone, t) {
  const rot = READ.bones[bone].rotation; const ks = Object.keys(rot).map(Number);
  let pt = null, pv = null;
  for (const k of Object.keys(rot)) {
    const kt = Number(k), v = rot[k];
    if (kt >= t - 1e-6) { if (pv == null || kt - pt < 1e-6) return v; const f = (t - pt) / (kt - pt); return pv.map((a, i) => a + (v[i] - a) * f); }
    pt = kt; pv = v;
  }
  return pv;
}
const armM = (pivot, deg) => mul(T(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16), RZ(deg[2] * D), RY(deg[1] * D), RX(deg[0] * D));
function itemT(d, left) {
  const tr = d.translation || [0, 0, 0], ro = d.rotation || [0, 0, 0], sc = d.scale || [1, 1, 1];
  const tx = (left ? -tr[0] : tr[0]) / 16;
  const ry = left ? -ro[1] : ro[1], rz = left ? -ro[2] : ro[2];
  return mul(T(tx, tr[1] / 16, tr[2] / 16), RX(ro[0] * D), RY(ry * D), RZ(rz * D), S3(sc[0], sc[1], sc[2]));
}
function box(px0, py0, pz0, sx, sy, sz, color) {
  const geo = new THREE.BoxGeometry(sx / 16, sy / 16, sz / 16);
  const m = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({color}));
  m.position.set((px0 + sx / 2) / 16, (py0 + sy / 2) / 16, (pz0 + sz / 2) / 16);
  return m;
}
function part(matrix, children) {
  const g = new THREE.Group(); g.matrixAutoUpdate = false; g.matrix.copy(matrix); children.forEach(c => g.add(c)); return g;
}
async function player(model, t, left) {
  const root = new THREE.Group();
  const inner = part(S3(-1, -1, 1), []);
  root.add(inner); root.position.y = 1.5;
  const skin = 0xc69a74, shirt = 0x2f6f96, pants = 0x2c2c5c, hair = 0x3a2416;
  inner.add(part(M4(), [box(-4, -8, -4, 8, 8, 8, skin), box(-4.05, -8.05, -4.05, 8.1, 2.2, 8.1, hair)]));
  inner.add(part(M4(), [box(-4, 0, -2, 8, 12, 4, shirt)]));
  inner.add(part(T(-1.9 / 16, 12 / 16, 0), [box(-2, 0, -2, 4, 12, 4, pants)]));
  inner.add(part(T(1.9 / 16, 12 / 16, 0), [box(-2, 0, -2, 4, 12, 4, pants)]));
  const ra = key('right_arm', t), la = key('left_arm', t);
  const rm = armM([-5, 2, 0], ra), lm = armM([5, 2, 0], la);
  inner.add(part(rm, [box(-3, -2, -2, 4, 7, 4, shirt), box(-3, 5, -2, 4, 7, 4, skin)]));
  inner.add(part(lm, [box(-1, -2, -2, 4, 7, 4, shirt), box(-1, 5, -2, 4, 7, 4, skin)]));
  const {group, json} = await modelGroup(model);
  const holder = left ? lm : rm;
  const disp = json.display[left ? 'thirdperson_lefthand' : 'thirdperson_righthand'];
  const im = mul(holder, RX(-90 * D), RY(180 * D), T((left ? -1 : 1) / 16, 0.125, -0.625), itemT(disp, left), T(-0.5, -0.5, -0.5), S3(1 / 16, 1 / 16, 1 / 16));
  inner.add(part(im, [group]));
  return root;
}
const renderer = new THREE.WebGLRenderer({antialias: true, preserveDrawingBuffer: true, alpha: false});
const PW = 360, PH = 360;
renderer.setSize(PW, PH);
const out = document.createElement('canvas'); out.width = PW * 4; out.height = PH * 2;
const ctx = out.getContext('2d');
function light(scene) {
  scene.background = new THREE.Color(0xb8c8d8);
  scene.add(new THREE.AmbientLight(0xffffff, 0.62));
  const d = new THREE.DirectionalLight(0xffffff, 0.55); d.position.set(-0.4, 1, -0.7); scene.add(d);
}
function shot(scene, cam, i, label) {
  renderer.render(scene, cam);
  const x = (i % 4) * PW, y = Math.floor(i / 4) * PH;
  ctx.drawImage(renderer.domElement, x, y);
  ctx.fillStyle = '#111'; ctx.font = '15px sans-serif'; ctx.fillText(label, x + 8, y + 18);
}
const FR = [['chart_unroll_0', 0.182], ['chart_unroll_2', 0.221], ['chart_unroll_4', 0.274], ['chart_unroll_6', 0.328], ['chart_unroll_8', 0.381], ['chart_unroll_10', 0.434], ['chart_open', 0.5]];
const panels = (globalThis.CHART_PANELS || 'items').split(',');
let i = 0;
{ // items
  const s = new THREE.Scene(); light(s);
  const {group} = await modelGroup('chart'); group.position.set(-8, -8, -8); const g = new THREE.Group(); g.add(group); s.add(g);
  const cam = new THREE.PerspectiveCamera(35, 1, 0.1, 500); cam.position.set(0, 0, 60); cam.lookAt(0, 0, 0);
  shot(s, cam, i++, 'closed (item)');
}
for (const [model, t] of FR) {
  const s = new THREE.Scene(); light(s);
  s.add(await player(model, t, false));
  const cam = new THREE.PerspectiveCamera(30, 1, 0.05, 50);
  cam.position.set(1.7, 1.8, -3.6); cam.lookAt(0, 1.05, 0);
  shot(s, cam, i++, model + ' (' + t.toFixed(3) + ' s)');
}
const b64 = out.toDataURL('image/png').split(',')[1];
fs.writeFileSync(OUT, Buffer.from(b64, 'base64'));
renderer.dispose();
renderer.forceContextLoss();
return {written: OUT, panels: i};
})()
