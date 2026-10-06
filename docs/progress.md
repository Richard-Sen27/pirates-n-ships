# Progress

Status of every roadmap milestone (design.md §20) and orchestrator work package (`docs/prompts/initial-orchestrator.md`).
Statuses: **todo** / **in progress** / **done** / **blocked: needs playtest** / **blocked: other** (with reason).

"Done" means: `./gradlew build` passes, `./gradlew :neoforge:runGameTestServer` passes, and the new logic has tests.

Last updated: 2026-10-07 (fourth session: phase G; G1 and F7a merged, F5a and F7c running).

## Phase F: first playtest feedback (done, F5 and F7 continue in phase G)

The human played milestones 1 to 3 and reported: the dry hull works well; a floating ship rocks from side to side without end; slabs, stairs and trapdoors show water in their empty half; the blocks look like 2012 and need real 3D models; sails should be built from two yards with cloth between them, and triangular sails from a rope and a cleat; flags are far too small; the anchor should be a real anchor on the hull side with chain sound and a splash; planks should creak now and then; the captain's whistle should open a radial menu. These are recorded as decisions in design.md §4.3, §4.7, §4.8, §5.2, §5.3, §7.2, §16 and §21.

| Package | Scope | Status |
|---|---|---|
| F1 | Roll and pitch damping (the rocking was undamped roll) and occasional hull creaking tied to the rolling, with placeholder vanilla sounds | done | Merged. `HullDampingModel` (defaults 1.5/1.5; a kicked 7×17 hull settles in 3 s instead of rocking for 8), `audio/` module with the creak trigger and the generated `sounds.json`. The endless rolling could not be reproduced headlessly: Sable damps small hulls in the test basin itself, so the in-game fix needs the playtest (`milestone-3.md` §5c). |
| F2 | Occlusion regions include watertight partial blocks (slabs, stairs, trapdoors) on the hull's inside | done | Merged. A partial cell joins the dry region when every uncovered face looks at a dry cell or a block, and never when one looks at the sea or the sky. Toggle `dry_hull.partial_blocks`. Playtest `milestone-2.md` §11c. |
| F3 | The captain's whistle opens a radial menu; orders go to the server as a payload | done | Merged. Four entries (hoist, reef, furl, release crew), click or hold-aim-release, Escape closes. The sneak-use cycling is gone. The screen itself is unverified: playtest `milestone-4.md` §9. |
| F4 | The anchor as an entity: stowed at the hull side, lowered on a chain with sound, splash and thud, raised again; travel time from depth | done | Merged. `sailing/anchor`: a view entity inside the plot while stowed, world space while out, code-built model and chain renderer, vanilla sounds played directly (its own sound events exist but are not in `sounds.json` yet, see follow-ups). Playtest `milestone-3.md` part 2 §4 and §5. |
| F5 | Multi-block sails: two yards with rendered cloth between them, triangular sails from a rope and a cleat | split into F5a and F5b, see phase G |
| F6 | Full-size flags: a cloth hanging downwind from the pole, models built in code | done | Merged at 1 × 1.5 blocks: a vanilla block model can't reach further than 24 px from the pole's centre; 2 blocks would need a block entity renderer. Both sides textured, 32×16 textures. Playtest `flags.md`. |
| F7 | 3D models for all ship blocks (helm wheel, capstan, figureheads, containers, …) | split into F7a to F7d, see phase G |

Phase F left `main` green (627 JUnit tests, 190 GameTests, four green runs). Milestone 0 was playtested by the human and passed completely.

Follow-ups from phase F (small):
- ~~`sounds.json` has one owner~~ done in G1: `data.sounds(...)` collects entries from all modules, and the anchor plays its own events.
- `SailingRuntime` applies the anchor force at the capstan centre; it should act at the hawse (`ShipAnchor.hawse`), which is now 2 to 3 blocks to the side. Harmless within the anchor's slack.
- `StationGameTests.crewHoistsAndFurlsAfterTheWorkTime` failed once for two different agents ("sails not furled: FULL") in about 12 runs each, and passed in all of the orchestrator's 20 runs. Its ±3 tick margin may be too tight under load.
- Custom banner flags show a generic cloth. A banner-coloured cloth needs a `BlockColor` registration hook in the foundation (NeoForge `RegisterColorHandlersEvent.Block`) and a tint index on the model.

