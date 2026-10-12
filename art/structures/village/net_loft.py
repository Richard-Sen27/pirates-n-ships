"""Generates the seafarer village's net loft (a loft room on posts; STR3) and prints the BuildSpec as JSON. The
building connector is on the north (-z) side; row z 0 holds the ladder's foot.

A boarded loft room on four spruce log posts with knee braces, under a dark shingle roof; below it, between the
posts, fishing nets hang to dry from the floor beams (cobwebs), with barrels, a lobster pot and coils of rope on the
gravel. A ladder against a front post climbs to a slab landing before the loft door; inside the loft, net in heaps
(cobwebs), a chest, barrels, a lantern."""
from _style import DARK, PALETTE, corner_post, jit, lantern_post, rafter_ends, stairs_state, window
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 6, 1, 6
FLOOR, TOP = 4, 7
p = Piece("village_net_loft", "Net Loft", (X1 + 1, 13, Z1 + 2), PALETTE)

# the ground under the loft: gravel and coarse dirt, footings under the posts
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        r = jit(x, 0, z, 461)
        p.put(x, 0, z, "gravel" if r < 450 else "coarse" if r < 800 else "path")
posts = [(X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (3, Z0)]
for x, z in posts:
    p.put(x, 0, z, "plinth_mossy" if (x + z) % 2 else "plinth")
    p.fill(x, 1, z, x, FLOOR - 1, z, "spruce_log")
# knee braces under the floor beams
for x, z, facing in ((X0 + 1, Z0, "west"), (X1 - 1, Z0, "east"), (X0 + 1, Z1, "west"), (X1 - 1, Z1, "east"),
                     (X0, Z0 + 1, "north"), (X1, Z0 + 1, "north"), (X0, Z1 - 1, "south"), (X1, Z1 - 1, "south")):
    p.put(x, FLOOR - 1, z, stairs_state("spruce", facing, "top"))
# the loft floor on beams, the room of boards between posts, a band, the roof
p.fill(X0, FLOOR, Z0, X1, FLOOR, Z1, "spruce")
p.fill(X0, FLOOR, Z0, X1, FLOOR, Z0, "beam_x")
p.fill(X0, FLOOR, Z1, X1, FLOOR, Z1, "beam_x")
p.ring(X0, Z0, X1, Z1, FLOOR + 1, TOP, "boards")
for x, z in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
    corner_post(p, x, z, FLOOR + 1, TOP)
p.roof_ridge_x(X0, X1, Z0, Z1, TOP + 1, DARK, "dark_ridge", "boards", plate="beam_x")
p.put(X0, TOP + 3, 3, "pane")
p.put(X1, TOP + 3, 3, "pane")
rafter_ends(p, [(x, TOP, Z1 + 1) for x in range(X0, X1 + 1, 2)], "south")
# the loft door, the landing and the ladder
p.put(3, FLOOR + 1, Z0, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
p.put(3, FLOOR + 2, Z0, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
p.fill(2, FLOOR, 0, 4, FLOOR, 0, "minecraft:spruce_slab[type=top]")
p.fill(2, FLOOR + 1, 0, 2, FLOOR + 1, 0, "fence")
p.fill(4, FLOOR + 1, 0, 4, FLOOR + 1, 0, "fence")
p.fill(3, 1, 0, 3, FLOOR - 1, 0, "minecraft:ladder[facing=north,waterlogged=false]")
p.put(4, FLOOR + 2, 0, "lantern")
for x in (1, 5):
    window(p, x, FLOOR + 2, Z0, "north", shutters=False)
window(p, X0, FLOOR + 2, 4, "west", lintel=False)
window(p, X1, FLOOR + 2, 4, "east", lintel=False)
# nets drying under the loft, gear on the ground
for x, z in ((1, 2), (2, 2), (4, 4), (5, 4), (1, 5), (2, 5)):
    p.put(x, FLOOR - 1, z, "net")
    if jit(x, 2, z, 462) < 500:
        p.put(x, FLOOR - 2, z, "net")
p.put(5, 1, 2, "minecraft:barrel[facing=up,open=false]")
p.put(5, 1, 5, "cargo_barrel")
p.put(4, 1, 5, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]")
p.put(1, 1, 3, "pirates_n_ships:rope_coil[facing=north,layers=3]")
p.put(2, 1, 6, "pirates_n_ships:rope_coil[facing=north,layers=2]")
# inside the loft
for x, z in ((1, 2), (1, 3), (5, 5)):
    p.put(x, FLOOR + 1, z, "net")
p.loot_chest(5, FLOOR + 1, 2, "west", "village/shipwright")
p.put(1, FLOOR + 1, 5, "minecraft:barrel[facing=up,open=false]")
p.put(3, TOP, 4, "lantern_hanging")
lantern_post(p, 6, 1, 0)

p.connector(3, 0, 0, "building_in", "north", "minecraft:gravel")
p.emit("a net loft: a boarded loft room on posts with nets drying beneath, a ladder up to the door",
       ["Faces north: the building_in connector is on the -z side at the foot of the ladder."])
