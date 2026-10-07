# Playtest: pirates, sailors and the navy (work package M3)

Hostility, the duelist brain, the musketeer cycle, crimes, drops, the spawn command and mobs on a moving deck are
covered by 24 JUnit tests and 10 GameTests. What only a client shows: looks, the telegraph poses and their rotation
signs, spawn egg colours, and how the fights feel.

Setup: `./gradlew :neoforge:runClient`, survival with cheats, a saber, `/pirates mob spawn <type> [count]` or the spawn
eggs, a ship for step 8. The mobs use script-made textures on the sailor rig until their Blockbench models land.
Please send `latest.log` if anything differs.

## Steps
1. **Looks.** Spawn each type: pirate (dark coat, bandana, eyepatch, cutlass in hand), sailor, navy soldier (blue coat,
   white cross belts, tricorn, musket), officer (gold trim, bicorne, saber); idle and walk animations, the head follows
   you. Spawn eggs should be coloured (grey eggs mean the colour registration failed).
2. **Pirate duel.** Holding a saber, stand near a pirate: it walks up, and before each hit its arm visibly rises
   (slash) or draws back (thrust). Parry just before the hit: no damage, the pirate staggers (arm flung out, leaning
   back) and you can riposte. Swing at it: it sometimes guards (blade across the body) and sometimes parries. Check
   the rotation signs: the arm should rise up and forward, not down and back; say so if mirrored.
3. **Officer.** A noticeably better parrier than the pirate.
4. **Navy, innocent.** With a clean score (`/pirates law score get @s`), stand 8 blocks from a soldier for 10 s: it
   ignores you.
5. **Navy, wanted.** Raise your score to WANTED (`/pirates law score set @s 60`): the soldier targets you, raises its
   musket (both arms forward), fires a smoking shot within about a second, reloads for 5 s, backs off if you come
   closer than 4 blocks and shoves you at arm's length.
6. **Factions.** A pirate and a soldier or officer 10 blocks apart fight each other.
7. **Sailor.** Hit it or bring a pirate or a zombie close: it runs.
8. **Ship.** Stand pirates and navy on an assembled ship and sail: they ride along and don't slide off. A strolling
   mob may walk off a small deck (known).
9. **Crimes.** Hit a soldier: `attack_navy` in `/pirates law last @s`. Kill one: `kill_navy`. Kill a pirate: nothing.
10. **Drops.** Pirates drop doubloons and now and then a cutlass; navy drop lead shot and gunpowder; `mobs.drops =
    false` drops nothing.
11. **Toggles.** `pirates_hostile = false`: pirates leave you alone until you hit them; `pirate.peaceful = true`: they
    never fight back; `navy_hostile = false`: wanted players are left alone; `factions_fight = false`: pirates and navy
    ignore each other; `sailor.enabled = false`: sailors disappear and the command refuses them;
    `melee.skill_based_combat = false`: pirates fight with vanilla melee; `navy_infinite_ammo = false`: soldiers stop
    after 12 shots.


Addendum (Q1, feints): spawn a pirate (set `melee.npc_skill_multiplier` to 5 so it always feints), hold a sword and parry as soon as it raises its arm: it sometimes stops the swing (no hit, a brief recovery), your parry runs out into the lockout, and its next swing hits you. With `melee.npc_feints = false` it never does this. Say how the aborted swing looks on the mob (the arm pose treats the feint recovery like a normal recovery).


## M5: approach and hits
Survival, difficulty Easy or higher. Run `/pirates mob debug on` first: its reply shows the difficulty (on peaceful
pirates ignore players; say if that was your setup during the third playtest).
1. **Standing still** 5 blocks away: the pirate walks up and hits within about a second, then every 1–2 s.
2. **Walking away** at normal speed: it catches up within several seconds and hits; sprinting gets away.
3. **Strafing** around it at 3 blocks: it closes in and hits within a few seconds, without stuttering.
4. **Kiting** (step back whenever it gets close): it still lands hits within about 10 s.
5. **Hitting it:** after the knockback it resumes the chase at once.
6. **On a deck:** it chases and hits on the deck and does not run off the edge after you.
7. **Parry and feints** still work (`melee.npc_skill_multiplier` 5 for feints).
8. If anything is still off, keep debug on during the fight, send `latest.log` (search `[mob debug]`), then
   `/pirates mob debug off`.
