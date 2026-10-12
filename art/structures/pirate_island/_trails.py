"""Builders for the STR2 trail pieces that come in mirrored pairs (jigsaw rotates but never mirrors, so a left and a
right bend are two pieces): the gentle bend and the fork. The piece files call these with their side."""
from _camp import arc, band, edging, line, palm, rocks, rope_coil, trail
from _style import PALETTE, lantern_post
from buildspec import Piece


def bend(left: bool):
    """A gentle quarter bend (9x9): ``path_in`` on the north edge, the trail sweeps round on a radius of 6 and leaves
    through ``path_out`` on the east edge (``left``: walking in, heading south, east is on the left) or the west edge.
    On the outside of the bend a ``hut_out`` on the west (``left``) or east edge and another on the south edge, a
    palm and a few rocks in the inside corner."""
    W = L = 9
    size = (W, 4, L)
    side = "left" if left else "right"
    p = Piece(f"pirate_island_path_bend_{side}", f"Pirate Trail (bend {side})", size, PALETTE)
    if left:
        # the arc around (8, 0): from (2, 0) heading south to (8, 6) heading east
        centre = line(2, 0, 2, 0.5) + arc(8, 0.5, 6, 180, 90) + line(8, 6.5, 8, 6.5)
        x_in, out, out_face, corner, deco_x = 2, (8, 6), "east", (7, 1), 3
        hut, hut_face, spur = (0, 4), "west", {(0, 4), (1, 4)}
    else:
        centre = line(6, 0, 6, 0.5) + arc(0, 0.5, 6, 0, 90) + line(0, 6.5, 0, 6.5)
        x_in, out, out_face, corner, deco_x = 6, (0, 6), "west", (1, 1), 5
        hut, hut_face, spur = (8, 4), "east", {(8, 4), (7, 4)}
    bed = band(centre, size)
    mouth = {(deco_x, 7), (deco_x, 8)} | spur
    trail(p, bed | mouth, salt=171 if left else 181)
    edging(p, bed, size, salt=172 if left else 182, skip=mouth | {(deco_x - 1, 8), (deco_x + 1, 8)})
    # the inside corner: a palm leaning out over the trail, rocks at its foot
    cx, cz = corner
    for c in ((cx, cz), (cx + (1 if left else -1), cz + 1)):
        p.clear(c[0], 0, c[1])
    palm(p, cx, 1, cz, height=4, lean="south")
    p.put(cx, 0, cz, "sand")
    rocks(p, [(cx + (-1 if left else 1), cz), (cx, cz + 1)], salt=173)
    # the outside: a lantern stake and a rope coil
    lantern_post(p, 0 if left else W - 1, 1, 2, height=1, post="drift")
    rope_coil(p, 4 if left else 4, 1, 8, layers=1)
    p.connector(x_in, 0, 0, "path_in", "north", "minecraft:gravel")
    p.connector(out[0], 0, out[1], "path_straight_out", out_face, "minecraft:gravel")
    p.connector(deco_x, 0, L - 1, "hut_out", "south", "minecraft:coarse_dirt")
    p.connector(hut[0], 0, hut[1], "hut_out", hut_face, "minecraft:coarse_dirt")
    p.emit(f"a gentle quarter bend of the trail ({side}), a palm in the inside corner",
           [f"path_in at [{x_in}, 0, 0] (north), path_out at [{out[0]}, 0, {out[1]}] ({out_face}), "
            f"hut_out at [{deco_x}, 0, 8] (south) and [{hut[0]}, 0, {hut[1]}] ({hut_face})."])


def fork(left: bool):
    """A fork (9x11): the trail runs on south to ``path_out`` and a branch splits off on a radius of 5 to a second
    ``path_out`` on the east edge (``left``) or the west edge; a ``hut_out`` and a ``deco_out`` on the other side, a
    stack of crates and a lantern in the fork's crotch."""
    W, L = 9, 11
    size = (W, 4, L)
    side = "left" if left else "right"
    p = Piece(f"pirate_island_path_fork_{side}", f"Pirate Trail (fork {side})", size, PALETTE)
    x = 3 if left else 5
    trunk = line(x, 0, x, L - 1)
    if left:
        branch = arc(8, 3, 5, 180, 90) + line(8, 8, 8.5, 8)
        out, out_face, deco, hut = (8, 8), "east", (0, 8, "west"), (0, 3, "west")
        crotch = [(5, 9), (6, 10)]
    else:
        branch = arc(0, 3, 5, 0, 90) + line(0, 8, -0.5, 8)
        out, out_face, deco, hut = (0, 8), "west", (8, 8, "east"), (8, 3, "east")
        crotch = [(3, 9), (2, 10)]
    bed = band(trunk, size) | band(branch, size)
    mouth = {(deco[0], deco[1]), (deco[0] + (1 if left else -1), deco[1]), (hut[0], hut[1]),
             (hut[0] + (1 if left else -1), hut[1])}
    trail(p, bed | mouth, salt=191 if left else 201)
    edging(p, bed, size, salt=192 if left else 202, skip=mouth)
    for i, (cx, cz) in enumerate(crotch):
        if p.get(cx, 0, cz) in (None, "sand", "coarse") and (cx, cz) not in bed:
            p.put(cx, 0, cz, "coarse")
            if i == 0:
                p.put(cx, 1, cz, "pirates_n_ships:cargo_crate")
                p.put(cx, 2, cz, "minecraft:barrel[facing=north,open=false]")
            else:
                lantern_post(p, cx, 1, cz, height=1, post="drift")
    p.connector(x, 0, 0, "path_in", "north", "minecraft:gravel")
    p.connector(x, 0, L - 1, "path_straight_out", "south", "minecraft:gravel")
    p.connector(out[0], 0, out[1], "path_straight_out", out_face, "minecraft:gravel")
    p.connector(deco[0], 0, deco[1], "deco_out", deco[2], "minecraft:coarse_dirt")
    p.connector(hut[0], 0, hut[1], "hut_out", hut[2], "minecraft:coarse_dirt")
    p.emit(f"a fork of the trail ({side}): on south, and a branch sweeping off to the {out_face}",
           [f"path_in at [{x}, 0, 0], path_out at [{x}, 0, 10] (south) and [{out[0]}, 0, {out[1]}] ({out_face}), "
            f"deco_out at [{deco[0]}, 0, {deco[1]}] ({deco[2]}), hut_out at [{hut[0]}, 0, {hut[1]}] ({hut[2]})."])
