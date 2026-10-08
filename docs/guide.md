# Pirates 'n' Ships: how it works

> In-game guide (GuideME): after changing this file, regenerate it with `python3 tools/gen_guideme.py` and commit the output.

A guide to everything that exists in the mod today: what each block, item and system does and how to use it.
It describes the code on `main`, not the plans. For the plans see [`design.md`](design.md), and for the state of
the work see [`progress.md`](progress.md).

> **Status: prototype.** Everything below is implemented and covered by automated tests on a headless server.
> Nobody has played it in a client yet. How things look and feel (ships floating, water inside hulls, sailing speed,
> crew on deck) is unverified until the playtests in [`playtests/`](playtests/) are done.
> Several features have no in-world source yet (no ports, no navy, no hiring), so they are reached through
> operator commands under `/pirates`.

Requirements: Minecraft 1.21.1, NeoForge, and the [Sable](https://github.com/ryanhcode/sable) mod, which provides the
physics for moving ships.

## Contents
1. [Your first ship](#1-your-first-ship)
2. [Ships](#2-ships)
3. [Sailing](#3-sailing)
4. [Crew](#4-crew)
5. [Flags](#5-flags)
6. [Provisions](#6-provisions)
7. [Cargo and trade](#7-cargo-and-trade)
8. [Law, bounties and the brig](#8-law-bounties-and-the-brig)
9. [Weapons and combat](#9-weapons-and-combat)
10. [All blocks](#10-all-blocks)
11. [All items](#11-all-items)
12. [Commands](#12-commands)
13. [Configuration](#13-configuration)
14. [Datapacks](#14-datapacks)
15. [What does not exist yet](#15-what-does-not-exist-yet)

---

## 0. The guide book
With GuideME installed you start with the **Pirates 'n' Ships Guide** (also craftable from a book and a feather): this
guide as an in-game book with item links, recipes and search. Hold G over one of the mod's items to open its page.

## 1. Your first ship

1. **Build a hull** from any blocks, floating in water. Planks, slabs, stairs and glass are watertight. Leave a
   one-block gap to the shore or a dock: anything that touches the hull becomes part of the ship.
   Make the bottom layer from something heavy such as stone, or the boat will tip (see [Limits](#limits-to-know)).
2. **Place a helm** on deck. Stand behind it and look where the ship should go: the bow is the direction the
   helmsman looks.
3. **Rig a square sail**: a mast of logs or fences with two rows of **yards** across it, one 2 to 8 blocks above the other, and a **sail winch** somewhere on deck. A **capstan** gives you an anchor.
4. **Use the helm.** The connected blocks become a ship: a physics object that floats, with a dry hold.
5. **Use the sail winch** to hoist the sails (furled → half → full). The wind pushes the ship.
6. **Steer at the helm:** hold right-click on the helm to take the wheel, then move the mouse or hold A/D to turn it:
   right or D turns it clockwise and the ship to starboard, left or A to port. The wheel turns three quarters of a turn
   each way from midships, and the line above the hotbar shows the rudder angle. Let go to release it; the wheel stays
   where you left it. (Server option `helm.wheel.drag_steering = false` brings back clicking the wheel's thirds.)
7. **Use the capstan** to drop the anchor, and again to raise it.
8. **Sneak-use the helm with an empty hand** to turn the ship back into normal blocks. The ship has to be nearly still
   and level.

For testing you can fix the wind with `/pirates wind set <fromDegrees> <strength>`, and see what pushes the ship with
`/pirates ship forces`.

---

## 2. Ships

### Assembly at the helm
Using a **helm** that stands in the world assembles a ship:
- It collects every block connected to the helm, through faces and edges.
- It never takes air, fluids, or **terrain** (dirt, sand, stone, ores, gravel, ice, snow, leaves, corals, kelp and
  similar: the block tag `pirates_n_ships:terrain`). Logs are not terrain, so a wooden build counts. The tag
  `pirates_n_ships:never_assemble` excludes more blocks (bedrock, barriers, portals, command blocks).
- The limit is 2048 blocks. Above it the helm refuses and tells you. A dock that touches the hull counts towards it.
- Chests and other block entities keep their contents.
- The sea is put back where the hull stood, so no hole is left in the water.
- The ship gets a record (its id, a name, the owner). Use a **name tag** on the helm of an assembled ship to name it.

### Disassembly
Sneak-use the helm with an empty hand. The ship is put back into the world as blocks, snapped to the block grid with
its heading rounded to the nearest 90°. It is refused when:
- the ship moves faster than 0.3 m/s or turns too fast,
- it is tilted more than 6°,
- something solid is in the way (the message names the position).

**If your helm breaks,** the ship stays a ship: its stations keep working, but nothing steers or disassembles it until
you place a helm anywhere on its deck. The first helm placed steers; a second helm on the same ship does nothing while
the first stands. Using a helm on a floating hull the mod has lost track of makes it your ship again.

Players and mobs on deck are set down on the deck blocks. Water inside the hull is removed.

### The dry hull
An assembled ship analyses its own hull:
- An air space counts as part of the hull when it lies **below its own pour point**, the lowest way water could get in.
  So a closed cabin is dry, and an open boat is dry up to its rim.
- Watertight: full blocks, slabs, stairs, glass. Not watertight: fences, walls, bars, panes, ladders, chains.
  The block tags `pirates_n_ships:watertight` and `pirates_n_ships:not_watertight` override this.
- Doors, trapdoors and fence gates are **openings**: watertight when closed, a way for water when open.
- Inside the dry part there is no water: you don't swim, you can breathe, and no water is drawn (through Sable's water
  occlusion). Slabs, stairs and closed hatches inside the hull are drawn dry in their empty half too, as long as that
  half faces the dry room and not the sea or the sky. Known gaps: boats, fishing bobbers and mob pathfinding still see
  the water.

### Flooding and sinking
- **A breach:** a hull block that is destroyed below the waterline lets water into the room behind it. So does an
  opening that is open below the waterline. Water comes in faster the bigger the hole and the deeper it lies.
  A 1×1 hole one block under water lets in about one block of water per second.
- Rooms joined by an open door or hatch level out. A closed door holds the water back.
- Placing a block into the breach stops the inflow.
- **Buoyancy:** the dry volume under water lifts the ship, and flood water weighs it down. A fully flooded ship sinks.
- The flood state is saved with the ship.
- Flood water shows as a level water surface inside the room, rising and falling with the flood (client config
  `dry_hull_view.flood_surface`); below it you get the underwater view.
- Below that surface you hold your breath as in the sea: with your head under the flood water your air runs out and
  you start to drown, even where the flood stands higher than the sea outside. Respiration, Water Breathing, Conduit
  Power and a turtle helmet help as usual; lift your head above the water and you breathe again (server config
  `dry_hull.flood_breath`).

### Fighting a leak
A hull block destroyed below the waterline leaves a breach, and water runs into that room at a rate; deeper holes leak
faster.
- **Hull Patch** (2 planks and 1 coal or charcoal give 2): stand inside, look at the edge of the hole (the face of a hull
  block next to it) and use the patch. It fills the hole with a tarred plank block and the water stops at once. A patch
  only goes into a real hole in an assembled ship; elsewhere it tells you so and is not used up. You can break the
  patch later and put in a normal plank, but the hole is open while you swap.
- **Bilge Pump** (a stick on top, plank, bucket, plank in the middle, a plank below): place it on the hold floor, or on
  the deck right above the hold, since its pipe reaches 4 blocks down through the planks. Hold right-click on it to
  pump: it removes 1 block of water per second and stops when you let go. The action bar shows how much water is left,
  or "The bilge is dry". It drains the highest flooded room below it. Pumping makes you hungry slowly. Several pumps in
  one room add up.
- A crew member can man a pump like a sail winch: assign it with the whistle, then pick **Man the pumps** in the
  whistle wheel (or `/pirates crew order pump`). It pumps until the room is dry and answers "The bilge is dry" when
  there is nothing to do. Ship-wide orders reach only the crew whose station takes them; an order to the wrong station
  is refused.
- Server config `flooding`: `pump_enabled`, `pump_rate`, `pump_reach`, `pump_use_ticks`, `pump_exhaustion`,
  `patch_enabled`.

### Limits to know
- **Stay near the world origin.** Sable's physics uses 32-bit positions. Beyond about 100,000 blocks from the origin a
  slow ship reports a speed but stops moving. Beyond several million blocks ships can sink into solid blocks.
- **Small hollow hulls tip easily.** They float but barely right themselves. In tests a 5×5 plank boat, 4 blocks high,
  with a deck, helm and mast lay about 20° bow up at rest, ran 35 to 46° bow down under a small sail, and wandered
  a few degrees off course without rudder input. **Give a small ship a heavy bottom:** with a bottom layer of stone
  the same boat ran about 16° bow down and held its course. Longer and wider hulls are much steadier. The heeling
  force of sails is also scaled down to 25% (config `sailing_runtime.sail_heel_factor`).
- A ship only floats on water that is under its own hull. A ship in a dry dock next to the sea stays put.

---

## 3. Sailing

### Wind
Each dimension has one wind: a direction and a strength of 3 to 12 blocks per second that drift slowly over time.
Rain makes it 1.5 times stronger and a thunderstorm 2.2 times, with gusts during thunderstorms. It is the same for
every player and is sent to clients (nothing displays it yet, except the flags).

### Sails
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

### Sail winch
Using it cycles the trim of **all** sails on its ship: furled → half → full → furled. Clicking an upper yard or a head cleat with the
empty hand cycles only that sail.

### Keel
A ship in water resists moving sideways much more than moving forward, so a sail on a beam reach drives it ahead
instead of pushing it downwind. No block is needed for this.

### Rolling and creaking
A ship that heels or is shoved swings back and settles within a few seconds (roll and pitch damping, config `sailing`).
While it rolls, its planks creak now and then, quietly, from somewhere in the hull; a ship at rest is silent. The
creak uses vanilla wooden sounds as placeholders until real recordings exist.

### Hull creaks
A rolling ship creaks now and then from somewhere in the lower hull, louder and more often the harder it rolls,
never as a loop; a ship lying still is silent. Server config `hull_creaking` sets the volume and the minimum gap.

### Sea music
The mod brings its own music. Aboard a ship (standing on deck or sitting at a station) the next track is a sea
shanty; at sea but not aboard (an ocean, deep ocean or beach biome) it is an ambient sea track; anywhere else vanilla
music plays as usual. A running track is never cut off: the pool changes when it ends, and the gap between tracks is
2 to 5 minutes by default (client config `audio`: `min_gap_seconds`, `max_gap_seconds`, `shanties_aboard`,
`music_enabled`, `music_volume`). The tracks and their authors are listed in [`credits.md`](credits.md).

### Helm and rudder
Hold right-click on the helm of an assembled ship and turn the wheel (mouse or A/D); the rudder follows it up to 35°
each way, and the line above the hotbar shows the angle. With `helm.wheel.drag_steering = false` you click the wheel
instead: the right third (as the helmsman sees it) one step to starboard, the left third to port, the middle back to
midships, three steps per side. The rudder only works while the ship moves through the water, and it reverses when the
ship goes astern. Hard over, the starter sloop turns a circle about three to four ship lengths across (some 100
blocks), at any speed: a slow ship turns on the same circle, it just takes longer to sail it (at full sail before a
fresh wind about a minute for a quarter turn). It heels a little in the turn but never far. Server config: how sharp
the ship turns is `sailing.rudder_force_factor` (3; 1 is the weaker rudder of earlier versions), the largest rudder
angle `sailing.max_rudder_angle`.

### A helmsman holds the course
Assign a crew member to the ship's helm (whistle on the crew member, then on the helm) or simply give a course:
`/pirates crew order course <x> <z>` (more pairs for waypoints, `loop` to sail them round and round); a free hand takes
the helm by himself. He turns the wheel toward each point in turn and puts the rudder midships when the last one is
reached ("We've arrived, captain!"). He does not touch the sails: order "Hoist sails" as well. Take the wheel
yourself at any time and he lets go; release it and he steers on. Only the ship's steering helm holds a course, and
if the ship stops making headway with its sails set he tells you it is stuck. Server config `crew_stations.course`.

### Heel
Wind on the side of a sail pushes the ship over. A hull rights itself: the wider and the shallower it is, the stiffer
it stands. The starter sloop leans about 2° in a moderate beam wind and about 5° in a strong one, and comes back
upright a second or two after you furl. Sails spill their wind as the heel nears 25°, so even a storm will not lay a
ship on its side under sail alone. Cargo stowed to one side and water in the hold still make a ship list; pump it out
and trim the load. Server config `stability`.

### Capstan and anchor
The anchor is a real object: it hangs outside the hull on the side nearer to the capstan, just below the deck, and
moves with the ship. The anchor is heavy and falls on its own: let it go while the ship has way on and it keeps the
ship's speed for a moment, sinks at about 4 blocks a second and lands a few blocks astern of where it left the hull,
with the chain rattling, a splash when it enters the water and a thud when it lands. Once it bites, the chain pulls
the ship back at the bow, softly, so there is no dead stop. Because the hawse is on the capstan's side, a ship under
sail swings hard round toward that side and ends up head to its chain, bow toward the anchor. A ship pulling harder
than the anchor holds drags it over the seabed. The ship counts as anchored once the anchor holds and the ship has
stopped. Using the capstan again heaves the chain in at 2.5 blocks a second and pulls the anchor along the seabed and
up to the hull; using it mid-way lets go again. If the chain runs out before the seabed, the anchor hangs at its end
and does not hold. Server config `anchor`.

You can also put a crew member on the capstan: use the Captain's Whistle on the crew member, then on the
capstan. In the whistle menu, "Drop anchor" makes the hand lean into the bars for two seconds before the anchor runs
out, and "Weigh anchor" starts the winding at once; he keeps pushing until the anchor is stowed. A free hand on deck
takes an unmanned capstan by himself when you give the order. Over water deeper than the chain he refuses: "No
ground for the anchor within the chain's reach, captain!" Commands: `/pirates crew order drop_anchor` and
`raise_anchor`. Server config `crew_stations.capstan`.

---

### When a ship breaks apart
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

### Hauling with the rope
With your hook set in a ship and the rope in your hand, hold sneak: the rope's length freezes. Walk back and the
rope pulls the ship toward you; a hook near the bow or stern swings it round. Let go of sneak and the rope pays out
again. Pull too hard and the rope slips through your hand rather than snapping. Standing on the ground or a deck
you feel nothing; in the air or in the water the rope pulls you toward the hook. A rope tied to a cleat can't be
hauled by hand. **Climbing:** right-click your own rope while it is still in your hand to climb it: you are pulled hand over hand to
the hook when it is at least two blocks above your feet (a mast, a cliff, a hull side from the water); sneak to let
go. A hook level with you or below is no climb; tie the rope off on a cleat to use it as a line, and only such a tied
rope is a line others can slide along. Server config
`grapple.hauling`, `haul_stiffness`, `haul_damping`, `haul_max_force`, `haul_player_pull`.

### Fire at will
Pick "Fire at will" on the captain's whistle and your gun crews take over their cannons. Every second each crew
raises or lowers its barrel one step toward the best elevation for the nearest hostile ship within its arc (15°
either side of the barrel) and 64 blocks, leads a moving target, loads from powder and shot nearby, and fires when
the shot will hit. Who counts as hostile depends on your own flag: under a navy flag they fire on the Jolly Roger
and on wanted captains; under the Jolly Roger they fire on navy and merchant ships; under a merchant flag or none
they fire only on the Jolly Roger. Nobody fires on a ship that has struck its colours, and your crew holds fire if
you strike yours. Shots your crew fires on its own break fewer planks than a captain's "Fire!". "Release crew" ends
it. Turn the ship to bring the guns to bear: a cannon cannot turn sideways. Server config `cannons.npc`.

### Boarding along the rope
Once a grappling rope is latched onto another ship, look at the rope and use it: you hang from it and slide down to the
lower end, following both ships as they move. Sneak to let go. A level rope is crawled slowly toward the hook. From the
crow's nest down to an enemy deck is the classic move. Server config `grapple.slide`.

### Boarding plank
Craft one from three wooden slabs over two iron nuggets. When your ship lies alongside another (haul her in with the
grappling hook first), stand at your gunwale and use the plank on the top of a gunwale or rail block, facing the other
ship. A plank up to four blocks long runs straight out, level with the top of that block, and hooks onto the other
deck. Used on the side of the block, it lies a block lower. The far deck may sit one block higher or lower than the
plank. Walk across, but keep the ships together: when they drift apart by more than a block and a half, the whole
plank breaks and drops back as one item. Breaking any part of it takes the whole plank down. Server config
`boarding.plank`.

### Map tiles
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

### Decor
Dress up your ship. The **Ship's Lantern** (a lantern and two gold nuggets) stands on deck, hangs from a beam or
from a bracket on a wall, and lights like a lantern, even underwater. The **Ship's Bell** sits on a post or a wall;
right-click to ring it. **Rope Coils** (four rope) stack up to four on one spot. **Stern Windows** go into a wall
from outside; right-click to close or open the shutters. The **Chart Table** is a captain's table with a chart
spread out on it. The **Sea Cot** is a wooden bed for the captain's cabin: on land you can sleep in it and set your
spawn like a bed, but not while it is aboard a ship at sea. Server config `ship_decor`.

### Hammocks
Your crew sleeps in hammocks. Hang one between two supports at the same height (fence posts, walls, logs, or a solid
wall such as the hull side): click the block next to one support while looking toward the other. Recipe: 2 string
over 3 wool. At nightfall every crew member who is not at a station turns in to the nearest free hammock and gets up at
dawn. A night in a hammock raises its morale by 5; a night on a ship without a free hammock for it lowers it by 10 (it
will grumble). Crew on duty all night are unaffected, and an order at night gets sleepers up at once. Each hammock is
one bunk: use the captain's whistle on a crew member, or `/pirates crew info`, to see morale and "crew 3 / bunks 2".
Players can't sleep in hammocks. Server config `crew.morale`.

### When an order reaches nobody
If the whistle or `/pirates crew order` says nobody carries an order out, the line now tells you why: no ship under
you, no station on board can do it, nothing to do, or no sails with the reason (a gap over 8 blocks between the yards,
a block in the mast between them, yards off the mast's column, a lower yard longer than the upper). `/pirates ship
rigging` lists every yard and what it carries, the triangular sails and the crew aboard.

### Upkeep
Every dawn your crew eats and drinks one day of provisions from the pantries and water barrels aboard and wants its
pay: 2 doubloons each, taken from any chest, barrel or cargo crate on the ship (the ones nearest the helm first).
Hungry or thirsty crew lose morale and work slower; rum cheers them up; weeks without citrus or fresh food bring
scurvy. Unpaid crew lose 8 morale, paid crew gain 1. If the coins aboard run short, the rest of the wages comes out of your
own purse while you are online in that dimension (chests and crates always pay first). A sailor whose morale stays
below 20 for two dawns means to desert: he tells you and walks off at the next port the ship comes within 48 blocks
of, onto the quay by the berth, or wherever the ship is after three more dawns; feed and pay him back above 20 and he
changes his mind. Twice a day, at noon and at sunset, the crew not at a station sit down by the pantry (or the water
barrel) to eat for a few seconds; an order gets them up at once. The meal is only a sight: rations are still taken
once a day at dawn. If mutiny is enabled in the config, a crew whose average stays below 15 for three dawns turns
pirate and takes your ship. `/pirates crew info` shows supplies left, the last payday and what your purse paid; the
whistle shows "unpaid", "deserting", who hired a sailor and the days of food and water left. Server config
`crew.wages`, `crew.desertion`, `crew.mutiny`, `crew.meals`, `provisions`.

### Orders, not assignments
You don't have to assign every sailor. Give an order with the whistle (or `/pirates crew order`), and every unmanned
station that can carry it out becomes an open job: free crew standing on your ship take the nearest one by themselves
within a second, get to work and stay there afterwards. Crew you put at a station yourself with the whistle stay put
and are never moved. If nobody is free you hear "No free hands" once; the job waits for the next crew member who comes
aboard. "Release crew" sends everyone off and cancels the open jobs. Server config `crew_stations.job_board`.

### Ordering a ship
At a seafarer village, open the harbor master's desk and switch to the **Orders** tab. Each ship shows its price in
doubloons, how many days the shipwright needs, and the logs and wool he wants. Click **Order** to pay and receive a
**Ship Receipt**; its tooltip shows how long the ship still needs. When the receipt says "Ready for pickup", use the
same desk while holding it: your ship appears assembled at a free berth beside the pier, and it is yours. If both
berths are taken, come back later; the receipt stays valid. The shipwright works on at most three ships at once.
Server config `ships.shipwright_orders`, `build_time_days`, `order_price_factor`, `order_materials_factor`,
`max_orders_per_port`.

### Seafarer villages
Seafarer villages generate on beaches. A stone quay with the harbor master's hut faces the sea, and a plank pier runs
straight out over the water with two ship berths, one on each side halfway out. Streets lead inland with cottages, a
tavern and a shipwright's shed, and end in a small cobbled place. The harbor master's desk belongs to the village's
port: use it to open the port's market. A desk you place anywhere inside a village joins that port too. Operators can
list ports with `/pirates world ports` and find the nearest with `/pirates world port nearest`. Server config
`world.structures.seafarer_village`.

### Navy outposts
Navy outposts are stone forts on beaches, rarer than villages. A sea gate leads to a quay with two berths. The
curtain walls carry cannons (unloaded) and end in corner towers. One building stands outside the land gate: a
barracks, a brig with two cells, or a watchtower. The harbor master's office in the court holds the navy market's
desk and a notice board. Every outpost has a garrison that stands at its posts and never wanders, despawns or
respawns: by default one officer beside the office door and six soldiers at the land gate and on the walls. Use the
officer to turn in shackled pirates and bounty proofs, to pay fines with doubloons, and to ransom captured navy
officers or merchants. With `law.ransom_needs_port` on, only an outpost's officer pays ransoms. With a bounty, the
soldiers open fire on sight. `/locate structure pirates_n_ships:navy_outpost` finds one; `/pirates world ports`
lists it. Server config `world.structures.navy_outpost`.

### The watch
A navy outpost never sleeps. Every so often the officer of the fort calls three of his soldiers from their posts and
leads them round the fort in file: across the parade court, through the land and sea gates, out onto the quay, up the
stairs and along the walls, stopping a while at each post. Strike one of them and the whole squad turns on you, the
officer's blade first; kill a man and another leaves his post to fill the file at the next stop. At nightfall the
watch returns to the posts, and the guards stand fast again until morning. Operators: `/pirates mob squad
info|patrol|return`. Server config `mobs.squad`.

### Ship HUD
While you stand on a ship, a small panel in the top right corner shows its state. The compass rose turns a little
ship-shaped needle to the bow's heading; the light arrow outside the rose sits on the side the wind comes from and
points the way it blows: the longer it is, the stronger the wind, and it turns amber in a gust. Below it you read the
speed (in knots, or blocks per second) and the rudder angle, then the ship's name and how heavily it is laden. The
strip at the bottom is your hull from bow (left) to stern: one cell per compartment, filling blue as water comes in,
with a red mark where a breach lets the sea in and a pump sign while a pump drains it. Client options under
`ship_hud`: on/off, corner, size and speed unit; servers can switch it off with `ships.ship_status_hud`.

### Cargo weight
What you carry weighs the ship down. Crates, cargo barrels, pantries and water barrels get heavier as they fill: a full
crate weighs as much as forty planks. Chests and other vanilla containers press down where they stand, so a heavy
chest in the bow trims the ship by the bow. Spread heavy cargo and keep it low and central. At the wheel the rudder
line shows the load: Light, Laden, Heavily laden or Overloaded (`/pirates ship info` shows the numbers). A laden ship
sits lower, so it floods sooner through a breach, and it is slower to accelerate and turn. Server config
`cargo_trade.cargo_weight_affects_ships`, `weight_factor`, `weigh_interval_ticks`, `load_levels`.

### The factions
The sea has three powers: the Navy, the Pirates and the Merchants. Each has a temper (aggression) and a purse
(wealth), and each pair shares a measure of bad blood (tension). Navy kills of pirates, plundered merchants, convoys
that arrive or sink, raids and lost patrols all shift them, and so do your own deeds. Tempers cool a little every
day. High tension between the Navy and the Pirates means more patrols, more hunting and more raids. Operators can see
the state with `/pirates world factions`. Server config `world_simulation.factions`.

### Reputation
The navy, the pirates and the villagers each remember you on a scale from −100 to 100 (`/pirates rep`). Killing
pirates and turning them in pleases the navy; killing navy sailors, plundering merchants and selling to fences wins
the pirates over; trading at villages pleases the villagers and harming them does the opposite. Scores drift back
toward 0 by about 2 points a day. Villagers and fences give up to 10 % better prices to people they like and worse to
people they don't, and villagers stop trading below −60. Pirates leave you alone above 40 until you strike first, and
below −60 the navy opens fire even without a bounty. A navy flag is only honest while your navy reputation is at
least 0, you have no bounty and you are not a suspect; otherwise the navy may see through your false colours. Server
config `reputation`.

### Convoys
Merchant ships sail between the ports along sea lanes that keep clear of the coasts. A convoy loads goods where they
are made (cheap) and carries them to a port that wants them (expensive), so prices move: the port it leaves pays a
little more for what it bought, the port it reaches pays a little less once it has sold. Convoys sail about 4 blocks
a second, faster before the wind and slower into it. Harbor masters now reckon contract distances along these lanes,
and routes that pass near a pirate island pay a risk bonus. Operators can watch the traffic with
`/pirates world voyages`. Server config `world_simulation.lanes`, `world_simulation.voyages`.

### Ships on the horizon
NPC ships sail the sea lanes between ports even when nobody watches. When you come within sight of one, it becomes a
real ship: a merchantman under the merchant flag with goods in her hold and a few armed sailors, a navy patrol, or a
pirate under the Jolly Roger. She sails on to her destination and fades back into the distance when you leave. Take
goods from a merchant's hold while aboard and you have plundered her. Sink her with your cannons and the deed is
yours. Kill every fighter aboard and hold her deck for a few seconds, and she is yours, crew and all. Capturing a
merchant is piracy in the navy's eyes. Operators: `/pirates world voyages spawn near convoy|patrol|raid`. Server
config `world_simulation.materialize`.

### Navy patrols
The navy sends patrols between its outposts, or out toward the nearest pirate island and back when an outpost stands
alone; an aggressive navy sends more. A patrol keeps a lookout of about 256 blocks. It gives chase to any player's
ship that flies the Jolly Roger, whose false colours it has seen through, or whose captain is wanted or carries a
bounty of 50 doubloons or more, whatever flag that ship flies. You'll get word when a patrol sights you. Once it
closes in, the patrol circles you at about 20 blocks while its gun crews fire. Strike your colours and its guns fall
silent: it keeps you in sight for half a minute, then sails on. Raise the Jolly Roger again and the chase is back on.
Outrun it (no contact within 64 blocks for two minutes, or more than 384 blocks between you) and it breaks off.
Patrols that kill pirates, and patrols lost at sea, stir up the bad blood between the navy and the pirates.
Operators: `/pirates world patrols`. Server config `world_simulation.navy`.

### Raids
Stay long at a navy outpost or a seafarer village and the pirates take notice: every minute a player spends there
raises the chance of a raid a little (capped), and the more bad blood between the Navy and the Pirates, the faster it
rises. When a raid comes, the settlement hears "Sails on the horizon!" and its bells ring (the fort's alarm bell; a
village has none and only hears the warning). Pirate sloops under the Jolly Roger sail in from the nearest pirate
island, guns ready; off the quay they drop anchor and put their fighters ashore, who fight the garrison and you. Kill
them all and the raiders are beaten off; if they hold the shore for five minutes they sail off having had their way.
Either way the ships leave, and the settlement is safe from raids for five days. Raiders do not loot. Operators can
force a raid with `/pirates world raid <port>` and see the chances with `/pirates world raid chance`. Server config
`world_simulation.raids`, `world_simulation.retaliation_enabled`.

### Careers
Two ladders, and you can only climb one. *Navy:* talk to a navy officer with an empty hand and enlist once the navy
trusts you (navy reputation 10, no bounty, no friends among the pirates). Killing or turning in pirates, navy quests
and a growing navy reputation promote you: Midshipman, Lieutenant, Captain, Commodore, Admiral. Firing on the navy
or a merchant while in service is desertion: you lose your rank and the navy wants you for it. You may resign at any
officer. *Infamy:* plunder fenced, captures and pirate reputation make you a Buccaneer, Dread Captain and finally
Pirate Lord; a known pirate is never taken into the navy. *Letter of marque:* not ready to serve? Buy a letter from
a navy officer (200 doubloons, navy reputation 20). Every pirate you kill under it earns prize money (5 doubloons, a
pirate captain 50) that any navy officer pays out. Attacking the navy or a merchant voids it for three days.
`/pirates career` shows where you stand. **What a rank is worth:** from Lieutenant the navy flag is yours by right: an
officer flying it is never charged with false colours for low navy reputation (a bounty still is), and navy outposts
let you dock for free. Each new rank comes with a gift: a Lieutenant receives the officer's bicorne, a saber and the
Officer's Coat (once; a full pack drops them at your feet). The coat is chest armour that marks you as navy; you can
also craft it from blue wool, white wool and a gold ingot. From Captain the harbor master's desk at a navy outpost has an Orders tab: the
navy shipyard builds you any ship the village shipwrights do, at 70 % of the price (Commodore 50 %, Admiral 40 %),
delivered to the outpost's quay. On the other side, infamy talks at the fences: a Buccaneer, Dread Captain or Pirate
Lord trades as if the pirates liked him 7, 13 or 20 points more, and from Dread Captain up pirates leave you alone
until you attack them. **Titles:** navy officers carry their rank before their name (Mid., Lt., Capt., Cdre. or
Adm.), a letter of marque makes you a Privateer, and growing infamy calls you Buccaneer, Dread Pirate and finally
Pirate Lord, in chat, in the player list and over your head unless you already belong to another team. A small box at
the top left shows your rank, your navy and pirate reputation and your letter; move or hide it in the client config
(`career_hud`). When you name your own ship with a name tag at the helm, your title goes in front of its name
("Capt. Black Gull"); after a promotion, rename it to show the new title. Server config `careers`,
`careers.rewards`, `careers.name_prefix`, `careers.title_on_ship`.

### Quests
Every harbor master's desk has a Quests tab with up to three offers, which the port renews when they run out after
two days. Accept up to three at once; each must be done within five days. Pirate hunts and prisoner deliveries come
from villages and navy outposts, navy raids only from pirate islands. Monster hunts ask for sharks or, rarely (always
in cold waters), the kraken. A cargo run puts a contract in your Contracts tab: deliver it at the destination's desk
for a raised reward with no deposit. A treasure hunt hands you a treasure map; open the chest to finish. Completing a
quest pays doubloons and raises your reputation with the giver's side (navy, pirates or villagers). Villages and navy outposts may also send
you after the nearest pirate island's named captain (within 3000 blocks): bring him down yourself or hand him to a
navy officer in shackles for 400 doubloons on top of his bounty; if someone else gets him first, the quest fails,
and his successor doesn't count. Drop a quest in the tab or with `/pirates quest abandon <id>`;
`/pirates quest list` shows your quests. Server config `quests`.

### Pirate captains
Every pirate island has a captain: a named pirate (for example "Black-Tooth Bartholomew Crowe") who keeps to the
middle of his hut, or to the camp trail if the island has no captain's hut. He is a dangerous swordsman with 40
health, and the navy keeps a standing bounty of 300 doubloons on him, posted on every notice board. To fight him
one-on-one, sneak and use him with a sword in hand: if he accepts, his crew within 16 blocks keep out of the duel
unless you strike them. The duel ends when one of you falls, when you run more than 32 blocks away, or after five
minutes. If you hit him first he refuses: no honour, no duel. Slain, he drops his captain's hat (wear it yourself), a purse of doubloons
and a treasure map of his island, and you get a bounty proof to hand to a navy officer. Taken alive in shackles, the
navy pays the captain's reward of 150 plus his bounty alive. Five days after his fall a successor with a new name and
a new bounty takes his post. Server config `mobs.captain`.

### Pirate islands
Pirate camps sit on beaches, rarer than villages, with a jetty (two berths), tents, a tavern hut, a captain's hut and
a fence's shack under the Jolly Roger. Pirates hang about the camp day and night and attack strangers. The fence's
desk opens a market that buys plunder and rum dear and sells little. Somewhere under two crossed logs a chest lies
buried two blocks deep: dig at the cross for doubloons, rum, provisions and, with luck, a pistol. Server config
`world.structures.pirate_island`.

### Wrecks
Sunken ships lie on the ocean floor of every ocean: a broken sloop, a scattered cargo field, a mast stump with its
yard, or the stern of a larger ship. Each has a chest of ship's stores (doubloons, rum, salted fish, rope, nails,
lead shot, now and then a cutlass, rarely kraken ink). Tall wrecks only lie in deep water; in shallow seas you find
cargo fields. Bring water breathing or night vision. `/locate structure pirates_n_ships:wreck` finds the nearest.
The sea chest in the sloop's hold is empty. Server config `world.structures.wreck.enabled`, `frequency`.

### Treasure maps
Fences on pirate islands sell blank treasure maps (60 doubloons), and wreck chests hold one now and then. Use a
blank map and it marks the nearest pirate island's buried treasure that nobody has found yet (within about 2000
blocks); otherwise it stays blank. While you hold the map, it shows the island in chart style with a red X and
tells you the way: "NW, 340 blocks". At the X, dig about two blocks into the sand and open the chest. The treasure
is found: every map of it turns grey and keeps as a souvenir, and the next blank map leads to another treasure.
Operators: `/pirates world treasure give [port]`. Server config `world.treasure_maps`, `cargo_trade.treasure_map_price`.

### Waves
The sea follows the weather: calm or a light chop in fair weather, rough in rain, a storm in thunder, changing over
about a minute. Ships roll and pitch with the waves (big ships far less than small boats). In rough seas the bow
throws spray, and any open hatch or low rim close to the waterline lets water in at the crests, so close your hatches
and keep a pump ready before a storm. `/pirates waves` shows the sea; operators can hold a state with `/pirates waves
set storm` and release it with `/pirates waves clear`. The camera can roll with the ship (client setting
`wave_effects.camera_sway`, off by default). Server config `waves`.

### Sea hazards
In a thunderstorm at sea a waterspout can form 48 to 96 blocks from you: a turning column of spray up to 24 blocks
high with a low roar. Within 8 blocks of it you, your boat, loose items and light ships are pulled toward it and
lifted; inside it the wind tears at set sails and drops them a step every few seconds (full to half, half to
furled). Steer clear, or furl first. In the deep ocean, at any time, a whirlpool can appear: a slowly drifting ring
of foam around a dark centre that pulls everything within 12 blocks inward and around. Close to its centre it drags
boats and swimmers under, and a ship caught at its centre is slowly turned. Big ships barely notice either hazard.
Operators: `/pirates hazard spawn <waterspout|whirlpool> [x y z]`, `/pirates hazard clear [radius]`; everything is
in the server config under `hazards`, the particles under the client's `hazard_visuals`.

### Chart
Every captain keeps their own chart. As you sail, the coasts within about 96 blocks are drawn in automatically:
ink coastlines, hatched shallows, sandy beaches, and the open sea, where the odd sea serpent lurks. Craft a
Chart (paper, leather, a feather) and use it to open your map. Drag to pan, scroll to zoom, and right-click to
place a marker (X, anchor, skull, port or danger) with a name; click a marker to rename or delete it. Your ship
shows where you are and which way you are heading. If the server allows it, the M key opens the chart without
one in hand, and other players can appear as ships. Only the overworld's seas are charted.

## 4. Crew

A **crew member** is a simple NPC. Hire one at a harbor desk (see "Hiring crew" below) or spawn one with
`/pirates crew spawn`.

**Stations** are blocks a crew member can man. Only the sail winch is one so far. A crew member at a station stands on
an invisible seat that travels with the ship, so it stays at its post while the ship moves.

The **captain's whistle** (creative tab) gives orders:
- Use it on a crew member, then on a station: the crew member takes that station.
- Use it on an assigned crew member: it is released.
- Use it in the air: opens the order wheel. Point at an order (hoist, reef, furl, release crew) and click, or hold the
  use key, aim and release. The order goes to all crew at stations on the ship you stand on. Esc closes the wheel.

A crew member answers in chat ("Aye, hoisting the sails!") and then works: each trim step takes 2 seconds, and the
sails change when the work is done. A player can still use the winch directly.

A crew member is released when its station is broken, its ship is disassembled or removed, or it dies.

---

The crew member is animated (idle, walking, working at a station, sitting in a boat) through GeckoLib, a required
mod on both sides; it is a Blockbench-made sailor (striped shirt, red bandana, neckerchief, belt and knife, bare feet) with idle, walking, hauling and sitting animations.

### Hiring crew
Every harbor desk has a **Crew** tab. Each day a port has a few people looking for a berth: sailors at seafarer
villages, pirates at pirate islands, navy ratings at navy outposts. Each asks a one-time fee (10, 20 or 15 doubloons)
and then the daily wage. Villagers won't sign on with someone they refuse to trade with. Pirates sign on only with a
friend of the pirates or a captain of some infamy (Buccaneer and up). Navy ratings sign on only with an enlisted
officer. Before you hire, moor your own ship at the port: the recruit walks straight aboard and waits on the deck
nearest the desk. Every crew member needs a free hammock, so hang more hammocks to take on more hands. To let someone
go, sneak and use your captain's whistle on them: they leave your service as an ordinary sailor. Only you or whoever
hired them can do that, or anyone at all if the ship has no owner. Operators: `/pirates crew hire <sailor|pirate|navy>`
and `/pirates crew dismiss`. Server config `crew.hiring`.

### Pirates, sailors and the navy
Pirates (dark coat, bandana, eyepatch, cutlass) attack players and the navy on sight. They fight with the same
swordplay as you: watch for the raised arm before a slash or the drawn-back arm before a thrust, and parry just
before the blow lands to stagger them and riposte. Beware: a pirate sometimes feints, stopping a swing to bait an
early parry and striking while your guard is spent. Navy soldiers (blue coat, white cross belts, tricorn) carry muskets:
they leave you alone unless you are wanted, then keep their distance, aim for a second and fire, and shove you back if
you get close. Officers (gold trim, bicorne) are skilled saber duelists. Pirates and navy fight each other on sight.
Sailors never fight and run from danger. Hitting or killing navy is a crime; killing pirates is not. Pirates drop
doubloons and sometimes a cutlass; navy drop lead shot and gunpowder. Operators spawn them with `/pirates mob spawn <pirate|sailor|navy_soldier|navy_officer>
[count]` or with spawn eggs, and `/pirates mob debug on` traces what nearby duelists decide (it also shows the
difficulty: on peaceful they ignore players, and vanilla zeroes all mob damage to players there, so set
`/difficulty normal` to fight); natural spawning comes with the world structures. Everything is in the `mobs` server
config.

### The kraken
The kraken lurks in the deep ocean and rises beside ships within 32 blocks, more often at night and in storms. Its
tentacles grip the hull near the waterline and drag the ship down, beat masts to splinters and sweep the deck;
swimmers are pulled under. Its eyes are its weak spots (triple damage); 40 damage on a tentacle cuts it and it lets go
until it grows back. Below 30 % health it sinks away. Drops a Kraken Beak and Kraken Ink. Switch it off with
`hazards.kraken.enabled`; tune it under `mobs.kraken`. Operators: `/pirates mob spawn kraken`.

### Sharks
Sharks roam every ocean, cruising a few blocks under the surface. A shark that spots you swimming circles you
before it charges and bites. Wounded swimmers drive it into a blood frenzy, and then it charges straight in. Stay
on deck or in a boat and it leaves you alone; climb out of the water and it loses you at once. If it can't land a
bite for a while, it gives up. Sharks fight back when hurt. Drops: sometimes a cod, rarely a prismarine shard. Server
config `mobs.shark.*`, including `enabled`, `peaceful` and the spawn weight, which takes effect at the next server
start. Operators: `/pirates mob spawn shark` or the spawn egg.

## 5. Flags

The **flagpole** flies a flag that shows a ship's allegiance.

The flag cloth is one block high and one and a half blocks long and hangs downwind from the top of the pole, so a
pole needs free space downwind.

**Tall poles.** Stack flagpoles on each other to build one tall pole, up to 6 blocks (server config
`flags.max_pole_height`; a taller pole is refused). The top block flies the flag: the pole shows its iron cleat at the
foot and its gilded finial at the top. You can use any block of the pole, the flag is always worked at the top.
Placing another flagpole on a pole that flies a flag takes the flag up with it, nothing drops; breaking the top block
drops the flag, breaking a block in the middle splits the pole in two (the upper part keeps the flag). With
`flags.stacked_poles` off every block is a pole of its own.

**The flag runs along the pole.** While you hoist or raise a flag it climbs from the foot of the pole to the top, and
while you strike or take it down it runs down again, over the 3 seconds the work takes. Client config
`flag_visuals.hoist_animation` turns this off (the flag then appears and vanishes at once).

| Flag | Meaning for the law rules |
|---|---|
| none or Merchant Flag | Neutral to everyone. |
| Navy Flag | Navy and merchants friendly, pirates hostile. A false flag if the captain has a bounty or too little navy standing. |
| Jolly Roger | Pirates friendly, navy hostile, merchants may surrender. Being seen under it is a crime. |
| any vanilla banner | A custom flag: neutral. It keeps its patterns, but the pole shows a generic cloth. |

At the pole:
- **Use it with a flag item:** hoists that flag after 3 seconds and gives back the old one.
- **Use it with an empty hand:** strikes the colors (the flag is lowered but kept), or raises them again.

**What the flag does.** The flag your ship flies is the highest flag on its poles. Under the Jolly Roger the navy attacks
everyone aboard on sight and charges you for being seen; pirates leave you alone. A navy flag lets you pass the navy,
unless you are a suspect or worse: then every navy soldier within range may see through your colours. If one does, you
are charged heavily and the navy hunts your ship for five minutes, whatever you fly. Striking your colours surrenders:
navy and pirates stop attacking. Firing on a ship that struck its colours, or on one flying a merchant flag or banner,
is a crime; if your crew fires, the charge goes to you as the ship's owner. Server config `law.flags`.
- **Sneak-use with an empty hand:** takes the flag down.
- Breaking the pole drops the flag.

The flag points downwind, in 90° steps. Nothing reacts to flags in the world yet: there are no navy or pirate ships.

---

Hoist any banner on a flagpole to fly a custom flag: the cloth takes the banner's base colour (its patterns are not
shown).

## 6. Provisions

### Pantry
The doors face you when you place it.
A container with 27 slots that opens like a chest. It is the ship's food store.
- Anything edible counts as food, weighted by its nutrition. One crew member needs 6 nutrition per day (about one
  loaf of bread and a bit).
- **Fresh food spoils** after 5 days in the pantry and turns into rotten flesh. Preserved food never spoils: hardtack,
  salted fish, salt pork, bread, cookies, dried kelp, honey bottles, golden carrots and golden apples.
- Sneak-use it with both hands empty to read what it holds: food, water and rum, and what spoils next.
- **Hoppers** can only put provisions in, and can only take out what is not a provision (empty bottles, bowls, rotten
  flesh). So a hopper below works as a waste chute.

### Water barrel
The water in a placed barrel takes the colour of the water around it (blue at sea, murky in a swamp); the item
shows plain blue water.
Holds up to 16 rations of fresh water. One crew member drinks one ration per day.
- A water bucket adds 3 rations, a water bottle 1. An empty bucket takes 3 out, a glass bottle 1.
- Rain slowly refills a barrel under the open sky.
- The top shows the fill level. A broken barrel keeps its water in the item.

### What the rules do
The rules for a crew eating and drinking exist, but no crew eats yet. Try them with `/pirates provisions`:
- A crew eats perishable food first, the food closest to spoiling before the rest.
- Hungry crew lose morale and work at 75% speed. Thirsty crew lose morale faster and work at 50%. After 3 days of
  hunger or 1 day of thirst the crew is ready to desert.
- **Rum:** a ration raises morale. More than the ration makes the crew slow for a while.
- **Scurvy** sets in after 8 days without fresh food or fruit (apple, melon, berries, lime) and is cured by eating some.

---

### Cold water and swimming
Swimming or wading in frozen or cold oceans and frozen rivers fills the freezing meter like powder snow: after
about 7 seconds you are frozen and take damage every 2 seconds. You are safe in a boat, on a ship's deck or inside
a dry hull, in any piece of leather armour, or after a bottle of rum ("Warm", 2 minutes). Swimming makes you hungry
half again as fast as in vanilla. Server config `survival`.

### Sea chest
Craft it from three leather over iron, chest, iron. It holds 54 stacks and keeps them when broken, carried or
floated. Use it in the air to carry it on your back: you can't jump, sprint or swim, you walk slower, and in water
it drags you under. Place it on water to float it; it drifts with the wind and the current. Use it to open,
sneak-use to pick it up, or hit it to knock it loose as an item. On a ship's deck it is placed as a block. Server
config `sea_chest`.

### Paddling a sea chest
Craft a paddle from two planks and a stick (planks up the right side, the stick bottom left). Use it on a floating
sea chest, from the water or the shore, to sit on the lid. With the paddle in either hand, forward and back paddle
it (about 1.5 blocks a second, half that backwards) and left and right turn it; it still drifts with the wind and
the current, and without the paddle in hand it only drifts. One rider at a time. Paddling makes you hungry like
swimming, and in cold water your legs hang in it, so you freeze as if swimming. Sneak to get off. You can't open
the chest while sitting on it: get off first, then use it. Server config `sea_chest` (`paddle_enabled`,
`paddle_speed`, `paddle_turn_degrees`, `paddle_hunger_factor`).

## 7. Cargo and trade

### Cargo crate and cargo barrel
Bulk containers that hold **one kind of item** in large amounts.

| | Holds |
|---|---|
| Cargo Crate | 32 stacks of the item (2048 sugar, 512 rum) |
| Cargo Barrel | 1536 items, whatever the stack size (better for rum and other small stacks) |

- **Use it with a stack:** puts the stack in.
- **Use it with an empty hand:** takes one stack out.
- **Sneak-use with an empty hand:** puts in everything of that kind from your inventory.
- The action bar shows what it holds. Items that don't stack (tools, filled crates) are refused.
- Plundered and clean goods of the same item never mix in one container.
- A broken container keeps its content in the item, like a shulker box.
- Hoppers work, and other mods' pipes can connect to it and to the pantry.

### Doubloons
The gold doubloon is the currency. It has no recipe: it comes from trade (and later from loot and bounties).
`/pirates trade coins` gives you some for testing.

### Trade goods
Twelve goods have a base price and a weight. They are data-driven (see [Datapacks](#14-datapacks)).

| Good | Item | Base price | Comes from |
|---|---|---|---|
| Sugar | sugar | 2 | tropical ports |
| Fish | cod | 1.5 | temperate and cold ports |
| Timber | oak log | 1 | temperate and cold ports |
| Iron | iron ingot | 6 | cold and arid ports |
| Grain | wheat | 1 | temperate ports |
| Hides | leather | 3 | arid and temperate ports |
| Cocoa | cocoa beans | 4 | tropical ports |
| Gunpowder | gunpowder | 8 | navy outposts |
| Tobacco | tobacco | 5 | tropical and arid ports |
| Spices | spices | 12 | tropical ports |
| Cloth | cloth | 6 | temperate ports |
| Rum | rum | 4 | tropical ports |

### Markets
There are no ports in the world yet. A market is created with `/pirates trade port <name> <kind> <climate>`
(kinds: seafarer village, navy outpost, pirate island).
- A port **produces** some goods (cheap there) and **demands** others (expensive there), depending on its kind and
  climate.
- **Buying raises the price, selling lowers it.** Prices drift back by about 30% of the gap per day.
- Buying and selling in the same port always loses money. Carrying goods from a port that produces them to one that
  demands them pays, but less with every stack.
- You can trade from your inventory or from a cargo container next to you.

### Harbor master's desk
Every port's market is run from a **harbor master's desk** (a book and a gold nugget over three planks, planks below
in the corners). Right-click a desk to open the market screen: the port's name and kind, your doubloons, and every good
the port trades. Green goods are produced here (cheap to buy), orange goods are wanted here (good to sell). Pick a
quantity (1, 8, 16, 64 or type one), then Buy or Sell; the prices shown are totals for that quantity and move as you
trade. The toggle next to the quantity decides whether Sell takes your clean or your plundered stacks: fences on
pirate islands pay less for plunder, village and navy desks refuse it (see "Stolen goods"). The Contracts tab lists today's
delivery offers (Accept pays the deposit) and your accepted contracts (Deliver at the destination port). Stay within 8
blocks of the desk; walking away closes the screen. A desk that belongs to no port says so. Operators bind desks with
`/pirates trade desk bind <port>` while looking at the desk (`<port>` is a full id or a test port name such as `cane`),
check with `/pirates trade desk info`, and `/pirates trade desk unbind`. Server config: Cargo Trade → Harbor Desks
(`desks_enabled`, `desk_reach`). The `/pirates trade` commands remain as a debugging fallback.

**Stolen goods.** Goods taken from captured or sunk ships carry a plunder mark. Only a pirate fence buys them, at a
discount and with no questions. A village or navy harbor master turns them away ("The harbor master wants no stolen
goods") and reports you to the navy: the first time each day at each port adds to your criminal score
(`law.severity.selling_plunder`), and a high enough score puts a bounty on your head. The navy also looks into your
hold: sail within sight of navy soldiers or an outpost with more than 16 marked goods aboard, under any flag, and you
are suspected of piracy once per ship and day. Fence your plunder before you go near the navy. Server config
`law.plunder_notice`, `law.plunder_notice_units`.

### The harbor master
Every port's desk has a harbor master behind it: in the village's dock-head hut, in the navy fort's office, and behind
the fence's counter on pirate islands. He wears a green frock coat and a peaked cap. Right-click him (not sneaking) to
open the port's market, just as using the desk does; he greets you when it opens. If you hit him, or he is running
from pirates, he waves you off ("not now") for a while. He keeps to his post and walks back if pushed away. If he
dies, a new harbor master takes the post after 3 days (`mobs.harbor_master.respawn_days`). With
`cargo_trade.harbor_desks.direct_use` off, the desk only says "Talk to the harbor master" and the market opens
through him. Server config `mobs.harbor_master`.

### Harbor dues
Navy outposts charge a small docking fee (5 doubloons) when your ship ties up at a berth or drops anchor inside the
outpost, once per day per ship. It comes from your purse, or from the doubloons in your ship's chests when your purse
is short. If nobody can pay, the dues are owed: the outpost's harbor desk will not trade with you until you use it
with doubloons in hand. Captains the navy trusts, and navy officers from Lieutenant up, dock for free. Sailing through
the harbor without stopping costs nothing. Server config `cargo_trade.port_fees`.

### Contracts
A port offers delivery contracts: bring an amount of a good to another port by a deadline for a reward. Accepting
takes a deposit of 20% of the reward, and delivering pays the reward and returns the deposit. A player can hold three
at a time.

### Plunder
Goods can carry a **plundered** mark (shown in the tooltip). A pirate island buys them at 35% less, no questions asked.
Village and navy desks turn them away and report you (see "Stolen goods" above). Goods taken from an NPC ship's
hold carry the mark; operators can mark stacks with `/pirates trade plunder`.

---

## 8. Law, bounties and the brig

### Criminal score
Every player and mob has a score. Crimes raise it:

| Crime | Points |
|---|---|
| Theft from a village chest | 5 |
| Attacking a villager | 5 |
| Attacking the navy | 10 |
| Seen under the Jolly Roger | 10 |
| Attacking a neutral ship, press-ganging, suspected piracy (plunder seen aboard) | 15 |
| Offering stolen goods at a village or navy desk | 17 |
| Killing a villager | 20 |
| Killing a navy member | 30 |
| Caught under false colors, attacking a ship that struck its colors | 40 |
| Piracy (capturing a ship) | 50 |
| Desertion | 60 |

- The score makes you **suspect** from 10, **wanted** from 50 and **notorious** from 200.
- It goes down by 10 points per in-game day, starting 5 minutes after your last crime.
- Hitting the same victim again within 30 seconds doesn't count twice.
- A fine costs 3 doubloons per point (only through `/pirates law fine` for now). Notorious criminals can't pay.

What is detected in the world today:
- **Hurting or killing villagers and wandering traders**, also with arrows or by your pets.
- **Theft:** taking items out of a container that stands in a village and that no player placed, while a villager
  can see you. Putting items in is fine.
- The other crimes need ships, flags or the navy to react, and are reached through `/pirates law crime`.

### Fines, ransom, press-gang and release
Pay your fine at a navy officer: hold doubloons and right-click him. Each criminal-score point costs 3 doubloons by
default, and he takes only the whole points you can afford; he won't deal with wanted criminals. Lead a shackled navy
officer, navy soldier or merchant (sailor, villager, trader) to a navy officer and right-click him with an empty hand
to ransom them. Hold the captain's whistle and right-click a shackled sailor standing on your own ship to press-gang
them into your crew (low morale, and a crime). Sneak and right-click your own prisoner with an empty hand to let them
go. Server config `law.officer_fines`, `law.ransom_needs_port`, `flags_brig.prisoner_interactions`.

### Bounties
- At a score of 50 the navy puts a **bounty** on you of twice your score. It grows with the score and is withdrawn
  when the score falls below 25.
- Players can add bounties on anyone (`/pirates law bounty place`, 10 doubloons or more).
- **Claiming:** killing a target with a bounty puts a **Bounty Proof** into the killer's inventory. Handing it in pays
  the bounty. Delivering the target alive pays 1.5 times as much. A claim wipes the target's score.
- **Turning in:** right-click a navy officer with a Bounty Proof to collect the bounty in doubloons. To deliver
  prisoners alive, walk up to an officer with your shackled prisoners within 4 blocks and right-click him with an
  empty hand: a bounty pays 1.5 times, and a captured pirate earns 10 doubloons on top. The navy leads NPC prisoners
  away; a captured player is set free on the spot with a clean record. You get your shackles back. Officers won't
  deal with you while the navy hunts you.
- **Notice board:** craft it from planks and paper (planks, paper, planks, twice). Use it to see every bounty: who is
  wanted, for how much, who placed it and when, and whether there is a bounty on you. To place one, type a name (an
  online player or anyone already on the board, or click a suggested name), enter at least 10 doubloons and press
  Place; the doubloons come out of your inventory. The list updates while it is open.

### Shackles and prisoners
- **Use shackles on a mob** that is at 25% health or less: it becomes your prisoner. It stops fighting and can't
  despawn. Bosses can't be captured. Players can only be captured if the server allows it and they have a bounty.
- **Use shackles on your prisoner** to lead it or let go. A led prisoner follows you like a mob on a lead.
- A prisoner left alone outside a cell tries to escape (about once per 10 minutes on average).

### Brig bars and brig door
Locking and unlocking a brig door needs a **Brig Key** (an iron ingot over an iron nugget). Use the key on the door:
"Brig door locked" with a padlock, again to unlock. Any key works on any brig door, so keep them from your prisoners.
The owner of a door still opens it without a key; nobody else, no mob and no redstone can. Bars connect to the door.

- **Brig bars** connect like iron bars.
- The **brig door** belongs to the player who placed it. Sneak-use it with an empty hand to lock or unlock it. A locked
  door opens only for its owner and ignores redstone.
- A prisoner is **in a cell** when it stands in a closed space of at most 64 blocks, bounded by solid blocks, brig bars
  and a closed, locked brig door. There it stays, and it doesn't try to escape.

What you can do with a prisoner (deliver at a navy officer; the rest through `/pirates brig`): deliver it for its bounty,
ransom it, press-gang it (a crime), or release it.

---

## 9. Weapons and combat

| Item | Today |
|---|---|
| Rapier | A sword: 5 damage, attack speed 2.0 |
| Cutlass | A sword: 7 damage, attack speed 1.2 |
| Saber | A sword: 6 damage, attack speed 1.6 |
| Pistol, Musket | No function yet |
| Lead Shot, Cannonball | No function yet |
| Grappling Hook | No function yet |

The swords work like vanilla swords for now. The skill-based fighting system (slash, thrust, guard, parry, riposte,
stamina) is implemented on the server with per-weapon values in `data/pirates_n_ships/pirates_n_ships/weapon/`, but
nothing lets a player use it yet: the input and animation layers are missing.

---

### Firearms
The pistol and the musket are single-shot flintlocks. Hold right-click with one lead shot and one gunpowder in your
inventory to load (3 seconds for the pistol, 5 for the musket, with the bow pose; letting go early cancels and costs
nothing; creative mode needs no ammo). The tooltip shows "Loaded" or "Unloaded". With a loaded gun, hold right-click to aim (a quick click still fires at once): after a second of steady aiming the
shot is tighter, the musket zooms in a little, and the shot leaves when you release. To lower an aimed gun without firing, press sneak: the gun goes down still
loaded and stays down while you keep sneaking (server option `firearms.aim.lower_on_sneak`). While you load, a white
bar under the gun's slot fills up; a loaded gun shows a full gold bar in the hotbar and inventory, its hammer cocked back, and its tooltip
says "Loaded" or "Not loaded". You see yourself aim and reload (the Player Animation Library drives it, client option
`firearm_animations.enabled`). Firing: a lead ball flies out with smoke and a small kick, the gun is unloaded again and needs half a second before it
can be used. The pistol hits hard but scatters; the musket flies flatter and tighter. Standing in the rain, a quarter of
the shots misfire with a click and the charge stays in. Without ammo the gun only clicks. Shooting someone counts as an
attack for the law, and the death message names you. Everything is in the `firearms` server config (and the misfire
chance in `combat`).

### Cannons
Craft a cannon (2 iron ingots, 1 iron block, 2 logs, 1 planks) and place it on a deck or on land; the muzzle points
the way you face. **Load** it by using it with gunpowder, then with a cannonball (powder first, always). **Aim** by
sneaking and using it with an empty hand: the upper half of the block raises the barrel, the lower half lowers it,
from −5° to +20° in 5° steps (the action bar shows the angle). **Fire** by using it with an empty hand. The barrel
needs 5 seconds to cool before new powder goes in. A ball hits for 20 damage and smashes the wooden block it hits; a
hole below a ship's waterline lets the sea in, so pump and patch. From a moving ship the shot carries the ship's speed,
and each shot pushes your ship back a little; a hit pushes the other ship. Balls that hit water splash and sink. A crew
member at a cannon fires it on the whistle order **Fire!** (or `/pirates crew order fire`) once you have loaded it:
"Aye, firing!", and the gun goes off half a second later; crew at unloaded guns answer "The gun is not loaded,
captain!". Server options: section `cannons` (on/off, damage, speed,
gravity, reload, elevations, blocks per hit, recoil and impact push) and `combat.cannon_block_damage`.

Placing a cannon: click the deck block where the **front** of the carriage should stand; the rear takes the block
toward you and the barrel overhangs one block ahead, so click the spot by the rail. Both halves need a block
underneath. Load, aim and fire on either half; breaking either half gives the whole cannon back.

Cannonballs obey the world's rules: with `mobGriefing` off or inside the spawn protection nothing breaks. Blocks
they smash drop their items, a gun you break gives back its powder and shot, and a ball that grazes a hull at a
shallow angle pings off instead of breaking it; a square hit does the most damage.

### Swivel gun
A small gun on a yoke (three iron ingots over a stick) that mounts on a fence, wall, iron bars, brig bars or any
full block. Load gunpowder, then a cannonball (or lead shot if the server says so). Hold right-click with an empty
hand: the gun turns wherever you look while you hold, up to 45° up and 30° down; let go to fire. Sneak-use tells
you what it needs. It hits less hard and less far than the cannon and breaks no planks unless the server allows it;
it reloads in three seconds. Crew assigned to it fire it on the whistle's "Fire!".

Crew at a gun can also load it: put gunpowder and cannonballs (or the swivel's shot) in a chest, barrel or cargo
crate within 4 blocks of the gun on the same ship, then blow "Load!" on the whistle. After every "Fire!" the crew
reloads by itself from that supply, so a manned, supplied gun keeps firing as fast as it cools down (server config
`cannons.crew`).

### Grappling hook
**Shooting the hook.** Put the grappling hook in your **off hand** and a musket in your main hand. Hold use to load
the hook into the musket (its full reload and one gunpowder), then aim and let go to fire it: a flat shot on a 64-block
rope. Thrown by hand, the hook's rope is 32 blocks. The hook catches on any solid surface: another ship (the rope hauls
both ships together), your own ship (a line to slide down, e.g. from the mast top), or land (from a ship it slowly
hauls your ship toward that point like a kedge; from land it is a zip line). It slips off leaves and glass panes. To
slide, look at the rope with an empty hand (or a hook in it) and use it; with a musket in hand, use always works the
musket. Left-handed players can turn off `grapple.launch.offhand_required` to swap the hands.

**Cleats** work like mooring rings: a hook flying close to a cleat catches on it, and using a cleat on your ship
while your hook is out ties the rope off there.

**Mooring rings** (4 iron ingots make 2) mount on decks, walls or beams. A hook that flies within a block of a ring
on another ship catches on it and holds twice as far before tearing loose. With your hook latched, use a ring on
your own ship to tie the rope there: the ships keep hauling together and you can walk away. Sneak and use with an
empty hand, or breaking a ring, lets go.

Right-click to throw the hook. If it hits the hull of another ship it bites in and hangs there, following the ship.
If you stand on your own ship, the taut rope hauls both ships together until they lie side by side, ready for
boarding; then it goes slack. From land the rope drags the hooked ship slowly toward you. A hook that hits your own
ship, land, water or a creature does not hold (a creature takes a small hit) and comes back after two seconds. To let
go, sneak and right-click with an empty hand. More than 24 blocks from the hook the rope snaps and the hook comes back
(or is lost, if the server says so). Throwing a second hook releases the first. Server options: section `grapple`.

### Swordplay
Swords make themselves heard, one sound per attack: a miss whooshes (lower for a thrust), a hit slices flesh and a
thrust bites harder, a chestplate rings, and blade on blade clangs, loudly for a parry, dully for a blocked blow
or when you catch an opponent mid-swing. Drawing a sword plays a short scrape. Server owners turn sword sounds off or change
their volume under `melee.sounds`.

With a rapier, cutlass or saber in hand the mouse works differently (server config `melee.skill_based_combat`,
on by default; vanilla weapons are untouched):
- **Slash:** a quick left click. A wide, short arc after a brief wind-up.
- **Thrust:** hold left click about half a second and release. Narrow, long reach, more damage, slow to recover if it
  misses.
- **Guard:** hold right click. It blocks every hit from the front completely, but each blocked blow costs stamina,
  and heavier blows cost more. When you can't pay for a blow, your guard breaks: the hit gets through, softened by
  your blade, and you stagger. Hits from behind or the side ignore your guard.
- **Parry:** a quick right tap just before a hit lands. The hit is deflected, the attacker staggers, and for about a
  second you may **riposte** (attack for bonus damage). A parry with no hit coming costs stamina and locks parrying
  briefly.
- **Stamina:** a brass-framed bar while you hold a sword, above the food row on the right (or left of the hotbar
  with the client option `melee_hud.position`); it fades out when full and comes back the moment you fight. Attacks, guarding and failed parries drain
  it; it refills after a moment of rest. Empty, you can neither guard nor parry and stagger easily. The bar turns
  violet while you are staggered and shows a grey block during the parry lockout.
- Right click is taken over while a mod sword is held, so swap to another item to open doors or use the helm.
- Your attacks, guard, parry and stagger are animated in first and third person through the Player Animation
  Library (a required client mod); the animations are authored in Blockbench (`art/animations/`). Client config
  `melee_animations`: on/off, first person auto/on/off (auto steps back for camera mods), layer priority.
- Client config `melee_input` (tap and hold thresholds) and `melee_hud` (bar on/off, scale, offsets).

## 10. All blocks

| Block | Recipe | What it does |
|---|---|---|
| Helm | 4 sticks, 1 planks | Assembles, steers and disassembles a ship. A real ship's wheel on a pedestal (Blockbench model). See [Ships](#2-ships) and [Sailing](#3-sailing). |
| Yard | 3 logs in a row (gives 3) | A spar. Two rows on one mast make a square sail; the cloth is drawn between them. See [Sails](#sails). |
| Cleat | iron ingot, planks | Attaches to floors, walls and masts. Three cleats and a rope make a triangular sail. See [Sails](#sails). |
| Sail Winch | 2 string, 1 iron ingot, 3 planks | Sets the trim of all sails on its ship. A crew station. The crank faces you when placed. |
| Capstan | 2 logs, 1 stick, 2 chains, 1 iron block, 3 planks | Drops and raises the anchor. |
| Harbor Master's Desk | book, gold nugget, 5 planks | Opens a port's market screen when bound to the port. See [Harbor master's desk](#harbor-masters-desk). |
| Cannon | 2 iron ingots, 1 iron block, 2 logs, 1 planks | Loads powder and a cannonball, aims by elevation, fires. A crew station. See [Cannons](#cannons). |
| Bilge Pump | stick, 3 planks, 1 bucket, 1 plank | Pumps water out of the hold below it. A crew station. See [Fighting a leak](#fighting-a-leak). |
| Hull Patch (block) | placed by the item | A tarred plank that closes a breach. Watertight hull block. |
| Flagpole | 3 sticks (gives 2) | Flies a flag. A thin pole with a finial and a cleat (Blockbench model). See [Flags](#5-flags). |
| Pantry | 8 planks, 1 wheat | Food store with spoilage. See [Provisions](#6-provisions). |
| Water Barrel | 6 planks, 2 iron nuggets, 1 water bucket | Holds 16 rations of water. Crafted full. |
| Cargo Crate | 4 planks, 4 sticks | Bulk container for 32 stacks of one item. |
| Cargo Barrel | 6 planks, 2 iron nuggets | Bulk container for 1536 items of one kind. |
| Brig Bars | 4 iron bars, 2 planks (gives 6) | Bars for cells. |
| Brig Door | 4 iron ingots, 2 iron bars | Lockable door for cells. |
| Notice Board | 4 planks, 2 paper | Lists every bounty and places new ones for doubloons. |
| Mermaid, Lion, Eagle and Skull Figurehead | 4 planks and a prismarine shard, gold ingot, feather or bone | Decoration for the bow. Click the hull block it should hang on: the plate lands there and the figure looks at you. |
| Nameplate | any sign, 1 gold nugget | A board on two iron brackets that shows the ship's name once the ship is assembled and named (name tag on the helm); long names shrink to fit, renaming updates every plate within a second, disassembly clears them. Server option `ship_identity.nameplate_shows_name`. |

All blocks drop themselves. Wooden ones are mined with an axe, the bars and the door with a pickaxe.

---

### Hats
Wear a pirate hat, bandana, navy tricorn or officer's bicorne by right-clicking with it or putting it in the
helmet slot. Each gives +1 armour (server config `apparel.hat_armor`; 0 turns it off). Crafted from three black wool
over leather, bone, leather (pirate hat), leather, white wool, leather (navy tricorn) or leather, gold nugget, leather
(officer's bicorne); red wool, string, red wool for the bandana.

## 11. All items

| Item | Recipe | What it does |
|---|---|---|
| Rapier | 2 iron ingots, 1 stick | Sword. See [Weapons](#9-weapons-and-combat). |
| Cutlass | 3 iron ingots, 1 stick | Sword. |
| Saber | 2 iron ingots, 1 gold ingot, 1 stick | Sword. |
| Pistol | 2 iron ingots, 1 flint, 1 planks | Single-shot flintlock: load with lead shot and gunpowder, fire. See [Firearms](#firearms). |
| Musket | 2 iron ingots, 1 flint, 1 planks | Longer reload, flatter and tighter shot. See [Firearms](#firearms). |
| Lead Shot | 2 iron nuggets (gives 4) | Ammunition for pistol and musket, one per load, with one gunpowder. |
| Cannonball | 4 iron ingots (gives 2) | Ammunition for the cannon, loaded after the gunpowder. |
| Grappling Hook | 3 iron ingots, 1 string | Throw it at another ship to hook it and haul the hulls together. See [Grappling hook](#grappling-hook). |
| Rope | string | Use it on a cleat or mooring ring, then on a second one up to 16 blocks away on the same ship: a stay (2+ blocks lower, cleats only) or a decorative rope line. It glints while it remembers the first anchor. |
| Hull Patch | 2 planks, 1 coal or charcoal (gives 2) | Use on the edge of a hole in an assembled hull to close the breach. See [Fighting a leak](#fighting-a-leak). |
| Doubloon | none | Currency. |
| Tobacco | none | Trade good. |
| Spices | none | Trade good. |
| Cloth | 3 string | Trade good. |
| Rum | 1 sugar cane, 2 sugar, 1 glass bottle | Trade good and provision. Drinkable, leaves a bottle. |
| Hardtack | 3 wheat, 1 water bucket (gives 2) | Food (4 nutrition) that never spoils. |
| Salted Fish | 1 cod or salmon, 1 dried kelp | Food (5 nutrition) that never spoils. |
| Salt Pork | 1 porkchop, 1 dried kelp | Food (6 nutrition) that never spoils. |
| Lime | none | Food (3 nutrition) that prevents scurvy. |
| Shackles | 2 iron ingots, 1 chain | Captures weakened mobs. See [the brig](#8-law-bounties-and-the-brig). |
| Merchant Flag | 1 stick, 2 white wool, 1 red dye | Flag for the flagpole. |
| Navy Flag | 1 stick, 2 blue wool, 1 white dye | Flag for the flagpole. |
| Jolly Roger | 1 stick, 2 black wool, 1 bone | Flag for the flagpole. |
| Chart | paper, leather, feather | Opens your own chart (coastlines, markers). |
| Bounty Proof | none | Given for killing a target with a bounty. Hand it in for the reward. |
| Captain's Whistle | none | Assigns crew to stations and gives orders from a radial menu. See [Crew](#4-crew). |

Everything is in the "Pirates 'n' Ships" creative tab. Items without a recipe are meant to come from loot, trade or
NPCs later.

---

## 12. Commands

All commands need operator rights (permission level 2). They exist so that features can be tried before the NPCs,
ports and screens that will normally drive them exist.

| Command | What it does |
|---|---|
| `/pirates wind get` | Shows the wind. |
| `/pirates wind set <fromDegrees> <strength>` | Fixes the wind: the compass bearing it blows from, and blocks per second. |
| `/pirates wind clear` | Returns to the natural wind. |
| `/pirates ship forces` | For the ship you stand on: speed, heading, wind angle, how deep it sits, rudder and anchor, and every force on it. |
| `/pirates crew spawn` | Spawns a crew member. |
| `/pirates crew assign <crew> <station>` | Puts a crew member at a station. |
| `/pirates crew release <crew>` | Releases crew from their stations. |
| `/pirates crew order <hoist\|reef\|furl> [crew]` | Gives a sail order. |
| `/pirates flag get\|strike\|raise <pos>`, `/pirates flag set <pos> <kind>` | Reads or changes a flagpole without the delay. |
| `/pirates provisions show <crew> [pos]` | What the pantry you look at holds, and how many days it feeds that crew. |
| `/pirates provisions advance <days> <crew> [prisoners] [rum] [pos]` | Lets that crew live off the pantry for some days and prints what happened. |
| `/pirates ship templates` / `/pirates ship place <template> [force] [assemble]` | Lists the prebuilt ships; puts one on the water in front of you, bow away from you, optionally assembled (operators). |
| `/pirates trade port <name> <kind> <climate>` | Creates a test port. |
| `/pirates trade open\|goods\|buy\|sell …` | Opens a market, lists prices, buys and sells with real coins and items. |
| `/pirates trade contracts <from> <to>`, `/pirates trade contract list\|accept\|deliver` | Delivery contracts between two test ports. |
| `/pirates trade weight` | Cargo weight of the container you look at. |
| `/pirates trade plunder` | Marks the stack in your hand as plundered. |
| `/pirates trade coins` | Gives doubloons. |
| `/pirates law score get\|set\|add <target> …` | Reads or changes a criminal score. |
| `/pirates law crime <target> <crime>` | Reports a crime. |
| `/pirates law last <target>`, `/pirates law hostile <target>` | The last reported crime, and whether the navy would attack. |
| `/pirates law fine <target> <doubloons>` | Pays a fine. |
| `/pirates law bounty list\|place\|claim\|clear …` | Bounties. `claim proof` uses the Bounty Proof in your hand. |
| `/pirates brig list\|capture\|deliver\|ransom\|pressgang\|release …` | Prisoners. |

The playtest checklists in [`playtests/`](playtests/) show each command in use.

---

## 13. Configuration

Open it in game under Mods → Pirates 'n' Ships → Config. Gameplay settings are in the server config, which is synced to
clients. Every feature has a switch and every strength or rate has a value.

| Section | What it controls |
|---|---|
| `assembly` | Assembly on/off, block limit, how still and level a ship must be to disassemble, water handling. |
| `dry_hull` | Dry hull on/off, buoyancy of the dry volume, weight of flood water. |
| `flooding` | Flooding on/off, inflow rate; bilge pump on/off, rate, reach, use time and exhaustion; hull patch on/off. |
| `wind` | Wind strength range, how fast it changes, weather multipliers, gusts, regional variation. |
| `sailing` | Sail force, rudder strength and turning authority (`rudder_force_factor`), keel drag, anchor strength, roll and pitch damping. |
| `anchor_chain` | Chain speeds, travel time limits, anchor sounds and volumes. |
| `hull_creaking` | Creaking on/off, how often, volume and pitch ranges, the rolling rate that counts. |
| `audio` (client) | Music on/off and volume, the gap between tracks, shanties aboard. |
| `grapple.launch` | Musket launch on/off, speed, gravity factor and rope length, `offhand_required`. |
| `grapple` | Grappling hook on/off, throw speed, rope length, haul force and damping, hold distance and slack, shore pull, entity damage, lost-hook rule, `latch_world_blocks`, `latch_own_ship`, `slide.grab_cooldown_ticks`. |
| `cannons` | Cannons on/off, damage, muzzle speed, gravity, reload, elevation range and steps, blocks per hit, recoil and impact impulses, ball lifetime and water behaviour, `mobGriefing` and spawn protection, drops from destroyed blocks, glancing hits and the bounce angle. |
| `cannons.swivel` | Swivel gun on/off, ammo item and count, damage, muzzle speed, reload, blocks per hit, recoil and impact impulses, ball lifetime, elevation limits, aim reach. |
| `mobs` | Mob types on/off and peaceful, hostility toggles, detection and fight ranges, skill tiers, musket timings and ammo, shove, drops. |
| `hazards` / `hazard_visuals` (client) | Waterspouts and whirlpools on/off, spawn chances and interval, distance band, lifetimes, radii, pull, lift, spin, drag-down, drift, sail tearing, ship force scale and mass cap; particle density and sounds. |
| `mobs.kraken` / `hazards.kraken` | Kraken on/off and chance per day; detection, grips, tentacle health and regrow, weak spots, strike and swipe intervals, damage, retreat. |
| `chart` / `chart_visuals` (client) | Charts on/off, cell size, sampling radius and interval, shallow depth, the cell cap, opening without the item, other players visible, marker cap; doodles. |
| `mobs.shark` | Shark on/off and peaceful, spawn weight and group (server restart), detection, circle and give-up times, bite cooldown, damage and knockback, frenzy threshold. |
| `melee_hud` (client) | Stamina bar on/off, position (tight above the hotbar or left of it), scale, offsets, opacity, fade when full and its timing. |
| `melee_animations` (client) | Sword animations on/off, first-person mode, layer priority. |
| `melee_input` / `melee_hud` (client) | Hold-to-thrust and parry-tap thresholds; stamina bar on/off, scale and offsets. |
| `firearms.aim` / `firearm_view` (client) | Minimum hold, steady time and aimed spread factor, sneak lowers the gun; musket zoom. |
| `firearms` | Firearms on/off, per gun: damage, muzzle velocity, spread, reload time, recoil; ball lifetime and gravity, cooldown, gunpowder use. |
| `sailing_runtime` | Sailing forces on/off, heel scaling, steering and anchor on/off, rudder steps, chain length. |
| `crew_stations` | Crew stations on/off, time per trim step. |
| `flags` | Hoisting delay, flags following the wind at its exact angle (land and ship check intervals), banners as flags. |
| `dry_hull` | Also: whether slabs, stairs and hatches are drawn dry in their empty half. |
| `sea_chest` | Sea chest on/off, worn speed, sink pull, wind drift and its cap, draft; paddling on/off, speed, backing speed, turn rate, hunger. |
| `survival` | Cold water on/off and freeze rate, warm effect length, swimming hunger multiplier. |
| `provisions` | Consumption, rations, spoilage, scurvy, rum, water barrel capacity, rain refill. |
| `cargo_trade.market_backend` | Desk reach, maximum trade quantity, refresh interval of open market screens. |
| `cargo_trade` | Container sizes, prices, price recovery, contracts, plunder on/off and the fence's discount, port fees, cargo weight. |
| `law` | Criminal score, severity of each crime, decay, fines, bounties, crime detection, theft; `law.bounty`: officer turn-ins on/off and delivery range, notice boards on/off and reach. |
| `flags_brig` | False-colors detection, NPC surrender, capturing players, prisoner escapes. |
| `brig` | Capture threshold, leading distances, cell size, escape chance, ransom. |
| `melee` | Skill-based sword fighting: parry window, stamina, stagger, feint recovery, NPC feints on/off, sword sounds on/off and volume. |
| `core` | Debug logging. |
| `ships`, `waves`, `hazards`, `crew`, `combat`, `survival`, `world`, `world_simulation`, and the client sections `audio` and `wave_effects` | Settings for features that are not built yet. They do nothing so far. |

Many defaults are first guesses that need playtesting. [`progress.md`](progress.md) lists the ones to review.

---

## 14. Datapacks

**Definitions** are JSON files under `data/<namespace>/pirates_n_ships/<type>/<name>.json`. A datapack can add entries
in its own namespace, or replace one of ours by shipping a file at the same path. A broken file is logged and skipped.

| Type | Folder | Contents |
|---|---|---|
| Ship templates | `ship_template` | Structure id, name key, helm position, waterline row, bow direction, price (for the shipwright later). The structures come from WorldEdit schematics in `art/schematics/` through `tools/schem_to_structure.py`. |
| Trade goods | `trade_good` | Item, base price, weight, category, where it is produced. |
| Weapons | `weapon` | Timings, damage, reach, arc, stamina costs and guard values for the fighting system. A weapon whose name matches an item id applies to that item. |

**Tags** you can extend:

| Tag | Effect |
|---|---|
| block `pirates_n_ships:terrain` | Never part of a ship. |
| block `pirates_n_ships:never_assemble` | Never part of a ship. |
| block `pirates_n_ships:watertight`, `not_watertight` | Overrides whether a block keeps water out. |
| item `pirates_n_ships:provisions/preserved` | Food that never spoils. |
| item `pirates_n_ships:provisions/anti_scurvy` | Food that prevents scurvy. |
| item `pirates_n_ships:provisions/rum`, `fresh_water`, `water_barrel` | What counts as rum and as water. |
| item `pirates_n_ships:provisions/excluded` | Food the crew won't eat (rotten flesh and similar). |
| item `pirates_n_ships:flags` | Items that can be hoisted. |
| entity `pirates_n_ships:law_protected` | Hurting these is a crime (villagers, wandering traders). |
| entity `pirates_n_ships:navy` | Navy members. Empty until navy mobs exist. |
| entity `pirates_n_ships:law_enforcers` | Never get a criminal record (iron golems, the navy). |
| entity `pirates_n_ships:not_capturable` | Can't be shackled (bosses). |

Sable reads block weight from its own tags. The mod's blocks are already sorted into `sable:light`, `sable:heavy` and
the like.

---

## 15. What does not exist yet

So that nobody looks for it:
- **World:** pirate islands, seafarer villages, navy outposts, wrecks, treasure maps, ports with positions and harbor
  master NPCs (desks are bound by command until then).
- **Mobs:** pirates, sailors, navy soldiers and officers, sharks, the kraken. The only NPC is the test crew member.
- **Crew life:** hiring, wages, morale, skills, crew eating from the pantry, crew loading cannons.
- **Combat:** sword animations (the input, rules and stamina HUD exist), boarding planks, chain and grapeshot, crimes
  for shooting another ship's crew.
- **Ship extras:** a water surface inside flooding rooms, waves, oars, cargo weight slowing a ship, shipwright orders,
  dyeable sails, a ship's allegiance from its flags.
- **Screens and HUD:** a wind indicator, ship status, a wanted display. The data for them is already sent to the
  client.
- **The Fabric version.**

The roadmap is in [`design.md` §20](design.md#20-roadmap).
