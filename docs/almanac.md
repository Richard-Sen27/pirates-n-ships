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
