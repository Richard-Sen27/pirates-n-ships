# Playtest: law in the world (work package E1a)

Checks that crimes are detected from real gameplay, that bounty proofs work, and that the score survives a relog.
The commands themselves are covered by `law-commands.md`; this file only uses them to read results.

Setup: single-player world with cheats on, default server config. Survival mode unless a step says otherwise
(creative works too, but the navy hostility check exempts creative players). Find a village (`/locate structure #minecraft:village`).
Before each section run `/pirates law score set @s 0` and `/pirates law bounty clear @s`.

Default numbers: attack villager +5 (repeat window 30 s), kill villager +20, theft +5 (repeat window 60 s per container).

## 1. Hitting and killing a villager
1. Punch a villager once. Run `/pirates law score get @s`.
   - Expected: score 5, Suspect is not reached yet (threshold 10), "1 crimes on record".
2. Punch the same villager a few more times within 30 seconds. `/pirates law score get @s`.
   - Expected: still 5. `/pirates law last @s` shows `attack_villager ... repeat_ignored`.
3. Kill the villager. `/pirates law score get @s`.
   - Expected: 25 (Suspect), 2 crimes. `/pirates law last @s` shows `kill_villager ... counted, +20.0`.
4. Punch a wandering trader (`/summon wandering_trader ~ ~ ~2`).
   - Expected: +5.
5. Punch a zombie villager, an iron golem, a pig.
   - Expected: no change, `/pirates law last @s` still shows the earlier crime.

## 2. Shooting a villager
1. `/pirates law score set @s 0`. Shoot a different villager with a bow (one arrow, don't kill it).
   - Expected: score 5, `/pirates law last @s` shows `attack_villager` against the villager's name.
2. Let a zombie attack a villager (night or `/summon zombie`).
   - Expected: `/pirates law score get <zombie>` (use `@e[type=zombie,limit=1,sort=nearest]`) shows 0 and no crimes.

## 3. Theft from a village chest
Find a chest inside a village house (natural loot chest, not placed by you).
1. With a villager standing nearby and able to see you (within 16 blocks), open the chest and take an item out. Close it.
   - Expected: action bar "Villager saw you stealing!", `/pirates law last @s` shows `theft ... counted`, score +5.
2. Open it again and only put items in. Close it.
   - Expected: nothing new (`/pirates law last @s` unchanged).
3. Take items from a different chest within 60 s of step 1, with a witness.
   - Expected: counted again (+5): the repeat window is per container.
4. Wait until night when villagers sleep in beds, or lead all villagers away / out of sight (behind a wall). Take items.
   - Expected: no theft (`last` unchanged, no action-bar message).
5. Place your own chest in the village, put something in, take it out with a villager watching.
   - Expected: no theft (you placed it).
6. A chest outside any village (e.g. your base), with a villager you brought there watching.
   - Expected: no theft.
7. Double chest in a village (if one exists, or `/setblock` two chests side by side inside a village house): take from the second half.
   - Expected: theft counted.

## 4. Bounty proof
1. `/summon pillager ~ ~ ~3 {NoAI:1b}`. Place a bounty: `/pirates law bounty place @e[type=pillager,limit=1,sort=nearest] 50`.
2. Kill the pillager with a sword.
   - Expected: a "Bounty Proof" (scroll with red seal) appears in your inventory, not on the ground. Tooltip: "Proof of the death of Pillager", "Slain by <you>".
3. Hold the proof in your main hand. Run `/pirates law bounty claim proof`.
   - Expected: "Claimed the bounty on Pillager: 50 doubloons from 1 bounties". The proof is gone. `/pirates law bounty list` shows no bounty on it.
4. Run the claim again with an empty hand.
   - Expected: failure `not_a_proof`.
5. Kill a pillager without a bounty.
   - Expected: no proof.
6. Arrow kill: repeat 1–2, killing with a bow.
   - Expected: proof in your inventory.
7. Full inventory: fill your inventory, repeat 1–2.
   - Expected: the proof drops at your feet.

## 5. Relog keeps the score, client gets the wanted level
1. `/pirates law score set @s 60`. Check `/pirates law hostile @s` in survival.
   - Expected: "The navy attacks <you> on sight (Wanted)". In creative: "leaves ... alone".
2. Save and quit to title, reopen the world. `/pirates law score get @s`.
   - Expected: about 60 (decay only starts 5 minutes after the last crime, then 10 points per in-game day).
3. Look in `logs/latest.log` for errors mentioning `wanted_sync` (there should be none). There is no HUD yet; the
   client value is only visible to a later HUD.

## 6. Toggles (optional)
Edit `serverconfig/pirates_n_ships-server.toml` (or the config screen), section `law.world`:
- `combat_crimes = false`: punching a villager gives no score.
- `theft_detection = false`: step 3.1 gives no theft.
- `bounty_proof_drops = false`: step 4.2 gives no proof.
Set them back afterwards.
