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
9. **Crew.** Assign crew to two cannons with the whistle and load only one. The whistle wheel has six sectors with
   "Fire!" on the cannon icon. Choose it: the crew at the loaded gun says "Aye, firing!" and it fires after about half
   a second, the other says "The gun is not loaded, captain!", the action bar reads "Order: fire the cannons (1 crew
   carry it out)", and the whistle's last-order marker stays on the sail order. `/pirates crew order fire` behaves the
   same; aimed at a winch crew it answers "I can't fire the cannons from this station, captain!".
10. **Reload of the world.** A loaded cannon stays loaded; a cannon on a ship keeps its aim after leaving and rejoining.
11. **Sounds.** The shot is heard from a distance (volume 4), subtitle "Cannon fires".

## Also worth a look
- Is 5 s reload right? Is one plank per hit enough, or should a ball at close range go deeper?
- The ball is a large cannonball sprite in flight; say if it should be a sphere.


## P2: two-block cannon and swivel gun (placeholder models until F7g)
1. **Place a cannon** on land facing east: the clicked block holds the carriage front, the rear is one block west, the
   barrel overhangs one block east. With the rear spot blocked, or air under the rear: refused, item kept.
2. **Break the rear** in survival: both halves vanish, one cannon drops; the same from the front. In creative: both
   vanish, no drop.
3. **Use from the rear:** powder, then a ball, on the rear half; sneak-click to aim; fire with an empty hand on the
   rear. The shot leaves about a block ahead of the front block at about 0.9 blocks height. You can't walk over the
   gun; the outline is about 1.25 blocks tall.
4. **On a ship:** place on deck, assemble: both halves move, the model draws across both blocks without gaps or
   flicker at section borders, it fires from the world muzzle while sailing. Disassemble turned 90°: the halves stay
   together and keep facing the right way. Assign crew by clicking the rear: the crew sits beside the gun, not on it,
   and a second crew member is refused.
5. **Swivel on a fence:** hold right-click with an empty hand: the gun turns smoothly with your view (yaw and pitch,
   clamped at −30/+45). Let go while loaded: it fires where it points, the shot is a cannonball sprite, clearly
   shorter range than the cannon, planks intact. Another player sees it turn. Break the fence: the gun drops.
6. **Swivel on a ship:** with the ship turned, the gun still points where you look. While aiming, nothing else gets
   used (doors, items).
7. **Crew:** assign a crew member to a loaded swivel with the whistle, blow "Fire!": it fires after about half a
   second.
8. **Config:** `cannons.swivel.ammo = LEAD_SHOT` takes lead shot and draws it; `blocks_per_hit = 1` breaks planks;
   `enabled = false` makes the gun inert with a message.
9. **Visuals:** the placeholders and the swivel item icon in the GUI and in hand are stand-ins; judge sizes only.

Result (third playtest, 2026-10-07): the large cannon's size is right (two-block carriage, barrel a block ahead); F7g keeps the P2 geometry.

## Q2: world rules, drops and glancing hits
1. `/gamerule mobGriefing false`, fire at a hull: nothing breaks and the ball stops; back to true: the hull breaks.
2. Load a cannon (powder and ball) in survival and break the rear half: one cannon, one gunpowder, one cannonball
   drop; in creative nothing drops.
3. Load a swivel gun and break it, or its fence: swivel, gunpowder and the loaded shot drop.
4. Shoot a hull: the plank items appear beside the hole in the water or air, not inside the ship and not far away;
   holes below the waterline still flood.
5. Fire along a hull side at a shallow angle: the ball pings off and flies on, the hull is intact, and the ball
   looks smooth on the client (say if it sticks at the face for a moment). At about 45° with `blocks_per_hit` 3 two
   blocks break; straight on three.
6. Dedicated server with spawn protection and an op set: a non-op's shots break nothing near spawn, a moored ship
   inside the radius included.
