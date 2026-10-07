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
