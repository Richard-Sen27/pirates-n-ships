# Playtest: harbor master's desk and market screen (work package G10)

The server path (binding, sessions, reach, buying, selling, contracts, the toggle) is covered by 7 GameTests and the
screen's pure helpers by JUnit. The screen itself has never been seen in a client.

Setup: a creative world with cheats; `/give @s pirates_n_ships:doubloon 2000`; test ports with
`/pirates trade port cane seafarer_village tropical`, `/pirates trade port fort navy_outpost temperate` and
`/pirates trade port tortuga pirate_island tropical`. Please send screenshots of the screen and `latest.log` if
anything differs.

## Steps
1. **Item, model, recipe.** Creative tab and crafting book; place a desk facing each of the four directions. Expected: a
   writing desk with drawers, gold knobs, a red ledger and an inkwell, facing you each time (a placeholder until its
   Blockbench batch); fine in the hotbar and in hand; breaks with an axe and drops itself.
2. **Unbound.** Right-click a new desk. Expected: "This desk does not belong to a port", no screen; `/pirates trade desk
   info` says it is not bound.
3. **Binding.** Look at the desk, `/pirates trade desk bind cane`, then `desk info`. Expected: "The desk now belongs to
   port pirates_n_ships:debug/cane", then the info with the kind; tab completion offers the port ids; the binding
   survives save and quit.
4. **Opening.** Right-click the desk. Expected: "Cane  Seafarer village", the doubloon icon with 2000, one row per
   traded good: produced (green) first, then traded (grey), then wanted (orange); prices for 1 unit; "you have 0".
5. **Buying each good.** Pick 64, Buy on each row. Expected: "Bought 64 X for N doubloons", wallet and "you have" update,
   the next buy price rises; prices match `/pirates trade goods cane 64` from before.
6. **Selling each good.** Sell what you carry. Expected: "Sold 64 X for N doubloons", coins and counts change.
7. **Not enough coins.** Type 4096 and hover the greyed Buy: "You can afford about N". Sell is greyed while you carry
   fewer than the quantity.
8. **Plunder at a navy port.** Bind a desk to `fort`; mark 64 sugar with `/pirates trade plunder`; set Cargo Trade →
   Plunder → navy notice chance to 1.0; at the fort desk switch to "Selling plundered goods" and sell 64. Expected:
   "The port noticed the plunder and confiscated 64 Sugar (0 doubloons)" in red. At a `tortuga` desk with the chance
   reset: "The fence took 64 Sugar at a discount …".
9. **Contracts.** Contracts tab at the cane desk: offers like "N × Good to Fort" with day, reward and deposit (none
   on some days: `/time add 24000`). Accept: "Contract accepted, P doubloons deposit paid", the contract moves to "Your
   contracts" with Deliver greyed. Get the goods, go to the fort desk, Deliver: "Contract delivered: …" and the payout.
10. **Walking away.** Walk backwards with the screen open: it closes at about 8 blocks. Escape closes it too.
11. **Toggle.** Cargo Trade → Harbor Desks → desks enabled off: right-click does nothing; an open screen closes with
    "The harbor master has closed the books" on the next click.
12. **Two players at one desk.** Each sees their own wallet; when one buys, the other's prices update after their next
    action; neither trades from beyond 8 blocks.
13. **Look and feel.** GUI scales 2 to 4 and a small window: long names cut with "..", buttons inside the panel, the
    list scrolls with the wheel, nothing overlaps.

## Also worth a look
- Should the screen pause the game in single player? Should a sale of noticed plunder already count as a crime (it does
  not yet)?


Addendum (G13): with `cargo_trade.plunder.navy_notice_chance = 1.0`, a noticed plunder sale at a navy desk raises `/pirates law score get @s` by 15 and `/pirates law last @s` shows "Selling plunder" with the port id; selling again within a minute does not raise it again; a pirate fence never does. The direct `/pirates trade sell` command does not record the crime yet (known gap).


Addendum (T1, two clients at one desk): A sells a stack: within a second B's screen shows the new totals without clicking. A gives B doubloons: B's coin count updates within a second. A buys: A's status line shows "Bought …" and the buttons re-enable even if a refresh arrives at the same time. A presses Escape: A's session ends (a trade request from A at that port is refused until the desk is used again), B keeps updating. The inventory key closes the screen unless the quantity field has focus. B walking out of reach closes the screen and stops the refreshes.


## U1: the market's look (GUI scale 2 and 3)
Header plaque with the port name, kind and doubloons; the open tab looks pressed; styled quantity buttons and field; goods rows on parchment with item icons and the Buy and Sell columns between brass rules under their headings; a hover wash on rows; Buy and Sell with hover, pressed and grey disabled states and the "afford" tooltip on a disabled Buy; the status line on the parchment footer; the Contracts tab with its divider, Accept and Deliver; the scrollbar in both tabs; nothing overlaps at the smallest window size.


## PRT1b: harbor dues at a navy outpost
Setup: a world with a navy outpost (`/pirates world port nearest` lists its id and box; `/locate structure` finds one), your own assembled ship with a capstan, a few doubloons. Reputation 0 (`/pirates rep set @s navy 0`). Defaults: fee 5 (`cargo_trade.port_fees.navy_docking_fee`), checked every 100 ticks, once per in-game day per ship and port.

1. **Anchoring pays.** Sail into the outpost's harbor and drop the anchor. Within about 5 s of the ship coming to rest: chat "Harbor dues at <Outpost Name>: 5 doubloons", 5 doubloons gone from your inventory. Stay anchored for a minute: no second charge. `/time add 24000`: charged once more.
2. **Tied up at a berth.** Raise the anchor, let the ship drift to rest beside the pier (within 6 blocks of a berth) without anchoring, next day (`/time add 24000`): charged again. Sailing slowly past the pier without stopping charges nothing.
3. **Passing through.** Sail through the outpost's box without stopping or anchoring: no message, no coins taken.
4. **Ship's chest.** Empty your inventory of doubloons, put 10 in a chest aboard, anchor the next day: "Harbor dues at …: 5 doubloons, taken from the coins aboard <ship>" and the chest holds 5.
5. **Owed.** No doubloons anywhere, anchor the next day: "… 5 doubloons owed for <ship>. Pay at the harbor desk with doubloons in hand". Right-click the outpost's harbor desk with an empty hand: action bar "Harbor dues owed: 5. Use the desk with doubloons in hand to pay", no market. Right-click it holding doubloons: "Harbor dues paid: 5 doubloons" and the market opens.
6. **Waivers.** `/pirates rep set @s navy 60`, anchor the next day: "Harbor dues at …: waived", nothing taken. With CAR2: a Lieutenant (`/pirates career` rank) is waived too.
7. **Villages and islands** never charge or message.
8. **Toggles.** `cargo_trade.port_fees.enabled = false`: no charges. `refuse_desk_when_owed = false`: with dues owed the desk still opens (after the reminder on the action bar). `charge_ship_chest = false`: an empty wallet makes the dues owed even with coins aboard.
9. **Offline owner.** A ship anchored in the harbor while its owner is logged out is not charged; the owner is charged at the next check after logging back in.
