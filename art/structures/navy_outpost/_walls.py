"""STR4: the navy outpost's wall trace (art/README.md, "Natural layout of the outpost (STR4)"). Builders for the curtain
wall pieces in their three states, the angles and the run-ending towers. The piece files (``wall_long.py``,
``flank_angle_east.py``, ...) call these with their state and kind.

**States.** A run grows from the fort gate along the shore (state ``wall``: pool ``navy_outpost/walls``), turns inland
at a salient angle (``flank``: pool ``walls_flank``) and may turn once more toward the other run (``back``: pool
``walls_back``). A piece's out connectors name the pool of its state (``wall_*_out``, ``flank_*_out``,
``back_*_out``), the salient angle the next state's, the flank's re-entrant step the shore state's again: a run turns
inland at most twice and never toward the sea. Every piece attaches by ``wall_east_in`` / ``wall_west_in``.

**Section** (shared with the gate, ``_style.curtain``): the seaward (outer) skin on z 0, the body on z 1..4 to the
walkway (y 4, people stand on y 5), parapet and merlons on z 0; landward the hoarding, the ladder and a gravel path
(z 5..6). Symmetric pieces carry both runs' pairs (east pair on z 2, west pair on z 3); an angle belongs to one run (a
jigsaw never mirrors), so it comes as an ``_east`` and a ``_west`` piece."""
from _style import (EAST_Z, PALETTE, WALK, WEST_Z, age, arrow_slit, banner, cannon, connector, curtain, door, dry,
                    fence_run, fort_face, lantern_post, merlons, rafter_ends, stair_shape)
from buildspec import Piece

H = 8                 # rows 0..7: walkway top y 4, parapet y 5, merlons y 6, caps y 7
HOARD_Z = 5

OUTS = {"wall": ("wall_east_out", "wall_west_out"), "flank": ("flank_east_out", "flank_west_out"),
        "back": ("back_east_out", "back_west_out")}
TITLES = {"wall": "Shore", "flank": "Flank", "back": "Back"}


class At:
    """``piece`` seen with its origin moved to (dx, dy, dz): lets the section fittings build a stretch of curtain that
    does not start at the box's z 0 or y 0 (the bastion's line behind its projection, on footings)."""

    def __init__(self, piece, dx=0, dy=0, dz=0):
        self.p, self.dx, self.dy, self.dz = piece, dx, dy, dz
        sx, sy, sz = piece.size
        self.size = (sx - dx, sy - dy, sz - dz)
        self.palette = piece.palette

    def put(self, x, y, z, key):
        self.p.put(x + self.dx, y + self.dy, z + self.dz, key)

    def fill(self, x0, y0, z0, x1, y1, z1, key):
        self.p.fill(x0 + self.dx, y0 + self.dy, z0 + self.dz, x1 + self.dx, y1 + self.dy, z1 + self.dz, key)

    def clear(self, x, y, z):
        self.p.clear(x + self.dx, y + self.dy, z + self.dz)

    def get(self, x, y, z):
        return self.p.get(x + self.dx, y + self.dy, z + self.dz)


def name(state, kind):
    """The piece name of a wall ``kind`` in ``state``: the shore's straight is ST3's ``wall``."""
    if kind == "straight":
        return "wall" if state == "wall" else f"{state}_wall"
    return f"{state}_{kind}"


def _piece(state, kind, size, title):
    return Piece(f"navy_outpost_{name(state, kind)}", f"Navy Outpost {TITLES[state]} {title}", size, PALETTE)


def _ins_outs(p, state, length, y=0, dz=0):
    east_out, west_out = OUTS[state]
    connector(p, 0, y, EAST_Z + dz, "wall_east_in", "west", "minecraft:stone_bricks")
    connector(p, length - 1, y, EAST_Z + dz, east_out, "east", "minecraft:stone_bricks")
    connector(p, 0, y, WEST_Z + dz, west_out, "west", "minecraft:stone_bricks")
    connector(p, length - 1, y, WEST_Z + dz, "wall_west_in", "east", "minecraft:stone_bricks")


