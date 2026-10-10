"""STR3 builder of the seafarer village's cottages: ST1's small house generalised, so its looks share one frame
(art/README.md, "Natural layout of the village (STR3)"). ``cottage`` builds the house shell on a piece: the floor,
white render between stripped spruce corner posts on a mossy cobblestone plinth, a framed door under a hood in the
north wall, framed windows with sills (shutters where the box leaves room), a spruce wall plate, a gable roof with the
ridge along x (red tile or dark shingle), rafter ends under both eaves, boarded or rendered gables with a light, and a
brick chimney over a hearth. Options: ``storeys`` (1 or 2: an upper floor of boards behind a spruce band, reached by a
ladder), the roof, the gable infill, the door's column, the chimney's gable. ``furnish`` adds the usual furniture.

The door is always in the north wall with the doorstep in front of it on row z 0, where the piece's ``building_in``
connector goes (the piece file places it)."""
from _style import (DARK, TILE, bed, chimney, corner_post, door_frame, hearth, plinth, rafter_ends, table,
                    trim_band, window)

FLOOR2 = 4          # the upper floor's boards in a two-storey house (ground floor walls y 1..3)


def top_row(storeys):
    return 3 if storeys == 1 else 7


def peak(z0, z1, storeys):
    """The ridge's row (the topmost roof course) of a cottage with walls z0..z1."""
    return top_row(storeys) + 1 + (z1 - z0 + 2) // 2


def cottage(p, x0, z0, x1, z1, storeys=1, roof=TILE, gable="render", door_x=None, chimney_side="east", salt=0,
            back_windows=True):
    top = top_row(storeys)
    door_x = (x0 + x1) // 2 if door_x is None else door_x
    ridge = "tile_ridge" if roof == TILE else "dark_ridge"
    # floors; walls of render between stripped spruce corner posts on the stone plinth
    p.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, "spruce")
    p.ring(x0, z0, x1, z1, 1, top, "render")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        corner_post(p, x, z, 1, top)
    if x1 - x0 >= 8:                                     # long walls get a middle post
        for z in (z0, z1):
            corner_post(p, (x0 + x1) // 2 + (1 if (x0 + x1) // 2 == door_x else 0), z, 1, top)
    plinth(p, x0, z0, x1, z1, y=1)
    if storeys == 2:
        p.fill(x0 + 1, FLOOR2, z0 + 1, x1 - 1, FLOOR2, z1 - 1, "spruce")
        trim_band(p, x0, z0, x1, z1, FLOOR2)
    # the roof: ridge along x, eaves over the doorstep row and the back; gables over a spruce band
    p.roof_ridge_x(x0, x1, z0, z1, top + 1, roof, ridge, gable, plate="beam_x")
    trim_band(p, x0, z0, x1, z1, top + 1, only=(gable,))
    mid_z = (z0 + z1) // 2
    for x in (x0, x1):
        if p.get(x, top + 3, mid_z) == gable:
            p.put(x, top + 3, mid_z, "pane")
    rafter_ends(p, [(x, top, z0 - 1) for x in range(x0, x1 + 1, 2)], "north")
    rafter_ends(p, [(x, top, z1 + 1) for x in range(x0, x1 + 1, 2)], "south")
    # the door
    door_frame(p, door_x, 1, z0, "north", posts=False)
    # windows: either side of the door, the gable walls, the back; the upper floor as well
    rows = (2,) if storeys == 1 else (2, 6)
    for y in rows:
        for x in (door_x - 2, door_x + 2):
            if x0 < x < x1:
                window(p, x, y, z0, "north")
        for z in range(z0 + 2, z1 - 1, 2):
            window(p, x0, y, z, "west", lintel=False)
            window(p, x1, y, z, "east", lintel=False)
        if back_windows:
            for x in range(x0 + 2, x1 - 1, 3):
                window(p, x, y, z1, "south", lintel=False)
    # the chimney on one gable, the hearth at its foot
    cx = x1 if chimney_side == "east" else x0
    chimney(p, cx, z0 + 1, 1, peak(z0, z1, storeys))
    hearth(p, cx + (-1 if chimney_side == "east" else 1), 1, z0 + 1, "west" if chimney_side == "east" else "east")
    return top


def tie_beam_lantern(p, x0, x1, z, y):
    """A tie beam across the room at row ``y`` carrying a hanging lantern in the middle."""
    p.fill(x0 + 1, y, z, x1 - 1, y, z, "beam_x")
    p.put((x0 + x1) // 2, y - 1, z, "lantern_hanging")


def furnish(p, x0, z0, x1, z1, y=1, door_x=None, bed_colour="red", hearth_side="east", rug="red"):
    """The usual cottage furniture on row ``y``: a bed in the back corner away from the hearth, a table with a stool,
    a barrel with a lantern, a chest, a crafting table with a flower pot and a rug (cells already set are kept)."""
    door_x = (x0 + x1) // 2 if door_x is None else door_x
    far = x0 + 1 if hearth_side == "east" else x1 - 1     # the side away from the hearth
    near = x1 - 1 if hearth_side == "east" else x0 + 1

    def free(x, z, h=1):
        return all(p.get(x, y + dy, z) is None for dy in range(h)) and (x, z) != (door_x, z0 + 1)

    if free(far, z1 - 2) and free(far, z1 - 1):
        bed(p, far, y, z1 - 2, "south", bed_colour)
    if free(near, z1 - 1):
        p.put(near, y, z1 - 1, "barrel_up")
        p.put(near, y + 1, z1 - 1, "lantern")
    mid = (x0 + x1) // 2
    if free(mid, z1 - 1):
        p.put(mid, y, z1 - 1, "minecraft:chest[facing=north,type=single,waterlogged=false]")
    if free(far, z0 + 1):
        p.put(far, y, z0 + 1, "minecraft:crafting_table")
        p.put(far, y + 1, z0 + 1, "minecraft:flower_pot")
    tz = (z0 + z1) // 2
    if free(far, tz) and free(far + (1 if far == x0 + 1 else -1), tz):
        table(p, far, y, tz)
        p.put(far + (1 if far == x0 + 1 else -1), y, tz,
              f"minecraft:spruce_stairs[facing={'east' if far == x0 + 1 else 'west'},half=bottom]")
    if free(mid, tz):
        p.put(mid, y, tz, f"minecraft:{rug}_carpet")
