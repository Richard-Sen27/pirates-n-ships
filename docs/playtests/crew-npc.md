# Playtest: the crew member on the GeckoLib rig (work package M1)

The rig file, the animation set and the working flag are covered by 11 JUnit tests and 2 GameTests. Rotation signs,
the hand-item transform and the look of the poses were derived on paper and have never been seen in game.

Setup: `./gradlew :neoforge:runClient`, a creative world, a crew member (`/summon pirates_n_ships:crew_member` or the
spawn egg), a ship with a sail winch and a bilge pump, the captain's whistle. Please send `latest.log` if anything
differs.

## Steps
1. **Idle.** Spawn one. Expected: a sailor skin (striped shirt, red bandana); the arms sway slowly; the head follows
   you; no missing-texture or missing-model errors in the log.
2. **Walking.** Let it stroll. Expected: arms and legs swing in opposite phase and the legs move forward and backward.
   If limbs bend sideways or backwards, the rotation signs are flipped: say which limb.
3. **Winch.** Assign it to the sail winch and order a hoist with the whistle. Expected: it stands at the winch (not
   sitting), switches to a hand-over-hand hauling pose leaning forward for the duration of the order, then blends
   back to idle.
4. **Pump.** The same at the bilge pump with "Man the pumps": the work pose while pumping.
5. **Seated.** Put it in a boat (lead it in or `/ride`). Expected: a sitting pose with legs forward at the vanilla
   rider height, no extra offset.
6. **Holding items.** `/item replace entity @e[type=pirates_n_ships:crew_member,limit=1] weapon.mainhand with
   minecraft:iron_sword`, then `weapon.offhand` with a torch. Expected: the sword in the right hand pointing forward
   like a player's, the torch in the left hand, both following the arms while walking and working. Report floating,
   offset or rotated items.
7. **Moving ship.** A crew member at a station while the ship sails and heels. Expected: it stays at the station,
   upright and smoothly placed, with no new jitter. Also watch an unassigned crew member standing on deck: if it runs
   in place because the deck moves, say so.
8. **Hurt and death.** The hurt flash and the death animation still show.


## M2: the Blockbench sailor (replaces the look checks above)
1. **Look.** A tanned sailor with a moustache, a navy and white striped shirt, a red bandana with white dots and a knot
   and tails at the back, a mustard neckerchief, a brown belt with a brass buckle, a knife handle at the back left hip,
   sleeves rolled to the elbow, dark canvas trousers rolled below the knee, bare feet with toes, a gold earring on the
   left ear. No z-fighting at the belt, cuffs or hems; no missing-model or missing-texture errors in the log.
2. **Idle.** A slow chest swell, a slight sideways weight shift, arms swaying; the head still follows you. Say whether
   the eased motion looks smooth or stutters.
3. **Walk.** Arms opposite to the legs and a small dip of the upper body at each stride. If a limb swings the wrong
   way, name it.
4. **Winch and pump.** Hand over hand: one hand reaches high in front while the other pulls down to the belt, the
   hands meet in front of the chest, the forward lean deepens with each pull. Does it read as hauling at the winch and
   as pumping at the pump? Do the hands pass through the station block?
5. **Sit (boat).** Legs forward and slightly splayed, hands on the thighs, leaning back slightly.
6. **Items in hand.** A sword in the main hand and a torch in the off hand follow the hands in every pose; in the work
   pose the sword swings with the hauling arm.
