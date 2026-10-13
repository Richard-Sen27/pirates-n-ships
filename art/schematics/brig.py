"""Generates the brig BuildSpec (SHIP1, reworked in SHIP2; bow toward -Z) and prints it as JSON.

A two-masted brig, 9 wide and 32 long over all (hull 27, bowsprit 4, the name boards one more), dark oak and spruce
like the starter sloop. Decks from the keel up: the keel (y 0), the bottom (y 1), the hold (y 2..4, three watertight
compartments between two bulkheads), the lower gun deck (floor y 5, headroom y 6..7, four guns a side, the great cabin
aft of z 26), the weather deck (y 8) and, aft, the quarterdeck (y 9) one row over it. Ballast stones lie in the bottom
(y 1) and beside the keel log (y 0).
The design and the mass budget are written down in art/README.md, "The brig (SHIP1)" and "The brig (SHIP2)".

SHIP2 (the human's first sail, 2026-10-13: "missing cannons (at least when ordered) ... looks very bulky ... not enough
of the ship under water ... gets strongly tilted to one side"): the hull is rounder below the water (the bottom and the
bilge narrower than the sides) with ballast stones in the bottom and beside the keel, so she floats deeper and with
her centre of mass low enough to stand upright; the topsides tumble home one block above the gun deck (the weather deck
and everything on it one block in from the hull side); the stripes and the rail rise toward the bow and the stern (the
sheer), the stem is a log from the forefoot to the knighthead with the bowsprit stepped on its head, the counter stern
overhangs the run, and the ordered brig carries her eight guns and their shot lockers.

``brig_cells(armed)`` builds both variants: run on its own this file prints the orderable brig (``brig``);
``navy_brig_armed.py`` and ``pirate_brig_armed.py`` print the armed one, which adds only the crow's nest, the ratline
fittings (railing latches and mast hoops; the nets between them are strung when the template is placed, from the
``nets`` list of its ship template definition, see ``NETS``) and the two swivel guns.
"""
import json

CX = 4          # centre line
OFF = 4         # z offset of the hull (room for the bowsprit)
L = 27          # hull length
STERN = OFF + L - 1          # 30, the transom

# half-width of the hull at the gun deck, by hull z (0 = stem)
HW = [0, 1, 1, 2, 2, 3, 3, 3] + [4] * 15 + [3, 3, 3, 3]
assert len(HW) == L
# the underwater body (the hold rows) is fuller toward the ends than the gun deck: more waterplane and displacement low
LOW_HW = [0, 1, 2, 2, 3, 3, 4, 4] + [4] * 15 + [4, 4, 3, 3]
assert len(LOW_HW) == L
# the topsides tumble home: from the weather deck up the hull is at most this half-width
TOP_HW = 3

KEEL = 0
BOTTOM = 1
HOLD = (2, 3, 4)             # hold rows
GUN_FLOOR = 5                # lower gun deck floor
GUN_ROWS = (6, 7)            # lower gun deck headroom
DECK = 8                     # weather deck
QD = 25                      # first z of the cabin / quarterdeck
QD_FLOOR = 9                 # quarterdeck floor, one row over the weather deck (the great cabin under it on the gun deck)
CABIN_FRONT = 26             # the bulkhead between the gun deck and the great cabin (z 27..29)
BULKHEADS = (14, 23)         # z of the two hold bulkheads: fore hold, main hold, aft hold

# the underwater body: per row the half-width reduction against LOW_HW (HW for the bottom) and the hull z range (the stem
# rakes forward, the run rises into the counter); rows from the gun deck floor up follow HW. The keel row is three wide
# amidships (KEEL_RED from HW): the keel log on the centre line with ballast stones either side of it.
BODY = {BOTTOM: (1, 2, 23), 2: (0, 1, 24), 3: (0, 1, 25), 4: (0, 0, 25)}
KEEL_RED = 3

FMZ, MMZ = 11, 19            # fore and main mast
FORE_TOP, MAIN_TOP = 23, 25  # mast heads
# yards (yard rule, PND3/TPL3): one block forward of the mast; (y, half length) from the lowest
FORE_YARDS = ((12, 4), (17, 3), (21, 2))
MAIN_YARDS = ((12, 4), (18, 3), (23, 2))

