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
{spotlight:minecraft:writable_book}
These pages are for the keeper of a server, not the hand at the wheel: the settings a keeper reaches for first, and the orders an operator may give.

## Where the settings live
Open them under Mods, then Pirates 'n' Ships, then Config (on Fabric with Mod Menu). Gameplay sits in the server config, which every client receives; looks and sounds sit in each player's client config.

Every feature has a switch and every strength a value. Below: section.name, default after the colon.

## Ships and sea
- assembly.max\_blocks: 2048. The largest ship a helm launches.
- ship\_screen.enabled: true. Off, sneak-using the helm takes the ship apart at once.
- helm.wheel.drag\_steering: true. Off, the wheel is clicked in thirds.
- stability.auto\_ballast: true.
- wind.min\_strength 3, wind.max\_strength 12 (blocks a second).
- hazards.kraken.enabled: true.
- dry\_hull.flood\_breath: true. Off, flood water inside a hull never drowns.
- cargo\_trade.cargo\_weight\_affects\_ships: true.

## Crew and combat
- crew.wages.per\_day: 2 doubloons.
- crew.mutiny.enabled: false.
- melee.skill\_based\_combat: true. Off, our swords fight like vanilla swords.
- firearms.fire\_on\_attack: true. Off, guns fire when right click is released.
- combat.cannon\_block\_damage: true. Cannon fire also obeys mobGriefing and spawn protection.
- almanac.give\_on\_first\_join: true. One Almanac for every new hand.

## Player commands
- /pirates career: your rank, standing and letter of marque.
- /pirates rep: your standing with the navy, the pirates and the villagers.
- /pirates quest list, /pirates quest abandon <id>.
- /pirates waves: the sea state where you stand.

## Operator commands
- /pirates wind get, set <fromDegrees> <strength>, clear.
- /pirates ship info, templates, place <template> [force] [assemble].
- /pirates crew spawn, hire <sailor|pirate> [port], order <order>.
- /pirates law score, fine, bounty list, clear.
- /pirates waves set <calm|moderate|rough|storm>, waves clear.
- /pirates mob spawn <type> [count].
- /pirates world ports, treasure give, raid <port>.
- /locate structure pirates\_n\_ships:navy\_outpost (also seafarer\_village, pirate\_island, wreck).

## Datapacks
Ship templates, trade goods and weapon timings are data files, and tags decide what never sails, what keeps water out and which foods keep or cure scurvy. A datapack can replace any of them, or add to the Almanac itself.
