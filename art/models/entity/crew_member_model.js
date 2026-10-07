// Model builder for crew_member.bbmodel (M2). Run in Blockbench (risky_eval) in a GeckoLib Animated Model project with
// the skin texture loaded as the only texture, then run crew_member_animations.js:
//   eval(require('fs').readFileSync('<repo>/art/models/entity/crew_member_model.js', 'utf8'))
// Coordinates here are file (Bedrock/GeckoLib) coordinates: the model faces -z, its right arm is at -x. Blockbench's
// Bedrock codec negates x on export, so M2.mirror flips every element before it is built (x, and the y and z rotations).
// Contract cubes use box UV on the vanilla skin layout; detail cubes map each face onto a 2x2 colour patch of
// tools/gen_entity_textures.py (PATCHES, u 56..63, v 16..47). A face set to null is left out of the export.
window.M2 = window.M2 || {};
M2.PATCH = ["bandana","bandana_d","kerchief","kerchief_d","belt","belt_d","brass","brass_d","sheath","handle","shirt","shirt_d","trouser","trouser_d","skin","skin_d","gold","stripe"];
M2.puv = n => { const i = M2.PATCH.indexOf(n); if (i<0) throw new Error('patch '+n); const u = 56+2*(i%4), v = 16+2*Math.floor(i/4); return [u+0.5, v+0.5, u+1.5, v+1.5]; };
M2.BONES = [
 ["root",null,[0,0,0]],
 ["waist","root",[0,12,0]],
 ["body","waist",[0,24,0]],
 ["head","waist",[0,24,0]],
 ["hat","head",[0,24,0]],
 ["right_arm","waist",[-5,22,0]],
 ["right_hand","right_arm",[-6,12,-2]],
 ["left_arm","waist",[5,22,0]],
 ["left_hand","left_arm",[6,12,-2]],
 ["right_leg","root",[-1.9,12,0]],
 ["left_leg","root",[1.9,12,0]]];
M2.CONTRACT = [
 {"bone":"body","name":"body","from":[-4,12,-2],"to":[4,24,2],"box":[16,16],"inflate":0},
 {"bone":"body","name":"jacket","from":[-4,12,-2],"to":[4,24,2],"box":[16,32],"inflate":0.25},
 {"bone":"head","name":"head","from":[-4,24,-4],"to":[4,32,4],"box":[0,0],"inflate":0},
 {"bone":"hat","name":"hat","from":[-4,24,-4],"to":[4,32,4],"box":[32,0],"inflate":0.5},
 {"bone":"right_arm","name":"right_arm","from":[-8,12,-2],"to":[-4,24,2],"box":[40,16],"inflate":0},
 {"bone":"right_arm","name":"right_sleeve","from":[-8,12,-2],"to":[-4,24,2],"box":[40,32],"inflate":0.25},
 {"bone":"left_arm","name":"left_arm","from":[4,12,-2],"to":[8,24,2],"box":[32,48],"inflate":0},
 {"bone":"left_arm","name":"left_sleeve","from":[4,12,-2],"to":[8,24,2],"box":[48,48],"inflate":0.25},
 {"bone":"right_leg","name":"right_leg","from":[-3.9,0,-2],"to":[0.10000000000000009,12,2],"box":[0,16],"inflate":0},
 {"bone":"right_leg","name":"right_trousers","from":[-3.9,0,-2],"to":[0.10000000000000009,12,2],"box":[0,32],"inflate":0.25},
 {"bone":"left_leg","name":"left_leg","from":[-0.1,0,-2],"to":[3.9,12,2],"box":[16,48],"inflate":0},
 {"bone":"left_leg","name":"left_trousers","from":[-0.1,0,-2],"to":[3.9,12,2],"box":[0,48],"inflate":0.25}];
