"""Generates the navy outpost's fort gate (the start piece: a 15x15 walled court between the sea wall and a landward
gatehouse) and prints the BuildSpec as JSON.

The sea is to the north. The seaward side is a stretch of the curtain wall (_style.curtain: walkway at y 4, parapet
and merlons on z 0) pierced by an arched sea gate (x 6..8) through which ``quay_out`` [7, 0, 0] hangs the quay: its
deck continues the paving (y 0), which sits one block above sea level. The curtain continues east and west through
``wall_east_out`` [14, 0, 2] and ``wall_west_out`` [0, 0, 3]. The landward side (south) is a lower wall with the
gatehouse: an arched land gate (x 6..8) between two pillars with lanterns and blue banners, opening onto an apron
(row z 14) where ``building_out`` [14, 0, 14] attaches the fort's buildings landward, clear of the gate.

Inside, the parade court: an andesite path from gate to gate, the navy flag on a pole, lantern posts, stores, and
stairs up to the walkway on the east side. On the west side the harbor master's office (door east onto the court): the
harbor master's desk facing the door and a notice board, a cartography table, shelves and a chest. Its flat roof
joins the curtain's walkway to the west wall's."""
from _style import (PALETTE, WALK, banner, connector, curtain, door, flagpole, lantern_post, stair, stool,
                    weather)
from buildspec import Piece

S, H = 15, 10
GX0, GX1 = 6, 8            # the gates' openings (both)
COURT_Z0, SOUTH_Z = 5, 13  # the court starts behind the curtain; the south wall
APRON_Z = S - 1
OX1, OZ0, OZ1 = 5, 5, 10   # the office: walls x 0..5, z 5..10

p = Piece("navy_outpost_fort_gate", "Navy Outpost Fort Gate", (S, H, S), PALETTE)

# ground: stone brick paving, an andesite path between the gates, a gravel apron outside the land gate
p.fill(0, 0, 0, S - 1, 0, S - 1, "bricks")
p.fill(GX0, 0, COURT_Z0, GX1, 0, SOUTH_Z, "andesite")
p.fill(0, 0, APRON_Z, S - 1, 0, APRON_Z, "gravel")
p.fill(GX0, 0, APRON_Z, GX1, 0, APRON_Z, "cobble")
for x, z in ((2, 12), (11, 6), (12, 12), (9, 11), (4, 11)):
    p.put(x, 0, z, "cobble")


def arch(z0, z1):
    """Clears a gate opening x GX0..GX1, y 1..3 through z0..z1, with upside-down stairs in its top corners."""
    for z in range(z0, z1 + 1):
        for y in range(1, 4):
            for x in range(GX0, GX1 + 1):
                p.clear(x, y, z)
        p.put(GX0, 3, z, stair("stone_brick", "west", "top"))
        p.put(GX1, 3, z, stair("stone_brick", "east", "top"))


# the sea side: the curtain wall over the whole width, the sea gate through it
curtain(p, 0, S - 1)
arch(0, 4)
weather(p, ((1, 2, 0, "bricks_mossy"), (4, 3, 0, "bricks_cracked"), (11, 2, 0, "bricks_mossy"),
            (13, 1, 0, "bricks_mossy"), (9, 4, 0, "bricks_cracked"), (3, 5, 0, "bricks_mossy")))
p.put(7, 4, 0, "chiseled")                       # keystone over the sea gate

# the side walls (one thick, walkway on top, merlons outside) and the south wall
for x in (0, S - 1):
    p.fill(x, 1, COURT_Z0, x, WALK, SOUTH_Z, "bricks")
    p.fill(x, WALK, COURT_Z0, x, WALK, SOUTH_Z, "andesite")
    for z in range(COURT_Z0 + 1, SOUTH_Z + 1, 2):
        p.put(x, WALK + 1, z, "bricks")
p.fill(0, 1, SOUTH_Z, S - 1, WALK, SOUTH_Z, "bricks")
p.fill(0, WALK, SOUTH_Z, S - 1, WALK, SOUTH_Z, "andesite")
for x in range(0, S, 2):
    p.put(x, WALK + 1, SOUTH_Z, "bricks")
p.fill(0, 1, SOUTH_Z, S - 1, 1, SOUTH_Z, "chiseled")

# the gatehouse: a stone block over the land gate between two pillars carrying lanterns, a slab cap
GZ0 = SOUTH_Z - 2
p.fill(GX0 - 1, 1, GZ0, GX1 + 1, WALK + 1, SOUTH_Z, "bricks")
p.fill(GX0 - 1, WALK + 2, GZ0, GX1 + 1, WALK + 2, SOUTH_Z, "brick_slab")
for x in (GX0 - 1, GX1 + 1):
    p.fill(x, 1, GZ0, x, WALK + 2, SOUTH_Z, "bricks")
    p.put(x, 1, SOUTH_Z, "chiseled")
    p.put(x, 1, GZ0, "chiseled")
    lantern_post(p, x, WALK + 3, SOUTH_Z, height=1)
    banner(p, x, WALK + 1, APRON_Z, "south")
