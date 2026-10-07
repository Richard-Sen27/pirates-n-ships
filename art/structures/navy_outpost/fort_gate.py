"""Generates the navy outpost's fort gate (the start piece: a 15x15 walled court between the sea wall and a landward
gatehouse) and prints the BuildSpec as JSON.

The sea is to the north. The seaward side is a stretch of the curtain wall (_style.curtain: walkway at y 4, corbelled
parapet and merlons on z 0, recessed bays between pilasters) pierced by an arched sea gate (x 6..8) through which
``quay_out`` [7, 0, 0] hangs the quay: its deck continues the paving (y 0), which sits one block above sea level. Over
the sea gate a timber guard shelter with the alarm bell stands on the walkway. The curtain continues east and west
through ``wall_east_out`` [14, 0, 2] and ``wall_west_out`` [0, 0, 3].

The flanks and the landward wall are two blocks thick: an outer skin of pilasters and recessed bays on a sloped, mossy
plinth under a corbel table, an inner wall behind the recesses, the walkway (y 4) on top behind a parapet with
slab-capped merlons. The landward wall's walkway has two embrasures where the garrison's guards stand (x 3 and x 11).
The gatehouse in its middle: an arched land gate (x 6..8) between two square towers (lanterns on their merlons, blue
banners on their faces), a raised portcullis showing in its slot over the arch under the navy's colours, buttresses on
the apron (row z 14) where ``building_out`` [14, 0, 14] attaches the fort's buildings landward, clear of the gate.

Inside, the parade court: an andesite path from gate to gate, the navy flag on a pole, lantern posts, stores, and
stairs up to the walkway on the east side. On the west side the harbor master's office (door east onto the court,
framed in dark oak under a blue panel; a shuttered window south): the harbor master's desk facing the door and a notice
board, a cartography table, shelves, a chest, a blue banner. Its flat roof joins the curtain's walkway to the west
wall's."""
from _style import (PALETTE, WALK, age, arrow_slit, banner, buttress, connector, curtain, door, flagpole,
                    fort_face, lantern_post, merlons, stair, stair_shape, stool, window)
from buildspec import Piece

S, H = 15, 10
GX0, GX1 = 6, 8            # the gates' openings (both)
COURT_Z0, SOUTH_Z = 5, 13  # the court starts behind the curtain; the landward wall's outer face
INNER_Z = SOUTH_Z - 1      # the landward wall's inner face
APRON_Z = S - 1
OX1, OZ0, OZ1 = 5, 5, 10   # the office: walls x 0..5, z 5..10
TW = ((4, 5), (9, 10))     # the gatehouse towers' x (z 12..13)
TOWER_TOP = 7              # the towers' roof row

p = Piece("navy_outpost_fort_gate", "Navy Outpost Fort Gate", (S, H, S), PALETTE)

# ground: stone brick paving, an andesite path between the gates, a gravel apron outside the land gate
p.fill(0, 0, 0, S - 1, 0, S - 1, "bricks")
p.fill(GX0, 0, COURT_Z0, GX1, 0, SOUTH_Z, "andesite")
p.fill(0, 0, APRON_Z, S - 1, 0, APRON_Z, "gravel")
p.fill(GX0, 0, APRON_Z, GX1, 0, APRON_Z, "cobble")
for x, z in ((2, 12), (11, 6), (12, 12), (9, 11), (4, 11), (10, 7), (3, 11)):
    p.put(x, 0, z, "cobble")


def arch(z0, z1):
    """Clears a gate opening x GX0..GX1, y 1..3 through z0..z1, with upside-down stairs in its top corners."""
    for z in range(z0, z1 + 1):
        for y in range(1, 4):
            for x in range(GX0, GX1 + 1):
                p.clear(x, y, z)
        p.put(GX0, 3, z, stair("stone_brick", "west", "top"))
        p.put(GX1, 3, z, stair("stone_brick", "east", "top"))


# ------------------------------------------------------------------ the sea side
curtain(p, 0, S - 1, pilasters=(0, GX0 - 1, GX1 + 1, S - 1), slits=(2, 3, 11, 12))
arch(0, 4)
for x in range(GX0, GX1 + 1):
    p.put(x, WALK, 0, "bricks")                  # the arch's head, a keystone in the middle
p.put(7, WALK, 0, "chiseled")

# the guard shelter over the sea gate: dark oak posts, a spruce roof, the alarm bell under it
for x in (GX0 - 1, GX1 + 1):
    for z in (1, 4):
        p.fill(x, WALK + 1, z, x, WALK + 2, z, "dark_post")
p.roof_ridge_x(GX0 - 1, GX1 + 1, 1, 4, WALK + 3, "minecraft:spruce_stairs", "spruce_slab", "dark_oak", overhang=0)
p.put(7, WALK + 3, 2, "minecraft:bell[attachment=ceiling,facing=east,powered=false]")

