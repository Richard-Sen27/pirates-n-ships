# Playtest: Ponder and Flywheel, the cannon scene (work package ALM4)

Ponder and Flywheel are new dependencies, bundled inside our jar on both loaders. The plugin registration, the scene
ids, the shared text and its lang keys, and the scene's schematic are covered by 12 JUnit tests; the GameTest server
starts with Ponder on both loaders (Flywheel is client-only and not loaded there on Fabric). What only a client shows:
that both libraries load, that Sable's Flywheel compatibility (active for the first time) leaves ships looking as
before, and the scene itself.

Setup: `./gradlew :neoforge:runClient`, then the same on Fabric with `./gradlew :fabric:runClient`. A creative world
with a ship (a starter sloop) on water. Please send `latest.log` from both clients.

## Steps
1. **Both libraries load.** In `latest.log`: the mod list shows `Flywheel 1.0.6 (flywheel)` and
   `Ponder 1.0.87+mc1.21.1 (ponder)` (NeoForge; Fabric lists `flywheel 1.0.6+1.21.1` and `ponder 1.0.87+mc1.21.1`),
   our line `Ponder: registered 1 scene(s) of pirates_n_ships`, and Ponder's `Registering Ponder Scenes took …` once
   loading completes. No errors mentioning ponder, flywheel or `pirates_n_ships:cannon_loading`.
2. **Sable with Flywheel.** Sable now logs `NOTE: Sable is loaded with Flywheel. Sable contains extensive shader
   overrides and a full light-storage replacement. Expect this to cause compatibility issues.` (seen in the data run).
   Look closely at ships compared with the last build: block textures and lighting on a ship's hull and deck, at
   night with a lantern on deck, the hull below the waterline (no water drawn inside the dry hull, the water mask),
   ships moving and rolling (no flicker, no blocks left behind or lagging), and the frame rate near several ships
   (F3). Also block entities on ships (cannons, the helm, chests, flags) and GeckoLib entities aboard. Any difference
   is worth a screenshot; the Flywheel backend can be switched with `/flywheel backend` to compare.
3. **The Ponder key.** Hover the cannon in the creative inventory. Expected: Ponder's "Hold [W] to Ponder" hint; holding
   W opens Ponder with "Loading and Firing the Cannon".
4. **The scene, about 30 s.** A 7×7 spruce deck fades in, then the bulwark, the cannon at its gun port, a barrel to its
   left and a chest to its right. Then in order: the gun outlined with "The cannon is two blocks long…"; "Load it in
   order: powder first, then the shot, rammed home, then fire"; a right-click with gunpowder, the rammer appears
   leaning on the carriage, "Use gunpowder on the cannon…"; a right-click with a cannonball, "Then use a cannonball on
   it…"; the gun outlined green with "The rammer seats the charge…"; a sneaking right-click, the barrel tilts up one
   step and back, "Sneak and use the upper half…"; an empty-hand right-click, flash, flame and smoke at the muzzle in
   the gun port, a cannonball flying out over the bow, the rammer gone (empty), "Use the cannon with an empty hand to
   fire"; a red arrow from the breech backwards with "The recoil pushes your ship back…". Check: the texts are readable
   and not cut off, nothing appears in the wrong place (the muzzle effects at the port, not behind the gun), the
   keyframes (the dots on the timeline) jump to the steps, and the pacing feels right (too slow, too fast?).
