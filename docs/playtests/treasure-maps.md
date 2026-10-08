# Playtest: treasure maps (work package TM1)

The binding choice, the bearing text, the component codecs with the picture, the coast flags, the special offer and the
wreck loot entry are covered by JUnit (`TreasureBindingTest`, `TreasureBearingTest`, `TreasureMapDataTest`,
`WreckPiecesTest`); the server side by 6 GameTests (`TreasureMapGameTests`: binding with a picture, finding at the
chest with the next map leading to the other site, a chest away from the sites, the give command, the fence's line,
config off). The GameTests fire the container event directly, so the NeoForge forwarding of a real chest opening, the
overlay and the look need a client.

The item model is a placeholder (the rolled chart's model); a Blockbench model of its own comes later (design.md §4.8).

## Setup
Creative or survival in a new world; `/pirates world ports` to see that a pirate island exists (`pirate_island_<x>_<z>`)
and `/pirates world port nearest` near it to see its treasure sites (`buried`). Doubloons: `/give @s
pirates_n_ships:doubloon 64`.

## Steps
1. **Buy at a fence:** at a pirate island, use the fence's harbor desk: the market lists "Treasure Map" as a produced
   line for 60 doubloons (Buy), Sell shows "-" and stays disabled. Buy one: "Bought 1 Treasure Map for 60", 60
   doubloons gone, a blank map in the inventory. Quantity 3 costs 180. A seafarer village desk has no such line.
2. **Tooltip:** the blank map says "Blank: use it to find the nearest buried treasure".
3. **Bind:** sail or walk within 2000 blocks of the island, right-click the blank map: the action bar shows a bearing
   such as "NW, 340 blocks"; the tooltip now says "Leads to a buried treasure". With a stack of several blanks only one
   binds, the bound one goes into the inventory and the rest stay blank.
4. **Overlay:** with the bound map in the main hand or off hand, a parchment panel on the right edge shows the island
   in the chart style (ink coast, hatched shallows, parchment sea) with a red X in the middle and the bearing and
   distance below; it updates while sailing ("NE, 120 blocks" → …), and a small dark dot marks you once you are inside
   the pictured 256 × 256 blocks. Areas that were not loaded when the map was bound stay blank parchment. Pressing F1
   hides it; opening the inventory hides it.
5. **Dig:** follow the X to the treasure spot; within 3 blocks the text reads "X marks the spot: dig here!". Dig down
   about two blocks into the sand: a chest. Open it: the action bar says "The treasure has been found", the loot is
   the buried-treasure table; the overlay greys the picture, drops the X and says "The treasure has been found".
   `/pirates world port nearest` lists that site as `looted`.
6. **Other maps of the same treasure:** a second player (or a second map bound before the chest was opened, kept in a
   chest elsewhere and picked up later) turns found within a second of being in a player's inventory.
7. **Next map:** a new blank map used near the same island binds to its next unfound site, or to the next island; with
   every island within 2000 blocks found, it says "No treasure within reach of this map" and stays blank.
8. **Wreck chest:** loot several wreck chests (`/locate structure pirates_n_ships:wreck`, or `/give` is no test of
   this): a blank treasure map turns up now and then (weight 2 of 45 per roll, 2-4 rolls).
9. **Command:** `/pirates world treasure give` gives a map of the nearest island's treasure at any distance;
   `/pirates world treasure give <port id>` (tab-completes pirate islands) of that island's first unfound site; a port
   without unfound treasure gives an error.
10. **Config off:** `world.treasure_maps.enabled = false` (server config, then `/reload`): using a blank map says
    "Treasure maps are disabled on this server" and it stays blank; the fence no longer lists the map; bound maps keep
    showing their picture, and opening a treasure chest still marks the site found.
11. **Price:** `cargo_trade.treasure_map_price = 5`: the fence's line shows 5.
12. **Relog:** log out and back in with a bound and a found map: both keep their picture and state.
