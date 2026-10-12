# Config defaults audit (CFG1)

Every config value of the mod (1220: 1122 server, 98 client, in 45 server and 22 client sections), its default, range and comment as the player sees them, where the code reads it, which values the tests pin, and a verdict against `docs/design.md`. Made on 2026-10-10 from `main` at the CFG1 branch point. **No value was changed:** the human decides from this table, CFG2 applies the answers.

Verdicts: **OK** matches the design and is a reasonable play default; **TEST** reads like a test or debug setting; **DESIGN** contradicts a number or rule in `docs/design.md`; **DEAD** is read by nothing that runs in the game; **DUP** does the same as another value; **NAME** has a key or comment that misleads.

## Summary

| Verdict | Values |
|---|---|
| OK | 1164 |
| DESIGN | 22 |
| DEAD | 23 |
| DUP | 2 |
| NAME | 5 |
| TEST | 4 |
| **total** | **1220** |

No code default contradicts a number in `docs/design.md`: every default the design names (checked one by one, from the block limit 2048 and the 25° heel cap to the quest counts, ransoms, fees and wave torques) matches the code. The DESIGN and most TEST rows are old values that old config files still hold (next section). The other problems are values that do nothing (DEAD), two pairs that overlap (DUP), comments that describe an older state or a weight that is only an on/off (NAME) and one maintenance switch (TEST).

### Old config files keep old defaults (read first)

The config library (NeoForge `ModConfigSpec`, Forge Config API Port on Fabric) never applies a changed default to an existing file: it adds missing keys, removes unknown ones (with a `.bak`) and clamps out-of-range values, nothing else. So every default that changed since the first dev worlds still has its old value in an old file. Comparing the human's dev files (`neoforge/run/config/pirates_n_ships-server.toml`, `-client.toml` and their five `.bak` generations; the Fabric dev client has written no config file yet) with the code found the rows below; they carry a DESIGN verdict (an old default the design has since replaced) or TEST (a test setting left in a file, never a default). **The simplest fix is to delete both config files of a dev or server install once; the game writes them again with today's defaults.** The current dev server file still holds two of them (`boarding.plank.max_length` 4, `rigging.net_max_length` 12); `navy_flag_min_standing` 100 and the rest sit in the older `.bak` generations, which shows what any world or server set up before those packages still has.

- `flags_brig.navy_flag_min_standing`: old `100` (server-5.toml.bak, the oldest), code `0`. REP1 turned it into a navy reputation (-100..100, 101 = never); 100 now means only a player at full navy reputation flies the navy flag legitimately, so the navy flag is false colours for practically everyone (design.md l.672: default 0).
- `boarding.plank.max_length`: old `4` (current server file and every .bak), code `6`. BRD2 raised the gangway from 4 to 6 cells (design.md l.479); the current dev file still has 4.
- `rigging.net_max_length`: old `12.0` (current server file and server-1.toml.bak), code `32.0`. the human found 12 too short and it became 32 (design.md l.366); the current dev file still has 12.
- `grapple.max_rope_length`: old `24.0` (server-5.toml.bak, the oldest), code `32.0`. GR4 set 32 by hand (design.md l.460).
- `grapple.throw_velocity`: old `1.5` (server-5.toml.bak, the oldest), code `3.0`. GR4 set 3.0 so a throw reaches the rope length (design.md l.460).
- `grapple.gravity`: old `0.03` (server-5.toml.bak, the oldest), code `0.02`. GR4 set 0.02 (design.md l.460).
- `grapple.launch.musket_speed`: old `2.4` (server-5.toml.bak, the oldest), code `1.6`. GR4 set 1.6 (design.md l.460).
- `grapple.launch.musket_rope_length`: old `48.0` (server-5.toml.bak, the oldest), code `64.0`. GR4 set 64 (design.md l.460).
- `waves.ship_torque`: old `5.5` (server-5.toml.bak, the oldest), code `18.0`. WAV2/WAV4 retuned it to 18 for the bigger sea (design.md l.337).
- `waves.max_torque_per_mass`: old `2.0` (server-5.toml.bak, the oldest), code `2.5`. WAV2 set 2.5 (design.md l.332).
- `waves.heave_strength`: old `0.5` (server-5.toml.bak, the oldest), code `0.95`. WAV4 set 0.95 (design.md l.337).
- `waves.heave_per_mass`: old `6.0` (server-5.toml.bak, the oldest), code `25.0`. WAV4 set 25 (design.md l.337).
- `wave_effects.foam_density`: old `2.5` (client-2 and client-3 .toml.bak), code `1.25`. WAV4 halved it to 1.25 (design.md l.337).
- `wave_effects.camera_sway`: old `true` (client-2 to client-5 .toml.bak), code `false`. camera sway is off by default (design.md l.329, motion sickness).
- `melee.guard_absorb_stamina_per_damage`: old `1.5` (server-5.toml.bak, the oldest), code `1.0`. P9b lowered it to 1.0 on the human's request.
- `hazards.kraken.chance_per_day`: old `0.02` (server-5.toml.bak, the oldest), code `0.05`. raised to 0.05 (commit b14815db).
- `careers.rewards.lieutenant_items`: old `[officer_hat, saber]` (server-5.toml.bak, the oldest), code `[pirates_n_ships:officer_hat, pirates_n_ships:saber, pirates_n_ships:officers_coat, pirates_n_ships:officers_breeches, pirates_n_ships:officers_boots]`. ART9/ART13 added the coat, breeches and boots to the lieutenant's gifts (design.md l.271).
- `quests.types.seafarer_village`: old `without escort` (server-5.toml.bak, the oldest), code `[deliver, kill_monster, hunt_pirates, find_treasure, hunt_captain, escort]`. QST2 added the sea quests (design.md l.588); an old list never offers them.
- `quests.types.navy_outpost`: old `without escort, hunt_ship` (server-5.toml.bak, the oldest), code `[hunt_pirates, turn_in, kill_monster, deliver, hunt_captain, escort, hunt_ship]`. QST2 added the sea quests (design.md l.588).
- `quests.types.pirate_island`: old `without plunder_convoy, hunt_patrol` (server-5.toml.bak, the oldest), code `[hunt_navy, find_treasure, deliver, kill_monster, plunder_convoy, hunt_patrol]`. QST2 added the sea quests (design.md l.588).
- `world_simulation.voyages.navy_templates`: old `[starter_sloop, starter_sloop_basic]` (server-5.toml.bak, the oldest), code `[pirates_n_ships:navy_sloop_armed]`. WS4c made the armed navy sloop the patrol ship (design.md l.586); an old list sends unarmed sloops on patrol.
- `world_simulation.voyages.pirate_templates`: old `[starter_sloop, starter_sloop_basic]` (server-5.toml.bak, the oldest), code `[pirates_n_ships:pirate_sloop_armed]`. WS4c made the armed pirate sloop the raider (design.md l.586).
- `world.structures.pirate_island.spacing`: old `40` (server-5.toml.bak, the oldest), code `64`. now 64; a datapack value anyway (see its DEAD verdict).
- `core.debug`: old `true` (server-5.toml.bak, the oldest), code `false`. never a code default: a debug setting left in the file.
- `hazards.spawn_check_interval_ticks`: old `20` (server-5.toml.bak, the oldest), code `200`. never a code default (200): a test setting left in the file, ten times the spawn checks.
- `hazards.waterspouts.chance_per_check`: old `1.0` (server-5.toml.bak, the oldest), code `0.02`. never a code default (0.02): a test setting, a waterspout on every check in a thunderstorm.

### Decide these (gameplay-changing first; answer yes or no per line)

1. **DESIGN/TEST** the 26 old values of the section above (`flags_brig.navy_flag_min_standing` first). **Proposal:** delete `pirates_n_ships-server.toml` and `-client.toml` of the dev client (and of any test server) once, and say so in the release notes for players upgrading. *Why:* old files keep old defaults; `navy_flag_min_standing` 100 makes the navy flag false colours for nearly everyone.
2. **DEAD** `ships.sinking_enabled`. **Proposal:** Remove the key now and re-add it with the feature (or wire it in a code package: off = flood water never takes the last buoyancy). *Why:* Read only by SettingsConfigTest/SettingsGameTests; switching it off changes nothing, though design.md l.702 (§17 Ships) promises "sinking enabled".
3. **DUP** `world.spawn_weights.shark`. **Proposal:** Remove it; keep `mobs.shark.spawn_weight` as the only shark weight and lower that one from 8 to 3. *Why:* Read nowhere; MobContent spawns sharks with mobs.shark.spawn_weight (8), which progress.md "Defaults to review" calls likely too many (squid 1, dolphin 1-2 in the same pool).
4. **DUP** `flags_brig.detection_max_range`. **Proposal:** Set it to 48 (= law.flags.observe_range), so the false-colours odds fall to zero where navy observers stop looking; or drop one of the two ranges. *Why:* FlagCrimes only looks at ships within law.flags.observe_range (48, x1.5 with a nest), so the falloff of FalseColorsDetection between 48 and 96 never applies: the comment "beyond which false colors can't be detected" is never the limit.
5. **DEAD** `melee.directional_mode`. **Proposal:** Remove the key until milestone 21 implements directional parries (the §17 row stays as the plan). *Why:* Its own comment says "not implemented yet, no effect"; MeleeParams carries it unused (design.md l.495, l.713).
6. **DEAD** `law.severity.fence_plunder`, `law.repeat_cooldown_seconds.fence_plunder`. **Proposal:** Skip legacy crime types when LawConfig declares severity and repeat_cooldown_seconds (CrimeType.legacy() exists already). *Why:* fence_plunder is never recorded since LAW3b (design.md l.657); its two values are generated by the loop over every CrimeType and do nothing.
7. **DEAD** `ships.order_price_doubloons.sloop`, `ships.order_price_doubloons.merchant_cog`, `ships.order_price_doubloons.brigantine`. **Proposal:** Remove the group; the price is the template's `price` times `ships.order_price_factor`. *Why:* ShipConfig's own class comment says "declared but unread"; design.md l.171 (SW1) prices orders from the template. The 200/350/500 in progress.md never took effect.
8. **DEAD** `ships.build_time_days.sloop`, `ships.build_time_days.merchant_cog`, `ships.build_time_days.brigantine`. **Proposal:** Remove the three keys (`default` 1.0 per 500 blocks then covers both orderable templates); or rename them to the real template paths `starter_sloop` / `starter_sloop_basic` if per-template times are wanted. *Why:* ShipConfig.buildDays looks the key up by template path (design.md l.171), and the templates are starter_sloop, starter_sloop_basic, navy_sloop_armed, pirate_sloop_armed: no sloop, merchant_cog or brigantine exists, so these are never matched. Their comments ("days to build a sloop") also misread the per-500-block factor.
9. **DEAD** `ships.wreck_persistence_days`. **Proposal:** Remove it until sunk ships can turn back into world blocks; `assembly.split.wreck_persist_ticks` is the working setting for split-off wrecks. *Why:* Read only by SettingsConfigTest; design.md l.224 and l.702 describe the option, nothing implements it.
10. **DEAD** `world.spawn_weights.sailor`, `world.spawn_weights.navy_soldier`, `world.spawn_weights.navy_officer`. **Proposal:** Remove the three keys (sailors, harbor masters and garrisons are placed by the structures, nothing spawns them naturally). *Why:* Read nowhere; design.md l.716 (§17 World) lists "mob spawn weights", but only world.spawn_weights.pirate and mobs.shark.spawn_weight are read.
11. **NAME** `world.spawn_weights.pirate`. **Proposal:** Fix the comment (or turn it into a boolean): "Pirates spawn naturally in camps (0 = never). The spawn weight itself, 10, is in the structure (a datapack value)". *Why:* PirateIslandSpawns only tests it for > 0; the weight the game uses is the 10 that datagen writes into the pirate_island structure's spawn overrides, so 20 spawns no more pirates than 10.
12. **DEAD** `world.structures.pirate_island.spacing`, `world.structures.pirate_island.separation`, `world.structures.seafarer_village.spacing`, `world.structures.seafarer_village.separation`, `world.structures.navy_outpost.spacing`, `world.structures.navy_outpost.separation`, `world.structures.wreck.spacing`, `world.structures.wreck.separation`. **Proposal:** Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Old config files keep `40` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. *Why:* Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). Old value: now 64; a datapack value anyway (see its DEAD verdict).
13. **DEAD** `audio.ambience_volume`. **Proposal:** Wire it as a client multiplier on the hull creak, hazard and anchor sounds when ambience moves to client playback; until then remove it. *Why:* Read nowhere (AudioConfig: "needs client-side sound playback (later)"); design.md l.718 (§17 Audio) lists it.
14. **NAME** `flags.follow_wind`. **Proposal:** Fix the comment: "Flags stream downwind at the wind's exact angle, doubling as a wind indicator". *Why:* The comment still says "in 90 degree steps"; since FL1 the flag stores the exact downwind angle (design.md l.249).
15. **NAME** `crew_stations.ack_radius`. **Proposal:** Fix the comment: "Players within this many blocks of a crew member see its refusals and reports on the action bar (0 = nobody)". *Why:* MSG1 removed the order acknowledgements (design.md l.364); CrewStations.say now only carries refusals and reports.
16. **NAME** `provisions.scurvy_enabled`. **Proposal:** Drop "slower healing" from the comment (or implement it). *Why:* Scurvy gives Weakness only; "slower healing is not implemented" (design.md l.415).
17. **NAME** `audio.music_enabled`. **Proposal:** Fix the comment: "Play the mod's sea and shanty music". *Why:* There is no battle or harbor music yet (design.md l.690: "Battle and harbor music come with their milestones").
18. **TEST** `world_simulation.lanes.version`. **Proposal:** Keep the value; optionally replace it later with an operator command (`/pirates world lanes reset`). *Why:* A cache-buster for saved lanes, not a gameplay setting: raising it once throws every cached lane away.

### Worth a look, but OK as they are

Not verdicts, tuning questions the docs leave open; each default below is the design's or a measured one.

- `provisions.thirst_desertion_days` 1.0: one in-game day without water and the whole crew deserts at the next dawn, whatever its morale (the tests pin it to 100 to stay clear of it). The design only says thirsty crews "eventually desert"; 2 would give a captain one full day to find water.
- `melee.stagger_ticks` 20, `parry_stagger_ticks` 25, `exhausted_poise_factor` 0.5: the human finds the stagger too frequent (progress.md, milestone 7, "tuning on hold").
- `assembly.split.wreck_persist_ticks` 0 (split-off wrecks stay forever): progress.md asks whether that costs performance on big servers; a PERF1 measurement should decide.
- `sailing.sail_force_scale` 1.0: design.md §21 still lists the spike-3 guess of 0.3 to 0.5 as open; SH1, SH2 and OAR1 tuned the ships against 1.0, so the open question in §21 is stale rather than the value.
- `crew.mutiny.enabled` false and `wave_effects.camera_sway` false: progress.md "Defaults to review" still lists both as true; the code and the design now say off (the list is stale).

### Consistency checks

- `python3 tools/check_config_docs.py` reads the config from the Java sources and fails when a section has no row in `docs/guide.md` §13 (or a row has no section), a nested group is not named in its row, or a backticked name in a row is no longer a key. Run on this branch: **ok** after fixing the rows (it found the client section `boarding_view` without a row and the groups `hazards.waterspouts`, `hazards.whirlpools`, `ships.build_time_days`, `ships.order_price_doubloons` and `careers.rewards.navy_ship_price` unnamed). It is item 6 of the release checklist in `docs/releasing.md`. `--keys` lists the keys each row leaves out (rows describe a section, they do not list every key; this table does).
- The guide rows now also say which values are declared but unread (`ships`, `world`, `melee`, `audio`).
- Config names in `docs/design.md` that no longer exist (left for the human, CFG1 does not edit the design): `law.crimes.selling_plunder` (l.658; the key is `law.severity.selling_plunder`), `crew.desertion_enabled` and `crew.mutiny_enabled` (l.403; now `crew.desertion.enabled`, `crew.mutiny.enabled`), `boarding.max_rise` (l.479; replaced by `boarding.plank.max_pitch` in BRD4), `oars.thrust_per_oar` and `max_speed_under_oars` (l.321, the OAR1 plan; now `oars.thrust_per_rower`, `oars.max_speed`), `dry_hull.hide_water_plants` and `dry_hull.hidden_plants_refresh_ticks` (l.205; the client section is `dry_hull_view`).
- `docs/progress.md` "Defaults to review" is partly stale (mutiny on, camera sway on, waterspout chance per minute, grapple rope 24 and throw 1.5, shipwright prices 200/350/500); CFG1 leaves progress.md alone, CFG2 can rewrite that list from this table.

### How the table was made

