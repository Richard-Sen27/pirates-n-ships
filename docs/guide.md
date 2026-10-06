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
3. **Place a mast with a sail block** on it, and a **sail winch** somewhere on deck. A **capstan** gives you an anchor.
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
One sail block stands for a whole sail. Place it on a mast.

| Sail | Area | Good at |
|---|---|---|
| Small Square Sail | 9 | Wind from astern. Useless close to the wind. |
| Large Square Sail | 25 | Same, with almost three times the push. |
| Fore-and-Aft Sail | 16 | Wind from the side. Still drives at 45° to the wind. |

- **Trim:** furled (no force), half, full. The block shows its trim.
- The force grows with the wind you feel on board, the sail's area and its trim. A ship running before the wind can't
  go faster than the wind.
- **No-go zone:** within 30° of the wind no sail drives the ship forward.
- Sails only work on a ship that is afloat.
- Square sails are meant to face bow or stern, fore-and-aft sails port or starboard. The facing is only visual for now.

### Sail winch
Using it cycles the trim of **all** sails on its ship: furled → half → full → furled. Using a single sail block cycles
only that sail.

### Keel
A ship in water resists moving sideways much more than moving forward, so a sail on a beam reach drives it ahead
instead of pushing it downwind. No block is needed for this.

### Rolling and creaking
A ship that heels or is shoved swings back and settles within a few seconds (roll and pitch damping, config `sailing`).
While it rolls, its planks creak now and then, quietly, from somewhere in the hull; a ship at rest is silent. The
creak uses vanilla wooden sounds as placeholders until real recordings exist.

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

## 10. All blocks

| Block | Recipe | What it does |
|---|---|---|
| Helm | 4 sticks, 1 planks | Assembles, steers and disassembles a ship. See [Ships](#2-ships) and [Sailing](#3-sailing). |
| Small Square Sail | 3 sticks, 3 wool | Sail, area 9. |
| Large Square Sail | 3 sticks, 6 wool | Sail, area 25. |
| Fore-and-Aft Sail | 3 sticks, 3 wool | Sail, area 16, sails closer to the wind. |
| Sail Winch | 2 string, 1 iron ingot, 3 planks | Sets the trim of all sails on its ship. A crew station. |
| Capstan | 2 logs, 1 stick, 2 chains, 1 iron block, 3 planks | Drops and raises the anchor. |
| Flagpole | 3 sticks (gives 2) | Flies a flag. See [Flags](#5-flags). |
| Pantry | 8 planks, 1 wheat | Food store with spoilage. See [Provisions](#6-provisions). |
| Water Barrel | 6 planks, 2 iron nuggets, 1 water bucket | Holds 16 rations of water. Crafted full. |
| Cargo Crate | 4 planks, 4 sticks | Bulk container for 32 stacks of one item. |
| Cargo Barrel | 6 planks, 2 iron nuggets | Bulk container for 1536 items of one kind. |
| Brig Bars | 4 iron bars, 2 planks (gives 6) | Bars for cells. |
| Brig Door | 4 iron ingots, 2 iron bars | Lockable door for cells. |
| Mermaid, Lion, Eagle and Skull Figurehead | 4 planks and a prismarine shard, gold ingot, feather or bone | Decoration for the bow. Faces the way you look when placing it. |
| Nameplate | any sign, 1 gold nugget | Decoration for the hull side. It doesn't show the ship's name yet. |
| Test Block | none | A development block. |

All blocks drop themselves. Wooden ones are mined with an axe, the bars and the door with a pickaxe.

---

## 11. All items

| Item | Recipe | What it does |
|---|---|---|
| Rapier | 2 iron ingots, 1 stick | Sword. See [Weapons](#9-weapons-and-combat). |
| Cutlass | 3 iron ingots, 1 stick | Sword. |
| Saber | 2 iron ingots, 1 gold ingot, 1 stick | Sword. |
| Pistol | 2 iron ingots, 1 flint, 1 planks | No function yet. |
| Musket | 2 iron ingots, 1 flint, 1 planks | No function yet. |
| Lead Shot | 2 iron nuggets (gives 4) | No function yet. |
| Cannonball | 4 iron ingots (gives 2) | No function yet. |
| Grappling Hook | 3 iron ingots, 1 string | No function yet. |
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
| `flooding` | Flooding on/off, inflow rate, pump rate. |
| `wind` | Wind strength range, how fast it changes, weather multipliers, gusts, regional variation. |
| `sailing` | Sail force, rudder strength, keel drag, anchor strength, roll and pitch damping. |
| `anchor_chain` | Chain speeds, travel time limits, anchor sounds and volumes. |
| `hull_creaking` | Creaking on/off, how often, volume and pitch ranges, the rolling rate that counts. |
| `sailing_runtime` | Sailing forces on/off, heel scaling, steering and anchor on/off, rudder steps, chain length. |
| `crew_stations` | Crew stations on/off, time per trim step. |
| `flags` | Hoisting delay, flags following the wind, banners as flags. |
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
- **World:** pirate islands, seafarer villages, navy outposts, wrecks, treasure maps, ports with harbor masters.
- **Mobs:** pirates, sailors, navy soldiers and officers, sharks, the kraken. The only NPC is the test crew member.
- **Crew life:** hiring, wages, morale, skills, crew eating from the pantry, other stations than the sail winch.
- **Combat:** firing pistols and muskets, cannons and ship damage, the grappling hook, boarding, and the input and
  animations for sword fighting.
- **Ship extras:** pumps and patching, a water surface inside flooding rooms, waves, oars, cargo weight slowing a ship,
  the ship's name on the nameplate, shipwright orders.
- **Screens and HUD:** the market screen, a wind indicator, ship status, a wanted display. The data for them is
  already sent to the client.
- **The Fabric version.**

The roadmap is in [`design.md` §20](design.md#20-roadmap).
