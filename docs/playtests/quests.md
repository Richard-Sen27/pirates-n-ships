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

## 10. QST1b: captain hunt (village or navy outpost)
Setup: a world with the named pirate captains (BOS1). An existing server config keeps its old type lists: add
`hunt_captain` to `quests.types.seafarer_village` and `quests.types.navy_outpost` (or delete the `quests` section so the
new defaults are written). Stand at a village or navy outpost within 3000 blocks of a pirate island whose captain lives
(`/pirates mob captain list`; in an open field `/pirates mob captain spawn` works too).
1. `/pirates quest offer <port> hunt_captain` (or wait for a day's offers). Expected: the Quests tab shows "Bring down
   `<Name>` of the island to the `<east|northwest|…>`", reward 400. The name matches `/pirates mob captain list`, the
   direction matches where his island lies from the port. At a pirate island the command answers "can't offer that
   quest now". A port farther than 3000 blocks from any living captain never offers it. One port never lists two.
2. Accept it, sail to the island and kill him (sword, pistol or in a duel). Expected: the moment he falls, "Quest
   complete: Bring down …. 400 doubloons paid"; his bounty proof and drops as before (the bounty pays on top).
3. Accept another hunt, capture him instead (shackles below the capture threshold) and hand him to a navy officer.
   Expected: the officer pays as before and the quest completes at the hand-over. (Needs `reputation.enabled`, the
   default: the hand-over is seen through the `turn_in_pirate` deed.)
4. Accept a hunt, then let somebody else kill him (another player, or `/kill @e[type=pirates_n_ships:pirate_captain]`).
   Expected: within a second "Quest failed: Bring down …" and "`<Name>` is gone, and not by your hand"; no reward. The
   port's open offer for him disappears from the Quests tab. A successor who later takes the post does not count;
   killing him leaves no trace in the quest log.
5. An offer accepted after its captain died (open tab, someone kills him, click Accept) says "That captain is already
   gone".

## 11. QST2: quests at sea and prize money for pirate ships
The rules (offers per port kind, counting, the escort's legs, the prize) are covered by JUnit (`QuestSeaTest`) and the
endings on real hulls by 7 GameTests (`QuestSeaGameTests`: a plunder completes a convoy raid, a sink after the player's
cannon hit credits a patrol hunt, a capture credits a ship hunt and pays the prize with a letter and nothing without,
an escort completes on arrival and fails on sinking, the `quests.sea_quests` toggle). This part checks it at sea.

Setup: survival world with cheats, a real seafarer village and a real navy outpost within 2500 blocks of each other in
the overworld (`/locate structure pirates_n_ships:seafarer_village`, `.../navy_outpost`, `/pirates world ports` for their
ids), a pirate island for section 11.3, your own armed ship with cannons and powder, a sword, 500 doubloons. An existing
server config keeps its old type lists: add `escort` to `quests.types.seafarer_village` and `quests.types.navy_outpost`,
`hunt_ship` to `quests.types.navy_outpost`, `plunder_convoy` and `hunt_patrol` to `quests.types.pirate_island` (or delete
the `quests` section so the new defaults are written). Defaults: escort 120 + 60 per 1000 blocks, stay within 96 blocks
on half the legs; 1 to 3 ships per hunt at 90 (convoy), 150 (patrol), 120 (pirate ship) each; prize money 60.

### 11.1 Escort (village or navy outpost)
1. At the village desk, `/pirates quest offer <village id> escort` (or wait for a day's offers). Expected: the Quests tab
   shows "Escort a convoy to `<outpost name>`" with a shield icon and a reward of about 120 + 60 per 1000 blocks.
2. Accept it. Expected: "The `<ship name>` sets sail now. Keep within 96 blocks of her until she makes port"; the tab
   now says "Escort the `<ship name>` to `<outpost>`, 0/N". Within a few seconds a merchant ship with that name appears
   off the harbour (you are near) and sails off along the lane. `/pirates world voyages` lists the convoy.
3. Sail along within 96 blocks. Expected: every so often the action bar says "Quest: Escort the … (k/N)", one step per
   leg of her course; it never counts while you stay behind at the harbour.
4. Keep with her until she makes port. Expected: when she arrives (the ship fades at the last waypoint), "Quest
   complete: Escort the … to …. R doubloons paid" and the navy or villager reputation deed.
5. Accept another escort, let her go alone (stay in port, or sleep). Expected: when she arrives, "The … made port, but
   you were not with her for long enough" and "Quest failed: …".
6. Accept another escort and sink her yourself (or let a pirate do it). Expected: "The … is lost", "Quest failed: …".
7. If no lane can be found (a destination across land), accepting says "No convoy can sail there now" and the offer is
   gone.

### 11.2 Ship hunt and prize money (navy outpost)
1. `/pirates world voyages spawn near raid` once so a pirate ship is at sea, then `/pirates quest offer <outpost id>
   hunt_ship`. Expected: "Sink or capture N pirate ships", fire-charge icon, 120 × N. With no pirate ship at sea the
   offer command answers "can't offer that quest now".
2. Accept, sink the pirate ship with your cannons (the last hit must be yours within a minute of her going down).
   Expected: "Quest: Sink or capture N pirate ships (1/N)"; at N "Quest complete".
3. Buy a letter of marque from a navy officer (200), spawn another pirate ship, kill her fighters, stand on her deck
   for 5 seconds. Expected: "You took the …! She is yours now", then "Prize money under your letter of marque: 60
   doubloons for the pirate ship." (gold) and the coins in your inventory, and the hunt counts the capture.
4. Without a letter (or after attacking a merchant voided it), sink or capture a pirate ship. Expected: no prize line,
   no coins (the quest still counts).

### 11.3 Convoy raid and patrol hunt (pirate island)
1. With a convoy and a patrol at sea (`/pirates world voyages spawn near convoy`, `... patrol`), at the pirate island's
   desk: `/pirates quest offer <island id> plunder_convoy` and `... hunt_patrol`. Expected: "Plunder N merchant convoys"
   (chest icon), "Sink or capture N navy patrols" (fire charge). Never offered at villages or outposts.
2. Board the convoy and take goods from her cargo container. Expected: "You plundered a merchant ship" and the raid
   counts 1; taking more from the same ship does not count again.
3. Sink a patrol with your cannons. Expected: the patrol hunt counts it (and the navy's reaction as before).
4. Capturing a convoy instead of plundering her does not count for the raid (take the goods first).

### 11.4 Toggle
1. `quests.sea_quests = false`. Expected: no escort, convoy raid, patrol or ship hunt among new offers; quests already
   accepted still count and finish. The prize money stays (it follows `careers.prize_money`; 0 turns it off).
