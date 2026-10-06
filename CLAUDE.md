# CLAUDE.md — Pirates 'n' Ships

Minecraft mod. Full spec: `docs/design.md`. Read it at the start of every session, and update it when a design decision changes.

## Stack (do not deviate)
- Minecraft **1.21.1**, Java **21**, Mojang mappings + Parchment.
- **Multiloader project** (based on the MultiLoader-Template): `common/` (vanilla only), `neoforge/` (ModDevGradle), `fabric/` (Loom, disabled until the Fabric port milestone in design.md).
- Hard dependency: **Sable** (physics / sub-levels). `common` uses `sable-common`. Optional: GeckoLib (entities). **No Create dependency.**
- Every new dependency must exist for both NeoForge and Fabric, or be optional behind a compat module.
- Never use APIs from older Forge/NeoForge versions (e.g. `DeferredRegister` patterns from 1.19, `IForgeCapability`, `RegistryObject`). When unsure, look it up in the generated MC/NeoForge sources instead of guessing.

## Multiloader rules (most important)
- **`common/` never imports `net.neoforged.*` or `net.fabricmc.*`.** All gameplay logic, blocks, items, entities, payloads, renderers, datagen providers and GameTests live in `common`.
- Loader-specific needs go through **platform service interfaces** in `common/src/main/java/.../platform/` (`Services.PLATFORM`, `.REGISTRY`, `.NETWORK`, `.EVENTS`, …). Implementations live in `neoforge/` and `fabric/`, registered via `META-INF/services`.
- Event logic is plain methods in `common`. Loader modules only subscribe and forward.
- Networking: payload records, codecs and handlers in `common` (vanilla `CustomPacketPayload`). Only registration and sending go through `Services.NETWORK`.
- Shared mixins go in `common`'s mixin config. Loader-specific mixins are allowed only when unavoidable, and must be documented.
- Prefer vanilla APIs (data components, codecs, GameTest) over loader APIs.
- Config: define values in `common` and read them only through our `config` wrapper, never through the config library directly.
- Before adding anything to `neoforge/`, ask: could this be in `common` behind a platform method? Keep loader modules thin.

## Reference code
- `refs/sable`: the Sable source. **Always read it before using any Sable API.** Never invent Sable methods. Note which APIs are in `sable-common` and which are loader-only.
- `refs/create-aeronautics`: an example of a real mod using Sable (assembly, forces).
- `refs/multiloader-template`: the original template, for reference on build setup.
- `refs/` is read-only and excluded from the build. Never copy code from `refs/` into the mod (licenses).

## Commands
- Build all enabled modules: `./gradlew build`
- Logic tests (NeoForge runner): `./gradlew :neoforge:runGameTestServer`
- Generate data: `./gradlew :neoforge:runData` (output goes to `common/src/generated/resources`)
- Dev client (run by the human, not by you): `./gradlew :neoforge:runClient`

## Conventions
- Mod ID `pirates_n_ships`, root package `com.richardsenger.piratesnships`, one package per feature module as listed in design.md §3.2.
- **All JSON assets come from datagen.** Never hand-write models, recipes, loot tables or lang files.
- **Every gameplay-changing feature gets a server config toggle, and every frequency or strength gets a config value** (design.md §17).
- Gameplay is server-authoritative. The client renders only.
- Every piece of new logic gets a GameTest in `common` where feasible.
- Use mixins only when no event or API exists. Keep each one small, document why it exists, and name it `Mixin<Target>`.

## Workflow
- Plan before implementing any feature that touches more than 2 files. List the files (with their module: common / neoforge / fabric) and the approach first.
- One feature per session. Make sure `./gradlew build` and GameTests pass before declaring something done.
- Spikes (milestones 1–4) may temporarily live in `neoforge/`. Move them into `common` before milestone 5.
- The human playtests in-game and reports back with screenshots and logs. Ask for a playtest when a feature can't be verified headlessly, and state exactly what to check.