GUN_ZS = (13, 17, 21, 25)    # gun deck guns, both sides
GUN_Y = 6
LOCKERS = [(1, 6, 15), (1, 6, 23), (7, 6, 15), (7, 6, 23)]   # shot lockers on the gun deck, two guns each
NEST = (CX, MAIN_TOP + 1, MMZ)
HOOPS = [(CX - 1, MAIN_TOP, MMZ, "west"), (CX + 1, MAIN_TOP, MMZ, "east")]
# railing latches on the quarterdeck's side rails, as far forward as they go (SHIP2: a strand climbing forward more
# steeply than 8 in 14 leaves its latch through the north face into the next latch, and its net is not strung)
LATCH_ZS = (25, 26, 27)
# on the taffrail's quarters, clear of the latches (SHIP2: a swivel beside the aftmost latch blocks its net's sheet)
SWIVELS = [(2, QD_FLOOR + 2, STERN), (6, QD_FLOOR + 2, STERN)]

blocks = {}


def put(x, y, z, b):
    blocks[(x, y, z)] = b


def fit(x, y, z, b):
    """Puts a fitting into a free cell (never over the hull or another fitting)."""
    assert (x, y, z) not in blocks, ((x, y, z), b, blocks[(x, y, z)])
    blocks[(x, y, z)] = b


def hw(zs, y):
    """Half-width of row y at z (-1 outside the hull)."""
    z = zs - OFF
    if not 0 <= z < L:
        return -1
    if y == KEEL:
        return max(0, HW[z] - KEEL_RED) if 1 <= z <= 23 else -1
    if y in BODY:
        red, z0, z1 = BODY[y]
        if not z0 <= z <= z1:
            return -1
        w = (HW if y == BOTTOM else LOW_HW)[z] - red
        return w if w >= 0 else -1
    if y >= DECK:
        return min(HW[z], TOP_HW)
    return HW[z]


def inside(x, y, zs):
    w = hw(zs, y)
    return w >= 0 and abs(x - CX) <= w


