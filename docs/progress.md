# Progress

Status of every roadmap milestone (design.md §20) and orchestrator work package (`docs/prompts/initial-orchestrator.md`).
Statuses: **todo** / **in progress** / **done** / **blocked: needs playtest** / **blocked: other** (with reason).

"Done" means: `./gradlew build` passes, `./gradlew :neoforge:runGameTestServer` passes, and the new logic has tests.

Last updated: 2026-10-06 (second session: wave 1 of phase C running).

## Current state

`main` builds (`./gradlew build`, 544 JUnit tests) and `./gradlew :neoforge:runData` leaves no diff. **The GameTest suite (146 tests) is flaky right now:** `AssemblyGameTests.disassemblyPutsBlocksBackOnTheGrid` failed in 2 of 5 full runs with "chest content lost on disassembly". All other tests pass in every run. D2c is investigating. Nothing is pushed: local `main` is ahead of `origin/main`.

Merged in this session: foundation follow-ups **A2** and **A3**, all of phase C (**C1** to **C8**), spikes **D1** (assembly) and **D2** (dry hull, with the fix **D2b**), and all of phase E (**E1a** law in the world, **E1b** brig and shackles, **E1c** flags, **E2** pantry and water barrel, **E3** cargo containers and market backend).
Running now: **D3a** (spike 3 part 1: sails move the ship) and **D2c** (the flaky disassembly test). **A4** (platform hooks) is merged.
After that: **D3b** (helm steering and anchor), then **D4** (spike 4, crew station).

How merges work in this phase:
- Every merged branch and its worktree is deleted right after the merge (requested by the human).
- `core/ModModules` and the generated resources (`common/src/generated/resources`, especially `lang/en_us.json` and `.cache`) are touched by every package. The orchestrator resolves `ModModules` by keeping all module lines, and resolves generated files by re-running `./gradlew :neoforge:runData` on the merged tree.
- After each merge: `./gradlew build` and `./gradlew :neoforge:runGameTestServer` on `main`.

Incidents:
- Around 17:51 and 18:21 a short connection loss stalled several agents. All recovered by themselves except A2, which was stopped and resumed from its transcript at 18:33 with its work intact.
- At about 19:00 the network dropped again and all five running agents (C3, C5, C8, D2, A3) ended with API connection errors. Their worktrees and uncommitted work were intact, and all five were resumed from their transcripts at 19:40.
- Flaky test: `AssemblyGameTests.disassemblyPutsBlocksBackOnTheGrid` (spike 1) failed three times in about 14 full runs with "pig not on deck". Two causes were found and fixed with spike 2: an off-by-one in the passenger placement, and tests running millions of blocks from the origin. One failure with a different message ("chest content lost on disassembly") was seen in 12 runs afterwards. D2c is investigating that one.
- Token budgets: the spike 1, spike 2 and D2b agents each used their whole budget (200k tokens), and two of them stopped before finishing. Later packages are cut smaller and told to keep Gradle output out of their context.
- The merge commits `2e3fcac` (C6) and `3978c02` (A2) **don't compile**: the orchestrator wrote a malformed module list into `core/ModModules` (a shell quoting mistake) and committed without checking the result. `b97293d` fixes it. Keep this in mind when bisecting. Since then the orchestrator builds and runs the GameTests before committing a merge.

Follow-ups for later packages (small, not blocking):
- `ship/assembly` (terrain tag) and `ship/hull` (watertight tags) can now use required vanilla tag references (`addTag(BlockTags.X)`), since A3 fixed the tag datagen. Both still use their workarounds.
- `sailing` should register `ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientWind.reset())`.
- The trade agent's balance notes: unit prices round harshly for cheap goods (a single sugar costs 2 and sells for 2), so the market screen should show prices per stack. Trade route profit fades after roughly 250 to 400 units per port pair.
- Rum is both a provision and a trade good. Whatever sums a ship's weight has to count each stack once.

## Work packages

