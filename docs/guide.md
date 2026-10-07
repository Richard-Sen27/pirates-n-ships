# Pirates 'n' Ships: how it works

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

## 1. Your first ship

1. **Build a hull** from any blocks, floating in water. Planks, slabs, stairs and glass are watertight. Leave a
   one-block gap to the shore or a dock: anything that touches the hull becomes part of the ship.
   Make the bottom layer from something heavy such as stone, or the boat will tip (see [Limits](#limits-to-know)).
2. **Place a helm** on deck. Stand behind it and look where the ship should go: the bow is the direction the
   helmsman looks.
3. **Rig a square sail**: a mast of logs or fences with two rows of **yards** across it, one 2 to 8 blocks above the other, and a **sail winch** somewhere on deck. A **capstan** gives you an anchor.
4. **Use the helm.** The connected blocks become a ship: a physics object that floats, with a dry hold.
5. **Use the sail winch** to hoist the sails (furled → half → full). The wind pushes the ship.
6. **Steer at the helm:** click the right third of the wheel for starboard, the left third for port, the middle for
   midships.
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
- Not there yet: a visible water surface inside a flooding room, pumps, patch items, and damage from cannons.

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

### Sea music
The mod brings its own music. Aboard a ship (standing on deck or sitting at a station) the next track is a sea
shanty; at sea but not aboard (an ocean, deep ocean or beach biome) it is an ambient sea track; anywhere else vanilla
music plays as usual. A running track is never cut off: the pool changes when it ends, and the gap between tracks is
2 to 5 minutes by default (client config `audio`: `min_gap_seconds`, `max_gap_seconds`, `shanties_aboard`,
`music_enabled`, `music_volume`). The tracks and their authors are listed in [`credits.md`](credits.md).

### Helm and rudder
On an assembled ship, using the helm turns the rudder one step: the right third of the wheel (as the helmsman sees it)
to starboard, the left third to port, the middle back to midships. There are three steps per side, up to 35°. The
action bar shows the position. The rudder only works while the ship moves through the water, and it reverses when the
ship goes astern.

### Capstan and anchor
The anchor is a real object: it hangs outside the hull on the side nearer to the capstan, just below the deck, and
moves with the ship. Using the capstan runs it out on a chain at 6 blocks per second to the first solid block within
32 blocks below, with the chain rattling, a splash when it enters the water and a thud when it lands; from that moment
the ship holds. Using the capstan again heaves it back in at 2.5 blocks per second until it hangs at the hull again.
Using it mid-way reverses. If there is no ground in reach, the capstan tells you and the anchor stays stowed. A held
ship stays within about two blocks of the anchor point and swings with the wind.

---

## 4. Crew

A **crew member** is a simple NPC. There is no hiring yet: get one with `/pirates crew spawn`.

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

### Pirates, sailors and the navy
Pirates (dark coat, bandana, eyepatch, cutlass) attack players and the navy on sight. They fight with the same
swordplay as you: watch for the raised arm before a slash or the drawn-back arm before a thrust, and parry just
before the blow lands to stagger them and riposte. Navy soldiers (blue coat, white cross belts, tricorn) carry muskets:
they leave you alone unless you are wanted, then keep their distance, aim for a second and fire, and shove you back if
you get close. Officers (gold trim, bicorne) are skilled saber duelists. Pirates and navy fight each other on sight.
Sailors never fight and run from danger. Hitting or killing navy is a crime; killing pirates is not. Pirates drop
doubloons and sometimes a cutlass; navy drop lead shot and gunpowder. They look like recoloured sailors until their
Blockbench models land. Operators spawn them with `/pirates mob spawn <pirate|sailor|navy_soldier|navy_officer>
[count]` or with spawn eggs; natural spawning comes with the world structures. Everything is in the `mobs` server
config.

## 5. Flags

The **flagpole** flies a flag that shows a ship's allegiance.

The flag cloth is one block high and one and a half blocks long and hangs downwind from the top of the pole, so a
pole needs free space downwind.

| Flag | Meaning for the law rules |
|---|---|
| none or Merchant Flag | Neutral to everyone. |
| Navy Flag | Navy and merchants friendly, pirates hostile. A false flag if the captain has a bounty or too little navy standing. |
| Jolly Roger | Pirates friendly, navy hostile, merchants may surrender. Being seen under it is a crime. |
| any vanilla banner | A custom flag: neutral. It keeps its patterns, but the pole shows a generic cloth. |

At the pole:
- **Use it with a flag item:** hoists that flag after 3 seconds and gives back the old one.
- **Use it with an empty hand:** strikes the colors (the flag is lowered but kept), or raises them again.
- **Sneak-use with an empty hand:** takes the flag down.
- Breaking the pole drops the flag.

The flag points downwind, in 90° steps. Nothing reacts to flags in the world yet: there are no navy or pirate ships.

---

Hoist any banner on a flagpole to fly a custom flag: the cloth takes the banner's base colour (its patterns are not
shown).

## 6. Provisions

### Pantry
A container with 27 slots that opens like a chest. It is the ship's food store.
- Anything edible counts as food, weighted by its nutrition. One crew member needs 6 nutrition per day (about one
  loaf of bread and a bit).
- **Fresh food spoils** after 5 days in the pantry and turns into rotten flesh. Preserved food never spoils: hardtack,
  salted fish, salt pork, bread, cookies, dried kelp, honey bottles, golden carrots and golden apples.
- Sneak-use it with both hands empty to read what it holds: food, water and rum, and what spoils next.
- **Hoppers** can only put provisions in, and can only take out what is not a provision (empty bottles, bowls, rotten
  flesh). So a hopper below works as a waste chute.

### Water barrel
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
pirate islands pay less for plunder, navy outposts may notice and confiscate it. The Contracts tab lists today's
delivery offers (Accept pays the deposit) and your accepted contracts (Deliver at the destination port). Stay within 8
blocks of the desk; walking away closes the screen. A desk that belongs to no port says so. Operators bind desks with
`/pirates trade desk bind <port>` while looking at the desk (`<port>` is a full id or a test port name such as `cane`),
check with `/pirates trade desk info`, and `/pirates trade desk unbind`. Server config: Cargo Trade → Harbor Desks
(`desks_enabled`, `desk_reach`). The `/pirates trade` commands remain as a debugging fallback.

Selling plunder at a navy outpost is risky: if the harbor master notices it the goods are confiscated and it counts as
a crime, +15 criminal score (`law.severity.fence_plunder`; several noticed sales at one port within a minute count
once). Pirate fences never report you.

### Contracts
A port offers delivery contracts: bring an amount of a good to another port by a deadline for a reward. Accepting
takes a deposit of 20% of the reward, and delivering pays the reward and returns the deposit. A player can hold three
at a time.

### Plunder
Goods can carry a **plundered** mark (shown in the tooltip). A pirate island buys them at 35% less, no questions asked.
A navy outpost may notice and confiscate them. Nothing marks goods yet except `/pirates trade plunder`.

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
| Attacking a neutral ship, press-ganging | 15 |
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

### Bounties
- At a score of 50 the navy puts a **bounty** on you of twice your score. It grows with the score and is withdrawn
  when the score falls below 25.
- Players can add bounties on anyone (`/pirates law bounty place`, 10 doubloons or more).
- **Claiming:** killing a target with a bounty puts a **Bounty Proof** into the killer's inventory. Handing it in pays
  the bounty. Delivering the target alive pays 1.5 times as much. A claim wipes the target's score.
- There is no navy officer yet, so claims go through `/pirates law bounty claim`.

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

What you can do with a prisoner (through `/pirates brig` until navy officers exist): deliver it for its bounty,
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
shot is tighter, the musket zooms in a little, and the shot leaves when you release. Firing: a lead ball flies out with smoke and a small kick, the gun is unloaded again and needs half a second before it
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

### Grappling hook
Right-click to throw the hook. If it hits the hull of another ship it bites in and hangs there, following the ship.
If you stand on your own ship, the taut rope hauls both ships together until they lie side by side, ready for
boarding; then it goes slack. From land the rope drags the hooked ship slowly toward you. A hook that hits your own
ship, land, water or a creature does not hold (a creature takes a small hit) and comes back after two seconds. To let
go, sneak and right-click with an empty hand. More than 24 blocks from the hook the rope snaps and the hook comes back
(or is lost, if the server says so). Throwing a second hook releases the first. Server options: section `grapple`.

### Swordplay
With a rapier, cutlass or saber in hand the mouse works differently (server config `melee.skill_based_combat`,
on by default; vanilla weapons are untouched):
- **Slash:** a quick left click. A wide, short arc after a brief wind-up.
- **Thrust:** hold left click about half a second and release. Narrow, long reach, more damage, slow to recover if it
  misses.
- **Guard:** hold right click. Frontal damage is reduced; it drains stamina while held and per blocked hit.
- **Parry:** a quick right tap just before a hit lands. The hit is deflected, the attacker staggers, and for about a
  second you may **riposte** (attack for bonus damage). A parry with no hit coming costs stamina and locks parrying
  briefly.
- **Stamina:** a small gold bar above the hotbar while you hold a sword. Attacks, guarding and failed parries drain
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
| Mermaid, Lion, Eagle and Skull Figurehead | 4 planks and a prismarine shard, gold ingot, feather or bone | Decoration for the bow. Click the hull block it should hang on: the plate lands there and the figure looks at you. |
| Nameplate | any sign, 1 gold nugget | A board on two iron brackets that shows the ship's name once the ship is assembled and named (name tag on the helm); long names shrink to fit, renaming updates every plate within a second, disassembly clears them. Server option `ship_identity.nameplate_shows_name`. |
| Test Block | none | A development block. |

All blocks drop themselves. Wooden ones are mined with an axe, the bars and the door with a pickaxe.

---

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
| Rope | string | Use it on one cleat, then on a second one 2 to 16 blocks away and lower, to rig a stay. It glints while it remembers the first cleat. |
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
| `sailing` | Sail force, rudder strength, keel drag, anchor strength, roll and pitch damping. |
| `anchor_chain` | Chain speeds, travel time limits, anchor sounds and volumes. |
| `hull_creaking` | Creaking on/off, how often, volume and pitch ranges, the rolling rate that counts. |
| `audio` (client) | Music on/off and volume, the gap between tracks, shanties aboard. |
| `grapple` | Grappling hook on/off, throw speed, rope length, haul force and damping, hold distance and slack, shore pull, entity damage, lost-hook rule. |
| `cannons` | Cannons on/off, damage, muzzle speed, gravity, reload, elevation range and steps, blocks per hit, recoil and impact impulses, ball lifetime and water behaviour. |
| `mobs` | Mob types on/off and peaceful, hostility toggles, detection and fight ranges, skill tiers, musket timings and ammo, shove, drops. |
| `melee_animations` (client) | Sword animations on/off, first-person mode, layer priority. |
| `melee_input` / `melee_hud` (client) | Hold-to-thrust and parry-tap thresholds; stamina bar on/off, scale and offsets. |
| `firearms.aim` / `firearm_view` (client) | Minimum hold, steady time and aimed spread factor; musket zoom. |
| `firearms` | Firearms on/off, per gun: damage, muzzle velocity, spread, reload time, recoil; ball lifetime and gravity, cooldown, gunpowder use. |
| `sailing_runtime` | Sailing forces on/off, heel scaling, steering and anchor on/off, rudder steps, chain length. |
| `crew_stations` | Crew stations on/off, time per trim step. |
| `flags` | Hoisting delay, flags following the wind (land and ship check intervals), banners as flags. |
| `dry_hull` | Also: whether slabs, stairs and hatches are drawn dry in their empty half. |
| `provisions` | Consumption, rations, spoilage, scurvy, rum, water barrel capacity, rain refill. |
| `cargo_trade` | Container sizes, prices, price recovery, contracts, plunder, port fees, cargo weight. |
| `law` | Criminal score, severity of each crime, decay, fines, bounties, crime detection, theft. |
| `flags_brig` | False-colors detection, NPC surrender, capturing players, prisoner escapes. |
| `brig` | Capture threshold, leading distances, cell size, escape chance, ransom. |
| `melee` | Skill-based sword fighting: parry window, stamina, stagger. |
| `core` | Debug logging. |
| `ships`, `waves`, `hazards`, `crew`, `combat`, `survival`, `world`, `world_simulation`, and the client sections `audio` and `wave_effects` | Settings for features that are not built yet. They do nothing so far. |

Many defaults are first guesses that need playtesting. [`progress.md`](progress.md) lists the ones to review.

---

## 14. Datapacks

**Definitions** are JSON files under `data/<namespace>/pirates_n_ships/<type>/<name>.json`. A datapack can add entries
in its own namespace, or replace one of ours by shipping a file at the same path. A broken file is logged and skipped.

| Type | Folder | Contents |
|---|---|---|
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