# lanterns on the walkway
lantern_post(p, 1, WALK + 1, 4, height=1)
lantern_post(p, 10, WALK + 1, 4, height=1)

# ------------------------------------------------------------------ the flanks and the landward wall
west = [(0, z) for z in range(COURT_Z0, SOUTH_Z + 1)]
east = [(S - 1, z) for z in range(COURT_Z0, SOUTH_Z + 1)]
south = [(x, SOUTH_Z) for x in list(range(1, TW[0][0])) + list(range(TW[1][1] + 1, S - 1))]
fort_face(p, west, "east", pilasters={(0, 5), (0, 6), (0, OZ1), (0, SOUTH_Z)})
fort_face(p, east, "west", pilasters={(S - 1, 5), (S - 1, 9), (S - 1, SOUTH_Z)})
fort_face(p, south, "north")
for z in range(COURT_Z0, SOUTH_Z):             # the inner walls behind the recesses, the walkways on top
    for x in (1, S - 2):
        p.fill(x, 2, z, x, 3, z, "bricks")
        p.put(x, WALK, z, "andesite")
for x, _ in south:
    p.fill(x, 2, INNER_Z, x, 3, INNER_Z, "bricks")
    p.put(x, WALK, INNER_Z, "andesite")
for x, z in west + east:                       # the string course over the pilasters
    if p.get(x, WALK, z) == "bricks":
        p.put(x, WALK, z, "andesite")
# parapets and merlons: along the flanks, and along the landward wall with embrasures for the guards (x 3 and 11)
for x, z in west + east:
    p.put(x, WALK + 1, z, "bricks")
merlons(p, [(x, z) for x, z in west + east if z % 2 == 0], WALK + 2)
guards = (3, 11)
south_line = [(x, SOUTH_Z) for x in list(range(0, TW[0][0])) + list(range(TW[1][1] + 1, S))]
for x, z in south_line:
    if x not in guards:
        p.put(x, WALK + 1, z, "bricks")
merlons(p, [(x, z) for x, z in south_line if x % 2 == 0], WALK + 2)

# ------------------------------------------------------------------ the gatehouse
GZ0 = INNER_Z - 1          # the gate block reaches one row into the court
p.fill(GX0 - 2, 1, INNER_Z, GX1 + 2, 1, SOUTH_Z, "chiseled")
p.fill(GX0 - 2, 2, INNER_Z, GX1 + 2, TOWER_TOP - 1, SOUTH_Z, "bricks")
p.fill(GX0, 1, GZ0, GX1, WALK + 1, GZ0, "bricks")
for x in range(GX0, GX1 + 1):                  # the walk between the towers, a row above the walkway
    for z in range(INNER_Z, SOUTH_Z + 1):
        for y in range(WALK + 2, TOWER_TOP):
            p.clear(x, y, z)
p.fill(GX0, WALK + 1, GZ0, GX1, WALK + 1, INNER_Z, "andesite")
for x0, x1 in TW:
    p.fill(x0, WALK, INNER_Z, x1, WALK, SOUTH_Z, "andesite")         # the walkway's band round the towers
    p.fill(x0, TOWER_TOP, INNER_Z, x1, TOWER_TOP, SOUTH_Z, "andesite")
    gate_side, out_side = (x1, x0) if x0 == TW[0][0] else (x0, x1)
    merlons(p, [(gate_side, SOUTH_Z)], TOWER_TOP + 1, cap=False)
    p.put(gate_side, TOWER_TOP + 2, SOUTH_Z, "lantern")
    merlons(p, [(out_side, SOUTH_Z), (out_side, INNER_Z)], TOWER_TOP + 1)
    arrow_slit(p, out_side, WALK + 1, SOUTH_Z, "x", grille=True)
merlons(p, [(GX0, SOUTH_Z), (GX1, SOUTH_Z)], WALK + 2)
for x in (GX0 - 2, GX1 + 2):                   # buttresses on the apron
    buttress(p, x, APRON_Z, 3, "south")
arch(GZ0, SOUTH_Z)
for x in range(GX0, GX1 + 1):                  # the raised portcullis in its slot, the navy's colours over it
    p.put(x, WALK, SOUTH_Z, "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]")
p.put(7, WALK + 1, SOUTH_Z, "wool")
p.put(6, WALK + 1, SOUTH_Z, "white_wool")
p.put(8, WALK + 1, SOUTH_Z, "white_wool")
for x in (GX0 - 1, GX1 + 1):
    banner(p, x, WALK + 1, APRON_Z, "south")
for x in (GX0, GX1):
    banner(p, x, WALK + 1, GZ0 - 1, "north")

