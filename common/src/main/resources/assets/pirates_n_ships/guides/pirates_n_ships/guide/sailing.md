---
navigation:
  title: "Sailing"
  parent: index.md
  position: 30
  icon: pirates_n_ships:yard
item_ids:
  - pirates_n_ships:boarding_plank
  - pirates_n_ships:capstan
  - pirates_n_ships:chart
  - pirates_n_ships:hammock
  - pirates_n_ships:map_tile
  - pirates_n_ships:rope
  - pirates_n_ships:sail_winch
  - pirates_n_ships:treasure_map
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

## A helmsman holds the course
Assign a crew member to the ship's helm (whistle on the crew member, then on the helm) or simply give a course:
`/pirates crew order course <x> <z>` (more pairs for waypoints, `loop` to sail them round and round); a free hand takes
the helm by himself. He turns the wheel toward each point in turn and puts the rudder midships when the last one is
reached ("We've arrived, captain!"). He does not touch the sails: order "Hoist sails" as well. Take the wheel
yourself at any time and he lets go; release it and he steers on. Only the ship's steering helm holds a course, and
if the ship stops making headway with its sails set he tells you it is stuck. Server config `crew_stations.course`.

## Heel
Wind on the side of a sail pushes the ship over. A hull rights itself: the wider and the shallower it is, the stiffer
it stands. The starter sloop leans about 2° in a moderate beam wind and about 5° in a strong one, and comes back
upright a second or two after you furl. Sails spill their wind as the heel nears 25°, so even a storm will not lay a
ship on its side under sail alone. Cargo stowed to one side and water in the hold still make a ship list; pump it out
and trim the load. Server config `stability`.

## Capstan and anchor
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

## Hauling with the rope
With your hook set in a ship and the rope in your hand, hold sneak: the rope's length freezes. Walk back and the
rope pulls the ship toward you; a hook near the bow or stern swings it round. Let go of sneak and the rope pays out
again. Pull too hard and the rope slips through your hand rather than snapping. Standing on the ground or a deck
you feel nothing; in the air or in the water the rope pulls you toward the hook. A rope tied to a cleat can't be
hauled by hand. **Climbing:** right-click your own rope while it is still in your hand to climb it: you are pulled hand over hand to
the hook when it is at least two blocks above your feet (a mast, a cliff, a hull side from the water); sneak to let
go. A hook level with you or below is no climb; tie the rope off on a cleat to use it as a line, and only such a tied
rope is a line others can slide along. Server config
`grapple.hauling`, `haul_stiffness`, `haul_damping`, `haul_max_force`, `haul_player_pull`.

## Fire at will
Pick "Fire at will" on the captain's whistle and your gun crews take over their cannons. Every second each crew
raises or lowers its barrel one step toward the best elevation for the nearest hostile ship within its arc (15°
either side of the barrel) and 64 blocks, leads a moving target, loads from powder and shot nearby, and fires when
the shot will hit. Who counts as hostile depends on your own flag: under a navy flag they fire on the Jolly Roger
and on wanted captains; under the Jolly Roger they fire on navy and merchant ships; under a merchant flag or none
they fire only on the Jolly Roger. Nobody fires on a ship that has struck its colours, and your crew holds fire if
you strike yours. Shots your crew fires on its own break fewer planks than a captain's "Fire!". "Release crew" ends
it. Turn the ship to bring the guns to bear: a cannon cannot turn sideways. Server config `cannons.npc`.

## Boarding along the rope
Once a grappling rope is latched onto another ship, look at the rope and use it: you hang from it and slide down to the
lower end, following both ships as they move. Sneak to let go. A level rope is crawled slowly toward the hook. From the
crow's nest down to an enemy deck is the classic move. Server config `grapple.slide`.

## Boarding plank
Craft one from three wooden slabs over two iron nuggets. When your ship lies alongside another (haul her in with the
grappling hook first), stand at your gunwale and use the plank on the top of a gunwale or rail block, facing the other
ship. A plank up to four blocks long runs straight out, level with the top of that block, and hooks onto the other
deck. Used on the side of the block, it lies a block lower. The far deck may sit one block higher or lower than the
plank. Walk across, but keep the ships together: when they drift apart by more than a block and a half, the whole
plank breaks and drops back as one item. Breaking any part of it takes the whole plank down. Server config
`boarding.plank`.

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

