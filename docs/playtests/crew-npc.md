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
