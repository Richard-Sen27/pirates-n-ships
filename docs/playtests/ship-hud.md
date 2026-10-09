# Playtest: the ship HUD (work package HUD1)

The compartment order, the 16-cell cap, the payload codec, the send rule, the layout per corner and scale, the knots
conversion and the client's freshness rules are covered by JUnit tests. GameTests build the 5×4×5 test hull in a basin
and check that a flooded hold with a breach reaches the player aboard (half full, one breach) and nothing reaches a
player on the shore, that a running bilge pump sets the pump flag, the sync interval from config, the change-only rule
and the server switch. Only the real client shows how the overlay looks and whether its needle, wind arrow and cells
agree with the world.

Setup: `./gradlew :neoforge:runClient`, a creative world with cheats, a small ship afloat with a helm (assembled), a
closed hold below the waterline with a bilge pump in it, ideally a sail and a flag. Please send screenshots of each
step and `latest.log`.

## Steps

1. **Ashore and aboard.** Stand on land next to the ship, then step onto its deck, then jump on the deck a few times,
   then step off onto land again.
   - **Expected:** no HUD on land. Within a second of stepping aboard the HUD appears in the top right corner: a
     compass rose (N in red at the top), a brown ship-shaped needle, a line like `0.0 kn · rudder midships` (HUD4: no
     name or load line under it) and a strip of cells with a pointed bow cap on the left. Jumping does not
     make it flicker. After stepping off it disappears within about 3 seconds.
2. **Heading.** Stand on the deck, run `/pirates ship forces` and compare its `heading …°` with the needle; then steer
   (hold use on the helm and turn) through a full circle.
   - **Expected:** the needle's bow points at the compass bearing that `/pirates ship forces` reports (0° = up/N,
     90° = right/E). While the ship turns, the needle turns smoothly with it and the short way round through north.
3. **Wind.** Look at a flag or the sails, and run `/pirates wind get`; then `/pirates wind set 270 8`, and later
   `/pirates wind set 0 20`.
   - **Expected:** a light arrow sits outside the rose on the side the wind comes **from** and points into the rose,
     i.e. along the way the flag streams (for `set 270 8`: the arrow on the left (W) side, pointing right). The stronger
     wind (20) draws a clearly longer arrow than 8. In a thunderstorm gust the arrow turns amber.
4. **Speed and rudder.** Sail with the wind; turn the wheel to starboard and to port.
   - **Expected:** the speed rises in knots (about twice the blocks per second `/pirates ship forces` shows as `speed`);
     the rudder text reads `rudder 12° stb` / `rudder 12° port` with the same angle as the helm overlay, and
     `rudder midships` when centred. A ship without a helm shows the speed only.
5. **Flooding.** Break a hull block of the hold below the waterline.
   - **Expected:** the hold's cell gets a red tick at its top right at once (within a second), and fills with blue
     from the bottom as the water comes in; a full hold is a full blue cell. With several compartments, the strip runs
     bow (left) to stern (right), wider cells for larger compartments.
6. **Pumping and patching.** Hold use on the bilge pump; then put a hull patch into the breach.
   - **Expected:** while you pump, a small pump glyph shows on that cell and its water drops; it disappears within a
     second after you let go. After patching, the red tick goes away.
7. **Name (HUD4: removed).** Rename the ship (name tag on the helm) and load cargo crates.
   - **Expected:** the HUD shows neither the name nor the load level (both are in the ship screen).
8. **Corners and scale.** In the mod's client config (`ship_hud`), try each `corner` and `scale` 0.5, 1.0, 2.0, and
   `speed_unit = BLOCKS`; give yourself a potion effect and then a bad one (e.g. `/effect give @s minecraft:speed`,
   `/effect give @s minecraft:slowness`).
   - **Expected:** the HUD sits `ship_hud.margin` (8 px, HUD4) from the chosen corner and grows away from it; at the top right it moves down
     below the effect icons (one row per kind); `BLOCKS` shows `b/s`. `enabled = false` hides it. Check that it does
     not cover the hotbar, the chat or the boss bar in your usual GUI scale (report which corner/scale collides).
9. **Server switch.** Set the server config `ships.ship_status_hud = false`.
   - **Expected:** the HUD disappears for everyone within 3 seconds; back on, it returns within a second.

## Open questions for the playtest

- Is the panel's size right at the default GUI scale (112 × 104 px)? Is the light text readable over bright sky and
  snow (only the speed line has a dark backing)?
- Is the once-a-second update fast enough for the water level and the pump glyph?

# HUD2: two panels and the flicker

**The flicker's cause.** Two, both fixed. (1) A ship whose rounded status did not change (at rest, moored, or on a
steady course) only got HUD1's keepalive: every fifth sync interval, 100 ticks, while the client dropped a status
after 60 ticks. The panel showed for 3 s and hid for 2 s, over and over. Now an unchanged status goes out about every
2 s and each payload tells the client how long it stays fresh (three keepalive periods and a second, 7 s at the
defaults). (2) On the client Sable only tracks the player on a ship while they touch it from above; a jump (about
12 ticks) outlasted HUD1's 10-tick grace and a ladder hid the panel outright. Now the grace is 1.5 s, and while the
player climbs or is in the air (not in water) the aboard time carries on (up to 20 s).

