# Playtest: reputation (work package REP1)

The server side (deeds and their configured deltas, persistence through death and relog, decay, the village and fence
price swing, the refusal, pirate and navy hostility, the false-flag rule, port fees, the commands and the
`reputation.enabled` toggle) is covered by 12 GameTests and the pure rules by JUnit. This file checks that it feels
right in a real world. Please send screenshots and `latest.log` if anything differs.

Setup: survival world with cheats, difficulty Easy or higher, default server config. `/give @s pirates_n_ships:doubloon 1000`,
a sword. Test ports: `/pirates trade port cane seafarer_village tropical` and `/pirates trade port tortuga pirate_island tropical`,
each with a harbor master's desk bound to it (`/pirates trade desk bind <name>` while looking at the desk).
Reset between sections with `/pirates rep set @s navy 0`, `... pirates 0`, `... villagers 0`.

Default deltas (navy / pirates / villagers): kill navy −15/+5/0, attack navy −3/+1/0, kill pirate +5/−15/+2,
attack pirate +1/−3/0, kill villager −10/0/−25, attack villager −2/0/−5, fence plunder 0/+3/0, trade at a village
0/0/+1 (5 a day), turn in a pirate +6/−5/0, pay a fine +3/0/0, caught under false colours −10/0/0. Decay 2 points a day
toward 0. Repeat window for attacks: 30 s per victim.

## 1. Kill a pirate near a village
1. `/pirates rep`. Expected: "Reputation of <you>: navy 0, pirates 0, villagers 0".
2. `/pirates mob spawn pirate`, fight it and kill it. `/pirates rep`.
   Expected: navy +6 (attack +1, kill +5), pirates −18 (−3 and −15), villagers +2. Hitting it several times within 30 s
   counts one attack only.
3. Punch a villager once, then `/pirates rep`. Expected: navy −2, villagers −5 more. `/pirates law score get @s` shows the
   crime as before (the criminal score is unchanged by REP1).
4. Die (e.g. `/kill @s`), respawn, `/pirates rep`. Expected: the same values. Save and quit, reload: the same again.

## 2. Trade prices before and after
1. Open the `cane` desk, quantity 64, note the buy and sell prices of two goods.
2. `/pirates rep set @s villagers 50`, close and reopen the desk. Expected: buy prices about 5 % lower, sell prices about
   5 % higher. Buy something: the coins taken match the shown price exactly.
3. `/pirates rep set @s villagers -50`, reopen. Expected: buy about 5 % higher, sell about 5 % lower.
4. `/pirates rep set @s villagers -61` and try to buy. Expected: refused, the status line reads "The villagers won't trade
   with you: your reputation with them is too low"; coins and goods unchanged. At −60 it trades again.
5. Trade six times at `cane` with villagers at 0. Expected: villagers +5 (capped at 5 a day), +1 more the next in-game day.
6. At the `tortuga` desk, mark 64 sugar with `/pirates trade plunder` and sell it as plunder with pirates at 0 and again at
   +50. Expected: the fence pays about 5 % more at +50; each fenced sale gives pirates +3.

## 3. A liked pirate camp stays calm
1. Find or `/locate` a pirate island (or spawn 3 pirates with `/pirates mob spawn pirate 3`).
2. `/pirates rep set @s pirates 41`, walk up to the pirates. Expected: they ignore you (no wind-up, no attack).
3. Hit one pirate. Expected: that pirate fights back for the grudge time (30 s), then loses interest; each first hit
   costs pirates −3, so a few hits drop you below 41 and the whole camp turns hostile again.
4. `/pirates rep set @s pirates 40` (not above the threshold). Expected: the pirates attack on sight as before.

## 4. A hated navy outpost opens fire
1. `/pirates law score set @s 0`, `/pirates law bounty clear @s` (clean, no bounty).
2. `/pirates rep set @s navy -61`, approach a navy outpost or spawned soldiers (`/pirates mob spawn navy_soldier 2`).
   Expected: soldiers aim and fire, officers draw sabers, although `/pirates law hostile @s` says the law has no reason.
   Officers refuse fines and turn-ins while they are hostile.
3. `/pirates rep set @s navy -60`. Expected: they stop and leave you alone.

## 5. False colours
1. Build a small ship, hoist a navy flag, become its owner. Clean record, `/pirates rep set @s navy 0`.
2. Sail past a navy outpost. Expected: nothing happens (navy 0 meets `flags_brig.navy_flag_min_standing` 0).
3. `/pirates rep set @s navy -5`, sail past again (stay within 48 blocks for a while). Expected: within a minute or so
   "The navy has seen through your colours", the soldiers turn on the crew, `/pirates rep` shows navy −15 (−10 for being
   caught) and `/pirates law last @s` shows `caught_false_colors`.
4. Optional: with navy at 0 but `/pirates law score set @s 20` (suspect), the navy flag is still false colours.

## 6. Toggle
Set Reputation → enabled to off. Expected: kills and trades leave `/pirates rep` unchanged (it adds "(reputation is off
on this server)"), prices equal the plain market, villagers trade at −100, pirates attack liked players, the navy ignores
hated players, and a navy flag is false colours only for a suspect or worse.
