// Check renders of the worn apparel (ART9): a player figure (the crew member's skin on vanilla's wide-arm player model)
// wearing armour layers exactly as vanilla's HumanoidArmorLayer and our CoatArmorModel build them (box UV, inflation,
// mirrored left limbs, ModelPart's translate-then-ZYX rotation), the captain's hat through CustomHeadLayer's head
// transform and the item's `head` display, and the hand-made item models. Not the game: lighting is a plain
// ambient + directional pair. Run in Blockbench (risky_eval, needs THREE and Node's fs); nothing touches a project:
//   eval(require('fs').readFileSync('<repo>/art/models/entity/apparel_render.js', 'utf8'))
//   await AR.renderAll('<repo>')    writes art/renders/coat_tails.png, captains_set_worn.png, captains_clothing.png
window.AR = window.AR || {};

AR.ASSETS = '/common/src/main/resources/assets/pirates_n_ships/';

AR.loadTexture = function (file) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const t = new THREE.Texture(img);
      t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.generateMipmaps = false;
      t.needsUpdate = true; t.image_w = img.width; t.image_h = img.height;
      const cv = document.createElement('canvas'); cv.width = img.width; cv.height = img.height;
      cv.getContext('2d').drawImage(img, 0, 0); t.pixels = cv.getContext('2d').getImageData(0, 0, img.width, img.height).data;
      resolve(t);
    };
    img.onerror = reject;
    img.src = 'data:image/png;base64,' + require('fs').readFileSync(file).toString('base64');
  });
};

// --- model parts (vanilla ModelPart semantics, units of pixels; y down, front = -z) ---------------------------------
// cube: {o: [x, y, z], s: [w, h, d], uv: [u, v], g: grow, mirror}
AR.part = (pivot, cubes, children) => ({pivot, rot: [0, 0, 0], cubes, children: children || {}});

/** Vanilla box UV: the six quads of a cube with their texture corners (ModelPart.Cube). */
AR.boxQuads = function (c) {
  const g = c.g || 0;
  let x0 = c.o[0] - g, x1 = c.o[0] + c.s[0] + g;
  const y0 = c.o[1] - g, y1 = c.o[1] + c.s[1] + g, z0 = c.o[2] - g, z1 = c.o[2] + c.s[2] + g;
  if (c.mirror) [x0, x1] = [x1, x0];
  const V = {v7: [x0, y0, z0], v: [x1, y0, z0], v1: [x1, y1, z0], v2: [x0, y1, z0], v3: [x0, y0, z1], v4: [x1, y0, z1], v5: [x1, y1, z1], v6: [x0, y1, z1]};
  const [u, v] = c.uv, [w, h, d] = c.s;
  const f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
  const f10 = v, f11 = v + d, f12 = v + d + h;
  const poly = (vs, u1, v1, u2, v2) => {
    let uvs = [[u2, v1], [u1, v1], [u1, v2], [u2, v2]];
    if (c.mirror) uvs = [[u1, v1], [u2, v1], [u2, v2], [u1, v2]];
    return {p: vs.map(k => V[k]), uv: uvs};
  };
  return [
    poly(['v4', 'v3', 'v7', 'v'], f5, f10, f6, f11), poly(['v1', 'v2', 'v6', 'v5'], f6, f11, f7, f10),
    poly(['v7', 'v3', 'v6', 'v2'], f4, f11, f5, f12), poly(['v', 'v7', 'v2', 'v1'], f5, f11, f6, f12),
    poly(['v4', 'v', 'v1', 'v5'], f6, f11, f8, f12), poly(['v3', 'v4', 'v5', 'v6'], f8, f11, f9, f12)
  ];
};

/** ModelPart#translateAndRotate: translate by the pivot, then rotate Z, Y, X (Quaternionf.rotationZYX). */
AR.partMatrix = function (p) {
  const m = new THREE.Matrix4().makeTranslation(p.pivot[0], p.pivot[1], p.pivot[2]);
  const r = new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(p.rot[0], p.rot[1], p.rot[2], 'ZYX'));
  return m.multiply(r);
};

