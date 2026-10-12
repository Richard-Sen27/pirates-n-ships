"""Generates the brig BuildSpec (SHIP1, bow toward -Z) and prints it as JSON.

A two-masted brig, 9 wide and 32 long over all (hull 27, bowsprit 4, the name boards one more), dark oak and spruce
like the starter sloop. Decks from the keel up: the keel (y 0), the hold (y 2..3, three watertight compartments
between two bulkheads), the lower gun deck (floor y 4, headroom y 5..6), the weather deck (y 7) and, aft, the cabin
(y 8..9) under the raised quarterdeck (y 10). The design is written down in art/README.md, "The brig (SHIP1)".

``brig_cells(armed)`` builds both variants: run on its own this file prints the orderable merchant brig (``brig``);
``navy_brig_armed.py`` and ``pirate_brig_armed.py`` print the armed one, which differs from it only as the armed
sloops differ from the starter sloop: the guns, the open gun ports, the shot lockers, the crow's nest and the ratline
fittings (railing latches and mast hoops; the nets between them are strung when the template is placed, from the
``nets`` list of its ship template definition, see ``NETS``), plus the two swivel guns.
"""
import json

CX = 4          # centre line
OFF = 4         # z offset of the hull (room for the bowsprit)
L = 27          # hull length
STERN = OFF + L - 1          # 30, the transom

# half-width of the hull at the gun deck and above, by hull z (0 = stem)
HW = [0, 1, 1, 2, 2, 3, 3, 3] + [4] * 15 + [3, 3, 3, 3]
assert len(HW) == L

# lower layers: half-width reduction and hull z range (rocker)
LOWER = {1: (1, 2, 24), 2: (0, 1, 25)}

HOLD = (2, 3)                # hold rows
GUN_FLOOR = 4                # lower gun deck floor
GUN_ROWS = (5, 6)            # lower gun deck headroom
DECK = 7                     # weather deck
QD = 25                      # first shifted z of the cabin / quarterdeck
QD_FLOOR = 10                # quarterdeck floor (cabin y 8..9 under it)
BULKHEADS = (14, 23)         # shifted z of the two hold bulkheads: fore hold, main hold, aft hold

FMZ, MMZ = 11, 19            # fore and main mast (shifted z)
FORE_TOP, MAIN_TOP = 22, 24  # mast heads
# yards (yard rule, PND3/TPL3): one block forward of the mast; (y, half length) from the lowest
FORE_YARDS = ((11, 4), (16, 3), (20, 2))
MAIN_YARDS = ((11, 4), (17, 3), (22, 2))

GUN_ZS = (13, 17, 21, 25)    # gun deck guns, both sides
LOCKERS = [(1, 5, 15), (1, 5, 23), (7, 5, 15), (7, 5, 23)]   # shot lockers on the gun deck, two guns each
NEST = (CX, MAIN_TOP + 1, MMZ)
HOOPS = [(CX - 1, MAIN_TOP, MMZ, "west"), (CX + 1, MAIN_TOP, MMZ, "east")]
LATCH_ZS = (22, 23, 24)      # railing latches on the weather deck rail, abaft the main mast
SWIVELS = [(1, QD_FLOOR + 2, 28), (7, QD_FLOOR + 2, 28)]

blocks = {}


def put(x, y, z, b):
    blocks[(x, y, z)] = b


def fit(x, y, z, b):
    """Puts a fitting into a free cell (never over the hull or another fitting)."""
    assert (x, y, z) not in blocks, ((x, y, z), b, blocks[(x, y, z)])
    blocks[(x, y, z)] = b


def hw(zs, y):
    z = zs - OFF
    if not 0 <= z < L:
        return -1
    if y in LOWER:
        red, z0, z1 = LOWER[y]
        if not z0 <= z <= z1:
            return -1
        return HW[z] - red
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


def side_block(y):
    return {1: "lower", 2: "lower", 3: "wale", 4: "band", 7: "wale"}.get(y, "top")


# ---------------------------------------------------------------- hull

for zs in range(OFF + 1, OFF + 24):
    put(CX, 0, zs, "keel")