## Decor
Dress up your ship. The **Ship's Lantern** (a lantern and two gold nuggets) stands on deck, hangs from a beam or
from a bracket on a wall, and lights like a lantern, even underwater. The **Ship's Bell** sits on a post or a wall;
right-click to ring it. **Rope Coils** (four rope) stack up to four on one spot. **Stern Windows** go into a wall
from outside; right-click to close or open the shutters. The **Chart Table** is a captain's table with a chart
spread out on it. The **Sea Cot** is a wooden bed for the captain's cabin: on land you can sleep in it and set your
spawn like a bed, but not while it is aboard a ship at sea. Server config `ship_decor`.

## Hammocks
Your crew sleeps in hammocks. Hang one between two supports at the same height (fence posts, walls, logs, or a solid
wall such as the hull side): click the block next to one support while looking toward the other. Recipe: 2 string
over 3 wool. At nightfall every crew member who is not at a station turns in to the nearest free hammock and gets up at
dawn. A night in a hammock raises its morale by 5; a night on a ship without a free hammock for it lowers it by 10 (it
will grumble). Crew on duty all night are unaffected, and an order at night gets sleepers up at once. Each hammock is
one bunk: use the captain's whistle on a crew member, or `/pirates crew info`, to see morale and "crew 3 / bunks 2".
Players can't sleep in hammocks. Server config `crew.morale`.

