# Playtest: world simulation (milestone 20)

One section per work package (`docs/plans/world-simulation.md`). The rules are covered by JUnit tests and GameTests;
these steps check them in a real world. Please send `latest.log` if anything differs.

## WS1: faction state

The faction state (aggression and wealth of Navy, Pirates and Merchants, tension between each pair) is saved per world
in `data/pirates_n_ships_factions.dat`. Nothing in the world reports events yet except the command below; patrols,
convoys and raids report theirs from WS2 to WS5.

Setup: `./gradlew :neoforge:runClient`, a new creative world with cheats, operator.

1. **Start state.** `/pirates world factions`
   - **Expected:** three lines:
     `Navy: aggression 0.30, wealth 10000, tension with Pirates 0.20, with Merchants 0.00`,
     `Pirates: aggression 0.30, wealth 2000, tension with Navy 0.20, with Merchants 0.00`,
     `Merchants: aggression 0.30, wealth 10000, tension with Navy 0.00, with Pirates 0.00`.
2. **An event.** `/pirates world factions report pirate_killed_by_navy` (tab completion lists every event).
   - **Expected:** "Reported pirate_killed_by_navy", then the three lines with Navy–Pirates tension 0.25, Pirates'
     aggression 0.32 and Navy's 0.31. `report convoy_delivered` raises the merchants' wealth by 100 and the navy's by 20.
3. **Decay over a day.** Report `pirate_killed_by_navy` four more times (tension 0.45), then `/time add 24000`.
   - **Expected:** within a second `/pirates world factions` shows Navy–Pirates tension 0.40 and every aggression 0.05
     closer to 0.30. Sleeping through a night does the same once. `/time add 72000` decays three days at once.
4. **Survives a restart.** Note the values, save and quit, reopen the world, run the command.
   - **Expected:** the same values; no extra decay from reloading.
5. **Toggle.** Config `world_simulation.factions.enabled = false` (or `world_simulation.enabled = false`).
   - **Expected:** `report` answers "... changed nothing", `/time add 24000` leaves the values as they are.
6. **Reset.** `/pirates world factions reset` puts the start state back.
7. **A deed moves the factions (WS1b).** `/pirates world factions reset`, then in survival kill a navy soldier
   (`/summon pirates_n_ships:navy_soldier`), and sell plundered cargo at a pirate fence (or plunder a merchant ship).
   - **Expected:** after the kill, Navy–Pirates tension 0.28 and Navy's aggression 0.35 (the first hit counts as
     attack_navy, the death as kill_navy); after the sale, the pirates' wealth up by 50. With your navy reputation at 0
     or above (`/pirates rep`, set it with `/pirates rep set`), killing a pirate raises
     Navy–Pirates tension by 0.05; at −1 or below it leaves the factions alone. With `reputation.enabled = false` or
     `world_simulation.factions.enabled = false` no deed changes the values.
8. **Later (once WS4b exists):** let a navy patrol kill a pirate; the Navy–Pirates tension rises by 0.05 per kill.

## WS2: sea lanes, voyages, convoys

The lane search (straight lines, bends around land, sealed ports, the budget, the coast margin, corner cutting), the
position along a lane, the pirate risk, the convoy choice (demand, distance limits, dimensions, 1/distance weighting),
the cargo, the wind factor, spawn chances and the cap, and the codecs are covered by JUnit (`LanePathfinderTest`,
`VoyageRulesTest`); the server side by 8 GameTests (`VoyageGameTests`: trade on departure and arrival, speed × interval
and arrival through the scheduler, `enabled=false` freezes, `convoys_per_day=0` sends none, the voyage cap, the failure
cache on the flat test world, contract distance and risk, the cost of a lane on a real overworld biome map). The test
world is flat, so lanes between real ports and the convoy traffic of a real world need a client.

### Setup
A new world (survival or creative, operator rights). Explore until at least two ports exist: `/pirates world ports`
lists them (`village_<x>_<z>`, `pirate_island_…`, `navy_outpost_…`). Two seafarer villages a few hundred to two
thousand blocks apart along the same sea are ideal. Keep `logs/latest.log` (the lane timings are logged at debug level;
the command prints them as well).

