# Crew and ports: package plan

Written by a planning agent on 2026-10-08 from design.md §7, §6, §10.3 and §8.3 and the code of that day; the orchestrator turns each package into an implementer prompt. Decisions that change land in design.md; this file is the detailed plan.

# Crew and port packages (milestones 9, 14, 15) — implementation plan

Base `/Users/richard/Projects/modding/mc/pirates-n-ships`, package root `common/src/main/java/com/richardsenger/piratesnships/` (`…/`). Written 2026-10-08 against `main` plus the batch waiting in `.claude/worktrees/merge` (ART7 station poses with the `capstan_push` animation, WS4a, BOS1); the plan assumes that batch lands first.

## What exists and what is missing

Exists: crew NPC with station seat, pinned assignment, morale, hammock, unpaid flag (`…/crew/npc/CrewMember`), orders and the job board (`CrewStations`, `…/station/jobs/JobBoard`, `WhistleOrder`), **wages, desertion and mutiny at dawn** (CR2 `…/crew/upkeep/ShipDayTick`, pure `WageRules`, `UpkeepDay`; coins from the ship's containers nearest the helm), **provisions consumed per day at dawn** (`ShipProvisions.advance`, morale through `CrewMorale.adjust`, work-speed factor), bunks (`…/crew/hammock/ShipBunks`, "informational until hiring"), a sailor → crew conversion (`…/law/brig/PrisonerOutcomes.pressGang`) and crew → sailor/pirate (`ShipDayTick.replace`, package-private), station contract (`StationKind.durationTicks/complete`, `StationBlock`, `StationSpot`, `Stations.order`), ART7 `…/crew/npc/StationPoses.register(kindId, resolver)` with `CrewPose` station poses and the `capstan_push` clip already in `crew_member.animation.json`, the capstan (`…/sailing/block/CapstanBlock`, `ShipControls.useCapstan` = toggle drop/raise, AN2a autonomous raise at `raise_speed`), the desk with tabs (`…/trade/net/MarketBackend.openDesk` sends Orders and Quests views; `…/trade/client/MarketScreen` `Tab` enum; the QST1 payload pattern `…/rpg/quest/QuestPayloads`), `TradeService.dockingFee(kind, player)` (CAR2 adds the rank waiver inside it; nothing calls it), ports with box and berths (`…/world/port/Port|PortIndex.containing|nearest`), generation-time mob placement (`…/world/outpost/Garrison|GarrisonPosts`, BOS1 `…/mob/captain/CaptainPosts|IslandCaptains|PirateCaptain` with a saved post, return-to-post, respawn after days), NPC interaction gestures on `CommonEvents.ENTITY_INTERACT` (`…/rpg/career/CareerInteractions`), `Reputation.piratesFriendly/villagersRefuse`, `Careers.navyRank/infamy`, the grapple's `GrappleRules.Holding` (hulls lying together), `DeckSpots` (WS3b worktree, pure deck-spot finder).

Missing, assigned below: hiring and dismissal (CRW1); pay from the captain's wallet, desertion at a port, visible meals (CRW2; progress.md's "wages missing" is stale, CR2 pays); a capstan station and an order-start hook (CRW3); a harbor master mob at the desks (PRT1a); fees actually charged (PRT1b); a plank between hulls (BRD1).

Missing helpers the orchestrator adds before wave 1 (one-liners): `CrewConfig.sub(name, comment)` (the `WorldSimConfig.sub` pattern, so packages add `crew.<sub>.*` without editing `CrewConfig`); `StationKind.begin(level, station, order)` default no-op called by `Stations.order` right after `state.start` (CRW3 needs a side effect at order start; `durationTicks` must stay a pure query because `JobBoard.post` calls `workTicks`); `…/crew/upkeep/CrewReplacement.replace` (make `ShipDayTick.replace` public under that name) and `ShipCoins.scan/take` (extract the coin-source scan from `ShipDayTick`); `…/mob/ai/ReturnToPostGoal` extracted from BOS1's `PirateCaptain`.

## Packages

### CRW1 — Hiring at the desk
**Goal.** A Crew tab at every harbor desk lists today's candidates; hiring pays a fee and puts a crew member aboard the player's ship moored at that port; the whistle dismisses crew; bunks cap the crew.