def edge(x, y, zs):
    return inside(x, y, zs) and any(
        not inside(x + dx, y, zs + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def outside_dirs(x, zs, y=DECK):
    """The horizontal directions in which the neighbour of (x, y, zs) lies outside the hull."""
    out = []
    for name, dx, dz in (("north", 0, -1), ("east", 1, 0), ("south", 0, 1), ("west", -1, 0)):
        if not inside(x + dx, y, zs + dz):
            out.append(name)
    return out


def sheer(zs):
    """How many rows the hull's stripes and the rail stand higher at z: the sheer rising toward the bow and stern."""
    if zs <= OFF + 1:
        return 2
    if zs <= OFF + 4:
        return 1
    if zs >= STERN - 1:
        return 1
    return 0


def side_block(y, zs):
    """The planking of the hull side: dark oak below the main wale, the wale, the gun stripe, the sheer strake."""
    r = y - sheer(zs)
    if r <= 4:
        return "lower"
    if r == 5:
        return "wale"
    if r == 6:
        return "band"
    return "top"


# ---------------------------------------------------------------- hull

for zs in range(OFF, STERN + 1):
    for y in range(KEEL, DECK + 1):
        for x in range(0, 9):
            if not inside(x, y, zs):
                continue
            if y == KEEL:
                put(x, y, zs, "keel" if x == CX else "ballast")
            elif y == BOTTOM or edge(x, y, zs):
                put(x, y, zs, side_block(y, zs))
            elif y == GUN_FLOOR or (y == DECK and zs < QD):
                put(x, y, zs, "deck")
            elif y in HOLD and zs in BULKHEADS:
                put(x, y, zs, "bulkhead")

# a bottom plank under every hollow cell whose cell below is outside the hull (SH1b): the run's and the entry's steps
for y in HOLD:
    for zs in range(OFF, STERN + 1):
        for x in range(0, 9):
            if inside(x, y, zs) and (x, y, zs) not in blocks and not inside(x, y - 1, zs):
                put(x, y - 1, zs, side_block(y - 1, zs))

# the stem: the foremost cell of every row on the centre line is a log standing up, from the forefoot to the knighthead
for y in range(BOTTOM, DECK + 1):
    zs = next(z for z in range(OFF, STERN + 1) if inside(CX, y, z))
    put(CX, y, zs, "stem")
# the deck edge (sheer strake) along the waist is a log, the tumblehome step outboard of it a stair turned inboard
for zs in range(OFF, STERN + 1):
    for x in range(0, 9):
        if edge(x, DECK, zs) and sheer(zs) == 0 and zs < QD:
            put(x, DECK, zs, "cap")
        if inside(x, DECK - 1, zs) and not inside(x, DECK, zs):
            put(x, DECK, zs, "tumble_e" if x < CX else "tumble_w")

# the bow's raised bulwark (the sheer): side planks one and two rows over the deck, the rail on top
for zs in range(OFF + 1, OFF + 5):
    for x in range(0, 9):
        if edge(x, DECK, zs):
            for k in range(1, sheer(zs) + 1):
                put(x, DECK + k, zs, "top")

# ---------------------------------------------------------------- quarterdeck and great cabin
# SHIP2: the quarterdeck is one row over the weather deck (it was three, over a cabin on the weather deck); the cabin is
# the great cabin aft on the gun deck under it, three rows high, behind a bulkhead at z 26, lit by the stern windows

for zs in range(QD, STERN + 1):
    for x in range(0, 9):
        if inside(x, DECK, zs):
            put(x, QD_FLOOR, zs, "cap" if edge(x, DECK, zs) else "deck")
for y in GUN_ROWS + (DECK,):
    for x in range(0, 9):
        if inside(x, y, CABIN_FRONT) and not edge(x, y, CABIN_FRONT):
            put(x, y, CABIN_FRONT, "top")

# cabin door, stern windows, side lights
put(CX, 6, CABIN_FRONT, "door_lower")
put(CX, 7, CABIN_FRONT, "door_upper")
for x in (2, 4, 6):
    put(x, 7, STERN, "stern_window")
for x in (1, 7):
    put(x, 7, 28, "pane")

# a stair up to the quarterdeck on each side of the break
STAIR_XS = (2, 6)
for x in STAIR_XS:
    fit(x, DECK + 1, QD - 1, "stair")

# ---------------------------------------------------------------- the companions: weather deck down to the gun deck
# a three-block opening in the weather deck (z 14..16) with a stair in the deck at z 17 and two below it, descending
# toward the bow onto the gun deck floor at z 14; a plank under the middle stair
for zs in (14, 15, 16):
    blocks.pop((CX, DECK, zs))
put(CX, DECK, 17, "stair_down")
fit(CX, 7, 16, "stair_down")
fit(CX, 6, 15, "stair_down")
fit(CX, 6, 16, "deck")

# the after companion (SHIP1 pathfinding: from the quarterdeck the crew's pathfinder runs out of nodes before it finds
# the gun deck's after half by way of the forward companion): the same opening at x 4, z 21..23, the stair in the deck
# at z 24 (at the cabin door), descending toward the bow onto the gun deck at z 21
for zs in (21, 22, 23):
    blocks.pop((CX, DECK, zs))
put(CX, DECK, 24, "stair_down")
fit(CX, 7, 23, "stair_down")
fit(CX, 6, 22, "stair_down")
fit(CX, 6, 23, "deck")

# ---------------------------------------------------------------- ballast
# ballast stones in the bottom (y 1) inside its outer planking and beside the keel log (y 0): the brig's centre of mass
# sits low enough that, with the hull's righting moment, she floats upright (art/README.md "The brig (SHIP2)"). The hold
# above keeps its three rows.
for zs in range(OFF, STERN + 1):
    for x in range(0, 9):
        if inside(x, BOTTOM, zs) and not edge(x, BOTTOM, zs):
            put(x, BOTTOM, zs, "ballast")

# hatches with ladders from the gun deck into each hold compartment (ladder on the mast or the bulkhead)
for zs in (FMZ + 1, MMZ + 1, BULKHEADS[1] + 1):
    put(CX, GUN_FLOOR, zs, "hatch")
    for y in HOLD:
        fit(CX, y, zs, "ladder")

# ---------------------------------------------------------------- rails


def rail_y(zs):
    return DECK + 1 + (sheer(zs) if zs < QD else 0)


def railing_cells():
    """(x, y, z) -> (kind, edges): the weather deck's railing family and the quarterdeck's rails."""
    out = {}
    for zs in range(OFF + 1, QD):
        for x in range(0, 9):
            if edge(x, DECK, zs):
                out[(x, rail_y(zs), zs)] = ("rail", outside_dirs(x, zs))
    for zs in range(QD, STERN + 1):
        for x in range(0, 9):
            if not inside(x, DECK, zs):
                continue
            dirs = outside_dirs(x, zs)
            if not dirs:
                continue
            kind = "rope" if zs == QD and not edge(x, DECK, zs) else "rail"
            out[(x, QD_FLOOR + 1, zs)] = (kind, dirs)
    # SHIP2: no rope railing along the break at all. The quarterdeck is one row over the weather deck, so the break is a
    # step, and the after companion's top stair (z 24) lies right under it: the companion is entered from the
    # quarterdeck. A one-block gap between rope railings is open floor to the crew's pathfinder but too narrow for a
    # walker (a gunner stuck at the helm pushing into the gap over the companion).
    for x in range(STAIR_XS[0], STAIR_XS[1] + 1):
        out.pop((x, QD_FLOOR + 1, QD), None)
    # no rope railing round the companion openings: the crew's pathfinder takes a rope railing's edge for open floor
    # (SHIP1: a walker stuck pushing against it beside the after companion)
    # a wide port (two ports side by side) and a gate on each side of the waist
    for zs in (13, 14):
        out[(1, DECK + 1, zs)] = ("port", ["west"])
        out[(7, DECK + 1, zs)] = ("port", ["east"])
    out[(1, DECK + 1, 17)] = ("gate", ["west"])
    out[(7, DECK + 1, 17)] = ("gate", ["east"])
    return out


RAILS = railing_cells()

# ---------------------------------------------------------------- bow, masts, rig

# the bowsprit, its heel stepped on the stem head, and the knighthead above it
STEM_HEAD = (CX, DECK + 1, OFF)
for zs in range(0, OFF + 1):
    put(CX, DECK + 1, zs, "sprit")
put(CX, DECK + 2, OFF, "stem")
fit(CX, DECK, OFF - 1, "figure")

for top, mz, yards in ((FORE_TOP, FMZ, FORE_YARDS), (MAIN_TOP, MMZ, MAIN_YARDS)):
    for y in range(BOTTOM, top + 1):
        put(CX, y, mz, "mast")
    for y, half in yards:
        for x in range(CX - half, CX + half + 1):
            put(x, y, mz - 1, "yard")

# jib on the fore mast (TPL3): head above the upper fore yard, clew below the lower, tack on the bowsprit
JIB_HEAD = (CX, FORE_YARDS[-1][0] + 1, FMZ - 1)
JIB_CLEW = (CX, FORE_YARDS[0][0] - 1, FMZ - 1)
JIB_TACK = (CX, DECK + 2, 1)
fit(*JIB_HEAD, "cleat_mast")
fit(*JIB_CLEW, "cleat_mast")
fit(*JIB_TACK, "cleat_floor")

# ---------------------------------------------------------------- stations, crew and gear

fit(CX, 9, 7, "capstan")
# the fore winch two abaft the mast (SHIP2): its hand's spot (the first free side, north first) is then the cell between
# the winch and the mast on the centre line, out of both walkways; beside the mast he stood in the starboard walkway,
# narrowed by the tumblehome, and a crew member walking forward to the capstan stuck behind him
fit(CX, 9, FMZ + 2, "winch")
fit(CX, 9, MMZ + 1, "winch")
fit(7, 6, MMZ, "pump")                     # gun deck, against the starboard side over the main hold: its intake reaches the hold
fit(CX, QD_FLOOR + 1, 27, "helm")
fit(CX, QD_FLOOR + 1, 29, "pedestal")
fit(CX, QD_FLOOR + 2, 29, "pole_bottom")
fit(CX, QD_FLOOR + 3, 29, "pole_middle")
fit(CX, QD_FLOOR + 4, 29, "pole_top")
fit(CX, 11, FMZ + 1, "bell_s")             # on the fore mast's after face, over the winch hand's head
# on the masts' after faces over the winches (SHIP2: the break lanterns went, the break being a step)
fit(CX, 12, FMZ + 1, "lantern_s")
fit(CX, 11, MMZ + 1, "lantern_s")
fit(1, 7, 12, "lantern_e")
fit(7, 7, 12, "lantern_w")
fit(1, 7, 19, "lantern_e")
fit(7, 7, 19, "lantern_w")
fit(2, 8, 27, "lantern_e")
fit(6, 8, 27, "lantern_w")

# galley at the fore end of the gun deck's waist
fit(1, 6, 12, "pantry")
fit(7, 6, 12, "water")

# hammocks: four in the fore berth (two fore and aft beside the hull at x 2 and 6 with a post at z 11, two across the
# bow at z 7 and 8 from the hull to a post), one across the gun deck forward of the after guns on the port side (hull
# and a post, z 24; the starboard side stays open: the after companion's foot closes x 4 at z 23, so z 24..25, the
# cabin door and the aft hatch are reached by x 5), three fore and aft in the great cabin (from its bulkhead to the
# crates and the barrel at z 29, the lane x 3..4 to the chest left free): eight
for x in (2, 6):
    fit(x, 6, 9, "ham_foot_s")
    fit(x, 6, 10, "ham_head_s")
    fit(x, 6, 11, "post")
for zs in (7, 8):
    fit(3, 6, zs, "ham_foot_e")
    fit(4, 6, zs, "ham_head_e")
    fit(5, 6, zs, "post")
fit(1, 6, 24, "ham_foot_e")
fit(2, 6, 24, "ham_head_e")
fit(3, 6, 24, "post")
for x in (2, 5, 6):
    fit(x, 6, 27, "ham_foot_s")
    fit(x, 6, 28, "ham_head_s")

# brig cell in the aft hold (down the aft hatch): bars across z 26 with a brig door in the middle, the cell abaft it
for y in HOLD:
    for x in range(1, 8):
        if x != CX:
            fit(x, y, 26, "bars")
fit(CX, 2, 26, "brig_door_lower")
fit(CX, 3, 26, "brig_door_upper")
fit(CX, 4, 26, "bulkhead")

# great cabin: crates (the cabin hammocks' after supports), chart table, sea chest
fit(2, 6, 29, "crate")
fit(3, 6, 29, "chart_table")
fit(CX, 6, 29, "chest")
fit(5, 6, 29, "barrel")
fit(6, 6, 29, "crate")

# holds: cargo
for x, zs, b in ((3, 9, "crate"), (5, 9, "crate"), (3, 10, "barrel"), (5, 10, "barrel"),
                 (2, 16, "crate"), (6, 16, "crate"), (2, 17, "water"), (6, 17, "barrel"), (2, 21, "crate"),
                 (6, 21, "crate")):
    fit(x, 2, zs, b)

# name boards on the transom, under the stern windows
for x in (3, 4, 5):
    fit(x, 6, STERN + 1, "plate_%d" % x)

# the guns (SHIP2: the ordered brig carries them too): a carriage cannon behind every gun port, its lid held open
for zs in GUN_ZS:
    for x, side in ((0, "w"), (8, "e")):
        step = 1 if x == 0 else -1
        assert blocks.get((x, GUN_Y, zs)) == "band", (x, zs, blocks.get((x, GUN_Y, zs)))
        put(x, GUN_Y, zs, "port_%s_open" % side)
        fit(x + step, GUN_Y, zs, "gun_" + side)
        fit(x + 2 * step, GUN_Y, zs, "gun_rear_" + side)
for cell in LOCKERS:
    assert blocks.get((cell[0], cell[1] - 1, cell[2])) == "deck", cell
    fit(*cell, "locker")


# ---------------------------------------------------------------- the railing family's block states

POST_PROP = {"north": "post_nw", "east": "post_ne", "south": "post_se", "west": "post_sw"}
CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}
STEP = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}
FAMILY = {"rail": "railing", "port": "railing", "gate": "railing", "rope": "rope"}


