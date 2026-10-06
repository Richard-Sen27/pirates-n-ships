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
4. `/pirates brig pressgang @e[type=pillager,limit=1,sort=nearest]` - Expected: "Press-ganged Pillager (morale 0.2), crime counted", it disappears, `/pirates law score get @s` shows your score went up by 15.
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
