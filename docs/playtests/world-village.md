# Playtest: seafarer villages at the coast (work package WG1)

The shore probe, rotation, berth extraction, climate mapping, port registry and the worldgen JSON are covered by JUnit
tests. GameTests place the village (dock head, pier, a street and its end) on a hand-built beach facing north and east
and check the quay edge, the pier deck height, both berths in the water, the port with two berths, the bound desk and
the `enabled` toggle. Only a real world shows where villages actually land, how they meet natural terrain and whether
the buildings collide.

Setup: `./gradlew :neoforge:runClient`, a **new** creative world (default world type; villages only generate in new
chunks), cheats on. Please send `latest.log` and screenshots, and note the world seed.

## Steps

1. **Find one.** `/locate structure pirates_n_ships:seafarer_village`, then `/tp` to the coordinates (pick a y above
   the ground, e.g. 80).
   - **Expected:** a village on a beach. If `/locate` finds nothing nearby, try a second seed or fly along a coast; the
     structure set spaces villages about 36 chunks apart (separation 12) and only beaches qualify
     (`#minecraft:is_beach`), and a site must have the sea within 12 blocks.
2. **Shore and quay.** Look at the dock head from above.
   - **Expected:** the stone quay's sea edge sits on the last row of land with the harbor master's hut on the land side;
     the pier runs straight out over the water from the quay's middle. The quay paving is one block above the sea
     surface (stand on it: the water surface is one block below your feet at the quay edge).
3. **Pier and berths.** Walk out on the pier.
   - **Expected:** the plank deck is level with the quay paving, one block above the water; piles go down into the
     water (on a shallow seabed their footings stand in the sand, on a deep one they hang in the water, see open
     problems). Beside the deck, about halfway out, on both sides, the water is open: these are the two berths (the
     jigsaw markers must be gone, replaced by water; no jigsaw blocks anywhere in the village).
4. **Port registry.** `/pirates world ports`.
   - **Expected:** a line `pirates_n_ships:village_<x>_<z> (seafarer_village, <climate>) in minecraft:overworld at
     <centre>, 2 berths [...]`; the climate is `temperate` on a normal beach, `cold` on a snowy beach.
   `/pirates world port nearest` (standing in the village).
   - **Expected:** distance a few blocks, the same port, and two "Berth at x, y, z, bow <dir>" lines. Fly to each berth
     position: it is the water column right beside the deck, at sea level, and `bow` points out to sea (the direction
     the pier runs).
5. **Harbor desk.** Use the harbor master's desk in the hut.
   - **Expected:** the market screen opens for that port (no "not bound" message). Break the desk and place it again
     inside the village: it binds again (the market opens). Place a desk far outside the village: "not bound".
6. **Notice board.** Next to the hut's door.
   - **Expected:** present and usable as before (no new behaviour in WG1).
7. **A rotated village.** Find villages whose sea lies east, south or west (`/locate` again from far away, or another
   seed).
   - **Expected:** the whole layout turns: the pier always points to the open sea, the hut's door and the street lead
     inland.
8. **Terrain seams.** Walk the streets and look at the buildings.
   - **Expected (known limits):** the village uses terrain adaptation `none` (see open problems), so streets follow
     the ground (cobbles one per column on the surface), while houses, the tavern and the shipwright stand level with
     their floor row on the surface at their door. On slopes a building may sit in the hill on one side or on a step on
     the other; the dock head may stand one step above the beach behind it. Please screenshot the worst seams.
9. **Collisions.** Count buildings along the streets.
   - **Expected:** some building slots stay empty, especially next to the tavern and the shipwright (11 wide against a
     7-long street): vanilla's jigsaw drops a piece that would overlap another. Every street ends in a small cobbled
     place with a lantern post and two barrels (the street end).
10. **Toggle.** In the server config set `world.structures.seafarer_village.enabled = false`, restart, fly to an
   unexplored coast.
   - **Expected:** no new villages; existing ones stay. Set `frequency = 0.5`: about half as many new villages.
   Changing `spacing` or `separation` in the config does nothing (they are datapack values, see the comments).
11. **Restart.** Save and quit, reopen the world, `/pirates world ports`.
   - **Expected:** the same ports with their berths; the desk still opens the market.

## Open problems to watch

- Piers on deep seabeds: the piles stop at the pier's y 0 (four blocks below the deck), so their footings hang in the
  water; on very shallow beaches the berths may be too shallow for a ship's hull.
- The quay is built at sea level + 1 regardless of the land behind it; sites whose ground is more than 4 blocks above
  the sea are skipped (`max_shore_height`).