for x in (GX0, GX1):
    banner(p, x, WALK + 1, GZ0 - 1, "north")
arch(GZ0, SOUTH_Z)
p.put(7, WALK, SOUTH_Z, "wool")                 # the navy's colours over the land gate
p.put(6, WALK, SOUTH_Z, "white_wool")
p.put(8, WALK, SOUTH_Z, "white_wool")
weather(p, ((2, 2, SOUTH_Z, "bricks_cracked"), (11, 3, SOUTH_Z, "bricks_mossy"), (S - 1, 2, 9, "bricks_mossy")))

# the harbor master's office: andesite walls on brick corners, flat stone roof level with the walkway
p.fill(1, 0, OZ0 + 1, OX1 - 1, 0, OZ1 - 1, "oak")
p.ring(0, OZ0, OX1, OZ1, 1, WALK - 1, "andesite")
for x, z in ((0, OZ0), (OX1, OZ0), (0, OZ1), (OX1, OZ1)):
    p.fill(x, 1, z, x, WALK - 1, z, "bricks")
p.fill(0, WALK, OZ0, OX1, WALK, OZ1, "bricks")
p.fill(0, WALK, OZ0, OX1, WALK, OZ0, "andesite")
door(p, OX1, 1, 8, facing="east", wood="spruce")
p.put(OX1, 2, 6, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]")
p.put(2, 2, OZ1, "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]")
p.put(OX1, 3, 8, "wool")
# inside: the desk across from the door, the harbor master's chair behind it, the notice board on the north wall
p.put(3, 1, 8, "pirates_n_ships:harbor_desk[facing=east]")
stool(p, 2, 1, 8, "west")
p.put(2, 1, 6, "pirates_n_ships:notice_board[facing=south]")
p.put(1, 1, 6, "minecraft:bookshelf")
p.put(1, 2, 6, "minecraft:bookshelf")
p.put(1, 1, 7, "minecraft:cartography_table")
p.put(1, 1, 9, "minecraft:chest[facing=east,type=single,waterlogged=false]")
p.fill(4, 1, 7, 4, 1, 9, "carpet")
p.put(3, WALK - 1, 7, "lantern_hanging")

# stairs up to the walkway along the east wall (rising north, two wide)
for x in (12, 13):
    for i, z in enumerate((8, 7, 6)):
        if i:
            p.fill(x, 1, z, x, i, z, "bricks")
        p.put(x, i + 1, z, stair("stone_brick", "north"))
    p.fill(x, 1, COURT_Z0, x, WALK, COURT_Z0, "bricks")
    p.put(x, WALK, COURT_Z0, "andesite")

# the court: the navy flag on a pole on a pedestal, lantern posts, stores
p.put(10, 1, 9, "chiseled")
flagpole(p, 10, 2, 9, height=3, flies="east")
lantern_post(p, 9, 1, COURT_Z0)
lantern_post(p, 4, 1, GZ0)
lantern_post(p, 10, 1, GZ0)
lantern_post(p, 1, WALK + 1, 4, height=1)
lantern_post(p, 11, WALK + 1, 4, height=1)
for x, y, z in ((12, 1, 11), (13, 1, 11), (13, 1, 12), (13, 2, 11)):
    p.put(x, y, z, "pirates_n_ships:cargo_crate")
for x, y, z in ((12, 1, 12), (11, 1, 12)):
    p.put(x, y, z, "pirates_n_ships:cargo_barrel")
p.put(1, 1, 11, "minecraft:barrel[facing=up,open=false]")
p.put(1, 1, 12, "minecraft:barrel[facing=north,open=false]")
p.put(2, 1, 12, "minecraft:hay_block[axis=x]")

connector(p, 7, 0, 0, "quay_out", "north", "minecraft:chiseled_stone_bricks")
connector(p, S - 1, 0, 2, "wall_east_out", "east", "minecraft:stone_bricks")
connector(p, 0, 0, 3, "wall_west_out", "west", "minecraft:stone_bricks")
connector(p, S - 1, 0, APRON_Z, "building_out", "south", "minecraft:gravel")

p.emit("walled fort gate: sea gate to the quay, parade court with the navy flag, harbor master's office, gatehouse",
       ["Start piece. Sea to the north: quay_out [7, 0, 0]; the paving (y 0) sits one block above sea level.",
        "Curtain wall east and west: wall_east_out [14, 0, 2], wall_west_out [0, 0, 3]; walkway top y 4.",
        "building_out [14, 0, 14] on the landward apron, clear of the land gate (x 6..8)."])
