---
navigation:
  title: "Sailing"
  parent: index.md
  position: 30
  icon: pirates_n_ships:yard
item_ids:
  - pirates_n_ships:capstan
  - pirates_n_ships:chart
  - pirates_n_ships:hammock
  - pirates_n_ships:map_tile
  - pirates_n_ships:rope
  - pirates_n_ships:sail_winch
---

# Sailing

## Wind
Each dimension has one wind: a direction and a strength of 3 to 12 blocks per second that drift slowly over time.
Rain makes it 1.5 times stronger and a thunderstorm 2.2 times, with gusts during thunderstorms. It is the same for
every player and is sent to clients (nothing displays it yet, except the flags).

## Sails
A **square sail** is built from two **yards**. A yard is a straight row of yard blocks (a thin spar; place them against
each other's ends to extend one); its middle block marks the mast column. Put a second yard with the same direction
2 to 8 blocks straight below the first, with only air or mast blocks (logs, fences) between the two middle blocks, and
the pair is one sail. The cloth is drawn between the yards, not built from blocks: furled is a roll under the upper
yard, half reaches half way down, full reaches the lower yard. Its area is the mean yard length times the distance
between the yards, so two 5-wide yards 5 apart give 25. Three yards on one mast make two sails. A yard that heads no
sail tells you so when you click it.

A **triangular (fore-and-aft) sail** is rigged with a rope and three **cleats**. Put one cleat high on the mast's side
(the head), one on the deck or a bowsprit further forward and at least 2 blocks lower (the tack), and one straight
below the head (the clew). Use a **rope** on the head cleat, then on the tack cleat (at most 16 blocks away): the rope
is used up and a stay runs between them. The cloth fills the triangle between the stay and the clew: furled is a bundle
along the stay, half reaches half way down the mast, full fills the triangle. Its area is half of head-to-tack times
head-to-clew. Click the head cleat with the empty hand to cycle its trim. Breaking the clew hides the cloth but keeps
the stay; breaking the tack drops the rope (not in creative).

| Sail | Area | Good at |
|---|---|---|
| Square sail (two yards) | yard length × distance | Wind from astern. Useless close to the wind. |
| Triangular sail (a stay and three cleats) | half of head-to-tack × head-to-clew | Wind from the side. Still drives at 45° to the wind. |

**Rope lines.** A rope used on two cleats (or a cleat and a mooring ring, or two rings) on the same ship, or both on
land, up to 16 blocks apart, makes a rope line when it is no stay: a decorative rope that sags in the middle. Each
anchor holds up to 4 ropes, so you can run a line along a railing. A rope from a high cleat down to a lower one
becomes a sail's stay as soon as a cleat sits straight below its upper end. Breaking an anchor gives the ropes back.
Server config `sailing.sails.rope_lines`, `rope_sag`.

- **Trim:** furled (no force), half, full. The cloth shows the trim and bellies to the downwind side.
- The force grows with the wind you feel on board, the sail's area and its trim. A ship running before the wind can't
  go faster than the wind.
- **No-go zone:** within 30° of the wind no sail drives the ship forward.
- Sails only work on a ship that is afloat.
- Yards run across the ship, stays run along it. Directions are only visual: the crew is assumed to trim the sails optimally.

## Sail winch
Using it cycles the trim of **all** sails on its ship: furled → half → full → furled. Clicking an upper yard or a head cleat with the
empty hand cycles only that sail.

## Keel
A ship in water resists moving sideways much more than moving forward, so a sail on a beam reach drives it ahead
instead of pushing it downwind. No block is needed for this.

## Rolling and creaking
A ship that heels or is shoved swings back and settles within a few seconds (roll and pitch damping, config `sailing`).
While it rolls, its planks creak now and then, quietly, from somewhere in the hull; a ship at rest is silent. The
creak uses vanilla wooden sounds as placeholders until real recordings exist.

## Hull creaks
A rolling ship creaks now and then from somewhere in the lower hull, louder and more often the harder it rolls,
never as a loop; a ship lying still is silent. Server config `hull_creaking` sets the volume and the minimum gap.

## Sea music
The mod brings its own music. Aboard a ship (standing on deck or sitting at a station) the next track is a sea
shanty; at sea but not aboard (an ocean, deep ocean or beach biome) it is an ambient sea track; anywhere else vanilla
music plays as usual. A running track is never cut off: the pool changes when it ends, and the gap between tracks is
2 to 5 minutes by default (client config `audio`: `min_gap_seconds`, `max_gap_seconds`, `shanties_aboard`,
`music_enabled`, `music_volume`). The tracks and their authors are listed in `credits.md`.

## Helm and rudder
On an assembled ship, using the helm turns the rudder one step: the right third of the wheel (as the helmsman sees it)
to starboard, the left third to port, the middle back to midships. There are three steps per side, up to 35°. The
action bar shows the position. The rudder only works while the ship moves through the water, and it reverses when the
ship goes astern.

## Capstan and anchor
The anchor is a real object: it hangs outside the hull on the side nearer to the capstan, just below the deck, and
moves with the ship. Using the capstan runs it out on a chain at 6 blocks per second to the first solid block within
32 blocks below, with the chain rattling, a splash when it enters the water and a thud when it lands; from that moment
the ship holds. Using the capstan again heaves it back in at 2.5 blocks per second until it hangs at the hull again.
Using it mid-way reverses. If there is no ground in reach, the capstan tells you and the anchor stays stowed. A held
ship stays within about two blocks of the anchor point and swings with the wind.

## When a ship breaks apart
Shoot or break the only block joining two parts of a ship and it splits: the part with the helm stays your ship, the
other part becomes a wreck (its nameplate says "Wreck of …") that drifts but no longer sails; crew on it stay aboard;
bits under four blocks fall apart into items. `/pirates ship info` shows a piece's id, origin and wreck flag
(operators). Server config `assembly.split`.

**Putting it back together.** Craft a Shipwright's Toolkit (Carpenter's Hammer, Saw, Nails and Leather) and some Nails
(3 iron nuggets make 8). Sneak-use the toolkit on the half you want to keep, usually the one with the helm; while you
hold it, green sparkles show where the other pieces are close. Bring the broken-off piece back against it, lined up
straight (no more than a few degrees off), touching face to face; if there is a one-block gap, place planks to bridge
it first. Then use the toolkit on the broken-off piece: after a second of hammering it is nailed back on, keeps its
chests and stations, and the ship keeps its name. Each repair uses 4 nails. Only pieces of the same ship can be
joined, and only pieces up to 200 blocks; bigger halves need a shipwright. Server config `assembly.rejoin`.

