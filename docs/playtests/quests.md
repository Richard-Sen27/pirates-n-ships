# Playtest: quests (work package QST1)

The server side (offers per port kind, accepting into the log, every type's progress and completion with its reward,
the deadline, the active cap, abandoning, the desk's Quests tab protocol, the commands and the `quests.enabled` toggle)
is covered by 11 GameTests and the pure rules and generator by JUnit. This file checks the Quests tab and that the
quests feel right in a real world. Please send screenshots and `latest.log` if anything differs.

Setup: survival world with cheats, difficulty Easy or higher, default server config.
`/give @s pirates_n_ships:doubloon 500`, a sword, a bow. Test ports with a harbor master's desk each, bound with
`/pirates trade desk bind <name>` while looking at the desk:
`/pirates trade port cane seafarer_village tropical`, `/pirates trade port fort navy_outpost temperate`,
`/pirates trade port tortuga pirate_island tropical`. For the treasure hunt you need a real pirate island
(`/locate structure pirates_n_ships:pirate_island`), whose desk is bound by world generation.

Defaults: 3 offers per port, offers stay 2 days, 5 days to finish once accepted, at most 3 active quests.
Rewards: 30 per pirate, 35 per navy sailor, 50 per prisoner, 25 per shark, 500 for the kraken, 100 for a treasure, a
delivery pays 1.5 × an ordinary contract. Completing a quest is also a deed: navy outpost +6 navy / −2 pirates, pirate
island +6 pirates / −2 navy, village +4 villagers (`/pirates rep` shows it).

## 1. The tab at each port kind
1. Open the `cane` desk. Expected: a fourth tab button "Quests" after Goods, Contracts and Orders. Click it.
   Expected: "Quests offered here (0 of 3 active)" with 3 offers, each with an icon (sword, trident, map, the cargo's
   item), a title ("Hunt 5 pirates", "Slay 3 × Shark", "Deliver 64 × Sugar to Fort", "Find the buried treasure of …")
   and "reward N doubloons, 5 days to finish, open until day D"; below "Your quests", "No active quests".
2. Village offers come from: deliver, monster hunt, pirate hunt, treasure hunt (only with a pirate island within 2000
   blocks). Expected: never "Kill N navy sailors", never "Bring N captured pirates".
3. Open the `fort` desk (only three tabs: no Orders at an outpost). Expected: pirate hunts, prisoner deliveries, monster
   hunts, deliveries; never navy kills.
4. Open the `tortuga` desk. Expected: navy raids ("Kill N navy sailors"), treasure hunts, deliveries, monster hunts;
   never pirate hunts.
5. Close and reopen a desk on the same day. Expected: the same offers. Sleep through a night twice and reopen.
   Expected: new offers replace the old ones.
6. The window at GUI scale 3 and 4: the four tabs fit and the rows are readable.

## 2. Pirate hunt (navy outpost)
1. At `fort`, accept a "Hunt N pirates". Expected: green status "Quest accepted: Hunt N pirates"; the offer leaves the
   list and shows under "Your quests" with "0/N, by day D, reward R (from Fort)"; the header says "1 of 3 active".
2. `/pirates mob spawn pirate` and kill it. Expected: the action bar says "Quest: Hunt N pirates (1/N)".
3. Kill the rest (or run `/pirates quest complete <id>` from `/pirates quest list` to skip). Expected: chat
   "Quest complete: Hunt N pirates. R doubloons paid", the coins arrive, `/pirates rep` shows navy +6 more than the kills
   alone gave.

## 3. Monster hunt
1. Accept a "Slay N × Shark" (any port). Find sharks in a warm ocean (or `/pirates mob spawn shark`), kill them with the
   sword and once with the bow. Expected: each kill counts (the bow kill too); a shark killed by something else does not.
2. If a port offers "Slay 1 × Kraken" (cold-climate ports always do): kill the kraken. Expected: completes with 500.

## 4. Prisoner delivery (navy outpost)
1. Accept "Bring N captured pirates to a navy officer" at `fort`.
2. Beat a pirate to low health, shackle it, lead it to a navy officer and hand it over (empty hand). Expected: the
   officer pays as before and the quest counts 1/N; at N it completes.

## 5. Cargo run
1. Accept a "Deliver Q × Good to X". Expected: chat "Deliver it at the destination's desk, Contracts tab"; in the
   Contracts tab under "Your contracts" the same contract with reward R and deposit 0, deadline = the quest's.
2. Buy the goods, sail (or walk) to X's desk, Contracts tab, Deliver. Expected: "Contract delivered: Q units, R
   doubloons paid out"; within a second the chat says "Quest complete: Deliver …" (no second payment).

## 6. Treasure hunt
1. On a real pirate island's desk (or a village near one), accept "Find the buried treasure of …". Expected: chat "You
   received a treasure map"; the map is in the inventory and shows the X and a bearing while held.
2. Dig up and open the chest. Expected: "The treasure has been found" and, within a second, "Quest complete: Find the
   buried treasure of …. 100 doubloons paid".
3. If somebody else looted it first, accepting the same offer says "Somebody already dug up that treasure".

## 7. Navy raid (pirate island)
1. At a pirate island, accept "Kill N navy sailors". Kill navy soldiers. Expected: each kill counts; it is still a crime
   (criminal score, bounty) as before; completion pays and gives pirates +6, navy −2.

## 8. Deadline, cap, abandon
1. Accept a quest, then `/time add 24000` six times (5 days to finish). Expected: within a second after the deadline
   day "Quest failed: …"; it leaves the list; a delivery's contract fails too.
2. Accept three quests. Expected: the Accept buttons go grey with the tooltip "You already have the most quests you can
   take".
3. Click "Drop" on one. Expected: "Quest dropped: …", one slot free again; a dropped delivery's contract is abandoned.
4. `/pirates quest list` shows the active ones with their ids; `/pirates quest abandon <id>` drops one.
5. Die and respawn, relog: the active quests are still there.

## 9. Toggle
1. Set `quests.enabled = false` in the server config (or the config screen) and reopen a desk. Expected: no Quests tab;
   active quests stand still (no progress, no deadline) until it is turned back on.
2. Set `reputation.enabled = false`. Expected: ports offer no pirate hunts, navy raids or prisoner deliveries (they count
   deeds, which are off); the others work.
