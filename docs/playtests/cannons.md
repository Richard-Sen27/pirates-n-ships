# Playtest: cannons (work package G9, milestone 9 first half)

Loading order, aiming, firing, damage, block breaking, the hull breach and the config toggles are covered by 13 JUnit
tests and 11 GameTests. What only a client shows: the feel of aiming and firing, sounds, smoke, recoil, and shots
from a moving ship at another ship.

Setup: `./gradlew :neoforge:runClient`, a world with deep water, two ships (or one ship and a plank wall), cannons,
gunpowder and cannonballs. Please send `latest.log` if anything differs.

## Steps
1. **Placing.** Place a cannon on land facing east. Expected: the barrel points east, wheels at the sides, no missing
   textures; the item shows the model (a placeholder until its Blockbench batch).
2. **Loading.** Empty hand: "Not loaded: put in gunpowder, then a cannonball". Cannonball first: "Powder first, then the
   ball". Gunpowder: "Powder in…", one gunpowder gone. Gunpowder again: "The powder is in…". Cannonball: "Loaded…", a
   metal clunk. Cannonball again: "Already loaded". In creative no items are used.
3. **Aiming.** Sneak-use with an empty hand on the upper half four times: +5, +10, +15, +20, then it stays at +20. On
   the lower half down to −5. The elevation visibly changes the arc.
4. **Firing on land.** A loud shot, a flash, a big smoke puff; the ball flies about 3 blocks per tick and drops slowly;
   a plank wall loses one plank with particles and a splinter sound; stone stays; a zombie dies in one hit (20);
   powder within 5 s: "The barrel is still hot: X s".
5. **Firing into water.** Splash particles and sound; the ball disappears within a few blocks.
6. **From a moving ship.** A cannon on deck facing sideways, sailing: the ball leaves the muzzle (not from behind the
   ship or from far away), fired forward or backward it keeps the ship's speed and lands where you aimed, each shot
   nudges the ship away from the barrel. Say whether `recoil_impulse = 8` is noticeable but not silly.
7. **Hull damage.** At another ship's hull above the waterline: a plank breaks, no water. Just at or below the
   waterline (or a ball landing just short of the hull): a plank breaks, the hold starts flooding, the hull patch
   closes it and the pump drains it. The hit ship gets a small push.
8. **Toggles.** `combat.cannon_block_damage = false`: balls stop at planks without breaking them. `cannons.enabled =
   false`: "Cannons are disabled on this server". `cannons.recoil_impulse = 0`: no push. `cannons.blocks_per_hit = 3`:
   three planks deep.
9. **Crew.** Assign a crew member to a cannon with the whistle, load it yourself, and give the fire order with
   `/pirates crew order fire` (there is no whistle entry yet). Expected: it fires after a short fuse.
10. **Reload of the world.** A loaded cannon stays loaded; a cannon on a ship keeps its aim after leaving and rejoining.
11. **Sounds.** The shot is heard from a distance (volume 4), subtitle "Cannon fires".

## Also worth a look
- Is 5 s reload right? Is one plank per hit enough, or should a ball at close range go deeper?
- The ball is a large cannonball sprite in flight; say if it should be a sphere.
