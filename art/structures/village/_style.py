"""The seafarer village's shared palette and fittings, and the import path for the generators in this folder.

The look (ST4a, design.md §10.1 "Look of the buildings"): Caribbean colonial, one small palette for the whole set.

* **Woods:** spruce for the frame (stripped spruce corner posts, wall plates and trim bands, rafter ends, shutters,
  doors, sills) and oak for the joinery that frames openings (stripped oak lintels and door frames).
* **Stone:** cobblestone for plinths and footings, mossy towards the ground (the foundation row is mostly mossy, the
  plinth course above it only here and there); stone bricks for the quay and the dock head's masonry.
* **Accent:** red brick, for the tile roofs, the ridge slabs and the chimneys; dark shingle (dark oak) roofs on the
  working buildings (the harbor master's hut, the shipwright's shed).
* **Infill:** white render (calcite) between the posts, spruce boarding in the gables.

Fittings are plain functions on a ``Piece``. ``out`` is always the direction pointing out of the wall the fitting sits
on; anything that would stick out past the piece's box is skipped (the box faces of a building are its walls on the
east and west, so those walls get their relief inside the wall plane: recessed sills, lintels, posts)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

PALETTE = {
    # ground and masonry
    "cobble": "minecraft:cobblestone",
    "mossy": "minecraft:mossy_cobblestone",
    "gravel": "minecraft:gravel",
    "path": "minecraft:dirt_path",
    "coarse": "minecraft:coarse_dirt",
    "bricks": "minecraft:stone_bricks",
    "bricks_mossy": "minecraft:mossy_stone_bricks",
    "bricks_cracked": "minecraft:cracked_stone_bricks",
    "chiseled": "minecraft:chiseled_stone_bricks",
    # timber
    "spruce": "minecraft:spruce_planks",
    "oak": "minecraft:oak_planks",
    "dark": "minecraft:dark_oak_planks",
    "post": "minecraft:stripped_spruce_log[axis=y]",
    "beam_x": "minecraft:stripped_spruce_log[axis=x]",
    "beam_z": "minecraft:stripped_spruce_log[axis=z]",
    "oak_post": "minecraft:stripped_oak_log[axis=y]",
    "oak_beam_x": "minecraft:stripped_oak_log[axis=x]",
    "oak_beam_z": "minecraft:stripped_oak_log[axis=z]",
    "pile": "minecraft:spruce_log[axis=y]",
    "fence": "minecraft:spruce_fence",
    # walls and roofs
    "render": "minecraft:calcite",
    "sandstone": "minecraft:smooth_sandstone",
    "pane": "minecraft:glass_pane",
    "tile_ridge": "minecraft:brick_slab[type=bottom]",
    "dark_ridge": "minecraft:dark_oak_slab[type=bottom]",
    # lights
    "lantern": "minecraft:lantern[hanging=false]",
    "lantern_hanging": "minecraft:lantern[hanging=true]",
}

# -------------------------------------------------------------------- ST4a fittings

OFFSET = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
ALONG = {"north": "x", "south": "x", "east": "z", "west": "z"}      # the axis a wall facing ``out`` runs along

PALETTE.update({
    "plinth": "minecraft:cobblestone",
    "plinth_mossy": "minecraft:mossy_cobblestone",
    "frame_x": "minecraft:stripped_oak_log[axis=x]",
    "frame_z": "minecraft:stripped_oak_log[axis=z]",
    "frame_post": "minecraft:stripped_oak_log[axis=y]",
    "boards": "minecraft:spruce_planks",
    "brick": "minecraft:bricks",
})


def _mossy(x, y, z, every: int) -> bool:
    """A fixed scatter (no randomness, so the pieces stay byte-identical): roughly one block in ``every``."""
    return (x * 73856093 ^ y * 19349663 ^ z * 83492791) % every == 0


def corner_post(piece, x, z, y0, y1, key="post"):
    """A stripped spruce post from y0 to y1 at (x, z)."""
    piece.fill(x, y0, z, x, y1, z, key)


def plinth(piece, x0, z0, x1, z1, y=1, footing=True, keep=("post", "frame_post")):
    """The stone base of a wall ring: the course at ``y`` is cobblestone with a little moss, the foundation row below it
    (``footing``) mostly mossy, so the stone darkens towards the ground. Posts already standing are kept."""
    for yy, every in ((y - 1, 2), (y, 5)) if footing else ((y, 5),):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x not in (x0, x1) and z not in (z0, z1):
                    continue
                if piece.get(x, yy, z) in keep or (x, yy, z) in piece.jigsaws:
                    continue
                piece.put(x, yy, z, "plinth_mossy" if _mossy(x, yy, z, every) else "plinth")


def trim_band(piece, x0, z0, x1, z1, y, only=("render",), wood="spruce"):
    """A horizontal timber band around the wall ring at row ``y``: stripped logs along each wall, laid only over the
    wall infill (``only``), so posts, windows and doors stay."""
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if piece.get(x, y, z) in only:
                piece.put(x, y, z, f"minecraft:stripped_{wood}_log[axis=x]")
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            if piece.get(x, y, z) in only:
                piece.put(x, y, z, f"minecraft:stripped_{wood}_log[axis=z]")


def window(piece, x, y, z, out, height=1, shutters=True, sill=True, lintel=True):
    """A framed window in the wall at (x, y, z): ``height`` panes, a stripped oak lintel above, spruce trapdoor shutters
    folded open against the wall either side and a spruce slab sill under it, both outside the wall. Where the outside
    is beyond the piece's box, the sill becomes an upside-down stair set into the wall (cobblestone in a plinth, spruce
    in render; its open bottom quarter outside, a shadow under the sill) and the shutters are left out."""
    dx, dz = OFFSET[out]
    along = ALONG[out]
    for dy in range(height):
        piece.put(x, y + dy, z, "pane")
    if lintel:
        piece.put(x, y + height, z, "frame_x" if along == "x" else "frame_z")
    ox, oz = x + dx, z + dz
    outside = piece.inside(ox, y, oz)
    if sill:
        if outside and piece.get(ox, y - 1, oz) is None:
            piece.put(ox, y - 1, oz, "minecraft:spruce_slab[type=top]")
        elif piece.get(x, y - 1, z) is not None:
            below = piece.get(x, y - 1, z)
            stone = {"plinth": "cobblestone", "plinth_mossy": "cobblestone", "cobble": "cobblestone",
                     "bricks": "stone_brick"}.get(below, "spruce")
            piece.put(x, y - 1, z, f"minecraft:{stone}_stairs[facing={OPPOSITE[out]},half=top]")
    if shutters and outside:
        sides = ((-1, 0), (1, 0)) if along == "x" else ((0, -1), (0, 1))
        for sx, sz in sides:
            for dy in range(height):
                px, py, pz = ox + sx, y + dy, oz + sz
                if piece.inside(px, py, pz) and piece.get(px, py, pz) is None and (px, py, pz) not in piece.jigsaws:
                    piece.put(px, py, pz, shutter(out))


def shutter(out, wood="spruce"):
    """An open trapdoor folded flat against the wall behind it (the wall lies opposite ``out``)."""
    return f"minecraft:{wood}_trapdoor[facing={out},half=bottom,open=true]"


def door_frame(piece, x, y, z, out, wood="spruce", hinge="left", hood=True, posts=True):
    """A door in the wall at (x, y, z) with a stripped oak frame: posts either side (over the wall infill only), a
    lintel above, and a hood outside (an upside-down spruce stair) where the box leaves room."""
    door(piece, x, y, z, facing=OPPOSITE[out], wood=wood, hinge=hinge)
    along = ALONG[out]
    sides = ((-1, 0), (1, 0)) if along == "x" else ((0, -1), (0, 1))
    for sx, sz in sides if posts else ():
        for dy in (0, 1):
            if piece.get(x + sx, y + dy, z + sz) in ("render", "plinth", "plinth_mossy", "boards"):
                piece.put(x + sx, y + dy, z + sz, "frame_post")
    piece.put(x, y + 2, z, "frame_x" if along == "x" else "frame_z")
    dx, dz = OFFSET[out]
    if hood and piece.inside(x + dx, y + 2, z + dz) and piece.get(x + dx, y + 2, z + dz) is None:
        piece.put(x + dx, y + 2, z + dz, stairs_state("spruce", OPPOSITE[out], "top"))


def stairs_state(wood, facing, half="bottom"):
    return f"minecraft:{wood}_stairs[facing={facing},half={half}]"


def rafter_ends(piece, cells, out, wood="spruce"):
    """Exposed rafter ends under an eave: upside-down stairs, their bottom step against the wall (``out`` points away
    from it), at each (x, y, z) of ``cells`` that is free."""
    for x, y, z in cells:
        if piece.inside(x, y, z) and piece.get(x, y, z) is None and (x, y, z) not in piece.jigsaws:
            piece.put(x, y, z, stairs_state(wood, OPPOSITE[out], "top"))


def chimney(piece, x, z, y0, y_top, cap=True):
    """A brick chimney from y0 to y_top at (x, z) (it replaces what is there, roof included), with a brick wall
    pot on top (no campfire, so no smoke)."""
    piece.fill(x, y0, z, x, y_top, z, "brick")
    if cap and piece.inside(x, y_top + 1, z):
        piece.put(x, y_top + 1, z, "minecraft:brick_wall[up=true,north=none,south=none,east=none,west=none]")


def hearth(piece, x, y, z, facing):
    """A furnace hearth at the foot of a chimney, its front toward ``facing`` (into the room)."""
    piece.put(x, y, z, f"minecraft:furnace[facing={facing},lit=false]")


TILE = "minecraft:brick_stairs"        # red tile roofs (houses, tavern)
DARK = "minecraft:dark_oak_stairs"     # dark shingle roofs (harbor hut, shipwright's shed)


def lantern_post(piece, x, y, z, height=2):
    """A spruce fence post ``height`` high standing on (x, y - 1, z) with a lantern on top."""
    for dy in range(height):
        piece.put(x, y + dy, z, "fence")
    piece.put(x, y + height, z, "lantern")


def door(piece, x, y, z, facing="north", wood="spruce", hinge="left"):
    piece.put(x, y, z, f"minecraft:{wood}_door[facing={facing},half=lower,hinge={hinge},open=false]")
    piece.put(x, y + 1, z, f"minecraft:{wood}_door[facing={facing},half=upper,hinge={hinge},open=false]")


def table(piece, x, y, z):
    """A one-block table: a fence leg under a pressure plate top."""
    piece.put(x, y, z, "fence")
    piece.put(x, y + 1, z, "minecraft:spruce_pressure_plate")


def bed(piece, x, y, z, facing, colour="red"):
    """A bed whose foot is at (x, y, z) and whose head lies one block toward ``facing``."""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    piece.put(x, y, z, f"minecraft:{colour}_bed[facing={facing},part=foot]")
    piece.put(x + dx, y, z + dz, f"minecraft:{colour}_bed[facing={facing},part=head]")
