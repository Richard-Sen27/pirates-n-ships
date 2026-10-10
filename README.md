# Pirates 'n' Ships

A Minecraft mod about sailing, piracy and life at sea, for Minecraft 1.21.1 on NeoForge and Fabric.

Build a ship out of ordinary blocks, take the helm and she becomes a real vessel: she floats, heels in a gust, keeps
her hold dry below the waterline until a cannonball holes her, floods compartment by compartment and sinks if the
crew does not pump and patch. Sail her by the wind, hire a crew and whistle your orders, trade in harbours, serve in
the navy or turn pirate, duel with timed swordplay, fire muskets and cannons, grapple and board other ships, and
meet sharks, waterspouts, whirlpools and the kraken on the deep ocean. Every player starts with the Captain's
Almanac, the in-game guide.

The full feature list, as players see it on Modrinth and CurseForge, is in
[`docs/release/store-page.md`](docs/release/store-page.md). What exists, what is open and what is being tuned is
tracked in [`docs/progress.md`](docs/progress.md).

**Status:** the first public release is **0.1.0**. It ships before the in-game playtest rounds, so reports on the
[issue tracker](https://github.com/Richard-Sen27/pirates-n-ships/issues) are welcome. Wood variants of every wooden
block (all vanilla woods and known wood mods) are in progress for the next release; 1.0.0 follows the playtest rounds.

## Screenshots

To be taken in game (the human) for the README, the Modrinth and CurseForge galleries and the release post. Save
them as `docs/release/screenshots/<name>.png`, 1920×1080, GUI hidden (F1) unless the shot is about a screen.

1. `hero-sloop-under-sail.png`: a sloop under full sail on a beam reach in sunlight, waves and wake visible, seen from
   off the bow.
2. `dry-hull-cutaway.png`: below deck with the sea outside the stern window: a dry hold under the waterline.
3. `flooding-and-pump.png`: a holed hull with water standing in one compartment, a crew member at the bilge pump,
   the flood strip of the ship HUD showing.
4. `broadside.png`: two ships trading cannon fire, smoke and a cannonball in flight.
5. `boarding.png`: a grapple rope taut between two hulls and a boarding plank across, crew crossing.
6. `deck-duel.png`: a sword duel against a pirate on deck, the stamina bar visible (GUI on).
7. `crew-at-work.png`: the captain's whistle wheel open over a deck with hands at the winch and the capstan (GUI on).
8. `harbour-village.png`: a seafarer village's harbour from the water, pier, harbour master's desk and berths.
9. `pirate-island.png`: a pirate island camp at dusk with the Jolly Roger flying.
10. `navy-outpost.png`: a navy outpost with its garrison and a navy sloop off the quay.
11. `kraken.png`: the kraken's tentacles gripping a hull in a storm.
12. `almanac-open.png`: the Captain's Almanac open at a chapter with a recipe panel and a Show me button (GUI on).
13. `chart-and-map-board.png`: the chart screen with markers, and a map board on a cabin wall.
14. `market-screen.png`: the harbour master's market screen with goods and prices (GUI on).

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1, Java 21 |
| Loader | NeoForge 21.1.228 or newer, or Fabric (Loader 0.19.3 or newer, Fabric API, Forge Config API Port) |
| Required | [Sable](https://modrinth.com/mod/sable) 2.0.6 (ship physics), [GeckoLib](https://modrinth.com/mod/geckolib) 4.9.3 (animated mobs), [Player Animation Library](https://modrinth.com/mod/player-animation-library) 1.1.6 (sword and gun animations) |
| Bundled | Ponder and Flywheel (the Almanac's Show me scenes), inside the mod's jar |
| Optional | [JEI](https://modrinth.com/mod/jei) 19.39.0.372 or newer; on Fabric [Mod Menu](https://modrinth.com/mod/modmenu) for the config screen |

The exact versions and platform ids are in [`docs/releasing.md`](docs/releasing.md) and `gradle.properties`.

## Installing (players)

1. Install Minecraft 1.21.1 with NeoForge or with Fabric Loader and Fabric API.
2. Download Pirates 'n' Ships for your loader from Modrinth, CurseForge or the GitHub releases, together with Sable,
   GeckoLib and the Player Animation Library (and Forge Config API Port on Fabric). The Modrinth and CurseForge apps
   fetch the dependencies by themselves.
3. Put the jars into the `mods` folder of the game, and of the server: the mod is needed on both sides.
4. Start a world: you get the Captain's Almanac on first join. Its chapter "Your first ship" walks you through the
   first launch.

Settings: Mods → Pirates 'n' Ships → Config in game (on Fabric through Mod Menu), or the files
`serverconfig/pirates_n_ships-server.toml` in the world and `config/pirates_n_ships-client.toml`. Every gameplay
feature has a server switch, and every strength and rate is a value.

## Building (developers)

A multiloader project based on the [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template),
Java 21, Mojang mappings with Parchment, Gradle 9.5 on a Java 21 toolchain.

| Module | Contents |
|---|---|
| `common/` | All gameplay logic, renderers, datagen and tests; vanilla Minecraft only |
| `neoforge/` | Thin NeoForge layer (ModDevGradle): entrypoint and platform services |
| `fabric/` | Thin Fabric layer (Loom): entrypoint and platform services |

```sh
./gradlew build                                   # build all modules and run the JUnit tests
./gradlew :neoforge:runGameTestServer             # the GameTests, headless, on NeoForge
./gradlew :fabric:runGameTest                     # the same GameTests on Fabric
./gradlew :neoforge:runGameTestServer -Ppirates_n_ships.gametest.only=FooGameTests,BarGameTests   # scoped run
./gradlew :neoforge:runData                       # generate assets and data into common/src/generated/resources
./gradlew :neoforge:runClient                     # dev client (Fabric: ./gradlew :fabric:runClient)
```

Local builds are `<mod_version>-dev`; releases are built from a pushed tag `vX.Y.Z` (0.x releases are beta, the
first is `v0.1.0`; see [`docs/releasing.md`](docs/releasing.md)).

## Documentation

- [`docs/design.md`](docs/design.md): the design spec and every decision.
- [`docs/progress.md`](docs/progress.md): the work packages, their state, open items and the playtest order.
- [`docs/guide.md`](docs/guide.md): the testing and reference guide (config keys, commands); the player's guide is
  the Almanac, written in [`docs/almanac/`](docs/almanac/) (format: [`docs/almanac.md`](docs/almanac.md)).
- [`docs/playtests/`](docs/playtests/): in-game checklists per feature.
- [`docs/audit/`](docs/audit/): audits, such as the sound audit ([`sounds.md`](docs/audit/sounds.md)).
- [`docs/releasing.md`](docs/releasing.md): versions, the release workflow and the checklist; the release texts are
  in [`docs/release/`](docs/release/) (store page, release notes, save compatibility).
- [`docs/sable-notes.md`](docs/sable-notes.md): working with Sable, verified notes.
- [`docs/fabric.md`](docs/fabric.md): the Fabric module; [`docs/compat.md`](docs/compat.md): JEI and other mods.
- [`art/README.md`](art/README.md): Blockbench models, animations and rigs; [`tools/sounds/README.md`](tools/sounds/README.md):
  the sound pipeline; [`docs/credits.md`](docs/credits.md): music and sound credits.

## Contributing

- **Multiloader rules:** `common/` never imports `net.neoforged.*` or `net.fabricmc.*`; loader needs go through the
  platform services in `common/.../platform/`, and the loader modules stay thin. All JSON assets come from datagen.
- **Commits:** Angular style, subject only (`type(scope): subject`, e.g. `feat(ship): add the anchor winch`); the
  subject of every `feat` and `fix` lands in the release notes as written.
- Every gameplay feature gets a config switch and tests (JUnit for pure logic, GameTests for the world).
- Details: [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`CLAUDE.md`](CLAUDE.md).

## Licence

[PolyForm Noncommercial 1.0.0](LICENSE): free to use, modify and share for any noncommercial purpose. Music and
sound effects are used under their own licence, listed in [`docs/credits.md`](docs/credits.md).
