# Compatibility with other mods

Optional integrations: the mod loads and plays the same without them (design.md §2, "Optional compat").

## JEI (Just Enough Items), JEI1

**Dependency.** Optional on both loaders: `neoforge.mods.toml` `type="optional"`, `versionRange = [19.39,)`;
`fabric.mod.json` `suggests` and `breaks` below 19.39. Built against `mezz.jei:jei-1.21.1-*:19.39.0.372` from
`https://maven.blamejared.com` (`gradle.properties` `jei_version`): `common` compiles against `jei-1.21.1-common-api`,
the loader modules against their API jar, and the dev runs (client, GameTest server, data) load the full JEI jar.
`-Ppirates_n_ships.no_jei` leaves JEI out of a run (`./gradlew :neoforge:runGameTestServer -Ppirates_n_ships.no_jei`),
to check that nothing needs it. Release: optional on Modrinth (`u6dRKJwZ`) and CurseForge (`238222`), see
`docs/releasing.md`.

Why not the newest JEI (19.57.x): every JEI from 19.42 on needs NeoForge 21.1.238 or newer (we are on 21.1.228), and
the 19.57 Fabric jars are built with Loom 1.18, which our Loom 1.17 refuses to remap. Players may run any newer JEI.

**What JEI shows.**
- **Information pages** (the "Information" tab under R/U) for every item of ours the Captain's Almanac describes:
  the first Almanac paragraph or list line that draws the item's icon (`{item:ns:id}`), searching the `inventory`
  chapter ("The ship's inventory", one line per block and item) first, then the other chapters in book order, else
  the first one that names the item by its display name; then the chapter it came from. The `ledger` (commands and
  settings) and `not_yet` chapters are never used. Markup is removed (a link that ends the line reads "(see
  Chapter)"), and so is any "(recipe: ...)" note. Read from the client's lang files (resource packs and the selected
  language apply). An item no paragraph describes gets no page (today 86 of 93 items have one; without: the flag
  pedestal and the six spawn eggs). Rules and tests: `compat/InfoPages`, `InfoPagesTest`.
- **Harbor Market** (`pirates_n_ships:harbor_market`, catalyst: harbor master's desk): one page per trade good
  (`trade_good` definitions, synced from the server; the shipped defaults until the sync arrives, then swapped):
  buy and sell price at a port that neither makes nor wants the good, the buy price where it is made and the sell
  price where it is wanted, the climates that make it, its cargo weight. Prices are those of a settled market from
  the server's `trade` config (`compat/MarketEntry`).
- **Ship's Provisions** (`pirates_n_ships:ships_provisions`, catalysts: pantry, water barrel): every item the crew
  counts as food, water or rum, classified by the crew's own rules (`ProvisionClassifier`, provision tags), with its
  value, shelf life (or "never spoils") and weight (`compat/ProvisionEntry`).
- **Hidden** from the item list: the ship receipt and the ship commission, which are nothing without the order or
  grant the game writes onto them (`compat/HiddenItems`, with the reasons for keeping the rest).

Not shown: shipwright orders. Ship templates are server-only definitions and an order's logs and wool come from the
template's structure file on the server, so the client has nothing to show; the Orders tab of the harbor master's desk
lists them. Meals are not recipes (the crew eats provisions at set times), so the provisions page covers the galley.
No config toggle: JEI pages change no gameplay.

**Layout.** `compat/` (no JEI classes): `CompatModule` (lang, GameTests), `CompatText`, `InfoPages`, `MarketEntry`,
`ProvisionEntry`, `HiddenItems`, `TextLine`. `compat/jei/` (the only package that imports `mezz.jei`; nothing else in
the mod names it, `PiratesJeiPluginTest` checks): `PiratesJeiPlugin`, `MarketCategory`, `ProvisionCategory`,
`JeiText`, `AlmanacLangReader`.

**How JEI finds the plugin.** NeoForge: JEI's `ForgePluginFinder` collects classes annotated `@JeiPlugin` from FML's
scan data and calls their no-argument constructor. Fabric: JEI's `FabricPluginFinder` reads the `jei_mod_plugin`
entry point, listed in `fabric/src/main/resources/fabric.mod.json`. Without JEI neither path runs, so no class of
`compat/jei` is ever loaded.

**Adding a category.**
1. Put the data in `compat/` as a plain record with a pure `lines()` (a list of `TextLine`) and a JUnit test; a
   GameTest in `CompatGameTests` if it needs registries or tags.
2. Add its lang keys to `CompatText.lang()` and run `./gradlew :neoforge:runData`.
3. In `compat/jei/`, a class extending `AbstractRecipeCategory<YourRecord>` with a `RecipeType.create(MOD_ID, ...)`,
   an icon (`gui.createDrawableItemLike(...)`), its size, `setRecipe` (slots) and `draw` (`JeiText.draw` for the
   lines); give `getRegistryName` a unique id per recipe.
4. Register it in `PiratesJeiPlugin`: `registerCategories`, `registerRecipes`, and a catalyst in
   `registerRecipeCatalysts`. Data that arrives from the server after JEI started needs the swap pattern of
   `onTradeGoodsSync`.
5. Add the check to the JEI1 playtest in `docs/playtests/items-and-blocks.md`.
