# Playtest: careers (work package CAR1)

The server side (enlisting and its refusals, promotions from deeds and quests, desertion and its crime, infamy and the
exclusion of the two ladders, the letter of marque with its fee, prize money and void, persistence through death and
NBT, the officer gesture and the screen's actions, the commands, the `careers.enabled` toggle) is covered by 11
GameTests and the pure rules by JUnit. This file checks that it works and reads well in a real world. Please send
screenshots and `latest.log` if anything differs.

Setup: survival world with cheats, difficulty Easy or higher, default server config. `/give @s pirates_n_ships:doubloon 1000`,
a sword. Find a navy outpost (its garrison has an officer) or spawn one with `/pirates mob spawn navy_officer`.
Reset between sections with `/pirates rep set @s navy 0`, `... pirates 0`, `/pirates career set @s navy none`,
`/pirates career set @s infamy deckhand`, `/pirates law score set @s 0`, `/pirates law bounty clear @s`.

Default thresholds (navy reputation / pirates killed or turned in / navy quests): Midshipman (= enlisting) 10/0/0,
Lieutenant 25/5/1, Captain 45/15/3, Commodore 65/30/6, Admiral 85/60/10. Infamy (pirate reputation / plunder fenced in
doubloons / captures = merchants plundered + ships captured + navy officers killed): Buccaneer 15/100/0, Dread Captain
40/1000/3, Pirate Lord 75/5000/10. Letter of marque: navy reputation 20, at most Buccaneer, 200 doubloons, 5 per pirate
kill, 50 per pirate captain, a void letter blocks a new one for 3 days.

## 1. The officer's screen
1. Empty main hand, not sneaking, right-click the officer. Expected: the "Navy Officer" screen opens: reputation line,
   "Navy rank: not in service", "Next: Midshipman" with "- navy reputation 0/10" in red, "Infamy: Deckhand",
   "Next: Buccaneer" with its requirements, "Letter of marque: none", "Prize money waiting: 0", and three buttons:
   Enlist (disabled), Letter (200) (disabled), Collect prize (disabled). Hovering a disabled button explains why.
2. Walk away (more than ~5 blocks). Expected: the screen closes. Escape and the inventory key close it too.
3. Sneak-click and click with an item in hand. Expected: no screen (the officer's turn-ins and fines work as before:
   coins in hand pay a fine, a proof claims a bounty, a shackled prisoner nearby is handed over).
4. `/pirates rep set @s navy -100`, click the officer. Expected: "The officer will not speak with you." in red, no
   screen (he may also attack you).

## 2. Enlist and get promoted
1. `/pirates rep set @s navy 10`, open the screen. Expected: Enlist is active. Press it. Expected: "You have enlisted in
   the navy as Midshipman." in chat, the screen shows "Navy rank: Midshipman", "Next: Lieutenant", the infamy part reads
   "No infamy while in navy service", the button now says Resign.
2. Kill five pirates (`/pirates mob spawn pirate`). After each kill the screen (reopened) counts "pirates defeated".
   With navy reputation over 25 the quest requirement still blocks: "navy quests 0/1".
3. Complete one navy quest (QST1, if merged) or skip with `/pirates career set @s navy lieutenant`.
   Expected on the last missing requirement: "You have been promoted to Lieutenant!" in gold.
4. `/pirates career` shows "Career of <you>: navy Lieutenant, infamy Deckhand, letter of marque none, prize money 0".
   `/pirates rep` ends with "Career: navy Lieutenant, ...". `/pirates career @s` (operator) lists the counters.
5. Die and respawn, save and reload: the rank stays.

## 3. Desertion
1. As an enlisted Lieutenant, hit a navy soldier once. Expected: "Desertion! You have lost your navy rank, and the navy
   wants you for it." in red; `/pirates career` shows navy none; `/pirates law last @s` lists the desertion
   crime (60 points plus the attack itself); nearby navy turns hostile; a bounty appears on a notice board.
2. Enlist again (needs a clean record and no bounty), then fire a cannon at a ship under a merchant flag. Expected: the
   same desertion. Please report whether this feels too harsh for an accidental hit (open question).
3. Resign with the Resign button instead: "You have left the navy's service.", no crime.

## 4. Pirate infamy
1. Not enlisted: `/pirates rep set @s pirates 15`, sell 100+ doubloons of plundered goods at a fence. Expected:
   "Your infamy grows: they call you Buccaneer now." in dark red.
2. At the officer: Enlist is disabled ("The navy does not take a known pirate").
3. Enlist a clean character, then `/pirates career set @s infamy buccaneer`. Expected: desertion as in 3.1.

## 5. Letter of marque and prize money
1. Not enlisted, `/pirates rep set @s navy 20`, 200 doubloons in the inventory. Press "Letter (200)". Expected: "You now
   hold a letter of marque ..." and 200 doubloons gone; the screen shows "Letter of marque: held" in green.
2. Kill two pirates. Expected: an action-bar line "Prize money: +5 doubloons (10 waiting at any navy officer)" each time.
3. At any officer press "Collect prize". Expected: 10 doubloons in the inventory, "Prize money paid: 10 doubloons."
4. Hit a navy soldier or plunder a merchant. Expected: "Your letter of marque is void."; no desertion crime for this
   (the attack itself is still a crime); the screen shows "void (new one in 3 days)" and the Letter button is disabled.

## 6. Toggle
1. Set `careers.enabled = false` in the server config, reload. Expected: right-clicking an officer with an empty hand
   opens nothing; kills count nothing; `/pirates career` adds "(careers are off on this server)".

# CAR2: rank rewards

The rules (flag right, fee waiver, navy shipyard and its discount, the fence's infamy bonus, the pirates' friendship,
the gift ledger) are covered by JUnit (`CareerRewardRulesTest`) and 8 GameTests (`CareerRewardsGameTests`). This part
checks them in a real world. Defaults (`careers.rewards.*`): flag right and free docking from Lieutenant; the navy
shipyard from Captain at price × 0.7 (Captain), 0.5 (Commodore), 0.4 (Admiral); a Lieutenant receives the officer's
bicorne and a saber; fences add a price score of 7 / 13 / 20 (Buccaneer / Dread Captain / Pirate Lord, as pirate
reputation points on top of the pirates' reputation, capped at 100); pirates leave a Dread Captain and up alone.

## 7. Promotion gifts
1. Reset, then `/pirates rep set @s navy 25`, enlist at an officer, kill five pirates and do one navy quest (or
   `/pirates career set @s navy lieutenant`). Expected: "You have been promoted to Lieutenant!", then two gold lines
   "With your new rank you receive: Officer's Bicorne" and "... Saber"; both items are in the inventory.
2. Resign at the officer and enlist again (you are promoted straight back to Lieutenant). Expected: no second bicorne.
3. Fill the inventory with dirt, `/pirates career set @s navy captain` on a fresh player (or another account) who was
   never a Lieutenant. Expected: bicorne and saber lie at your feet, "Your pack is full: the rest lies at your feet."

## 8. The navy flag right
1. `/pirates career set @s navy lieutenant`, `/pirates rep set @s navy -20` (below `law.flags.navy_flag_min_standing`,
   default 0). Sail a ship under the navy flag past an outpost's garrison for a minute.
   Expected: no "Your cover is blown" message, no "caught false colours" crime (`/pirates law record @s`).
2. `/pirates career set @s navy midshipman`, same pass. Expected: sooner or later the cover is blown and the crime recorded.

## 9. The navy shipyard
1. As a Midshipman, open the harbor master's desk at a navy outpost. Expected: Goods and Contracts only, no Orders tab.
2. `/pirates career set @s navy captain`, reopen the desk. Expected: an Orders tab; the basic sloop costs 210
   doubloons (300 × 0.7). Order it with logs and wool: 210 doubloons are taken and a ship receipt arrives.
3. `/pirates ship orders finish <id>`, use the desk with the receipt. Expected: the sloop appears at a free berth of
   the outpost's quay, assembled, owned by you. (Report if the quay's berths are blocked or missing.)
4. `/pirates career set @s navy commodore`, reopen. Expected: the sloop costs 150.

## 10. Fences and pirates
1. `/pirates rep set @s pirates 0`, `/pirates career set @s infamy deckhand`, note the buy price of a good at a pirate
   island's fence. `/pirates career set @s infamy dread_captain`, reopen. Expected: the buy price is about 1.3 % lower
   (13 points of the 10 % swing), plunder pays about 1.3 % more; a Pirate Lord gets 2 %.
2. As a Dread Captain with pirate reputation 0, walk into a pirate camp. Expected: the pirates do not attack until you
   hit one. As a Buccaneer they attack.

## 11. Toggles
1. `careers.rewards.flag_right = false`: section 8.1 now ends with blown cover. `navy_shipyard = false`: no Orders tab
   at outposts for a Captain. `promotion_gifts = false`: no items on promotion. `careers.enabled = false`: all rewards off.
