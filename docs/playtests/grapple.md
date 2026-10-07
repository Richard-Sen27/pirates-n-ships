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
5. **Rope breaking.** Walk or sail more than 24 blocks away: a snap, the hook comes back. With
   `grapple.rope_breaks_lose_hook = true` the hook drops where it hung.
6. **From land.** Hook a nearby ship from the shore: it drifts gently toward you.
7. **Misses.** Throw at your own ship, at land and into water: the hook lies there for two seconds, then returns.
8. **Toggle.** `grapple.enabled = false`: right-click does nothing, a hook already out comes back.
9. **Two players.** Each has their own hook and rope, both haul at once, one player's release does not affect the
   other's hook; log out with a hook out and check it is in your inventory after rejoining.
10. **First person.** The rope's hand position looks right and the rope is drawn from far away.

## Also worth a look
- Should the hauled ships end closer (hull to hull) for boarding, or is the current gap right for a plank later?


## GR1: launching and mooring rings
1. **Ranges:** on flat land at about 30°, thrown, crossbow and musket: the hook drops at about 24, 36 and 48 blocks, each flight visibly faster.
2. **Draw:** hook in the main hand, crossbow in the off hand: holding use plays the loading sounds with the end click at about 1.25 s; letting go early does nothing; after the click the shoot sound, the flight, 1 durability lost, the crossbow's own charge unchanged. Say whether the bow pose looks acceptable.
3. **Powder:** an empty musket and 3 gunpowder: use fires at once with the shot sound, smoke and a view kick, leaving 2; no powder gives a click and a message; creative is free.
4. **Refusal:** a loaded musket in the off hand: "Unload the musket first…", the hook stays, the musket stays loaded.
5. **Catching:** a ring on another ship's deck; throw slightly over or beside it: the hook snaps onto the ring and hauling works.
6. **Holding:** a ring latch holds past 24 blocks and snaps at about 48; a plain latch snaps at 24.
7. **Tying off:** with a hook latched, use a ring on your own ship: "Rope tied…", a knot sound, the rope drawn from the ring; walk off: the haul continues and the hulls end side by side; sneak-use the air empty-handed releases; breaking either ring releases.
8. **Model:** the placeholder iron plate faces correctly on floor, wall and ceiling.

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
9. **Rope gone:** while someone slides, the thrower sneak-uses the air empty-handed (release) or walks beyond 24 blocks (snap): the rider drops where they hang.
10. **Things in the way:** looking at a block or a mob in front of the rope and pressing use uses that block or mob, not the rope.
11. **Config off:** `/config` or the server config: `grapple.slide.enabled = false`: using the rope does nothing special (the click goes to whatever vanilla would do; the action-bar message "Sliding along ropes is disabled" only appears if the client has not received the new value yet); anyone still sliding drops off at once. Turn it back on: grabbing works again.
