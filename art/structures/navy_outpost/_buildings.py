"""STR4: the navy outpost's new buildings and building variants (pool ``navy_outpost/buildings``; art/README.md,
"Natural layout of the outpost (STR4)"). Each piece file calls one builder here. The ST3 barracks, brig and watchtower
stay as they were; these add a long barracks, a barracks with a mess hall, a larger brig, a signal tower, the
officers' quarters, a half-sunk powder magazine, an armoury, a gun battery, a store house and a chapel.

Conventions as every outpost building: y 0 the foundation row (the magazine's surface is row y 2), the door and
``building_in`` on the north side, row z 0 the doorstep; the fort's palette (stone bricks and polished andesite,
spruce structure and dark oak trim, the navy's blue). Garrison posts (``GarrisonPosts.POSTS``) are free cells on a
sturdy floor; the notes of each piece name them."""
from _style import (PALETTE, age, banner, bars, bed, brig_door, buttress, cannon, chain, chimney, connector, door, dry,
                    fence_run, fort_face, lantern_post, merlons, pane, rafter_ends, stair_shape, stool, table, window)
from buildspec import Piece


def _piece(name, title, size):
    return Piece(f"navy_outpost_{name}", f"Navy Outpost {title}", size, PALETTE)


def finish(p, door_x, description, notes, y=0, final="minecraft:cobblestone", where=None):
    age(p, where=where)
    connector(p, door_x, y, 0, "building_in", "north", final)
    dry(p)
    p.emit(description, notes)


def frame_hall(p, X0, X1, Z0, Z1, top, door_x, posts_x=(), posts_z=(), roof="x", eaves=True):
    """The barracks' timber frame on a stone plinth (ST4c): a mossy stone brick plinth, polished andesite infill
    between spruce log corners and stripped posts, dark oak door posts, a spruce roof (ridge along ``roof``) with dark
    oak eaves and rafter ends, a blue panel over the door, lanterns hanging by it, a cobbled doorstep."""
    p.ring(X0, Z0, X1, Z1, 0, 0, "bricks")
    p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
    p.ring(X0, Z0, X1, Z1, 1, 1, "bricks")
    p.ring(X0, Z0, X1, Z1, 2, top, "andesite")
    for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        p.fill(x, 1, z, x, top, z, "log")
    for z in (Z0, Z1):
        for x in posts_x:
            p.fill(x, 2, z, x, top, z, "post")
    for x in (X0, X1):
        for z in posts_z:
            p.fill(x, 2, z, x, top, z, "post")
    for x in (door_x - 1, door_x + 1):
        p.fill(x, 1, Z0, x, top, Z0, "dark_post")
    if roof == "x":
        p.roof_ridge_x(X0, X1, Z0, Z1, top + 1, "minecraft:spruce_stairs", "spruce_slab", "spruce", plate="beam_x")
        if eaves:
            for x in range(X0, X1 + 1):
                if Z0 - 1 >= 0:
                    p.put(x, top + 1, Z0 - 1, stair_shape("dark_oak", "south"))
                if p.inside(x, top + 1, Z1 + 1):
                    p.put(x, top + 1, Z1 + 1, stair_shape("dark_oak", "north"))
            rafter_ends(p, [(x, Z0 - 1) for x in range(X0 + 1, X1, 2) if x != door_x], top, "north")
            if p.inside(X0, top, Z1 + 1):
                rafter_ends(p, [(x, Z1 + 1) for x in range(X0 + 1, X1, 2)], top, "south")
    else:
        p.roof_ridge_z(Z0, Z1, X0, X1, top + 1, "minecraft:spruce_stairs", "spruce_slab", "spruce")
        for z in range(Z0, Z1 + 1):
            p.put(X0, top, z, "beam_z")
            p.put(X1, top, z, "beam_z")
    door(p, door_x, 1, Z0, facing="north")
    p.fill(door_x - 2, 0, 0, door_x + 2, 0, 0, "cobble")
    p.put(door_x, top, Z0, "wool")
    p.put(door_x - 1, top, 0, "lantern_hanging")
    p.put(door_x + 1, top, 0, "lantern_hanging")


def bunk_row(p, xs, z_foot, upper=(), chests=()):
    for x in xs:
        bed(p, x, 1, z_foot, "south")
    for x in upper:
        bed(p, x, 2, z_foot, "south")
    for x in chests:
        p.put(x, 1, z_foot + 1, "minecraft:chest[facing=north,type=single,waterlogged=false]")


def rack(p, xs, z):
    """A weapon rack: a row of joined fences with stone pressure plates (muskets and cutlasses laid on them)."""
    fence_run(p, [(x, z) for x in xs], 1)
    for x in xs:
        p.put(x, 2, z, "minecraft:stone_pressure_plate[powered=false]")


# ---------------------------------------------------------------------------------------------- barracks


