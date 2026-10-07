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
