---
navigation:
  title: "Cargo and trade"
  parent: index.md
  position: 70
  icon: pirates_n_ships:doubloon
item_ids:
  - pirates_n_ships:cargo_barrel
  - pirates_n_ships:cargo_crate
  - pirates_n_ships:doubloon
  - pirates_n_ships:harbor_desk
---

# Cargo and trade

## Cargo crate and cargo barrel
Bulk containers that hold **one kind of item** in large amounts.

|  | Holds |
|---|---|
| <ItemLink id="pirates_n_ships:cargo_crate" /> | 32 stacks of the item (2048 sugar, 512 rum) |
| <ItemLink id="pirates_n_ships:cargo_barrel" /> | 1536 items, whatever the stack size (better for rum and other small stacks) |

- **Use it with a stack:** puts the stack in.
- **Use it with an empty hand:** takes one stack out.
- **Sneak-use with an empty hand:** puts in everything of that kind from your inventory.
- The action bar shows what it holds. Items that don't stack (tools, filled crates) are refused.
- Plundered and clean goods of the same item never mix in one container.
- A broken container keeps its content in the item, like a shulker box.
- Hoppers work, and other mods' pipes can connect to it and to the pantry.

## Doubloons
The gold doubloon is the currency. It has no recipe: it comes from trade (and later from loot and bounties).
`/pirates trade coins` gives you some for testing.

## Trade goods
Twelve goods have a base price and a weight. They are data-driven (see [Datapacks](datapacks.md)).

| Good | Item | Base price | Comes from |
|---|---|---|---|
| Sugar | <ItemLink id="minecraft:sugar" /> | 2 | tropical ports |
| Fish | <ItemLink id="minecraft:cod" /> | 1.5 | temperate and cold ports |
| Timber | <ItemLink id="minecraft:oak_log" /> | 1 | temperate and cold ports |
| Iron | <ItemLink id="minecraft:iron_ingot" /> | 6 | cold and arid ports |
| Grain | <ItemLink id="minecraft:wheat" /> | 1 | temperate ports |
| Hides | <ItemLink id="minecraft:leather" /> | 3 | arid and temperate ports |
| Cocoa | <ItemLink id="minecraft:cocoa_beans" /> | 4 | tropical ports |
| Gunpowder | <ItemLink id="minecraft:gunpowder" /> | 8 | navy outposts |
| Tobacco | <ItemLink id="pirates_n_ships:tobacco" /> | 5 | tropical and arid ports |
| Spices | <ItemLink id="pirates_n_ships:spices" /> | 12 | tropical ports |
| Cloth | <ItemLink id="pirates_n_ships:cloth" /> | 6 | temperate ports |
| Rum | <ItemLink id="pirates_n_ships:rum" /> | 4 | tropical ports |

## Markets
There are no ports in the world yet. A market is created with `/pirates trade port <name> <kind> <climate>`
(kinds: seafarer village, navy outpost, pirate island).
- A port **produces** some goods (cheap there) and **demands** others (expensive there), depending on its kind and
  climate.
- **Buying raises the price, selling lowers it.** Prices drift back by about 30% of the gap per day.
- Buying and selling in the same port always loses money. Carrying goods from a port that produces them to one that
  demands them pays, but less with every stack.
- You can trade from your inventory or from a cargo container next to you.

## Harbor master's desk
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

## The harbor master
Every port's desk has a harbor master behind it: in the village's dock-head hut, in the navy fort's office, and behind
the fence's counter on pirate islands. He wears a green frock coat and a peaked cap. Right-click him (not sneaking) to
open the port's market, just as using the desk does; he greets you when it opens. If you hit him, or he is running
from pirates, he waves you off ("not now") for a while. He keeps to his post and walks back if pushed away. If he
dies, a new harbor master takes the post after 3 days (`mobs.harbor_master.respawn_days`). With
`cargo_trade.harbor_desks.direct_use` off, the desk only says "Talk to the harbor master" and the market opens
through him. Server config `mobs.harbor_master`.

## Harbor dues
Navy outposts charge a small docking fee (5 doubloons) when your ship ties up at a berth or drops anchor inside the
outpost, once per day per ship. It comes from your purse, or from the doubloons in your ship's chests when your purse
is short. If nobody can pay, the dues are owed: the outpost's harbor desk will not trade with you until you use it
with doubloons in hand. Captains the navy trusts, and navy officers from Lieutenant up, dock for free. Sailing through
the harbor without stopping costs nothing. Server config `cargo_trade.port_fees`.

## Contracts
A port offers delivery contracts: bring an amount of a good to another port by a deadline for a reward. Accepting
takes a deposit of 20% of the reward, and delivering pays the reward and returns the deposit. A player can hold three
at a time.

## Plunder
Goods can carry a **plundered** mark (shown in the tooltip). A pirate island buys them at 35% less, no questions asked.
A navy outpost may notice and confiscate them. Nothing marks goods yet except `/pirates trade plunder`.
