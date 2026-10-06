# Playtest: cargo containers, wallet and market backend (work package E3)

GameTests cover the container rules, hoppers, drops, the wallet and every transaction. This checklist covers what
needs a real client: the hand interactions and action bar text, comparators, how it feels, and saving across a relog.
There is no market screen yet. Trading goes through operator commands under `/pirates trade`. Test ports are called
`pirates_n_ships:debug/<name>`. Please send `latest.log` if anything differs.

Setup: start the dev client (`./gradlew :neoforge:runClient`) and create a creative single-player world with cheats
on. Switch to survival (`/gamemode survival`) for the steps that break blocks, because creative breaking drops nothing.

## Containers

1. Place a Cargo Crate and a Cargo Barrel (creative tab Pirates 'n' Ships). Right-click each with an empty hand.
   - **Expected:** the action bar shows `Cargo Crate: empty` and `Cargo Barrel: empty`.
2. `/give @s minecraft:sugar 640`. Right-click the crate with a sugar stack in your hand.
   - **Expected:** the stack goes in and the action bar shows `Cargo Crate: 64 / 2048 Sugar`.
3. Empty your hand, sneak and right-click the crate.
   - **Expected:** all the sugar in your inventory goes in: `Cargo Crate: 640 / 2048 Sugar`.
4. Right-click the crate with dirt, then with a sword.
   - **Expected:** both are refused with `Holds one kind only, refused …`. Nothing leaves your hand.
5. Right-click the crate with an empty hand (no sneaking).
   - **Expected:** you get one stack of 64 sugar and the count drops to 576.
6. `/give @s pirates_n_ships:rum 32`, put it into the barrel.
   - **Expected:** `Cargo Barrel: 32 / 1536 Rum`. A crate would only hold 512 rum (32 stacks of 16).
7. Put a hopper with sugar on top of a fresh crate and a second hopper below it, with a chest under the second hopper.
   - **Expected:** sugar runs through the crate into the chest. While the crate holds sugar, a comparator next to it
     gives a signal of 1 or more. The signal grows as the crate fills (15 = full).
8. Look at the crate and run `/pirates trade weight`.
   - **Expected:** `Cargo Crate: 576 / 2048 Sugar: cargo weight 144.00` (sugar weighs 0.25 per item).
9. In survival, break the crate that still holds sugar with an axe.
   - **Expected:** exactly one crate item drops, and its stack size is 1. Place it again and right-click it with an
     empty hand: the same sugar count is back. (Its tooltip: see step 24.)

## Coins and markets

10. `/give @s pirates_n_ships:doubloon 2000`, then `/pirates trade coins`.
    - **Expected:** `You carry 2000 doubloons`.
11. `/pirates trade port cane seafarer_village tropical` and `/pirates trade port fort navy_outpost temperate` and
    `/pirates trade port tortuga pirate_island tropical`.
    - **Expected:** each shows `Port pirates_n_ships:debug/<name> (<kind>, <climate>) is open`.
12. `/pirates trade goods cane 64` and `/pirates trade goods fort 64`.
    - **Expected:** one line per good with its role and the buy and sell totals for 64 units. Sugar is cheaper in
      `cane` if `cane` produces it, and dearer where it is demanded. Write the sugar prices down.
13. `/pirates trade buy cane pirates_n_ships:sugar 64`, then run `/pirates trade goods cane 64` again.
    - **Expected:** `Done: 64 units, N doubloons`. You lose N doubloons and gain 64 sugar. Cane's sugar buy price for
      the next 64 is higher than before.
14. `/pirates trade sell fort pirates_n_ships:sugar 64`, then `/pirates trade goods fort 64`.
    - **Expected:** you get doubloons and lose the sugar. Fort's sugar sell price is lower than before.
15. Wait one in-game day (`/time add 24000`), then list both ports' goods again.
    - **Expected:** both sugar prices moved back part of the way toward the prices you wrote down.
16. Buy 64 sugar again, hold it, run `/pirates trade plunder`, then hover over the stack.
    - **Expected:** `Marked Sugar as plundered`. The stack no longer stacks with clean sugar. (Its tooltip: see step 24.)
17. `/pirates trade sell tortuga pirates_n_ships:sugar 64 plundered`.
    - **Expected:** `Done: … (fenced)`. The payout is about 35 % lower than a clean sale at tortuga.
18. Mark another 64 sugar as plundered. Open the config screen and set Cargo Trade → Plunder → Navy notice chance
    to 1.0. Run `/pirates trade sell fort pirates_n_ships:sugar 64 plundered`.
    - **Expected:** `Confiscated: 64 units, 0 doubloons (confiscated)` and `The port noticed the plunder`. The sugar is
      gone and you get nothing. Set the chance back to 0.25.
19. Look at an empty crate: `/pirates trade buy cane pirates_n_ships:sugar 200 container`, then
    `/pirates trade sell fort pirates_n_ships:sugar 100 container`.
    - **Expected:** the crate fills with 200 sugar and you pay. Then 100 leave the crate and you get paid. Your
      inventory doesn't change.

## Contracts

20. `/pirates trade contracts cane fort`.
    - **Expected:** today's offers from cane to fort, each with an id, quantity, good, deadline day, reward and deposit.
      (Zero lines is possible on some days. Then try `contracts fort cane`, or wait a day.)
21. Click or copy an id and run `/pirates trade contract accept <id>`, then `/pirates trade contract list`.
    - **Expected:** `Done: 0 units, D doubloons` (D = the deposit is taken). The contract is listed as `accepted`.
22. Get the goods (buy them at cane or `/give`), then `/pirates trade contract deliver fort <id>`.
    - **Expected:** `Done: Q units, R doubloons`. The goods leave your inventory and you get reward plus deposit.
      Delivering at `cane` instead says `Contract refused`.

## Persistence

23. Accept another contract and note a port's prices. Save and quit, then reopen the world.
    - **Expected:** `/pirates trade contract list` still shows the contract. The prices match what you noted, plus
      any recovery for the time that passed. The crates keep their content. Run `/pirates trade open cane` once after
      rejoining to start a new market session (sessions don't survive a relog).

## Tooltips

24. Hover over the plundered sugar from step 16 (mark a fresh stack with `/pirates trade plunder` if it's gone),
    then break a crate holding sugar (step 9) and hover over the dropped crate item. Repeat with advanced tooltips on
    (F3+H).
    - **Expected:** the plundered stack shows a red `Plundered` line right below its name. The crate item shows a
      gray `576 × Sugar` line (its real count) right below its name; a crate filled with plundered sugar also shows
      `Plundered`. Clean sugar and an empty crate show neither line. With F3+H the lines stay directly below the
      name, above the item id.

## Known gaps
- The market protocol has no screen yet. `/pirates trade open <name>` sends the state to the client, but nothing
  displays it yet.