M2.DETAILS = [
 {"bone":"hat","name":"bandana_knot","from":[-1,26.6,4.4],"to":[1,28.4,5.5],"tex":"bandana_d","faces":{"south":"bandana"}},
 {"bone":"hat","name":"bandana_tail_right","from":[-1.6,24.2,4.6],"to":[-0.5,26.8,5.1],"tex":"bandana","rot":[0,0,-14],"origin":[-1,26.8,4.85]},
 {"bone":"hat","name":"bandana_tail_left","from":[0.4,24.6,4.6],"to":[1.4,26.8,5.1],"tex":"bandana_d","rot":[0,0,18],"origin":[0.9,26.8,4.85]},
 {"bone":"head","name":"earring","from":[4,25.4,-0.2],"to":[4.4,26.2,0.4],"tex":"gold"},
 {"bone":"body","name":"neckerchief_band","from":[-3.2,23.2,-2.45],"to":[3.2,24,2.45],"tex":"kerchief_d","faces":{"up":null}},
 {"bone":"body","name":"neckerchief_flap","from":[-2,22.2,-2.6],"to":[2,23.2,-2.2],"tex":"kerchief"},
 {"bone":"body","name":"neckerchief_tip","from":[-1.1,21.2,-2.6],"to":[1.1,22.2,-2.2],"tex":"kerchief"},
 {"bone":"body","name":"neckerchief_point","from":[-0.45,20.4,-2.6],"to":[0.45,21.2,-2.2],"tex":"kerchief_d"},
 {"bone":"body","name":"neckerchief_knot","from":[-0.7,22.4,-2.85],"to":[0.7,23.5,-2.5],"tex":"kerchief_d"},
 {"bone":"body","name":"belt","from":[-4.35,11.95,-2.35],"to":[4.35,13.3,2.35],"tex":"belt","faces":{"down":"belt_d"}},
 {"bone":"body","name":"buckle","from":[-1,11.8,-2.55],"to":[1,13.45,-2.3],"tex":"brass","faces":{"down":"brass_d","east":"brass_d","west":"brass_d"}},
 {"bone":"body","name":"buckle_hole","from":[-0.45,12.25,-2.6],"to":[0.45,13,-2.5],"tex":"belt_d"},
 {"bone":"body","name":"knife_sheath","from":[-3.2,12.05,2.3],"to":[0.6,13.15,3],"tex":"sheath","rot":[0,0,-8],"origin":[0,12.6,2.65]},
 {"bone":"body","name":"knife_guard","from":[0.6,11.75,2.25],"to":[0.95,13.45,3.05],"tex":"brass_d","rot":[0,0,-8],"origin":[0,12.6,2.65]},
 {"bone":"body","name":"knife_handle","from":[0.95,12.2,2.4],"to":[2.9,13,2.9],"tex":"handle","rot":[0,0,-8],"origin":[0,12.6,2.65]},
 {"bone":"right_arm","name":"right_cuff","from":[-8.4,16.6,-2.4],"to":[-3.6,18.1,2.4],"tex":"shirt","faces":{"down":"shirt_d"}},
 {"bone":"left_arm","name":"left_cuff","from":[3.6,16.6,-2.4],"to":[8.4,18.1,2.4],"tex":"shirt","faces":{"down":"shirt_d"}},
 {"bone":"right_leg","name":"right_roll","from":[-4.25,3.6,-2.35],"to":[-0.05,4.9,2.35],"tex":"trouser","faces":{"down":"trouser_d"}},
 {"bone":"left_leg","name":"left_roll","from":[0.05,3.6,-2.35],"to":[4.25,4.9,2.35],"tex":"trouser","faces":{"down":"trouser_d"}},
 {"bone":"right_leg","name":"right_toes","from":[-3.4,0,-2.45],"to":[-0.2,0.7,-2],"tex":"skin","faces":{"down":"skin_d"}},
 {"bone":"left_leg","name":"left_toes","from":[0.2,0,-2.45],"to":[3.4,0.7,-2],"tex":"skin","faces":{"down":"skin_d"}}];
M2.mirror = function(c){
  const o = Object.assign({}, c);
  o.from = [-c.to[0], c.from[1], c.from[2]]; o.to = [-c.from[0], c.to[1], c.to[2]];
  if (c.origin) o.origin = [-c.origin[0], c.origin[1], c.origin[2]];
  if (c.rot) o.rot = [c.rot[0], -c.rot[1], -c.rot[2]];
  if (c.faces) { o.faces = Object.assign({}, c.faces); if ('east' in c.faces || 'west' in c.faces){ o.faces.east = c.faces.west; o.faces.west = c.faces.east; for (const k of ['east','west']) if (o.faces[k]===undefined) delete o.faces[k]; } }
  return o;
};
M2.mirrorContract = c => Object.assign({}, c, {from: [-c.to[0], c.from[1], c.from[2]], to: [-c.from[0], c.to[1], c.to[2]]});
M2.build = function(details){
  Undo.initEdit({outliner: true, elements: [], selection: true});
  for (const e of [...Outliner.elements]) e.remove();
  for (const g of [...Group.all]) g.remove(false);
  const G = {};
  for (const [n,p,o] of M2.BONES){ G[n] = new Group({name:n, origin:[-o[0],o[1],o[2]]}).addTo(p ? G[p] : undefined).init(); }
  const made = [];
  for (const c0 of M2.CONTRACT){ const c = M2.mirrorContract(c0);
    const cube = new Cube({name:c.name, from:c.from, to:c.to, inflate:c.inflate, box_uv:true, uv_offset:c.box}).addTo(G[c.bone]).init();
    made.push(cube);
  }
  for (const c of details.map(M2.mirror)){
    const faces = {};
    for (const f of ['north','south','east','west','up','down']){
      const t = (c.faces && c.faces[f] !== undefined) ? c.faces[f] : c.tex;
      faces[f] = t === null ? {uv:[0,0,0,0], texture: null} : {uv: M2.puv(t), texture: 0};
    }
    const cube = new Cube({name:c.name, from:c.from, to:c.to, inflate:c.inflate||0, box_uv:false, rotation:c.rot||[0,0,0], origin:c.origin||[(c.from[0]+c.to[0])/2,(c.from[1]+c.to[1])/2,(c.from[2]+c.to[2])/2], faces}).addTo(G[c.bone]).init();
    made.push(cube);
  }
  const tex = Texture.all[0];
  if (tex) for (const cube of Cube.all) for (const f in cube.faces) { if (cube.faces[f].texture !== null) cube.faces[f].texture = tex.uuid; }
  Undo.finishEdit('M2 build', {outliner: true, elements: made});
  Canvas.updateAll();
  return {cubes: Cube.all.length, groups: Group.all.length};
};
Project.texture_width = 64; Project.texture_height = 64;
M2.build(M2.DETAILS);
