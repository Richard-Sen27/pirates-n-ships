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

# PND2: crew, combat and life aboard

Eight more scenes, each on its item's Ponder key. JUnit builds every storyboard headless (id, 20 to 40 s, keyframes,
every line said), checks the schematics block by block, the lang, and the private fields the scenes write through
(`SceneSync`). New in these scenes and only visible in a client: crew members and mobs showing the poses the server
would sync (work, sleep, eating, gun poses, a pirate's melee telegraph, a soldier aiming), and flags and bells driven
through their block entities' synced state. Setup and the general checks as for PND1 (both clients, `latest.log`, a
screenshot of anything odd; a log line "Ponder scene: cannot use …" means a pose or a flag could not be set).

1. **Crew and Orders** (captain's whistle, about 38 s). A quay with the harbor desk and a ship alongside. The harbor
   master appears behind the desk (facing the ship), a click on the desk, "Hands are hired…"; a sailor appears on deck
   with a puff. A whistle click in the air, then a ring of ten item icons above the ship (yard, white wool, lead, bilge
   pump, cannon, gunpowder, fire charge, capstan, chain, sail winch; the yard at the top, the rest clockwise) with "Use
   the captain's whistle in the air…". Two whistle clicks and the sailor **walks** to the winch, turns to it and **hauls**
   (work pose) on "Hoist sails". A second sailor appears, "Load!", he walks to the cannon, kneels into the **loading
   pose**, the gun's model shows the rammer (loaded); "Fire at will" with two red lines 15° either side and an arrow
   east, the sailor in the **aiming pose**; "Release crew", both step back; a sneaking click on the helm. Check: the
   wheel's icons are readable and not cluttered, the poses play, the walking looks like walking.
2. **Boarding** (boarding plank, about 37 s). Open sea, our ship and a pirate ship flying the Jolly Roger (the cloth
   should show). The hook flies onto her gunwale, a tan line, then she slides three blocks toward us with the hook
   riding along; the plank appears from the top of our gunwale onto hers in two pieces (the base and the hooked tip).
   Two pirates appear with cutlasses; a white path across the plank; then the fight: each pirate **raises his arm and
   swings** (the melee telegraph), a left click with a cutlass, he **flashes red**; the first takes two exchanges and
   goes down in a puff, the second one. Her hull outlined green ("A ship of the seas is yours…"), the law line, then she
   drifts two blocks off and the plank breaks with particles and a dropped plank. Check: the plank's model looks laid
   (not floating), the pirates' swings are visible, nothing of her ship lags behind when she moves.
3. **Flags and Allegiance** (Jolly Roger, about 39 s). Open sea, our ship with a two-block pole standing in a flag
   pedestal (the pedestal shows the pole stub and the cleat, the pole above it no cleat), nothing flying; the pole and
   then the pedestal outlined. Every click goes to the pedestal. A click with the Jolly Roger: **the flag runs up the pole over 3 s** and flies at the top. The navy patrol appears with the
   navy flag flying and a soldier who **aims his musket** at us (a red line); a navy flag hoisted on our pole (the
   Jolly Roger comes down first or is replaced: say which), the soldier lowers his musket; the red outline of being
   caught; an empty-hand click and **the navy flag runs down** (struck); a blue banner with a white skull and border
   runs up as a custom flag. Check: every hoist and strike animates (also in single player, where Ponder pauses the
   game), the banner shows its patterns, the cloths point the same way.
4. **The Crow's Nest** (crow's nest, about 35 s; at the end a flagpole appears in the nest, the nest's model gains its
   socket, and a merchant flag flies above it). A mast with the nest on top, ratlines from the deck (three sloped,
   four hung up to the nest's side) with a white climbing path. A sailor on deck, two whistle clicks, he vanishes in a
   puff and **stands in the nest** up to his chest (his feet on the nest's floor, not sunk in or floating), facing the
   bow (north). A green circle round the nest, then a white line ahead and an orange arrow two points to starboard with
   a merchant flag icon, "Sail ho!…". Check: the lookout's height in the barrel, the arrow to the right of the bow line.
5. **The Ship's Bell** (ship's bell, about 24 s). A bell on a bulkhead and one on a post. A click on the wall bell from
   the front: it **swings** away from the clicker and settles; a click on the post bell from the west: it **rocks at
   half** the swing. Then both ring twice more (the alarm) and once with the alarm text. No sound (expected). Check: the
   swing direction, that the swing plays while Ponder is open in single player.
6. **The Galley and Provisions** (pantry, about 32 s). The pantry and an empty water barrel against a bulkhead. Bread
   into the pantry; two water-bucket clicks and the barrel's water **rises to level 1, then 2**; a loaf pops out on
   "Every dawn…"; two sailors walk in, stand at the pantry's north and east sides facing it in the **work pose** (the
   eating stand-in) on "At noon and at sunset…", then walk off; red outline on short rations; rum, lime and a rotten
   flesh icon. Check: the barrel's fill levels show, the diners face the pantry.