def gun(p, x, facing="north"):
    """An embrasure gun on the walkway (the parapet gap is the caller's): a dark oak deck x-1..x+1, z 1..3, the cannon
    (master at z 1, muzzle north), a powder barrel and a shot locker against the parapet either side of the muzzle."""
    p.fill(x - 1, WALK, 1, x + 1, WALK, 3, "dark_oak")
    cannon(p, x, WALK + 1, 1, facing)
    p.put(x - 1, WALK + 1, 1, "pirates_n_ships:cargo_barrel")
    p.put(x + 1, WALK + 1, 1, "minecraft:barrel[facing=up,open=false]")


def landward(p, x0, x1, ladder=None, hoard=True, skip=(), lantern=None):
    """The landward side of a straight stretch x0..x1: a gravel path along the wall's foot (z 5..6, a cobbled edge),
    the timber hoarding level with the walkway (dark oak brackets, spruce deck slabs, a fence rail), a ladder up to it
    and a lantern on the rail beside the ladder's head."""
    p.fill(x0, 0, 5, x1, 0, 6, "gravel")
    p.fill(x0, 0, 5, x1, 0, 5, "cobble")
    if not hoard:
        return
    cells = [x for x in range(x0, x1 + 1) if x not in skip]
    rafter_ends(p, [(x, HOARD_Z) for x in cells if (x - x0) % 2 == 0 and x != ladder], WALK - 1, "south")
    for x in cells:
        if x != ladder:
            p.put(x, WALK, HOARD_Z, "spruce_slab_top")
    fence_run(p, [(x, HOARD_Z) for x in cells if x != ladder], WALK + 1)
    if ladder is not None:
        for y in range(1, WALK + 1):
            p.put(ladder, y, 5, "ladder_s")
        if lantern is not None:
            p.put(lantern, WALK + 2, HOARD_Z, "lantern")


def niche(p, x0, z=4):
    """An arched store niche x0..x0+2 in the landward face of the body: powder and two crates of shot."""
    for x in range(x0, x0 + 3):
        for y in (1, 2):
            p.clear(x, y, z)
    p.put(x0, 2, z, stair_shape("stone_brick", "west", "top"))
    p.put(x0 + 2, 2, z, stair_shape("stone_brick", "east", "top"))
    p.put(x0 + 1, 3, z, "chiseled")
    p.put(x0, 1, z, "pirates_n_ships:cargo_crate")
    p.put(x0 + 1, 1, z, "minecraft:barrel[facing=north,open=false]")
    p.put(x0 + 2, 1, z, "pirates_n_ships:cargo_crate")


def finish(p, state, kind, length, description, notes, y=0, dz=0, ground=0):
    age(p, ground=ground)
    _ins_outs(p, state, length, y, dz)
    dry(p)
    p.emit(description, notes)


# ---------------------------------------------------------------------------------------------- straights


def straight(state):
    """The 7 long wall with one gun: ST3's ``wall`` in another state."""
    L = 7
    p = _piece(state, "straight", (L, H, 7), "Curtain Wall")
    curtain(p, 0, L - 1, gaps=(3,), slits=(1, L - 2))
    gun(p, 3)
    niche(p, 1)
    landward(p, 0, L - 1, ladder=5, lantern=6)
    finish(p, state, "straight", L, "stone curtain wall with a cannon, a store niche and a timber hoarding",
           ["Sea (outside) to the north; walkway top y 4; cannon master [3, 5, 1].", f"State {state}."])


def long(state):
    """An 11 long wall with two guns, three arrow slits, a store niche and a bench under the hoarding."""
    L = 11
    p = _piece(state, "long", (L, H, 7), "Long Curtain Wall")
    curtain(p, 0, L - 1, gaps=(3, 7), pilasters=(0, 5, L - 1), slits=(1, 9))
    gun(p, 3)
    gun(p, 7)
    niche(p, 1)
    landward(p, 0, L - 1, ladder=9, lantern=10)
    for x in (5, 6, 7):            # a bench of spruce stairs against the landward face
        p.put(x, 1, 5, stair_shape("spruce", "north"))
    p.put(4, 1, 5, "minecraft:barrel[facing=up,open=false]")
    p.put(4, 2, 5, "lantern")
    finish(p, state, "long", L, "a long curtain wall with two cannons, arrow slits, a store niche and a bench",
           ["Two embrasures with cannons at x 3 and 7; walkway clear on z 3..4.", f"State {state}."])