def carries(cell, edge_dir):
    kind, dirs = RAILS.get(cell, (None, []))
    return kind is not None and edge_dir in dirs


def continues(self_kind, cell, edge_dir):
    kind = RAILS.get(cell, (None, []))[0]
    return kind is not None and FAMILY[kind] == FAMILY[self_kind] and carries(cell, edge_dir)


def posts(cell):
    """RailingRules.posts with RailingBlock.withPosts' neighbour rule (a run goes on only into its own kind)."""
    kind, dirs = RAILS[cell]
    x, y, z = cell
    out = set()
    for key in ("north", "east", "south", "west"):
        other = CCW[key]
        a, b = key in dirs, other in dirs
        if a and b:
            out.add(key)
        elif a:
            dx, dz = STEP[other]
            if not continues(kind, (x + dx, y, z + dz), key):
                out.add(key)
        elif b:
            dx, dz = STEP[key]
            if not continues(kind, (x + dx, y, z + dz), other):
                out.add(key)
    return out


def rail_state(cell):
    kind, dirs = RAILS[cell]
    x, y, z = cell
    if kind == "gate":
        return "pirates_n_ships:dark_oak_railing_gate[facing=%s,hinge=left,open=false,paired=false,waterlogged=false]" % dirs[0]
    if kind == "port":
        facing = dirs[0]
        # looking outboard the left end is the corner keyed by the facing, the right the one keyed clockwise of it
        left_dir = CCW[facing]                       # the edge's end toward the facing's counter-clockwise side
        right_dir = {v: k for k, v in CCW.items()}[facing]
        def port_at(d):
            dx, dz = STEP[d]
            n = RAILS.get((x + dx, y, z + dz))
            return n is not None and n[0] == "port" and n[1][0] == facing
        return ("pirates_n_ships:dark_oak_railing_port[facing=%s,post_left=%s,post_right=%s,waterlogged=false]"
                % (facing, str(not port_at(left_dir)).lower(), str(not port_at(right_dir)).lower()))
    block = "pirates_n_ships:dark_oak_railing" if kind == "rail" else "pirates_n_ships:dark_oak_rope_railing"
    p = posts(cell)
    props = ["%s=%s" % (d, str(d in dirs).lower()) for d in ("east", "north", "south", "west")]
    props += ["%s=%s" % (POST_PROP[k], str(k in p).lower()) for k in ("east", "north", "south", "west")]
    props.append("waterlogged=false")
    return block + "[" + ",".join(sorted(props)) + "]"


