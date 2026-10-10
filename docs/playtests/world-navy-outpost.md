# Playtest: navy outposts with their garrison (work package WG3)

The worldgen JSON, the pools, the outpost config and the garrison's posts (each one inside its piece, on a sturdy block
with room to stand, filled in the decided order and turned with the piece) are covered by JUnit tests. GameTests place
the outpost (fort gate, quay, one wall each way ending in a tower, one building) on a hand-built beach facing north
and east and check: the sea wall on the last land row, the quay deck one above the water with two berths in water, the
`NAVY_OUTPOST` port with its open market and bound desk, 6 soldiers and 1 officer at their posts (stationary,
persistent, facing their post's direction, inside the box, not stuck in a block), no doubled garrison when the same
structure is placed twice, and the `enabled` toggle. Only a real world shows where outposts land, the full five-wall
runs, and how the garrison behaves around a player.

Setup: `./gradlew :neoforge:runClient`, a **new** world (default world type; outposts only generate in new chunks),
cheats on. Use survival for steps 5 to 9 (creative players are never attacked and never wanted). Please send
`latest.log` and screenshots, and note the world seed.

## Steps

1. **Find one.** `/locate structure pirates_n_ships:navy_outpost`, then `/tp` to the coordinates (y about 80).
   - **Expected:** a stone fort on a beach. Outposts are the rarest port (random spread 48 chunks, separation 20,
     frequency 0.8), only on beaches (`#minecraft:is_beach`) with the sea within 12 blocks. If `/locate` finds nothing
     within reach, try another seed.
2. **Facing the sea.** Look at the fort from above.
   - **Expected:** the curtain wall runs along the shore with its crenellated parapet on the sea side and its seaward
     face standing at the waterline; the sea gate (arched, middle of the gate piece) opens onto the quay, which runs
     straight out over the water. The landward gatehouse (blue banners, lanterns) faces inland. Each wall run ends in
     a corner tower; every wall segment has a cannon in an embrasure, muzzle to the sea. One building (barracks,
     brig or watchtower) stands on the gravel apron outside the land gate, to its east.
3. **Quay and berths.** Walk out through the sea gate.
   - **Expected:** the quay deck is level with the court's paving, one block above the water. Halfway out on both
     sides the water is open: the two berths. `/pirates world ports` lists `pirates_n_ships:navy_outpost_<x>_<z>
     (navy_outpost, <climate>) ... 2 berths [...]`; `/pirates world port nearest` gives the same port with the two
     berths, `bow` pointing out to sea.
4. **Flag, desk, notice board.** In the court and the harbor master's office (west side, door to the court).
   - **Expected:** the navy flag flies on the pole in the court and on each tower's roof. The harbor desk opens the
     market of the outpost (no "not bound" message; a navy port's goods and prices, different from a village's). The
     notice board on the office's north wall lists bounties and takes new ones as anywhere else.
5. **The garrison.** Walk through the fort without a bounty (fresh survival player).
   - **Expected:** 6 navy soldiers and 1 navy officer (default config). The officer stands in the court beside the
     office door, facing into the court. Two soldiers flank the inside of the land gate facing inland; the others stand
     on the walkways of the walls nearest the gate (beside the guns) and on the towers' roofs, facing the sea. Nobody
     wanders off: they turn their heads to look at you but keep their posts. They do not attack you. None of them is
     stuck in a block. `/kill @e[type=pirates_n_ships:navy_soldier]` and wait: nobody respawns (relief comes later).
6. **Persistence.** Fly 200 blocks away, come back; then save, quit and reopen the world.
   - **Expected:** the same garrison at the same posts, still standing still.
7. **Turn in a pirate.** Spawn a pirate (`/summon pirates_n_ships:pirate`) away from the fort, beat it down and put it
   in shackles, lead it to the officer and use the officer.
   - **Expected:** the officer takes the prisoner and pays `law.pirate_turn_in.<tier>` doubloons (as in the L1
     playtest, law-world.md). The soldiers do not interfere while you are not wanted.
8. **Pay a fine.** Raise your criminal score (e.g. hit a villager), check it with `/pirates law score get @s` (or set it: `/pirates law score set @s 5`), then use the officer
   with doubloons in hand.
   - **Expected:** the fine is paid for whole points at `law.fine_cost_per_point`, with change back, and the score
     drops. With a bounty, expect the soldiers on the walls to attack on sight (muskets) and the officer to refuse
     turn-ins.
9. **Ransom.** Capture a navy officer or a merchant elsewhere (shackles), lead it to the outpost's officer and use
   him. Then set `law.ransom_needs_port = true` in the server config and try the same once at the outpost's officer
   and once with a navy officer you summoned far away from any outpost.
   - **Expected:** with the option off, both officers pay the ransom. With it on, only the outpost's officer pays (he
     stands inside the outpost's port box); the summoned one far away refuses. Note whether the refusal message is
     clear.
10. **The brig.** If the outpost's building is the brig (or find another outpost): open the cell doors.
    - **Expected:** two cells of brig bars with brig doors, closed and unlocked (no owner), straw, a cauldron. A
      shackled prisoner led into a cell and the door locked with a brig key stays there (as on a ship, brig.md).
11. **The cannons.** Look at a wall gun.
    - **Expected:** a two-block cannon (muzzle north to the sea), unloaded; it fires only after you load powder and
      shot like any cannon (§8.2). The barrel beside it is decoration and empty. The garrison does not fire the guns.
12. **Config.** In the server config:
    - `world.structures.navy_outpost.garrison_soldiers = 10`, `garrison_officers = 2`, restart, find a new outpost:
      **expected** 2 officers (beside the door and behind the desk) and 10 soldiers (the remaining ones on the further
      walls, in the building and on the gate's own walkways).
    - `garrison_soldiers = 0`, `garrison_officers = 0`: new outposts stand empty.
    - `mobs.navy_soldier.enabled = false` (the mob toggle): no soldiers are placed.
    - `world.structures.navy_outpost.enabled = false`, restart, fly to an unexplored coast: **expected** no new
      outposts; existing ones and their garrison stay. `frequency = 0.5`: about half as many. Changing `spacing` or
      `separation` in the config does nothing (datapack values, see the comments).

## Open problems to watch
- **Terrain inside the fort.** The templates carry no air (art/README.md) and terrain adaptation is `none`, so on a
  beach that rises above the court's paving (up to `max_shore_height` 4) sand may fill parts of the court or the
  apron. A garrison post filled by terrain stays empty rather than smother a mob, so a few guards may be missing there.
  Please screenshot such cases.
- **The full fort is wide.** Size 5 grows up to five walls and a tower each way (about 100 blocks along the shore);
  wall runs stand rigidly at the gate's height, so on an uneven coast they may cut into a hill or stand on air over a
  bay. Please report how often that looks bad.
- **Guards on the towers** stand on the roof platform; check that they do not fall through the ladder hatch.

## MOB2: officers leading squads

Setup: as above (a creative world, find a navy outpost with `/locate structure pirates_n_ships:navy_outpost`), stand
in the fort's court. Switch to survival for the combat steps. For quicker checks set
`mobs.squad.post_pause_seconds = 5` in the server config.

1. **Info.** `/pirates mob squad info` near the officer.
   - **Expected:** `Squad of Navy Officer: at_post, waypoint 1 of N, 0 soldiers, next patrol in ... s` with N up to 15
     for a full fort (court, land gate, sea gate, quay, gate east walkway, the east walls, gate west walkway, the west
     walls, the head of the court's stairs; 15 with five walls each way). In an outpost generated before MOB2 the same works: the squad is built from the outpost when its officer
     first loads.
2. **Patrol.** `/pirates mob squad patrol`.
   - **Expected:** the officer and three soldiers (the two land-gate guards and the east wall's guard, the nearest
     ones that can walk to him; never a tower roof guard, who only has a ladder) leave their posts at once. They walk in file, about 1.5 blocks apart, the
     officer first: the court, the land gate, the sea gate, out onto the quay, then up the court's stone stairs to the
     walkway and out to each wall of the east run, back along the walls and the sea-side walkway and out to each wall of
     the west run, then back to the head of the stairs (each wall's waypoint is at its far end, past its gun, so the file
     walks every segment end to end; ST3b). At each waypoint the
     officer waits for the file to close up, then they stand for `post_pause_seconds`. A soldier who falls far behind
     runs. After the last wall they walk back to their posts, stand still again and face their post's way (wall
     guards to the sea, gate guards landward).
   - Please watch and report: the stairs up to the walkway (does anyone get stuck at the top step or in the
     1-wide passage between the quay and the sea gate?), the guns on the walkway (do they squeeze past the cannon?),
     a soldier stuck behind a merlon, anyone walking off the walkway's open landward edge.
3. **Return.** During a patrol `/pirates mob squad return`.
   - **Expected:** everyone walks straight back to his own post and stands there (stationary: no strolling about
     afterwards). `/pirates mob squad info` shows `at_post` and the next patrol in about `patrol_interval_minutes`.
4. **Fighting as one.** Get wanted or hit a soldier of the patrolling squad once (survival).
   - **Expected:** the whole squad turns on you within a second: the officer draws his saber and closes in, the
     soldiers aim their muskets. Hit a soldier as a creative player: nobody reacts. Kill a summoned pirate near the
     patrol's route (`/summon pirates_n_ships:pirate`) or let one walk in: whoever sees him first engages, and the
     officer's target becomes the soldiers' target. After the fight (you leave or die) the squad gathers on the officer
     where it stands and walks on to the waypoint.
5. **Refill.** Kill one soldier of the squad (`/kill` on him) during a patrol.
   - **Expected:** at the next waypoint another garrison soldier who can walk to the officer leaves his post and runs
     to join the file at the end: with the default garrison (6) the west wall's guard (since ST3b he can walk past his
     gun); with `world.structures.navy_outpost.garrison_soldiers = 9` a gate walkway guard too. Kill the officer instead: the soldiers walk back to their posts on their own after
     about five seconds.
6. **Night.** Let night fall during a patrol started by itself (`/time set 12000` and wait, or wait for the timer).
   - **Expected:** with `mobs.squad.night_at_posts = true` the squad turns back at nightfall and no patrol starts until
     morning. A patrol ordered with the command keeps going until its round is done.
7. **Timer.** Leave the squad at its posts for `patrol_interval_minutes` (default 10; try 1).
   - **Expected:** the officer sets off on his own with three soldiers; the next patrol starts that many minutes after
     the squad got back.
8. **Config.** `mobs.squad.enabled = false`: `/pirates mob squad patrol` is refused and the garrison keeps its posts; a
   squad on patrol when you switch it off walks back to its posts. `mobs.squad.size = 5`: five soldiers follow (the
   tower roof guards never). `size = 0`: the officer patrols alone.
9. **Save and reload** during a patrol (leave and rejoin the world).
   - **Expected:** the squad goes on (or walks home if it was fighting); nobody stays stuck away from his post.

### Open problems to watch (MOB2)
- **Pathing on the walls (fixed in ST3b).** The walkway is reached only by the court's stairs on the gate's east
  side. Each wall's gun deck used to leave a one-block passage along the hoarding that vanilla pathfinding handled
  badly. ST3b moved the powder barrel and the shot locker against the parapet beside the muzzle and the lantern onto
  the hoarding's rail, so the walkway is clear two blocks wide through every wall and the gate; the GameTests walk a
  soldier along two walls and back, draw the west wall's guard into a squad of four and walk the fixture's whole
  route without skipping a waypoint. Only outposts generated after ST3b have the new walls (and only officers whose
  squad is built after ST3b get the new route); older ones keep the narrow decks. A waypoint the officer gets no
  closer to for 10 seconds is still skipped; please report it if that happens on a new outpost.

### ST3b check: walk a soldier along the walls
1. In a **new** world, find a navy outpost generated after ST3b (`/locate structure pirates_n_ships:navy_outpost`)
   that has at least two walls on a run (most do; size 5 grows up to five each way). Set `mobs.squad.post_pause_seconds
   = 5` and `mobs.squad.size = 4`.
   - **Expected:** on each wall the cannon stands in its embrasure with a barrel on each side of the muzzle against
     the parapet; behind the gun the walkway is open two blocks wide; the lantern stands on the hoarding's rail beside
     the ladder. The flags on the court's pole and the towers show the tall pole at once (no single-pole look in the
     first moment); the pirate camp's pole likewise.
