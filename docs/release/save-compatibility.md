# Save compatibility for 0.1.0

What happens to an existing world, its ships and its config files when it is opened with Pirates 'n' Ships 0.1.0, the first public release, and what a 0.1.0 world does in an older build. Written from the code's load paths (SAV1, 2026-10-10). The guards are `SaveCompatGameTests` (old saves written straight into the chunk convert as described here) and `KeptIdsTest` (every id in [Kept ids](#kept-ids) is still registered). This note is part of the release checklist (`docs/releasing.md`): a package that changes a stored format, a block state, a registry id or a config key updates it.

## Which worlds this is about

**There is no earlier public release.** `git tag` is empty on 2026-10-10 and nothing has been published, so 0.1.0 is the first version players can have (the human, 2026-10-10: the first public release is 0.1.0; 1.0.0 follows the playtest rounds). The promise of [Kept ids](#kept-ids) starts at 0.1.0 and carries on through 1.0.0. The worlds that exist before it were made with development builds (`<mod_version>-dev` jars from `main`, the human's playtest worlds, the GameTest structures). They are not one version but a sequence of packages; this note names the package that changed a format so a world can be placed by the last package its build contained (`docs/progress.md`).

For players the summary is: **a world from any development build opens under 0.1.0, ships included.** A few old blocks convert on their first tick, a few kinds of state are rebuilt or lost on every restart anyway, five block ids from the first week of development are gone, and a config file keeps its old values until it is edited or deleted.

## Kept as is

Everything not listed under "converts" or "lost" below loads unchanged. In detail:

- **Ships (Sable sub-levels)**, their blocks, block entities, position, orientation and velocity: Sable's own files (see [Sable](#sable)). Our per-ship data lives in Sable's user data (keys `pirates_n_ships` the identity pointer with the helm, `pirates_n_ships_sailing` the bow and the anchor, `pirates_n_ships_oars` the rowing flag (OAR1), `pirates_n_ships_voyage` the world-sim link, `pirates_n_ships_hull` breaches and flood water) and in the `pirates_n_ships_ships` saved data (the ship's identity: id, name, owner, crew, flag, `blown_cover_until`). A ship's id is its Sable sub-level UUID, so it never changes on load.
- **Saved data** (`data/pirates_n_ships_*.dat` of the overworld): `ships`, `ship_upkeep`, `trade`, `port_visits`, `ports`, `quests`, `hiring`, `bounties`, `factions`, `raids`, `lanes`, `navy`, `captain_voyages`, `voyages`, `captains`, `harbor_masters`, `villagers` (LIFE1: the village people's slots and plans; a world without the file has none). Every field added after a file first existed is optional with a default, so an older file reads (e.g. a port without `orders` or `name`, a raid without `fought`, a voyage without `pursuit`, a faction without `tension`). The files carry no format version; the lane cache (`pirates_n_ships_lanes`) is the exception, see below.
- **Player data** (data attachments, kept on death): the chart (`chart`; CHART2 changed nothing in it; CHART4 adds an optional `colours` byte array per region, the block colour of each cell: a chart saved before CHART4 loads unchanged, its cells draw in ink until the player charts them again, and a region with colours saved by CHART4 loads in an older build, which ignores the field), the ports and structures a chart knows (`chart_sightings`, new in CHART2: a player from before starts with none and learns the wrecks again by sailing near them; ports show anyway with `chart.ports_always_known`), the Almanac's last page (`almanac_progress`, ALM2; an unknown chapter is ignored), whether the Almanac was given (`almanac_given`), career and rewards (`career`, `career_gifts`), reputation, the criminal record and prisoner releases (the law), the quest log. Every field is optional; a career counter or crime type no longer used is kept or skipped so old records load (`CareerCounter`, `CrimeType.FENCE_PLUNDER`).
- **Other attachments:** a prisoner in the brig (`prisoner`, on any entity), garrison posts and squads (`garrison_post`, `squad`), the player-placed blocks of a chunk (`player_placed_blocks`, the law).
- **Items** and their data components: the sea chest keeps its contents in vanilla `minecraft:container` (item) and vanilla `Items` (block and floating chest); treasure maps and drawn map tiles read older shapes (missing fields default, bad data is a codec error, not a crash); commissions, receipts, bounty proofs, loaded grapples and firearms, cargo, the whistle's remembered sail order (an unknown order name is refused, not a crash), `rope_start`/`net_start`.
- **Crew** (entity NBT): assignment, pinned station, morale, rest, night outcome, wages owed, hiring, desertion, walk target, `stationary`. The ship's upkeep (wages, provisions) is in `pirates_n_ships_ship_upkeep`.
- **The world simulation** (ports, factions, voyages, captains, harbour masters, navy pursuits, raids) and **the law** (scores, bounties, criminal records).

## Converts on load

Lazy, per block or entity, on load or on its first tick. No command runs a migration and none is needed.

| What | From | Package | Mechanism | Guard |
|---|---|---|---|---|
| Boarding plank run, level | BRD1 (`segment` 0..3, `tip`, block entity on segment 0 only, no `on_ship`) or a level BRD2 run | BRD4 | `BoardingPlankBlockEntity.loadAdditional` marks a base without `cells` but with `length` as BRD1/BRD2; its first server tick (`relay`) keeps the same cells as one flat board of `length` columns; `tip`/`pitch` are dropped by the state codec. A BRD1 run's near support was never saved, so it is not watched afterwards. | `SaveCompatGameTests.aBrd1PlankRunLoadsAsALevelBoard`, `BoardingGameTests.aLevelBrd2RunLoadsInPlace` |
| Boarding plank run, sloped or stepped | BRD2 (`pitch` ramps, steps) | BRD4 | `relay` takes the cells up without drops and lays one straight board from the saved near support (`PlankPlanner`); if no straight board fits, the run is gone and **one plank drops** at the old base. A BRD1 run with a missing cell also drops one plank. | `BoardingGameTests.aBrd2RampLoadsAsAStraightBoard` |
| Ratline net | RL2 (`net_other`/`net_a` on each fitting, cells naming fitting A only, no `boarding` state) | RL4, RAIL5 | `NetFittingBlockEntity` reads the single RL2 link into its list and writes RL4's `nets` on the next save; RL2 cells stay as they are and find their net through fitting A (`RatlineNets.ownerStrand`); `boarding` defaults to false. | `SaveCompatGameTests.anRl2NetLoadsAsOneStrand` |
| Nameplate | G8 (a ladder-like plate: `facing`, `waterlogged`; block entity `text` only) | NAME2 | The id `nameplate` was kept: the plate loads as a name board with `left`/`right` false and `row_index` 0, `row_length` 1; its first tick re-reads the ship's name and links the row. | `SaveCompatGameTests.g8NameplatesLoadAsAJoinedNameBoard`, `NameplateGameTests.oldNameplatesLoadAsBoards` |
| Oar bench | OAR1 before the bench became waterloggable (`facing` only) | 112caadc | `waterlogged` takes its default, false: the bench loads dry. A bench that stood in water in the old build did not hold water then either. | `SaveCompatGameTests.anOldOarBenchLoadsDry` |
| Waterloggable blocks | before WLOG1 | WLOG1 | `waterlogged` false on map tiles, planks, cannons, swivel guns, hammocks, notice boards, cleats, yards, sea chests, chart tables, flagpoles, sea cots, bells, bilge pumps, crow's nests, harbour desks. | vanilla codec |
| Other added state properties | before the package | RAIL1, CAN3, CW1, D3b, VIS1a, `6462dbd2` | Default on load, then recomputed where the block derives them: swivel `mount` NONE, cannon `shot` BALL, pantry and water barrel `load` (recomputed), helm `rudder` MIDSHIPS, flagpole `part` (fixed on the first tick), crow's nest `pole` (fixed by the next shape update). | vanilla codec |
| Rope anchors (cleats, mooring rings) | before RP1 (`stay`) | RP1 | `RopeAnchorBlockEntity` reads `stay` when `ropes` is missing; duplicates and ropes beyond 4 dropped. | |
| Flagpoles | before FL1 / VIS1a / FL2 | FL1, VIS1a, FL2 | Yaw from `FACING` when unsaved; an action without `start_tick` draws no animation; pre-FL2 placeholder flag strings read as no flag (also in the ship registry). | |
| Yards and cleats | any | F5a/F5b, CAN3 | Cloth geometry is recomputed on the first tick and replaces the saved cloth; missing tears read as none. | |
| Port names | before NAME3 (port records without `name`) | NAME3 | `Port.CODEC` reads a missing `name` as empty; the first `PortRegistry.get` after the load names every unnamed port from the world seed in the saved order (`PortNames`, unique within the world; a later port repeating an earlier name is renamed) and marks the registry dirty, so the names are written on the next save and never change afterwards. Ids are unchanged and stay the key everywhere (markets, quests, contracts, voyages, lanes, desks, harbour masters). | `SaveCompatGameTests.aPreName3PortRecordLoadsAndGetsAName`, `PortRegistryNamesTest` |
| Crew members | before CR1 | CR1 | An assigned crew member without `pinned` counts as pinned (always assigned by hand then); missing morale reads as the configured start. | |
| Crew of materialised voyages | before WS3c | WS3c | Made `stationary` when the voyage re-adopts its ship (`VoyageCrew.settle`). | |
| Navy officers | before MOB2 | MOB2 | No saved squad: the squad is rebuilt from his outpost on his first tick. | |
| Helms | before HELM1 | HELM1 | A helm without a block entity gets one (`HelmService`). | |
| Ship identity pointer | before HL1 | HL1 | No saved helm position: the first helm found is taken and written. | |
| Anchor | before AN2a | AN2a | `ShipAnchor` reads the old shape (`Codec.withAlternative`): RAISED becomes RAISING, the chain paid out is set from the distance on the next physics tick, `hawse` falls back to the capstan. An anchor whose capstan is gone is removed. | |
| The Almanac gift | before GUIDE2 (`guide_book_given`) | GUIDE2 | The old attachment is no longer registered: every player gets the Captain's Almanac once on the next join. | |
| Lane cache | any (no `version`) | WS lanes | `LaneData.checkVersion`: a cache whose `version` differs from `world_simulation.lanes.version` (default 1; a missing version reads 0) is emptied once and the lanes are rebuilt. | |
| Hull analysis | any | CMP1 | Never saved: compartments are analysed again when a ship loads. The flood water is saved per compartment (its lowest cell) and re-bound to the fresh analysis; water whose cell is no longer in any compartment is lost. | |

## Lost or reset

**On every restart, in every version** (memory only by design; listed so nobody reports them as save bugs):

- **Boarding parties (BRD3):** the party, its phase and the "Back aboard" state; boarders become ordinary crew where they stand (the `boarding` flag is not saved). An NPC boarding in progress stops.
- **A running lean (RAIL5)** and the claimed rail spots; **orders in flight**: the job board, station occupancy (seated crew re-claim it), helm courses, the gunnery's fire at will, a whistle order being carried out. **Duels, truces, navy engagement, raid bells.**
- **Rowing (OAR1):** only the flag is saved; a ship saved rowing rows again, but rail rowers stand up on load and walk back out to a rail spot.
- **The sea (WAV7):** the wave field's state is memory only. The phase follows the game time, which the world saves, so the waves continue where they were; the amplitude snaps to the weather's target and the heading restarts from the current wind, so both can jump at load. A running direction crossfade, `/pirates waves set` and wind overrides are lost.
- **Hull runtime (CMP1):** compartments, pumps and breathing state (re-analysed; the water stays, see above).
- **Entities never saved:** the anchor body, rope riders, grappling hooks (a hook returns to its thrower), kraken parts, a shark's grudge.
- **Block-entity timers:** a yard's next mend, the first-tick checks of cleats and flagpoles (they rerun), an open Almanac tome (loads closed).

**Lost when an old development world loads:**

- **Five removed block ids:** `small_square_sail`, `large_square_sail` (F5a), `fore_and_aft_sail` (F5b), `test_block` (CT1) and the render-only `helm_wheel` (HELM1b). Placed blocks become air (vanilla turns an unknown block id in a chunk's or a plot's palette into air), their items vanish from inventories and containers, a ship built with them loses those blocks. No item or entity id was ever removed or renamed; no registry alias exists.
- **One-block cannons (before P2):** load as the front half of the two-block cannon without its rear; the first block update behind it removes the cannon **without a drop** (its load is lost too). Break and re-place such a cannon before touching the block behind it.
- **Sloped BRD2 gangways** that no straight board can replace: one plank item at the old base, as above.
- **Flood water** in cells that are no longer part of a compartment after a hull change (CMP1).

## What a player must do by hand

- **Config files: nothing is required.** To get the new defaults (they never reach an existing file, see [Config files](#config-files)), delete `config/pirates_n_ships-server.toml` (or the copy in `<world>/serverconfig/`) or the lines in question; the game writes the defaults back. Check at least `rigging.net_max_length` (old files keep 12, now 32), `boarding.plank.max_length` (4 in files from before BRD2, now 6) and `flags_brig.navy_flag_min_standing` (an old file keeps 100, which since REP1 means a reputation of 100, the top of the new −101..101 scale: almost no one qualifies for the navy flag).
- **Denser structures and longer convoys (WG5):** an old file keeps `world.structures.*.spacing`/`separation` at the old, sparser values and `world_simulation.voyages.convoy_max_distance` at 3000; delete those lines to take 20/7 (villages), 28/10 (outposts), 36/14 (islands) and 6000. New spacing only reaches chunks not generated yet.
- **Denser structures again (WG6, 2026-10-12):** the spacing defaults changed once more, villages 30/10 → 20/7, navy outposts 40/16 → 28/10, pirate islands 52/20 → 36/14 (wrecks unchanged at 24/8). An existing config file, including one written by 0.1.0 or a WG5 build, keeps the numbers it has and generates exactly as before; delete the `spacing`/`separation` lines of `world.structures.seafarer_village`, `navy_outpost` and `pirate_island` (or the file) to take the new ones. They reach only chunks not generated yet.
- **Quest and template lists** (`quests.types.*`, `world_simulation.voyages.*_templates`) in an old file keep their old entries, so new quest types (QST1b `hunt_captain`; QST2 `escort`, `hunt_ship`, `plunder_convoy`, `hunt_patrol`) and the armed sloops (WS4c) are not offered until the lines are deleted or edited.
- **Old cannons** (before P2): break and re-place them.
- **Nets and gangways need nothing:** RL2 nets keep working (re-stringing one is only needed to join it into an RL4 fan), BRD1/BRD2 gangways convert by themselves.
- **Village people (LIFE1):** they are placed when a village's chunks generate, so seafarer villages generated before LIFE1 stay as they were: no role villagers move in, and a vanilla villager that wandered into one stays a vanilla villager. Only villages in newly generated chunks get their people. A crew member hired before LIFE1 has no `outfit` and keeps the crew's own look; a hired villager saves `pirates_n_ships:outfit` (its role id) and an older build drops it. The market square piece (`village/street_square_market`) changed its stalls; squares already generated keep the old single stall and get no vendors.
- **Do not switch loaders on a world** (NeoForge to Fabric or back): see [Switching loaders](#switching-loaders).

## Going back: a 0.1.0 world in an older build

Not supported. An older development build reads a 0.1.0 world with vanilla's rules: block ids it does not know become **missing-block air** (the blocks are deleted, also inside ships), unknown items are dropped from inventories and containers, unknown entities are skipped, properties it does not know are dropped and missing ones take its defaults, unknown saved-data and attachment fields are ignored and are then **overwritten** by its next save. Specifically: an RL4 fan's fittings have no `net_other` for a pre-RL4 build (every net loses its fittings' link), BRD4 gangways are cells without data for a pre-BRD4 build, a name board row is single plates. Keep a backup before opening a world in an older build. A Sable downgrade is worse, see below.

## Switching loaders

The same world folder opens on NeoForge and on Fabric (chunks, Sable's files, our saved data and config are the same), but **data attachments are not portable**: NeoForge stores them under its own key in the entity and chunk NBT and Fabric under another, so switching loaders loses every player's chart, Almanac page, career, reputation, criminal record, prisoner releases and quest log, the brig's prisoners, garrison posts and squads, and the player-placed block record of every chunk. Pick one loader per world.

## Config files

**Files:** `pirates_n_ships-server.toml` and `pirates_n_ships-client.toml`. The server file is **not per world by default**: FML (and Forge Config API Port on Fabric, which embeds the same code) uses `<world>/serverconfig/pirates_n_ships-server.toml` only when that file already exists there, otherwise the global `config/pirates_n_ships-server.toml` (`ConfigTracker.resolveBasePath`). `defaultconfigs/` only seeds a file that does not exist yet. Server values are synced to clients on join. (Several of our comments and playtest docs still say "per world"; they are wrong.)

**What the library does with an old file** (NeoForge `ModConfigSpec` and `ConfigTracker`, NeoForge 21.1.228 / FML 4.0.42; identical in Forge Config API Port 21.1.3): on load it checks the file against the spec; if anything differs (a key, a value, or only a comment, so nearly every update) it logs one warning "Configuration file … is not correct. Correcting", keeps up to five backups `pirates_n_ships-server-N.toml.bak`, corrects and rewrites the file:

- **Removed or renamed key:** deleted from the file (the old value survives only in the `.bak`). A renamed key is therefore a removed one plus a new one at its default; nothing carries the old value over.
- **Missing key or section:** added with its default.
- **Value outside the new range:** **clamped** to the nearest bound, not reset to the default. A value of the wrong type, or an enum value that no longer exists, falls back to the default.
- **Changed default:** never applied to an existing file; the old value stays.
- **List values** are only checked to be strings: stale ids (a removed quest type, template) stay in the file and are skipped by the mod.

**Keys removed or renamed** during development (an old file loses them; the replacement starts at its default):

| Old key | Now | Package |
|---|---|---|
| `boarding.plank.max_rise`, `rise_run`, `steep_fallback` | `boarding.plank.max_pitch` (45°) | BRD4 |
| `dry_hull.sea_sample_depth` | `dry_hull.sea_probe_height` | fix(hull) `148e3750` |
| `sailing.anchor_stiffness`, `anchor_damping`, `anchor_max_acceleration`, `anchor_slack`, `anchor_drop_ticks`, `anchor_raise_ticks`; `anchor_chain.depth_travel_time`, `drop_speed`, `min_travel_ticks`, `max_travel_ticks` | the AN2a chain model (`anchor_chain.sink_speed`, `water_drag`, `chain_*`, `holding_force`, …) | AN2a |
| `survival.cold_water_enabled`, `time_to_freeze_seconds`, `swimming_hunger_multiplier` | `survival.cold_water.*`, `survival.swim_exhaustion_multiplier`, `survival.swim_hunger.enabled` | survival |
| `hazards.waterspouts.chance_per_minute`, `hazards.whirlpools.chance_per_day` | `chance_per_check` | hazards |
| `grapple.launch.crossbow_draw_ticks`, `crossbow_enabled`, `crossbow_speed`, `crossbow_rope_length` | (the crossbow launch is gone) | GR3, GR4 |
| `crew.wages_enabled`, `crew.mutiny_enabled` | `crew.wages.enabled`, `crew.mutiny.enabled` (mutiny now off by default) | CR2 |
| `brig.press_gang_morale` | `crew.morale.press_gang_start` | LA2 |
| `cannons.npc.aim_tolerance` | (removed) | fix(gunnery) |
| `cargo_trade.plunder.navy_notice_chance`, `village_notice_chance`, `confiscate_when_noticed` | (removed) | LAW3b |
| `guide.give_on_first_join` (the `[guide]` section) | `almanac.give_on_first_join` | ALM |
| client `wave_effects.camera_sway_strength` | `camera_sway_fraction` | WV1 |
| client `ship_hud.corner` | `compass_corner`, `hull_corner` | HUD |
| client `career_hud.enabled`, `x`, `y` | `mode`, `show_seconds`, `corner` | HUD5 |
| `chart.show_other_players` | (removed: the chart never shows other players, the human 2026-10-12) | CHART2 |

**Ranges changed** (an old value outside is clamped): `chart.max_markers` 0–512 → 0–4096 with 0 now meaning "no limit" (CHART2; an old 0 meant "no markers" and now allows any number); `boarding.plank.max_length` 1–4 → 1–6 (BRD2); `rigging.net_max_length` 2–32 → 1–64 (RL4); `waves.ship_torque` 0–50 → 0–200 (WAV2); `law.flags.false_flag_wanted_threshold` 0–3 → 0–4 and `flags_brig.navy_flag_min_standing` ±1,000,000 → −101..101 with a new meaning (REP1; an old 100 stays 100, see above); client `bell_visuals.swing_degrees` 0–30 → 0–25.

**Defaults changed** (old files keep the old value): `chart.max_markers` 64 → 0, no limit (CHART2: a world or server set up before CHART2 keeps its cap of 64 until the line is changed to 0 or deleted; markers already placed are kept either way, a chart over a lowered cap only refuses new ones), `boarding.plank.max_length` 4 → 6, `rigging.net_max_length` 12 → 32, `sailing.keel_lateral_drag` 2 → 8, `stability.max_metacentric_height` 4 → 1.5, `sea_chest.sink_pull` 0.02 → 0.035, `hazards.whirlpools.chance_per_check` 0.005 → 0.002, `hazards.kraken.chance_per_day` 0.02 → 0.05, the GR4 grapple numbers (`throw_velocity`, `gravity`, `max_rope_length`, `launch.musket_speed`, `launch.musket_rope_length`), `world.structures.pirate_island.spacing` 40 → 64 → 52 → 36 and `separation` 24 → 20 → 14, `world.structures.seafarer_village.spacing`/`separation` 36/12 → 30/10 → 20/7, `world.structures.navy_outpost.spacing`/`separation` 48/20 → 40/16 → 28/10 (WG5 a little denser, WG6 on 2026-10-12 denser again), `world_simulation.voyages.convoy_max_distance` 3000 → 6000 (WG5), the WAV2/WAV4 wave forces (`waves.ship_torque`, `max_torque_per_mass`, `heave_strength`, `heave_per_mass`), the client's `wave_effects.surface_full_depth`, `foam_density` and `camera_sway`, the lists `careers.rewards.lieutenant_items`, `quests.types.*` and `world_simulation.voyages.*_templates`. CFG1 (running in parallel) reviews the defaults for 0.1.0; whatever it changes joins this list.

**Structure spacing is read at runtime since WG5.** Before WG5 the `spacing` and `separation` keys of `world.structures.*` did nothing (the structure sets carried fixed numbers); now the structure sets' placement `pirates_n_ships:configured_spread` reads them whenever a new chunk generates, and `/locate` follows the same grid. An old file keeps its old numbers (villages 36/12, islands 64/24, or spacing 40 in the oldest files, outposts 48/20; a file written by 0.1.0 or a WG5 build keeps 30/10, 52/20, 40/16 after WG6), which give exactly the old grid: an old world generates on as before, with no seam. A file that takes the new defaults (lines deleted) changes the grid only for chunks not generated yet; structures already placed stay, and at the edge of the explored world a new structure may start close to an old one. The salts did not change.

**Not config:** WHS1 replaced the whistle orders `drop_anchor`/`raise_anchor` with one `anchor` toggle. No config key and no saved data holds whistle ids (they travel only in a network payload; an unknown id is refused), and `/pirates crew order drop_anchor|raise_anchor` still exists, so nothing needs migrating. `ConfigOverrides` is a GameTest helper, not a migration; the mod has no config migration code.

## Sable

**Format.** Sable (2.0.6; we accept `[2.0.6, 3.0.0)` on both loaders) saves ships in `<world>/<dimension>/sublevels/`: region-style holding files `r.X.Z.slvlr` (which sub-levels sit in each world chunk) and storage files `r.X.Z.N.slvls` (large ones overflow into `r.X.Z.r/<n>.slvl`), compressed NBT, plus three saved-data files (`sable_sub_level_force_load_tickets`, `sable_sub_level_occupancy`, `sable_tracking_points`) (`refs/sable/common/.../sublevel/storage/`). Per ship: `uuid`, the plot (`plot_x`, `plot_z`, `log_size`, `data_version`, chunks with vanilla-style sections, block entities, ticks), the pose (centre of mass, orientation), world bounds, velocities above a threshold (scaled by Sable's `velocity_retained_on_load`), `display_name`, `loading_dependencies` and our `user_data`. Mass and physics properties are recomputed from the blocks.

**Versioning.** The only version check is the plot's `data_version` (`ServerLevelPlot.DATA_VERSION = 1`): 0 or 1 loads, anything else throws "Unsupported version" (a `log_size` mismatch too). Plot chunks get no Minecraft DataFixer; block states go through vanilla's `BlockState.CODEC` with partial recovery, so a removed block id inside a ship becomes air like in the world, and a block entity of an unknown type is skipped. A ship whose data cannot be read (IO error, NaN pose, empty plot) is logged, reported as a server toast and dropped from the world.

**Does a Sable update break ships?** Within 2.0.x, no: the plot format is version 1 and newer Sable reads 0 and 1. A future Sable that raises `data_version` would read our ships, but **going back to an older Sable after that throws on load, outside any catch** (`SubLevelSerializer.fullyLoad` → `ServerLevelPlot.load`), which most likely stops the server tick: never downgrade Sable on a world. Sable 3.0 is outside our declared range and needs a new release of this mod. Sable releases can change gameplay physics without touching the format (forces, buoyancy, splitting); that is a test concern, not a save one.

## Kept ids

Every block, item and entity id of 0.1.0. From 0.1.0 on these must never be renamed or removed: a world, a ship or a chest holding one would lose it (see [Lost or reset](#lost-or-reset)). `KeptIdsTest` fails when an id listed here has no name in the generated `en_us.json`; to retire one, take it out of this table and describe the loss above. Block items share their block's id and are listed under Block. Wood-family blocks (`oak_*`) get one id per shipped wood. The nameplate keeps the id `nameplate` although it is shown as "Name Board" (NAME2).

<!-- kept-ids:start -->
| Kind | Ids |
|---|---|
| Block | `bilge_pump`, `boarding_plank`, `brig_bars`, `brig_door`, `cannon`, `capstan`, `captains_almanac`, `cargo_barrel`, `cargo_crate`, `chainplate`, `chart_table`, `cleat`, `crows_nest`, `figurehead_eagle`, `figurehead_lion`, `figurehead_mermaid`, `figurehead_skull`, `flag_pedestal`, `flagpole`, `hammock`, `harbor_desk`, `helm`, `hull_patch`, `map_tile`, `mast_hoop`, `mooring_ring`, `nameplate`, `notice_board`, `oak_bulwark`, `oak_bulwark_port`, `oak_oar_bench`, `oak_railing`, `oak_railing_gate`, `oak_railing_port`, `pantry`, `ratline_net_cell`, `ratlines`, `rope_coil`, `sail_winch`, `sea_chest`, `sea_cot`, `ship_lantern`, `ships_bell`, `stern_window`, `swivel_gun`, `water_barrel`, `yard` |
| Item | `bandana`, `bounty_proof`, `brig_key`, `cannonball`, `captains_boots`, `captains_breeches`, `captains_coat`, `captains_hat`, `captains_whistle`, `carpenters_hammer`, `chain_shot`, `chart`, `cloth`, `cutlass`, `doubloon`, `grapeshot`, `grappling_hook`, `hardtack`, `jolly_roger_flag`, `kraken_beak`, `kraken_ink`, `kraken_spawn_egg`, `lead_shot`, `lime`, `merchant_flag`, `musket`, `nails`, `navy_flag`, `navy_hat`, `navy_officer_spawn_egg`, `navy_soldier_spawn_egg`, `oar`, `officer_hat`, `officers_boots`, `officers_breeches`, `officers_coat`, `paddle`, `pirate_hat`, `pirate_spawn_egg`, `pistol`, `rapier`, `ratline_net`, `rope`, `rum`, `saber`, `sailor_spawn_egg`, `salt_pork`, `salted_fish`, `saw`, `shackles`, `shark_spawn_egg`, `ship_commission`, `ship_receipt`, `shipwright_toolkit`, `spices`, `tobacco`, `treasure_map` |
| Entity | `anchor`, `cannonball`, `crew_member`, `grappling_hook`, `hammock_seat`, `harbor_master`, `kraken`, `kraken_part`, `lead_ball`, `meal_seat`, `navy_officer`, `navy_soldier`, `oar_seat`, `pirate`, `pirate_captain`, `rope_rider`, `sailor`, `sea_chest`, `shark`, `station_seat`, `waterspout`, `whirlpool` |
<!-- kept-ids:end -->

Two block-entity types were added after this table was written (FIX3): `cannon_rear` and `brig_door_upper`. They are additive: a rear cannon half or an upper brig door half from an older world gets its empty block entity on first access, and nothing is lost.

Not in the lang file and so not guarded here: block entity type ids (e.g. `boarding_plank`, `net_fitting`, `ratline_net_cell`, `nameplate`), data component, attachment and saved-data ids. They are equally part of the save format; renaming one loses its data the same way.

## Ports generated before STR7 (after 0.1.0)

STR7 (room for ships at ports) changes only how new ports generate and where a ship appears; nothing saved changes
shape.

- **Old ports keep their shallow berths.** A village, island or outpost generated before STR7 has no extension
  sections and no carved basin (both happen once, when the structure's chunks generate; `world.ports.pier_extension`
  and `carve_berths` do nothing to chunks that exist). Its `ports` record and berths load unchanged.
- **Ships still find room there.** A shipwright's pickup, a granted ship and an NPC ship leaving or reaching the port
  now go through the spot search (`world.port.ShipSpots`): the berth's own mooring when it is deep, otherwise the
  nearest deep, open water reached from the berths within `world.ports.ship_spot_search` (64 blocks), so at an old,
  shallow port the ship is placed farther out instead of on the sand. Where there is none, the pickup refuses with
  "No deep water near the pier for her" and the receipt or commission stays.
- **Chunks generated after the update** at the edge of an old port are not carved either: the carve belongs to the
  structure's own placement. An old port's start is not re-placed.
- **New config keys** `world.ports.ship_clearance` (3), `berth_depth` (3), `ship_spot_search` (64), `pier_extension`
  (true), `carve_berths` (true) are added to an existing server file with their defaults.
- **Template pieces changed:** the pier's seaward rail and the jetty's west end post went (no jigsaw was added, so the
  jigsaw draws and the layout of a port generated after the update are the same as before for the same seed, apart
  from the extension sections). Already generated piers keep their blocks.

## Ports generated before STR8 (after 0.1.0)

STR8 (second structure polish: piers in the sea, level trails, doors, no pieces over water, cold shores) changes how
new ports are planned and placed; nothing saved changes shape and old ports keep their blocks.

- **Placed chunks stay as they are.** Every rule works when a port is planned (`PortStructure.plan`) or its chunks are
  placed; chunks that exist are never placed again.
- **A port planned before the update but not yet fully placed** (its start saved, some chunks not generated yet) keeps
  its plan: its pieces, their heights and its streets' processor lists (`village_street`, `pirate_island_trail`, the
  lanes' `connections`; all still shipped). So its remaining chunks place exactly as before STR8: no levelled bed, no
  straight pools, no land or wall rules. Only the door clearing (`world.structures.clear_doors`, in `afterPlace`) runs
  on its remaining chunks too; it only takes ground, plants, snow and wayside decorations out of a door's way.
- **New structures and structure sets** `pirates_n_ships:{seafarer_village,pirate_island,navy_outpost}_cold` and
  `{seafarer_villages,pirate_islands,navy_outposts}_cold` are added; the old ids are unchanged (the old structures gained
  the optional codec fields `cold_biomes` and `cold`, which old datapacks without them read as before).
- **Placement of new ports moves at the edge of the explored world:** the new exclusion zones against vanilla's
  `minecraft:villages` (6 chunks) and `minecraft:woodland_mansions` (10) and the cold-shore rule change which grid
  chunks start a port; the salts and grids are unchanged, so ports already placed stay where they are and new chunks
  next to them may start a port where none would have been (or none where one would).
- **New config keys** `world.structures.level_trails` (true), `clear_doors` (true), `piers_end_in_water` (true) and
  `world.structures.<kind>.cold_frequency` (0.1) are added to an existing server file with their defaults.
- **Template pieces changed:** the bends, forks, junctions and the crossroads point their ways on at the new straights
  pools (`village/streets_straight`, `pirate_island/paths_straight`, `navy_outpost/lanes_straight`); the jigsaw count
  is the same, but the draws differ, so the same seed lays out a different port than before the update.

## Known risks found while writing this

- **Enum names in saved data:** several codecs read an enum by name with `valueOf` inside an `xmap` (`FactionState`, `Voyage.faction`, `ReleaseRecord`, `ShipAnchor`), which throws on an unknown name instead of returning a codec error. Renaming or removing one of those enum constants after 0.1.0 would make the whole file fail to read, and vanilla then starts that saved data empty and overwrites it on the next save. Treat these enum constants like ids.
- **`ShipRegistry` and `ShipUpkeepData` pass `null` as their `DataFixTypes`.** Vanilla's `DimensionDataStorage.readTagFromDisk` calls `update` on it unconditionally; NeoForge null-checks it, and on Fabric Fabric API's `PersistentStateManagerMixin` (object builder API) wraps the call with a null check, so both load today. The comment in `BountyBoardData` saying null "would crash on Fabric" is out of date; the two files depend on Fabric API keeping that mixin.
- **Sable's plot light data and chunk attachments** may not round-trip (written into the plot tag, read from the chunk tag, `ServerLevelPlot` l.409-410 vs 525/533); read from the code, not tested. Our only chunk attachment is the law's `player_placed_blocks`; if it is ever written for a plot chunk it may not survive a reload.
