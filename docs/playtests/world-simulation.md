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
