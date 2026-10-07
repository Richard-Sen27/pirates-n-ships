---
navigation:
  title: "Crew"
  parent: index.md
  position: 40
  icon: pirates_n_ships:captains_whistle
---

# Crew

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

The crew member is animated (idle, walking, working at a station, sitting in a boat) through GeckoLib, a required
mod on both sides; it is a Blockbench-made sailor (striped shirt, red bandana, neckerchief, belt and knife, bare feet) with idle, walking, hauling and sitting animations.

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
