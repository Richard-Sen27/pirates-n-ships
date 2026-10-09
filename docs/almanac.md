# The Captain's Almanac

The in-game guide (docs/design.md §3.4). This file collects how its parts are built and extended; the chapters,
the screen and the book are described by their own work packages (ALM1–ALM3, ALM5).

## Scenes

"Show me" scenes are [Ponder](https://github.com/Creators-of-Create/Ponder) scenes (ALM4). Ponder and Flywheel ship
inside our jar (jar-in-jar on both loaders, docs/releasing.md); everything of ours lives in
`common/.../almanac/ponder/` and runs on the physical client only.

- **Registration.** `AlmanacPonderModule.initClient()` calls `AlmanacPonder.register()`, which adds
  `AlmanacPonderPlugin` to `PonderIndex` during mod construction (NeoForge) or the client entry point (Fabric). Ponder
  registers every plugin's scenes once loading completes (NeoForge `FMLLoadCompleteEvent`, Fabric `CLIENT_STARTED`).
  No loader code of ours is involved.
- **Ids.** `AlmanacScenes.ALL` lists every scene as plain data: its id (what a chapter's "Show me" element carries),
  the item it is registered for (the Ponder key, "hold W" over the item, lists it), and its schematic. Today there is
  one: `pirates_n_ships:cannon_loading`, for `pirates_n_ships:cannon`, schematic `pirates_n_ships:cannon_loading`.
- **Opening one.** `AlmanacPonder.show(ResourceLocation id)` (client thread) opens exactly that scene for one of our
  scene ids, every scene of the item for an item id (ours and other mods'), and logs and ignores anything else
  (returns false, no crash). This is the hook the Almanac screen's "Show me" button calls (ALM3).
- **Text.** Every line is Ponder *shared text* with an explicit key (`CannonLoadingText.Line`), not Ponder's
  numbered `text_1, text_2…` (those follow call order, so a reordered scene would swap translations). Lang keys:
  `pirates_n_ships.ponder.shared.<scene>.<line>` and the title `pirates_n_ships.ponder.<scene>.header`; datagen writes
  the English (`AlmanacPonderModule.gatherData`). Ponder only uses the code's English defaults in its editing mode.
- **Truth to the code.** A scene drives the real blocks: the cannon scene steps `CannonBlock.LOAD` through
  `CannonLoad` (empty, powder, loaded, empty) and the elevation through `CannonBlockEntity`, and puts the muzzle flash
  where `CannonRules.muzzle` puts the muzzle.

### Adding a scene

1. **Schematic.** Write a BuildSpec generator `art/structures/ponder/<scene>.py` (pattern:
   `art/structures/ponder/cannon_loading.py`; y 0 is the base plate Ponder fades in first, keep it a full square) and
   run `python3 tools/build_structures.py ponder/<scene>`. The pipeline writes the schematic
   (`art/schematics/structures/ponder/<scene>.schem`), a render (`art/renders/structures/ponder/<scene>.png`) and,
   for the `ponder` group, the NBT to `common/src/main/resources/assets/pirates_n_ships/ponder/<scene>.nbt`, where
   Ponder loads it from. Commit all four.
2. **Storyboard.** A class like `CannonLoadingScene` with `static void program(SceneBuilder, SceneBuildingUtil)`;
   its first call is `scene.title("<scene>", "<English title>")`, so the scene id is `pirates_n_ships:<scene>`.
3. **Text.** Its lines and title as plain data (like `CannonLoadingText`), registered by
   `AlmanacPonderPlugin.registerSharedText` and written by `AlmanacPonderModule.gatherData`; run
   `./gradlew :neoforge:runData`.
4. **Plugin entry.** Add the scene to `AlmanacScenes.ALL` (id, item, schematic) and its storyboard to
   `AlmanacPonderPlugin.STORYBOARDS`.
5. **"Show me".** A chapter's "Show me" element names the scene id `pirates_n_ships:<scene>`.
6. **Tests.** `AlmanacScenesTest`, `AlmanacPonderPluginTest` and `PonderSchematicsTest` pick the new scene up from
   `AlmanacScenes.ALL`; add the positions your storyboard relies on to `PonderSchematicsTest` as done for the cannon.
   The scene itself plays only in a client: add its checks to the Ponder playtest.
