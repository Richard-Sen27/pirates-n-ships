# Pirates 'n' Ships: how it works

> **This is the testing and reference guide**, for the people who build and test the mod: it names config keys,
> commands and test notes. The player's in-game guide, the Captain's Almanac, is its own text in the mod's voice and
> lives in [`almanac/`](almanac/) (format: [`almanac.md`](almanac.md)); a mechanic that changes here must be changed
> there too.

A guide to everything that exists in the mod today: what each block, item and system does and how to use it.
It describes the code on `main`, not the plans. For the plans see [`design.md`](design.md), and for the state of
the work see [`progress.md`](progress.md).

> **Status: prototype.** Everything below is implemented and covered by automated tests on a headless server.
> Nobody has played it in a client yet. How things look and feel (ships floating, water inside hulls, sailing speed,
> crew on deck) is unverified until the playtests in [`playtests/`](playtests/) are done.
> The operator commands under `/pirates` reach every system directly, for testing; in a normal game the ports,
> the navy, the hiring desks and the NPC ships drive them.
> Numbers in texts (action bar, tooltips, screens, command replies) always use a dot, independent of the system
> locale: a German system reads "Too long: 6.3 of 8 blocks", never "6,3".

Requirements: Minecraft 1.21.1, NeoForge or Fabric, the [Sable](https://github.com/ryanhcode/sable) mod, which
provides the physics for moving ships, GeckoLib (the animated mobs) and, on the client, the Player Animation Library.

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
The in-game guide is the **Captain's Almanac**, a leather-bound tome with brass corners. It works the same on NeoForge
and Fabric and needs no other mod.

- **Getting one:** every player gets one Almanac the first time they join a world (server config
  `almanac.give_on_first_join`, on by default; once per player, so a lost book is not replaced, and while the switch
  is off nothing is given and the book comes at the first login after it is turned on). More are crafted shapeless
  from a book, two leather and two copper ingots; the recipe unlocks once you hold a book.
- **Reading in hand:** use the Almanac (right-click) to open it.
- **Placing it:** sneak-use it on top of a block to set the tome down, on any surface and also underwater. It lies
  closed until someone right-clicks it to read; then it opens with a page flip and stays open while they read and a
  few seconds after (`almanac.open_ticks`). Breaking it drops the book. With `almanac.enabled` off the Almanac is
  decoration and never opens.
- **The index (ALM8):** the first opening after joining a world shows the index spread: on the left page every
  chapter's icon and title in book order, in its bookmark colour (click one to open the chapter, hover for the full
  title), on the right the book's foreword and "Continue where you left off:" with a button to the chapter and page
  last read (a player who has read nothing gets "New aboard? Begin here:" and the first chapter). Later openings in
  the same session go straight to the page last read; Home turns back to the index, and so does turning back past the
  first chapter. The index has no bookmark and no page numbers.
- **Turning pages:** the arrows in the page corners, the arrow keys, Page Up and Page Down or the mouse wheel. The
  bookmarks down the left edge (continuing down the right edge when the left is full) jump to a chapter; links in the
  text jump to other chapters. Escape closes the book. The page you last read is remembered, also after death and
  relog (`almanac.remember_page`).
- **Recipe panels and spotlights (ALM8):** where a chapter introduces a craftable block or item it shows its crafting
  grid: the 3x3 inputs, an arrow and the result with its count, a two-arrow mark on shapeless recipes, the inputs of a
  tag cycling once a second, a tooltip on every slot. The panel reads the recipe the server sent, so a datapack that
  changes a recipe changes the panel; a recipe that does not exist shows "This recipe is not in the world". Every
  chapter opens with its picture and an item spotlight: the item framed at twice its size with its name under it.
- **Show me:** some pages have a "Show me" button that plays a Ponder scene of the mechanic (17 scenes: assembling a
  ship, steering, sails and the winch, anchor and capstan, pumps and flooding, hull repair, mooring, cargo and weight,
  loading and firing a cannon, crew orders, boarding, flags and allegiance, the crow's nest, the ship's bell, galley
  and provisions, hammocks and rest, the swivel gun). The back button returns to the same page. The same scene opens
  from the item: hold the Ponder key (W by default) while hovering the helm, sail winch, capstan, bilge pump, hull
  patch, mooring ring, cargo crate, cannon, captain's whistle, boarding plank, Jolly Roger, crow's nest, ship's bell,
  pantry, hammock or swivel gun in your inventory.
- **JEI:** with JEI (Just Enough Items) installed, every block and item the Almanac describes has an information
  page in JEI with that text (R or U over it), and JEI gains two pages of the mod's own: the Harbor Market (each
  trade good with its buy and sell prices) and Ship's Provisions (what the crew counts as food, water or rum).

This file is the testing and reference guide: it lists every block, item, command and config value for testers and
server owners. The Almanac's chapters are a separate text written for players (`docs/almanac/`, generated into the
book with `python3 tools/gen_almanac.py` and `./gradlew :neoforge:runData`). Since ALM8 the chapters are short (half
their former length): exact numbers and edge cases live here. The chapter source's block markup for the new pieces is
a line of its own: `{recipe:<id>}` (one of our recipes; the generator stops on an id that is not in the generated
recipes, and every recipe of ours has exactly one panel in the book) and `{spotlight:<item id>}` (format in
`docs/almanac.md`).

### What the Almanac no longer spells out (ALM8)

The content pass moved these details out of the player's chapters; everything else it cut is stated in the sections
below.

- **Ships:** very far from the world's centre (tens of thousands of blocks out) ships stop moving true (floating-point
  precision). A hull patch used anywhere but a real hole of an assembled ship says so and is not spent.
- **Steering:** the line above the hotbar shows the rudder angle; let go of the wheel and the rudder stays where you
  left it.
- **Sails:** a yard is at most 15 blocks long (`sailing.sails.yard_max_length`); a longer row of yard blocks is no
  yard and carries no sail.
- **Mooring:** a harbour counts a ship as moored at its port once she lies still (anchored) within about 32 blocks of
  it.