/** Adds the meshes of a part tree drawn with one texture to group, under matrix parent (model space). */
AR.addParts = function (group, parts, tex, parent) {
  for (const p of Object.values(parts)) {
    if (p.hidden) continue;
    const m = parent.clone().multiply(AR.partMatrix(p));
    for (const c of p.cubes) {
      const pos = [], uvs = [];
      for (const q of AR.boxQuads(c)) {
        for (const i of [0, 1, 2, 0, 2, 3]) {
          const pt = new THREE.Vector3(...q.p[i]).applyMatrix4(m);
          pos.push(pt.x, pt.y, pt.z); uvs.push(q.uv[i][0] / tex.tw, 1 - q.uv[i][1] / tex.th);
        }
      }
      const geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
      geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
      geo.computeVertexNormals();
      group.add(new THREE.Mesh(geo, new THREE.MeshLambertMaterial({map: tex.t, side: THREE.DoubleSide, alphaTest: 0.1, transparent: false})));
    }
    AR.addParts(group, p.children, tex, m);
  }
};

// --- the models -------------------------------------------------------------------------------------------------
/** PlayerModel (wide arms) with its outer layers, on a 64x64 skin. */
AR.player = () => ({
  head: AR.part([0, 0, 0], [{o: [-4, -8, -4], s: [8, 8, 8], uv: [0, 0]}, {o: [-4, -8, -4], s: [8, 8, 8], uv: [32, 0], g: 0.5}]),
  body: AR.part([0, 0, 0], [{o: [-4, 0, -2], s: [8, 12, 4], uv: [16, 16]}, {o: [-4, 0, -2], s: [8, 12, 4], uv: [16, 32], g: 0.25}]),
  right_arm: AR.part([-5, 2, 0], [{o: [-3, -2, -2], s: [4, 12, 4], uv: [40, 16]}, {o: [-3, -2, -2], s: [4, 12, 4], uv: [40, 32], g: 0.25}]),
  left_arm: AR.part([5, 2, 0], [{o: [-1, -2, -2], s: [4, 12, 4], uv: [32, 48]}, {o: [-1, -2, -2], s: [4, 12, 4], uv: [48, 48], g: 0.25}]),
  right_leg: AR.part([-1.9, 12, 0], [{o: [-2, 0, -2], s: [4, 12, 4], uv: [0, 16]}, {o: [-2, 0, -2], s: [4, 12, 4], uv: [0, 32], g: 0.25}]),
  left_leg: AR.part([1.9, 12, 0], [{o: [-2, 0, -2], s: [4, 12, 4], uv: [16, 48]}, {o: [-2, 0, -2], s: [4, 12, 4], uv: [0, 48], g: 0.25}])
});

/** HumanoidArmorModel.createBodyLayer(g): the humanoid mesh inflated by g, legs by g - 0.1; parts per slot. */
AR.armor = (g, slot) => {
  const all = {
    head: AR.part([0, 0, 0], [{o: [-4, -8, -4], s: [8, 8, 8], uv: [0, 0], g}]),
    body: AR.part([0, 0, 0], [{o: [-4, 0, -2], s: [8, 12, 4], uv: [16, 16], g}]),
    right_arm: AR.part([-5, 2, 0], [{o: [-3, -2, -2], s: [4, 12, 4], uv: [40, 16], g}]),
    left_arm: AR.part([5, 2, 0], [{o: [-1, -2, -2], s: [4, 12, 4], uv: [40, 16], g, mirror: true}]),
    right_leg: AR.part([-1.9, 12, 0], [{o: [-2, 0, -2], s: [4, 12, 4], uv: [0, 16], g: g - 0.1}]),
    left_leg: AR.part([1.9, 12, 0], [{o: [-2, 0, -2], s: [4, 12, 4], uv: [0, 16], g: g - 0.1, mirror: true}])
  };
  const keep = {head: ['head'], chest: ['body', 'right_arm', 'left_arm'], legs: ['body', 'right_leg', 'left_leg'], feet: ['right_leg', 'left_leg']}[slot];
  for (const k of Object.keys(all)) if (!keep.includes(k)) all[k].hidden = true;
  return all;
};

