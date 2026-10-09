"""Generates the Ponder scene "railings" (PND4, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

An 11x7x11 patch of calm sea: a sand seabed (y 0, Ponder's base plate) and water on y 1..2, a stone pier to the west
(x 0..1, z 2..4, its top level with the deck) and a ship, bow north (hull x 3..8, z 1..9: a spruce bottom on y 1, walls
on y 2 round a dry hold, a deck on y 3). Her port (west) gunwale on y 4, from the bow, is the whole family of RAIL1,
RAIL3 and RAIL4, laid out for the default camera from the north-west (PND3):

* [3, 4, 1] the corner of the bow bulwark (north and west edges); the bulwark runs east along z 1 to x 8 with a bulwark
  gun port at [6, 4, 1] facing north, its lid shut, and a cannon behind it (front [6, 4, 2], rear [6, 4, 3], facing
  north), which the scene shows last;
* [3, 4, 2] a railing (the end post where it meets the bulwark) with a lantern standing on that post at [3, 5, 2];
* [3, 4, 3] a railing gate facing west, shut (the gangway opening onto the pier);
* [3, 4, 4] a railing between the gate and the port (no posts: both continue the run);
* [3, 4, 5] a railing port facing west, a cannon behind it (front [4, 4, 5], rear [5, 4, 5], facing west);
* [3, 4, 6..9] the run the scene lays block by block; [3, 4, 9] gets its second (south) edge by a second click;
* a swivel gun on top of [3, 4, 7], shifted to the railed (west) edge.

The blocks the scene changes are saved in their first look: the run's railings with the posts of the finished run, the
last one ([3, 4, 9]) with only its west edge and its end post; the gate shut; the gun port's lid shut."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 11, 7, 11
X = 3  # the port gunwale's railing column
GATE = (X, 4, 3)
PORT = (X, 4, 5)
RUN = (6, 9)
LANTERN = (X, 5, 2)
SWIVEL = (X, 5, 7)
GUN_PORT = (6, 4, 1)
PORT_GUN = ((4, 4, 5), (5, 4, 5))
BOW_GUN = ((6, 4, 2), (6, 4, 3))


def edges(block: str, north=False, east=False, south=False, west=False, nw=False, ne=False, se=False, sw=False) -> str:
    def b(v):
        return "true" if v else "false"
    return (f"{MOD}:{block}[east={b(east)},north={b(north)},post_ne={b(ne)},post_nw={b(nw)},post_se={b(se)},"
            f"post_sw={b(sw)},south={b(south)},waterlogged=false,west={b(west)}]")


p = Piece("ponder_railings", "Ponder: Railings", (W, H, D), {})
seabed(p)
footprint = hull(p, 3, 1, 8, 9, bottom=1, wall_top=2, deck=3)
p.fill(0, 1, 2, 1, 3, 4, "minecraft:stone_bricks")
sea(p, 2, dry=[footprint])

# the bow bulwark along z 1 with its gun port; the corner block takes the west edge too
p.put(X, 4, 1, edges("oak_bulwark", north=True, west=True, nw=True, sw=True))
for x in range(4, 9):
    if (x, 4, 1) == GUN_PORT:
        continue
    p.put(x, 4, 1, edges("oak_bulwark", north=True, ne=(x == 8)))
p.put(*GUN_PORT, f"{MOD}:oak_bulwark_port[facing=north,open=false,propped=false,waterlogged=false]")

# the port gunwale
p.put(X, 4, 2, edges("oak_railing", west=True, nw=True))
p.put(*LANTERN, "minecraft:lantern[hanging=false,waterlogged=false]")
p.put(*GATE, f"{MOD}:oak_railing_gate[facing=west,hinge=left,open=false,powered=false,waterlogged=false]")
p.put(X, 4, 4, edges("oak_railing", west=True))
p.put(*PORT, f"{MOD}:oak_railing_port[facing=west,waterlogged=false]")
for z in range(RUN[0], RUN[1] + 1):
    p.put(X, 4, z, edges("oak_railing", west=True, sw=(z == RUN[1])))
p.put(*SWIVEL, f"{MOD}:swivel_gun[load=empty,mount=west,piece=yoke,waterlogged=false]")

# the guns
p.put(*PORT_GUN[0], f"{MOD}:cannon[facing=west,load=empty,part=front,shot=ball,waterlogged=false]")
p.put(*PORT_GUN[1], f"{MOD}:cannon[facing=west,load=empty,part=rear,shot=ball,waterlogged=false]")
p.put(*BOW_GUN[0], f"{MOD}:cannon[facing=north,load=empty,part=front,shot=ball,waterlogged=false]")
p.put(*BOW_GUN[1], f"{MOD}:cannon[facing=north,load=empty,part=rear,shot=ball,waterlogged=false]")

p.emit("ponder scene: a ship at a pier, her port gunwale railed, gated, ported and bulwarked, with guns and a swivel",
       ["Bow north. Hull x 3..8, z 1..9: bottom y 1, walls y 2, deck y 3; sea y 1..2; pier x 0..1, z 2..4, y 1..3.",
        f"Gunwale x {X}, y 4: bulwark corner z 1 (bulwark to x 8, gun port {list(GUN_PORT)}), railing z 2, gate z 3, "
        f"railing z 4, port z 5, run z {RUN[0]}..{RUN[1]}; lantern {list(LANTERN)}, swivel {list(SWIVEL)}.",
        f"Cannons: {[list(c) for c in PORT_GUN]} facing west, {[list(c) for c in BOW_GUN]} facing north."])