def sally_port(state):
    """A 9 long wall pierced at ground level by a sally port: an arched passage through the body with a spruce door
    in a stone plug at its middle, a chiseled keystone over the outer arch, a gun over the passage, lanterns at the
    inner mouth."""
    L = 9
    p = _piece(state, "sally_port", (L, H, 7), "Sally Port")
    curtain(p, 0, L - 1, gaps=(4,), pilasters=(0, 2, 6, L - 1), slits=(1, 7))
    for z in range(0, 5):                  # the passage x 3..5, y 1..3, arched corners
        for y in range(1, 4):
            for x in range(3, 6):
                p.clear(x, y, z)
        p.put(3, 3, z, stair_shape("stone_brick", "west", "top"))
        p.put(5, 3, z, stair_shape("stone_brick", "east", "top"))
    p.fill(3, 0, 0, 5, 0, 4, "andesite")
    p.put(4, WALK, 0, "chiseled")          # the keystone over the outer arch
    for x in (3, 5):                       # the plug with the door, a grille over it
        p.fill(x, 1, 2, x, 2, 2, "bricks")
    door(p, 4, 1, 2, facing="north", wood="spruce")
    p.put(4, 3, 2, "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]")
    gun(p, 4)                              # a gun over the passage, its embrasure above the keystone
    landward(p, 0, L - 1, ladder=7, skip=(), lantern=8)
    p.fill(3, 0, 5, 5, 0, 6, "andesite")
    lantern_post(p, 2, 1, 6, height=1)
    lantern_post(p, 6, 1, 6, height=1)
    finish(p, state, "sally_port", L, "a curtain wall with a sally port: an arched passage with a door through the wall",
           ["Passage x 3..5 under the walkway; the door at [4, 1, 2] faces out (north).", f"State {state}."])


def stair(state):
    """A 9 long wall with a gun and a stone stair down from the walkway to the court (z 5..6, descending east)."""
    L = 9
    p = _piece(state, "stair", (L, H, 7), "Wall with a Stair")
    curtain(p, 0, L - 1, gaps=(6,), slits=(1, 3))
    gun(p, 6)
    landward(p, 0, L - 1, hoard=False)
    for i, x in enumerate(range(2, 6)):    # steps from the walkway (y 4) down to the ground, two wide
        y = WALK - i
        for z in (5, 6):
            if y > 1:
                p.fill(x, 1, z, x, y - 1, z, "bricks")
            p.put(x, y, z, stair_shape("stone_brick", "west"))
    p.fill(0, 1, 5, 1, WALK, 6, "bricks")  # the landing's pier under the top of the stair
    p.fill(0, WALK, 5, 1, WALK, 6, "andesite")
    fence_run(p, [(x, 6) for x in (0, 1)], WALK + 1)
    p.put(1, WALK + 2, 6, "lantern")
    p.put(7, 1, 5, "pirates_n_ships:cargo_crate")
    p.put(8, 1, 5, "minecraft:barrel[facing=up,open=false]")
    finish(p, state, "stair", L, "a curtain wall with a cannon and a stone stair down to the court",
           ["Stair on z 5..6 from the walkway at x 2 down to the path at x 6.", f"State {state}."])


# ---------------------------------------------------------------------------------------------- the bastion

BF, BP = 3, 5          # the bastion's footing rows and its projection's depth