**Decisions.** (1) Candidates per port in saved data `pirates_n_ships_hiring` (`HiringData`: port → day, `List<Candidate(id, name, kind, fee)>`), regenerated lazily on a new day like QST1 offers, pure `HiringRules.candidates(kind, seed, n)` and `HiringRules.eligible(portKind, candidateKind, villagersRefuse, piratesFriendly, infamy, navyRank)`: villages offer sailors (refused below the villagers' threshold), islands pirates (needs `Reputation.piratesFriendly` or `Careers.infamy ≥ hiring.pirate_min_infamy`), outposts navy ratings (needs `Careers.navyRank ≥ MIDSHIPMAN`, §15 "crew recruited at outposts"). Kinds only; role skills stay open (§7.1). (2) Tab, not NPC talk: `CrewPayloads.CrewView/CrewAction` on the QST1 pattern, `HiringBackend.view/handle` wired in `MarketBackend.openDesk/registerPayloads` and a `Tab.CREW` in `MarketScreen` (PRT1a's harbor master opens the same desk, so both paths get the tab). (3) The hire needs the player's ship at the port: `ShipsAtPort.of(level, owner, port, radius)` (new, `…/world/port`): owned ships (`ShipRegistry` owner) whose `ShipBody.worldBounds()` touch the port box inflated by `hiring.ship_radius` (32), nearest the desk; none → "Moor your ship at this port first" (no following AI: crew cannot path over quays, §6). (4) Cap: `ShipBunks.count` crew < bunks when `hiring.require_bunks`, else `max_without_bunks`; refuse "No free hammock aboard". (5) The recruit is a `CrewMember` spawned at a free deck spot (`DeckSpots` from WS3b, else a local topmost-sturdy-column helper) nearest the desk, named, morale `crew.morale.start`, new saved `hiredBy` (Optional UUID) for the dismissal right; the ship stays the unit of membership (`ShipBunks.crewOf`), `ShipData.crew` stays unused. (6) Fee from the wallet (`Wallet.take`); the daily wage shown is CR2's flat `crew.wages.per_day` (per-member wages would change `WageRules`; open). (7) Dismissal: sneak-use with the whistle on a crew member (plain use still selects/releases) by the hirer or the ship's owner (anyone on an ownerless ship) → `CrewReplacement.replace(…, SAILOR)`; `/pirates crew hire <kind> [port]`, `dismiss <crew>` for operators and tests.

**Files** (`…/crew/hiring/`): `Candidate`, `HiringData`, `HiringRules` (pure), `Hiring` (service: hire, dismiss), `HiringBackend`, `CrewPayloads`, `HiringText`, `HiringCommands`, `HiringConfig`, `HiringModule`, `HiringGameTests`; JUnit `HiringRulesTest`; change `CrewMember` (hiredBy), `MarketBackend`, `MarketScreen`, `MarketText`, `CaptainsWhistleItem.interactLivingEntity`, `ClientMarketState`.

**API.** `Hiring.hire(player, port, candidateId) → Result(outcome, crew)`, `Hiring.dismiss(player, crew) → Outcome`, `HiringBackend.view(player, port)`, `ShipsAtPort.of(...)`.

**Config** `crew.hiring.*`: `enabled`, `candidates_per_port` 3, `fee_sailor` 10, `fee_pirate` 20, `fee_navy` 15, `pirate_min_infamy` (InfamyRank), `navy_requires_enlistment` true, `ship_radius` 32, `require_bunks` true, `max_without_bunks` 2.

**GameTests** (fake port registered with `PortRegistry.add` and removed at the end, the `ShipOrderGameTests` pattern; desk and offline `ServerPlayer` as in `HarborDeskGameTests`; a small assembled hull with one hammock inside the box): hire → a crew member stands on the hull, coins taken, the candidate gone from the view; second hire → `NO_BUNK`; no ship in the box → `NO_SHIP`; an island with pirate reputation 0 and no infamy → no candidates; an outpost without a rank → refused, with `Careers.setNavy(MIDSHIPMAN)` accepted; dismiss by a stranger refused, by the owner the member becomes a `Sailor`; `enabled=false` hides the tab (own batch); JUnit: candidates deterministic per day, eligibility matrix.

**Risks/open.** `MarketScreen` tab row is getting wide (five tabs: shrink to 56 px or a second row). Per-member wages and skills (open, later with "role skill").

### CRW2 — Wages from the wallet, desertion at a port, meals
**Goal.** Wages fall back to the captain's wallet, deserters leave only at a port, crew visibly eat and drink on a schedule; the provisions rules stay the pure per-day CR2 math.

