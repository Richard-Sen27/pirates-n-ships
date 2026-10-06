# Pirates 'n' Ships

A Minecraft mod about sailing, piracy and life at sea. Build real block ships that float, sail with the wind, take damage and sink. Crew them with NPCs, fight the Royal Navy or join it, and hunt treasure across pirate islands.

> Early development. Nothing is playable yet. See the roadmap in [`docs/design.md`](docs/design.md#20-roadmap).

## Features (planned)

- **Dry hulls**: ship interiors below the waterline stay dry.
- **Flooding and sinking**: breaches flood compartments, ships list and go down unless the crew bails and patches.
- **Wind-driven sailing**: a global, weather-dependent wind drives the sails.
- **Commandable crew**: NPCs man the sails, cannons, crow's nest and anchor.
- **A living sea world**: pirate islands, seafarer villages, the navy, bounties, sharks and a rare kraken.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge (Fabric planned) |
| Dependencies | [Sable](https://github.com/ryanhcode/sable) |

## Development

Multiloader project based on the [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template). Requires Java 21.

| Module | Contents |
|---|---|
| `common/` | All gameplay logic, vanilla Minecraft only |
| `neoforge/` | Thin NeoForge layer: entrypoint and platform services |
| `fabric/` | Fabric layer, disabled until the Fabric port |

```sh
./gradlew build                       # build all enabled modules
./gradlew :neoforge:runClient         # start a dev client
./gradlew :neoforge:runGameTestServer # run GameTests
./gradlew :neoforge:runData           # generate assets and data
```

- Design spec: [`docs/design.md`](docs/design.md)
- Contribution and commit conventions: [`CONTRIBUTING.md`](CONTRIBUTING.md)

## License

[PolyForm Noncommercial 1.0.0](LICENSE). You may use, modify and share this mod for any noncommercial purpose.
