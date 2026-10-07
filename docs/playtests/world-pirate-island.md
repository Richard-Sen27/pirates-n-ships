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