RAIL_KEYS = {}
for cell in sorted(RAILS):
    state = rail_state(cell)
    key = RAIL_KEYS.setdefault(state, "rail_%d" % len(RAIL_KEYS))
    assert cell not in blocks, (cell, blocks[cell])
    put(*cell, key)


palette = {
    "lower": "minecraft:dark_oak_planks",
    "wale": "minecraft:stripped_dark_oak_log[axis=z]",
    "band": "minecraft:spruce_planks",
    "top": "minecraft:dark_oak_planks",
    "cap": "minecraft:stripped_dark_oak_log[axis=z]",
    "tumble_e": "minecraft:dark_oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
    "tumble_w": "minecraft:dark_oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]",
    "keel": "minecraft:stripped_dark_oak_log[axis=z]",
    "stem": "minecraft:stripped_dark_oak_log[axis=y]",
    "ballast": "minecraft:cobbled_deepslate",
    "deck": "minecraft:spruce_planks",
    "bulkhead": "minecraft:dark_oak_planks",
    "pane": "minecraft:glass_pane",
    "door_lower": "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]",
    "door_upper": "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]",
    "stair": "minecraft:spruce_stairs[facing=south,half=bottom]",
    "stair_under": "minecraft:spruce_stairs[facing=north,half=top]",
    "stair_down": "minecraft:spruce_stairs[facing=south,half=bottom]",
    "sprit": "minecraft:spruce_log[axis=z]",
    "mast": "minecraft:spruce_log[axis=y]",
    "hatch": "minecraft:spruce_trapdoor[facing=north,half=top,open=false]",
    "ladder": "minecraft:ladder[facing=south]",
    "post": "minecraft:spruce_fence",
    "yard": "pirates_n_ships:yard[axis=x]",
    "cleat_mast": "pirates_n_ships:cleat[face=wall,facing=north]",
    "cleat_floor": "pirates_n_ships:cleat[face=floor,facing=north]",
    "figure": "pirates_n_ships:figurehead_lion[facing=north]",
    "winch": "pirates_n_ships:sail_winch[facing=south]",
    "capstan": "pirates_n_ships:capstan",
    "pump": "pirates_n_ships:bilge_pump[facing=west]",
    "barrel": "pirates_n_ships:cargo_barrel",
    "crate": "pirates_n_ships:cargo_crate",
    "water": "pirates_n_ships:water_barrel[fill=4]",
    "pantry": "pirates_n_ships:pantry[facing=east]",
    "helm": "pirates_n_ships:helm[facing=south]",
    "chest": "pirates_n_ships:sea_chest[facing=north]",
    "chart_table": "pirates_n_ships:chart_table[facing=north]",
    "stern_window": "pirates_n_ships:stern_window[facing=south,shutters=false]",
    "pedestal": "pirates_n_ships:flag_pedestal[pole=true,waterlogged=false]",
    "pole_bottom": "pirates_n_ships:flagpole[facing=north,flag=none,part=bottom]",
    "pole_middle": "pirates_n_ships:flagpole[facing=north,flag=none,part=middle]",
    "pole_top": "pirates_n_ships:flagpole[facing=north,flag=none,part=top]",
    "bell_s": "pirates_n_ships:ships_bell[face=wall,facing=south]",
    "lantern_s": "pirates_n_ships:ship_lantern[face=wall,facing=south]",
    "lantern_e": "pirates_n_ships:ship_lantern[face=wall,facing=east]",
    "lantern_w": "pirates_n_ships:ship_lantern[face=wall,facing=west]",
    "ham_foot_s": "pirates_n_ships:hammock[facing=south,part=foot,waterlogged=false]",
    "ham_head_s": "pirates_n_ships:hammock[facing=south,part=head,waterlogged=false]",
    "ham_foot_e": "pirates_n_ships:hammock[facing=east,part=foot,waterlogged=false]",
    "ham_head_e": "pirates_n_ships:hammock[facing=east,part=head,waterlogged=false]",
    "bars": "pirates_n_ships:brig_bars[east=true,north=false,south=false,waterlogged=false,west=true]",
    "brig_door_lower": "pirates_n_ships:brig_door[facing=north,half=lower,hinge=left,locked=false,open=false]",
    "brig_door_upper": "pirates_n_ships:brig_door[facing=north,half=upper,hinge=left,locked=false,open=false]",
    "plate_3": "pirates_n_ships:dark_oak_nameplate[facing=south,left=false,right=true]",
    "plate_4": "pirates_n_ships:dark_oak_nameplate[facing=south,left=true,right=true]",
    "plate_5": "pirates_n_ships:dark_oak_nameplate[facing=south,left=true,right=false]",
    "gun_w": "pirates_n_ships:cannon[facing=west,load=empty,part=front]",
    "gun_rear_w": "pirates_n_ships:cannon[facing=west,load=empty,part=rear]",
    "gun_e": "pirates_n_ships:cannon[facing=east,load=empty,part=front]",
    "gun_rear_e": "pirates_n_ships:cannon[facing=east,load=empty,part=rear]",
    "port_w_open": "pirates_n_ships:dark_oak_bulwark_port[facing=west,open=true,propped=false,waterlogged=false]",
    "port_e_open": "pirates_n_ships:dark_oak_bulwark_port[facing=east,open=true,propped=false,waterlogged=false]",
    "locker": "minecraft:barrel[facing=up,open=false]",
    **{key: state for state, key in RAIL_KEYS.items()},
}

