# Playtest: milestone 0 (Sable loads, test block appears)

Headless checks already pass (`./gradlew build`, `./gradlew :neoforge:runGameTestServer`). This checklist covers what
only the real client shows. Please send screenshots of steps 2, 4 and 6 and the `latest.log` if anything fails.

## Steps

1. Start the dev client: `./gradlew :neoforge:runClient`.
   - **Expected:** the title screen appears without a crash. In the log (`neoforge/run/logs/latest.log`,
     or the Gradle console) you see `Initializing Pirates 'n' Ships on NeoForge (development)` and
     `[de.ry.sa.Sable/]: Sable loaded!`.
   - Known harmless log lines: `Failed to apply tag physics properties. Unknown block: create:flywheel` (Sable's
     built-in data mentions a Create block; we don't ship Create), and `ClassNotFoundException` for Iris classes
     (Sable's optional shader compat).
2. Click **Mods**.
   - **Expected:** `Pirates 'n' Ships 0.1.0`, `Sable 2.0.6`, `Sable Companion 1.6.0` and `Veil` are listed.
3. Select Pirates 'n' Ships in the mod list and click **Config**.
   - **Expected:** NeoForge's config screen opens. Server config (only editable inside a world) contains a
     **Core** section with a **Debug** toggle whose tooltip reads "Log extra debug information from Pirates 'n'
     Ships systems". No raw translation keys are shown.
4. Create a new Creative world (Superflat is fine, cheats on) and open the creative inventory.
   - **Expected:** a tab named **Pirates 'n' Ships** with the test block as its icon, containing **Test Block**.
5. Place a Test Block, look at it, then break it in Survival (`/gamemode survival`) with an axe.
   - **Expected:** a wooden-sounding cube with an orange checkered placeholder texture and a dark border (no
     purple/black missing texture). It drops itself. The item in hand shows the same cube.
6. Sable sanity check: run `/sable spawn sphere 3` (command defined in
   `refs/sable/common/src/main/java/dev/ryanhcode/sable/command/SableSpawnCommands.java`, needs op level 2).
   - **Expected:** a small sphere of blocks appears near you, falls and rolls as a physics object. No crash and
     no errors from `dev.ryanhcode.sable` in the log.
7. Quit to the title screen and close the game.
   - **Expected:** clean shutdown, no exception in the log.
