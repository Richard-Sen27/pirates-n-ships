# Playtest: world simulation (milestone 20)

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
