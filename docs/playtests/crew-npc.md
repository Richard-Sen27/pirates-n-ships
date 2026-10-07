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