The layout per corner, GUI size (1920×1080, 1280×720, 854×480 at GUI scale 1 to 4) and HUD scale, the chat, hotbar and
stamina bar clearances, the stacking in one corner, the freshness and grace rules and a minute at rest at every sync
interval are covered by JUnit; a GameTest checks a ship at rest with a player aboard gets a status at least every
40 ticks, each fresh for three times the gap. Only the real client shows how it looks.

Setup as above. Delete nothing: an existing `pirates_n_ships-client.toml` loses its old `ship_hud.corner` on load and
gets `compass_corner = BOTTOM_LEFT` and `hull_corner = BOTTOM_RIGHT`. Please send screenshots of each step and
`latest.log`.

1. **Default corners, GUI scale 2 and 3.** Survival, a sword in your hotbar but another slot selected, stand aboard.
   Check at GUI scale 2, then 3 (Options, Video Settings).
   - **Expected (HUD4):** the compass panel (rose, wind arrow, speed and rudder) at the bottom left, 8 px from the
     edges, whatever the chat shows; the hull strip at the bottom right corner, 8 px from the edges. With a narrow window (e.g. 854×480 at scale 2) the hull strip rises
     above the hotbar, the food row and the stamina bar instead of covering them.
2. **Chat (rewritten for HUD4).** Receive a few messages (`/say hi` several times), then open the chat (T) and scroll.
   - **Expected:** the compass panel does not move; the chat lines and their dark backing are drawn **over** it. With
     the chat open it stays visible (never hidden) and the input line covers its bottom edge. The hull strip never
     covers the chat's input line.
3. **Sword in hand.** Select the sword so the stamina bar shows (above the food row, right of the hotbar's centre),
   then dive under water so the air bubbles push it up.
   - **Expected:** the hull strip never overlaps the stamina bar or the air bubbles; with `melee_hud.position =
     LEFT_OF_HOTBAR` neither panel overlaps the upright bar either.
4. **No flicker aboard.** With the ship anchored or at rest, stand still on the deck for a full minute; then walk the
   deck, stand on the rail, climb a ladder up the mast and down into the hold, sit at the helm, and jump on the deck
   repeatedly.
   - **Expected:** both panels stay the whole time, no blink at all (HUD1 blinked off for 2 s every 5 s at rest).
5. **Leaving.** Jump over the rail into the sea and swim away; separately, walk off onto a dock.
   - **Expected:** both panels vanish about 1.5 s after you hit the water or land on the dock (not while you are
     still falling).
6. **Same corner.** Set `compass_corner` and `hull_corner` both to `TOP_RIGHT`, then both to `BOTTOM_LEFT`.
   - **Expected:** one stacked panel like HUD1's (compass on top, the strip right under it); at the top right it moves
     below the effect icons (`/effect give @s minecraft:speed`); at the bottom left it stays in the corner under the
     chat (HUD4).
7. **Server switch.** `ships.ship_status_hud = false`.
   - **Expected:** the panels disappear within about 7 s (the last status's freshness); back on, they return within a
     second.
8. **HUD3, superseded by HUD4: compass at the bottom left.** A 1920×1080 window at GUI scale 3, `compass_corner =
   BOTTOM_LEFT`, no chat for 10 s, then `/say hi` three times and wait. - **Expected:** the compass panel sits at the
   bottom left, 8 px from the edges (not at the top), and stays there while the three lines show over it.

## Open questions for the HUD2 playtest

- Is the compass panel above the chat too high at a large GUI height (scale 1 on a 1440p screen)? A `y_offset` could
  follow.
- Should the compass hide rather than jump up when the chat opens?
- At the top left the compass panel does not yet keep clear of the rank box (`rank_hud`); does that collide for you?

# HUD4: static, minimal compass panel below the chat

The placement at the margin with and without chat lines, the hull panel's chat clearance, the config margin and the
layer order (NeoForge: right below `CHAT`; Fabric: `MixinGui` at the head of `Gui#renderChat`, target checked by the
fabric ASM test) are covered by JUnit. Only the real clients show the drawing order. Please run both
`./gradlew :neoforge:runClient` and `./gradlew :fabric:runClient`, and send screenshots of each step and `latest.log`.

1. **Spacing and size.** Stand aboard at GUI scale 2 and 3, no chat for 10 s.
   - **Expected:** the compass panel at the bottom left, its rose and speed line clearly in from the left edge (8 px
     margin) and from the bottom; only the rose with the wind arrow and the `kn · rudder` line, no name, no load, no
     empty band under the speed line.
2. **Below the chat.** `/say` a long line five times.
   - **Expected:** the panel does not move; the chat lines and their dark backing draw over the rose and the speed
     line (the text of the chat is readable on top). Open the chat (T): the panel stays visible, the open chat and
     its input line draw over it. Both loaders alike.
3. **Margin.** Client config `ship_hud.margin` = 0, then 32.
   - **Expected:** both panels move to the very edges, then 32 px in; the hull strip still keeps clear of the hotbar
     and stamina bar.
4. **Helm overlay.** Hold the wheel and turn, on a laden ship (crates full).
   - **Expected:** the line above the action bar reads only `Rudder 12° starboard` (no `· Laden`).
5. **F1 and other HUD parts.** Press F1; give yourself an effect; look at the hotbar on a narrow window (854×480 at GUI
   scale 2).
   - **Expected:** F1 hides the panels; at the narrow window the compass rises just above the hotbar's offhand slot instead
     of covering it; the rank box and the stamina bar still draw over or beside it as before.