## Boarding along the rope
Once a grappling rope is latched onto another ship, look at the rope and use it: you hang from it and slide down to the
lower end, following both ships as they move. Sneak to let go. A level rope is crawled slowly toward the hook. From the
crow's nest down to an enemy deck is the classic move. Server config `grapple.slide`.

## Map tiles
Craft a Map Tile from 8 sticks around a paper and place it on a table or a wall. Put several side by side in a
rectangle, all facing the same way, and they form one board of up to 8 by 8 tiles. Use any tile with your chart in
hand: the chart opens with a frame the shape of the board. Drag it over the part you want, pick a zoom (each step shows
a wider area, coarser), choose whether your markers go on it, and press Draw. Drawing costs ink: an ink sac or glow
ink sac per tile, and a kraken's ink pays for eight; creative players draw for free. Later, anyone who has charted more
can use the board again and press Update: what they know fills in the blank parchment, and what was drawn before
stays. They pay only for the tiles that changed. "New" draws the board afresh at another area or zoom, and "Clear"
wipes it. Looking at a tile tells who drew or last updated it, when, and the area. Break a tile and it keeps its part
of the drawing as an item; put it back in its place and the board is whole again. Server options under
`chart.tiles`: `enabled`, `redraw_allowed`, `require_chart_item`, `reach`, `tile_cells`, `max_board_side`, `max_zoom`,
`ink_cost_enabled`, `ink_per_tile`, `kraken_ink_tile_value`.

