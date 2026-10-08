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

## CR1: job board (orders instead of assignments)
Covered by 9 JUnit tests (`JobBoardRulesTest`) and 6 GameTests (`station/jobs/JobBoardGameTests`). What the tests
cannot show: how claiming looks and reads in game. Crew are seated at the station at once (no walking yet).

Setup: an assembled ship with a mast and at least two sails, two sail winches and a bilge pump on deck, standing on
the deck yourself with the captain's whistle. Default config (`crew_stations.job_board.enabled = true`,
`claim_interval_ticks = 20`, `max_claim_distance = 0`).

1. **Hoist without assignments.** `/pirates crew spawn` three times on the deck, assign nobody. Whistle menu: "Hoist
   sails". Expected: the action bar reads "Order: hoist the sails (0 crew carry it out, 2 stations open for free
   hands)"; within about one second two of the three crew appear at the two winches (the nearer crew member at each),
   each says "Aye, hoisting the sails!", and the sails go up after the work time (80 ticks from furled with the
   default trim step). The third crew member keeps strolling. Report a crew member that ends up at a winch on another
   ship, or one that stood on the dock and still claimed.
2. **They stay.** After the hoist the two crew stay at their winches. "Furl sails": they furl at once (they man the
   winches now), no new line about open jobs.
3. **Pinned at the pump.** "Release crew" (all three walk free; the board is empty). Then select one crew member with
   the whistle and use it on the pump: it is pinned there. "Hoist sails": the two others take the winches, the pinned
   one stays at the pump. "Man the pumps" with water in the hold: the pinned one pumps as before.
4. **No free hands.** "Release crew", then `/kill @e[type=pirates_n_ships:crew_member]`. "Hoist sails": the action bar
   reads "No free hands to hoist the sails" once, and it does not come back on its own. Spawn a crew member on the
   deck: within about a second it takes a winch and hoists (the open job waited for it).
5. **Release clears the board.** With no crew on board, "Hoist sails", then "Release crew", then spawn a crew member:
   it stays free, the sails stay furled.
6. **Command.** Standing on the deck: `/pirates crew order hoist`. Expected: the usual "Order ..." feedback plus "2 open
   jobs posted: hoist the sails" when nobody mans the winches, and free crew take them as in step 1.
7. **Config off.** Set `crew_stations.job_board.enabled = false` (server config, or `/config` screen), repeat step 1:
   nothing happens at unmanned winches, the action bar reads "Order: hoist the sails (0 crew carry it out)", the free
   crew keep strolling. Assigning by hand with the whistle works as before.
8. **Save and reload.** A board-assigned crew member at a winch and a pinned one at the pump, save and quit, reload:
   both are at their stations. Furl, then "Release" only the winch crew member with the whistle (use it on that crew
   member) and order "Hoist sails": the open winch job never takes the pinned one from the pump (open jobs themselves
   are not saved, so post the order after the reload).

## Q5: "0 of 0 crew" and "no sails" next to a big sail (fix check)
Covered by 11 GameTests (`station/winch/WinchOrderGameTests`: the reported command sequence right after assembly, on
a floating ship, with the rig built after assembly, with a sailing state that missed the yards, free crew through the
command and through the whistle, and every hint below) and 5 JUnit tests (`YardDiagnosisTest`). Headless, the
reported sequence works: the cause in your world is not reproduced yet, so first collect the state.

0. **First, on the ship from the report** (before changing anything): stand on its deck and run
   `/pirates ship rigging`. Send a screenshot of the whole output and the `latest.log`. Expected lines: "Rigging of
   <name>: N sails (Y yards, T triangular sails)", "Sailing state: N sails, M set", one line per yard ("Yard at x y z
   (9 blocks along x): heads a sail 6 blocks deep, area 54, furled" / "foot of the sail above" / the reason it heads
   none), and "Crew aboard: A at stations, F free on deck". If the log has a warning "Sailing state of ship ... was
   stale", the old code had missed the yards: that warning is the root cause and the winch now repairs it by itself.
