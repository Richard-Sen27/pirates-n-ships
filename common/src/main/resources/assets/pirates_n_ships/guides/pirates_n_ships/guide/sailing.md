---
navigation:
  title: "Sailing"
  parent: index.md
  position: 30
  icon: pirates_n_ships:yard
item_ids:
  - pirates_n_ships:capstan
  - pirates_n_ships:chart
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
Craft a Map Tile from 8 sticks around a paper and place it on a table or a wall. Use it with your chart in hand: the
chart opens with a frame. Drag the frame over the part you want, choose whether your markers go on it, and press Draw.
Everyone who passes by sees that part of your chart on the tile, and looking at it tells who drew it and when. Drawing
again replaces the picture. Break the tile and it keeps its drawing as an item, ready to hang somewhere else. Server
options: `chart.tiles.enabled`, `redraw_allowed`, `require_chart_item`, `reach`, `tile_cells`.

## Orders, not assignments
You don't have to assign every sailor. Give an order with the whistle (or `/pirates crew order`), and every unmanned
station that can carry it out becomes an open job: free crew standing on your ship take the nearest one by themselves
within a second, get to work and stay there afterwards. Crew you put at a station yourself with the whistle stay put
and are never moved. If nobody is free you hear "No free hands" once; the job waits for the next crew member who comes
aboard. "Release crew" sends everyone off and cancels the open jobs. Server config `crew_stations.job_board`.

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
