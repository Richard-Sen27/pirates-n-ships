# Playtest: firearms (work package G3)

Loading, firing, damage, misfire and the config toggle are covered by 9 GameTests and 24 JUnit tests. What only a
client shows: the use animation, smoke, recoil, sounds, the feel of range and drop, shooting from a moving ship, and
whether balls hit ship blocks.

Setup: `./gradlew :neoforge:runClient`, a world with cheats; a pistol, a musket, lead shot and gunpowder; subtitles on.
Please send `latest.log` if anything differs.

## Steps
1. **Tooltip.** Hover a pistol from the creative tab. Expected: "Unloaded" in gray.
2. **Loading in survival.** With 1 lead shot and 1 gunpowder, hold right-click with the pistol. Expected: the bow-draw
   pose and slowdown, a ramrod click at about 1.5 s, after 3 s a loading clunk, the tooltip reads "Loaded" in gold, one
   lead shot and one gunpowder are gone. The musket takes 5 s.
3. **Releasing early** at about 1 s. Expected: still unloaded, nothing consumed.
4. **No ammo.** Right-click without lead shot. Expected: the "Pistol clicks" subtitle, no loading. With
   `firearms.consume_gunpowder = false`, lead shot alone is enough.
5. **Creative.** Loads with an empty inventory.
6. **Firing.** Right-click a loaded gun. Expected: an instant shot ("Pistol fires" subtitle; the musket sounds lower), a
   smoke puff in front of the muzzle, the view kicks up a few degrees and you are nudged back, the gun shows
   "Unloaded" and has about half a second of cooldown, a small lead-shot sprite flies out.
7. **Damage.** A pistol hit on a zombie takes about 10 minus armour, a musket hit 14. Death message "... was shot by
   <player>". Shooting a villager is reported as a crime (law module, `/pirates law score get @s`).
8. **Range and drop.** Shoot at targets 10, 30 and 60 blocks away. Expected: the pistol scatters visibly at range, the
   musket stays tight with a flatter arc, the ball vanishes after 5 s.
9. **Rain.** `/weather rain`, stand outside. Expected: about 1 in 4 shots only click and the gun stays loaded. Under a
   roof no misfires. With `combat.rain_misfire_chance = 0` none at all.
10. **Moving ship.** Shoot forward, sideways and backward while sailing at speed. Expected: the balls don't visibly lag
    behind or veer relative to the deck. Report if they do.
11. **Ship blocks.** Shoot at your own ship's hull from the water. Expected: the ball hits the planks (smoke puff) and
    does not pass through. Report if it does.
12. **Config.** With `firearms.enabled = false`, right-click does nothing.
13. **Multiplayer** (if you have a second client): the other player sees the ball and the smoke and hears the shot.

## Also worth a look
- Is the recoil (3° pistol, 5° musket) too much or too little? Is the reload too long?
- Do the gun models (F8b) point where you aim when firing?


Addendum (P1): loading shows no bow pose any more (and no pose at all, known); keep holding after the loading sounds end and release: no shot. Click a loaded gun: it fires at once. Hold, then release: it fires on release; after about a second of aiming the shot is visibly tighter, and the musket's view narrows while aiming (not while loading).
