# Pirates 'n' Ships — Design Spec

> Status: brainstorm → spec, v0.6 (2026-10-06, adds the first playtest feedback: roll damping, 3D models, multi-block sails, the anchor as an object, big flags, the radial menu). Living document: update it whenever a decision changes.
> Mod name "Pirates 'n' Ships". Mod ID: `pirates_n_ships`.

---

## 1. Vision

A Minecraft mod about sailing, piracy and life at sea. Players build **real block ships** that float, sail with the wind, take damage and sink. They crew them with NPCs, fight the Royal Navy or join it, and hunt treasure across pirate islands.

### Unique selling points
1. **Dry hulls.** Ship interiors below the waterline stay dry: no water inside, no swimming below deck.
2. **Flooding and sinking.** Hull breaches let water into compartments. Ships list, lose buoyancy and sink. The crew can bail water and patch holes.
3. **Wind-driven sailing.** A global, weather-dependent wind drives sails. Course and sail trim matter.
4. **Commandable crew.** NPCs man stations such as sails, cannons, the crow's nest and the anchor, following the captain's orders.
5. **Skill-based swordplay.** Slash, thrust, guard, parry and riposte with stamina. Deck duels are about timing, not click spam, against players and NPCs alike.
6. **A living sea world.** Pirate islands, seafarer villages, the navy, bounties, sharks and a rare kraken.

### Non-goals (for now)
- No visibly deforming water surface. Waves are simulated through ship motion, sound and particles.
- No dependency on Create. Optional compatibility can come later.
- No full fluid simulation. Water inside ships is a gameplay model, not a CFD model.

---

## 2. Technical foundation

| Topic | Decision |
|---|---|
| Minecraft | 1.21.1 |
| Loader | **Multiloader project.** NeoForge is the first shipped target. Fabric is planned (module exists in the build, but it is disabled until the NeoForge version is stable). |
| Java | 21 |
| Physics | **Sable** (`dev.ryanhcode.sable`) is the only hard dependency. It provides sub-levels (moving, interactive block structures), Rapier-based physics and basic buoyancy. License: PolyForm Shield 1.0.0. Depend on it, never copy or bundle its code. `common` compiles against `sable-common`, and each loader module uses its loader artifact. |
| Animated entities | GeckoLib 4.9.3 (required dependency, both sides, both loaders). `common` compiles against `geckolib-common-1.21.1` (Mojang names), `neoforge` uses `geckolib-neoforge-1.21.1`, `fabric` will use `geckolib-fabric-1.21.1`. Integrated in M1; the humanoid rig contract is in `art/README.md`, section "Entities". |
| GuideME (optional, NeoForge only) | In-game guide book: pages generated from `docs/guide.md` by `tools/gen_guideme.py` (D1); the mod loads without it. |
| Player animations | **Player Animation Library (PAL)** (`com.zigythebird.playeranim`, MIT), required on the client for both loaders. `common` compiles against `PlayerAnimationLibNeo` (`compileOnly`, non-transitive; the published Common artifact is in Fabric intermediary names, the Neo jar bundles Common and Core in Mojang names); `neoforge` depends on `PlayerAnimationLibNeo` with the separate Core artifact excluded; `fabric` will use `PlayerAnimationLibFabric` (Loom remaps it to Mojang names, unverified). Used only for the player's melee animations (§8.5); NPCs use GeckoLib. Decided 2026-10-07, see `docs/animation-libraries.md`. |
| Config | A cross-loader config solution (e.g. Forge Config API Port), accessed only through our own `config` wrapper. See §21. |
| Mappings | Official Mojang + Parchment |
| Build | Based on the **MultiLoader-Template** (jaredlll08): `common` (vanilla only, via NeoForm), `neoforge` (ModDevGradle), `fabric` (Loom). The template's legacy Forge module was removed. |
| Optional compat | Create (contraptions on ships), shader packs (Iris), JEI/EMI, Jade |
| License | **PolyForm Noncommercial 1.0.0**: anyone may use, modify and redistribute the mod for any noncommercial purpose. |

### Dependency rule
Every new dependency must exist for **both** NeoForge and Fabric, or be optional and isolated behind a compat module.

### Vanilla vs. loader APIs
Prefer vanilla APIs when vanilla offers an equivalent (data components, codecs, `CustomPacketPayload`, GameTest). Use a loader API, through a platform service, when it offers better compatibility, interop or performance. **Never write a mixin just to avoid a loader API.** Typical cases where the loader API wins:
- **Loader events instead of our own mixins:** maintained hooks, shared with other mods, fewer conflicts.
- **Capabilities / transfer APIs** (NeoForge capabilities, Fabric Transfer API): let other mods' pipes and hoppers work with cargo crates and pantries. NeoForge also caches capability lookups.
- **Data attachments** on entities, levels and chunks (criminal score, stamina, ship data). Vanilla has no general equivalent.
- **Render stage events** for the water mask and HUD, instead of level-renderer mixins where possible.
- **Chunk loading** for ships far from players.
- **Convention tags** (`c:` namespace) for recipes and trade goods.

Performance in this mod comes mainly from our own hot paths: hull flood fill, flooding, per-ship forces, mask meshes, network sync and crew AI. Use efficient data structures (e.g. bitsets for hull volume), incremental updates and throttled sync, and **profile with spark** before optimizing.

### Reference sources (read-only, in `refs/`, excluded from build)
- `refs/sable`: primary API reference
- `refs/create-aeronautics`: example of a real mod using Sable (assembly, forces)
- Minecraft sources generated via Gradle / IDE

---

## 3. Architecture

Package root: `com.richardsenger.piratesnships`.

### 3.1 Multiloader layout

```
common/      ~80–90% of the code. Vanilla Minecraft + sable-common only.
             All gameplay logic, block/item/entity classes, payloads, renderers,
             most mixins, datagen providers, GameTest definitions.
  └─ platform/   Service interfaces (Services.PLATFORM, .REGISTRY, .NETWORK, .ATTACHMENTS, .CONFIG)
                 and the event hubs (CommonEvents, ClientEvents)
neoforge/    Thin layer: @Mod entrypoint, platform service implementations,
             registration timing, event wiring, NeoForge-only datagen extras.
fabric/      Same thin layer for Fabric (ModInitializer / ClientModInitializer).
             Disabled in settings.gradle until the Fabric port starts.
```