7. **Hammocks and Rest** (hammock, about 26 s). Fence posts, then two hammocks between them. A clock icon, two sailors
   walk up and **lie in the hammocks** along them (head over the eastern half, feet toward the western post, in the sleep pose, on the canvas, not
   under it or floating); a second clock and they stand up beside them; a click on a hammock; the sea cot appears and is
   clicked. Check: the lying height and direction (send a screenshot if they float, sink or lie across).
8. **The Swivel Gun** (swivel gun, about 29 s). A fence rail, the swivel on its middle post pointing over the bow.
   Gunpowder, then a cannonball (the model should show the load if it has load stages); a held click and the gun
   **sweeps** right and up, left and down, and back a little right of the bow, raised; flash, smoke and a cannonball
   leaving the muzzle along the barrel; chain shot and grapeshot clicks with a red outline; a sailor beside the gun, a
   whistle click and the gun fires again. Check: the barrel turns smoothly, the flash sits at the muzzle.
9. **Steering** (PND1's scene, second on the helm): the "course" line now says the whistle and the ship screen give no
   course yet and only a server operator can, with no command name.
10. **Ponder's index** lists the captain's whistle, boarding plank, Jolly Roger, crow's nest, ship's bell, pantry,
    hammock and swivel gun too.
11. **Both clients.** Steps 1 to 8 on Fabric too (at least open each scene once).

# PND3: the scenes after the first look

The human's first look found walls between the watcher and the action, text boxes partly off the screen, and a sail
whose yards stood in the mast's own column. A new JUnit check (`SceneViewTest`, through `SceneView`) now replays every
scene headless at Ponder's **default camera** (Ponder's own `SceneTransform`: from the **north-west**, 35° above the
horizon) in a **1920×1080 window at GUI scale 3** (640×360 GUI pixels): every text target, click and changed block
must be visible (no solid block between it and the camera) and on the screen, and every text box and click box must lie
inside the window. All 17 scenes pass it; what it cannot see is the cloth, entities and models (only vanilla full
blocks count as walls), so please re-watch these at the default view (do not drag the camera) and at GUI scale 3. If
your GUI scale is "Auto" (4 on a 1080p screen), say so: the window is then 480×270 and the scenes look bigger.

Setup as above, both clients, a creative world. Screenshots of anything hidden or cut off.

1. **The opened walls.** For each, check that what the text talks about is seen while it happens:
   - **Pumps and Flooding** (bilge pump): after the hull fades in, its north and west walls fade away (a cut-away
     section); the pump on the hold floor, the breach in the far (east) wall, both flood rows, the pump handle and the
     patch going in are all in view. The water at the cut shows as a flat face (expected: a cross-section).
   - **Patching a Breach** (hull patch): the same cut-away of the north and west walls; the hole in the east wall, the
     click on its edge and the patch appearing are seen from inside.
   - **The Ship's Bell** (ship's bell): the bulkhead now stands on the far (south) side, the wall bell hangs on its
     north face toward you, the post bell in front at the left. Wall bell clicked from the front (north): it swings
     away from you, toward the wall; post bell clicked from the west: it rocks east.
   - **The Galley and Provisions** (pantry): the bulkhead now stands on the far (east) side; pantry and water barrel in
     front of it with their doors and water toward you; the clicks land on the pantry's west face; the two diners walk
     in from the west and sit at its free sides facing it.
   - **The Anchor and the Capstan** (capstan): the capstan stands one block to port of the centre line, so the anchor
     now hangs on the **port (west)** side, the side you look at: the drop, the chain links, the anchor on the seabed
     and its way back up are in view (before, the hull hid all of it). Check the anchor model hangs outside the hull's
     west side just below the deck, and the sailor walks round the capstan at its new place.
   - **Steering** (helm): the white midships line and the red rudder line are now drawn at deck height just aft of the
     stern (a top-down diagram), no longer at the waterline behind the hull; the circle text points at the water east
     of the boat. The whistle click on the helmsman is a box beside him (not above his head, which was off the screen).
   - **The Crow's Nest** (crow's nest): the ratlines now run up the mast's **port (west)** side toward you (three sloped
     links from the deck, then nets on the mast and the nest's side), the white climbing path along them. The view is
     smaller and lower, so the nest, the lookout, the scan circle and the call arrow are on the screen.
   - **Cargo and Weight** (cargo crate): the stern text points at the stern deck (not under it); the scene sits a
     little lower.
   - **Flags and Allegiance**: the "struck" text points at our deck.
