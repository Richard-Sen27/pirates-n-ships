# Playtest: structure polish (work packages STR6, STR8, PIER1)

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

## STR8

The human, 2026-10-12, after the first look at generated ports: "piers need to end in water, not on land. the paths
are often very curly because of the original terrain, though a little bit is not bad, but now it is too much.
entrances of sub structures are still blocked with one or multiple blocks. something like this should not happen
either: a structure completely on water, without proper land below it. also, don't create the structures right in ice
biomes, that doesn't fit and doesn't use anything, though a rare chance for that would still be good." His screenshots
added: the village streets ride every bump (the kerb steps up and down), snow-topped ground stands before hut doors,
an outpost wall run goes out over the sea with its end tower in the water, a navy post beside a woodland mansion.

Headless coverage: `PortSiteGameTests` (a sandbar under the pier, an inlet beside a camp, a headland beside a fort, a
bumpy rising shore, a snowy beach), `PortTerrainGameTests` (every port piece with a door on raised snowy ground; a
village, an island and an outpost placed on a bumpy snowy shore: every trail level, every door walkable),
`SpreadPlacementGameTests` (the cold sets share their kind's grid; no port near a vanilla village or mansion),
`PortShoresDataTest` (JUnit over the generated tags, sets, processor lists and the straights pools). Use a **new**
world.

1. **Piers in the sea.** Find three or four villages, islands and outposts (`/locate structure
   pirates_n_ships:seafarer_village`, `pirate_island`, `navy_outpost`; or fly a coast).
   - **Expected:** every pier, jetty and quay runs out over open water and its end (and the extension sections, if
     any) stands in water with at least four blocks of sea beyond it; none ends on a sandbar, a spit or the far shore
     of a bay. Sites that do not allow it are skipped (the server log counts them: `STR8: ... site ... rejected`).
2. **Level streets.** Walk the village's streets, the island's trails and the outpost's lanes on rising or uneven
   ground.
   - **Expected:** each street piece lies flat; from one piece to the next the street steps at most one block; the
     ground beside a street is cut down or filled up to it (a road on a levelled bed), no kerbs stepping every few
     blocks. Bends and forks always lead into a straight piece, so streets do not wriggle. Where the ground climbs
     steeply, the street stops instead of cutting a deep trench (more than four blocks).
3. **Doors, round two.** Walk through every door from both sides, especially huts and houses on slopes and in snow.
   - **Expected:** the two cells in front of each door (and the one beside it where a path comes in at an angle) hold
     no grass, dirt, sand, snow, leaves, barrel, lantern post or kerb; a thin snow layer on the ground is fine. Report
     any blocked door with its F3 coordinates.
4. **No piece over water.** Look at buildings near the shore.
   - **Expected:** no hut, house, tent or decoration stands with a corner over the sea or on stilts of nothing; trails
     that reached the waterline end there (a trail across a narrow inlet off its centre may still be boarded).
5. **Wall runs on land.** Find an outpost on a point or headland.
   - **Expected:** where the coast turns away, the wall run ends with a tower on land; no wall or tower stands in the
     sea (the shore bastions may still reach into the water, as designed).
6. **Not in ice biomes.** Fly a snowy coast (snowy beach, frozen ocean) and a warm coast.
   - **Expected:** ports are rare on snowy and frozen shores (about one in ten sites there, `cold_frequency` 0.1) and
     common on warm beaches. Set `world.structures.seafarer_village.cold_frequency = 0.0`, restart, fly to new snowy
     coast: no village there.
7. **No port beside vanilla villages and mansions.**
   - **Expected:** no port starts within about 6 chunks (96 blocks) of a vanilla village or 10 chunks of a woodland
     mansion.

## PIER1

The human, 2026-10-13: "the pier at navy posts is COMPLETELY solid from top to bottom, which just doesn't fit. also, the
poles at other piers could go completely down to the ground." His screenshot from under water showed the village pier's
piles ending in cobblestone footings hanging in open water.

Headless coverage: `PileFootingsGameTests` (on a built sea with the floor 8 blocks under the surface, every pier, jetty and
quay piece and each extension: every footing column reaches the floor with no water in it; the navy quay has open water
under its deck between the pillars and under the middle; on a shallow sea the pass changes nothing), plus the layout tests
(`BerthDepthGameTests`: the pier's piles reach the carved floor). Use a **new** world.

1. **Piles to the floor.** Dive beside a village pier and a pirate jetty where the sea is deeper than three or four blocks.
   - **Expected:** every pile or post runs straight down to the sea floor and ends in a cobblestone footing standing on it;
     no pile ends in open water. Also check the extension sections (where the shore is shallow).
2. **The quay on pillars.** Swim under the navy outpost's quay and its extension.
   - **Expected:** a one-block deck on stone pillars every four blocks along both edges, an edge course under the kerbs, open
     water between the pillars and under the middle, pillars reaching the floor; mooring rings, cleats, bollards, the crane and
     the ladder as before, ships still lie alongside the berths.
3. **Off switch.** `world.structures.piles_to_floor = false`, new chunks.
   - **Expected:** piles end at their template's footings as before (the quay still stands on its pillars).

## STR9

The human, 2026-10-13, after regenerating the seed of an earlier structure test: "for some reason they are A LOT
smaller now, though the steets are now a lot better than before. on height steps it would be fantastical if half slabs
could be used to make it smoother." His screenshots: an outpost with one wall run, two towers and the quay and nothing
inside; a village with one house, the net loft and the pier.

Cause (measured on the smoke seed 2718281828): the land rule on the first street, trail or lane behind the dock head,
camp or gate; on spits and beaches with a lagoon behind them it stood over water and took the whole port with it. Now
every side of a site is planned, the side that keeps at least half its port wins, and a site where none does gets no
port; huts and houses over water move inland or become a smaller one, street pieces become straight ones, wall runs end
a wall earlier with the tower on land, streets climb as terraces with half-slab steps. Headless coverage:
`RealCoastGameTests` (the smoke seed's sites on the real noise, STR8 against STR9), `PortSiteGameTests` (the rough-shore
size tests), `PortTerrainGameTests` (the slabs on placed steps). Use a **new** world, and the same seed as before.

1. **Sizes.** Regenerate the seed of the earlier test (a new world with that seed), visit the same places.
   - **Expected:** villages, camps and outposts about as large as before STR8 (a village 25 to 50 pieces: several
     houses, streets branching inland; an outpost with both wall runs where the coast allows and its court filled with
     lanes and buildings). Some sites that held a tiny port (the one-house village) now hold none; the server log says
     why (`STR9: <kind> site at x z rejected: its best side keeps K of N pieces`). Every generated port logs one line
     `STR9: <kind> at x z: planned N, kept K, dropped: ...`: please send those lines with the screenshots.
2. **Half-slab steps.** Walk the streets, trails and lanes up and down a slope.
   - **Expected:** wherever one street piece lies one block above the next, the lower piece's last row holds half
     slabs (cobblestone, stone brick or the paving's own; a sandstone slab on an island's sand), so you walk up without
     jumping; no step is two blocks high. Set `world.structures.trail_slabs = false`, restart, new chunks: full steps.
3. **Moved and swapped pieces.** Look at buildings near the water and on slopes.
   - **Expected:** no building stands over water (STR8's rule holds); now and then a house stands one to three blocks
     back from its street with a short gap (it was moved inland), or a small building (a well, a privy) stands where a
     large one would have hung over the water. Report a door that cannot be reached from its street.
4. **Ramps.** Find a port on a hillside.
   - **Expected:** the main street climbs as a terrace of one-block steps, each with slabs, cut up to about eight
     blocks into the slope or banked up to it (`max_trail_step_rows`, 8); it stops only where the slope is steeper.
5. **Toggle.** Set `world.structures.resite_pieces = false`, restart, new chunks.
   - **Expected:** STR8's behaviour (smaller ports, no re-siting), for comparison.

## Open problems to watch

- The exclusion zone counts every possible village start chunk, also where no village could generate (no beach), so
  some outposts are skipped beside an empty coast: outposts are about a third rarer, islands about a quarter.
- Terrain (a hillside, a tree, tall grass) can still fill a door's approach where the beard leaves it; the templates
  write no air (the converter drops it so pieces never carve the sea).
- STR9: a port moved one to three blocks inland leaves a strip of natural ground between its street and its door
  (the door clearing clears two cells); STR9 rejects about a third of the smoke seed's sites, so ports are rarer.
