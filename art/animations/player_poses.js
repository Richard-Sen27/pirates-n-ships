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
    right_item: {rotation: p.ri||[0,0,0]}
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
F9.build();
