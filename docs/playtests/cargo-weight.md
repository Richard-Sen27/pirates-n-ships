# Playtest: cargo weight on ships (work package CW1)

The rules (the `load` state per fill level, the Sable mass per state, the vanilla containers' downward force, the load
level thresholds, the config toggle) are covered by JUnit and GameTests: a full crate on a test ship adds 20.48 kpg to
Sable's mass, a chest of iron blocks in a hold corner makes a small test hull sit about 0.8 blocks lower and heel
toward the chest, `/pirates ship info` reports the load level, and with the toggle off nothing changes. This checklist
covers what only a real ship in the game shows: the waterline, the trim, the overlay word and how a laden ship feels.

**How it works, in short.** Our containers (cargo crate, cargo barrel, pantry, water barrel) carry their content as
Sable mass through a hidden `load` block state 0..4 (empty, quarter, half, three quarters, full); a full crate weighs
about 21 kpg instead of 0.5 (a plank block). Vanilla containers (chests, barrels, shulker boxes, hoppers, the sea
chest) get a downward force at their position instead, recomputed every 40 ticks (2 s). The load level is the cargo
weight over the ship's block count × 8: the starter sloop (680 blocks) is **laden** from about 1,800 units, **heavily
laden** from about 4,100 and **overloaded** above 5,440. A crate of sugar is 512 units, a crate of iron ingots 2,048,
a chest of iron blocks 432 (non-trade items weigh 0.25 each).

Setup: `./gradlew :neoforge:runClient`, a creative world with cheats on, default config (server section
`cargo_trade`). Place a starter sloop at sea level facing open sea with `/pirates ship place starter_sloop assemble`
(or build one and assemble it at the helm). Turn on Sable's force display if you can (`/sable` debug, see docs/sable-notes.md §9.2) to
see the new "Cargo" force group. Please send screenshots of steps 1, 3, 4 and 6 from the same spot beside the ship (a
block on the shore or a pillar as a mark for the waterline), and `latest.log` if anything goes wrong.

## Steps

1. **Empty sloop.** Look at the sloop from the side; note where the water meets the hull at bow, midships and stern.
   Run `/pirates ship info` standing on deck.
   - **Expected:** the info shows the ship line and **"Load: Light (cargo 0.0 of 5440 capacity)"** (the numbers
     depend on the block count of your sloop; a few hundred units of provisions in the water barrels are fine).
2. **The crates look the same.** Fill one cargo crate with sugar: use a stack of sugar on it once, then sneak-use it
   with an empty hand to pour in all the sugar from your inventory, until it says 2,048 / 2,048.
   - **Expected:** the crate's model does not change at all, no flicker, no missing texture. Breaking and replacing it
     keeps the cargo as before.
3. **Load the hold.** Fill all three crates with iron ingots (2,048 each) and the three cargo barrels with anything
   stackable (1,536 each). Wait 2 seconds, run `/pirates ship info` again, and look at the waterline from your mark.
   - **Expected:** the sloop sits **clearly lower**, roughly half a block to a block (please estimate). If the
     containers are not centred, it trims toward them (bow or stern lower). The info says **"Heavily laden"** or
     **"Overloaded"**.
4. **A heavy chest in the bow.** Place a vanilla chest as far forward as you can (in the bow, on or below deck) and fill
   it with 27 stacks of iron blocks (432 units).
   - **Expected:** within about 2 seconds the bow goes **down** a little compared with step 3 (the stern up). In Sable's
     force display a "Cargo" arrow points straight down at the chest. Moving the chest to the stern trims it the other
     way.
5. **The load word at the helm.** Grab the wheel (hold right-click on it) and turn a little.
   - **Expected:** the overlay above the action bar reads e.g. **"Rudder 12° starboard · Overloaded"** (or "· Laden",
     "· Heavily laden", "· Light"). Take cargo out until the level changes and grab the wheel again (or keep steering):
     the word follows within about 2 seconds. With `helm_view.show_rudder_angle = false` (client config) the whole
     line is hidden.
6. **Overloaded behaviour.** With everything full, set sail downwind and then turn.
   - **Expected:** the ship accelerates and turns more sluggishly than empty (more mass, more submerged hull), and it
     must not sink by itself just from the load. Tell us how it feels: too little effect, about right, or too much.
     `cargo_trade.weight_factor` scales the whole effect (2 = twice as heavy); if the sloop sinks, note the factor.
7. **Config off.** Set `cargo_trade.cargo_weight_affects_ships = false` (server config, the in-game config screen or the
   file), wait about 5 seconds (the crates recheck every 5 s).
   - **Expected:** the sloop rises back to its empty waterline from step 1, the bow chest no longer trims it, the
     "Cargo" force arrow disappears. `/pirates ship info` still shows the load level (it is information only). Turn it
     back on: the ship settles low again.
8. **Save and reload.** With the ship loaded, leave the world and rejoin.
   - **Expected:** the ship comes back at the same laden waterline; no crate lost its cargo.

## Known limits

- A container heavier than its "full" weight counts as full: a crate of iron ingots (2,048 units) weighs the same as a
  crate holding 1,024 units (21 kpg). Vanilla chests have no such cap.
- The water barrel's water changes its mass only a little (16 rations = 16 units = 0.32 kpg on top of its 2.0), and a
  water barrel takes a config change only when its water changes next.
- Loose heavy blocks and items (cannons, a cannonball pile on deck) are not cargo; cannons already have their own mass.
