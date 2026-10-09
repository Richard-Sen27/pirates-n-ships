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
