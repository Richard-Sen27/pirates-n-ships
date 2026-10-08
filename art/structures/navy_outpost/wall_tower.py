"""Generates the navy outpost's corner tower (7x7, two floors and a roof platform) that ends a run of curtain wall,
and prints the BuildSpec as JSON. It is also the entry of the ``navy_outpost/terminators`` pool, the fallback once the
walls pool's depth runs out.

It ends either run: wall_east_in on the west face [0, 0, 2] when it closes the eastward run, wall_west_in on the east
face [6, 0, 3] when it closes the westward one; it pulls nothing itself. The sea is to the north like the wall's.

Build: a 5x5 stone core (walls on x 1..5, z 1..5) between four corner pilasters that stand the full height of the box.
On the sea and land faces the core is set back behind the pilasters from a sloped, mossy plinth up to a corbel table
under the roof; on the side faces the curtain's body continues to the walkway's height (y 4) and ends in a corbelled
ledge level with the walkway, so the walkway runs on to the doors in the core. The corbels carry the roof platform
(y 9, the full 7x7) with slab-capped merlons, lanterns on the corner merlons and the navy flag on a pole in the middle.

Inside, a ladder on the core's north wall climbs from the ground floor through a landing level with the walkway (y 4; a
door in each side wall at z 2 leads out onto the curtain's walkway, the one away from the wall stays shut) to the
roof. The ground floor's door on the landward face (z 5) opens under a blue banner onto the path at the wall's foot."""
from _style import (dry, EAST_Z, PALETTE, WALK, WEST_Z, age, arrow_slit, banner, connector, door, flagpole, fort_face,
                    merlons, stair_shape)
from buildspec import Piece

S, H = 7, 12
ROOF = 9                # the roof platform's floor row (people stand on y 10)
CORBEL = ROOF - 1
C0, C1 = 1, 5           # the core's walls
LX, LZ = 3, 2           # the ladder column (against the core's north wall, facing south)

p = Piece("navy_outpost_wall_tower", "Navy Outpost Wall Tower", (S, H, S), PALETTE)

# the foundation and the four corner pilasters (chiseled plinth, the walkway's andesite band)
p.fill(0, 0, 0, S - 1, 0, S - 1, "bricks")
for x, z in ((0, 0), (S - 1, 0), (0, S - 1), (S - 1, S - 1)):
    p.fill(x, 1, z, x, CORBEL, z, "bricks")
    p.put(x, 1, z, "chiseled")
    p.put(x, WALK, z, "andesite")

# the core, floors of spruce planks
p.ring(C0, C0, C1, C1, 1, CORBEL, "bricks")
p.fill(C0 + 1, 0, C0 + 1, C1 - 1, 0, C1 - 1, "spruce")
p.fill(C0 + 1, WALK, C0 + 1, C1 - 1, WALK, C1 - 1, "spruce")

# the skin: sea and land faces set back up to the corbels; the side faces carry the curtain to the walkway and end in a
# corbelled ledge there, then set back up to the roof corbels
fort_face(p, [(x, 0) for x in range(1, S - 1)], "south", corbel=CORBEL)
fort_face(p, [(x, S - 1) for x in range(1, S - 1)], "north", corbel=CORBEL)
for x, inward in ((0, "east"), (S - 1, "west")):
    cells = [(x, z) for z in range(1, S - 1)]
    fort_face(p, cells, inward, corbel=WALK)
    for z in range(1, S - 1):
        for y in range(WALK + 1, CORBEL):
            p.clear(x, y, z)
        p.put(x, CORBEL, z, stair_shape("stone_brick", inward, "top"))
p.fill(C0, WALK, C0, C1, WALK, C0, "andesite")          # the band round the core at the landing
p.fill(C0, WALK, C1, C1, WALK, C1, "andesite")

# the roof platform over the whole box, merlons round it (lanterns on the corner ones), the navy flag in the middle
p.fill(0, ROOF, 0, S - 1, ROOF, S - 1, "andesite")
ring = sorted({(i, 0) for i in range(S)} | {(i, S - 1) for i in range(S)} | {(0, i) for i in range(S)}
              | {(S - 1, i) for i in range(S)})
corners = {(0, 0), (S - 1, 0), (0, S - 1), (S - 1, S - 1)}
merlons(p, [(x, z) for x, z in ring if (x + z) % 2 == 0 and (x, z) not in corners], ROOF + 1)
merlons(p, sorted(corners), ROOF + 1, cap=False)
for x, z in sorted(corners):
    p.put(x, ROOF + 2, z, "lantern")
flagpole(p, 3, ROOF + 1, 3, height=2, flies="east")

# the ladder from the ground floor through the landing to a hatch in the roof
for y in range(1, ROOF + 1):
    p.put(LX, y, LZ, "ladder_s")

# doors: ground floor landward under a banner, landing onto the walkway on both sides
door(p, 3, 1, C1, facing="south")
p.clear(3, 1, S - 1)
p.put(3, 0, S - 1, "cobble")
banner(p, 3, 4, S - 1, "south")
banner(p, 3, 6, 0, "north")
door(p, C0, WALK + 1, 2, facing="west")
door(p, C1, WALK + 1, 2, facing="east", hinge="right")

# arrow slits (barred) in the core on every side, both floors
arrow_slit(p, 2, 2, C0, "x", grille=True)
arrow_slit(p, 4, 6, C0, "x", grille=True)
arrow_slit(p, 2, 6, C1, "x", grille=True)
arrow_slit(p, 4, 2, C1, "x", grille=True)
for x in (C0, C1):
    arrow_slit(p, x, 2, 4, "z", grille=True)
    arrow_slit(p, x, 6, 4, "z", grille=True)

# stores and lights: a barrel with a lantern on the ground floor, powder and a chest on the landing
p.put(4, 1, 4, "minecraft:barrel[facing=up,open=false]")
p.put(4, 2, 4, "lantern")
p.put(2, 1, 3, "pirates_n_ships:cargo_crate")
p.put(2, WALK + 1, 4, "pirates_n_ships:cargo_barrel")
p.put(2, WALK + 2, 4, "lantern")
p.put(4, WALK + 1, 4, "minecraft:chest[facing=west,type=single,waterlogged=false]")
p.put(3, CORBEL, 3, "lantern_hanging")

age(p)

connector(p, 0, 0, EAST_Z, "wall_east_in", "west", "minecraft:stone_bricks")
connector(p, S - 1, 0, WEST_Z, "wall_west_in", "east", "minecraft:stone_bricks")

dry(p)
p.emit("crenellated corner tower: a set-back core between corner pilasters, corbelled roof, lanterns and the navy flag",
       ["Ends a wall run: wall_east_in [0, 0, 2] (west face) or wall_west_in [6, 0, 3] (east face).",
        "Landing at the walkway's height (y 4) with doors west and east at z 2; roof platform y 9."])