def barracks_long():
    """A 15 long barracks: ten bunks along the back wall (the outer pairs doubled up), two hearths with a chimney each,
    two mess tables, weapon racks either side of the door. Post: soldier [10, 1, 3]."""
    X0, X1, Z0, Z1, TOP = 0, 14, 1, 7, 3
    p = _piece("barracks_long", "Long Barracks", (X1 + 1, 9, Z1 + 2))
    frame_hall(p, X0, X1, Z0, Z1, TOP, 7, posts_x=(3, 11), posts_z=(3, 5))
    for x in (2, 5, 9, 12):
        window(p, x, 2, Z0, "north")
    for x in (2, 12):
        window(p, x, 2, Z1, "south")
    for hx in (5, 9):
        p.fill(hx, 1, Z1, hx, TOP, Z1, "bricks")
        p.put(hx, 1, Z1, "minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")
        p.fill(hx, 0, Z1 + 1, hx, TOP, Z1 + 1, "bricks")
        chimney(p, hx, Z1 + 1, TOP + 1, 8)
    banner(p, 7, TOP, Z1 - 1, "north")
    bunk_row(p, (1, 3, 11, 13), Z1 - 2, upper=(1, 13), chests=(2, 12))
    bunk_row(p, (6, 8), Z1 - 2)
    p.put(7, 1, Z1 - 1, "minecraft:chest[facing=north,type=single,waterlogged=false]")
    for tx in (4, 10):
        table(p, tx, 1, 4)
        stool(p, tx - 1, 1, 4, "west")
        stool(p, tx + 1, 1, 4, "east")
    rack(p, (1, 2, 3), Z0 + 1)
    rack(p, (11, 12, 13), Z0 + 1)
    p.put(7, 1, Z0 + 1, "carpet")
    p.put(4, 7, 4, "lantern_hanging")
    p.put(10, 7, 4, "lantern_hanging")
    finish(p, 7, "a long timber-framed barracks with ten bunks, two hearths and two mess tables",
           ["Door and building_in [7, 0, 0]; post soldier [10, 1, 3]."],
           where=lambda x, y, z: not (X0 < x < X1 and Z0 < z < Z1))


def barracks_mess():
    """A barracks with a mess hall: a deep hall under a roof whose ridge runs north-south, the mess in front (a long
    table with benches, a hearth and chimney on the east wall, a dresser of barrels) and the bunk room behind a
    partition with a door. Post: soldier [3, 1, 4]."""
    X0, X1, Z0, Z1, TOP = 0, 10, 1, 13, 3
    p = _piece("barracks_mess", "Barracks with a Mess Hall", (X1 + 1, 10, Z1 + 1))
    frame_hall(p, X0, X1, Z0, Z1, TOP, 5, posts_x=(3, 7), posts_z=(4, 7, 10), roof="z")
    for z in (3, 9, 11):
        window(p, X0, 2, z, "west", shutters=False, sill=False)
        window(p, X1, 2, z, "east", shutters=False, sill=False)
    window(p, 2, 2, Z0, "north")
    window(p, 8, 2, Z0, "north")
    # the partition with its door, the mess in front
    p.fill(X0 + 1, 1, 7, X1 - 1, TOP, 7, "spruce")
    p.fill(4, 1, 7, 4, TOP, 7, "post")
    p.fill(6, 1, 7, 6, TOP, 7, "post")
    door(p, 5, 1, 7, facing="south")
    for z in range(3, 6):
        table(p, 5, 1, z)
        stool(p, 4, 1, z, "west")
        stool(p, 6, 1, z, "east")
    p.fill(X1 - 1, 1, 4, X1 - 1, TOP, 5, "bricks")
    p.put(X1 - 1, 1, 4, "minecraft:campfire[facing=west,lit=false,signal_fire=false,waterlogged=false]")
    chimney(p, X1 - 1, 5, TOP + 1, 9)
    for z in (2, 3):
        p.put(1, 1, z, "minecraft:barrel[facing=east,open=false]")
        p.put(1, 2, z, "minecraft:barrel[facing=east,open=false]")
    p.put(1, 1, 6, "pirates_n_ships:cargo_crate")
    banner(p, 5, TOP, 6, "north")
    # the bunk room
    for z in (9, 11):
        bed(p, 1, 1, z, "west")
        bed(p, X1 - 1, 1, z, "east")
        bed(p, 1, 2, z, "west")
    for z in (10, 12):
        p.put(1, 1, z, "minecraft:chest[facing=east,type=single,waterlogged=false]")
    rack(p, (4, 5, 6), Z1 - 1)
    p.put(X1 - 1, 1, 12, "minecraft:barrel[facing=up,open=false]")
    p.put(X1 - 1, 2, 12, "lantern")
    p.put(5, 7, 4, "lantern_hanging")
    p.put(5, 7, 10, "lantern_hanging")
    finish(p, 5, "a barracks with a mess hall: a long table, a hearth, and a bunk room behind a partition",
           ["Door and building_in [5, 0, 0]; post soldier [3, 1, 4]."],
           where=lambda x, y, z: not (X0 < x < X1 and Z0 < z < Z1))


# ---------------------------------------------------------------------------------------------- the large brig

BRIG_BARS = "pirates_n_ships:brig_bars[east=true,north=false,south=false,waterlogged=false,west=true]"