Decided (2026-10-07, the human connected Blockbench): Blockbench models are hand-made assets. `.bbmodel` sources in `art/models/`, exports in `common/src/main/resources/assets/pirates_n_ships/models/`, renders in `art/renders/`, datagen writes only block states and item models. Written into `CLAUDE.md` and design.md §4.8.

## Phase G: sound, multi-block sails and 3D models (running)

Started 2026-10-07 after the session restart with the Blockbench MCP connected (the MCP tools reach subagents; checked). The human added eight Pixabay files to `raw_sound/` (git-ignored): four music tracks (Leave Her Johnny; The Ghost Of Gallows Reef; There Be Pirates - The Quest; There Be Pirates - Lost in the Deep) and four gun sounds (pistol shot, empty gun shot, cannon shot, artillery gunfire), and will add more over time. Sources are listed in `docs/credits.md` once G1 lands.

| Package | Scope | Status |
|---|---|---|
| G1 | Shared `data.sounds(...)` builder for one generated `sounds.json`; `tools/convert_sounds.py` + `tools/sounds/manifest.json` (mp3 → Ogg Vorbis, loudness-normalised, credits generated); music pools `music.sea` and `music.shanty` with a client music selector (aboard → shanty, ocean biome → sea, else vanilla) through `ClientEvents.SELECT_MUSIC` / `SOUND_STREAM_STARTED`; gun and sword sound events registered for the combat milestones; anchor events join `sounds.json` | done | Merged (`387e587`). 20 sound files (music 8.4 MiB, the jar is now 10.35 MB), 21 `sounds.json` entries, `docs/credits.md`. "Aboard" comes from Sable's `getTrackingOrVehicleSubLevel` (`ship/sable/ClientShipPoses.onShip`) with a 3 s memory for jumps. A running track is never cut off (`replaceCurrentMusic=false`). `music_volume` scales the channel when our track starts streaming; moving the game's music slider mid-track resets that track to the slider. The Homebrew ffmpeg has no libvorbis, so the agent installed `vorbis-tools` (`oggenc`) on the human's machine; the README says so. Playtest `audio.md`. |
| F5a | Square sails from two yards (`yard` block, pairing rule from design.md §5.2, trapezoid area, trim on the upper yard, block entity renderer drawing the cloth); removes the one-block square sails; the one-block fore-and-aft sail stays until F5b | in progress |
| F5b | Triangular sails: cleat block, rope item making a stay between two cleats, third cleat below the head, cloth rendered in the triangle; removes the one-block fore-and-aft sail | todo, after F5a |
| F7a | Blockbench models, batch 1: helm wheel, anchor (entity, exported as `LayerDefinition` code), flagpole, nameplate; JUnit tests that every hand-made model parses and every blockstate references an existing model | done | Merged (`e149578`). Helm 46 elements, flagpole 11, nameplate 18, anchor 26 boxes in 13 parts; `.bbmodel` projects in `art/models/` (vanilla texture data stripped; `tools/extract_vanilla_textures.py` fills the ignored `art/vanilla/` before opening them), renders in `art/renders/`, workflow in `art/README.md`. The helm got `noOcclusion()` so the deck under it is drawn; its wheel reaches above the block. Four model batches took about 230k agent tokens: keep batches at three or four models. Playtest `items-and-blocks.md`, section "3D models". |
| F7b | Blockbench models, batch 2: capstan, sail winch, yard, cargo crate, cargo barrel, pantry, water barrel | todo, after F5a and F7a (shares `SailingModule` datagen with F5a) |
| F7c | Blockbench models, batch 3: the four figureheads | in progress |
| F7d | Blockbench models, batch 4: cleat, brig bars and door, flag cloth at 2 blocks via a block entity renderer | todo, after F5b |

Only one agent at a time may use Blockbench (one desktop instance, one open project), so the F7 batches run one after another; G1 and F5 run next to them.

Follow-ups from phase G (small):
- Saving a `.bbmodel` from Blockbench embeds the vanilla textures again; strip them before committing (F7a did it with an ad-hoc script; a `tools/strip_bbmodel_textures.py` would make it one command).
- The gun effects came out 2 to 3 LU below the −16 LUFS target (single-pass `loudnorm` with the true-peak limit). Raise `EFFECT_LUFS` or use two-pass if they sound quiet next to vanilla.
- Fabric port: `SELECT_MUSIC` and `SOUND_STREAM_STARTED` have no direct Fabric equivalent and may need a mixin there.
- `.gitattributes` had `* text eol=lf`, which would have corrupted the `.ogg` files; `*.ogg/.mp3/.wav binary` (G1) and `*.nbt binary` (orchestrator) were added. Existing PNGs were already covered.