ARMED_PALETTE = {
    "nest": "pirates_n_ships:crows_nest",
    "hoop_w": "pirates_n_ships:mast_hoop[facing=west,waterlogged=false]",
    "hoop_e": "pirates_n_ships:mast_hoop[facing=east,waterlogged=false]",
    "latch_w": "pirates_n_ships:railing_latch[facing=west,waterlogged=false]",
    "latch_e": "pirates_n_ships:railing_latch[facing=east,waterlogged=false]",
    "swivel": "pirates_n_ships:swivel_gun",
}


def latches():
    return [(1, QD_FLOOR + 2, zs, "west") for zs in LATCH_ZS] + [(7, QD_FLOOR + 2, zs, "east") for zs in LATCH_ZS]


# the ratline nets of the armed brig: every latch on a side to that side's hoop (a fan of three strands each, RL4/RL5)
NETS = [((x, y, z), next((hx, hy, hz) for hx, hy, hz, f in HOOPS if f == side)) for x, y, z, side in latches()]


def armed(base):
    """``base`` with the crow's nest, the ratline fittings and the swivel guns; nothing else."""
    out = dict(base)
    nx, ny, nz = NEST
    assert NEST not in out and out.get((nx, ny - 1, nz)) == "mast", "the nest crowns the main mast"
    out[NEST] = "nest"
    for x, y, z, facing in HOOPS:
        assert (x, y, z) not in out and out.get((CX, y, z)) == "mast"
        out[(x, y, z)] = "hoop_" + facing[0]
    for x, y, z, facing in latches():
        assert (x, y, z) not in out and RAILS[(x, y - 1, z)] == ("rail", [facing]), (x, y, z, RAILS.get((x, y - 1, z)))
        out[(x, y, z)] = "latch_" + facing[0]
    for x, y, z in SWIVELS:
        assert (x, y, z) not in out and RAILS.get((x, y - 1, z), (None,))[0] == "rail", (x, y, z)
        out[(x, y, z)] = "swivel"
    return out


