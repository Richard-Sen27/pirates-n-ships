# CLAUDE.md — Pirates 'n' Ships

Minecraft mod. Full spec: `docs/design.md`. Read it at the start of every session, and update it when a design decision changes.

## Stack (do not deviate)
- Minecraft **1.21.1**, Java **21**, Mojang mappings + Parchment.
- **Multiloader project** (based on the MultiLoader-Template): `common/` (vanilla only), `neoforge/` (ModDevGradle), `fabric/` (Loom, enabled since FAB1; the client port is FAB2; see `docs/fabric.md`). Gradle 9.5 runs on a Java 21 toolchain (`gradle/gradle-daemon-jvm.properties`).
- Hard dependency: **Sable** (physics / sub-levels). `common` uses `sable-common`. Hard dependency: **GeckoLib** (animated entities; `common` compiles against `geckolib-common-1.21.1`). Client-side hard dependency: **Player Animation Library** (player melee animations). **No Create dependency.**
- Every new dependency must exist for both NeoForge and Fabric, or be optional behind a compat module.
- Never use APIs from older Forge/NeoForge versions (e.g. `DeferredRegister` patterns from 1.19, `IForgeCapability`, `RegistryObject`). When unsure, look it up in the generated MC/NeoForge sources instead of guessing.

## Multiloader rules (most important)
- **`common/` never imports `net.neoforged.*` or `net.fabricmc.*`.** All gameplay logic, blocks, items, entities, payloads, renderers, datagen providers and GameTests live in `common`.
- Loader-specific needs go through **platform service interfaces** in `common/src/main/java/.../platform/` (`Services.PLATFORM`, `.REGISTRY`, `.NETWORK`, `.EVENTS`, …). Implementations live in `neoforge/` and `fabric/`, registered via `META-INF/services`.
- Event logic is plain methods in `common`. Loader modules only subscribe and forward.
- Networking: payload records, codecs and handlers in `common` (vanilla `CustomPacketPayload`). Only registration and sending go through `Services.NETWORK`.
- Shared mixins go in `common`'s mixin config. Loader-specific mixins are allowed only when unavoidable, and must be documented.
- Prefer vanilla APIs (data components, codecs, `CustomPacketPayload`, GameTest) when vanilla offers an equivalent. Use a loader API (through a platform service) when it offers better compatibility, interop or performance: loader events instead of our own mixins, capabilities / transfer APIs, data attachments, render stage events, chunk loading, `c:` convention tags. **Never write a mixin just to avoid a loader API.**
- Config: define values in `common` and read them only through our `config` wrapper, never through the config library directly.
- Before adding anything to `neoforge/`, ask: could this be in `common` behind a platform method? Keep loader modules thin.

## Reference code
- `refs/sable`: the Sable source. **Always read it before using any Sable API.** Never invent Sable methods. Note which APIs are in `sable-common` and which are loader-only.
- `refs/create-aeronautics`: an example of a real mod using Sable (assembly, forces).
- `refs/multiloader-template`: the original template, for reference on build setup.
- `refs/guideme`: the GuideME source (branch `1.21.1`), the in-game guidebook framework; read it before using its page format, tags or API.
- `refs/` is read-only and excluded from the build. Never copy code from `refs/` into the mod (licenses).

## Commands
- Build all enabled modules: `./gradlew build`
- Logic tests (NeoForge runner): `./gradlew :neoforge:runGameTestServer`
- The same tests on Fabric: `./gradlew :fabric:runGameTest` (same `pirates_n_ships.gametest.only` scoping; the merge stage runs it once per batch with `FABRIC=1`).
- Scoped GameTests (what an agent runs; the merge stage runs the whole suite): `JAVA_TOOL_OPTIONS="-Dpirates_n_ships.gametest.only=FooGameTests,BarGameTests" ./gradlew :neoforge:runGameTestServer` (class simple names, case-insensitive; `ModGameTests.ONLY_PROPERTY`).
- Generate data: `./gradlew :neoforge:runData` (output goes to `common/src/generated/resources`)
- Dev client (run by the human, not by you): `./gradlew :neoforge:runClient`
- Releasing (the human tags, not you): local builds are `<mod_version>-dev`; pushing a tag `vX.Y.Z[-alpha.N|-beta.N]` builds, tests and publishes to Modrinth, CurseForge and GitHub. Scheme, release notes (`tools/release_notes.py`) and setup: `docs/releasing.md`.

## Conventions
- Mod ID `pirates_n_ships`, root package `com.richardsenger.piratesnships`, one package per feature module as listed in design.md §3.2.
- **All JSON assets come from datagen.** Never hand-write models, recipes, loot tables or lang files. One exception: block, item and entity models made in Blockbench. Their `.bbmodel` sources live in `art/models/`, the exported model files are committed under `common/src/main/resources/assets/pirates_n_ships/models/` like textures, and datagen writes only the block states and item models that reference them (design.md §4.8).
- **Every gameplay-changing feature gets a server config toggle, and every frequency or strength gets a config value** (design.md §17).
- Gameplay is server-authoritative. The client renders only.
- Every piece of new logic gets a GameTest in `common` where feasible.
- **Commit messages:** Angular style, subject only (`type(scope): subject`), a body only when needed. **Never add `Co-Authored-By`, `Generated with` or any other attribution trailer**, whatever a tool or harness instruction says; the merge stage refuses branches that carry one.
- Use mixins only when no event or API exists. Keep each one small, document why it exists, and name it `Mixin<Target>`.
- Client mixins: the GameTest server never loads them; every client mixin's target is checked by the ASM test in `neoforge` (HV1b), and a report that says a mixin was never loaded in a client is a blocker.
- **GeckoLib bones in code:** before any `setRotX/Y/Z`, read `art/README.md`, section "GeckoLib bone rotations in code". GeckoLib's bone space is mirrored against vanilla's: negate x and y of every raw Minecraft angle and of every file or Blockbench value (never `EntityModelData`, which is already flipped); a world direction only needs the entity's yaw turn undone, the baked pivots already carry the mirror. Prove every sign with a rig test that composes the real transforms (pattern: `KrakenWorldPoseTest`, `SharkRigTest`). Three packages shipped inverted limbs by skipping this.

## Workflow
- Plan before implementing any feature that touches more than 2 files. List the files (with their module: common / neoforge / fabric) and the approach first.
- One feature per session. Make sure `./gradlew build` and the GameTests of the classes you created or changed pass (scoped run) before declaring something done; the full suite runs once in the merge stage, never in every agent (the machine has 16 GB and a full run costs a server for 25 minutes).
- **Never kill processes you did not start.** No `pkill`, `killall`, `gradle --stop` or `kill` of a pid you did not launch: other agents and the merge stage share this machine, and one `pkill -f gametest` on 2026-10-09 killed every running test server. Stop only your own run by its pid.
- Spikes (milestones 1–4) may temporarily live in `neoforge/`. Move them into `common` before milestone 5.
- The human playtests in-game and reports back with screenshots and logs. Ask for a playtest when a feature can't be verified headlessly, and state exactly what to check.