## Summary of the second session

Everything that could be built and tested without a game client was merged.

**State of `main`:** `./gradlew build` passes (567 JUnit tests), `./gradlew :neoforge:runGameTestServer` passes (172 GameTests, eight green runs in a row after the last merge), and `./gradlew :neoforge:runData` leaves no diff. Nothing is pushed: local `main` is 130 commits ahead of `origin/main`. No agent is running, and no agent branch or worktree is left.

**Done (merged, with tests):**
- **Foundation** (A, A2, A3, A4): Sable dependency, platform services, module system, config wrapper, datagen, datapack definitions, JUnit and GameTest harness, CI. See design.md §3.3.
- **Sable investigation** (B): `docs/sable-notes.md`, extended with what the spikes found by running code (§9.0 to §9.0e).
- **Logic packages** (C1 to C8): hull analysis and flooding, wind and sailing forces, melee rules, law, trade, provisions, the settings table, basic items and blocks with placeholder textures.
- **The four spikes** (D1 to D4): assembly at the helm, dry hull with flooding and buoyancy, sails with helm and anchor, crew station. Plus the fixes D2b, D2c and D5.
- **Integration** (E1a to E3): crimes detected in the world, brig and shackles, flags, pantry and water barrel, cargo containers and market backend.
- **A guide** to everything that exists: [`docs/guide.md`](guide.md).

**Blocked, and why:**
- **Milestones 0 to 4 need playtests.** All five are green on a headless server, but nothing has been seen in a client: whether ships float at a sensible height, whether the hold looks dry, how fast and how steadily a ship sails, how a crew member looks on a moving deck. The ordered list is at the end of this file.
- **The main risk for the playtests is stability.** Small hollow hulls barely right themselves in Sable. The test boat (5×5, 4 high, planks) lies about 20° bow up at rest and runs 35 to 46° bow down under a small sail. A stone bottom layer brings that to about 16°. A decision is needed after the milestone 3 playtest: ballast or keel blocks, our own righting moment, or accepting it (design.md §21).
- **Everything that needs assets:** structures (human-built NBT), mobs (models and animations), real textures and block models, sounds.
- **Everything that needs a working, playtested spike to be meaningful:** ship-level wiring of flags, cargo weight, prisoners and provisions, cannon damage, crew eating, NPC ships. Phase E stops at entry points that take plain inputs.
- **Client features that can't be verified headlessly and were not started:** HUD, market screen, sword fighting input and animations.

**What to do next, in order:**
1. Do the playtests (list at the end of this file), at least milestones 0 to 4, and send back screenshots, logs and the answers to the tuning questions.
2. Decide the open questions the playtests answer: buoyancy and stability, sail force and rudder strength, whether Sable's water occlusion looks right (design.md §21).
3. Review the defaults the agents chose ("Defaults to review" below) and the two deviations from `CLAUDE.md` ("Foundation notes").
4. Consider reporting the Sable bug described in `docs/sable-notes.md` §9.0c upstream.
5. Push `main` when you are happy with it. The orchestrator did not push.

How merges worked:
- Every merged branch and its worktree was deleted right after the merge (requested by the human).
- `core/ModModules` and the generated resources (`common/src/generated/resources`) were touched by every package. The orchestrator resolved `ModModules` by keeping all module lines, and resolved generated files by re-running `./gradlew :neoforge:runData` on the merged tree.
- Before each merge commit: `./gradlew build` and several runs of `./gradlew :neoforge:runGameTestServer` on the merged tree.

Incidents:
- Around 17:51 and 18:21 a short connection loss stalled several agents. All recovered by themselves except A2, which was stopped and resumed from its transcript at 18:33 with its work intact.
- At about 19:00 the network dropped again and all five running agents (C3, C5, C8, D2, A3) ended with API connection errors. Their worktrees and uncommitted work were intact, and all five were resumed from their transcripts at 19:40.
- Flaky tests, all traced to a cause:
  - `AssemblyGameTests.disassemblyPutsBlocksBackOnTheGrid`: three causes. An off-by-one in the passenger placement (fixed with spike 2), tests running millions of blocks from the origin where Sable's 32-bit physics fails (fixed by D2b), and a real bug where a chest arrives empty in a ship assembled in the same tick another ship was removed (a Sable bug, worked around by D2c).
  - `SailingGameTestsControls.rudderTurnsTheShipUnderSail`: the unballasted test hull pitches about 40° under sail and then wanders off course. The test hull now has a stone bottom, and its tolerances were tightened (D5).
  - `StationGameTests.disassemblyLeavesCrewAtTheStation`: the test counted a seat of another test's ship, which had been given the same plot. No seat was ever really left over (D5).
