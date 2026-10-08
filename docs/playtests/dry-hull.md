# Playtest: the dry hull view (work package HV1)

Two bugs the human saw in game (design.md §4.4, "Dry hull view (HV1)"): the underwater overlay while climbing through
a deck trapdoor, and seagrass and kelp of the world showing inside a dry hold. The region rule (the deck hatch is in
the dry region while the air above it is above the sea) is covered by GameTests; the plant culling is client rendering
and has only JUnit tests for its math, so it has never been seen in game.

Setup: `./gradlew :neoforge:runClient`, a creative world, the starter sloop (`/pirates ship place` on deep water near
a seagrass and kelp bottom, or any ship with a hold below the waterline and a trapdoor in the deck over the hold).
Please send `latest.log` if anything differs. For the cost numbers, run the client with debug logging for our mod
(the log line starts with "Hidden water plants").

## Steps
1. **Deck trapdoor, every camera height.** Moor the ship, open the deck trapdoor and climb down the ladder slowly,
   stopping every few pixels (sneak). Then climb up again. Expected: no blue underwater tint, no fog and no water
   overlay at any height, also with the head exactly in the trapdoor cell. Repeat in third person (F5).
2. **A trapdoor hung under the deck.** If you have one (a trapdoor at the top of the hold under a deck opening), do the
   same. Expected: the same, no overlay in the opening either.
3. **The sea beside the hull.** Swim beside the hull below the waterline. Expected: you are in water (swimming, fog,
   overlay), right up to the planks; no dry pocket next to slabs or stairs in the outer hull.
4. **Moored over seagrass and kelp.** Moor the ship over a seagrass meadow or a kelp forest so plants reach up into the
   hold's volume in the world. Go below deck. Expected: no seagrass, kelp, sea pickles or bubble columns inside the
   hold; outside the hull (look through an open door or porthole, or swim around) the plants still stand.
5. **Sail at speed over a kelp forest.** Sail at full speed over kelp. Expected: plants that the hold passes over
   vanish inside within a few ticks (at the leading edge of the hold you may see a plant for a moment), and the plants
   behind the ship come back. No stutter compared with sailing over plain sea. With debug logging, note the
   "sections re-marked" number in the log line (every 10 s).
6. **Toggle off.** Set `dry_hull_view.hide_water_plants = false` in the client config (`pirates_n_ships-client.toml`,
   or the config screen). Expected: within a fraction of a second the plants show inside the hold again; set it back
   to true and they vanish again.
7. **Flooding.** Breach the hold below the waterline and let it fill. Expected: as the dry region shrinks, the
   plants in the flooded part come back (they stand in real water now).