for y in range(1, DECK + 1):
    for zs in range(OFF, STERN + 1):
        for x in range(0, 9):
            if not inside(x, y, zs):
                continue
            if y == 1 or edge(x, y, zs):
                put(x, y, zs, side_block(y))
            elif y in (GUN_FLOOR, DECK):
                put(x, y, zs, "deck")
            elif y in HOLD and zs in BULKHEADS:
                put(x, y, zs, "bulkhead")

# SH1b: a bottom plank under every hollow hold cell whose cell below is outside the hull
for y in HOLD:
    for zs in range(OFF, STERN + 1):
        for x in range(0, 9):
            if inside(x, y, zs) and (x, y, zs) not in blocks and not inside(x, y - 1, zs):
                put(x, y - 1, zs, side_block(y - 1))

# the forefoot: the bottom of the two forward hold cells sits on the keel (layer 1 starts at z 7)
for zs in (OFF + 1, OFF + 2):
    if (CX, 1, zs) not in blocks:
        fit(CX, 1, zs, "lower")

# ---------------------------------------------------------------- cabin and quarterdeck

for zs in range(QD, STERN + 1):
    for x in range(0, 9):
        if not inside(x, DECK, zs):
            continue
        wall = edge(x, DECK, zs) or zs == QD
        for y in (8, 9):
            if wall:
                put(x, y, zs, "top")
        put(x, QD_FLOOR, zs, "top" if edge(x, DECK, zs) else "deck")

# cabin door, stern windows, side lights
put(CX, 8, QD, "door_lower")
put(CX, 9, QD, "door_upper")
for x in (2, 4, 6):
    put(x, 9, STERN, "stern_window")
for x in (1, 7):
    put(x, 9, 28, "pane")

# stairs from the weather deck up to the quarterdeck on both sides (landing on the quarterdeck at z QD), one block in
# from the rail so the deck beside the ratline latches (x 1 and 7, z 22..24) stays free for the climber
STAIR_XS = (2, 6)
for x in STAIR_XS:
    fit(x, 8, QD - 3, "stair")
    fit(x, 8, QD - 2, "top")
    fit(x, 9, QD - 2, "stair")
    fit(x, 8, QD - 1, "top")
    fit(x, 9, QD - 1, "top")
    fit(x, 10, QD - 1, "stair")

# ---------------------------------------------------------------- the companion: weather deck down to the gun deck
# a three-block opening in the weather deck (z 14..16) with a stair in the deck at z 17 and two below it, descending
# toward the bow onto the gun deck floor at z 14; a plank under the middle stair
for zs in (14, 15, 16):
    blocks.pop((CX, DECK, zs))
put(CX, DECK, 17, "stair_down")
fit(CX, 6, 16, "stair_down")
fit(CX, 5, 15, "stair_down")
fit(CX, 5, 16, "deck")

# hatches with ladders from the gun deck into each hold compartment (ladder on the mast or the bulkhead)
for zs in (FMZ + 1, MMZ + 1, BULKHEADS[1] + 1):
    put(CX, GUN_FLOOR, zs, "hatch")
    for y in HOLD:
        fit(CX, y, zs, "ladder")

# ---------------------------------------------------------------- rails


def railing_cells():
    """(x, y, z) -> (kind, edges): the weather deck's railing family and the quarterdeck's rails."""
    out = {}
    for zs in range(OFF + 1, QD):
        for x in range(0, 9):
            if edge(x, DECK, zs):
                out[(x, 8, zs)] = ("rail", outside_dirs(x, zs))
    for zs in range(QD, STERN + 1):
        for x in range(0, 9):
            if not inside(x, DECK, zs):
                continue
            dirs = outside_dirs(x, zs)
            if zs == QD:
                dirs = dirs + ["north"]
            if not dirs:
                continue
            kind = "rope" if zs == QD and not edge(x, DECK, zs) else "rail"
            out[(x, 11, zs)] = (kind, dirs)
    # gaps over the stair landings in the quarterdeck front
    for x in STAIR_XS:
        out.pop((x, 11, QD))
    # rope railing round the companion opening
    for zs in (14, 15, 16):
        out[(CX - 1, 8, zs)] = ("rope", ["east"])
        out[(CX + 1, 8, zs)] = ("rope", ["west"])
    # a wide port (two ports side by side) and a gate on each side of the waist
    for zs in (13, 14):
        out[(0, 8, zs)] = ("port", ["west"])
        out[(8, 8, zs)] = ("port", ["east"])
    out[(0, 8, 17)] = ("gate", ["west"])
    out[(8, 8, 17)] = ("gate", ["east"])
    return out


