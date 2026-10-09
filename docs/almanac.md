# The Captain's Almanac: chapter format

The Almanac's content is a datapack (design.md §3.4, ALM2). Each chapter is one JSON file; the text is lang keys, so
the server sends only keys and every client shows its own language. The code lives in
`common/.../almanac/data` (model, loader) and `common/.../almanac/sync` (payloads, client copy).

## Files and ids

- Path: `data/<namespace>/almanac/chapters/<path>.json` (subfolders allowed). The chapter id is `<namespace>:<path>`.
- Any datapack can add chapters in its own namespace, or replace one of ours by shipping the same path (the whole file
  is replaced, the top pack wins).
- The mod's own chapters are generated from `docs/guide.md` (see "Generated from the guide" below); never hand-edit the
  generated files.
- A broken file (bad JSON, unknown element type, unknown item, coordinates outside the page, no pages) is logged with
  its id and reason and skipped. It never fails the reload and never affects other chapters.
- The server's book is sent to each player on login and to every player after `/reload`; a client that got nothing has
  an empty book.

## Chapter

| Field | Type | Default | Meaning |
|---|---|---|---|
| `title` | lang key | required | Chapter title, also the bookmark's tooltip |
| `order` | int | `1000` | Position in the book; chapters sort by `order`, then by id |
| `bookmark_color` | `"#rrggbb"` | `"#8b1a1a"` | Colour of the chapter's bookmark ribbon |
| `icon` | item id | required | Item drawn on the bookmark; must be a registered item |
| `pages` | list of pages | required | 1 to 256 pages, each `{"elements": [...]}` with up to 64 elements |

Pages are explicit: there is no page-break element. The screen (ALM3) may still continue a page that does not fit.

## Page elements

Every element has a `"type"`. Page coordinates are a unit square per page: `x`/`y` 0 is the top left corner, 1 the
bottom right, so the screen can scale a page to any size. Text, headings, lists and "Show me" buttons flow from the top
in list order; illustrations and item icons sit at their coordinates.

| `type` | Fields | Notes |
|---|---|---|
| `text` | `key` (lang key); `bold`, `italic` (bool, default false); `color` (`"#rrggbb"`, optional) | A paragraph; no colour = the default ink |
| `heading` | `key` | A heading line |
| `list` | `items` (1–64 lang keys) | A bulleted list |
| `illustration` | `texture` (full texture path, e.g. `pirates_n_ships:textures/gui/almanac/ship.png`), `x`, `y`, `w`, `h` | `w`, `h` > 0, `x + w` ≤ 1, `y + h` ≤ 1 |
| `item` | `item` (item id), `x`, `y`; `caption` (lang key, optional) | An unknown item fails the chapter |
| `show_me` | `scene` (Ponder scene id), `label` (lang key) | A button that plays the scene (ALM4) |

Lang keys: non-empty, at most 256 characters, no whitespace. An unknown lang key is not an error (lang lives on the
client; the screen shows the raw key).

## Lang-key convention

All keys of chapter `<ns>:<path>` start with `<ns>.almanac.<path>` (slashes in the path become dots), see
`almanac/data/AlmanacLang`:

- title: `<ns>.almanac.<path>.title`
- page texts: `<ns>.almanac.<path>.p<page>.<name>`, page counted from 1, `<name>` free (`heading`, `text`, `step1`, …)

The convention is not enforced; it keeps generated chapters and other datapacks from colliding. The English text of the
mod's own keys comes from datagen next to the chapter JSON.

## Example

