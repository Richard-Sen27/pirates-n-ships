#!/usr/bin/env python3
"""RAIL5: writes the crew rig's `lean` animation into crew_member.animation.json.

Leaning on the rail: the waist bent 24 deg forward over the rail, both arms reaching forward (-80 deg, 10 deg inwards)
so the forearms lie across the top rail and the hands hang just outboard of it, the weight on the right leg (the root
shifted half a pixel to the right, the left leg relaxed back and out), a slow breath every 6 s that keeps the hands on
the rail (the arms counter the waist). The head is never keyed (the rig contract, art/README.md "Entities"): its slow
turn to the sea comes from code (crew/lean/LeanRules#headYaw).

Values are in the file convention of every animation in the file (crew_member_animations.js, station_animations.js):
the vanilla player's signs, which GeckoLib's loader flips (x and y negated) on load: positive x on `waist` leans
forward, negative x on an arm or leg swings it forward, negative y on the right arm and positive y on the left turn them
inwards (towards the body's middle, as `capstan_push`'s 14 deg), negative z on the left leg spreads it outwards. Position x is negated on load as well, so file x -0.5
moves the root towards the model's right. CrewLeanPoseTest composes GeckoLib's real transforms and checks where the
hands and forearms end up against the rail (art/README.md rule 5).

Not built in Blockbench (Blockbench was taken by another package when this was written): the keys are written here and
proven by the rig test only. Re-running the script replaces the `lean` entry and keeps every other byte of the file.

Usage: python3 tools/gen_lean_animation.py
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILE = os.path.join(ROOT, 'common/src/main/resources/assets/pirates_n_ships/animations/crew_member.animation.json')

EASE = 'easeInOutSine'

# waist lean and arm reach at rest (0 s, 6 s) and at the top of the breath (3 s): the arm angle counters the waist so
# the hands stay on the rail (waist + arm = -56 deg in the body's side plane at both keys)
WAIST = (24.0, 25.5)
ARM = (-80.0, -81.5)
ARM_IN = 10.0


def keys(values):
    """{time: vector} for the times 0, 3 and 6 s from (rest, breath) vectors."""
    rest, breath = values
    return {'0.0': {'vector': rest, 'easing': EASE},
            '3.0': {'vector': breath, 'easing': EASE},
            '6.0': {'vector': rest, 'easing': EASE}}


def lean():
    return {
        'loop': True,
        'animation_length': 6,
        'bones': {
            'root': {'position': keys(([-0.5, 0, 0], [-0.5, 0, 0]))},
            'waist': {'rotation': keys(([WAIST[0], 0, 2], [WAIST[1], 0, 2.5]))},
            'right_arm': {'rotation': keys(([ARM[0], -ARM_IN, 0], [ARM[1], -ARM_IN, 0]))},
            'left_arm': {'rotation': keys(([ARM[0], ARM_IN, 0], [ARM[1], ARM_IN, 0]))},
            'right_leg': {'rotation': keys(([-2, 0, 1], [-2, 0, 1]))},
            'left_leg': {'rotation': keys(([8, -6, -4], [9, -6, -4.5]))},
        },
    }


def num(v):
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, float) and v.is_integer():
        return str(int(v))
    return repr(v) if isinstance(v, float) else str(v)


def dump(v, depth=0):
    """Blockbench's autoStringify: tabs, objects one key per line, number arrays on one line."""
    tab = '\t'
    if isinstance(v, dict):
        if not v:
            return '{}'
        inner = ',\n'.join(tab * (depth + 1) + json.dumps(k) + ': ' + dump(x, depth + 1) for k, x in v.items())
        return '{\n' + inner + '\n' + tab * depth + '}'
    if isinstance(v, list):
        if all(isinstance(x, (int, float)) and not isinstance(x, bool) for x in v):
            return '[' + ', '.join(num(x) for x in v) + ']'
        inner = ',\n'.join(tab * (depth + 1) + dump(x, depth + 1) for x in v)
        return '[\n' + inner + '\n' + tab * depth + ']'
    if isinstance(v, str):
        return json.dumps(v)
    return num(v)


def main():
    with open(FILE) as f:
        text = f.read()
    data = json.loads(text)
    check = dict(data)
    check['animations'] = {k: a for k, a in data['animations'].items() if k != 'lean'}
    if 'lean' not in data['animations'] and dump(check) + '\n' != text:
        sys.exit('the animation file is not in autoStringify form; refusing to rewrite it')
    data['animations'] = {k: a for k, a in data['animations'].items() if k != 'lean'}
    data['animations']['lean'] = lean()
    with open(FILE, 'w') as f:
        f.write(dump(data) + '\n')
    print('wrote lean to', os.path.relpath(FILE, ROOT))


if __name__ == '__main__':
    main()