### Steps
1. **Lane between two real ports:** `/pirates world voyages lane <port A> <port B>` (Tab completes the ids).
   Expected: "Lane A -> B: N blocks, W waypoints, C cells searched, S biome samples, T ms" and a line of waypoints
   `x z; x z; …`. N is at least the straight distance between the berths; the waypoints keep off the coasts.
   Teleport to a few waypoints (`/tp @s <x> 70 <z>`): each is on open ocean, none on land or in a river. Note T (the
   cost of a first search; expected tens of milliseconds for ~2000 blocks). Running the command again recomputes.
   Two ports separated by a continent: either a longer lane around it or "No lane … (no_path / budget …)".
2. **List:** `/pirates world voyages` → "0 voyages under way (0 lanes queued)" in a fresh world.
3. **Prices before:** at port A's harbor desk note the price of a good A produces (cheap, e.g. sugar); at port B's
   desk note the price of the same good if B demands it (expensive).
4. **Spawn a convoy:** `/pirates world voyages spawn convoy <A> <B>` → "A convoy sets sail" and a line
   `<id> convoy A -> B: 0/N blocks at x z, sailing, cargo 64 sugar` (the cargo is up to three goods A produces that B
   trades, demanded ones first). If A and B share no goods the cargo is "-".
   Expected: A's price for the cargo good is now a little higher (the convoy bought 64 units).
5. **Progress:** wait one minute and run `/pirates world voyages` again: progress grew by about 240 blocks (4 blocks
   per second, ×0.6 into the wind up to ×1.2 before it); the position moves along the waypoints of step 1.
6. **Arrival:** `/pirates world voyages advance <id> 100000` → "Voyage <id> arrived at B"; the list no longer shows
   it. Expected: at B's desk the cargo good is now cheaper than in step 3 (the convoy sold its cargo).
7. **Natural convoys:** set `world_simulation.convoys_per_day` to 100 in the server config (or wait several in-game
   days at the default 2): within a few minutes `/pirates world voyages` lists convoys between ports that produce and
   demand the same goods, never more than `max_simultaneous_voyages`. "N lanes queued" drops by one per second as the
   lanes are computed in the background; the server keeps 20 TPS (`/neoforge tps`) while it does.
8. **Contracts:** at a harbor desk, look at the delivery contracts offered: deliveries to ports with a known lane show deadlines and
   rewards scaled by the lane length (longer routes pay more), and routes passing within 300 blocks of a pirate island
   pay a risk bonus. Before a lane is computed the straight distance is used.
9. **Toggle:** `world_simulation.enabled = false` → `/pirates world voyages` shows the same progress after a minute.
10. **Restart:** save and quit, reload: the voyages and their progress are still listed; `voyages lane` for a pair
    computed before still works and the cache is in `data/pirates_n_ships_lanes.dat`.

Report: the lane command's output for one or two pairs (blocks, waypoints, cells, ms), a screenshot of one waypoint at
sea, the prices of steps 3, 4 and 6, and any lane that crosses land.

## WS3b: materialisation (ships on the horizon)

Covered headless: deck spots, the ending rules (sunk, capture hold, plunder once, shooter memory), the route
projection, the radius clamp and the saved ship link (JUnit `DeckSpotsTest`, `VoyageEndingsTest`); in basins
(`MaterializeGameTests`): a convoy appears centred on its lane point at the leg heading with flag, cargo, crew and
fighters; a pirate ship flies the Jolly Roger; dematerialising keeps progress, cargo and headcount; a reload
re-adopts the ship and re-sets its course; a stuck ship skips a waypoint; flooding sinks it (deed for the shooter, or
the world event without one); plunder counts once; a boarded ship without fighters is captured; the cap holds. Not
covered: real sailing along a lane, wind, chunk loading, real players walking away and back, a real save and reload.

