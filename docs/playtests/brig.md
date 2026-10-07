# Playtest: brig and shackles (E1b)

Setup: creative world with cheats, `/gamemode survival` for the capture steps. Give yourself the items:
`/give @s pirates_n_ships:shackles 8`, `/give @s pirates_n_ships:brig_bars 32`, `/give @s pirates_n_ships:brig_door 2`,
`/give @s minecraft:wooden_sword`. Default config. Tip: `/gamerule doDaylightCycle false`.

## 1. Capture
1. Summon a pillager: `/summon pillager ~ ~ ~5`. Hit it once with full health shackles in hand (right-click it with shackles).
   - Expected: action bar "Pillager is still too strong. Weaken them first", shackles count unchanged.
2. Hit it with the sword until it is at a quarter of its health or less (about 4 hits with a wooden sword; `/data get entity @e[type=pillager,limit=1] Health` must be 6 or less), then right-click it with shackles.
   - Expected: "Pillager is in shackles", one pair of shackles used, chain sound, small grey particles around the pillager every second.
   - Expected: it stops shooting, stops moving on its own, no longer looks at you. Hitting it again works, it never shoots back.
3. Right-click a pillager at full health in creative: refused the same way. Try shackles on the Ender Dragon or Wither (`/summon wither`, careful): "can't be shackled".

## 2. Leading
1. Walk away from the shackled pillager.
   - Expected: it walks after you and stops about 3 blocks away. Climb a small step or two: it follows.
2. Jump down a 3-block cliff or sprint away: beyond 6 blocks it is pulled after you like on a lead.
3. Fly/run more than 12 blocks away (e.g. `/tp @s ~20 ~ ~`).
   - Expected: "You lost hold of Pillager". It stays shackled and stands still.
4. Walk back and right-click it with shackles: "You lead Pillager" (no shackles used). Right-click again: "You let go of Pillager's chain", it stands still.

## 3. Brig cell
1. Build on a solid floor: a 1x2-wide, 2-high space. Walls of brig bars or any solid blocks on all sides except one opening, a solid ceiling, and a brig door in the opening (placed by you).
2. Lead the pillager into the cell, step out and close the door. Sneak + right-click the door with an empty hand.
   - Expected: "Brig door locked", the lower half of the door shows a golden padlock.
   - Expected within a second: "Pillager is locked in the brig". It no longer follows you.
3. Walk 30 blocks away, wait a minute, come back.
   - Expected: the pillager is still in the cell, still shackled.
4. `/pirates brig list` - Expected: the pillager is listed with "not led, in a locked cell".

## 4. The lock
1. Right-click the locked door normally: it opens for you (owner) and still shows the padlock. Close it again.
2. Optional, second account: right-click the locked door. Expected: "The brig door is locked", nothing opens. Sneak + right-click: "Only the door's owner can lock or unlock it".
3. Put a lever next to the locked door and flip it. Expected: the door does not open. Unlock (sneak + right-click), flip the lever: it opens and closes with the lever.
4. Villagers and zombies never open or break the brig door (it behaves like an iron door to mobs).

## 5. Escape
1. Lock the pillager in the cell again, then sneak + right-click to unlock and open the door. Stand far away (more than 12 blocks) so it is not led.
   - Expected: with the default chance (10% per minute) it eventually escapes. To speed it up, set
     `brig.escape_chance_per_minute = 1.0` in the server config (or the config screen) and wait a second.
   - Expected on escape: chain-break sound, "Pillager escaped!", the particles stop, it shoots at you again if you are within 32 blocks in survival.
2. Set `flags_brig.prisoner_escapes = false`, capture another mob, let go of its chain and wait a minute. Expected: no escape.

## 6. Outcomes (debug commands)
Capture a fresh pillager for each step: summon it, weaken it (or `/data merge entity @e[type=pillager,limit=1,sort=nearest] {Health:3f}`), then shackle it by hand or with `/pirates brig capture @e[type=pillager,limit=1,sort=nearest]` (same rules, no item used).
1. Deliver as a pirate turn-in: `/pirates brig deliver @e[type=pillager,limit=1,sort=nearest] captain`.
   - Expected: "Delivered, payout 150 doubloons" (captain turn-in reward), the pillager disappears.