RAILS = railing_cells()

# ---------------------------------------------------------------- bow, masts, rig

fit(CX, 8, OFF, "top")                  # stem head
for zs in range(0, OFF):
    fit(CX, 8, zs, "sprit")
fit(CX, 7, OFF - 1, "figure")

for top, mz, yards in ((FORE_TOP, FMZ, FORE_YARDS), (MAIN_TOP, MMZ, MAIN_YARDS)):
    for y in range(1, top + 1):
        put(CX, y, mz, "mast")
    for y, half in yards:
        for x in range(CX - half, CX + half + 1):
            put(x, y, mz - 1, "yard")

# jib on the fore mast (TPL3): head above the upper fore yard, clew below the lower, tack on the bowsprit
JIB_HEAD = (CX, FORE_YARDS[-1][0] + 1, FMZ - 1)
JIB_CLEW = (CX, FORE_YARDS[0][0] - 1, FMZ - 1)
JIB_TACK = (CX, 9, 1)
fit(*JIB_HEAD, "cleat_mast")
fit(*JIB_CLEW, "cleat_mast")
fit(*JIB_TACK, "cleat_floor")

# ---------------------------------------------------------------- stations, crew and gear

fit(CX, 8, 7, "capstan")
fit(CX, 8, FMZ + 1, "winch")
fit(CX, 8, MMZ + 1, "winch")
fit(CX + 1, 5, MMZ + 1, "pump")             # gun deck over the main hold: its intake reaches the hold
fit(CX, QD_FLOOR + 1, 27, "helm")
fit(CX, QD_FLOOR + 1, 29, "pedestal")
fit(CX, QD_FLOOR + 2, 29, "pole_bottom")
fit(CX, QD_FLOOR + 3, 29, "pole_middle")
fit(CX, QD_FLOOR + 4, 29, "pole_top")
fit(CX, 9, FMZ + 1, "bell_s")              # on the fore mast's after face, over the fore winch
for x in (3, 5):
    fit(x, 9, QD - 1, "lantern_n")
fit(1, 6, 12, "lantern_e")
fit(7, 6, 12, "lantern_w")
fit(1, 6, 19, "lantern_e")
fit(7, 6, 19, "lantern_w")
fit(2, 9, 27, "lantern_e")

# galley at the fore end of the gun deck's waist
fit(1, 5, 12, "pantry")
fit(7, 5, 12, "water")

# hammocks: two fore and aft in the fore berth (hull at z 8, posts at z 11), four across the gun deck between the guns
# (hull and a post; at z 22 and 26, so the middle lane past the aft hatch at z 24 stays open), two across the cabin
# (cabin wall and a post); eight in all
for x in (2, 6):
    fit(x, 5, 9, "ham_foot_s")
    fit(x, 5, 10, "ham_head_s")
    fit(x, 5, 11, "post")
for zs in (22, 26):
    fit(1, 5, zs, "ham_foot_e")
    fit(2, 5, zs, "ham_head_e")
    fit(3, 5, zs, "post")
    fit(5, 5, zs, "post")
    fit(6, 5, zs, "ham_foot_e")
    fit(7, 5, zs, "ham_head_e")
fit(1, 8, 26, "ham_foot_e")
fit(2, 8, 26, "ham_head_e")
fit(3, 8, 26, "post")
fit(5, 8, 26, "post")
fit(6, 8, 26, "ham_foot_e")
fit(7, 8, 26, "ham_head_e")

# brig cell at the after end of the gun deck: bars across with a brig door in the middle
for y in GUN_ROWS:
    for x in (2, 3, 5, 6):
        fit(x, y, 27, "bars")
fit(CX, 5, 27, "brig_door_lower")
fit(CX, 6, 27, "brig_door_upper")

# cabin: chart table, sea chest
fit(2, 8, 29, "chart_table")
fit(CX, 8, 29, "chest")
fit(6, 8, 29, "crate")