# ------------------------------------------------------------------ the harbor master's office
p.fill(1, 0, OZ0 + 1, OX1 - 1, 0, OZ1 - 1, "spruce")
p.fill(1, 1, OZ0, OX1, 3, OZ0, "andesite")
p.fill(1, 1, OZ1, OX1, 3, OZ1, "andesite")
p.fill(OX1, 1, OZ0, OX1, 3, OZ1, "andesite")
for x, z in ((OX1, OZ0), (OX1, OZ1)):
    p.fill(x, 1, z, x, 3, z, "bricks")
    p.put(x, 1, z, "chiseled")
p.fill(1, WALK, OZ0, OX1, WALK, OZ1, "bricks")
p.fill(1, WALK, OZ0, OX1, WALK, OZ0, "andesite")
p.fill(OX1, WALK, OZ0, OX1, WALK, OZ1, "andesite")
door(p, OX1, 1, 8, facing="east", wood="spruce")
for z in (7, 9):                               # the door's dark oak frame under the navy's blue
    p.fill(OX1, 1, z, OX1, 3, z, "dark_post")
p.put(OX1, 3, 8, "wool")
p.put(OX1, 2, 6, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")
window(p, 3, 2, OZ1, "south")
# inside: the desk across from the door, the harbor master's chair behind it, the notice board on the north wall
p.put(3, 1, 8, "pirates_n_ships:harbor_desk[facing=east]")
stool(p, 2, 1, 8, "west")
p.put(2, 1, 6, "pirates_n_ships:notice_board[facing=south]")
p.put(1, 1, 6, "minecraft:bookshelf")
p.put(1, 2, 6, "minecraft:bookshelf")
p.put(1, 1, 7, "minecraft:cartography_table")
p.put(1, 1, 8, "minecraft:barrel[facing=east,open=false]")
p.put(1, 1, 9, "minecraft:chest[facing=east,type=single,waterlogged=false]")
p.fill(4, 1, 7, 4, 1, 9, "carpet")
p.put(3, WALK - 1, 7, "lantern_hanging")
banner(p, 3, 3, OZ0 + 1, "south")

# ------------------------------------------------------------------ stairs up to the walkway (east, rising north)
for x in (11, 12):
    for i, z in enumerate((8, 7, 6)):
        if i:
            p.fill(x, 1, z, x, i, z, "bricks")
        p.put(x, i + 1, z, stair("stone_brick", "north"))
p.fill(11, 1, COURT_Z0, 13, WALK - 1, COURT_Z0, "bricks")
p.fill(11, WALK, COURT_Z0, 13, WALK, COURT_Z0, "andesite")

# ------------------------------------------------------------------ the court: flag, lights, stores
p.put(10, 1, 9, "chiseled")
flagpole(p, 10, 2, 9, height=3, flies="east")
for x, z in ((9, 9), (11, 9), (10, 8), (10, 10)):
    p.put(x, 1, z, stair_shape("stone_brick", {(9, 9): "east", (11, 9): "west", (10, 8): "south",
                                               (10, 10): "north"}[(x, z)]))
lantern_post(p, 9, 1, COURT_Z0)
lantern_post(p, GX0 - 1, 1, GZ0)
lantern_post(p, GX1 + 1, 1, GZ0)
for x, y, z in ((13, 1, 10), (13, 1, 11), (12, 1, 11), (12, 2, 11)):
    p.put(x, y, z, "pirates_n_ships:cargo_crate")
for x, y, z in ((13, 1, 12), (12, 1, 12)):
    p.put(x, y, z, "pirates_n_ships:cargo_barrel")
p.put(1, 1, 11, "minecraft:barrel[facing=up,open=false]")
p.put(1, 1, 12, "minecraft:barrel[facing=north,open=false]")
p.put(2, 1, 12, "minecraft:hay_block[axis=x]")
p.put(13, 1, 7, "minecraft:barrel[facing=up,open=false]")
p.put(13, 1, 8, "pirates_n_ships:cargo_crate")

age(p, where=lambda x, y, z: y > 0 or not (0 < x < S - 1 and COURT_Z0 <= z < SOUTH_Z))

connector(p, 7, 0, 0, "quay_out", "north", "minecraft:chiseled_stone_bricks")
connector(p, S - 1, 0, 2, "wall_east_out", "east", "minecraft:stone_bricks")
connector(p, 0, 0, 3, "wall_west_out", "west", "minecraft:stone_bricks")
connector(p, S - 1, 0, APRON_Z, "building_out", "south", "minecraft:gravel")

p.emit("walled fort gate: sea gate to the quay under a guard shelter, parade court with the navy flag, harbor master's "
       "office, towered gatehouse with a portcullis",
       ["Start piece. Sea to the north: quay_out [7, 0, 0]; the paving (y 0) sits one block above sea level.",
        "Curtain wall east and west: wall_east_out [14, 0, 2], wall_west_out [0, 0, 3]; walkway top y 4.",
        "building_out [14, 0, 14] on the landward apron, clear of the land gate (x 6..8)."])