def brig_large():
    """A larger brig (13x10): the ST3 lock-up's masonry round a guard room across the front and four cells along the
    back, each fronted by brig bars with a brig door (closed, unlocked), straw, a bucket and a barred window; a flat
    roof behind merlons. Post: soldier [6, 1, 3] facing the cells."""
    X0, X1, Z0, Z1, TOP = 0, 12, 1, 9, 3
    ROOF = TOP + 1
    CELL_Z = 5
    p = _piece("brig_large", "Large Brig", (X1 + 1, 8, Z1 + 1))
    p.fill(X0, 0, Z0, X1, 0, Z1, "bricks")
    p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, CELL_Z - 1, "andesite")
    p.ring(X0, Z0, X1, Z1, 1, TOP, "bricks")
    p.fill(X0, 1, Z0, X1, 1, Z0, "chiseled")
    p.fill(X0, TOP, Z0, X1, TOP, Z0, "andesite")
    p.fill(X0, ROOF, Z0, X1, ROOF, Z1, "bricks")
    p.fill(X0, ROOF, Z0, X1, ROOF, Z0, "andesite")
    walls = (3, 6, 9)                                  # the dividers between the four cells
    fort_face(p, [(X0, z) for z in range(Z0 + 1, Z1 + 1)], "east", pilasters={(X0, CELL_Z), (X0, Z1)}, corbel=TOP)
    fort_face(p, [(X1, z) for z in range(Z0 + 1, Z1 + 1)], "west", pilasters={(X1, CELL_Z), (X1, Z1)}, corbel=TOP)
    fort_face(p, [(x, Z1) for x in range(X0, X1 + 1)], "north", pilasters={(X0, Z1), (X1, Z1)} | {(x, Z1) for x in walls},
              corbel=TOP)
    for z in range(Z0 + 1, Z1):
        if z != CELL_Z:
            p.put(X0 + 1, 2, z, "bricks")
            p.put(X1 - 1, 2, z, "bricks")
    for x in range(X0 + 1, X1):
        if x not in walls:
            p.put(x, 2, Z1 - 1, "bricks")
    # the parapet round the roof with merlons, lanterns on the front corners
    ring = sorted({(x, Z0) for x in range(X0, X1 + 1)} | {(x, Z1) for x in range(X0, X1 + 1)}
                  | {(X0, z) for z in range(Z0, Z1 + 1)} | {(X1, z) for z in range(Z0, Z1 + 1)})
    for x, z in ring:
        p.put(x, ROOF + 1, z, "brick_slab")
    merlons(p, [(x, z) for x, z in ring if (x + z) % 2 == 1 and (x, z) not in ((X0, Z0), (X1, Z0))], ROOF + 1)
    for x in (X0, X1):
        p.put(x, ROOF + 1, Z0, "bricks")
        p.put(x, ROOF + 2, Z0, "lantern")
    # the front: buttresses, the portal round the door, barred windows, lantern posts
    for x in (X0, X1, 4, 8):
        if x in (X0, X1):
            buttress(p, x, 0, TOP, "north")
    door(p, 6, 1, Z0, facing="north")
    p.fill(5, 0, 0, 7, 0, 0, "cobble")
    for x in (5, 7):
        p.fill(x, 1, 0, x, 2, 0, "bricks")
        p.put(x, 1, 0, "chiseled")
    p.put(5, TOP, 0, stair_shape("stone_brick", "west", "top"))
    p.put(7, TOP, 0, stair_shape("stone_brick", "east", "top"))
    p.put(6, TOP, 0, "chiseled")
    p.fill(5, ROOF, 0, 7, ROOF, 0, "brick_slab")
    p.put(6, TOP, Z0, "wool")
    for x in (2, 10):
        p.put(x, 2, Z0, bars("x"))
    lantern_post(p, 3, 1, 0, height=1)
    lantern_post(p, 9, 1, 0, height=1)
    # the four cells: dividers, a front of brig bars with a brig door, straw, a bucket, a window, shackles
    for x in walls:
        p.fill(x, 1, CELL_Z, x, TOP, Z1, "bricks")
    for x0 in (1, 4, 7, 10):
        for x in (x0, x0 + 1):
            for y in range(1, TOP + 1):
                p.put(x, y, CELL_Z, BRIG_BARS)
        brig_door(p, x0, 1, CELL_Z, facing="south", hinge="left")
        p.put(x0, 2, Z1 - 1, bars("x")) if x0 != 4 else p.put(x0 + 1, 2, Z1 - 1, bars("x"))
        p.put(x0 + 1, 1, Z1 - 1, "minecraft:hay_block[axis=z]")
        p.put(x0, 1, Z1 - 1, "minecraft:white_carpet")
        p.put(x0 + 1, 1, CELL_Z + 1, "minecraft:cauldron")
    chain(p, 4, 2, TOP, CELL_Z + 2)
    chain(p, 8, 2, TOP, CELL_Z + 2)
    # the guard room: table, stools, a chest and barrels, the keys' hooks, a hanging lantern, a bench by the window
    table(p, 3, 1, 3)
    stool(p, 2, 1, 3, "west")
    p.put(10, 1, 2, "minecraft:chest[facing=west,type=single,waterlogged=false]")
    p.put(11, 1, 2, "minecraft:barrel[facing=up,open=false]")
    p.put(11, 2, 2, "lantern")
    p.put(11, 1, 3, "pirates_n_ships:cargo_crate")
    chain(p, 1, TOP, TOP, 4)
    p.put(6, TOP, 3, "lantern_hanging")
    for x in (8, 9):
        p.put(x, 1, 2, stair_shape("spruce", "north"))
    finish(p, 6, "a larger stone brig: a guard room and four cells of brig bars under a crenellated roof",
           ["Door and building_in [6, 0, 0]; cell fronts on z 5 with brig doors at x 1, 4, 7, 10.",
            "Post soldier [6, 1, 3] facing the cells (south)."],
           where=lambda x, y, z: not (X0 < x < X1 and Z0 < z < Z1 and y == 0))


# ---------------------------------------------------------------------------------------------- towers


