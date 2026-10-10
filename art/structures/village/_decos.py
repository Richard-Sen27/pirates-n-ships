"""The seafarer village's decoration pieces (STR3, pool ``village/decorations``): small props hung on the
``deco_out`` connectors of the street pieces and the squares, filling the gaps between the buildings. Each piece is a
``deco_<name>.py`` file that calls ``build("<name>")``; all of them have ``deco_in`` in the middle of the north edge on
the ground row (y 0), leave the cells they do not need empty (the terrain shows) and stay small (at most 7 wide)."""
from _style import PALETTE, bench, flower, ground_patch, jit, lantern_post, stairs_state, tree
from buildspec import Piece


def _piece(name, title, size):
    return Piece(f"village_deco_{name}", title, size, PALETTE)


def trapdoor(facing, half="bottom", open_=True, wood="spruce"):
    return (f"minecraft:{wood}_trapdoor[facing={facing},half={half},open={'true' if open_ else 'false'},"
            "powered=false,waterlogged=false]")


def cart():
    p = _piece("cart", "Hand Cart", (5, 4, 5))
    ground_patch(p, 1, 1, 3, 3, reach=1, salt=601)
    # the bed on two wheels (trapdoors on their sides), the shafts resting on the ground, a load of crates and hay
    p.fill(1, 1, 1, 3, 1, 3, "minecraft:spruce_slab[type=top]")
    p.put(0, 1, 2, trapdoor("west"))
    p.put(4, 1, 2, trapdoor("east"))
    p.put(1, 1, 0, "fence")
    p.put(3, 1, 0, "fence")
    p.put(1, 2, 2, "crate")
    p.put(2, 2, 2, "minecraft:hay_block[axis=x]")
    p.put(3, 2, 3, "minecraft:barrel[facing=up,open=false]")
    p.put(2, 2, 3, "minecraft:pumpkin")
    return p, "a hand cart on two wheels loaded with a crate, hay, a barrel and a pumpkin"


def barrels():
    p = _piece("barrels", "Barrels and Crates", (5, 4, 4))
    ground_patch(p, 1, 1, 3, 2, reach=1, salt=611)
    p.fill(1, 0, 1, 3, 0, 2, "coarse")
    p.put(1, 1, 1, "cargo_barrel")
    p.put(1, 2, 1, "cargo_barrel")
    p.put(2, 1, 2, "crate")
    p.put(3, 1, 2, "crate")
    p.put(3, 2, 2, "crate")
    p.put(3, 1, 1, "minecraft:barrel[facing=east,open=false]")
    p.put(2, 1, 1, "pirates_n_ships:rope_coil[facing=north,layers=2]")
    p.put(1, 1, 2, "lantern")
    return p, "cargo barrels and crates stacked by the street, a rope coil and a lantern"


def nets():
    p = _piece("nets", "Drying Nets", (7, 5, 3))
    for x in (0, 6):
        p.fill(x, 1, 1, x, 3, 1, "fence")
        p.put(x, 0, 1, "coarse")
    p.fill(1, 3, 1, 5, 3, 1, "chain_x")
    for x in (1, 2, 4, 5):
        p.put(x, 2, 1, "net")
    p.put(3, 2, 1, "net")
    p.put(3, 1, 2, "minecraft:barrel[facing=up,open=false]")
    p.put(1, 1, 2, "pirates_n_ships:rope_coil[facing=north,layers=1]")
    return p, "fishing nets hung to dry on a rope between two posts, a barrel of floats"


def rowboat():
    p = _piece("rowboat", "Rowing Boat on Trestles", (5, 5, 8))
    ground_patch(p, 1, 1, 3, 6, reach=1, salt=621, keys=("gravel", "coarse", "path"))
    for z in (2, 5):
        p.put(1, 1, z, "fence")
        p.put(3, 1, z, "fence")
        p.fill(1, 2, z, 3, 2, z, "beam_x")
    # the hull right way up on the trestles: planks bottom, slab sides, pointed bow (north) and stern
    for z in range(2, 6):
        p.put(2, 3, z, "spruce")
        p.put(1, 3, z, "minecraft:spruce_slab[type=bottom]")
        p.put(3, 3, z, "minecraft:spruce_slab[type=bottom]")
    p.put(2, 3, 1, stairs_state("spruce", "south"))
    p.put(2, 3, 6, stairs_state("spruce", "north"))
    p.put(2, 4, 3, "minecraft:spruce_slab[type=bottom]")          # a thwart
    p.put(1, 1, 7, "minecraft:cauldron")                           # the tar pot
    p.put(3, 1, 7, trapdoor("south"))                              # an oar leaning on the ground
    return p, "a rowing boat up on two trestles for caulking, a tar pot and an oar"


