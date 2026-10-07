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
side by 9 GameTests (`MapTileGameTests`). The block model is a datagen placeholder (Blockbench model later); the
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
