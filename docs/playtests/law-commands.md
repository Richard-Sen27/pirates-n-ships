# Playtest: law system through the debug commands (work package C4)

The rules are covered by JUnit tests and GameTests. This checklist covers what only a real client and a real save show:
command feedback text, persistence across death and across a world reload, and the config toggle.

Navy mobs, notice boards and the doubloon item don't exist yet, so everything goes through the operator commands under
`/pirates law` (permission level 2). Doubloon amounts are plain numbers for now.

Setup: `./gradlew :neoforge:runClient`, a single-player world with cheats on. Spawn one villager to use as a target.
Please send a screenshot of the chat after steps 4, 8 and 12, and `latest.log` if anything differs.

## Steps

1. Run `/pirates law crime @s kill_navy` twice.
   - **Expected:** +30 points each time. Kills never count as repeats.
2. Run `/pirates law crime @s attack_navy` twice within a few seconds.
   - **Expected:** the first adds 10 points, the second reports `repeat_ignored` (same crime, same victim, inside the 30 s window).
3. Run `/pirates law score get @s`.
   - **Expected:** a score of about 70 and the wanted level "Wanted".
4. Run `/pirates law bounty list`.
   - **Expected:** one entry for you with 140 doubloons (score × 2), 1 bounty, marked as wanted by the navy.
5. Run `/pirates law score set @s 30`, wait 5 seconds, then list again.
   - **Expected:** the bounty is still there with 140. It is never lowered, and 30 is not below the withdraw line of 25.
6. Run `/pirates law score set @s 20`, wait 5 seconds, then list again.
   - **Expected:** "There are no bounties". The navy withdrew it.
7. Run `/pirates law score set @e[type=villager,limit=1] 60`, then `/pirates law bounty place @e[type=villager,limit=1] 25`, then list.
   - **Expected:** the villager has a total of 145 (navy 120 + your 25).
8. Run `/pirates law bounty claim @e[type=villager,limit=1] alive`.
   - **Expected:** a payout of 218 doubloons from 2 bounties (145 × 1.5, rounded). Afterwards the villager's score is 0 and the list is empty.
9. Run `/pirates law bounty place @s 50`.
   - **Expected:** refused with `self_target`.
10. Give yourself a score (`/pirates law score set @s 40`), then run `/pirates law fine @s 10`.
    - **Expected:** 10 doubloons spent, about 3.3 points removed (3 doubloons per point).
11. With a score above 0, die (`/kill`) and respawn, then run `/pirates law score get @s`.
    - **Expected:** the same score as before death, minus a little decay.
12. With a score of 60 or more and a bounty on the list, save and quit to the title screen, reload the world, then run `score get` and `bounty list`.
    - **Expected:** both the score and the bounty survived.
13. While in the world, open the config screen (Mods → Pirates 'n' Ships → Config → server config → Law) and turn **Criminal Score Enabled** off, then repeat step 1.
    - **Expected:** the command reports `disabled`, the score stays 0, and an existing navy bounty disappears within 5 seconds.

## Also worth a look
- All command feedback is readable English, with no raw translation keys.
- Score decay: set a score of 20 and fast-forward one in-game day with `/tick sprint 24000` (decay follows game time, which `/time add` does not advance). It should drop by about 10.
