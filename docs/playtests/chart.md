# Playtest: the chart (work package MAP1)

Classification, merging, the caps, codecs, markers, projection, raster and streaming are covered by 40 JUnit tests
and 8 GameTests. Nothing has been seen in game: the look, the textures, the key and the feel need a client.

## Steps
1. **Filling while sailing:** craft or give `pirates_n_ships:chart`, right-click it on a coast, sail along the coast
   with the chart closed and reopen it now and then: the area around you appears within about 2 s with an ink line on
   the land side of the shore, hatched shallows and wave ticks in deep water; left open while sailing, new regions
   appear live.
2. **Your ship:** the small ship icon sits at your position, bow the way you face, north up.
3. **Zoom and pan:** −, +, the wheel over a feature, dragging, Centre: three zoom steps, the point under the cursor
   stays put while zooming, no dragging far beyond the charted area, Centre jumps to you; close and reopen: the same
   view.
4. **Markers:** right-click, choose the skull, type "Dead Man's Chest", save; left-click it, rename, change the icon,
   save, delete; a 33-character name stops at 32; with `max_markers = 1` a second marker shows "Your chart holds no
   more markers" in red; changes survive a relog and death.
5. **Key without the option:** M with empty hands gives an action-bar hint; with the chart in hand it opens; M inside
   closes.
6. **Key with the option:** `open_without_item = true` and `/reload`: M opens with empty hands; the tooltip says so.
7. **Other players:** two clients and `show_other_players = true`: the other appears as a ship with heading and a
   name on hover; off by default.
8. **Disabled:** `enabled = false`: the item and the key say charts are disabled, nothing is charted.
9. **Doodles:** over open ocean serpents, whales and small roses appear only over wide deep water; `chart_visuals
   .doodles = false` removes them.
10. **Nether:** the overworld chart with "Only the overworld's seas are charted", no own ship, no sampling.
11. **Art:** the item icon and the sheet art (compass rose, ships, markers) read at GUI scale 2 and 3.

## MAP2: map tile

What the single tile does today: one tile shows one 128 x 128 cell area of the drawing player's own chart (one
pixel per cell, 512 blocks square at the default 4-block cells), with or without that player's markers. Drawing again
replaces the tile's picture (after a confirm click). Boards of several tiles, updating a drawing with newer knowledge,
clearing a tile and the ink cost come with MAP3 and are not in this build.

The data, packing, rules and raster are covered by JUnit tests (`MapTileDataTest`, `MapTileRasterTest`), the server
side by 10 GameTests (`MapTileGameTests`). The block model is a datagen placeholder (Blockbench model later); the
drawing itself is drawn by the block entity renderer.

1. **Craft:** 8 sticks around 1 paper in a crafting table gives one Map Tile; the tooltip of a blank tile says to use
   it with a chart. Blank tiles stack to 64.
2. **Place on a table:** place it on a floor block, a slab and a fence post (all hold); it lies flat, one pixel thick.
   Placing it under a ceiling is not possible.
3. **Place on a wall:** look at the side of a stone block and place it: it hangs flat on the wall. Break the wall
   block: the tile pops off as an item.
4. **Open the draw mode:** chart charted near you in hand, right-click the tile (also sneak-right-click, also with the
   chart in the off hand and an empty main hand): the chart opens with the title "Draw on map tile", a frame centred
   on you, the hint "Drag the frame to choose the area, then draw", a Markers toggle and a Draw button.
5. **Pick a region:** drag the frame; the area line shows its x and z and "512 blocks square"; the frame cannot be
   dragged off your charted area (when you have charted less than the frame, it always covers all of it).
6. **Draw without markers:** Markers off, Draw: the action bar says "You draw your chart onto the tile", a
   cartographer sound plays, and within a moment the tile shows the area exactly like the chart screen at one pixel
   per cell (hatched shallows, dotted land, inked coast), unknown areas plain parchment, a dark ink border, a worn edge
   and a small compass rose in the top-right corner. North of the drawing points away from where you stood when you
   placed a floor tile, and up on a wall tile.
7. **Draw with markers:** use the tile again, Markers on, Draw (the button first says "Redraw", then "Redraw?";
   click twice): your markers inside the area appear as icons; standing close, their names are written under them.
   Markers outside the area are left out.
8. **Look at it:** crosshair on a drawn tile: two lines say "Drawn by <name> on day <n>" and the area's x and z range.
   Walk around a floor tile and look from all four sides; look at a wall tile from the front, from steep angles and
   from behind (from behind, only the backing shows, no picture bleeding through); step back to 32 and 64 blocks: the
   picture stays, no flicker or z-fighting against the parchment. A second player sees the same picture.
9. **Redraw:** draw a different area: the whole picture (and the markers) is replaced, nothing of the old one stays.
10. **Break and re-place:** break the drawn tile with an axe (fast) and by hand: it drops one tile whose tooltip names
    the drawer and the area; place it elsewhere (also on a wall): the same picture appears. A drawn tile does not stack with
    blank ones or with tiles showing another drawing.
11. **Refusals (action bar):** no chart in hand: "You need a chart in hand to draw on the tile"; walk more than 8
    blocks away with the screen open and click Draw: "You are too far from the map tile"; break the tile while the
    screen is open and click Draw: "That map tile is gone".
12. **Config off:** `chart.tiles.enabled = false` and `/reload`: using the tile says "Drawing on map tiles is disabled
    on this server" and the chart does not open; tiles drawn before keep their pictures and can still be broken and
    re-placed. `chart.tiles.require_chart_item = false`: an empty hand opens the draw mode. `chart.tiles.redraw_allowed
    = false`: a drawn tile says "This tile's drawing is permanent"; a blank one can still be drawn once.
