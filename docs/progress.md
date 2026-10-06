# Progress

Status of every roadmap milestone (design.md §20) and orchestrator work package (`docs/prompts/initial-orchestrator.md`).
Statuses: **todo** / **in progress** / **done** / **blocked: needs playtest** / **blocked: other** (with reason).

"Done" means: `./gradlew build` passes, `./gradlew :neoforge:runGameTestServer` passes, and the new logic has tests.

Last updated: 2026-10-06 (second session: wave 1 of phase C running).

## Current state

`main` is green after phases A and B. Nothing is pushed: local `main` is ahead of `origin/main`.

Running now (wave 1, five agents): **A2** foundation follow-up, **C1** hull + flooding, **C2** wind + sails, **C4** law, **C6** provisions.
Waiting for A2 (wave 2): **C3** melee, **C5** trade, **C7** remaining config groups, **C8** basic items and blocks.
After that: phase D spikes (using `docs/sable-notes.md`) and phase E integration.

How merges work in this phase:
- Every merged branch and its worktree is deleted right after the merge (requested by the human).
- `core/ModModules` and the generated resources (`common/src/generated/resources`, especially `lang/en_us.json` and `.cache`) are touched by every package. The orchestrator resolves `ModModules` by keeping all module lines, and resolves generated files by re-running `./gradlew :neoforge:runData` on the merged tree.
- After each merge: `./gradlew build` and `./gradlew :neoforge:runGameTestServer` on `main`.

## Work packages

| Phase | Package | Status | Notes |
|---|---|---|---|
| A | Foundation (milestone 0): template cleanup, Sable dependency, platform services, registration / config / networking / attachment helpers, datagen, JUnit, GameTest harness, CI, playtest checklist | done (headless part) | Merged. Build, JUnit (5), GameTests (2) and datagen verified on `main`. The client part is in the milestone 0 playtest. See "Foundation notes" below. |
| A2 | Foundation follow-up: entity-type and foreign-namespace tags and arbitrary JSON in datagen, shared datapack definition loader with client sync, GameTest helper for config changes | in progress | Needed by C3, C5 and C8. |
| B | Sable investigation → `docs/sable-notes.md` | done | Merged (docs only, so no build needed). Reviewed by spot-checking about 25 API claims against `refs/`, all matched. See "Sable findings" below. |
| C1 | Hull analysis + flooding model (§4.2, §4.5), pure logic | in progress | Wave 1. |
| C2 | Wind and sail model (§5.1, §5.2), pure logic | in progress | Wave 1. |
| C3 | Melee resolution core (§8.5), pure logic | todo | Wave 2, waits for A2. |
| C4 | Law system logic (§13.1, §13.2) + false-flag detection math (§4.7) | in progress | Wave 1. |
| C5 | Trade economy logic (§10.3) | todo | Wave 2, waits for A2. |
| C6 | Provisions logic (§7.4) | in progress | Wave 1. |
| C7 | Config groups and values (§17) | todo | Wave 2. Reduced to the groups no other package owns (see decisions). |
| C8 | Basic items and blocks + datagen + placeholder textures | todo | Wave 2, waits for A2. |
| D1 | Spike 1: assembly (§4.1) | todo | Starts after phase C wave 2 is under way. Ends at a playtest gate. |
| D2 | Spike 2: dry hull (§4.3, §4.4) | todo | Waits for D1 + C1. Ends at a playtest gate. |
| D3 | Spike 3: wind + sails (§5) | todo | Waits for D1 + C2. Ends at a playtest gate. |
| D4 | Spike 4: crew station (§6) | todo | Waits for D3. Ends at a playtest gate. |
| E | Integration: law, trade, provisions connected to entities and blocks (attachments, market backend, pantry, brig, flagpole) | todo | Waits for C4–C8. |

## Roadmap milestones (design.md §20)

| # | Milestone | Status | Notes |
|---|---|---|---|
| 0 | Project setup | blocked: needs playtest | Everything headless is done and green. "Sable loads in the NeoForge dev client" and the test block's look need the playtest in `docs/playtests/milestone-0.md`. |
| 1 | Spike: assembly | todo | Phase D1. |
| 2 | Spike: dry hull | todo | Phase D2. |
| 3 | Spike: wind + sails | todo | Phase D3. |
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

## Playtests for the human

In this order:
1. [`docs/playtests/milestone-0.md`](playtests/milestone-0.md): Sable loads in the dev client, the mod list and config screen are correct, the test block appears and renders, `/sable spawn sphere 3` works.