def bastion(state):
    """An 11 long wall with a bastion projecting outward (north) from the line: a 7 wide gun platform level with the
    walkway with two cannons over the outer face, merlons round it, the fort's skin on its three faces down to footings
    three rows under the walls' foundation row (a bastion on the shore stands in the water). The line runs behind it
    (z 5..9) on the same footings, the hoarding and a ladder landward. Connectors on y 3 (the walls' foundation row),
    rows z 7 and 8."""
    L = 11
    p = _piece(state, "bastion", (L, H + BF, BP + 7), "Bastion")
    line = At(p, 0, BF, BP)
    p.fill(0, 0, BP, L - 1, BF - 1, BP + 4, "bricks")               # footings under the line
    curtain(line, 0, L - 1, pilasters=(0, 2, 8, L - 1), slits=(1, 9))
    # the projection x 2..8, z 0..4: footings, a solid core, the skin on three faces, the platform on top
    top = BF + WALK
    p.fill(3, 0, 1, 7, top - 1, BP - 1, "bricks")
    p.fill(3, BF, BP, 7, top - 1, BP, "bricks")             # the line's skin behind the projection, solid
    fort_face(p, [(x, 0) for x in range(2, 9)], "south", pilasters={(2, 0), (5, 0), (8, 0)}, corbel=top)
    fort_face(p, [(2, z) for z in range(1, BP)], "east", pilasters={(2, BP - 1)}, corbel=top)
    fort_face(p, [(8, z) for z in range(1, BP)], "west", pilasters={(8, BP - 1)}, corbel=top)
    for x in (2, 5, 8):
        p.put(x, top, 0, "andesite")
    p.fill(3, top, 1, 7, top, BP, "andesite")
    for x in range(3, 8):                   # open the line's parapet onto the platform
        for y in (top + 1, top + 2, top + 3):
            p.clear(x, y, BP)
        p.put(x, top, BP, "andesite")
    # the platform's parapet, embrasures for two guns, merlons, the guns on a deck
    ring = [(x, 0) for x in range(2, 9)] + [(2, z) for z in range(1, BP)] + [(8, z) for z in range(1, BP)]
    gaps = {(4, 0), (6, 0)}
    for x, z in ring:
        if (x, z) not in gaps:
            p.put(x, top + 1, z, "bricks")
    merlons(p, [(x, z) for x, z in ring if (x, z) not in gaps and (x + z) % 2 == 1], top + 2)
    p.fill(3, top, 1, 7, top, 2, "dark_oak")
    cannon(p, 4, top + 1, 1, "north")
    cannon(p, 6, top + 1, 1, "north")
    p.put(5, top + 1, 1, "minecraft:barrel[facing=up,open=false]")   # the shot locker between the muzzles
    p.put(3, top + 1, 3, "pirates_n_ships:cargo_barrel")
    p.put(7, top + 1, 3, "pirates_n_ships:cargo_crate")
    for x in (2, 8):
        p.put(x, top + 2, BP - 1, "bricks")
        p.put(x, top + 3, BP - 1, "lantern")
    # landward: the path, the hoarding and a ladder, a store niche
    landward(line, 0, L - 1, ladder=5, lantern=6)
    niche(line, 1)
    finish(p, state, "bastion", L, "a curtain wall with a bastion: a projecting gun platform with two cannons",
           [f"Connectors on y {BF} (the walls' foundation row), z {BP + EAST_Z} and {BP + WEST_Z}; footings y 0..{BF - 1}.",
            f"State {state}."], y=BF, dz=BP, ground=BF)


# ---------------------------------------------------------------------------------------------- angles


