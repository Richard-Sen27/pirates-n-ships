# Performance audit (PERF1, 2026-10-10)

The human: "do a performance test". A headless load test of the mod with ships near a player on the waves,
measured and profiled; the cheap hot spots fixed, the expensive ones proposed.

## How it was measured

`core.perf.PerformanceGameTests`, registered only with `-Ppirates_n_ships.perf=true` (`ModGameTests.PERF_PROPERTY`;
how to run it: [`guide.md`](../guide.md), "Testing note: the performance test"). One test per N in its own batch:

- A 160 × 160 basin, water 10 deep, the sea held at ROUGH (abeam), a light wind (3, from the east).
- The harness's mock player (in the level, creative) on the quarterdeck of an assembled starter sloop whose waist
  bulwarks are railings, with a boarding net on the port rail and three ratline nets to the mast (all four strung in
  every run). Six crew: two row from the rail (Row order), one at the sail winch, one at the bilge pump, two idle
  (they lean on the rail; one or two were leaning at each census).
- N materialised armed voyages (alternately pirate raiders on `pirate_sloop_armed` and navy patrols on
  `navy_sloop_armed`, with their crews, fighters, stocked and loaded guns), plus a merchant convoy (starter sloop with
  cargo) and a navy patrol. The patrol and the first raider fire at each other (gunnery on a target, 22 blocks apart:
  33–39 balls a minute), three of the raider's fighters are boarding the convoy (`BoardingParties.enlist`).
- After 300 ticks of settling (the first test of a server also 1200 ticks of JIT warm-up), 1200 ticks are measured:
  every 20 ticks the last 20 tick times are read from `MinecraftServer.getTickTimesNanos()`. Reported: per-tick and
  per-second min/median/p95/max, ships, entities by type. The N = 8 run also samples the server thread's stack every
  2 ms for 400 ticks (`StackSampler`) and records vanilla's profiler (the `/perf` metrics recorder) over the same 400
  ticks; the seconds of that window are left out of its "unprofiled" numbers, which the table uses.
- Ships in the scene: N = 1 → 4 sub-levels, 49 entities; N = 4 → 7, 93; N = 8 → 11, 144 (43 crew members, 15 navy
  soldiers, 14–16 pirates, 5 officers, 32 station seats, 11 anchors).