- Keys, defaults, ranges and comments come from the loaded schema (`ModConfigs.schema`, every module's `registerConfig()` run in a throw-away JUnit test that was not committed), so values declared in loops (crimes, deeds, ranks, mobs) are included.
- "Read in" lists the classes outside tests and datagen that reference the value's field (through the field that holds it: a map, record or loop-built group shows the readers of the whole group). A row with none is DEAD; what does reference it is named in the reasoning.
- "Tests pin" lists the values `ConfigOverrides.during/apply` sets in tests (distinct values, shortened). Most pins switch a feature off or shorten a timer to keep a test fast; none suggests that a default is wrong.
- Design lines are cited as `design.md l.N` on the CFG1 branch point.

## Server config

### `core` (server): General settings

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `debug` | `false` |  | Log extra debug information from Pirates 'n' Ships systems | CoreModule, HullRuntime, ShipBallast | !before, false, true | **TEST**: Old config files keep `true` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; never a code default: a debug setting left in the file. |

### `assembly` (server): Ship assembly and disassembly at the helm

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Using a helm in the world turns the connected blocks into a ship | ShipAssembler, ShipHelm |  | OK |
| `disassembly_enabled` | `true` |  | Using the helm of a still, level ship turns it back into world blocks | ShipAssembler |  | OK |
| `max_blocks` | `2048` | 1 – 65536 | Maximum number of blocks one ship may have. Also stops runaway gathers into docks or buildings | ShipAssembler |  | OK |
| `max_linear_speed` | `0.3` | 0.0 – 20.0 | Disassembly is refused while the ship moves faster than this (m/s) | ShipAssembler |  | OK |
| `max_angular_speed` | `0.15` | 0.0 – 10.0 | Disassembly is refused while the ship turns faster than this (rad/s) | ShipAssembler |  | OK |
| `max_tilt_degrees` | `6.0` | 0.0 – 45.0 | Disassembly is refused while the ship is tilted more than this from level (degrees) | AssemblyResult, ShipAssembler |  | OK |
| `restore_sea_on_assembly` | `true` |  | Refill the hole a hull leaves in the sea with water sources when it is assembled | ShipAssembler |  | OK |
| `drain_hull_on_disassembly` | `true` |  | Remove the sea water that would end up inside the hull when a ship is disassembled in water | ShipAssembler |  | OK |
| `move_entities_on_disassembly` | `true` |  | Move players and mobs standing on deck onto the placed deck blocks when a ship is disassembled | ShipAssembler |  | OK |
| `split.enabled` | `true` |  | Ships split when a part is cut off. Off = also switches Sable's sub_level_splitting off while this server runs, so ships never split | ShipSplits | false | OK |
| `split.wreck_min_blocks` | `4` | 1 – 4096 | A loose piece with fewer blocks than this breaks up into items instead of drifting off as a wreck | ShipSplits |  | OK |
| `split.wreck_persist_ticks` | `0` | 0 – 100000000 | Ticks a wreck piece stays while no player is near it before it is removed (0 = forever) | ShipSplits |  | OK |
| `rejoin.enabled` | `true` |  | The Shipwright's Toolkit can rejoin pieces of the same ship that lie together | ShipRejoin | false | OK |
| `rejoin.max_piece_blocks` | `200` | 1 – 65536 | Largest piece (in blocks) the toolkit can join onto the marked piece; bigger halves need a shipwright | ShipRejoin | 5 | OK |
| `rejoin.max_angle` | `5.0` | 0.0 – 45.0 | How far (degrees) the pieces may be off level and off a multiple of 90 degrees to each other | ShipRejoin |  | OK |
| `rejoin.max_gap` | `0.5` | 0.0 – 2.0 | How far (blocks) the piece may be off the marked piece's block grid before it is snapped onto it | ShipRejoin |  | OK |
| `rejoin.nails_per_rejoin` | `4` | 0 – 64 | Nails used up by one rejoin | ShipRejoin |  | OK |
| `rejoin.toolkit_durability` | `64` | 1 – 10000 | Uses of a Shipwright's Toolkit (one per rejoin) | ShipwrightToolkitItem |  | OK |
| `rejoin.work_ticks` | `20` | 1 – 1200 | Ticks of hammering between the second click and the rejoin | ShipRejoin |  | OK |
| `rejoin.seam_radius` | `6.0` | 1.0 – 32.0 | While a piece is marked, its blocks this close (blocks) to other pieces of the same ship show particles | ShipRejoin |  | OK |

### `dry_hull` (server): Dry hulls below the waterline: water occlusion, hull runtime and the buoyancy correction

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Keep the inside of closed hulls free of water (no water rendered, no swimming below deck) | FloodBreathing, HullRuntime, ShipBallast |  | OK |
| `partial_blocks` | `true` |  | Also keep the empty part of slabs, stairs, trapdoors, hatches and doors free of water when it faces the dry inside and no sea (a deck hatch counts as inside the ship) | HullRuntime |  | OK |
| `dry_buoyancy` | `true` |  | The dry air inside a hull below the waterline lifts the ship | DryHullConfig |  | OK |
| `dry_buoyancy_scale` | `1.0` | 0.0 – 10.0 | Multiplier on the lift of the submerged dry volume (1.0 = Sable's own float force per block) | DryHullConfig |  | OK |
| `flood_weight` | `true` |  | Water inside a hull weighs the ship down | DryHullConfig |  | OK |
| `flood_weight_scale` | `1.0` | 0.0 – 10.0 | Multiplier on the weight of flood water (1.0 = Sable's float force per block) | DryHullConfig |  | OK |
| `region_rebuild_ticks` | `10` | 1 – 200 | Least ticks between two rebuilds of a ship's dry regions while it floods | HullRuntime |  | OK |
| `sync_check_ticks` | `5` | 1 – 100 | How often (ticks) the server looks for players who started seeing a ship and sends them its dry regions | HullRuntime |  | OK |
| `save_interval_ticks` | `40` | 1 – 1200 | How often (ticks) a changing flood state is written into the ship's saved data | HullRuntime |  | OK |
| `sea_probe_height` | `24` | 1 – 128 | How far up from the hull's bottom to follow the water that touches it when finding the sea surface | HullRuntime |  | OK |
| `async_analysis` | `true` |  | Run hull re-analysis on a background thread (the first analysis after assembly or loading is synchronous) | HullRuntime |  | OK |
| `flood_breath` | `true` |  | Breath runs out below the flood water inside a ship's rooms, also where the room still counts as dry or the flood stands higher than the sea | FloodBreathing | false | OK |

### `flooding` (server): Hull breaches, water inflow, pumps and hull analysis

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Ships take on water through breaches, holes, low rims and open hatches below the waterline | FloodingConfig | false | OK |
| `inflow_rate` | `1.0` | 0.0 – 20.0 | Multiplier for how fast water flows through breaches, holes and open doors or hatches | FloodingConfig | 10.0, inflowRate | OK |
| `pump_rate` | `1.0` | 0.0 – 100.0 | Blocks of water one working pump removes per second | FloodingConfig | 10.0 | OK |
| `pump_enabled` | `true` |  | Bilge pumps work: a player using one, or a crew member ordered to it, removes flood water | BilgePumps, HullRuntime | false | OK |
| `pump_reach` | `4` | 0 – 16 | Blocks below its own cell a bilge pump's intake reaches (through decks) to find a flooded compartment | BilgePumps, HullRuntime |  | OK |
| `pump_use_ticks` | `8` | 1 – 100 | Ticks one use keeps a pump working. Holding the use key repeats the use every 4 ticks, so keep this above 4 | BilgePumps |  | OK |
| `pump_exhaustion` | `0.025` | 0.0 – 4.0 | Food exhaustion per pump use (4 = one hunger point; holding the key uses the pump 5 times per second) | BilgePumps |  | OK |
| `patch_enabled` | `true` |  | Hull patches can be placed into breaches to close them | HullPatchItem |  | OK |
| `debounce_ticks` | `10` | 0 – 200 | Ticks a hull must stay unchanged before it is re-analyzed after block changes | FloodingConfig |  | OK |
| `max_debounce_ticks` | `60` | 0 – 1200 | Latest re-analysis after the first unprocessed hull change, even while the hull keeps changing | FloodingConfig |  | OK |
| `reanalysis_tilt_degrees` | `5.0` | 0.5 – 45.0 | Refresh a hull's water heights when the ship has tilted this many degrees since the last refresh (its compartments never change with the tilt) | HullRuntime | 0.5 | OK |

### `ships` (server): Sinking, wrecks and shipwright orders

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `sinking_enabled` | `true` |  | Ships that lose their buoyancy sink to the seabed. Off = ships stay afloat however much water they take on | — (only SettingsConfigTest, SettingsGameTests) |  | **DEAD**: Remove the key now and re-add it with the feature (or wire it in a code package: off = flood water never takes the last buoyancy). Read only by SettingsConfigTest/SettingsGameTests; switching it off changes nothing, though design.md l.702 (§17 Ships) promises "sinking enabled". |
| `wreck_persistence_days` | `0` | 0 – 10000 | In-game days a sunk ship stays a sub-level on the seabed before it turns back into world blocks (0 = forever) | — (only SettingsConfigTest) |  | **DEAD**: Remove it until sunk ships can turn back into world blocks; `assembly.split.wreck_persist_ticks` is the working setting for split-off wrecks. Read only by SettingsConfigTest; design.md l.224 and l.702 describe the option, nothing implements it. |
| `shipwright_orders` | `true` |  | Shipwrights in seafarer villages sell prebuilt ships that are picked up at the village dock | ShipOrders | false | OK |
| `order_price_factor` | `1.0` | 0.0 – 100.0 | Multiplier on the doubloon price of each ship template a shipwright sells (0 = free) | ShipOrders |  | OK |
| `order_materials_factor` | `1.0` | 0.0 – 100.0 | Multiplier on the logs and wool a shipwright wants for an ordered ship (0 = no materials) | ShipOrders |  | OK |
| `max_orders_per_port` | `3` | 0 – 100 | Ship orders a seafarer village's shipwright takes at once (open orders of all players, picked up ones free a slot) | ShipOrders |  | OK |
| `ship_status_hud` | `true` |  | Send players aboard a ship its status (heading, speed, rudder, water per compartment) for the ship HUD. Off = the HUD shows for nobody | ShipStatusSync | false | OK |
| `ship_status_sync_interval_ticks` | `20` | 1 – 200 | How often (ticks) the server checks the ship status of players aboard and sends it when it changed (unchanged, it still goes out every fifth time) | ShipStatusSync | 7 | OK |
| `build_time_days.default` | `1.0` | 0.0 – 100.0 | In-game days a shipwright needs per 500 blocks of a ship template without its own entry here (0 = ready at once) | ShipConfig |  | OK |
| `build_time_days.sloop` | `2.0` | 0.0 – 100.0 | In-game days a shipwright needs to build a sloop (0 = ready at once) | ShipConfig |  | **DEAD**: Remove the three keys (`default` 1.0 per 500 blocks then covers both orderable templates); or rename them to the real template paths `starter_sloop` / `starter_sloop_basic` if per-template times are wanted. ShipConfig.buildDays looks the key up by template path (design.md l.171), and the templates are starter_sloop, starter_sloop_basic, navy_sloop_armed, pirate_sloop_armed: no sloop, merchant_cog or brigantine exists, so these are never matched. Their comments ("days to build a sloop") also misread the per-500-block factor. |
| `build_time_days.merchant_cog` | `3.0` | 0.0 – 100.0 | In-game days a shipwright needs to build a merchant cog (0 = ready at once) | ShipConfig |  | **DEAD**: Remove the three keys (`default` 1.0 per 500 blocks then covers both orderable templates); or rename them to the real template paths `starter_sloop` / `starter_sloop_basic` if per-template times are wanted. ShipConfig.buildDays looks the key up by template path (design.md l.171), and the templates are starter_sloop, starter_sloop_basic, navy_sloop_armed, pirate_sloop_armed: no sloop, merchant_cog or brigantine exists, so these are never matched. Their comments ("days to build a sloop") also misread the per-500-block factor. |
| `build_time_days.brigantine` | `4.0` | 0.0 – 100.0 | In-game days a shipwright needs to build a brigantine (0 = ready at once) | ShipConfig |  | **DEAD**: Remove the three keys (`default` 1.0 per 500 blocks then covers both orderable templates); or rename them to the real template paths `starter_sloop` / `starter_sloop_basic` if per-template times are wanted. ShipConfig.buildDays looks the key up by template path (design.md l.171), and the templates are starter_sloop, starter_sloop_basic, navy_sloop_armed, pirate_sloop_armed: no sloop, merchant_cog or brigantine exists, so these are never matched. Their comments ("days to build a sloop") also misread the per-500-block factor. |
| `order_price_doubloons.sloop` | `200` | 0 – 1000000 | Doubloons a shipwright charges for a sloop, on top of the materials | — |  | **DEAD**: Remove the group; the price is the template's `price` times `ships.order_price_factor`. ShipConfig's own class comment says "declared but unread"; design.md l.171 (SW1) prices orders from the template. The 200/350/500 in progress.md never took effect. |
| `order_price_doubloons.merchant_cog` | `350` | 0 – 1000000 | Doubloons a shipwright charges for a merchant cog, on top of the materials | — |  | **DEAD**: Remove the group; the price is the template's `price` times `ships.order_price_factor`. ShipConfig's own class comment says "declared but unread"; design.md l.171 (SW1) prices orders from the template. The 200/350/500 in progress.md never took effect. |
| `order_price_doubloons.brigantine` | `500` | 0 – 1000000 | Doubloons a shipwright charges for a brigantine, on top of the materials | — (only SettingsConfigTest) |  | **DEAD**: Remove the group; the price is the template's `price` times `ships.order_price_factor`. ShipConfig's own class comment says "declared but unread"; design.md l.171 (SW1) prices orders from the template. The 200/350/500 in progress.md never took effect. |

### `ship_identity` (server): Ship identity: the ship's name on name boards

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `nameplate_shows_name` | `true` |  | A name board on an assembled, named ship shows the ship's name across its row | NameplateBlockEntity | false | OK |
| `nameplate_refresh_ticks` | `20` | 1 – 1200 | How often a name board checks the name of the ship it is on and its place in its row, in ticks (20 ticks = 1 second) | NameplateBlockEntity |  | OK |
| `name_board_distance` | `64` | 16 – 256 | How far away, in blocks, the name on a name board is still drawn | NameplateRenderer |  | OK |

### `ship_screen` (server): The ship screen: sneak-use the helm of your assembled ship to manage the ship and its crew

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Sneak-using the helm of an assembled ship opens the ship screen. Off = sneak-use disassembles the ship at once, as before | ShipScreens | false | OK |
| `reach` | `8.0` | 2.0 – 32.0 | Blocks from the helm within which the screen stays open and its buttons work | ShipScreens |  | OK |
| `refresh_ticks` | `20` | 5 – 200 | Ticks between two refreshes of an open screen (sent only when something it shows changed) | ShipScreens |  | OK |

### `ship_decor` (server): Ship decor: lanterns, ship's bell, rope coils, stern windows, chart table, sea cot, railings

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `sea_cot_sleeping` | `true` |  | Players can sleep in a sea cot and set their spawn there like in a bed (on an assembled ship only with sea_cot_sleeping_aboard as well) | SeaCotBlock |  | OK |
| `sea_cot_sleeping_aboard` | `true` |  | Players can sleep in a sea cot on an assembled ship: they lie in it as the ship sails, count for skipping the night and respawn on the ship. Needs sea_cot_sleeping | SeaCotBlock | false | OK |
| `bell_ring_ticks` | `20` | 2 – 200 | How long the ship's bell swings after it is rung, in ticks (20 ticks = 1 second) | ShipsBellBlock, ShipsBellRenderer |  | OK |
| `railings_enabled` | `true` |  | Railings, railing ports and railing gates can be placed. Off = the items place nothing; railings already placed stay | BulwarkPortBlock, RailingBlock, RailingGateBlock, RailingPortBlock | false | OK |
| `railing_collision_blocks` | `1.5` | 1.0 – 2.0 | How high a railing's collision reaches, in blocks: 1.5 like a fence keeps players from jumping over it, 1.0 lets them vault it | RailingBlock | 1.0 | OK |

### `rigging` (server): Rigging blocks on masts: ratlines and ratline nets

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `ratlines_enabled` | `true` |  | Ratlines can be placed. Off = the item places nothing; ratlines already placed stay and can be climbed | RatlinesItem | false | OK |
| `nets_enabled` | `true` |  | Ratline nets can be strung between a chainplate and a mast hoop. Off = the net item strings nothing; nets already strung stay and can be climbed | RatlineNetItem | false | OK |
| `net_max_length` | `32.0` | 1.0 – 64.0 | Longest ratline net, ring to ring [blocks] | RatlineNetItem | 12.0 | **DESIGN**: Old config files keep `12.0` (seen in the human's dev config: current server file and server-1.toml.bak); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: the human found 12 too short and it became 32 (design.md l.366); the current dev file still has 12. |
| `net_min_pitch` | `30.0` | 10.0 – 90.0 | Flattest ratline net, its pitch above the horizontal [degrees]; flatter nets are refused (90 = vertical nets only) | RatlineNetItem |  | OK |
| `net_assist` | `true` |  | A player climbing a ratline net is carried along the net's line, so steep and diagonal nets climb like a ladder. Off = vanilla ladder climbing only (fine for vertical nets, jerky or stuck on diagonal ones) | RatlineNetAssist | false | OK |
| `net_max_strands` | `5` | 1 – 8 | Most ratline nets one mast hoop holds, each from its own chainplate; together they form one fan (a chainplate always holds one) | RatlineNetItem, RatlineNets | 4 | OK |
| `net_max_spread` | `6.0` | 1.0 – 16.0 | Widest gap between the chainplates of two neighbouring nets on one hoop that is still netted in between [blocks]; wider ones stay separate nets | RatlineNets |  | OK |
| `boarding_nets` | `true` |  | A ratline net strung between two chainplates on top of railings along one rail edge hangs as a boarding net a block above the rail; a boarder crossing it from outboard is held, then climbs over, while the ship's own crew pass freely. Off = such nets are refused as too flat, and boarding nets already strung hold nobody | BoardingNets, RatlineNets | false | OK |
| `boarding_net_length` | `8` | 2 – 32 | Longest boarding net, chainplate to chainplate along the rail [blocks] | RatlineNetItem, RatlineNets |  | OK |
| `boarding_net_hold` | `20` | 0 – 200 | Ticks a boarding net holds a boarder who crosses the rail from outboard (slowed like a cobweb at half strength), before it climbs over (0 = nobody is held) | BoardingNets |  | OK |
| `boarding_net_cut_hits` | `3` | 1 – 20 | Cutlass hits that cut a boarding net down (it drops as one net item at its first chainplate); other tools and bare hands cannot break it | BoardingNets |  | OK |

### `wind` (server): Global wind field: direction, strength, weather and gusts

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `variability` | `1.0` | 0.0 – 10.0 | How fast wind direction and strength drift. 1 = direction changes over about one in-game day, 0 = frozen | SailingConfig |  | OK |
| `min_strength` | `3.0` | 0.0 – 50.0 | Lowest clear-weather wind speed in blocks per second | SailingConfig |  | OK |
| `max_strength` | `12.0` | 0.0 – 50.0 | Highest clear-weather wind speed in blocks per second (raised to min_strength if lower) | SailingConfig |  | OK |
| `weather_affects_wind` | `true` |  | Rain and thunderstorms strengthen the wind and storms bring gusts | SailingConfig |  | OK |
| `rain_multiplier` | `1.5` | 0.0 – 10.0 | Wind strength multiplier in rain | SailingConfig |  | OK |
| `thunder_multiplier` | `2.2` | 0.0 – 10.0 | Wind strength multiplier in thunderstorms | SailingConfig |  | OK |
| `gusts_enabled` | `true` |  | Thunderstorms produce short gusts of extra wind | SailingConfig |  | OK |
| `gusts_per_minute` | `3.0` | 0.1 – 30.0 | Average number of gusts per minute during a full thunderstorm | SailingConfig |  | OK |
| `gust_strength` | `0.4` | 0.0 – 5.0 | Extra wind strength at the peak of the strongest gust, as a fraction (0.4 = +40%) | SailingConfig |  | OK |
| `gust_direction_shift` | `20.0` | 0.0 – 90.0 | Largest change of wind direction during a gust, in degrees | SailingConfig |  | OK |
| `regional_variation` | `false` |  | Wind direction and strength differ by region instead of being the same everywhere in a dimension | SailingConfig |  | OK |
| `regional_scale` | `2048.0` | 64.0 – 100000.0 | Distance in blocks over which the regional wind pattern changes | SailingConfig |  | OK |
| `regional_direction_variation` | `45.0` | 0.0 – 180.0 | Largest regional deviation of the wind direction, in degrees | SailingConfig |  | OK |
| `regional_strength_variation` | `0.3` | 0.0 – 1.0 | Largest regional deviation of the wind strength, as a fraction (0.3 = plus or minus 30%) | SailingConfig |  | OK |
| `sync_interval_ticks` | `20` | 1 – 1200 | How often the wind is sent to each player, in ticks | WindSync |  | OK |

### `sailing` (server): Sail, rudder, keel and hull damping forces (the anchor's chain: anchor_chain)

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `sail_force_scale` | `1.0` | 0.0 – 100.0 | Sail force per block of sail area and per block/s of apparent wind | SailingConfig |  | OK |
| `half_trim_factor` | `0.5` | 0.0 – 1.0 | Fraction of the full sail force a half-set sail produces | SailingConfig | halfFactor | OK |
| `rudder_strength` | `0.5` | 0.0 – 20.0 | Rudder side force per unit of ship mass, forward speed and rudder deflection | SailingConfig | 20.0, 5.0 | OK |
| `rudder_force_factor` | `3.0` | 0.0 – 20.0 | Turning authority: multiplier on the rudder's side force (higher = tighter turning circle). 1 = the weaker rudder before SH2 | SailingConfig |  | OK |
| `max_rudder_angle` | `35.0` | 0.0 – 90.0 | Largest rudder deflection in degrees | HelmCourses, HelmService, SailingConfig, ShipControls |  | OK |
| `keel_enabled` | `true` |  | Hulls resist sideways motion in water (needed to sail across or against the wind) | SailingConfig | false | OK |
| `keel_longitudinal_drag` | `0.1` | 0.0 – 10.0 | Extra water drag along the hull, per second (fraction of speed lost per second) | SailingConfig |  | OK |
| `keel_lateral_drag` | `8.0` | 0.0 – 50.0 | Extra water drag across the hull, per second | SailingConfig | 8.0 | OK |
| `keel_yaw_drag` | `1.0` | 0.0 – 10.0 | Multiplier on the turning resistance the keel causes | SailingConfig |  | OK |
| `hull_damping_enabled` | `true` |  | The water damps a floating ship's rolling and pitching, so it settles after a wave or a gust. Off: it rocks on without end | SailingConfig | false, true | OK |
| `roll_damping` | `1.5` | 0.0 – 10.0 | How fast the side-to-side rolling dies down, per second (higher = settles sooner, too high looks glued to the water) | SailingConfig | HullDampingModel.Params.DEFAULTS.roll() | OK |
| `pitch_damping` | `1.5` | 0.0 – 10.0 | How fast the bow-up, bow-down pitching dies down, per second | SailingConfig | HullDampingModel.Params.DEFAULTS.pitch() | OK |
| `sails.yard_min_gap` | `2` | 1 – 32 | Smallest height difference, in blocks, between the upper and the lower yard of a square sail | SailingConfig |  | OK |
| `sails.yard_max_gap` | `8` | 1 – 32 | Largest height difference, in blocks, between the upper and the lower yard of a square sail (raised to yard_min_gap if lower) | SailingConfig |  | OK |
| `sails.yard_max_length` | `15` | 1 – 31 | Longest yard in blocks; a longer row of yard blocks carries no sail. Odd lengths center the yard on the mast | SailingConfig |  | OK |
| `sails.yard_refresh_ticks` | `20` | 1 – 1200 | How often, in ticks, each yard and each cleat with a stay re-checks the sail it heads for the cloth display (placing or breaking one updates at once) | CleatBlockEntity, YardBlockEntity | 1200, 5 | OK |
| `sails.stay_max_length` | `16` | 2 – 64 | Longest rope stay of a triangular sail: largest distance in blocks between its two cleats | SailingConfig | 16 | OK |
| `sails.stay_min_drop` | `2` | 1 – 32 | Smallest height difference in blocks between the two cleats of a rope stay | SailingConfig | 2 | OK |
| `sails.rope_lines` | `true` |  | A rope between two cleats, a cleat and a mooring ring, or two rings on one ship (or both on land) that makes no sail stays as a decorative rope line, up to stay_max_length long. Off = the rope only rigs stays, and existing lines are not drawn | RopeItem, RopeLineRenderer, StayClothRenderer | false | OK |
| `sails.rope_sag` | `0.08` | 0.0 – 0.5 | How far a rope line hangs down in the middle, as a fraction of its horizontal span (0 = taut) | RopeLineRenderer |  | OK |
| `sails.dyeing` | `true` |  | Using a dye on the upper yard of a square sail or the head cleat of a triangular sail dyes its cloth (one dye per use). Off: dyes do nothing on sails and dyed sails show the natural canvas | CleatBlock, CleatBlockEntity, YardBlock, YardBlockEntity | false | OK |
| `sails.banners` | `true` |  | Using a banner on the upper yard of a large square sail hangs its colour and patterns on the cloth; sneak-use with an empty hand takes it back. Off: no new banners, and hung ones are not shown (they can still be taken back) | CleatBlock, YardBlock, YardBlockEntity | false | OK |
| `sails.banner_min_width` | `3` | 1 – 31 | Narrowest square sail, in blocks (the shorter of its two yards), that can carry a banner | SailDecorations, YardBlockEntity |  | OK |
| `sails.banner_min_drop` | `2` | 1 – 32 | Smallest distance, in blocks, between the two yards of a square sail that can carry a banner | SailDecorations, YardBlockEntity |  | OK |

### `sailing_runtime` (server): How sails and the keel act on assembled ships

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `forces_enabled` | `true` |  | Sails and the keel push assembled ships. Off: ships only float and drift | SailingRuntimes |  | OK |
| `sails_need_water` | `true` |  | Sails only push a ship that is afloat. Off: wind also pushes ships on land or in the air | OarThrust, SailingRuntimes |  | OK |
| `full_draft` | `1.0` | 0.1 – 16.0 | Depth of the hull bottom below the sea, in blocks, at which the keel acts at full strength (less depth acts proportionally) | SailingRuntimes |  | OK |
| `sail_heel_factor` | `0.25` | 0.0 – 1.0 | Fraction of the heeling and pitching moment of sails and keel that is applied (1 = physical; hollow block hulls have no ballast and capsize easily) | SailingRuntimes | 0.25 | OK |
| `scan_interval_ticks` | `5` | 1 – 200 | How often loaded ships without a sailing state are looked for, in ticks | SailingRuntimes |  | OK |
| `sail_block_trim` | `true` |  | Using a yard that heads a square sail, or the head cleat of a triangular sail, cycles that sail's trim (furled, half, full) without the winch | CleatBlock, YardBlock |  | OK |
| `steering_enabled` | `true` |  | Using the helm of an assembled ship turns its rudder, and the rudder turns the ship. Off: the rudder has no effect | HelmCourses, HelmService, HelmStation, SailingRuntimes, ShipControls | true | OK |
| `rudder_steps` | `3` | 1 – 5 | Rudder steps on each side of midships; the last step is max_rudder_angle | SailingRuntimes, ShipControls |  | OK |
| `anchor_enabled` | `true` |  | The capstan drops and raises an anchor whose chain holds the ship. Off: capstans do nothing and dropped anchors stop holding | AnchorPhysics, SailingRuntimes, ShipControls | true | OK |
| `anchor_chain_length` | `32` | 1 – 256 | Length of the anchor chain in blocks: how far the falling anchor can run out from the hawse. No ground this far below the hawse: the anchor can't be dropped | AnchorPhysics, ShipControls | 3, 32 | OK |

### `stability` (server): How floating ships stay upright: the hull's righting moment, the limit on how far sails heel a ship (SH1) and the shipwright's ballast (TRM1)

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Floating ships right themselves like a real hull and sails can't heel them past max_heel_degrees. Off: only the blocks' own buoyancy keeps ships upright (tender, they heel far and may capsize) | SailingConfig | false | OK |
| `righting_factor` | `1.0` | 0.0 – 10.0 | Strength of the righting moment: multiplier on the estimated metacentric height beam^2 / (12 x draft). Higher = stiffer ships that heel less | SailingConfig |  | OK |
| `pitch_righting_factor` | `0.0` | 0.0 – 10.0 | Righting moment along the length (bow up or down) as a fraction of righting_factor, from length^2 / (12 x draft). 0 = only the blocks' own buoyancy sets the trim (the default; cargo and rigging trim a ship) | SailingConfig |  | OK |
| `max_righting_degrees` | `30.0` | 1.0 – 180.0 | The righting moment grows with the heel up to this angle in degrees and stays the same beyond | SailingConfig |  | OK |
| `max_metacentric_height` | `1.5` | 0.0 – 32.0 | Upper limit of the estimated metacentric height in blocks (keeps wide, shallow hulls and the bow-to-stern trim from getting rigid) | SailingConfig |  | OK |
| `righting_damping` | `0.4` | 0.0 – 5.0 | Damping ratio of the righting motion on top of sailing.roll_damping (1 = comes back upright without swinging through, 0 = swings) | SailingConfig | 0.0 | OK |
| `max_heel_torque_per_mass` | `3.0` | 0.0 – 100.0 | Cap of the heeling moment of sails and keel per unit of ship mass, after sailing_runtime.sail_heel_factor (strong winds heel no further) | SailingConfig |  | OK |
| `max_heel_degrees` | `25.0` | 1.0 – 90.0 | Heel in degrees at which the sails stop heeling a ship further (they spill their wind over the last 10 degrees) | SailingConfig |  | OK |
| `auto_ballast` | `true` |  | Ships float level as built: a virtual ballast, worked out from the blocks when the ship is assembled or loaded and again when blocks are added or removed, cancels the trim and heel of the build itself. Cargo, flood water, damage and crew still trim a ship. Off: a heavy stern castle sits the ship stern down | SailingConfig | false | OK |
| `auto_ballast_max_per_mass` | `3.0` | 0.0 – 64.0 | Most the ballast can make good, as the distance in blocks between the build's centre of mass and its centre of buoyancy (the torque is capped at this x mass x gravity), so an absurd build still lists | SailingConfig |  | OK |

### `helm` (server): The helm: steering by turning the wheel

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `wheel.drag_steering` | `true` |  | Steer by holding use on the helm and turning the wheel with the mouse or A/D. Off: the old click steps (right third of the wheel = one step to starboard, left third = port, middle = midships) | HelmService, HelmSteeringClient, SailingRuntimes | false, true | OK |
| `wheel.mouse_degrees_per_unit` | `1.0` | 0.0 – 20.0 | Wheel degrees per degree the mouse would have turned the view (the game's mouse sensitivity applies) | HelmSteeringClient |  | OK |
| `wheel.key_degrees_per_tick` | `6.0` | 0.0 – 90.0 | Wheel degrees per tick while the strafe left or right key is held | HelmSteeringClient |  | OK |
| `wheel.lock_view` | `true` |  | While steering, horizontal mouse movement turns only the wheel, not the view. Off: it turns both | HelmSteeringClient |  | OK |
| `wheel.turns_lock_to_lock` | `1.5` | 0.25 – 10.0 | Full turns of the wheel from hard to port to hard to starboard; half of it lies on each side of midships | HelmConfig |  | OK |
| `wheel.max_degrees_per_tick` | `30.0` | 1.0 – 360.0 | Largest change of the wheel per tick the server accepts from one helmsman | HelmService, HelmSteeringClient |  | OK |
| `wheel.session_reach` | `2.0` | 0.5 – 16.0 | The helmsman lets go of the wheel when farther than this many blocks from the helm block | HelmService |  | OK |

### `oars` (server): Rowing: the whistle's Row order sends the crew to the rail or to oar benches to row the ship

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | The whistle has a Row order, the crew row and the oars push the ship. Off: no rowing, and rowing crews ship their oars | OarBenchStation, OarThrust, Rowing | false, true | OK |
| `thrust_per_rower` | `40.0` | 0.0 – 1000.0 | Force one rower adds along the bow at rest (Sable's units, kpg x blocks/s²; a full sail gives about its area x the wind) | OarsConfig |  | OK |
| `bench_bonus` | `1.25` | 1.0 – 4.0 | A rower seated on an oar bench pulls this many times the thrust of one standing at the rail | OarsConfig |  | OK |
| `max_speed` | `3.0` | 0.1 – 20.0 | Rowing speed limit in blocks per second: the oars' force fades out as the ship's speed along the bow approaches it | OarsConfig | 0.15 | OK |
| `side_torque` | `0.5` | 0.0 – 8.0 | Lever, in blocks from the centre line, of each rower's pull: more rowers on one side turn the ship a little (0 = never) | OarsConfig |  | OK |
| `ship_oars_speed_factor` | `1.1` | 1.0 – 3.0 | The crew ship their oars by themselves when the sails drive the ship faster than max_speed times this | OarsConfig |  | OK |
| `ship_oars_ticks` | `40` | 1 – 1200 | Ticks the sails must keep the ship that fast before the crew ship their oars | OarsConfig | 20 | OK |

### `anchor_chain` (server): The anchor: its fall, its chain, its holding power, sounds and particles

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `sink_speed` | `4.0` | 0.5 – 32.0 | Speed at which the dropped anchor sinks through water, in blocks per second (in air it falls freely) | AnchorPhysics | 4.0 | OK |
| `water_drag` | `2.0` | 0.0 – 20.0 | How fast the water takes away the sideways speed the anchor kept from the moving ship, per second. Lower: it lands further ahead | AnchorPhysics | 2.0 | OK |
| `chain_stiffness` | `5.0` | 0.1 – 100.0 | Pull of a taut chain per block it is stretched beyond the paid-out length, per ton of the ship, in blocks per second squared | AnchorConfig | AnchorChain.Params.DEFAULTS.stiffness() | OK |
| `chain_damping` | `2.0` | 0.0 – 100.0 | Extra pull of a taut chain per block per second the ship moves away from the anchor, per ton of the ship | AnchorConfig | AnchorChain.Params.DEFAULTS.damping() | OK |
| `holding_force` | `10.0` | 0.0 – 50.0 | Holding power of the anchor per ton of the ship: the largest deceleration it gives, in blocks per second squared. A stronger pull drags the anchor over the seabed. 0 = the anchor never holds | AnchorConfig | AnchorChain.Params.DEFAULTS.holding()… | OK |
| `drag_scrape_rate` | `1.0` | 0.0 – 16.0 | Fastest the anchor scrapes over the seabed when the ship pulls harder than its holding power, in blocks per second | AnchorPhysics | 1.0 | OK |
| `settle_drag` | `0.2` | 0.0 – 5.0 | Mild drag of the chain lying on the seabed on a ship whose anchor rests, per second, so it settles. Counts toward the holding power | AnchorConfig | 0.2 | OK |
| `heel_factor` | `0.25` | 0.0 – 1.0 | Fraction of the heeling and pitching moment of the chain's pull that is applied (yaw is always full; see sailing_runtime.sail_heel_factor) | AnchorPhysics | 0.25 | OK |
| `at_rest_speed` | `0.3` | 0.0 – 4.0 | A ship whose anchor holds counts as anchored (for mooring, crew and the HUD) below this speed, in blocks per second | AnchorPhysics | 0.3 | OK |
| `raise_speed` | `2.5` | 0.1 – 64.0 | Speed at which the capstan winds the chain in, in blocks per second | AnchorPhysics, ShipControls | 2.5 | OK |
| `sounds` | `true` |  | Chain, splash, landing, jolt and capstan sounds and the splash particles of the anchor | AnchorEntities |  | OK |
| `chain_volume` | `0.8` | 0.0 – 4.0 | Volume of the running chain | AnchorEntities |  | OK |
| `splash_volume` | `1.0` | 0.0 – 4.0 | Volume of the splash when the anchor enters the water | AnchorEntities |  | OK |
| `thud_volume` | `1.0` | 0.0 – 4.0 | Volume of the anchor landing on the ground | AnchorEntities |  | OK |
| `jolt_volume` | `0.7` | 0.0 – 4.0 | Volume of the clank when the chain snaps taut | AnchorEntities |  | OK |
| `capstan_volume` | `0.6` | 0.0 – 4.0 | Volume of the capstan's clank while it winds the chain in | AnchorEntities |  | OK |

### `hull_creaking` (server): Creaking planks of rolling ships

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | A ship that rolls or pitches creaks now and then. Off: ships are silent | CreakConfig | true | OK |
| `creaks_per_second` | `0.2` | 0.0 – 20.0 | Average creaks per second of a ship that rolls just fast enough to creak; a ship rolling twice as fast creaks twice as often | CreakConfig | 10.0 | OK |
| `min_interval_ticks` | `40` | 1 – 1200 | Shortest time between two creaks of one ship, in ticks | CreakConfig | 5 | OK |
| `min_volume` | `0.25` | 0.0 – 4.0 | Volume of the quietest creak | CreakConfig |  | OK |
| `max_volume` | `0.6` | 0.0 – 4.0 | Volume of the loudest creak (raised to min_volume if lower) | CreakConfig |  | OK |
| `min_pitch` | `0.5` | 0.5 – 2.0 | Lowest pitch of a creak (1 = the sound as recorded) | CreakConfig |  | OK |
| `max_pitch` | `0.8` | 0.5 – 2.0 | Highest pitch of a creak (raised to min_pitch if lower) | CreakConfig |  | OK |
| `roll_rate_threshold` | `0.05` | 0.001 – 5.0 | How fast a ship must roll or pitch to creak, in radians per second (0.05 is about 3 degrees per second) | CreakConfig | 0.05 | OK |

### `waves` (server): Simulated sea state: wave roll and pitch on ships

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | The sea state follows the weather, rocks ships with roll and pitch and spills water into low open hulls. Off = always a flat sea | BoatWaves, SeaStates, WaveCommands, WaveForces |  | OK |
| `amplitude` | `1.0` | 0.0 – 5.0 | Multiplier on the wave height of every sea state (calm 0.15, moderate 0.6, rough 1.2, storm 2.0 blocks); 0 = flat sea | SeaStates | 0.1 / SeaState.CALM.amplitude() | OK |
| `ship_torque` | `18.0` | 0.0 – 200.0 | Roll and pitch torque per unit of wave slope and per kpg of ship mass, scaled by (blocks / 200)^-size_exponent | WaveForces | 5.5 | **DESIGN**: Old config files keep `5.5` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: WAV2/WAV4 retuned it to 18 for the bigger sea (design.md l.337). |
| `size_exponent` | `1.0` | 0.0 – 2.0 | How much steadier big ships lie: the wave torque per kpg falls as (blocks / 200) to the power of minus this | WaveForces | 0.5 | OK |
| `max_torque_per_mass` | `2.5` | 0.0 – 20.0 | Upper limit of the wave torque per kpg of ship mass, so that small boats are not flipped | WaveForces | 2.0 | **DESIGN**: Old config files keep `2.0` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: WAV2 set 2.5 (design.md l.332). |
| `state_change_seconds` | `60.0` | 1.0 – 1200.0 | Seconds the sea takes to go from calm to storm (or back) when the weather changes | SeaStates |  | OK |
| `spill` | `true` |  | Wave crests spill water over low rims and through low open hatches into compartments | WaveForces |  | OK |
| `sync_interval_ticks` | `60` | 10 – 1200 | Ticks between sea state updates sent to players (clients blend between them) | WaveSync |  | OK |
| `components` | `6` | 2 – 12 | Wave trains the sea is made of (16 to 64 blocks long, weighted around the sea state's peak); more gives a less regular rhythm | SeaStates |  | OK |
| `group_depth` | `0.35` | 0.0 – 0.9 | How much the wave height swells and fades in sets (wave groups): 0.35 = between 65 % and 135 % of the sea state's height | SeaStates |  | OK |
| `group_period_seconds` | `60.0` | 10.0 – 600.0 | Time scale of the wave groups in seconds: sets of bigger waves come and go over about this long | SeaStates |  | OK |
| `direction_step_degrees` | `10.0` | 1.0 – 90.0 | The waves turn to a new direction once the wind (with a slow offset) has moved this many degrees away from theirs | SeaStates |  | OK |
| `direction_fade_seconds` | `15.0` | 0.0 – 120.0 | Seconds over which the waves crossfade from the old direction to the new one (0 = switch at once) | SeaStates |  | OK |
| `heave` | `true` |  | Ships rise on wave crests and sink into troughs (a vertical force from the mean wave height under the hull) | WaveForces | false | OK |
| `heave_strength` | `0.95` | 0.0 – 5.0 | Heave force per block of mean wave height under the hull, as a share of the ship's weight | WaveForces |  | **DESIGN**: Old config files keep `0.5` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: WAV4 set 0.95 (design.md l.337). |
| `heave_per_mass` | `25.0` | 0.0 – 50.0 | Upper limit of the heave force per kpg of ship mass | WaveForces |  | **DESIGN**: Old config files keep `6.0` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: WAV4 set 25 (design.md l.337). |
| `boats` | `true` |  | Vanilla boats on the open sea rise and fall with the waves (not in lakes, rivers, shallow water or inside a hull) | BoatWaves | false | OK |
| `floating_items` | `true` |  | Items floating on the open sea bob with the waves | BoatWaves |  | OK |

### `hazards` (server): Waterspouts, whirlpools and the kraken

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `spawn_check_interval_ticks` | `200` | 20 – 24000 | Ticks between the spawn checks for waterspouts and whirlpools (each check rolls once per player at sea) | HazardSpawner, KrakenSpawner |  | **TEST**: Old config files keep `20` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; never a code default (200): a test setting left in the file, ten times the spawn checks. |
| `spawn_min_distance` | `48` | 8 – 256 | Nearest distance in blocks from the player at which a hazard forms | HazardSpawner, KrakenSpawner |  | OK |
| `spawn_max_distance` | `96` | 8 – 256 | Farthest distance in blocks from the player at which a hazard forms (the chunk must be loaded) | HazardSpawner, KrakenSpawner |  | OK |
| `ship_force_scale` | `1.0` | 0.0 – 20.0 | Multiplier on the pull, lift and spin of hazards on ships (1 = a light ship is accelerated at 100 m/s² per blocks/tick² of field, a quarter of what entities get; 0 = ships are not affected) | HazardForces |  | OK |
| `max_ship_mass_effect` | `400.0` | 1.0 – 1000000.0 | Ship mass (Sable mass units, about 1 per plank) up to which a ship feels the full hazard force; heavier ships feel it scaled by this mass divided by theirs, so big ships barely notice | HazardForces, Kraken | 20.0 | OK |
| `waterspouts.enabled` | `true` |  | Waterspouts form over the ocean during thunderstorms, lift entities and small ships and tear at sails. Off also removes existing waterspouts | HazardCommands, HazardSpawner, WaterspoutEntity | false | OK |
| `waterspouts.chance_per_check` | `0.02` | 0.0 – 1.0 | Chance per spawn check, for each player at sea while the world is thundering, that a waterspout forms nearby | HazardSpawner | 1.0 | **TEST**: Old config files keep `1.0` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; never a code default (0.02): a test setting, a waterspout on every check in a thunderstorm. |
| `waterspouts.max_per_player` | `1` | 0 – 16 | Most waterspouts alive near one player at once (no new ones form while that many are near) | HazardSpawner |  | OK |
| `waterspouts.duration_ticks` | `1200` | 20 – 72000 | How long a waterspout lasts in ticks | WaterspoutEntity |  | OK |
| `waterspouts.radius` | `8.0` | 1.0 – 48.0 | Radius in blocks within which a waterspout pulls and lifts | HazardSpawner, WaterspoutEntity |  | OK |
| `waterspouts.funnel_height` | `24.0` | 4.0 – 128.0 | Height of the funnel in blocks; the lift fades to nothing at the top | WaterspoutEntity |  | OK |
| `waterspouts.pull` | `0.08` | 0.0 – 1.0 | Pull toward the funnel's axis at the centre, blocks/tick² | WaterspoutEntity |  | OK |
| `waterspouts.lift` | `0.12` | 0.0 – 1.0 | Upward acceleration at the centre and the water line, blocks/tick² (gravity is about 0.08) | WaterspoutEntity |  | OK |
| `waterspouts.max_lift_speed` | `0.6` | 0.0 – 4.0 | The lift stops speeding entities up above this upward speed, blocks/tick | WaterspoutEntity |  | OK |
| `waterspouts.sail_damage_chance` | `0.15` | 0.0 – 1.0 | Chance per second for each set sail inside a waterspout that the wind tears it in one trim step (full to half, half to furled) | WaterspoutEntity | 1.0 | OK |
| `whirlpools.enabled` | `true` |  | Whirlpools appear in the deep ocean, pull ships toward their centre, turn them and can drag small boats under. Off also removes existing whirlpools | HazardCommands, HazardSpawner, WhirlpoolEntity |  | OK |
| `whirlpools.chance_per_check` | `0.002` | 0.0 – 1.0 | Chance per spawn check, for each player in the deep ocean, that a whirlpool appears nearby (0.002 at the default interval is about one whirlpool every four in-game days spent in the deep ocean) | HazardSpawner |  | OK |
| `whirlpools.max_per_player` | `1` | 0 – 16 | Most whirlpools alive near one player at once | HazardSpawner |  | OK |
| `whirlpools.duration_ticks` | `2400` | 20 – 72000 | How long a whirlpool lasts in ticks | WhirlpoolEntity |  | OK |
| `whirlpools.radius` | `12.0` | 1.0 – 64.0 | Radius in blocks within which a whirlpool pulls and spins | HazardSpawner, WhirlpoolEntity |  | OK |
| `whirlpools.pull` | `0.05` | 0.0 – 1.0 | Pull toward the centre at the centre, blocks/tick² | WhirlpoolEntity |  | OK |
| `whirlpools.spin` | `0.04` | 0.0 – 1.0 | Sideways (counter-clockwise seen from above) push at the centre, blocks/tick²; turns a ship lying at the centre | WhirlpoolEntity |  | OK |
| `whirlpools.drag_down` | `0.08` | 0.0 – 1.0 | Downward pull on boats and swimmers in the inner third, blocks/tick² (enough to drag a boat under) | WhirlpoolEntity |  | OK |
| `whirlpools.drift_speed` | `0.02` | 0.0 – 0.5 | How fast a whirlpool drifts on its random heading, blocks/tick (0 = stationary) | WhirlpoolEntity |  | OK |
| `kraken.enabled` | `true` |  | The kraken can appear in the deep ocean and attack ships | Kraken, KrakenSpawner, MobCommands | false | OK |
| `kraken.chance_per_day` | `0.05` | 0.0 – 1.0 | Base chance per in-game day, for each player in the deep ocean, that the kraken appears (×mobs.kraken.night_multiplier at night, ×thunder_multiplier in storms; 0.05 is about one kraken per ten days at sea) | KrakenSpawner | 1.0 | **DESIGN**: Old config files keep `0.02` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: raised to 0.05 (commit b14815db). |

### `crew_stations` (server): Ship stations operated by crew members (spike 4)

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Crew members can be assigned to stations and carry out orders there. Off: assigned crew are released | CrewStations, HelmCourses, JobBoard, ShipScreenViews, ShipScreens, Stations, WhistleOrders | true | OK |
| `ticks_per_trim_step` | `40` | 0 – 1200 | Ticks a crew member at the sail winch needs per trim step (furled to half, half to full) | WinchStation | 10, 20, 200, 30, STEP | OK |
| `ack_radius` | `24` | 0 – 256 | Players within this many blocks of a crew member see its acknowledgement of an order (0 = nobody) | CrewStations |  | **NAME**: Fix the comment: "Players within this many blocks of a crew member see its refusals and reports on the action bar (0 = nobody)". MSG1 removed the order acknowledgements (design.md l.364); CrewStations.say now only carries refusals and reports. |
| `order_radius` | `64` | 1 – 512 | Range of /pirates crew order without targets: assigned crew members within this many blocks | StationCommands |  | OK |
| `seat_check_interval` | `10` | 1 – 200 | Ticks between checks of an assigned crew member that is not at its station (take the seat, or release it) | CrewMember, CrewRest | 5 | OK |
| `turn_degrees_per_tick` | `15.0` | 1.0 – 180.0 | Degrees per tick a crew member turns to face its station (or meal) after it took its place, then it stays facing it (180 = at once) | CrewMember | 180.0 | OK |
| `job_board.enabled` | `true` |  | Ship-wide orders post open jobs for unmanned stations that free crew take by themselves. Off = only manned stations carry orders out | JobBoard | false | OK |
| `job_board.claim_interval_ticks` | `20` | 1 – 200 | Ticks between two passes of the job board, in which free crew claim the open jobs | JobBoard |  | OK |
| `job_board.max_claim_distance` | `0.0` | 0.0 – 128.0 | Crew members farther than this many blocks from an open job never claim it (0 = the whole ship) | JobBoard |  | OK |
| `course.enabled` | `true` |  | Crew members at the helm hold courses. Off: courses end, the rudder is put midships and the helm takes no course orders | HelmCourses, HelmStation | false | OK |
| `course.gain` | `2.0` | 0.0 – 20.0 | Rudder degrees the helmsman gives per degree of heading error (higher = harder, quicker steering) | CourseConfig |  | OK |
| `course.deadband_degrees` | `3.0` | 0.0 – 45.0 | Heading errors up to this many degrees leave the rudder midships | CourseConfig |  | OK |
| `course.anticipation_seconds` | `1.5` | 0.0 – 20.0 | How many seconds ahead the helmsman judges the bow's swing, easing the rudder off before the bow points at the target (0 = no anticipation) | CourseConfig |  | OK |
| `course.arrival_radius` | `8.0` | 1.0 – 128.0 | A waypoint counts as reached within this many blocks (horizontal distance from the ship's center) | HelmCourses |  | OK |
| `course.stuck_ticks` | `200` | 20 – 24000 | A ship with sails set that moves slower than stuck_speed for this many ticks is reported stuck | HelmCourses |  | OK |
| `course.stuck_speed` | `0.3` | 0.0 – 10.0 | Speed in blocks per second under which a ship with sails set counts as not moving | HelmCourses |  | OK |
| `course.update_interval_ticks` | `5` | 1 – 100 | Ticks between two rudder corrections of the helmsman | HelmCourses |  | OK |
| `course.manual_override_ticks` | `100` | 0 – 2400 | After a player moved the wheel (by a click step, or holding the wheel), the helmsman leaves the rudder alone for this many ticks | HelmCourses | 60 | OK |
| `capstan.enabled` | `true` |  | Crew members at a capstan carry out anchor orders. Off: the capstan takes no crew orders (players still use it) | CapstanStation | false | OK |
| `capstan.drop_ticks` | `40` | 1 – 1200 | Ticks a crew member at the capstan needs to let the anchor go (knocking the pawls off) before it falls | CapstanStation |  | OK |
| `capstan.min_raise_ticks` | `20` | 1 – 1200 | Shortest work time of a raise order in ticks; otherwise it lasts as long as the capstan needs to wind the chain in (anchor_chain.raise_speed), and the crew member carries on while the anchor is still coming up | CapstanStation |  | OK |
| `capstan.animation` | `true` |  | The capstan's bars turn while a crew member works it, and the crew member walks round the drum pushing them (off = the bars stand still and the crew member pushes on the spot) | CapstanBlockEntity | false | OK |
| `capstan.turn_degrees_per_tick` | `6.0` | 0.5 – 45.0 | How fast the capstan's bars, and the crew member walking them, go round the drum while it works, in degrees per tick | CapstanBlockEntity |  | OK |
| `capstan.walk_radius` | `1.1` | 0.6 – 2.0 | Distance from the capstan's axis, in blocks, at which the crew member walks round it | CapstanBlockEntity |  | OK |

### `lookout` (server): The crow's nest: a crew member or player in it keeps watch and calls out ships, land and sea monsters

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | A crow's nest with a lookout (a crew member at the station or a player standing in it) scans the sea. Off: no scans, no calls | LookoutStation, Lookouts | false | OK |
| `announce` | `true` |  | The lookout's calls reach the players aboard and in the nest as a short action-bar line, a hail from the nest and a mark on the ship HUD's compass. Off: the lookout keeps watch silently | Lookouts | false | OK |
| `range` | `160` | 8 – 1024 | Blocks (horizontal) at which the lookout in a crow's nest sees ships, land and sea monsters | Lookouts | 8 | OK |
| `scan_interval_ticks` | `60` | 1 – 12000 | Ticks between two scans of every manned crow's nest | Lookouts |  | OK |
| `memory_ticks` | `6000` | 0 – 1728000 | Ticks the lookout remembers a sighting after it was last seen; within them the same ship, monster or coast is not called again | Lookouts | 20 | OK |
| `recall_blocks` | `64` | 0 – 1024 | A sighting still in view is called again once it came this many blocks closer than at its last call. 0: never for coming closer | Lookouts | 6 | OK |
| `recall_seconds` | `120` | 0 – 86400 | A sighting still in view is called again this many seconds after its last call. 0: never for the time | Lookouts | 10 | OK |
| `mark_seconds` | `30` | 1 – 600 | Seconds a sighting's mark stays on the ship HUD's compass rose (it fades out over the last third) | Lookouts |  | OK |
| `land_directions` | `16` | 4 – 64 | Directions (rays round the compass) the lookout samples for land | Lookouts |  | OK |
| `land_step` | `16` | 4 – 128 | Blocks between two land samples along a ray | Lookouts |  | OK |
| `land_min_distance` | `24` | 0 – 512 | Land closer than this many blocks is not called (the quay the ship lies at) | Lookouts |  | OK |
| `land_region` | `128` | 16 – 2048 | Size in blocks of the squares a coast is remembered by: land in a square called already is not called again | Lookouts |  | OK |

### `crew` (server): Crew wages, morale and crew size

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `max_crew_multiplier` | `1.0` | 0.1 – 10.0 | Multiplier on the crew limit a ship gets from its bunks and hammocks | ShipBunks, ShipScreenViews |  | OK |
| `morale.enabled` | `true` |  | Crew members have morale, turn in to the ship's hammocks at night and gain or lose morale at dawn. Off = morale stays at 'start' and nobody is sent to a hammock | CrewMorale | false | OK |
| `morale.start` | `70` | 0 – 100 | Morale of a new crew member | CrewMorale |  | OK |
| `morale.hammock_rest_per_night` | `5` | 0 – 100 | Morale a crew member gains at dawn after a night in a hammock (capped at 100) | CrewMorale | 0 | OK |
| `morale.no_hammock_per_night` | `10` | 0 – 100 | Morale a crew member loses at dawn after a night on a ship without a free hammock for it (floored at 0) | CrewMorale | 0 | OK |
| `morale.press_gang_start` | `30` | 0 – 100 | Morale of a sailor press-ganged into the crew (LA2) | PrisonerOutcomes |  | OK |
| `wages.enabled` | `true` |  | Crew members are paid wages in doubloons from the coins in the ship's crates, barrels and chests at dawn and lose morale when unpaid. Off = crew work for free | HiringBackend, Upkeep | false | OK |
| `wages.per_day` | `2` | 0 – 1000 | Doubloons one crew member costs per day | HiringBackend, Upkeep |  | OK |
| `wages.unpaid_per_day` | `8` | 0 – 100 | Morale an unpaid crew member loses at dawn | Upkeep |  | OK |
| `wages.paid_per_day` | `1` | 0 – 100 | Morale a paid crew member gains at dawn | Upkeep |  | OK |
| `wages.from_wallet` | `true` |  | When the coins aboard do not cover the wages, the rest comes from the doubloons the ship's owner carries, while the owner is online in the ship's dimension (CRW2). Chests and crates aboard always pay first. Off = only coins aboard pay | ShipDayTick | false | OK |
| `desertion.enabled` | `true` |  | A crew member whose morale stays low for several dawns deserts and becomes a neutral sailor. Off = crew never desert | Desertions, Upkeep | false | OK |
| `desertion.desert_below` | `20` | 0 – 101 | Morale below which a dawn counts toward desertion | BrigService, Upkeep |  | OK |
| `desertion.desert_days` | `2` | 1 – 100 | Consecutive dawns with low morale after which a crew member deserts | Upkeep |  | OK |
| `desertion.at_port_only` | `true` |  | A deserter does not leave at once: it stays aboard, marked as deserting, and walks off onto the quay when the ship is at a port (CRW2), or anywhere after desert_anywhere_after_days. Off = it leaves the ship at the dawn it deserts (the old behaviour) | Desertions | false | OK |
| `desertion.desert_port_radius` | `48` | 0 – 512 | Blocks around a port's area within which a ship counts as at that port for deserters | Desertions | 0, 16 | OK |
| `desertion.desert_anywhere_after_days` | `3` | 0 – 100 | Dawns a deserter waits for a port; after that it leaves wherever the ship is (0 = at once) | Desertions |  | OK |
| `mutiny.enabled` | `false` |  | Crew with very low average morale may mutiny: they turn into hostile pirates and the ship loses its owner. While the average is that low nobody deserts (they plot instead). Off = they only desert | Upkeep | true | OK |
| `mutiny.mutiny_below` | `15` | 0 – 101 | Average crew morale below which a dawn counts toward mutiny | Upkeep |  | OK |
| `mutiny.mutiny_days` | `3` | 1 – 100 | Consecutive dawns with low average morale after which the crew mutinies | Upkeep |  | OK |
| `meals.enabled` | `true` |  | At every meal time the free crew (not at a station, not asleep) sit down beside the nearest pantry or water barrel aboard. Off = no meals | MealVisits | false, true | OK |
| `meals.meal_times` | `[6000, 12000]` |  | Times of day (0 to 23999 ticks; 6000 = noon, 12000 = sunset) at which a meal starts. The crew turns in at nightfall (12542), so a later meal reaches only crew without a hammock. Entries that are not whole numbers are ignored | MealConfig | List.of("6000") | OK |
| `meals.meal_ticks` | `100` | 1 – 6000 | Ticks a meal lasts before the crew gets up again | MealVisits | 400, MEAL_TICKS | OK |
| `hammock.player_sleep` | `true` |  | Players can sleep in a free hammock at night, on land and on an assembled ship; it counts for skipping the night and sets the respawn point. Off = hammocks are for the crew only | HammockBlock | false | OK |
| `walk.enabled` | `true` |  | Crew members walk to their station, meal spot and hammock over the deck. Off = they are seated there at once (the old behaviour) | CrewWalk | false | OK |
| `walk.speed` | `1.0` | 0.1 – 3.0 | Walking speed as a multiple of the crew member's movement speed | CrewWalk, RailLean, Rowing |  | OK |
| `walk.arrive_distance` | `1.5` | 0.5 – 4.0 | Distance in blocks (measured on the ship) from the spot at which a walking crew member counts as arrived and takes its place | CrewWalk |  | OK |
| `walk.timeout_ticks` | `200` | 20 – 2400 | Ticks a crew member walks before it is seated at its spot anyway (an unreachable spot never stalls it) | CrewWalk, RailLean | timeout | OK |
| `walk.repath_ticks` | `20` | 5 – 200 | Ticks between two path searches while the ship moves | CrewWalk |  | OK |
| `rail_lean.enabled` | `true` |  | An idle crew member (no order, no station, not eating or sleeping) walks to a free spot at the ship's railing, faces the sea and leans on the rail for a while. Off = idle crew only stroll about the deck | RailLean, RailLeanGoal | false | OK |
| `rail_lean.radius` | `6` | 1 – 16 | Blocks around an idle crew member (measured on the ship) within which it looks for a spot at the rail | RailLean |  | OK |
| `rail_lean.min_seconds` | `30` | 1 – 600 | Shortest lean on the rail [seconds]; each lean lasts a random time between this and max_seconds | RailLean |  | OK |
| `rail_lean.max_seconds` | `90` | 1 – 600 | Longest lean on the rail [seconds] | RailLean |  | OK |
| `rail_lean.leaners` | `3` | 0 – 32 | Most crew members of one ship leaning on its rail at the same time (0 = nobody leans) | RailLean |  | OK |
| `rail_lean.pause_seconds` | `30` | 0 – 600 | Seconds a crew member strolls about after a lean (or a spot it could not reach) before it looks for the rail again | RailLean |  | OK |
| `hiring.enabled` | `true` |  | Harbor desks have a Crew tab where crew can be hired, and sneak-using the whistle on a crew member dismisses it. Off = no tab, no hiring, no dismissal | HiringConfig | false | OK |
| `hiring.candidates_per_port` | `3` | 0 – 12 | Candidates a port offers each day (hired ones are gone until the next day) | Hiring |  | OK |
| `hiring.fee_sailor` | `10` | 0 – 100000 | Doubloons a sailor asks to sign on at a seafarer village | HiringConfig |  | OK |
| `hiring.fee_pirate` | `20` | 0 – 100000 | Doubloons a pirate asks to sign on at a pirate island | HiringConfig |  | OK |
| `hiring.fee_navy` | `15` | 0 – 100000 | Doubloons a navy rating asks to sign on at a navy outpost | HiringConfig |  | OK |
| `hiring.pirate_min_infamy` | `BUCCANEER` |  | Infamy rank from which pirates sign on with a captain they are not friendly with (careers on) | HiringConfig |  | OK |
| `hiring.navy_requires_enlistment` | `true` |  | Navy ratings sign on only with an enlisted captain (Midshipman or higher; ignored while careers are off) | HiringConfig |  | OK |
| `hiring.ship_radius` | `32` | 0 – 512 | Blocks around a port's area in which the player's ship counts as moored there | Hiring, HiringBackend | 0 | OK |
| `hiring.require_bunks` | `true` |  | A ship takes on crew only while it has a free bunk (hammocks x crew.max_crew_multiplier). Off = at least max_without_bunks crew | Hiring, HiringBackend |  | OK |
| `hiring.max_without_bunks` | `2` | 0 – 100 | Crew a ship may take on without hammocks while require_bunks is off | Hiring, HiringBackend |  | OK |

### `provisions` (server): Crew food, fresh water, rum, scurvy and spoilage

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `consumption_enabled` | `true` |  | The crew consumes provisions. Off: pantries never shrink and there are no hunger, thirst, rum or scurvy effects | ProvisionsCommands, ProvisionsConfig | false | OK |
| `consumption_rate` | `1.0` | 0.0 – 10.0 | Multiplier on all provision consumption | ProvisionsConfig |  | OK |
| `food_per_crew_per_day` | `6.0` | 0.0 – 40.0 | Nutrition (food hunger points) one crew member eats per in-game day | ProvisionsConfig |  | OK |
| `water_per_crew_per_day` | `1.0` | 0.0 – 20.0 | Water rations one crew member drinks per in-game day (a water bottle is one ration) | ProvisionsConfig |  | OK |
| `rum_ration_per_crew_per_day` | `0.25` | 0.0 – 10.0 | Rum items in one crew member's standard daily rum ration | ProvisionsConfig |  | OK |
| `prisoner_share` | `0.5` | 0.0 – 1.0 | Share of a crew member's food and water that a prisoner consumes | ProvisionsConfig |  | OK |
| `hunger_morale_per_day` | `10.0` | 0.0 – 100.0 | Morale lost per in-game day without food | ProvisionsConfig |  | OK |
| `thirst_morale_per_day` | `25.0` | 0.0 – 100.0 | Morale lost per in-game day without water | ProvisionsConfig |  | OK |
| `rum_morale_per_day` | `4.0` | 0.0 – 100.0 | Morale gained per in-game day while the rum ration is issued | ProvisionsConfig |  | OK |
| `scurvy_morale_per_day` | `5.0` | 0.0 – 100.0 | Morale lost per in-game day with scurvy | ProvisionsConfig |  | OK |
| `hungry_work_speed` | `0.75` | 0.0 – 1.0 | Work speed multiplier of a hungry crew | ProvisionsConfig |  | OK |
| `thirsty_work_speed` | `0.5` | 0.0 – 1.0 | Work speed multiplier of a thirsty crew | ProvisionsConfig |  | OK |
| `drunk_work_speed` | `0.6` | 0.0 – 1.0 | Work speed multiplier while the crew is drunk from more than the rum ration | ProvisionsConfig |  | OK |
| `drunk_duration_ticks` | `6000` | 0 – 240000 | How long the drunk penalty lasts after the last over-ration, in ticks | ProvisionsConfig |  | OK |
| `rum_binge_days` | `10.0` | 0.0 – 1000.0 | A crew with rum for at least this many days of its standard ration in the pantries drinks too much and works at drunk_work_speed (0: never) | ShipDayTick |  | OK |
| `rum_binge_rations` | `2.0` | 1.0 – 10.0 | Rum rations a crew drinks per day while it has rum_binge_days of rum (1: no more than the ration, never drunk) | ShipDayTick |  | OK |
| `hunger_desertion_days` | `3.0` | 0.0 – 100.0 | In-game days in a row without food until the whole crew deserts at the next dawn, whatever its morale (with crew.desertion.enabled) | ProvisionsConfig | 2.0 | OK |
| `thirst_desertion_days` | `1.0` | 0.0 – 100.0 | In-game days in a row without water until the whole crew deserts at the next dawn, whatever its morale (with crew.desertion.enabled) | ProvisionsConfig, ShipDayTick | 100.0 | OK |
| `scurvy_enabled` | `true` |  | Crew get scurvy (weakness, slower healing) after a long time without citrus or fresh food | ProvisionsConfig |  | **NAME**: Drop "slower healing" from the comment (or implement it). Scurvy gives Weakness only; "slower healing is not implemented" (design.md l.415). |
| `scurvy_onset_days` | `8.0` | 0.0 – 1000.0 | In-game days without anti-scurvy food until scurvy sets in | ProvisionsConfig | 0.5 | OK |
| `fresh_food_prevents_scurvy` | `true` |  | Any fresh (not preserved) food prevents scurvy, not only citrus | ProvisionsConfig |  | OK |
| `scurvy_affects_players` | `false` |  | Players on a ship whose crew has scurvy get scurvy too | ProvisionsConfig, ShipDayTick |  | OK |
| `spoilage_enabled` | `true` |  | Fresh food in the pantry spoils after its shelf life. Preserved food never spoils | ProvisionsConfig |  | OK |
| `fresh_shelf_life_days` | `5.0` | 0.1 – 1000.0 | In-game days fresh food keeps in the pantry | ProvisionsConfig |  | OK |
| `spoiled_food_result` | `ROTTEN_FLESH` |  | What spoiled food in a pantry turns into: NOTHING (it vanishes) or ROTTEN_FLESH (one per spoiled item) | PantryBlockEntity | com.richardsenger.piratesnships.crew.… | OK |
| `pantry_tick_interval` | `1200` | 20 – 24000 | Ticks between two spoilage checks of a pantry. The result does not depend on it; pantries also catch up when opened or read | PantryBlockEntity |  | OK |
| `rain_refill_enabled` | `true` |  | Water barrels open to the sky collect rain (rain catchers) | WaterBarrelBlock |  | OK |
| `rain_refill_chance` | `0.5` | 0.0 – 1.0 | Chance per random tick that a water barrel in the rain gains one water ration (random ticks come about every 68 s at the default tick speed) | WaterBarrelBlock |  | OK |
| `food_weight_per_unit` | `0.25` | 0.0 – 100.0 | Cargo weight of one food item | ProvisionsConfig |  | OK |
| `water_weight_per_ration` | `1.0` | 0.0 – 100.0 | Cargo weight of one water ration | ProvisionsConfig |  | OK |
| `rum_weight_per_unit` | `0.5` | 0.0 – 100.0 | Cargo weight of one rum item | ProvisionsConfig |  | OK |
| `water_bucket_rations` | `3` | 1 – 1000 | Water rations in a water bucket | ProvisionsConfig |  | OK |
| `water_barrel_rations` | `16` | 1 – 1000 | Water rations in a full water barrel (its capacity) | ProvisionsConfig |  | OK |

### `survival` (server): Cold water and swimming hunger

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `swim_exhaustion_multiplier` | `1.5` | 1.0 – 10.0 | Multiplier on vanilla's exhaustion (hunger) for moving in water: 1 = vanilla, 1.5 = half again as much | SwimHunger | 1.0 | OK |
| `cold_water.enabled` | `true` |  | Being in the water of a cold biome fills the freezing meter (frost overlay, slowdown, then freezing damage). Boats, ships, leather armour and the warm effect protect | ColdWater, SeaChestEntity | false | OK |
| `cold_water.freeze_ticks_per_tick` | `1` | 1 – 20 | Freezing meter gained per tick in cold water (powder snow: 1; the meter is full at 140, 7 seconds at 1) | ColdWater, SeaChestEntity |  | OK |
| `cold_water.warm_effect_ticks` | `2400` | 0 – 72000 | How long the warm effect of a warming drink (#pirates_n_ships:warming, e.g. rum) lasts, in ticks (0 = no effect) | ColdWater |  | OK |
| `swim_hunger.enabled` | `true` |  | Moving in water costs extra hunger, scaled by swim_exhaustion_multiplier | SwimHunger |  | OK |

### `sea_chest` (server): The sea chest: worn on the back and floating in water

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Sea chests can be worn on the back and float in water (off = a plain block item: no wearing, no floating; a worn chest is inert) | SeaChestClient, SeaChestConfig, SeaChestItem | false | OK |
| `worn_speed_multiplier` | `0.6` | 0.05 – 1.0 | Walking speed while wearing a sea chest, as a factor of the normal speed | SeaChestConfig |  | OK |
| `sink_pull` | `0.035` | 0.0 – 0.2 | Blocks per tick the worn chest drags its wearer down while in water (holding jump adds 0.04 per tick against fluid gravity 0.005: from 0.035 on a wearer can no longer swim up) | SeaChestConfig |  | OK |
| `drift_factor` | `0.3` | 0.0 – 2.0 | A floating sea chest drifts with the wind at this fraction of the wind speed | SeaChestEntity |  | OK |
| `max_drift_speed` | `3.0` | 0.0 – 20.0 | Cap on a floating chest's wind drift [blocks per second] | SeaChestEntity |  | OK |
| `draft` | `0.45` | 0.1 – 1.0 | Fraction of a floating chest's height under the waterline at rest | SeaChestConfig |  | OK |
| `paddle_enabled` | `true` |  | A player can sit on a floating sea chest with a paddle and paddle it like a small boat (off = the paddle seats nobody; a rider only drifts) | SeaChestConfig | false | OK |
| `paddle_speed` | `1.5` | 0.0 – 10.0 | Paddling speed of a sea chest in still water [blocks per second]; wind drift and currents add to it | SeaChestConfig |  | OK |
| `paddle_reverse_factor` | `0.5` | 0.0 – 1.0 | Backing speed of a paddled sea chest as a fraction of paddle_speed | SeaChestConfig |  | OK |
| `paddle_turn_degrees` | `60.0` | 0.0 – 360.0 | Turn rate of a paddled sea chest [degrees per second] | SeaChestConfig |  | OK |
| `paddle_hunger_factor` | `1.5` | 0.0 – 10.0 | Hunger of paddling as a multiple of vanilla's swimming hunger over the same distance (0 = paddling is free) | SeaChestConfig |  | OK |

### `flags` (server): Flagpoles: hoisting, striking and showing flags

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `hoist_delay_ticks` | `60` | 0 – 200 | Ticks it takes to hoist, change, strike, raise or take down a flag at a flagpole (20 ticks = 1 second) | FlagpoleBlockEntity | 20, DELAY | OK |
| `follow_wind` | `true` |  | Flags turn to point downwind (in 90 degree steps), doubling as a wind indicator | FlagpoleBlockEntity |  | **NAME**: Fix the comment: "Flags stream downwind at the wind's exact angle, doubling as a wind indicator". The comment still says "in 90 degree steps"; since FL1 the flag stores the exact downwind angle (design.md l.249). |
| `wind_update_interval_ticks` | `200` | 20 – 24000 | How often a flying flag checks the wind direction, in ticks | FlagpoleBlockEntity | interval | OK |
| `ship_update_interval_ticks` | `20` | 1 – 24000 | How often a flying flag on a ship re-checks which way is downwind, in ticks (the ship turns under it) | FlagpoleBlockEntity | interval | OK |
| `custom_banner_flags` | `true` |  | Vanilla banners can be hoisted as custom flags (treated as neutral) | Flags | false | OK |
| `stacked_poles` | `true` |  | Flagpoles stacked on each other form one tall pole: the top block flies the flag, the blocks below pass every use up to it. Off: every flagpole block is a pole of its own | FlagpoleRun | false, true | OK |
| `max_pole_height` | `6` | 1 – 16 | The most flagpole blocks one stacked pole may have; placing another on a pole this tall is refused | FlagpoleBlock | 2, 4, MAX | OK |

### `cargo_trade` (server): Cargo weight, port markets, contracts, plunder and port fees

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `cargo_weight_affects_ships` | `true` |  | Cargo weight makes ships sit lower, slower and less agile | TradeConfig | false | OK |
| `weight_factor` | `1.0` | 0.0 – 100.0 | Multiplier from cargo weight to the weight applied to the ship | TradeConfig |  | OK |
| `default_item_weight` | `0.25` | 0.0 – 100.0 | Cargo weight of one item that is not a trade good | TradeConfig |  | OK |
| `weigh_interval_ticks` | `40` | 1 – 1200 | Ticks between two weighings of a ship's cargo (vanilla containers' downward force, load level) | ShipCargo |  | OK |
| `treasure_map_price` | `60` | 0 – 100000 | Doubloons a fence on a pirate island asks for one blank treasure map | TreasureMaps |  | OK |
| `load_levels.laden_at` | `0.33` | 0.0 – 10.0 | Ratio from which a ship is laden | TradeConfig |  | OK |
| `load_levels.heavily_laden_at` | `0.75` | 0.0 – 10.0 | Ratio from which a ship is heavily laden | TradeConfig |  | OK |
| `load_levels.overloaded_above` | `1.0` | 0.0 – 10.0 | Ratio above which a ship is overloaded | TradeConfig |  | OK |
| `load_levels.capacity_per_block` | `8.0` | 0.01 – 1000.0 | A ship's cargo capacity in weight units per block of the ship (the load ratio is cargo weight / (blocks × this)) | TradeService |  | OK |
| `market.price_volatility` | `0.05` | 0.0 – 10.0 | How strongly trading moves prices: log-price change per 64 units of a normal good in a village (0.05 = about 5 % per stack) | TradeConfig |  | OK |
| `market.price_recovery_rate` | `0.3` | 0.0 – 1.0 | Share of a price and stock deviation that recovers per in-game day (1 = instantly) | TradeConfig |  | OK |
| `market.spread` | `0.2` | 0.01 – 1.0 | Gap between buy and sell price as a share of the mid price (must be above 0 so trading in one port always loses) | TradeConfig |  | OK |
| `market.produced_price_factor` | `0.6` | 0.01 – 100.0 | Price multiplier for goods a port produces | TradeConfig |  | OK |
| `market.neutral_price_factor` | `1.0` | 0.01 – 100.0 | Price multiplier for goods a port neither produces nor demands | TradeConfig |  | OK |
| `market.demanded_price_factor` | `1.6` | 0.01 – 100.0 | Price multiplier for goods a port demands | TradeConfig |  | OK |
| `market.stock_limits` | `true` |  | Ports have limited stock to sell and stop buying a good once saturated | TradeConfig |  | OK |
| `market.stock_produced` | `768.0` | 0.0 – 1000000.0 | Units in stock of a produced good (times the good's stock factor and the port depth) | TradeConfig |  | OK |
| `market.stock_neutral` | `192.0` | 0.0 – 1000000.0 | Units in stock of a neutral good | TradeConfig |  | OK |
| `market.stock_demanded` | `48.0` | 0.0 – 1000000.0 | Units in stock of a demanded good | TradeConfig |  | OK |
| `market.absorb_produced` | `128.0` | 0.0 – 1000000.0 | Units of a produced good a port buys before it is saturated | TradeConfig |  | OK |
| `market.absorb_neutral` | `512.0` | 0.0 – 1000000.0 | Units of a neutral good a port buys before it is saturated | TradeConfig |  | OK |
| `market.absorb_demanded` | `1536.0` | 0.0 – 1000000.0 | Units of a demanded good a port buys before it is saturated | TradeConfig |  | OK |
| `market.depth_seafarer_village` | `1.0` | 0.01 – 100.0 | Market depth of seafarer villages: bigger stock and smaller price impact | TradeConfig |  | OK |
| `market.depth_navy_outpost` | `0.75` | 0.01 – 100.0 | Market depth of navy outposts | TradeConfig |  | OK |
| `market.depth_pirate_island` | `0.5` | 0.01 – 100.0 | Market depth of pirate islands | TradeConfig |  | OK |
| `contracts.enabled` | `true` |  | Harbor masters offer delivery contracts | TradeConfig | false | OK |
| `contracts.offers_per_day` | `3` | 0 – 32 | Contract offers per port and day | TradeConfig |  | OK |
| `contracts.min_quantity` | `32` | 1 – 100000 | Smallest contract quantity (before the good's stock factor) | TradeConfig |  | OK |
| `contracts.max_quantity` | `192` | 1 – 100000 | Largest contract quantity (before the good's stock factor) | TradeConfig |  | OK |
| `contracts.reward_base` | `0.6` | 0.0 – 100.0 | Reward per unit of base value before bonuses | TradeConfig |  | OK |
| `contracts.price_difference_weight` | `0.8` | 0.0 – 100.0 | Extra reward per unit of base value per point of price factor difference between the ports | TradeConfig |  | OK |
| `contracts.distance_bonus_per_1000_blocks` | `0.15` | 0.0 – 100.0 | Reward multiplier gained per 1000 blocks of distance | TradeConfig |  | OK |
| `contracts.risk_bonus` | `0.6` | 0.0 – 100.0 | Reward multiplier gained on the riskiest routes (through pirate waters) | TradeConfig |  | OK |
| `contracts.blocks_per_day` | `4000.0` | 1.0 – 1000000.0 | Expected travel per in-game day when setting deadlines | TradeConfig |  | OK |
| `contracts.slack_days` | `2` | 0 – 1000 | Extra days on top of the travel time | TradeConfig |  | OK |
| `contracts.offer_lifetime_days` | `2` | 0 – 1000 | Days an offer stays on the board after the day it was made | TradeConfig |  | OK |
| `contracts.deposit_fraction` | `0.2` | 0.0 – 10.0 | Deposit paid on accepting, as a share of the reward (refunded on delivery, lost on failure) | TradeConfig |  | OK |
| `contracts.max_active_per_player` | `3` | 0 – 100 | Accepted contracts one player may hold at once | TradeConfig |  | OK |
| `plunder.enabled` | `true` |  | Plundered goods are marked and treated differently by fences and navy ports | TradeConfig, TradeService | false | OK |
| `plunder.fence_discount` | `0.35` | 0.0 – 1.0 | Share a pirate fence takes off the price of plundered goods | TradeConfig |  | OK |
| `port_fees.enabled` | `true` |  | Navy outposts charge a docking fee | DockingFees, TradeConfig | false | OK |
| `port_fees.navy_docking_fee` | `5` | 0 – 1000000 | Docking fee in doubloons | TradeConfig |  | OK |
| `port_fees.waiver_navy_standing` | `50` | -1000000 – 1000000 | Navy standing at or above which the fee is waived | TradeConfig |  | OK |
| `port_fees.check_interval_ticks` | `100` | 1 – 12000 | Ticks between two looks for owned ships docked in a port's area | DockingFees, TradeConfig |  | OK |
| `port_fees.berth_radius` | `6.0` | 0.0 – 64.0 | A ship whose hull lies within this many blocks of a berth, slower than berth_max_speed, is tied up (anchored ships always count) | TradeConfig |  | OK |
| `port_fees.berth_max_speed` | `0.1` | 0.0 – 100.0 | Speed (m/s) below which a ship at a berth counts as tied up | TradeConfig |  | OK |
| `port_fees.fee_period_days` | `1` | 1 – 1000 | Days between two charges of the same ship at the same port while it stays docked | TradeConfig |  | OK |
| `port_fees.charge_ship_chest` | `true` |  | The ship's own doubloons (chests and crates aboard) pay when the owner's wallet cannot | TradeConfig |  | OK |
| `port_fees.refuse_desk_when_owed` | `true` |  | The port's harbor desk refuses a captain who owes harbor dues there until they pay | TradeConfig |  | OK |
| `containers.crate_capacity_stacks` | `32` | 1 – 4096 | Capacity of a cargo crate in full stacks of the held item (32 stacks = 2048 sugar or 512 rum) | CargoContainers |  | OK |
| `containers.barrel_capacity_items` | `1536` | 1 – 262144 | Capacity of a cargo barrel in items, whatever their stack size (better than a crate for rum and other small stacks) | CargoContainers |  | OK |
| `containers.accepts` | `ANY_STACKABLE` |  | What an empty container accepts: any stackable item, or only trade goods | CargoContainers |  | OK |
| `market_backend.max_trade_quantity` | `4096` | 1 – 262144 | Largest quantity one buy or sell request may move | MarketBackend, MarketScreen |  | OK |
| `market_backend.market_reach` | `16.0` | 1.0 – 256.0 | How far (blocks) a player may move from where the market was opened and still trade | MarketBackend |  | OK |
| `market_backend.container_reach` | `16.0` | 1.0 – 256.0 | How far (blocks) a cargo container may be from the player to buy into it or sell or deliver from it | MarketBackend, TradeCommands |  | OK |
| `market_backend.market_refresh_ticks` | `20` | 1 – 1200 | How often (ticks) open market screens are re-sent when what they show changed (prices, stock, the viewer's doubloons) | MarketBackend | interval | OK |
| `harbor_desks.desks_enabled` | `true` |  | Harbor master's desks open the market screen (off = desks are inert; the debug commands still work) | HarborDeskBlock, HarborDeskService | false | OK |
| `harbor_desks.desk_reach` | `8.0` | 1.0 – 64.0 | How far (blocks) a player may be from the desk and still trade; the screen closes beyond it | HarborDeskService, HarborMasterInteractions, MarketBackend |  | OK |
| `harbor_desks.direct_use` | `true` |  | Using the desk block opens the market (off = only talking to the port's harbor master opens it) | HarborDeskService | false | OK |

### `law` (server): Criminal score, fines and bounties

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `criminal_score_enabled` | `true` |  | Track criminal scores. Off = no crime is recorded, nobody is wanted and the navy places no bounties | FlagCrimes, Gunnery, Hunting, LawConfig, LawService | false | OK |
| `decay_per_day` | `10.0` | 0.0 – 100000.0 | Criminal score points lost per in-game day (24000 ticks) | LawConfig | 24000.0 | OK |
| `decay_delay_seconds` | `300` | 0 – 864000 | Seconds after the last crime before the score starts to decay | LawConfig | 0 | OK |
| `max_score` | `1000` | 1 – 1000000 | Highest possible criminal score | LawConfig |  | OK |
| `suspect_threshold` | `10` | 0 – 1000000 | Score from which an entity counts as a suspect | LawConfig |  | OK |
| `bounty_threshold` | `50` | 1 – 1000000 | Score from which an entity is wanted and the navy places a bounty | LawConfig |  | OK |
| `notorious_threshold` | `200` | 1 – 1000000 | Score from which an entity is notorious | LawConfig |  | OK |
| `fine_cost_per_point` | `3` | 1 – 10000 | Doubloons per criminal score point when paying a fine | LawConfig, OfficerTurnIns |  | OK |
| `fines_when_notorious` | `false` |  | Notorious criminals may still pay fines | LawConfig |  | OK |
| `navy_bounty_per_point` | `2.0` | 0.0 – 10000.0 | Navy bounty doubloons per criminal score point | LawConfig |  | OK |
| `navy_bounty_withdraw_ratio` | `0.5` | 0.0 – 1.0 | The navy withdraws its bounty once the score decays below bounty_threshold times this | LawConfig |  | OK |
| `player_bounties` | `true` |  | Players may place bounties on other players and NPCs | LawConfig, NoticeBoardBackend |  | OK |
| `player_bounty_minimum` | `10` | 1 – 1000000 | Smallest bounty a player can place, in doubloons | LawConfig, NoticeBoardBackend |  | OK |
| `player_bounty_duration_days` | `0` | 0 – 100000 | In-game days a player bounty stays on the board (0 = until claimed) | LawConfig |  | OK |
| `alive_factor` | `1.5` | 1.0 – 100.0 | Bounty payout multiplier for delivering the target alive instead of a proof item | LawConfig |  | OK |
| `score_after_claim_factor` | `0.0` | 0.0 – 1.0 | The target's criminal score is multiplied by this when a bounty on them is claimed (0 = clean slate) | LawConfig |  | OK |
| `officer_fines` | `true` |  | Using a navy officer with doubloons in hand pays the fine for your criminal score (whole points, fine_cost_per_point each) | OfficerTurnIns |  | OK |
| `ransom_needs_port` | `false` |  | Only navy officers inside a navy outpost pay ransoms. Off = any navy officer ransoms led navy and merchant prisoners | OfficerTurnIns | true | OK |
| `plunder_notice` | `true` |  | Village and navy outpost desks refuse plunder-marked goods either way; on = the harbor master reports the seller (selling_plunder, first refusal per port and day) and a navy observer that sees more than plunder_notice_units marked units in a ship's containers records suspected_piracy (once per ship and day) | LawConfig, PlunderCrimes | false | OK |
| `plunder_notice_units` | `16` | 0 – 1000000 | Plunder-marked units a ship's containers may hold before a navy observer suspects piracy (more than this is noticed) | LawConfig |  | OK |
| `bounty.turn_in_officers` | `true` |  | Navy officers take bounty proofs and shackled prisoners (right-click with a proof or an empty hand). The pirate turn-in reward is law.pirate_turn_in (0 = the navy pays nothing for a pirate without a bounty) | OfficerTurnIns | false | OK |
| `bounty.delivery_range` | `4.0` | 1.0 – 32.0 | Blocks within which a shackled prisoner must stand from the navy officer to be delivered | OfficerTurnIns |  | OK |
| `bounty.notice_boards` | `true` |  | Notice boards open a list of the active bounties and let players place bounties | NoticeBoardBackend, NoticeBoardBlock | false | OK |
| `bounty.notice_board_reach` | `8.0` | 2.0 – 64.0 | Blocks from a notice board within which its screen stays open | NoticeBoardBackend |  | OK |
| `severity.attack_navy` | `10` | 0 – 100000 | Points for: attack navy | LawConfig |  | OK |
| `repeat_cooldown_seconds.attack_navy` | `30` | 0 – 864000 | Repeat window for: attack navy | LawConfig |  | OK |
| `severity.kill_navy` | `30` | 0 – 100000 | Points for: kill navy | LawConfig |  | OK |
| `repeat_cooldown_seconds.kill_navy` | `0` | 0 – 864000 | Repeat window for: kill navy | LawConfig |  | OK |
| `severity.attack_villager` | `5` | 0 – 100000 | Points for: attack villager | LawConfig |  | OK |
| `repeat_cooldown_seconds.attack_villager` | `30` | 0 – 864000 | Repeat window for: attack villager | LawConfig |  | OK |
| `severity.kill_villager` | `20` | 0 – 100000 | Points for: kill villager | LawConfig |  | OK |
| `repeat_cooldown_seconds.kill_villager` | `0` | 0 – 864000 | Repeat window for: kill villager | LawConfig |  | OK |
| `severity.attack_neutral_ship` | `15` | 0 – 100000 | Points for: attack neutral ship | LawConfig |  | OK |
| `repeat_cooldown_seconds.attack_neutral_ship` | `120` | 0 – 864000 | Repeat window for: attack neutral ship | LawConfig |  | OK |
| `severity.theft` | `5` | 0 – 100000 | Points for: theft | LawConfig |  | OK |
| `repeat_cooldown_seconds.theft` | `60` | 0 – 864000 | Repeat window for: theft | LawConfig |  | OK |
| `severity.piracy` | `50` | 0 – 100000 | Points for: piracy | LawConfig |  | OK |
| `repeat_cooldown_seconds.piracy` | `600` | 0 – 864000 | Repeat window for: piracy | LawConfig |  | OK |
| `severity.seen_under_jolly_roger` | `10` | 0 – 100000 | Points for: seen under jolly roger | LawConfig |  | OK |
| `repeat_cooldown_seconds.seen_under_jolly_roger` | `300` | 0 – 864000 | Repeat window for: seen under jolly roger | LawConfig |  | OK |
| `severity.caught_false_colors` | `40` | 0 – 100000 | Points for: caught false colors | LawConfig |  | OK |
| `repeat_cooldown_seconds.caught_false_colors` | `300` | 0 – 864000 | Repeat window for: caught false colors | LawConfig |  | OK |
| `severity.attack_struck_colors` | `40` | 0 – 100000 | Points for: attack struck colors | LawConfig |  | OK |
| `repeat_cooldown_seconds.attack_struck_colors` | `120` | 0 – 864000 | Repeat window for: attack struck colors | LawConfig |  | OK |
| `severity.press_gang` | `15` | 0 – 100000 | Points for: press gang | LawConfig |  | OK |
| `repeat_cooldown_seconds.press_gang` | `0` | 0 – 864000 | Repeat window for: press gang | LawConfig |  | OK |
| `severity.desertion` | `60` | 0 – 100000 | Points for: desertion | LawConfig |  | OK |
| `repeat_cooldown_seconds.desertion` | `600` | 0 – 864000 | Repeat window for: desertion | LawConfig |  | OK |
| `severity.fence_plunder` | `15` | 0 – 100000 | Points for: fence plunder | LawConfig |  | **DEAD**: Skip legacy crime types when LawConfig declares severity and repeat_cooldown_seconds (CrimeType.legacy() exists already). fence_plunder is never recorded since LAW3b (design.md l.657); its two values are generated by the loop over every CrimeType and do nothing. |
| `repeat_cooldown_seconds.fence_plunder` | `60` | 0 – 864000 | Repeat window for: fence plunder | LawConfig |  | **DEAD**: Skip legacy crime types when LawConfig declares severity and repeat_cooldown_seconds (CrimeType.legacy() exists already). fence_plunder is never recorded since LAW3b (design.md l.657); its two values are generated by the loop over every CrimeType and do nothing. |
| `severity.selling_plunder` | `17` | 0 – 100000 | Points for: selling plunder | LawConfig |  | OK |
| `repeat_cooldown_seconds.selling_plunder` | `1200` | 0 – 864000 | Repeat window for: selling plunder | LawConfig |  | OK |
| `severity.suspected_piracy` | `15` | 0 – 100000 | Points for: suspected piracy | LawConfig |  | OK |
| `repeat_cooldown_seconds.suspected_piracy` | `1200` | 0 – 864000 | Repeat window for: suspected piracy | LawConfig |  | OK |
| `pirate_turn_in.deckhand` | `10` | 0 – 1000000 | Reward for a captured pirate deckhand | LawConfig | 0 | OK |
| `pirate_turn_in.buccaneer` | `20` | 0 – 1000000 | Reward for a captured pirate buccaneer | LawConfig | 0 | OK |
| `pirate_turn_in.officer` | `50` | 0 – 1000000 | Reward for a captured pirate officer | LawConfig | 0 | OK |
| `pirate_turn_in.captain` | `150` | 0 – 1000000 | Reward for a captured pirate captain | LawConfig | 0 | OK |
| `world.combat_crimes` | `true` |  | Hurting or killing villagers, wandering traders and navy is reported as a crime | CombatCrimeDetector | false | OK |
| `world.prosecute_monsters` | `false` |  | Monsters (zombies, pillagers, ...) get criminal records for hurting protected entities too | CombatCrimeDetector |  | OK |
| `world.theft_detection` | `true` |  | Taking items out of a village container while a villager or navy watches is reported as theft | LawConfig | false | OK |
| `world.theft_require_village` | `true` |  | Only containers inside a village structure count. Off = every container no player placed counts | LawConfig | false | OK |
| `world.theft_witness_range` | `16.0` | 0.0 – 128.0 | Blocks within which a villager, trader or navy member notices a theft | LawConfig |  | OK |
| `world.theft_witness_line_of_sight` | `true` |  | A witness must be able to see the thief | LawConfig |  | OK |
| `world.bounty_proof_drops` | `true` |  | Killing a target with a bounty gives the killer a proof item to claim the bounty with | ProofDrops | false | OK |
| `world.wanted_sync_interval_ticks` | `20` | 1 – 1200 | How often (ticks) a player's wanted level is checked and sent to their client if it changed | WantedSync |  | OK |
| `world.navy_hostility_threshold` | `WANTED` |  | Lowest wanted level the navy attacks on sight | Gunnery, Hunting, LawService |  | OK |
| `flags.enabled` | `true` |  | Flags act in the world. Off = NPCs ignore ship flags, nobody checks for false colours and attacking ships that struck their colours or fly a neutral flag is no crime | FlagCrimes | false | OK |
| `flags.observe_range` | `48.0` | 1.0 – 256.0 | Blocks within which navy soldiers and officers notice a ship's flag (Jolly Roger, false colours) | FlagCrimes | 6.0 | OK |
| `flags.observe_interval_ticks` | `40` | 1 – 12000 | How often (ticks) the navy looks at the flags of ships in range and rolls for false colours | FlagCrimes | 10 | OK |
| `flags.false_flag_wanted_threshold` | `1` | 0 – 4 | A navy flag is also false colours when the ship's owner has at least this wanted level, whatever their navy reputation (0 clean, 1 suspect, 2 wanted, 3 notorious, 4 = reputation and bounties only) | LawService |  | OK |
| `flags.blown_cover_ticks` | `6000` | 0 – 1728000 | Ticks the navy treats a ship caught under false colours as hostile, whatever it flies | FlagCrimes |  | OK |

### `flags_brig` (server): Flags, false colors and prisoners

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `false_flag_detection_strength` | `1.0` | 0.0 – 100.0 | Multiplier for how quickly observers see through false colors (0 = never) | LawConfig | 100.0 | OK |
| `detection_base_rate` | `0.05` | 0.0 – 100.0 | Detections per second at close range against a captain with a clean record | LawConfig |  | OK |
| `detection_close_range` | `16.0` | 0.0 – 1024.0 | Blocks within which an observer sees a flag perfectly | LawConfig | 0.0 | OK |
| `detection_max_range` | `96.0` | 0.0 – 4096.0 | Blocks beyond which false colors can't be detected (without a crow's nest) | LawConfig | 6.0 | **DUP**: Set it to 48 (= law.flags.observe_range), so the false-colours odds fall to zero where navy observers stop looking; or drop one of the two ranges. FlagCrimes only looks at ships within law.flags.observe_range (48, x1.5 with a nest), so the falloff of FalseColorsDetection between 48 and 96 never applies: the comment "beyond which false colors can't be detected" is never the limit. |
| `crows_nest_observers` | `true` |  | Navy observers aboard a ship with a manned crow's nest (a crew lookout seated in it or a player standing in it) notice flags farther and see through false colors faster (crows_nest_range_factor, crows_nest_rate_factor) | FlagCrimes | false | OK |
| `crows_nest_range_factor` | `1.5` | 1.0 – 10.0 | Detection range multiplier for an observer with a manned crow's nest (applies to law.flags.observe_range and detection_max_range) | LawConfig | 4.0 | OK |
| `crows_nest_rate_factor` | `1.5` | 1.0 – 10.0 | Detection rate multiplier for an observer with a manned crow's nest | LawConfig | 3.0 | OK |
| `detection_score_scale` | `100.0` | 1.0 – 1000000.0 | Criminal score that doubles the detection rate | LawConfig |  | OK |
| `navy_flag_min_standing` | `0` | -101 – 101 | Navy reputation (-100 to 100, reputation.deeds) needed to fly the navy flag legitimately; below it a navy flag is false colours (-101 = any reputation, 101 = never legitimate) | CareerRewards, LawService |  | **DESIGN**: Old config files keep `100` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: REP1 turned it into a navy reputation (-100..100, 101 = never); 100 now means only a player at full navy reputation flies the navy flag legitimately, so the navy flag is false colours for practically everyone (design.md l.672: default 0). |
| `npc_surrender` | `true` |  | Merchant NPC ships may surrender to a ship flying the Jolly Roger | FlagAllegiance, LawService |  | OK |
| `player_capture` | `true` |  | Players with a bounty can be captured in shackles (PvP) | BrigService |  | OK |
| `prisoner_escapes` | `true` |  | Prisoners may try to escape from the brig | BrigService | false, true | OK |
| `prisoner_interactions` | `true` |  | Prisoners can be ransomed at navy officers, press-ganged with the captain's whistle and released by sneak-using them with an empty hand | OfficerTurnIns, PrisonerInteractions, PrisonerOutcomes |  | OK |

### `brig` (server): Shackles, prisoners and brig cells

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `capture_health_fraction` | `0.25` | 0.01 – 1.0 | A target can be shackled at or below this fraction of its max health | BrigService |  | OK |
| `follow_distance` | `3.0` | 1.0 – 16.0 | A led prisoner walks after its captor when farther away than this (blocks) | BrigService |  | OK |
| `pull_distance` | `6.0` | 2.0 – 32.0 | Beyond this distance the chain pulls a led prisoner towards its captor, like a lead | BrigService |  | OK |
| `break_distance` | `12.0` | 4.0 – 64.0 | Beyond this distance the captor loses hold: the prisoner stays shackled and stands still | BrigService |  | OK |
| `follow_speed` | `1.0` | 0.1 – 3.0 | Walking speed modifier of a led prisoner | BrigService |  | OK |
| `max_cell_size` | `64` | 2 – 1024 | Most open blocks (air volume) a brig cell may have | BrigService |  | OK |
| `check_interval_ticks` | `20` | 1 – 1200 | Ticks between cell and escape checks of a prisoner | BrigConfig, BrigService | 1 | OK |
| `escape_chance_per_minute` | `0.1` | 0.0 – 1.0 | Chance per minute that a prisoner outside a locked cell (and not being led) escapes | BrigConfig | 0.0, 1.0 | OK |
| `low_morale_escape_chance_per_minute` | `0.02` | 0.0 – 1.0 | Chance per minute that a prisoner escapes even a locked cell while the crew's morale is low | BrigConfig | 0.0, 1.0 | OK |
| `player_capture_seconds` | `300` | 10 – 86400 | A captured player frees themselves after this many seconds of game time | BrigService |  | OK |
| `ransom_common` | `15` | 0 – 1000000 | Ransom in doubloons for a common prisoner | BrigConfig |  | OK |
| `ransom_merchant` | `60` | 0 – 1000000 | Ransom in doubloons for a captured merchant | BrigConfig |  | OK |
| `ransom_navy_officer` | `120` | 0 – 1000000 | Ransom in doubloons for a captured navy officer | BrigConfig |  | OK |
| `captain_ransom_multiplier` | `3.0` | 1.0 – 100.0 | Ransom multiplier for a captured captain | BrigConfig |  | OK |

### `reputation` (server): Reputation with the navy, the pirates and the villagers: deeds shift it, it changes prices and hostility

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Track reputation. Off = deeds change nothing, prices don't swing, villagers always trade, pirates and navy ignore reputation, and the false-flag rule and port fees use a navy standing of 0 | Reputation | false | OK |
| `decay_per_day` | `2.0` | 0.0 – 200.0 | Points every score moves toward 0 per in-game day (24000 ticks), spread evenly over the day | Reputation |  | OK |
| `price_swing` | `0.1` | 0.0 – 0.9 | Largest price change from reputation, as a share of the price: at +100 buying costs this much less and selling pays this much more, at -100 the reverse (villagers at village markets, pirates at fences) | MarketReputation |  | OK |
| `pirate_friendly_threshold` | `40` | -100 – 101 | Pirates leave a player whose pirate reputation is above this alone until the player attacks them (101 = never) | Reputation |  | OK |
| `navy_hostile_threshold` | `-60` | -101 – 100 | The navy attacks a player whose navy reputation is below this on sight, even without a bounty (-101 = never) | Reputation |  | OK |
| `villager_trade_threshold` | `-60` | -101 – 100 | Village markets refuse to trade with a player whose villager reputation is below this (-101 = never) | Reputation |  | OK |
| `village_trade_daily_cap` | `5` | 0 – 1000 | Village trades per in-game day that count as the trade_village deed | MarketReputation | 2 | OK |
| `attack_repeat_seconds` | `30` | 0 – 3600 | Hitting the same navy, pirate or villager again within this many seconds is not another attack deed | CombatDeeds, LawDeeds |  | OK |
| `sync_interval_ticks` | `40` | 1 – 1200 | How often (ticks) a player's shown reputation is checked and sent to their client if it changed | ReputationSync |  | OK |
| `deeds.attack_navy.navy` | `-3` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.attack_navy.pirates` | `1` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.attack_navy.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.kill_navy.navy` | `-15` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.kill_navy.pirates` | `5` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.kill_navy.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.attack_pirate.navy` | `1` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.attack_pirate.pirates` | `-3` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.attack_pirate.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.kill_pirate.navy` | `5` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.kill_pirate.pirates` | `-15` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.kill_pirate.villagers` | `2` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.attack_villager.navy` | `-2` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.attack_villager.pirates` | `0` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.attack_villager.villagers` | `-5` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.kill_villager.navy` | `-10` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.kill_villager.pirates` | `0` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.kill_villager.villagers` | `-25` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.attack_merchant_ship.navy` | `-3` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.attack_merchant_ship.pirates` | `2` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.attack_merchant_ship.villagers` | `-3` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.plunder_merchant.navy` | `-8` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.plunder_merchant.pirates` | `8` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.plunder_merchant.villagers` | `-5` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.fence_plunder.navy` | `0` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.fence_plunder.pirates` | `3` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.fence_plunder.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.trade_village.navy` | `0` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.trade_village.pirates` | `0` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.trade_village.villagers` | `1` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.turn_in_pirate.navy` | `6` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.turn_in_pirate.pirates` | `-5` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.turn_in_pirate.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.pay_fine.navy` | `3` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.pay_fine.pirates` | `0` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.pay_fine.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.fly_false_colours.navy` | `-10` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.fly_false_colours.pirates` | `0` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.fly_false_colours.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.complete_navy_quest.navy` | `6` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.complete_navy_quest.pirates` | `-2` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.complete_navy_quest.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.complete_pirate_quest.navy` | `-2` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.complete_pirate_quest.pirates` | `6` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.complete_pirate_quest.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.complete_village_quest.navy` | `0` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.complete_village_quest.pirates` | `0` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.complete_village_quest.villagers` | `4` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.sink_merchant.navy` | `-10` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.sink_merchant.pirates` | `5` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.sink_merchant.villagers` | `-8` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.sink_navy.navy` | `-20` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.sink_navy.pirates` | `10` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.sink_navy.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.sink_pirate.navy` | `8` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.sink_pirate.pirates` | `-15` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.sink_pirate.villagers` | `3` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.capture_navy.navy` | `-20` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.capture_navy.pirates` | `10` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.capture_navy.villagers` | `0` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |
| `deeds.capture_pirate.navy` | `8` | -100 – 100 | Change of the navy reputation | ReputationConfig |  | OK |
| `deeds.capture_pirate.pirates` | `-12` | -100 – 100 | Change of the pirates reputation | ReputationConfig |  | OK |
| `deeds.capture_pirate.villagers` | `2` | -100 – 100 | Change of the villagers reputation | ReputationConfig |  | OK |

### `careers` (server): Careers: the navy rank ladder, pirate infamy and the letter of marque

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Track careers. Off = deeds change no counters and promote nobody, officers neither enlist nor grant letters, and nobody deserts | CareerConfig, CareerShipTitles, CareerTeams, Careers | false | OK |
| `officer_reach` | `4.0` | 1.0 – 16.0 | How close (blocks) a player must stay to the navy officer for the career screen's actions | CareerBackend, CareerScreen |  | OK |
| `max_pirate_rep_to_enlist` | `0` | -100 – 100 | Enlisting and navy promotions need a pirate reputation of at most this | CareerConfig |  | OK |
| `desertion_deeds` | `[plunder_merchant, attack_merchant_ship, attack_navy, kill_navy]` |  | Deeds (ids as in reputation.deeds) that are desertion while in navy service and void a letter of marque | CareerConfig |  | OK |
| `desertion_is_crime` | `true` |  | Desertion is also the 'desertion' crime on the criminal record (the navy then sets a bounty by itself) | Careers | false | OK |
| `captain_weight` | `5` | 1 – 100 | How many pirate kills killing a pirate captain counts as | CareerConfig |  | OK |
| `captain_kinds` | `[pirates_n_ships:pirate_captain]` |  | Entity types that count as pirate captains (weighted kills, the captain's prize money) | Careers |  | OK |
| `officer_kinds` | `[pirates_n_ships:navy_officer]` |  | Entity types that count as navy officers (a pirate's 'captures or captains') | Careers |  | OK |
| `name_prefix` | `true` |  | Show the career title (Lt., Capt., Privateer, Dread Pirate, ...) in front of the player's name in chat, the tab list and over the head, through a scoreboard team per title. Players on another team keep it. Off = players are taken off the title teams when they next log in | CareerTeams | false | OK |
| `title_on_ship` | `true` |  | Naming a ship you own with a name tag puts your career title in front of the name ("Capt. Black Gull"); a title typed into the name tag is always replaced by your own | CareerShipTitles | false | OK |
| `navy.midshipman.min_navy_rep` | `10` | -100 – 101 | Navy reputation needed (101 = never) | CareerConfig |  | OK |
| `navy.midshipman.pirates_killed` | `0` | 0 – 100000 | Pirates killed or turned in (a captain counts captain_weight) | CareerConfig |  | OK |
| `navy.midshipman.quests` | `0` | 0 – 10000 | Navy quests completed | CareerConfig |  | OK |
| `navy.lieutenant.min_navy_rep` | `25` | -100 – 101 | Navy reputation needed (101 = never) | CareerConfig |  | OK |
| `navy.lieutenant.pirates_killed` | `5` | 0 – 100000 | Pirates killed or turned in (a captain counts captain_weight) | CareerConfig |  | OK |
| `navy.lieutenant.quests` | `1` | 0 – 10000 | Navy quests completed | CareerConfig |  | OK |
| `navy.captain.min_navy_rep` | `45` | -100 – 101 | Navy reputation needed (101 = never) | CareerConfig |  | OK |
| `navy.captain.pirates_killed` | `15` | 0 – 100000 | Pirates killed or turned in (a captain counts captain_weight) | CareerConfig |  | OK |
| `navy.captain.quests` | `3` | 0 – 10000 | Navy quests completed | CareerConfig |  | OK |
| `navy.commodore.min_navy_rep` | `65` | -100 – 101 | Navy reputation needed (101 = never) | CareerConfig |  | OK |
| `navy.commodore.pirates_killed` | `30` | 0 – 100000 | Pirates killed or turned in (a captain counts captain_weight) | CareerConfig |  | OK |
| `navy.commodore.quests` | `6` | 0 – 10000 | Navy quests completed | CareerConfig |  | OK |
| `navy.admiral.min_navy_rep` | `85` | -100 – 101 | Navy reputation needed (101 = never) | CareerConfig |  | OK |
| `navy.admiral.pirates_killed` | `60` | 0 – 100000 | Pirates killed or turned in (a captain counts captain_weight) | CareerConfig |  | OK |
| `navy.admiral.quests` | `10` | 0 – 10000 | Navy quests completed | CareerConfig |  | OK |
| `infamy.buccaneer.min_pirate_rep` | `15` | -100 – 101 | Pirate reputation needed (101 = never) | CareerConfig |  | OK |
| `infamy.buccaneer.plunder_coins` | `100` | 0 – 100000000 | Doubloons received for plunder at fences | CareerConfig |  | OK |
| `infamy.buccaneer.captures_or_captains` | `0` | 0 – 100000 | Merchants plundered, ships captured and navy officers killed, together | CareerConfig |  | OK |
| `infamy.dread_captain.min_pirate_rep` | `40` | -100 – 101 | Pirate reputation needed (101 = never) | CareerConfig |  | OK |
| `infamy.dread_captain.plunder_coins` | `1000` | 0 – 100000000 | Doubloons received for plunder at fences | CareerConfig |  | OK |
| `infamy.dread_captain.captures_or_captains` | `3` | 0 – 100000 | Merchants plundered, ships captured and navy officers killed, together | CareerConfig |  | OK |
| `infamy.pirate_lord.min_pirate_rep` | `75` | -100 – 101 | Pirate reputation needed (101 = never) | CareerConfig |  | OK |
| `infamy.pirate_lord.plunder_coins` | `5000` | 0 – 100000000 | Doubloons received for plunder at fences | CareerConfig |  | OK |
| `infamy.pirate_lord.captures_or_captains` | `10` | 0 – 100000 | Merchants plundered, ships captured and navy officers killed, together | CareerConfig |  | OK |
| `letter.enabled` | `true` |  | Navy officers grant letters of marque | CareerConfig, ShipPrizes |  | OK |
| `letter.min_navy_rep` | `20` | -100 – 101 | Navy reputation needed for a letter | CareerConfig, CareerScreen |  | OK |
| `letter.max_infamy` | `BUCCANEER` |  | Highest infamy rank that still gets a letter | CareerConfig |  | OK |
| `letter.fee` | `200` | 0 – 1000000 | Doubloons a letter costs | CareerConfig |  | OK |
| `letter.void_days` | `3.0` | 0.0 – 365.0 | In-game days after a voided letter before the navy grants a new one | CareerConfig |  | OK |
| `letter.prize_deckhand` | `5` | 0 – 100000 | Prize money (doubloons) per pirate killed under a letter | CareerConfig |  | OK |
| `letter.prize_captain` | `50` | 0 – 100000 | Prize money (doubloons) per pirate captain killed under a letter | CareerConfig |  | OK |
| `prize_money` | `60` | 0 – 100000 | Doubloons paid at once to a holder of a valid letter of marque who sinks (last cannon hit) or captures a pirate ship at sea, once per ship. 0 = none | ShipPrizes |  | OK |
| `rewards.flag_right` | `true` |  | Navy officers from flag_right_rank up fly the navy flag legitimately whatever their navy reputation (a bounty still makes it false colours) | CareerConfig | false | OK |
| `rewards.flag_right_rank` | `LIEUTENANT` |  | Lowest navy rank with the right to fly the navy flag | CareerConfig |  | OK |
| `rewards.fee_waiver` | `true` |  | Navy officers from fee_waiver_rank up pay no docking fee at navy outposts | CareerConfig | false | OK |
| `rewards.fee_waiver_rank` | `LIEUTENANT` |  | Lowest navy rank that docks at navy outposts for free | CareerConfig |  | OK |
| `rewards.navy_shipyard` | `true` |  | The harbor master's desk of a navy outpost takes ship orders from navy officers of navy_orders_min_rank and up | CareerConfig |  | OK |
| `rewards.navy_orders_min_rank` | `CAPTAIN` |  | Lowest navy rank the navy shipyard builds for | CareerConfig |  | OK |
| `rewards.promotion_gifts` | `true` |  | A new rank comes with the items of <rank>_items (once per rank and player; dropped at the feet if the inventory is full) | CareerRewards |  | OK |
| `rewards.infamy_prices` | `true` |  | Fences at pirate islands give the infamous better prices (on top of the pirates' reputation) | CareerConfig |  | OK |
| `rewards.infamy_price_bonus` | `20` | 0 – 200 | Price score (as pirate reputation points, see reputation.price_swing) a Pirate Lord gains at fences; lower infamy ranks get a linear share (Buccaneer a third, Dread Captain two thirds) | CareerConfig |  | OK |
| `rewards.infamy_pirates_friendly` | `true` |  | Pirates leave players of infamy_pirates_friendly_rank and up alone until attacked, whatever their reputation | CareerConfig |  | OK |
| `rewards.infamy_pirates_friendly_rank` | `DREAD_CAPTAIN` |  | Lowest infamy rank the pirates treat as one of their own | CareerConfig |  | OK |
| `rewards.navy_ship_price.midshipman` | `1.0` | 0.0 – 1.0 | Price factor for the navy rank midshipman | CareerConfig |  | OK |
| `rewards.midshipman_items` | `[]` |  | Item ids given on reaching the navy rank midshipman (repeat an id for more than one) | CareerConfig, CareerRewards |  | OK |
| `rewards.navy_ship_price.lieutenant` | `1.0` | 0.0 – 1.0 | Price factor for the navy rank lieutenant | CareerConfig |  | OK |
| `rewards.lieutenant_items` | `[pirates_n_ships:officer_hat, pirates_n_ships:saber, pirates_n_ships:officers_coat, pirates_n_ships:officers_breeches, pirates_n_ships:officers_boots]` |  | Item ids given on reaching the navy rank lieutenant (repeat an id for more than one) | CareerConfig, CareerRewards |  | **DESIGN**: Old config files keep `[officer_hat, saber]` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: ART9/ART13 added the coat, breeches and boots to the lieutenant's gifts (design.md l.271). |
| `rewards.navy_ship_price.captain` | `0.7` | 0.0 – 1.0 | Price factor for the navy rank captain | CareerConfig |  | OK |
| `rewards.captain_items` | `[]` |  | Item ids given on reaching the navy rank captain (repeat an id for more than one) | CareerConfig, CareerRewards |  | OK |
| `rewards.navy_ship_price.commodore` | `0.5` | 0.0 – 1.0 | Price factor for the navy rank commodore | CareerConfig |  | OK |
| `rewards.commodore_items` | `[]` |  | Item ids given on reaching the navy rank commodore (repeat an id for more than one) | CareerConfig, CareerRewards |  | OK |
| `rewards.navy_ship_price.admiral` | `0.4` | 0.0 – 1.0 | Price factor for the navy rank admiral | CareerConfig |  | OK |
| `rewards.admiral_items` | `[]` |  | Item ids given on reaching the navy rank admiral (repeat an id for more than one) | CareerConfig, CareerRewards |  | OK |
| `rewards.buccaneer_items` | `[]` |  | Item ids given on reaching the infamy rank buccaneer (repeat an id for more than one) | CareerConfig, CareerRewards |  | OK |
| `rewards.dread_captain_items` | `[]` |  | Item ids given on reaching the infamy rank dread_captain (repeat an id for more than one) | CareerConfig, CareerRewards |  | OK |
| `rewards.pirate_lord_items` | `[]` |  | Item ids given on reaching the infamy rank pirate_lord (repeat an id for more than one) | CareerConfig, CareerRewards |  | OK |
| `ship_grants.enabled` | `true` |  | Reaching navy_rank or infamy_rank hands out a ship commission (once per player and ladder), and commissions are redeemed. Off = no commissions are handed out and held ones are refused (and kept) | CareerConfig | false | OK |
| `ship_grants.navy_rank` | `CAPTAIN` |  | The navy rank whose first reaching grants a navy ship | CareerConfig |  | OK |
| `ship_grants.infamy_rank` | `DREAD_CAPTAIN` |  | The infamy rank whose first reaching grants a pirate ship | CareerConfig |  | OK |
| `ship_grants.navy_template` | `pirates_n_ships:navy_sloop_armed` |  | Ship template (pirates_n_ships/ship_template id) of the navy grant | CareerConfig |  | OK |
| `ship_grants.pirate_template` | `pirates_n_ships:pirate_sloop_armed` |  | Ship template (pirates_n_ships/ship_template id) of the pirate grant | CareerConfig |  | OK |

### `quests` (server): Quests offered at every port's harbor desk: hunts, monsters, prisoners, deliveries and treasure

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Offer quests. Off = no Quests tab, no new offers, accepting is refused and active quests stand still | QuestConfig | false | OK |
| `offers_per_port` | `3` | 0 – 12 | Open quest offers a port keeps; missing ones are made at the first look on a new day | QuestConfig |  | OK |
| `offer_days` | `2` | 1 – 30 | In-game days an offer stays open before the port replaces it | QuestConfig |  | OK |
| `max_active` | `3` | 1 – 20 | Quests a player can have active at once | QuestCommands, QuestConfig |  | OK |
| `deadline_days` | `5` | 1 – 100 | In-game days after accepting until a quest fails (a delivery's contract has the same deadline) | QuestConfig |  | OK |
| `reward_scale` | `1.0` | 0.0 – 100.0 | Multiplier on every quest's doubloon reward | QuestConfig |  | OK |
| `hunt_count_min` | `3` | 1 – 100 | Fewest pirates or navy sailors a hunt asks for | QuestConfig |  | OK |
| `hunt_count_max` | `8` | 1 – 100 | Most pirates or navy sailors a hunt asks for | QuestConfig |  | OK |
| `monster_count_min` | `2` | 1 – 100 | Fewest sharks a monster quest asks for | QuestConfig |  | OK |
| `monster_count_max` | `4` | 1 – 100 | Most sharks a monster quest asks for | QuestConfig |  | OK |
| `turn_in_count_max` | `2` | 1 – 20 | Most shackled pirates a turn-in quest asks for (at least 1) | QuestConfig |  | OK |
| `kraken_chance` | `0.15` | 0.0 – 1.0 | Chance that a monster quest asks for the kraken instead of sharks (always the kraken in a cold climate) | QuestConfig |  | OK |
| `treasure_quest_enabled` | `true` |  | Offer treasure hunts (they hand out a bound treasure map; also needs world.treasure_maps.enabled) | QuestConfig |  | OK |
| `captain_hunt_radius` | `3000` | 0 – 100000 | Blocks from a village or navy outpost within which the nearest pirate island's living captain can be hunted (hunt_captain; none in reach = no captain hunt offered) | QuestConfig | 8 | OK |
| `poll_ticks` | `20` | 1 – 1200 | How often (ticks) deliveries, treasure hunts, escorts and deadlines of online players are checked | QuestTracker |  | OK |
| `sea_quests` | `true` |  | Offer quests at sea: escorts (villages, navy outposts), convoy raids and patrol hunts (pirate islands), ship hunts (navy outposts). Off = none offered; quests already accepted still finish | QuestConfig | false | OK |
| `sea_count_min` | `1` | 1 – 50 | Fewest ships a convoy raid, patrol hunt or ship hunt asks for | QuestConfig |  | OK |
| `sea_count_max` | `3` | 1 – 50 | Most ships a convoy raid, patrol hunt or ship hunt asks for | QuestConfig |  | OK |
| `escort_radius` | `96` | 8 – 1024 | Blocks from an escorted convoy within which you count as sailing with it | QuestConfig, Quests, SeaQuests |  | OK |
| `escort_fraction` | `0.5` | 0.0 – 1.0 | Share of the convoy's legs you must sail within escort_radius of it for the escort to count when it arrives | QuestConfig, Quests |  | OK |
| `escort_max_distance` | `2500` | 100 – 100000 | Farthest destination (blocks, straight line) a port sends an escorted convoy to | QuestConfig |  | OK |
| `escort_chart_timeout_ticks` | `2400` | 20 – 72000 | Ticks an accepted escort waits for the harbor master to chart its sea lane (searched off the tick, a few milliseconds per voyage check) before it is called off | SeaQuests |  | OK |
| `rewards.per_pirate` | `30` | 0 – 100000 | Per pirate of a hunt | QuestConfig |  | OK |
| `rewards.per_navy` | `35` | 0 – 100000 | Per navy sailor of a hunt | QuestConfig |  | OK |
| `rewards.per_prisoner` | `50` | 0 – 100000 | Per pirate turned in (on top of the officer's turn-in pay) | QuestConfig |  | OK |
| `rewards.per_shark` | `25` | 0 – 100000 | Per shark | QuestConfig |  | OK |
| `rewards.kraken` | `500` | 0 – 1000000 | For the kraken | QuestConfig |  | OK |
| `rewards.treasure` | `100` | 0 – 1000000 | For finding a treasure (the chest's loot comes on top) | QuestConfig |  | OK |
| `rewards.captain` | `400` | 0 – 1000000 | For bringing down a named pirate captain (the navy's bounty on him comes on top) | QuestConfig |  | OK |
| `rewards.deliver_multiplier` | `1.5` | 0.0 – 100.0 | A delivery quest pays this times what an ordinary contract for the same cargo would (paid on delivery, no deposit) | QuestConfig |  | OK |
| `rewards.per_convoy` | `90` | 0 – 100000 | Per merchant convoy plundered for a convoy raid | QuestConfig |  | OK |
| `rewards.per_patrol` | `150` | 0 – 100000 | Per navy patrol sunk or captured for a patrol hunt | QuestConfig |  | OK |
| `rewards.per_ship` | `120` | 0 – 100000 | Per pirate ship sunk or captured for a ship hunt | QuestConfig |  | OK |
| `rewards.escort` | `120` | 0 – 1000000 | Base reward of an escort | QuestConfig |  | OK |
| `rewards.escort_per_1000` | `60` | 0 – 1000000 | Escort reward per 1000 blocks between the two ports (straight line) | QuestConfig |  | OK |
| `types.seafarer_village` | `[deliver, kill_monster, hunt_pirates, find_treasure, hunt_captain, escort]` |  | Quest types offered at seafarer villages | QuestConfig |  | **DESIGN**: Old config files keep `without escort` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: QST2 added the sea quests (design.md l.588); an old list never offers them. |
| `types.navy_outpost` | `[hunt_pirates, turn_in, kill_monster, deliver, hunt_captain, escort, hunt_ship]` |  | Quest types offered at navy outposts | QuestConfig |  | **DESIGN**: Old config files keep `without escort, hunt_ship` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: QST2 added the sea quests (design.md l.588). |
| `types.pirate_island` | `[hunt_navy, find_treasure, deliver, kill_monster, plunder_convoy, hunt_patrol]` |  | Quest types offered at pirate islands | QuestConfig |  | **DESIGN**: Old config files keep `without plunder_convoy, hunt_patrol` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: QST2 added the sea quests (design.md l.588). |

### `world` (server): Structure placement and mob spawn weights

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `structures.pirate_island.spacing` | `64` | 2 – 4096 | Average distance in chunks between two pirate islands. Datapack value: this default is written into the structure set pirates_n_ships:pirate_islands; change it with a datapack, not here | IslandData, WorldConfig |  | **DEAD**: Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Old config files keep `40` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). Old value: now 64; a datapack value anyway (see its DEAD verdict). |
| `structures.pirate_island.frequency` | `0.8` | 0.0 – 1.0 | Chance that pirate islands generate at a possible location (0 = never, 1 = always); read at placement | IslandData, WorldConfig |  | OK |
| `structures.pirate_island.enabled` | `true` |  | Pirate island camps generate in new chunks (off = no new camps; existing ones stay) | WorldConfig | false | OK |
| `structures.pirate_island.separation` | `24` | 1 – 4095 | Minimum distance in chunks between two pirate islands. Datapack value: this default is written into the structure set pirates_n_ships:pirate_islands; change it with a datapack, not here | IslandData, WorldConfig |  | **DEAD**: Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). |
| `structures.pirate_island.shore_probe_blocks` | `24` | 4 – 64 | How far (blocks) a camp site looks in each direction for the sea; the direction with the most water gets the jetty | WorldConfig |  | OK |
| `structures.pirate_island.max_distance_from_water` | `12` | 1 – 64 | A camp site farther than this (blocks) from sea water is skipped | WorldConfig |  | OK |
| `structures.pirate_island.max_shore_height` | `4` | 0 – 32 | A camp site whose ground at the camp is more than this many blocks above sea level is skipped (the camp's sand is laid one block above the sea) | WorldConfig |  | OK |
| `structures.pirate_island.buried_treasure` | `true` |  | The treasure spot of a new camp gets a buried chest (loot table pirates_n_ships:chests/buried_treasure), recorded on the port as a treasure site; off = the marker stays plain sand | PortService |  | OK |
| `structures.pirate_island.max_pirates` | `8` | 0 – 64 | Pirates stop spawning naturally in a camp once this many pirates are within 32 blocks of the spawn spot | PirateIslandSpawns | 0, 64 | OK |
| `structures.seafarer_village.spacing` | `36` | 2 – 4096 | Average distance in chunks between two seafarer villages. Datapack value: this default is written into the structure set pirates_n_ships:seafarer_villages; change it with a datapack, not here | VillageData, WorldConfig |  | **DEAD**: Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). |
| `structures.seafarer_village.frequency` | `1.0` | 0.0 – 1.0 | Chance that seafarer villages generate at a possible location (0 = never, 1 = always); read at placement | VillageData, WorldConfig |  | OK |
| `structures.seafarer_village.enabled` | `true` |  | Seafarer villages generate in new chunks (off = no new villages; existing ones stay) | WorldConfig | false | OK |
| `structures.seafarer_village.separation` | `12` | 1 – 4095 | Minimum distance in chunks between two seafarer villages. Datapack value: this default is written into the structure set pirates_n_ships:seafarer_villages; change it with a datapack, not here | VillageData, WorldConfig |  | **DEAD**: Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). |
| `structures.seafarer_village.shore_probe_blocks` | `24` | 4 – 64 | How far (blocks) a village site looks in each direction for the sea; the direction with the most water gets the pier | WorldConfig |  | OK |
| `structures.seafarer_village.max_distance_from_water` | `12` | 1 – 64 | A village site farther than this (blocks) from sea water is skipped | WorldConfig |  | OK |
| `structures.seafarer_village.max_shore_height` | `4` | 0 – 32 | A village site whose ground at the dock head is more than this many blocks above sea level is skipped (the quay is built one block above the sea) | WorldConfig |  | OK |
| `structures.navy_outpost.spacing` | `48` | 2 – 4096 | Average distance in chunks between two navy outposts. Datapack value: this default is written into the structure set pirates_n_ships:navy_outposts; change it with a datapack, not here | OutpostData, WorldConfig |  | **DEAD**: Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). |
| `structures.navy_outpost.frequency` | `0.8` | 0.0 – 1.0 | Chance that navy outposts generate at a possible location (0 = never, 1 = always); read at placement | OutpostData, WorldConfig |  | OK |
| `structures.navy_outpost.enabled` | `true` |  | Navy outposts generate in new chunks (off = no new outposts; existing ones stay) | WorldConfig | false | OK |
| `structures.navy_outpost.separation` | `20` | 1 – 4095 | Minimum distance in chunks between two navy outposts. Datapack value: this default is written into the structure set pirates_n_ships:navy_outposts; change it with a datapack, not here | OutpostData, WorldConfig |  | **DEAD**: Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). |
| `structures.navy_outpost.shore_probe_blocks` | `24` | 4 – 64 | How far (blocks) an outpost site looks in each direction for the sea; the direction with the most water gets the quay | WorldConfig |  | OK |
| `structures.navy_outpost.max_distance_from_water` | `12` | 1 – 64 | An outpost site farther than this (blocks) from sea water is skipped | WorldConfig |  | OK |
| `structures.navy_outpost.max_shore_height` | `4` | 0 – 32 | An outpost site whose ground at the fort gate is more than this many blocks above sea level is skipped (the gate's paving is laid one block above the sea) | WorldConfig |  | OK |
| `structures.navy_outpost.garrison_soldiers` | `6` | 0 – 16 | Navy soldiers placed at their posts (gate, walls, the building) when a new outpost generates; they stand guard and never despawn or respawn. Capped by the posts the outpost's layout has (at least 6). 0 = none | Garrison | 9 | OK |
| `structures.navy_outpost.garrison_officers` | `1` | 0 – 3 | Navy officers placed in the fort's court beside the harbor master's office when a new outpost generates (they take turn-ins, fines and ransoms); never despawn or respawn. 0 = none | Garrison |  | OK |
| `structures.wreck.spacing` | `24` | 2 – 4096 | Average distance in chunks between two wrecks. Datapack value: this default is written into the structure set pirates_n_ships:wrecks; change it with a datapack, not here | WreckData, WreckStructure |  | **DEAD**: Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). |
| `structures.wreck.frequency` | `1.0` | 0.0 – 1.0 | Chance that wrecks generate at a possible location (0 = never, 1 = always); read at placement | WreckData, WreckStructure |  | OK |
| `structures.wreck.enabled` | `true` |  | Wrecks generate on the ocean floor of new chunks (off = no new wrecks; existing ones stay) | WreckStructure | false | OK |
| `structures.wreck.separation` | `8` | 1 – 4095 | Minimum distance in chunks between two wrecks. Datapack value: this default is written into the structure set pirates_n_ships:wrecks; change it with a datapack, not here | WreckData |  | **DEAD**: Turn them into constants for datagen and drop the keys (a datapack changes the structure set); or keep them and accept a key that only documents the datapack default. Only `.defaultValue()` is read, by the datagen data classes; the live value never reaches the game, as the comment admits (design.md l.549-551). |
| `treasure_maps.enabled` | `true` |  | Blank treasure maps bind to a treasure when used and fences sell them (off = blank maps stay blank, fences stop selling them; bound maps keep their picture) | Quests, TreasureMapService, TreasureMaps | false | OK |
| `treasure_maps.search_radius` | `2000` | 64 – 30000000 | How far (blocks, to the island's centre) a blank map looks for a pirate island with an unfound treasure | Quests, TreasureMapService |  | OK |
| `ports.names` | `true` |  | Ports show their proper names wherever a port is named (off = the readable port id as before; the names stay stored and commands accept both). Chat and commands follow a change at once, screens after the next join | PortNameBook | false | OK (NAME3) |
| `spawn_weights.pirate` | `10` | 0 – 1000 | Spawn weight of pirates on pirate islands (0 = never spawn naturally) | PirateIslandSpawns |  | **NAME**: Fix the comment (or turn it into a boolean): "Pirates spawn naturally in camps (0 = never). The spawn weight itself, 10, is in the structure (a datapack value)". PirateIslandSpawns only tests it for > 0; the weight the game uses is the 10 that datagen writes into the pirate_island structure's spawn overrides, so 20 spawns no more pirates than 10. |
| `spawn_weights.sailor` | `10` | 0 – 1000 | Spawn weight of sailors in seafarer villages (0 = never spawn naturally) | — |  | **DEAD**: Remove the three keys (sailors, harbor masters and garrisons are placed by the structures, nothing spawns them naturally). Read nowhere; design.md l.716 (§17 World) lists "mob spawn weights", but only world.spawn_weights.pirate and mobs.shark.spawn_weight are read. |
| `spawn_weights.navy_soldier` | `10` | 0 – 1000 | Spawn weight of navy soldiers at navy outposts (0 = never spawn naturally) | — |  | **DEAD**: Remove the three keys (sailors, harbor masters and garrisons are placed by the structures, nothing spawns them naturally). Read nowhere; design.md l.716 (§17 World) lists "mob spawn weights", but only world.spawn_weights.pirate and mobs.shark.spawn_weight are read. |
| `spawn_weights.navy_officer` | `2` | 0 – 1000 | Spawn weight of navy officers at navy outposts (0 = never spawn naturally) | — |  | **DEAD**: Remove the three keys (sailors, harbor masters and garrisons are placed by the structures, nothing spawns them naturally). Read nowhere; design.md l.716 (§17 World) lists "mob spawn weights", but only world.spawn_weights.pirate and mobs.shark.spawn_weight are read. |
| `spawn_weights.shark` | `4` | 0 – 1000 | Spawn weight of sharks in deep ocean biomes, compared to vanilla ocean mobs such as cod (15) and dolphins (2) | — (only SettingsGameTests) |  | **DUP**: Remove it; keep `mobs.shark.spawn_weight` as the only shark weight and lower that one from 8 to 3. Read nowhere; MobContent spawns sharks with mobs.shark.spawn_weight (8), which progress.md "Defaults to review" calls likely too many (squid 1, dolphin 1-2 in the same pool). |

### `world_simulation` (server): NPC voyages, convoys, patrols and raids between the ports

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Simulate NPC ships travelling between ports, faction tension and pirate raids. Off = no NPC voyages or raids | CaptainVoyageConfig, FactionConfig, MaterializeConfig, Materializer, NavyConfig, RaidConfig, VoyageScheduler | false, true | OK |
| `max_simultaneous_voyages` | `32` | 0 – 1000 | Most NPC voyages (convoys and patrols) that can exist at the same time in the whole world | CaptainVoyages, VoyageScheduler | 1 | OK |
| `materialize_radius` | `192` | 32 – 2048 | Distance in blocks from a player at which an NPC voyage becomes a real ship with crew | MaterializeConfig |  | OK |
| `convoys_per_day` | `2.0` | 0.0 – 100.0 | Average number of trade convoys that set out per in-game day across the world | ConvoyPlanner | 0.0, 100.0 | OK |
| `patrols_per_day` | `2.0` | 0.0 – 100.0 | Average number of navy patrols that set out per in-game day across the world | PatrolPlanner |  | OK |
| `retaliation_enabled` | `true` |  | High tension between factions makes them raid each other's settlements and convoys more often | RaidConfig | false | OK |
| `raids.chance_growth_per_minute` | `5.0E-4` | 0.0 – 1.0 | How much the raid chance per minute rises for every minute (1200 ticks) a player spends at a navy settlement | RaidConfig | 0.001, 1.0 | OK |
| `raids.chance_cap` | `0.02` | 0.0 – 1.0 | Highest raid chance per minute the growth can reach (0 = no raids) | RaidConfig | 1.0 | OK |
| `raids.cooldown_days` | `5.0` | 0.0 – 1000.0 | In-game days after a raid before the same settlement can be raided again | RaidConfig |  | OK |
| `factions.enabled` | `true` |  | Keep a faction state that deeds and world events shift and that decays every day. Off = the state stays as it is | FactionConfig | false | OK |
| `factions.decay_per_day` | `0.05` | 0.0 – 1.0 | How far aggression (toward 0.3) and tension (toward 0) move back each in-game day | Factions | 0.1 | OK |
| `factions.event_scale` | `1.0` | 0.0 – 10.0 | Strength of world events (NPC kills, convoys, raids, lost patrols) on the faction state. 0 = ignored | Factions |  | OK |
| `factions.deed_scale` | `1.0` | 0.0 – 10.0 | Strength of the players' deeds (plunder, kills, turn-ins) on the faction state. 0 = ignored | Factions |  | OK |
| `factions.max_wealth` | `1000000` | 0 – 2147483647 | Most wealth (abstract doubloons) a faction can hold | Factions |  | OK |
| `lanes.cell_blocks` | `32` | 8 – 256 | Side of one sea grid cell in blocks (smaller = finer lanes, slower to find) | CaptainVoyages, Hunting, Lanes |  | OK |
| `lanes.land_margin_cost` | `4.0` | 1.0 – 100.0 | Cost multiplier for sea cells next to land, so lanes keep off the coasts (1 = hug the coast) | VoyageConfig |  | OK |
| `lanes.cross_shores` | `true` | – | Lanes may cross beach cells of the biome map (often shallow water off a coast, and a port's bay can be ringed by them) at shore_cost; off = only ocean biomes are sea | Lanes |  | OK (LANE1) |
| `lanes.shore_cost` | `6.0` | 1.0 – 100.0 | Cost multiplier for crossing a beach cell, so lanes use them only to leave a bay or pass a strait | VoyageConfig |  | OK (LANE1) |
| `lanes.max_cells` | `200000` | 100 – 1000000 | Most grid cells one lane search may expand, however far apart the ports are | VoyageConfig |  | OK (LANE1: was 20000, a fixed budget) |
| `lanes.min_cells` | `20000` | 100 – 1000000 | Grid cells every lane search may expand, however close the ports are | VoyageConfig |  | OK (LANE1) |
| `lanes.cells_per_cell` | `400` | 0 – 100000 | Lane search budget per grid cell of straight distance between the ports (between min_cells and max_cells; 0 = always max_cells) | VoyageConfig |  | OK (LANE1) |
| `lanes.millis_per_check` | `5` | 1 – 1000 | Server time in milliseconds a background lane search may use per voyage check; a longer search continues in the next check | Lanes |  | OK |
| `lanes.endpoint_radius_cells` | `8` | 0 – 64 | How far (in cells) from a port's berth the lane may start when the berth's own cell is not sea | VoyageConfig |  | OK |
| `lanes.retry_failed_ticks` | `24000` | 0 – 10000000 | Ticks before a port pair without a lane is searched again (24000 = one day) | Lanes |  | OK |
| `lanes.version` | `2` | 0 – 1000000 | Raise to throw away every cached lane (e.g. after changing the grid settings) | Lanes |  | **TEST**: Keep the value; optionally replace it later with an operator command (`/pirates world lanes reset`). A cache-buster for saved lanes, not a gameplay setting: raising it once throws every cached lane away. |
| `lanes.risk_radius` | `300` | 0 – 10000 | Pirate islands within this many blocks of a lane make delivery contracts on it riskier (and pay more) | Lanes |  | OK |
| `lanes.risk_per_pirate_island` | `0.4` | 0.0 – 1.0 | Contract risk added by each pirate island near the lane (combined as 1 - (1 - r)^n) | Lanes |  | OK |
| `lanes.base_risk` | `0.1` | 0.0 – 1.0 | Contract risk of a lane with no pirate island near it | Lanes |  | OK |
| `voyages.tick_interval_ticks` | `20` | 1 – 1200 | Ticks between two voyage updates (movement, arrivals, spawn rolls) | CaptainVoyageModule, Hunting, Materializer, VoyageScheduler | 20 | OK |
| `voyages.speed_blocks_per_second` | `4.0` | 0.1 – 50.0 | Speed of an abstract voyage in blocks per second, before the wind factor | VoyageConfig | 4.0 | OK |
| `voyages.wind_affects_speed` | `true` |  | Voyages sail faster downwind and slower into the wind | VoyageConfig | false | OK |
| `voyages.downwind_factor` | `1.2` | 0.1 – 5.0 | Speed factor with the wind straight astern | VoyageConfig |  | OK |
| `voyages.headwind_factor` | `0.6` | 0.1 – 5.0 | Speed factor with the wind straight ahead (beam reach is halfway between) | VoyageConfig |  | OK |
| `voyages.convoy_cargo_units` | `64` | 1 – 10000 | Units of each good a convoy buys at its origin and sells at its destination | ConvoyPlanner, MaterializeCommands, Voyages |  | OK |
| `voyages.convoy_min_distance` | `200` | 0 – 100000 | Ports closer than this (blocks between centres) never send convoys to each other | ConvoyPlanner |  | OK |
| `voyages.convoy_max_distance` | `3000` | 0 – 100000 | Ports farther apart than this (blocks between centres) never send convoys to each other | ConvoyPlanner |  | OK |
| `voyages.merchant_templates` | `[pirates_n_ships:starter_sloop, pirates_n_ships:starter_sloop_basic, pirates_n_ships:brig]` |  | Ship templates merchant convoys sail (one is picked at random) | ConvoyPlanner, MaterializeCommands |  | OK |
| `voyages.navy_templates` | `[pirates_n_ships:navy_sloop_armed, pirates_n_ships:navy_brig_armed]` |  | Ship templates navy patrols sail (one is picked at random) | MaterializeCommands, PatrolPlanner |  | **DESIGN**: Old config files keep `[starter_sloop, starter_sloop_basic]` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: WS4c made the armed navy sloop the patrol ship (design.md l.586); an old list sends unarmed sloops on patrol. |
| `voyages.pirate_templates` | `[pirates_n_ships:pirate_sloop_armed, pirates_n_ships:pirate_brig_armed]` |  | Ship templates pirate raiders sail (one is picked at random) | CaptainVoyages, MaterializeCommands, RaidPlanner |  | **DESIGN**: Old config files keep `[starter_sloop, starter_sloop_basic]` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: WS4c made the armed pirate sloop the raider (design.md l.586). |
| `materialize.enabled` | `true` |  | Turn NPC voyages near players into real ships. Off = voyages stay abstract records (ships already at sea stay) | MaterializeConfig |  | OK |
| `materialize.max_materialized` | `4` | 0 – 64 | Most NPC voyages that are real ships at the same time in the whole world | Materializer | VoyageShips.count() + 1 | OK |
| `materialize.linger_ticks` | `200` | 20 – 72000 | Ticks an NPC ship stays real after the last player left its surroundings (materialize_radius + linger_margin) | Materializer |  | OK |
| `materialize.linger_margin` | `64` | 0 – 1024 | Blocks beyond materialize_radius within which a player still keeps an NPC ship real | Materializer, RaidPlanner |  | OK |
| `materialize.ticket_ticks` | `600` | 20 – 72000 | Ticks the chunks at a voyage's spawn point are kept loaded while it waits to appear | VoyageChunks |  | OK |
| `materialize.retry_ticks` | `600` | 20 – 72000 | Ticks before a voyage that could not appear (land, no water, obstructed) or got stuck is tried again | Materializer |  | OK |
| `materialize.sunk_flood_fraction` | `0.8` | 0.05 – 1.0 | Part of the hull's inside volume under water at which an NPC ship counts as sunk | MaterializeConfig |  | OK |
| `materialize.submerged_ticks` | `100` | 1 – 72000 | Ticks the whole ship must stay under the sea surface to count as sunk | MaterializeConfig |  | OK |
| `materialize.capture_hold_ticks` | `100` | 1 – 72000 | Ticks a player must stay aboard an NPC ship whose fighters are all dead to capture it | MaterializeConfig | 20 | OK |
| `materialize.shooter_memory_ticks` | `1200` | 1 – 72000 | Ticks a player's cannon hit still counts as the cause when the ship sinks | MaterializeConfig |  | OK |
| `materialize.crew_per_ship` | `3` | 0 – 16 | Deckhands on an NPC ship besides its helmsman (they work the sails through the job board) | Materializer |  | OK |
| `materialize.man_lookout` | `true` |  | One of an NPC ship's deckhands keeps watch in its crow's nest (if it has one), so the nest counts as manned (a navy observer's wider false-colours watch). Off = nobody goes up; the deckhand works the sails | VoyageCrew | false | OK |
| `materialize.fighters_merchant` | `2` | 0 – 16 | Armed sailors guarding a merchant ship | MaterializeConfig |  | OK |
| `materialize.fighters_navy` | `4` | 0 – 16 | Navy soldiers on a navy ship (the first is an officer) | MaterializeConfig |  | OK |
| `materialize.fighters_pirate` | `4` | 0 – 16 | Pirates on a pirate ship | MaterializeConfig |  | OK |
| `materialize.cannon_rounds` | `12` | 0 – 256 | Rounds (one gunpowder and one cannonball each) per cannon put in the shot locker of a navy or pirate ship when it appears (0 = none; merchants carry none) | VoyageGuns | 5 | OK |
| `materialize.chain_shot_rounds` | `2` | 0 – 64 | Chain shot (with one gunpowder each) put in every stocked shot locker of a navy or pirate ship when it appears (0 = none) | VoyageGuns | 0, 3 | OK |
| `materialize.grapeshot_rounds` | `2` | 0 – 64 | Grapeshot (with one gunpowder each) put in every stocked shot locker of a navy or pirate ship when it appears (0 = none) | VoyageGuns | 0, 1 | OK |
| `materialize.guns_start_loaded` | `true` |  | The cannons of a navy or pirate ship appear loaded, so its first shot needs no reload | VoyageGuns |  | OK |
| `materialize.restore_health` | `true` |  | A ship that appears again is flooded back to the health its voyage kept (1 - flooded part when it last turned into a record), lowest compartments first. Off = it always appears dry | Materializer | false | OK |
| `materialize.cargo_is_plunder` | `true` |  | Goods aboard an NPC ship carry the plunder mark, so whatever a player takes from it sells as plunder | Materializer |  | OK |
| `navy.enabled` | `true` |  | Navy patrols set out and hunt. Off = no new patrols, and patrols at sea stop hunting and sail their route | NavyConfig | false | OK |
| `navy.hunt_radius` | `256` | 16 – 2048 | A patrol sights a player's ship within this many blocks (horizontally) and gives chase if it is hunted | NavyConfig |  | OK |
| `navy.hunt_bounty_minimum` | `50` | 0 – 1000000 | Doubloons of bounty on a ship's owner from which the navy hunts the ship whatever flag it flies (a ship is also hunted under the Jolly Roger, with its cover blown, or with its owner wanted at law.world.navy_hostility_threshold) | NavyConfig |  | OK |
| `navy.give_up_ticks` | `2400` | 20 – 720000 | A patrol that has not come within contact_distance of its quarry for this many ticks breaks off the chase | NavyConfig | 40 | OK |
| `navy.contact_distance` | `64` | 4 – 1024 | Blocks within which a patrol counts as in contact with its quarry (resets the give-up timer) | NavyConfig |  | OK |
| `navy.lose_distance` | `384` | 16 – 4096 | A quarry farther than this many blocks from the patrol is lost, and the patrol returns to its route | NavyConfig |  | OK |
| `navy.standoff_distance` | `20` | 4 – 256 | Distance in blocks a patrol keeps from its quarry: it circles it at this range | Hunting |  | OK |
| `navy.course_interval_ticks` | `40` | 5 – 1200 | Ticks between two course updates of a patrol ship chasing its quarry | Hunting |  | OK |
| `navy.course_refresh_distance` | `6` | 0 – 256 | A patrol ship's course is only given anew when its quarry moved at least this many blocks since the last one | Hunting |  | OK |
| `navy.patrol_radius` | `400` | 16 – 10000 | A lone outpost's patrol sails this many blocks out along the lane toward the nearest pirate island and back | PatrolPlanner |  | OK |
| `navy.max_outpost_distance` | `3000` | 0 – 100000 | A patrol sails to another navy outpost at most this far away (blocks between centres); else it sails out and back | PatrolPlanner |  | OK |
| `navy.surrender_linger_ticks` | `600` | 0 – 72000 | Ticks a patrol shadows a ship that struck its colours, guns silent, before it returns to its route | NavyConfig | 100 | OK |
| `navy.announce` | `true` |  | Tell a hunted ship's owner when a patrol gives chase, breaks off, or accepts the surrender | Hunting |  | OK |
| `raids.enabled` | `true` |  | Pirates raid navy settlements while players stay there. Off = no raids start (raids under way go on) | RaidConfig |  | OK |
| `raids.ships` | `1` | 1 – 2 | Pirate ships that sail in one raid | RaidPlanner |  | OK |
| `raids.approach_distance` | `320` | 32 – 2048 | Blocks out at sea from the settlement's berth at which the raiders appear | RaidPlanner |  | OK |
| `raids.landing_distance` | `24` | 4 – 128 | Within this many blocks of the berth the raiders anchor and their fighters go ashore | RaidLanding |  | OK |
| `raids.bell_ticks` | `600` | 0 – 12000 | Ticks the settlement's bells ring (every 100 ticks) after a raid is sighted (0 = no bells) | RaidAnnouncer |  | OK |
| `raids.raid_duration_ticks` | `6000` | 200 – 72000 | Ticks the raiders fight ashore before the survivors go back and the ships withdraw | RaidLanding |  | OK |
| `raids.target_villages` | `true` |  | Seafarer villages are raided too, not only navy outposts | RaidPlanner |  | OK |
| `raids.retaliation_factor` | `1.0` | 0.0 – 10.0 | With retaliation_enabled the raid chance growth is multiplied by 1 + this x the Navy-Pirates tension (0..1) | RaidConfig | 1.0 | OK |
| `raids.announce` | `true` |  | Tell the players at the settlement when raiders are sighted and how the raid ended | RaidAnnouncer |  | OK |
| `captain.enabled` | `true` |  | Pirate captains put to sea. Off = no new voyages; a captain at sea sails home and hunts nobody | CaptainVoyageConfig | false | OK |
| `captain.voyage_days` | `2` | 1 – 365 | Days between two chances of a captain at his post to put to sea | CaptainVoyageConfig | 1 | OK |
| `captain.voyage_chance` | `0.5` | 0.0 – 1.0 | Chance that a captain puts to sea when his day comes (every voyage_days days) | CaptainVoyageConfig | 1.0 | OK |
| `captain.cruise_distance` | `600` | 32 – 10000 | A captain sails this many blocks out (along the lane toward a village or outpost within this distance, else into open sea) and back to his island | CaptainVoyages |  | OK |
| `captain.hunt_radius` | `192` | 0 – 2048 | A captain at sea sights a hunted player's ship within this many blocks (0 = he hunts nobody) | CaptainVoyageConfig |  | OK |
| `captain.hunt_letter` | `true` |  | A captain hunts the ship of a player who holds a letter of marque | CaptainVoyageConfig |  | OK |
| `captain.hunt_min_proofs` | `1` | 0 – 64 | A captain hunts the ship of a player carrying at least this many bounty proofs (0 = proofs never draw him) | CaptainVoyageConfig |  | OK |
| `captain.lose_distance` | `320` | 16 – 4096 | A quarry farther than this many blocks from the captain is lost, and he returns to his course | CaptainVoyageConfig |  | OK |
| `captain.contact_distance` | `64` | 4 – 1024 | Blocks within which the captain counts as in contact with his quarry (resets the give-up timer) | CaptainVoyageConfig |  | OK |
| `captain.give_up_ticks` | `2400` | 20 – 720000 | A captain who has not come within contact_distance of his quarry for this many ticks breaks off the chase | CaptainVoyageConfig |  | OK |
| `captain.standoff_distance` | `16` | 4 – 256 | Distance in blocks the captain's ship keeps from its quarry: it circles it at this range with its guns firing | CaptainHunt |  | OK |
| `captain.surrender_linger_ticks` | `600` | 0 – 72000 | Ticks the captain's ship lies by a quarry that struck its colours, guns silent, before it sails on | CaptainVoyageConfig |  | OK |
| `captain.course_interval_ticks` | `40` | 5 – 1200 | Ticks between two course updates of a captain's ship chasing its quarry (and between calls to man the guns) | CaptainHunt |  | OK |
| `captain.course_refresh_distance` | `6` | 0 – 256 | A captain's chase course is only given anew when the quarry moved at least this many blocks since the last one | CaptainHunt |  | OK |
| `captain.announce` | `true` |  | Tell a hunted ship's owner when a captain gives chase or breaks off | CaptainHunt |  | OK |

### `mobs` (server): Pirates, sailors, navy soldiers and officers

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `pirates_hostile` | `true` |  | Pirates attack players on sight (off = they only fight back) | MobConfig | false | OK |
| `navy_hostile` | `true` |  | Navy soldiers and officers attack players and NPCs the law wants (wanted level from law.world.navy_hostility_threshold) | MobConfig |  | OK |
| `factions_fight` | `true` |  | Pirates and navy attack each other on sight | MobConfig |  | OK |
| `detection_range` | `16.0` | 1.0 – 64.0 | Blocks within which a mob notices a player it is hostile to (and keeps following a target) | HostileTargetGoal |  | OK |
| `faction_fight_range` | `12.0` | 1.0 – 64.0 | Blocks within which pirates and navy notice each other | HostileTargetGoal |  | OK |
| `grudge_ticks` | `600` | 0 – 24000 | Ticks a mob keeps fighting someone who hit it, even if it would not attack them on sight | HarborMaster, SeafarerMob, Shark |  | OK |
| `sailor_flee_range` | `8.0` | 1.0 – 32.0 | Blocks within which sailors run from pirates, monsters and anyone targeting them | HarborMaster, Sailor |  | OK |
| `pirate_skill` | `PIRATE` |  | Sword skill of pirates (parry chance, reaction time); scaled by melee.npc_skill_multiplier | MobConfig |  | OK |
| `officer_skill` | `NAVY_OFFICER` |  | Sword skill of navy officers; scaled by melee.npc_skill_multiplier | MobConfig |  | OK |
| `duelist_attack_pause_ticks` | `15` | 0 – 200 | Least ticks a duelist waits between two attacks (a random extra of up to the same again is added) | DuelistAttackGoal |  | OK |
| `duelist_guard_stamina` | `25.0` | 0.0 – 10000.0 | Duelists only raise their guard with at least this much stamina | DuelistAttackGoal |  | OK |
| `duelist_chase_speed` | `1.15` | 0.5 – 3.0 | Speed multiplier of a sword-fighting mob closing in on its target. At 1.0 a pirate is a little slower than a walking player; 1.15 is a little faster than walking and slower than sprinting | DuelistAttackGoal |  | OK |
| `duelist_approach_margin` | `1.0` | 0.0 – 4.0 | A duelist walks in until its target is this many blocks inside its weapon's reach, then stands its ground | DuelistAttackGoal |  | OK |
| `duelist_reach_margin` | `0.3` | 0.0 – 2.0 | A duelist starts an attack only when its target is expected to be this many blocks inside its reach at the first hit frame (it accounts for the target and itself moving during the wind-up) | DuelistAttackGoal |  | OK |
| `musket_reload_ticks` | `100` | 1 – 1200 | Ticks a navy soldier needs to reload its musket | NavySoldier |  | OK |
| `musket_aim_ticks` | `20` | 0 – 200 | Ticks of steady aim before a navy soldier fires | MobConfig |  | OK |
| `navy_infinite_ammo` | `true` |  | Navy soldiers never run out of shot (off = each carries musket_shots shots) | NavySoldier |  | OK |
| `musket_shots` | `12` | 0 – 1000 | Shots a navy soldier carries when navy_infinite_ammo is off | NavySoldier |  | OK |
| `musket_range` | `20.0` | 2.0 – 64.0 | Farthest distance a navy soldier shoots at | MobConfig |  | OK |
| `musket_min_range` | `4.0` | 0.0 – 32.0 | A navy soldier backs off from targets closer than this | MobConfig |  | OK |
| `shove_range` | `1.8` | 0.0 – 8.0 | A navy soldier shoves targets this close with the musket butt | MobConfig |  | OK |
| `shove_damage` | `2.0` | 0.0 – 100.0 | Damage of a musket-butt shove | MusketeerGoal |  | OK |
| `shove_knockback` | `1.0` | 0.0 – 10.0 | Knockback strength of a musket-butt shove | MusketeerGoal |  | OK |
| `shove_cooldown_ticks` | `30` | 1 – 1200 | Ticks between two shoves | MusketeerGoal |  | OK |
| `drops` | `true` |  | Mobs drop their loot table (doubloons and cutlasses from pirates, shot and gunpowder from navy) | Kraken, PirateCaptain, SeafarerMob, Shark |  | OK |
| `pirate.enabled` | `true` |  | This mob exists: off = it can't be spawned and existing ones disappear | MobConfig |  | OK |
| `pirate.peaceful` | `false` |  | Never attacks anyone and doesn't fight back | MobConfig |  | OK |
| `sailor.enabled` | `true` |  | This mob exists: off = it can't be spawned and existing ones disappear | MobConfig |  | OK |
| `navy_soldier.enabled` | `true` |  | This mob exists: off = it can't be spawned and existing ones disappear | MobConfig |  | OK |
| `navy_soldier.peaceful` | `false` |  | Never attacks anyone and doesn't fight back | MobConfig |  | OK |
| `navy_officer.enabled` | `true` |  | This mob exists: off = it can't be spawned and existing ones disappear | MobConfig |  | OK |
| `navy_officer.peaceful` | `false` |  | Never attacks anyone and doesn't fight back | MobConfig |  | OK |
| `captain.enabled` | `true` |  | This mob exists: off = it can't be spawned and existing ones disappear | MobConfig |  | OK |
| `captain.peaceful` | `false` |  | Never attacks anyone and doesn't fight back | MobConfig |  | OK |
| `harbor_master.enabled` | `true` |  | This mob exists: off = it can't be spawned and existing ones disappear | MobConfig |  | OK |
| `shark.enabled` | `true` |  | Sharks exist: off = they don't spawn, can't be spawned, and existing ones disappear | MobCommands, Shark | false | OK |
| `shark.peaceful` | `false` |  | Sharks never attack anyone and don't fight back | MobConfig | true | OK |
| `shark.spawn_weight` | `8` | 0 – 100 | Natural spawn weight among the water creatures of ocean biomes (squid 1-10, dolphin 1-2; 0 = no natural spawns). Read at server start | MobContent |  | OK |
| `shark.min_group` | `1` | 1 – 8 | Fewest sharks in a naturally spawned group. Read at server start | MobContent |  | OK |
| `shark.max_group` | `2` | 1 – 8 | Most sharks in a naturally spawned group (at least min_group). Read at server start | MobContent |  | OK |
| `shark.detection_range` | `16.0` | 1.0 – 64.0 | Blocks within which a shark notices prey in the water | MobConfig, SharkTargetGoal |  | OK |
| `shark.circle_ticks` | `60` | 0 – 1200 | Ticks a shark circles its prey before each charge | MobConfig | 1200 | OK |
| `shark.bite_cooldown_ticks` | `30` | 1 – 1200 | Least ticks between two bites | MobConfig |  | OK |
| `shark.give_up_ticks` | `200` | 20 – 12000 | A shark that hasn't bitten its prey for this many ticks loses interest (and ignores it as long again) | MobConfig, Shark |  | OK |
| `shark.frenzy_health_fraction` | `0.4` | 0.0 – 1.0 | Blood frenzy: prey below this fraction of its health is charged without circling (0 = never) | SharkHuntGoal |  | OK |
| `shark.bite_damage` | `6.0` | 0.0 – 100.0 | Damage of a bite | Shark |  | OK |
| `shark.knockback` | `0.5` | 0.0 – 5.0 | Knockback strength of a bite | Shark |  | OK |
| `kraken.peaceful` | `false` |  | The kraken never attacks: it stays in the deep and ignores ships and swimmers | Kraken, KrakenConfig | true | OK |
| `kraken.detection_range` | `32.0` | 4.0 – 128.0 | Blocks within which the kraken notices a ship or a swimmer and drifts toward it | Kraken |  | OK |
| `kraken.tentacle_reach` | `10.0` | 3.0 – 32.0 | Blocks from the kraken's body that a tentacle reaches | Kraken |  | OK |
| `kraken.grip_force` | `3.0` | 0.0 – 100.0 | Pull of one gripping tentacle on a ship, as an acceleration in m/s² of a ship up to hazards.max_ship_mass_effect (heavier ships feel the force of a ship at that mass; gravity is about 11). Downward, plus grip_side_fraction of it toward the kraken | Kraken |  | OK |
| `kraken.max_grips` | `4` | 0 – 8 | Most tentacles gripping a ship's hull at once (the others beat the masts, sweep the deck and grab swimmers) | Kraken |  | OK |
| `kraken.grip_side_fraction` | `0.35` | 0.0 – 2.0 | Sideways part of a grip's pull (toward the kraken) as a fraction of the downward part; makes a big ship list | Kraken |  | OK |
| `kraken.tentacle_health` | `40.0` | 1.0 – 1000.0 | Damage a tentacle takes before it is cut (it lets go and grows back) | Kraken |  | OK |
| `kraken.tentacle_regrow_ticks` | `600` | 1 – 72000 | Ticks a cut tentacle needs to grow back; it can't be hit or used meanwhile | Kraken | 40 | OK |
| `kraken.tentacle_body_share` | `0.5` | 0.0 – 1.0 | Fraction of the damage dealt to a tentacle that the kraken's body takes too | Kraken |  | OK |
| `kraken.eye_damage_multiplier` | `3.0` | 1.0 – 20.0 | Damage multiplier of hits on the kraken's eyes (its weak spots) | Kraken |  | OK |
| `kraken.mast_strike_interval_ticks` | `100` | 1 – 72000 | Ticks between two mast blocks broken by a tentacle (respects the mobGriefing game rule) | Kraken |  | OK |
| `kraken.swipe_interval_ticks` | `60` | 1 – 72000 | Ticks between two swipes of a tentacle across a ship's deck | Kraken |  | OK |
| `kraken.swipe_knockback` | `1.5` | 0.0 – 10.0 | Sideways speed (blocks/tick) a swipe gives to everyone on deck within 3 blocks of the tentacle | Kraken |  | OK |
| `kraken.swipe_damage` | `4.0` | 0.0 – 100.0 | Damage of a swipe | Kraken |  | OK |
| `kraken.grab_damage` | `2.0` | 0.0 – 100.0 | Damage per second to a swimmer held under water by a tentacle | Kraken |  | OK |
| `kraken.grab_hold_ticks` | `60` | 1 – 1200 | Ticks a tentacle holds a swimmer under before letting go | Kraken |  | OK |
| `kraken.attack_duration_ticks` | `1200` | 20 – 72000 | Ticks of attacking after which the kraken gives up and sinks away | KrakenConfig |  | OK |
| `kraken.retreat_health_fraction` | `0.3` | 0.0 – 1.0 | Below this fraction of its health the kraken lets go of everything and sinks away (0 = fights to the death) | KrakenConfig |  | OK |
| `kraken.night_multiplier` | `3.0` | 0.0 – 100.0 | Multiplier on hazards.kraken.chance_per_day at night | KrakenSpawner |  | OK |
| `kraken.thunder_multiplier` | `3.0` | 0.0 – 100.0 | Multiplier on hazards.kraken.chance_per_day during a thunderstorm (stacks with the night) | KrakenSpawner |  | OK |
| `kraken.min_separation` | `200` | 0 – 10000 | No kraken appears within this many blocks of another one | KrakenSpawner |  | OK |
| `kraken.min_water_depth` | `20` | 4 – 200 | Blocks of water a kraken needs under the surface where it appears | KrakenSpawner |  | OK |
| `captain.health` | `40.0` | 1.0 – 1024.0 | Health of a pirate captain (a pirate has 24), applied when he is placed | IslandCaptains |  | OK |
| `captain.skill` | `PIRATE_CAPTAIN` |  | Sword skill of pirate captains; scaled by melee.npc_skill_multiplier | MobConfig |  | OK |
| `captain.bounty` | `300` | 0 – 1000000 | Doubloons of the navy's standing bounty on every pirate captain (0 = none); listed on the notice boards | IslandCaptains |  | OK |
| `captain.respawn_days` | `5` | 0 – 365 | Days after a captain's death or capture until a successor takes over his post (once its chunk is loaded) | IslandCaptains |  | OK |
| `captain.duel_enabled` | `true` |  | A player can challenge a captain to a duel: sneak and use him with a sword in hand | CaptainConfig | false | OK |
| `captain.duel_truce_range` | `16.0` | 0.0 – 64.0 | Blocks around the captain within which his crew keep out of a duel (a truce with the challenger) | CaptainConfig |  | OK |
| `captain.duel_leave_range` | `32.0` | 2.0 – 128.0 | A challenger farther than this from the captain has left the duel; the truce ends | CaptainConfig |  | OK |
| `captain.duel_max_seconds` | `300` | 10 – 3600 | Longest duel; after it the truce ends and the crew fight again | CaptainConfig |  | OK |
| `captain.drop_map` | `true` |  | A slain captain drops a treasure map of his island (while it has unlooted treasure) | IslandCaptains |  | OK |
| `captain.clothing_drop_chance` | `0.35` | 0.0 – 1.0 | Chance that a slain captain drops each piece of his clothing (coat, breeches, boots), rolled per piece; his hat always drops | PirateCaptain | 0.0, 0.35, 1.0 | OK |
| `harbor_master.at_pirate_islands` | `true` |  | A harbor master (the fence) also stands behind the counter of each new pirate island camp | HarborMasters | false | OK |
| `harbor_master.respawn_days` | `3` | 0 – 365 | Days after a harbor master's death until a new one takes his post (once its chunk is loaded) | HarborMasters |  | OK |
| `harbor_master.return_distance` | `2.0` | 0.5 – 32.0 | Blocks from his post beyond which an idle harbor master walks back to it; read when he is loaded | HarborMaster |  | OK |
| `squad.enabled` | `true` |  | A navy outpost's officer leads a squad of its garrison on patrol around the fort (off = the garrison keeps its posts) | Squad | false | OK |
| `squad.size` | `3` | 0 – 8 | Soldiers of the garrison that follow the officer on patrol | Squad | 4 | OK |
| `squad.patrol_interval_minutes` | `10` | 1 – 240 | Minutes the squad stands at its posts between two patrols | SquadConfig |  | OK |
| `squad.post_pause_seconds` | `20` | 0 – 600 | Seconds the squad stops at each waypoint of its patrol route | SquadConfig | 1, 2 | OK |
| `squad.night_at_posts` | `true` |  | At night the squad returns to the garrison posts and starts no patrol (a patrol ordered by command keeps going) | Squad |  | OK |

### `melee` (server): Skill-based swordplay: parry, guard, stamina and stagger

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `skill_based_combat` | `true` |  | Skill-based sword fighting (slash, thrust, guard, parry, riposte, stamina). Off = mod swords fight like vanilla swords | MeleeConfig, MeleeService | false | OK |
| `directional_mode` | `false` |  | Directional attacks and parries (not implemented yet, no effect) | MeleeConfig |  | **DEAD**: Remove the key until milestone 21 implements directional parries (the §17 row stays as the plan). Its own comment says "not implemented yet, no effect"; MeleeParams carries it unused (design.md l.495, l.713). |
| `parry_window_ticks` | `7` | 1 – 40 | Ticks a parry stays open after the input; a hit landing in this window is deflected | DuelistAttackGoal, MeleeConfig, PalMeleeAnimations |  | OK |
| `latency_allowance_ticks` | `2` | 0 – 10 | A sword hit on a defender who could parry is held this many ticks, so a parry input delayed by lag still counts | MeleeConfig |  | OK |
| `parry_lockout_ticks` | `15` | 0 – 200 | Ticks after a failed parry before the next parry is possible | MeleeConfig |  | OK |
| `stamina_max` | `100.0` | 1.0 – 10000.0 | Full stamina | DuelistAttackGoal, MeleeAttachments, MeleeConfig, MeleeHud, MeleeStateSync |  | OK |
| `stamina_regen_per_tick` | `1.0` | 0.0 – 1000.0 | Stamina regained per tick when not attacking, guarding or parrying | MeleeConfig |  | OK |
| `stamina_regen_delay_ticks` | `20` | 0 – 1200 | Ticks after spending stamina before it regenerates | MeleeConfig |  | OK |
| `stamina_cost_multiplier` | `1.0` | 0.0 – 100.0 | Multiplies every stamina cost of every weapon (attacks, guard, failed parries) | MeleeConfig |  | OK |
| `riposte_window_ticks` | `20` | 0 – 200 | Ticks after a successful parry in which an attack becomes a riposte | MeleeConfig |  | OK |
| `riposte_damage_bonus` | `1.5` | 0.0 – 10.0 | Damage multiplier of a riposte (times the weapon's own riposte multiplier) | MeleeConfig |  | OK |
| `stagger_ticks` | `20` | 0 – 200 | Stagger from a thrust into recovery, or from a hit that beats the defender's poise | MeleeConfig |  | OK |
| `parry_stagger_ticks` | `25` | 0 – 200 | Stagger of an attacker whose hit was parried | MeleeConfig |  | OK |
| `guard_break_stagger_ticks` | `30` | 0 – 200 | Stagger when a blocked hit empties the defender's stamina | MeleeConfig |  | OK |
| `guard_absorbs_all` | `true` |  | A successful guard blocks the whole frontal hit (it still costs stamina). Off = a guard only reduces the damage by the weapon's guard reduction. A guard break always deals the reduced damage | MeleeConfig | false | OK |
| `guard_absorb_stamina_per_damage` | `1.0` | 0.0 – 20.0 | Extra stamina a blocked frontal hit costs per point of damage the guard absorbs, on top of the weapon's own block cost (0 = only the weapon's cost) | MeleeConfig |  | **DESIGN**: Old config files keep `1.5` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: P9b lowered it to 1.0 on the human's request. |
| `exhausted_poise_factor` | `0.5` | 0.0 – 1.0 | Poise multiplier at zero stamina (lower = staggered more easily, 0 = every hit staggers) | MeleeConfig |  | OK |
| `npc_skill_multiplier` | `1.0` | 0.0 – 10.0 | Scales NPC duelists' parry chance, reaction speed and feint frequency | DuelistAttackGoal, MeleeConfig | 0.5, 5.0 | OK |
| `feint_recovery_ticks` | `6` | 0 – 200 | Recovery after a feint (an attack aborted during its wind-up): no hit frames, no guard or parry, a new attack may start | MeleeConfig |  | OK |
| `npc_feints` | `true` |  | NPC duelists feint (start an attack and abort it when the opponent parries or guards), as often as their skill tier says | DuelistAttackGoal | false | OK |
| `stamina_sync_interval_ticks` | `5` | 1 – 100 | Shortest interval between two stamina updates sent to a player for the HUD (phase changes are sent at once) | MeleeStateSync |  | OK |
| `sounds.enabled` | `true` |  | Sword fights play sounds (swing, hit, armour, parry, guard, stagger, feint, drawing a sword). Heard by every nearby player | MeleeConfig | false | OK |
| `sounds.volume` | `1.0` | 0.0 – 4.0 | Multiplies the volume of every sword sound (above 1 they also carry further) | MeleeConfig, MeleeSoundPlayer | 0.5 | OK |
| `sounds.unsheathe_cooldown_ticks` | `10` | 0 – 200 | Shortest interval between two sword-drawing sounds of one player (scrolling the hotbar across two swords sounds once) | MeleeSoundPlayer |  | OK |

### `firearms` (server): Pistols and muskets: loading, firing, recoil and the lead ball

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Pistols and muskets can be loaded and fired. Off = they are inert items | FirearmAttackInput, FirearmItem, FirearmTrigger, GrapplingHookItem, NavySoldier | false | OK |
| `fire_on_attack` | `true` |  | The attack key (left mouse) fires a gun, aimed while use is held or from the hip with the full spread; letting go of use only lowers the gun. Off = letting go of an aim fires (the old scheme) and the attack key is left alone | FirearmAttackInput, FirearmItem, FirearmTrigger | false | OK |
| `consume_gunpowder` | `true` |  | Loading takes one gunpowder besides the lead shot. Off = lead shot only | FirearmLoads, FirearmService, MusketHookLoad |  | OK |
| `cooldown_ticks` | `10` | 0 – 200 | Ticks after a shot or a misfire before the gun can be used again | FirearmService |  | OK |
| `ball_lifetime_ticks` | `100` | 1 – 1200 | Ticks a lead ball flies before it vanishes | FirearmLoads |  | OK |
| `ball_gravity` | `0.02` | 0.0 – 1.0 | Downward acceleration of a lead ball in blocks per tick squared (arrows: 0.05, snowballs: 0.03) | LeadBallEntity |  | OK |
| `aim.aim_min_ticks` | `0` | 0 – 100 | With fire_on_attack off: ticks a loaded gun must be held before letting go fires it; a shorter hold puts it down still loaded. 0 = a click fires at once | FirearmItem | 5 | OK |
| `aim.aim_steady_ticks` | `20` | 0 – 1200 | Ticks of aiming after which the shot is steadied (aimed_spread_factor applies) | FirearmService, FirearmTrigger | 0, 20 | OK |
| `aim.aimed_spread_factor` | `0.5` | 0.0 – 1.0 | Spread of a steadied shot as a fraction of the gun's spread (1 = no benefit from aiming) | FirearmService, FirearmTrigger | 0.0 | OK |
| `aim.lower_on_sneak` | `true` |  | Sneaking while aiming lowers the gun without firing; it stays loaded and is not raised while sneaking. Off = a sneaking player aims (and, with fire_on_attack off, letting go always fires) | FirearmItem, FirearmLowering | false | OK |
| `pistol.damage` | `10.0` | 0.0 – 100.0 | Damage of a pistol ball hit | FirearmsConfig |  | OK |
| `pistol.muzzle_velocity` | `2.5` | 0.1 – 10.0 | Start speed of a pistol ball in blocks per tick | FirearmsConfig |  | OK |
| `pistol.spread_degrees` | `4.0` | 0.0 – 45.0 | Half angle of the cone a pistol ball leaves in, in degrees | FirearmsConfig | 30.0 | OK |
| `pistol.reload_ticks` | `60` | 1 – 1200 | Ticks the pistol has to be held to load it | FirearmsConfig |  | OK |
| `pistol.sound_pitch` | `1.0` | 0.5 – 2.0 | Pitch of the pistol shot sound | FirearmsConfig |  | OK |
| `pistol.recoil_pitch_degrees` | `3.0` | 0.0 – 45.0 | How far a pistol shot kicks the shooter's view up, in degrees | FirearmsConfig |  | OK |
| `pistol.recoil_push` | `0.08` | 0.0 – 2.0 | Backward push on the shooter per pistol shot, in blocks per tick | FirearmsConfig |  | OK |
| `musket.damage` | `14.0` | 0.0 – 100.0 | Damage of a musket ball hit | FirearmsConfig |  | OK |
| `musket.muzzle_velocity` | `4.0` | 0.1 – 10.0 | Start speed of a musket ball in blocks per tick | FirearmsConfig |  | OK |
| `musket.spread_degrees` | `1.0` | 0.0 – 45.0 | Half angle of the cone a musket ball leaves in, in degrees | FirearmsConfig |  | OK |
| `musket.reload_ticks` | `100` | 1 – 1200 | Ticks the musket has to be held to load it | FirearmsConfig |  | OK |
| `musket.sound_pitch` | `0.7` | 0.5 – 2.0 | Pitch of the musket shot sound (the pistol sound, lower) | FirearmsConfig |  | OK |
| `musket.recoil_pitch_degrees` | `5.0` | 0.0 – 45.0 | How far a musket shot kicks the shooter's view up, in degrees | FirearmsConfig |  | OK |
| `musket.recoil_push` | `0.15` | 0.0 – 2.0 | Backward push on the shooter per musket shot, in blocks per tick | FirearmsConfig |  | OK |

### `combat` (server): Firearms, cannons and damage

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `firearm_misfire_in_rain` | `true` |  | Pistols and muskets can misfire when fired in the rain | FirearmsConfig | true | OK |
| `rain_misfire_chance` | `0.25` | 0.0 – 1.0 | Chance that a pistol or musket misfires when fired in the rain (0 to 1) | FirearmsConfig | 1.0 | OK |
| `cannon_block_damage` | `true` |  | Cannonballs destroy the ship and world blocks they hit. Off = cannonballs only hurt entities and push ships | CannonConfig | false | OK |
| `damage_multipliers.firearm` | `1.0` | 0.0 – 10.0 | Multiplier on the damage pistols and muskets deal (1 = normal) | FirearmsConfig |  | OK |
| `damage_multipliers.cannon_entity` | `1.0` | 0.0 – 10.0 | Multiplier on the damage cannonballs deal to entities (1 = normal) | CannonConfig |  | OK |
| `damage_multipliers.cannon_block` | `1.0` | 0.0 – 10.0 | Multiplier on how many blocks a cannonball destroys (1 = normal) | CannonConfig |  | OK |

### `cannons` (server): Cannons: loading, aiming, firing, cannonballs

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Cannons can be loaded, aimed and fired. Off = they are inert blocks | CannonService, CannonStation, Gunnery, VoyageGuns | false | OK |
| `damage` | `20.0` | 0.0 – 1000.0 | Damage of a cannonball hitting an entity (times combat.damage_multipliers.cannon_entity) | CannonConfig |  | OK |
| `muzzle_velocity` | `3.0` | 0.1 – 10.0 | Start speed of a cannonball in blocks per tick, relative to the cannon | CannonConfig |  | OK |
| `gravity` | `0.03` | 0.0 – 1.0 | Downward acceleration of a cannonball in blocks per tick squared (arrows: 0.05) | CannonballEntity, Gunnery |  | OK |
| `reload_ticks` | `100` | 0 – 6000 | Ticks after a shot before powder can go into the cannon again | CannonService | 20 | OK |
| `elevation_steps` | `6` | 1 – 32 | Number of elevation steps from min_elevation_degrees to max_elevation_degrees | CannonBlockEntity, CannonConfig, CannonLoadingScene, CannonService, Gunnery |  | OK |
| `min_elevation_degrees` | `-5.0` | -45.0 – 45.0 | Lowest barrel elevation in degrees (negative = pointing down) | CannonConfig |  | OK |
| `max_elevation_degrees` | `20.0` | -45.0 – 60.0 | Highest barrel elevation in degrees | CannonConfig |  | OK |
| `blocks_per_hit` | `1` | 0 – 16 | Blocks one cannonball destroys: the hit block and the next ones along its path (times combat.damage_multipliers.cannon_block; combat.cannon_block_damage turns it off) | CannonConfig | 3 | OK |
| `recoil_impulse` | `8.0` | 0.0 – 1000.0 | Impulse a shot gives the firing ship against the barrel, in kpg*m/s (0 = no recoil) | CannonService |  | OK |
| `impact_impulse` | `8.0` | 0.0 – 1000.0 | Impulse a cannonball gives the ship it hits, along its flight, in kpg*m/s (0 = none) | CannonballEntity |  | OK |
| `ball_lifetime_ticks` | `200` | 1 – 2400 | Ticks a cannonball flies before it vanishes | CannonService, Gunnery |  | OK |
| `water_slowdown` | `0.5` | 0.0 – 1.0 | Share of its speed a cannonball keeps per tick in water, on top of vanilla's water drag | CannonballEntity |  | OK |
| `sink_speed` | `0.3` | 0.0 – 10.0 | A cannonball in water slower than this (blocks per tick) has sunk and is removed | CannonballEntity |  | OK |
| `walls_block_barrel` | `true` |  | A carriage cannon whose barrel runs into a bulwark (#pirates_n_ships:barrel_blocking) fires into it: the ball hits the wall at once. Off = the ball leaves the muzzle beyond the wall as before | CannonService | false | OK |
| `respect_mob_griefing` | `true` |  | Cannonballs break no blocks while the mobGriefing game rule is off | CannonballEntity |  | OK |
| `respect_spawn_protection` | `true` |  | Cannonballs break no blocks inside the server's spawn protection (server.properties spawn-protection) | CannonballEntity |  | OK |
| `destroyed_blocks_drop` | `true` |  | Blocks a cannonball destroys drop their items as if mined | CannonballEntity, RiggingDamage | false | OK |
| `glancing_hits` | `true` |  | A ball that hits at an angle breaks fewer blocks (blocks_per_hit times the cosine of the angle to the face, at least 1) and pushes less; past glancing_bounce_degrees it bounces off without breaking anything | CannonballEntity |  | OK |
| `glancing_bounce_degrees` | `75.0` | 0.0 – 90.0 | A ball whose flight is more than this many degrees off the hit face's normal bounces off (90 = never) | CannonballEntity |  | OK |
| `glancing_bounce_factor` | `0.3` | 0.0 – 1.0 | Share of its speed towards the face a bouncing ball keeps, turned away from the face | CannonballEntity |  | OK |
| `swivel.enabled` | `true` |  | Swivel guns can be loaded, aimed and fired. Off = they are inert blocks | SwivelService, SwivelStation |  | OK |
| `swivel.ammo` | `CANNONBALL` |  | What goes into a swivel gun after the gunpowder: a cannonball or lead shot | SwivelService | SwivelAmmo.CANNONBALL | OK |
| `swivel.ammo_count` | `1` | 1 – 64 | How many of the ammo items one load takes | CrewLoading, SwivelService | 3 | OK |
| `swivel.damage` | `8.0` | 0.0 – 1000.0 | Damage of a swivel shot hitting an entity (times combat.damage_multipliers.cannon_entity) | CannonConfig |  | OK |
| `swivel.muzzle_velocity` | `2.0` | 0.1 – 10.0 | Start speed of a swivel shot in blocks per tick, relative to the gun (gravity: cannons.gravity) | SwivelService |  | OK |
| `swivel.reload_ticks` | `60` | 0 – 6000 | Ticks after a shot before powder can go into the swivel gun again | SwivelService |  | OK |
| `swivel.blocks_per_hit` | `0` | 0 – 16 | Blocks one swivel shot destroys (times combat.damage_multipliers.cannon_block; 0 = none) | CannonConfig |  | OK |
| `swivel.recoil_impulse` | `1.0` | 0.0 – 1000.0 | Impulse a swivel shot gives the firing ship against the barrel, in kpg*m/s (0 = no recoil) | SwivelService |  | OK |
| `swivel.impact_impulse` | `1.0` | 0.0 – 1000.0 | Impulse a swivel shot gives the ship it hits, in kpg*m/s (0 = none) | SwivelService |  | OK |
| `swivel.ball_lifetime_ticks` | `100` | 1 – 2400 | Ticks a swivel shot flies before it vanishes | SwivelService |  | OK |
| `swivel.min_elevation_degrees` | `-30.0` | -90.0 – 0.0 | Lowest barrel elevation of the swivel gun in degrees (negative = pointing down) | SwivelAimClient, SwivelService |  | OK |
| `swivel.max_elevation_degrees` | `45.0` | 0.0 – 90.0 | Highest barrel elevation of the swivel gun in degrees | SwivelAimClient, SwivelService |  | OK |
| `swivel.aim_reach` | `4.0` | 1.0 – 16.0 | A player aiming a swivel gun lets go of it when further than this from the gun, in blocks | SwivelService |  | OK |
| `crew.enabled` | `true` |  | Crew members load guns from a supply (the "Load!" order and the reload after a shot). Off = only players load | CrewLoading, Gunnery | false | OK |
| `crew.supply_range` | `4` | 0 – 16 | Containers (chests, barrels, cargo crates) within this many blocks of the gun on the same ship are its supply | CrewLoading, VoyageGuns | 1, 2 | OK |
| `crew.load_ticks` | `80` | 1 – 6000 | Ticks a crew member needs to load a cannon (powder and ball); at least the reload time after a shot | CrewLoading | 20 | OK |
| `crew.swivel_load_ticks` | `40` | 1 – 6000 | Ticks a crew member needs to load a swivel gun; at least the reload time after a shot | CrewLoading |  | OK |
| `crew.auto_reload` | `true` |  | After firing, a crew member loads the gun again by itself from the supply | CrewLoading | false | OK |
| `crew.load_preference` | `BALL` |  | The shot a crew loads into a cannon on "Load!" and after its own shot when the supply holds several kinds (BALL, CHAIN or GRAPE); then the ball, chain shot and grapeshot. Crews firing at will choose by cannons.npc instead | CrewLoading | ShotKind.CHAIN | OK |
| `chain_shot.enabled` | `true` |  | Chain shot can be loaded into cannons. Off = cannons refuse it | CannonConfig, Gunnery | false | OK |
| `chain_shot.range_factor` | `0.6` | 0.1 – 1.0 | Start speed of chain shot as a share of cannons.muzzle_velocity (a level shot's reach scales with it) | CannonConfig |  | OK |
| `chain_shot.spread_degrees` | `2.0` | 0.0 – 30.0 | Chain shot leaves the barrel up to this many degrees off its line | CannonService |  | OK |
| `chain_shot.damage` | `12.0` | 0.0 – 1000.0 | Damage of chain shot hitting an entity (times combat.damage_multipliers.cannon_entity) | CannonConfig |  | OK |
| `chain_shot.rigging_damage` | `true` |  | Chain shot tears the cloth of square sails, cuts ropes (a cut stay drops its sail) and breaks ratlines within cloth_radius of its path through the rigging and of where it strikes. Off = it only hurts entities | CannonballEntity | false | OK |
| `chain_shot.cloth_radius` | `1.5` | 0.0 – 8.0 | Radius in blocks around where chain shot passes through a sail or strikes, in which cloth, ropes and ratlines go | CannonballEntity |  | OK |
| `chain_shot.mend_ticks` | `1200` | 0 – 1728000 | A torn sail mends one block of cloth every this many ticks (0 = never; breaking and replacing the upper yard always gives new cloth) | CannonModule | 5 | OK |
| `grapeshot.enabled` | `true` |  | Grapeshot can be loaded into cannons. Off = cannons refuse it | CannonConfig, Gunnery | false | OK |
| `grapeshot.pellets` | `9` | 1 – 32 | Pellets one grapeshot fires | CannonService |  | OK |
| `grapeshot.range_factor` | `0.4` | 0.1 – 1.0 | Start speed of the pellets as a share of cannons.muzzle_velocity (a level shot's reach scales with it) | CannonConfig |  | OK |
| `grapeshot.spread_degrees` | `6.0` | 0.0 – 45.0 | Half angle of the cone the pellets fly in, in degrees | CannonService |  | OK |
| `grapeshot.damage` | `3.0` | 0.0 – 1000.0 | Damage of one pellet hitting an entity (times combat.damage_multipliers.cannon_entity). Pellets break no blocks and never hit the people on the firing ship | CannonConfig |  | OK |
| `npc.enabled` | `true` |  | Gun crews ordered to fire at will (and the crews of NPC ships) aim and fire at hostile ships by themselves. Off = crews fire only on a captain's "Fire!" | CannonStation, Gunnery | false | OK |
| `npc.arc_degrees` | `15.0` | 0.0 – 90.0 | A crew fires only at a target within this many degrees left or right of its cannon's barrel | Gunnery |  | OK |
| `npc.engage_range` | `64` | 4 – 512 | Ships within this many blocks (horizontally) are targets for crews firing at will | CaptainHunt, Gunnery |  | OK |
| `npc.aim_interval_ticks` | `20` | 1 – 1200 | Ticks between two aiming moves of a crew: each move turns the barrel one elevation step or fires | Gunnery |  | OK |
| `npc.fire_interval_ticks` | `40` | 0 – 12000 | Least ticks between two shots a crew fires by itself from the same gun (on top of loading) | Gunnery | 20 | OK |
| `npc.npc_block_damage_multiplier` | `0.5` | 0.0 – 4.0 | Blocks a ball fired by a crew on its own breaks, times this (0.5 = half of the hits break a block). Shots on a captain's "Fire!" break the full amount | Gunnery | 0.0, damage | OK |
| `npc.aim_height` | `0.5` | 0.0 – 1.0 | Where crews aim on a target ship's height: 0 = the bottom of its bounds, 0.5 = the middle, 1 = the top | Gunnery |  | OK |
| `npc.prefers_chain_shot` | `true` |  | Crews chasing a chosen quarry (a navy patrol's hunt, a pirate captain's chase) load chain shot while the quarry still makes way, to shoot its sails away, when their locker holds any | Gunnery |  | OK |
| `npc.chain_shot_min_speed` | `0.5` | 0.0 – 50.0 | A quarry slower than this (blocks per second) is stopped already: the crews load balls again | Gunnery | 0.0 | OK |
| `npc.grapeshot_min_fighters` | `3` | 1 – 64 | Crews load grapeshot (when their locker holds any) when at least this many fighters (players and armed mobs, not their own crew) stand on the target's deck within grapeshot_range | Gunnery |  | OK |
| `npc.grapeshot_range` | `24` | 1 – 128 | Grapeshot is chosen only for a target this close (blocks, horizontally between the ships' centres) | Gunnery | 32 | OK |

### `grapple` (server): Grappling hook: throwing or firing it, latching onto ships and blocks, hauling ships

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Grappling hooks can be thrown. Off = the item does nothing and hooks already out are released | GrappleClient, GrappleHaul, GrappleService, GrapplingHookEntity, GrapplingHookItem, MooringRingBlock, RopeSlideService | false | OK |
| `throw_velocity` | `3.0` | 0.1 – 5.0 | Start speed of a thrown hook in blocks per tick. With the default gravity a level throw from standing height would land about 39.4 blocks away (measured), past the 32-block rope, so the rope is the reach | GrappleConfig |  | **DESIGN**: Old config files keep `1.5` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: GR4 set 3.0 so a throw reaches the rope length (design.md l.460). |
| `gravity` | `0.02` | 0.0 – 1.0 | Downward acceleration of a flying hook in blocks per tick squared (a musket shot uses launch.musket_gravity_factor times this) | GrapplingHookEntity |  | **DESIGN**: Old config files keep `0.03` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: GR4 set 0.02 (design.md l.460). |
| `max_rope_length` | `32.0` | 2.0 – 128.0 | Rope length in blocks of a thrown hook: a flying hook stops there, and a thrower farther than this from a latched hook snaps the rope | GrappleConfig, GrapplingHookEntity |  | **DESIGN**: Old config files keep `24.0` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: GR4 set 32 by hand (design.md l.460). |
| `haul_force` | `120.0` | 0.0 – 100000.0 | Pull of a taut rope between two ships in kpg*m/s^2, on each ship in opposite directions (about 2.4 m/s^2 on a small 50 kpg hull; 0 = ships are not hauled) | GrappleService, GrapplingHookEntity | 0.0 | OK |
| `rope_damping` | `40.0` | 0.0 – 100000.0 | Damping of the rope in kpg/s: less pull the faster the ends approach each other, so hauled ships close at about haul_force / rope_damping m/s instead of crashing | GrappleService |  | OK |
| `hold_distance` | `1.5` | 0.0 – 32.0 | Horizontal distance in blocks between the rope's two ends (the thrower ship's nearest block and the hook) at which hauling stops: the hulls are side by side | GrappleConfig |  | OK |
| `hold_slack` | `0.5` | 0.0 – 8.0 | Dead band in blocks beyond hold_distance in which the rope holds without pulling (hulls that touch or collide before the rope's ends reach hold_distance still count as alongside) | GrappleConfig |  | OK |
| `shore_haul_force` | `30.0` | 0.0 – 100000.0 | Pull on a hooked ship toward a thrower who is not on a ship, in kpg*m/s^2 (0 = no pulling from land) | GrappleService, GrapplingHookEntity |  | OK |
| `entity_damage` | `2.0` | 0.0 – 100.0 | Damage of a hook hitting an entity (it does not latch onto entities) | GrapplingHookEntity |  | OK |
| `rope_breaks_lose_hook` | `false` |  | A snapped rope loses the hook (dropped where it hung). Off = the hook returns to the thrower | GrapplingHookEntity |  | OK |
| `retract_ticks` | `40` | 0 – 1200 | Ticks a hook that missed (water, an entity, a block it slips off, or a surface its latch toggle forbids) lies there before the rope pulls it back | GrapplingHookEntity |  | OK |
| `latch_world_blocks` | `true` |  | A hook latches on solid world blocks (land, cliffs, a quay). Thrown from a ship, the rope hauls that ship toward the block (a kedge line, haul_force); thrown from land it is a fixed line to slide along. Off = a hook on a world block drops and is pulled back | GrapplingHookEntity | false | OK |
| `latch_own_ship` | `true` |  | A hook latches on the thrower's own ship without hauling it (a line between two points of the ship to slide along, e.g. from the mast top to the deck). Off = it drops and is pulled back | GrapplingHookEntity | false | OK |
| `hauling` | `true` |  | Hauling by hand (GR5): holding sneak with the rope's near end in hand and the hook in a ship freezes the rope's length, and walking away pulls the hooked ship toward the player. A hand-held rope from land no longer drags the hooked ship by itself (shore_haul_force then applies only to ropes tied off on land). Off = sneaking does nothing and shore_haul_force pulls as before | GrappleHaul, GrapplingHookEntity | false, true | OK |
| `haul_stiffness` | `60.0` | 0.0 – 100000.0 | Spring of a frozen rope in kpg/s^2: the pull on the hooked ship per block the rope's ends are beyond the frozen length (60: two blocks pull with 120 kpg*m/s^2, about 2.4 m/s^2 on a small 50 kpg hull) | GrappleHaul | STIFFNESS | OK |
| `haul_damping` | `30.0` | 0.0 – 100000.0 | Damping of a frozen rope in kpg/s: less pull the faster the hooked ship closes in, more while the player walks away | GrappleHaul | DAMPING | OK |
| `haul_max_force` | `200.0` | 0.0 – 100000.0 | Strongest pull of a frozen rope in kpg*m/s^2. Beyond it the rope slips through the hand (its frozen length grows) instead of snapping or stopping the player | GrappleHaul | maxForce | OK |
| `haul_player_pull` | `0.08` | 0.0 – 1.0 | Velocity in blocks per tick added every tick to an airborne or swimming player on a frozen rope at full tension, toward the hook (players standing on the ground or a deck feel nothing) | GrappleHaul |  | OK |
| `climb_min_rise` | `2.0` | 0.0 – 16.0 | Using a rope still in your hand pulls you hand over hand to the hook only as a climb (GR6): when the hook is at least this many blocks above your feet (up a mast, a cliff or a hull side from the water). With the hook lower (level, below, across a gap) the use does nothing but a hint to tie the rope off on a cleat. 0 = every use pulls you to the hook (GR5) | RopeSlideService | 0.0 | OK |
| `rings_enabled` | `true` |  | Mooring rings catch hooks passing close by, hold them harder, and take the rope's near end when tied off. Off = a ring is a plain ship block | GrappleService, GrapplingHookEntity |  | OK |
| `cleats_enabled` | `true` |  | Cleats act like mooring rings (with the ring values below): they catch hooks passing close by, hold them harder, and take the rope's near end when tied off. Needs rings_enabled. Off = only rings do | GrappleService | false | OK |
| `ring_catch_radius` | `1.0` | 0.0 – 4.0 | A flying hook passing this close [blocks] to a mooring ring (or cleat) on another ship latches onto it (0 = only a direct hit) | GrapplingHookEntity |  | OK |
| `ring_hold_multiplier` | `2.0` | 1.0 – 8.0 | A hook latched on a mooring ring (or cleat) snaps only when the rope's ends are this many times its length apart | GrapplingHookEntity |  | OK |
| `latches_hold` | `true` |  | A hook latched on a railing or railing gate (the block tag pirates_n_ships:grapple_latches) holds like one on a mooring ring: it snaps only at ring_hold_multiplier times the rope's length. Off = a railing holds like any block | GrapplingHookEntity | false | OK |
| `railing_break_force` | `180.0` | 0.0 – 100000.0 | Rope pull in kpg*m/s^2 above which a hauled hook tears out the railing (or gate) it holds and lets go: a ship-to-ship haul pulls at most haul_force (120), a hand haul up to haul_max_force (200), so by default only a hard haul by hand breaks a railing. 0 = railings never break | GrapplingHookEntity | 50.0 | OK |
| `launch.offhand_required` | `true` |  | The hook must be in the off hand and the musket in the main hand. Off = the swapped hands work too (hook in the main hand, musket in the off hand, e.g. for left-handed players) | GrapplingHookItem | false | OK |
| `launch.musket_enabled` | `true` |  | A musket next to the hook loads it (the musket's reload time, one gunpowder) and fires it like a shot (cooldown, recoil, rain misfire). Off = the hook is thrown | GrapplingHookItem |  | OK |
| `launch.musket_speed` | `1.6` | 0.1 – 10.0 | Start speed of a hook fired from a musket, as a multiple of throw_velocity. With the default gravity factor a level shot from standing height would land about 83.4 blocks away (measured), past the 64-block rope | GrappleConfig |  | **DESIGN**: Old config files keep `2.4` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: GR4 set 1.6 (design.md l.460). |
| `launch.musket_gravity_factor` | `0.5` | 0.0 – 4.0 | Gravity of a hook fired from a musket as a multiple of grapple.gravity: a flatter shot than a throw | GrappleConfig |  | OK |
| `launch.musket_rope_length` | `64.0` | 2.0 – 128.0 | Rope length in blocks of a hook fired from a musket (never shorter than max_rope_length) | GrappleConfig |  | **DESIGN**: Old config files keep `48.0` (seen in the human's dev config: server-5.toml.bak, the oldest); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: GR4 set 64 (design.md l.460). |
| `slide.enabled` | `true` |  | Players can hang on a latched grappling rope and slide down it. Off = using the rope does nothing and riders drop off | GrappleClient, RopeSlideService | false | OK |
| `slide.board_reach` | `2.5` | 0.5 – 8.0 | How far [blocks] from the eyes a player can grab the rope | ClientRopes, RopeSlideService |  | OK |
| `slide.grab_cooldown_ticks` | `20` | 0 – 200 | Ticks after a hook is thrown or fired during which its rope cannot be grabbed (a use right after the shot never hangs the player on their own rope) | GrappleClient, RopeSlideService |  | OK |
| `slide.board_pick_radius` | `0.6` | 0.1 – 2.0 | How close [blocks] the look ray must pass the rope to grab it | ClientRopes, RopeSlideService |  | OK |
| `slide.slide_speed` | `0.35` | 0.01 – 2.0 | Start speed of the slide in blocks per tick, toward the rope's lower end | GrappleConfig |  | OK |
| `slide.slide_gravity` | `0.02` | 0.0 – 0.5 | Speed gained per tick, in blocks per tick, times the rope's slope (height difference per block of rope): steeper is faster | GrappleConfig |  | OK |
| `slide.slide_min_speed` | `0.1` | 0.01 – 2.0 | Crawling speed in blocks per tick along a level rope, toward the hook; also the speed at which a player pulls themselves along their own hand-held rope to the hook | GrappleConfig |  | OK |
| `slide.slide_max_speed` | `0.8` | 0.01 – 4.0 | Top speed of the slide in blocks per tick | GrappleConfig |  | OK |
| `slide.hang_offset` | `2.0` | 0.0 – 4.0 | How far [blocks] the hanging player's feet are below the rope (2: both hands raised to the rope) | ClientRopes, RopeSlideService |  | OK |
| `slide.dismount_distance` | `1.0` | 0.1 – 8.0 | Distance [blocks] from the end of the rope at which the rider lets go and lands on that spot | GrappleConfig |  | OK |

### `boarding` (server): Boarding another ship

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `plank.enabled` | `true` |  | Boarding planks can be laid. Off = the item refuses; planks already laid still break when the ships part | BoardingPlanks, PlankPreview | false | OK |
| `plank.max_length` | `6` | 1 – 6 | Longest plank run in blocks, from the block beside the clicked block to the far end | BoardingPlankItem, PlankPlanner |  | **DESIGN**: Old config files keep `4` (seen in the human's dev config: current server file and every .bak); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: BRD2 raised the gangway from 4 to 6 cells (design.md l.479); the current dev file still has 4. |
| `plank.max_pitch` | `45.0` | 0.0 – 45.0 | Steepest a plank may climb or fall, in degrees: it is one straight board from its start to where it lands. 0 = level planks only; 45 = a block of rise per block of run | BoardingPlankItem, PlankPlanner |  | OK |
| `plank.pick_up` | `true` |  | Sneak-use a laid plank with an empty hand to take the whole run up again (one plank back) | BoardingPlanks |  | OK |
| `plank.break_distance` | `1.5` | 0.1 – 8.0 | A plank breaks (dropping one plank) when its far end has moved this many blocks sideways from the spot it was laid onto, for example when the hulls part or a ship drifts off the pier | BoardingPlankBlockEntity |  | OK |
| `plank.break_rise` | `2.5` | 0.1 – 8.0 | A plank breaks when its far end has moved this many blocks up or down from the spot it was laid onto; larger than break_distance so a moored ship's heave in a storm does not snap a gangway | BoardingPlankBlockEntity |  | OK |
| `plank.check_interval_ticks` | `10` | 1 – 200 | Ticks between two checks of a plank's ends | BoardingPlankBlockEntity | 1 | OK |
| `board_order` | `true` |  | The whistle's "Board!" order: the crew cross a plank or a narrow gap onto the other ship and fight its crew. Off = the order is refused and boarders already across come back | BoardingParties, NpcBoarding |  | OK |
| `board_gap` | `2` | 0 – 4 | Widest gap in blocks the crew jump without a plank (0 = only touching hulls); the decks may differ by one block | BoardingWorld, NpcBoarding |  | OK |
| `boarders_keep_stations` | `true` |  | Gunners and the helmsman stay at their stations when the crew board; off = every hand goes | BoardingParties | false | OK |
| `capture_seconds` | `10` | 1 – 600 | Seconds the boarders must hold the other deck with no enemy left before it counts as taken | BoardingParties | 1, 600 | OK |
| `npc_boarding` | `true` |  | Pirate and navy ships lying alongside a ship they fight send their fighters across | NpcBoarding | false | OK |
| `npc_board_delay` | `8` | 0 – 600 | Seconds an NPC ship lies alongside its enemy before its captain gives "Board!" | NpcBoarding | 1, 2 | OK |
| `npc_recall_outnumbered` | `2.0` | 1.0 – 10.0 | An NPC captain calls his boarders back when the defenders outnumber them this many times (and boards only while they do not) | BoardingParties, NpcBoarding |  | OK |
| `npc_recall_flooded` | `0.3` | 0.05 – 1.0 | An NPC captain calls his boarders back when this fraction of his own hull is flooded | BoardingParties |  | OK |
| `take_arms` | `true` |  | Boarders with an empty hand take a sword (cutlass, saber, rapier) from a chest or barrel of their ship | BoardingParties |  | OK |
| `unarmed_damage` | `2.0` | 0.0 – 20.0 | Damage of a crew member's punch when it boards without a sword | BoardingParties |  | OK |
| `crew_skill` | `SAILOR` |  | Sword skill of the own crew when it fights with a sword (parry chance, reaction time); scaled by melee.npc_skill_multiplier | CrewMember |  | OK |
| `cross_timeout_ticks` | `200` | 20 – 2400 | Ticks a boarder may take to reach the crossing and get across before it is set down on the other side | BoardingParties |  | OK |
| `order_check_ticks` | `10` | 1 – 200 | Ticks between two looks of the boarders for an enemy, a way back and the deck's state | BoardingParties | 200 | OK |

### `apparel` (server): Wearable apparel: pirate hat, bandana, navy tricorn, officer's bicorne, captain's hat, officer's coat, breeches and boots, the captain's coat, breeches and boots

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `hat_armor` | `1` | 0 – 3 | Armour points a hat gives while worn on the head (0 = no armour modifier) | HatItem | 0, 3 | OK |
| `officers_coat_armor` | `3` | 0 – 8 | Armour points the officer's coat gives while worn on the chest (0 = no armour modifier) | ApparelContent | 0, 4, 5 | OK |
| `officers_breeches_armor` | `2` | 0 – 6 | Armour points the officer's breeches give while worn on the legs (0 = no armour modifier) | ApparelContent | 0, 3 | OK |
| `officers_boots_armor` | `1` | 0 – 3 | Armour points the officer's boots give while worn on the feet (0 = no armour modifier) | ApparelContent | 0, 2 | OK |
| `captains_coat_armor` | `3` | 0 – 8 | Armour points the captain's coat gives while worn on the chest (0 = no armour modifier) | ApparelContent | 0, 5 | OK |
| `captains_breeches_armor` | `2` | 0 – 6 | Armour points the captain's breeches give while worn on the legs (0 = no armour modifier) | ApparelContent | 0, 4 | OK |
| `captains_boots_armor` | `1` | 0 – 3 | Armour points the captain's boots give while worn on the feet (0 = no armour modifier) | ApparelContent | 0, 2 | OK |

### `chart` (server): The pirate chart: every player's own map of the coasts they have seen

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Players chart the world around them and can open their chart (false: nothing is sampled and the chart does not open) | ChartBackend, ChartService, MapTileService | false | OK |
| `cell_blocks` | `4` | 1 – 16 | Size of one chart cell in blocks (changing it clears every chart's explored cells on the next sample; markers stay) | ChartBackend, ChartService, MapTileService, TreasureMapService |  | OK |
| `sample_radius` | `96` | 16 – 256 | Radius in blocks around a player that is charted (only loaded chunks) | ChartService |  | OK |
| `sample_interval_ticks` | `40` | 5 – 1200 | Ticks between two samples of a player's surroundings | ChartService |  | OK |
| `shallow_depth` | `4` | 1 – 32 | Water up to this many blocks deep is drawn as shallows | ChartService, TreasureMapService |  | OK |
| `max_cells` | `1048576` | 65536 – 16777216 | Most chart cells kept per player, counted in whole regions of 64x64 cells (one byte each); beyond it the least recently visited regions are forgotten | ChartService |  | OK |
| `open_without_item` | `false` |  | The chart key (default M) opens the chart without a chart in hand | ChartBackend | true | OK |
| `show_other_players` | `false` |  | Other players within view distance appear on the chart | ChartBackend |  | OK |
| `max_markers` | `64` | 0 – 512 | Most markers a player can put on their chart | ChartBackend |  | OK |
| `tiles.enabled` | `true` |  | Players can draw their chart onto map tiles (false: drawn tiles keep their drawing, but nothing new is drawn) | MapTileService | false | OK |
| `tiles.tile_cells` | `128` | 32 – 256 | Chart cells along each side of a tile drawing (one pixel per cell; 128 cells of 4 blocks = 512 blocks square) | MapTileService |  | OK |
| `tiles.redraw_allowed` | `true` |  | A drawn board can be redrawn at a new area or zoom and cleared; false: the first drawing stays (anyone can still update it with what they have charted since) | MapTileService | false | OK |
| `tiles.require_chart_item` | `true` |  | A chart must be in hand (either hand) to draw onto a tile | MapTileBlock, MapTileService | false | OK |
| `tiles.reach` | `8` | 2 – 64 | Farthest distance in blocks between a player and the tile they draw on | MapTileService |  | OK |
| `tiles.max_board_side` | `8` | 1 – 16 | Most map tiles along each side of a board (tiles side by side in a full rectangle show one chart area together) | MapTileService |  | OK |
| `tiles.max_zoom` | `8` | 1 – 16 | Highest zoom a board can be drawn at (zoom z: each tile pixel covers z x z chart cells) | MapTileService |  | OK |
| `tiles.ink_cost_enabled` | `true` |  | Drawing and updating a board costs ink (items in #pirates_n_ships:chart_ink); creative players never pay | MapTileService | false | OK |
| `tiles.ink_per_tile` | `1` | 0 – 64 | Ink for each tile drawn, or on an update for each tile that changed (at least one tile when anything changed) | MapTileService |  | OK |
| `tiles.kraken_ink_tile_value` | `8` | 1 – 64 | How many tiles one kraken ink pays for | MapTileService |  | OK |

### `almanac` (server): The Captain's Almanac: the in-game guide book and its placed tome

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | The Captain's Almanac can be read, in hand or placed (off = the tome is decoration: it never opens or animates) | AlmanacBlock, AlmanacBlockEntity, AlmanacItem, AlmanacReading | false | OK |
| `open_ticks` | `100` | 10 – 1200 | How long a placed almanac lies open after a right-click at the least, in ticks (20 ticks = 1 second); it stays open longer while the reader still has it open | AlmanacBlockEntity | 10 | OK |
| `remember_page` | `true` |  | The Almanac opens at the page each player last read, also after death and relog (false: nothing is stored, it opens at the start) | AlmanacDataConfig |  | OK |
| `give_on_first_join` | `true` |  | A player who joins for the first time gets one Captain's Almanac, once | AlmanacGift | false, true | OK |

## Client config

### `audio` (client): Sea music and ambience

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `music_enabled` | `true` |  | Play the mod's sea, shanty, battle and harbor music. Off = only vanilla music plays | AudioConfig |  | **NAME**: Fix the comment: "Play the mod's sea and shanty music". There is no battle or harbor music yet (design.md l.690: "Battle and harbor music come with their milestones"). |
| `music_volume` | `1.0` | 0.0 – 1.0 | Volume of the mod's music, on top of the game's music volume slider (0 to 1) | SeaMusic |  | OK |
| `shanties_aboard` | `true` |  | Aboard a ship the shanties play. Off = the ambient sea music plays aboard too | AudioConfig |  | OK |
| `min_gap_seconds` | `120` | 0 – 3600 | Shortest silence between two of the mod's music tracks, in seconds | AudioConfig |  | OK |
| `max_gap_seconds` | `300` | 0 – 3600 | Longest silence between two of the mod's music tracks, in seconds (raised to min_gap_seconds if lower) | AudioConfig |  | OK |
| `ambience_volume` | `1.0` | 0.0 – 1.0 | Volume of the mod's sea ambience such as creaking hulls, wind and waves (0 to 1) | — (only SettingsConfigTest) |  | **DEAD**: Wire it as a client multiplier on the hull creak, hazard and anchor sounds when ambience moves to client playback; until then remove it. Read nowhere (AudioConfig: "needs client-side sound playback (later)"); design.md l.718 (§17 Audio) lists it. |

### `ship_hud` (client): Ship status overlay while you are aboard a ship

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Show the ship HUD while aboard: compass with heading and wind, speed and rudder, and the water in each compartment | ShipHud |  | OK |
| `compass_corner` | `BOTTOM_LEFT` |  | Screen corner of the compass panel (rose, wind, speed and rudder): TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT. It stays put under the chat and keeps clear only of the hotbar, the stamina bar and the effect icons; set both corners alike to stack the two panels as one | ShipHud |  | OK |
| `hull_corner` | `BOTTOM_RIGHT` |  | Screen corner of the hull panel (the strip of compartments with water, breaches and pumps): TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT; in the compass panel's corner it stacks below the compass | ShipHud |  | OK |
| `margin` | `8` | 0 – 32 | Distance of the ship HUD panels from the screen edges, in GUI pixels | RankHud, ShipHud |  | OK |
| `scale` | `1.0` | 0.5 – 3.0 | Size of the ship HUD panels (1 = 112 x 76 GUI pixels for the compass, 112 x 17 for the hull strip) | ShipHud |  | OK |
| `speed_unit` | `KNOTS` |  | Unit of the ship's speed on the HUD: KNOTS (1 block = 1 metre) or BLOCKS (blocks per second) | ShipHud, ShipScreen |  | OK |

### `helm_view` (client): What the helmsman sees while steering

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `show_rudder_angle` | `true` |  | Show the rudder angle and side above the hotbar while holding the wheel | HelmOverlay |  | OK |

### `dry_hull_view` (client): How the inside of dry hulls looks on this client

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `hide_water_plants` | `true` |  | Do not draw seagrass, kelp, sea pickles and bubble columns of the world where they stand inside a dry hull (block tag pirates_n_ships:hidden_in_dry_hull) | HiddenWaterPlants |  | OK |
| `hidden_plants_refresh_ticks` | `5` | 1 – 100 | How often (ticks) the hidden water plants follow a moving ship; lower is quicker but redraws more chunk sections | HiddenWaterPlants |  | OK |
| `flood_surface` | `true` |  | Draw a water surface inside flooded rooms of a ship, and the underwater view below it | FloodSurfaceRenderer |  | OK |

### `pump_visuals` (client): How the bilge pump looks on this client

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Rock the bilge pump's handle while a player or crew member pumps (off = the handle stays up) | PumpHandleRenderer |  | OK |
| `stroke_ticks` | `20` | 4 – 200 | Ticks of one pump stroke, handle down and up again | PumpHandleRenderer |  | OK |
| `stroke_degrees` | `28.0` | 0.0 – 30.0 | How far the handle goes down from its raised rest, in degrees (30 = just clear of the pump's cylinder) | PumpHandleRenderer |  | OK |

### `bell_visuals` (client): How the ship's bell looks on this client

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Swing the ship's bell about its yoke when it is rung (off = it hangs still) | ShipsBellRenderer |  | OK |
| `swing_degrees` | `20.0` | 0.0 – 25.0 | How far the ship's bell swings out on its first swing after a strike from the front or back, in degrees | ShipsBellRenderer |  | OK |

### `sail_visuals` (client): How sail cloth moves in the wind on this client

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Sails belly out while they draw, shake while they luff and hang slack in a calm; off: the fixed belly of before | StayClothRenderer, YardClothRenderer |  | OK |
| `max_belly` | `0.6` | 0.0 – 1.5 | Deepest belly of a drawing sail with a 4-block drop, in blocks (scaled with the sail's drop, at most twice this) | SailAirTracker, YardClothRenderer |  | OK |
| `flutter_amplitude` | `0.15` | 0.0 – 0.5 | Swing of a luffing sail's cloth in a full wind, in blocks | SailAirTracker, YardClothRenderer |  | OK |
| `segments` | `8` | 2 – 32 | Cloth grid: at least this many columns across a sail and half as many rows per block down | StayClothRenderer, YardClothRenderer |  | OK |
| `banner_layers` | `true` |  | Draw the pattern layers of a banner hung on a square sail over its cloth; off: only the banner's base colour | YardClothRenderer |  | OK |

### `flag_visuals` (client): How flags are drawn on this client

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `hoist_animation` | `true` |  | While a flag is hoisted, struck, raised or taken down, draw the cloth running along the pole; off: it appears and vanishes at once | FlagClothRenderer |  | OK |
| `banner_upright` | `false` |  | Draw a banner flag's design upright (its top at the top of the cloth, stretched along the fly); off: the banner hangs sideways from the pole, its top at the pole | FlagClothRenderer |  | OK |

### `wind_effects` (client): Client-side wind visuals: white streaks drifting with the wind

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `streaks` | `true` |  | White wind streaks drift with the wind through the air over the sea, so you can see where the wind blows | SeaEffectsClient |  | OK |
| `density` | `0.12` | 0.0 – 2.0 | Wind streaks spawned per tick for every block per second of wind speed | SeaEffectsConfig |  | OK |
| `min_strength` | `4.0` | 0.0 – 50.0 | No streaks below this wind speed in blocks per second (calm air) | SeaEffectsConfig |  | OK |
| `gust_boost` | `1.0` | 0.0 – 5.0 | Extra streaks at the peak of a gust, as a fraction (1 = twice as many) | SeaEffectsConfig |  | OK |
| `radius` | `24` | 4 – 64 | Horizontal distance from the camera in blocks within which streaks appear | SeaEffectsConfig |  | OK |
| `height` | `12` | 2 – 48 | Streaks fly from 1 block up to at most this many blocks above the sea (the top of the rigging) | SeaEffectsConfig |  | OK |
| `life_ticks` | `40` | 10 – 200 | Mean life of one streak in ticks, including fading in and out; each lives 0.625 to 1.5 times this | SeaEffectsConfig |  | OK |
| `speed_spread` | `0.3` | 0.0 – 0.9 | Each streak flies at 1 plus or minus this times the wind speed (0.3: 0.7 to 1.3 times) | SeaEffectsConfig |  | OK |
| `heading_jitter_degrees` | `8.0` | 0.0 – 45.0 | Each streak's heading strays up to this many degrees from the wind's | SeaEffectsConfig |  | OK |
| `wobble` | `0.25` | 0.0 – 2.0 | Largest gentle up-and-down wobble of a streak over its flight in blocks (each wobbles 0.2 to 1 times this) | SeaEffectsConfig |  | OK |
| `height_peak` | `3.0` | 1.0 – 48.0 | Most streaks fly this many blocks above the sea (deck height), fewer higher up toward 'height' | SeaEffectsConfig |  | OK |
| `puff_share` | `0.65` | 0.0 – 1.0 | Share of the streaks that come in loose puffs in a steady wind (the rest trickle singly); a gust raises it to all | SeaEffectsConfig |  | OK |
| `puff_size` | `6` | 1 – 16 | Most streaks in one puff (a puff has half this, rounded up, to this many) | SeaEffectsConfig |  | OK |
| `puff_spread` | `2.0` | 0.0 – 8.0 | A puff's streaks start within this many blocks of its centre | SeaEffectsConfig |  | OK |
| `puff_ticks` | `5` | 1 – 40 | A puff's streaks are born over this many ticks | SeaEffectsConfig |  | OK |
| `opacity` | `0.36` | 0.0 – 1.0 | Opacity of a streak in a strong wind (12 blocks per second and more); at 4 blocks per second it is 0.3 times this | SeaEffectsConfig |  | OK |

### `wave_effects` (client): Client-side wave visuals

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `camera_sway` | `false` |  | The camera rolls with the ship while you are aboard | WaveClient |  | **DESIGN**: Old config files keep `true` (seen in the human's dev config: client-2 to client-5 .toml.bak); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: camera sway is off by default (design.md l.329, motion sickness). |
| `camera_sway_fraction` | `0.5` | 0.0 – 1.0 | Fraction of the ship's roll the camera follows (1 = the full roll) | WaveClient |  | OK |
| `camera_sway_ease_ticks` | `5` | 0 – 100 | Ticks the camera sway takes to fade in when you board or land and out when you leave the ship (0 = instant) | WaveClient |  | OK |
| `spray` | `true` |  | Spray and a splash at the bow when it digs into a wave in rough or stormy seas | WaveClient |  | OK |
| `foam` | `true` |  | Faint foam streaks on the water, stretched along the direction the waves run, more in a higher sea; none in a calm sea | SeaEffectsClient |  | OK |
| `foam_density` | `1.25` | 0.0 – 20.0 | Foam streaks tried per tick at one block of wave height above foam_min_amplitude, growing with the square root of that height (about a third of them land, on the crests) | SeaEffectsConfig |  | **DESIGN**: Old config files keep `2.5` (seen in the human's dev config: client-2 and client-3 .toml.bak); the config library never rewrites a changed default, it only adds missing keys, drops unknown ones and clamps ranges. Delete this line (or the whole file) so the new default applies. The code default is right; the old value contradicts the design: WAV4 halved it to 1.25 (design.md l.337). |
| `foam_min_amplitude` | `0.2` | 0.0 – 5.0 | No foam below this wave height in blocks (calm sea 0.15, moderate 0.6, rough 1.2, storm 2.0) | SeaEffectsConfig |  | OK |
| `foam_radius` | `24` | 4 – 64 | Horizontal distance from the camera in blocks within which foam appears | SeaEffectsConfig |  | OK |
| `foam_life_ticks` | `80` | 10 – 400 | Life of one foam streak in ticks, including fading in and out | SeaEffectsConfig |  | OK |
| `wake` | `true` |  | Moving ships leave a wake: a bow wave of foam at the stem, a foam trail behind the stern and foam along the waterline when fast | WakeClient |  | OK |
| `wake_radius` | `64` | 8 – 128 | Ships within this horizontal distance of the camera in blocks leave a wake | SeaEffectsConfig |  | OK |
| `wake_min_speed` | `1.0` | 0.0 – 20.0 | No wake below this speed through the water in blocks per second | SeaEffectsConfig |  | OK |
| `wake_full_speed` | `8.0` | 0.5 – 40.0 | The wake is at its densest and most opaque from this speed in blocks per second on | SeaEffectsConfig |  | OK |
| `wake_density` | `0.6` | 0.0 – 5.0 | Foam quads per tick for every block of stern width at full speed; the bow wave and the waterline foam scale with it | SeaEffectsConfig |  | OK |
| `wake_life_ticks` | `160` | 20 – 600 | Life of the stern trail's foam in ticks (the bow wave lives three quarters of it): the longer, the longer the trail | SeaEffectsConfig |  | OK |
| `wake_spread_degrees` | `19.5` | 0.0 – 45.0 | Angle in degrees between the course and each arm of the bow wave's V (19.5 is the Kelvin angle of real ships) | SeaEffectsConfig |  | OK |
| `surface` | `true` |  | The sea surface rises and falls with the waves (a moving water surface around you; ships, swimming and collisions still use the flat sea level). Off, and always with Sodium, Embeddium, Iris, Oculus or VulkanMod: flat water | SeaSurface |  | OK |
| `surface_near_radius` | `32` | 8 – 96 | Distance from the camera in blocks within which the moving surface has one point per block (rounded up to 4) | SeaSurface |  | OK |
| `surface_mid_radius` | `128` | 32 – 512 | Distance from the camera in blocks within which the moving surface has one point every 4 blocks; beyond it the far cells reach to the render distance | SeaSurface |  | OK |
| `surface_far_cell` | `16` | 4 – 64 | Size in blocks of the moving surface's cells beyond surface_mid_radius (4, 8, 16, 32 or 64; other values round down) | SeaSurface |  | OK |
| `surface_full_depth` | `6` | 2 – 16 | Water depth in blocks from which the surface moves with the full wave height; shallower water moves less, one block deep not at all, so waves never cut into a beach | SeaSurface |  | OK |
| `surface_underside` | `true` |  | Also draw the moving surface from below, so it shows when you look up from under water | SeaSurface |  | OK |
| `boat_tilt_degrees` | `8.0` | 0.0 – 20.0 | Vanilla boats on the waves tilt along the slope of the sea by up to this many degrees (0 = no tilt; the rider stays upright) | BoatTilt |  | OK |

### `hazard_visuals` (client): Client-side look and sound of waterspouts and whirlpools

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `particle_density` | `1.0` | 0.0 – 4.0 | Multiplier on the number of particles of waterspouts and whirlpools (0 = none) | HazardVisuals |  | OK |
| `sounds` | `true` |  | Waterspouts roar and whirlpools gurgle | HazardVisuals |  | OK |

### `cannon_visuals` (client): How the cannon looks on this client

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Draw the cannon's barrel raised or lowered to its elevation (off = always level) | CannonBarrelRenderer |  | OK |
| `tilt_ticks` | `6` | 0 – 100 | Ticks the barrel takes to swing to a new elevation step (0 = at once) | CannonBarrelRenderer |  | OK |

### `boarding_view` (client): How laying a boarding plank looks on this client

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `preview` | `true` |  | While holding a boarding plank, outline the run it would lay where you look (green), or the cell in the way (red) | PlankPreview |  | OK |

### `sea_chest_visuals` (client): How the floating sea chest looks

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `bob_amplitude` | `0.04` | 0.0 – 0.5 | Height of the floating chest's bobbing on the water [blocks] (0 = still) | SeaChestRenderer |  | OK |

### `chart_visuals` (client): How the pirate chart is drawn

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `doodles` | `true` |  | Sea monsters and compass roses drawn into open water | ChartScreen |  | OK |

### `melee_input` (client): Sword input timing (skill-based combat)

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `hold_to_thrust_ticks` | `6` | 1 – 40 | Attack held at least this many ticks (20 per second), then released = thrust; released sooner = slash | MeleeClientConfig |  | OK |
| `parry_tap_ticks` | `4` | 1 – 20 | Use key released before it was held this many ticks = parry (after a short guard); held longer = guard only | MeleeClientConfig |  | OK |

### `melee_hud` (client): Stamina bar shown while holding a skill-based sword

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Show the stamina bar near the hotbar while holding a skill-based sword | MeleeHud, ShipHud |  | OK |
| `position` | `ABOVE_HOTBAR_TIGHT` |  | Where the stamina bar sits. ABOVE_HOTBAR_TIGHT = 2 px above the hotbar in creative, over the right half just above the food row in survival (the experience bar takes the space above the hotbar); LEFT_OF_HOTBAR = an upright bar left of the hotbar and offhand slot, aligned to the hotbar's bottom | MeleeHud, ShipHud |  | OK |
| `scale` | `1.0` | 0.25 – 4.0 | Size of the stamina bar | MeleeHud, ShipHud |  | OK |
| `x_offset` | `0` | -2000 – 2000 | Horizontal shift of the stamina bar from its position, in GUI pixels (positive = right) | MeleeHud, ShipHud |  | OK |
| `y_offset` | `0` | -2000 – 2000 | Vertical shift of the stamina bar from its position, in GUI pixels (positive = down) | MeleeHud, ShipHud |  | OK |
| `opacity` | `1.0` | 0.1 – 1.0 | Opacity of the visible stamina bar (1 = solid) | MeleeHud |  | OK |
| `fade_when_full` | `true` |  | Fade the stamina bar out while stamina is full and you are not fighting; it comes back at once when stamina drops, you guard, attack, parry, are staggered or get hit | MeleeHud |  | OK |
| `fade_delay_ticks` | `20` | 0 – 1200 | Ticks (20 per second) the stamina bar stays after stamina is full before it starts to fade | MeleeHud |  | OK |
| `fade_ticks` | `10` | 1 – 200 | Ticks the stamina bar takes to fade from visible to transparent | MeleeHud |  | OK |

### `melee_animations` (client): Sword animations of players (Player Animation Library)

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Animate players' sword attacks, guard, parry and stagger. Off = only vanilla's hand swing | PalMeleeAnimations |  | OK |
| `first_person` | `AUTO` |  | Show the sword animations in first person (animated arm and sword instead of the vanilla hand). AUTO = on, unless a camera mod that draws the body is installed (First-person Model, Real Camera) | PalFirearmAnimations, PalMeleeAnimations, PalRopeSlidePoses |  | OK |
| `layer_priority` | `1500` | 0 – 100000 | Priority of the sword animation layer among other mods' player animations (higher = drawn over them; emotes typically use 1000). Applied at game start | FirearmAnimationsSetup, MeleeAnimationsSetup, RopeSlidePoses |  | OK |
| `fade_ticks` | `4` | 0 – 20 | Ticks (20 per second) a sword animation takes to blend in when it does not start from the pose on screen (an attack cut short by a parry or a hit, a riposte, a parry from rest, going back to idle mid-swing). Cosmetic only: hit timing is unchanged. 0 = snap | PalMeleeAnimations |  | OK |
| `keep_crouch` | `true` |  | Keep the crouch while a sneaking player's sword animation plays (lowered, leaning upper body). Off = the sword animation shows a standing upper body on crouched legs | PalMeleeAnimations |  | OK |

### `firearm_animations` (client): Pistol and musket animations of players (Player Animation Library)

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `enabled` | `true` |  | Animate players aiming and reloading pistols and muskets (first person follows melee_animations.first_person). Off = guns stay in the normal held pose | PalFirearmAnimations |  | OK |

### `firearm_view` (client): How aiming a gun looks on this client

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `musket_zoom` | `1.25` | 1.0 – 4.0 | Zoom while aiming a loaded musket (field of view divided by this). 1 = no zoom | FirearmsClient |  | OK |

### `career_hud` (client): The rank box: your career title, reputation and letter of marque

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `mode` | `ON_CHANGE` |  | When the rank box shows: OFF never, ON_CHANGE for a few seconds on joining, after a promotion or other change of rank and after closing the career screen, ALWAYS all the time | RankHud |  | OK |
| `show_seconds` | `5` | 1 – 30 | Seconds the rank box stays on screen in mode ON_CHANGE, fading in and out included | RankHud |  | OK |
| `corner` | `TOP_LEFT` |  | Screen corner of the rank box: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT. It keeps clear of the hotbar, the stamina bar, the effect icons and the ship HUD panels in the same corner | RankHud |  | OK |

### `almanac_screen` (client): How the Captain's Almanac's book screen looks and sounds

| Key | Default | Range | Comment | Read in | Tests pin | Verdict |
|---|---|---|---|---|---|---|
| `page_turn_ticks` | `8` | 0 – 40 | Length of the page-turn animation in ticks (0: pages change at once) | AlmanacScreen |  | OK |
| `page_turn_sound` | `true` |  | Play the book's page-turn sound when a page turns | AlmanacScreen |  | OK |
| `scale` | `1.0` | 0.5 – 2.0 | Size of the book on screen (1.0: 292x180 GUI pixels); always shrunk to fit small windows | AlmanacScreen |  | OK |
