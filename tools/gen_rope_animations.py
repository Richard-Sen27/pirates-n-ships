#!/usr/bin/env python3
"""Writes the GR8 rope poses of the player as PAL animation files (Player Animation Library, GeckoLib/Bedrock format).

    python3 tools/gen_rope_animations.py

Output: common/src/main/resources/assets/pirates_n_ships/rope_animations/{rope_hang,rope_climb,rope_swing,rope_pull}.json,
played by `combat/grapple/client/anim/PalRopeSlidePoses` as `RopePose` picks them (docs/playtests/grapple.md, GR8).

Values are in PAL file convention, the same as `art/animations/player_poses.js` (its `F9.expand` is ported below):
rotations are vanilla ModelPart angles in degrees (x negative swings an arm or leg forward and up, -180 is an arm
straight up; z positive on a right arm or a right leg swings it outward, on the left ones negative), positions are
pixels in the model frame with y up and z backwards. `lean` tips the chest about the hips (negative leans back), as in
the haul. The same pose tables live in `player_poses.js` (`F9.G8`) so the poses can be rebuilt in Blockbench; this
script is the source of the files (Blockbench was not available for GR8).
"""
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'common', 'src', 'main', 'resources', 'assets', 'pirates_n_ships', 'rope_animations')

# the player's default pose before a rope animation blends in (F9.R.REST)
REST = {'ra': [-18, 0, 0], 'la': [0, 0, 0], 'rl': [0, 0, 0], 'll': [0, 0, 0]}

# GR8 poses (F9.G8 in player_poses.js)
# Hanging on a vertical rope: both hands on the rope above the head (as the GR2 slide), the legs pressed together and
# crossed a little in front, as if wrapped round the rope's end.
HANG = {'ra': [-172, 0, 6], 'la': [-172, 0, -6], 'rl': [-20, 0, -5], 'll': [-10, 0, 5]}
# Swinging: the hang's grip, the legs together trailing the swing a little; the whole body leans along the rope in code
# (RopeLean, PalRopeSlidePoses' lean modifier), not in this file.
SWING = {'ra': [-172, 0, 5], 'la': [-172, 0, -5], 'rl': [10, 0, -2], 'll': [14, 0, 2]}
# Climbing, one pull (rope_climb_step) per play: the right hand reaches up while the left pulls down and the right knee
# draws up, then they change over, ending in the hang.
CLIMB_A = {'ra': [-180, 0, 3], 'la': [-148, 0, -10], 'rl': [-38, 0, -4], 'll': [-6, 0, 4]}
CLIMB_B = {'ra': [-148, 0, 10], 'la': [-180, 0, -3], 'rl': [-6, 0, -4], 'll': [-38, 0, 4]}
# Pulling while standing, one pull per play: hand over hand up the rope ahead, leaning back a little, feet braced.
PULL_A = {'ra': [-140, -8, 0], 'la': [-92, 8, 0], 'lean': -8, 'rl': [-14, 0, 2], 'll': [10, 0, -2]}
PULL_B = {'ra': [-92, -8, 0], 'la': [-140, 8, 0], 'lean': -11, 'rl': [-14, 0, 2], 'll': [10, 0, -2]}
PULL_C = {'ra': [-118, -8, 0], 'la': [-118, 8, 0], 'lean': -6, 'rl': [-10, 0, 2], 'll': [8, 0, -2]}

ANIMATIONS = {
    'rope_hang': (0.25, 'hold_on_last_frame', [(0.0, REST), (0.25, HANG)]),
    'rope_swing': (0.25, 'hold_on_last_frame', [(0.0, HANG), (0.25, SWING)]),
    # 0.6 s long, the moves end at 0.5 s (RopePose.PULL_TICKS): the pose holds until the next pose fades in
    'rope_climb': (0.6, 'play_once', [(0.0, HANG), (0.15, CLIMB_A), (0.35, CLIMB_B), (0.5, HANG)]),
    'rope_pull': (0.6, 'play_once', [(0.0, PULL_C), (0.15, PULL_A), (0.35, PULL_B), (0.5, PULL_C)]),
}


def r(d):
    return d * math.pi / 180.0


def add(a, b):
    return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]


def expand(p):
    """F9.expand of player_poses.js: the pose's bones and channels in PAL file convention."""
    tw, ln = r(p.get('twist', 0)), r(p.get('lean', 0))
    lean_top = [0, -12 * (1 - math.cos(ln)), -12 * math.sin(ln)]
    lean_sh = [0, -10 * (1 - math.cos(ln)), -10 * math.sin(ln)]
    tw_r = [5 * (1 - math.cos(tw)), 0, 5 * math.sin(tw)]
    tw_l = [-5 * (1 - math.cos(tw)), 0, -5 * math.sin(tw)]
    e = {
        'torso': {'rotation': [p.get('lean', 0), p.get('twist', 0), 0], 'position': lean_top},
        'head': {'position': lean_top},
        'right_arm': {'rotation': p['ra'], 'position': add(add(lean_sh, tw_r), p.get('raPos', [0, 0, 0]))},
        'left_arm': {'rotation': p['la'], 'position': add(add(lean_sh, tw_l), p.get('laPos', [0, 0, 0]))},
        'right_item': {'rotation': p.get('ri', [0, 0, 0])},
    }
    if 'rl' in p:
        e['right_leg'] = {'rotation': p['rl']}
    if 'll' in p:
        e['left_leg'] = {'rotation': p['ll']}
    return e


def clean(v):
    out = []
    for x in v:
        x = round(x, 2)
        out.append(0 if x == 0 else (int(x) if x == int(x) else x))
    return out


def animation(name, length, loop, keys):
    bones = {}
    for t, pose in keys:
        for bone, channels in expand(pose).items():
            for channel, value in channels.items():
                bones.setdefault(bone, {}).setdefault(channel, {})[f'{t:.2f}'.rstrip('0').rstrip('.') if t else '0.0'] = clean(value)
    # keys sorted by time, '0.0' first
    for channels in bones.values():
        for channel, frames in channels.items():
            channels[channel] = dict(sorted(frames.items(), key=lambda kv: float(kv[0])))
    return {'format_version': '1.8.0', 'animations': {name: {'animation_length': length, 'loop': loop, 'bones': bones}}}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (length, loop, keys) in ANIMATIONS.items():
        path = os.path.join(OUT, name + '.json')
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(animation(name, length, loop, keys), f, indent=2)
            f.write('\n')
        print('wrote', os.path.relpath(path, ROOT))


if __name__ == '__main__':
    main()
