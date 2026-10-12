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
   Since WG5 `spacing` and `separation` are read when new chunks generate: see "WG5" in `world-simulation.md`.
11. **Restart.** Save and quit, reopen the world, `/pirates world ports`.
   - **Expected:** the same ports with their berths; the desk still opens the market.

## Open problems to watch

- Piers on deep seabeds: the piles stop at the pier's y 0 (four blocks below the deck), so their footings hang in the
  water; on very shallow beaches the berths may be too shallow for a ship's hull.
- The quay is built at sea level + 1 regardless of the land behind it; sites whose ground is more than 4 blocks above
  the sea are skipped (`max_shore_height`).

## STR3: natural layout

Headless, `VillageLayoutGameTests` plans six villages through the real jigsaw and checks each is not a line (footprint
aspect at least 0.5), has at least 10 pieces and 4 distinct buildings and its pier's far end over the water, checks
with vanilla's beardifier that the beard leaves the berths' water alone, and places three of them on a flat test beach
(port, berths, the harbor master at the desk); the renders
`art/renders/structures/seafarer_village_{7,1234,5150}_{top,iso}.png` show those three, and
`seafarer_village_smoke_{top,iso}.png` the village of the NeoForge smoke world. Real terrain, the beard and the land
filter on a real coastline only show in a world. Setup as above (a **new** world; old villages keep their old layout),
cheats on, a few F2 screenshots from high above (`/tp ~ ~40 ~`, look down) and from the side.

1. **Three villages.** `/locate structure pirates_n_ships:seafarer_village`, `/tp` there, look at it from above; then
   find two more (`/tp` a few thousand blocks along the coast, `/locate` again).
   - **Expected:** each village starts at the dock head on the shore with the pier running out into the water; the
     street leaves the dock head inland and branches at once (a fork or a crossroads), then bends, forks, crosses,
     opens into a square with a well or a market stall and ends at a turning place (a kerbed end with benches, a
     little green with an oak, a loop round a flower bed). No village is one straight street; the three look different
     (layout and buildings).
2. **Buildings.** Walk the streets.
   - **Expected:** a mix of houses (the small cottage, a tall two-storey house, a cottage with a fenced vegetable
     garden behind it, a fisherman's cottage with a boat on trestles and a net), taverns (the big tavern, the inn with
     the hanging sign "The Salty Anchor"), shipwrights (the shed, an open slipway with a hull frame), a warehouse with a
     hoist, a net loft on posts, a fish market stall ("Fresh Fish"), a beacon tower, a chapel with a bell, a well;
     between them decorations (carts, barrels, nets, a rowing boat, flower beds, a bench under a tree, lantern posts,
     fish racks, woodpiles, laundry lines). Every door faces its street; hanging signs show their text; a building
     never stands in a street.
3. **Houses on slopes.** Find a village that climbs a slope.
   - **Expected:** buildings stand on levelled ground with the terrain eased round them (the beard), not floating with
     air under a corner and not half buried; streets follow the ground. Report a building that floats or sinks, with
     coordinates.
4. **The pier in the water.** Walk out on the pier, look down at the berths.
   - **Expected:** the pier's end stands in open water and both berths are water (`/pirates world port nearest` lists
     two), with no sand bar raised under the pier. A building on the beach beside the dock head may have raised the
     shore a little right in front of the quay: report it with a screenshot if it reaches the pier.
5. **The harbor master at the dock head.** Go into the hut on the quay.
   - **Expected:** the harbor master stands behind his desk facing the door; the desk opens the port's market.
6. **Nothing out at sea.** Look along the shoreline on both sides of the village.
   - **Expected:** no street, building or decoration out over the open sea (the land filter drops those); a street may
     cross a narrow inlet as a cobbled causeway with plank verges. Report anything floating on the water.

Open questions: are the villages the right size (14 to 72 pieces across 100 seeds, median 39, 70 to 130 blocks
across)? Is a beacon tower in most villages too many?

## STR7: room for ships at ports

Headless, `ShipSpotTest` (JUnit) pins the spot rule, `BerthDepthGameTests` places a village on a sloped beach (the
pier's end in five blocks of water) and finds one extension section, four berths, every berth's mooring deep for the
largest sloop after the carve, the piles on the carved floor and an ordered ship at berth 1; `ShipSpotGameTests` sends
a pickup from a shallow berth into the deep water and refuses one with no deep water; `MaterializeGameTests` puts an
NPC ship leaving a shallow berth in deep water. The look of the extension pieces, the carved basin round the berths on
real coastlines, and the ships at real berths only show in a world. Use a **new** world for steps 1 to 4 and an
**old** one (generated before this version) for step 5; cheats on, F2 screenshots and the chat lines.

1. **A shallow beach.** `/locate structure pirates_n_ships:seafarer_village` a few times until a village's pier ends in
   shallow water (you can see the sand from the deck), `/tp` there.
   - **Expected:** the pier runs on by one or two extension sections (the same plank deck, level with the pier, on
     taller piles, a lantern post at each corner of the end and a ladder down; the pier's own end rail is gone so you
     walk straight on). `/pirates world port nearest` lists two berths per section more. Report a section that floats,
     cuts into a sand bar above the water, or stands crooked against the pier.
2. **Deep water at the berths.** Swim along the pier on both sides and dive at each berth.
   - **Expected:** beside every berth the floor lies six blocks below the surface over a long basin (about 13 blocks
     out from the deck and 50 along it), the piles reach down to it, no kelp in it; the beach, the quay and the
     buildings are untouched. Report a hole in the beach or a cut into land above the sea.
3. **Ordering a ship.** Order the starter sloop at the harbor master's desk, finish it with
   `/pirates ship orders finish <id>`, use the desk with the receipt.
   - **Expected:** she appears alongside berth 1 on the side away from the pier with one water column between, afloat,
     not touching sand, the pier not gathered into her. Order and pick up two more: the second goes to the next berth,
     the third farther along or out (the chat names the berth it started from). `/pirates port spot
     pirates_n_ships:starter_sloop` before each pickup names the place she will take.
4. **NPC ships.** `/pirates world voyages spawn convoy <this village> <another port>` (port ids from
   `/pirates world ports`; a lane must exist), stand on the pier.
   - **Expected:** an NPC ship that appears at the port lies in deep water off the pier, never on the beach or the
     sand by the berth.
5. **An old port.** In a world from before this version, go to a village whose berths are shallow and pick up an
   ordered ship (or redeem a commission at an outpost).
   - **Expected:** the ship appears in the nearest deep water off the pier (maybe turned across it), not on the sand.
     Where there is no deep water within 64 blocks, the action bar says "No deep water near the pier for her" and the
     receipt stays in your hand.