- **Crew:** morale runs from 0 to 100, a new hand comes aboard at 70 (`crew.morale.start`). Hunger costs a hand 10
  morale a day, thirst 25 (`provisions.hunger_morale_per_day`, `thirst_morale_per_day`); scurvy lowers it every day
  until cured; a rum ration raises it a little each day. To hire, your own ship must lie within 32 blocks of the port
  (`crew.hiring.ship_radius`). A hand speaks up only when he cannot carry out an order ("This ship has no sails,
  captain!").
- **Provisions:** the crew refuses foul fare as rations (rotten flesh, spider eyes and the like). Water and rum count
  wherever they are stowed aboard, in a pantry or a water barrel. Prisoners in the brig eat and drink half a hand's
  share and get no rum.
- **Settings:** `flooding.pump_reach` 4, `crew_stations.ticks_per_trim_step` 40 (the 2-second trim step). A datapack
  can add chapters to the Almanac or replace ours (`data/<ns>/almanac/chapters/`, format in `docs/almanac.md`).

## 1. Your first ship

1. **Build a hull** from any blocks, floating in water. Planks, slabs, stairs and glass are watertight. Leave a
   one-block gap to the shore or a dock: anything that touches the hull becomes part of the ship.
   The ship floats level as you built it (the auto-ballast, see [Heel](#heel)), so no heavy bottom is needed. Raw
   stone, dirt, sand and the like are terrain and never become part of a ship (see [Assembly](#assembly-at-the-helm)).
   The blocks of this mod that are not full cubes (cleats, cannons, yards, lanterns, pumps and the like; all but the
   brig door) can be waterlogged like a vanilla slab: place one in water or fill it with a bucket. Its own water is
   never flood water.
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
  similar: the block tag `pirates_n_ships:terrain`, which includes `#minecraft:base_stone_overworld`, so raw stone,
  granite, diorite, andesite, deepslate and tuff never assemble; cobblestone and stone bricks do). Logs are not
  terrain, so a wooden build counts. The tag
  `pirates_n_ships:never_assemble` excludes more blocks (bedrock, barriers, portals, command blocks).
- The limit is 2048 blocks. Above it the helm refuses and tells you. A dock that touches the hull counts towards it.
- Chests and other block entities keep their contents.
- The sea is put back where the hull stood, so no hole is left in the water.
- The ship gets a record (its id, a name, the owner). Use a **name tag** on the helm of an assembled ship to name it.

### Disassembly
Sneak-use the helm with an empty hand to open the [ship screen](#ship-screen), then press **Disassemble** on its Ship
tab twice (the second click confirms). With the ship screen switched off (`ship_screen.enabled`), the sneak-use
disassembles at once, as it used to. The ship is put back into the world as blocks, snapped to the block grid with
its heading rounded to the nearest 90°. It is refused when:
- the ship moves faster than 0.3 m/s or turns too fast,
- it is tilted more than 6°,
- something solid is in the way (the message names the position).

**If your helm breaks,** the ship stays a ship: its stations keep working, but nothing steers or disassembles it until
you place a helm anywhere on its deck. The first helm placed steers; a second helm on the same ship does nothing while
the first stands. Using a helm on a floating hull the mod has lost track of makes it your ship again.

Players and mobs on deck are set down on the deck blocks. Water inside the hull is removed.

### Ship screen
Sneak-use the helm of your assembled ship with an empty hand to open the **ship screen**: the ship and her crew on one
page, three tabs.
- **Ship:** her name (type a new one and press Rename or Enter; your career title goes in front, as with a name tag),
  the flag she flies and what it tells others (struck colours, and whether the navy has seen through a false flag),
  her captain with his title, the hull (compartments, how many flood, open breaches, pumps working), the load and
  speed, the anchor (stowed, running out, down while she still moves, anchored, weighing), the sails (full, reefed,
  furled), crew and bunks, the supplies left in days and the last payday. **Disassemble** (click twice) disassembles
  her at this helm.
- **Crew:** the captain's whistle orders as buttons (hoist, reef, furl, man the pumps, fire, load, fire at will, the
  anchor, row or ship oars, board or back aboard, release crew; "Release crew" also ends "Fire at will"). The anchor is one button: it reads "Drop anchor"
  while the anchor is stowed or coming up and "Raise anchor" while it runs out or holds, and gives that order; the
  boarding button reads "Board!" or, while your boarders are across, "Back aboard". Then every hand aboard with morale, station
  or off duty, the order being carried out, who hired them, unpaid, and a warning when low morale has them close to
  deserting. **Release** takes a hand off their station, **Dismiss** lets them go as the whistle's sneak-use does.
- **Stations:** every station aboard (winches, pumps, cannons, capstans, the helm), who mans it, the order worked
  there or an open job waiting for a free hand. **Release** frees it; **Man** lists your free hands, pick one with
  **Send** and they go there and stay (like assigning with the whistle).

The orders go out exactly as from the whistle: hands at the stations that take them start at once, unmanned stations
become open jobs. Only the ship's owner opens the screen (anyone, on a ship without an owner); others are told it is
not their ship. Release and Dismiss follow the whistle's rules: the captain or whoever hired that hand. The screen
updates every second while open and closes when you step more than 8 blocks from the helm (`ship_screen.reach`). The
whistle keeps working as before. Server config `ship_screen` (`enabled`, `reach`, `refresh_ticks`).

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
- A ship's compartments come from its blocks alone: rolling and pitching on the waves never splits or joins them, and
  the water stays in the room it is in (only the water levels follow the tilt).
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
- **Small hulls are livelier.** The auto-ballast (`stability.auto_ballast`, on by default) cancels the trim and heel
  of the build itself, so a ship floats level as built; what you load later (cargo, flood water, crew) still trims
  and heels her, and a narrow, short hull heels and pitches more than a long, wide one. The heeling force of sails is
  scaled down to 25% (`sailing_runtime.sail_heel_factor`). With the auto-ballast off, a heavy stern castle sits the
  ship stern down. No tipping figures have been measured since the ballast came in.
- A ship only floats on water that is under its own hull. A ship in a dry dock next to the sea stays put.

---

## 3. Sailing

### Wind
Each dimension has one wind: a direction and a strength of 3 to 12 blocks per second that drift slowly over time.
Rain makes it 1.5 times stronger and a thunderstorm 2.2 times, with gusts during thunderstorms. It is the same for
every player and is sent to clients, which show it through the wind streaks below, the wind arrow of the
[ship HUD](#ship-hud), the flags and the set of the sail cloth. `/pirates wind get` prints it.

**Seeing the wind.** Over the sea, thin white streaks drift through the air around you, from deck height up into the
rigging, flying with the wind at the wind's own speed: they come from where the wind comes from and show its strength
by how many there are and how fast they go. In a light breeze there are none, in a gale many, and a gust brings a
flurry. They hang in the world, not on your ship, so on a sailing ship you see the true wind and your ship moving
through it, as the HUD's wind arrow shows it. Like real air they are never quite uniform: each flies a little faster
or slower and a few degrees off the wind with a gentle bob, most come in loose puffs of a few, they gather around deck
height, and they are faint brush strokes in a light wind and bright whooshes in a strong one. They are only a picture:
nothing in the game depends on them. Client settings `wind_effects` (`streaks` turns them off; `density`,
`min_strength`, `radius`, `height`, `life_ticks`, and for their look `speed_spread`, `heading_jitter_degrees`,
`wobble`, `height_peak`, `puff_share`, `puff_size`, `puff_spread`, `puff_ticks`, `opacity`); the video setting
"Particles: Decreased" halves them and "Minimal" hides them.

### Sails
A **square sail** is built from two **yards**. A yard is a straight row of yard blocks (a thin spar; place them against
each other's ends to extend one); its middle block marks the mast column. Put a second yard with the same direction
2 to 8 blocks straight below the first, with only air or mast blocks (logs, fences) between the two middle blocks, and
the pair is one sail. The cloth is drawn between the yards, not built from blocks: furled is a roll under the upper
yard, half reaches half way down, full reaches the lower yard. Its area is the mean yard length times the distance
between the yards, so two 5-wide yards 5 apart give 25. Three yards on one mast make two sails. A yard that heads no
sail tells you so when you click it. Rig both yards (the one at the head of the sail and the one at its foot) in the
column in front of the mast, one block forward of it, not in the mast's own column: the rule accepts both, but only
the first looks like a real rig, with the cloth hanging before the mast (the yard rule; the NPC ships and the Almanac's
scenes are built that way).

A **triangular (fore-and-aft) sail** is rigged with a rope and three **cleats**. Put one cleat high on the mast's side
(the head), one on the deck or a bowsprit further forward and at least 2 blocks lower (the tack), and one straight
below the head (the clew). Use a **rope** on the head cleat, then on the tack cleat (at most 16 blocks away): the rope
is used up and a stay runs between them. The cloth fills the triangle between the stay and the clew: furled is a bundle
along the stay, half reaches half way down the mast, full fills the triangle. Its area is half of head-to-tack times
head-to-clew. Click the head cleat with the empty hand to cycle its trim. Breaking the clew hides the cloth but keeps
the stay; breaking the tack drops the rope (not in creative). Between the head and the clew only air, mast blocks
and **yard blocks** may stand, so a jib's cleats can share the yards' column in front of the mast: the head right above
the upper yard and the clew right below the lower one, the yards in between. The square sail is not affected as long as
no cleat sits between its two yards. The starter and armed sloops are rigged this way: yards at z 12, one block forward
of the mast at z 13, the jib's head cleat at [4, 17, 12], its clew at [4, 9, 12] and the tack on the bowsprit at
[4, 7, 1] (a jib of 44 blocks²).

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

**Dyeing sails and banners.** Use a **dye** on the upper yard of a square sail (any of its blocks) or on the head cleat
of a triangular sail: the whole cloth takes the colour, hanging, reefed or furled, and one dye is used up. A white dye
brings back the natural canvas; there is no washing out. A large square sail can also carry a **banner**: use a banner
on its upper yard, and the cloth takes the banner's base colour with its patterns drawn upright over it, rippling and
bellying with the cloth. Both yards must be at least 3 blocks long and 2 blocks apart. The design reads right from the
side facing the ship's bow (on land: from the south for a yard running east-west, from the east for one running
north-south) and mirrored from behind. Sneak-use the yard with an empty hand to take the banner back; breaking the yard
block that holds it drops it. While a banner hangs, dyes are refused. Triangular sails take dye only. Colours and
banners are only a look: they never change a sail's force, and they stay on through assembly, disassembly and trim
changes. Server config `sailing.sails.dyeing`, `banners`, `banner_min_width`, `banner_min_drop`; client config
`sail_visuals.banner_layers` (off: the base colour only).

**Sails in the wind.** The cloth shows how a sail meets the wind you feel on board. A sail that draws bellies out to
leeward, deeper the stronger the wind, and breathes slowly. On a ship the cloth knows where the bow is and follows the
same rule as the force, with the crew bracing the yards: it draws whenever the sail drives the ship, a square sail on a
beam reach too, and luffs (hangs slack and shakes) in the no-go zone and head to wind, from the moment the ship is
assembled. On land, where nobody trims it, a sail luffs when the wind runs along its cloth. Half sail bellies less, a
furled sail stays a bundle, and in a calm the cloth just sags a little. It is only a look: the force comes from the
rules above. Client config
`sail_visuals.enabled`, `max_belly`, `flutter_amplitude`, `segments`.

### Sail winch
Using it cycles the trim of **all** sails on its ship: furled → half → full → furled. Clicking an upper yard or a head cleat with the
empty hand cycles only that sail.

### Keel
A ship in water resists moving sideways much more than moving forward, so a sail on a beam reach drives it ahead
instead of pushing it downwind. No block is needed for this.

### Rolling and creaking
A ship that heels or is shoved swings back and settles within a few seconds (roll and pitch damping, config `sailing`).
While it rolls, its planks creak now and then from somewhere in the lower hull, louder and more often the harder it
rolls, never as a loop; a ship lying still is silent. The creak is the mod's own sound (`pirates_n_ships:ship.creak`).
Server config `hull_creaking` (on/off, creaks per second, the minimum gap, volume and pitch ranges, the rolling rate
that counts).

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

A ship floats level as you built it: the shipwright ballasts it, so a tall stern castle or a heavy gun deck on one side
does not tip it. What you load later does: cargo, flood water and damage still trim and heel it. Only an absurd build
(a tower of iron far aft) is more than the ballast can make good. Server config `stability.auto_ballast`.

### Capstan and anchor
The anchor is a real object: it hangs outside the hull on the side nearer to the capstan, just below the deck, and
moves with the ship. The anchor is heavy and falls on its own: let it go while the ship has way on and it keeps the
ship's speed for a moment, sinks at about 4 blocks a second and lands a few blocks astern of where it left the hull,
with the chain rattling, a splash when it enters the water and a thud when it lands. Once it bites, the chain pulls
the ship back at the bow, softly, so there is no dead stop. Because the hawse is on the capstan's side, a ship under
sail swings hard round toward that side and ends up head to its chain, bow toward the anchor. A ship pulling harder
than the anchor holds drags it over the seabed. The ship counts as anchored once the anchor holds and the ship has
stopped. Using the capstan again heaves the chain in at 2.5 blocks a second and pulls the anchor along the seabed and
up to the hull; using it mid-way lets go again. A fresh drop is refused when there is no ground within the chain's
length (32 blocks) straight below the hawse: "No ground within 32 blocks below: the anchor would not hold". (Letting
go again while it is being weighed skips that check; an anchor that then falls past the chain's end hangs there and
does not hold.) Server config `anchor_chain` (fall, chain, holding, sounds) and `sailing_runtime.anchor_enabled`,
`sailing_runtime.anchor_chain_length`.

You can also put a crew member on the capstan: use the Captain's Whistle on the crew member, then on the
capstan. The whistle menu (and the ship screen) has one anchor entry that switches with the anchor: it reads "Drop
anchor" while the anchor is stowed or coming up and "Raise anchor" while it runs out or holds, and the order follows
the anchor's state when you give it (like using the capstan by hand). "Drop anchor" makes the hand lean into the bars
for two seconds before the anchor runs out, and "Raise anchor" starts the winding at once; he keeps pushing until the
anchor is stowed. While he works, the
capstan's bars turn and he walks round the drum pushing them, then steps back to his spot. A free hand on deck
takes an unmanned capstan by himself when you give the order. Over water deeper than the chain he refuses: "No
ground for the anchor within the chain's reach, captain!" Commands: `/pirates crew order drop_anchor` and
`raise_anchor` (the command keeps both directions; only the wheel and the screen have the one toggle). Server config
`crew_stations.capstan`.

### Oars
In a calm, against the wind or to creep into a harbour, pick **Row** on the captain's whistle (or the ship screen):
every hand who is not at another station takes an oar. No block is needed: a hand walks to the rail, on one side and
then the other in turn so the ship runs straight, and rows standing there, facing aft, the oar out over the side. A
spot at the rail is a deck cell beside the hull's edge, a railing or a one-block bulwark, on the port or starboard side
(never at the bow or stern). An **Oak Oar Bench** placed at the rail (it faces the way you look when you place it, so
look outboard) is a station: a free hand walks to it, sits on the thwart facing aft and rows a quarter harder than one
standing (`oars.bench_bonus`, 1.25). Each rower adds `oars.thrust_per_rower` along the bow; the push fades as the ship
nears `oars.max_speed` (3 blocks a second), so three hands move the small test hull at about 0.7 blocks a second and
oars alone never pass the limit. Oars add to the sails. A tired crew (hungry, thirsty or drunk, see
[Upkeep](#upkeep)) rows slower, as it hauls slower. More hands on one side turn the ship a little.

While the ship rows, the same whistle entry reads **Ship oars**: the oars come in and every rower walks back to where it
stood. Any other order (a sail order, the pumps, Release crew) ends the rowing first, and the hands ship oars by
themselves when the sails drive the ship faster than `oars.max_speed` × `ship_oars_speed_factor` for two seconds. Rowers
stay at their oars through the night. Command: `/pirates crew order row`. Server config `oars` (`enabled`,
`thrust_per_rower`, `bench_bonus`, `max_speed`, `side_torque`, `ship_oars_speed_factor`, `ship_oars_ticks`).

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
joined, and the piece you nail on may have at most 200 blocks (`assembly.rejoin.max_piece_blocks`): when the far piece
is bigger, the toolkit refuses and you rejoin a smaller piece, for instance by keeping the bigger half and nailing the
smaller one to it. Server config `assembly.rejoin`.

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
Once a grappling rope is latched onto another ship and tied off on a cleat or mooring ring of yours (use the cleat or
ring while your hook is out), look at the rope and use it: you hang from it and slide down to the lower end, following
both ships as they move. A rope still in the thrower's hand is no slide (he can only climb it, see
[Hauling with the rope](#hauling-with-the-rope)). Sneak to let go. A level rope is crawled slowly toward the hook. From the
crow's nest down to an enemy deck is the classic move. Server config `grapple.slide`.

### Boarding plank
Craft one from three wooden slabs over two iron nuggets. It is a gangway: lay it from a ship, a pier or the shore onto
a ship, a pier or the shore. Use it on the top of a gunwale, rail or pier edge block, facing where it should go; it
runs straight out from beside that block, level with its top (used on the side of the block, it lies a block lower).
It reaches up to six blocks to the first deck or pier it can land on, preferring one level with its start, then one a
block up or down, and so on: resting on that deck or pier, or meeting the top edge of a higher one. The plank is one
straight board at a single pitch from its start to its landing, as steep as a block of rise for every block of run
(45 degrees) at most; under the board are small steps, so you and your crew walk up and down without jumping. While
you hold the plank, the board it would lay where you look is outlined in green, or the cell in the way in red. A
refusal says why on the action bar: something in the way (and how far out), too far, too steep (and the steepest
pitch allowed), nothing to land on, or no plank from here (for example along a pier, where there is nothing to bridge).

A plank that touches a ship lies on that ship and moves with her, even when you lay it from the pier. Keep the ends
together: when the far end drifts more than a block and a half sideways, or more than two and a half blocks up or down
(a moored ship rides out a storm's heave), or a ship or a block an end rests on is gone, the whole plank breaks and
drops back as one item. A plank between two piers is plain blocks. Breaking any part takes the whole plank down; sneak
and use it with an empty hand, from either end, to take it up again. Server config `boarding.plank` (`max_length`,
`max_pitch`, `pick_up`, `break_distance`, `break_rise`; the ramp settings `max_rise`, `rise_run` and `steep_fallback`
of earlier versions are gone and ignored in an old config file); client config `boarding_view.preview`. Planks laid
with an earlier version are turned into straight boards the first time their world loads (a level one stays as it
is; a sloped one is laid again from its near end, or drops back as one plank if no straight board fits there).

### Boarding: the crew go across
Lie alongside another ship (haul her in with the grappling hook, or lay a gangway across) and give **Board!** on the
captain's whistle (or the ship screen's button). Every hand of your ship goes except the gunners and the helmsman:
each leaves his station, walks to the nearest way across and crosses it: along a gangway between the two ships
(whichever ship it was laid from), over touching hulls, or with a jump over a gap of up to two blocks of water to a deck
at most a block higher or lower. A railing stops them; a railing gate or port is an opening. On her deck they fight her
crew and any monster aboard: a hand with an empty hand first takes a cutlass, saber or rapier from a chest or barrel of
your ship and fences with it like the pirates do, otherwise he fights with his fists. Her people fight back, and your
own free hands defend your deck the same way when someone boards you. No way across: "No way across to board", and
nobody moves.

When her deck has been clear of enemies for ten seconds you are told "Her deck is yours" and the boarders come home. To
take the ship herself, step aboard as before: an NPC ship is yours once her fighters are dead and you stand on her deck
for a while (the capture of the world simulation, with its deeds; a merchant taken is piracy, and the goods taken from
her are plunder that a harbor master will not buy). While they are across the same entry reads **Back aboard**: they
come home over the nearest way back, also when you give it from her deck, and walk to the stations they left. A
boarder whose way back is gone (the gangway broken, the ships drifted apart) stays and fights on, and comes home as soon
as a way opens again. A hand who has not got across after ten seconds is set down on the far side.

NPC ships board you too: a pirate ship (the navy, when you fly the Jolly Roger or are wanted) that lies alongside your
ship, with a gangway or a gap of up to two blocks, sends her fighters across after eight seconds when her guns have
fired on you or she is the faster or the heavier ship, and they are not outnumbered. Her captain calls them back when
his ship floods or when your people outnumber them two to one. Server config `boarding`: `board_order`, `board_gap`
(2), `boarders_keep_stations`, `capture_seconds` (10), `npc_boarding`, `npc_board_delay` (8 seconds),
`npc_recall_outnumbered`, `npc_recall_flooded`, `take_arms`, `unarmed_damage`, `crew_skill`, `cross_timeout_ticks`,
`order_check_ticks`.

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
spread out on it. The **Sea Cot** is a wooden bed for the captain's cabin: sleep in it and set your spawn like in a
bed, on land and aboard an assembled ship. Aboard you lie in it as the ship sails on, count toward skipping the night,
get up on the deck beside it, and respawn at the cot wherever the ship has gone. Server config `ship_decor`
(`sea_cot_sleeping`, `sea_cot_sleeping_aboard`).

### Ratlines
Ratlines are rope nets for climbing a mast, up to a crow's nest or a yard. Craft four from three string between four
sticks (a stick in each corner). Click the side of a mast (logs, fence posts, walls or any solid side) to hang a net on
it like a ladder; it falls and drops when the mast block behind it goes. A net hangs on a yard too: on its end, or
on its side when no sail hangs from it (a sail's cloth fills the sides of its yards; you are told so). Click the top of the deck or the gunwale to
lay a sloped net instead: it rises at 45 degrees the way you look, so stand at the gunwale facing the mast. Click a
ratline you already placed to add the next one at the end of its run: straight up a hanging net, one up and one
forward along a sloped one, so a run climbs from the gunwale to the masthead like real shrouds. Sneak to place against
the clicked side instead. Climb a hanging net like a ladder; walk up a sloped one, stepping on its ratlines. A sloped
net needs the deck, a ratline below it, the previous net of its run, or the mast, a yard or a crow's nest in front of
it to rest on; break one and the nets above it that rest on nothing else fall too. A sloped run may lie its last net
on the end of a yard right beside the mast: walk up into it, face the mast and push forward, and you climb on up the
hanging net above without a jump. Ratlines work in water and on a sailing ship. Server config
`rigging.ratlines_enabled`.

### Railings
A **Railing** lies along the outward edge of a block, not across its middle like a fence: a top rail, a lower rail and
balusters standing on the deck, 2 pixels in from the edge. Craft four from three sticks over three planks of one wood
(oak for now; other woods follow). Click the top of the deck near the side the rail should go on (near the middle of
the block it goes on the side you face); click the side of a block to rail the edge against it. Posts appear by
themselves where a run ends or turns: a straight run has a post at each end, a gap of one block gets a post on both
sides, and clicking a railing again (not sneaking) rails another edge of the same block, which makes a corner (one
post) or, around a hatch, a surround. Each edge is one railing; breaking the block gives back one per edge. Like a
fence, a railing is too high to jump (1.5 blocks; server config `ship_decor.railing_collision_blocks`, 1.0 lets players
vault it) and crew do not walk through it.

A **Railing Port** (two sticks over three planks, gives 2) is the opening in a railing: its two posts and a low sill,
no rails. A railing beside it ends against its post. A cannon placed right behind it fires through it at every
elevation; use a boarding plank on the sill while facing out and the gangway starts in the block beyond the port at
deck height, so the port is also where boarders go across. A **swivel gun** placed on a railing sits on the rail
itself, at the edge (with a corner railing, on the edge nearest your click), and its crew stands on the deck inside;
**ratlines** stand on a railing's top or hang on its side. Railings weigh like fences on a ship and hold water. Server
config `ship_decor.railings_enabled` (off: railings, ports and gates cannot be placed; placed ones stay).

A **Railing Gate** (a stick, an iron nugget and a stick over three planks) is a port with a swinging leaf: closed it is
a railing section with two iron hinge straps on one post and a latch on the other (the post on the half you click is the
hinge), open it swings outboard like a fence gate. Use it, or give it a redstone signal, to open and close it. A run of
railing continues into it without a second post. Closed it blocks like a railing and crew go around it; open, crew walk
through, and a boarding plank used on its sill while facing out lays the gangway from beyond it, as from a port (a
plank in hand does not swing the gate). **Railing posts carry things:** wherever a railing (or a port's or gate's) post
stands, a lantern or soul lantern hangs below the block or stands on it, and a chain runs on from it; a plain stretch
of rail carries nothing. The **grappling hook** holds on a railing or gate like on a cleat (twice the rope's length),
but a hard haul tears the railing out and the rope lets go (`grapple.railing_break_force`).

A **Bulwark** (a stick, a plank and a stick over three planks, gives 4) is the railing's solid variant: a plank wall
2 pixels thick along the block's edge with a log cap rail on top, placed exactly like a railing (another click adds an
edge, one item per edge), with square stanchions where a run ends or turns. A bulwark run goes on only into another
bulwark or a gun port; next to a railing both end in a post. It blocks and weighs like a railing, is not part of the
hull (a bulwarked deck floods and stays dry like a railed one), carries a swivel gun, ratlines, chainplates and the
grappling hook, and lanterns hang from its stanchions. A carriage cannon right behind a plain bulwark fires into it:
the ball is spent at once and breaks the wall (server config `cannons.walls_block_barrel`). A **Bulwark Gun Port** (a
bulwark and an iron nugget) has a square notch from a low sill up through the cap rail and a lid hinged at the top:
the lid swings up and out by itself when a carriage cannon stands right behind the port facing it (and shuts when the
gun goes), and with an empty hand you prop it open or shut it ("The gun behind holds the lid open" while a gun stands
there). The cannon fires through an open port at every elevation, and a boarding plank used on its sill while facing
out lays the gangway, as from a railing port, while the lid is open.

**Ratline nets (RL2).** For a longer or steeper climb, string a net between two iron fittings. A **chainplate** (two
from an iron ingot under a ring of three iron nuggets) goes on top of the rail or on its side (any solid face, or a
fence, wall or railing); a **mast hoop** (two from three iron ingots over a ring of three nuggets) goes on the side of a
mast like a ladder. Craft a **ratline net** from five string and four sticks (string across the middle row and in the
middle of the top and bottom rows, sticks in the corners). Use it on one fitting ("Net tied: now use the other
fitting", the net glints), then on the other: the net is strung between their rings and used up. It is refused, with
a short reason in the action bar, when the line is longer than 32 blocks (`rigging.net_max_length`, up to 64), flatter than 30°
(`rigging.net_min_pitch`), runs through a block ("Blocked at x y z"), joins the land to a ship or two ships, or a
fitting already holds a net. The net is drawn as two side ropes with a rung every half block; every block it passes
through holds an invisible, climbable net cell. Walk into the net (from the chainplate) and hold forward or jump: you
climb along the net's line at any slope, as fast as up a ladder (slower on a net flatter than about 38°); back climbs
down, sneaking holds on. The fittings are climbable too, so you get on at the chainplate and off at the hoop.
Breaking a fitting, a net cell or putting a block into the net brings the whole net down: one net drops at its
chainplate, the fittings stay. Nets work in water and sail with the ship. Server config `rigging.nets_enabled`,
`rigging.net_assist` (off: plain ladder climbing, which takes a diagonal net only with jump and forward held
together).

**Triangular nets (RL4).** A mast hoop takes up to five nets (`rigging.net_max_strands`), each from its own chainplate
(a chainplate still takes one); a full hoop says "Hoop full: at most 5 nets". The nets on one hoop form a **fan**: put
three chainplates along the rail and string each to the same hoop, and the space between neighbouring nets is netted
too, so you get the wide-at-the-bottom, pointed-at-the-top shrouds of a real ship. The whole triangle is climbable:
step in anywhere along the rail between the chainplates and climb straight up to the hoop. It is drawn as one net: every
strand's rope from its chainplate to the hoop and rungs every half block across all of them, narrowing towards the
hoop. Two neighbouring chainplates more than 6 blocks apart (`rigging.net_max_spread`) stay separate nets with nothing
in between ("Too far apart for one net: 7.2"). The space between must be clear like the net's own line (another
chainplate in it is fine). Each strand is still one net item: breaking a chainplate, a net cell or putting a block
into the net drops that strand's net at its chainplate and the rest of the fan closes up again; breaking the hoop drops
every strand. All nets of a fan must be on one ship (or all on land).

**Boarding nets (RAIL5).** Put two chainplates on top of two railings of one straight run, railed on the same edge,
with at least one block between them, and string a ratline net from one to the other: "Boarding net strung along the
rail". It hangs upright along the rail, from the rail top to a block above it, with a sagging top rope. Anyone who comes
over the rail from outboard into the net (a boarder jumping across, a mob, a player) is held behind it for a second,
slowed like in half a cobweb, and may then climb over (the net is climbable); the ship's own crew (on her deck, at her
stations, or hired by her owner) pass freely both ways. It cannot be mined or built over: three hits with a **cutlass**
cut it down and drop one net at the first chainplate. Chainplates on two edges ("Boarding nets run along one rail
edge"), on neighbouring railings ("Leave a block between the chainplates") or more than 8 blocks apart ("Too long") are
refused. Server config `rigging.boarding_nets` (off: such a net is refused as too flat, and strung ones hold nobody),
`boarding_net_length` (8), `boarding_net_hold` (20 ticks), `boarding_net_cut_hits` (3).

### Hammocks
Your crew sleeps in hammocks. Hang one between two supports at the same height (fence posts, walls, logs, or a solid
wall such as the hull side): click the block next to one support while looking toward the other. Recipe: 2 string
over 3 wool. At nightfall every crew member who is not at a station turns in to the nearest free hammock and gets up at
dawn. A night in a hammock raises its morale by 5; a night on a ship without a free hammock for it lowers it by 10 (it
will grumble). Crew on duty all night are unaffected, and an order at night gets sleepers up at once. Each hammock is
one bunk: use the captain's whistle on a crew member, or `/pirates crew info`, to see morale and "crew 3 / bunks 2".

You can sleep in a hammock too, on land or aboard: right-click a free one at night (or in a thunderstorm) and you lie
in it like in a bed. Aboard you stay in it while the ship sails and turns, you count toward skipping the night, and
"Leave Bed" (or the dawn) puts you on your feet beside it. The usual bed rules apply: not by day, not with monsters
nearby, not in a hammock a crew member sleeps in. A hammock you sleep in is taken: no crew member turns in to it that
night. Using a hammock sets your respawn point there, as a bed does; on a ship the point sails with the ship, so you
respawn at the hammock wherever the ship is. Server config `crew.morale`, `crew.hammock.player_sleep`.

### When an order reaches nobody
If the whistle or `/pirates crew order` says nobody carries an order out, the line now tells you why: no ship under
you, no station on board can do it, nothing to do, or no sails with the reason (a gap over 8 blocks between the yards,
a block in the mast between them, yards off the mast's column, a lower yard longer than the upper). `/pirates ship
rigging` lists every yard and what it carries, the triangular sails and the crew aboard.

### Upkeep
Every dawn your crew eats and drinks one day of provisions from the pantries and water barrels aboard and wants its
pay: 2 doubloons each, taken from any chest, barrel or cargo crate on the ship (the ones nearest the helm first).
Hungry or thirsty crew lose morale and work slower; rum cheers them up, but a crew that finds rum for 10 days or more
in the pantries drinks a double ration and works at 60% speed; weeks without citrus or fresh food bring scurvy. A crew
that goes 3 days in a row without food, or 1 day without water, deserts at the next dawn whatever its morale (all of it,
the same way as below; the owner hears what they went without). Unpaid crew lose 8 morale, paid crew gain 1. If the
coins aboard run short, the rest of the wages comes out of your own purse while you are online in that dimension (chests and crates always pay first). A sailor whose morale stays
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
While you are aboard a ship, two small panels show its state. At the bottom left, a little in from the edge, the compass rose
turns a little ship-shaped needle to the bow's heading; the light arrow outside the rose sits on the side the wind comes
from and points the way it blows: the longer it is, the stronger the wind, and it turns amber in a gust. Below it you
read the speed (in knots, or blocks per second) and the rudder angle; nothing more (the ship's name and load are in the ship screen).
At the bottom right, clear of the hotbar, the hull strip shows your hull from bow (left) to stern: one cell per
compartment, filling blue as water comes in, with a red mark where a breach lets the sea in and a pump sign while a
pump drains it. The panels stay while you walk the deck, jump, climb the rigging or sit at the helm, and go a moment
after you leave the ship. The compass stays put when chat messages come in: they are drawn over it. Client options
under `ship_hud`: on/off, `compass_corner` and `hull_corner` (put both in one corner to stack them as one panel),
`margin` (the distance from the screen edges), size and speed unit; servers can switch it off with
`ships.ship_status_hud`.

### Cargo weight
What you carry weighs the ship down. Crates, cargo barrels, pantries and water barrels get heavier as they fill: a full
crate weighs as much as forty planks. Chests and other vanilla containers press down where they stand, so a heavy
chest in the bow trims the ship by the bow. Spread heavy cargo and keep it low and central. The
[ship screen](#ship-screen) shows the load: Light, Laden, Heavily laden or Overloaded (`/pirates ship info` shows the
numbers); the rudder line at the wheel and the HUD do not. A laden ship
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
pirate under the Jolly Roger. Navy and pirate sloops carry four cannons, two a side, firing through ports in the
bulwark; they put to sea with their guns loaded and twelve rounds of powder and shot per gun in the shot locker, a
barrel in the hold beside the mast. Merchants carry no powder. She sails on to her destination and fades back into
the distance when you leave. Take goods from a merchant's hold while aboard and you have plundered her (powder and
shot from a shot locker are not cargo). Sink her with your cannons and the deed is yours. Kill every fighter aboard
and hold her deck for a few seconds, and she is yours, crew and all. Capturing a merchant is piracy in the navy's
eyes. Operators: `/pirates world voyages spawn near convoy|patrol|raid`. Server config
`world_simulation.materialize` (`cannon_rounds`, `guns_start_loaded`), `world_simulation.voyages` (which ship
templates each faction sails).

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
officer's outfit: the Officer's Coat, Officer's Breeches and Officer's Boots (once; a full pack drops them at your
feet). The outfit is armour that marks you as navy; you can also craft each piece (see "Coats, breeches and boots"). From Captain the harbor master's desk at a navy outpost has an Orders tab: the
navy shipyard builds you any ship the village shipwrights do, at 70 % of the price (Commodore 50 %, Admiral 40 %),
delivered to the outpost's quay. On the other side, infamy talks at the fences: a Buccaneer, Dread Captain or Pirate
Lord trades as if the pirates liked him 7, 13 or 20 points more, and from Dread Captain up pirates leave you alone
until you attack them. **Titles:** navy officers carry their rank before their name (Mid., Lt., Capt., Cdre. or
Adm.), a letter of marque makes you a Privateer, and growing infamy calls you Buccaneer, Dread Pirate and finally
Pirate Lord, in chat, in the player list and over your head unless you already belong to another team. A small box at
the top left shows your rank, your navy and pirate reputation and your letter for a few seconds when you join, when
your rank changes ("Promoted to Lieutenant", "Now Buccaneer") and after you close the career screen, then fades; pin
it, hide it or move it to another corner in the client config (`career_hud.mode`, `show_seconds`, `corner`). When you name your own ship with a name tag at the helm, your title goes in front of its name
("Capt. Black Gull"); after a promotion, rename it to show the new title. **Fighting ships:** no shipwright sells
the armed sloops of the navy and the pirates. You earn one by rank or you take one. The first time you reach Captain
in the navy, a Ship Commission comes with the rank (a full pack drops it at your feet). Hand it to a navy officer or
use it on the harbor master's desk of a navy outpost: an armed navy sloop with four guns waits at a free berth of the
outpost, assembled, yours, named with your title ("Capt. Vigilant") and flying the navy flag. Reach Dread Captain on
the pirate side and the brethren grant you a pirate sloop under the Jolly Roger the same way, at the fence's desk of a
pirate island. Each side grants one ship per player, and only to the captain named on the commission; a navy
commission is void once you leave the service. The shot locker comes empty, so buy powder and cannonballs before you
sail. If no berth is free, come back later: you keep the commission. The other road is to take one at sea: kill the
fighters of a navy or pirate sloop and hold her deck (see [Ships on the horizon](#ships-on-the-horizon)). Server
config `careers`, `careers.rewards`, `careers.ship_grants`, `careers.name_prefix`, `careers.title_on_ship`.

### Quests
Every harbor master's desk has a Quests tab with up to three offers, which the port renews when they run out after
two days. Accept up to three at once; each must be done within five days. Pirate hunts come from villages and navy
outposts, prisoner deliveries only from navy outposts, navy raids only from pirate islands (the defaults of
`quests.types.*`: villages offer deliver, kill_monster, hunt_pirates, find_treasure, hunt_captain, escort; navy
outposts hunt_pirates, turn_in, kill_monster, deliver, hunt_captain, escort, hunt_ship; pirate islands hunt_navy,
find_treasure, deliver, kill_monster, plunder_convoy, hunt_patrol). Monster hunts ask for sharks or, rarely (always
in cold waters), the kraken. A cargo run puts a contract in your Contracts tab: deliver it at the destination's desk
for a raised reward with no deposit. A treasure hunt hands you a treasure map; open the chest to finish. Completing a
quest pays doubloons and raises your reputation with the giver's side (navy, pirates or villagers). Villages and navy outposts may also send
you after the nearest pirate island's named captain (within 3000 blocks): bring him down yourself or hand him to a
navy officer in shackles for 400 doubloons on top of his bounty; if someone else gets him first, the quest fails,
and his successor doesn't count. **Quests at sea:** villages and navy outposts ask you to escort a convoy to another
port: on accepting, a merchant ship sets sail from the harbour, and you must keep within 96 blocks of her on at least
half of her course and see her make port (120 doubloons, plus 60 per 1000 blocks of the way). If she is sunk or taken,
or arrives without you, the escort fails. If no sea route to that port is known yet, the harbor master first charts
one (the quest shows "charting the route") and the convoy sails as soon as it is ready; if no route turns up within two
minutes, the escort is called off without counting against you. Pirate islands want merchant convoys plundered (take goods from a convoy's
hold while you stand aboard) or navy patrols sunk or captured; navy outposts want pirate ships sunk or captured. A
sinking counts for whoever fired the last cannonball that hit her within the last minute; a capture for whoever holds
her deck. These hunts are offered only while such ships are at sea. Holding a letter of marque, you also earn 60
doubloons of prize money on the spot for every pirate ship you sink or capture. Drop a quest in the tab or with
`/pirates quest abandon <id>`; `/pirates quest list` shows your quests. Server config `quests` (`quests.sea_quests`
for the sea quests), `careers.prize_money`.

### Pirate captains
Every pirate island has a captain: a named pirate (for example "Black-Tooth Bartholomew Crowe") who keeps to the
middle of his hut, or to the camp trail if the island has no captain's hut. He is a dangerous swordsman with 40
health, and the navy keeps a standing bounty of 300 doubloons on him, posted on every notice board. To fight him
one-on-one, sneak and use him with a sword in hand: if he accepts, his crew within 16 blocks keep out of the duel
unless you strike them. The duel ends when one of you falls, when you run more than 32 blocks away, or after five
minutes. If you hit him first he refuses: no honour, no duel. Slain, he drops his captain's hat (wear it yourself), maybe his coat, breeches and boots, a purse of doubloons
and a treasure map of his island, and you get a bounty proof to hand to a navy officer. Taken alive in shackles, the
navy pays the captain's reward of 150 plus his bounty alive. Five days after his fall a successor with a new name and
a new bounty takes his post. Server config `mobs.captain`.

A captain does not always stay home. Every two days he may put to sea (an even chance) on a pirate ship under the
Jolly Roger, out to 600 blocks along the lane toward a village or navy outpost, or into open sea, and back again;
while he is away his hut stands empty and `/pirates mob captain list` says he is at sea. He sails as the ship's lead
fighter, and his ship fires on navy and merchant ships it meets. Carry a letter of marque or a bounty proof and he
hunts you: within 192 blocks his ship circles yours and its guns take you as their target, until you get away,
strike your colours, or he loses you. He never strikes his own colours. Killing him at sea counts as on land (duel
him aboard, shoot him, or sink his ship: if it goes down with him, whoever hit it last gets his bounty proof), and
his ship is yours to capture once he and his fighters are dead. Shackled aboard, he is your prisoner and his ship
sails on without him. Otherwise he comes home to his post when his voyage ends. Server config
`world_simulation.captain`.

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
The sea follows the weather: a low swell or a light chop in fair weather, rough in rain, a storm in thunder, changing
over about a minute. A fair-weather chop lifts the water about half a block, rain about a block, and in a thunderstorm
the biggest crests stand two blocks and more above the still sea. Ships roll, pitch and rise with the waves (big ships far less than small boats). The sea is not one
even swell: waves of different lengths run a little across each other, so the rhythm is irregular, and they come in
sets, a run of bigger waves for half a minute and then a calmer spell. On a crest the bow lifts and the whole hull rides
up, in a trough it sinks back. In rough seas the bow
throws spray, and any open hatch or low rim close to the waterline lets water in at the crests, so close your hatches
and keep a pump ready before a storm. `/pirates waves` shows the sea; operators can hold a state with `/pirates waves
set storm` and release it with `/pirates waves clear`. The camera can roll with the ship (client setting
`wave_effects.camera_sway`, off by default). Faint foam streaks lie on the water around you, stretched along the
direction the waves run and gathered on the crests: none in a calm sea, a few in a moderate one, about twice as many in
a storm, and none inside a dry hull (client setting `wave_effects.foam`). A moving ship leaves a wake: a bow wave of foam
spreading out from the stem in a narrow V, a foam trail behind the stern that widens and fades over about 8 seconds and
stays where the water is (a turning ship leaves a curved wake), and at speed a thin line of foam along the waterline;
the faster the ship, the denser and brighter, nothing below 1 block per second or at anchor (client settings
`wave_effects.wake`, `wake_density`, `wake_life_ticks`, `wake_min_speed`, `wake_full_speed`, `wake_spread_degrees`,
`wake_radius`). Server config `waves`.

The sea itself moves: around you the water surface rises into crests and sinks into troughs with the same waves that
rock the ships, smooth near you and coarser toward the horizon, flat at the beach where the water is shallow, and never
inside a dry hull. The foam rides on it. Swimming, collisions and the ships' buoyancy still use the flat sea level, so a
crest can wash over your feet without lifting you. Boats ride the swell too: on the open sea a boat climbs the crests,
sinks into the troughs and leans with the slope, and items thrown into the sea bob with it, while in lakes, rivers,
shallow water and inside a hull they float as before (server config `waves.boats`, `waves.floating_items`, client
setting `wave_effects.boat_tilt_degrees`). You can see it from under water too.
Client settings `wave_effects.surface` (on by default), `surface_near_radius`, `surface_mid_radius`, `surface_far_cell`,
`surface_full_depth` and `surface_underside`. With a mod that replaces Minecraft's chunk renderer (Sodium, Embeddium,
Iris, Oculus, VulkanMod) the surface turns itself off and the water stays flat as before; the waves still rock the
ships.

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
shows where you are and which way you are heading. The M key opens the chart while you hold one in either hand; only
if the server turns on `chart.open_without_item` (off by default) does it open without one. Other players can appear
as ships (`chart.show_other_players`). Only the overworld's seas are charted.

## 4. Crew

A **crew member** is a simple NPC. Hire one at a harbor desk (see "Hiring crew" below) or spawn one with
`/pirates crew spawn`.

**Stations** are blocks a crew member can man. The sail winch, helm, cannon, swivel gun, bilge pump, capstan and
crow's nest are stations. A crew member at a station stands on
an invisible seat that travels with the ship, so it stays at its post while the ship moves.

Crew **walk** to their place: a sailor on deck who is sent to a station, to a meal or to a hammock walks across the
deck (even while the ship sails) and takes the place when he gets there; the station starts its work only once he has
arrived. A place he cannot reach (behind a wall, up the mast to the crow's nest) or has not reached after 10 seconds
puts him there anyway, as does an order to a sailor who is not on that ship. Server config `crew.walk` (`enabled` off:
crew take their place at once).

Idle crew **lean on the rail**: a sailor with no order, no station, no meal and no hammock looks for a railing within 6
blocks on his ship, walks to it, steps up to the rail, turns to face the sea and leans on it, forearms on the top rail,
looking out and now and then turning his head, for 30 to 90 seconds; then he strolls again for at least 30 seconds.
At most three lean at once per ship, never in neighbouring blocks, and never in the rail blocks beside the helm or
another station, nor where something stands on the rail. Any order, the meal call or nightfall ends it at once. Server
config `crew.rail_lean` (`enabled`, `radius`, `min_seconds`, `max_seconds`, `leaners`, `pause_seconds`).

The **captain's whistle** (creative tab) gives orders:
- Use it on a crew member, then on a station: the crew member takes that station.
- Use it on an assigned crew member: it is released.
- Use it in the air: opens the order wheel with eleven orders: Hoist sails, Reef sails, Furl sails, Man the pumps,
  Fire!, Load!, Fire at will, the anchor, Row, Board!, Release crew. The anchor is one toggle: it reads "Drop anchor" while the
  anchor is stowed or coming up and "Raise anchor" while it runs out or holds (the label follows the ship status your
  HUD shows; the server decides from the anchor's state when the order arrives). Row is a toggle too: it reads Ship oars
  while the ship rows (see [Oars](#oars)). So is "Board!": it reads "Back aboard" while your boarders are across (see
  [Boarding: the crew go across](#boarding-the-crew-go-across)). Point at one and click, or hold the
  use key, aim and release. The order goes to the crew on the ship you stand on whose station takes it; unmanned stations
  become open jobs (see [Orders, not assignments](#orders-not-assignments)). Esc closes the wheel.

The [ship screen](#ship-screen) at the helm (sneak-use with an empty hand) gives the same orders, assigns and releases
hands and shows every hand's morale, station and order at a glance.

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

### Crow's nest
A barrel of spruce staves with iron hoops for the top of a mast. Place it on a log, a fence, a wall or any block with
a solid top; it breaks and drops when the block under it goes. It is one block, but the barrel is a block and a half
across and stands chest high, so whoever is in it is plainly up in the nest. Only its floor is solid: climb a ladder up
the mast and step in over the rim (sneak to stay put). A flagpole placed on the nest rises from its middle and flies the
flag above it (see [Flags](#5-flags)).

Whoever is in the nest keeps the lookout. Put a crew member up there with the Captain's Whistle (use it on the crew
member, then on the nest): he takes his post inside the barrel and keeps watch until you release him. Or stand in it
yourself. Every 3 seconds the lookout scans 160 blocks round the ship and calls what is new to everyone aboard and in
the nest:
- **ships**, by the flag they fly: a merchant, a navy ship, a pirate ship, a wreck, or a sail when no flag flies;
- **land**: the nearest coast (one call per stretch of coast);
- **sharks** and **the kraken**.

A call is one short line over the hotbar (at most 40 characters): "Sail two points to starboard, 140 blocks", "Land
ahead, 300 blocks", "Pirate ship aft to port, 160 blocks". With it a small hand bell rings from the nest (higher and
softer than the Ship's Bell), and the ship panel's compass rose shows a small diamond on its rim at the sighting's
bearing, white for land, red for a pirate ship, blue for a navy ship and grey for anything else; it fades out after
30 seconds. Several calls at once follow each other on the hotbar line. Nothing goes to chat.

Bearings are named from the bow: ahead, "n points to starboard (or port)" (32 points round the horizon), abeam, "on the
starboard (or port) quarter" abaft the beam, astern; when a line would be too long it names only the side ("to
starboard", "aft to port"). A ship the lookout knows by sight is called by her name. Each ship, shark or coast is called
once, then again once it has come 64 blocks closer than at its last call or 2 minutes after it; the lookout remembers it
for 5 minutes after he last saw it. Only loaded chunks are watched. Recipe: planks, rope, planks over two iron nuggets,
over three planks. Server config `lookout`: `enabled`, `announce` (off: the watch is silent), `range`,
`scan_interval_ticks`, `memory_ticks`, `recall_blocks`, `recall_seconds`, `mark_seconds`, and the land rays
(`land_directions`, `land_step`, `land_min_distance`, `land_region`).

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

**Flag pedestal.** A squat plinth carved from a log, banded with iron, with a socket on top. Place it on any block with
a solid top (it pops off when that block goes) and put a flagpole on it: the pole stands in the socket and the pedestal
becomes its foot, with the cleat and the halyard belayed on it, while the pole blocks above stay bare. Using the
pedestal works the pole like using the pole itself. The pedestal does not count towards the pole's height. Breaking the
pedestal brings the whole pole down with its flag. One log over a wooden slab makes two pedestals. It can stand under
water like every small block.

**A pole on the crow's nest.** Place a flagpole on top of a crow's nest and it rises from the middle of the barrel: the
nest shows the pole's foot with its iron collar and the cleat inside the barrel, and the flag flies from the top of the
pole above the nest. Give the pole at least two blocks so the cloth clears the barrel's rim. The lowest pole block lets
you and the lookout stand in the nest as before (it does not push anyone out), the lookout keeps watch and calls as
usual, and breaking the nest brings the pole down.

**The flag runs along the pole.** While you hoist or raise a flag it climbs from the foot of the pole to the top, and
while you strike or take it down it runs down again, over the 3 seconds the work takes. Client config
`flag_visuals.hoist_animation` turns this off (the flag then appears and vanishes at once).

| Flag | Meaning for the law rules |
|---|---|
| none or Merchant Flag | Neutral to everyone. |
| Navy Flag | Navy and merchants friendly, pirates hostile. A false flag if the captain has a bounty or too little navy standing. |
| Jolly Roger | Pirates friendly, navy hostile, merchants may surrender. Being seen under it is a crime. |
| any vanilla banner | A custom flag: neutral. The cloth shows the banner's colour and patterns. |

At the pole:
- **Use it with a flag item:** hoists that flag after 3 seconds and gives back the old one.
- **Use it with an empty hand:** strikes the colors (the flag is lowered but kept), or raises them again.
- **Sneak-use with an empty hand:** takes the flag down.
- Breaking the pole drops the flag.

**What the flag does.** The flag your ship flies is the highest flag on its poles. Under the Jolly Roger the navy attacks
everyone aboard on sight and charges you for being seen; pirates leave you alone. A navy flag lets you pass the navy,
unless you are a suspect or worse: then every navy soldier within range may see through your colours. If one does, you
are charged heavily and the navy hunts your ship for five minutes, whatever you fly. A navy ship with a manned
crow's nest sees your flag half again as far off and sees through it half again as fast. Striking your colours surrenders:
navy and pirates stop attacking. Firing on a ship that struck its colours, or on one flying a merchant flag or banner,
is a crime; if your crew fires, the charge goes to you as the ship's owner. Server config `law.flags`, `flags_brig`
(false-colours detection, crow's nest observers, surrender).

The flag streams downwind at the exact wind angle. The world reacts to it as described above: navy soldiers, outposts
and patrols read it (see [Navy patrols](#navy-patrols)), your gun crews pick their targets by it (see
[Fire at will](#fire-at-will)), and the NPC ships on the sea lanes fly their own.

---

Hoist any banner on a flagpole to fly a custom flag: the cloth takes the banner's base colour and shows every pattern
of the banner, as if the banner were hung sideways from the pole (its top edge at the pole, its length along the
cloth); the back of the flag shows the design mirrored, like a real flag. Client config `flag_visuals.banner_upright`
stands the design upright instead (its top at the top of the cloth, stretched to the cloth's length).

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
Every dawn each ship with crew aboard eats and drinks one day from its pantries and water barrels (see
[Upkeep](#upkeep)). `/pirates provisions` runs the same rules on a pantry for testing:
- A crew eats perishable food first, the food closest to spoiling before the rest.
- Hungry crew lose morale and work at 75% speed. Thirsty crew lose morale faster and work at 50%. The work speed is set
  at dawn and holds until the next one. After 3 days of hunger or 1 day of thirst in a row the whole crew deserts at
  the next dawn, whatever its morale (`provisions.hunger_desertion_days`, `thirst_desertion_days`; off with
  `crew.desertion.enabled`).
- **Rum:** a ration raises morale. With rum for 10 days of the ration or more in the pantries the crew drinks a double
  ration and works at 60% speed until 6000 ticks after the binge ends (`provisions.rum_binge_days`,
  `rum_binge_rations`, `drunk_work_speed`, `drunk_duration_ticks`).
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
The gold doubloon is the currency. It has no recipe: it comes from trade, contracts and quests, bounties and
turn-ins at navy officers, slain pirates and pirate captains, wreck chests and buried treasure. `/pirates trade coins`
shows how many you carry (it gives none; in creative take them from the creative tab).

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
Every port in the world has a market: each [seafarer village](#seafarer-villages), [navy outpost](#navy-outposts)
and [pirate island](#pirate-islands) registers one when it generates, run from its harbor master's desk. For testing,
`/pirates trade port <name> <kind> <climate>` creates a port anywhere (kinds `seafarer_village`, `navy_outpost`,
`pirate_island`; climates `tropical`, `temperate`, `arid`, `cold`).
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
- The same crime against the same victim doesn't count twice within its repeat window
  (`law.repeat_cooldown_seconds.<crime>`): 30 seconds for attacking the navy or a villager, 60 for theft, 120 for
  attacking a neutral ship or one that struck its colours, 300 for the Jolly Roger and false colours, 600 for piracy
  and desertion, 1200 for selling plunder and suspected piracy; kills and press-ganging always count.
- A fine costs 3 doubloons per point, paid at a navy officer (see below). Notorious criminals can't pay.

Every crime in the table is detected in the world:
- **Hurting or killing villagers, wandering traders and the navy**, also with arrows or by your pets (`law.world.combat_crimes`).
- **Theft:** taking items out of a container that stands in a village and that no player placed, while a villager
  can see you. Putting items in is fine.
- **Flags:** being seen by the navy under the Jolly Roger, caught under false colours, or with plunder aboard
  (suspected piracy); see [Flags](#5-flags) and [Stolen goods](#harbor-masters-desk).
- **Firing on ships:** a cannonball into a neutral ship or one that struck its colours.
- **Selling plunder** at a village or navy desk, **piracy** (capturing a merchant), **press-ganging** a prisoner,
  **desertion** from the navy.

`/pirates law crime` records any of them by hand, for testing.

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
- Players add bounties on anyone at a **notice board** (below), 10 doubloons or more (`law.player_bounty_minimum`).
  Operators can do the same with `/pirates law bounty place`.
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
- A prisoner left alone outside a cell tries to escape (`brig.escape_chance_per_minute`, 10 % a minute). A prisoner
  being led never escapes. Player prisoners don't escape; they go free when their sentence runs out. Master switch
  `flags_brig.prisoner_escapes`.

### Brig bars and brig door
Locking and unlocking a brig door needs a **Brig Key** (an iron ingot over an iron nugget). Use the key on the door:
"Brig door locked" with a padlock, again to unlock. Any key works on any brig door, so keep them from your prisoners.
The owner of a door still opens it without a key; nobody else, no mob and no redstone can. Bars connect to the door.

- **Brig bars** connect like iron bars.
- The **brig door** belongs to the player who placed it. Only the brig key locks or unlocks it (any player holding a
  key can); without a key, using it to lock only tells you that you need one. A locked door opens only for its owner
  and ignores mobs and redstone.
- A prisoner is **in a cell** when it stands in a closed space of at most 64 blocks, bounded by solid blocks, brig bars
  and a closed, locked brig door. There it stays, and it doesn't try to escape, unless it is held on a ship whose
  crew is unhappy: while the crew's average morale is below 20 (`crew.desertion.desert_below`), a prisoner breaks out
  of a locked cell with a chance of about 2% a minute (`brig.low_morale_escape_chance_per_minute`). A ship without
  crew keeps its prisoners like a cell ashore. Cells work aboard ships too.

What you can do with a prisoner: deliver it at a navy officer for its bounty, ransom it there, press-gang it with the
captain's whistle (a crime), or release it (see [Fines, ransom, press-gang and release](#fines-ransom-press-gang-and-release));
`/pirates brig` does each of these without the officer, for testing.

---

## 9. Weapons and combat

| Item | Today |
|---|---|
| Rapier | Sword: slash 4 (reach 2.8), thrust 9 (reach 3.6). The thruster. |
| Cutlass | Sword: slash 7 (reach 2.6), thrust 6.5 (reach 2.8). The slasher. |
| Saber | Sword: slash 6 (reach 2.7), thrust 7.5 (reach 3.2). In between. |
| Pistol | 10 damage, 3 s to load, wide spread; see Firearms. |
| Musket | 14 damage, 5 s to load, flatter and tighter; also fires the grappling hook; see Firearms. |
| Lead Shot, Cannonball | Ammunition: lead shot for the pistol and musket, cannonballs for cannons; see Firearms and Cannons. |
| Grappling Hook | Thrown by hand or fired from a musket; hooks a ship or a block, hauls, climbs; see Grappling hook. |

The swords fight with the skill system (slash, thrust, guard, parry, riposte, stamina; see [Swordplay](#swordplay)),
on by default (`melee.skill_based_combat`). The slash and thrust figures above are the per-weapon values in
`data/pirates_n_ships/pirates_n_ships/weapon/`, which a datapack can change. The tooltip shows the items' vanilla
attributes (rapier 5 damage at speed 2.0, saber 6 at 1.6, cutlass 7 at 1.2), which is how they hit with
`melee.skill_based_combat` off, as plain swords.

---

### Firearms
The pistol and the musket are single-shot flintlocks. Hold right-click with one lead shot and one gunpowder in your
inventory to load (3 seconds for the pistol, 5 for the musket, with the bow pose; letting go early cancels and costs
nothing; creative mode needs no ammo). The tooltip shows "Loaded" or "Not loaded". **Left-click fires, right-click
aims.** With a loaded gun, hold right-click to aim: after a second of steady aiming the shot is tighter and the musket
zooms in a little. Press left-click to fire, either while aiming or straight from the hip (no right-click: the full,
wider spread of the gun). Letting go of right-click never fires, it only lowers the gun, which stays loaded. A gun in
your main hand never swings, hits or breaks blocks with left-click, and left-click does nothing while you load. To
lower an aimed gun, you can also press sneak: the gun goes down still loaded and stays down while you keep sneaking
(server option `firearms.aim.lower_on_sneak`). The server option `firearms.fire_on_attack` (on by default) switches
back to the old scheme when turned off: hold right-click to aim and release to fire (a quick click fires at once), and
left-click is an ordinary attack again. While you load, a white
bar under the gun's slot fills up; a loaded gun shows a full gold bar in the hotbar and inventory, its hammer cocked back, and its tooltip
says "Loaded" or "Not loaded". You see yourself aim and reload (the Player Animation Library drives it, client option
`firearm_animations.enabled`). Firing: a lead ball flies out with smoke and a small kick, the gun is unloaded again and needs half a second before it
can be used. The pistol scatters; the musket hits harder (14 damage against the pistol's 10) and flies flatter and tighter. Standing in the rain, a quarter of
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

**Chain shot and grapeshot** load like a cannonball, after the powder. **Chain shot** (2 iron ingots and a chain,
gives 2) is two balls on a chain: it flies slower and shorter than a ball (six tenths of its speed) and a little off
the line, breaks no hull, and rips through rigging. Where it passes through a square sail it tears the cloth within
1.5 blocks: the holes show, the cloth above a hole hangs with a frayed edge, and the sail draws only with what is
left of it, so the ship loses way. It also cuts every rope it passes within 1.5 blocks of (a cut stay drops its
triangular sail) and breaks the ratlines around where it tears or strikes. Torn cloth mends by itself, one block of
it a minute, from the yard down; a new upper yard brings new cloth at once. Navy and pirate ships carry a few rounds of chain shot and grapeshot in their shot lockers beside the balls (`world_simulation.materialize.chain_shot_rounds`, `grapeshot_rounds`). **Grapeshot** (8 iron nuggets in a
string-tied bag, gives 2) bursts from the muzzle as nine pellets in a narrow cone at four tenths of a ball's speed:
each pellet hurts whoever it hits for 3 and breaks nothing, and none hit the people on your own deck, so fire it
across a crowded enemy deck before boarding. Breaking a loaded gun gives back the shot that was in it.

Crew loading takes whatever shot the supply holds: the ball first, unless the server's `cannons.crew.load_preference`
names chain shot or grapeshot. Crews firing at will choose by themselves: grapeshot when three or more fighters
stand on the target's deck within 24 blocks, chain shot at a ship they chase (a navy patrol's quarry, a pirate
captain's prey) while it still makes way, else balls, and only what the locker holds. Server options:
`cannons.chain_shot` and `cannons.grapeshot` (each on/off, speed, spread, damage; chain shot's radius, rigging damage
and mending), `cannons.npc` (`prefers_chain_shot`, `chain_shot_min_speed`, `grapeshot_min_fighters`,
`grapeshot_range`).

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
the hook into the musket (its full reload and one gunpowder), then aim with right-click and fire it with left-click: a flat shot on a 64-block
rope. Thrown by hand, the hook's rope is 32 blocks. The hook catches on any solid surface: another ship (the rope hauls
both ships together), your own ship (a line to slide down, e.g. from the mast top), or land (from a ship it slowly
hauls your ship toward that point like a kedge; from land it is a zip line). It slips off leaves and glass panes. To
slide, tie the rope off on a cleat or mooring ring first, then look at the rope with an empty hand (or a hook in it)
and use it; with a musket in hand, use always works the musket. Left-handed players can turn off `grapple.launch.offhand_required` to swap the hands.

**Cleats** work like mooring rings: a hook flying close to a cleat catches on it, and using a cleat on your ship
while your hook is out ties the rope off there.

**Mooring rings** (4 iron ingots make 2) mount on decks, walls or beams. A hook that flies within a block of a ring
on another ship catches on it and holds twice as far before tearing loose. With your hook latched, use a ring on
your own ship to tie the rope there: the ships keep hauling together and you can walk away. Sneak and use with an
empty hand, or breaking a ring, lets go.

**Throwing by hand.** Without a musket in the other hand, right-click throws the hook. If it hits the hull of another
ship it bites in and hangs there, following the ship; from your own ship the taut rope hauls both ships together
until they lie side by side, ready for boarding, then goes slack. It also holds on your own ship (a line, not a haul;
`grapple.latch_own_ship`) and on land (`grapple.latch_world_blocks`). On land the rope does not drag a hooked ship by
itself: hold sneak to freeze its length and walk back to haul (see [Hauling with the rope](#hauling-with-the-rope)).
A hook that hits water or a creature does not hold (a creature takes a small hit) and comes back after two seconds.
To let go, sneak and right-click with an empty hand. The rope snaps when you are farther from the hook than the rope
is long (32 blocks thrown, 64 fired from a musket, twice that when the hook caught on a ring or cleat), and the hook
comes back (or is lost, if the server says so). Throwing a second hook releases the first. Server options: section
`grapple`.

### Swordplay
Swords make themselves heard, one sound per attack: a miss whooshes (lower for a thrust), a hit slices flesh and a
thrust bites harder, a chestplate rings, and blade on blade clangs, loudly for a parry, dully for a blocked blow
or when you catch an opponent mid-swing. Drawing a sword plays a short scrape. Server owners turn sword sounds off or change
their volume under `melee.sounds`.

With a rapier, cutlass or saber in hand the mouse works differently (server config `melee.skill_based_combat`,
on by default; vanilla weapons are untouched):
- **Slash:** a quick left click. A wide, short arc after a brief wind-up.
- **Thrust:** hold left click about 0.3 seconds (`melee_input.hold_to_thrust_ticks`, 6) and release. Narrow, long reach, harder than a slash on the rapier and saber, slow to recover if it
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
| Cleat | 1 iron ingot between 2 planks (gives 2) | Attaches to floors, walls and masts. Three cleats and a rope make a triangular sail. See [Sails](#sails). |
| Sail Winch | 2 string, 1 iron ingot, 3 planks | Sets the trim of all sails on its ship. A crew station. The crank faces you when placed. |
| Capstan | 2 logs, 1 stick, 2 chains, 1 iron block, 3 planks | Drops and raises the anchor. |
| Harbor Master's Desk | book, gold nugget, 5 planks | Opens a port's market screen when bound to the port. See [Harbor master's desk](#harbor-masters-desk). |
| Cannon | 2 iron ingots, 1 iron block, 2 logs, 1 planks | Loads powder and a cannonball, aims by elevation, fires. A crew station. See [Cannons](#cannons). |
| Bilge Pump | 1 stick, 3 planks, 1 bucket | Pumps water out of the hold below it. A crew station. See [Fighting a leak](#fighting-a-leak). |
| Crow's Nest | 5 planks, 1 rope, 2 iron nuggets | A lookout barrel for the masthead; whoever stands in it calls out ships, land and sea monsters. A crew station. See [Crow's nest](#crows-nest). |
| Hull Patch (block) | placed by the item | A tarred plank that closes a breach. Watertight hull block. |
| Flagpole | 3 sticks (gives 2) | Flies a flag. A thin pole with a finial and a cleat (Blockbench model). See [Flags](#5-flags). |
| Flag Pedestal | 1 log over 1 wooden slab (gives 2) | A plinth carved from a log that a flagpole stands in; the pole's foot with its cleat. See [Flags](#5-flags). |
| Pantry | 8 planks, 1 wheat | Food store with spoilage. See [Provisions](#6-provisions). |
| Water Barrel | 6 planks, 4 iron nuggets, 1 water bucket | Holds 16 rations of water. Crafted full. |
| Cargo Crate | 4 planks, 4 sticks | Bulk container for 32 stacks of one item. |
| Cargo Barrel | 6 planks, 4 iron nuggets | Bulk container for 1536 items of one kind. |
| Brig Bars | 4 iron bars, 2 planks (gives 6) | Bars for cells. |
| Brig Door | 4 iron ingots, 2 iron bars | Lockable door for cells. |
| Notice Board | 4 planks, 2 paper | Lists every bounty and places new ones for doubloons. |
| Mermaid, Lion, Eagle and Skull Figurehead | 4 planks and a prismarine shard, gold ingot, feather or bone | Decoration for the bow. Click the hull block it should hang on: the plate lands there and the figure looks at you. |
| Name Board (was Nameplate) | any sign, 1 gold nugget (gives 3) | A framed oak plate fixed flat on the hull's side (no chains). Boards side by side on one face join into one board, and the ship's name is drawn once across the whole row, as large as the board's height allows: one board shows it small, three or more read from 30 to 60 blocks. Shows the name once the ship is assembled and named (name tag on the helm); renaming updates it within a second, disassembly clears it. Old nameplates load as boards. Server options `ship_identity.nameplate_shows_name`, `name_board_distance` (64, how far the name is drawn). |
| Oak Railing | 3 sticks over 3 oak planks (gives 4) | A rail along a block's edge with automatic end and corner posts; click again to rail another edge. Drops one per edge. See [Railings](#railings). |
| Oak Oar Bench | 3 oak planks over 2 sticks | A thwart with thole pins: a crew station where a hand rows seated, a quarter harder than at the rail. Faces the way you look when placed (look outboard). See [Oars](#oars). |
| Oak Railing Port | 2 sticks (the corners) over 3 oak planks (gives 2) | The opening in a railing: two posts and a sill. A cannon fires through it, a boarding plank goes out from its sill. See [Railings](#railings). |
| Oak Bulwark | a stick, a plank and a stick over 3 oak planks (gives 4) | A solid plank wall along a block's edge with a cap rail and stanchions, placed like a railing. A cannon behind it fires into it. Drops one per edge. See [Railings](#railings). |
| Oak Bulwark Gun Port | 1 oak bulwark, 1 iron nugget | A bulwark with a square gun port; its lid opens for a cannon behind it or by hand, and a boarding plank goes out from its sill while open. See [Railings](#railings). |

All blocks drop themselves. Wooden ones are mined with an axe, the bars and the door with a pickaxe.

---

### Hats
Wear a pirate hat, bandana, navy tricorn or officer's bicorne by right-clicking with it or putting it in the
helmet slot. Each gives +1 armour (server config `apparel.hat_armor`; 0 turns it off). Crafted from three black wool
over leather, bone, leather (pirate hat), leather, white wool, leather (navy tricorn) or leather, gold nugget, leather
(officer's bicorne); red wool, string, red wool for the bandana.

### Coats, breeches and boots
The **Officer's Coat** and the **Captain's Coat** go on the chest, with knee-long tails that hang from the waist,
split at the back, and swing back with your stride. The captain's coat is charcoal edged in gold over a brocade
waistcoat, with a red sash on the left hip and a leather baldric over the right shoulder; the **Captain's Breeches**
(legs) and **Captain's Boots** (feet, tall with turned-down tops) complete the pirate captain's look, with his hat.
The navy officer's coat is completed the same way: the **Officer's Breeches** are white with a navy stripe down the
outer seam and the **Officer's Boots** black with turned-down tops and brass buckles, as the navy's officers wear them.
None of them wears out. Armour: coat +3, breeches +2, boots +1 (server config `apparel.officers_coat_armor`,
`apparel.officers_breeches_armor`, `apparel.officers_boots_armor`, `apparel.captains_coat_armor`,
`apparel.captains_breeches_armor`, `apparel.captains_boots_armor`; 0 turns one off).
Crafted: the captain's coat from six black wool with a gold ingot on top, red wool in the middle and leather below;
the breeches from black wool in the leggings shape with leather at the waist; the boots from black wool over leather.
The officer's coat from blue wool round white wool with a gold ingot on top; the officer's breeches from white wool in
the leggings shape with blue wool at the waist; the officer's boots from two gold nuggets over black wool over leather.
A new Lieutenant receives the officer's coat, breeches and boots together (see Careers); navy officers do not drop them.
A slain pirate captain drops each piece of his clothing with a 35 % chance (server config
`mobs.captain.clothing_drop_chance`).

## 11. All items

| Item | Recipe | What it does |
|---|---|---|
| Rapier | 2 iron ingots, 1 stick | Sword. See [Weapons](#9-weapons-and-combat). |
| Cutlass | 3 iron ingots, 1 stick | Sword. |
| Saber | 2 iron ingots, 1 gold ingot, 1 stick | Sword. |
| Pistol | 2 iron ingots, 1 flint, 1 planks | Single-shot flintlock: load with lead shot and gunpowder, fire. See [Firearms](#firearms). |
| Musket | 2 iron ingots, 1 flint, 1 planks | Longer reload, flatter and tighter shot. See [Firearms](#firearms). |
| Lead Shot | 2 iron nuggets (gives 4) | Ammunition for pistol and musket, one per load, with one gunpowder. |
| Oar | 1 planks over 2 sticks, on a diagonal | What a rowing hand holds. In your own hand it is only for the look. See [Oars](#oars). |
| Cannonball | 4 iron ingots (gives 2) | Ammunition for the cannon, loaded after the gunpowder. |
| Chain Shot | 2 iron ingots, 1 chain (gives 2) | Cannon shot that tears sails and cuts rigging, no hull damage. See [Cannons](#cannons). |
| Grapeshot | 8 iron nuggets, 1 string (gives 2) | Cannon shot: a cone of nine pellets that hurt the people on a deck. See [Cannons](#cannons). |
| Grappling Hook | 3 iron ingots, 1 string | Throw it at another ship to hook it and haul the hulls together. See [Grappling hook](#grappling-hook). |
| Rope | 3 string in a column | Use it on a cleat or mooring ring, then on a second one up to 16 blocks away on the same ship: a stay (2+ blocks lower, cleats only) or a decorative rope line. It glints while it remembers the first anchor. |
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

Everything is in the "Pirates 'n' Ships" creative tab. Items without a recipe come from loot, trade and
NPCs (the captain's whistle has none; it is in the creative tab).

---

## 12. Commands

Every command hangs under `/pirates`. Most need operator rights (permission level 2); they reach each system
directly for testing. Every player may use `/pirates career`, `/pirates rep`, `/pirates quest list`,
`/pirates quest abandon <id>` and `/pirates waves` (level 0); their other branches stay at level 2. Level 2 is the only
other level in use.

**Open to every player (level 0)**

| Command | What it does |
|---|---|
| `/pirates career` | Your navy rank, infamy and letter of marque. |
| `/pirates rep` | Your reputation with the navy, the pirates and the villagers. |
| `/pirates quest list` | Your accepted quests. |
| `/pirates quest abandon <id>` | Drops one of your quests. |
| `/pirates waves` | The current sea state and any override. |

**Operators (level 2)**

| Command | What it does |
|---|---|
| `/pirates career <player>` | Another player's career. |
| `/pirates career set <player> navy <none\|midshipman\|lieutenant\|captain\|commodore\|admiral>`, `… infamy <deckhand\|buccaneer\|dread_captain\|pirate_lord>` | Sets a rank. |
| `/pirates career letter <player> grant\|void` | Grants or voids a letter of marque. |
| `/pirates career ship_grant <player> reset` | Forgets the player's ship grants; a rank held re-grants at once. |
| `/pirates rep <player>`, `/pirates rep set <player> <navy\|pirates\|villagers> <value>` | Reads or sets a reputation. |
| `/pirates quest offer <port> <type>` | Creates a quest offer at a port (hunt_pirates, kill_monster, turn_in, deliver, find_treasure, hunt_navy, hunt_captain, escort, plunder_convoy, hunt_patrol, hunt_ship). |
| `/pirates quest complete <id>` | Completes one of your quests at once. |
| `/pirates waves set <calm\|moderate\|rough\|storm> [fromDegrees]`, `/pirates waves clear` | Holds a sea state (optionally a wave direction), or releases it. |
| `/pirates wind get` | Shows the wind at you and whether it is fixed. |
| `/pirates wind set <fromDegrees> <strength>` | Fixes the wind: the compass bearing it blows from, and blocks per second. |
| `/pirates wind clear` | Returns to the natural wind. |
| `/pirates ship forces` | For the ship you stand on: speed, heading, wind angle, how deep it sits, rudder and anchor, and every force on it. |
| `/pirates ship rigging` | Every yard and what it carries (or why not), the triangular sails, the crew aboard. |
| `/pirates ship info` | The ship you stand on: id, name, origin, wreck flag, load. |
| `/pirates ship templates` | Lists the prebuilt ships. |
| `/pirates ship place <template> [assemble \| force [assemble]]` | Puts one on the water in front of you, bow away from you; `force` ignores obstructions, `assemble` makes it a ship. |
| `/pirates ship orders [port]`, `/pirates ship orders finish <order>` | Shipyard orders at the nearest (or a named) port; finishes one at once. |
| `/pirates crew spawn` | Spawns a crew member. |
| `/pirates crew info [crew]` | Morale, crew and bunks, supplies left, the last payday. |
| `/pirates crew assign <crew> <station>` | Puts a crew member at a station. |
| `/pirates crew release <crew>` | Releases crew from their stations. |
| `/pirates crew order <hoist\|reef\|furl\|pump\|fire\|load\|fire_at_will\|drop_anchor\|raise_anchor> [crew]` | Gives an order, ship-wide or to the named crew. The anchor keeps its two directions here; the whistle wheel and the ship screen have one anchor toggle. Boarding is given on the whistle or the ship screen only. |
| `/pirates crew order course <x> <z> [<x> <z> …] [loop]` | Gives the helmsman a course through waypoints. |
| `/pirates crew hire <sailor\|pirate\|navy> [port]`, `/pirates crew dismiss <crew>` | Hires a candidate; dismisses a hired hand. |
| `/pirates provisions show <crew> [pos]` | What the pantry you look at holds, and how many days it feeds that crew. |
| `/pirates provisions advance <days> <crew> [prisoners] [rum] [pos]` | Lets that crew live off the pantry for some days and prints what happened. |
| `/pirates flag get\|strike\|raise <pos>`, `/pirates flag set <pos> <none\|merchant\|navy\|jolly_roger\|custom>` | Reads or changes a flagpole without the delay. |
| `/pirates trade port <name> <seafarer_village\|navy_outpost\|pirate_island> <tropical\|temperate\|arid\|cold>` | Creates a test port. |
| `/pirates trade open\|goods\|buy\|sell …` | Opens a market, lists prices, buys and sells with real coins and items (`sell … plundered` sells marked goods, `container` uses the crate you look at). |
| `/pirates trade contracts <from> <to>`, `/pirates trade contract list\|accept\|deliver …` | Delivery contracts between two ports. |
| `/pirates trade weight` | Cargo weight of the container you look at. |
| `/pirates trade plunder` | Marks the stack in your hand as plundered. |
| `/pirates trade coins` | Shows the doubloons you carry (it gives none). |
| `/pirates trade desk bind <port>`, `… unbind`, `… info` | Binds the harbor desk you look at to a port, unbinds it, or names its port. |
| `/pirates law score get\|set\|add <target> …` | Reads or changes a criminal score. |
| `/pirates law crime <target> <crime>` | Records a crime (any crime in the [table](#criminal-score)). |
| `/pirates law last <target>`, `/pirates law hostile <target>` | The last recorded crime, and whether the navy would attack. |
| `/pirates law fine <target> <doubloons>` | Pays a fine without an officer. |
| `/pirates law bounty list\|place\|claim\|clear …` | Bounties. `claim proof` uses the Bounty Proof in your hand, `claim <target> dead\|alive` claims directly. |
| `/pirates brig list [radius]` | Prisoners nearby. |
| `/pirates brig capture <target>` | Takes the target prisoner. |
| `/pirates brig deliver <prisoner> [deckhand\|buccaneer\|officer\|captain]`, `… ransom <prisoner> <common\|merchant\|navy_officer> [captain]`, `… pressgang <prisoner>`, `… release <prisoner>` | What an officer or the whistle does with a prisoner, without them. |
| `/pirates brig releases [target]` | The release record. |
| `/pirates world ports`, `/pirates world port nearest` | The port registry; the nearest port with its berths and treasure sites. |
| `/pirates world treasure give [port]` | A treasure map for that port or the nearest pirate island. |
| `/pirates world factions`, `… factions report <event>`, `… factions reset` | The faction state; applies an event; resets it. |
| `/pirates world raid chance`, `/pirates world raid <port>` | Raid chances per settlement; starts a raid at once. |
| `/pirates world voyages [list]` | Active voyages. |
| `/pirates world voyages spawn convoy <from> <to>`, `… spawn near <convoy\|patrol\|raid>` | Starts a convoy between two ports; spawns a test voyage 48 blocks ahead of you, materialized. |
| `/pirates world voyages advance <id> <blocks>`, `… lane <from> <to>`, `… materialize <id>`, `… dematerialize <id>` | Moves a voyage on; prints a sea lane; turns a voyage into real ships and back. |
| `/pirates world patrols` | The navy patrols. |
| `/pirates hazard spawn <waterspout\|whirlpool> [pos]`, `/pirates hazard clear [radius]` | Spawns or removes sea hazards. |
| `/pirates mob spawn <pirate\|sailor\|navy_soldier\|navy_officer\|pirate_captain\|harbor_master\|shark\|kraken> [count]` | Spawns the mod's mobs. |
| `/pirates mob debug on\|off` | Traces what nearby duelists decide. |
| `/pirates mob captain spawn\|list`, `/pirates mob captain voyage <island>` | Pirate captains; sends an island's captain to sea now. |
| `/pirates mob squad info\|patrol\|return` | The navy outpost's watch. |

The playtest checklists in [`playtests/`](playtests/) show the commands in use.

---

## 13. Configuration

Open it in game under Mods → Pirates 'n' Ships → Config (on Fabric through Mod Menu). Gameplay settings are in the
server config, which is synced to clients; how things look and sound is in the client config. Every feature has a
switch and every strength or rate has a value. One row per top-level section, as declared in the code
(`ModConfigs.server(...)` / `ModConfigs.client(...)`); nested groups are named after the dot.

**Server config**

| Section | What it controls |
|---|---|
| `core` | Debug logging. |
| `perf` | The manual performance test's budget (`budget_ms`, 50: the p95 tick time allowed with four NPC ships); nothing in a game reads it. |
| `assembly` | Assembly and disassembly on/off, block limit, how still and level a ship must be to disassemble, restoring the sea, draining the hull and moving entities on disassembly; `split` (ships breaking apart, wreck size and lifetime), `rejoin` (the shipwright's toolkit: max piece size, gap, angle, nails, work time, toolkit durability). |
| `dry_hull` | Dry hull on/off, buoyancy of the dry volume, weight of flood water, drowning under flood water (`flood_breath`), partial blocks drawn dry, analysis and sync timings. |
| `flooding` | Flooding on/off, inflow rate, re-analysis on tilt; bilge pump on/off, rate, reach, use time and exhaustion; hull patch on/off. |
| `ships` | The ship HUD on/off and its sync, shipwright orders (on/off, build days per template in `build_time_days` (only `default` matches a template today), price and material factors, orders per port); `sinking_enabled`, `wreck_persistence_days` and the `order_price_doubloons` group are declared but read by nothing yet (the price is the template's times `order_price_factor`; see `docs/audit/config-defaults.md`). |
| `ship_identity` | Name boards showing the ship's name, their refresh, how far the name is drawn (`name_board_distance`, 64 blocks). |
| `ship_screen` | The ship screen at the helm on/off (off: sneak-use disassembles at once), its reach, how often it refreshes. |
| `ship_decor` | The ship's bell ring time; sleeping in the sea cot, on land and aboard; railings on or off and their collision height (`railings_enabled`, `railing_collision_blocks`). |
| `rigging` | Ratlines on/off; ratline nets on/off, their longest length (32 blocks, up to 64) and flattest pitch, the climbing assist; how many nets one mast hoop holds and how far apart two neighbouring chainplates may be to be netted together; boarding nets along the rail on/off (`boarding_nets`), their longest length, how long they hold a boarder and the cutlass hits that cut one. |
| `wind` | Wind strength range, how fast it changes, weather multipliers, gusts, regional variation, sync interval. |
| `sailing` | Sail force, half-trim factor, rudder strength and turning authority (`rudder_force_factor`, `max_rudder_angle`), keel drag, roll and pitch damping; `sails` (yard gaps and lengths, stay length and drop, rope lines and sag, dyeing, banners and their minimum size). |
| `sailing_runtime` | Sailing forces, steering and anchor on/off, sail heel factor, sails needing water, rudder steps, the anchor chain's length (`anchor_chain_length`), draft, scan interval. |
| `stability` | Righting and heel: on/off, righting factor and damping, the heel at which sails spill, pitch righting, the auto-ballast (`auto_ballast`, on by default) and its cap. |
| `helm` | `wheel`: steering by turning the wheel (`drag_steering`), mouse and key rates, turns lock to lock, view lock, reach. |
| `oars` | Rowing on/off (`enabled`), each rower's thrust, the bench bonus (1.25), the rowing speed limit (3 blocks a second), the yaw of unequal sides, when the crew ship oars under sail. |
| `anchor_chain` | The anchor's fall and sink speed, raising speed, chain stiffness and damping, holding force, drag and scraping, heel, the sounds and their volumes. |
| `hull_creaking` | Creaking on/off, creaks per second, minimum gap, volume and pitch ranges, the rolling rate that counts. |
| `waves` | Sea state on/off, amplitude and components, wave groups, how fast the state and the wave direction change, heave and torque on ships, spill over low rims, vanilla boats and floating items riding the waves, sync. |
| `hazards` | Waterspouts and whirlpools (`waterspouts`, `whirlpools`: on/off, chances, durations, radii, pull, lift, spin, drag-down, drift, sail damage, per-player cap), spawn distances and interval, ship force scale and mass cap; `kraken` (on/off, chance per day). |
| `crew_stations` | Crew stations on/off, time per trim step, order and acknowledgement radius; `capstan` (crew at the capstan), `course` (the helmsman's course keeping), `job_board` (free hands taking open jobs). |
| `lookout` | Crow's nest lookout on/off, calls on/off, range, scan interval, how long a sighting is remembered, when it is called again, how long its compass mark stays, the land rays. |
| `crew` | Crew size; `wages`, `morale`, `desertion`, `mutiny`, `meals`, `hammock` (players sleeping in hammocks), `hiring`, `walk`, `rail_lean` (idle crew leaning on the rail: on/off, radius, how long, how many per ship, the pause between). |
| `provisions` | Consumption on/off and rate, food and water per crew and day, rum ration and morale, drunkenness, hunger and thirst effects, scurvy, spoilage, water barrel capacity, rain refill, weights. |
| `survival` | `cold_water` (on/off, freeze rate, warm effect length), `swim_hunger` and its multiplier. |
| `sea_chest` | Sea chest on/off, worn speed, sink pull, wind drift and its cap, draft; paddling on/off, speed, backing speed, turn rate, hunger. |
| `flags` | Hoisting delay, flags following the wind (land and ship update intervals), banners as flags, stacked poles and the pole height limit. |
| `cargo_trade` | Cargo weight on ships, weight factor and interval, the treasure map price; `containers`, `load_levels`, `market`, `market_backend`, `contracts`, `plunder`, `harbor_desks`, `port_fees`. |
| `law` | Criminal score on/off, thresholds, decay, fines (`officer_fines`, cost per point), bounties (navy and player), plunder notices, ransom at a port; `severity` and `repeat_cooldown_seconds` per crime, `world` (crime detection, theft witnesses, navy hostility), `flags` (seeing flags, blown cover), `bounty` (officer turn-ins, notice boards), `pirate_turn_in` (rewards per kind). |
| `flags_brig` | False-colours detection (ranges, rates, crow's nest observers), the navy flag's minimum standing, NPC surrender, capturing players, prisoner escapes and interactions. |
| `brig` | Capture threshold, leading distances and speed, cell size, escape chances (alone and in a cell under low crew morale), ransoms, player capture time. |
| `reputation` | Reputation on/off, decay, thresholds (pirates friendly, navy hostile, villagers trading), price swing, the daily village trade cap; `deeds` (what each deed is worth to each side). |
| `careers` | Careers on/off, enlistment, desertion, the officer's reach, name prefixes and titles on ships, prize money; `navy.*` and `infamy.*` (what each rank needs), `letter` (letter of marque), `rewards` (gifts, fee waiver, flag right, navy shipyard prices in `navy_ship_price`, infamy prices), `ship_grants`. |
| `quests` | Quests on/off, offers per port, deadlines, active cap, counts, sea quests, escort rules, the captain hunt radius; `types` (which port kind offers which quest), `rewards`. |
| `world` | `structures.*` (seafarer villages, navy outposts with their garrison, pirate islands with buried treasure, wrecks: on/off, frequency; `spacing` and `separation` only feed datagen, a datapack changes them), `spawn_weights` (only `pirate` is read; the shark's weight lives in `mobs`), `treasure_maps`. |
| `world_simulation` | The off-screen world on/off, convoys and patrols per day, voyage cap, materialize radius, retaliation; `factions`, `lanes`, `voyages`, `materialize` (crews, fighters, guns and shot lockers of NPC ships), `navy` (patrols), `raids`, `captain` (pirate captains at sea). |
| `mobs` | Hostility, detection and fight ranges, duelist behaviour, skill tiers, musket timings and ammo, shove, drops; `pirate`, `sailor`, `navy_soldier`, `navy_officer` (on/off, peaceful), `captain` (pirate captains, duels, drops, respawn), `harbor_master`, `squad` (the outpost's watch), `kraken`, `shark`. |
| `melee` | Skill-based sword fighting (`skill_based_combat`, on by default): parry and riposte windows, guard, stamina, stagger, feint recovery, NPC feints and skill, `directional_mode` (declared, no effect yet); `sounds`. |
| `firearms` | Firearms on/off, `fire_on_attack`, ball lifetime and gravity, cooldown, gunpowder use; `aim` (hold and steady times, aimed spread, sneak lowers the gun), `pistol` and `musket` (damage, muzzle velocity, spread, reload, recoil, sound). |
| `combat` | Cannon block damage, rain misfires; `damage_multipliers` (cannon on blocks and entities, firearms). |
| `cannons` | Cannons on/off, damage, muzzle speed, gravity, reload, elevations, blocks per hit, recoil and impact, ball lifetime and water, `mobGriefing` and spawn protection, drops, a barrel run into a bulwark (`walls_block_barrel`), glancing hits; `swivel`, `chain_shot`, `grapeshot`, `crew` (loading by the crew, load preference), `npc` (fire at will). |
| `grapple` | Grappling hook on/off, throw speed, rope length, haul force and damping, hold distance and slack, shore haul, entity damage, lost hooks, latching on world blocks and on your own ship, cleats and rings, railings holding the hook and tearing out (`latches_hold`, `railing_break_force`), climbing; `launch` (from the musket), `slide` (sliding along a rope). |
| `boarding` | The "Board!" order on/off (`board_order`), the widest gap jumped (`board_gap`, 2), gunners and helmsman staying (`boarders_keep_stations`), the clear deck's hold before "Her deck is yours" (`capture_seconds`), NPC boarding on/off and its delay (`npc_boarding`, `npc_board_delay`), the NPC recall (`npc_recall_outnumbered`, `npc_recall_flooded`), swords from the chests (`take_arms`), the punch (`unarmed_damage`), the crew's sword skill (`crew_skill`), the crossing's fallback (`cross_timeout_ticks`), how often boarders look around (`order_check_ticks`); `plank`: the boarding plank on/off, length, steepest pitch (`max_pitch`, 45 degrees), pick-up, break distance and rise. |
| `apparel` | Armour of the hats, coats, breeches and boots. |
| `chart` | Charts on/off, cell size, sampling radius and interval, shallow depth, the cell cap, opening without the item (`open_without_item`, off by default), other players visible, marker cap; `tiles` (map tiles). |
| `almanac` | The Captain's Almanac readable on/off (`enabled`), how long a placed tome stays open (`open_ticks`), the last page remembered per player (`remember_page`), the first-join gift (`give_on_first_join`). |

**Client config**

| Section | What it controls |
|---|---|
| `audio` | Music on/off and volume, the gap between tracks, shanties aboard; `ambience_volume` is declared but read by nothing yet. |
| `ship_hud` | The ship HUD on/off, corners of the compass and the hull strip, margin, scale, speed unit. |
| `helm_view` | The rudder angle line at the wheel. |
| `dry_hull_view` | The flood water surface, hiding water plants inside dry hulls. |
| `pump_visuals` | The pump's stroke animation. |
| `bell_visuals` | The bell's swing. |
| `sail_visuals` | Cloth movement on/off, belly, flutter, segments, banner layers on sails. |
| `flag_visuals` | The hoist animation, banners drawn upright. |
| `wind_effects` | The wind streaks: on/off, density, minimum strength, radius, height, life, look (spread, jitter, wobble, puffs, opacity), gust boost. |
| `wave_effects` | Camera sway (off by default), foam, spray, the moving water surface, the wake, how far vanilla boats tilt. |
| `hazard_visuals` | Particle density and sounds of waterspouts and whirlpools. |
| `cannon_visuals` | The barrel's tilt animation. |
| `boarding_view` | The boarding plank's placement preview (`preview`: the run outlined green, the cell in the way red). |
| `sea_chest_visuals` | The floating chest's bob. |
| `chart_visuals` | Doodles on the chart. |
| `melee_input` | Hold-to-thrust and parry-tap thresholds. |
| `melee_hud` | Stamina bar on/off, position, scale, offsets, opacity, fading. |
| `melee_animations` | Sword animations on/off, first-person mode, crouch, fade, layer priority. |
| `firearm_animations` | Pistol and musket animations on/off. |
| `firearm_view` | The musket's zoom while aiming. |
| `career_hud` | The rank box: mode (fade, pinned, hidden), how long it shows, its corner. |
| `almanac_screen` | Page-turn animation length and sound, book size. |

Many defaults are first guesses that need playtesting. [`progress.md`](progress.md) lists the ones to review, and [`audit/config-defaults.md`](audit/config-defaults.md) reviews every value against the design (CFG1). `python3 tools/check_config_docs.py` checks this table against the code.

### Testing note: the performance test (PERF1)

A headless load test measures the server's tick time with ships around a player on a rough sea: the player on a
starter sloop with railings, three ratline nets and a boarding net, six crew (two rowing, two at stations, two leaning
on the rail), and N = 1, 4 and 8 materialised armed NPC ships plus a merchant convoy and a navy patrol, two of them
firing at each other and three pirates boarding the convoy. It is registered only with the switch on, so the normal
suite, CI and releases never run it: `./gradlew :neoforge:runGameTestServer -Ppirates_n_ships.gametest.only=PerformanceGameTests
-Ppirates_n_ships.perf=true` (on Fabric `:fabric:runGameTest` with the same flags; add
`-Ppirates_n_ships.perf.dir=<dir>` for the report directory, default `perf` in the run directory). Each N measures
1200 ticks after settling (the first test of a run also warms the JVM up for 1200 ticks) and writes `perf-N.txt`
(min/median/p95/max tick time per tick and per second, ships, entities by type), `perf-summary.txt` (the cost of one
more ship) and, for N = 8, a stack-sample profile of the mod's and Sable's methods plus vanilla's profiler report
(`profile-n8-1.txt`; the `/perf` recorder's zip goes to `debug/profiling`). The N = 4 test fails when its p95 tick
time is over `perf.budget_ms`. The GameTest server ticks as fast as it can, so 1200 ticks take less than a minute
and the numbers carry the noise of whatever else runs on the machine: run it twice and keep the better run. Findings
and the numbers so far: [`audit/performance.md`](audit/performance.md).

---

## 14. Datapacks

**Definitions** are JSON files under `data/<namespace>/pirates_n_ships/<type>/<name>.json`. A datapack can add entries
in its own namespace, or replace one of ours by shipping a file at the same path. A broken file is logged and skipped.

| Type | Folder | Contents |
|---|---|---|
| Ship templates | `ship_template` | Structure id, name key, helm position, waterline row, bow direction, price. The structures come from WorldEdit schematics in `art/schematics/` through `tools/schem_to_structure.py`. |
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
| entity `pirates_n_ships:navy` | Navy members (the navy soldier and navy officer). |
| entity `pirates_n_ships:law_enforcers` | Never get a criminal record (iron golems, the navy). |
| entity `pirates_n_ships:not_capturable` | Can't be shackled (bosses). |

Sable reads block weight from its own tags. The mod's blocks are already sorted into `sable:light`, `sable:heavy` and
the like.

---

## 15. What does not exist yet

Checked against the code on 2026-10-09. Everything else in this guide is built and on main.

- **Audio:** the sea music and the creaks exist, but most actions have no sounds of their own yet; that waits for sound files. Every action, its sound today and the recording that would fit are listed in [`audit/sounds.md`](audit/sounds.md), silent ones first.
- **Melee extras:** feints by players and a directional attack and parry mode (optional milestone); the stagger tuning is an open decision.
- **The logbook** named in the design (a record of voyages and deeds) has no block or screen yet.
- **A wanted display** beyond the chat lines and the rank box.
- **Hull paint and trim** (dyeable planks), an optional item in the design.
- **On Fabric:** no config screen without Mod Menu (edit the config files instead), and the Fabric client has not been
  played yet. The Almanac works on both loaders.