- Token budgets: the spike 1, spike 2 and D2b agents each used their whole budget (200k tokens), and two of them stopped before finishing. Later packages were cut smaller and told to keep Gradle output out of their context.
- The merge commits `2e3fcac` (C6) and `3978c02` (A2) **don't compile**: the orchestrator wrote a malformed module list into `core/ModModules` (a shell quoting mistake) and committed without checking the result. `b97293d` fixes it. Keep this in mind when bisecting. Since then the orchestrator built and ran the GameTests before committing a merge.
- The orchestrator changed one line of code itself: the GameTest position range in `mixin/MixinGameTestServer` (±250,000 to ±4,096 blocks), on the D3b agent's measurement. Everything else was written by subagents.

Follow-ups for later packages (small, not blocking):
- Disassembly is not refused while the anchor is out (the anchor is simply cleared).
- Dry-hull buoyancy is only re-analysed after 5° of tilt (`reanalysis_tilt_degrees`). Inside that band the dry volume adds no extra righting moment, which may add to the random heel of small hulls. Not checked.
- The beam-reach, anchor and crew sailing tests still use the unballasted hull, which runs about 40° bow down. They pass reliably, but their measured speeds come from that attitude.
- The helm's rudder position is only visible through F3: the block model doesn't change with it yet.
- An order in progress at a station is lost on save, because station states are not persisted.
- The spike 3 agent thinks `sail_force_scale = 1.0` is too strong for Sable's masses (it expects 0.3 to 0.5), and saw a hint that the reported speed and the distance covered may not match. Both are in the milestone 3 playtest.
- The ship's plot box only grows: removed blocks don't shrink the hull length used for sailing until the ship is reloaded.
- `ChunkCacheGuard` lives in `ship/assembly` and is called by our assembly and disassembly only. It should move into the Sable adapter (`ship/sable`) so that every removal and creation of a ship goes through it. A cleaner way to clear the memo would be an access transformer line for `ServerChunkCache.clearCache()`.
- `ShipTestCleanup` (in `ship/`) and `ContentTestSupport` (in `combat/content`) are general GameTest helpers and belong in `core/gametest`.
- `ship/assembly` (terrain tag) and `ship/hull` (watertight tags) can now use required vanilla tag references (`addTag(BlockTags.X)`), since A3 fixed the tag datagen. Both still use their workarounds.
- The trade agent's balance notes: unit prices round harshly for cheap goods (a single sugar costs 2 and sells for 2), so the market screen should show prices per stack. Trade route profit fades after roughly 250 to 400 units per port pair.
- Rum is both a provision and a trade good. Whatever sums a ship's weight has to count each stack once.
- Fabric port list so far: access-widener twins for the four access transformer lines, a render layer for brig bars and flags, and implementations of the platform services (config through Forge Config API Port, capabilities through the transfer API, the event forwarders).

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
| D2c | Flaky test `disassemblyPutsBlocksBackOnTheGrid` ("chest content lost") | done | Merged. **It was a real bug, and it is a Sable bug:** when one ship is removed and another assembled in the same server tick, Sable reuses the plot while vanilla's chunk lookup memo still points at the old chunk, so a chest arrived in the new ship empty and its items were lost. Fixed with a workaround in `ship/assembly/ChunkCacheGuard` and a regression test that failed every time without it. Details in `docs/sable-notes.md` §9.0c. |
| D3a | Spike 3 part 1: sail blocks with trim, sail winch, sailing runtime applying wind and keel forces, wind override command | blocked: needs playtest | Merged and green headlessly. Three sail blocks, a winch, `sailing/ship` runtime, `/pirates wind` and `/pirates ship forces`. 8 GameTests measure real ships in a basin: about 0.4 m/s downwind for a small hull with a small sail in 6 blocks/s of wind, no forward motion into the wind, and the keel halving the sideways drift. Finding: small hollow hulls capsize under the full heel torque, so it is scaled to 25% (`sail_heel_factor`). Playtest: `docs/playtests/milestone-3.md`. Spike 3 was split in two, because spikes 1 and 2 each used up an agent's whole token budget. |
| D3b | Spike 3 part 2: helm steering (rudder) and anchor (capstan) | blocked: needs playtest | Merged. Plain use of the helm on a ship now steers (three steps per side by click position), and sneak-use with an empty hand disassembles. New capstan block with an anchor that drops to the sea floor. 10 JUnit tests, 7 GameTests. Measured: full rudder turns the small test hull about 0.6 to 0.8° per second at 0.3 m/s, and the anchor holds it within about 2 blocks. Playtest: `docs/playtests/milestone-3.md` part 2. |
| D4 | Spike 4: crew station (§6) | blocked: needs playtest | Merged. Station contract (`station/`), the sail winch as a station, an invisible seat entity inside the ship's plot, a test crew member (`crew/npc`), the captain's whistle test item and `/pirates crew` commands. 7 JUnit tests, 9 GameTests, including the milestone sentence: a crew member hoists the sails on command while the ship sails and is still at its station afterwards. The seat approach works on the server. How it looks in the client is unverified. Playtest: `docs/playtests/milestone-4.md`. |
| D5 | The two flaky GameTests after the last merges | done | Merged. Both were test problems with a reproduced cause, no game bug: an unstable test hull, and a test that counted another ship's seat in a reused plot. The investigation measured how unstable small hollow hulls are (see the final summary). Ten green runs in a row on the branch, eight on `main`. |
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
| 0 | Project setup | done | Playtested by the human on 2026-10-06: passed completely. |
| 1 | Spike: assembly | blocked: needs playtest | Phase D1 is merged. `docs/playtests/milestone-1.md`. |
| 2 | Spike: dry hull | blocked: needs playtest | Phase D2 is merged. `docs/playtests/milestone-2.md`. |
| 3 | Spike: wind + sails | blocked: needs playtest | Phases D3a and D3b are merged. `docs/playtests/milestone-3.md`. |
| 4 | Spike: crew station | blocked: needs playtest | Phase D4 is merged. `docs/playtests/milestone-4.md`. |
| 5 | Config framework + weapons | partly done | The config framework and the whole settings table exist (C7 and the feature packages), and all §8.1 items exist as items (C8). Missing: firearms that shoot, and the config screen has not been seen in a client. |
| 6 | Flooding + damage | partly done | Breach, flooding, equalising between rooms, buoyancy loss and sinking work on real ships (C1, D2). Missing: cannon damage, pump and patch as blocks or items, a visible water surface inside. |
| 7 | Melee combat core | partly done | Rules, weapon definitions, server-side resolution and GameTests exist (C3). Missing: input, network events, the animation library decision, the stamina HUD. |
| 8 | Melee animations + NPC duelists | todo | |
| 9 | Cannons + grappling hook v1 + boarding | todo | |
| 10 | Ship identity + shipwright | partly done | Flags with hoisting and striking (E1c), naming a ship with a name tag (D1), figurehead and nameplate blocks (C8). Missing: the name on the nameplate, dyeable sails, a ship's allegiance from its flagpoles, shipwright orders. |
| 11 | World | todo | Needs human-built structure NBT. |
| 12 | Mobs | todo | Needs models and animations (Blockbench). |
| 13 | Law + brig | partly done | Criminal score, bounties, proof, crimes from combat and theft (C4, E1a), shackles, prisoners, cells and the lockable door (E1b). Missing: navy NPCs to turn in to, notice boards, false-flag detection in the world. Playtests: `law-commands.md`, `law-world.md`, `brig.md`. |
| 14 | Trade + cargo | partly done | Goods, markets, contracts, plunder rules (C5), bulk containers, wallet and the market backend (E3). Missing: ports and harbor masters, the market screen, cargo weight acting on ships. Playtest: `cargo-and-market.md`. |
| 15 | Crew command system + provisions | partly done | Provisions rules, pantry and water barrel (C6, E2), and the crew station prototype with sail orders (D4). Missing: hiring, the other orders and stations, wages and morale, crew eating from the pantry. Playtests: `pantry.md`, `milestone-4.md`. |
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
| 2026-10-06 | D3b and D4 run in parallel, although the orchestrator prompt lists the spikes as sequential. | D4 only needs the sail winch from D3a, not the helm or anchor. The two packages own separate files, and each adds new files in `ship/sable` instead of editing the shared adapter classes. |
| 2026-10-06 | D3a: the bow is the way the helmsman looks (opposite of the helm block's facing). Sails don't push a ship that is not afloat. The roll and pitch torque of sail and keel is scaled to 25%, and the keel's sideways drag default was raised from 2 to 8. | Measured in GameTests: with the full heel torque a small hull capsized within 2 seconds, and with the old keel value it drifted sideways as fast as it went forward. All four are config values or documented conventions. |
| 2026-10-06 | GameTests now run within ±4,096 blocks of the origin (was ±250,000). The orchestrator changed this one constant itself. | The D3b agent measured that slow ships don't move at all at 191,000 blocks (32-bit position steps), and it could not edit the mixin. It is test infrastructure, not feature code. It did not remove the flakiness, so D5 is looking for the real causes. |
| 2026-10-06 | D3b: plain use of the helm on a ship steers, and disassembly needs sneak-use with an empty hand. | Steering is the frequent action. `docs/playtests/milestone-1.md` was updated. |
| 2026-10-06 | D4: a crew member is released when its ship is disassembled, and is not kept assigned. No spawn egg: crew members come from `/pirates crew spawn`. | A reassembled ship gets a new id. Vanilla's spawn egg needs the entity type at construction time. |
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
- **Audio:** 120 to 300 s between our music tracks (vanilla waits 10 to 20 minutes); shanties aboard; a pool change waits for the running track to end.

## Playtests for the human

In this order:
1. ~~`milestone-0.md`~~ done, passed completely.
2. [`docs/playtests/milestone-1.md`](playtests/milestone-1.md): **the important one.** Build the boat from the recipe, assemble it at the helm in the sea, walk on deck, shove it, disassemble it, check the water in both directions, the block limit, a chest keeping its items, naming, and rejoining. Spikes 2 to 4 build on this.
3. [`docs/playtests/milestone-2.md`](playtests/milestone-2.md): **the second important one.** No water inside the hull, no swimming below deck, a breach floods the hold, a flooded ship sinks, a ship in a dry dock stays put. It also has the tuning questions for the buoyancy values.
4. [`docs/playtests/milestone-3.md`](playtests/milestone-3.md): sailing. Set the wind with `/pirates wind set`, hoist the sail at the winch, sail downwind, on a beam reach and into the wind, compare square and fore-and-aft sails, read `/pirates ship forces`. It has a tuning table (speed, heel, sideways drift), and section 5b on stability, which is the biggest open question. Part 2 covers steering at the helm and the anchor at the capstan.
5. [`docs/playtests/milestone-4.md`](playtests/milestone-4.md): crew station. Get a crew member, assign it to the winch with the captain's whistle, order it to hoist and furl while sailing. The main question is how the crew member looks on a moving, heeling ship (jitter, lagging, feet off the deck), which nobody could check headlessly.
6. [`docs/playtests/law-commands.md`](playtests/law-commands.md): criminal score, navy and player bounties, claims, fines, persistence across death and reload, and the config toggle, all through `/pirates law` commands. Not a gate for other work.
7. [`docs/playtests/items-and-blocks.md`](playtests/items-and-blocks.md): now also the first Blockbench models (section "3D models"); every item and block in the creative tab with texture, name, model, drops and recipe. Not a gate for other work.
8. [`docs/playtests/audio.md`](playtests/audio.md): sea music at sea and aboard, music config, the anchor's own sounds, and the gun and sword sounds via `/playsound`. Not a gate for other work.
9. [`docs/playtests/data-and-config.md`](playtests/data-and-config.md): definitions synced to clients, a broken datapack file, tags, and the config screen. Not a gate for other work.
10. The phase E features, each through its own debug commands, in any order and none of them a gate: [`law-world.md`](playtests/law-world.md) (crimes from hitting villagers and from theft, bounty proof), [`brig.md`](playtests/brig.md) (shackles, leading, cells, the lockable door), [`flags.md`](playtests/flags.md) (hoisting and striking flags), [`pantry.md`](playtests/pantry.md) (pantry, water barrel, consumption and spoilage), [`cargo-and-market.md`](playtests/cargo-and-market.md) (bulk containers, coins, buying and selling at test ports, contracts).
