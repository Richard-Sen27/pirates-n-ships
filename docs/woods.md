# Woods (WOOD1)

The human, 2026-10-10: "i want you to add the multi wood support (including woods from other mods)"; earlier "the thing
with the wood counts for all various wood based things in this mod". WOOD1 builds the core and ships the two families
that existed (the railing family of RAIL1, RAIL3 and RAIL4, and the oar bench of OAR1) in every wood; WOOD2 to WOOD4
convert the other wooden blocks.

## The pieces (`ship/decor/wood`)

- **`ShipWood`**: one wood. Id (`spruce`; a mod's wood `<modid>_<name>`, e.g. `biomesoplenty_redwood`), English name
  ("Redwood", never the mod's name), the planks, stripped-log and stripped-log-top textures, the planks, log and
  stripped-log ids (looked up by id when asked, never at class load: `planksItem()`, `log()`, `strippedLog()`), the
  sound set, whether it burns, the map colour, the mod it needs (`null` for vanilla). `VANILLA` holds the eleven
  vanilla woods (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, bamboo, crimson, warped; bamboo's
  "log" is the bamboo block, crimson and warped use stems and do not burn).
- **`ShipWood.shipped()`**: the woods the families register, decided once at registration: the vanilla woods, then
  every compat wood whose mod is loaded (`Services.PLATFORM.isModLoaded`), compat woods ordered by mod id, then name. A
  data run (`Services.PLATFORM.isDataGeneration()`) ships every wood.
- **`WoodCompat`**: the table of mod woods (below).
- **`WoodFamily`**: one wooden block in every shipped wood. `register(suffix, factory)` registers `<wood>_<suffix>`
  per wood in `shipped()` order; `register(suffix, legacyWood, factory)` keeps one wood's member under the legacy id
  `<suffix>` (see Legacy ids). Datagen hooks: `childModels`/`itemModels` (children of a hand-made parent with
  `#planks`, `#log`, `#log_top`, particle planks or log), `lang(noun)`, `loot`/`dropSelf`, `recipe(WoodRecipe)`,
  `tag`; runtime: `entry`, `get`, `lead`, `blocks`, `ids`, `woodOf`, `blockEntity(name, factory)` (one block entity
  type valid for every member), and `WoodFamily.ofPath` (the family of a registry path).
- **`WoodBlock`**: the family's blocks implement `wood()`.
- **`WoodRecipe`**: a family recipe written once (`planks()`, `log()`, `strippedLog()`, `item(...)`, `member(family)`).
- **`WoodConditions`**: both loaders' load conditions in one JSON.
- **`WoodIds`**: pure family rules on ids (`suffixOf`, `representative`), used by the Almanac test and JEI's info pages.
- **`client/WoodModels`**: renderer parts per wood (`register(family, parent)` loads `block/<wood>_<part>` for every
  wood through `ClientEvents.registerAdditionalModel`; `get(block, parent)` fetches the member's).

The model convention (`#planks`, `#log`, `#log_top`, which texture is "the wood", rope and shutters stay fixed) is in
`art/README.md`, section "Wood families".

## What ships

- **Registration:** every vanilla wood always; a mod's wood only when its mod is loaded. The registry gains 6
  families x 11 woods = 66 blocks with items (it had the 6 oak members); each loaded wood mod adds 6 per wood.
- **Creative tab:** registration order, so family-major: oak railing, spruce railing, ... warped railing, the loaded
  mods' railings, then the ports, and so on (like vanilla's fences).
- **Names:** "Spruce Railing", "Redwood Railing". No mod name in the name or a tooltip: two mods with the same wood
  ("Fir" in Biomes O' Plenty, Nature's Spirit and Oh The Biomes We've Gone) give identical names, told apart by the
  recipe viewer's mod name and the item id. Twilight Forest's names follow its planks ("Darkwood", "Timewood"); its
  mangrove is "Twilight Mangrove" so it does not read like vanilla's.
- **Recipes:** each member from that wood's planks (and sticks, nuggets as before), in the family's recipe group.

## The families

| Family (suffix) | Legacy id (wood) | Variables | Registered in | Datagen |
|---|---|---|---|---|
| `railing`, `railing_port`, `railing_gate`, `bulwark`, `bulwark_port` | none (oak leads) | `#planks`, `#log` | `ship/decor/railing/RailingContent` | `RailingData` |
| `oar_bench` | none | `#planks`, `#log` | `sailing/oars/OarsContent` | `OarsData` |
| `nameplate` (the name board) | `nameplate` (oak) | `#planks`, `#log` | `ShipDecor.NAMEPLATES` | `ShipDecorModule` |
| `flagpole` | `flagpole` (spruce) | `#log` | `ShipDecor.FLAGPOLES` | `flag/FlagData` |
| `flag_pedestal` | `flag_pedestal` (spruce) | `#log`, `#log_top` | `ShipDecor.FLAG_PEDESTALS` | `ShipDecorModule` |
| `stern_window`, `chart_table`, `sea_cot` | dark oak | `#planks`, `#log` | `ShipDecor.STERN_WINDOWS`, `CHART_TABLES`, `SEA_COTS` | `DecorData` |
| `notice_board` | `notice_board` (dark oak) | `#planks`, `#log`, `#log_top` | `law/content/LawContent.NOTICE_BOARDS` | `NoticeBoardData` |
| `crows_nest` | `crows_nest` (spruce) | `#planks`, `#log` | `station/lookout/LookoutContent.CROWS_NESTS` | `LookoutModule` |
| `yard` | `yard` (spruce) | `#log`, `#log_top` | `sailing/block/SailingBlocks.YARDS` | `sailing/block/YardData` |
| `cleat` | `cleat` (dark oak) | `#log`, `#log_top` | `sailing/sail/TriangularSailContent.CLEATS` | `TriangularSailData` |

WOOD3 (the fittings): each content class keeps its old constant as the legacy member (`ShipDecor.YARD` is
`YARDS.entry(SPRUCE)`), so tests, schematics and templates that name it still work; everything that asks "is this a
yard" asks `instanceof YardBlock` (or the family's tag) instead, never `is(YARD)`. The family block entities
(`nameplate`, `flagpole`, `yard`, `cleat`) are one type valid for every member (`WoodFamily.blockEntity`). Codecs:
`WoodBlock.codec(factory)` for a block class whose vanilla parent demands one. Recipes per wood: name board two planks
and a gold nugget (3); flagpole the stripped log over two sticks (2); flag pedestal the stripped log over a plank (2);
stern window from the wood's planks (2, dark oak's before); chart table, sea cot (any wool, `WoodRecipe.tag`), notice
board, crow's nest and cleat from the wood's planks; yard three stripped logs (3, any log before). Tags: the yards and
crow's nests of every wood are `ratlines_anchors`; `pirates_n_ships:yards` and `:cleats` hold every member.

**Sable physics of a family:** the yard and the cleat had a per-id `physics_block_properties` file. A selector naming a
block that is not registered (a mod's wood without its mod) logs "Unknown block" on every level load
(`refs/sable/common/.../PhysicsBlockPropertiesDefinitionLoader#applyToBlocks`), and Sable's own loader applies no load
conditions, so a family's file keeps the legacy path and selects the family's tag (`"selector": "#pirates_n_ships:yards"`),
whose compat entries are optional. WOOD2 and WOOD4 (cargo, pantry, water barrel, bilge pump, cannon) should do the same.

## Datagen and the compat files

Datagen writes every wood, vanilla and compat alike (models, block states, item models, lang, loot, recipes,
advancements, tags). A compat wood's files load only with its mod:

- recipes and their unlock advancements carry `neoforge:conditions` (`neoforge:mod_loaded`, `neoforge:item_exists` of
  the wood's planks or logs it uses) and `fabric:load_conditions` (`fabric:all_mods_loaded`,
  `fabric:registry_contains` of the same items) in the same JSON; the item check skips the recipe when the mod has its
  woods switched off by config (Quark);
- loot tables carry the mod conditions (both loaders apply conditions to loot tables: NeoForge's `LootDataType`
  conditional codec, Fabric API's `ReloadableRegistriesMixin`);
- tag entries of compat members are optional (`{"id": ..., "required": false}`), so no "unknown block" warning;
- models, block states and lang are assets: without the block they are never read.

The vanilla builders cannot make a compat recipe (its planks are not registered in a data run), so `WoodRecipe` builds
the same `ShapedRecipe`/`ShapelessRecipe` and unlock advancement with a stand-in item per wood ingredient, encodes them
with the vanilla codecs and replaces the stand-in ids with the mod's. `WoodDataTest` checks every generated compat
recipe, advancement and loot table for both conditions and every tag entry for `required: false`.

## The compat table (`WoodCompat`, `docs/wood-compat.md`)

The mods: the ten most-downloaded (all-time Modrinth downloads, 2026-10-10) projects with a 1.21.1 release on NeoForge
or Fabric that add wood sets, plus The Twilight Forest (required by the human; CurseForge only):

| Mod | Mod id | Loaders | Woods |
|---|---|---|---|
| Biomes O' Plenty | `biomesoplenty` | NeoForge, Fabric | 13 |
| Quark | `quark` | NeoForge | 3 |
| Deeper and Darker | `deeperdarker` | NeoForge, Fabric | 1 |
| Oh The Biomes We've Gone | `biomeswevegone` | NeoForge, Fabric | 25 |
| Nature's Spirit | `natures_spirit` | NeoForge, Fabric | 17 |
| The Aether | `aether` | NeoForge, Fabric | 1 |
| Regions Unexplored | `regions_unexplored` | NeoForge, Fabric | 22 |
| Ecologics | `ecologics` | NeoForge, Fabric | 3 |
| Ars Nouveau | `ars_nouveau` | NeoForge | 1 (archwood) |
| The Undergarden | `undergarden` | NeoForge | 3 |
| The Twilight Forest | `twilightforest` | NeoForge, Fabric (beta) | 8 |

97 compat woods, 108 in all. Versions, jar names and every texture path are in `docs/wood-compat.md`.

Left out (no texture or block to verify, so not guessed): Deeper and Darker's bloom (its stripped stem has no end
texture), Regions Unexplored's alpha oak and its painted planks (no stripped log), Ecologics' flowering azalea (no
stripped log), The Undergarden's ancient root (no stripped log), Oh The Biomes We've Gone's palo verde (no planks).
Explicit rows where a mod spells things its own way, each verified in the jar: Aether's textures in `construction/`
and `natural/`, Oh The Biomes We've Gone's `block/<wood>/planks`, Twilight Forest's planks `wood/planks_<name>_0` (the
first of four random variants), The Undergarden's `_log_side`/`_log_end`, Ars Nouveau's log (archwood logs come in
four colours; the blue one stands for them in log recipes).

The map colour of a compat wood is the nearest vanilla map colour to the mean colour of its planks texture, computed
by the tool from the jar (on the vanilla planks the rule hits 4 of 11 exactly and lands near the rest). Compat woods
use the plain wood sound set and burn, unless a row says otherwise.

## Adding a wood

- **Vanilla** (a new vanilla wood in a later version): one constant in `ShipWood` and its place in `VANILLA`.
- **A mod's wood:** a `Mod` constant and rows in `WoodCompat` (one per line: the tool parses the file), the mod's jars
  under `<folder>/<slug>/<loader>/`, then `python3 tools/check_wood_compat.py <folder> --markdown docs/wood-compat.md`
  until it reports no problems (it writes the report only then), then `./gradlew :neoforge:runData`.
- **Known limitation:** woods of mods not in the table get no members. A data pack or a config cannot add one: blocks
  are registered at startup from the table, and data cannot add blocks.

## Config

Wood variants are not a gameplay change, so there is no server toggle. A startup switch (to skip the compat woods or
whole families) is left out too: the list is decided at registration, before either loader's config is read, so it
could not be a server config value, and a separate startup file is not worth it for blocks that only exist with their
mod anyway.

## Legacy ids

The wooden blocks WOOD2 to WOOD4 convert (`helm`, `capstan`, `cargo_crate`, `cargo_barrel`, `pantry`, `water_barrel`,
`harbor_desk`, `sea_chest`, `nameplate`, `chart_table`, `sea_cot`, `stern_window`, `notice_board`, `flagpole`,
`flag_pedestal`, `crows_nest`, `yard`, `cleat`, `sail_winch`, `bilge_pump`, `cannon`) are in the save-compatibility
note's kept-ids table, structure NBTs, ship templates and Ponder schematics, so their ids stay:
`WoodFamily.register("helm", ShipWood.DARK_OAK, factory)` registers `helm` for dark oak and `<wood>_helm` for every
other wood. The legacy id is internal: the member is "Dark Oak Helm" like every other; it leads the family (the
Almanac's panel, JEI's info page); its models are named by wood like the rest (`block/dark_oak_helm_wheel`).

## Suite rules

- **One recipe panel per family:** `AlmanacGuideTest` maps every `<wood>_<suffix>` recipe (and a legacy id) to the
  family's representative (`WoodIds.representative`: the legacy member's, else oak's), and wants exactly one
  `{recipe:...}` panel per family. In the book, a family's panel cycles through every loaded wood's recipe
  (`RecipeView.family`, `RecipeGrid.FAMILY_CYCLE_TICKS` 40), and its arrow's hover says so.
- **One inventory line per family:** JEI's information pages (`compat/InfoPages`) use the family's line for every
  member without its own.
- Waterlogging: the families' blocks are partial and waterloggable; `WaterloggingGameTests` walks the registry, so new
  members are covered.
- Renderer parts: `WoodModels.ids` is unit-tested; no current family has one (WOOD4's helm, capstan, pump and cannon
  will).
