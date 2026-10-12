"""Builder of the pirate island's small huts (STR2): a 7-wide one-room hut of vertical dark and spruce boards between
stripped corner posts under a patched dark oak thatch, in three looks (``hut_stilts``, ``hut_porch``, ``hut_thatch``).

Options: ``stilts`` raises the floor on palm trunk stilts with steps up (the floor at y 2, like the captain's hut),
``porch`` is the depth of the roofed porch in front of the door (on palm posts, with a rope rail), ``ridge`` is the
roof's ridge direction (``x``: gables on the sides, ``z``: gables front and back). Every hut has its door in the
middle of the north wall, the ``hut_in`` connector in front of it on the north edge, shuttered windows, a bed, a chest,
a table and a lantern inside, and trodden ground that fades into the terrain."""
from _camp import crate_stack, patch, rope_coil
from _style import (PALETTE, THATCH, bed, chimney, lantern_post, rafter_ends, roof_patches, rope_rail,
                    shutter_window, stool, table, weatherboard)
from buildspec import Piece

W = 7
X0, X1 = 0, W - 1
DOOR = 3
DEPTH = 5              # the room, front wall to back wall


def thatch(facing, half="bottom"):
    return f"{THATCH}[facing={facing},half={half},shape=straight]"


def hut(piece_id, name, stilts=False, porch=1, ridge="x", salt=0, chimney_at=None, extras=None, notes=""):
    floor = 2 if stilts else 0
    top = floor + 2                       # the last wall row
    z0 = (1 if stilts else 0) + porch     # the front wall (steps take row 0 on stilts)
    z1 = z0 + DEPTH - 1
    length = z1 + 2                       # one row behind for the back eave
    height = top + 6
    p = Piece(f"pirate_island_{piece_id}", name, (W, height, length), PALETTE)

    # ground under and around the hut
    if stilts:
        p.fill(X0 + 1, 0, z0 + 1, X1 - 1, 0, z1 - 1, "coarse")
    patch(p, X0, z0, X1, z1, reach=1, salt=salt + 1)

    if stilts:
        for x in (X0, X1):
            for z in (z0, z1):
                p.fill(x, 0, z, x, floor - 1, z, "palm")
        for z in (z0, z1):
            p.fill(DOOR, 0, z, DOOR, floor - 1, z, "dark_post")
        if porch:
            for x in (X0 + 1, X1 - 1):
                p.fill(x, 0, z0 - porch, x, floor - 1, z0 - porch, "palm")
        crate_stack(p, 1, 1, z0 + 2, salt=salt + 2)
        p.put(5, 1, z1 - 1, "minecraft:barrel[facing=east,open=false]")
    # the floor with a sill beam round its edge (on the ground row for a ground hut)
    p.fill(X0, floor, z0, X1, floor, z1, "spruce")
    for x, z in ((2, z0 + 1), (4, z0 + 3), (5, z0 + 1)):
        p.put(x, floor, z, "dark")
    p.fill(X0, floor, z0, X1, floor, z0, "dark_beam_x")
    p.fill(X0, floor, z1, X1, floor, z1, "dark_beam_x")
    p.fill(X0, floor, z0 + 1, X0, floor, z1 - 1, "dark_beam_z")
    p.fill(X1, floor, z0 + 1, X1, floor, z1 - 1, "dark_beam_z")
    pz0 = z0 - porch
    if porch:
        p.fill(X0 + 1, floor, pz0, X1 - 1, floor, z0 - 1, "spruce")
        p.put(2, floor, pz0, "minecraft:jungle_planks")
        if stilts:
            p.fill(X0 + 1, floor, pz0, X1 - 1, floor, pz0, "beam_x")
            p.put(DOOR, 1, pz0 - 1, stair_south())
            p.put(DOOR, floor, pz0, stair_south())
    elif stilts:
        p.put(DOOR, 1, z0 - 1, stair_south())

    # walls: vertical boards, stripped corner posts, dark door posts
    p.ring(X0, z0, X1, z1, floor + 1, top, "dark")
    weatherboard(p, X0, floor + 1, z0, X1, top, z0, salt=salt + 3)
    weatherboard(p, X0, floor + 1, z1, X1, top, z1, salt=salt + 4)
    weatherboard(p, X0, floor + 1, z0, X0, top, z1, salt=salt + 5)
    weatherboard(p, X1, floor + 1, z0, X1, top, z1, salt=salt + 6)
    for x in (X0, X1):
        for z in (z0, z1):
            p.fill(x, floor + 1, z, x, top, z, "post")
    for x in (DOOR - 1, DOOR + 1):
        p.fill(x, floor + 1, z0, x, top, z0, "dark_post")
    p.put(DOOR, floor + 1, z0, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    p.put(DOOR, floor + 2, z0, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    shutter_window(p, X0, floor + 2, z0 + 2, "west", wood="dark_oak")
    shutter_window(p, X1, floor + 2, z0 + 2, "east", wood="dark_oak")
    shutter_window(p, DOOR, floor + 2, z1, "south", sides=("left",), wood="dark_oak")

    # the porch: palm posts holding the front eave, a rope rail along its front
    if porch:
        for x in (X0 + 1, X1 - 1):
            p.fill(x, floor + 1, pz0, x, top, pz0, "palm")
        if stilts or porch > 1:
            rope_rail(p, X0 + 1, pz0, DOOR - 1, pz0, floor + 1)
            rope_rail(p, DOOR + 1, pz0, X1 - 1, pz0, floor + 1)
        if porch > 1:
            stool(p, X1 - 2, floor + 1, z0 - 1, "east")
            p.put(X0 + 2, floor + 1, z0 - 1, "minecraft:barrel[facing=up,open=false]")

    # the roof
    if ridge == "x":
        p.roof_ridge_x(X0, X1, z0, z1, top + 1, THATCH, "thatch_ridge", "dark", plate="beam_x")
        for x in (X0, X1):
            p.fill(x, top + 1, z0 + 1, x, top + 1, z1 - 1, "dark_beam_z")
            p.put(x, top + 3, z0 + 2, "fence")
        rafter_ends(p, [(X0, z1 + 1), (X1, z1 + 1)], top, "north")
        for z in range(pz0, z0 - 1):       # a deep porch: its front row under a lower course
            for x in range(X0, X1 + 1):
                p.put(x, top + 1, z, thatch("south"))
    else:
        p.roof_ridge_z(pz0 if porch else max(0, z0 - 1), z1 + 1, X0, X1, top + 1, THATCH, "thatch_ridge")
        for z in (z0, z1):
            for i in range(3):
                for x in range(i + 1, W - 1 - i):
                    p.put(x, top + 1 + i, z, "dark" if (x + i) % 2 else "spruce")
            p.put(DOOR, top + 3, z, "dark_post")
        p.put(DOOR - 1, top + 2, z1, "fence")
        p.put(DOOR + 1, top + 2, z0, "fence")
    roof_patches(p, salt=salt + 7)

    # inside: the bed along the east wall, a chest, a table and stool, a barrel with a lantern, a rug
    bed(p, X1 - 1, floor + 1, z1 - 2, "south", colour="brown")
    p.loot_chest(X0 + 1, floor + 1, z1 - 1, "east", "pirate_island/hut")
    table(p, X0 + 1, floor + 1, z0 + 2)
    stool(p, X0 + 2, floor + 1, z0 + 2, "east")
    p.put(X0 + 1, floor + 1, z0 + 1, "minecraft:barrel[facing=up,open=false]")
    p.put(X0 + 1, floor + 2, z0 + 1, "lantern")
    p.put(DOOR, floor + 1, z1 - 1, "minecraft:red_carpet")
    if chimney_at:
        cx, cz = chimney_at
        chimney(p, cx, cz, 0, top + 3)

    # outside the door
    if not stilts and porch < 2:
        lantern_post(p, X0, 1, 0, height=1)
    if porch and not stilts and pz0 > 0:
        rope_coil(p, X1, 1, 0, layers=1)
    if extras:
        extras(p, floor, z0, z1)

    on_floor = not stilts and (porch or z0 == 0)
    p.connector(DOOR, 0, 0, "hut_in", "north", "minecraft:spruce_planks" if on_floor else "minecraft:coarse_dirt")
    p.emit(name.lower() + notes,
           ["Faces north: the door and the hut_in connector are on the -z side.",
            f"y 0 is the foundation row (ground or stilt feet); the floor is y {floor}."])
    return p


def stair_south():
    return "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight]"