**Machine and noise.** Apple M1, 8 cores, 16 GB, Java 21 toolchain (the GameTest server's JVM), NeoForge. Other agents'
GameTest servers ran at the same time (load average 14–17 on 8 cores), and the GameTest server ticks as fast as it
can, so absolute numbers carry a lot of noise: the same N = 8 scene measured 9.7 to 20.7 ms mean over five runs, and a
single run's Sable share moved between 69 % and 88 %. Each configuration ran at least twice; the table keeps the
better run. Logs and reports: `/private/tmp/claude-501/perf1/{base-b,base-c,fix-a,fix-b,fix-c,fabric}/` (machine-local).

## Results

Per-second MSPT (the mean of each second's 20 ticks), N = 8 without the profiled seconds; per-tick p95 in brackets.

| N | ships | before: min / median / p95 / max (tick p95) | after: min / median / p95 / max (tick p95) |
|---|---|---|---|
| 1 | 4 | 2.78 / 3.87 / 5.54 / 7.00 (6.0) | 2.43 / 2.77 / 4.15 / 5.22 (4.2) |
| 4 | 7 | 4.93 / 6.04 / 8.04 / 10.30 (10.1) | 4.22 / 5.22 / 7.49 / 8.27 (8.7) |
| 8 | 11 | 7.34 / 9.44 / 12.40 / 12.56 (16.6) | 6.94 / 10.11 / 16.63 / 22.15 (24.3) |

Per-ship cost `(MSPT(N=8) − MSPT(N=1)) / 7` by the mean: **0.8 ms per tick before, 1.1 after** (the runs in between gave
0.5–1.3 before and 1.1–2.3 after): about **1 ms per tick per armed NPC ship with its crew and fighters** on this
machine under this load, i.e. the default `max_materialized` of 4 costs about 4 ms of the 50 ms budget. The p95 at
N = 4 stayed between 7.5 and 21 ms in every run, under the 50 ms budget (`perf.budget_ms`). The before/after
differences in MSPT are inside the noise; the fixes are shown by their share of the tick (below). Fabric, after the
fixes, one run: 4.57 / 6.26 / 9.59 ms median at N = 1 / 4 / 8.

## Where the tick goes (N = 8)

**Sable is 69–80 % of the tick** (stack samples with a Sable frame), almost all of it in its physics system on the
server thread: `RapierPhysicsPipeline.prePhysicsTicks` → the native `Rapier3D.tick` alone 48–64 %, the substeps'
`Rapier3D.step` 7–15 %, and `SubLevelEntityCollision.collide` (entities against the ships) 6–12 %, the latter inside the
entity ticks below. The mod's own frames are on 15–20 % of the busy stacks, and most of that is vanilla mob AI under
`CrewMember.tick`.

The top 15 under the mod's namespace and packages (baseline runs, share of the tick; entity rows from vanilla's
profiler, method rows from the stack samples, inclusive):

| # | Entry | Share | Notes |
|---|---|---|---|
| 1 | entity ticks `pirates_n_ships:crew_member` (43) | 12.9 % | 0.03 ms each; `CrewMember.tick` → vanilla `Mob.aiStep` 10 %, Sable's entity collision inside |
| 2 | entity ticks `pirates_n_ships:navy_soldier` (15) | 8.0 % | 0.05 ms each, twice a crew member |
| 3 | entity ticks `pirates_n_ships:pirate` (14) | 7.8 % | as above |
| 4 | the mod's level-tick handlers (`CommonEvents.LEVEL_TICK_END`) | 3.2–3.8 % | all modules' level ticks together; 2.1 % after the fixes |
| 5 | entity ticks `pirates_n_ships:navy_officer` (5) | 2.8 % | |
| 6 | `ship.sable` package (physics-tick listeners, `plotBlocks`, `velocities`) | 2.4–2.7 % | 1.0 % after the fixes |
| 7 | entity ticks `pirates_n_ships:sailor` (2) | 1.4 % | the convoy's crew |
| 8 | `ShipCargo.onLevelTick` → `weigh` → `ShipBody.plotBlocks` | 0.6–1.0 % | a whole-plot walk per ship every 40 ticks only to count blocks; **fixed** |
| 9 | entity ticks `pirates_n_ships:station_seat` (32) | 0.7–0.8 % | `StationSeat.tick` → vanilla `updateInWaterStateAndDoFluidPushing` |
| 10 | `CrewMember.showStationPose` (+ `StationPoses.resolve`) | 0.2–0.6 % | every tick for every seated hand |
| 11 | `HostileTargetGoal.scan` | 0.3–0.5 % | a line-of-sight ray for every living entity in range, also for shipmates; **fixed** |
| 12 | `HullRuntimes.onLevelTick` / `HullRuntime.tick` | 0.5–0.8 % | sea sampling, `HeightProfile.moments`, `FloodSimulation.buildReport` |
| 13 | `SailingRuntimes.onPhysicsTick` / `SailingRuntime.physicsTick` | 0.4–0.8 % | per substep |
| 14 | `Gunnery.onLevelTick` | 0.2–0.5 % | aim solutions for the two engaged ships |
| 15 | `WaveForces.onLevelTick`, `BoardingParties.onLevelTick`, yard and name-board block entities | 0.1–0.3 % each | |

Below the sampler's resolution (under 0.1 %, not seen in any run): RAIL5's lean scan (`RailLean.candidates`, the
13 × 3 × 13 block read runs only for an idle hand that is not pausing while fewer than three lean, at most every 20
ticks), OAR1's rail-spot scan (`Rowing.row`, once per Row order) and `Rowing.onLevelTick`, the ratline and boarding
nets, `SeaStates` (synchronized, uncontended), the worldsim. The worldsim's voyage updates are **not** measured: test
voyages are left to their tests (`Materializer.check` skips them), so `Materializer.update` never ran.

## What was fixed

1. **Cargo block count kept between block changes** (`ship.cargo.ShipCargo`): `weigh` counted the ship's blocks with
   `plotBlocks().size()`, a walk over every cell of the plot's chunk boxes, for every ship every
   `weigh_interval_ticks` (40). The count is now kept per ship, dropped when a block of the ship turns into air or out
   of it (`ShipBlockChanges`, as the hull runtime and ballast do) or when the ship is removed, and recounted at the
   latest after 1200 ticks. Gain: `ShipCargo` and `plotBlocks` 0.6–1.0 % → not sampled; the `ship.sable` package
   2.4–2.7 % → 1.0 %. Test: `CargoWeightGameTests.weighingCountsBlocksPutOnAndTakenOffTheShip` (the count follows a
   plank put on the deck and taken off; the plot walk agrees).
2. **The target scan checks hostility before the line of sight** (`mob.ai.HostileTargetGoal`): `canAttack` and
   `attacksOnSight` (map and attachment reads) now come before `Sensing.hasLineOfSight` (a ray through the level), so
   shipmates and friendly factions cost no ray. Same targets. Gain: 0.3–0.5 % → 0.1–0.2 %. Covered by `MobGameTests`
   (hostility, factions, navy and pirate targeting).

Both: the scoped GameTests (`CargoWeightGameTests`, `MobGameTests`) green on NeoForge and Fabric (23 each).

## Proposed (expensive, not done)

1. **Sable's per-tick native step (`Rapier3D.tick`, about half the tick).** Not ours to change. Worth one experiment
   before asking upstream: the same scene in a basin deep enough that no hull touches the floor, and with Sable's
   `substepsPerTick` config lowered, to see whether the cost follows the bodies, their contacts with the basin's
   terrain, or the substeps. A real ocean (seabed 20+ blocks down) may be cheaper than this 10-deep basin.
2. **Fewer entities per NPC ship, or cheaper idle ones.** Each armed sloop brings about 12 entities (crew, fighters,
   station seats); entity ticks with Sable's entity-against-ship collision are a third of the tick. A navy soldier or
   pirate costs twice a crew member. Options: fewer fighters per materialised ship (the materialiser's per-faction fighter counts), an idle
   state for fighters with no enemy in range (no target scan, no navigation; wake on a hit or a boarding), and for
   seated crew (station and oar seats) a goal selector that ticks every few ticks. Design §18 already asks for "crew
   AI throttled when no player is nearby"; it is not implemented (no code checks for a near player).
3. **Station poses cached** (`CrewMember.showStationPose`, 0.2–0.6 %): resolve the pose once per seat and station
   state instead of every tick for every seated hand.
4. **Station seats without fluid pushing** (0.7–0.8 %): the invisible seat runs vanilla's water check every tick; a seat
   could skip it, but a sinking ship's seats then need a test that the crew still drowns and floats off.
5. **Run the perf test with the worldsim driving the voyages** (non-test voyages near the player), so
   `Materializer.update`, `HelmCourses` and the voyage scheduler are measured too, and with a real `ServerPlayer`
   (the harness's mock server player is refused Sable's payloads by NeoForge: its embedded connection negotiated no
   channels), so the mod's syncs (waves, ship status, hull regions) count.

## Limits of this test

- The scene uses `max_materialized` 16; the game's default is 4, so N = 8 is beyond what one player meets by default.
- The NPC ships hardly moved in the light wind (their centres at the end were on their slots; each carries an anchor
  entity), so their helm courses did little work; the player's sloop rowed.
- Client cost (rendering, the dry-hull mask meshes, the wave surface) is not measured; this is the server only.
- The stack sampler stops the server thread at a safepoint 500 times a second; the profiled seconds are left out of the
  MSPT table for that reason.