def angle(run, state):
    """A salient angle (7x7): the run turns 90 degrees inland. East run: in from the west face on z 2, the wall's outer
    skin turns round the north-east corner and the run leaves through the south face at x 4 (its outer skin on x 6);
    the west run mirrors it. A gun over the outer face, a lantern on the corner merlon, the ladder in the inner corner."""
    east = run == "east"
    out_name = OUTS[{"wall": "flank", "flank": "back"}[state]][0 if east else 1]
    p = _piece(state, f"angle_{run}", (7, H, 7), f"Angle ({run})")

    def X(x):                 # the east piece's x, mirrored for the west one
        return x if east else 6 - x

    def fx(x0, x1):
        a, b = X(x0), X(x1)
        return min(a, b), max(a, b)

    # the body: the run's stretch (z 1..4) and the turned stretch (x 2..5 of the east piece, z 5..6), walkway on top
    a, b = fx(0, 5)
    p.fill(a, 0, 1, b, WALK - 1, 4, "bricks")
    p.fill(a, WALK, 1, b, WALK, 4, "andesite")
    a, b = fx(2, 5)
    p.fill(a, 0, 5, b, WALK - 1, 6, "bricks")
    p.fill(a, WALK, 5, b, WALK, 6, "andesite")
    # the outer skin: along the north face and down the outer side face, the corner a full pilaster
    north = [(X(x), 0) for x in range(0, 6)]
    side = [(X(6), z) for z in range(1, 7)]
    fort_face(p, north, "south", pilasters={(X(0), 0)})
    fort_face(p, side, "west" if east else "east", pilasters={(X(6), 6), (X(6), 3)})
    p.fill(X(6), 0, 0, X(6), WALK, 0, "bricks")
    p.put(X(6), 1, 0, "chiseled")
    for c in [(X(0), 0), (X(6), 0), (X(6), 3), (X(6), 6)]:
        p.put(c[0], WALK, c[1], "andesite")
    ring = north + [(X(6), 0)] + side
    gaps = {(X(3), 0)}
    for x, z in ring:
        if (x, z) not in gaps:
            p.put(x, WALK + 1, z, "bricks")
    merlons(p, [(x, z) for x, z in ring if (x, z) not in gaps and (x, z) != (X(6), 0) and (x + z) % 2 == 0], WALK + 2)
    p.put(X(6), WALK + 2, 0, "bricks")      # the corner merlon carries a lantern
    p.put(X(6), WALK + 3, 0, "lantern")
    for z in (2, 4):
        arrow_slit(p, X(5), 2, z, "z")
    gun(p, 3)
    # the inner corner: a gravel yard, the ladder up the turned stretch's inner face, a lantern post, stores
    a, b = fx(0, 1)
    p.fill(a, 0, 5, b, 0, 6, "gravel")
    for y in range(1, WALK + 1):
        p.put(X(1), y, 5, f"minecraft:ladder[facing={'west' if east else 'east'}]")
    lantern_post(p, X(0), 1, 6, height=1)
    p.put(X(0), 1, 5, "minecraft:barrel[facing=up,open=false]")
    age(p)
    if east:
        connector(p, 0, 0, EAST_Z, "wall_east_in", "west", "minecraft:stone_bricks")
        connector(p, 4, 0, 6, out_name, "south", "minecraft:stone_bricks")
    else:
        connector(p, 6, 0, WEST_Z, "wall_west_in", "east", "minecraft:stone_bricks")
        connector(p, 3, 0, 6, out_name, "south", "minecraft:stone_bricks")
    dry(p)
    p.emit(f"a salient angle of the curtain wall ({run} run): the wall turns inland round a corner with a lantern",
           [f"In from the {'west' if east else 'east'} face; out through the south face ({out_name}).", f"State {state}."])


def step(run):
    """A re-entrant angle on the flank (9x7): the run turns back outward (the east run heading inland turns east again),
    stepping the trace out; its out connector names the shore state's pool again. East piece: in from the west face on
    z 2, the line's outer skin on z 0 for x 0..2 turns north at x 2 and the run leaves through the north face at x 4
    (the next piece's skin on x 2, its body x 3..6, its landward side x 7..8)."""
    east = run == "east"
    W = 9
    p = _piece("flank", f"step_{run}", (W, H, 7), f"Step ({run})")

    def X(x):
        return x if east else W - 1 - x

    def fx(x0, x1):
        a, b = X(x0), X(x1)
        return min(a, b), max(a, b)

    a, b = fx(0, 6)
    p.fill(a, 0, 1, b, WALK - 1, 4, "bricks")
    p.fill(a, WALK, 1, b, WALK, 4, "andesite")
    a, b = fx(3, 6)
    p.fill(a, 0, 0, b, WALK - 1, 0, "bricks")
    p.fill(a, WALK, 0, b, WALK, 0, "andesite")
    skin = [(X(x), 0) for x in range(0, 3)]
    fort_face(p, skin, "south", pilasters={(X(0), 0), (X(2), 0)})
    for c in [(X(0), 0), (X(2), 0)]:
        p.put(c[0], WALK, c[1], "andesite")
    for x, z in skin:
        p.put(x, WALK + 1, z, "bricks")
    merlons(p, [(X(0), 0), (X(2), 0)], WALK + 2)
    arrow_slit(p, X(1), 2, 1, "x")
    # landward: the court wraps round the inner corner (z 5..6 along the line, x 7..8 beside the turned stretch)
    a, b = fx(0, 6)
    p.fill(a, 0, 5, b, 0, 6, "gravel")
    a, b = fx(7, 8)
    p.fill(a, 0, 0, b, 0, 6, "gravel")
    p.fill(X(7), 0, 0, X(7), 0, 4, "cobble")
    rafter_ends(p, [(X(x), HOARD_Z) for x in (0, 2, 4)], WALK - 1, "south")
    hoard = [(X(x), HOARD_Z) for x in range(0, 7) if x != 5] + [(X(7), z) for z in range(0, 5)]
    for x, z in hoard:
        p.put(x, WALK, z, "spruce_slab_top")
    fence_run(p, hoard + [(X(7), HOARD_Z)], WALK + 1)
    p.put(X(7), WALK, HOARD_Z, "spruce_slab_top")
    rafter_ends(p, [(X(7), z) for z in (0, 2, 4)], WALK - 1, "east" if east else "west")
    for y in range(1, WALK + 1):
        p.put(X(5), y, 5, "ladder_s")
    p.put(X(8), 1, 6, "pirates_n_ships:cargo_crate")
    p.put(X(8), 1, 5, "minecraft:barrel[facing=up,open=false]")
    p.put(X(8), 2, 5, "lantern")
    p.put(X(6), WALK + 1, 0, "lantern")     # a lantern on the walkway's outer corner of the turned stretch
    age(p)
    if east:
        connector(p, 0, 0, EAST_Z, "wall_east_in", "west", "minecraft:stone_bricks")
        connector(p, 4, 0, 0, "wall_east_out", "north", "minecraft:stone_bricks")
    else:
        connector(p, W - 1, 0, WEST_Z, "wall_west_in", "east", "minecraft:stone_bricks")
        connector(p, 3, 0, 0, "wall_west_out", "north", "minecraft:stone_bricks")
    dry(p)
    p.emit(f"a re-entrant angle on the flank ({run} run): the trace steps outward again",
           ["Out through the north face into the shore state's pool (wall_*_out)."])