### Setup
A creative world with cheats, open ocean in front of you (stand on a boat or a pier facing the sea, at least 60 blocks
of water ahead). Keep `logs/latest.log`; set the log level of `pirates_n_ships` to debug if you can (the materialiser
logs every appearance, dematerialisation, adoption and ending).

### Steps
1. **A convoy appears.** Face the open sea and run `/pirates world voyages spawn near convoy`.
   - **Expected:** "A convoy voyage (<id>) crosses the sea in front of you" and "Voyage <id> is a real ship now". About
     48 blocks ahead and 30 to the left a starter sloop sits on the water, turned along a line crossing your view
     (not snapped to north/east/south/west), with a merchant flag, a name on `/pirates world voyages`
     (state `materialised`), four crew (one at the helm) and two sailors standing still on deck. Within a few seconds
     a deckhand goes to the sail winch and the sails come down; the ship starts to move to your right.
2. **It sails its route.** Watch for a minute.
   - **Expected:** the helmsman steers along the line (with the wind; into a headwind it may stall, see step 8).
     `/pirates world voyages` shows its progress growing while it is a real ship.
3. **Cargo and plunder.** Board it (fly over, land on the deck) and open a cargo crate or barrel: it holds the convoy's
   goods (64 units each of two goods), marked as plunder. Take some.
   - **Expected:** action bar "You plundered a merchant ship" once; taking more says nothing new. Your reputation
     (`/pirates reputation`) moved for `plunder_merchant`; `/pirates world factions` shows Pirates–Merchants tension up.
     `/pirates world voyages` shows the convoy's cargo reduced.
4. **Capture.** Spawn another convoy (step 1), board it and kill both sailors, then stay aboard for 5 seconds.
   - **Expected:** action bar "You took the <name>! She is yours now". The voyage is gone from the list, the ship stays,
     its crew stands on deck released (whistle them). Your criminal score rose for piracy. The ship answers to you
     (helm, whistle) like your own.
5. **Sinking.** Spawn a convoy, fire your cannons into its hull below the waterline (or `/pirates` damage commands if
   you have them) until it sinks.
   - **Expected:** once it is flooded to about 80 % (or a piece of it breaks off as a wreck, or it is fully underwater
     for 5 seconds) the voyage disappears from the list; the hull keeps sinking as a wreck and is not removed. If your
     cannon hit it within the last minute you get the `sink_merchant` deed (reputation), else `/pirates world factions`
     shows Merchants' wealth down (convoy_sunk). Try `spawn near raid` and sink the pirate ship too (`sink_pirate`).
6. **Walk away and come back.** Spawn a convoy, note its id, then fly away more than `materialize_radius` + 64 blocks
   (192 + 64, clamped to your view distance: (view distance − 1) × 16 + 64).
   - **Expected:** 10 seconds after you leave, the list shows it `sailing` again (no ship id), progress where the ship
     was. Fly back toward its position: when you are within the radius it appears again within a second or two, with
     the same cargo (minus what you took), its crew and fighters (minus those you killed).
7. **Reload.** With a convoy materialised in front of you, save and quit, reopen the world.
   - **Expected:** the same ship is there with its people; within a second the helmsman steers along the route again
     (`/pirates world voyages` still shows it `materialised`). No second copy of the ship appears.
8. **Islands and headwinds.** Spawn a convoy pointing at a nearby island (face the island), or into the wind.
   - **Expected:** if the ship cannot make way for 10 seconds with sails set it vanishes ("stuck") and its record
     jumps to the next waypoint; it does not reappear for 30 seconds.
9. **Natural traffic.** With two ports and `world_simulation.convoys_per_day` raised (WS2 step 7), sail along a lane
   between them.
   - **Expected:** convoys appear where the lane passes within the radius, never more than 4 at once
     (`world_simulation.materialize.max_materialized`), and turn back into records when you leave.
10. **Toggle.** `world_simulation.materialize.enabled = false`: no new ships appear; existing ones still turn into
    records when you leave.

