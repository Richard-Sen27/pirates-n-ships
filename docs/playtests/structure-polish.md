# Playtest: structure polish (work package STR6)

The human, 2026-10-12: "Remove blocks in front of doors at generated structures (EVERY single structure has that
problem). Replace half slabs at Leuchtturm with full blocks. Add loot. navy_post directly next to mansion, should not
happen."

Headless coverage: `tools/check_structures.py` checks both sides of every door in every structure piece (JUnit runs
it in `./gradlew build`); the layout GameTests place three islands, villages and outposts on a flat beach and check
every placed door (`DoorAccess`); `StructureLootGameTests` checks every loot chest's table, opens placed chests and
checks the `loot` toggle and the `loot_rolls` scale; `SpreadPlacementGameTests` checks the exclusion zones over a
beach-only world. A real world is still needed for terrain, vegetation and how far apart ports really are.

Setup: `./gradlew :neoforge:runClient` (and once on Fabric, `./gradlew :fabric:runClient`), a **new** creative world
(structures change only in new chunks), cheats on. Please send `latest.log`, screenshots and the seed.

## STR6

1. **Doors in a village.** `/locate structure pirates_n_ships:seafarer_village`, `/tp` there. Walk up to every door
   (houses, tavern, inn, harbor master's hut, chapel, lighthouse, net loft, warehouse) from the street and walk through
   it into the room.
   - **Expected:** nothing stands on the cell in front of a door or right behind it at foot or head height: no lantern
     post, barrel, fence, kerb, terrain block or wall. The tavern's upper floor has a tall window over the gallery rail
     (no door onto the rail any more).
   - Report any door you cannot walk through with a screenshot and the F3 coordinates of the door.
2. **Doors in an outpost.** `/locate structure pirates_n_ships:navy_outpost`, `/tp`. Walk through the office door,
   every tower door (ground floor and both walkway doors), the barracks, brig (also each cell door inside), armoury,
   store house, officers' quarters, chapel and the privy at a lane's end.
   - **Expected:** every door walkable from both sides. The privy is a two-deep booth now (you can step inside); the
     large brig's first cell door is clear (no ledge block at head height either side); the officers' quarters have a
     tall window over the balcony rail instead of a door onto it.
3. **Doors on a pirate island.** `/locate structure pirates_n_ships:pirate_island`, `/tp`. Walk into every hut, tent,
   tavern, the captain's hut (up the porch steps), the powder store and the smokehouse.
   - **Expected:** every doorway walkable; a hut whose door sits on its box edge (the thatched hut) opens onto the
     trail, not onto a stake, crate or terrain block.
4. **The lighthouse.** Find a village with a beacon tower (pool weight 2: try two or three villages). Look at the
   gallery from below and from the side.
   - **Expected:** the gallery round the top is a course of full stone bricks on the stair corbels, no half slabs; it
     reads as part of the tower, not a thin plate floating on it. The lantern room is glazed all round (the old door
     into the light is a pane now).
5. **Loot.** Open a chest in each kind of building.
   - **Expected:** village house, warehouse, chapel: household goods and food (bread, salted fish, apples, potatoes,
     string, wool, candles, cloth, limes), sometimes 1-4 doubloons. Tavern and inn: rum, bread, cooked cod, salt pork,
     hardtack, tobacco, always 2-6 doubloons. Shipwright and net loft: nails, rope, cloth, planks, iron, now and then a
     carpenter's hammer, saw or toolkit. Outpost barracks, brig, officers' quarters, office: hardtack, lead shot,
     gunpowder, salt pork, limes, rarely a cutlass or pistol. Armoury: shot, powder, cannonballs, iron, cutlass, saber,
     pistol, musket. Store house and tower landings: rations, limes, powder, shot, rum, rope. Island tents and huts:
     rum, fish, tobacco, gold nuggets, shot, spices, an emerald now and then, often doubloons. Island taverns: rum,
     tobacco, food, spices, gold nuggets, 2-8 doubloons. Captain's hut (vanilla chest beside the sea chest): a blank
     treasure map, 6-16 doubloons, a cutlass, pistol, saber or rapier, and a little more. The captain's sea chest stays
     empty.
6. **Loot toggle.** In the server config set `world.structures.loot = false`, restart, find a **new** village (fly to
   fresh chunks).
   - **Expected:** its chests are empty. Set it back to `true` and `world.structures.loot_rolls = 3.0`, restart, find
     another new village: its chests are clearly fuller (about three times the items).
7. **No outpost beside a village.** Find a few villages and outposts (`/locate` from several places, or fly a coast).
   - **Expected:** no navy outpost starts within 8 chunks (128 blocks) of a village's start, so the fort's walls never
     stand against the village's houses; no pirate camp within 6 chunks of either. Report the coordinates of any pair
     that still touches.

## Open problems to watch

- The exclusion zone counts every possible village start chunk, also where no village could generate (no beach), so
  some outposts are skipped beside an empty coast: outposts are about a third rarer, islands about a quarter.
- Terrain (a hillside, a tree, tall grass) can still fill a door's approach where the beard leaves it; the templates
  write no air (the converter drops it so pieces never carve the sea).
