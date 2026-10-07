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
     compass rose (N in red at the top), a brown ship-shaped needle, a line like `0.0 kn · rudder midships`, the ship's
     name (or a dim "Unnamed ship") and a strip of cells under it with a pointed bow cap on the left. Jumping does not
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
7. **Name.** Rename the ship (name tag on the helm).
   - **Expected:** the new name shows above the strip within about a second; a very long name ends in `…` inside the
     HUD's width. Once the ship has been weighed (CW1; e.g. after `/pirates ship info` or a while at sea) the load level
     follows the name, dimmed (`Black Pearl · Laden`); load cargo crates until it changes.
8. **Corners and scale.** In the mod's client config (`ship_hud`), try each `corner` and `scale` 0.5, 1.0, 2.0, and
   `speed_unit = BLOCKS`; give yourself a potion effect and then a bad one (e.g. `/effect give @s minecraft:speed`,
   `/effect give @s minecraft:slowness`).
   - **Expected:** the HUD sits 4 px from the chosen corner and grows away from it; at the top right it moves down
     below the effect icons (one row per kind); `BLOCKS` shows `b/s`. `enabled = false` hides it. Check that it does
     not cover the hotbar, the chat or the boss bar in your usual GUI scale (report which corner/scale collides).
9. **Server switch.** Set the server config `ships.ship_status_hud = false`.
   - **Expected:** the HUD disappears for everyone within 3 seconds; back on, it returns within a second.

## Open questions for the playtest

- Is the panel's size right at the default GUI scale (112 × 104 px)? Is the light text readable over bright sky and
  snow (only the speed line has a dark backing)?
- Is the once-a-second update fast enough for the water level and the pump glyph?