2. `/pirates mob squad info`, then `/pirates mob squad patrol`, and follow the squad up the stairs.
   - **Expected:** four soldiers follow (the two land-gate guards and the wall guards nearest the gate on both runs, not
     a tower roof guard). The file walks out along the east run to the last wall before the tower, stopping at the far
     end of each wall, turns back along the walls, crosses the gate's walkway, walks out along the west run the same
     way and comes back to the head of the stairs, then everyone walks home. Nobody squeezes past a gun, gets stuck
     behind a barrel, steps off the hoarding or falls down a ladder hole. `/pirates mob squad info` counts the
     waypoints up without jumps (a jump means one was skipped as stuck).
- **Ladders.** Vanilla mobs don't climb ladders on purpose: tower roof guards are never drafted, and a soldier who
  fell off a wall and can only get back by ladder keeps trying to reach his post.
- **Terrain.** A waypoint that terrain filled is skipped; a post filled by terrain has no guard (WG3).

## STR4: a natural layout (walls that bend, a court inside)
Every outpost is different now: the gate and the quay as before, a wall trace that runs along the shore, turns inland
round a corner, may step out again or turn back toward the other run, with bastions, sally ports, stairs and three
kinds of tower; inside, lanes of gravel and cobbles from the land gate with buildings (barracks of three kinds, two
brigs, a watchtower, a signal tower, the officers' quarters, a powder magazine, an armoury, a gun battery, a store
house, a chapel), a parade ground with the navy ensign and a well, and props (shot, crates, a drill target, a bell, a
cart, a tree, benches). Renders of three generated outposts: `art/renders/structures/navy_outpost_*_{top,iso}.png`.

