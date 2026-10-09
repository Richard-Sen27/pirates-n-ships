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

## F7g: the real cannon and swivel gun models
1. **Cannon facing N, E, S and W** on flat land: no gap or seam where the master and rear blocks meet, the barrel
   reaching about 15 px over the block in front, wheels on the ground, nothing cut off, the outline roughly matching
   the model (0..20 px on the master, 0..18 on the rear).
2. **Loading:** powder adds the rammer leaning against the right cheek; the ball shows in the muzzle with a black
   ring; firing goes back to the empty model.
3. **Lighting:** from both sides the rear half (drawn by the master) is lit correctly and does not go dark next to
   solid blocks; from far away or at section borders, say if the barrel or the rear carriage vanishes.
4. **Swivel on a fence:** turn 360° (the yoke turns about the post centre), elevate fully up and down (the trunnions
   stay in the yoke bosses, the barrel never cuts the crossbar), the loaded ball shows at the muzzle.
5. **Both items** in the GUI, hotbar, hand (first and third person), on the ground and in an item frame.


## C9: crew loading
An assembled ship with a manned cannon and a chest within 4 blocks holding 5 gunpowder and 5 cannonballs.
1. **Load!** (gunpowder icon next to Fire! on the whistle): "Aye, loading!", the crew works about 4 s, then the loaded model, the powder and iron-door sounds, the chest down by one of each.
2. **Fire! and auto-reload:** the shot leaves, the crew works again, after 5 s the gun is loaded and the chest down one more; Fire! again shoots; with the chest empty the crew stays idle and Load! says "No powder and shot within reach, captain!".
3. **Fire! during the reload:** it finishes the load and fires, no "not loaded".
4. **Swivel:** loads in about 2 s; with `cannons.swivel.ammo_count` 3 it takes 3 balls and a broken gun returns 3.
5. **Refusals:** a loaded gun ("already loaded"); a chest beyond 4 blocks, on the dock, or with only powder; two cargo crates (powder and balls) work.
6. **Toggles:** `cannons.crew.enabled = false` refuses Load! and skips the reload; `auto_reload = false` leaves the gun empty; `supply_range` changes the reach. Hand-loading still works. The whistle wheel's 7 sectors still read well.

## WS4a: NPC gunnery ("Fire at will")
Two assembled ships about 25 blocks apart on open water. Yours has a cannon facing the other ship (broadside or bow),
a crew member at it and a chest within 4 blocks with 5 gunpowder and 5 cannonballs. The other ship (the dummy) has
no crew and a flagpole flying the Jolly Roger (by hand, or `/pirates flag set <pole pos> jolly_roger`; `/pirates flag strike <pos>` / `raise <pos>` for step 4).
Your own ship flies nothing or a merchant flag.
1. **Fire at will:** whistle, the new "Fire at will" entry (fire-charge icon, 8 sectors now). The crew answers
   "Aye, firing at will!", loads, then every second or so the barrel visibly steps up or down (the wood-knock sound)
   until it is aimed, and fires by itself. Most shots should hit the dummy's hull at 25 blocks; say roughly how many of
   five hit. It keeps firing and reloading until the chest is empty.
2. **The arc:** turn your ship (or the dummy) until the dummy is more than about 15° off the barrel: the crew stops
   firing and the barrel stays; turn back and it resumes. A cannon facing the bow never fires at a ship abeam.
3. **Moving target:** sail the dummy across the barrel at a few blocks per second (or sail your own ship past it):
   the crew still hits now and then (it leads the target). Say whether shots fall short or behind.
4. **Striking the colours:** strike the dummy's flag at its pole: the crew stops within one aim interval (1 s) and
   never fires again while it is struck; raise it again and the crew resumes. Strike your own ship's flag instead:
   your crew holds fire.
5. **Not hostile:** set the dummy to a merchant or navy flag: an unflagged or merchant gunner ignores it. Hoist the
   navy flag on your own ship: it now fires at the Jolly Roger only; hoist the Jolly Roger on yours: it fires at a
   navy or merchant dummy (and you commit the crimes for hitting a merchant, as for any crew shot).
6. **Release crew** on the whistle: the crew leaves the gun and gunnery stops; manning the gun again with "Load!"
   does not restart it until "Fire at will" is given again.
7. **Damage multiplier on your own hull:** swap roles: put a crew and supply on the dummy with its cannon facing your
   ship, fly the Jolly Roger on yours, and `/pirates crew order fire_at_will` standing on the dummy. About half of the
   hits on your hull break a plank (`cannons.npc.npc_block_damage_multiplier` 0.5); set it to 0 and hits only push,
   set it to 1 and every hit breaks one. A plain "Fire!" from your own crew always breaks the full amount.
8. **Toggles:** `cannons.npc.enabled = false`: "Fire at will" is refused ("This gun won't fire, captain!") and nothing
   fires by itself; `arc_degrees`, `engage_range` (the dummy beyond it is ignored), `aim_interval_ticks` (slower
   barrel steps) and `fire_interval_ticks` change what their names say.

## CAN2: the barrel tilts with the elevation
The block model is now only the carriage; the barrel and the quoin (the wedge under the breech) are drawn by a block
entity renderer at the gun's elevation step. Reference: `art/renders/cannon_tilt.png` (0°, 10°, 20°). Place a cannon
on land facing east, stand beside it.
1. **Each step:** sneak-use the upper half six times, then the lower half six times. With the default 6 steps the
   barrel shows −5°, 0°, 5°, 10°, 15°, 20°, matching the action-bar message: the muzzle rises, the breech sinks between
   the cheeks, and the quoin under the breech thins and slides back toward the rear until at 20° the breech lies on
   the low stool bed. At −5° the muzzle dips and the quoin grows under the raised breech. Look closely for the barrel
   or the quoin cutting into the cheeks, the bed or the cap squares at any step; say which step and where.
2. **Smooth tilt:** each step swings over about 6 ticks (0.3 s) instead of jumping; stepping twice quickly carries on
   from where the barrel is, without a jerk.
3. **Facing and light:** place guns facing north, south and west too: the barrel turns with the carriage and always
   points out the muzzle side; under a roof and at night the barrel is as dark as the carriage (no glowing barrel).
4. **Loaded looks:** gunpowder in: the rammer leans on the carriage and a thin light priming quill stands in the vent;
   a cannonball in: the ball sits in the muzzle (and tilts with it); fire: back to the bare barrel. All three looks at
   0° and at 20°.
5. **Item:** the cannon item in the hotbar, in hand and in an item frame still shows the whole gun (barrel level).
6. **Relog and distance:** aim a gun to 15°, leave the world and come back (or walk 10 chunks away and back): the
   barrel shows 15° at once, without swinging up. The raised muzzle does not vanish when only its tip is on screen
   (look past the gun so the carriage is just off screen).
7. **NPC aiming at sea:** on a sailing ship with a crewed cannon and "Fire at will" (WS4a steps above), the barrel
   steps visibly and smoothly while the crew aims, rocks with the ship, and the ball leaves along the drawn barrel.
   A second player watching from another ship sees the same steps.
8. **Toggles (client config):** `cannon_visuals.enabled = false`: every barrel is drawn level (aiming still works,
   only the look stays level); `cannon_visuals.tilt_ticks = 0`: steps jump at once; 20: slow swings. Server
   `cannons.max_elevation_degrees = 30`: the barrel stops tilting at 20° (the model's limit) while the shots still
   go to 30°.
