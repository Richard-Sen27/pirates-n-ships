---
id: ledger
title: The Quartermaster's ledger
order: 130
icon: minecraft:writable_book
color: #5a4f6e
illustrations:
  - ship_commission: 0.05 0.09 0.9 0.35
---
illustration: ship_commission
These pages are for the keeper of a server, not for the hand at the wheel. Here are the settings that change how the sea plays, and the orders an operator may give. Every feature has a switch and every strength a value; only the ones a keeper reaches for first are written here.

## Where the settings live
Open them in game under Mods, then Pirates 'n' Ships, then Config (on a Fabric server with Mod Menu installed). Gameplay settings sit in the server config, which every client receives; how things look and sound sits in each player's client config.

Settings are written as section.name, with the default after the colon.

## Ships and sailing
- assembly.max\_blocks: 2048. The largest ship a helm launches.
- assembly.max\_tilt\_degrees: 6, assembly.max\_linear\_speed: 0.3. How still a ship must lie to be taken apart.
- assembly.rejoin: the shipwright's toolkit; nails per repair 4, largest piece 200.
- ship\_screen.enabled: true. Off, sneak-using the helm takes the ship apart at once.
- helm.wheel.drag\_steering: true. Off, the wheel is clicked in thirds.
- sailing.max\_rudder\_angle: 35. sailing.rudder\_force\_factor: 3.
- sailing.sails.yard\_min\_gap 2, yard\_max\_gap 8, yard\_max\_length 15. sailing.sails.dyeing, banners: true.
- sailing\_runtime.anchor\_chain\_length: 32. sailing\_runtime.sail\_heel\_factor: 0.25.
- stability.auto\_ballast: true. stability.max\_heel\_degrees: 25.
- wind.min\_strength 3, wind.max\_strength 12 (blocks a second).
- waves: the sea state; hazards: waterspouts, whirlpools and the kraken (hazards.kraken.enabled).

## Hull and stores
- flooding.pump\_enabled, patch\_enabled: true. flooding.pump\_rate: 1 block a second. flooding.pump\_reach: 4.
- dry\_hull.flood\_breath: true. Off, flood water inside a hull never drowns.
- provisions: rations, spoilage (5 days), scurvy (8 days), rum, the water barrel (16 rations).
- survival: cold water and swimming hunger.
- cargo\_trade.cargo\_weight\_affects\_ships: true.

## Crew
- crew.wages.per\_day: 2 doubloons.
- crew.desertion: below 20 morale for 2 dawns.
- crew.mutiny.enabled: false.
- crew.hiring: fees 10 (sailor), 20 (pirate), 15 (navy); ship\_radius 32.
- crew.walk.enabled: true. Off, hands take their stations at once.
- crew\_stations.ticks\_per\_trim\_step: 40.
- lookout.range: 160. lookout.announce: true.
- cannons.npc: what crews do under Fire at will.

## Combat
- melee.skill\_based\_combat: true. Off, the mod's swords fight like vanilla swords.
- firearms.fire\_on\_attack: true. Off, guns fire when right click is released.
- combat.rain\_misfire\_chance: 0.25.
- cannons: damage 20, reload 100 ticks; cannons.chain\_shot, cannons.grapeshot, cannons.swivel.
- combat.cannon\_block\_damage: true. Cannon fire also obeys the mobGriefing game rule and spawn protection.
- grapple: rope lengths (32 thrown, grapple.launch 64 from a musket), grapple.hauling, grapple.slide.
- boarding.plank: the boarding plank.

## Law, trade and the world
- law: crime points, decay, fines (3 a point), bounties (twice the record from 50).
- flags\_brig: false colours, surrender, capturing players, escapes. brig: capture at a quarter health, cell size 64.
- flags.max\_pole\_height: 6.
- cargo\_trade: markets, contracts, plunder (fence discount 35 %), port fees (5).
- careers, reputation, quests: the rank ladders, standing and the harbour quests.
- world.structures: villages, navy outposts, pirate islands, wrecks.
- world\_simulation: voyages, convoys, navy patrols, raids, pirate captains at sea.
- mobs: pirates, navy, sharks (mobs.shark), the kraken (mobs.kraken), harbour masters, pirate captains.
- almanac.enabled: true. almanac.remember\_page: true. almanac.give\_on\_first\_join: true (one Almanac for every new hand).

## Player commands
Any player may use these:

- /pirates career: your rank, standing and letter of marque.
- /pirates rep: your standing with the navy, the pirates and the villagers.
- /pirates quest list, /pirates quest abandon <id>.
- /pirates waves: the sea state where you stand.

## Operator commands
These need operator rights.

- /pirates wind get, set <fromDegrees> <strength>, clear.
- /pirates ship forces, info, rigging, templates, place <template> [force] [assemble], orders.
- /pirates crew spawn, info, assign, release, order <order>, order course <x> <z> ..., hire <sailor|pirate> [port], dismiss.
- /pirates flag get, strike, raise, set <pos> <kind>.
- /pirates provisions show, advance: a dry run of a crew's stores.
- /pirates trade port, open, goods, buy, sell, contracts, contract, weight, plunder, coins, desk bind <port>, desk unbind, desk info.
- /pirates law score, crime, last, hostile, fine, bounty list, place, claim, clear.
- /pirates brig list, capture, deliver, ransom, pressgang, release.
- /pirates career set, letter, ship\_grant; /pirates rep set; /pirates quest offer, complete.
- /pirates waves set <calm|moderate|rough|storm>, waves clear.
- /pirates hazard spawn <waterspout|whirlpool> [pos], hazard clear [radius].
- /pirates mob spawn <type> [count], mob debug on|off, mob captain spawn, list, voyage, mob squad info, patrol, return.
- /pirates world ports, port nearest, treasure give, factions, patrols, raid chance, raid <port>, voyages.
- /locate structure pirates\_n\_ships:navy\_outpost (also seafarer\_village, pirate\_island, wreck).

## Datapacks
Ship templates, trade goods and weapon timings are data files under data/<namespace>/pirates\_n\_ships/, and block and item tags decide what never sails (terrain, never\_assemble), what keeps water out (watertight, not\_watertight), which foods keep or cure scurvy, and what may be hoisted (flags). A datapack can replace any of them, or add to the Almanac itself: see the mod's chapter format.
