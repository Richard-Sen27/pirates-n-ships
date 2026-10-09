#!/usr/bin/env python3
"""The crew rig's rowing animations (OAR1, design.md §5.3b): row_standing and row_seated.

Writes (or rewrites) the two animations in common/src/main/resources/assets/pirates_n_ships/animations/
crew_member.animation.json, keeping every other animation byte for byte (the entries are text-inserted before the end
of "animations", in Blockbench's autoStringify layout: tabs, inline vectors).

The stroke is two seconds and loops: the catch at 0 s (leaning 22 degrees in toward the stern the rower faces, arms
reaching, the handle forward, the blade in the water toward the bow), the drive to 0.9 s (leaning back, the handle
pulled in, the blade sweeping aft through the water), the release at 1.2 s (the handle pressed down, the blade out of
the water), the recovery back to the catch (the blade carried forward high, dipped at 2 s). Values are in the file
convention of crew_member_animations.js (negative x swings an arm forward, positive x leans the waist forward); the
right_hand key turns the oar the right hand holds (item/oar's third-person entry: the loom in the fist, the shaft along
the hand's +y), the left_hand key is its mirror for a rower whose outboard side is on his left (sailing/oars/Rowing makes
such a rower left-handed). The hand keys were solved with GeckoLib's transforms (baked pivots, x and y of every key
negated, rotationZYX) and the oar's display entry so that the blade lies outboard, below the deck in the drive, and
moves toward the stern the rower faces while it is in the water; crew/npc/OarRigTest proves the signs on the real
files. row_seated is the same stroke with the root lowered onto the thwart (4 px) and the legs forward; row_standing
stands with the feet apart.

Run from the repository root: python3 tools/gen_rowing_animations.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILE = ROOT / "common/src/main/resources/assets/pirates_n_ships/animations/crew_member.animation.json"

LENGTH = 2.0
# time: (waist x, right arm (x, y, z), right hand (x, y, z))
STROKE = [
    (0.0, 22, (-88, -16, 0), (1.8, 138.2, 29.6)),
    (0.45, 6, (-70, -16, 0), (30.5, 139.5, 36.3)),
    (0.9, -10, (-48, -16, 0), (59.9, 147.3, 37.0)),
    (1.2, -6, (-55, -16, 0), (76.4, 133.7, 58.9)),
    (1.6, 8, (-75, -16, 0), (59.9, 125.8, 74.5)),
]
SEATED_DROP = 4  # px: the hips from standing height (12) down onto the thwart (8)


def num(v):
    v = round(v, 4)
    return str(int(v)) if v == int(v) else repr(v)


def vec(v):
    return "[" + ", ".join(num(c) for c in v) + "]"


def channel(keys, indent):
    """keys: [(time, vector)]; the first key again at LENGTH closes the loop (a constant channel too: Blockbench's
    exporter writes a one-key channel as a bare vector, so two keys keep the file equal to its export)."""
    t = "\t" * indent
    rows = list(keys) + [(LENGTH, keys[0][1])]
    lines = []
    for i, (time, v) in enumerate(rows):
        key = f"{time:.2f}".rstrip("0")
        key = key + "0" if key.endswith(".") else key
        lines.append(f'{t}"{key}": {{\n{t}\t"vector": {vec(v)},\n{t}\t"easing": "easeInOutSine"\n{t}}}' + ("," if i < len(rows) - 1 else ""))
    return "\n".join(lines)


def bone(name, channels, indent, last):
    t = "\t" * indent
    parts = []
    for i, (kind, keys) in enumerate(channels):
        parts.append(f'{t}\t"{kind}": {{\n{channel(keys, indent + 2)}\n{t}\t}}' + ("," if i < len(channels) - 1 else ""))
    return f'{t}"{name}": {{\n' + "\n".join(parts) + f'\n{t}}}' + ("" if last else ",")


def animation(name, seated):
    bones = []
    bones.append(("waist", [("rotation", [(t, (w, 0, 0)) for t, w, _, _ in STROKE])]))
    bones.append(("right_arm", [("rotation", [(t, a) for t, _, a, _ in STROKE])]))
    bones.append(("left_arm", [("rotation", [(t, (a[0], -a[1], -a[2])) for t, _, a, _ in STROKE])]))
    bones.append(("right_hand", [("rotation", [(t, h) for t, _, _, h in STROKE])]))
    bones.append(("left_hand", [("rotation", [(t, (h[0], -h[1], -h[2])) for t, _, _, h in STROKE])]))
    if seated:
        bones.append(("root", [("position", [(0.0, (0, -SEATED_DROP, 0))])]))
        bones.append(("right_leg", [("rotation", [(0.0, (-84, 10, 3))])]))
        bones.append(("left_leg", [("rotation", [(0.0, (-84, -10, -3))])]))
    else:
        bones.append(("right_leg", [("rotation", [(0.0, (-14, 0, 4)), (0.9, (-18, 0, 4))])]))
        bones.append(("left_leg", [("rotation", [(0.0, (12, 0, -4)), (0.9, (8, 0, -4))])]))
    body = "\n".join(bone(n, c, 4, i == len(bones) - 1) for i, (n, c) in enumerate(bones))
    return (f'\t\t"{name}": {{\n\t\t\t"loop": true,\n\t\t\t"animation_length": {num(LENGTH)},\n\t\t\t"bones": {{\n'
            f'{body}\n\t\t\t}}\n\t\t}}')


def main():
    text = FILE.read_text()
    data = json.loads(text)
    anims = data["animations"]
    for name in ("row_standing", "row_seated"):
        if name in anims:
            # rewrite: drop the old entries (always the last two of the file) and write them again
            text = text[:text.index(f'\t\t"{name}": {{')].rstrip().rstrip(",") + "\n\t}\n}\n"
            anims = json.loads(text)["animations"]
    end = text.rstrip().rfind("}")  # the file's closing brace
    end = text.rstrip()[:end].rstrip().rfind("}")  # the closing brace of "animations"
    body = text[:end].rstrip()
    new = body + ",\n" + animation("row_standing", False) + ",\n" + animation("row_seated", True) + "\n\t" + text[end:]
    json.loads(new)  # still valid
    FILE.write_text(new)
    print("row_standing, row_seated written")


if __name__ == "__main__":
    main()