1. **The reported sequence.** On the deck: `/pirates crew spawn`, then `/pirates crew order hoist`. Expected: "Order
   hoist the sails: 0 of 0 crew carry it out" (nobody mans a station yet) followed by "1 open jobs posted: hoist the
   sails"; within about a second the crew member sits at the winch, says "Aye, hoisting the sails!", and the sails go
   up. The same with the whistle's "Hoist sails": "Order: hoist the sails (0 crew carry it out, 1 stations open for
   free hands)".
2. **Assigned by hand.** Select the crew member with the whistle and use the whistle on the winch, then "Hoist sails".
   Expected: "Aye, hoisting the sails!" and the sails go up. If it still says "This ship has no sails, captain: ...",
   the text after the colon names what the rule does not accept (step 4); report it with the `/pirates ship rigging`
   output.
3. **Hints instead of "0 of 0".** Each case shows one line saying what is missing:
   - standing ashore: `/pirates crew order hoist` says "No ship under you: stand on the deck of an assembled ship to
     give orders";
   - on a ship without a bilge pump, whistle "Man the pumps": "No station on this ship can pump the bilge: place one on
     the deck first";
   - sails already furled, nobody aboard, "Furl sails": "Nothing to do: every station that can furl the sails is done
     already";
   - with `crew_stations.job_board.enabled = false` and nobody at the winch, "Hoist sails": "Nobody to hoist the sails:
     no crew at a station that takes it and none free on this deck ...".
4. **"No sails" with the reason.** Put a plank into the mast between the two yards, then "Hoist sails" with nobody
   aboard. Expected: "No sails on this ship: the yard at x y z has Oak Planks in its mast column 3 blocks down; only
   air, logs and wooden fences may be between two yards". Other reasons to try: lower yard 9+ blocks below ("has no
   second yard 2 to 8 blocks straight below its middle block"), a 17-long yard ("is 17 blocks long, longer than 15"),
   lower yard shifted sideways ("not centered on the same mast column"). The crew member at the winch answers with the
   same reason.

## HM1: hammocks and morale
Covered by 24 JUnit tests (`HammockRulesTest`, `ShipBunksTest`, `RestRulesTest`, `MoraleRulesTest`) and 8 GameTests
(`crew/hammock/HammockGameTests`). What the tests cannot show: how the hammock looks (a datagen placeholder until ART1d:
a thin canvas slab with two rope ties at each outer end), how a sleeping crew member looks in it on a moving ship, and
how the lines read. Sleeping crew play the **sit** pose: the rig has no lying animation yet.

Setup: an assembled ship with a hold, standing on it with the captain's whistle. Default config (`crew.morale.enabled =
true`, `start = 70`, `hammock_rest_per_night = 5`, `no_hammock_per_night = 10`, `crew.max_crew_multiplier = 1.0`).
Give yourself hammocks (`/give @s pirates_n_ships:hammock 4`; recipe: 2 string over 3 wool) and fences.

1. **Hanging.** Below deck, put two fence posts at the same height with two blocks of space between them and use a hammock on the block next
   to one post, looking toward the other: it hangs between them (foot where you clicked, head one block further). Try
   it against the hull side instead of a post (a solid wall counts) and against a log. Expected: placed. Try it with
   nothing at the far end, or with a slab or chest there. Expected: nothing placed, action bar "A hammock hangs between
   two supports at the same height: …". Right-click a hammock: "Hammocks are for the crew: they turn in here at night"
   (players cannot sleep in it). Report how the placeholder looks from all four facings (canvas centred, ropes at the
   ends toward the posts).
2. **Breaking.** Break one post: the hammock comes down and drops exactly one hammock. Break the head half of another,
   then the foot half of a third: one item each. In creative: nothing drops.
3. **Bunk count.** Hang two hammocks in the hold, assemble the ship (or hang them on an assembled ship), spawn three
   crew on deck (`/pirates crew spawn`). Use the whistle on one: chat shows "Jack: morale 70, off duty · crew 3 / bunks
   2". `/pirates crew info` on deck: "This ship: crew 3 / bunks 2 (2 hammocks)" and one line per crew member. Nothing
   stops a fourth crew member yet (the limit is informational until hiring).
4. **Turning in.** Put one crew member at a winch with the whistle. `/time set 13000`. Expected within a second: the two
   free crew members nearest the hammocks lie in them (seated pose on the canvas), the third free one keeps standing,
   the one at the winch stays there. Sail the ship a little: the sleepers stay in their hammocks and move with the
   ship. Report sleepers floating above or sinking into the canvas, facing across the hammock, or jitter while sailing.
   `/pirates crew info` shows "in a hammock" for the sleepers.
5. **An order at night.** Whistle "Hoist sails" with an unmanned second winch: a sleeper gets up at once and goes to the
   winch (it is on duty for the night: no morale change at dawn).
6. **Dawn.** `/time set 23500` (or sleep in a bed). Expected: the sleepers get up and stand on deck where the hammock is
   (report if they drop through the deck or end up in the hold walls); the one without a hammock says "No hammock for
   me… another night on the bare planks." in chat; `/pirates crew info`: sleepers morale 75, the one without 60, the
   one at the winch 70.
7. **Save and reload at night.** `/time set 13000`, wait until they lie in their hammocks, save and quit, reload: they
   are still in their hammocks, and at dawn they get up with +5. Reload during the day after a night: nobody is stuck
   in a hammock.
8. **Config off.** Set `crew.morale.enabled = false`, `/time set 13000`: nobody turns in; `/pirates crew info` shows
   morale 70 for everyone, whatever it was before; at dawn nothing changes and nobody grumbles.

### ART1d: the hammock model and the lying pose
The placeholder slab is replaced by the Blockbench hammock (`art/renders/hammock.png`, `hammock_item.png`) and sleepers
play the rig's new `sleep` animation (`art/renders/anim_crew_sleep.png`). Since HM2 `CrewMember#pose` passes the
resting flag, so sleepers play **sleep**, and they lie along the hammock (steps 13 to 15).

