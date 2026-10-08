# Playtest: grappling hook (work package G11, milestone 9 second half)

Latching, hauling, release, rope break, misses and the toggle are covered by 20 JUnit tests and 8 GameTests. What only
a client shows: the rope and hook rendering, the feel and speed of the haul, and two players.

Setup: `./gradlew :neoforge:runClient`, deep water, two assembled ships a few blocks apart, a grappling hook (3 iron
ingots, 1 string). Please send `latest.log` if anything differs.

## Steps
1. **Throwing.** From your deck: the hook flies with a rope from your hand, the item leaves your hand, bobber-throw
   sound.
2. **Latching.** Hit the other ship's hull: a chain or leash sound; the hook sits on the hull and stays there while both
   ships move, without lag or jitter; the rope is straight.
3. **Hauling.** Stand on your ship with a latched hook: within a few seconds the ships come together without a crash
   or heel and end up side by side, with one to two blocks between the rope's ends; then the rope sags. `/sable` force
   display shows "Grappling Rope" on both ships. Say whether the closing speed (up to about 3 m/s) and the bump feel
   right, and whether the pull stops cleanly once the hulls touch.
4. **Releasing.** Sneak and right-click with an empty hand: the hook returns to your inventory with a reel sound.
5. **Rope breaking.** Walk or sail more than 32 blocks away (GR4; 24 before): a snap, the hook comes back. With
   `grapple.rope_breaks_lose_hook = true` the hook drops where it hung.
6. **From land.** Hook a nearby ship from the shore: it drifts gently toward you.
7. **Misses.** Throw into water or at leaves: the hook lies there for two seconds, then returns. (Since GR4 your own
   ship and land are no misses: the hook latches there, see "GR4".)
8. **Toggle.** `grapple.enabled = false`: right-click does nothing, a hook already out comes back.
9. **Two players.** Each has their own hook and rope, both haul at once, one player's release does not affect the
   other's hook; log out with a hook out and check it is in your inventory after rejoining.
10. **First person.** The rope's hand position looks right and the rope is drawn from far away.

## Also worth a look
- Should the hauled ships end closer (hull to hull) for boarding, or is the current gap right for a plank later?


## GR3: loading and firing the hook
GR3 replaces GR1's launch (hook in the hand, weapon in the other hand, the hook shot at once). Now the hook goes in the
**off hand**, the musket in the **main hand**, the musket is loaded with the hook and then fired. GR4 removed the
crossbow (steps 9 to 13 are gone; a crossbow next to the hook is just a crossbow) and lengthened the ropes to 32 by
hand and 64 from the musket. Survival, default config, some gunpowder.
1. **Musket load, third person (F5):** musket in the main hand, hook in the off hand, gunpowder in the inventory. Hold
   use: the `musket_reload` pose plays (butt to the ground, powder, ram, raise, cock), stretched to the reload time
   (about 5 s); a white bar fills under the musket's slot. When it is full: the hook leaves the off hand, one gunpowder
   is gone (no lead shot used), the bar turns full gold, the tooltip says "Loaded" and "With a grappling hook".
   Keep holding, then let go: nothing fires.
2. **Musket load, first person:** the same session shows the reload with both arms in first person, the bar fills the
   same way.
3. **The hook in the musket:** in the hotbar, the inventory and in hand the musket shows a small iron hook sticking out
   of the muzzle (placeholder model, cocked hammer as when loaded); after firing it switches back to the plain musket.
   Say whether the hook is placed at the muzzle in both views (a Blockbench model follows in an art batch).
4. **Cancel:** start loading and let go half way: the bar vanishes, the hook stays in the off hand, no powder used.
5. **Aim and fire:** hold use on the hook-loaded musket: the `musket_aim` pose with both arms pitched to the look, the
   musket zoom; after about 1 s (`aim_steady_ticks`) let go: the shot sound, smoke, a view kick, the hook flies faster and flatter than a throw with a 64-block rope (GR4: aimed level on flat land it is still in
   the air when the rope runs out at 64 blocks). The gold bar and
   the hook model are gone, the cooldown runs. A quick click fires at once the same way. Sneak while aiming: the
   musket lowers without firing and keeps the hook.