| Phase | Package | Status | Notes |
|---|---|---|---|
| A | Foundation (milestone 0): template cleanup, Sable dependency, platform services, registration / config / networking / attachment helpers, datagen, JUnit, GameTest harness, CI, playtest checklist | done (headless part) | Merged. Build, JUnit (5), GameTests (2) and datagen verified on `main`. The client part is in the milestone 0 playtest. See "Foundation notes" below. |
| A2 | Foundation follow-up: entity-type and foreign-namespace tags and arbitrary JSON in datagen, shared datapack definition loader with client sync, GameTest helper for config changes | done | Merged. `core/data` (`DefinitionType`), `DataContributions.tags/json/encoded/definitions`, `ConfigOverrides`, new event `DATAPACK_SYNC`. Documented in design.md §3.3. Client sync is in the playtest `docs/playtests/data-and-config.md`. |
| B | Sable investigation → `docs/sable-notes.md` | done | Merged (docs only, so no build needed). Reviewed by spot-checking about 25 API claims against `refs/`, all matched. See "Sable findings" below. |
| C1 | Hull analysis + flooding model (§4.2, §4.5), pure logic | done | Merged. 44 JUnit tests, 2 GameTests. Package `ship/hull` (spill-height analysis, flooding simulation, block classifier). Full analysis of a 64×32×64 hull: about 93 ms, flood tick: under 0.2 ms. |
| C2 | Wind and sail model (§5.1, §5.2), pure logic | done | Merged. 58 JUnit tests, 2 GameTests. Package `sailing` (`wind`, `force`). Nothing is visible in-game until spike 3. |
| C3 | Melee resolution core (§8.5), pure logic | done | Merged. 76 JUnit tests, 8 GameTests. Package `combat/melee` (state machine, hit geometry, resolution, weapon definitions for rapier, cutlass and saber, server-side `MeleeService`). No input layer yet, so nothing can be tried in-game. |
| C4 | Law system logic (§13.1, §13.2) + false-flag detection math (§4.7) | done | Merged. 106 JUnit tests and 6 GameTests. Package `law` (`crime`, `bounty`, `flag`, `LawService`, `/pirates law` debug commands). Playtest: `docs/playtests/law-commands.md`. | |
| C5 | Trade economy logic (§10.3) | done | Merged. 45 JUnit tests, 6 GameTests. Package `trade` (12 default goods, port markets, contracts, plunder rules, cargo weight, `TradeService`). |
| C6 | Provisions logic (§7.4) | done | Merged. 54 JUnit tests, 3 GameTests. Package `crew/provisions`. |
| C7 | Config groups and values (§17) | done | Merged. Sections `ships`, `waves`, `hazards`, `crew`, `combat`, `survival`, `world`, `world_simulation` (server) and `wave_effects`, `audio` (client), loaded by `core/settings/SettingsModule`. Nothing reads them yet. Almost every default is the agent's own choice: see "Defaults to review". |
| C8 | Basic items and blocks + datagen + placeholder textures | done | Merged. 19 items and 13 blocks with models, recipes, loot, lang and tags, 22 GameTests, and `tools/gen_placeholder_textures.py`. Playtest: `docs/playtests/items-and-blocks.md`. |
| D1 | Spike 1: assembly (§4.1) | blocked: needs playtest | Merged and green headlessly: 14 JUnit tests and 10 GameTests that assemble, name, refuse and disassemble real sub-levels. Lives in `common` (`ship/assembly`, Sable adapter in `ship/sable`, `ship/ShipData`). Playtest: `docs/playtests/milestone-1.md`. |
| D2 | Spike 2: dry hull (§4.3, §4.4) | blocked: needs playtest | Merged and green headlessly. Hull runtime per ship (`ship/hull/runtime`), Sable water occlusion regions with client sync, flooding with breaches, buoyancy correction, persistence. Review found a ship "moving at 72 m/s" in a test: the cause was the GameTest server placing tests up to 15 million blocks out, where Sable's 32-bit physics fails (fixed by D2b, which also made the sea detection look only at the hull's own bottom). Rendering is unverified. Playtest: `docs/playtests/milestone-2.md`. |
| D2c | Flaky test `disassemblyPutsBlocksBackOnTheGrid` ("chest content lost") | in progress | It was rare with 140 tests (1 in 12 runs) and failed in 2 of 5 runs with 146 tests, so it depends on load or timing. The agent has to find out whether it is a test problem or a real bug. |
| D3a | Spike 3 part 1: sail blocks with trim, sail winch, sailing runtime applying wind and keel forces, wind override command | in progress | Spike 3 is split in two, because spikes 1 and 2 each used up an agent's whole token budget. |
| D3b | Spike 3 part 2: helm steering (rudder) and anchor (capstan) | todo | After D3a. Ends at a playtest gate. |
| D4 | Spike 4: crew station (§6) | todo | Waits for D3b. Ends at a playtest gate. |
| A3 | Foundation maintenance: required vanilla tag references in datagen, `ConfigValue` set/reset documentation, clash check for client and server section names, `ClientEvents.CLIENT_DISCONNECT` | done | Merged. |
| E1a | Law in the world: crimes reported from damage, death and theft, entity tags, bounty proof item, wanted level sent to the client | done | Merged. 13 GameTests. New events `CONTAINER_OPEN` / `CONTAINER_CLOSE`. Playtest: `docs/playtests/law-world.md`. |
| E1b | Brig and shackles (§13.3): capture, prisoners, lockable brig door, cells, escapes | done | Merged. 23 JUnit tests, 11 GameTests. `/pirates brig` commands. Added two access transformer lines (`Mob.goalSelector`, `targetSelector`). Playtest: `docs/playtests/brig.md`. |
| E1c | Flags (§4.7): flag state on the flagpole, hoisting, striking colors | done | Merged. 22 JUnit tests, 14 GameTests. Three flag items, banners as custom flags, `/pirates flag` commands. Ship-level allegiance waits for the spikes. Playtest: `docs/playtests/flags.md`. |
| E2 | Pantry and water barrel as real containers, connected to the provisions rules | done | Merged. 12 JUnit tests, 13 GameTests. Package `crew/galley`, `/pirates provisions` commands. Consumption by a crew waits for crew NPCs. Playtest: `docs/playtests/pantry.md`. |
| E3 | Cargo crate and barrel as bulk containers, doubloon wallet, market backend | done | Merged. 18 JUnit tests, 11 GameTests. `trade/cargo`, `trade/coin`, `trade/exchange`, `trade/net`, `/pirates trade` commands. The market screen itself and ports come later. Playtest: `docs/playtests/cargo-and-market.md`. |
| A4 | Platform hooks asked for by phase E: item capability for pantry and cargo containers, item tooltip event (plunder mark), entity interaction event (shackles on villagers), test hygiene | done | Merged. New `Services.CAPABILITIES`, `ClientEvents.ITEM_TOOLTIP`, `CommonEvents.ENTITY_INTERACT`. Four GameTests live in the NeoForge module (`NeoForgeGameTests`), because the capability lookup is a NeoForge API. |