2. Deliver without tier and without bounty: "Not delivered: nothing_to_claim", the pillager stays a prisoner.
3. `/pirates brig ransom @e[type=pillager,limit=1,sort=nearest] navy_officer true` - Expected: "Ransomed for 360 doubloons", it disappears.
4. `/pirates brig pressgang @e[type=pillager,limit=1,sort=nearest]` - Expected since LA2: "Not press-ganged: not_a_sailor", the pillager stays a prisoner, no crime. Only shackled sailors on your own ship can be press-ganged (section LA2 below).
5. `/pirates brig release @e[type=pillager,limit=1,sort=nearest]` - Expected: "Released", a pair of shackles drops, the pillager fights again.

## 7. Relog
1. Lock a prisoner in a cell, leave the world (save and quit), rejoin.
   - Expected: it is still in the cell, still shackled (particles), `/pirates brig list` shows it, it doesn't attack.
2. Same with a prisoner you are leading: after rejoining it follows you again (or, if more than 12 blocks away, stands still).

## 8. Optional: PvP capture (two accounts)
1. Give player B a bounty: `/pirates law score set B 60` (above the bounty threshold 50; the navy places a bounty within 5 seconds).
2. Player A hits B down to 5 hearts or less and right-clicks B with shackles.
   - Expected: B sees "You were captured by A", gets slowness, weakness and mining fatigue, can't damage anyone or break blocks, and is pulled after A.
3. Wait 5 minutes (`brig.player_capture_seconds`, 300) or relog B after that time. Expected: "You slipped out of your shackles", effects gone.
4. With `flags_brig.player_capture = false`: "Capturing players is disabled on this server". A player without a bounty: "Only players with a bounty can be captured".

Report: screenshots of the locked door texture and the particles, and the log if anything throws.


Addendum (F7e): the bars and the door are now Blockbench models (iron posts and rails, a barred cell door with hinge straps and a padlock when locked). Check in each step that the connections, the open leaf and the padlock side look right; details in `items-and-blocks.md`, section "3D models", step 10.


Addendum (P1): bars beside a door reach its edge on both heights and retract when the door is broken. Craft a brig key (ingot over nugget): using it on either half locks the door with a click, "Brig door locked" and the padlock; again unlocks. Sneaking without a key says "You need a brig key…" and changes nothing. The owner opens the locked door bare-handed; another player can't.


## LA2: law outcomes as NPC interactions

Setup: cheats on, `/gamemode survival` for every step (creative players are never navy targets, but the shackles and coins work the same). Items: `/give @s pirates_n_ships:shackles 8`, `/give @s pirates_n_ships:doubloon 64`, `/give @s pirates_n_ships:captains_whistle`, `/give @s minecraft:wooden_sword`. Default config. Weaken a mob quickly with `/data merge entity @e[type=<type>,limit=1,sort=nearest] {Health:3f}` before shackling it.

### 1. Paying a fine at a navy officer
1. `/summon pirates_n_ships:navy_officer ~3 ~ ~` and `/pirates law score set @s 5`.
2. Hold exactly 20 doubloons in the main hand (drop or store the rest) and right-click the officer.
   - Expected: chat (gold) `"That settles 5 points. Your slate is clean. Mind yourself." (15 doubloons)`, the officer nods (villager yes sound), 5 doubloons left in hand.
   - `/pirates law score get @s`: score 0.
3. Right-click him again with the coins.
   - Expected: `"You owe the Crown nothing."`, no coins taken.
4. `/pirates law score set @s 5`, hold 10 doubloons, right-click.
   - Expected: `"That settles 3 points. Mind yourself." (9 doubloons)`, 1 doubloon left, `/pirates law score get @s` shows 2.
