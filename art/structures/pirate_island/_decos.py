"""The pirate island's decoration pieces (STR2, pool ``pirate_island/decorations``): small props hung on the
``deco_out`` connectors of the trail pieces and the clearing, filling the gaps between the buildings. Each piece is a
``deco_<name>.py`` file that calls ``build("<name>")``; all of them have ``deco_in`` in the middle of the north edge
on the ground row (y 0), leave the cells they do not need empty (the terrain shows) and stay small (at most 7 wide)."""
from _camp import crate_stack, palm, patch, rocks, rope_coil, skull_post
from _style import PALETTE, campfire_ring, flagpole, trapdoor
from buildspec import Piece


def _piece(name, title, size):
    return Piece(f"pirate_island_deco_{name}", title, size, PALETTE)


def campfire():
    p = _piece("campfire", "Camp Fire Ring", (5, 4, 6))
    campfire_ring(p, 2, 0, 3, seats=("west", "east", "south"), spit=False)
    p.put(2, 1, 3, "minecraft:campfire[facing=north,lit=true,signal_fire=false]")
    p.put(4, 1, 1, "minecraft:barrel[facing=up,open=false]")
    p.put(0, 1, 1, "dead_bush")
    p.fill(1, 0, 1, 3, 0, 1, "coarse")
    p.put(2, 0, 0, "coarse")
    return p, "a fire ring of cobblestone with a lit campfire and driftwood seats on three sides"


def crates():
    p = _piece("crates", "Crates and Barrels", (5, 4, 5))
    patch(p, 1, 1, 3, 3, reach=1, salt=501)
    p.fill(1, 0, 1, 3, 0, 3, "coarse")
    crate_stack(p, 1, 1, 2, salt=502)
    crate_stack(p, 2, 1, 3, salt=503)
    p.put(3, 1, 2, "pirates_n_ships:cargo_crate")
    p.put(3, 2, 2, "pirates_n_ships:cargo_crate")
    p.put(3, 3, 2, "minecraft:barrel[facing=north,open=false]")
    p.put(1, 1, 3, "minecraft:barrel[facing=east,open=false]")
    rope_coil(p, 3, 1, 1, layers=2)
    p.put(1, 1, 1, "lantern")
    return p, "a heap of cargo crates, barrels, a rope coil and a lantern"


def hammock():
    p = _piece("hammock", "Palms with a Hammock", (7, 8, 4))
    for x in (1, 5):
        p.put(x, 0, 2, "sand")
    palm(p, 1, 1, 2, height=5, lean="west", fronds=True)
    palm(p, 5, 1, 2, height=5, lean="east", fronds=True)
    # the hammock slung between the trunks, ropes (chains) to them
    p.put(2, 1, 2, "pirates_n_ships:hammock[facing=east,part=foot]")
    p.put(3, 1, 2, "pirates_n_ships:hammock[facing=east,part=head]")
    p.put(4, 1, 2, "chain_x")
    p.put(3, 0, 1, "coarse")
    p.put(4, 1, 3, "minecraft:barrel[facing=up,open=false]")
    p.put(4, 2, 3, "lantern")
    patch(p, 1, 1, 5, 3, reach=1, salt=511, inner=("sand", "coarse"))
    return p, "a hammock slung between two leaning palms, a barrel with a lantern"


def rocks_():
    p = _piece("rocks", "Beach Rocks", (5, 3, 4))
    rocks(p, [(1, 1), (2, 1), (2, 2), (3, 2), (1, 2), (3, 3)], salt=521)
    p.put(2, 1, 2, "mossy")
    p.put(2, 2, 2, "minecraft:cobblestone_slab[type=bottom]")
    p.put(4, 0, 2, "gravel")
    p.put(4, 1, 2, "dead_bush")
    p.put(0, 0, 3, "bones")
    return p, "a cluster of mossy beach rocks, a dead bush and a bleached bone"


