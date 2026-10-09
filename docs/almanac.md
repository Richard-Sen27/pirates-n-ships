# The Captain's Almanac: chapter format

The Almanac's content is a datapack (design.md §3.4, ALM2). Each chapter is one JSON file; the text is lang keys, so
the server sends only keys and every client shows its own language. The code lives in
`common/.../almanac/data` (model, loader) and `common/.../almanac/sync` (payloads, client copy).

## Files and ids

- Path: `data/<namespace>/almanac/chapters/<path>.json` (subfolders allowed). The chapter id is `<namespace>:<path>`.
- Any datapack can add chapters in its own namespace, or replace one of ours by shipping the same path (the whole file
  is replaced, the top pack wins).
- The mod's own chapters are written by datagen (`almanac/data/AlmanacChapterProvider`, later ALM5's generator from
  `docs/guide.md`); never hand-edit the generated files.
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

`data/pirates_n_ships/almanac/chapters/cannons.json` (shortened):

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
- **"Show me"** calls `AlmanacSceneOpener.open(sceneId)`, a no-op until a handler is installed with
  `AlmanacSceneOpener.install(handler)` (ALM4 installs Ponder's).
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
