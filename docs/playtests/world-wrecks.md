# Playtest: wrecks on the ocean floor (work package WK1)

The piece choice by water depth and weight, the y rule and the worldgen JSON are covered by JUnit tests. GameTests
place each of the four pieces (and one chosen by the structure's own planning) on a hand-built sand floor under 13
blocks of water and check that the seabed row replaces the floor's top block, the piece stays under the surface, its
box holds no air, every waterlogged block of the template is still waterlogged, and every chest carries the
`pirates_n_ships:chests/wreck` loot table; with 5 blocks of water only the cargo field is chosen, with 3 none; with
`enabled = false` there is no generation point. Only a real world shows where wrecks land on natural, uneven seabeds,
how they meet kelp and seagrass, and whether they look right.

Setup: `./gradlew :neoforge:runClient`, a **new** creative world (default world type; wrecks only generate in new
chunks), cheats on. Please send `latest.log` and screenshots, and note the world seed. Night vision and water
breathing help: `/effect give @s minecraft:night_vision infinite` and `/effect give @s minecraft:water_breathing infinite`.

## Steps

1. **Find one.** Out at sea, `/locate structure pirates_n_ships:wreck`, then `/tp` to the coordinates (y about 70) and
   dive down.
   - **Expected:** a wreck on the seabed: the sunken sloop (a heeled hull broken amidships), a cargo field (crates,
     barrels, a cannon and a chest scattered in a ragged bed), a mast stump (a mast with its yard standing up from a
     broken deck square, a lantern on a short chain under the yard) or a stern (a half-buried transom with cabin
     windows). Wrecks are spaced about 24 chunks apart (separation 8) in every ocean and deep ocean biome; `/locate`
     should find one within a few hundred blocks of any ocean.
2. **On the floor.** Look at the piece's base from the side.
   - **Expected:** the piece's own sand/gravel bed is level with the surrounding seabed (it replaced the floor's top
     row); nothing floats over the floor at the centre of the piece. On a sloping seabed one side may stand a little
     in the slope or above it (only the centre column is measured), but never more than a block or two.
3. **Water everywhere.** Swim through and into the piece (into the sloop's hold and cabin, the stern's cabin).
   - **Expected:** no air pockets anywhere: every gap, the hull's inside included, is water; stairs, slabs, fences,
     panes, chains, lanterns and chests show water inside them (no dry bubble around them). Nothing sticks out above
     the sea surface.
4. **Loot.** Open the chest of the wreck.
   - **Expected:** loot from the wreck table: 3-12 doubloons plus two to four of rum, salted fish, rope, nails, lead
     shot (8-16) and rarely a cutlass (kraken ink in about one chest of 40). Find a second wreck of a different kind
     and open its chest too.
   - **Known:** the sea chest in the sunken sloop's forward hold is **empty** (it is a plain container without a loot
     table, ST5).
5. **Rotations and depths.** Visit three or four wrecks (`/locate` again from about 30 chunks away).
   - **Expected:** they face different directions; tall pieces (mast stump, sloop) appear only in deeper water, and
     in shallow water (fewer than 8 blocks over the floor) only cargo fields appear. No wreck reaches the surface.
6. **Kelp and seagrass.** Look around the wreck.
   - **Expected:** kelp and seagrass of the biome grow around the piece (they may also grow up through open gaps in
     the hull; that is fine).
7. **`/place` works.** Over deep water (`/tp` to an ocean, y 70), run `/place structure pirates_n_ships:wreck`.
   - **Expected:** a wreck is built on the seabed under you (in water shallower than 4 blocks: "Failed to place
     structure").
8. **Config off.** Stop the world. In `serverconfig/pirates_n_ships-server.toml` of the world (or through the config
   screen) set `world.structures.wreck.enabled = false`, load the world again, fly to unexplored ocean (far from where
   you were) and run `/locate structure pirates_n_ships:wreck`.
   - **Expected:** wrecks generated earlier are still there. `/locate` names no new wreck in unexplored ocean (it runs
     the structure's own check, which is off; it may still find the old ones you generated before), flying over fresh
     ocean shows none, and `/place structure pirates_n_ships:wreck` fails. Set `enabled` back to `true` afterwards.
9. **Frequency.** Set `world.structures.wreck.frequency = 0.0`, reload, fly to fresh ocean.
   - **Expected:** as with `enabled = false`; with `0.5` roughly every second possible site has a wreck.

## Report

Screenshots of each kind of wreck from outside and from inside (sloop hold), the chest's loot, a sloped-floor case if
you find one, and `latest.log` (search for `wreck` and any `ERROR`).