def flowers():
    p = _piece("flowers", "Flower Bed", (5, 3, 4))
    for x in range(5):
        for z in (1, 3):
            p.put(x, 0, z, "bricks" if jit(x, 0, z, 631) < 800 else "bricks_mossy")
    for x in (0, 4):
        p.put(x, 0, 2, "bricks")
    for x in range(1, 4):
        p.put(x, 0, 2, "grass")
        flower(p, x, 1, 2, salt=632)
    p.put(0, 1, 1, "minecraft:potted_red_tulip")
    p.put(4, 1, 3, "minecraft:potted_azure_bluet")
    return p, "a raised flower bed in a stone brick curb with potted flowers at the corners"


def bench_tree():
    p = _piece("bench_tree", "Bench under a Tree", (5, 8, 5))
    p.put(2, 0, 3, "grass")
    tree(p, 2, 1, 3, height=4, crown=2, salt=641)
    bench(p, 1, 1, 1, "north")
    bench(p, 2, 1, 1, "north")
    bench(p, 3, 1, 1, "north")
    for x in (1, 2, 3):
        p.put(x, 0, 1, "path" if x != 2 else "coarse")
    p.put(0, 1, 1, "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]")
    p.put(4, 1, 1, "minecraft:spruce_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]")
    return p, "a bench looking over the street under a young oak"


def lantern():
    p = _piece("lantern", "Lantern Post", (3, 5, 3))
    p.put(1, 0, 1, "bricks")
    p.put(1, 1, 1, "minecraft:stone_brick_wall")
    p.fill(1, 2, 1, 1, 3, 1, "fence")
    p.put(1, 4, 1, "lantern")
    p.put(0, 1, 1, "minecraft:potted_dandelion")
    p.put(2, 0, 2, "coarse")
    return p, "a lantern post on a stone brick footing with a pot of flowers"


def fish_rack():
    p = _piece("fish_rack", "Fish Drying Rack", (5, 4, 4))
    for x in (0, 4):
        p.fill(x, 1, 2, x, 2, 2, "fence")
    p.fill(1, 2, 2, 3, 2, 2, "chain_x")
    for x in (1, 2, 3):
        p.put(x, 1, 2, trapdoor("north" if x % 2 else "south"))
    p.put(2, 3, 2, "minecraft:dried_kelp_block")
    p.put(1, 1, 1, "minecraft:barrel[facing=up,open=false]")
    p.put(3, 1, 3, "minecraft:water_cauldron[level=2]")
    p.fill(0, 0, 1, 4, 0, 3, "gravel")
    p.put(2, 0, 0, "coarse")
    return p, "a drying rack of posts with fish strung on a line, kelp, a salt barrel and a brine tub"


def woodpile():
    p = _piece("woodpile", "Woodpile", (5, 4, 4))
    p.fill(0, 1, 2, 4, 1, 2, "minecraft:spruce_log[axis=x]")
    p.fill(1, 2, 2, 3, 2, 2, "minecraft:spruce_log[axis=x]")
    p.put(2, 3, 2, "minecraft:oak_log[axis=x]")
    for x in (0, 4):
        p.put(x, 2, 2, "fence")
    p.put(1, 1, 1, "minecraft:oak_log[axis=y]")                    # the chopping block
    p.put(3, 1, 1, "minecraft:spruce_slab[type=bottom]")
    p.fill(0, 0, 1, 4, 0, 3, "coarse")
    p.put(0, 0, 3, "path")
    p.put(4, 0, 1, "path")
    return p, "a stack of split logs between two stakes, a chopping block"


def laundry():
    p = _piece("laundry", "Laundry Line", (7, 4, 3))
    for x in (0, 6):
        p.fill(x, 1, 1, x, 2, 1, "fence")
    p.fill(1, 2, 1, 5, 2, 1, "chain_x")
    for x, wood in ((1, "birch"), (2, "birch"), (4, "birch"), (5, "oak")):
        p.put(x, 1, 1, trapdoor("north" if x % 2 else "south", wood=wood))
    p.put(3, 1, 1, "minecraft:white_carpet")
    p.put(6, 1, 2, "minecraft:barrel[facing=up,open=false]")
    p.put(0, 1, 0, "minecraft:water_cauldron[level=1]")
    p.put(3, 0, 1, "coarse")
    return p, "a laundry line between two posts with sheets and shirts hung on it, a wash tub"


BUILDERS = {
    "cart": cart, "barrels": barrels, "nets": nets, "rowboat": rowboat, "flowers": flowers, "bench_tree": bench_tree,
    "lantern": lantern, "fish_rack": fish_rack, "woodpile": woodpile, "laundry": laundry,
}


def build(name):
    p, description = BUILDERS[name]()
    sx, _, _ = p.size
    p.connector(sx // 2, 0, 0, "deco_in", "north", "minecraft:coarse_dirt")
    p.emit(description, [f"Decoration: deco_in at [{sx // 2}, 0, 0] (north), nothing else."])