def spec(cells, pal, spec_id, name, style, notes):
    """The BuildSpec of ``cells``: runs along x merged into box operations, the palette keys in use only."""
    ops = []
    for (y, zs) in sorted({(y, z) for (_, y, z) in cells}):
        xs = sorted(x for (x, yy, z) in cells if yy == y and z == zs)
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1 and cells[(x, y, zs)] == cells[(start, y, zs)]:
                prev = x
                continue
            ops.append({"type": "box", "from": [start, y, zs], "to": [prev, y, zs], "block": cells[(start, y, zs)]})
            if x is not None:
                start = prev = x
    used = set(cells.values())
    return {
        "id": spec_id,
        "name": name,
        "minecraftVersion": "1.21.1",
        "size": {"x": 9, "y": NEST[1] + 2, "z": STERN + 2},
        "palette": {k: v for k, v in pal.items() if k in used},
        "operations": ops,
        "metadata": {"style": style, "notes": notes},
    }


NOTES = ["Bow toward -Z. Helm on the quarterdeck at [4, 10, 27], facing south.",
         "Decks: ballast stones at y=0..1 beside the keel and in the bottom, hold y=2..4 (bulkheads at z=14 and 23), "
         "lower gun deck floor y=5 (great cabin aft of z=26), weather deck y=8 (tumbled home to x=1..7 in the waist), "
         "quarterdeck y=9 from z=25.",
         "Guns: four a side on the lower gun deck at z=13, 17, 21, 25, muzzles at x=1 (west) and x=7 (east) behind open "
         "gun ports; shot lockers (barrels) at [1|7, 6, 15] and [1|7, 6, 23].",
         "Fore mast z=11 (top y=23), yards at z=10, y=12/17/21; main mast z=19 (top y=25), yards at z=18, y=12/18/23.",
         "Jib: rope on the head cleat [4, 22, 10], then on the bowsprit cleat [4, 10, 1]; clew cleat [4, 11, 10]."]
ARMED_NOTES = NOTES + [
    "Crow's nest on the main mast top at [4, 26, 19]; mast hoops at [3|5, 25, 19]; railing latches at "
    "[1|7, 11, 25..27] on the quarterdeck rails: the template strings a net from every latch to its side's hoop when "
    "it is placed.",
    "Swivel guns on the taffrail at [2|6, 11, 30]."]


def brig_cells(armed_variant):
    """The final cells of either variant."""
    return armed(blocks) if armed_variant else dict(blocks)


def armed_spec(spec_id, name):
    return spec(armed(blocks), {**palette, **ARMED_PALETTE}, spec_id, name,
                "two-masted brig with a lower gun deck of eight guns, a crow's nest and ratline nets", ARMED_NOTES)


if __name__ == "__main__":
    print(json.dumps(spec(blocks, palette, "pns_brig", "Brig",
                          "two-masted brig with a lower gun deck of eight guns, cabin under a raised quarterdeck",
                          NOTES)))