6. **Latching and hauling with a fired hook:** the fired hook latches on another ship, hauls, can be tied to a mooring
   ring and slid along (GR2) exactly like a thrown one; releasing it returns the hook item to your inventory.
7. **Refusals:** a musket loaded with shot and the hook in the off hand: use shows "The musket is loaded with shot:
   fire it before loading the hook" and aims the ball as usual; the hook stays in the off hand. No gunpowder: a click,
   "Loading the hook into the musket takes one gunpowder", nothing taken.
8. **Rain:** in the rain (`combat.rain_misfire_chance` high, e.g. 1.0 for the test) the hook-loaded musket clicks,
   the hook stays in it (gold bar and hook model stay), the cooldown runs.
9. *(GR4: the crossbow steps 9 to 13 are gone; see "GR4" step 1.)*
14. **Hand throw:** the hook alone in the main hand (or with a sword in the other hand) is still thrown by hand.
    The hook in the main hand and the musket in the off hand: also thrown (the off hand is required).
15. **Left-handed:** `grapple.launch.offhand_required = false`: hook in the main hand, musket in the off
    hand: holding use loads the weapon in the off hand (pose mirrored), the next hold-and-release fires it.
16. **Toggle:** `grapple.launch.musket_enabled = false`: the hook in the off hand is no longer loaded into the musket
    (it loads lead shot).
17. **LAN, if possible:** the other player sees your reload and aim poses; the hook model on the musket in your hand.

## GR1: mooring rings
1. **Catching:** a ring on another ship's deck; throw slightly over or beside it: the hook snaps onto the ring and hauling works.
2. **Holding:** a ring latch holds past 32 blocks and snaps at about 64; a plain latch snaps at 32 (GR4 lengths).
3. **Tying off:** with a hook latched, use a ring on your own ship: "Rope tied…", a knot sound, the rope drawn from the ring; walk off: the haul continues and the hulls end side by side; sneak-use the air empty-handed releases; breaking either ring releases.
4. **Model:** the placeholder iron plate faces correctly on floor, wall and ceiling.