## Roadmap milestones (design.md §20)

| # | Milestone | Status | Notes |
|---|---|---|---|
| 0 | Project setup | blocked: needs playtest | Everything headless is done and green. "Sable loads in the NeoForge dev client" and the test block's look need the playtest in `docs/playtests/milestone-0.md`. |
| 1 | Spike: assembly | blocked: needs playtest | Phase D1 is merged. `docs/playtests/milestone-1.md`. |
| 2 | Spike: dry hull | blocked: needs playtest | Phase D2 is merged. `docs/playtests/milestone-2.md`. |
| 3 | Spike: wind + sails | in progress | Phases D3a and D3b. |
| 4 | Spike: crew station | todo | Phase D4. |
| 5 | Config framework + weapons | todo | Partly covered by C7 + C8 (items exist, config defined). Firearm behavior and the config screen are not in this session's scope. |
| 6 | Flooding + damage | todo | Flooding simulation is C1. World integration needs spike 2. |
| 7 | Melee combat core | todo | Resolution logic is C3. Input, sync and animation library are later. |
| 8 | Melee animations + NPC duelists | todo | |
| 9 | Cannons + grappling hook v1 + boarding | todo | |
| 10 | Ship identity + shipwright | todo | Decorative blocks come with C8. |
| 11 | World | todo | Needs human-built structure NBT. |
| 12 | Mobs | todo | Needs models and animations (Blockbench). |
| 13 | Law + brig | todo | Logic is C4, integration is phase E. |
| 14 | Trade + cargo | todo | Logic is C5, integration is phase E. |
| 15 | Crew command system + provisions | todo | Provisions logic is C6, integration is phase E. |
| 16 | Sea chest + survival | todo | |
| 17 | Weather and hazards | todo | |
| 18 | Audio | todo | |
| 19 | RPG + careers + kraken + duel bosses | todo | |
| 20 | World simulation | todo | |
| 21 | Melee extras (optional) | todo | |
| 22 | Fabric port | todo | |

## Decisions made by the orchestrator

