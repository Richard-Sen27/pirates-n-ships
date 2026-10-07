// Pose table and builder for player_rig.bbmodel (F9). Run in Blockbench (risky_eval) with the rig open.
// Values are in PAL file convention (vanilla ModelPart xRot/yRot/zRot in degrees, positions in px); see art/README.md, "Player animations".
window.F9 = window.F9 || {};
F9.bone = n => Group.all.find(g=>g.name===n);
F9.r = d => d*Math.PI/180;
F9.expand = function(p){
  const tw = F9.r(p.twist||0), ln = F9.r(p.lean||0);
  const add = (a,b)=>[a[0]+b[0],a[1]+b[1],a[2]+b[2]];
  const leanTop = [0, -12*(1-Math.cos(ln)), -12*Math.sin(ln)];
  const leanSh = [0, -10*(1-Math.cos(ln)), -10*Math.sin(ln)];
  const twR = [5*(1-Math.cos(tw)), 0, 5*Math.sin(tw)];
  const twL = [-5*(1-Math.cos(tw)), 0, -5*Math.sin(tw)];
  return {
    torso: {rotation: [p.lean||0, p.twist||0, 0], position: leanTop},
    head: {position: leanTop},
    right_arm: {rotation: p.ra, position: add(add(leanSh, twR), p.raPos||[0,0,0])},
    left_arm: {rotation: p.la, position: add(add(leanSh, twL), p.laPos||[0,0,0])},
    right_item: p.riPos ? {rotation: p.ri||[0,0,0], position: p.riPos} : {rotation: p.ri||[0,0,0]}
  };
};
F9.toInternal = (ch, v) => ch==='rotation' ? [-v[0],-v[1],v[2]] : [-v[0],v[1],v[2]];
F9.make = function(name, length, loop, keys){
  const old = Animation.all.find(a=>a.name===name); if (old) old.remove(false);
  const a = new Animation({name, length, loop, snapping: 100}).add(false);
  for (const k of keys){
    const e = F9.expand(k.pose);
    for (const bn in e) for (const ch in e[bn]) {
      const v = F9.toInternal(ch, e[bn][ch].map(x=>Math.round(x*100)/100));
      a.getBoneAnimator(F9.bone(bn)).addKeyframe({channel: ch, time: k.t, interpolation:'linear', data_points:[{x:v[0],y:v[1],z:v[2]}]});
    }
  }
  return a;
};
F9.P = {
 "REST": {
  "ra": [
   -18,
   0,
   0
  ],
  "la": [
   0,
   0,
   0
  ],
  "ri": [
   0,
   0,
   0
  ]
 },
 "SLASH_WIND": {
  "ra": [
   -105,
   100,
   0
  ],
  "la": [
   -45,
   -25,
   0
  ],
  "ri": [
   -20,
   0,
   0
  ],
  "twist": 30
 },
 "SLASH_MID": {
  "ra": [
   -95,
   15,
   0
  ],
  "la": [
   -30,
   -10,
   0
  ],
  "ri": [
   60,
   0,
   0
  ],
  "twist": 5,
  "lean": 4
 },
 "SLASH_END": {
  "ra": [
   -75,
   -70,
   0
  ],
  "la": [
   -15,
   0,
   -20
  ],
  "ri": [
   60,
   0,
   0
  ],
  "twist": -30,
  "lean": 6
 },
 "SLASH_SETTLE": {
  "ra": [
   -50,
   -45,
   0
  ],
  "la": [
   -8,
   0,
   -10
  ],
  "ri": [
   30,
   0,
   0
  ],
  "twist": -15,
  "lean": 3
 },
 "THRUST_DRAW": {
  "ra": [
   25,
   15,
   10
  ],
  "la": [
   -55,
   -15,
   -5
  ],
  "ri": [
   -35,
   0,
   0
  ],
  "twist": 25,
  "lean": -5,
  "raPos": [
   0,
   0,
   1.5
  ]
 },
 "THRUST_LUNGE": {
  "ra": [
   -88,
   -6,
   0
  ],
  "la": [
   20,
   0,
   -25
  ],
  "ri": [
   76,
   0,
   0
  ],
  "twist": -20,
  "lean": 16,
  "raPos": [
   0,
   0,
   -2
  ]
 },
 "THRUST_LUNGE2": {
  "ra": [
   -90,
   -6,
   0
  ],
  "la": [
   22,
   0,
   -28
  ],
  "ri": [
   78,
   0,
   0
  ],
  "twist": -22,
  "lean": 18,
  "raPos": [
   0,
   0,
   -2.5
  ]
 },
 "THRUST_BACK": {
  "ra": [
   -50,
   0,
   0
  ],
  "la": [
   5,
   0,
   -10
  ],
  "ri": [
   40,
   0,
   0
  ],
  "twist": -8,
  "lean": 7,
  "raPos": [
   0,
   0,
   -0.5
  ]
 },
 "RIP_A": {
  "ra": [
   -35,
   -40,
   -10
  ],
  "la": [
   -20,
   -10,
   -10
  ],
  "ri": [
   10,
   -60,
   0
  ],
  "twist": -10,
  "lean": 4
 },
 "RIP_B": {
  "ra": [
   -15,
   35,
   20
  ],
  "la": [
   -45,
   -15,
   -5
  ],
  "ri": [
   50,
   20,
   0
  ],
  "twist": 15,
  "lean": 0
 },
 "GUARD": {
  "ra": [
   -80,
   -35,
   0
  ],
  "la": [
   -55,
   10,
   0
  ],
  "ri": [
   0,
   -45,
   0
  ],
  "twist": -5,
  "lean": 3
 },
 "GUARD_OVER": {
  "ra": [
   -86,
   -38,
   0
  ],
  "la": [
   -58,
   12,
   0
  ],
  "ri": [
   0,
   -48,
   0
  ],
  "twist": -6,
  "lean": 3
 },
 "PARRY_FLICK": {
  "ra": [
   -108,
   32,
   18
  ],
  "la": [
   -45,
   0,
   -10
  ],
  "ri": [
   -15,
   -15,
   0
  ],
  "twist": 12,
  "lean": -2
 },
 "PARRY_HOLD": {
  "ra": [
   -100,
   26,
   14
  ],
  "la": [
   -40,
   0,
   -8
  ],
  "ri": [
   -10,
   -10,
   0
  ],
  "twist": 10,
  "lean": -1
 },
 "STAG_HIT": {
  "ra": [
   -40,
   15,
   60
  ],
  "la": [
   -40,
   -15,
   -60
  ],
  "ri": [
   30,
   0,
   0
  ],
  "twist": -8,
  "lean": -18
 },
 "STAG_HOLD": {
  "ra": [
   -30,
   10,
   50
  ],
  "la": [
   -30,
   -10,
   -50
  ],
  "ri": [
   25,
   0,
   0
  ],
  "twist": -6,
  "lean": -14
 }
};
var P = F9.P;
F9.build = function(){
F9.make('slash_windup', 0.25, 'hold', [{t:0,pose:P.REST},{t:0.25,pose:P.SLASH_WIND}]);
F9.make('slash_active', 0.15, 'hold', [{t:0,pose:P.SLASH_WIND},{t:0.05,pose:P.SLASH_MID},{t:0.15,pose:P.SLASH_END}]);
F9.make('slash_recovery', 0.3, 'once', [{t:0,pose:P.SLASH_END},{t:0.12,pose:P.SLASH_SETTLE},{t:0.3,pose:P.REST}]);
F9.make('thrust_windup', 0.35, 'hold', [{t:0,pose:P.REST},{t:0.35,pose:P.THRUST_DRAW}]);
F9.make('thrust_active', 0.15, 'hold', [{t:0,pose:P.THRUST_DRAW},{t:0.1,pose:P.THRUST_LUNGE},{t:0.15,pose:P.THRUST_LUNGE2}]);
F9.make('thrust_recovery', 0.5, 'once', [{t:0,pose:P.THRUST_LUNGE2},{t:0.2,pose:P.THRUST_BACK},{t:0.5,pose:P.REST}]);
F9.make('riposte_windup', 0.2, 'hold', [{t:0,pose:P.REST},{t:0.07,pose:P.RIP_A},{t:0.14,pose:P.RIP_B},{t:0.2,pose:P.THRUST_DRAW}]);
F9.make('guard', 0.2, 'hold', [{t:0,pose:P.REST},{t:0.14,pose:P.GUARD_OVER},{t:0.2,pose:P.GUARD}]);
F9.make('guard_lower', 0.2, 'once', [{t:0,pose:P.GUARD},{t:0.2,pose:P.REST}]);
F9.make('parry', 0.35, 'hold', [{t:0,pose:P.GUARD},{t:0.07,pose:P.PARRY_FLICK},{t:0.18,pose:P.PARRY_HOLD},{t:0.35,pose:P.REST}]);
F9.make('stagger', 1.0, 'once', [{t:0,pose:P.REST},{t:0.12,pose:P.STAG_HIT},{t:0.5,pose:P.STAG_HOLD},{t:1.0,pose:P.REST}]);
};
// Firearm poses (P3). Same convention. ri turns the gun in the arm's frame (x +90 lays the barrel along the arm, z
// yaws it; never y and z together). riPos slides the gun along the arm (y negative = towards the hand and beyond), so
// the hand holds the musket by the fore-stock while its butt rests on the ground. The aim poses keep the arms level:
// in game the look pitch is added to both arms (FirearmAnimations' adjustment). Values were tuned by measuring the
// proxy barrel's world direction and solving the left hand onto muzzle and lock (see art/README.md, "Firearm animations").
// F8h moved the guns in the hand (new third-person translations): the fore-stock riPos values were re-solved so the
// guns sit exactly where they did in P3 (butt on the ground, muzzle at the left hand), and the cocking left arms were
// re-solved onto the moved locks.
F9.G = {
 PISTOL_AIM_RISE: {ra: [-62, -3, 0], la: [4, 0, -8], ri: [55, 0, 0], twist: -6, lean: 1},
 PISTOL_AIM: {ra: [-92, -5, 0], la: [8, 0, -10], ri: [92, 0, 5], twist: -12, lean: 2},
 MUSKET_AIM_RISE: {ra: [-55, -12, 0], la: [-55, 25, 0], ri: [50, 0, 0], twist: 8, lean: 3},
 MUSKET_AIM: {ra: [-84, -17, 0], la: [-86, 34, 0], ri: [84, 0, 17], twist: 15, lean: 5},
 P_LOW: {ra: [-45, -40, 0], la: [0, 0, 0], ri: [-35, 0, 0], riPos: [0, -2.4, -0.1], twist: 0, lean: 3},
 P_POUR: {ra: [-45, -40, 0], la: [-120, 35, -5], ri: [-35, 0, 0], riPos: [0, -2.4, -0.1], twist: 0, lean: 4},
 P_POUR2: {ra: [-45, -40, 0], la: [-116, 38, 0], ri: [-35, 0, 0], riPos: [0, -2.4, -0.1], twist: 0, lean: 4},
 P_BELT: {ra: [-45, -40, 0], la: [10, 0, -15], ri: [-35, 0, 0], riPos: [0, -2.4, -0.1], twist: 0, lean: 3},
 P_ROD_UP: {ra: [-45, -40, 0], la: [-132, 35, 0], ri: [-35, 0, 0], riPos: [0, -2.4, -0.1], twist: 0, lean: 3},
 P_ROD_DOWN: {ra: [-45, -40, 0], la: [-118, 30, -15], ri: [-35, 0, 0], riPos: [0, -2.4, -0.1], twist: 0, lean: 4},
 P_LEVEL: {ra: [-45, -35, 0], la: [-20, 0, -10], ri: [45, 0, 0], twist: 0, lean: 2},
 P_COCK: {ra: [-45, -35, 0], la: [-83, 51, 0], ri: [45, 0, 0], twist: 0, lean: 3},
 P_COCK2: {ra: [-47, -35, 0], la: [-78, 48, 0], ri: [39, 0, 0], twist: 0, lean: 3},
 M_UP: {ra: [-20, -35, 0], la: [0, 0, 0], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 0, lean: 2},
 M_POUR: {ra: [-20, -35, 0], la: [-100, 70, 0], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 5, lean: 2},
 M_POUR2: {ra: [-20, -35, 0], la: [-97, 74, 5], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 5, lean: 2},
 M_ROD_TOP: {ra: [-20, -35, 0], la: [-125, 65, 0], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 5, lean: 0},
 M_ROD_DOWN: {ra: [-20, -35, 0], la: [-95, 70, 0], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 5, lean: 4},
 M_STOW: {ra: [-20, -35, 0], la: [-30, 10, -10], ri: [-75, 0, 0], riPos: [0, -6.1, -0.65], twist: 0, lean: 2},
 M_RAISE: {ra: [-45, -35, 0], la: [-90, 15, 0], ri: [15, 0, 0], twist: 8, lean: 3},
 M_COCK: {ra: [-45, -35, 0], la: [-66, 52, 10], ri: [15, 0, 0], twist: 8, lean: 4},
 M_COCK2: {ra: [-47, -35, 0], la: [-61, 49, 8], ri: [9, 0, 0], twist: 8, lean: 4}
};
// Builds an animation whose poses all key right_item's position (riPos defaults to [0, 0, 0]), so the channel has a
// keyframe wherever the others do.
F9.makeG = function(name, length, loop, keys){
  return F9.make(name, length, loop, keys.map(k => ({t: k.t, pose: Object.assign({riPos: [0, 0, 0]}, k.pose)})));
};
F9.buildFirearms = function(){
var G = F9.G, R = F9.P.REST;
F9.make('pistol_aim', 0.25, 'hold', [{t:0,pose:R},{t:0.1,pose:G.PISTOL_AIM_RISE},{t:0.25,pose:G.PISTOL_AIM}]);
F9.make('musket_aim', 0.35, 'hold', [{t:0,pose:R},{t:0.15,pose:G.MUSKET_AIM_RISE},{t:0.35,pose:G.MUSKET_AIM}]);
F9.makeG('pistol_reload', 3.0, 'once', [{t:0,pose:R},{t:0.25,pose:G.P_LOW},{t:0.45,pose:G.P_POUR},{t:0.7,pose:G.P_POUR2},{t:0.95,pose:G.P_POUR},
 {t:1.2,pose:G.P_BELT},{t:1.45,pose:G.P_ROD_UP},{t:1.65,pose:G.P_ROD_DOWN},{t:1.85,pose:G.P_ROD_UP},{t:2.05,pose:G.P_ROD_DOWN},
 {t:2.3,pose:G.P_BELT},{t:2.5,pose:G.P_LEVEL},{t:2.65,pose:G.P_COCK},{t:2.75,pose:G.P_COCK2},{t:3.0,pose:R}]);
F9.makeG('musket_reload', 5.0, 'once', [{t:0,pose:R},{t:0.4,pose:G.M_UP},{t:0.75,pose:G.M_POUR},{t:1.05,pose:G.M_POUR2},{t:1.35,pose:G.M_POUR},
 {t:1.7,pose:G.M_ROD_TOP},{t:2.1,pose:G.M_ROD_DOWN},{t:2.5,pose:G.M_ROD_TOP},{t:2.9,pose:G.M_ROD_DOWN},{t:3.3,pose:G.M_ROD_TOP},
 {t:3.6,pose:G.M_STOW},{t:4.0,pose:G.M_RAISE},{t:4.3,pose:G.M_COCK},{t:4.45,pose:G.M_COCK2},{t:4.65,pose:G.M_RAISE},{t:5.0,pose:R}]);
};
// Shows the proxy item of one weapon in right_item: 'sword', 'pistol' or 'musket' (the gun proxies are rough boxes
// placed where vanilla's third-person transform of the gun models puts barrel, lock and grip; moved for F8h).
F9.proxy = function(kind){
  for (const c of Cube.all) if (c.parent && c.parent.name==='right_item') c.visibility = kind==='sword' ? !/^(pistol|musket)_/.test(c.name) : c.name.startsWith(kind+'_');
  Canvas.updateVisibility();
};
F9.build();
F9.buildFirearms();
