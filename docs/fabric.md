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
- `fabric.mod.json`: the `main` and `client` (FAB2) entry points, both mixin configs, the access widener, and `depends` on Fabric API,
  Sable (`>=2.0.6 <3.0.0`), GeckoLib, Forge Config API Port and PAL. Fabric has no client-only dependencies, so PAL is
  required on a dedicated server too (it loads there harmlessly). GuideME is `suggests`: the fabric module does not
  put it on the dev classpath (GuideME 21.1 is NeoForge only), so there is no in-game guide on Fabric. `modmenu` is
  `recommends` (the config screen); Mod Menu 11.0.3 is on the dev runs' classpath only (`modLocalRuntime`).
- JUnit tests (`fabric/src/test`, run by `./gradlew build`): the mixin target check, the render layer resolver and the
  client assumptions, see "Client (FAB2)".
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
| `ENTITY_JOIN_LEVEL` | `ServerEntityEvents.ENTITY_LOAD` | server side (client side: see "Client (FAB2)"); after the entity was added, so **not cancellable** (no listener cancels) |
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

## Client (FAB2)

`PiratesNShipsClient` (the `client` entry point of `fabric.mod.json`) runs after every mod's `main` entry point:
`PiratesNShipsCommon.initClient()`, then `FabricClientSetup.attach()`, the twin of `NeoForgeClientSetup`. Fabric
registers immediately, so every registration happens there, before the first resource load. `CLIENT_SETUP` fires at the
end of `attach()` (NeoForge: `FMLClientSetupEvent.enqueueWork`; Fabric has no later setup stage, and Forge Config API
Port loads the client config the moment it is registered, so it is loaded by then). Dev client: `./gradlew :fabric:runClient`
(run dir `fabric/runs/client`, Mod Menu on the dev classpath).

### Hook table