2. **The boxes.** At GUI scale 3, no text box and no click box may be cut by the window's edge, in particular:
   **Crew and Orders** (the order wheel of ten icons was above the screen: the view is now smaller and lower and the
   wheel sits lower; check the icons are readable and not overlapping), **The Crow's Nest** (four texts were above the
   screen), **Cargo and Weight** (the crates' text and the placing click), **The Anchor and the Capstan** and
   **Steering** (the whistle clicks on the sailor, now boxes beside him).
3. **The yard column (Sails and the Sail Winch, sail winch).** The yards stand **one block forward (north) of the mast**,
   in the column in front of it, not in the mast's own column; the cloth hangs in front of the mast and the mast shows
   behind it. The winch now stands aft and to port of the mast at [1, 1, 4], where it is seen past the sail. The
   "yard" text reads "…It hangs one block forward of the mast, its middle block in the column in front of it". Check
   the cloth still appears, hoists to half and full, and bellies; the wind arrow stands east of the sail.
4. **Nothing else changed.** Cannon loading, assembly, mooring, boarding, hammocks and the swivel gun look as before
   (the check passed them as they were); open each once to be sure nothing hides the action there either.

# PND4: ratline nets, railings, her name, crew on deck

Four new scenes (22 in all) and one new line in **Cargo and Weight**. All pass the headless view check (`SceneViewTest`
22/22) at the default camera from the north-west and GUI scale 3; what it cannot check is our own models, the nets
drawn by their renderer and the entities, so watch each at the default view (do not drag the camera), GUI scale 3,
on both clients (`./gradlew :neoforge:runClient`, `./gradlew :fabric:runClient`), creative world. Screenshots of
anything hidden, cut off, missing or wrong.

1. **Ratline Nets** (ratline net; Almanac: Sailing, "Ratline nets", "Show me: stringing ratline nets"; about 36 s).
   A deck with a railing to port, a mast, a hoop six up and a crow's nest on top. Check: the chainplate on the rail and
   the hoop on the mast's west face appear; after the two net clicks **the net is drawn** from the chainplate's ring up
   to the hoop (two side ropes and rungs; this is the game's own `RatlineNetRenderer` fed by the block entities, never
   seen in a Ponder world before: if no net shows, that is the bug to report); a sailor climbs it facing the mast, rises
   past the hoop and steps onto the nest's floor (not into the mast, not through the rim); two more chainplates appear
   at either side and **the three strands draw as one fan** with rungs narrowing to the hoop; the green triangle
   outline lies over the fan; a click breaks the south chainplate: it vanishes, a net item pops out, **two strands
   stay**, still joined.
2. **Railings and Bulwarks** (oak railing, first scene; Almanac: Ships, "Railings", "Show me: railings and bulwarks";
   about 37 s). Check: the run of four railings laid stern-ward, **the end post moving with the run's end** (no post
   where a block meets the next, none next to the port); the second click on the last one adds the south edge with a
   corner post and an end post; the railing port appears and the posts beside it go; the cannon behind it fires
   **through the port to the west** (flash and smoke outside the hull, the ball flying west); the gate opens (the leaf
   swings) and a one-cell plank lies from its sill onto the pier, level; a lantern stands on the post at the bow end of
   the railing; the swivel gun sits on the rail **shifted to its outer (west) edge**; the bow bulwark appears with its
   shut gun port, then a cannon behind it and **the lid opens**.
3. **Her Name and Figurehead** (name board; Almanac: Ships, "Fittings", "Show me: her name and figurehead"; about 29 s).
   Check: a named name tag ("Black Gull") on the helm; one board on the port side shows **Black Gull** small; two more
   boards join on its right (as seen from outside: toward the stern) and the name **grows across all three**, drawn
   once, centred, no frame lines between the boards; the mermaid hangs on the bow facing north; after the second name
   tag the boards read **Capt. Black Gull**. If the text never shows or shows on every board separately, the scene's
   block entity data did not reach the renderer: report it.
4. **On Deck** (oak railing, second scene; Almanac: Crew, "Stations", "Show me: hands at the rail"; about 37 s). Check:
   two hands **lean on the port rail facing the sea** (this is the first time the `lean` pose is seen in game: the
   arms should lie across the top rail with the hands just outboard, the body bent over it, no arm through the rail;
   send a screenshot), the aft one's head stays still about 3 s, then turns slowly to one side and back, the next
   cycle to the other side; a whistle click and the bow hand straightens up and walks to the deck; two chainplates on
   the rail's top and **a boarding net hangs along the rail** between them (an upright mesh with a sagging top rope);
   a pirate climbs from the longboat, **stops at the net** (red outline) while the text shows (in the game the hold is one second), then climbs over onto
   the deck; the whistle's Board! entry (cutlass icon) and a sailor walks from the deck over the rail through the net
   down into the longboat, unhindered; the same entry again (now Back aboard, BRD3) and he climbs back over onto the
   deck; three cutlass clicks and the net is gone, a net item drops.
5. **Cargo and Weight** (cargo crate): after the two empty crates appear aft, a new green line "Placed empty, the
   crates leave her level: her ballast is worked out again for new blocks" points at the starboard crate; the ship stays
   level until the crates fill. The rest is unchanged.