def signal_tower():
    """A signal tower (7x7): a stone shaft (5x5, corner pilasters, set-back sides, andesite bands) with a ladder to a
    spruce gallery on dark oak corbels (y 10, a fence rail, lanterns on the corner posts), and on the gallery an iron
    brazier: a signal fire (a campfire on a hay bale) whose smoke column is seen from the sea. Post: soldier
    [1, 11, 1] on the gallery."""
    S, DECK = 7, 10
    p = _piece("signal_tower", "Signal Tower", (S, 14, S))
    p.fill(1, 0, 1, 5, 0, 5, "bricks")
    p.fill(2, 0, 2, 4, 0, 4, "spruce")
    p.ring(1, 1, 5, 5, 1, DECK - 1, "bricks")
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        p.put(x, 1, z, "chiseled")
    for y in (4, 8):
        p.ring(1, 1, 5, 5, y, y, "andesite")
    for x in (2, 4):
        p.fill(x, 2, 1, x, DECK - 2, 1, "bricks_mossy" if x == 2 else "bricks")
    door(p, 3, 1, 1, facing="north")
    p.fill(2, 0, 0, 4, 0, 0, "cobble")
    p.put(3, 3, 1, "wool")
    for x in (0, S - 1):
        buttress(p, x if x == 0 else S - 1, 1, 3, "west" if x == 0 else "east")
    for y in range(1, DECK + 1):
        p.put(3, y, 4, "ladder_n")
    for y, z in ((3, 3), (6, 2), (7, 4)):
        p.put(1, y, z, bars("z"))
        p.put(5, y, z, bars("z"))
    p.put(3, 6, 1, bars("x"))
    p.put(3, 6, 5, bars("x"))
    # the gallery on corbels over the whole box
    for x in range(S):
        for z in range(S):
            if not (1 <= x <= 5 and 1 <= z <= 5):
                facing = "south" if z == 0 else "north" if z == S - 1 else "east" if x == 0 else "west"
                p.put(x, DECK - 1, z, stair_shape("dark_oak", facing, "top"))
    p.fill(0, DECK, 0, S - 1, DECK, S - 1, "spruce")
    p.put(3, DECK, 4, "ladder_n")
    corners = [(0, 0), (S - 1, 0), (0, S - 1), (S - 1, S - 1)]
    edge = [(x, z) for x in range(S) for z in range(S) if (x in (0, S - 1) or z in (0, S - 1)) and (x, z) not in corners]
    fence_run(p, edge, DECK + 1)
    for x, z in corners:
        p.fill(x, DECK + 1, z, x, DECK + 2, z, "log")
        p.put(x, DECK + 3, z, "lantern")
    # the brazier: a hay bale under a signal fire inside an iron cage
    p.put(3, DECK + 1, 2, "minecraft:hay_block[axis=y]")
    p.put(3, DECK + 2, 2, "minecraft:campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]")
    for x, z in ((2, 2), (4, 2)):
        p.put(x, DECK + 1, z, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
    banner(p, 3, 7, 0, "north")
    p.put(5, DECK + 1, 4, "minecraft:barrel[facing=up,open=false]")
    finish(p, 3, "a signal tower: a stone shaft, a gallery on corbels and a signal fire on a hay bale",
           ["Door and building_in [3, 0, 0]; gallery floor y 10; post soldier [1, 11, 1]."],
           where=lambda x, y, z: y < DECK - 1)


# ---------------------------------------------------------------------------------------------- quarters


def officers_quarters():
    """The officers' quarters (11x9): a stone ground floor (stone bricks, chiseled corners, an andesite course) under
    a timber upper floor (polished andesite infill between stripped posts), a balcony over the door on dark oak
    brackets, a spruce roof with a chimney. Downstairs the captain's office (a desk of a cartography table and a
    lectern, bookshelves, a map chest) and a dining table; a ladder to the bedrooms (two beds, chests, a wardrobe of
    barrels). Posts: officer [3, 1, 4], soldier [8, 1, 3]."""
    X0, X1, Z0, Z1 = 0, 10, 1, 7
    G, UP = 3, 7                  # the ground floor's last wall row, the upper floor's
    p = _piece("officers_quarters", "Officers' Quarters", (X1 + 1, 13, Z1 + 2))
    p.ring(X0, Z0, X1, Z1, 0, 0, "bricks")
    p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "oak")
    p.ring(X0, Z0, X1, Z1, 1, G, "bricks")
    for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        p.fill(x, 1, z, x, G, z, "chiseled")
    p.ring(X0, Z0, X1, Z1, G + 1, G + 1, "beam_x")
    for z in range(Z0 + 1, Z1):
        p.put(X0, G + 1, z, "beam_z")
        p.put(X1, G + 1, z, "beam_z")
    p.fill(X0 + 1, G + 1, Z0 + 1, X1 - 1, G + 1, Z1 - 1, "spruce")
    p.ring(X0, Z0, X1, Z1, G + 2, UP, "andesite")
    for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        p.fill(x, G + 2, z, x, UP, z, "log")
    for x in (3, 7):
        for z in (Z0, Z1):
            p.fill(x, G + 2, z, x, UP, z, "post")
    p.roof_ridge_x(X0, X1, Z0, Z1, UP + 1, "minecraft:spruce_stairs", "spruce_slab", "spruce", plate="beam_x")
    for x in range(X0, X1 + 1):
        p.put(x, UP + 1, Z0 - 1, stair_shape("dark_oak", "south"))
        p.put(x, UP + 1, Z1 + 1, stair_shape("dark_oak", "north"))
    rafter_ends(p, [(x, Z1 + 1) for x in (1, 3, 7, 9)], UP, "south")
    chimney(p, 9, 4, UP + 1, 11)
    # door, windows, the balcony over the door
    door(p, 5, 1, Z0, facing="north")
    p.fill(3, 0, 0, 7, 0, 0, "cobble")
    for x in (4, 6):
        p.fill(x, 1, Z0, x, G, Z0, "dark_post")
    p.put(5, G, Z0, "wool")
    for x in (2, 8):
        window(p, x, 2, Z0, "north", shutters=False)
        window(p, x, 2, Z1, "south")
    for x in (2, 8):
        window(p, x, G + 3, Z0, "north", shutters=False, sill=False)
        window(p, x, G + 3, Z1, "south")
    for z in (3, 5):
        p.put(X0, 2, z, pane("z"))
        p.put(X1, 2, z, pane("z"))
        p.put(X0, G + 3, z, pane("z"))
        p.put(X1, G + 3, z, pane("z"))
    p.put(5, G + 2, Z0, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    p.put(5, G + 3, Z0, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    for x in range(3, 8):
        p.put(x, G + 1, 0, "spruce_slab_top")
    fence_run(p, [(x, 0) for x in range(3, 8)], G + 2)
    for x in (3, 7):
        p.put(x, G, 0, stair_shape("dark_oak", "south", "top"))
    banner(p, 5, UP, 0, "north")
    # the office and the dining table
    p.put(2, 1, 5, "minecraft:cartography_table")
    p.put(3, 1, 5, "minecraft:lectern[facing=north,has_book=false,powered=false]")
    stool(p, 2, 1, 6, "south")
    p.put(1, 1, 6, "minecraft:bookshelf")
    p.put(1, 2, 6, "minecraft:bookshelf")
    p.put(1, 1, 5, "minecraft:bookshelf")
    p.put(1, 1, 2, "minecraft:chest[facing=east,type=single,waterlogged=false]")
    p.fill(2, 1, 3, 4, 1, 4, "minecraft:blue_carpet")
    p.clear(3, 1, 4)
    for tx in (7, 8):
        table(p, tx, 1, 5)
    stool(p, 6, 1, 5, "west")
    stool(p, 9, 1, 5, "east")
    p.put(9, 1, 2, "minecraft:barrel[facing=up,open=false]")
    p.put(9, 2, 2, "lantern")
    for y in range(1, G + 2):
        p.put(X1 - 1, y, Z1 - 1, "minecraft:ladder[facing=north]")
    p.put(5, G, 4, "lantern_hanging")
    # upstairs: two beds, chests, a wardrobe of barrels, a rug
    bed(p, 2, G + 2, 3, "south")
    bed(p, 6, G + 2, 3, "south")
    p.put(3, G + 2, 5, "minecraft:chest[facing=north,type=single,waterlogged=false]")
    p.put(7, G + 2, 5, "minecraft:chest[facing=north,type=single,waterlogged=false]")
    p.put(1, G + 2, 2, "minecraft:barrel[facing=east,open=false]")
    p.put(1, G + 3, 2, "minecraft:barrel[facing=east,open=false]")
    p.fill(4, G + 2, 3, 5, G + 2, 4, "minecraft:blue_carpet")
    p.put(5, UP + 3, 4, "lantern_hanging")
    finish(p, 5, "the officers' quarters: stone below, timber above, a balcony over the door, the captain's office",
           ["Door and building_in [5, 0, 0]; posts officer [3, 1, 4], soldier [8, 1, 3]."],
           where=lambda x, y, z: y <= G and not (X0 < x < X1 and Z0 < z < Z1))


# ---------------------------------------------------------------------------------------------- the magazine


def powder_magazine():
    """A powder magazine (9x9), half sunk into an earth bank: thick stone walls (x 1..7, z 1..7) whose sides and back
    are buried to two rows under a bank of earth and grass (moss on its crest), a vaulted stone roof under turf, a
    chiseled band and an iron door with a lever between two short retaining walls in front; inside, kegs and crates of
    powder and shot, a rack, no open flame. A jigsaw piece cannot carve a room below the ground (its air is dropped,
    the terrain stays), so the magazine sinks by the bank, not by its floor. Post: soldier [2, 1, 5]."""
    X0, X1, Z0, Z1 = 1, 7, 1, 7
    p = _piece("powder_magazine", "Powder Magazine", (9, 8, 9))
    p.fill(X0, 0, Z0, X1, 0, Z1, "bricks")
    p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "andesite")
    p.ring(X0, Z0, X1, Z1, 1, 4, "bricks")
    p.fill(X0, 1, Z0, X1, 1, Z0, "chiseled")
    p.fill(X0, 4, Z0, X1, 4, Z0, "andesite")
    # the vault: stair courses rising from the side walls, turf on top
    for z in range(Z0, Z1 + 1):
        p.put(X0, 5, z, stair_shape("stone_brick", "east"))
        p.put(X1, 5, z, stair_shape("stone_brick", "west"))
        p.fill(X0 + 1, 5, z, X1 - 1, 5, z, "bricks")
        p.put(X0 + 1, 6, z, stair_shape("mossy_stone_brick", "east"))
        p.put(X1 - 1, 6, z, stair_shape("mossy_stone_brick", "west"))
        p.fill(X0 + 2, 6, z, X1 - 2, 6, z, "minecraft:moss_block")
        p.put(4, 7, z, "minecraft:moss_carpet")
    # the earth bank against the sides and the back, two rows up, grass on its crest, moss at the corners
    bank = [(0, z) for z in range(Z0 + 1, Z1 + 2)] + [(8, z) for z in range(Z0 + 1, Z1 + 2)] + \
        [(x, 8) for x in range(1, 8)]
    for x, z in bank:
        p.put(x, 1, z, "minecraft:coarse_dirt")
        p.put(x, 2, z, "minecraft:grass_block[snowy=false]" if (x + z) % 3 else "minecraft:moss_block")
    for x, z in ((0, Z0), (8, Z0)):          # the retaining walls' ends in front
        p.fill(x, 0, z, x, 2, z, "bricks")
        p.put(x, 3, z, "brick_slab")
    # the door between short retaining walls, a lever, a red warning banner over it
    for x in (3, 5):
        p.fill(x, 0, 0, x, 1, 0, "bricks")
        p.put(x, 2, 0, "brick_slab")
    door(p, 4, 1, Z0, facing="north", wood="iron")
    p.put(4, 3, Z0, "chiseled")
    banner(p, 4, 3, 0, "north", colour="red")
    # inside: kegs and crates on racks, shot, no flame
    for x in range(2, 7):
        p.put(x, 1, Z1 - 1, "pirates_n_ships:cargo_barrel" if x % 2 else "minecraft:barrel[facing=up,open=false]")
        p.put(x, 2, Z1 - 1, "pirates_n_ships:cargo_barrel" if x % 2 == 0 else "dark_oak")
    for z in (3, 4):
        p.put(6, 1, z, "pirates_n_ships:cargo_crate")
        p.put(6, 2, z, "pirates_n_ships:cargo_crate")
    p.put(2, 1, 3, "minecraft:barrel[facing=up,open=false]")
    p.put(2, 1, 4, "minecraft:heavy_core[waterlogged=false]")
    p.fill(3, 0, 3, 5, 0, 5, "dark_oak")
    finish(p, 4, "a powder magazine half sunk into an earth bank: thick stone walls, a turf vault, an iron door",
           ["Door and building_in [4, 0, 0]; post soldier [2, 1, 5]."],
           where=lambda x, y, z: X0 <= x <= X1 and Z0 <= z <= Z1)


# ---------------------------------------------------------------------------------------------- the armoury


def armoury():
    """The armoury (9x9): stone ground walls with pilasters and barred windows, a spruce upper gable, a stone chimney
    over the forge; inside a forge (a blast furnace with an anvil and a grindstone), weapon racks, a smithing table,
    chests and barrels of arms. Post: soldier [6, 1, 3]."""
    X0, X1, Z0, Z1, TOP = 0, 8, 1, 7, 3
    p = _piece("armoury", "Armoury", (X1 + 1, 10, Z1 + 2))
    p.fill(X0, 0, Z0, X1, 0, Z1, "bricks")
    p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "andesite")
    p.ring(X0, Z0, X1, Z1, 1, TOP, "bricks")
    p.fill(X0, 1, Z0, X1, 1, Z0, "chiseled")
    p.ring(X0, Z0, X1, Z1, TOP + 1, TOP + 1, "andesite")
    p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 2, "minecraft:spruce_stairs", "spruce_slab", "spruce", plate="beam_x")
    for x in range(X0, X1 + 1):
        p.put(x, TOP + 2, Z0 - 1, stair_shape("dark_oak", "south"))
        p.put(x, TOP + 2, Z1 + 1, stair_shape("dark_oak", "north"))
    rafter_ends(p, [(x, Z1 + 1) for x in (1, 3, 5, 7)], TOP + 1, "south")
    for x in (X0, X1):
        for z in range(Z0 + 1, Z1):
            p.put(x, TOP + 2, z, "beam_z")
    door(p, 4, 1, Z0, facing="north", wood="dark_oak")
    p.fill(3, 0, 0, 5, 0, 0, "cobble")
    for x in (3, 5):
        p.put(x, 1, 0, "bricks")
        p.put(x, 2, 0, stair_shape("stone_brick", "north"))
    p.put(4, 3, Z0, "wool")
    p.put(3, 3, 0, "lantern")
    p.put(5, 3, 0, "lantern")
    for x in (2, 6):
        p.put(x, 2, Z0, bars("x"))
        p.put(x, 2, Z1, bars("x"))
    for z in (3, 5):
        p.put(X0, 2, z, bars("z"))
        p.put(X1, 2, z, bars("z"))
    # the forge in the back corner with its chimney
    p.put(7, 1, 6, "minecraft:blast_furnace[facing=north,lit=false]")
    p.fill(7, 2, 6, 7, TOP, 6, "bricks")
    chimney(p, 7, 6, TOP + 1, 9)
    p.put(6, 1, 6, "minecraft:anvil[facing=east]")
    p.put(5, 1, 6, "minecraft:grindstone[face=floor,facing=north]")
    p.put(7, 1, 5, "minecraft:smithing_table")
    p.put(7, 1, 4, "minecraft:cauldron")
    rack(p, (1, 2, 3), 6)
    rack(p, (1,), 3)
    rack(p, (1,), 4)
    p.put(1, 1, 2, "minecraft:chest[facing=east,type=single,waterlogged=false]")
    p.put(7, 1, 2, "minecraft:barrel[facing=west,open=false]")
    p.put(6, 1, 2, "pirates_n_ships:cargo_crate")
    p.put(4, TOP + 3, 4, "lantern_hanging")
    finish(p, 4, "a stone armoury with a forge, weapon racks and chests of arms under a spruce gable",
           ["Door and building_in [4, 0, 0]; post soldier [6, 1, 3]."],
           where=lambda x, y, z: not (X0 < x < X1 and Z0 < z < Z1))


