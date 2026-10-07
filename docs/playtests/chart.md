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