/** CoatArmorModel.createLayer(): the chest's outer armour parts plus the tails on the body (keep in step with Java). */
AR.coat = () => {
  const m = AR.armor(1.0, 'chest');
  m.body.children.tail_right = AR.part([0, 12, 3], [{o: [-5.5, 0, 0.1], s: [5, 7, 1], uv: [0, 0]}, {o: [-6.5, 0, -4.9], s: [1, 7, 6], uv: [12, 0]}]);
  m.body.children.tail_left = AR.part([0, 12, 3], [{o: [0.5, 0, 0.1], s: [5, 7, 1], uv: [0, 8]}, {o: [5.5, 0, -4.9], s: [1, 7, 6], uv: [26, 0]}]);
  return m;
};

/** apparel.CoatTails.pitch (keep in step with Java). */
AR.tailPitch = (leg, body) => {
  const b = Math.max(0, leg);
  return Math.min(1.6, 0.08 + b + 0.5 * b * b + 0.12 * Math.max(0, -leg)) - Math.max(-0.4, Math.min(0.4, body));
};

/** Poses (HumanoidModel#setupAnim): stand, walk (a stride with the right leg back), sneak. */
AR.POSES = {
  stand: {},
  walk: {right_leg: [0.7, 0, 0], left_leg: [-0.7, 0, 0], right_arm: [-0.6, 0, 0], left_arm: [0.6, 0, 0]},
  walk_other: {right_leg: [-0.7, 0, 0], left_leg: [0.7, 0, 0], right_arm: [0.6, 0, 0], left_arm: [-0.6, 0, 0]},
  sneak: {body: [0.5, 0, 0], right_arm: [0.4, 0, 0], left_arm: [0.4, 0, 0], sneak: true}
};
AR.pose = function (parts, name) {
  const p = AR.POSES[name];
  for (const [k, part] of Object.entries(parts)) {
    if (p[k]) part.rot = p[k].slice();
    if (p.sneak) {
      if (k === 'body') part.pivot = [part.pivot[0], 3.2, part.pivot[2]];
      if (k === 'head') part.pivot = [part.pivot[0], 4.2, part.pivot[2]];
      if (k === 'right_arm' || k === 'left_arm') part.pivot = [part.pivot[0], 5.2, part.pivot[2]];
      if (k === 'right_leg' || k === 'left_leg') part.pivot = [part.pivot[0], 12.2, 4.0];
    }
  }
  if (parts.body && parts.body.children.tail_right) {
    parts.body.children.tail_right.rot[0] = AR.tailPitch(parts.right_leg.rot[0], parts.body.rot[0]);
    parts.body.children.tail_left.rot[0] = AR.tailPitch(parts.left_leg.rot[0], parts.body.rot[0]);
  }
  return parts;
};

// --- item models (java block elements) ------------------------------------------------------------------------------
/** Meshes of an item model's elements in item space (0..16), one flat colour per face (palette patches are flat). */
AR.itemMeshes = function (json, sheets) {
  const g = new THREE.Group();
  const dirs = {north: [[1, 1, 0], [0, 1, 0], [0, 0, 0], [1, 0, 0]], south: [[0, 1, 1], [1, 1, 1], [1, 0, 1], [0, 0, 1]],
    east: [[1, 1, 1], [1, 1, 0], [1, 0, 0], [1, 0, 1]], west: [[0, 1, 0], [0, 1, 1], [0, 0, 1], [0, 0, 0]],
    up: [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]], down: [[0, 0, 1], [1, 0, 1], [1, 0, 0], [0, 0, 0]]};
  for (const e of json.elements) {
    const r = e.rotation ? new THREE.Matrix4().makeTranslation(...e.rotation.origin)
      .multiply(new THREE.Matrix4().makeRotationAxis(new THREE.Vector3(e.rotation.axis === 'x' ? 1 : 0, e.rotation.axis === 'y' ? 1 : 0, e.rotation.axis === 'z' ? 1 : 0), e.rotation.angle * Math.PI / 180))
      .multiply(new THREE.Matrix4().makeTranslation(...e.rotation.origin.map(v => -v))) : new THREE.Matrix4();
    for (const [dir, f] of Object.entries(e.faces)) {
      const tex = sheets[f.texture.replace('#', '')];
      const cu = Math.floor((f.uv[0] + f.uv[2]) / 2 * tex.image_w / 16), cv = Math.floor((f.uv[1] + f.uv[3]) / 2 * tex.image_h / 16);
      const k = (cv * tex.image_w + cu) * 4, col = new THREE.Color(tex.pixels[k] / 255, tex.pixels[k + 1] / 255, tex.pixels[k + 2] / 255);
      const pos = [];
      const pts = dirs[dir].map(c => new THREE.Vector3(c[0] ? e.to[0] : e.from[0], c[1] ? e.to[1] : e.from[1], c[2] ? e.to[2] : e.from[2]).applyMatrix4(r));
      for (const i of [0, 1, 2, 0, 2, 3]) pos.push(pts[i].x, pts[i].y, pts[i].z);
      const geo = new THREE.BufferGeometry(); geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); geo.computeVertexNormals();

      g.add(new THREE.Mesh(geo, new THREE.MeshLambertMaterial({color: col, side: THREE.DoubleSide})));
    }
  }
  return g;
};

