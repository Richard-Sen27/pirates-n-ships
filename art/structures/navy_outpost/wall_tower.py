"""Generates the navy outpost's corner tower (7x7, two floors and a roof platform) that ends a run of curtain wall,
and prints the BuildSpec as JSON. It is also the entry of the ``navy_outpost/terminators`` pool, the fallback once the
walls pool's depth runs out.

It ends either run: wall_east_in on the west face [0, 0, 2] when it closes the eastward run, wall_west_in on the east
face [6, 0, 3] when it closes the westward one; it pulls nothing itself. The sea is to the north like the wall's.
Inside, a ladder on the north wall climbs from the ground floor through a landing level with the walkway (y 4; a
door in each side wall at z 2 leads out onto the curtain's walkway, the one away from the wall stays shut) to the
roof: a crenellated platform at y 9 with lanterns on the corner merlons and the navy flag on a pole in the middle.
The ground floor's door on the landward face (z 6) opens onto the path at the wall's foot."""
from _style import EAST_Z, PALETTE, WALK, WEST_Z, connector, door, flagpole, weather
from buildspec import Piece

S, H = 7, 12
ROOF = 9                # the roof platform's floor row (people stand on y 10)
LX, LZ = 3, 1           # the ladder column (against the north wall, facing south)

p = Piece("navy_outpost_wall_tower", "Navy Outpost Wall Tower", (S, H, S), PALETTE)

# the shell: stone bricks on a chiseled plinth, a polished andesite band at the landing, floors of planks
p.fill(0, 0, 0, S - 1, 0, S - 1, "bricks")
p.ring(0, 0, S - 1, S - 1, 1, ROOF, "bricks")
p.ring(0, 0, S - 1, S - 1, 1, 1, "chiseled")
p.ring(0, 0, S - 1, S - 1, WALK, WALK, "andesite")
p.fill(1, 0, 1, S - 2, 0, S - 2, "spruce")
p.fill(1, WALK, 1, S - 2, WALK, S - 2, "spruce")
p.fill(0, ROOF, 0, S - 1, ROOF, S - 1, "andesite")
weather(p, ((1, 2, 0, "bricks_mossy"), (5, 6, 0, "bricks_cracked"), (0, 3, 5, "bricks_mossy"),
            (6, 7, 4, "bricks_cracked"), (4, 8, 6, "bricks_mossy"), (2, 2, 6, "bricks_cracked")))

# merlons round the roof at the corners and every other block
for i in range(S):
    for x, z in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
        if i % 2 == 0:
            p.put(x, ROOF + 1, z, "bricks")

# the ladder from the ground floor through the landing to the roof
for y in range(1, ROOF + 1):
    p.put(LX, y, LZ, "ladder_s")

# doors: ground floor landward, landing onto the walkway on both sides (walkway rows z 1..4)
door(p, 3, 1, S - 1, facing="south")
door(p, 0, WALK + 1, 2, facing="west")
door(p, S - 1, WALK + 1, 2, facing="east", hinge="right")

# arrow slits of iron bars, a lantern inside on each floor
for y in (2, WALK + 2, 7):
    p.put(3, y, 0, "bars")
for y in (2, 7):
    p.put(0, y, 4, "bars")
    p.put(S - 1, y, 4, "bars")
p.put(5, 1, 5, "minecraft:barrel[facing=up,open=false]")
p.put(5, 2, 5, "lantern")
p.put(1, WALK + 1, 5, "pirates_n_ships:cargo_barrel")
p.put(1, WALK + 2, 5, "lantern")
p.put(5, WALK + 1, 5, "minecraft:chest[facing=west,type=single,waterlogged=false]")

# the roof: lanterns on the corner merlons, the navy flag in the middle
for x, z in ((0, 0), (S - 1, 0), (0, S - 1), (S - 1, S - 1)):
    p.put(x, ROOF + 2, z, "lantern")
flagpole(p, 3, ROOF + 1, 3, height=2, flies="east")
for x in (2, 4):
    p.put(x, 3, S - 1, "wool")     # the navy's blue beside the landward door

connector(p, 0, 0, EAST_Z, "wall_east_in", "west", "minecraft:stone_bricks")
connector(p, S - 1, 0, WEST_Z, "wall_west_in", "east", "minecraft:stone_bricks")

p.emit("crenellated corner tower with a ladder, lanterns on the merlons and the navy flag",
       ["Ends a wall run: wall_east_in [0, 0, 2] (west face) or wall_west_in [6, 0, 3] (east face).",
        "Landing at the walkway's height (y 4) with doors west and east at z 2; roof platform y 9."])