# ---------------------------------------------------------------------------------------------- the gun battery


def gun_battery():
    """A gun battery (11x9): a raised stone platform (deck y 2) reached by a ramp of stairs from the lane, two cannons
    in embrasures of its far parapet (facing away from the lane), gabions (barrels with soil), heaps of shot, powder
    kegs, a flag staff of fences with a blue banner. Post: soldier [5, 3, 4]."""
    X0, X1, Z0, Z1 = 1, 9, 2, 8
    DECK = 2
    p = _piece("gun_battery", "Gun Battery", (11, 6, 9))
    p.fill(X0, 0, Z0, X1, DECK - 1, Z1, "bricks")
    p.fill(X0, DECK, Z0, X1, DECK, Z1, "andesite")
    fort_face(p, [(x, Z1) for x in range(X0, X1 + 1)], "north", pilasters={(X0, Z1), (5, Z1), (X1, Z1)}, corbel=DECK)
    fort_face(p, [(X0, z) for z in range(Z0, Z1)], "east", pilasters={(X0, Z0)}, corbel=DECK)
    fort_face(p, [(X1, z) for z in range(Z0, Z1)], "west", pilasters={(X1, Z0)}, corbel=DECK)
    for x, z in ((X0, Z1), (5, Z1), (X1, Z1), (X0, Z0), (X1, Z0)):
        p.put(x, DECK, z, "andesite")
    # the far parapet with two embrasures, low side parapets
    for x in range(X0, X1 + 1):
        if x not in (3, 7):
            p.put(x, DECK + 1, Z1, "bricks")
    merlons(p, [(x, Z1) for x in (X0, 5, X1)], DECK + 2)
    for z in range(Z0, Z1):
        p.put(X0, DECK + 1, z, "brick_slab")
        p.put(X1, DECK + 1, z, "brick_slab")
    p.fill(2, DECK, 6, 8, DECK, 7, "dark_oak")
    cannon(p, 3, DECK + 1, Z1 - 1, "south")
    cannon(p, 7, DECK + 1, Z1 - 1, "south")
    p.put(5, DECK + 1, Z1 - 1, "minecraft:heavy_core[waterlogged=false]")
    p.put(2, DECK + 1, Z1 - 1, "pirates_n_ships:cargo_barrel")
    p.put(8, DECK + 1, Z1 - 1, "pirates_n_ships:cargo_barrel")
    # gabions along the sides, the ramp, a flag staff
    for z in (3, 5):
        p.put(2, DECK + 1, z, "minecraft:composter[level=8]")
        p.put(8, DECK + 1, z, "minecraft:composter[level=8]")
    for x in (4, 5, 6):
        p.put(x, 1, 1, stair_shape("stone_brick", "south"))
        p.fill(x, 0, 1, x, 0, 1, "bricks")
    p.fill(3, 0, 0, 7, 0, 0, "cobble")
    p.put(3, 1, 1, "bricks")
    p.put(7, 1, 1, "bricks")
    p.fill(9, DECK + 1, 3, 9, DECK + 3, 3, "fence")
    p.put(1, DECK + 1, 3, "minecraft:lantern[hanging=false]")
    finish(p, 5, "a gun battery: a raised stone platform with two cannons in embrasures, gabions and shot",
           ["Door side and building_in [5, 0, 0] (the ramp); deck y 2; cannons face south; post soldier [5, 3, 4]."])


