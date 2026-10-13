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
7. ~~**Other players:**~~ withdrawn by CHART2: the chart never shows other players (see CHART2 step 9).
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

## CHART2: zoom out, markers list, your icon, ships, structures

What changed: three more zoom-out steps (`chart.max_zoom_out` 3, up to 5), no marker limit (`chart.max_markers` 0 =
none), a **Markers (n)** button where the "n / 64 markers" count stood, the player as a compass needle, the ship you
stand on and your own ships with their headings (`chart.show_ships`), ports, wrecks and the mod's other structures with
icons and a hover legend (`chart.show_structures`, `chart.ports_always_known`, `chart.structure_reveal_distance` 96).
**Rule (the human, 2026-10-12): the chart shows only its owner.** Other players and their ships never appear, on any
setting; `chart.show_other_players` (MAP1's step 7) is gone. NPC convoys and patrols do not appear either.

Covered headlessly: `ChartProjectionTest` (the scales), `ChartZoomOutTest` (far pixels = a board's zoomed pixels),
`MarkerListTest` (order and bearings), `StructureRevealTest` (the reveal rule), `MarkerRulesTest` (0 = no limit),
`ChartCodecsTest` (the new state payload, 4096 markers in one payload), `ChartTexturesTest` (the new icons) and the
GameTests `ChartGameTests` (unlimited markers, the cap when set, ports in the state and no other player, ports
revealed by distance, a wreck start revealed and a vanilla one not, only the owner's chart showing a ship, the
switches) and `MapBoardGameTests` (100 markers on a board). The screen itself needs a client.

Preparation: an old dev config file still has `max_markers = 64`; delete that line (or the file) to get the new default.
A world with a few ports (`/pirates world ports` or `/locate structure pirates_n_ships:seafarer_village`), a wreck
nearby (`/locate structure pirates_n_ships:wreck`), a ship of your own, a second account for step 9.

1. **Zoom out:** open the chart, press − (or scroll down) repeatedly: after MAP1's widest step come three more; the
   scale bar reads about 250, 500 and 1000 blocks at the last steps (32 blocks per pixel at 4-block cells, a 400 px
   map spans about 13,000 blocks). The coasts stay recognisable: land and sea in their colours, the coast inked, small
   islands may vanish into the sea around them. No doodles beyond MAP1's widest step. Zooming back in is instant and
   the point under the cursor stays put. Pan around at the widest step: no stutter, textures fill in within a second.
   `max_zoom_out = 0` and `/reload` (or a relog): only MAP1's three steps; `5`: two more (128 blocks per pixel).
2. **No limit:** place more than 64 markers (a quick way: give yourself a chart, right-click, Enter, repeat); the 65th
   is accepted, no "Your chart holds no more markers". With `max_markers = 3` the 4th is refused in red.
3. **Markers list:** the bottom-right button reads "Markers (n)" with your count. Click it: a parchment panel on the
   right lists every marker, nearest first, icon, name (an unnamed one reads "Marker at x, z") and "NW, 340 blocks"
   (or "here" on top of it). The wheel over the panel scrolls it (a scroll bar shows when it is longer than the panel).
   Click a row: the chart centres on that marker, its ring flashes for about 3 seconds and the row gets a red edge; the
   list stays open. Click the button again or press Esc to close it (Esc with the list closed closes the chart). With
   no markers it says how to place one. Clicks on the list never pan the map or place markers.
4. **Map board:** use a map tile with your chart: the footer has the same "Markers (n)" button left of the Markers
   toggle; the list and focusing work the same while you pick the board's area.
5. **Your icon:** you are a small compass needle, red tip forward: turn on the spot and the tip turns with you (north =
   up when you look north). Hover it: "You". It is clearly not a marker or a ship.
6. **Your ships:** stand on your assembled ship: a parchment-sailed ship icon sits on the ship and points along the bow
   (sail a circle: it turns with the ship, about once a second); hover: "Aboard: <name>". Step ashore and walk 50
   blocks off: the ship stays on the chart with navy sails, hover "Your ship: <name>". A ship far away in unloaded
   chunks is not shown. `show_ships = false`: no ships, the needle stays.
7. **Ports:** every port of the world is on the chart from the start with its icon (village: red-roofed houses, navy
   outpost: a grey tower with a navy flag, pirate island: a palm on sand) and, from MAP1's widest step inward, its
   proper name (NAME3; its id while `world.ports.names` is off) under it; hover: the name and the kind. `ports_always_known = false`: a new
   player sees no ports; sail within 96 blocks of a port's walls and it appears for good (also after a relog and death).
8. **Wrecks:** dive or sail within 96 blocks of a wreck: within two seconds a wreck icon (a broken hull in the waves)
   appears at it, hover "Wreck"; it stays after you leave. Vanilla shipwrecks, ruins and monuments never appear.
   `structure_reveal_distance = 16`: you have to pass right over it. `show_structures = false`: no ports or wrecks.
9. **Other players never:** two clients, both with charts, standing next to each other, one on the other's ship: each
   chart shows only its own needle and its own ships; nothing of the other player, not even their ship when you stand
   beside it. (The old option `show_other_players` no longer exists.)
10. **Nether:** open the chart in the Nether: no needle, no ships, no structures, the "Only the overworld's seas are
    charted" line, as before.
11. **Art:** the needle, the five structure icons and the list read at GUI scale 2 and 3; labels do not cover the icons.

## CHART3: the chart held open in 3D

What changed: the chart item is a parchment on two wooden rollers (closed: both rolls side by side, tied with a cord,
held like a tool). While you read your chart (the screen is open) the server sets a synced flag on you
(`chart.held_open`, default true), and every client that sees you unrolls the chart in your hands over half a second:
the arms come up with the fists together round the rolled chart, then draw apart to the shoulders while the rollers
spread; the open chart (a map with two islands, a dotted course, a red X and a compass rose) stays between both fists
at chest height, tilted up towards your eyes. Closing the screen rolls it up the same way backwards. Item model frames
`chart_unroll_0..3` and `chart_open` (item property `pirates_n_ships:chart_open`), arm animations `chart_read` and
`chart_stow` through the Player Animation Library (`assets/pirates_n_ships/chart_animations/`). The face shows a fixed
painted chart, not your own map.

Covered headlessly: `ChartHeldModelTest` (vanilla's real render chain for both hands: rollers in the fists, the map
facing the reader and reading left to right, chest height, first person low and centred; the frames, overrides and
PAL files), `HandMadeModelsTest`, `lint_models.py` (0 visible fights), and the GameTests `ChartHeldGameTests` (the flag
is set while the chart is open, cleared on close, never set with `held_open` off). Renders `art/renders/chart_held.png`.

Preparation: two clients (a second account, or a LAN world joined by a second instance), both with a chart.

1. **Closed in hand:** hold the chart (hotbar, first and third person, F5): two parchment rolls on dark wooden rollers
   with knobs at both ends and a cord round the middle, held diagonally like a tool; in the inventory slot the whole
   roll is visible. Left hand (off hand) the same.
2. **First person, opening:** right-click with the chart. Behind the chart screen's blurred background the chart
   unrolls at the bottom of the view (half a second); close the screen (Esc): the open chart is low in the middle of
   the view, tilted away like vanilla's map, and rolls up within half a second. No flicker between the frames beyond
   the visible steps (CHART3: five frames; CHART4: fourteen, see below).
3. **Third person (F5, front and back):** press F5 first, then open the chart (the world shows blurred behind the
   screen; closing it shows the roll-up sharply): both arms come forward and the fists meet round the
   rolled chart, then draw apart; each fist grips one roller in its middle, the knobs stick out above and below the
   fists, the map faces you, tilted up. The rollers do not float beside the fists and the chart is not mirrored (the
   compass rose at the top right, from your side).
4. **A second player watching:** player B stands in front of player A, then beside them. A opens the chart: B sees
   A's arms rise and the chart unroll between A's hands (B sees the plain back of the parchment, the rollers and the
   knobs); A closes it: it rolls up and the arms drop back to the normal holding pose. B walks around A while the chart
   is open: the chart stays between A's fists from every side. B comes into view while A already reads (walk away 64+
   blocks and back): A is shown with the chart open at once, no unroll.
5. **Left hand:** put the chart in the off hand with an empty main hand (or switch Main Hand to Left in Skin
   Customization) and open it with M: the same pose, the chart still between both fists and readable (not mirrored).
6. **Key without the chart:** with `chart.open_without_item = true`, open the chart with M and empty hands: no pose,
   nothing unrolls (the flag is set, but nobody holds a chart).
7. **Switching items while reading is impossible**, but closing and immediately scrolling the hotbar: the chart's
   hotbar icon may show a rolling-up frame for up to half a second (known, minor).
8. **Option off:** `chart.held_open = false` (server config) and `/reload` or a restart: opening the chart keeps it
   rolled in the hand for you and for B.
9. **Without PAL** (if you can remove the Player Animation Library jar): the chart still unrolls, held in vanilla's
   one-armed pose (the chart then floats to the side of the hand; expected).

## CHART4: block colours on the chart, the smoother unroll

What changed: every sampled cell keeps, beside its class, vanilla's map colour of its columns (the most common colour
of the cell's 4 x 4 columns, a brightness step from the height against the cell to the north, like a vanilla map at
4 blocks per pixel). The chart draws the land in those colours, desaturated and laid onto the parchment
(`chart.style` `COLOURED`, the default; `INK` is the old look); the sea keeps its wash, the coasts their ink. Far zoom
tiles take the most common colour of their land cells. Map tiles and boards stay in ink. The held chart now has 14
frames (`chart_unroll_0..12`, `chart_open`) and its arm animations are keyed every 25 ms along an ease-in-out curve,
so unrolling and rolling up start and stop gently.