A hand-written chapter (ALM2's former example, shortened):

```json
{
  "title": "pirates_n_ships.almanac.cannons.title",
  "order": 10,
  "bookmark_color": "#3b3b3b",
  "icon": "pirates_n_ships:cannon",
  "pages": [
    {
      "elements": [
        { "type": "heading", "key": "pirates_n_ships.almanac.cannons.p1.heading" },
        { "type": "text", "key": "pirates_n_ships.almanac.cannons.p1.text" },
        { "type": "list", "items": [
            "pirates_n_ships.almanac.cannons.p1.step1",
            "pirates_n_ships.almanac.cannons.p1.step2" ] },
        { "type": "item", "item": "minecraft:gunpowder", "x": 0.2, "y": 0.75,
          "caption": "pirates_n_ships.almanac.cannons.p1.powder" }
      ]
    },
    {
      "elements": [
        { "type": "text", "key": "pirates_n_ships.almanac.cannons.p2.warning", "bold": true, "color": "#8b1a1a" },
        { "type": "illustration", "texture": "pirates_n_ships:textures/block/flag_jolly_roger.png",
          "x": 0.2, "y": 0.45, "w": 0.6, "h": 0.4 },
        { "type": "show_me", "scene": "pirates_n_ships:cannon_loading",
          "label": "pirates_n_ships.almanac.cannons.p3.show_me" }
      ]
    }
  ]
}
```

## The last page per player

The client reports the page it shows (`ClientAlmanac.reportPage(chapter, page)` → `AlmanacPagePayload.Report`); the
server stores it in the persistent player attachment `pirates_n_ships:almanac_progress` (kept on death and relog) and
sends it back on login (`AlmanacPagePayload.Restore`), so the Almanac opens there (`ClientAlmanac.progress()`). The page
index is the screen's, clamped to 0..9999 and not checked against the chapter's data pages. A report for a chapter the
server does not have is ignored. Server config `almanac.remember_page` (default true): off stores nothing and sends no
page.

## Markup

Every lang text the book shows (text, headings, list items, captions, button labels) may carry a small inline markup,
parsed by `almanac/client/Markup` (no `§` codes):

| Write | Shows |
|---|---|
| `**bold**` | bold (on top of the element's own `bold`) |
| `_italic_` | italic; an underscore opens only at the start of a word and closes only at its end, so `snake_case` stays as written |
| `{#rrggbb}text{/}` | coloured text; colours nest, `{/}` returns to the colour before |
| `{item:ns:id}` | the item's icon inside the line (9 px; no namespace = `minecraft`); hovering shows the item's tooltip |
| `[[ns:path]]` | a link to that chapter's first page, shown as the chapter's title (no namespace = `pirates_n_ships`) |
| `[[ns:path\|text]]` | the same link showing `text` |
| `\n` (a real line break in the lang text) | a new line |
| `\*`, `\_`, `\{`, `\[`, `\\` | the character itself |

Anything malformed (an unknown `{...}`, a bad id, an unclosed `[[`) is shown as written; an unclosed `**`, `_` or
colour runs to the end of the text. A link to a chapter the book does not have does nothing.

## The screen (ALM3)

`almanac/client/AlmanacScreen`, opened by `AlmanacClient.open(@Nullable BlockPos tome)` (null: read in hand) at the page
the player last read. A two-page parchment spread of 292 × 180 GUI pixels; each page's text box is 116 × 140 pixels
(14 lines). The layout (`almanac/client/PageLayout`) works in these pixels, so it does not depend on the window:

- **Free elements** (illustrations, item icons with their caption centred under them) sit at their unit coordinates on
  the data page's first screen page. They are obstacles: a line that would cross one takes the widest free part of its
  row when that is at least a third of the page, otherwise it continues below the obstacle. Illustrations keep their
  aspect, centred in their box; a missing texture shows a faint box and logs a warning.
- **Flowing elements** (headings bold and centred, paragraphs, bulleted lists, centred "Show me" buttons) wrap word by
  word; what does not fit spills onto continuation pages, so one data page can take several screen pages. A heading
  never ends a page alone.
- **Spreads:** every chapter starts on a left page; the right page of a chapter's last spread may stay blank. The page
  index stored as the last page (`ClientAlmanac.reportPage`) is the left page of the spread, in screen pages of that
  chapter; a stored index past the chapter's end opens its last spread, an unknown chapter the first page of the book.
- **Navigation:** the page corners (vanilla's arrows), the arrow keys and Page Up / Page Down, the mouse wheel, the
  bookmarks on the right edge (one per chapter in book order, tinted with `bookmark_color`, with the chapter's icon;
  hovering shows the title, the open chapter's sticks out further; they shrink and then form more columns when many
  chapters do not fit) and links. Escape closes. Input waits while a page turns.
- **Page turn:** the turning page narrows toward the spine, then the new page widens from it, with a darkening on the
  fold and a shadow on the page under it; vanilla's page-turn sound once per turn.
- **"Show me"** calls `AlmanacSceneOpener.open(sceneId)`; `almanac.ponder.AlmanacPonder.register()` installs Ponder's
  handler (`AlmanacPonder.show`). A known id replaces the book with Ponder's screen; Ponder keeps the book on its back
  stack, so its back button or Backspace returns to the same page, while Escape closes Ponder and the book together.
  Leaving the book for Ponder counts as closing it (the placed tome shuts; it does not reopen on the way back). An
  unknown or malformed id logs a warning and the reader stays on the page.
- An empty book (no sync yet) shows a single page saying so, without bookmarks.

Placeholder textures come from `tools/gen_almanac_textures.py` (standard library; `textures/gui/almanac/book.png` and
`bookmark.png`, `AlmanacTexturesTest` fails while the committed files differ from a fresh run); ART14 replaces them.

Client config, section `almanac_screen`:

| Key | Default | Meaning |
|---|---|---|
| `page_turn_ticks` | `8` (0–40) | Length of the page-turn animation; 0 turns at once |
| `page_turn_sound` | `true` | Vanilla's page-turn sound on every turn |
| `scale` | `1.0` (0.5–2.0) | Size of the book; always shrunk to fit small windows |

## Scenes

"Show me" scenes are [Ponder](https://github.com/Creators-of-Create/Ponder) scenes (ALM4). Ponder and Flywheel ship
inside our jar (jar-in-jar on both loaders, docs/releasing.md); everything of ours lives in
`common/.../almanac/ponder/` and runs on the physical client only.

- **Registration.** `AlmanacPonderModule.initClient()` calls `AlmanacPonder.register()`, which adds
  `AlmanacPonderPlugin` to `PonderIndex` during mod construction (NeoForge) or the client entry point (Fabric). Ponder
  registers every plugin's scenes once loading completes (NeoForge `FMLLoadCompleteEvent`, Fabric `CLIENT_STARTED`).
  No loader code of ours is involved.
- **Ids.** `AlmanacScenes.ALL` lists every scene as plain data: its id (what a chapter's "Show me" element carries),
  the item it is registered for (the Ponder key, "hold W" over the item, lists it), and its schematic (always the
  scene's own id). An item with two scenes lists them in this order. The scenes (all ids `pirates_n_ships:`):

  | Scene id | Item (Ponder key) | Schematic size | Length | What it shows |
  |---|---|---|---|---|
  | `cannon_loading` (ALM4) | `cannon` | 7×3×7 | 30 s | The gun, loading powder and shot, aiming, firing, recoil. |
  | `ship_assembly` (PND1) | `helm` | 9×5×9 | 40 s | A hull on the water, the helm placed, the connected blocks outlined as one ship that then rolls on the sea, terrain left out, a barrel placed aboard joining it, disassembly through the ship screen with the heading rounded to a quarter turn. |
  | `steering` (PND1) | `helm` | 11×5×11 | 37 s | Taking the wheel, the wheel turning (the helm's block entity) and the rudder angle it gives (35° at three quarters of a turn), the rudder needing way, a helmsman assigned with the whistle steering for a course mark, then the boat sailing part of its turning circle to starboard (not to scale). |
  | `sails_and_winch` (PND1) | `sail_winch` | 7×8×7 | 39 s | Mast, the two yards and the rule that pairs them, the cloth's area, the winch's cycle furled, half, full (the real cloth renderer hoisting), the wind arrow and the cloth drawing, reefing to half in a strong wind, trimming one sail at its yard, the crew at the winch. |
  | `anchor_and_capstan` (PND1) | `capstan` | 9×7×9 | 39 s | The stowed anchor where the hull-side rule hangs it, letting go (falling at 4 blocks/s, the chain running out), holding in the wind, dragging, the too-deep refusal, weighing (2.5 blocks/s), then a crew member at the capstan on the whistle: the bars turning (the synced turning of CAP2) and the hand walking round the drum by the real `CapstanWalk`. |
  | `pumps_and_flooding` (PND1) | `bilge_pump` | 9×5×9 | 34 s | A dry hold, a breach below the waterline, the flood rising row by row, the pump's handle working against the open breach, the hull patch closing it, the hold drained, the crew's "Man the pumps". |
  | `hull_repair` (PND1) | `hull_patch` | 7×4×7 | 23 s | A breach, the recipe, aiming at the hole's edge from inside, the patch block closing it at once (no repair time), the water left inside, where a patch goes. |
  | `mooring` (PND1) | `mooring_ring` | 9×5×9 | 32 s | Coming alongside a quay, counting as moored within a port's 32 blocks, a grappling line thrown onto the quay and tied off at the ship's ring to hold her, the same from the quay's ring, casting off, cleats. There is no separate mooring-rope mechanic. |
  | `cargo_and_weight` (PND1) | `cargo_crate` | 9×6×11 | 32 s | The assembly ballast floating the empty ship level, crates placed aft and filling through their `load` states, the ship trimmed by the stern and lower, restowed amidships and level again (the ballast counts containers empty), a laden ship slower, the load levels. |
- **Opening one.** `AlmanacPonder.show(ResourceLocation id)` (client thread) opens exactly that scene for one of our
  scene ids, every scene of the item for an item id (ours and other mods'), and logs and ignores anything else
  (returns false, no crash). This is the hook the Almanac screen's "Show me" button calls (ALM3).
- **Text.** Every line is Ponder *shared text* with an explicit key (a `SceneLine`: `CannonLoadingText.Line`, the
  per-scene enums of `ShipSceneText`; `SceneText.ALL` lists every scene's title and lines), not Ponder's
  numbered `text_1, text_2…` (those follow call order, so a reordered scene would swap translations). Lang keys:
  `pirates_n_ships.ponder.shared.<scene>.<line>` and the title `pirates_n_ships.ponder.<scene>.header`; datagen writes
  the English (`AlmanacPonderModule.gatherData`). Ponder only uses the code's English defaults in its editing mode.
- **Truth to the code.** A scene drives the real blocks: the cannon scene steps `CannonBlock.LOAD` through
  `CannonLoad` (empty, powder, loaded, empty) and the elevation through `CannonBlockEntity`, and puts the muzzle flash
  where `CannonRules.muzzle` puts the muzzle. The ship scenes do the same where the game has a state to show: yard
  trims and the cloth geometry from the yard rule, the helm's wheel angle, the capstan's phases and synced bar turning,
  the crew member's walk (`CapstanWalk`), the anchor's hawse (`HullSide`) and speeds, the pump's pumping flag, the
  `hull_patch` block, the crates' `load` states.
- **What Ponder cannot show as the game does** (PND1): a moving ship is a Ponder section moved by element transforms,
  not Sable (assembly's roll, the turning circle, coming alongside, trim and draft under cargo, to be seen rather than
  measured); flood water is water blocks a row at a time (the game draws a smooth flat surface); the anchor chain is a
  column of chain blocks (the game draws a catenary from the hawse, which needs a ship); the grappling hook is an item
  on a drawn arc and the line a Ponder line (the hook entity needs its thrower). The sail's cloth bellies with the
  client's real wind, so the scene's wind arrow takes that wind's side; on land a wind along the yards makes the cloth
  luff. Items in storyboards are looked up by id (`SceneKit.item`), so `PonderStoryboardsTest` can build every
  storyboard headless.

### Adding a scene

1. **Schematic.** Write a BuildSpec generator `art/structures/ponder/<scene>.py` (pattern:
   `art/structures/ponder/cannon_loading.py`; y 0 is the base plate Ponder fades in first, keep it a full square) and
   run `python3 tools/build_structures.py ponder/<scene>`. The pipeline writes the schematic
   (`art/schematics/structures/ponder/<scene>.schem`), a render (`art/renders/structures/ponder/<scene>.png`) and,
   for the `ponder` group, the NBT to `common/src/main/resources/assets/pirates_n_ships/ponder/<scene>.nbt`, where
   Ponder loads it from. Commit all four.
2. **Storyboard.** A class like `CannonLoadingScene` with `static void program(SceneBuilder, SceneBuildingUtil)`;
   its first call is `scene.title("<scene>", "<English title>")`, so the scene id is `pirates_n_ships:<scene>`.
3. **Text.** Its lines and title as plain data (an enum implementing `SceneLine`, like those of `ShipSceneText`),
   added to `SceneText.ALL`; `AlmanacPonderPlugin.registerSharedText` registers and `AlmanacPonderModule.gatherData`
   writes them; run `./gradlew :neoforge:runData`.
4. **Plugin entry.** Add the scene to `AlmanacScenes.ALL` (id, item, schematic) and its storyboard to
   `AlmanacPonderPlugin.STORYBOARDS`.
5. **"Show me".** A chapter's "Show me" element names the scene id `pirates_n_ships:<scene>`.
6. **Tests.** `AlmanacScenesTest`, `AlmanacPonderPluginTest`, `PonderSchematicsTest` and `PonderStoryboardsTest`
   pick the new scene up from `AlmanacScenes.ALL` (the last builds the storyboard headless: its id, 20 to 40 s of
   idles, keyframes, every line said; name the class `<CamelCaseId>Scene`); add the positions your storyboard relies
   on to `PonderSchematicsTest`.
   The scene itself plays only in a client: add its checks to the Ponder playtest.

## Generated from the guide (ALM5)

The mod's own chapters are `docs/guide.md`, so the guide has one source. Two steps, both after every change to
`docs/guide.md` (next to `python3 tools/gen_guideme.py` while GuideME lives):

```
python3 tools/gen_almanac.py        # docs/guide.md -> common/src/datagen/almanac/guide_chapters.json (+ illustrations)
./gradlew :neoforge:runData         # intermediate -> data/pirates_n_ships/almanac/chapters/*.json and en_us.json
```

- **The intermediate** `common/src/datagen/almanac/guide_chapters.json` holds every chapter in the file format above
  (`body`) and a `lang` map from each key to its English text. It is no resource and not shipped. Datagen
  (`almanac/data/AlmanacChapterProvider`, found by walking up from the data run's working directory) parses every
  chapter with the chapter codec (so a bad item id fails the data run), checks that every key has a text and writes the
  chapter files and the lang. `python3 tools/gen_almanac.py --no-images` skips the illustrations (fast);
  `--out FILE` writes the intermediate elsewhere (the staleness test).
- **Chapters:** one per `## N. Title` section, id and icon and bookmark colour from `CHAPTERS` in the script, `order`
  10 × N. Section 0 (The guide book) is the welcome chapter, with a generated Contents page that links every other
  chapter; 15 (What does not exist yet) is the last. A new section in the guide stops the script until it is added to
  `CHAPTERS`.
- **Pages:** the first page opens with the chapter title as a heading and the text before the first `###`; every
  `### Heading` starts a new page with that heading (a chapter with no text before its first `###` puts that
  subsection on its first page). The screen continues pages that overflow.
- **Markup:** `**bold**` stays, `*italic*` becomes `_italic_`, inline code becomes plain text; links to a heading of
  the guide become `[[pirates_n_ships:<chapter>|text]]` (plain text inside the chapter itself), links to repository
  files and the web become their text; the first bold name of one of our items on a page (`**Helm**`, `**yards**`)
  gets its `{item:...}` icon in front. Literal `*`, `\`, `{`, `[[` and underscores at a word edge are escaped.
- **Blocks:** paragraphs become `text`, `- ` lists a `list` (at most 64 entries each), numbered lists one `text` per
  step with the bold number, and tables (the screen has none) a `list` with one entry per row: the first cell bold
  (with item icons when the column holds item names, as in gen_guideme), descriptive columns ("What it does",
  "Meaning", "Today", ...) after a colon, the others as `header: value` in brackets. `---` is dropped.
- **Lang keys** follow the convention: `pirates_n_ships.almanac.<chapter>.p<page>.heading`, `.text<n>`,
  `.list<n>_<entry>`, `.show_me<n>`.
- **"Show me":** `SHOW_ME` in the script maps a (chapter, subsection heading) to a Ponder scene and a label; the button
  follows the first paragraph of that page (today the cannon page of Weapons and combat).
- **Illustrations:** `ILLUSTRATIONS` maps a chapter (its first page) or a (chapter, subsection heading) to a render in
  `art/renders` and the panel of it to use (fractions; a render sheet often holds several views and labels). The script
  keys out the panel's flat background (its corner colour), trims it to the model, scales it to at most 256 × 160 pixels
  (box filter, standard library PNG reader and writer) and writes
  `common/src/main/resources/assets/pirates_n_ships/textures/almanac/<render>.png`; the page gets an `illustration`
  at x 0.05, y 0.09, 0.9 × 0.35 (under the heading line). One per page. An entry for a heading the guide no longer has
  stops the script.
- **Tests:** `tools/test_gen_almanac.py` (the conversions; run by JUnit), `AlmanacGuideTest` (the committed
  intermediate is a fresh run; the generated files and lang match it; pages parse with the codecs; every key, item
  icon, link target and illustration exists; illustrations under 2 MB; the whole book under 512 KB on the wire, it is
  about 27 KB) and `AlmanacDataGameTests` (every chapter loads in a server, parsed with our items registered).
