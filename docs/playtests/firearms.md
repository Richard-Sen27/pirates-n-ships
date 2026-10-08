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


## P3: aim and reload animations
1. **Pistol aim, third person (F5):** hold use on a loaded pistol: the arm rises in about a quarter second and stays
   out at eye height; look up and down and turn your head without moving: arm and barrel follow the crosshair.
   Release: it fires and the arm drops at once.
2. **Musket aim:** two-handed hold, left arm under the barrel, barrel straight ahead; the zoom still applies.
3. **Pistol reload:** hold use on an empty pistol with ammo: pour, rod, two rams, cock, ending just as the gun becomes
   loaded (3 s). With `firearms.pistol.reload_ticks = 120` it plays at half speed; releasing early stops it.
4. **Musket reload:** butt to the ground, two rams, raise, cock; 5 s, ending when loading completes.
5. **First person:** the animated arms and gun replace the vanilla hand, the musket shows the left arm; looking steeply
   up and down keeps the gun in view. `melee_animations.first_person = OFF`: vanilla hand, third person still
   animates.
6. **Second client:** the other player sees your aims and reloads (someone joining mid-reload sees it from the start).
7. **While moving:** walk, sprint and strafe while aiming or reloading: legs walk, arms keep the pose, the barrel stays
   on the crosshair.
8. **Left-handed, or the gun in the off hand:** mirrored, gun in the correct hand.
9. **A sword stagger while aiming** shows over the gun pose, then the aim returns while held.
10. **Toggle:** `firearm_animations.enabled = false` mid-aim: the pose stops; back on: the pose returns.
11. **Look:** do the guns sit naturally in the hand during the aims, and do they jump during the reloads (the item
    position channel is unverified)?


## P5: lowering, loading bar, loaded state
1. Load a pistol by holding right-click: a white bar under the hotbar slot fills over about 3 s (musket 5 s), also
   in the inventory. Let go half way: the bar disappears, the gun stays unloaded, no ammo used.
2. Finished loading: the bar is full and gold, in the hotbar, the inventory and after relogging; tooltip "Loaded",
   "Hold to aim, release to fire", "Sneak to lower without firing".
3. Aim, then press sneak while still holding: the aim pose and musket zoom end at once, no shot, no click, the gold
   bar stays; keep holding right-click and sneak: the gun stays down; let go of sneak: it rises again; release: it
   fires.
4. A quick click and a normal hold-and-release still fire; afterwards no bar and "Not loaded".
5. Sneak and right-click a loaded gun: nothing; an unloaded gun with ammo: it loads.
6. `firearms.aim.lower_on_sneak = false`: sneaking no longer lowers, a release while crouched fires, the hint line is
   gone. Say whether you want to fire while crouched by default (then lowering becomes a sneak *press*, not the
   sneaking state).
7. LAN if possible: the other player sees your aim animation stop when you sneak.


Addendum (F8h): in third person the fist closes on the pistol's grip just behind the trigger guard (butt cap below and behind the fist, barrel forward and slightly up), the musket is held at the wrist of the stock behind the lock with the butt under the forearm; off hand mirrored; first person unchanged. During the reloads watch for a visible snap at the start and end (the gun slides 1.6 px / 2.5 px along the arm between rest and the fore-stock poses) and for the musket butt sinking or hovering.


## P6: the cocked hammer
For pistol and musket: an empty gun shows the hammer forward against the frizzen; once loaded the icon switches to the hammer cocked back with the frizzen upright (the gold bar still shows, the white bar during loading). In hand (third person) the hammer is cocked on a loaded gun with no jump of the grip when the state changes; first person likewise. Aiming and sneak-lowering keep the cocked model; the model stays empty until a reload completes; after firing it switches back at once. Item frame, ground and a navy soldier's hand (mobs' guns show the empty model, expected). No missing-model warnings in the log.


## GR3 note: the grappling hook as a musket load
The musket can also be loaded with a grappling hook held in the off hand (one gunpowder, no lead shot). Loading,
the bar, "Loaded", aiming, lowering, cooldown, recoil and rain misfire are the same as for a ball; the item additionally
says "With a grappling hook" and shows the `musket_hook` model. Steps in [`grapple.md`](grapple.md) "GR3". Check here
only that a musket loaded with a ball still behaves exactly as in P1/P3/P5/P6 while a hook sits in the off hand (it
says "The musket is loaded with shot…" once and fires the ball).


## FA1: left click fires, right click aims
Default `firearms.fire_on_attack = true`. Survival, a pistol and a musket, lead shot and gunpowder, a zombie or a
target 10 to 20 blocks away; F3 off, subtitles on. Send `latest.log` if anything differs.
1. **Aimed shot.** Load the pistol (hold right-click, 3 s), let go, hold right-click again: the aim pose comes up (musket:
   zoom). Keep holding for over a second, press left-click: the shot leaves at once (sound, smoke, view kick, gold bar
   gone, "Not loaded"). Still holding right-click: nothing more happens; let go: no second shot, no click.
2. **Hip shot.** Load, then without touching right-click press left-click: it fires from the hip. Fire ten hip shots and
   ten aimed shots (held a second) at a wall 20 blocks away: hip shots scatter visibly wider (pistol 4 degrees, musket 1).
3. **Release does not fire.** Load, hold right-click to aim, let go without left-click: the gun goes down, still loaded,
   gold bar stays, no sound, no cooldown on the hotbar slot. Do it with a quick click too: no shot.
4. **No sword swing with a gun.** Gun in the main hand (loaded or not): left-click air, a block and a mob. Expected: no
   arm swing in first or third person, no punch damage, no block cracks while holding left-click on a block (also in
   creative), no "attack" crit particles.
5. **Empty and reloading.** Left-click an empty gun: the dry click ("Pistol clicks" subtitle), nothing else. While
   loading (holding right-click on an empty gun with ammo) press left-click several times: nothing fires, the white bar
   keeps filling and the load completes; ammunition is used once.
6. **Cooldown.** Fire, reload instantly in creative and spam left-click: at most one shot per half second.
7. **Sneak.** Aiming, press sneak: the gun lowers as before (P5), no shot. Sneaking with a loaded gun, left-click: a hip
   shot (say if you would rather block this).
8. **Off-hand gun.** Empty main hand, pistol in the off hand: right-click aims the off-hand pistol, left-click while
   aiming fires it. Without aiming, left-click with the empty main hand is a normal punch (the off-hand gun only fires
   while it is raised). With a sword in the main hand and a loaded pistol in the off hand, left-click is the sword.
9. **Grappling hook.** Hook in the off hand, musket loaded with it: aim with right-click, fire with left-click; letting go
   of right-click does not launch it.
10. **Tooltip.** A loaded gun reads "Hold use to aim, attack to fire".
11. **Toggle.** Set `firearms.fire_on_attack = false` (the mod's config screen, or the world's server config file):
    left-click is a normal punch with a swing, holding and
    releasing right-click fires as in P1, a quick right-click fires at once, the tooltip reads "Hold to aim, release to
    fire".
12. **Multiplayer** (LAN if possible): the other player sees the shot, smoke and the aim pose; on a dedicated server
    with the toggle off, the client follows the server's value (it is a synced server option).
13. **Melee untouched.** Switch to a rapier: slashes, thrusts and guards work as before.