Covered headlessly: `MapColoursTest` (vanilla's ids for grass, sand, planks, water, stone, the path, waterlogged
slabs and kelp; brightness steps from height and depth; the dominant colour), `ChartColoursTest` (merge, save and
old saves, the region payload's size, far-tile downsampling, ink vs coloured, the client's redraw on a new style),
`ChartHeldModelTest` (14 frames, eased roller spacing, keys every 25 ms, the rig for every frame), and the GameTests
`aSampledVillageCellCarriesThePlanksColour`, `theStyleSwitchesAtRuntime`, `aBoardFromAColouredChartIsDrawnInInk`,
`theUnrollShowsEveryFrameInOrder`. Render of both styles: `art/renders/chart_styles.png`; held frames:
`art/renders/chart_held.png`.

1. **A village on the chart:** sail or walk past a vanilla village (or one of the mod's ports) and open the chart at
   the closest zoom. Expected: the houses' roofs and walls show as brown and grey patches, the paths as a lighter
   brown-ochre, fields and grass in a muted green, sand in pale yellow, snow white; slopes facing south look lighter,
   north faces darker. The colours are softer than a vanilla map's and the ink markers, labels and icons stay
   readable on top. Water is the old blue wash; the coastline is still a black ink line.
2. **Zoomed out:** scroll out to the widest step: the village is still a small coloured spot; forests, plains and
   deserts read as green, light green and yellow areas.
3. **Old charts:** in a world from before this version, open the chart: the parts charted earlier are in the old
   inks; walk past one of them again and it turns coloured within two seconds.
4. **Style switch:** set `chart.style = "INK"` in the server config (the config screen or the file, then `/reload`)
   and open the chart again: the land is the old parchment inks, everything else unchanged. Back to `COLOURED`: the
   colours come back on the next opening.
5. **Map board:** draw a board over the village: the board is in the old ink look (by design).
6. **Smoother unroll:** open and close the chart in third person (F5) and watch another player do it: the rollers
   spread in many small steps (no visible jumps), slow at the start and when they reach the shoulders; rolling up
   follows the same curve backwards. Closing half way through the unroll rolls it up from where it was.
7. **Performance:** with a few players flying over new land (elytra), the server's tick time (F3 or `/neoforge tps`)
   stays where it was before this version; the chart sampling reads about 16 columns per cell every 40 ticks
   (`chart.colour_columns` 2 reads 4).

## CHART5: the ink chart with built cells in colour

What changed: the chart is back to the ink look by default (`chart.style` `INK`), and only built cells draw in colour:
a land cell is built when at least `chart.built_columns` (2) of its sampled columns have a block of the tag
`pirates_n_ships:chart_built` on top (planks, wooden slabs, stairs, fences, gates, doors, trapdoors, wool and carpets,
bricks, stone bricks, cobblestone and mossy cobblestone with slabs, stairs and walls, all walls, smooth stone, dyed
terracotta that badlands do not generate, glazed terracotta, concrete and powder, glass and panes, dirt paths,
lanterns, campfires, barrels, chests, crafting tables, furnaces, bookshelves, beds, hay bales, scaffolding and every
block of the mod; never logs, leaves, stone, sand, dirt, grass or plain badlands terracotta). The flag is bit 4 of the
cell byte (the colour byte is full: 62 colour ids times 4 steps use all 8 bits), so it rides in the run-length cells
of the region payload, is merged like the class, and a far pixel is built when any of its land cells is. A built
cell takes the colour of its built columns only (a wall across a garden is stone, not green) in CHART4's softened
look. `COLOURED` is unchanged, `PLAIN` is the pure ink. Map boards stay in ink.

Covered headlessly: `ChartBuiltTest` (the tag's members read from the datagen'd file, the built rule, merge, far
downsample, payload size, ink colouring only built cells, a preview at `build/chart5/styles.png`), `ChartCodecsTest`,
`ChartColoursTest`, and the GameTests `builtCellsAreFlaggedAndForestsAreNot`, `aSampledVillageCellCarriesThePlanksColour`,
`theStyleSwitchesAtRuntime`, `aBoardFromAColouredChartIsDrawnInInk`. Render of the three styles side by side (plain
ink, ink with built cells, coloured): `art/renders/chart_styles.png`.

1. **A village:** walk or sail past a vanilla village and open the chart at the closest zoom. Expected: grass, fields,
   forests and hills in the plain parchment inks; the houses, paths, wells and any planks, cobblestone or glass in
   muted colour; the sea and coasts as before.
2. **A forest and a mountain:** stand in a dense forest and on a stone mountain. Expected: both stay ink, no coloured
   spots from logs or stone.
3. **Your own build:** place a small wooden hut, a cobblestone wall and a harbour pier of planks near the shore, wait
   two seconds. Expected: they show in colour; a single stray plank does not (needs two columns in a cell).
4. **Zoomed out:** scroll out to the widest step: a village is a small coloured speck on an otherwise ink chart.
5. **Old charts:** areas charted before this version are plain ink until you walk past them again.
6. **Styles:** `chart.style = "COLOURED"` shows all land in colour again (CHART4); `"PLAIN"` shows pure ink with no
   built colours; reopen the chart to see the change.
7. **Map board:** a board drawn over the village is pure ink (by design).
