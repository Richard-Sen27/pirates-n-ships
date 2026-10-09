# Fabric port

Milestone 22 (design.md §18). **FAB1** enabled the `fabric/` module with the server side and the GameTests; **FAB2**
ports the client. This file records what the Fabric module does, where it differs from NeoForge, and what is left.

## Build

- `settings.gradle` includes `fabric`; `./gradlew build` builds and tests both loaders.
- Loom is `net.fabricmc.fabric-loom-remap` **1.17.21** (Minecraft 1.21.1 is obfuscated, so the remap variant). Loom
  refuses mod dependencies built with a newer Loom minor version, and GeckoLib 4.9.3 for Fabric was built with 1.17, Sable
  2.0.6 and Veil with 1.16. Loom 1.17 needs **Gradle 9.5** (the wrapper), which Sable uses with the same ModDevGradle
  2.0.140 as we do. Loom also needs the Gradle JVM to be Java 21: `gradle/gradle-daemon-jvm.properties`
  (`toolchainVersion=21`) makes Gradle start its daemon on a locally installed JDK 21, whatever `JAVA_HOME` is.
- Dependencies (`fabric/build.gradle`): Fabric API `fabric_version` (0.116.12+1.21.1, GeckoLib's minimum), Loader
  0.19.3, `sable-fabric-1.21.1` (contains sable-common and nests Veil, sable-companion, Forge Config API Port and the
  Rapier natives), Forge Config API Port `forgeconfigapiport_version` (Fuzs' Maven), `geckolib-fabric-1.21.1`,
  `PlayerAnimationLibFabric` (its Core classes are inside the jar, so the separate Core artifact is excluded as on
  NeoForge). Loom remaps PAL's intermediary jar to Mojang names, which common's melee animation package compiles against.
- `fabric.mod.json`: the `main` entry point, both mixin configs, the access widener, and `depends` on Fabric API,
  Sable (`>=2.0.6 <3.0.0`), GeckoLib, Forge Config API Port and PAL. Fabric has no client-only dependencies, so PAL is
  required on a dedicated server too (it loads there harmlessly). GuideME is `suggests`: the fabric module does not
  put it on the dev classpath yet, so the in-game guide is untested on Fabric.
- Mixins are remapped statically by Loom 1.17 (`Fabric-Loom-Mixin-Remap-Type: static`), so no refmap is written; the
  `refmap` key in the mixin configs is unused on Fabric.

## Access widener

`common/src/main/resources/pirates_n_ships.accesswidener` is the twin of `META-INF/accesstransformer.cfg`: one entry
per AT line, in the same order (`BlockEntityType$BlockEntitySupplier`, `IntrinsicHolderTagsProvider$IntrinsicTagAppender`,
`TagsProvider$TagAppender`, `Mob.goalSelector` / `targetSelector`, `ItemProperties.register`,
`ServerPlayer$RespawnPosAngle`). Keep both files in step. Fabric-only entries (no AT twin):

- `SpawnPlacements.register`: `IRegistryHelper#registerSpawnPlacement` (NeoForge has `RegisterSpawnPlacementsEvent`;
  Fabric API widens the method for itself only).
- `GameTestRegistry.TEST_FUNCTIONS` and `TEST_CLASS_NAMES`: see "GameTests".

Fabric API also widens some vanilla methods transitively; common code that overrides them must not narrow them. FAB1
made `ModBlockLoot#generate` and the recipe provider's `buildRecipes` in `ModDataGenerator` `public` (they were
`protected`, which fails to compile against Fabric's widened `public` vanilla methods).

## Platform services (`fabric/src/main/java/.../platform/`)

| Service | Fabric implementation |
|---|---|
| `IPlatformHelper` | `FabricLoader` (mod list, dev environment, physical side). |
| `IRegistryHelper` | `FabricRegistryHelper`: registers each entry into the vanilla registry **when common code declares it** (Fabric leaves the registries open during init), so a factory may only use entries declared before it. Any registry in `BuiltInRegistries.REGISTRY` works: Sable's force groups, `Registries.ARMOR_MATERIAL` (ART6), structure types, mob effects. Entity attributes (`FabricDefaultAttributeRegistry`) and spawn placements (`SpawnPlacements.register`) are applied by `finish()` after common init. Natural spawns: one `BiomeModifications` addition per `NaturalSpawn`. |
| `INetworkHelper` | `FabricNetworkHelper`: codecs in `PayloadTypeRegistry.playS2C()` / `playC2S()`, server receivers through `ServerPlayNetworking.registerGlobalReceiver`, client receivers and `sendToServer` through `ClientPlayNetworking` in `FabricClientNetworking` (only touched on the physical client). Tracking sends use `PlayerLookup`. Handlers run on the main thread (Fabric API's contract). |
| `IAttachmentHelper` | `FabricAttachmentHelper`: Fabric Data Attachment API, `AttachmentRegistry.create` with the default as initializer, `persistent(codec)`, `syncWith(streamCodec, AttachmentSyncPredicate.all())`, `copyOnDeath()`. Entities, levels (cast; Fabric mixes `AttachmentTarget` into `Level`) and chunks. Fabric transfers a player's attachments only after the respawn (`AFTER_RESPAWN`); NeoForge copies them inside `ServerPlayer#restoreFrom`, so the helper also copies our keys at `ServerPlayerEvents.COPY_FROM` (before `PLAYER_CLONE` listeners run). Fabric's later transfer sets the old values once more, which would undo a `PLAYER_CLONE` listener's change to a copied key (none does that today). |
| `ICapabilityHelper` | `FabricCapabilityHelper`: Transfer API, `ItemStorage.SIDED.registerForBlockEntity` with `InventoryStorage.of(container, side)`. |
| `IConfigHelper` | `FabricConfigHelper`: the same `ModConfigSpec` translation as `NeoForgeConfigHelper`, registered with Forge Config API Port's `NeoForgeConfigRegistry`. Server config per world in `serverconfig/`, client config in `config/`. |

## Events (`FabricEventForwarder`, `fabric/.../fabric/mixin/`)

| `CommonEvents` | Fabric source | Difference to NeoForge |
|---|---|---|
| `SERVER_STARTING/STARTED/STOPPING/STOPPED` | `ServerLifecycleEvents` | none |
| `SERVER_TICK_START/END` | `ServerTickEvents.START/END_SERVER_TICK` | none |
| `LEVEL_TICK_START/END` | `ServerTickEvents.START/END_WORLD_TICK` | fired inside `ServerLevel#tick` instead of just around it |
| `PLAYER_TICK_END` | **mixin** `MixinPlayer` (`Player#tick` TAIL, both sides) | Fabric API has no player tick event |
| `PLAYER_LOGIN/LOGOUT` | `ServerPlayerEvents.JOIN/LEAVE` | none |
| `PLAYER_WAKE_UP` | `EntitySleepEvents.STOP_SLEEPING` (players only) | fires at the head of `LivingEntity#stopSleeping`, so also when hurt in bed (NeoForge: `Player#stopSleepInBed` only) |
| `PLAYER_CLONE` | `ServerPlayerEvents.COPY_FROM` (`wasDeath = !alive`) | none |
| `CONTAINER_OPEN/CLOSE` | **mixin** `MixinServerPlayer` (`openMenu`, `openHorseInventory`, `doCloseContainer`) | Fabric API has no container events |
| `ENTITY_JOIN_LEVEL` | `ServerEntityEvents.ENTITY_LOAD` | server side only (client: FAB2); after the entity was added, so **not cancellable** (no listener cancels) |
| `ENTITY_INTERACT` | `UseEntityCallback` (plain interaction only, `hitResult == null`; spectators skipped) | fired from the interaction packet handler and the client's game mode, **not from `Player#interactOn`**: code calling `interactOn` directly (mock players in tests) bypasses it |
| `LIVING_INCOMING_DAMAGE` | **mixin** `MixinLivingEntity` (`hurt`, at the `isSleeping()` call, the spot of NeoForge's event and Fabric's `ALLOW_DAMAGE`) | `ALLOW_DAMAGE` cannot change the amount |
| `LIVING_DEATH` | `ServerLivingEntityEvents.AFTER_DEATH` | fires after the death (end of `die`), so **the cancel result is ignored** (no listener cancels); `ALLOW_DEATH` can cancel but fires before a totem of undying saves the entity |
| `ITEM_USE_FINISH` | **mixin** `MixinLivingEntity` (around `finishUsingItem` in `completeUsingItem`) | Fabric API has no such event |
| `BLOCK_BREAK` | `PlayerBlockBreakEvents.BEFORE` | none |
| `BLOCK_PLACE` | **mixin** `MixinBlockItem` (around `placeBlock` in `BlockItem#place`, server) | only block items placed by a player or dispenser path through `BlockItem#place`; only the clicked position (NeoForge also reports the second half of multi-blocks and some entity placements) |
| `REGISTER_COMMANDS` | `CommandRegistrationCallback` | none |
| `ADD_RELOAD_LISTENERS` | `FabricReloadListeners` (one Fabric listener factory that fires the event per reload and runs the added listeners as one composite) | Fabric hands the factory a `HolderLookup.Provider`, not a `RegistryAccess`; an adapter passes it on (lookups from the reload, `registry(key)` from the static registries only). Proposed common change: make the event's parameter a `HolderLookup.Provider` (the only listener uses it as one). |
| `DATAPACK_SYNC` | `ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS` | none |

## Known gaps on Fabric (server side)

- **Hammock respawn point**: `HammockBlock`'s NeoForge `IBlockExtension` overrides are asked through Fabric's sleep
  events (`FabricHammockBeds`: `ALLOW_BED` for `isBed`, `SET_BED_OCCUPATION_STATE` for `setBedOccupied`,
  `MODIFY_SLEEPING_DIRECTION` for the bed direction), but `getRespawnPosition` has no Fabric hook: an unforced respawn
  point at a hammock on land is not found on Fabric (on a ship Sable answers first). Fix with a mixin in
  `ServerPlayer`'s respawn lookup if it matters.
- **Hat armour** (`apparel.HatItem`) relies on NeoForge's stack-aware `IItemExtension#getDefaultAttributeModifiers`
  path; check on Fabric (vanilla's own path may suffice).
- **Natural spawn config**: Fabric API applies biome modifications once when the server is created, before Forge
  Config API Port loads the server config (`SERVER_STARTING`), so weights and group sizes are the config values at that
  moment (the code defaults on a fresh server), not the world's server config as on NeoForge.
- **Transfer API with a `null` side**: Fabric wraps a `WorldlyContainer` as a plain container (no face rules) for a
  `null` side; NeoForge still applies the face rules with a `null` direction. Pipes always pass a side.
- **Networking**: no protocol version negotiation; mismatched payload formats fail when decoding.
- **Release workflow**: `.github/workflows/release.yml` still builds, tests and publishes the NeoForge jar only, and
  `build.yml` runs only the NeoForge GameTests. Add `./gradlew :fabric:runGameTest` to CI and the Fabric jar
  (`fabric/build/libs/pirates_n_ships-fabric-1.21.1-<version>.jar`) to the release (docs/releasing.md).

## FAB2: the client

- A `ClientModInitializer` (`fabric.mod.json` `client` entry point) that calls `PiratesNShipsCommon.initClient()` and
  forwards every `ClientEvents` hook like `NeoForgeClientSetup`: renderers, block entity renderers, model layers, key
  mappings, HUD layers, block colours, client ticks, `CLIENT_DISCONNECT`, `ITEM_TOOLTIP`, `SELECT_MUSIC`,
  `SOUND_STREAM_STARTED`, `INTERACTION_KEY`, FOV and camera angle events, the render stage hook, the client config.
- The TODOs in `fabric/.../PiratesNShips.java`: additional models (`ModelLoadingPlugin`), `RENDER_FRAME_PRE` (no
  Fabric event between the mouse turn and the frame; a mixin at the head of `GameRenderer#render`), armour models
  (`ArmorRenderer`).
- `ENTITY_JOIN_LEVEL` on the client (`ClientEntityEvents.ENTITY_LOAD`).
- The client mixins in common (`MixinCamera`, `MixinScreenEffectRenderer`, `MixinSectionCompiler`) on Fabric's vanilla
  classes, with a headless target check like neoforge's `ClientMixinTargetsTest`.
- Block entity renderers' culling boxes (`getRenderBoundingBox`, a NeoForge `IBlockEntityRendererExtension` method in
  common renderers) have no Fabric twin: check large renderers (sails, flags, ropes, cannons, helm) for culling.
- The config screen (design.md §17): Mod Menu through Forge Config API Port, or a fallback screen.
- A playtest of the Fabric client: join a world, sail, melee (PAL), crew (GeckoLib).

## GameTests

`./gradlew :fabric:runGameTest` (a Loom run, `-Dfabric-api.gametest`, run dir `fabric/build/gametest`, world and our
config files deleted before every run, JUnit report in `fabric/build/gametest/junit.xml`). Scope it like the NeoForge
run: `JAVA_TOOL_OPTIONS="-Dpirates_n_ships.gametest.only=FooGameTests,BarGameTests" ./gradlew :fabric:runGameTest`.

`FabricGameTests.register()` (called by the entry point on the GameTest server and in dev runs) adds every module's
GameTest classes plus `FabricGameTests` itself to vanilla's `GameTestRegistry`. It invokes the static
`@GameTestGenerator` methods itself: vanilla's `GameTestRegistry.register(Class)` instantiates the test class first
(NeoForge patches that out) and our test classes have private constructors. Fabric API's `fabric-gametest` entry point
is not used (it needs public constructors and only renames `@GameTest` templates).

`FabricGameTests` covers the Fabric wiring: the transfer API storages (pantry, cargo crate, none on the water barrel),
`UseEntityCallback` into the shackles, and a player placing a chest through `MixinBlockItem` into `PlacedBlocks`.
