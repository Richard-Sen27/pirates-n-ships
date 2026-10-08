# Playtest: sea chest (work package S1)

Contents in every state, the worn restrictions, the floating chest's physics and the toggle are covered by 21 JUnit
tests and 11 GameTests with measured physics. What only a client shows: the feel of the sink pull, the sprint block,
the bobbing, and dedicated-server movement.

Setup: `./gradlew :neoforge:runClient`, survival, a dock at the sea, a ship. The block is a placeholder cube until
its Blockbench pass.

## Steps
1. **Contents in every state.** Fill the chest and break it with an axe: one item drops and its tooltip lists the
   items. Place it again: contents are back. Pick-block in creative gives a copy with contents; creative-break drops
   one item with contents. Put it on, take it off, place it: intact. Rename it in an anvil: the name is the screen
   title in every state.
2. **Nesting.** A sea chest cannot go into a sea chest (shift-click or drag); a shulker box can.
3. **Wearing.** Use in the air or drop it into the chest armour slot. Space does not jump, walking is visibly slower,
   Ctrl or double-tap W does not sprint (at most a one-tick flicker, no FOV pumping; try toggle-sprint too). In deep
   water you sink without input, cannot sprint-swim, and holding space only keeps you in place; stay under to confirm
   drowning. Say whether the pull feels right. Take it off: all normal again.
4. **Floating chest.** Right-click the seabed through water, or the water surface from a dock: a chest floats at the
   waterline, rocking gently; placed deep it rises. `/pirates wind set 270 6`: it drifts east at about a third of the
   wind speed; a current pushes it too. Use opens the 6-row screen; sneak-use puts it in your inventory with
   contents; a hit drops it as an item. Relog: still there with contents. Say if its front faces the wrong way.
5. **On a ship.** Placing on an assembled deck gives a block, not a floating chest; open it while sailing.
6. **Toggle.** `sea_chest.enabled = false`: the item cannot be worn, placing on water places a block, a worn chest
   stops restricting within a tick.


## S1-art: the look
1. Place the chest facing each direction: hasp and lock plate face you, rope handles left and right, no missing or dark faces, also with blocks beside and on top of it.
2. The outline hugs the chest (about 14 wide, 12 deep, 10 high); walking against it and standing on it (10 px high) behave accordingly.
3. The floating chest bobs and rocks with the new model, centred in its shadow, front toward the player who launched it.
4. The item in the GUI (three-quarter view, front on the right, a bit smaller than a block), in hand (first and third person, not clipping the arm), on the ground (small, just above it) and in an item frame (hasp facing out, centred).

## SC2: paddling
Covered headless by 9 JUnit tests (`PaddleRulesTest`) and 7 GameTests (`PaddleGameTests`): mounting, 60 ticks of
forward input (about 3.8 blocks), a left turn of 60 degrees per second, no headway without the paddle in hand,
sneak to dismount, refusals (worn, placed, beached, occupied), hunger and the toggle. What only a client shows: the
feel, the rider's seat and camera, the sync of a server-moved vehicle, and cold water on the rider.

Setup: survival, a floating sea chest (step 4 above), a paddle (two planks and a stick, diagonal: ` P` / ` P` / `S `).
The paddle is a placeholder model (a wooden shovel) until its Blockbench pass.

1. **Mounting.** Swim or stand at the shore next to the chest, paddle in hand, use it on the chest: you sit on its
   lid, facing where you looked; the chest turns to that heading. A second player using a paddle on it gets
   "Someone is sitting on this sea chest". A paddle on a placed chest block or on a player wearing one does nothing;
   on a chest washed up on dry land you get "The sea chest must float to be paddled".
2. **Seat and camera.** The sitting pose sits on the lid (not inside the chest, not floating above it); legs about at
   the waterline. Say if the seat should be lower or higher. Looking around is limited to about 105 degrees either
   side of the heading, like a boat.
3. **Paddling.** W: the chest moves the way its hasp faces (front first), building up to about 1.5 blocks per second
   (a slow walk) within half a second; S backs at half speed; A/D turn left/right at about 60 degrees per second, also
   on the spot. Your view turns with the chest. A paddle stroke sound and an arm swing about once a second while a
   key is held. Say whether the speed and turn rate feel right, and whether the motion stutters (the chest is moved
   by the server and interpolated, unlike a vanilla boat; test on a dedicated server and with some ping too).
4. **Paddle in hand.** Switch to another hotbar slot while riding: the keys do nothing, the chest only drifts. Paddle
   in the off hand works too.
5. **Wind and buoyancy.** `/pirates wind set 270 6`: while paddling south the chest also drifts east; with no keys it
   drifts like an empty one. It keeps floating at the same height with you on it.
6. **Hunger.** Paddle for a few minutes with full food: the hunger bar goes down about as fast as swimming the same
   distance (F3 or the saturation mod of your choice; 1.35 exhaustion a minute of continuous strokes, a third of a
   drumstick).
7. **Cold water.** In a frozen or cold ocean, sit on the chest: the frost overlay builds up as if you were swimming
   (about 7 s to full), and freezing damage follows; leather armour or rum prevents it. In a boat it does not.
8. **Dismounting.** Sneak: you get off into the water next to the chest. The rider cannot open the chest (use shows
   "Get off first (sneak) to open the sea chest") or knock it loose by hitting it; after getting off, use opens it and
   sneak-use picks it up as before. Nobody can pick it up while you sit on it, but another player's hit still knocks
   it loose (and you fall in).
9. **Relog while seated.** Log out on the chest and back in: you are still seated (vanilla's root vehicle saving).
10. **Toggle.** `sea_chest.paddle_enabled = false`: using a paddle on the chest opens it instead; a rider seated
   before the switch only drifts.
