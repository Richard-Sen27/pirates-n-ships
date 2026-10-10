"""Generates the seafarer village's chapel (9x10 footprint, gable-end to the street; STR3) and prints the BuildSpec
as JSON. The door and the building connector are on the north (-z) side; row z 0 is the doorstep.

A small whitewashed chapel: render walls on a mossy cobblestone plinth between stone brick quoins and buttresses
(stairs), tall framed windows down both sides, a framed door under a hood with a round light over it, a steep red tile
roof with the ridge along z and rafter ends under the eaves, an open belfry on the front gable (four posts, a bell,
a little brick pyramid roof with a lightning rod). Inside: pews either side of a carpeted aisle, an altar of chiseled
stone bricks with candles and a lectern, lanterns on a tie beam, a chest for the alms."""
from _style import PALETTE, TILE, door_frame, jit, lantern_post, rafter_ends, stairs_state, window
from buildspec import Piece

X0, X1, Z0, Z1 = 0, 8, 1, 10
TOP = 5
p = Piece("village_chapel", "Seafarer Chapel", (X1 + 1, 17, Z1 + 1), PALETTE)

# floor and walls
p.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, "spruce")
for y in range(0, TOP + 1):
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if not (x in (X0, X1) or z in (Z0, Z1)):
                continue
            quoin = x in (X0, X1) and z in (Z0, Z1)
            if quoin:
                key = "bricks_mossy" if y == 0 else "bricks"
            elif y <= 1:
                key = "plinth_mossy" if jit(x, y, z, 491) < (600 if y == 0 else 200) else "plinth"
            else:
                key = "render"
            p.put(x, y, z, key)
# buttresses along the sides between the windows
for z in (4, 7):
    for x, facing in ((X0, "east"), (X1, "west")):
        p.put(x, 1, z, "bricks")
        p.put(x, 2, z, "bricks")
# the roof: ridge along z, the gables boarded over a band; rafter ends under the eaves
p.roof_ridge_z(Z0, Z1, X0, X1, TOP + 1, TILE, "tile_ridge", gable="render")
for x in range(X0 + 1, X1):
    for z in (Z0, Z1):
        if p.get(x, TOP + 1, z) == "render":
            p.put(x, TOP + 1, z, "beam_x")
# the door, a round light above it, windows down both sides and in the back gable
door_frame(p, 4, 1, Z0, "north")
p.put(4, TOP + 2, Z0, "pane")
p.put(4, TOP + 3, Z0, "pane")
for z in (3, 6, 9):
    window(p, X0, 2, z, "west", height=2, lintel=True)
    window(p, X1, 2, z, "east", height=2, lintel=True)
p.put(4, TOP + 2, Z1, "pane")
# the belfry on the front gable: four posts on the ridge course, the bell, a brick pyramid roof
peak = TOP + 1 + (X1 - X0) // 2
BY = peak + 1
for x in (3, 5):
    for z in (Z0, Z0 + 2):
        p.fill(x, BY - 1, z, x, BY + 2, z, "post")
p.fill(3, BY - 1, Z0 + 1, 5, BY - 1, Z0 + 1, "slab_top")
p.fill(4, BY - 1, Z0, 4, BY - 1, Z0 + 2, "slab_top")
p.put(4, BY + 1, Z0 + 1, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
p.fill(3, BY + 2, Z0 + 1, 5, BY + 2, Z0 + 1, "beam_x")
p.put(4, BY + 2, Z0, "beam_x")
p.put(4, BY + 2, Z0 + 2, "beam_x")
for x in range(2, 7):
    for z in range(Z0 - 1, Z0 + 4):
        ring = max(abs(x - 4), abs(z - (Z0 + 1)))
        if ring == 2:
            facing = "south" if z == Z0 - 1 else "north" if z == Z0 + 3 else "east" if x == 2 else "west"
            p.put(x, BY + 3, z, stairs_state("brick", facing))
        elif ring == 1:
            facing = "south" if z == Z0 else "north" if z == Z0 + 2 else "east" if x == 3 else "west"
            p.put(x, BY + 4, z, stairs_state("brick", facing))
p.put(4, BY + 4, Z0 + 1, "brick")
p.put(4, BY + 5, Z0 + 1, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
# inside: pews either side of the aisle, the altar, candles, a lectern, lanterns, the alms chest
for z in range(3, 8, 2):
    for x in (1, 2, 6, 7):
        p.put(x, 1, z, stairs_state("spruce", "north"))
for z in range(2, Z1):
    p.put(4, 1, z, "minecraft:red_carpet")
p.fill(3, 1, Z1 - 1, 5, 1, Z1 - 1, "chiseled")
p.put(3, 2, Z1 - 1, "minecraft:candle[candles=3,lit=false,waterlogged=false]")
p.put(5, 2, Z1 - 1, "minecraft:candle[candles=2,lit=false,waterlogged=false]")
p.put(4, 2, Z1 - 1, "minecraft:white_carpet")
p.put(4, 1, Z1 - 2, "minecraft:lectern[facing=north,has_book=false,powered=false]")
p.put(1, 1, Z1 - 1, "minecraft:chest[facing=east,type=single,waterlogged=false]")
p.put(7, 1, Z1 - 1, "minecraft:potted_white_tulip")
p.fill(X0 + 1, TOP + 1, 5, X1 - 1, TOP + 1, 5, "beam_x")
p.put(2, TOP, 5, "lantern_hanging")
p.put(6, TOP, 5, "lantern_hanging")
# the doorstep and a lantern post either side
p.fill(3, 0, 0, 5, 0, 0, "cobble")
lantern_post(p, 1, 1, 0)
lantern_post(p, 7, 1, 0)

p.connector(4, 0, 0, "building_in", "north", "minecraft:cobblestone")
p.emit("a small whitewashed chapel with a steep red tile roof and an open belfry over the door",
       ["Faces north: the door and the building_in connector are on the -z side."])
