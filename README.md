# Pirates 'n' Ships

A Minecraft mod about sailing, piracy and life at sea. Build a ship out of ordinary blocks, take the helm and it
becomes a real vessel: it floats, keels over in a gust, keeps its hold dry until a cannonball holes it, floods
compartment by compartment and sinks if the crew does not pump and patch. Sail it by the wind, crew it with NPCs,
trade in harbours, fall foul of the navy, duel pirates with timed swordplay, fire muskets and cannons, grapple other
ships, and meet sharks, waterspouts, whirlpools and the kraken on the deep ocean.

> **Beta.** The core loop works and is being playtested; structures, quests and the Fabric port are still to come.
> Versions below 1.0.0 are published as beta on Modrinth and CurseForge. What exists, what is missing and what is
> being tuned is tracked in [`docs/progress.md`](docs/progress.md).

## What is in the game today

- **Ships that are blocks.** Assemble at the helm, up to a configurable block count; dry hulls below the waterline;
  breaches, flooding between compartments, listing and sinking; the bilge pump and hull patches to fight a leak.
  Pieces that get shot apart split into their own bodies.
- **Wind and sails.** A global, weather-driven wind; square sails on yards and triangular sails on stays, hoisted,
  reefed and furled; helm, rudder, keel and anchor; flags that stream downwind.
- **Crew.** Hire crew members and give orders with the captain's whistle: sails, pump, cannons, grapple release.
  A lookout in the crow's nest calls out ships, land and sharks with their bearing in points.
- **Provisions and survival.** Pantry, water barrel, rations, scurvy and rum; cold water that freezes swimmers;
  the sea chest you carry on your back or float on the water.
- **Trade and law.** Goods, markets and contracts at harbour desks; a criminal score, navy and player bounties with
  proofs, shackles, prisoners and a lockable brig; bounty turn-ins at navy officers and a notice board.
- **Combat.** Timed swordplay with slash, thrust, guard, parry, riposte and feints, animated in first and third
  person; flintlock pistol and musket with loading and aiming; a two-block cannon and a railing-mounted swivel gun
  that hole hulls; the grappling hook that hauls ships alongside.
- **Mobs.** Pirates that duel, sailors that flee, navy soldiers with muskets and officers with sabers; sharks that
  hunt swimmers; the kraken that grips hulls, breaks masts and sweeps decks.
- **Sea hazards.** Waterspouts in thunderstorms, drifting whirlpools in the deep ocean.
- **Looks and sounds.** Every block and item is a hand-made 3D model; shanties and sea music, hull creaks, sword
  and gun sounds from licensed recordings (see [`docs/credits.md`](docs/credits.md)).
- **In-game guide.** With GuideME installed, a guide book with every topic, item links and recipes (also the written
  [player guide](docs/guide.md)).

Every gameplay feature has a server config toggle, and every frequency and strength is a config value.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge (Fabric planned, see the roadmap) |
| Required | [Sable](https://modrinth.com/mod/sable) (ship physics), [GeckoLib](https://modrinth.com/mod/geckolib) (animated mobs), [Player Animation Library](https://modrinth.com/mod/player-animation-library) (client, sword and gun animations) |
| Optional | [GuideME](https://modrinth.com/mod/guideme) (the in-game guide book) |

Releases are published to Modrinth, CurseForge and GitHub by tag (see [`docs/releasing.md`](docs/releasing.md)).

## Playing

The [player guide](docs/guide.md) covers everything from the first ship to the kraken: recipes, controls, commands
and the config. Playtest checklists for each feature live in [`docs/playtests/`](docs/playtests/).

## Development

A multiloader project based on the [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template),
Java 21, Mojang mappings with Parchment.

| Module | Contents |
|---|---|
| `common/` | All gameplay logic, renderers, datagen and tests; vanilla Minecraft only |
| `neoforge/` | Thin NeoForge layer: entrypoint and platform services |
| `fabric/` | Fabric layer, disabled until the Fabric port |

```sh
./gradlew build                       # build all enabled modules and run the JUnit tests
./gradlew :neoforge:runGameTestServer # run the GameTests headlessly
./gradlew :neoforge:runData           # generate assets and data into common/src/generated
./gradlew :neoforge:runClient         # start a dev client
```

- Design spec and decisions: [`docs/design.md`](docs/design.md)
- Progress, open questions, defaults to review: [`docs/progress.md`](docs/progress.md)
- Working with Sable (verified notes): [`docs/sable-notes.md`](docs/sable-notes.md)
- Art pipeline (Blockbench models, player animations, rigs): [`art/README.md`](art/README.md)
- Sound pipeline: [`tools/sounds/README.md`](tools/sounds/README.md)
- Versioning and releases: [`docs/releasing.md`](docs/releasing.md)
- Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md)

## License

[PolyForm Noncommercial 1.0.0](LICENSE). Music and sound effects are used under their own licenses, listed in
[`docs/credits.md`](docs/credits.md).