Report: screenshots of steps 1, 4 and 5, the list output before and after steps 6 and 7, `latest.log`, and anything a
ship did that looked wrong (spawning inside land, fighters falling off, a crew member walking overboard).

## WS4b: navy patrols and the hunt

Covered headless: which ship the navy hunts (Jolly Roger, blown cover, a bounty from `hunt_bounty_minimum`, a wanted
owner; never NPC ships or struck colours), the nearest pick within `hunt_radius`, the chase verdicts (contact, give-up,
lost, out of range, surrender linger, colours raised again), the patrol routes (outpost to outpost, out and back
toward a pirate island, the pursuit line capped at the first land after open sea, the ring around the quarry, the way
back to the route) and the spawn chance under aggression 0 and 1 (JUnit `HuntRulesTest`, `PatrolRoutesTest`,
`PatrolPlannerTest`); in basins (`NavyGameTests`): a Jolly Roger player ship 100 blocks from an abstract patrol is
chased after one check, a clean merchant ship is ignored, a bountied owner is hunted from 50 doubloons, a patrol out of
contact gives up and heads back to its route, the toggle stops the hunt, a materialised patrol sets its gun crew on
the quarry and hits it, striking the colours silences the gun and the patrol leaves after the linger, a patrol
soldier killing a pirate raises Navy–Pirates tension. Not covered: a real patrol ship circling a moving ship at sea,
real outposts and lanes, players switching flags in the middle of a chase.

**Known gap (closed by WS4c):** the default navy templates (`voyages.navy_templates`: the two starter sloops) carried
no cannons, so a patrol chased and circled but could not fire unless its ship had a cannon with powder and balls
aboard (step 3 adds one by hand). Since WS4c the default is the armed navy sloop; see the WS4c section below.

### Setup
A creative world with cheats and open ocean. Your own ship at sea (assembled by you, so you are its owner) with a
flagpole, and you aboard it. `/pirates law score set @s 0` and `/pirates law bounty clear @s` so you start clean. Keep
`logs/latest.log`, with the `pirates_n_ships` log level at debug if you can (the hunt logs every chase and its end).

### Steps
1. **A clean ship is left alone.** Fly the merchant flag. Face the open sea and run
   `/pirates world voyages spawn near patrol`, then `/pirates world patrols`.
   - **Expected:** a navy sloop with the navy flag, an officer and soldiers appears about 50 blocks ahead and sails
     across your view. The list shows it `materialised ... on patrol from ...`. It does not turn toward you.
2. **The Jolly Roger is hunted.** Raise the Jolly Roger on your flagpole.
   - **Expected:** within about a second the chat says "A navy patrol has sighted the <your ship> and gives chase!".
     `/pirates world patrols` shows `chasing ship <id>`. The patrol turns toward you and, once there, circles you at
     about 20 blocks (`standoff_distance`). Sail away slowly: it follows; its course is renewed every 2 seconds while
     you move. Note how often its helmsman's course lines appear in chat (they repeat with each new course).