5. **Ponder's index.** Ponder's index (from the Ponder screen's index button) lists the cannon among the items with scenes.
6. **Both clients.** Steps 1, 3 and 4 on Fabric too.

## Also worth a look
- Does the barrel tilt in step 4 (the elevation goes through the block entity; if the barrel stays level, say so)?
- Is the cannonball item flying out convincing, or would particles alone be better?

# PND1: the ship mechanics' scenes

Eight more scenes, each on its item's Ponder key (hover the item in the creative inventory, hold W) and on the
Almanac's "Show me" buttons once ALM6's chapters carry them. JUnit builds every storyboard headless (its id, 20 to 40 s,
keyframes, every text line said), checks the schematics block by block and the lang; what only a client shows is the
scenes themselves. Setup as above, both clients (`./gradlew :neoforge:runClient`, `./gradlew :fabric:runClient`), a
creative world. For every scene check: it opens without a crash or a log error, the texts are readable and never cut
off at the window's edge, nothing appears in the wrong place, the keyframe dots jump to the steps, and the pacing.
Please send `latest.log` from both clients and a screenshot of anything odd.

1. **Assembling a Ship** (helm; the first of the helm's two scenes, about 40 s). Sand seabed, the sea, then the box
   hull with a dry hold. "Build your ship…" with the hull outlined, "Everything that touches the hull…", the helm
   placed near the stern with a white arrow to the bow, "Use the helm: every block connected to it, up to 2048…" with
   a green outline, "Air, water and terrain…", the ship rolling gently, a barrel placed on deck that rolls with it,
   the ship drifting a few degrees off the grid, a sneaking click on the helm, "To take her apart…", the ship turning
   back square. Check: the barrel appears on the deck (not floating), the ship's roll leaves no ugly gaps in the water.
2. **Steering at the Helm** (helm, second scene, about 37 s). A boat lying on the water. The wheel turns clockwise
   (three quarters of a turn) as "Move the mouse or hold A and D…" shows; at the stern a white midships line and a red
   rudder line swung to starboard (east, the boat's right seen from the helm) with "The rudder follows the wheel, up to
   35°…"; the wheel back to midships; a crew member appears behind the helm, two whistle clicks, "A crew member can
   hold a course…"; a green cross on the water to the north-west, the wheel turning a little to port; then hard over
   and the boat sailing part of a white circle to starboard. Check: the crew member renders (skin, standing pose),
   the wheel visibly turns, the rudder line points the right way, the boat turns to its right.
3. **Sails and the Sail Winch** (sail winch, about 39 s). Mast, upper yard, lower yard, the pair outlined, a drop line
   "…5 × 4 = 20 here", the winch; three winch clicks hoist the cloth to half and full (the cloth should hoist smoothly),
   a white wind arrow, then a red longer one with the sail going furled and back to half, a click on the upper yard
   making it full, the whistle. Check: **the cloth appears at all** (the scene sets the cloth through the yard rule;
   if the yards stay bare, say so), it bellies to the side the arrow points to (the cloth follows your world's real
   wind; with the wind along the yards it luffs: note the wind direction in F3 or the HUD).
4. **The Anchor and the Capstan** (capstan, about 39 s). A ship's waist in deeper water, the anchor hanging outside
   the hull on the right (east) side just below the deck. A click on the capstan: the anchor drops to the seabed with
   chain blocks appearing above it and a splash; "Once it bites…" with a wind arrow and a green chain line; the red
   "too deep" line; a click and the anchor comes up as the chain disappears. Then a crew member south of the capstan,
   two whistle clicks, and he walks round the drum while the bars turn (letting go: anticlockwise from above, the
   anchor then drops; heaving in: clockwise, the anchor comes up), then steps back. Check: the anchor model renders
   and hangs at the hull side, the bars turn with the walk, the sailor's walk looks right (facing along the circle).
5. **Pumps and Flooding** (bilge pump, about 34 s). An open hull section with a pump on the hold floor. The east wall
   block breaks with splash particles; the hold fills in two rows (water blocks; the game draws a smooth surface); a
   held click on the pump with its handle swinging; "Against an open breach one pump only holds the level"; the hull
   patch block set into the hole; the water goes down; the whistle. Check: the pump handle swings, the patch block
   looks like the game's, water in the hole and hold renders cleanly.
6. **Patching a Breach** (hull patch, about 23 s). A short scene: the breach with sea in it and flood water inside,
   the recipe line, a click with the patch on the hole's edge from inside, the patch block appearing, "…stops at once:
   there is no repair time…".
7. **Mooring** (mooring ring, about 32 s). A stone quay with a ring, a small ship coming in one block alongside, the
   quay outlined green with "Lying within 32 blocks of a port she counts as moored there…", the grappling hook flying
   from the deck onto the quay on an arc with a tan line, a click on the ship's ring and a tan line from it to the
   hook, "The tied line holds her…", the quay's ring, a sneaking click and the hook flying back aboard. Check: the hook
   item lands on the quay top, the ship's movement leaves no hole in the sea (it starts at the scene's edge).
8. **Cargo and Weight** (cargo crate, about 32 s). A nine-block ship, two crates placed aft and filling (their model
   should change with the load if it has fill stages), the ship tipping a few degrees stern down and settling lower,
   the crates moved amidships and the ship level again but still lower, a long white and a short red arrow at the
   bow. Check: the trim is visible but not absurd, the crates move with the ship.
9. **Ponder's index** lists the helm, sail winch, capstan, bilge pump, hull patch, mooring ring and cargo crate; the
   helm shows two scenes (assembly, then steering).
10. **Both clients.** Steps 1 to 8 on Fabric too (at least open each scene once).