| `ClientEvents` hook | Fabric source | Difference to NeoForge |
|---|---|---|
| `CLIENT_SETUP` | end of `FabricClientSetup.attach()` (client init) | runs during client init, before the first resource load, not after parallel setup |
| `CLIENT_TICK_START/END` | `ClientTickEvents.START/END_CLIENT_TICK` | none |
| `RENDER_FRAME_PRE` | **mixin** `client.MixinMinecraft` (`runTick`, before `GameRenderer#render`) | none (NeoForge fires `RenderFrameEvent.Pre` at the same call) |
| `CLIENT_DISCONNECT` | `ClientPlayConnectionEvents.DISCONNECT` | fires when the play connection closes (NeoForge: `LoggingOut` in `Minecraft#disconnect`); both may fire more than once |
| `ITEM_TOOLTIP` | `ItemTooltipCallback` | Fabric passes no player: the client player is passed (null without a world) |
| `SELECT_MUSIC` | **mixin** `client.MixinMusicManager` (around `getSituationalMusic()` in `tick`) | none |
| `SOUND_STREAM_STARTED` | **mixin** `client.MixinSoundEngine` (wraps the channel setup task of `play`, streamed sounds only) | fires right after vanilla's pitch/volume setup task, a moment before the stream is attached and started (NeoForge: right after `play()` on the channel); same sound thread |
| `INTERACTION_KEY` | **mixin** `client.MixinMinecraft` (`startAttack`, `continueAttack`, `startUseItem` per hand, `pickBlock`) | none: the four points of NeoForge's `ClientHooks.onClickInput`; a cancel returns there with no swing |
| `COMPUTE_FOV` | **mixin** `client.MixinAbstractClientPlayer` (around the final `Mth.lerp` of `getFieldOfViewModifier`) | none (same scaled start value, the spyglass return bypasses it on both) |
| `COMPUTE_CAMERA_ROLL` | **mixin** `client.MixinCamera` (wraps the first two `setRotation` calls of `setup`, rolls `setRotation`'s quaternion) | none: same roll, mirrored for the reversed third-person view, none while sleeping |
| `RENDER_AFTER_TRANSLUCENT` | `WorldRenderEvents.AFTER_TRANSLUCENT` | fires after the particles (NeoForge: right after the translucent block layer, inside `renderSectionLayer`), with the main target bound under Fabulous (NeoForge: the translucent target). The flood surface uses `translucentMovingBlock()`, which binds its own target, so it draws into the same target on both. Chosen over a mixin into `renderSectionLayer` because Sodium replaces that method but keeps Fabric's event. |
| `registerKeyMapping` | `KeyBindingHelper.registerKeyBinding` | none |
| `registerHudLayer` | `HudRenderCallback` (after vanilla's HUD) | above all vanilla layers like `registerAboveAll`, one `Z_SEPARATION` apart in registration order; the depth buffer is cleared first (vanilla clears it right after the HUD anyway); drawn with F1 too, as on NeoForge |
| `registerEntityRenderer` | `EntityRendererRegistry.register` | none |
| `registerBlockEntityRenderer` | `BlockEntityRenderers.register` (widened by Fabric's transitive access wideners) | none |
| `registerModelLayer` | `EntityModelLayerRegistry.registerModelLayer` | none |
| `registerAdditionalModel` / `additionalModel` | `ModelLoadingPlugin` `addModels`; key `ModelResourceLocation(id, "fabric_resource")` | none (the variant is Fabric API's impl constant, checked by `FabricClientAssumptionsTest`) |
| `registerArmorModel` | `ArmorRenderer.register` (`FabricArmorModels`) | Fabric replaces the whole armour piece, so `FabricArmorModels` redoes NeoForge's layer: pose and slot visibility onto vanilla's model, the provider, pose and visibility onto the provider's model, then the material layers (dye colour), trim and glint. `original` is always the player armour model (Fabric does not pass the wearer renderer's own) |
| `registerBlockColor` | `ColorProviderRegistry.BLOCK` | none |
| block render layers (model `render_type`) | `BlockRenderLayerMap` (`FabricRenderLayers`) | NeoForge reads `render_type` per model, vanilla/Fabric ignore it; `FabricRenderLayers` reads it from our blockstates and models at client init and puts the block in that layer (per block: if models disagree, the most transparent wins and a warning is logged). Today: the stern window (cutout) |
| `CommonEvents.ENTITY_JOIN_LEVEL` (client side) | `ClientEntityEvents.ENTITY_LOAD` | after the entity was added, so not cancellable (no listener cancels) |
| config screen | Forge Config API Port's `ConfigScreenFactoryRegistry` with its port of NeoForge's `ConfigurationScreen` | shown by Mod Menu (FCAP ships Mod Menu's entry point; `fabric.mod.json` recommends `modmenu`); without Mod Menu there is no way to open it in game (edit `config/pirates_n_ships-client.toml` and `serverconfig/`) |
| GeckoLib, PAL | nothing to wire: GeckoLib renderers go through `registerEntityRenderer`, PAL layers are registered in `CLIENT_SETUP` | none |

The six mixins are client mixins of `pirates_n_ships.fabric.mixins.json` (package `fabric.mixin.client`); each says
why no Fabric API event fits. The mixins of both configs (common's `MixinCamera`, `MixinScreenEffectRenderer`,
`MixinSectionCompiler`, the server ones and Fabric's own) are checked headlessly against the vanilla classes by
`fabric/src/test/.../fabric/mixin/ClientMixinTargetsTest` (HV1b on Fabric: selectors, `INVOKE` call sites, ordinals,
plus exact counts for every FAB2 injection point); `./gradlew build` runs it.

### Render bounding boxes

NeoForge culls every block entity by its renderer's `getRenderBoundingBox` (default: the block), which is why the
flag cloth, the yard and stay sails, the rope lines, the cannon barrel and the helm wheel override it in common. Vanilla
1.21.1 has no such method on `BlockEntityRenderer` and no per-block-entity frustum test: `LevelRenderer` draws every
block entity of every visible chunk section (and the global ones). So on Fabric those methods are unused and a large
renderer is culled only with its whole 16-block section, never earlier than on NeoForge; on ships Sable draws the block
entities of all sub-level sections without a frustum test on both loaders. Nothing to forward; `FabricClientAssumptionsTest`
fails if vanilla or Fabric API ever adds per-block-entity culling. (The pump and the bell keep the default box on both loaders.)

### Known gaps on Fabric (client side)

- **Guide book**: GuideME 21.1 exists for NeoForge only, so there is no in-game guide on Fabric (`suggests` stays for a
  future Fabric build of GuideME).
- **Config screen without Mod Menu**: none in game, see the table.
- **Armour on non-player wearers**: the coats on armour stands and mobs use the player armour model as `original`;
  only visible if a provider returned `original` for a slot it is worn in.
- **Unverified in a client**: everything above compiles, the mixin targets are checked headlessly and the GameTest server
  starts with the client entry point present, but no Fabric client has been opened by an agent. The checklist below is
  the acceptance test.

### Fabric client playtest (`./gradlew :fabric:runClient`)

Start a new creative world (Fabric dev client, Mod Menu loaded). Check `fabric/runs/client/logs/latest.log` for
`Mixin apply failed`, `InvalidInjectionException`, `Render layer of` warnings and exceptions after each step.

1. **World load**: the world loads without a crash; the title screen and the world list work.
2. **Ship**: build a small hull with a helm, sails and a flag, assemble it, sail it. Expected: the ship moves, the helm
   wheel turns, steering from the helm holds the view (`RENDER_FRAME_PRE`), the HUD shows the wind/ship HUD.
3. **Flag cloth**: a flag on a pole, on land and on the ship: the cloth waves and stays drawn while the pole block is at
   the screen edge (only vanishes when its whole chunk section leaves the view).
4. **Sails**: yard sails and stay sails set and furled; the cloth stays visible when the head block leaves the view.
5. **Cannon barrel**: place a cannon, aim and fire it; the barrel model shows and turns (additional models).
6. **Pump**: use the bilge pump; the handle and rod models show and move.
7. **Bell**: ring the ship's bell; bell and clapper show and swing.
8. **Stern window**: placed, its glass panes are see-through (cutout layer from `render_type`), not black.
9. **HUD**: the mod's HUD elements draw above the vanilla HUD and hide with F1 where they do on NeoForge.
10. **Ship screen and other screens**: open the ship screen, the chart, the market; they open and close normally.
11. **Guide book**: none on Fabric (gap); nothing crashes when GuideME is absent.
12. **Coat tails**: wear the officer's and the captain's coat, third person: the coat with tails draws on the body and
    moves with it; dyed/trimmed/enchanted variants colour, trim and shimmer like vanilla armour.
13. **Flood surface and overlay**: hole a hull below the waterline; the water surface inside the hull draws and the
    underwater overlay/fog appears when the eyes are below it (common `MixinCamera`, `MixinScreenEffectRenderer`).
    Also with Fabulous graphics.
14. **Firearm input**: with a loaded musket, attack fires without swinging or mining; aiming zooms the view
    (`COMPUTE_FOV`). Melee: with skill-based combat on, a sword's attack/use do the melee actions (PAL animations play)
    instead of vanilla's swing. Grapple and swivel gun inputs work.
15. **Camera sway**: on a heeling ship with camera sway on, the view rolls with the ship (`COMPUTE_CAMERA_ROLL`), also in
    third person front view (mirrored).
16. **Music**: at sea the sea music plays at the configured volume (`SELECT_MUSIC`, `SOUND_STREAM_STARTED`).
17. **Config screen**: Mod Menu, Pirates 'n' Ships, Config: the client and server config pages open and save.
18. **Crew and colours**: a crew member and a shark (GeckoLib) render and animate; a water barrel's water surface is
    tinted like the biome's water (block colour); banner flags show their colours.

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