AR.loadItem = async function (repo, name) {
  const json = JSON.parse(require('fs').readFileSync(repo + AR.ASSETS + 'models/item/' + name + '.json', 'utf8'));
  const sheets = {};
  for (const [id, path] of Object.entries(json.textures)) {
    if (id === 'particle') continue;
    sheets[id] = await AR.loadTexture(repo + AR.ASSETS + 'textures/' + path.split(':')[1] + '.png');
  }
  return {json, sheets};
};

// --- scene ----------------------------------------------------------------------------------------------------------
/** World (block units, y up, the figure facing +z) from model space (pixels, y down, front -z): diag(1, -1, -1)/16 + 1.5 up. */
AR.WORLD = new THREE.Matrix4().makeTranslation(0, 1.5, 0).multiply(new THREE.Matrix4().makeScale(1 / 16, -1 / 16, -1 / 16));

AR.figure = async function (repo, layers, poseName, hat) {
  const group = new THREE.Group();
  const skin = await AR.loadTexture(repo + AR.ASSETS + 'textures/entity/crew_member.png');
  const body = AR.pose(AR.player(), poseName);
  AR.addParts(group, body, {t: skin, tw: 64, th: 64}, AR.WORLD.clone());
  for (const l of layers) {
    const t = await AR.loadTexture(repo + AR.ASSETS + 'textures/models/armor/' + l.texture + '.png');
    AR.addParts(group, AR.pose(l.model(), poseName), {t, tw: 64, th: 32}, AR.WORLD.clone());
  }
  if (hat) {
    // CustomHeadLayer: the head's transform, translate(0, -0.25, 0) blocks, Y 180, scale(0.625, -0.625, -0.625), then
    // the item's head display (translation in px, rotation XYZ, scale) and translate(-0.5, -0.5, -0.5) of the item
    const item = await AR.loadItem(repo, hat);
    const d = item.json.display.head;
    const m = AR.WORLD.clone().multiply(new THREE.Matrix4().makeScale(16, 16, 16)).multiply(AR.partMatrix({pivot: body.head.pivot.map(v => v / 16), rot: body.head.rot}))
      .multiply(new THREE.Matrix4().makeTranslation(0, -0.25, 0)).multiply(new THREE.Matrix4().makeRotationY(Math.PI))
      .multiply(new THREE.Matrix4().makeScale(0.625, -0.625, -0.625))
      .multiply(new THREE.Matrix4().makeTranslation(...d.translation.map(v => v / 16)))
      .multiply(new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(...d.rotation.map(v => v * Math.PI / 180), 'XYZ')))
      .multiply(new THREE.Matrix4().makeScale(...d.scale)).multiply(new THREE.Matrix4().makeTranslation(-0.5, -0.5, -0.5))
      .multiply(new THREE.Matrix4().makeScale(1 / 16, 1 / 16, 1 / 16));
    const meshes = AR.itemMeshes(item.json, item.sheets);
    meshes.applyMatrix4(m);
    group.add(meshes);
  }
  return group;
};