# holds: cargo
for x, zs, b in ((3, 9, "crate"), (5, 9, "crate"), (3, 10, "barrel"), (5, 10, "barrel"),
                 (2, 16, "crate"), (6, 16, "crate"), (2, 17, "barrel"), (6, 17, "water"), (2, 21, "crate"),
                 (6, 21, "crate"), (2, 26, "barrel"), (6, 26, "barrel"), (3, 28, "crate"), (5, 28, "crate")):
    fit(x, 2, zs, b)

# name boards on the transom, under the stern windows
for x in (3, 4, 5):
    fit(x, 6, STERN + 1, "plate_%d" % x)

# gun ports (shut in the merchant brig: the lids open when a cannon is run out behind them)
for zs in GUN_ZS:
    assert blocks.get((0, 5, zs)) == "top" and blocks.get((8, 5, zs)) == "top"
    put(0, 5, zs, "port_w")
    put(8, 5, zs, "port_e")


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
    assert cell not in blocks, cell
    put(*cell, key)


palette = {
    "lower": "minecraft:dark_oak_planks",
    "wale": "minecraft:stripped_dark_oak_log[axis=z]",
    "band": "minecraft:spruce_planks",
    "top": "minecraft:dark_oak_planks",
    "keel": "minecraft:stripped_dark_oak_log[axis=z]",
    "deck": "minecraft:spruce_planks",
    "bulkhead": "minecraft:dark_oak_planks",
    "pane": "minecraft:glass_pane",
    "door_lower": "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]",
    "door_upper": "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]",
    "stair": "minecraft:spruce_stairs[facing=south,half=bottom]",
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
    "lantern_n": "pirates_n_ships:ship_lantern[face=wall,facing=north]",
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
    "port_w": "pirates_n_ships:dark_oak_bulwark_port[facing=west,open=false,propped=false,waterlogged=false]",
    "port_e": "pirates_n_ships:dark_oak_bulwark_port[facing=east,open=false,propped=false,waterlogged=false]",
    **{key: state for state, key in RAIL_KEYS.items()},
}

ARMED_PALETTE = {
    "gun_w": "pirates_n_ships:cannon[facing=west,load=empty,part=front]",
    "gun_rear_w": "pirates_n_ships:cannon[facing=west,load=empty,part=rear]",
    "gun_e": "pirates_n_ships:cannon[facing=east,load=empty,part=front]",
    "gun_rear_e": "pirates_n_ships:cannon[facing=east,load=empty,part=rear]",
    "port_w_open": "pirates_n_ships:dark_oak_bulwark_port[facing=west,open=true,propped=false,waterlogged=false]",
    "port_e_open": "pirates_n_ships:dark_oak_bulwark_port[facing=east,open=true,propped=false,waterlogged=false]",
    "locker": "minecraft:barrel[facing=up,open=false]",
    "nest": "pirates_n_ships:crows_nest",
    "hoop_w": "pirates_n_ships:mast_hoop[facing=west,waterlogged=false]",
    "hoop_e": "pirates_n_ships:mast_hoop[facing=east,waterlogged=false]",
    "latch_w": "pirates_n_ships:railing_latch[facing=west,waterlogged=false]",
    "latch_e": "pirates_n_ships:railing_latch[facing=east,waterlogged=false]",
    "swivel": "pirates_n_ships:swivel_gun",
}


def latches():
    return [(0, 9, zs, "west") for zs in LATCH_ZS] + [(8, 9, zs, "east") for zs in LATCH_ZS]


# the ratline nets of the armed brig: every latch on a side to that side's hoop (a fan of three strands each, RL4/RL5)
NETS = [((x, y + 1, z), next((hx, hy + 1, hz) for hx, hy, hz, f in HOOPS if f == side)) for x, y, z, side in latches()]  # final (deepened)