# ---------------------------------------------------------------------------------------------- the store house


def store_house():
    """A store house the size of a stable (11x9): a stone plinth, spruce board walls on a log frame, wide double doors
    under a hoist beam with a chain, a loft of planks; inside hay, barrels, crates, a cart's wheels. Post: soldier
    [2, 1, 3]."""
    X0, X1, Z0, Z1, TOP = 0, 10, 1, 7, 4
    p = _piece("store_house", "Store House", (X1 + 1, 10, Z1 + 2))
    p.ring(X0, Z0, X1, Z1, 0, 0, "bricks")
    p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "cobble")
    p.ring(X0, Z0, X1, Z1, 1, 1, "bricks")
    p.ring(X0, Z0, X1, Z1, 2, TOP, "spruce")
    for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
        p.fill(x, 1, z, x, TOP, z, "log")
    for x in (3, 7):
        for z in (Z0, Z1):
            p.fill(x, 2, z, x, TOP, z, "post")
    p.ring(X0, Z0, X1, Z1, TOP, TOP, "beam_x")
    for z in range(Z0 + 1, Z1):
        p.put(X0, TOP, z, "beam_z")
        p.put(X1, TOP, z, "beam_z")
    p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab[type=bottom]", "spruce")
    for x in range(X0, X1 + 1):
        p.put(x, TOP + 1, Z0 - 1, stair_shape("dark_oak", "south"))
        p.put(x, TOP + 1, Z1 + 1, stair_shape("dark_oak", "north"))
    # the double doors and the hoist
    door(p, 5, 1, Z0, facing="north", hinge="left")
    door(p, 6, 1, Z0, facing="north", hinge="right")
    p.fill(4, 1, Z0, 4, 3, Z0, "dark_post")
    p.fill(7, 1, Z0, 7, 3, Z0, "dark_post")
    p.fill(5, 3, Z0, 6, 3, Z0, "beam_x")
    p.fill(4, 0, 0, 7, 0, 0, "cobble")
    p.put(5, TOP + 1, 0, "beam_z")
    p.put(5, TOP, 0, "chain")
    for x in (2, 9):
        window(p, x, 2, Z0, "north")
    for x in (2, 8):
        window(p, x, 2, Z1, "south")
    # the loft over the back half, stores below
    p.fill(1, TOP, 4, 9, TOP, 6, "spruce")
    for y in range(1, TOP):
        p.put(9, y, 3, "minecraft:ladder[facing=north]")
    for x in (1, 2, 3):
        p.put(x, 1, 6, "minecraft:hay_block[axis=x]")
        p.put(x, 2, 6, "minecraft:hay_block[axis=z]") if x < 3 else None
    for x in (7, 8, 9):
        p.put(x, 1, 6, "minecraft:barrel[facing=up,open=false]")
    p.put(8, 2, 6, "pirates_n_ships:cargo_barrel")
    p.put(9, 1, 5, "pirates_n_ships:cargo_crate")
    p.put(9, 2, 5, "pirates_n_ships:cargo_crate")
    p.put(5, 1, 6, "pirates_n_ships:cargo_crate")
    p.put(5, TOP + 1, 5, "minecraft:hay_block[axis=x]")
    p.put(3, TOP + 1, 5, "pirates_n_ships:cargo_crate")
    p.put(1, 1, 3, "minecraft:barrel[facing=east,open=false]")
    p.put(5, TOP - 1, 4, "lantern_hanging")
    finish(p, 5, "a timber store house the size of a stable, double doors under a hoist beam, a loft of stores",
           ["Doors at x 5..6 and building_in [5, 0, 0]; post soldier [2, 1, 3]."],
           where=lambda x, y, z: not (X0 < x < X1 and Z0 < z < Z1))