**Decisions.** (1) `ShipDayTick` wages: coin sources = ship containers (CR2) plus, when `crew.wages.from_wallet` (default true), the owner's wallet when the owner is online in the level, as the farthest source (`WageRules.Source` with distance `MAX_VALUE`), so the chest pays first. (2) Desertion: `UpkeepDay` keeps deciding `deserts`; `ShipDayTick` then sets a saved `CrewMember.deserting` flag instead of replacing at once; `Desertions` (level tick every 100 ticks) replaces a deserting member by a `Sailor` on the quay (nearest berth of the port whose box or `desert_port_radius` the ship is in, `CrewReplacement`), or anywhere after `desert_anywhere_after_days`; the owner is told "X walked off at Y". (3) Meals: `MealVisits` at `meal_times` (two per day) seats every free member (not at a station, not asleep) for `meal_ticks` on an invisible `StationSeat`-style plot seat beside the nearest pantry (then water barrel), `work` pose until an `eat` clip exists (ART item); the ration is still booked at dawn (54 JUnit tests of C6 untouched); an order interrupts a meal. (4) Hunger lowering morale is CR2's provisions delta; supplies left go into the whistle's crew line (`CrewInfo`).

**Files** (`…/crew/upkeep/`): change `ShipDayTick`, `CrewMember` (deserting), new `Desertions`, `…/crew/galley/MealVisits`, `MealRules` (pure schedule), `MealGameTests`, `DesertionGameTests`; JUnit `MealRulesTest`.

**Config** `crew.wages.from_wallet`; `crew.desertion.at_port_only` true, `desert_port_radius` 48, `desert_anywhere_after_days` 3; `crew.meals.enabled`, `meal_times` [6000, 13000], `meal_ticks` 100.

**GameTests.** Empty chest + owner with coins → coins taken from the wallet, `PayRecord` paid; `from_wallet=false` → unpaid (own batch); a deserting member on a ship outside any port stays `deserting` and leaves when a fake port box is registered around the ship (Sailor at the berth); `ShipDayTick.dawn` after `desert_days` sets the flag; at a meal tick a free member sits beside the pantry and gets up after `meal_ticks`; an order during the meal frees the seat; `meals.enabled=false` nobody moves.

**Open.** Should a meal visit take the ration at that moment (then the HUD "days left" moves twice a day)? Recommended no.

### CRW3 — Capstan station
**Goal.** A crew member at the capstan drops and raises the anchor on order, pushing the bars (`capstan_push`), from the whistle, the command and the job board.

**Decisions.** (1) `CapstanBlock implements StationBlock` with `CapstanStation implements StationKind<AnchorOrder>` (`AnchorOrder` enum `DROP_ANCHOR`, `RAISE_ANCHOR` implementing `CrewOrder`, added to `CrewOrder.all()` and as two `WhistleOrder` entries, icon the anchor item). (2) Split `ShipControls.useCapstan` into `dropAnchor(level, pos)` and `raiseAnchor(level, pos)` returning a result enum plus the message (the toggle stays for players); `dropCheck` (ground within the chain) becomes callable without side effects. (3) DROP: `durationTicks` = `capstan.drop_ticks` when RAISED/RAISING, 0 when already out, −1 without ground or anchor off; `complete` → `dropAnchor`. RAISE: `durationTicks` = raise estimate (chain ÷ `raise_speed` × 20, at least `min_raise_ticks`), −1 when RAISED; `begin` → `raiseAnchor` (the crew's push is the raise); `complete` re-orders while still RAISING (pump pattern). A hand leaving mid-raise does not stop the winding (AN2a's raise is autonomous, as for a player). (4) `CapstanPoses` resolver → `CrewPose.CAPSTAN_PUSH("capstan_push")` appended to the `STATION` array (append only), facing the capstan, while OPERATING; the spot is `StationSpot`'s default. Orbiting the drum is not attempted.

