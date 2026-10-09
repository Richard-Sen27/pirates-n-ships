# Playtest: a client on a dedicated server (work package REL3)

`tools/server_smoke.sh <neoforge|fabric> --jar` starts the release jar on a real dedicated server and drives it through
the console (docs/releasing.md, "Dedicated-server smoke test"): ship placement and assembly, mobs, waves, ports from
generated structures, a convoy, a save and a clean stop, on both loaders, with no exception in the log. What it cannot
do is join: no headless bot gets past the loaders' handshake. This playtest is the part only a real client confirms.

Setup (once per loader you test):
1. `tools/server_smoke.sh neoforge --jar` (or `fabric --jar`) builds the kit and installs the server into
   `neoforge/build/server-smoke-install/<mc>-<loader>/` (or `fabric/...`). Leave it as it is after the run.
2. Start that server by hand for the playtest, from its directory:
   - NeoForge: `<java 21> @user_jvm_args.txt @libraries/net/neoforged/neoforge/<version>/unix_args.txt nogui`
   - Fabric: `<java 21> -jar fabric-server-launch.jar nogui`
   (the Java 21 path is in `<loader>/build/server-smoke-kit/java.txt`). Type `op <your name>` in its console once you
   have joined. `server.properties` has `online-mode=false` and a random `server-port`: set `server-port=25565` first.
3. Join with the dev client of the same loader (`./gradlew :neoforge:runClient` or `:fabric:runClient`): Multiplayer,
   Direct Connection, `localhost`.

Please send the server's `logs/latest.log` and the client's `latest.log` if anything differs.

## Steps
1. **Join.** The client connects without a "mod mismatch", "missing channel" or registry error, and the server log
   shows no exception while you join. Expected: you spawn normally.
2. **HUD and ship sync.** `/pirates ship place starter_sloop assemble` in front of you, board it, take the helm. The
   ship HUD (speed, heading, hull status) appears and updates; the wind display shows. Sail a little: the ship moves
   smoothly for you (no rubber-banding beyond what a local world shows).
3. **Crew and mobs.** `/pirates crew spawn`, `/pirates mob spawn pirate`: both render with their models and animations
   (GeckoLib on a dedicated server), the pirate attacks, melee animations play (Player Animation Library is client
   only on NeoForge: it must work although the server does not have it).
4. **Almanac sync.** Open the Captain's Almanac. Its pages and the entries unlocked by what you did show (the unlock
   state comes from the server). Expected: the same pages as in a single-player world.
5. **Ponder on a dedicated server.** In the Almanac, press "Show me" on a scene (e.g. ship assembly), and hold the
   Ponder key over one of our items. The scene opens and plays; the server log stays clean (Ponder and Flywheel are
   bundled in our jar: on the server they load but stay idle).
6. **Trade and payloads.** `/pirates trade port test seafarer_village temperate` then `/pirates trade open test`: the
   market screen opens with prices. Buy one item. Expected: coins and item change on both sides.
7. **Config.** Edit `config/pirates_n_ships-server.toml` on the server while you are connected (e.g. a wave strength),
   save: the server log shows the reload and nothing else (the config file watcher on a dedicated server).
8. **Leave and rejoin.** Disconnect, reconnect: the ship, the crew and your inventory are as you left them.
9. **Stop.** `/stop` on the server console: it saves and exits within a few seconds, with no exception.
