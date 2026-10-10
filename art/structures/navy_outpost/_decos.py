"""STR4: the navy outpost's decorations (pool ``navy_outpost/decorations``): small props hung on the ``deco_out``
connectors of the court's lanes, filling the gaps between the buildings. Each piece is a ``deco_<name>.py`` file that
calls ``build("<name>")``; all have ``deco_in`` in the middle of the north edge on the ground row (y 0), stay small and
leave the cells they do not need empty (the terrain shows)."""
from _style import PALETTE, banner, connector, dry, fence_run, h01, lantern_post, stair_shape, table
from buildspec import Piece


def _piece(name, title, size):
    return Piece(f"navy_outpost_deco_{name}", title, size, PALETTE)


def pad(p, x0, z0, x1, z1, salt=0):
    """A worn patch of gravel and cobbles under a prop."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            r = h01(x, 0, z, salt)
            p.put(x, 0, z, "gravel" if r < 0.5 else "cobble" if r < 0.85 else "andesite")


def cannon_balls():
    p = _piece("cannon_balls", "Shot and a Spare Gun", (5, 4, 5))
    pad(p, 0, 1, 4, 4, salt=301)
    p.fill(0, 0, 3, 1, 0, 4, "dark_oak")
    for x, z in ((0, 3), (1, 3), (0, 4), (1, 4)):
        p.put(x, 1, z, "minecraft:heavy_core[waterlogged=false]")
    p.put(0, 2, 3, "minecraft:heavy_core[waterlogged=false]")
    p.put(4, 1, 4, "minecraft:heavy_core[waterlogged=false]")
    p.put(3, 1, 1, "pirates_n_ships:cannon[facing=north,load=empty,part=front]")
    p.put(3, 1, 2, "pirates_n_ships:cannon[facing=north,load=empty,part=rear]")
    p.put(4, 1, 3, "pirates_n_ships:cargo_barrel")
    p.put(1, 1, 1, "minecraft:barrel[facing=up,open=false]")
    return p, "a spare gun on the gravel, pyramids of shot on a dark oak pallet, a powder keg"


def crates():
    p = _piece("crates", "Navy Stores", (5, 4, 5))
    pad(p, 1, 1, 3, 4, salt=311)
    p.put(1, 1, 2, "pirates_n_ships:cargo_crate")
    p.put(1, 2, 2, "pirates_n_ships:cargo_crate")
    p.put(2, 1, 2, "pirates_n_ships:cargo_barrel")
    p.put(3, 1, 3, "pirates_n_ships:cargo_crate")
    p.put(3, 1, 4, "minecraft:barrel[facing=north,open=false]")
    p.put(1, 1, 4, "minecraft:barrel[facing=up,open=false]")
    p.put(1, 2, 4, "lantern")
    p.put(2, 1, 4, "minecraft:hay_block[axis=x]")
    return p, "stacked cargo crates and barrels, a bale and a lantern"


def drill_target():
    p = _piece("drill_target", "Drill Target", (5, 4, 4))
    pad(p, 0, 1, 4, 3, salt=321)
    p.put(2, 1, 3, "minecraft:hay_block[axis=y]")
    p.put(2, 2, 3, "minecraft:target[power=0]")
    for x in (0, 4):                       # straw men: a hay bale on a fence post under a carved pumpkin
        p.put(x, 1, 2, "fence")
        p.put(x, 2, 2, "minecraft:hay_block[axis=y]")
        p.put(x, 3, 2, "minecraft:carved_pumpkin[facing=north]")
    p.put(1, 1, 1, "minecraft:hay_block[axis=x]")
    return p, "a target on a hay bale between two straw men for musket and cutlass drill"


def lantern_post_():
    p = _piece("lantern_post", "Lantern Post", (3, 5, 3))
    p.put(1, 0, 1, "andesite")
    p.put(1, 1, 1, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    p.fill(1, 2, 1, 1, 3, 1, "bars")
    p.put(1, 4, 1, "lantern")
    p.put(0, 1, 2, stair_shape("spruce", "west"))
    p.put(0, 0, 2, "cobble")
    return p, "a tall lantern post on a stone base with a bench beside it"


def bell_post():
    p = _piece("bell_post", "Alarm Bell", (5, 6, 3))
    pad(p, 1, 1, 3, 2, salt=331)
    for x in (1, 3):
        p.fill(x, 1, 1, x, 3, 1, "dark_post")
    p.fill(1, 4, 1, 3, 4, 1, "beam_x")
    p.put(2, 3, 1, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
    p.put(1, 5, 1, stair_shape("spruce", "east"))
    p.put(3, 5, 1, stair_shape("spruce", "west"))
    p.put(2, 5, 1, "spruce_slab")
    return p, "the alarm bell under a little roof on two dark oak posts"


def notice_board():
    p = _piece("notice_board", "Orders Board", (5, 4, 3))
    pad(p, 0, 1, 4, 2, salt=341)
    for x in (0, 4):
        p.fill(x, 1, 1, x, 2, 1, "post")
    p.fill(1, 1, 1, 3, 2, 1, "spruce")
    for x in (1, 2, 3):
        p.put(x, 3, 1, stair_shape("dark_oak", "north"))
    p.put(2, 2, 1, "minecraft:dark_oak_planks")       # the orders, a darker board in the middle
    p.put(0, 3, 1, "lantern")
    p.put(4, 3, 1, "lantern")
    banner(p, 2, 1, 2, "south")
    return p, "an orders board of spruce boards between two posts under a little roof, lanterns on the posts"


def water_trough():
    p = _piece("water_trough", "Water Trough", (5, 3, 3))
    pad(p, 0, 1, 4, 2, salt=351)
    for x in (1, 2, 3):
        p.put(x, 1, 1, "minecraft:water_cauldron[level=3]")
    p.put(0, 1, 1, "bricks")
    p.put(4, 1, 1, "minecraft:hay_block[axis=y]")
    p.put(0, 2, 1, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]")
    return p, "a stone water trough (cauldrons) with a hay bale"


def cart():
    p = _piece("cart", "Supply Cart", (5, 4, 5))
    pad(p, 0, 1, 4, 4, salt=361)
    p.fill(1, 1, 2, 3, 1, 3, "spruce_slab_top")
    for x, facing in ((0, "west"), (4, "east")):
        for z in (2, 3):
            p.put(x, 1, z, f"minecraft:dark_oak_trapdoor[facing={facing},half=bottom,open=true,powered=false,waterlogged=false]")
    p.put(1, 2, 2, "pirates_n_ships:cargo_crate")
    p.put(2, 2, 3, "minecraft:barrel[facing=north,open=false]")
    p.put(3, 2, 2, "minecraft:hay_block[axis=x]")
    fence_run(p, [(2, 1)], 1)
    p.put(2, 1, 4, "fence")
    return p, "a two-wheeled supply cart with crates, a barrel and hay"


def tree():
    p = _piece("tree", "Court Tree", (5, 8, 5))
    leaf = "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]"
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            p.put(x, 0, z, "minecraft:coarse_dirt" if (x + z) % 2 else "minecraft:grass_block[snowy=false]")
    for x, z in ((1, 1), (3, 1), (1, 3), (3, 3), (2, 1), (1, 2), (3, 2), (2, 3)):
        p.put(x, 1, z, "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    p.fill(2, 1, 2, 2, 4, 2, "minecraft:oak_log[axis=y]")
    for y in (4, 5, 6):
        r = 2 if y < 6 else 1
        for x in range(2 - r, 3 + r):
            for z in range(2 - r, 3 + r):
                if (x, y, z) != (2, 4, 2) and abs(x - 2) + abs(z - 2) <= r + (1 if y == 5 else 0) and p.inside(x, y, z):
                    if h01(x, y, z, 371) < 0.88:
                        p.put(x, y, z, leaf)
    p.put(2, 7, 2, leaf)
    return p, "an oak in a low stone ring"


def benches():
    p = _piece("benches", "Benches", (5, 3, 4))
    pad(p, 0, 1, 4, 3, salt=381)
    p.put(0, 1, 2, stair_shape("spruce", "west"))
    p.put(1, 1, 2, stair_shape("spruce", "west"))
    p.put(3, 1, 2, stair_shape("spruce", "east"))
    p.put(4, 1, 2, stair_shape("spruce", "east"))
    table(p, 2, 1, 2)
    p.put(2, 1, 3, "minecraft:barrel[facing=up,open=false]")
    p.put(2, 2, 3, "lantern")
    return p, "two benches either side of a small table, a barrel with a lantern"


BUILDERS = {"cannon_balls": cannon_balls, "crates": crates, "drill_target": drill_target,
            "lantern_post": lantern_post_, "bell_post": bell_post, "notice_board": notice_board,
            "water_trough": water_trough, "cart": cart, "tree": tree, "benches": benches}


def build(name):
    p, description = BUILDERS[name]()
    connector(p, p.size[0] // 2, 0, 0, "deco_in", "north", "minecraft:gravel")
    dry(p)
    p.emit(description, [f"deco_in [{p.size[0] // 2}, 0, 0] (north edge, y 0)."])