**Files** (`…/station/capstan/`): `AnchorOrder`, `CapstanStation`, `CapstanPoses`, `CapstanConfig`, `CapstanGameTests`; change `CapstanBlock`, `ShipControls`, `CrewOrder`, `WhistleOrder` (merges with WS4a's `FIRE_AT_WILL`), `CrewPose`, `StationModule` (register the pose).

**Config** `crew_stations.capstan.*`: `enabled`, `drop_ticks` 40, `min_raise_ticks` 20.

**GameTests** (the `SailingGameTestsControls` hull over a floor): crew at the capstan + DROP → `isWorking` for 40 ticks, then phase DROPPING/HOLDING; RAISE while holding → RAISING at once, RAISED within 2× the estimate, pose `CAPSTAN_PUSH` meanwhile; no ground → `NOT_APPLICABLE` with the reason; `WhistleOrders.handle("drop_anchor")` posts a job a free hand claims (`JobBoard.pass`); `Stations.workTicks` never moves the anchor; `enabled=false` → `NOT_APPLICABLE` (own batch).

### PRT1a — Harbor master NPC
**Goal.** A harbor master stands behind every village and outpost desk (and the island fence's), placed at generation, persistent, returning to his post; talking to him opens the desk.

**Decisions.** (1) `HarborMaster extends Sailor`, `MobKind.HARBOR_MASTER` (BOS1 added `PIRATE_CAPTAIN` the same way), faction CIVILIAN, `misc`, no natural spawn, the sailor's crime tags; a scripted texture (coat and hat) via `tools/gen_entity_textures.py`. (2) `HarborMasterPosts` (pure, the `CaptainPosts` pattern): `dock_head` (7,1,7) facing north, `fort_gate` (2,1,8) facing east, `camp_start` (10,1,2) facing west (behind each desk's harbor-master side); `HarborMasters.place(level, chunkBox, pieces, port)` from `PortStructure.afterPlace` for every kind (island behind `at_pirate_islands`), `stationary`, saved post and port, `ReturnToPostGoal`, respawn after `respawn_days` via a per-port `HarborMasterRegistry` (the `IslandCaptains` tick). (3) `HarborMasterInteractions` on `ENTITY_INTERACT` (main hand, not sneaking, not a prisoner) → the bound desk nearest him within `desk_reach` → `HarborDeskService.use(player, desk)` (sessions stay desk-bound, reach unchanged); hostile or fleeing → "not now". Direct desk use stays unless `harbor_desks.direct_use=false` ("Talk to the harbor master"). Greeting line on open.

**Files** (`…/mob/harbor/`): `HarborMaster`, `HarborMasterPosts`, `HarborMasters`, `HarborMasterRegistry`, `HarborMasterInteractions`, `HarborMasterConfig`, `HarborMasterModule`, `HarborMasterGameTests`; JUnit `HarborMasterPostsTest`; change `MobKind`, `MobContent`, `MobConfig`, `PortStructure`, `HarborDeskService`, `PirateCaptain` (uses the extracted goal), renderer registration in `mob/client`.

**Config** `world.harbor_master.*`: `enabled`, `at_pirate_islands` true, `respawn_days` 3, `return_distance` 8; `harbor_desks.direct_use` true.

**GameTests.** `HarborMasters.spawn` beside a bound desk + an offline `ServerPlayer`: `onEntityInteract` opens a session (`MarketBackend.isOpen`), sneaking passes; pushed 12 blocks he is back within 300 ticks; discarded, the registry respawns him after the day advance (`/time` edge via the registry's `check`); `direct_use=false` refuses the block (own batch); JUnit: posts per piece and rotation.

### PRT1b — Docking fees in game
**Goal.** A ship that ties up or anchors inside a navy outpost's box pays `TradeService.dockingFee` once per visit, with CAR2's waiver and the reputation waiver; unpaid dues block the desk.

**Decisions.** `DockingFees` level tick every `check_interval_ticks`: for each loaded owned ship whose centre `PortIndex.containing` puts in a NAVY_OUTPOST (or any kind; `PortFees` already yields 0 elsewhere) and that is docked = anchor `HOLDING`/`ANCHORED` (`SailingRuntime.anchorStatus`) or within `berth_radius` of a berth with speed < 0.1 → `PortVisitData` (`pirates_n_ships_port_visits`: ship, port, day, paid, owed) charges once per `fee_period_days`: owner's wallet when online, else the ship's coins (`ShipCoins`, `charge_ship_chest`), else `owed`; `HarborDeskService.use` refuses with "Harbor dues owed: N" while owed (`refuse_desk_when_owed`), paying by talking to the master or using the desk with coins clears it. Owner messages: "Harbor dues at <port>: 10 doubloons" / "waived" (the harbor master's name when PRT1a is present, else the port).

**Files** (`…/trade/fees/`): `DockingFees`, `DockingRules` (pure: docked test, charge decision), `PortVisitData`, `FeeText`, `FeesModule`, `FeesGameTests`; JUnit `DockingRulesTest`; change `TradeConfig` (fees section), `HarborDeskService.use`.

**Config** `cargo_trade.port_fees.*` existing plus `check_interval_ticks` 100, `berth_radius` 6, `fee_period_days` 1, `charge_ship_chest` true, `refuse_desk_when_owed` true.

**GameTests.** Fake navy port around an assembled hull with the anchor dropped (`ShipControls.useCapstan`) and an owner with coins → one check takes the fee, a second takes nothing; `Careers.setNavy(LIEUTENANT)` → 0 (CAR2); no coins → owed and the desk refuses, coins given → cleared; a hull drifting through without anchoring pays nothing; `enabled=false` (own batch).

### BRD1 — Boarding plank
**Goal.** A plank item laid from one gunwale over the gap to a ship lying alongside (G11 holding), walkable, breaking when the hulls part, registered for later crew crossing.

**Decisions.** (1) A **block run, not an entity**: standing on blocks of a Sable ship is proven; standing on moving entities is not. `BoardingPlankBlock` (facing, `segment` 0..3, slab-thin collision) placed by `BoardingPlankItem.useOn` on the clicked gunwale block of ship A: the run starts beside the clicked face, in A's plot (the plot expands, sable-notes §1.2), `PlankRun.compute` (pure) takes the first cell whose world position lies over a sturdy block of another ship B within `max_length` and ±1 block height (B found by `SableShips.all` bounds + `toPlot`), all cells free; else "No deck within reach". (2) The first segment has a block entity storing B's id and the far cell's plot position on B; its ticker every `check_interval_ticks` breaks the run (drops one item, wood break sound) when the far end's world position is farther than `break_distance` from B's cell or B is gone; breaking any segment breaks all (hammock pattern). (3) `BoardingPlanks.between(a, b)` lists live planks for the boarding AI (BRD2, later).

**Files** (`…/combat/boarding/`): `BoardingPlankBlock`, `BoardingPlankBlockEntity`, `BoardingPlankItem`, `PlankRun` (pure), `BoardingPlanks`, `BoardingConfig`, `BoardingContent` (block, item, recipe, model via datagen or a Blockbench item), `BoardingModule`, `BoardingGameTests`; JUnit `PlankRunTest`.

**Config** `boarding.plank.*`: `enabled`, `max_length` 4, `break_distance` 1.5, `check_interval_ticks` 10.

**GameTests** (`EMPTY_40` basin, two assembled hulls three blocks apart): placing from A → segments in A's plot, the far end over B, `between(A,B)` non-empty, collision shape non-empty; `B.placeAt` three blocks away → broken within 20 ticks and one item dropped; hulls eight blocks apart → refused; `enabled=false` refused (own batch).

**Risks/open.** Sable contact between A's plank and B's deck when the hulls roll (jitter; test with the far end one block above B's deck); a ladder variant for height differences; reach when the clicked face is a rail.

## Sequencing and shared files
- Orchestrator first: the helpers above, after the `merge` batch lands.
- Wave 1 (parallel): CRW1, CRW3, PRT1a, PRT1b, BRD1.
- Wave 2: CRW2 (after CRW1: `CrewReplacement`, `hiredBy`, the whistle crew line).
- Merge touch points: `core/ModModules` (all), `CrewOrder`/`WhistleOrder`/`CrewPose`/`StationKind`/`Stations`/`ShipControls` (CRW3), `MarketBackend`/`MarketScreen`/`MarketText`/`CaptainsWhistleItem`/`CrewMember` (CRW1; CRW2 adds a flag to `CrewMember`), `HarborDeskService` (PRT1a and PRT1b both), `PortStructure`/`MobKind`/`MobContent`/`MobConfig`/`PirateCaptain` (PRT1a), `TradeConfig` (PRT1b), `ShipDayTick` (CRW2). Each package adds a `docs/playtests/<name>.md` and a guide page.

### Critical Files for Implementation
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/crew/npc/CrewMember.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/station/Stations.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/sailing/ship/ShipControls.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/trade/net/MarketBackend.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/trade/desk/HarborDeskService.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/crew/upkeep/ShipDayTick.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/world/structure/PortStructure.java