9. **The model, four facings.** Hang a hammock between two fence posts facing north, east, south and west (in the
   world and below deck on a ship). Expected per facing: white wool canvas 12 px wide with rolled side hems, flat in
   the middle at about a quarter block, sloping up at 22.5 degrees to the ends, a rolled end hem round a spruce
   spreader bar at each end, four strings (two from the bar ends, two from inside) meeting in a knot about 10 px (0.6
   block) up, and one rope from the knot into the post. Check: **no visible seam** where the two halves meet (flat
   canvas, hems in line); the rope ends **inside** the fence post, and into a wall post or the hull side (solid
   block) without a gap; nothing flickers. The block outline/collision is the canvas box (3 to 7 px high).
10. **The item.** In the hotbar: a rolled canvas with three rope windings, a knot and loop at the upper right, on the
    diagonal like a tool; in hand held like a tool (roll forward from the fist); on the ground and in an item frame.
11. **Lying.** At night (`/time set 13000`) a crew member turns in. Expected: lying on its back along the hammock,
    hips in the middle where the halves meet, upper body and straight legs raised slightly with the canvas, head
    raised a little (not looking around), arms folded over the belly; the chest breathes slowly (4 s). Report: the body
    floating above or sinking more than a pixel or two into the canvas, the head or feet poking through the end hems
    or ropes, or the body lying across the hammock (record the facing). Check it next to the hull side: hammock and
    sleeper do not clip into the hull planks. On a sailing ship: no jitter between body and canvas.
12. **Waking.** An order or dawn: the crew member stands up and walks off with the normal poses (no leftover tilt).

**HM2: lying along the hammock.** Covered headlessly by `crew/hammock/SleepAxisTest` (the yaw maths for all four facings,
ship turns, the render frame) and two GameTests in `HammockGameTests` (in the world and on a ship turned 90 degrees, 40
ticks with a player beside it). The sleeper faces the **foot** half (the block you clicked when hanging it); its head
lies over the **head** half (the block toward which you looked). Crew only turn in on ships, so check below deck.
13. **Along the hammock, head on the head half.** Hang four hammocks in the hold, one per facing (remember where you
    clicked: that is the foot), spawn four crew, `/time set 13000`. Expected for every facing: the body lies along the
    canvas (never across it), the head over the half away from the clicked block, the feet toward the clicked block,
    hips over the seam, nothing poking through the end hems. Report any facing that is reversed (head over the foot
    half) or crossed.