# ---------------------------------------------------------------------------------------------- the chapel


def chapel():
    """A garrison chapel (9x13): a stone nave with buttresses between tall pointed windows, a steep spruce roof, a
    bell gable over the door with the chapel's bell; inside pews (spruce stairs) either side of a blue runner, an altar
    of chiseled stone with candles and a banner over it. Post: soldier [1, 1, 3] by the door."""
    X0, X1, Z0, Z1, TOP = 1, 7, 1, 11, 5
    p = _piece("chapel", "Chapel", (9, 14, 13))
    p.fill(X0, 0, Z0, X1, 0, Z1, "bricks")
    p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "andesite")
    p.ring(X0, Z0, X1, Z1, 1, TOP, "bricks")
    p.ring(X0, Z0, X1, Z1, 1, 1, "chiseled")
    p.ring(X0, Z0, X1, Z1, 4, 4, "andesite")
    for z in (3, 5, 7, 9):                       # tall windows between the buttresses
        for x in (X0, X1):
            p.fill(x, 2, z, x, 3, z, pane("z"))
            p.put(x, TOP, z, pane("z"))
    for z in (2, 6, 10):
        buttress(p, 0, z, 4, "west")
        buttress(p, 8, z, 4, "east")
    p.roof_ridge_z(Z0, Z1, X0, X1, TOP + 1, "minecraft:spruce_stairs", "spruce_slab", "bricks", overhang=1)
    # the bell gable over the door: the front wall rises to a pointed opening with the bell
    for y in range(TOP + 1, TOP + 5):
        w = TOP + 4 - y
        p.fill(4 - w, y, Z0, 4 + w, y, Z0, "bricks")
    p.fill(3, TOP + 2, Z0, 5, TOP + 3, Z0, "bricks")
    p.clear(4, TOP + 2, Z0)
    p.clear(4, TOP + 3, Z0)
    p.put(4, TOP + 3, Z0, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
    p.put(4, TOP + 4, Z0, "chiseled")
    p.put(4, TOP + 5, Z0, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    # the door under a pointed arch, the step
    door(p, 4, 1, Z0, facing="north", wood="spruce")
    p.put(4, 3, Z0, "chiseled")
    p.fill(3, 0, 0, 5, 0, 0, "cobble")
    banner(p, 4, 4, 0, "north")
    lantern_post(p, 2, 1, 0, height=1)
    lantern_post(p, 6, 1, 0, height=1)
    # the nave: pews either side of a runner, the altar, candles, the banner
    for z in range(3, 9):
        if z % 2:
            for x in (2, 3, 5, 6):
                p.put(x, 1, z, stair_shape("spruce", "north"))
    p.fill(4, 1, 2, 4, 1, 9, "carpet")
    p.fill(3, 1, 10, 5, 1, 10, "chiseled")
    p.put(4, 2, 10, "minecraft:candle[candles=3,lit=false,waterlogged=false]")
    p.put(3, 2, 10, "minecraft:candle[candles=1,lit=false,waterlogged=false]")
    p.put(5, 2, 10, "minecraft:candle[candles=1,lit=false,waterlogged=false]")
    banner(p, 4, 3, Z1 - 1, "north")
    p.put(4, 9, 6, "lantern_hanging")
    p.put(6, 1, 2, "minecraft:barrel[facing=up,open=false]")
    p.put(4, TOP + 1, Z1, pane("x"))
    finish(p, 4, "a stone garrison chapel with buttresses, tall windows, a bell gable, pews and an altar",
           ["Door and building_in [4, 0, 0]; post soldier [2, 1, 2]."],
           where=lambda x, y, z: not (X0 < x < X1 and Z0 < z < Z1))