Use a **new** world (old outposts keep their old shape). Find three outposts: `/locate structure
pirates_n_ships:navy_outpost`, teleport there, then `/locate` again from a few hundred blocks further along the coast.

1. **The trace.** Walk round each fort on the outside, then along the walkway (court stairs, then the wall pieces' own
   stairs).
   - **Expected:** no two forts share a shape and none is a straight line. Each run leaves the gate along the shore for
     at least one piece, most turn inland at a corner (a lantern on the corner merlon) and some turn back toward the
     other run with a corner tower; the run always ends in a tower (the square corner tower, the round tower or the
     squat battery). Bastions on the shore stand in the water on their footings, with two guns facing out to sea;
     every wall piece but the steps has a gun facing outward. Walkways join piece to piece without gaps or steps, including round the
     corners and onto the bastions. Report any wall piece floating over a dip or buried in a hill (the walls are level
     at the gate's height on purpose; how often it looks wrong matters).
2. **The court.** Walk the lanes from the land gate.
   - **Expected:** lanes of gravel and cobbles between andesite kerbs, lying on the ground (no paved rectangles, grass
     between the kerbs), bending and forking; buildings stand beside them with their doors to the lane, sitting on the
     ground (the beard levels a little ground round them; no floating corners, no buried doors); decorations fill the
     gaps; lanes end at a store shed, the privy or a woodpile. Most courts lie inside the walls; some lanes run on past
     the towers inland (report how it looks).
3. **The buildings.** Open a few doors: the powder magazine (iron door, earth bank on three sides), the larger brig
   (four cells), the officers' quarters (ladder to the bedrooms), the chapel (bell in the gable), the signal tower (climb
   the ladder to the gallery: a smoking fire on a hay bale).
   - **Expected:** furnished, lit (the magazine dark on purpose), nothing poking through walls; the smoke column of the
     signal fire is visible from the sea.