14. **Turning ship.** With the sleepers in their hammocks, sail and turn the ship through a full circle (and hold a
    turn for a while). Expected: the sleepers turn with the hull and stay along their hammocks the whole time; no
    sudden half or full spin of the body when the ship's heading passes south (yaw ±180), no lag of more than a few
    degrees behind the canvas, no jitter between body and canvas while the ship rolls.
15. **No twisting when you walk by.** Walk round a sleeper, stand close to its head and its feet, and crouch next to it.
    Expected: neither head nor body turns toward you or looks around; the head keeps its slight chin-to-chest tilt.
    Then give an order that gets it up (whistle "Hoist sails" with an unmanned winch) or `/time set 23500`: once up it
    looks at you and around again as usual.

## CR2: crew upkeep (wages, desertion, mutiny)
Covered by JUnit tests (`crew/upkeep/WageRulesTest`, `UpkeepDayTest`) and 8 GameTests (`crew/upkeep/UpkeepGameTests`).
What the tests cannot show: how the lines read in a real game (action bar and chat), the deserter and the mutineers in
the world, and the pay coming out of real chests and cargo crates.

Every dawn (the night ends: `/time set 13000`, wait a second, then `/time set 23500`, or sleep in a bed) runs one **day
of upkeep** for every loaded ship with crew aboard, before the hammock rule: provisions (`pantry.md`, CR2), wages, then
mutiny or desertion. Note for the HM1 checks above: with CR2 a crew without food, water and pay loses much more at dawn
than the hammock rule; to check HM1's numbers alone set `provisions.consumption_enabled = false` and
`crew.wages.enabled = false`.

Setup: an assembled ship **you assembled** (you are its owner), standing on its deck, two crew from `/pirates crew spawn`,
default config (`crew.wages.per_day = 2`, `unpaid_per_day = 8`, `paid_per_day = 1`, `crew.desertion.desert_below = 20`,
`desert_days = 2`, `crew.mutiny.enabled = false`). Give yourself doubloons (`/give @s pirates_n_ships:doubloon 64`) and a
chest.

1. **The pay chest.** A chest on the ship with 10 doubloons, a pantry and water barrel with food and water. Force a dawn.
   Expected: action bar "Paid 2 crew, 4 doubloons"; the chest holds 6; `/pirates crew info` shows "Last pay: 2 paid, 0
   unpaid, 4 doubloons", the supplies line, "Work speed 100%", and both crew one morale point up (plus the hammock
   rule).
2. **Nearest the helm first.** A second chest with 10 doubloons right next to the helm, the first one far from it. Force a
   dawn. Expected: the 4 doubloons come out of the chest next to the helm. Put doubloons in a cargo crate instead of a
   chest: they pay too. Doubloons in the pantry never pay.
3. **Short pay.** 3 doubloons in total. Force a dawn. Expected: action bar "Could not pay 1 crew"; one crew member says
   "No pay again? A sailor can't live on promises."; the whistle line of that member reads "…, off duty, unpaid"; 1
   doubloon stays (a single coin is not a wage). Unpaid wages are not carried over to the next day.
4. **A deserter walking off.** Take the chest, pantry and barrel away (no coins, no food, no water). Force dawns and watch
   `/pirates crew info`: each dawn costs 35 (hunger and thirst) + 8 (unpaid) morale, plus 10 without a hammock. Expected:
   once a member has been below 20 at two dawns in a row it says "I've had enough of this ship. I'm off.", you get
   "Jack has deserted" in chat, and a neutral sailor stands where it stood and wanders off. It is gone from
   `/pirates crew info`, from its station and from its hammock. Report how it looks (a plain sailor, not the crew look)
   and whether it walks off the ship or stays on deck.
5. **Mutiny with the toggle on.** `crew.mutiny.enabled = true`, two new crew, no supplies, no coins. Force dawns.
   Expected: nobody deserts while the average morale is below 15 (they plot); at the third such dawn they shout "The
   ship is ours now!", you get "Mutiny aboard <ship name>!" (or "your ship") in chat, and two hostile pirates stand where
   the crew stood and attack you. The ship has no owner any more but keeps its name and flag: report what that changes
   for you at the helm and with the whistle. Set the toggle back afterwards.