5. Hold 2 doubloons with a score above 0: `"That won't settle a single point. 3 doubloons a point."` (red, action bar), nothing taken.
6. `/pirates law score set @s 60` (wanted), right-click with coins.
   - Expected: red action bar "The officer won't deal with a wanted criminal", no coins taken, score 60; he attacks you. `/pirates law score set @s 0` afterwards.
7. Precedence: with a shackled prisoner next to you and coins in hand, a plain right-click pays the fine; sneak + right-click hands the prisoner over instead (step 2 below).

### 2. Ransoming a navy soldier or a merchant
1. `/summon pirates_n_ships:navy_soldier ~5 ~ ~`, weaken it, right-click it with shackles ("Navy Soldier is in shackles"). Note: hitting it is a crime (`/pirates law score get @s`); keep the score below 50 or reset it with `/pirates law score set @s 0`.
2. Lead it to the officer (within 4 blocks of him) and right-click the officer with an empty main hand.
   - Expected: `"The Crown thanks you." Navy Soldier is ransomed for 15 doubloons`, then "The navy takes 1 prisoner(s) off your hands and pays you 15 doubloons"; 15 doubloons in your inventory, the shackles come back, the soldier's chain particles stop and it walks over to the officer.
3. Same with a villager or a sailor (`/summon pirates_n_ships:sailor`): 60 doubloons (merchant). With a shackled navy officer: 120.
4. A shackled pirate without a bounty is turned in (10 doubloons for a deckhand, "led away by the navy"), never ransomed.
5. Set `law.ransom_needs_port = true` and repeat step 2.
   - Expected: red "Ransoms are only paid at a navy outpost", the soldier stays shackled (no navy outposts generate yet).

### 3. Press-ganging a sailor
1. Build and assemble a small ship yourself (you are its owner), standing on land or in water.
2. Summon a sailor on the deck (`/summon pirates_n_ships:sailor` while standing on the deck), weaken it, shackle it.
3. Hold the captain's whistle and right-click the shackled sailor.
   - Expected: the radial order menu does NOT open; chat (gold) `Sailor, grumbling: "Aye... Captain." Pressed into your crew`, a grumble and a chain-break sound. The sailor turns into a crew member at the same spot (crew member look).
   - Right-click the new crew member with the whistle: its crew line shows morale 30.
   - `/pirates law score get @s`: score up by 15. `/pirates law last @s`: "Press-ganging", counted.
4. Shackle a sailor on the ground next to the ship and use the whistle on it.
   - Expected: red action bar "Bring them aboard your ship first.", it stays shackled, no crime.
5. Optional, second account: on a ship assembled by the other player: "This isn't your ship to crew."
6. `/pirates brig pressgang @e[type=pirates_n_ships:sailor,limit=1,sort=nearest]` takes the same path ("Press-ganged Sailor (morale 30), crime counted" on your ship, "not_on_ship" off it).

### 4. Releasing a prisoner
1. Shackle a pirate (`/summon pirates_n_ships:pirate`, weaken, shackle). Sneak + right-click it with an empty main hand.
   - Expected: chat `"Go, before I change my mind." Pirate is free`, chain-break sound, particles stop, the shackles come back into your inventory. The pirate fights you again (survival).
2. `/pirates brig releases` - Expected: "<you> released: navy 0, pirates 1, merchants 0, total 1". Release a villager: merchants 1, total 2.
3. Right-click a prisoner without sneaking: nothing is released (it toggles leading only with shackles in hand). A prisoner shackled by another player can't be released by you.

### 5. Toggles
1. `flags_brig.prisoner_interactions = false`: the whistle opens the radial menu on a shackled sailor, sneak-use releases nothing, the officer ransoms nobody ("The navy pays nothing for …").
2. `law.officer_fines = false`: coins in hand at the officer do nothing.

Report: the chat lines of each step, whether the whistle menu stays closed in step 3.3, and how the ransomed soldier behaves after step 2.2 (it should not attack you; if it does within ~30 s, that is the mob's grudge from being hit).