# ---------------------------------------------------------------------------------------------- towers


def round_tower():
    """A round tower ending either run (7x7): a drum of stone bricks (rounded 5x5) between two stubs of the curtain on
    its side faces (the walkway runs on to a door at the landing), a corbel table carrying the full 7 wide roof
    platform (y 9) with merlons, lanterns and a blue banner; a ladder from the ground floor through the landing to a
    hatch in the roof, a door landward."""
    S, TOP = 7, 12
    ROOF = 9
    p = Piece("navy_outpost_round_tower", "Navy Outpost Round Tower", (S, TOP, S), PALETTE)

    def disk(r2):
        return {(x, z) for x in range(S) for z in range(S) if (x - 3) ** 2 + (z - 3) ** 2 <= r2}

    drum, outer = disk(6.5), disk(10.5)

    def rim(cells):
        return {(x, z) for x, z in cells if any((x + dx, z + dz) not in cells
                                                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}

    shell = rim(drum)
    for x, z in sorted(drum):
        p.put(x, 0, z, "bricks")
    for x, z in sorted(shell):
        p.fill(x, 1, z, x, ROOF - 1, z, "bricks")
        p.put(x, 1, z, "chiseled" if (x + z) % 3 == 0 else "bricks")
        p.put(x, WALK, z, "andesite")
    for x, z in sorted(drum - shell):
        p.put(x, 0, z, "spruce")
        p.put(x, WALK, z, "spruce")
    # the curtain's stubs on both side faces (x 0 and x 6), so the walkway runs on to the landing's doors
    for x in (0, S - 1):
        p.fill(x, 0, 1, x, WALK - 1, 4, "bricks")
        p.fill(x, WALK, 1, x, WALK, 4, "andesite")
        p.fill(x, 0, 0, x, WALK, 0, "bricks")
        p.put(x, WALK + 1, 0, "bricks")
        merlons(p, [(x, 0)], WALK + 2)
    # the corbel table and the roof platform over the outer disk, merlons round its rim
    for x, z in sorted(outer - drum):
        if p.get(x, ROOF - 1, z) is None:
            facing = "south" if z < 3 and (x, z + 1) in drum else "north" if z > 3 and (x, z - 1) in drum else \
                "east" if x < 3 else "west"
            p.put(x, ROOF - 1, z, stair_shape("stone_brick", facing, "top"))
    for x, z in sorted(outer):
        p.put(x, ROOF, z, "andesite")
    edge = rim(outer)
    merlons(p, sorted((x, z) for x, z in edge if (x + z) % 2 == 0), ROOF + 1)
    for x, z in ((0, 3), (6, 3)):
        p.put(x, ROOF + 1, z, "bricks")
        p.put(x, ROOF + 2, z, "lantern")
    # the ladder (against the north shell) through the landing to the roof hatch, the doors
    for y in range(1, ROOF + 1):
        p.put(3, y, 2, "ladder_s")
    door(p, 1, WALK + 1, 3, facing="west")
    door(p, 5, WALK + 1, 3, facing="east", hinge="right")
    door(p, 3, 1, 5, facing="south")
    p.put(3, 0, 6, "cobble")
    banner(p, 3, 6, 6, "south")
    # barred slits, stores and lights
    for x, y, z, axis in ((2, 2, 1, "x"), (4, 6, 1, "x"), (1, 2, 4, "z"), (5, 6, 2, "z"), (2, 6, 5, "x")):
        arrow_slit(p, x, y, z, axis, grille=True)
    p.put(2, 1, 3, "minecraft:barrel[facing=up,open=false]")
    p.put(2, 2, 3, "lantern")
    p.put(4, 1, 4, "pirates_n_ships:cargo_crate")
    p.put(4, WALK + 1, 4, "pirates_n_ships:cargo_barrel")
    p.put(2, WALK + 1, 4, "minecraft:chest[facing=east,type=single,waterlogged=false]")
    p.put(3, ROOF - 1, 3, "lantern_hanging")
    age(p)
    connector(p, 0, 0, EAST_Z, "wall_east_in", "west", "minecraft:stone_bricks")
    connector(p, S - 1, 0, WEST_Z, "wall_west_in", "east", "minecraft:stone_bricks")
    dry(p)
    p.emit("a round tower: a stone drum under a corbelled, crenellated roof platform, ending a wall run",
           ["Ends either run: wall_east_in [0, 0, 2], wall_west_in [6, 0, 3]; landing doors at z 3; roof y 9."])


def battery():
    """A squat battery ending either run (5x7): the curtain's body widened into a low gun platform with one cannon over
    the outer face, merlons on all three outer sides (the walkway stays open on z 3..4 at both ends), heaps of shot,
    and landward a timber powder hut under the platform's edge."""
    L, D = 5, 7
    p = Piece("navy_outpost_battery", "Navy Outpost Battery", (L, H, D), PALETTE)
    curtain(p, 0, L - 1, gaps=(2,), pilasters=(0, L - 1), slits=())
    gun(p, 2)
    for x in (0, L - 1):                  # parapets on the end faces, open where the walkway runs on (z 3..4)
        for z in (1, 2):
            p.put(x, WALK + 1, z, "bricks")
        merlons(p, [(x, 1)], WALK + 2)
    for x, z in ((1, 4), (3, 4)):         # shot heaps
        p.put(x, WALK + 1, z, "minecraft:heavy_core[waterlogged=false]")
    # the powder hut on the landward side: spruce boards under a slab roof, a door, kegs inside
    p.fill(0, 0, 5, L - 1, 0, 6, "gravel")
    p.fill(0, 1, 5, L - 1, 3, 6, "spruce")
    p.fill(0, 1, 6, 0, 3, 6, "dark_post")
    p.fill(L - 1, 1, 6, L - 1, 3, 6, "dark_post")
    p.clear(1, 1, 5)
    p.clear(2, 1, 5)
    p.clear(3, 1, 5)
    p.clear(1, 2, 5)
    p.clear(2, 2, 5)
    p.clear(3, 2, 5)
    door(p, 2, 1, 6, facing="south")
    p.put(1, 1, 5, "pirates_n_ships:cargo_barrel")
    p.put(3, 1, 5, "pirates_n_ships:cargo_barrel")
    p.put(1, 2, 5, "lantern")
    p.fill(0, WALK, 5, L - 1, WALK, 6, "spruce_slab_top")
    fence_run(p, [(x, 6) for x in range(L)], WALK + 1)
    age(p)
    connector(p, 0, 0, EAST_Z, "wall_east_in", "west", "minecraft:stone_bricks")
    connector(p, L - 1, 0, WEST_Z, "wall_west_in", "east", "minecraft:stone_bricks")
    dry(p)
    p.emit("a squat battery ending a wall run: a gun platform with merlons and a timber powder hut",
           ["Ends either run: wall_east_in [0, 0, 2], wall_west_in [4, 0, 3]; walkway open on z 3..4."])


BUILDERS = {"straight": straight, "long": long, "sally_port": sally_port, "stair": stair, "bastion": bastion}