| Date | Decision | Why |
|---|---|---|
| 2026-10-06 | Sable version **2.0.6** (`dev.ryanhcode.sable:sable-common-1.21.1` / `sable-neoforge-1.21.1` from `https://maven.ryanhcode.dev/releases`). | It is the version checked out in `refs/sable` and the latest on the Sable maven. |
| 2026-10-06 | Phase B starts together with phase A instead of late in phase A. | It needs no working build, only `refs/`. |
| 2026-10-06 | Phase C runs in waves of about four agents instead of all eight at once. | The machine has 16 GB RAM, and every agent runs Gradle (3 GB heap) plus a GameTest server. |
| 2026-10-06 | Agent worktrees fork from `origin/main`, not local `main`. Every agent prompt after phases A and B must start with `git merge --ff-only main`. | The orchestrator must not push, so `origin/main` doesn't contain merged work. |
| 2026-10-06 | No new agents are spawned after phases A and B finish. Phase C starts in the next session. | Requested by the human, so the session can be reopened cleanly (which should also make the `implementer` agent type available). |
| 2026-10-06 | From the second session on, subagents use `subagent_type: "implementer"` again. | The restarted session lists the agent type. |
| 2026-10-06 | A design module (§3.2 package) may have several `ModModule` implementations, one per work package or sub-feature (e.g. `ship/hull/HullModule`, id `"ship.hull"`). | Parallel packages in the same design module (C1, C7 and C8 all touch `ship`) would otherwise share one module class. A2 documents this in design.md §3.3. |
| 2026-10-06 | Config ownership: each phase C package defines the §17 group of its own feature (C1 `flooding`, C2 `wind` + `sailing`, C3 `melee`, C4 `law` + `flags_brig`, C5 `cargo_trade`, C6 `provisions`). C7 defines only the remaining groups (ships, waves, hazards, crew, combat, survival, world, world simulation, audio). | The orchestrator prompt allows either "C7 early" or "each package adds its own section". Own sections let all packages run in parallel without waiting for C7. |
| 2026-10-06 | Hull analysis uses a spill-height (priority-flood) model instead of a plain "enclosed air" flood fill: a cell is floodable volume when it lies below its own pour point to the outside. | With the plain flood fill of §4.2, an open-topped hull or a hull with an open deck hatch counts as "outside" and would have water inside. The new rule keeps it dry while the rim is above the waterline, floods it through holes below the waterline, and also covers waves spilling over a low rim (§5.4). To be written into design.md §4.2 when C1 is merged. |
| 2026-10-06 | C2 also covers rudder, keel drag and anchor as pure force functions, and a thin server-side wind service with client sync. | Spike 3 needs all of them, and they are pure math like the sail model. |
| 2026-10-06 | Spike 1 starts in parallel with phase C wave 1 and is written in `common`, not in `neoforge/`. | The four spikes are sequential and form the longest chain, and spike 1 only needs phases A and B. `docs/sable-notes.md` §7 found every needed Sable API in `sable-common`, so nothing has to be moved later. |
| 2026-10-06 | Spike 1 defines the config section `assembly` (assembly enabled, max block count, disassembly thresholds). C7's "Ships" group leaves those two values out. | The spike needs them now, and two definitions of the same value would clash. |
| 2026-10-06 | Spike 2 starts without waiting for the human's playtest of spike 1. The same will apply to spikes 3 and 4. | Nobody is watching, and spike 1's GameTests assemble and disassemble real sub-levels headlessly. The risk is that a problem only visible in the client (e.g. the ship not floating as expected) is found late. The playtests are listed in order at the end of this file. |
| 2026-10-06 | C1: openings (doors, hatches) are never passable in the hull analysis. They are links whose open state the flooding simulation reads. Destroyed hull blocks are tracked as breaches. | Opening or closing never needs a re-analysis, and an open hatch or a hole below the waterline floods at a rate instead of at once. Written into design.md §4.2. |
| 2026-10-06 | D1: a dock touching the hull is gathered with the ship. Ships are moored with a one-block gap. Logs count as ship blocks, leaves as terrain. Default block limit 2048. | Simple and predictable, and the block limit stops runaway gathers. Written into design.md §4.1. |
| 2026-10-06 | C4: the criminal record attachment is not synced to clients. | The foundation syncs to every tracking client on each change, and decay changes the record often. A HUD will get the wanted level through its own payload. |
| 2026-10-06 | GameTests run within ±250,000 blocks of the origin (`mixin/MixinGameTestServer`). | Vanilla places the test grid up to ±15 million blocks out, where Sable's 32-bit physics makes ships fall through blocks. The mixin only affects the GameTest server. |
| 2026-10-06 | Spike 3 is split into D3a (sails and keel) and D3b (helm and anchor). | Agents ran out of token budget on spikes 1 and 2. Smaller packages finish. |
| 2026-10-06 | Phase E stops at entry points that take plain inputs (a set of positions, a headcount, a flag reading). Nothing in phase E looks up ships. | The orchestrator prompt says to stop before anything that needs a working spike. The ship-level wiring (a ship's flag, cargo weight, prisoners and provisions) comes after the spikes are playtested. |
| 2026-10-06 | E1a: villagers and wandering traders are law-protected, iron golems are enforcers and never criminals, monsters get no record by default. Theft needs a village container that no player placed, a net removal, and a witness with line of sight. The bounty proof goes into the killer's inventory. | See the agent's reasoning in the commit and in `docs/playtests/law-world.md`. All values are config. |
| 2026-10-06 | E1b: any mob can be captured except bosses (tag `pirates_n_ships:not_capturable`), at 25% health or less. The brig door's owner is the player who placed it. | A denylist makes new mobs work by default. |
| 2026-10-06 | E2: players can put anything into a pantry, but automation may only insert provisions and only extract leftovers. Spoiled food becomes rotten flesh. | A hopper chain can feed the galley without filling it with junk, and a hopper below works as a waste chute. |
| 2026-10-06 | E3: a crate holds 32 stacks of one item, a barrel 1536 items. Plundered and clean goods never mix in one container. A broken container keeps its content in the item. | Barrels suit small stacks such as rum, crates suit full stacks. |
| 2026-10-06 | Subagents read `refs/` from the main checkout by absolute path. | `refs/` is git-ignored, so it doesn't exist inside agent worktrees. |
| 2026-10-06 | Subagents are spawned as `general-purpose` agents pinned to Opus, with the instructions from `.claude/agents/implementer.md` referenced in the prompt, instead of `subagent_type: "implementer"`. | This session doesn't list the `implementer` agent type ("Agent type 'implementer' not found"), probably because the definition was added after the session's agent list was loaded. Model and instructions are the same as intended. |

## Sable findings that change the design (from `docs/sable-notes.md`)

These are written into `docs/design.md` (§3.1, §4.1, §4.3–§4.5, §4.9, §5.3, §6) as decisions to be confirmed in the spikes, and the Sable questions in §21 are marked resolved.

| Finding | Consequence |
|---|---|
| Buoyancy is computed natively and **can't be overridden or disabled per ship** (notes §4.1, §4.2). Only solid hull blocks displace water, air inside the hull adds nothing. | Hard limitation. Dry-volume buoyancy and flood-water weight become an extra force group on top of Sable's hull buoyancy (§4.5). The constants must be tuned together in a playtest. |
| Sable has **no disassembly API** (notes §2.4). | We build disassembly from its public pieces (`AssemblyTransform`, `moveBlocks`, `moveTrackingPoints`), with yaw in 90° steps only, and our own obstruction check. |
| Sable already has **water occlusion regions** with gameplay mixins and a depth-mask renderer in `sable-common` (notes §4.4). | Spike 2 uses these regions instead of our own fluid-query mixins and mask renderer (§4.3, §4.4). Left to us: client sync of regions, rebuilds, partial flooding, item flotation, block placement checks. |
| Native water drag is the same in every direction (notes §4.3). | Sailing across the wind needs our own keel (sideways) drag. This goes into spike 3. |
| A structure template can be placed directly into a new sub-level (notes §2.5). | Shipwright pickup needs no placement at the berth (§4.1, §21). |
| All needed APIs, including events, are in `sable-common` (notes §7). | No Sable-specific platform service is needed. Much of it is outside `api.*`, so all Sable calls go through one adapter package. |
| Assembling in water leaves an air pocket in the world, and disassembling leaves sea water inside the hull (notes §2.2, §2.4). | Spike 1 has to handle both. |

## Foundation notes (phase A)

How feature modules plug in is documented in design.md §3.3. Deviations from the orchestrator prompt and from `CLAUDE.md`, all accepted in review:

| Deviation | Why |
|---|---|
| There is no `Services.EVENTS`. Events are static callback hubs in `common` (`platform/event/CommonEvents`, `ClientEvents`) that the NeoForge module fires. | Event logic is plain common code, so a ServiceLoader service adds nothing. `CLAUDE.md` still lists `.EVENTS` as an example service: **the human may want to update that line.** |
| GameTests use our own `@ModGameTest` annotation plus one vanilla `@GameTestGenerator` method per class, not vanilla `@GameTest`. | NeoForge takes the template namespace of a vanilla `@GameTest` from its own `@GameTestHolder`, which `common` can't use. Without it the tests land in the `minecraft` namespace and are filtered out. It is still the vanilla GameTest framework. |
| ModDevGradle `2.0.49-beta` → `2.0.140`. | The version Sable 2.0.6 builds with. |
| `common/src/main/resources/META-INF/accesstransformer.cfg` with two entries (`BlockEntityType$BlockEntitySupplier`, `IntrinsicHolderTagsProvider$IntrinsicTagAppender`). | Needed by the registration and tag datagen helpers in `common`. The Fabric port needs matching access-widener lines. |
| Sable's NeoForge artifact is added with Create, Ponder, Flywheel and Registrate excluded. | Sable's published runtime variant lists them, but only uses them for optional compat. We have no Create dependency. |

Known harmless log noise with Sable: `Failed to apply tag physics properties. Unknown block: create:flywheel` on every level start (Sable's own data mentions a Create block).

## Defaults to review (chosen by agents, not given by the spec)

All of these are config values, so they can be changed without code. The ones most worth a look:
- **Crew:** `mutiny_enabled = true` (the spec only says "behind a config toggle").
- **Waves:** client `camera_sway = true` (could cause motion sickness).
- **Ships:** `wreck_persistence_days = 0`, meaning wrecks stay forever (could cost performance on big servers). Shipwright build times 2 / 3 / 4 days and prices 200 / 350 / 500 doubloons for sloop / merchant cog / brigantine.
- **Hazards:** waterspout chance 0.1 per minute in a thunderstorm, whirlpool 0.05 per day, kraken 0.02 per day, each per player at sea.
- **World simulation:** raids typically after 45 to 60 minutes at one settlement, then a 5-day cooldown.
- **Law:** bounty threshold at a score of 50, navy bounty = score × 2, alive claim pays 1.5×, decay 10 points per day.
- **Assembly:** block limit 2048, disassembly allowed below 0.3 m/s and 6° tilt.
- **Provisions:** 6 nutrition per crew member per day, fresh food keeps 5 days, scurvy after 8 days.
- **Sailing:** see the tuning questions in the spike 3 playtest (to be written).

## Playtests for the human

In this order:
1. [`docs/playtests/milestone-0.md`](playtests/milestone-0.md): Sable loads in the dev client, the mod list and config screen are correct, the test block appears and renders, `/sable spawn sphere 3` works.
2. [`docs/playtests/milestone-1.md`](playtests/milestone-1.md): **the important one.** Build the boat from the recipe, assemble it at the helm in the sea, walk on deck, shove it, disassemble it, check the water in both directions, the block limit, a chest keeping its items, naming, and rejoining. Spikes 2 to 4 build on this.
3. [`docs/playtests/milestone-2.md`](playtests/milestone-2.md): **the second important one.** No water inside the hull, no swimming below deck, a breach floods the hold, a flooded ship sinks, a ship in a dry dock stays put. It also has the tuning questions for the buoyancy values.
4. [`docs/playtests/law-commands.md`](playtests/law-commands.md): criminal score, navy and player bounties, claims, fines, persistence across death and reload, and the config toggle, all through `/pirates law` commands. Not a gate for other work.
5. [`docs/playtests/items-and-blocks.md`](playtests/items-and-blocks.md): every item and block in the creative tab with texture, name, model, drops and recipe. Not a gate for other work.
6. [`docs/playtests/data-and-config.md`](playtests/data-and-config.md): definitions synced to clients, a broken datapack file, tags, and the config screen. Not a gate for other work.
7. The phase E features, each through its own debug commands, in any order and none of them a gate: [`law-world.md`](playtests/law-world.md) (crimes from hitting villagers and from theft, bounty proof), [`brig.md`](playtests/brig.md) (shackles, leading, cells, the lockable door), [`flags.md`](playtests/flags.md) (hoisting and striking flags), [`pantry.md`](playtests/pantry.md) (pantry, water barrel, consumption and spoilage), [`cargo-and-market.md`](playtests/cargo-and-market.md) (bulk containers, coins, buying and selling at test ports, contracts).
