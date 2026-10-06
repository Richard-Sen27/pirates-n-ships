# Playtest: milestone 1, ship assembly at the helm (spike 1)

Build: the branch of work package D1. Start with `./gradlew :neoforge:runClient`, create a **Creative** world
(default world type, cheats on) and fly to an ocean. Open chat with `T` for every command below.
Turn on coordinates with `F3`. Keep `logs/latest.log` for the end.

Known and expected in spike 1: the hold of an assembled ship **shows water inside** and you swim there. The dry hull
(water occlusion) is spike 2. Sable logs `Failed to apply tag physics properties. Unknown block: create:flywheel` on
every start. That is harmless.

## 0. Setup
1. Find open ocean at least 6 blocks deep. Fly so that your **feet are at Y=63** (one block above the water surface at
   Y=62) and face **east** (F3 shows `Facing: east`). Do not move between the commands of step 1.

## 1. Build the test hull (all coordinates relative to where you hover)
```
/fill ~2 ~-3 ~-2 ~8 ~ ~2 oak_planks hollow
/fill ~3 ~ ~-1 ~7 ~ ~1 air
/setblock ~5 ~-2 ~ pirates_n_ships:helm[facing=west]
/setblock ~4 ~-2 ~1 chest
```
Result: an open boat 7 long, 5 wide, 4 high (floor at Y=60, walls up to Y=63), with a dry hold of air below the
waterline, a helm and a chest on the hold floor. 97 blocks. Put a few different items into the chest (e.g. 5 diamonds,
1 iron sword).
- Expected: the helm shows the placeholder wheel texture on its front, wood on the other sides.

## 2. Terrain is not picked up
1. In shallow water near a beach, place a helm on sand or stone (`/setblock` or by hand) and use it (right-click, empty
   hand).
- Expected: red chat message "Nothing to assemble: no ship blocks are connected to the helm". Sand, stone, dirt,
  gravel, grass, kelp, seagrass and water stay in the world.
2. Place 3 oak planks in a row on the sand, the helm on top of one plank, use the helm.
- Expected: "Ship assembled: 4 blocks". The sand under the planks stays. Break or remove this little ship afterwards
  (`/sable remove @n`).

## 3. Block limit
1. Set `assembly.max_blocks` to `50` in the mod's server config (in-game mod config screen, or the
   `pirates_n_ships` server config file under `saves/<world>/serverconfig/`), then use the helm of the hull from step 1.
- Expected: red "Too many connected blocks: a ship may have at most 50. …". Nothing moves.
2. Set `max_blocks` back to `2048`.

## 4. Assemble in the sea
1. Use the helm of the hull from step 1 (empty hand, right-click).
- Expected: green "Ship assembled: 97 blocks". The boat keeps its exact place and look for the first moment, then
  rises a little and bobs on the waves. **The sea closes where the hull was**: looking at the water surface next to
  the hull, there is no rectangular air hole and no waterfall flowing into one. (The hold looks flooded, see above.)
- Screenshot A: the floating ship from outside, about 10 blocks away.

## 5. Walk on deck
1. Land on the wall top (Y=63 at assembly time) and walk around the rim and on the hold floor (you swim in the hold).
- Expected: you stand on the ship and move with it as it bobs, no falling through, no rubber-banding.

## 6. Shove it, refusal while moving
1. Stand on the ship and run `/sable physics impulse @n linear 30 0 0 global`
   (syntax from `refs/sable/.../command/SablePhysicsCommands.java` l.43-56; `@n` = nearest sub-level; the impulse is
   in kpg·m/s, about 50 kpg for this boat, so 30 gives roughly 0.6 m/s. Use 100 for a harder shove).
2. Immediately use the helm.
- Expected: red "The ship is still moving (x.xx m/s). Wait until it lies still". The ship drifts on.
3. Optional tilt check: `/sable physics impulse @n angular 30 0 0 global` and use the helm while it rocks.
- Expected: "The ship is tilted x.x°. It must be within 6.0° of level" or the "still moving" message.

## 7. Name the ship
1. Rename a name tag in an anvil to `Black Pearl`, then right-click the helm with it.
- Expected: "Ship named "Black Pearl"", the name tag is used up (in survival; in creative it stays).
  `/sable name get @n` prints `Black Pearl`.

## 8. Save, quit, rejoin while assembled
1. Esc, "Save and Quit to Title", re-open the world.
- Expected: the ship is where you left it, still floating, chest content unchanged when opened, name still
  `Black Pearl` (`/sable name get @n`). Using the helm still works (refuses or disassembles, never "not part of a ship").

## 9. Disassemble when still
1. Wait until the ship lies still (watch it for ~10 s; waves may keep it rocking a little). Stand on the hold floor or
   the rim, then use the helm.
- Expected: green "Ship disassembled: 97 blocks placed back". The boat is now world blocks, aligned to the block grid
  and level, facing the nearest 90° direction. You are still standing on the deck (not inside a block, not falling,
  no fall damage). **No stray water inside the hold**: the hold is air again. **No air hole in the sea** around the
  hull. The chest has exactly the items from step 1.
- Screenshot B: inside the hold after disassembly. Screenshot C: the outside waterline.
2. Use the helm again. Expected: it assembles again (97 blocks). This checks the round trip.

## 10. Obstruction
1. Before assembling (step 9.2 is a good moment), look at the west wall block in the middle of the hull at the
   waterline and write down its coordinates from F3 (`Targeted Block`). Assemble and wait until the ship lies still
   without drifting more than half a block (in calm water; `/sable physics impulse` back if needed).
2. `/setblock <X> <Y> <Z> cobblestone` at the written coordinates (inside the water under the ship's wall), then use
   the helm.
- Expected: red "Something is in the way at X Y Z" naming the cobblestone (or another position the hull would land on
  if the ship drifted). The ship stays assembled. `/setblock <X> <Y> <Z> water`, use the helm again: it disassembles.

## 11. Config toggles (optional)
- `enabled = false` → using a world helm says "Ship assembly is disabled on this server".
- `restore_sea_on_assembly = false` → after assembling, an air hole remains in the sea where the hull was (vanilla
  water flows in only slowly from the sides).
- `drain_hull_on_disassembly = false` → after disassembly the hold is full of water.

## What to send back
- Screenshots A, B, C, and one of any message that looks wrong.
- From `logs/latest.log`: every line containing `pirates_n_ships`, `Sable` with `ERROR` or `WARN`
  (except the flywheel one), `Failed to move block`, `Failed to mark & notify`, and any stack trace.
- Whether steps 5 and 9 felt right for the player position (any jump, fall damage, suffocation).
