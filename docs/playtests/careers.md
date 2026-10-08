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