## Hammocks
Your crew sleeps in hammocks. Hang one between two supports at the same height (fence posts, walls, logs, or a solid
wall such as the hull side): click the block next to one support while looking toward the other. Recipe: 2 string
over 3 wool. At nightfall every crew member who is not at a station turns in to the nearest free hammock and gets up at
dawn. A night in a hammock raises its morale by 5; a night on a ship without a free hammock for it lowers it by 10 (it
will grumble). Crew on duty all night are unaffected, and an order at night gets sleepers up at once. Each hammock is
one bunk: use the captain's whistle on a crew member, or `/pirates crew info`, to see morale and "crew 3 / bunks 2".
Players can't sleep in hammocks. Server config `crew.morale`.

## Orders, not assignments
You don't have to assign every sailor. Give an order with the whistle (or `/pirates crew order`), and every unmanned
station that can carry it out becomes an open job: free crew standing on your ship take the nearest one by themselves
within a second, get to work and stay there afterwards. Crew you put at a station yourself with the whistle stay put
and are never moved. If nobody is free you hear "No free hands" once; the job waits for the next crew member who comes
aboard. "Release crew" sends everyone off and cancels the open jobs. Server config `crew_stations.job_board`.

## Seafarer villages
Seafarer villages generate on beaches. A stone quay with the harbor master's hut faces the sea, and a plank pier runs
straight out over the water with two ship berths, one on each side halfway out. Streets lead inland with cottages, a
tavern and a shipwright's shed, and end in a small cobbled place. The harbor master's desk belongs to the village's
port: use it to open the port's market. A desk you place anywhere inside a village joins that port too. Operators can
list ports with `/pirates world ports` and find the nearest with `/pirates world port nearest`. Server config
`world.structures.seafarer_village`.

## Cargo weight
What you carry weighs the ship down. Crates, cargo barrels, pantries and water barrels get heavier as they fill: a full
crate weighs as much as forty planks. Chests and other vanilla containers press down where they stand, so a heavy
chest in the bow trims the ship by the bow. Spread heavy cargo and keep it low and central. At the wheel the rudder
line shows the load: Light, Laden, Heavily laden or Overloaded (`/pirates ship info` shows the numbers). A laden ship
sits lower, so it floods sooner through a breach, and it is slower to accelerate and turn. Server config
`cargo_trade.cargo_weight_affects_ships`, `weight_factor`, `weigh_interval_ticks`, `load_levels`.

## Sea hazards
In a thunderstorm at sea a waterspout can form 48 to 96 blocks from you: a turning column of spray up to 24 blocks
high with a low roar. Within 8 blocks of it you, your boat, loose items and light ships are pulled toward it and
lifted; inside it the wind tears at set sails and drops them a step every few seconds (full to half, half to
furled). Steer clear, or furl first. In the deep ocean, at any time, a whirlpool can appear: a slowly drifting ring
of foam around a dark centre that pulls everything within 12 blocks inward and around. Close to its centre it drags
boats and swimmers under, and a ship caught at its centre is slowly turned. Big ships barely notice either hazard.
Operators: `/pirates hazard spawn <waterspout|whirlpool> [x y z]`, `/pirates hazard clear [radius]`; everything is
in the server config under `hazards`, the particles under the client's `hazard_visuals`.

## Chart
Every captain keeps their own chart. As you sail, the coasts within about 96 blocks are drawn in automatically:
ink coastlines, hatched shallows, sandy beaches, and the open sea, where the odd sea serpent lurks. Craft a
Chart (paper, leather, a feather) and use it to open your map. Drag to pan, scroll to zoom, and right-click to
place a marker (X, anchor, skull, port or danger) with a name; click a marker to rename or delete it. Your ship
shows where you are and which way you are heading. If the server allows it, the M key opens the chart without
one in hand, and other players can appear as ships. Only the overworld's seas are charted.