/** Renders group from each view ([yaw degrees, pitch degrees, label]) side by side; returns a canvas. */
AR.shoot = function (group, views, opts) {
  const W = opts.w || 320, H = opts.h || 420, half = opts.half || 1.25, cy = opts.cy == null ? 1.1 : opts.cy;
  const renderer = new THREE.WebGLRenderer({antialias: true, alpha: true, preserveDrawingBuffer: true});
  renderer.setSize(W, H); renderer.setClearColor(0xd8d8d8, 1);
  const scene = new THREE.Scene();
  scene.add(new THREE.AmbientLight(0xffffff, 0.62));
  const sun = new THREE.DirectionalLight(0xffffff, 0.55); sun.position.set(0.6, 1, 0.9); scene.add(sun);
  const back = new THREE.DirectionalLight(0xffffff, 0.25); back.position.set(-0.6, 0.4, -0.9); scene.add(back);
  scene.add(group);
  const out = document.createElement('canvas'); out.width = W * views.length; out.height = H + 22;
  const ctx = out.getContext('2d'); ctx.fillStyle = '#d8d8d8'; ctx.fillRect(0, 0, out.width, out.height);
  views.forEach(([yaw, pitch, label], i) => {
    const a = half * W / H;
    const cam = new THREE.OrthographicCamera(-a, a, half, -half, 0.1, 50);
    const y = yaw * Math.PI / 180, p = pitch * Math.PI / 180;
    cam.position.set(Math.sin(y) * Math.cos(p) * 10, cy + Math.sin(p) * 10, Math.cos(y) * Math.cos(p) * 10);
    cam.lookAt(0, cy, 0);
    renderer.render(scene, cam);
    ctx.drawImage(renderer.domElement, i * W, 0);
    ctx.fillStyle = '#333'; ctx.font = '14px sans-serif'; ctx.fillText(label, i * W + 8, H + 16);
  });
  renderer.dispose();
  return out;
};

AR.stack = function (canvases) {
  const out = document.createElement('canvas');
  out.width = Math.max(...canvases.map(c => c.width)); out.height = canvases.reduce((a, c) => a + c.height, 0);
  const ctx = out.getContext('2d'); let y = 0;
  for (const c of canvases) { ctx.drawImage(c, 0, y); y += c.height; }
  return out;
};

AR.write = (canvas, file) => require('fs').writeFileSync(file, Buffer.from(canvas.toDataURL('image/png').split(',')[1], 'base64'));

AR.VIEWS = [[0, 5, 'front'], [35, 15, 'front three-quarter'], [90, 5, 'side (left)'], [180, 5, 'back'], [215, 15, 'back three-quarter']];

AR.renderAll = async function (repo) {
  const R = repo + '/art/renders/';
  const coat = tex => ({texture: tex, model: AR.coat});
  // the officer's coat's tails: standing, the two strides, sneaking
  const tails = [];
  for (const pose of ['stand', 'walk', 'walk_other', 'sneak']) {
    tails.push(AR.shoot(await AR.figure(repo, [coat('officers_coat_layer_1')], pose), [[0, 5, pose + ': front'], [90, 5, 'side (left)'], [180, 5, 'back'], [215, 15, 'back three-quarter']], {}));
  }
  AR.write(AR.stack(tails), R + 'coat_tails.png');
  // the captain's full set: hat, coat (tails), breeches (inner layer 2), boots (outer layer 1)
  const set = [{texture: 'captains_clothing_layer_2', model: () => AR.armor(0.5, 'legs')}, {texture: 'captains_clothing_layer_1', model: () => AR.armor(1.0, 'feet')},
    coat('captains_coat_layer_1')];
  const worn = [AR.shoot(await AR.figure(repo, set, 'stand', 'captains_hat'), AR.VIEWS, {}),
    AR.shoot(await AR.figure(repo, set, 'walk', 'captains_hat'), [[0, 5, 'walking: front'], [90, 5, 'side (left)'], [180, 5, 'back']], {})];
  AR.write(AR.stack(worn), R + 'captains_set_worn.png');
  // the three items
  const items = [];
  for (const name of ['captains_coat', 'captains_breeches', 'captains_boots']) {
    const it = await AR.loadItem(repo, name);
    const g = AR.itemMeshes(it.json, it.sheets); g.applyMatrix4(new THREE.Matrix4().makeTranslation(-0.5, -0.5, -0.5).multiply(new THREE.Matrix4().makeScale(1 / 16, 1 / 16, 1 / 16)));
    items.push(AR.shoot(g, [[0, 0, name + ': front'], [35, 20, 'three-quarter'], [90, 0, 'side'], [180, 0, 'back']], {w: 260, h: 260, half: 0.62, cy: 0}));
  }
  AR.write(AR.stack(items), R + 'captains_clothing.png');
  return ['coat_tails.png', 'captains_set_worn.png', 'captains_clothing.png'];
};