**Hard rules:**
- `common/` never imports `net.neoforged.*` or `net.fabricmc.*`. Since `common` compiles against vanilla only, violations fail the build.
- Loader-specific needs go through a **platform service interface** in `common/.../platform/`. Implementations live in `neoforge/` and `fabric/` and are loaded via `ServiceLoader` (`META-INF/services`).
- Event handlers are plain methods in `common` (e.g. `ShipEvents.onServerTick(server)`). Loader modules only *subscribe* and forward to them. Events are not a `Services` entry: common code registers listeners on the static hubs `CommonEvents` and `ClientEvents`, and the loader module fires them (§3.3).
- Registration: `common` declares what exists (a registry helper with suppliers). The loader layer performs the actual registration at the right time.
- Networking: payload records + codecs + handlers live in `common` (vanilla `CustomPacketPayload`). Only registration and sending go through `Services.NETWORK`.
- Mixins: shared mixins go in `common` (`pirates_n_ships.mixins.json`). Loader-specific mixins are allowed only when unavoidable, in that loader's own mixin config.
- Loader-specific *features* (e.g. NeoForge's generated config screen) are allowed, but must sit behind a platform method with a fallback on the other loader.

### 3.2 Modules
The feature modules below are packages inside `common` (and, where needed, a small matching package in each loader module for wiring).

| Module | Responsibility |
|---|---|
| `core` | Registries, config, networking, data attachments, common utilities |
| `ship` | Assembly/disassembly, ship registry, hull analysis (dry volume), flooding, buoyancy hook, damage, cargo weight, flags, customization blocks |
| `sailing` | Wind field, weather coupling, sails, rudder/helm, anchor, oars |
| `station` | Station blocks (sail winch, cannon station, crow's nest, capstan, pump) and the shared "operate" interface used by players and crew |
| `crew` | Crew NPC base, hiring, command system, station assignment, morale/pay, provisions consumption |
| `combat` | Weapons, ammo, cannons, projectiles, grappling hook, boarding |
| `law` | Criminal score, bounties, navy turn-in, brig and prisoners, flag allegiance detection |
| `trade` | Doubloon economy, trade goods, port markets and dynamic prices, contracts, cargo containers |
| `world` | Structures (pirate islands, seafarer villages, navy outposts), loot tables, treasure maps, shipwright orders and dock berths |
| `worldsim` | World simulation: port registry, faction state, NPC voyages, raids (§10.4) |
| `entity` | Mobs (pirates, sailors, navy soldiers/officers, sharks, kraken), sea chest entity |
| `survival` | Cold water, swimming hunger, sea chest carry rules |
| `hazard` | Waves, waterspouts, whirlpools |
| `rpg` | Reputation/honor, quests, story hooks |
| `audio` | Sound events, sea ambience, shanty music manager |
| `client` | Renderers, water-mask rendering, HUD (wind indicator, ship status), config screen |

**Rule:** server-authoritative gameplay. The client only renders and predicts.
**Rule:** every gameplay-changing feature has a config toggle, and every frequency or strength has a config value (see §17).

### 3.3 Foundation APIs
How a feature module plugs in. Copy the `core` module (`common/.../core/CoreModule.java`) as the template. Paths are under `common/src/main/java/com/richardsenger/piratesnships/`.

- **Module class.** Implement `core/ModModule` once per module (e.g. `ship/ShipModule`). Hooks run during mod construction in this order: `registerConfig`, `registerContent`, `registerPayloads`, `registerEvents`, then `initClient` (physical client only; delegate to a separate client class). The only shared file a module changes is `core/ModModules` (one line, orchestrator only): `new com.richardsenger.piratesnships.ship.ShipModule(),`. A design module (§3.2 package) may have **several `ModModule` implementations, one per work package or sub-feature**, each in its own sub-package (e.g. `ship/hull/HullModule` with id `"ship.hull"`), so that parallel work never shares a module class. Each one is one line in `core/ModModules`.
- **Content.** `static final` fields in your own class (e.g. `ShipBlocks`) via `core/registry/ModRegistry`: `block`, `blockWithItem`, `item`, `blockEntity`, `entity`, `sound`, `dataComponent`, `menu`, `creativeTab`. Each returns a `RegistryEntry<R, T>` (`get()`, `id()`, `holder()`). Trigger the class from `registerContent()` (`ShipBlocks.init()`). Living entities also call `Services.REGISTRY.registerEntityAttributes`. Every registered item lands in the mod's creative tab. Example: `CoreContent`.
- **Config.** One section class per module, e.g. `ShipConfig`: `ConfigSection S = ModConfigs.server("ship", "comment")`, then `S.bool / intRange / doubleRange / enumValue / string / stringList / section(...)`, each returning `core/config/ConfigValue<T>` (`get()`). Server = gameplay (synced), client = audio/visuals. Call `ShipConfig.init()` from `registerConfig()`. Config lang (titles + tooltips) is generated automatically, with keys `pirates_n_ships.configuration.<path>` that carry no config type, so **a top-level section name must be unique across server and client config**: declaring `ModConfigs.client("waves", …)` next to `ModConfigs.server("waves", …)` throws at declaration time, naming both sections (use e.g. `wave_effects`). Unbound handles return their default. **Test rule: change config in tests only through `core/gametest/ConfigOverrides`** (see GameTests below). Raw `set(v)` / `reset()` are safe only in JUnit (unbound): there `set` is a local override and `reset()` clears it. In a running game `set` writes the real config, and `reset()` does **not** undo it (it only clears local overrides and never restores the old value or the default). Never import the backing library. Example: `CoreConfig`.
- **Payloads.** A `record` implementing `CustomPacketPayload` with a `Type` and a `StreamCodec`, registered in `registerPayloads()`: `Services.NETWORK.registerToServer(MyPayload.TYPE, MyPayload.CODEC, (p, player) -> ...)` (or `registerToClient`). Handlers run on the main thread. Send with `Services.NETWORK.sendToServer / sendToPlayer / sendToTrackingEntityAndSelf / sendToTrackingChunk / sendToAll`.
- **Events.** In `registerEvents()`: `CommonEvents.SERVER_TICK_END.register(server -> ...)`. Available: server start/stop, server/level/player ticks, login/logout/clone, container open and close (`CONTAINER_OPEN` / `CONTAINER_CLOSE`: `(player, menu)`, server only; open fires after the menu is set as `player.containerMenu` and loot is unpacked, close after `menu.removed(player)`, with the slots still showing the final contents), entity join, entity interaction (`ENTITY_INTERACT`: `(player, target, hand) -> InteractionResult`, fired on **both logical sides** once per hand before the entity's own interaction (e.g. villager trades) and before the held item's `interactLivingEntity`; return `PASS` to let vanilla run, anything else cancels it and is returned to vanilla, first non-`PASS` wins; server-authoritative listeners return `PASS` on the client; used by `law.content.ShacklesItem.onEntityInteract`), incoming damage, death, block break/place (return `true` to cancel), commands, reload listeners, datapack sync (`DATAPACK_SYNC`: `(player, joined)`, once per player on login and for every player after `/reload`; the place to send server data to clients). Client: `platform/event/ClientEvents` (ticks, `CLIENT_DISCONNECT` (`mc -> …`, fired when the client leaves a world or server, also before a new single-player world; clear client caches of server data here, e.g. `ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientWind.reset())`; `core` uses it to empty every definition client store), `ITEM_TOOLTIP` (`(stack, context, flag, player, lines) -> …`, after vanilla and the item built the tooltip; `lines` is mutable, index 0 is the name, `player` may be `null` during startup indexing; example `trade/client/TradeClient` with `trade/cargo/CargoTooltip`), `SELECT_MUSIC` (`(Music vanillaChoice) -> @Nullable Music`, the first non-null result replaces vanilla's choice; fired from NeoForge's `SelectMusicEvent`; used by `audio/music/SeaMusic`), `SOUND_STREAM_STARTED` (`(SoundInstance, Channel)`, on the sound thread when a streamed sound starts playing; `SeaMusic` scales the channel volume of our music by `music_volume` there), `INTERACTION_KEY` (`(mc, input, hand) -> InteractionKeyResult`; `CANCEL` stops vanilla's attack, use or pick-block and the hand swing; fired from NeoForge's `InputEvent.InteractionKeyMappingTriggered`; used by `combat/melee/client/MeleeInput`), `registerBlockColor` (vanilla `BlockColor` for model faces with a `tintindex`; called while meshing, `level` is the meshing region and can read block entities, also on Sable ships; re-mesh after the data changes with a client `sendBlockUpdated(pos, s, s, Block.UPDATE_IMMEDIATE)`; forwarded from `RegisterColorHandlersEvent.Block`; example `ship/decor/client/ShipDecorClient`), `registerHudLayer`, `registerKeyMapping`, `registerEntityRenderer`, `registerBlockEntityRenderer`, `registerModelLayer`). A missing event = one field in `CommonEvents` + one line in `NeoForgeEventForwarder` (orchestrator).
- **Attachments.** `platform/attachment/AttachmentKey.builder("stamina", () -> 100).persistent(Codec.INT).synced(ByteBufCodecs.VAR_INT).copyOnDeath().build()` as a `static final`, then `Services.ATTACHMENTS.register(KEY)` in `registerContent()`. Read and write with `Services.ATTACHMENTS.get/set/has/remove(entity|level|chunk, KEY)`. Use immutable values; only `set` saves and syncs.
- **Automation (item transfer).** Vanilla hoppers use `Container` directly. To also expose a block entity to other mods' pipes (NeoForge item handler capability, Fabric `ItemStorage.SIDED`), call in `registerContent()`: `Services.CAPABILITIES.registerBlockContainer(MyContent.MY_BLOCK_ENTITY)` (block entity is its own `Container`) or `registerBlockContainer(type, (be, side) -> container or null)`. The loader wraps the container, so its own rules still apply: a `WorldlyContainer` per side (`getSlotsForFace`, `canPlaceItemThroughFace`, `canTakeItemThroughFace`; `null` side = all slots), a plain `Container` through `canPlaceItem` / `removeItem` / `setChanged`. Registered: pantry, cargo containers (not the water barrel, which holds rations). The NeoForge module's own GameTests (`neoforge/.../platform/NeoForgeGameTests`, registered by the entry point) query the capability.
- **Datagen.** Override `gatherData(DataContributions data)`: `data.lang(...)`, `data.models(m -> m.blocks().createTrivialCube(block))` (vanilla `BlockModelGenerators` / `ModelTemplates`; block items get a delegating item model automatically; every block of ours needs a blockstate), `data.recipes(out -> ...)`, `data.blockLoot(loot -> loot.dropSelf(block))`, `data.blockTags / itemTags / entityTypeTags(tags -> tags.tag(KEY).add(...).addTag(OTHER))`, or `data.tags(Registries.X, ...)` for any other built-in registry. Tag ids may be in any namespace (`sable:heavy`, `sable:retain_in_sub_level`, `c:...`); the file is written with `replace: false`, so it merges. **Tag references:** a required `addTag(OTHER)` (written as plain `"#ns:path"`) must point to a tag defined in our own run or to a vanilla tag (`BlockTags.DIRT`, `ItemTags.PLANKS`, …; known from vanilla's built-in data pack via `core/datagen/VanillaTags`). Anything else, including a typo, fails the data run with "missing following references". Tags of other mods (`c:…`, `sable:…`) must be optional: chain `tags.tag(KEY).add(block).addTag(BlockTags.DIRT).addOptionalTag(cTagId)` / `.addOptional(elementId)` (written with `"required": false`, skipped at load if absent; put `add(value)` calls before the first optional call), or use the provider methods `tags.addOptional(KEY, id)` / `tags.addOptionalTag(KEY, tagId)`. Example: `CoreTags.TEST_GROUND`. Raw JSON for formats without a vanilla provider: `data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", id, () -> json)` writes `data/<id ns>/physics_block_properties/<id path>.json`; `data.encoded(target, dir, id, codec, value)` encodes through a codec; an empty directory writes a file at the namespace root (`assets/pirates_n_ships/sounds.json`). Two contributions with the same output path fail the data run. `sounds.json` is shared: every module adds its entries through `data.sounds(s -> s.event(MySounds.X).sounds("minecraft:block/chain/step1", ...).subtitle(key))` (`core/datagen/SoundEntries`: `.stream()` for music, several sounds as variants with `.volume/.pitch/.weight`, a duplicate key fails the run) and `core` writes the one file; a raw `data.json` write of `sounds.json` is rejected. Sound files are hand-made assets under `assets/pirates_n_ships/sounds/`, produced by `tools/convert_sounds.py` from `tools/sounds/manifest.json` (§16). Only vanilla datagen classes. Run `./gradlew :neoforge:runData`; output goes to `common/src/generated/resources` and is committed. Textures (PNG), sound files (OGG, §16) and Blockbench models (§4.8) are the hand-made assets, in `common/src/main/resources/assets`.
- **Datapack definitions** (`core/data`). For data-driven tuning tables (weapons, trade goods, ...). Declare once: `public static final DefinitionType<WeaponDefinition> WEAPONS = DefinitionType.createSynced("weapon", WeaponDefinition.CODEC);` (or `create(name, codec)` for server-only, or `create(name, codec, streamCodec)`), and touch the class from `registerContent()`. Type names are global, use the feature word (`weapon`, `trade_good`). Files: `data/<namespace>/pirates_n_ships/<type>/<path>.json` → id `<namespace>:<path>`; any datapack adds entries in its own namespace or replaces ours by shipping the same path (whole-file replace, top pack wins). Invalid files are logged with id and codec error and skipped. Lookup: `WEAPONS.server()` (filled by the datapack reload), `WEAPONS.client()` (filled by the sync payload, synced types only), or `WEAPONS.of(level)`; each returns an immutable `Definitions<T>` with `get(id)` (Optional), `require(id)`, `all()`, `ids()`. The two stores are separate, so single-player never mixes them. Notification: `WEAPONS.onServerReload(defs -> ...)` / `onClientSync(defs -> ...)`. Datagen: `data.definitions(WEAPONS, Map.of(id, value))` or `data.definition(WEAPONS, id, value)`. **Pure logic takes `Definitions<T>` or single entries as parameters** and never reads the static stores; JUnit builds them with `Definitions.of("weapon", map)`. The parser `DefinitionParser.parse(codec, ops, Map<id, JsonElement>)` is pure. Example: `core/CoreDefinitions.TEST_MARKER`.
- **JUnit.** `common/src/test/java/...`, run by `./gradlew build`. Prefer pure Java. When Minecraft classes are needed (codecs, `ResourceLocation`, vanilla registries), call `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` in `@BeforeAll` (see `core/MinecraftBootstrapTest`). Mod registry content and `Services` are not available in JUnit; never touch `Services` from code under unit test.
- **GameTests.** A class like `core/CoreGameTests` with one `@GameTestGenerator` method returning `ModGameTests.of(MyTests.class)` and `public static void x(GameTestHelper)` methods annotated `@ModGameTest(template = GameTestTemplates.EMPTY_9)`. Return the class from `gameTestClasses()`. Shared empty templates (3³, 9×6×9, 24×12×24, 40×12×40) are generated; tests build their own setup. The framework encases every template in barriers one block above its top (relative y = 13 for ours), and a floating test ship rises about 1.4 blocks after assembly, so a rig that reaches y ≥ 12 drags on that ceiling (ships stopping, hulls that cannot roll): ships with masts use `SailingGameTestsShips.openSky(helper, size)`, which removes it (F5a). `runGameTestServer` deletes the test world and the mod's config files under `neoforge/build/gametest/` before every run, so tests always see the code defaults; a GameTest must never rely on a persisted config value. Run `./gradlew :neoforge:runGameTestServer` (fails the task on any required failure). **Config in GameTests:** `ConfigOverrides.during(helper, ShipConfig.X, value)` sets a value for the rest of the test and restores it when the test passes, fails, times out or is rerun (and on server stop). **Rule: a GameTest that changes config must use its own batch**, `@ModGameTest(batch = "pirates_n_ships_config_<module>_<what>")`, because tests of one batch run at the same time and see the real server config, while batches run one after another. In JUnit use `ConfigOverrides.apply(value, v)` and `restore()` the handle. Never call `ConfigValue.set` in a GameTest: `reset()` cannot undo it and the value leaks into later batches. Example: `CoreGameTests.configOverrideIsAppliedAndRestored`.
- **Access transformers.** `common/src/main/resources/META-INF/accesstransformer.cfg` applies to common and NeoForge. Every line needs an access-widener twin in the Fabric port. Current lines: `BlockEntityType$BlockEntitySupplier`, `IntrinsicHolderTagsProvider$IntrinsicTagAppender`, `TagsProvider$TagAppender` (datagen appender chaining).

---

## 4. Ships (core system)

### 4.1 Building and assembly
- **Ships are built freely** from any blocks, like any other build. There is no fixed ship type and no blueprint requirement.
- **Mod components** placed on the build give the ship its abilities and change how it behaves: helm (steering), sails (§5.2), anchor and capstan, cannons and gun ports (Kanonenluke, §8.2), crow's nest (lookout), flags (§4.7), pumps, galley/pantry, brig, cargo containers. A ship without sails doesn't sail, a ship without cannons can't fire, and so on. See §6 for the full station list.
- Free building plus assembly at the helm is the **main** way to get a ship. For beginners, ships can also be ordered from a shipwright:
- **Shipwright orders:**
  1. **Order:** at the shipwright in a seafarer village, the player picks a prebuilt ship type (e.g. sloop, brigantine, merchant cog) and pays in doubloons, plus some materials (logs, wool for sails).
  2. **Wait:** the build takes a few in-game days, longer for bigger ships (config value). The player gets a **receipt** item that shows progress and proves ownership.
  3. **Pick up:** when it's done, the player hands in the receipt. The ship appears at a free **berth** in the village dock, in the water and already assembled, so the player boards and sails away. From then on it is a regular ship that can be disassembled at the helm and modified freely.
  - The ship is only materialized at pickup, not when the build finishes. Until then it is a record only (same idea as §10.4), so finished ships never block the dock and can't be taken by others.
  - Docks have a few marked berths (markers in the village structure). If all are occupied, the shipwright asks the player to come back later.
  - Ship types are stored as structure NBT built by hand. At pickup, the template is placed directly into a new Sable sub-level at the berth, without placing the blocks in the world first (`docs/sable-notes.md` §2.5).
  - Later, optional: visible construction stages at the dock (frame → hull → masts), which needs one structure per stage.
- A **Helm block** (Steuerrad) is the ship's anchor point. Using it while docked triggers assembly.
  - Helm interaction since spike 3: on land, using the helm assembles. On a ship, using it steers, and sneak-using it with an empty hand disassembles.
  - Implemented in spike 1 (`ship/assembly`): terrain is decided by the block tag `pirates_n_ships:terrain`, and `pirates_n_ships:never_assemble` excludes more blocks. A dock that touches the hull is gathered with it, so ships are moored with a one-block gap. The default block limit is 2048.
- Assembly collects connected blocks, excluding world terrain, with a configurable block limit. They become a Sable sub-level.
- **Disassembly** happens at the helm when the ship is stationary and aligned. Blocks are placed back into the world, snapped to the grid.
  - Sable has no disassembly API (`docs/sable-notes.md` §2.4). We build it from Sable's public assembly pieces: the ship is levelled and its yaw snapped to a 90° step, and the target volume is checked for obstructions before the blocks are moved.
  - Assembling in water leaves an air pocket where the hull was, and disassembling leaves sea water inside the hull. Both need handling (spike 1).
- Each ship gets a persistent **ShipData** record: UUID, name, owner, crew, flag/faction, hull analysis cache.
- Ship naming happens via the helm GUI or a name tag on the helm.

### 4.2 Hull analysis: dry volume
- **Decision (implemented in `ship/hull`):** a plain "enclosed air" flood fill is not enough. An open-topped hull, or one with an open deck hatch, is connected to the outside, yet it has to stay dry while its rim is above the waterline. So the analysis uses a **spill-height model**: for every air cell it computes the lowest path to the outside (the cell's pour point). A cell is floodable volume, a **basin** cell, when it lies below its own pour point. Fully enclosed rooms, the inside of an open boat below its rim, and a holed hull are all covered by this one rule.
- **Compartments** are connected groups of basin cells. Solid blocks and openings separate them.
- **Openings** (doors, hatches, trapdoors, fence gates) are never part of a compartment. They are links between two compartments, or between a compartment and the outside, and the flooding simulation reads their open state. Opening or closing one never needs a new analysis.
- **Breaches:** a hull block destroyed by damage is tracked as a permanently open opening until it is patched, so the room behind it fills at a rate instead of at once.
- "Up" is a vector in ship-local coordinates, so a heeling ship can be re-analysed with its real tilt (a low rim going under water then starts to flood).
- Watertight: full blocks, slabs, stairs and glass. Not watertight: fences, walls, bars, panes, ladders, chains. The block tags `pirates_n_ships:watertight` and `pirates_n_ships:not_watertight` override this.
- Results are cached and recomputed only when ship blocks change (debounced, and able to run off the server thread). A full analysis of a 64×32×64 hull took about 93 ms in tests, so it must never run every tick.

### 4.3 Dry hull: rendering
- **Decision (Sable investigation, to be confirmed in spike 2):** use Sable's built-in **water occlusion regions** (`docs/sable-notes.md` §4.4) instead of our own mask mesh and renderer. Sable already renders a depth mask for each region before the water layer, from a common mixin that works on both loaders, and the regions follow the ship. Our part: turn dry compartments into regions, sync them to clients (Sable doesn't network them) and rebuild them when the hull changes. The bullets below describe the technique Sable implements.
- For enclosed compartments below the waterline, build a **water-mask mesh** (the inner faces of the dry volume).
- Render it with a depth-only render type before the translucent (water) layer, the same technique as vanilla's boat water patch, generalized.
- The mesh moves with the ship transform, so no chunk rebuilds are needed.
- Compatibility risk: shader packs. Plan a separate compatibility pass (Iris) later.
- Sable's regions are dry-or-not. A water surface inside a partly flooded compartment (§4.5) needs our own rendering later.

### 4.4 Dry hull: gameplay
- **Decision (to be confirmed in spike 2):** for positions inside a water occlusion region, Sable already cancels the in-water state, fluid pushing, swimming, drowning, the eye-in-fluid check, underwater fog and ambient particles (`docs/sable-notes.md` §4.4). We add our own mixins only for what is still missing after the spike. Expected gaps: item flotation, block placement checks and bubble columns.
- Mixins on fluid queries (entity in-water checks, fluid pushing, drowning, bubbles, item flotation, block placement checks).
- If a world position transformed into ship-local space lies in a **dry** compartment cell, it reports no fluid.
- Applies to players, mobs, items and particles.
- Spike 2 found these gaps in Sable's occlusion, not fixed yet: boats, fishing bobbers and mob pathfinding still see the water inside a dry hull.
- **Playtest finding:** the dry hull renders well, but slabs, stairs and trapdoors in the hull still show water in their empty half, although there is none. The occlusion region has to include the cells of watertight partial blocks on the hull's inside, so the whole cell is drawn dry.

### 4.5 Flooding and sinking
- Each compartment stores `waterLevel` (0..volume).
- **Breach:** when a hull block below the waterline is destroyed, or an opening is left open below the waterline, the compartment connects to the sea. Inflow rate scales with the opening size and its depth below the waterline.
- Water renders inside flooded compartments, either as a mask cut at the current water level or as a flat rendered surface.
- Compartments connected through open doors or hatches equalize.
- **Pump / bailing station:** removes water at a set rate. A player or crew member operates it.
- **Patching:** a repair item (planks + tar/pitch) placed in a breach closes it. **Implemented (G4, `ship/hull/pump`):** the bilge pump is a station block (`station/pump/PumpStation`) whose intake runs down its ship-local column through decks up to `pump_reach`; players pump by holding use, crew by a `PumpOrder`; the hull patch item places a watertight `hull_patch` block only into a tracked breach, and any watertight block in a breach closes its opening at once. Charcoal stands in for pine tar in the recipe.
- **Buoyancy:** effective displacement = hull blocks + dry volume − flood water. Sable computes buoyancy natively, for solid hull blocks only, and it can't be overridden or switched off per ship (`docs/sable-notes.md` §4.1, §4.2). So we keep Sable's hull-block buoyancy and apply the dry volume (upward) and the flood water (downward) as our own extra force at their centroids, which also produces list and heel. The constants must be tuned together in a playtest.
- Implemented in spike 2 (`ship/hull/runtime`): water only counts as sea when it is at the hull's own bottom. Up to nine probes sit at the lowest hull cells, and at least a third of them must be in world water. So a ship in a dry dock next to the sea, or on a cliff above it, does not float. A hull that is a third or more over water floats, and with less it is aground.
- **Known limit:** Sable's physics is 32-bit. Slow ships are affected first: beyond about 100,000 blocks from the world origin a ship slower than roughly 0.2 to 0.3 m/s reports a speed but stops moving, because one position step is larger than its movement per physics step. Beyond about 4 million blocks collision gets imprecise, and beyond about 8 million ships can sink into blocks. Recommend keeping sea travel within a few tens of thousands of blocks of the origin (details in `docs/sable-notes.md` §9.0b and §9.0e).
- A ship that loses buoyancy sinks. A sunk ship stays a sub-level resting on the seabed (with a config option to turn it back into world blocks after some time), so wrecks can be looted.

### 4.6 Damage
- Cannonballs and explosions destroy ship blocks, which may create a breach.
- There is no separate HP bar. Ship health = structural integrity + buoyancy. A HUD shows hull status and flooding per compartment.

### 4.7 Flags
- A **flagpole / mast-top flag** block. The flag a ship flies sets its **displayed allegiance**, which NPC ships, ports and forts react to.

| Flag | Effect |
|---|---|
| None / merchant flag | Default. Ports and navy treat the ship normally. |
| Navy (national) flag | Navy ships and outposts are friendly, pirates are hostile. Legitimate only with enough navy reputation, otherwise it counts as a false flag. |
| Jolly Roger | Pirates are friendly or neutral. Navy attacks on sight. Merchant NPC ships may surrender without a fight (morale check). Being seen under it raises the criminal score. |
| Custom flag | Made from banner patterns. Treated as neutral. |

- **False colors:** flying a flag that doesn't match the ship's real allegiance (e.g. a navy flag on a ship with a known bounty) lets you get close unnoticed. Being caught raises the criminal score heavily. Detection chance depends on distance, whether the observer has a manned crow's nest, and your criminal score.
- **Striking colors:** lowering the flag mid-fight signals surrender. NPC ships stop firing, and the attacker can board without resistance. Attacking a ship that has struck its colors is a crime.
- Changing the flag takes a few seconds at the flagpole (player, or the crew order "hoist colors").
- Flags flutter in the wind direction, doubling as a visual wind indicator (§5.1).
- **Wind on ships (P4):** the flag's downwind facing is computed in the ship's frame (the world wind sampled at the pole's world position, rotated by the inverse ship orientation) and re-checked every `flags.ship_update_interval_ticks`; on land every `wind_update_interval_ticks`.
- **Banner flags (G8):** a custom flag made from a banner shows the banner's base colour through a tint index and a `BlockColor`; patterns are not shown.
- **Size (playtest decision):** a flag is one block high and 1.5 to 2 blocks long, a real flag, not a small panel on the pole. Implemented at 1 × 1.5 blocks: a vanilla block model can't reach further than 24 pixels from the pole's centre. Two blocks would need a block entity renderer.

### 4.8 Ship customization
- **Ship name:** set at the helm. Shown on a nameplate block on the hull and in the HUD, logbook and bounty notices ("the *Black Gull*"). **Implemented (G8):** the nameplate shows the name of the ship it is on, refreshed every `ship_identity.nameplate_refresh_ticks`; off a ship, after disassembly or with `nameplate_shows_name` off it is blank.
- **Figureheads:** decorative bow blocks in several designs (mermaid, lion, eagle, skull, …). **Placement (P1):** the plate lands on the clicked block and the figure looks away from it, toward the player (`FACING` = the clicked face; on a top or bottom face the opposite of the player's direction).
- **Sails:** dyeable, and large sails can carry banner patterns.
- **Hull paint and trim:** dyeable planks and trim blocks (optional, since vanilla wood types already give a lot of variety).
- **Decor:** lanterns, stern windows, ship's bell, rope coils, captain's cabin furniture.
- **Visual quality (playtest decision):** every ship block gets a real 3D model (helm wheel with spokes, capstan drum, anchor, figureheads, containers, flagpole). Placeholder cubes are not acceptable for the ship blocks. Models are made in Blockbench and committed as hand-made assets like textures, with datagen still generating the block states and item models that reference them; until a model exists, a model built in code is the placeholder. **Pipeline:** the Blockbench project (`.bbmodel`) is committed in `art/models/`, the exported Java block model in `common/src/main/resources/assets/pirates_n_ships/models/block/` (entity models are exported as Mojang-mapped `LayerDefinition` code), and a render of each model in `art/renders/`. Models reuse vanilla block textures (`minecraft:block/spruce_planks`, `stripped_dark_oak_log`, `iron_block`, …) wherever they fit, so ships blend with vanilla wood and iron; small custom textures come from `tools/` scripts as before. Datagen writes only the block states and item models that point at the hand-made model, never the model itself. **Items (decision 2026-10-07; colours come from 16×16 palette sheets `textures/item/palette*.png` made by `tools/gen_item_palette.py`, a sheet is never resized because model UVs are fractions of the sprite):** items that are held in the hand, weapons first (swords, firearms), then tools such as the whistle, shackles and the spyglass, get 3D Blockbench item models (`java_block` format, exported to `models/item/`) with their own display transforms for first and third person, GUI, ground and frame; food (rum, hardtack, lime, salt pork, salted fish) and trade goods (cloth, spices, tobacco) get 3D models too (human decision 2026-10-07); coins and papers (doubloon, bounty proof) stay flat sprites. A block item with a hand-made item model is marked with `ModelContext.handMadeItem` so datagen writes no automatic block-item model for it (F8g).
- Customization is purely cosmetic and never changes performance stats.

### 4.9 Cargo and weight
- Every ship has a **cargo weight** = the contents of containers on board (crates, barrels, chests) plus heavy items such as cannons and cannonballs.
- Weight lowers the ship in the water (less freeboard, so it floods more easily) and reduces speed and turning (applied to Sable mass or as a drag factor). Decision: our own containers get a load block-state with a mass per state (Sable `sable:mass` overrides from datagen), so Sable updates mass and center of mass itself. Vanilla containers get a downward force instead (`docs/sable-notes.md` §10).
- The HUD and the helm GUI show the load level (light / laden / heavily laden / overloaded).

---

## 5. Sailing

### 5.1 Wind
- A **global wind field** per dimension: a direction and strength that drift slowly over time (noise-based).
- Weather multiplier: clear ×1.0, rain ×1.5, thunder ×2.2 (configurable). Gusts appear during storms.
- Optional regional variation (noise by position), behind a config toggle.
- Synced to clients for the HUD (wind indicator) and visuals (flags and sails flutter in the wind direction).

### 5.2 Sails
- Sail blocks come in sizes (small and large square sails, plus a fore-and-aft/lateen sail). Each has an area and an efficiency curve over the angle to the wind.
- **Trim states:** furled / half / full, set via the sail winch station.
- **Multi-block sails (playtest decision, replaces the one-block sail of spike 3):**
  - A **square sail** is two horizontal **yards** on the same mast, one above the other. Placing the lower yard under an upper one on the same mast links them, and the pair is one sail. Its area comes from the yard length and the distance between them. When the sail is hoisted, cloth is drawn between the yards: furled means the cloth is bundled at the upper yard, half means it reaches half way down, full means it reaches the lower yard. **Rule (F5a):** a yard is a straight row of yard blocks along one horizontal axis; its middle block marks the mast column. Two yards with the same axis are one sail when the lower one's middle block lies 2 to 8 blocks straight below the upper one's, with nothing but air or mast blocks between the two middle blocks, and no third yard in between. The cloth is a trapezoid, so the area is the mean of the two yard lengths times the distance between the yards. The trim lives on the upper yard; the lower yard and unpaired yards carry no sail.
  - A **triangular (fore-and-aft) sail** hangs from a **rope** (stay) that runs from a point high on the mast to a point further forward or aft, and its lower corner is tied to a **cleat**. The cloth fills the triangle between the rope and the cleat according to the trim. **Rule (F5b):** a stay is made by using a rope item on one cleat and then on a second cleat at most 16 blocks away, with the two at least 2 blocks apart in height. The higher cleat is the head (A), the lower end of the stay the tack (B). The sail exists when a third cleat (C) sits in the column straight below A, between B's height and A's; the cloth is the triangle A-B-C. Furled bundles the cloth along the stay, half lowers it to the midpoint of A-C, full fills the whole triangle. Area = half the cross product of AB and AC. **Convention (P1, checked):** the wind is a flow vector (`WindSample.dirX/dirZ`, the direction it blows toward; `/pirates wind set 270` = from the west = toward +X), both renderers and the force model use it, and the cloth bellies on the downwind side in the ship's frame (`sailing/client/ClothSide`).
  - The yard blocks, the rope and the cleat are the only placed blocks; the cloth is rendered, not built from blocks.
- Force = wind strength × area × trim × efficiency(angle), applied at the sail position, which also produces heel torque.
- Square sails work best downwind. Fore-and-aft sails allow sailing closer to the wind.
- No-go zone: sailing directly into the wind produces no forward force.
- Implemented: square sails from yards (F5a: `sailing/sail` holds the pure linking and geometry, `sailing/block/YardBlock` + block entity, `sailing/client/YardClothRenderer` draws the cloth, which bellies downwind and animates trim changes). A yard may be the foot of one sail and the head of the next, so three yards on a mast make two sails. Triangular sails (F5b): `sailing/sail/StayLinker` and `TriangularSail` (pure), `CleatBlock` with a block entity, `RopeItem` remembering its first cleat in a `rope_start` component, `StayClothRenderer` for stay and cloth; the stay is stored as an offset in the head cleat's own frame, so it survives a rotated disassembly (a mirrored structure template breaks it). Breaking a stay's cleat by hand returns the rope. `SailTypes.FORE_AND_AFT` has a centre-of-effort height of 0 because the cloth centroid is passed as the position. The one-block sails of spike 3 are gone. The bow is the direction the helmsman looks, i.e. the opposite of the helm block's facing. Sails don't push a ship that is not afloat.
- **Finding:** hollow block hulls have almost no righting moment in Sable, so the full heel torque of a sail capsizes a small ship within seconds. For now the roll and pitch part of our sail and keel torque is scaled down (`sail_heel_factor`, default 0.25). See §21.

### 5.3 Steering and other propulsion
- **Helm:** sets the rudder angle. Rudder torque scales with the ship's speed through the water. Implemented in spike 3: clicking the right third of the wheel (as the helmsman sees it) turns the rudder one step to starboard, the left third one step to port, the middle puts it midships, with three steps per side up to the maximum rudder angle. Steering with keys while holding the wheel comes later.
- **Keel (lateral resistance):** Sable's water drag is the same in every direction, so without extra sideways drag a ship would just drift downwind. We apply our own drag below the waterline, strong sideways and weak along the hull (`docs/sable-notes.md` §4.3). Tuned in spike 3.
- **Oars:** an optional small, slow propulsion source for windless conditions or small boats, operated by crew.
- **Anchor (capstan):** a dropped anchor applies strong drag and holds position. Raising it takes time. Implemented in spike 3: the anchor drops straight down from the capstan to the first solid block within the chain length (default 32), and doesn't hold if there is none.
  - **The anchor is a visible object (playtest decision, implemented in `sailing/anchor`):** it hangs outside the hull at the capstan's side as a real anchor model. Dropping lowers it on a chain to the sea floor with the sound of the running chain, a splash with particles when it enters the water, and the chain sound until it stops. Raising plays the chain back up. Later additions: a windlass (chain roll) block and a hawsepipe block that lets the chain run through the hull to the outside.

### 5.4 Waves (simulated)
- Sea state comes from weather: calm / moderate / rough / storm.
- Applied as roll and pitch forces on ships (configurable amplitude), plus spray particles, sounds and an optional client-side camera sway.
- Rough seas spill water over low decks into open compartments.

---

## 6. Stations

Stations are blocks on a ship that a **player or a crew member** can operate. They all share one interface: `occupy → operate → release`.

| Station | Function |
|---|---|
| Sail winch | Hoist, reef or furl the assigned sails |
| Helm | Steer (players; crew only in "hold course" mode) |
| Cannon | Load (powder + ball), aim, fire |
| Crow's nest | Extended view range; spots ships, land and monsters and reports them |
| Capstan | Raise or drop the anchor |
| Pump | Remove flood water |
| Repair kit / carpenter | Patch breaches |
| Flagpole | Hoist, change or strike colors (§4.7) |
| Galley / pantry | Stores provisions; a cook crew member boosts morale (§7.4) |

The crew operates a station by being attached to it, much like being seated. This avoids complex pathfinding on moving ships. Walking between stations on deck is a later improvement. Implemented in spike 4 (`station/`, `crew/npc/`): an invisible seat entity that lives inside the ship's sub-level, with the crew member riding it. It works on the server: the crew member stays at its station on a moving ship, upright in world space. How it looks in the client is the open point of the milestone 4 playtest (`docs/sable-notes.md` §9.0e). The station contract (`station/StationKind`, `StationBlock`, `Stations`) is: occupy, operate an order for a duration, release. Only the sail winch implements it so far.

---

## 7. Crew

### 7.1 Hiring
- Hire crew in seafarer villages (sailors), in taverns, or on pirate islands (pirates).
- Each crew member has a role skill (sailing, gunnery, carpentry, lookout), a wage in doubloons and morale.
- The crew limit per ship depends on the ship's size (number of bunks or hammocks).

### 7.2 Commands
- Issued with a **captain's whistle** (radial menu) or a **command GUI** at the helm.
  - **Playtest decision (implemented in `station/order` and `station/client`):** using the whistle opens a circular (radial) menu on the client with the orders. Choosing one sends it to the server, which issues it to the crew of the ship the player stands on. The sneak-use cycling from spike 4 is gone.
- Orders:
  - hoist / reef / furl sails
  - load cannons / fire (broadside port or starboard / at will)
  - man the crow's nest
  - raise / drop the anchor
  - man the pumps
  - repair
  - prepare to board
  - all hands to stations
- Assignment: automatic by skill, or manually per station.
- Feedback: crew members respond with voice lines or text, and the HUD shows which stations are manned.

### 7.3 Crew upkeep
- Wages are paid periodically from the ship's chest. Unpaid or starving crew lose morale, and low morale leads to desertion or mutiny (mutiny behind a config toggle).

### 7.4 Provisions
- A **galley / provisions store** on the ship (a pantry container block) holds food, fresh water and rum.
- The crew consumes provisions per in-game day, scaled by crew size. The HUD shows how many days of supplies are left.
- **Food:** any vanilla food counts, weighted by nutrition. Ship-specific foods are optional extras (hardtack, salted fish, salt pork) that keep longer.
- **Fresh water:** water barrels, refilled in ports or from rain catchers.
- **Rum:** a morale booster. Too much reduces the crew's work speed for a while.
- **Running out:** hungry crew work slower and lose morale. Thirsty crew lose morale fast and eventually desert or mutiny.
- **Scurvy (optional, config toggle):** after a long time at sea without citrus or fresh food, crew (and optionally players) get weakness and slower healing. Eating citrus cures it.
- **Spoilage (optional):** fresh food in the pantry slowly spoils on long voyages. Preserved foods don't.
- Provisions add weight to the cargo (§4.9).

---

## 8. Combat

### 8.1 Weapons (Waffen)
| Item | Notes |
|---|---|
| Rapier (Degen) | Fast, long reach, strong thrust, weak slash. Uses the melee combat system (§8.5). |
| Cutlass (Entermesser) | Shorter, heavier. Strong slash, weaker thrust. Uses §8.5. |
| Saber (Säbel, officers) | Balanced between rapier and cutlass. Uses §8.5. |
| Pistol (Pistole) | Single shot, long reload, high damage, short range |
| Musket (Flinte) | Single shot, longer range, slower reload |
| Ammunition (Munition) | Lead shot, crafted |
| Gunpowder (Schießpulver) | Vanilla gunpowder. No custom variant. |
| Grappling hook (Enterhaken) | See §8.3 |
| Cannon (Kanone) | Block, placed on ships or land, see §8.2 |
| Cannonball (Kanonenkugel) | Plus later chain shot (damages sails/rigging) and grapeshot (hits crew) |

Firearms get a reload animation, smoke and recoil. Rain reduces reliability (a misfire chance, configurable). **Aiming (P1):** holding right-click with a loaded gun is an aim session and releasing fires; a plain click fires at once (`firearms.aim.aim_min_ticks` 0); after `aim_steady_ticks` the spread is multiplied by `aimed_spread_factor`; the musket narrows the FOV while aimed (client `firearm_view.musket_zoom`). Loading is a hold session too; letting go after it finished never fires. Lowering and bar (P5): pressing sneak during an aim lowers the gun without firing and keeps it down while sneaking (`firearms.aim.lower_on_sneak`); the item shows a white bar filling over the reload and a full gold bar when loaded, with a "Loaded"/"Not loaded" tooltip. Poses (P3): PAL animations on the player rig, `pistol_aim`/`musket_aim` held while aiming with both arms pitched to the look, `pistol_reload`/`musket_reload` stretched to the reload ticks, in third and first person; a melee stagger overrides them (layer priority). **Implemented (G3, `combat/firearms`):** crossbow-style loading (hold to load, one lead shot and one gunpowder, bow pose), a `firearm_loaded` data component, a thrown `LeadBallEntity` with vanilla `arrow` damage attribution, smoke at the muzzle, a recoil payload (view kick and push), misfire in rain via `combat.rain_misfire_chance`, the musket reusing the pistol sound at a lower pitch. The ball inherits the deck velocity through Sable's projectile mixin.

### 8.2 Cannons
- Operated by a player or by crew at the cannon station.
- Loading sequence: powder → ball → (ram) → ready. Then fire.
- The projectile is a physics-aware entity that damages ship blocks (§4.6) and applies an impulse to the hit ship.
- Recoil applies an impulse to the firing ship.

**Implemented (Q2):** block damage obeys `mobGriefing` and the server's spawn protection (world positions, also for ship blocks), destroyed blocks drop their loot, a broken gun returns its powder and shot in survival, and a ball breaks `max(1, round(blocks_per_hit × |cos θ|))` blocks for the hit angle θ, bouncing off at over `glancing_bounce_degrees` with the impulse scaled the same way. **Implemented (P2):** the cannon is a two-block gun: the clicked block becomes the FRONT master (block entity, station), the REAR part lies behind it toward the placer; both need centre support, either half breaks both, one cannon drops; the barrel reaches one block ahead of the master (pivot 14 px up, muzzle 1.5 blocks from the pivot). The **swivel gun** mounts on `#swivel_mounts` (fences, walls, iron bars, brig bars) or a full block, has a continuous yaw and elevation in a block entity drawn by a renderer, follows the operator's camera while use is held (turned into the ship's frame) and fires on release; powder then one ammo item (`cannons.swivel.ammo`), lower velocity, damage and range, no block damage by default, 60-tick reload; it is a station taking the FIRE order. Multi-block stations resolve to their master (`StationBlock.stationPos`) and seat crew beside their footprint. **Implemented (G9, `combat/cannon`):** the cannon is a station block with a facing and an elevation step; powder then ball loads it, sneak-use aims, use fires. The ball leaves the world-space muzzle with the ship's velocity at that point added (`ShipBody.velocityAt`), recoil and impact push the hulls through `ShipBody.applyImpulseNow`; a ball breaks the ship block it hits (plus `blocks_per_hit − 1` along its path), and a hole below the waterline becomes a breach through the hull runtime. Balls splash into water and sink over a few blocks. Crew at a cannon can fire it once a player has loaded it.

### 8.3 Grappling hook
- **Version 1:** throw or shoot the hook at a block (including blocks on a moving ship) and pull yourself to the hook point.
- **Version 2:** rope physics: swing, climb up and down, balance on the rope between two ships.
- The hook attaches in ship-local coordinates, so it follows the moving ship.

**Implemented (G11, `combat/grapple`):** the thrown hook latches onto another ship's block (plot position); while the thrower stands on a ship, a rope spring between the thrower ship's nearest block and the hook pulls both bodies with equal and opposite horizontal forces at centre-of-mass height, through the force group "grapple" every physics substep, until the ends are within `hold_distance + hold_slack` or the rope stalls against the touching hulls (`GrappleRules.Holding`, judged by distance progress only); sneak-use releases, walking beyond the rope length snaps it. From land the rope drags the hooked ship gently.

### 8.4 Boarding
- Grappled ships can be pulled closer (with multiple hooks or crew assistance).
- Crew with the "prepare to board" order jump over and fight.
- A ship is **captured** when its captain is defeated or all hostile crew are gone. The player then becomes the owner.
- Fights on deck use the melee combat system (§8.5). Boarding is where skill-based duels matter most.

### 8.5 Melee combat (skill-based swordplay)

Goal: sword fights are about timing and reading the opponent, not click spam. The system only applies to the mod's swords. Vanilla weapons keep vanilla behavior.

**Actions**

| Action | Default input | Effect |
|---|---|---|
| Slash | Left click | Wide, short arc. Medium damage, fast recovery. |
| Thrust | Hold left click, release | Narrow ray, long reach. High damage, slower, long recovery if it misses. |
| Guard | Hold right click | Blocks frontal hits completely (`melee.guard_absorbs_all`; off = only the weapon's guard reduction); drains stamina while held and per blocked hit: the weapon's block cost plus `melee.guard_absorb_stamina_per_damage` × absorbed damage. A hit the stamina cannot pay breaks the guard: it lands with the weapon's reduction and the defender staggers. Hits from outside the guard arc ignore the guard (P9). |
| Parry | Tap right click shortly before a hit lands | Deflects the hit completely, staggers the attacker, opens a **riposte** window. |
| Riposte | Attack during the riposte window | Bonus damage, can't be parried. |
| Feint (Q1, NPCs; player input later) | Cancel an attack during wind-up (`CombatRules.feint`): a short recovery without guard or parry, the baited parry runs out into its lockout | Baits a mistimed parry. |
| Directional attacks/parries (later, optional) | Mouse movement picks the direction | A parry only works in the matching direction (Mount & Blade style). Off by default, config toggle. |

**Rules**
- **Stamina:** attacks, guarding and failed parries cost stamina. At zero stamina the player can't guard or parry and gets staggered more easily. Stamina regenerates when not attacking. Shown in a small HUD bar while holding a sword.
- **Attack phases:** wind-up (telegraph, readable animation) → active (hit frames) → recovery (vulnerable). Each weapon defines the timings for each phase.
- **Parry window:** about 6–8 ticks (configurable), deliberately generous for multiplayer latency. Each failed parry briefly blocks the next one, which prevents parry spam.
- **Stagger/poise:** being parried or hit by a thrust while in recovery causes a short stagger (no actions, slowed movement).
- **Mixed fights:** a parry also deflects vanilla melee hits from mobs. Projectiles (pistols, muskets) can't be parried.

**Technical design**
- `common`: combat state machine per entity (an attachment), weapon definitions (data-driven via datapack JSON: timings, damage, reach, arc, stamina costs), hit resolution (ray for thrust, arc sweep for slash), GameTests for resolution logic.
- **Client:** intercepts attack/use input while holding a mod sword. It plays the animation immediately (prediction) and sends an action payload with a client timestamp. **Implemented (G6):** `MeleeInputClassifier` samples both buttons per tick: attack released before `hold_to_thrust_ticks` is a slash, after it a thrust (both start on release); use pressed is guard down, released after `parry_tap_ticks` is guard up, released earlier is guard up plus parry. Vanilla attack, use and block breaking are cancelled through `ClientEvents.INTERACTION_KEY` while a mod sword is held. `MeleeAnimations` (predict/play/stop) is the hook milestone 8 fills with PAL; the no-op default swings the hand.
- **Server:** authoritative. It validates range, cone, cooldowns and stamina, then resolves parries using the server tick plus a latency allowance. It broadcasts the resulting state so other clients animate correctly. **Implemented (G6):** `MeleeActionPayload` in, `MeleeStatePayload` out to tracking players (phase, attack, guard, riposte, lockout) and to the owner with stamina and refusals, throttled by `melee.stamina_sync_interval_ticks`.
- **Animations:** the Player Animation Library (PAL) for first- and third-person player animations, available on both loaders (§2; chosen in G5, see §21 and `docs/animation-libraries.md`). NPC animations use GeckoLib.
- Works on moving ships: hit checks use positions relative to the sub-level where needed.

**NPC duelists** (implemented in M3, `mob/ai/DuelistAttackGoal` with the pure `combat/melee/npc/DuelistBrain`: reaction delay, one parry roll per incoming attack from the skill tier, guard on a failed roll, riposte at once, thrust into recovery or stagger; sounds since P7/P8: `combat/melee/sound` plays one DRAGON-STUDIO cue per attack by its outcome (a vanilla sweep whoosh on a miss, lower for a thrust; `hit` on flesh; `hit_heavy` for a thrust, riposte or staggering hit; `clash` for a parry, a quieter guard or blade on blade; `hit_armor` on a chestplate; feints and staggers silent; unsheathe), server config `melee.sounds`; chase since M5: `mob/ai/ChaseRules` closes at `duelist_chase_speed`, stops inside reach with hysteresis, attacks only when the gap predicted for the first hit frame is inside reach, keeps walking during the wind-up, paths at vanilla's cadence; feints since Q1: `planFeint` rolls the tier's `feintFrequency`, the planned attack aborts when the opponent begins a parry or at wind-up tick 2 against a guard, and the follow-up skips the attack pause; `melee.npc_feints` toggles it)
- Pirates, navy soldiers and officers use the same state machine and the same rules as players: they telegraph attacks, guard, parry and riposte.
- Skill tiers per NPC type set the parry chance, reaction time and how often they use feints. For example, a sailor is clumsy and a pirate captain is dangerous.
- Duel bosses: named pirate captains with unique movesets, as quest and boarding targets.

**Compatibility:** no hard dependency on combat overhaul mods (e.g. Epic Fight, Better Combat). Optional compatibility can come later.

---

## 9. Mobs and NPCs

| Mob | Behavior |
|---|---|
| Pirate | Hostile to most players by default (depends on reputation). Cutlass duelist (§8.5) and pistols. Spawns on pirate islands and pirate ships. Hireable when reputation is high enough. |
| Sailor (Matrose) | Neutral villager-like NPC in seafarer villages. Hireable. |
| Navy soldier | Patrols outposts and ships. Hostile to players with a bounty. Muskets. |
| Navy officer | Leads soldiers and accepts pirate turn-ins and bounty claims. Gives quests. Skilled saber duelist (§8.5). |
| Shark (Hai) | Hostile in deep water, attracted to blood (injured entities in water). |
| Kraken | Rare boss (§12). |
| Sea chest | Entity, see §11. |

Models and animations use GeckoLib. Textures are 16×16-scale pixel art.

---

## 10. World and economy

### 10.1 Structures
- **Pirate islands** (Piraten-Inseln): a jigsaw structure with a camp, tavern, docks, buried treasure and a pirate captain. Pirates spawn there, and loot can be traded or fenced there.
- **Seafarer villages** (Seemannsdörfer): coastal villages with docks (with marked ship berths), a shipwright (ship orders, §4.1), a tavern and a harbor master. Villagers and sailors spawn there.
- **Navy outposts / forts**: a turn-in point for pirates and bounties, with patrols.
- **Wrecks**: sunken ship structures with loot.
- **Treasure maps**: lead to buried treasure chests (Schatzkisten) with special loot tables.

### 10.2 Currency and loot
- **Gold doubloons** (Golddublonen) are the main currency, used for wages, bounties, trading and hiring.
- Loot (Beute) from treasure chests, captured ships and wrecks can be sold to fences on pirate islands or traders in villages.

### 10.3 Trade and cargo
- **Trade goods:** a set of cargo items, each with a base price. Vanilla items are reused where they exist (sugar, fish, timber/logs, iron). New items only for typical colonial goods vanilla lacks (tobacco, spices, cloth, rum). Stored in cargo crates and barrels (cargo containers hold one good type in bulk).
- **Markets:** every port (seafarer village, navy outpost, pirate island) has a harbor master or trader with a market screen. **Implemented (G10, `trade/desk`, `trade/client/MarketScreen`):** the harbor master's desk block is bound to a port (by world generation through a port-locator hook, or by `/pirates trade desk bind`); using it opens the market screen, and every request is checked against the desk's binding and reach on the server. Each port has goods it **produces** (cheap) and goods it **demands** (expensive), derived from its biome and type.
- **Dynamic prices:** buying raises a good's price and selling lowers it, recovering slowly over time. This prevents infinite money loops.
- **Trade runs:** buy cheap in one port and sell where demand is high. Longer and riskier routes (through pirate waters) pay more.
- **Contracts:** harbor masters offer delivery contracts (bring X to port Y by day Z) as a simpler entry point to trading. They link to the quest system (§15).
- **Plunder:** cargo on captured or sunk ships can be taken. Pirate fences buy plundered goods at a discount, no questions asked. Selling plundered goods in navy ports is risky and can raise the criminal score.
- **Port fees (optional):** small docking fees in navy ports, waived for high navy reputation.
- Cargo weight affects the ship (§4.9), so a fully laden merchant ship is slow and an easy target.

### 10.4 World simulation (later stage)
A server-wide simulation that makes the sea feel alive between the ports, without simulating every ship physically.

- **Port registry:** a saved-data record of every generated port (seafarer village, navy outpost, pirate island) with its position, faction and market. Ports are added when their structure generates.
- **Faction state:** a small state machine per faction (Navy, Pirates, Merchants) with values such as aggression, wealth and tension between factions. Player actions and world events shift them (e.g. many pirate kills by the navy raise pirate tension).
- **Abstract voyages:** NPC ships exist mostly as abstract records (route, cargo, faction, progress) that move along routes between ports on the server tick. Only when a player comes within range is a voyage **materialized** as a real Sable ship with crew. When no player is near any more, it is turned back into a record. This keeps the cost low no matter how many ships are travelling.
- **Trade convoys:** merchants travel between ports that produce and demand goods (§10.3) and actually move goods, nudging market prices. Pirate players can intercept and plunder them, which feeds the criminal score (§13.1) and Merchant/Navy reputation.
- **Navy patrols:** sail between navy outposts and hunt ships flying the Jolly Roger or with high bounties.
- **Pirate raids on navy settlements:** while a player stays at a navy outpost or a navy-aligned village, the chance of a pirate raid slowly rises with the time spent there. It is capped and has a long cooldown, so raids stay rare events rather than a routine.
- **Retaliation:** when the navy has been very aggressive against pirates (high pirate tension), pirates are more likely to raid navy settlements or attack navy convoys, and the reverse.
- Events are announced in advance where it makes sense (sails on the horizon, a warning bell in the village), so the player has time to react.
- This also answers how trade stays interesting: routes have real traffic, risk and opportunities.

---

## 11. Sea chest (Seemannstruhe)

**Implemented (S1, `seachest`):** block, item with contents, worn in the chest slot (no jump, no sprint, no swim, slower, dragged down in water), floating entity with buoyancy, current and wind drift, contents preserved in every state; paddling is still open.
- An item and block with **double-chest capacity**.
- **Carried on the back:** while worn, the player can't jump, sprint or swim. Walking is slowed (configurable). Drowning risk applies, since the chest drags the player down.
- **Placed in water:** becomes a floating entity that drifts with currents and wind.
- **With a paddle:** a player sitting on it can paddle it like a small boat.
- Contents are preserved in every state (item, worn, block, entity).

---

## 12. Hazards and weather

**Implemented (H1, `hazards`):** waterspouts (thunderstorms at sea; pull and lift within 8 blocks, sails torn a step at a time) and whirlpools (deep ocean, drifting; pull, spin and drag-down within 12 blocks) as entities with force fields on entities, boats, players (client push payload) and ships (force group `sea_hazards`, mass-capped so big ships barely notice), spawner with per-player caps and `/pirates hazard` commands; all strengths and frequencies in `hazards.*`. **Implemented (K1a, `mob/kraken`):** the kraken lurks deep, rises beside a ship or swimmer within `detection_range`, and its eight tentacles (part entities with their own pools; cut ones regrow) grip the hull near the waterline and pull the ship down through the `sea_hazards` force group, beat masts, sweep the deck and drag swimmers under; eyes take triple damage; it retreats below 30 % health; spawns in the deep ocean with the night and thunder bonuses (`hazards.kraken.*`, `mobs.kraken.*`); drops a beak and ink. Its Blockbench look is K1b.

**Implemented (M4, the shark):** `mob/entity/Shark`, a water animal with GeckoLib on a script rig (`art/README.md` "Shark rig"); hunts swimmers only (players, villager-like mobs, animals in water, in range and in sight; never anyone riding, seated or tracked on a ship, never creative or spectator, never sharks), circles for `circle_ticks` then charges and bites (`bite_cooldown_ticks`, `bite_damage`, `knockback`), charges at once below `frenzy_health_fraction`, gives up after `give_up_ticks`, retaliates for `grudge_ticks`, `peaceful` disables all of it; natural spawns in `#is_ocean`/`#is_deep_ocean` 3 blocks under sea level through the platform's `registerNaturalSpawn` (NeoForge: the `pirates_n_ships:natural_spawns` biome modifier generated by datagen, reading the config at server start) and `registerSpawnPlacement`.
- **Waves:** see §5.4.
- **Waterspouts** (Wasserhosen): spawn during thunderstorms over the ocean. They pull in and lift entities and small ships, and damage sails.
- **Whirlpools** (Wasserstrudel): rare, stationary or slowly drifting. They pull ships toward their center, apply rotational force, and can drag small boats under.
- **Kraken** (rare): spawns in the deep ocean, more likely at night and during storms.
  - Multi-part entity with tentacles that grab ship blocks, pull the ship down, destroy masts and swipe crew off the deck.
  - Weak spots on the eyes and tentacles.
  - Drops unique loot.

All hazards can be turned off individually and have frequency settings.

---

## 13. Law system

### 13.1 Criminal score
- Tracked per player and per NPC.
- Increases through crimes: attacking or killing navy or villagers, attacking neutral ships, theft from village chests, piracy (capturing non-pirate ships).
- Decays slowly over time (configurable) and can be reduced by paying fines at navy outposts.

### 13.2 Bounties (Kopfgeld)

**Implemented (L1):** proofs and shackled prisoners are turned in by right-clicking a navy officer (hostile officers refuse); pirate NPCs pay `law.pirate_turn_in.<tier>`; the notice board block lists every bounty and places new ones for doubloons (`law.bounty.*`, `player_bounty_minimum`). Notices in villages and outposts wait for the world structures.
- When the criminal score passes a threshold, the navy automatically places a bounty that scales with the score.
- Players can place bounties on other players or NPCs by paying doubloons at a notice board.
- **Claiming a bounty:** defeat the target and bring a proof item, or capture the target alive (shackles) and deliver them to a navy officer.
- **Pirate turn-in:** captured pirate NPCs can be delivered to the navy for doubloons.
- Bounty notices are posted on notice boards in villages and outposts.

### 13.3 Brig (prisoners)
- **Capture:** a defeated (not killed) NPC with low health can be put in **shackles**. Players with a bounty can be captured the same way (PvP, config toggle).
- **Brig:** a cell area on a ship, made of brig bars and a lockable brig door. A shackled prisoner led into the cell (on a lead, like a mob) stays there and can't escape while the door is locked.
- **Prisoners on board:** take a small share of provisions. They may try to escape when the door is open or the crew's morale is low, and they free themselves if the ship is captured by their faction.
- **What to do with them:**
  - Deliver pirates and bounty targets to a navy officer for the bounty (§13.2).
  - Ransom captured navy officers or merchants at their faction's port.
  - Press-gang captured sailors into your crew (low morale at first). Pirates only, raises the criminal score.
  - Release them (small reputation gain with their faction).
- Captured enemy captains are worth extra and are needed for some quests.

---

**Brig key (P1):** locking and unlocking a brig door needs a brig key (an iron ingot over an iron nugget); any key works on any brig door (keys are the security, cells are shared among a crew); the key never changes the owner; the owner can still open a locked door bare-handed; without a key nobody else, no mob and no redstone opens a locked door; brig bars connect to door halves.

## 14. Survival

**Implemented (S2, `survival`):** cold water adds to vanilla's freezing meter in `#pirates_n_ships:cold_water` (frozen and cold oceans, frozen river), so the frost overlay, slowdown and freeze damage are vanilla's; boats, ship decks and dry hulls, leather armour, creative and the `warm` effect (rum, `#warming`) prevent it; swimming costs `swim_exhaustion_multiplier` × vanilla's exhaustion. New hook `CommonEvents.ITEM_USE_FINISH`.
- **Cold water:** in cold and frozen ocean biomes, being in water builds up a freezing meter (similar to powder snow). It ends in freezing damage. Boats, dry hulls and warming items prevent it.
- **Swimming hunger:** swimming increases exhaustion (configurable multiplier).

---

## 15. RPG layer
- **Reputation:** separate scores with Pirates, Navy and Villagers. Actions shift them. Reputation affects prices, hiring, hostility and available quests.
- **Honor/status:** a captain's rank based on deeds (ships captured, bounties claimed), shown in the ship's flag or title.
- **Careers:** rank ladders built on reputation. Promotions need reputation plus deeds or quests, and each rank unlocks rewards.
  - **Navy career:** Midshipman → Lieutenant → Captain → Commodore → Admiral. Promotions come from quests by navy officers (patrols, convoy escorts, hunting pirates). Rewards: pay in doubloons, cheaper or exclusive ships at navy shipyards, crew recruited at outposts, officer gear (coat, saber), and the right to fly the navy flag without false-flag penalties (§4.7). Attacking navy or merchant ships while in service counts as **desertion**: the rank is lost and a bounty is set.
  - **Pirate infamy:** the mirror image, from Deckhand up to Pirate Lord, driven by plunder, captured ships and bounty size. Higher infamy makes pirate ships friendlier, gives better prices at fences, lets the player recruit pirate crews and unlocks pirate-captain quests. It also makes the navy hunt the player harder (more patrols, §10.4).
  - **Privateer (middle path):** the navy grants a **letter of marque** that makes attacking pirate ships legal and pays bounties for them, without full navy service or its duties. Attacking navy or merchant ships voids the letter.
  - The two ladders exclude each other: joining the navy requires low infamy, and a navy rank is lost when the player turns pirate.
- **Quests** (Aufträge) from navy officers, harbor masters and pirate captains: escort, deliver cargo, hunt a ship, find treasure, kill a monster.
- **Story (optional, later):** a light questline, e.g. a legendary pirate, a cursed treasure and the kraken.

---

## 16. Audio and ambience
- Sounds for creaking hulls, sail flapping, rope, cannon fire, pistols, splashes, flooding water, wind, the crew and the kraken.
- **Hull creaking (playtest decision, first sound to build):** when a ship rolls, its planks creak now and then, the ordinary wooden creak one expects on a ship: occasional, quiet, tied to the rolling motion, never a constant loop. Implemented in `audio/` with the human's Pixabay creak recording cut into two variants (A1) (`sounds.json` is generated; real recordings go into `assets/pirates_n_ships/sounds/`).
- **Music:** a sea music manager plays ambient and shanty tracks while at sea, combat music during ship battles, and calm tracks in harbors. Music discs.
  - **Pools (G1):** `music.sea` holds the ambient sea tracks, `music.shanty` the shanties. Aboard a ship the shanty pool plays, at sea but not aboard (an ocean, deep ocean or beach biome) the sea pool, anywhere else vanilla music. Both pools and the music gaps are client config; `music_enabled = false` leaves vanilla music alone. Battle and harbor music come with their milestones.
  - **Sound pipeline:** the human drops the source files (mp3) into `raw_sound/` (git-ignored). `tools/sounds/manifest.json` lists every sound with its source file, target path, kind, title, author, source URL and license; `tools/convert_sounds.py` converts them with ffmpeg to Ogg Vorbis (music stereo, effects mono) into `common/src/main/resources/assets/pirates_n_ships/sounds/` and writes `docs/credits.md`. `sounds.json` is generated from the entries every module contributes through `data.sounds(...)`.
- **Licensing:** traditional shanties are public-domain songs, but only use our own recordings or recordings with a compatible license. The first music and gun sounds come from Pixabay (Pixabay Content License: free use and modification, no attribution required, no standalone redistribution), credited in `docs/credits.md` anyway.

---

## 17. Configuration (Einstellungen)

All gameplay settings live in the **server config** (synced to clients). Audio and visuals live in the **client config**. Config values are defined once in `common` and read through our own `config` wrapper, so the backing library can be swapped. The table lists the main settings. The config classes define more (every strength and threshold has a value), and they are the reference. A client config section must not have the same name as a server section, because the config screen's translation keys don't include the config type. The in-game config screen uses NeoForge's generated config UI on NeoForge. On Fabric, use a Mod Menu integration or a simple fallback screen (behind a platform method).

| Group | Toggles and values |
|---|---|
| Ships | max block count, assembly enabled, disassembly thresholds (stillness, levelness), sinking enabled, wreck persistence time, shipwright orders on/off, build time per ship type, order prices |
| Flooding | enabled, inflow rate, pump rate |
| Wind | variability, weather multipliers, regional variation on/off |
| Sailing | sail force scale, rudder strength, keel drag (on/off + strengths), anchor strength and durations |
| Waves | enabled, amplitude, camera sway (client) |
| Hazards | waterspouts / whirlpools / kraken: enabled + frequency each |
| Crew | wages on/off, mutiny on/off, max crew multiplier |
| Provisions | consumption on/off, consumption rate, scurvy on/off + onset time, spoilage on/off |
| Cargo & trade | cargo weight affects ships on/off + weight factor, price volatility, price recovery rate, port fees on/off |
| Flags & brig | false-flag detection strength, NPC surrender on/off, player capture (PvP) on/off, prisoner escapes on/off |
| Combat | firearm misfire in rain, cannon block damage on/off, damage multipliers |
| Melee | skill-based combat on/off (off = vanilla-style melee for mod swords), parry window (ticks), stamina costs and regen, directional mode on/off, NPC skill multiplier |
| Law | criminal score enabled, decay rate, bounty threshold, player bounties on/off |
| Survival | cold water on/off + time to freeze, swimming hunger multiplier |
| World | structure spacing / frequency per structure type, mob spawn weights |
| World simulation | enabled, max simultaneous voyages, materialize radius, convoy frequency, patrol frequency, raid chance growth + cap + cooldown, retaliation on/off |
| Audio (client) | music on/off, music volume, ambience volume |

---

## 18. Multiplayer, performance and compatibility
- All gameplay state is server-side, with ship and crew state synced through custom payloads. Hull analysis runs on the server. Mask meshes are built on the client from synced compartment data.
- **Budgets:** hull analysis is incremental and off the hot path. No per-tick full ship scans. Crew AI is throttled when no player is nearby.
- **Compatibility targets:** Create (optional), Iris/Oculus shaders (water mask), Jade/WTHIT, JEI/EMI.

---

## 19. Testing strategy
- **GameTests** for all logic: assembly/disassembly, hull analysis (known hull shapes → expected compartments), flooding rates, criminal score/bounty thresholds, station operate logic, market price changes, provision consumption, prisoner/brig rules, flag detection, world simulation (voyage progress, raid chance and cooldown, faction state changes), melee resolution (parry windows, stamina, hit arcs/rays), config toggles actually disabling features.
- GameTests are defined in `common` (vanilla GameTest framework). They run via the NeoForge game test server, and later also via Fabric's runner.
- **Datagen** for all JSON: models, blockstates, recipes, loot tables, tags, lang, worldgen. Providers live in `common` where possible, and generated resources are output into `common/src/generated/resources` so both loaders ship them.
- **CI** (GitHub Actions): build every enabled loader module on each push. Once Fabric is enabled, a Fabric build failure blocks merges just like a NeoForge one.
- **Manual playtest checklist** per milestone, with screenshots and logs fed back into Claude Code.

---

## 20. Roadmap

| # | Milestone | Done when |
|---|---|---|
| 0 | Project setup | MultiLoader-Template builds. `common` + `neoforge` enabled. Sable loads in the NeoForge dev client. A test block registered from `common` and a GameTest from `common` both run. Platform service skeleton exists. |
| 1 | **Spike: assembly** | A helm assembles a small hull into a floating sub-level, which disassembles back to blocks |
| 2 | **Spike: dry hull** | No water renders inside the hull, and the player doesn't swim below deck |
| 3 | **Spike: wind + sails** | A sail moves the ship relative to the wind, the helm steers, the anchor holds |
| 4 | **Spike: crew station** | An NPC attached to the sail winch hoists the sails on command while the ship moves |
| 5 | Config framework + weapons | All §8.1 items exist (swords still with vanilla-style melee), firearms work, the config screen works |
| 6 | Flooding + damage | Cannon damage → breach → flooding → sinking, plus pump and patch |
| 7 | **Melee combat core** | Slash, thrust, guard, parry, riposte and stamina work player vs. player and player vs. a test dummy. Server-side resolution covered by GameTests. Animation library chosen. |
| 8 | Melee animations + NPC duelists | First- and third-person sword animations with readable telegraphs. A test NPC uses the same system (telegraph, guard, parry) with skill tiers. |
| 9 | Cannons + grappling hook v1 + boarding | Full ship-to-ship combat loop, ending in a deck duel |
| 10 | Ship identity + shipwright | Flags (incl. striking colors), ship name, figureheads, dyeable sails, decor blocks, shipwright orders with receipt and berth pickup |
| 11 | World | Islands, villages, outposts, wrecks, treasure maps |
| 12 | Mobs | Pirates, sailors, navy, sharks (human mobs use the §8.5 duel AI) |
| 13 | Law + brig | Criminal score, bounties, turn-ins, shackles, brig, ransom, false-flag detection |
| 14 | Trade + cargo | Trade goods, port markets with dynamic prices, contracts, cargo weight affecting ships, plunder |
| 15 | Crew command system + provisions | Hiring, all orders, wages and morale, galley, provisions consumption, scurvy |
| 16 | Sea chest + survival | §11, §14 |
| 17 | Weather and hazards | Waves, waterspouts, whirlpools |
| 18 | Audio | Sounds, music manager |
| 19 | RPG + careers + kraken + duel bosses | Reputation, quests, navy ranks, pirate infamy, letter of marque, named pirate captains, the kraken |
| 20 | World simulation | Port registry, faction state, abstract voyages that materialize near players, trade convoys, navy patrols, raids and retaliation (§10.4) |
| 21 | Melee extras (optional) | Feints, directional attacks/parries mode |
| 22 | Fabric port | Enable `fabric/`, implement platform services, all GameTests pass on both loaders, CI builds both |

Spikes 1–4 are throwaway-quality prototypes that prove feasibility. They may live in `neoforge/` while exploring Sable, but must be moved into `common` (behind platform services) before milestone 5.

---

## 21. Open questions
- ~~Sable's API for assembly, forces and custom buoyancy~~ **Resolved (Sable investigation):** assembly and forces have an API, disassembly and custom buoyancy don't. See `docs/sable-notes.md` §2–§4 and §10.
- ~~Per-ship buoyancy override~~ **Resolved:** not possible. Dry volume and flood water are applied as an extra force on top of Sable's hull buoyancy (§4.5).
- ~~Cargo weight vs. Sable mass~~ **Resolved:** a load block-state with per-state mass for our own containers, and a downward force for vanilla containers (§4.9).
- ~~Sub-level from a structure template~~ **Resolved:** yes, directly (§4.1, `docs/sable-notes.md` §2.5).
- ~~Player animation library for melee combat~~ **Resolved (G5, `docs/animation-libraries.md`):** Player Animation Library (PAL, `com.zigythebird.playeranim`, MIT) 1.1.6+mc.1.21.1, the maintained successor of PlayerAnimator, with builds for NeoForge and Fabric and a Mojang-named common artifact. It animates the third-person player model and, in its `THIRD_PERSON_MODEL` first-person mode, the first-person arms and held sword. It does no networking: our server broadcasts the melee phase and clients play one animation per phase. All PAL calls sit in one client package in `common` behind our `MeleeAnimations` interface. Fallback: PlayerAnimator 2.0.4+1.21.1. Integrated in G12: `common` compiles against the Neo jar (the Common artifact is intermediary-named), no force block needed, PAL loads on the dedicated server without harm. Still to check: first-person rendering on a rolling ship, and the placeholder animations' rotation signs.
- Melee input defaults: do hold-to-thrust and tap-to-parry feel good with mouse buttons, or are dedicated keybinds better? Decide by playtesting.
- ~~Config library~~ **Resolved (milestone 0):** NeoForge's native `ModConfigSpec` on NeoForge, and Forge Config API Port (the same API) on Fabric, both hidden behind `Services.CONFIG`. Common declares values through our own wrapper (`core/config`, see §3.3).
- ~~`sable-common` vs. loader artifacts~~ **Resolved:** everything we need, including event subscription, is in `sable-common`. Much of it is outside Sable's `api` packages, so all Sable calls go through one adapter package in `common` (`docs/sable-notes.md` §7).
- ~~Loader-specific dry-hull rendering hooks~~ **Resolved for the basic dry hull:** Sable's water occlusion renderer is hooked from a common mixin, so we need no rendering mixin of our own (§4.3). Still open for the water surface in partly flooded compartments.
- Do our dry-volume force and Sable's native hull buoyancy tune well together, without over-buoyant or unstable ships? Decide in the spike 2 and milestone 6 playtests.
- Does Sable's water occlusion scale to many ships? Its lookup loops over all regions for every entity each tick. Profile in spike 2.
- Do mobs riding a seat entity inside a sub-level render, interpolate and interact correctly? Check in spike 4.
- Stability: hollow block hulls barely right themselves (spike 3). Measured: a 5×5 plank boat, 4 high, lies about 20° bow up at rest and runs 35 to 46° bow down under a small sail, and a stone bottom layer brings that to about 16°. Do we keep scaling down the heel torque, add ballast or keel blocks with real mass low in the hull, add our own righting moment from the hull analysis, or apply the sail's drive lower? Decide after the milestone 3 playtest. This is the biggest open risk for how ships feel.
- Sail force scale: the spike 3 agent thinks 1.0 is too strong for Sable's masses and expects something like 0.3 to 0.5. Decide in the milestone 3 playtest.
- ~~Is the continuous rolling of a floating ship intended?~~ **Resolved (playtest):** no. It is undamped roll: nothing resists the rolling motion. A roll and pitch damping torque proportional to the angular velocity, with config values, is added in the sailing runtime (`HullDampingModel`, defaults 1.5 / 1.5: a kicked 7×17 hull settles in about 3 s instead of rocking for 8). To be confirmed in game: the endless rolling could not be reproduced headlessly.