13. **Ships:** place a tile on a ship deck, draw it, sail: the picture moves with the ship and stays on the tile.

## MAP3: boards, updates, clearing, ink

Map tiles placed side by side in a full rectangle (same floor height or same wall, same facing) form one board of up
to 8 x 8 tiles. Using any tile of it with a chart draws one chart area across all tiles, at a zoom of 1 to 8 (each
tile pixel covers zoom x zoom chart cells). A drawn board can be updated by anyone (their newer knowledge fills in, the
old picture stays where they know less), redrawn at a new area or zoom, or cleared. Drawing costs ink: one ink sac or
glow ink sac per tile (kraken ink pays for 8 tiles); an update pays only for tiles that changed; creative is free.
This replaces MAP2's step 12 for `redraw_allowed = false`: a drawn board then opens in update mode (no New, no Clear).

Covered headlessly: `BoardRulesTest`, `BoardMergeTest`, `InkCostTest`, the zoom tests in `MapTileRasterTest`,
`MapTileDataTest`, and 9 GameTests in `MapBoardGameTests`. What only the game can show: the screen, the picture across
several tiles, orientation on floors and walls, seams.

Preparation: two players (or one player and a second account), both with a chart that knows a stretch of coast; a
stack of ink sacs, a few glow ink sacs, one kraken ink (`/give @s pirates_n_ships:kraken_ink`), about 12 map tiles.

1. **2 x 2 on a table:** put 4 tiles on a 2 x 2 table top (same direction: place them all while facing the same
   way). Right-click one with the chart: the title says "Draw on map tile", the panel in the top-left corner reads
   "Board 2 x 2  zoom 1" and "Ink: 4 (you have N)", and the frame on the chart is square with thin lines where the
   tiles meet (twice as wide and high as a single tile's frame).
2. **Draw at zoom 1:** drag the frame over your coast and click Draw: the four tiles together show one continuous map;
   coast lines, hatching and dots run across the seams without a jump. The ink border and the worn edge run only round
   the outside of the whole board, not between tiles; one compass rose, in the top-right corner of the top-right tile.
   Four ink sacs are gone.
3. **Orientation:** stand where you stood when you placed the tiles: north of the drawing points away from you, the
   board's left column is on your left. Walk round it: it reads as one map from every side.
4. **3 x 1 on a wall:** hang 3 tiles side by side on a wall (all facing out of the same wall). The panel says
   "Board 3 x 1"; draw: the map runs left to right as you look at the wall, north up. Try a 1 x 3 column on a wall too
   (top tile is the north end of the drawing).
5. **Zoom 4:** on a 2 x 2 board, use the panel's + three times: "zoom 4", the frame grows four times per side (it may
   not fit into the chart view; pan to see it, drag it inside). Draw (Redraw, Redraw?): the board shows a 4 x 4 times
   larger area, coarser; land and sea still read correctly, coasts stay inked, small islands may merge into the most
   common class of their block. The HUD's third line says "Board of 2 x 2 tiles, zoom 4".
6. **Second player updates:** the second player charts more of the area (sails into what was parchment), then uses
   the board: the panel says "zoom 4 (as drawn)", the frame cannot be dragged, the button says Update and the panel
   "Ink: up to 4 (you have N)". Click Update: the parchment parts the second player now knows fill in; everything the
   first player drew is still there. The crosshair lines say "Updated by <second player> on day <n>". Ink: one per
   tile that changed (a tile that got nothing new costs nothing). Updating again at once says "Your chart adds nothing
   new to this board" and takes no ink.
7. **Markers on update:** the first player draws with Markers on (a marker right on the line between two tiles is
   best). The second player updates with Markers on: both players' markers show, and the marker on the seam shows its
   whole icon across both tiles (its name only once, on the tile that owns it).
8. **New drawing instead:** on an updatable board the panel's "New" button unlocks the frame and the zoom (the button
   now reads Update to go back); Draw then asks "Redraw?" and replaces everything.
9. **Clear:** click Clear in the panel, then "Clear?": the screen closes, all tiles go blank (plain parchment model),
   no ink is taken. Using a blank board's tile again shows no Clear button.
10. **Ink counting:** with 3 ink sacs, a 2 x 2 board: the panel line is red ("Not enough ink: 4 needed, you have 3");
    clicking Draw gives "You need 4 ink for this (you have 3): ink sacs, or kraken ink" and nothing is drawn. Add one
    kraken ink: the panel says "you have 11"; Draw uses the kraken ink and leaves the 3 sacs. Glow ink sacs count like
    ink sacs. In creative the panel says "Ink: free". With `chart.tiles.ink_cost_enabled = false` nobody pays.
11. **Not a rectangle:** place 3 tiles in an L and use one: "The tiles do not form a rectangle", no screen. A 9 x 1
    row: "The board is too big" (`chart.tiles.max_board_side` 8).
12. **Breaking and re-placing a tile:** break one tile of a drawn 2 x 2 board: the item's tooltip names the drawer, the
    whole board's area and "Board of 2 x 2 tiles, zoom ..."; the other three tiles keep their pictures. Using one of
    them now says "The tiles do not form a rectangle". Place the item back in the same spot, facing the same way as
    the others: its picture returns, and using the board opens in update mode again. Placed into a different board or
    turned the wrong way, the board counts as mixed: Draw replaces everything (after "Redraw?").
13. **Big board:** an 8 x 8 board at zoom 8 (create mode is fine): drawing takes at most a moment on the server (watch
    for a lag spike in the log); all 64 tiles show their part (textures appear within a second or two, no flicker when
    looking around).
14. **Redraw off:** `chart.tiles.redraw_allowed = false`: a drawn board opens with only Update (no New, no Clear);
    updating works; a board with leftovers of another drawing (step 12's mixed case) says "This tile's drawing is
    permanent".
