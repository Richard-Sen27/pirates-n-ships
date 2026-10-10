# Playtest: pirate islands (work package WG2)

The generalised anchor maths, the treasure marker under rotations, the port codec with treasure sites, the spawn rule
and the island's worldgen JSON are covered by JUnit tests. GameTests place the camp (with its jetty, two paths and a
treasure spot) on a hand-built beach facing north and check the beach edge, the jetty deck height, both berths in the
water, the buried chest with its loot table under sand, the port of kind `pirate_island` with one treasure site, the
fence's bound desk, the pirate spawn overrides and spawn rule, and the `enabled` toggle. Only a real world shows where
camps land, how they meet natural terrain, and whether pirates actually turn up.

Setup: `./gradlew :neoforge:runClient`, a **new** survival world on Normal difficulty (default world type; camps only
generate in new chunks; pirates are `monster` spawns, so not on Peaceful), cheats on. Please send `latest.log` and
screenshots, and note the world seed.

## Steps

1. **Find one.** `/locate structure pirates_n_ships:pirate_island`, then `/tp` there (a y above the ground, e.g. 80).
   - **Expected:** a pirate camp on a beach. Camps are rarer than villages: the structure set spaces them about 64
     chunks apart (separation 24), only 80 % of the possible spots are used (`frequency` 0.8), only beaches qualify
     (`#minecraft:is_beach`), and the sea must be within 12 blocks. If `/locate` reports nothing, try another seed.
2. **Facing the sea.** Look at the camp from above.
   - **Expected:** the sandy clearing's beach edge (the side with the jetty) is on the last row of land, the jetty runs
     straight out over the water from the middle of that edge; the campfire, the loot heap and the fence's lean-to shack
     are on the land side. The camp's sand is one block above the sea surface.
3. **Jolly Roger.** Look at the flagpole in the camp.
   - **Expected:** a five-block pole with the Jolly Roger at the top, flying over open sand (not clipping into the
     shack).
4. **Jetty and berths.** Walk out on the jetty.
   - **Expected:** the plank deck is level with the camp's sand, one block above the water; about halfway out, on both
     sides, the water beside the deck is open (the two berths). No jigsaw blocks anywhere in the camp.
5. **Paths and huts.** Walk the paths inland.
   - **Expected:** gravel and dirt trails on the sand between palisade stakes, ending at a row of stakes; tents, a
     tavern hut and maybe the captain's hut on stilts beside them, standing on the ground (not floating one block up,
     not buried). Report huts that overlap each other or a path.