3. **The guns (only with a gun aboard).** With the patrol circling you, land on its deck, place a cannon facing
   outboard and a chest with gunpowder and cannonballs next to it, and step back aboard your own ship.
   - **Expected:** within about 2 seconds a deckhand mans the cannon (it is posted on the patrol's job board), loads,
     and fires whenever your ship is within 15° of its barrel and 64 blocks (WS4a). Its balls break about half the
     blocks a player's would (`cannons.npc.npc_block_damage_multiplier`).
4. **Striking the colours.** While it fires, strike your colours at your flagpole.
   - **Expected:** the chat says the patrol "holds its fire"; at most one more shot (a fuse already lit), then none.
     The patrol keeps circling you for 30 seconds (`surrender_linger_ticks` 600), then the chat says it "returns to its
     route", and it sails back to its line. `/pirates world patrols` shows it `on patrol` again. Raising the Jolly
     Roger again during the 30 seconds makes it chase and fire again.
5. **A bounty is hunted whatever you fly.** Fly the merchant flag. `/pirates law bounty place @s 60`, then spawn a
   patrol (step 1).
   - **Expected:** it gives chase as in step 2. With `/pirates law bounty clear @s` during the chase it breaks off
     ("returns to its route" is not said; it simply heads back) within a second. A bounty of 40 alone is not enough
     (`hunt_bounty_minimum` 50).
6. **Giving up.** Set `world_simulation.navy.give_up_ticks` to 400, raise the Jolly Roger, spawn a patrol and sail away
   from it at full speed, keeping more than 64 blocks (`contact_distance`) between you.
   - **Expected:** 20 seconds after it last came within 64 blocks the chat says it "has lost the <ship> and breaks off
     the chase", and it turns back. Fleeing beyond 384 blocks (`lose_distance`) ends the chase at once.
7. **Abstract patrols.** Set `world_simulation.patrols_per_day` to 100 and stand by a navy outpost (another outpost
   within 3000 blocks, or a pirate island). Wait a few minutes and watch `/pirates world patrols`.
   - **Expected:** patrols set out (more while `/pirates world factions` shows a high navy aggression), sail outpost
     to outpost or out to about 400 blocks toward the pirate island and back, and appear as ships when they come
     within the materialise radius of you. Under the Jolly Roger a patrol that is still a record turns toward you from
     up to 256 blocks (the chat line comes before you can see it) and appears as a ship when it comes close.
8. **Toggle.** `world_simulation.navy.enabled = false`: no patrols set out, a chasing patrol breaks off within a
   second, and the Jolly Roger draws no chase.

Report: screenshots of steps 2 and 4, the `/pirates world patrols` output during steps 2 and 4, `latest.log`, and
anything a patrol ship did that looked wrong (circling too wide or ramming you, spinning on the spot, sailing onto land
during a chase, chat spam from its helmsman).

## WS5: raids on navy settlements

Covered headless: the chance rules (linear growth, cap, cooldown, presence count, retaliation multiplier, approach and
home routes; JUnit `RaidRulesTest`); in GameTests (`RaidGameTests`): three minutes of a mock player in a fake outpost
count three minutes with the chance at the cap, a forced raid rings a vanilla bell in the box and spawns a RAID voyage
`approach_distance` out from the berth, starts the cooldown and refuses a second raid; a real raider in a basin within
`landing_distance` of the berth puts its four pirates on the shore and turns for home; killing them all withdraws the
ship and reports `raid_repelled` with the cooldown from today; with retaliation off the tension does not change the
chance. Not covered: real sailing to the berth, the anchor holding off a real quay, pirates fighting the garrison, the
bells of a generated fort, the minute-by-minute roll with real players, a save and reload during a raid.

### Setup
A creative world with cheats and a generated navy outpost on the coast (`/locate structure pirates_n_ships:navy_outpost`)
and, ideally, a pirate island within a few hundred blocks (`/locate structure pirates_n_ships:pirate_island`). Note
the outpost's port id with `/pirates world port nearest` while standing in it. For a quicker raid set
`world_simulation.raids.approach_distance` to 120 in the server config (the default 320 is outside the materialise
radius, so the ships first sail in as records and appear when they come within about 150 blocks). Keep
`logs/latest.log` with `pirates_n_ships` at debug if you can (the raid logs its start, landing, withdrawal and end).

### Steps
1. **Chance.** Stand inside the outpost for three minutes, then run `/pirates world raid chance`.
   - **Expected:** the first line shows growth 0.0005 per minute × about 1.2 (retaliation from the starting Navy–Pirates
     tension 0.2), cap 0.02, cooldown 5 days; the outpost's line shows 3 minutes of presence and a chance of about
     0.18 % per minute, "raided on its own". Leave the outpost for over a minute and run it again: the outpost is gone
     from the list (the count started over).
2. **Forced raid, announcement.** Stand on the fort's sea wall and run `/pirates world raid <port id>`.
   - **Expected:** "Raid on <port>: 1 ship(s) … along the lane from the nearest pirate island" (or "straight in from the
     sea" without an island or a cached lane) and the number of bells; in chat "Sails on the horizon! Pirates are
     making for the settlement"; the alarm bell over the sea gate rings at once and every 5 seconds for 30 seconds.
     `/pirates world voyages` lists a `raid` voyage to the outpost.
3. **Approach.** Watch the sea in the direction of the island (or straight out from the quay).
   - **Expected:** a sloop under the Jolly Roger appears (at once with approach 120, else when it comes within about
     150 blocks) and sails toward the quay. If it has crewed cannons they fire at your ship if you sail out to meet it.
4. **Landing.** Wait at the quay.
   - **Expected:** about 24 blocks from the quay the sloop furls its sails and drops its anchor (it may swing a little
     on the chain); its four pirates appear on the shore next to the quay and attack the garrison and you. The ship
     stays anchored, not drifting onto the quay.
5. **Repelled.** Kill the four pirates (with the garrison's help).
   - **Expected:** within a second "The raiders are beaten off!" in chat; the sloop raises its anchor, hoists its sails
     and sails back out the way it came; it disappears when you are far from it. `/pirates world factions` shows
     Pirates' aggression down and Navy–Pirates tension up (`raid_repelled`).
6. **Cooldown.** Run `/pirates world raid chance` again in the outpost.
   - **Expected:** "cooldown 5.000 days left" and a chance of 0 %, while you stay there; after `/time add 120000`
     (5 days) the chance grows again.
7. **Held shore.** Set `raid_duration_ticks` to 600, force a raid, let it land and do not kill the pirates (fly up out
   of reach).
   - **Expected:** 30 seconds after the landing the pirates vanish, "The raiders held the shore and sail off", the ship
     withdraws; `/pirates world factions` shows `raid_succeeded` (Pirates' wealth up).
8. **Sinking a raider.** Force a raid and sink the sloop with cannons before it lands.
   - **Expected:** the voyage ends as sunk (your `sink_pirate` deed, WS3b), the raid ends as repelled ("The raiders
     are beaten off!", `raid_repelled`).
9. **Village.** Force a raid on a seafarer village (`/pirates world raid <village port id>`).
   - **Expected:** the chat line only (the village has no bell); the landing works the same way, but there is no garrison,
     so only you and the villagers face the pirates (the pirates attack you; whether they attack villagers depends on
     the mobs' hostility rules). With `world_simulation.raids.target_villages = false` a village never counts presence
     on its own (`/pirates world raid chance` shows "not raided on its own"), but forcing still works.
10. **Natural raid.** Set `world_simulation.raids.chance_growth_per_minute` to 0.01 and `chance_cap` to 0.5, stay in
    the outpost (with a pirate island in the world) for a few minutes.
    - **Expected:** a raid starts on its own within roughly 3–6 minutes, announced as in step 2. Without any pirate
      island in the dimension no raid starts on its own.
11. **Toggles.** `world_simulation.raids.enabled = false`: no presence is counted and no raid starts on its own (forcing
    still works); `world_simulation.raids.announce = false`: no chat lines (the bells still ring; `bell_ticks = 0`
    silences them); `world_simulation.retaliation_enabled = false`: the growth multiplier in step 1 is 1.

Report: screenshots of steps 2, 4 and 5, the `/pirates world raid chance` output of steps 1 and 6, `latest.log`, and
anything that looked wrong (the ship running onto the quay or getting stuck before it lands, pirates appearing inside
walls or in the water, bells not ringing, the ship not leaving).

## WS4c: patrols that can fight

Covered headless: the armed sloops differ from the starter sloop only by the four guns, their gun ports and the shot
locker, which is within the gun crews' supply range of every gun (JUnit `ArmedSloopLayoutTest`, the committed
structures pinned by `SchemToStructureTest`); the locker and rounds rules (`GunStockingTest`); in basins
(`ArmedShipGameTests`): the armed navy sloop places, assembles and settles upright with its main deck dry, a crew
member can be seated at each of the four guns and loads one from the locker; a materialised navy patrol appears with
4 × `cannon_rounds` powder and shot in the locker and loaded guns, and dematerialising carries no ammunition as cargo;
a pirate raider gets the default 12 per gun; a merchant on the same hull gets nothing; (`NavyGameTests`) a patrol
materialised on the default navy template fires at a Jolly Roger hull with its own guns and hits it;
(`HelmStationGameTests`) a course renewed silently says nothing, the first course of a chase is acknowledged once
without waypoint calls. Not covered: a real chase at sea with the wind, how the gun crews fare while the patrol
circles, how the armed sloop sails and heels compared with the plain one.

### Setup
As for WS4b: a creative world with cheats and open ocean, your own ship with a flagpole and you aboard,
`/pirates law score set @s 0` and `/pirates law bounty clear @s`. In an existing world check
`config/pirates_n_ships-server.toml` (or the world's `serverconfig`): `world_simulation.voyages.navy_templates` and
`pirate_templates` keep their old values (the two starter sloops) in a config written before WS4c; set them to
`["pirates_n_ships:navy_sloop_armed"]` and `["pirates_n_ships:pirate_sloop_armed"]`, or delete the two lines.

### Steps
1. **The armed sloop.** `/pirates ship place navy_sloop_armed assemble` at the shore.
   - **Expected:** the starter sloop with two cannons a side in the waist, just forward and aft of the winch, each
     muzzle in a one-block gap of the bulwark. A barrel on a plank stand in the hold beside the mast (down the hatch
     ladder). The ship floats level, like the plain starter sloop (compare `/pirates ship place starter_sloop
     assemble`), and does not lie deeper in a way you can see. `/pirates ship templates` lists both armed sloops; the
     shipwright's Orders tab does not.
2. **A patrol appears armed.** Fly the merchant flag, face the open sea, `/pirates world voyages spawn near patrol`.
   Fly over and land on its deck.
   - **Expected:** the patrol's ship is the armed sloop with the navy flag. All four cannons show loaded (use one with
     an empty hand from the side: it says it is loaded, or look at the barrel). The barrel in the hold holds 48
     gunpowder and 48 cannonballs (12 rounds × 4 guns). Its cargo crates and barrels on deck are empty.
3. **A raider and a convoy.** `/pirates world voyages spawn near raid`, then `... convoy`.
   - **Expected:** the raider is the same armed sloop under the Jolly Roger, stocked the same way. The convoy is a
     plain starter sloop (merchants unchanged) with goods and no powder anywhere.
4. **The chase fires without help.** Back on your own ship, raise the Jolly Roger near the patrol from step 2 (or
   spawn a new one).
   - **Expected:** "A navy patrol has sighted the <ship> and gives chase!" once. Its helmsman says "Aye, holding the
     course!" once at the start of the chase and nothing more while it circles you (no waypoint lines, no repeated
     acknowledgements, even with you sailing away slowly). Within a few seconds two deckhands go to the guns on the
     side facing you; once you are within about 15° of a barrel and 64 blocks, they fire. No cannon was placed by
     hand. After each shot the crew reloads from the barrel in the hold (watch its count go down).
5. **Out of shot.** Let the patrol keep firing at you (or set `world_simulation.materialize.cannon_rounds` to 1 and
   spawn a new patrol).
   - **Expected:** when the barrel is empty the guns fall silent after their last shot; the patrol keeps circling.
6. **Striking the colours** as in WS4b step 4.
   - **Expected:** as in WS4b; the guns stay loaded and manned but silent.
7. **Dematerialise and come back.** Fly away beyond the linger distance and come back to a patrol that has fired.
   - **Expected:** when it appears again its locker holds the full 48 rounds again and its guns are loaded (the
     ammunition is not part of the record). `/pirates world voyages` shows no gunpowder or cannonballs as its cargo.
8. **Toggles.** `world_simulation.materialize.guns_start_loaded = false`: a new patrol's guns are empty; its crew
   loads them from the locker when the chase begins (the first shot comes later). `cannon_rounds = 0`: the locker is
   empty, loaded guns fire once and then stay silent.

Report: screenshots of steps 1 (the waist from above and the hold), 2 (the locker's contents) and 4, the chat during
step 4, `latest.log`, and anything that looked wrong (a gun facing inboard, a crew member standing in a gun port or
falling overboard, the ship heeling to one side, balls hitting the patrol's own bulwark).

## WS3c: voyage polish (deliveries, battered ships, deckhands)

Covered headless: the delivery rule (a convoy with cargo; JUnit `VoyageRulesTest`) and the flooding of a hull back to
a health, lowest compartments first (JUnit `HealthFloodingTest`); in basins (`MaterializeGameTests`): a materialised
convoy arriving reports `convoy_delivered` once, a second arrival of the same record reports nothing, one plundered
empty reports nothing; a ship flooded to 40 % and dematerialised keeps health 0.6 and appears again 40 % flooded with
the water in its lowest compartments, a fresh record appears dry, `restore_health = false` appears dry; the deckhands
are stationary and all crew are still on the deck after 400 ticks, a captured ship's crew is not stationary any more;
a ship that appears on a plot freed in the same tick still finds its crew, fighters and a boarder (the WS3b open item
about stale `CrewStations.worldBox` bounds: the bounds hold every block, nothing to fix). (`VoyageGameTests`) an
abstract convoy arriving with cargo reports once, an emptied one does not. Not covered: a convoy reaching a real
port, a real hull battered by cannonballs (breaches) coming back, the deckhands over a long voyage at sea.

What the faction state does with `convoy_delivered` (WS1): Merchants' wealth +100 and the Navy's +20, times
`world_simulation.factions.event_scale`; no tension or aggression changes.

### Setup
As for WS3b: a creative world with cheats and open ocean in front of you. For step 1 two ports with a lane between
them and `world_simulation.convoys_per_day` raised (WS2 step 7); `/pirates world factions` to read the state.

### Steps
1. **A delivery.** Note Merchants' wealth in `/pirates world factions`, then let a convoy reach its destination port
   (abstract: wait, or follow it as a real ship until it reaches the last waypoint).
   - **Expected:** when the convoy disappears from `/pirates world voyages` at its port, Merchants' wealth is 100
     higher and the Navy's 20 (at `event_scale` 1, before any other change). Only once per convoy.
2. **A plundered convoy delivers nothing.** Spawn a convoy (`/pirates world voyages spawn near convoy`), board it and
   empty every cargo crate and barrel, then follow it until it arrives (or let it arrive abstractly after you leave).
   - **Expected:** "You plundered a merchant ship"; on arrival Merchants' wealth does not rise (only the plunder moved
     it, down).
3. **A battered ship comes back battered.** Spawn a convoy, shoot a few holes into its hull below the waterline until
   it is clearly flooding but well short of sinking (the hull HUD or the water inside), then fly away beyond the linger
   distance (WS3b step 6) and come back.
   - **Expected:** the record's health in `/pirates world voyages` is below 1 after it turned into a record. When the
     ship appears again it lies lower, with water in its hold (the lowest compartment fills first), about as flooded as
     when you left; it has no holes (breaches do not carry back, only the water). A convoy you never shot appears dry.
4. **Deckhands stay aboard.** Spawn a convoy and watch its crew for a few minutes while it sails, and while you stand
   on its deck.
   - **Expected:** the deckhands who are not at the sail winch stand where they are (they turn and look around, like
     the sailors); nobody walks over the side. Capture a convoy (WS3b step 4): its crew strolls about again like your
     own crew.
5. **Toggle.** `world_simulation.materialize.restore_health = false`, then repeat step 3.
   - **Expected:** the battered ship comes back dry; the record still shows its health.

Report: `/pirates world factions` before and after steps 1 and 2, a screenshot of the ship in step 3 before you left
and after it came back, `latest.log`, and anything a deckhand did that looked wrong.