## GR2: sliding along the rope
Setup: two assembled ships on open water, about 10 to 15 blocks apart; ship A with a mast or a pillar you can stand on 5+ blocks above its deck (the crow's nest). Default config (`grapple.slide.*`). Survival, so fall damage counts.
1. **Grab your own rope (crow's nest):** on top of A's mast, throw the hook at B's hull so it latches. Look at the rope just in front of you (within about 2.5 blocks) and press use with an empty main hand or the hook: a soft knot sound, you hang about 2 blocks below the rope with both arms up, and you start sliding toward B at once. Your end of the rope stays where your hand was (it does not follow you down).
2. **Slide:** the slide speeds up on the way (steeper rope = faster), roughly 1.5 s for a 13-block rope from a 6-block-high nest. The rope stays drawn from the mast top to the hook the whole time, and you stay under it even while both ships bob or drift.
3. **Landing:** within about 1 block of the hook you let go and stand on B's deck right above the hook (leather sound), no fall damage, no getting stuck in a block.
4. **Hang pose, third person (F5):** both arms straight up and slightly inward to the rope, legs hanging straight (not the seated riding pose). Watch a second player on the same rope from another client too: same pose, smooth movement. The pose ends when they land or let go.
5. **Hang pose, first person:** look up while sliding: both raised arms are visible, no item in the hands. With `melee_animations.first_person = OFF` (client config) the vanilla hand shows instead.
6. **Grab someone else's rope:** a second player stands under or near the middle of the rope (or swims under it), looks at it and uses it: they slide to the lower end (B when the thrower stands higher than the hook). The thrower's end is not pinned by them.
7. **Level rope:** both ends at about the same height (thrower on A's deck, hook in B's rail at the same height): the slide crawls slowly (about 2 blocks/s) toward the hook.
8. **Sneak drop:** while sliding over water, press sneak: you let go where you hang and fall straight down (into the water: no damage; onto a deck from 2 blocks: no damage, fall distance only counts from the rope).
9. **Rope gone:** while someone slides, the thrower sneak-uses the air empty-handed (release) or walks beyond 32 blocks (snap): the rider drops where they hang.
10. **Things in the way:** looking at a block or a mob in front of the rope and pressing use uses that block or mob, not the rope.
11. **Config off:** `/config` or the server config: `grapple.slide.enabled = false`: using the rope does nothing special (the click goes to whatever vanilla would do; the action-bar message "Sliding along ropes is disabled" only appears if the client has not received the new value yet); anyone still sliding drops off at once. Turn it back on: grabbing works again.

## GR4: musket only, no accidental grab, longer ropes, any surface
The human's playtest of GR3: "remove the compatibility from the crossbow", "when pressing rightclick after having it
shot from the musket … the rope is being fixed at the point where the player was and the player is mounting on the
rope", "increase … to 32 blocks", and "the grappling hook should also stick at any surface". Survival, default config,
gunpowder, two hooks, flat land with a cliff or a tower, two assembled ships on open water.
1. **No crossbow:** crossbow in the main hand, hook in the off hand, arrows in the inventory: holding use draws and
   fires arrows exactly as in vanilla; the hook stays in the off hand. With no arrows, right-click throws the off-hand
   hook by hand (vanilla gives the use to the off hand).
2. **Re-aim after a shot (the bug):** musket + hook, load (5 s), aim at a wall or a ship 20+ blocks away and fire. The
   hook latches. Now press right-click again at once, still looking along the rope, and again after a few seconds:
   - with the off hand empty: the musket does its own thing (a click / nothing to load); you never hang on the rope,
     the rope's near end keeps following you (walk around: it is not fixed where you stood);
   - with a second hook in the off hand: the musket starts loading it again (reload pose, white bar).
3. **Grabbing still works:** tie the rope off on a cleat or ring first (GR5), then with an empty main hand (or a hook
   in it) look at the rope within about 2.5 blocks and right-click: you hang and slide (GR2). Your own rope still in
   your hand is only climbed (GR6, see below). Within the first second after a throw or shot
   (`grapple.slide.grab_cooldown_ticks` 20) nothing happens, the use goes to vanilla.
4. **Hand throw reach:** on flat land, aim level and throw: the hook flies about 32 blocks before the rope stops it
   (measured: it would land about 39 blocks away). Aimed higher it still stops at 32.
5. **Musket reach:** aim level and fire: the hook flies a flat arc and the rope stops it at about 64 blocks (measured:
   it would land about 83 blocks away). Say whether the speed and the flatness feel right.
6. **Another ship:** unchanged: the hook latches on the hull and both ships are hauled together.
7. **Your own ship:** from the mast top throw at your own deck rail: the hook latches, nothing pulls, the ship does not
   move or heel. Right-clicking the rope still in your hand does nothing but the hint "Tie the rope off on a cleat to
   use it as a line" (the hook is below you, no climb, GR6); you stay on the mast top. To slide down, tie the rope to a
   cleat up there first.
8. **Kedge off a sandbank:** a ship aground or at anchor near shore: stand on it and throw at a cliff, a rock or a
   quay block within 32 blocks: the hook latches on the block and the ship is slowly hauled toward it (about 0.3 m/s
   for a small hull; slower than two ships hauling each other), the force shows as "Grappling Rope" in `/sable`; it
   stops alongside the block. Breaking the block lets the hook go.
9. **Zip line between two cliffs:** stand on a high cliff, throw at the face or top of a lower cliff across a gap: the
   hook latches, nothing pulls. Right-clicking the rope in your hand only shows the tie-off hint. Place a cleat on the
   high cliff, use it with the hook out (the rope is tied off), then grab the rope with an empty hand: you slide down
   and land on top of the lower cliff. A second player can use the same line.
10. **Slipping:** throw at leaves or a glass pane: the hook does not latch, it drops and comes back after two seconds.
    Into water: the same (a splash).
11. **Toggles:** `grapple.latch_world_blocks = false`: land, cliffs and quays are misses again; `grapple.latch_own_ship
    = false`: your own ship is a miss again.

## GR5 hauling

Hauling a hooked ship by hand (sneak freezes the rope, walking away pulls). Defaults: `grapple.hauling` true,
`haul_stiffness` 60 kpg/s² (pull per block beyond the frozen length), `haul_damping` 30 kpg/s, `haul_max_force` 200
kpg·m/s², `haul_player_pull` 0.08 blocks/tick. Changed with GR5: a hand-held rope from land no longer drags the hooked
ship by itself (it pays out); a rope tied to a cleat or ring on land still does (`shore_haul_force`).

1. **Askew sloop:** moor or float a small sloop near a quay so it lies at an angle to it. Stand on the quay, throw the
   hook at its bow or stern rail (not amidships). Without sneaking, walk back a few blocks: the rope pays out, the
   sloop does not move, the rope sags.
2. **Haul:** hold sneak (the rope freezes), walk back two or three blocks while sneaking: the rope goes straight and
   the sloop starts moving toward you and swings round (a hook near the bow turns the bow toward you). `/sable` shows
   the force as "Hauling by Hand". Release sneak: the pull stops at once and the rope sags again. Say whether the pull
   feels too weak or too strong (`haul_stiffness`, `haul_max_force`).
3. **Slip:** while sneaking, walk back fast and far (sprint-sneak is not possible, so just keep walking): you are never
   stopped and the rope never snaps below its full length; the pull stays at the cap and the frozen length grows.
4. **No reaction on the ground:** while hauling on the quay or a deck you are not pulled toward the ship.
5. **Airborne / swimming:** hook a ship, sneak, then jump off the quay into the water (or jump in place after backing
   off): the taut rope stops you moving away and pulls you toward the hook; you swing on it instead of falling free.
6. **Cleat-tied rope:** tie the near end to a cleat (use the cleat with the hook out), then sneak and walk away: nothing
   is hauled by hand (a cleat on land drags the ship as before; a cleat on your ship hauls the ships together as before).
7. **Your own ship:** a hook on the ship you stand on is never hauled by sneaking (nothing moves).
8. **Toggle:** `grapple.hauling = false`: sneaking does nothing, and a hand-held rope from land drags the hooked ship
   gently toward you again (the old G11 behaviour).

## GR5 no floating rope end, GR6 climb only

The rope's near end is fixed only by a cleat or mooring ring. A rope still in your hand is only climbed (GR6):
right-click pulls you to the hook only when the hook is at least `grapple.climb_min_rise` (2) blocks above your feet.

1. **The reported bug (GR6):** on flat ground throw the hook across a gap at a wall, about level with you or one block
   up. Wait a second, then with an empty hand (or a hook in hand) look along the rope and right-click: nothing moves,
   you do not slide or hang, and the status bar says "Tie the rope off on a cleat to use it as a line". Spam
   right-click: the hint shows at most once a second. The same from a quay at a ship's deck level with you.
2. **Climbing:** throw at a wall or cliff face 4+ blocks above your feet (or at a mast, or from the water at a hull's
   deck edge), wait a second, right-click along the rope: you are pulled hand over hand toward the hook (about
   2 blocks/s, `grapple.slide.slide_min_speed`), up the face where the straight way meets the edge, and land on top
   of the block the hook bit into. The rope always runs from the hook to your hand. Sneak during the climb to let go.
   Say whether the climb looks right where it goes straight up the face near the top.
3. **Blocked:** climb toward a hook under an overhang so that neither the straight way, nor straight up, nor straight
   across is free: you let go where you are. Ship blocks do not block the climb (Sable's ships are not in the world's
   collision query), so check that you are not dragged visibly through a hull.
4. **Config:** `grapple.climb_min_rise = 0`: every right-click on your hand-held rope pulls you to the hook again (GR5).
5. **Someone else's hand-held rope:** a second player right-clicking your rope while you hold it gets nothing (the use
   goes to whatever is behind it).
6. **Cleat-tied line:** tie the rope to a cleat on your ship (or a cleat on a cliff for a zip line), then right-click
   it: you hang and slide toward the lower end as in GR2; a second player can slide too.
7. **Haul and climb together:** with a climb (hook 2+ blocks above your feet, e.g. swimming below a hull), sneak (the
   rope freezes), right-click with an empty hand: the hook is released (sneak + use lets go, unchanged), you are never
   pulled while sneaking. Without sneak, right-click: you climb; sneak
   during the climb: you let go and, still holding sneak (press it again if needed), the rope freezes where you are.