def armed(base):
    """``base`` with the guns, open ports, lockers, crow's nest, ratline fittings and swivel guns; nothing else."""
    out = dict(base)
    for zs in GUN_ZS:
        for x, side in ((0, "w"), (8, "e")):
            step = 1 if x == 0 else -1
            assert out.get((x, 5, zs)) == "port_" + side, (x, zs)
            for cell in ((x + step, 5, zs), (x + 2 * step, 5, zs)):
                assert cell not in out, cell  # free gun deck
            out[(x, 5, zs)] = "port_%s_open" % side
            out[(x + step, 5, zs)] = "gun_" + side
            out[(x + 2 * step, 5, zs)] = "gun_rear_" + side
    for cell in LOCKERS:
        assert cell not in out and out.get((cell[0], cell[1] - 1, cell[2])) == "deck", cell
        out[cell] = "locker"
    nx, ny, nz = NEST
    assert NEST not in out and out.get((nx, ny - 1, nz)) == "mast", "the nest crowns the main mast"
    out[NEST] = "nest"
    for x, y, z, facing in HOOPS:
        assert (x, y, z) not in out and out.get((CX, y, z)) == "mast"
        out[(x, y, z)] = "hoop_" + facing[0]
    for x, y, z, facing in latches():
        assert (x, y, z) not in out and RAILS[(x, y - 1, z)] == ("rail", [facing]), (x, y, z)
        out[(x, y, z)] = "latch_" + facing[0]
    for x, y, z in SWIVELS:
        assert (x, y, z) not in out and RAILS.get((x, y - 1, z), (None,))[0] == "rail", (x, y, z)
        out[(x, y, z)] = "swivel"
    return out


# SHIP1: laid out above with a two-row hold, the brig floated with her gun deck floor awash (Sable's buoyancy and the dry
# hull lift against her weight: waterline 4.55 rows over the keel). ``deepen`` gives her a third hold row: every cell
# from y DEEPEN_AT up moves one row up, and the new row repeats the hold row under it (sides, bulkheads, ladders, masts;
# cargo stays on the hold floor). All coordinates in this file above are before deepening; the notes, art/README.md,
# ShipTemplates and the tests use the final ones (y + 1 from the gun deck floor up).
DEEPEN_AT = GUN_FLOOR


def deepen(cells):
    out = {}
    for (x, y, z), b in cells.items():
        out[(x, y + 1 if y >= DEEPEN_AT else y, z)] = b
    for (x, y, z), b in cells.items():
        if y == DEEPEN_AT - 1 and b in ("wale", "bulkhead", "ladder", "mast"):
            out[(x, y + 1, z)] = b
            if b == "wale":
                out[(x, y, z)] = "lower"
    return out


def spec(cells, pal, spec_id, name, style, notes):
    """The BuildSpec of ``cells`` (deepened): runs along x merged into box operations, the palette keys in use only."""
    cells = deepen(cells)
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


NOTES = ["Bow toward -Z. Helm on the quarterdeck at [4, 12, 27], facing south.",
         "Decks: hold y=2..4 (bulkheads at z=14 and 23), lower gun deck floor y=5, weather deck y=8, cabin y=9..10, "
         "quarterdeck y=11.",
         "Fore mast z=11 (top y=23), yards at z=10, y=12/17/21; main mast z=19 (top y=25), yards at z=18, y=12/18/23.",
         "Jib: rope on the head cleat [4, 22, 10], then on the bowsprit cleat [4, 10, 1]; clew cleat [4, 11, 10]."]
ARMED_NOTES = NOTES + [
    "Guns: four a side on the lower gun deck at z=13, 17, 21, 25, muzzles at x=1 (west) and x=7 (east) behind open "
    "gun ports; shot lockers (barrels) at [1|7, 6, 15] and [1|7, 6, 23].",
    "Crow's nest on the main mast top at [4, 26, 19]; mast hoops at [3|5, 25, 19]; railing latches at "
    "[0|8, 10, 22..24]: the template strings a net from every latch to its side's hoop when it is placed.",
    "Swivel guns on the quarterdeck rails at [1|7, 13, 28]."]


def brig_cells(armed_variant):
    """The final cells (deepened) of either variant."""
    return deepen(armed(blocks) if armed_variant else dict(blocks))


def armed_spec(spec_id, name):
    return spec(armed(blocks), {**palette, **ARMED_PALETTE}, spec_id, name,
                "two-masted brig with a lower gun deck of eight guns, a crow's nest and ratline nets", ARMED_NOTES)


if __name__ == "__main__":
    print(json.dumps(spec(blocks, palette, "pns_brig", "Brig",
                          "two-masted brig with a lower gun deck pierced for eight guns, cabin under a raised quarterdeck",
                          NOTES)))