def fish_rack():
    p = _piece("fish_rack", "Fish Drying Rack", (5, 4, 4))
    for x in (0, 4):
        p.fill(x, 1, 2, x, 2, 2, "drift")
    p.fill(1, 2, 2, 3, 2, 2, "chain_x")
    for x in (1, 2, 3):
        p.put(x, 1, 2, trapdoor("north" if x % 2 else "south", "bottom", True))
    p.put(2, 3, 2, "minecraft:dried_kelp_block")
    p.put(1, 1, 1, "minecraft:barrel[facing=up,open=false]")
    p.put(3, 1, 3, "minecraft:stripped_spruce_log[axis=y]")
    p.fill(0, 0, 1, 4, 0, 3, "coarse")
    p.put(2, 0, 0, "coarse")
    return p, "a drying rack of driftwood poles with fish strung on a line, kelp and a salt barrel"


def washing_line():
    p = _piece("washing_line", "Washing Line", (7, 4, 3))
    for x in (0, 6):
        p.fill(x, 1, 1, x, 2, 1, "drift")
    p.fill(1, 2, 1, 5, 2, 1, "chain_x")
    for x, wood in ((1, "birch"), (2, "birch"), (4, "spruce"), (5, "birch")):
        p.put(x, 1, 1, trapdoor("north" if x % 2 else "south", "bottom", True, wood))
    p.put(3, 1, 1, "minecraft:white_carpet")
    p.put(3, 0, 1, "coarse")
    p.put(6, 1, 2, "minecraft:barrel[facing=up,open=false]")
    p.put(0, 1, 0, "minecraft:cauldron")
    return p, "a washing line of rope (chain) between two driftwood poles with shirts and sailcloth hung on it"


def rum_cask():
    p = _piece("rum_cask", "Rum Cask", (3, 4, 4))
    p.put(0, 1, 2, "fence")
    p.put(2, 1, 2, "fence")
    p.put(0, 2, 2, "minecraft:barrel[facing=east,open=false]")
    p.put(1, 2, 2, "minecraft:barrel[facing=east,open=false]")
    p.put(2, 2, 2, "minecraft:spruce_slab[type=bottom]")
    p.put(1, 1, 1, stair_stool())
    p.put(2, 1, 3, "pirates_n_ships:cargo_barrel")
    p.put(0, 3, 2, "lantern")
    p.fill(0, 0, 1, 2, 0, 3, "coarse")
    return p, "a rum cask on its side on a trestle, a stool and a keg"


def stair_stool():
    return "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]"


def skull_post_():
    p = _piece("skull_post", "Skull on a Post", (3, 5, 3))
    skull_post(p, 1, 1, 1, rotation=8, height=2)
    p.put(0, 0, 1, "bones")
    p.put(2, 0, 2, "gravel")
    p.put(2, 1, 0, "cobweb")
    p.put(0, 1, 2, "dead_bush")
    p.put(1, 0, 1, "mossy")
    return p, "a skull on a palm stake as a warning, bones and cobwebs at its foot"


def jolly_roger():
    p = _piece("jolly_roger", "Jolly Roger Flagpole", (3, 8, 3))
    p.put(1, 0, 1, "mossy")
    for x, z in ((0, 1), (2, 1), (1, 2)):
        p.put(x, 0, z, "cobble")
    flagpole(p, 1, 1, 1, 6, "east")
    p.put(0, 1, 2, "minecraft:cobblestone_slab[type=bottom]")
    return p, "a flagpole flying the Jolly Roger on a footing of mossy stones"


def palm_():
    p = _piece("palm", "Palm", (5, 9, 5))
    p.put(2, 0, 2, "sand")
    palm(p, 2, 1, 2, height=6, lean="south")
    rocks(p, [(1, 3), (3, 1)], salt=541)
    p.put(3, 1, 3, "minecraft:jungle_leaves[distance=1,persistent=true,waterlogged=false]")
    return p, "a lone palm leaning over a couple of rocks"


BUILDERS = {
    "campfire": campfire, "crates": crates, "hammock": hammock, "rocks": rocks_, "fish_rack": fish_rack,
    "washing_line": washing_line, "rum_cask": rum_cask, "skull_post": skull_post_, "jolly_roger": jolly_roger,
    "palm": palm_,
}


def build(name):
    p, description = BUILDERS[name]()
    sx, _, _ = p.size
    p.connector(sx // 2, 0, 0, "deco_in", "north", "minecraft:coarse_dirt")
    p.emit(description, [f"Decoration: deco_in at [{sx // 2}, 0, 0] (north), nothing else."])
