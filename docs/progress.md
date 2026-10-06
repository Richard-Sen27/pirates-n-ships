# Progress

Status of every roadmap milestone (design.md §20) and orchestrator work package (`docs/prompts/initial-orchestrator.md`).
Statuses: **todo** / **in progress** / **done** / **blocked: needs playtest** / **blocked: other** (with reason).

"Done" means: `./gradlew build` passes, `./gradlew :neoforge:runGameTestServer` passes, and the new logic has tests.

Last updated: 2026-10-06 (phase B merged, phase A running).

## Work packages

| Phase | Package | Status | Notes |
|---|---|---|---|
| A | Foundation (milestone 0): template cleanup, Sable dependency, platform services, registration / config / networking / attachment helpers, datagen, JUnit, GameTest harness, CI, playtest checklist | in progress | |
| B | Sable investigation → `docs/sable-notes.md` | done | Merged (docs only, so no build needed). Reviewed by spot-checking about 25 API claims against `refs/`, all matched. See "Sable findings" below. |
| C1 | Hull analysis + flooding model (§4.2, §4.5), pure logic | todo | Waits for phase A. |
| C2 | Wind and sail model (§5.1, §5.2), pure logic | todo | Waits for phase A. |
| C3 | Melee resolution core (§8.5), pure logic | todo | Waits for phase A. |
| C4 | Law system logic (§13.1, §13.2) + false-flag detection math (§4.7) | todo | Waits for phase A. |
| C5 | Trade economy logic (§10.3) | todo | Waits for phase A. |
| C6 | Provisions logic (§7.4) | todo | Waits for phase A. |
| C7 | Config groups and values (§17) | todo | Waits for phase A. |
| C8 | Basic items and blocks + datagen + placeholder textures | todo | Waits for phase A. |
| D1 | Spike 1: assembly (§4.1) | todo | Waits for phases A + B. Ends at a playtest gate. |
| D2 | Spike 2: dry hull (§4.3, §4.4) | todo | Waits for D1 + C1. Ends at a playtest gate. |
| D3 | Spike 3: wind + sails (§5) | todo | Waits for D1 + C2. Ends at a playtest gate. |
| D4 | Spike 4: crew station (§6) | todo | Waits for D3. Ends at a playtest gate. |
| E | Integration: law, trade, provisions connected to entities and blocks (attachments, market backend, pantry, brig, flagpole) | todo | Waits for C4–C8. |

## Roadmap milestones (design.md §20)

| # | Milestone | Status | Notes |
|---|---|---|---|
| 0 | Project setup | in progress | Phase A. |
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
| 2026-10-06 | Subagents read `refs/` from the main checkout by absolute path. | `refs/` is git-ignored, so it doesn't exist inside agent worktrees. |
| 2026-10-06 | Subagents are spawned as `general-purpose` agents pinned to Opus, with the instructions from `.claude/agents/implementer.md` referenced in the prompt, instead of `subagent_type: "implementer"`. | This session doesn't list the `implementer` agent type ("Agent type 'implementer' not found"), probably because the definition was added after the session's agent list was loaded. Model and instructions are the same as intended. |

## Sable findings that change the design (from `docs/sable-notes.md`)

These still have to be written into `docs/design.md` (§4.3–§4.5, §5, §21). That edit waits until phase A is merged, because phase A also edits §21.

| Finding | Consequence |
|---|---|
| Buoyancy is computed natively and **can't be overridden or disabled per ship** (notes §4.1, §4.2). Only solid hull blocks displace water, air inside the hull adds nothing. | Hard limitation. Dry-volume buoyancy and flood-water weight become an extra force group on top of Sable's hull buoyancy (§4.5). The constants must be tuned together in a playtest. |
| Sable has **no disassembly API** (notes §2.4). | We build disassembly from its public pieces (`AssemblyTransform`, `moveBlocks`, `moveTrackingPoints`), with yaw in 90° steps only, and our own obstruction check. |
| Sable already has **water occlusion regions** with gameplay mixins and a depth-mask renderer in `sable-common` (notes §4.4). | Spike 2 uses these regions instead of our own fluid-query mixins and mask renderer (§4.3, §4.4). Left to us: client sync of regions, rebuilds, partial flooding, item flotation, block placement checks. |
| Native water drag is the same in every direction (notes §4.3). | Sailing across the wind needs our own keel (sideways) drag. This goes into spike 3. |
| A structure template can be placed directly into a new sub-level (notes §2.5). | Shipwright pickup needs no placement at the berth (§4.1, §21). |
| All needed APIs, including events, are in `sable-common` (notes §7). | No Sable-specific platform service is needed. Much of it is outside `api.*`, so all Sable calls go through one adapter package. |
| Assembling in water leaves an air pocket in the world, and disassembling leaves sea water inside the hull (notes §2.2, §2.4). | Spike 1 has to handle both. |

## Playtests for the human

None yet.
