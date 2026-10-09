"""Generates the Ponder scene "crew_orders" (PND2, docs/almanac.md "Scenes") and prints the BuildSpec as JSON.

An 11x5x11 harbour corner: a stone-brick quay on the west (x 0..1, up to y 3) with the harbor master's desk on it at
[1, 4, 5], its customer side facing east toward the ship; a sand seabed (y 0, Ponder's base plate) and sea water on
y 1..2 east of the quay. A ship lies alongside with the bow north (hull x 3..9, z 2..8: a spruce bottom on y 1, walls
on y 2 round a dry hold, a deck on y 3), and on her deck: a sail winch at [4, 4, 3], a cannon run out to starboard
(master at [8, 4, 4] facing east, its rear at [7, 4, 4]), a cargo barrel of powder and shot at [8, 4, 6] within the
crew's supply reach, and the helm at [6, 4, 7] facing south. The scene (almanac/ponder/CrewOrdersScene) puts the
harbor master behind the desk and the crew members on deck."""
from _sea import MOD, Piece, hull, sea, seabed

W, H, D = 11, 5, 11
QUAY = "minecraft:stone_bricks"
DESK = (1, 4, 5)
WINCH = (4, 4, 3)
CANNON = (8, 4, 4)
SUPPLY = (8, 4, 6)
HELM = (6, 4, 7)

p = Piece("ponder_crew_orders", "Ponder: Crew and Orders", (W, H, D), {})
seabed(p)
p.fill(0, 0, 0, 1, 3, D - 1, QUAY)
footprint = hull(p, 3, 2, 9, 8, bottom=1, wall_top=2, deck=3)
sea(p, 2, dry=[footprint])
p.put(*DESK, f"{MOD}:harbor_desk[facing=east,waterlogged=false]")
p.put(*WINCH, f"{MOD}:sail_winch[facing=south]")
p.put(*CANNON, f"{MOD}:cannon[facing=east,load=empty,part=front,shot=ball,waterlogged=false]")
p.put(CANNON[0] - 1, CANNON[1], CANNON[2], f"{MOD}:cannon[facing=east,load=empty,part=rear,shot=ball,waterlogged=false]")
p.put(*SUPPLY, f"{MOD}:cargo_barrel")
p.put(*HELM, f"{MOD}:helm[facing=south]")

p.emit("ponder scene: a quay with a harbor desk and a ship alongside with a winch, a cannon, its supply and the helm",
       [f"Quay x 0..1 up to y 3, desk {list(DESK)} facing east.",
        "Ship x 3..9, z 2..8: bottom y 1, walls y 2, deck y 3; sea y 1..2.",
        f"Winch {list(WINCH)}, cannon master {list(CANNON)} facing east, supply {list(SUPPLY)}, helm {list(HELM)}."])