6. **Wages off.** `crew.wages.enabled = false`: a dawn takes no coins, shows no pay line, nobody gains or loses morale for
   pay; `/pirates crew info` says "Last pay: wages are off".
7. **Save and reload.** After step 3, save and quit, reload: the unpaid member's whistle line still says "unpaid", and a
   member one low dawn away from deserting still deserts at the next low dawn.

Report: screenshots of the action bar after steps 1 and 3, the chat at steps 4 and 5, `/pirates crew info` after each
step, and `logs/latest.log` if anything errors.

## ART7: crew station animations (helmsman, gun crew, hauling)

Setup: a small ship with a helm (put the deck free on the helm's wheel side, the side the player stands on to steer, so
the helmsman's spot is there), a cannon on deck with a chest of gunpowder and cannonballs within reach, two crew members,
a captain's whistle; F5 for third person. Compare with `art/renders/crew_poses_art7.png` and `anim_haul.png`.

1. **Helmsman holds the wheel.** Assign a crew member to the helm (whistle or `/pirates crew assign`). Expected: he turns
   to face the wheel and stops looking around; both hands rest on the rim at about 2 and 10 o'clock, leaning slightly in
   (`helm_hold`, slow breathing). Report if the hands sink into the rim, float more than a hand's width off it, or if
   his feet clip into the helm. If the free side of your helm is not the wheel side, he stands beside the helm, faces
   it and reaches past the wheel: screenshot that too.
2. **Helmsman turns the wheel.** Hoist the sails (`/pirates crew order hoist`) and give a course to a point off the
   starboard bow (`/pirates crew order course <x> <z>`, see `crew-helm.md`), later one to port. Expected: whenever the wheel jumps to a new angle, he works it hand over
   hand for one second clockwise (to starboard, `helm_turn_right`) or counter-clockwise (to port, `helm_turn_left`),
   then holds it again. Check the direction against the wheel's own turn.
3. **Turning ship.** While the ship turns, he keeps facing the wheel (his yaw follows the ship). Report any spin when
   the ship's heading crosses south (yaw ±180).
4. **Gun crew loads.** Assign a crew member to the cannon, whistle "Load!". Expected: he faces the gun and rams
   (`cannon_load`, both hands on an invisible rammer, pushing forward every 1.5 s) until the gun is loaded, then stands
   normally and looks around again.
5. **Gun crew fires.** Whistle "Fire!". Expected: during the half-second fuse he raises the linstock hand and lunges
   down to the touch hole, then steps back clear (`cannon_fire`, once); with auto-reload he goes straight on to
   ramming.
6. **Fire at will.** With a hostile ship in range (navy or pirate ship), whistle "Fire at will". Expected: between
   loads and shots the gunner leans over the breech with a hand on it, sighting (`cannon_aim`). With no target in
   reach he stands normally.
7. **Hauling (player, PAL).** Hook a ship with the grappling hook, hold the rope in your hand (not tied), sneak until
   the rope goes taut. Expected (F5): you lean back with the right foot braced forward and pull hand over hand
   (`haul`, one second per cycle). Releasing sneak or tying the rope stops it; riding a rope still shows the slide pose.
   Check that another player sees it too.
8. **Seafarer variants.** If any pirate or navy crew stand at stations (they share the animation file), the same poses
   apply; report anything that looks off on their models (coat tails, tricorns).

Report: a screenshot of steps 1, 2 (mid-turn), 4, 5 (the lunge) and 7 from the side, and whether the turn directions in
step 2 match the wheel. Not in game: `capstan_push` is authored (crew rig, 1.2 s loop) but no capstan station exists
yet, so nothing plays it.

## SLP1: players sleep in hammocks and in the sea cot aboard
Covered by `crew/hammock/PlayerSleepRulesTest` (JUnit: refusals in vanilla's order, when the spawn is set, reach),
`neoforge` `crew/HammockBedExtensionTest` (the hammock's NeoForge bed methods really override), and 10 GameTests in
`crew/hammock/PlayerSleepGameTests` (a real server player on the test ship: hammock aboard, sneak to wake, crew finds no
free hammock, the ship moved and turned under the sleeper, Leave Bed, crew already in it, daytime, a zombie on deck,
the night skipped, a hammock on land, the cot aboard, both toggles). What they cannot show: how lying looks and feels
in a client (pose, camera, the "Leave Bed" screen), how a sleeper moves on a sailing ship, and respawning.

Setup: an assembled ship with a hammock hung in the hold (two posts, see step 1 of HM1) and a sea cot on deck or in a
cabin; a second hammock on land. Default config (`crew.hammock.player_sleep = true`,
`ship_decor.sea_cot_sleeping = true`, `ship_decor.sea_cot_sleeping_aboard = true`). Survival mode for steps 3 and 9.

1. **Hammock aboard, at night.** `/time set 13000`, right-click the hammock (either half). Expected: you lie down in
   it like in a bed: the screen shows "Leave Bed", the view is low over the canvas, "Respawn point set" in chat. In
   third person (F5): the body lies **along** the hammock, head over the half you looked toward when hanging it (not
   the clicked one), feet toward the clicked half, resting on the canvas: report the body floating above it or sinking
   through it (the height is a constant, `HammockBlock.LYING_HEIGHT`, 6 px), or lying across it.
2. **Leave Bed.** Press "Leave Bed" (or Esc). Expected: you stand up beside the hammock on the hold's floor (or on the
   deck next to it), not inside the deck above and not in a wall; no damage. Try a hammock hung right under the deck
   planks and one hung at floor height.
3. **Monsters and day.** By day: action bar "You can only sleep at night" (the respawn point is still set, as at a
   bed). At night with a zombie within 8 blocks (`/summon zombie` on deck, survival): "You may not rest now; there are
   monsters nearby".
4. **Sailing while asleep.** Lie down in the hold's hammock, have someone else (or a crew helmsman with a course) sail
   and turn the ship for a minute. Expected: you stay in the hammock the whole time, turning with the hull; the camera
   follows the ship without jitter. Report the body or camera drifting off the hammock, lagging behind, or snapping
   back every few ticks, and whether the camera's facing turns with the ship (vanilla aims the sleeping camera along
   the bed's block direction; on a turned ship that may look off by the ship's heading: screenshot it).
5. **Night skip.** Alone on the server, stay in the hammock: after about 5 seconds the night is skipped, you wake
   beside the hammock at dawn. With two players: "1/2 players sleeping" counts you.
6. **Crew and the hammock.** With one hammock and one free crew member aboard, `/time set 11000` and `/weather
   thunder` (players may sleep in a thunderstorm by day), lie down in the hammock, then `/time set 13000`: the crew
   member finds no hammock at nightfall, keeps standing, and grumbles "No hammock for me…" at dawn. The other way
   round: when a crew member already sleeps in it, right-click: "A crew member is sleeping in this hammock".
7. **Respawn on the ship.** Sleep in the hammock (or just use it by day to set the spawn), sail the ship a few hundred
   blocks, then die (`/kill`). Expected: you respawn on the ship, at the hammock, wherever the ship is now (Sable keeps
   the point with the ship). Break the hammock and die again: report where you respawn (Sable does not check the bed
   still exists, so possibly still on the ship).
8. **Sea cot aboard.** Right-click the cot on the assembled ship at night. Expected: you lie in it like in a bed, on
   the mattress (vanilla's bed height), and sail with the ship as in step 4; another player right-clicking it gets
   "This bed is occupied"; Leave Bed puts you on the deck beside it. On land the cot is a plain bed as before.
9. **Hammock on land.** Sleep in the land hammock, Leave Bed, then die: you respawn beside the hammock (not at the world
   spawn). Break it and die again: "You have no home bed or charged respawn anchor, or it was obstructed".
10. **Reconnect while asleep.** Lie in the hammock aboard, disconnect, reconnect. Expected: you are awake, standing
    beside the hammock or on deck, not stuck in a lying pose on an invisible seat.
11. **Toggles.** `crew.hammock.player_sleep = false`: right-click a hammock shows "Hammocks are for the crew: they
    turn in here at night". `ship_decor.sea_cot_sleeping_aboard = false`: the cot on a ship shows "This cot is just for
    show while it is aboard a ship"; on land it still works.

Report: screenshots of steps 1 and 8 in third person, step 4 from outside the ship, and where you stood up in step 2.