4. **The brig.** Shackle a pirate and put him in a cell of the larger brig, lock the brig door with a key.
   - **Expected:** the cell counts as a cell (the prisoner stays, the brig code's cell check passes), as in the ST3
     brig.
5. **The garrison.** Count the navy mobs (`/pirates mob squad info` near the officer).
   - **Expected:** with the defaults six soldiers and one officer: the officer by the office door in the gate, two
     guards at the land gate, the rest on the nearest wall pieces or towers facing outward (out to sea on the shore,
     out to the land on a flank); none stands inside a block. With `garrison_soldiers` raised, the buildings get their
     guards (inside the barracks, in the brig's guard room facing the cells, on the watchtower's lookout and so on).
6. **The patrol.** `/pirates mob squad patrol`, follow the squad.
   - **Expected:** the file walks the court, the gates, the quay, then out along the east run piece by piece round its
     corners to the last wall before the tower, back, and the same on the west run; nobody gets stuck on a corner, a
     bastion or a stair piece. Report any waypoint skipped as stuck.
7. **The quay.** Sail in.
   - **Expected:** the quay runs out into the water with both berths free and in water; no sand bar along it, the sea
     in front of the walls stays sea (the walls and the quay are kept out of the beard; the gate's own beard may widen
     the beach a few blocks beside the quay's root).