6. **Pirates about.** Stay near the camp (but not in its middle: vanilla spawns no mob within 24 blocks of a player)
   for a minute or two, in daylight.
   - **Expected:** pirates appear inside the camp's pieces (on the sand, paths and hut floors, not on the water or the
     jetty's water side), alone or in groups of up to three, and attack you (hostile by default). They stop at about 8
     within 32 blocks (`world.structures.pirate_island.max_pirates`). No navy and no sailors spawn there. No zombies
     or skeletons spawn inside the pieces at night (the island's overrides replace the monster list there).
7. **The fence.** Right-click the harbor desk in the fence's shack.
   - **Expected:** a market screen opens. Its prices are the fence's: few goods, rum, cloth and luxuries in demand
     (high sell prices), little offered. The notice board on the shack's south side opens the bounty board as anywhere.
8. **Port registry.** Stand in the camp, run `/pirates world port nearest`.
   - **Expected:** `pirates_n_ships:pirate_island_<x>_<z> (pirate_island, <climate>) ...` with two berths, and one line
     `Treasure at <x>, <y>, <z> (buried)` per treasure spot in this camp (none if the layout drew no treasure spot: the
     huts pool gives it one chance in six per hut slot; try another camp then).
9. **Dig up the treasure.** Go to the treasure coordinates: two stripped logs crossed on the sand, a skull and dead
   bushes. Dig straight down at the cross's centre.
   - **Expected:** one block of sand, then a chest two blocks under the surface. Open it: 3 to 5 stacks from doubloons
     (8-24), rum (1-3), salt pork, iron ingots, an emerald or two, lead shot, rarely a pistol, very rarely kraken ink.
     The `(buried)` flag stays (looting is recorded by the later treasure-map feature).
10. **Config off.** Stop the game, set `world.structures.pirate_island.enabled = false` in the server config
    (`pirates_n_ships-server.toml` of the world), start again and fly into new, unexplored coast.
    - **Expected:** no new camps generate; the existing one stays. Then set `enabled = true` and
      `buried_treasure = false`: new camps have plain sand under the log cross and no treasure line in
      `/pirates world port nearest`. `max_pirates = 0` (or `world.spawn_weights.pirate = 0`) stops new pirates.

## Open questions for the human

- Do camps land on narrow beaches often enough with spacing 64, or do we need a lower spacing?
- Huts hung from terrain-following paths sit on the surface row; on steep beaches they may stand in the slope.
- The jetty's posts stop five blocks under the deck, as the village pier's do: on a deep seabed they hang in the water.

## STR2: natural layout

Headless, `StructureLayoutGameTests` plans six islands through the real jigsaw and checks each is not a line (footprint
aspect at least 0.5), has at least 10 pieces and 4 distinct buildings and its jetty's far end over the water, and
places three of them on a flat test beach; the renders `art/renders/structures/pirate_island_{11,2024,4242}_{top,iso}.png`
show those three. Real terrain, the beard (terrain adaptation) and the land filter on a real coastline only show in a
world. Setup as above (a **new** world; old camps keep their old layout), cheats on, ideally a few F2 screenshots from
high above (`/tp ~ ~40 ~`, look down) and from the side.

1. **Three islands.** `/locate structure pirates_n_ships:pirate_island`, `/tp` there, look at it from above; then find
   two more (`/tp` a few thousand blocks along the coast, `/locate` again).
   - **Expected:** each camp starts at the beach with the palisaded landing and the jetty running out into the water;
     from it trails of gravel, trodden dirt and sand with pale driftwood edges branch along the beach both ways and
     climb inland, bending, forking and ending in clearings or at a palm or rocks. No camp is one straight row; the
     three look different (layout and buildings).
2. **Buildings.** Walk the trails.
   - **Expected:** a mix from tents (four looks: the striped A-frame, a small grey pup tent, a big open mess tent, a
     sail lean-to), huts (on stilts, with a deep porch, a steep thatch), taverns (the open hut, the long hall with the
     hanging sign "The Rusty Cutlass", the deck tavern with "The Drunken Kraken"), captain's huts (the stilt hut, one
     with a lookout platform, a cabin in a beached stern), a lookout tower, a smokehouse with fish racks, a stone powder
     store, a beached longboat with nets, treasure under crossed logs, by a cairn or among boat ribs; between them
     decorations (fire rings, crates, a hammock between two palms, rocks, washing lines, rum casks, skull posts, a Jolly
     Roger, palms). Every door faces its trail; hanging signs show their text.
3. **Huts on slopes.** Find a camp that climbs a slope or a dune.
   - **Expected:** huts stand on levelled ground with the terrain eased round them (the beard), not floating with air
     under a corner and not half buried; trails follow the ground. Report a hut that floats or sinks, with coordinates.
4. **The jetty in the water.** Walk out on the jetty, look down at the berths.
   - **Expected:** the jetty's end stands in open water and both berths are water (`/pirates world port nearest` lists
     two). The beard may have shaped the shore and the seabed round the jetty (a dip in the beach beside it, about
     three blocks of water at the berths): report if the jetty stands on a sand bar or the berths are dry.
5. **Nothing out at sea.** Look along the shoreline on both sides of the camp.
   - **Expected:** no trail, hut or decoration out over the open sea (the land filter drops those); a trail may cross a
     narrow inlet on spruce boards. Report anything floating on the water.
6. **Captain and crew.** `/pirates mob captain list`, walk to the island's captain.
   - **Expected:** the captain stands in the room of the camp's captain's hut (any of the three: the stilt hut, the
     one with the lookout, the beached stern), facing the door; without one, on the camp's inland trail. Pirates spawn
     in the camp's pieces as in step 6 above.

Open questions: are the camps the right size (16 to 81 pieces across 60 seeds, median 41, often 80 to 100 blocks
across)? Are three treasure looks per island too generous (about one building slot in ten)?
