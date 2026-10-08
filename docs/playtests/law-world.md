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


Addendum (Q1): `/pirates trade port test navy_outpost tropical`, then `/pirates trade plunder` holding sugar, then `/pirates trade sell test pirates_n_ships:sugar 16 plundered` (with `navy_notice_chance` at 1): the "port noticed the plunder" line appears and the score rises by the `fence_plunder` severity; selling clean sugar raises nothing.


## L1: turn-ins and the notice board
1. **Proof turn-in.** Put a bounty on a mob (`/pirates law score set @e[type=pillager,limit=1,sort=nearest] 60`), kill
   it, spawn an officer (`/pirates mob spawn navy_officer`), right-click him with the proof: a gold line "The
   officer takes the proof of … and pays you 120 doubloons", 120 doubloons in the inventory, the proof gone, a
   villager "yes" and an orb sound, `/pirates law bounty list` empty.
2. **Refusal when wanted** (survival; in creative the navy is never hostile): `/pirates law score set @s 100` with a
   proof in hand: "The officer won't deal with a wanted criminal" on the action bar, proof kept.
3. **Used proof:** a second proof of the same kill: "There is no bounty left to claim on …", proof kept.
4. **Pirate delivered alive:** weaken a pirate below 25 %, shackle it, lead it within 4 blocks of an officer,
   right-click with an empty hand: "… is led away by the navy" with a poof, "The navy takes 1 prisoner(s) … pays you
   10 doubloons", shackles back.
5. **Reward 0:** `law.pirate_turn_in.deckhand = 0`: "The navy pays nothing for Pirate", the pirate stays shackled;
   with a bounty on it, bounty × 1.5 is still paid.
6. **Player delivered alive** (two players): place a bounty on B, shackle B at low health, lead B to an officer,
   empty-hand click: A gets 1.5× the bounty, B is freed in place with a message and score 0.
7. **Out of range:** a prisoner more than 4 blocks away is not delivered; the click does nothing.
8. **Turn-ins off:** `law.bounty.turn_in_officers = false`: proof and empty-hand clicks do nothing.
9. **Notice board:** craft, place (notices face you), use: the list shows "The Navy" or the payer, "x min ago", totals
   for targets with several bounties, your own bounty at the top in red if any, coins top right. Place 30 on an
   online name or a chip: "Posted a bounty …", 30 doubloons taken, the list updates. Try 5, more than you carry, an
   unknown name and your own name: a red status line each, nothing charged. A second player placing a bounty shows
   within a second. Walking 8 blocks away closes it; Escape closes it; no errors in the log.
10. **Boards off:** `law.bounty.notice_boards = false`: using the board does nothing.
11. **Model:** the placeholder in the world, hand and inventory in all four facings; an axe drops it.


## U1: the notice board's look (GUI scale 2 and 3)
Wood frame with brass corner studs, a header plaque with the title and the coin count top right; cards as parchment with a pin, a red seal or a blue anchor, the name in dark ink and the amount with the coin; the second line grey ("Placed by: X · N min ago"); hovering a card brightens it with a brass outline; a bounty on you is a red card plus the seal line above the list; with more than about five bounties the wheel and the knob scroll; the form's fields are dark insets whose rim turns brass when focused, name tags turn brass on hover and fill the field when clicked, Place is grey until valid and brass with hover and pressed looks; the status line is green or red ink. Say whether the parchment speckle and the ink read well.


Addendum (N1): the notice board block placed facing each way shows the notices toward you under a small roof between two posts; the outline hugs the posts and board; the item in the GUI, hand, ground and frame; the board's look matches the screen's parchment-and-pin style.

## LAW3: noticed plunder (stolen goods at the desk and aboard)
Setup: `/pirates law score set @s 0`, `/pirates law bounty clear @s`. Mark goods as plunder with `/pirates trade plunder`
(held stack) or take them from a captured ship. Defaults: `law.plunder_notice = true`, `law.plunder_notice_units = 16`,
`selling_plunder` +17 and `suspected_piracy` +15, each with a repeat window of one in-game day (1200 s).

1. **Village desk refuses:** at a seafarer village harbor master's desk, try to sell 32 marked sugar from your
   inventory.
   - Expected: the market screen shows "The harbor master wants no stolen goods"; the sugar stays in your inventory, no
     doubloons. Chat (red): "The harbor master reported you to the navy for offering stolen goods".
     `/pirates law score get @s` shows 17; `/pirates law last @s` shows `Offering stolen goods ... counted, +17.0`
     against the port.
2. **Once per day:** try the same sale again (and from a marked cargo crate in reach).
   - Expected: refused again, no new chat line, score still 17, `/pirates law last @s` shows `repeat_ignored`.
3. **Outpost desk:** repeat step 1 at a navy outpost desk. Expected: refused, reported (+17, another port), red line.
4. **Fence:** sell the same marked sugar at a pirate island fence. Expected: sold at the fence discount, no chat line,
   score unchanged.
5. **Bounty follows:** `/pirates law score set @s 40`, then offer plunder at a desk you have not tried today.
   Expected: score 57, a navy bounty on the notice board.
6. **Plunder aboard:** fill a chest (or crate) on your own assembled ship with 20 marked units, fly any flag or none and
   sail (or anchor) within 48 blocks of a navy soldier or officer (an outpost).
   - Expected: within a few seconds a red "Your cover is blown: the navy has spotted plunder in your hold";
     `/pirates law last @s` shows `Suspected piracy ... counted, +15.0` against the ship. Staying in range records
     nothing more that day. With only 10 marked units aboard nothing happens. Marked goods in a shulker box inside a
     chest count too.
7. **Toggle off:** `law.plunder_notice = false`: desks still refuse marked goods with the same status line, but there
   is no chat line, no crime and no `suspected_piracy` aboard.