## When an order reaches nobody
If the whistle or `/pirates crew order` says nobody carries an order out, the line now tells you why: no ship under
you, no station on board can do it, nothing to do, or no sails with the reason (a gap over 8 blocks between the yards,
a block in the mast between them, yards off the mast's column, a lower yard longer than the upper). `/pirates ship
rigging` lists every yard and what it carries, the triangular sails and the crew aboard.

## Upkeep
Every dawn your crew eats and drinks one day of provisions from the pantries and water barrels aboard and wants its
pay: 2 doubloons each, taken from any chest, barrel or cargo crate on the ship (the ones nearest the helm first).
Hungry or thirsty crew lose morale and work slower; rum cheers them up; weeks without citrus or fresh food bring
scurvy. Unpaid crew lose 8 morale, paid crew gain 1. A sailor whose morale stays below 20 for two dawns deserts. If
mutiny is enabled in the config, a crew whose average stays below 15 for three dawns turns pirate and takes your ship.
`/pirates crew info` shows supplies left, the last payday and the work speed; the whistle shows "unpaid" next to
anyone you could not pay. Server config `crew.wages`, `crew.desertion`, `crew.mutiny`, `provisions`.

## Orders, not assignments
You don't have to assign every sailor. Give an order with the whistle (or `/pirates crew order`), and every unmanned
station that can carry it out becomes an open job: free crew standing on your ship take the nearest one by themselves
within a second, get to work and stay there afterwards. Crew you put at a station yourself with the whistle stay put
and are never moved. If nobody is free you hear "No free hands" once; the job waits for the next crew member who comes
aboard. "Release crew" sends everyone off and cancels the open jobs. Server config `crew_stations.job_board`.

## Ordering a ship
At a seafarer village, open the harbor master's desk and switch to the **Orders** tab. Each ship shows its price in
doubloons, how many days the shipwright needs, and the logs and wool he wants. Click **Order** to pay and receive a
**Ship Receipt**; its tooltip shows how long the ship still needs. When the receipt says "Ready for pickup", use the
same desk while holding it: your ship appears assembled at a free berth beside the pier, and it is yours. If both
berths are taken, come back later; the receipt stays valid. The shipwright works on at most three ships at once.
Server config `ships.shipwright_orders`, `build_time_days`, `order_price_factor`, `order_materials_factor`,
`max_orders_per_port`.

## Seafarer villages
Seafarer villages generate on beaches. A stone quay with the harbor master's hut faces the sea, and a plank pier runs
straight out over the water with two ship berths, one on each side halfway out. Streets lead inland with cottages, a
tavern and a shipwright's shed, and end in a small cobbled place. The harbor master's desk belongs to the village's
port: use it to open the port's market. A desk you place anywhere inside a village joins that port too. Operators can
list ports with `/pirates world ports` and find the nearest with `/pirates world port nearest`. Server config
`world.structures.seafarer_village`.

## Navy outposts
Navy outposts are stone forts on beaches, rarer than villages. A sea gate leads to a quay with two berths. The
curtain walls carry cannons (unloaded) and end in corner towers. One building stands outside the land gate: a
barracks, a brig with two cells, or a watchtower. The harbor master's office in the court holds the navy market's
desk and a notice board. Every outpost has a garrison that stands at its posts and never wanders, despawns or
respawns: by default one officer beside the office door and six soldiers at the land gate and on the walls. Use the
officer to turn in shackled pirates and bounty proofs, to pay fines with doubloons, and to ransom captured navy
officers or merchants. With `law.ransom_needs_port` on, only an outpost's officer pays ransoms. With a bounty, the
soldiers open fire on sight. `/locate structure pirates_n_ships:navy_outpost` finds one; `/pirates world ports`
lists it. Server config `world.structures.navy_outpost`.

## The watch
A navy outpost never sleeps. Every so often the officer of the fort calls three of his soldiers from their posts and
leads them round the fort in file: across the parade court, through the land and sea gates, out onto the quay, up the
stairs and along the walls, stopping a while at each post. Strike one of them and the whole squad turns on you, the
officer's blade first; kill a man and another leaves his post to fill the file at the next stop. At nightfall the
watch returns to the posts, and the guards stand fast again until morning. Operators: `/pirates mob squad
info|patrol|return`. Server config `mobs.squad`.

## Ship HUD
While you stand on a ship, a small panel in the top right corner shows its state. The compass rose turns a little
ship-shaped needle to the bow's heading; the light arrow outside the rose sits on the side the wind comes from and
points the way it blows: the longer it is, the stronger the wind, and it turns amber in a gust. Below it you read the
speed (in knots, or blocks per second) and the rudder angle, then the ship's name and how heavily it is laden. The
strip at the bottom is your hull from bow (left) to stern: one cell per compartment, filling blue as water comes in,
with a red mark where a breach lets the sea in and a pump sign while a pump drains it. Client options under
`ship_hud`: on/off, corner, size and speed unit; servers can switch it off with `ships.ship_status_hud`.

## Cargo weight
What you carry weighs the ship down. Crates, cargo barrels, pantries and water barrels get heavier as they fill: a full
crate weighs as much as forty planks. Chests and other vanilla containers press down where they stand, so a heavy
chest in the bow trims the ship by the bow. Spread heavy cargo and keep it low and central. At the wheel the rudder
line shows the load: Light, Laden, Heavily laden or Overloaded (`/pirates ship info` shows the numbers). A laden ship
sits lower, so it floods sooner through a breach, and it is slower to accelerate and turn. Server config
`cargo_trade.cargo_weight_affects_ships`, `weight_factor`, `weigh_interval_ticks`, `load_levels`.

## The factions
The sea has three powers: the Navy, the Pirates and the Merchants. Each has a temper (aggression) and a purse
(wealth), and each pair shares a measure of bad blood (tension). Navy kills of pirates, plundered merchants, convoys
that arrive or sink, raids and lost patrols all shift them, and so do your own deeds. Tempers cool a little every
day. High tension between the Navy and the Pirates means more patrols, more hunting and more raids. Operators can see
the state with `/pirates world factions`. Server config `world_simulation.factions`.

## Reputation
The navy, the pirates and the villagers each remember you on a scale from −100 to 100 (`/pirates rep`). Killing
pirates and turning them in pleases the navy; killing navy sailors, plundering merchants and selling to fences wins
the pirates over; trading at villages pleases the villagers and harming them does the opposite. Scores drift back
toward 0 by about 2 points a day. Villagers and fences give up to 10 % better prices to people they like and worse to
people they don't, and villagers stop trading below −60. Pirates leave you alone above 40 until you strike first, and
below −60 the navy opens fire even without a bounty. A navy flag is only honest while your navy reputation is at
least 0, you have no bounty and you are not a suspect; otherwise the navy may see through your false colours. Server
config `reputation`.

## Convoys
Merchant ships sail between the ports along sea lanes that keep clear of the coasts. A convoy loads goods where they
are made (cheap) and carries them to a port that wants them (expensive), so prices move: the port it leaves pays a
little more for what it bought, the port it reaches pays a little less once it has sold. Convoys sail about 4 blocks
a second, faster before the wind and slower into it. Harbor masters now reckon contract distances along these lanes,
and routes that pass near a pirate island pay a risk bonus. Operators can watch the traffic with
`/pirates world voyages`. Server config `world_simulation.lanes`, `world_simulation.voyages`.

## Ships on the horizon
NPC ships sail the sea lanes between ports even when nobody watches. When you come within sight of one, it becomes a
real ship: a merchantman under the merchant flag with goods in her hold and a few armed sailors, a navy patrol, or a
pirate under the Jolly Roger. She sails on to her destination and fades back into the distance when you leave. Take
goods from a merchant's hold while aboard and you have plundered her. Sink her with your cannons and the deed is
yours. Kill every fighter aboard and hold her deck for a few seconds, and she is yours, crew and all. Capturing a
merchant is piracy in the navy's eyes. Operators: `/pirates world voyages spawn near convoy|patrol|raid`. Server
config `world_simulation.materialize`.

## Navy patrols
The navy sends patrols between its outposts, or out toward the nearest pirate island and back when an outpost stands
alone; an aggressive navy sends more. A patrol keeps a lookout of about 256 blocks. It gives chase to any player's
ship that flies the Jolly Roger, whose false colours it has seen through, or whose captain is wanted or carries a
bounty of 50 doubloons or more, whatever flag that ship flies. You'll get word when a patrol sights you. Once it
closes in, the patrol circles you at about 20 blocks while its gun crews fire. Strike your colours and its guns fall
silent: it keeps you in sight for half a minute, then sails on. Raise the Jolly Roger again and the chase is back on.
Outrun it (no contact within 64 blocks for two minutes, or more than 384 blocks between you) and it breaks off.
Patrols that kill pirates, and patrols lost at sea, stir up the bad blood between the navy and the pirates.
Operators: `/pirates world patrols`. Server config `world_simulation.navy`.

## Careers
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
let you dock for free. Each new rank comes with a gift: a Lieutenant receives the officer's bicorne and a saber (once;
a full pack drops them at your feet); the Officer's Coat (blue wool, white wool and a gold ingot) is chest armour
that marks you as navy. From Captain the harbor master's desk at a navy outpost has an Orders tab: the
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

## Quests
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

## Pirate captains
Every pirate island has a captain: a named pirate (for example "Black-Tooth Bartholomew Crowe") who keeps to the
middle of his hut, or to the camp trail if the island has no captain's hut. He is a dangerous swordsman with 40
health, and the navy keeps a standing bounty of 300 doubloons on him, posted on every notice board. To fight him
one-on-one, sneak and use him with a sword in hand: if he accepts, his crew within 16 blocks keep out of the duel
unless you strike them. The duel ends when one of you falls, when you run more than 32 blocks away, or after five
minutes. If you hit him first he refuses: no honour, no duel. Slain, he drops his captain's hat (wear it yourself), a purse of doubloons
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

## Pirate islands
Pirate camps sit on beaches, rarer than villages, with a jetty (two berths), tents, a tavern hut, a captain's hut and
a fence's shack under the Jolly Roger. Pirates hang about the camp day and night and attack strangers. The fence's
desk opens a market that buys plunder and rum dear and sells little. Somewhere under two crossed logs a chest lies
buried two blocks deep: dig at the cross for doubloons, rum, provisions and, with luck, a pistol. Server config
`world.structures.pirate_island`.

## Wrecks
Sunken ships lie on the ocean floor of every ocean: a broken sloop, a scattered cargo field, a mast stump with its
yard, or the stern of a larger ship. Each has a chest of ship's stores (doubloons, rum, salted fish, rope, nails,
lead shot, now and then a cutlass, rarely kraken ink). Tall wrecks only lie in deep water; in shallow seas you find
cargo fields. Bring water breathing or night vision. `/locate structure pirates_n_ships:wreck` finds the nearest.
The sea chest in the sloop's hold is empty. Server config `world.structures.wreck.enabled`, `frequency`.

## Treasure maps
Fences on pirate islands sell blank treasure maps (60 doubloons), and wreck chests hold one now and then. Use a
blank map and it marks the nearest pirate island's buried treasure that nobody has found yet (within about 2000
blocks); otherwise it stays blank. While you hold the map, it shows the island in chart style with a red X and
tells you the way: "NW, 340 blocks". At the X, dig about two blocks into the sand and open the chest. The treasure
is found: every map of it turns grey and keeps as a souvenir, and the next blank map leads to another treasure.
Operators: `/pirates world treasure give [port]`. Server config `world.treasure_maps`, `cargo_trade.treasure_map_price`.

## Waves
The sea follows the weather: calm or a light chop in fair weather, rough in rain, a storm in thunder, changing over
about a minute. Ships roll and pitch with the waves (big ships far less than small boats). In rough seas the bow
throws spray, and any open hatch or low rim close to the waterline lets water in at the crests, so close your hatches
and keep a pump ready before a storm. `/pirates waves` shows the sea; operators can hold a state with `/pirates waves
set storm` and release it with `/pirates waves clear`. The camera can roll with the ship (client setting
`wave_effects.camera_sway`, off by default). Server config `waves`.

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
