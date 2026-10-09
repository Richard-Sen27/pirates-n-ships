---
navigation:
  title: "Crew"
  parent: index.md
  position: 40
  icon: pirates_n_ships:captains_whistle
item_ids:
  - pirates_n_ships:crows_nest
---

# Crew

A **crew member** is a simple NPC. Hire one at a harbor desk (see "Hiring crew" below) or spawn one with
`/pirates crew spawn`.

**Stations** are blocks a crew member can man. The sail winch, helm, cannons, pump, capstan and crow's nest are stations. A crew member at a station stands on
an invisible seat that travels with the ship, so it stays at its post while the ship moves.

Crew **walk** to their place: a sailor on deck who is sent to a station, to a meal or to a hammock walks across the
deck (even while the ship sails) and takes the place when he gets there; the station starts its work only once he has
arrived. A place he cannot reach (behind a wall, up the mast to the crow's nest) or has not reached after 10 seconds
puts him there anyway, as does an order to a sailor who is not on that ship. Server config `crew.walk` (`enabled` off:
crew take their place at once).

The **captain's whistle** (creative tab) gives orders:
- Use it on a crew member, then on a station: the crew member takes that station.
- Use it on an assigned crew member: it is released.
- Use it in the air: opens the order wheel. Point at an order (hoist, reef, furl, release crew) and click, or hold the
  use key, aim and release. The order goes to all crew at stations on the ship you stand on. Esc closes the wheel.

The [ship screen](ships.md#ship-screen) at the helm (sneak-use with an empty hand) gives the same orders, assigns and releases
hands and shows every hand's morale, station and order at a glance.

A crew member answers in chat ("Aye, hoisting the sails!") and then works: each trim step takes 2 seconds, and the
sails change when the work is done. A player can still use the winch directly.

A crew member is released when its station is broken, its ship is disassembled or removed, or it dies.

The crew member is animated (idle, walking, working at a station, sitting in a boat) through GeckoLib, a required
mod on both sides; it is a Blockbench-made sailor (striped shirt, red bandana, neckerchief, belt and knife, bare feet) with idle, walking, hauling and sitting animations.

## Hiring crew
Every harbor desk has a **Crew** tab. Each day a port has a few people looking for a berth: sailors at seafarer
villages, pirates at pirate islands, navy ratings at navy outposts. Each asks a one-time fee (10, 20 or 15 doubloons)
and then the daily wage. Villagers won't sign on with someone they refuse to trade with. Pirates sign on only with a
friend of the pirates or a captain of some infamy (Buccaneer and up). Navy ratings sign on only with an enlisted
officer. Before you hire, moor your own ship at the port: the recruit walks straight aboard and waits on the deck
nearest the desk. Every crew member needs a free hammock, so hang more hammocks to take on more hands. To let someone
go, sneak and use your captain's whistle on them: they leave your service as an ordinary sailor. Only you or whoever
hired them can do that, or anyone at all if the ship has no owner. Operators: `/pirates crew hire <sailor|pirate|navy>`
and `/pirates crew dismiss`. Server config `crew.hiring`.

## Crow's nest
A barrel of spruce staves with iron hoops for the top of a mast. Place it on a log, a fence, a wall or any block with
a solid top; it breaks and drops when the block under it goes. It is one block, but the barrel is a block and a half
across and stands chest high, so whoever is in it is plainly up in the nest. Only its floor is solid: climb a ladder up
the mast and step in over the rim (sneak to stay put).

Whoever is in the nest keeps the lookout. Put a crew member up there with the Captain's Whistle (use it on the crew
member, then on the nest): he takes his post inside the barrel and keeps watch until you release him. Or stand in it
yourself. Every 3 seconds the lookout scans 160 blocks round the ship and calls out what is new, in chat, to the ship's
owner and everyone aboard:
- **ships**, by the flag they fly: "Sail ho! A merchant two points off the starboard bow, 140 blocks" (a navy ship, a
  pirate ship, a wreck, or just a ship when no flag flies);
- **land**: "Land ho! Land four points off the port bow, 120 blocks" (the nearest coast; one call per stretch of coast);
- **sharks** and **the kraken**.

Bearings are in points of the compass (32 round the horizon) from the bow: dead ahead, "n points off the starboard (or
port) bow", on the beam, "n points abaft the beam", dead astern. Each ship, shark or coast is called once; the lookout
remembers it for 5 minutes after he last saw it. Only loaded chunks are watched. Recipe: planks, rope, planks over two
iron nuggets, over three planks. Server config `lookout`: `enabled`, `announce` (off: the watch is silent), `range`,
`scan_interval_ticks`, `memory_ticks`, and the land rays (`land_directions`, `land_step`, `land_min_distance`,
`land_region`).

## Pirates, sailors and the navy
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

## The kraken
The kraken lurks in the deep ocean and rises beside ships within 32 blocks, more often at night and in storms. Its
tentacles grip the hull near the waterline and drag the ship down, beat masts to splinters and sweep the deck;
swimmers are pulled under. Its eyes are its weak spots (triple damage); 40 damage on a tentacle cuts it and it lets go
until it grows back. Below 30 % health it sinks away. Drops a Kraken Beak and Kraken Ink. Switch it off with
`hazards.kraken.enabled`; tune it under `mobs.kraken`. Operators: `/pirates mob spawn kraken`.

## Sharks
Sharks roam every ocean, cruising a few blocks under the surface. A shark that spots you swimming circles you
before it charges and bites. Wounded swimmers drive it into a blood frenzy, and then it charges straight in. Stay
on deck or in a boat and it leaves you alone; climb out of the water and it loses you at once. If it can't land a
bite for a while, it gives up. Sharks fight back when hurt. Drops: sometimes a cod, rarely a prismarine shard. Server
config `mobs.shark.*`, including `enabled`, `peaceful` and the spawn weight, which takes effect at the next server
start. Operators: `/pirates mob spawn shark` or the spawn egg.
