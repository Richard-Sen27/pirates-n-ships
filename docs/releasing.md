# Releasing

How versions are numbered, how a release is cut, and what the release workflow (`.github/workflows/release.yml`) does. Every release publishes two jars for Minecraft 1.21.1, one for NeoForge and one for Fabric (since REL2; the Fabric module: `docs/fabric.md`).

## Version scheme

- **Semantic versions** `MAJOR.MINOR.PATCH`, starting at `0.1.0`, the first public release (the human, 2026-10-10: "i will not release right now 1.0.0, but 0.1.0. as i cannot test it out right now"). Before `1.0.0` the mod is in beta by definition; `1.0.0` follows the in-game playtest rounds and the wood variants of every wooden block (WOOD1 and its follow-ups).
- **Pre-releases** add `-alpha.N` or `-beta.N`: `0.3.0-alpha.1`, `0.3.0-beta.2`. Nothing else (no `-rc.N`, no build metadata); the build and the workflow reject other forms.
- **Git tags** are the version with a `v`: `v0.1.0`, `v0.3.0-beta.1`.
- **Mod version and jars** carry the version without the `v`. The mod list shows it (`neoforge.mods.toml` has `version = "0.3.0-beta.1"`, `fabric.mod.json` has `"version": "0.3.0-beta.1"`), and the jars are `pirates_n_ships-neoforge-1.21.1-<version>.jar` and `pirates_n_ships-fabric-1.21.1-<version>.jar` (`<mod_id>-<loader>-<minecraft version>-<version>`, set in `buildSrc/src/main/groovy/multiloader-common.gradle`).
- **`gradle.properties`** holds `mod_version`, the *next* planned version without a suffix (e.g. `0.1.0`). Bump it after a final release.
- **Local builds** are `<mod_version>-dev` (e.g. `pirates_n_ships-neoforge-1.21.1-0.1.0-dev.jar`), so a dev jar never looks like a release. A release build passes the tag's version: `./gradlew build -Pversion=0.1.0-beta.1`. The root `build.gradle` sets this for all projects and fails on a malformed `-Pversion` (so a test build uses a well-formed version such as `0.0.0-beta.1`, not `0.0.0-test`).

Local check of both release jars (what the workflow's two jar checks test):

```sh
./gradlew build -Pversion=0.0.0-beta.1
ls neoforge/build/libs fabric/build/libs   # one jar each without -sources/-javadoc
unzip -p neoforge/build/libs/pirates_n_ships-neoforge-1.21.1-0.0.0-beta.1.jar META-INF/neoforge.mods.toml | grep -E '^\s*version\s*=\s*"0.0.0-beta.1"'
unzip -p fabric/build/libs/pirates_n_ships-fabric-1.21.1-0.0.0-beta.1.jar fabric.mod.json | python3 -c 'import json, sys; print(json.load(sys.stdin)["version"])'   # Loom minifies the JSON, so grep for a spaced "version": never matches
```

### Release channel

Both Modrinth and CurseForge get the same channel:

| Tag | Channel |
|---|---|
| `vX.Y.Z-alpha.N` | `alpha` |
| `vX.Y.Z-beta.N` | `beta` |
| `v0.Y.Z` (any 0.x version without suffix) | `beta` |
| `vX.Y.Z` with X ≥ 1 | `release` |

The GitHub release is marked as a pre-release for every channel except `release`. So `v0.1.0` publishes on the `beta` channel and as a GitHub pre-release.

### Names on the platforms

Each release is two versions per platform, one per loader, in the same Modrinth and CurseForge project:

- Version number: the mod version, e.g. `0.3.0-beta.1`, the same for both loaders (Modrinth tells them apart by the loader, as Sable's own versions do).
- Display names: `Pirates 'n' Ships 0.3.0-beta.1 for NeoForge 1.21.1` and `Pirates 'n' Ships 0.3.0-beta.1 for Fabric 1.21.1`.
- Loader `neoforge` or `fabric`, game version `1.21.1`, Java 21.
- Dependencies (the last column says which loader's version lists it):

| Mod | Type | Modrinth | CurseForge | Loaders |
|---|---|---|---|---|
| Sable (ryanhcode) | required | [`sable`](https://modrinth.com/mod/sable), id `T9PomCSv` | [`sable`](https://www.curseforge.com/minecraft/mc-mods/sable), id `1312371` | both |
| GeckoLib | required | [`geckolib`](https://modrinth.com/mod/geckolib), id `8BmcQJ2H` | [`geckolib`](https://www.curseforge.com/minecraft/mc-mods/geckolib), id `388172` | both |
| Player Animation Library (ZigyTheBird) | required (client on NeoForge, both sides on Fabric) | [`player-animation-library`](https://modrinth.com/mod/player-animation-library), id `ha1mEyJS` | [`player-animation-library`](https://www.curseforge.com/minecraft/mc-mods/player-animation-library), id `1283899` | both |
| Fabric API | required | [`fabric-api`](https://modrinth.com/mod/fabric-api), id `P7dR8mSH` | [`fabric-api`](https://www.curseforge.com/minecraft/mc-mods/fabric-api), id `306612` | fabric |
| Forge Config API Port (Fuzs) | required | [`forge-config-api-port`](https://modrinth.com/mod/forge-config-api-port), id `ohNO6lps` | [`forge-config-api-port-fabric`](https://www.curseforge.com/minecraft/mc-mods/forge-config-api-port-fabric), id `547434` | fabric |
| JEI (Just Enough Items, JEI1) | optional | [`jei`](https://modrinth.com/mod/jei), id `u6dRKJwZ` | [`jei`](https://www.curseforge.com/minecraft/mc-mods/jei), id `238222` | neoforge, fabric (`type="optional"` with `[19.39,)` in `neoforge.mods.toml`; `suggests`, and `breaks` below 19.39, in `fabric.mod.json`; ids checked 2026-10-09 through the Modrinth API and api.cfwidget.com; what the plugin shows: docs/compat.md) |

**Bundled, not listed: Ponder and Flywheel (ALM4).** Both are required (Ponder on both sides, Flywheel on the client: the Almanac's "Show me" scenes, design.md §3.4), but neither is published as a standalone mod for 1.21.1: on Modrinth the slug [`ponder`](https://modrinth.com/mod/ponder) (id `5A34Stj8`) is *Ponder for KubeJS* and [`flywheel`](https://modrinth.com/mod/flywheel) (id `5lpsZoRi`) is *Flywheel (Legacy)*, versions < 0.6.9 only ("Flywheel is now intended to be bundled within the mods that need it"); on CurseForge likewise [`ponder`](https://www.curseforge.com/minecraft/mc-mods/ponder) (id `622888`, Ponder for KubeJS) and [`flywheel`](https://www.curseforge.com/minecraft/mc-mods/flywheel) (id `486392`, legacy). Checked 2026-10-09 through the Modrinth API and api.cfwidget.com. So our jars carry them as jar-in-jar, as Create does: NeoForge `jarJar` (`META-INF/jarjar/ponder-neoforge-<v>+mc1.21.1.jar`, `flywheel-neoforge-1.21.1-<v>.jar`), Fabric `include` (`META-INF/jars/`); the loader picks the newest copy when another mod (Create, Aeronautics) bundles them too. Their metadata entries stay (`neoforge.mods.toml`: `ponder` required both sides, `flywheel` required on the client; `fabric.mod.json`: `ponder`), and the bundled copies satisfy them. Both are MIT-licensed. They are deliberately absent from `release.yml`'s `dependencies` lists: declaring the KubeJS addon or the legacy Flywheel would send players to the wrong mods.

The workflow passes the numeric ids (more robust than slugs). When a dependency is added or dropped in `neoforge.mods.toml` or `fabric.mod.json`, update that loader's `dependencies` list in `release.yml` and this table.

## Dedicated-server smoke test

"The usual release-day crash": the mod works in the dev client and the GameTests but a dedicated server crashes at
start or on first use, typically on a client class reached from server code (`NoClassDefFoundError:
net/minecraft/client/...`), a registration done only in a client entrypoint, a mixin that only fails outside dev, or a
dependency the server lacks. `tools/server_smoke.sh` (REL3) checks for it headlessly; no performance is measured
(that is PERF1).

```sh
tools/server_smoke.sh neoforge          # the dev server run, ./gradlew :neoforge:runServer
tools/server_smoke.sh fabric --jar      # the built jar on a real Fabric server (what the release workflow runs)
```

**Two modes.**

- Default (`--dev`): starts the loader's dev run (`:neoforge:runServer`, `:fabric:runServer`; both take
  `-Ppirates_n_ships.server.dir=<dir>`, which the script sets to `<loader>/build/server-smoke` so the normal run
  directory and its world are never touched). Fast to iterate, but on NeoForge it does **not** catch a client class
  on a server path: the dev Minecraft jar keeps the client classes loadable (FML strips only `@OnlyIn` classes, and
  Minecraft has none since 1.20.5). Fabric's dev server does refuse client classes (Loom marks them `@Environment`).
- `--jar`: what a player's server runs. `./gradlew :<loader>:serverSmokeKit` collects the release jar (with its
  jar-in-jar Ponder and Flywheel), the required mods as published (NeoForge: Sable, GeckoLib; Player Animation Library
  is client only there and deliberately left out. Fabric: Fabric API, Sable, Forge Config API Port, GeckoLib, Player
  Animation Library), the loader's installer and the Java 21 toolchain's path. The script installs the real server once
  per loader version into `<loader>/build/server-smoke-install/` (network: the installers download Minecraft and the
  libraries) and runs it with only those mods. This catches client classes on server paths on both loaders, wrong
  `side`/`environment` metadata, a jar-in-jar that does not load, and a mod missing from the dependency list.

**What it does**, on a fresh world (fixed seed `2718281828`, `SMOKE_SEED` overrides; offline mode, `max-tick-time=-1`,
view distance 4, free ports), talking to the server console through RCON (`tools/rcon.py`, standard library only;
Gradle does not forward stdin):

1. Waits for `Done (` (`SMOKE_TIMEOUT`, default 600 s).
2. Lists the ship templates, finds the nearest ocean (`/locate biome #minecraft:is_ocean`), force-loads it, and there
   `/pirates ship place starter_sloop assemble` (a Sable sub-level on a dedicated server), spawns pirates and a shark;
   at spawn a sailor, a navy soldier and a harbor master; `/pirates waves set rough`, `/pirates waves`,
   `/pirates wind get`, `/pirates world ports|port nearest|factions|raid chance|patrols`, `/pirates mob captain list`,
   `/pirates trade port …` (creates the markets; its reply then says it needs a player) and `/pirates trade contracts`,
   `/pirates hazard spawn waterspout`.
3. Ports through worldgen: locates the nearest seafarer village, pirate island and navy outpost and generates their
   chunks, so the structures place and the port registry records them (fails the test if fewer than two ports
   appear); then `/pirates world voyages lane` and `… spawn convoy` for each pair until one sails. When none does
   (on the default seed the three lie on three separate seas, and each lane reply says so), it locates the nearest
   port of each kind from the middle and the quarters of the largest closed sea a lane reply names, generates them and
   tries each pair with a new port: on the default seed a convoy then sails from pirate_island_-3012_4328 to
   village_-5449_10600 (a 12 217-block lane, LANE1). If none sails, the summary lists the sailing convoy as skipped.
   Then `/reload`.
4. Lets the server tick for 30 s (`SMOKE_TICK_SECONDS`), then `/pirates world voyages`, `/pirates hazard clear`,
   `/pirates waves clear`, `/save-all flush` and `/stop`, and waits up to 120 s for the process to exit.
5. Fails on: no `Done (`, a process that does not exit (it is then stopped by its own pid, nothing else is touched),
   a non-zero exit, no `Stopping server` or no save in the log, a file in `crash-reports/`, and any line of the log or
   of the command replies matching `Exception`, `NoClassDefFoundError`, `ClassNotFoundException`, `Mixin apply
   failed`, `crash`/`Crash` or `An unexpected error occurred` (the one exemption: Fabric's mod list entry
   `fabric-crash-report-info-v1`).

A command's own failure (a reply such as "Failed to place" or "No convoy …") does not fail the test; only crash signs
do. The output goes to `build/server-smoke/<loader>/` (`SMOKE_OUT` overrides `build/server-smoke`): `server.log`,
`commands.txt` (every command with its reply), `summary.txt` (pass or fail, startup time, the replies, the skipped
commands, every log warning or error that mentions the mod). On Fabric a multi-line reply comes back without line
breaks (vanilla's RCON source joins the lines; NeoForge adds them), which only affects how the summary reads.

**Not covered** (needs a player, and no bot can join headlessly: a vanilla protocol bot such as Mineflayer is refused
by NeoForge's registry and channel negotiation and could not handle our registries on Fabric either, and neither
loader's test framework connects a client to a dedicated server; vanilla's fake players are server-side only and none
of our commands creates one): joining, the HUD and every payload to a client, the Almanac and Ponder on a client
connected to a dedicated server, `/pirates trade open|goods|buy|sell|weight|plunder|coins`, `/pirates world voyages
spawn near`, crew hiring and orders, quests, careers and reputation of a player, law and brig on a player, `/pirates
ship forces|rigging|info`. Those are the [dedicated-server playtest](playtests/dedicated-server.md). The build-time
guard for client classes is `common`'s JUnit test `ServerSideClassesTest`: no class outside the client side (client
packages, client mixins, `compat/jei`, the Ponder scenes, `Client…` classes) may resolve a `net.minecraft.client` or
`com.mojang.blaze3d` class.

Typical times on a laptop: startup to `Done` about 20 s in either mode once built (the server itself reports 5 to
10 s); a whole `--jar` run about 2.5 minutes including the first install.

## Release notes

`tools/release_notes.py <previous-tag> <tag>` prints Markdown notes for the commits between two tags, grouped by their Angular type (`.claude/skills/commit/SKILL.md`):

- **Features**: `feat(...)`, except art and audio
- **Fixes**: `fix(...)`
- **Art and sounds**: `feat(art)`, `feat(audio)`
- **Other**: everything else, including subjects that are not in Angular format. `build`, `ci`, `chore`, `docs`, `refactor`, `style` and `test` are left out unless `--all` is passed or the commit is marked breaking (`type!:`).

The script knows nothing about loaders. The workflow puts one line above its output in `build/release/release-notes.md`: the release is for NeoForge and Fabric, and which mods each loader needs. Both loaders' versions get the same notes.

Commits are listed oldest first, merge commits are skipped, and the output depends only on the subjects. Pass `""` as the previous tag for the first release. `--self-test` checks the grouping on fixed input; the workflow runs it before using the script.

So write commit subjects for players: the subject of every `feat` and `fix` ends up in the changelog as written.

## Release texts

Hand-written texts for the platforms and the release, in `docs/release/` (REL4), checked by a script:

- [`release/store-page.md`](release/store-page.md): the Modrinth and CurseForge description (one text for both, with what CurseForge's renderer does not take).
- [`release/0.1.0-notes.md`](release/0.1.0-notes.md): the 0.1.0 release notes (hand-written, since there is no earlier tag to generate from). The workflow publishes `docs/release/<version>-notes.md` for the tag's version (`v0.1.0` → `0.1.0-notes.md`) when it exists.
- [`release/save-compatibility.md`](release/save-compatibility.md): what a pre-release world keeps and loses under 0.1.0 (SAV1), and the ids kept from 0.1.0 on; the notes link to it.
- [`../README.md`](../README.md): the repository's front page, with the screenshot list for the galleries.
- `tools/check_release_texts.py`: fails when the store page, the README or the notes of the version being released (`docs/release/<version>-notes.md`, the version from the workflow's `VERSION` or else `mod_version`; skipped with a message when there is none) name an item, block, mob or config key that the generated lang does not have, or a dependency version that differs from `gradle.properties` (`--self-test` checks its rules).

## Cutting a release

Before tagging, check:

1. `main` is green on CI (build, JUnit, GameTests). The release workflow also runs the dedicated-server smoke test; run it locally first when server-side code changed (below).
2. The playtests for everything new since the last release are done (`docs/playtests/`, and the playtest list at the end of `docs/progress.md`). Nothing the release notes advertise should be unplaytested.
3. `docs/progress.md` has no open blocker for the features in this release.
4. `docs/release/save-compatibility.md` is current: every stored format, block state, registry id or config key changed since the last release is in it (what converts, what is lost, what a player does by hand), and `KeptIdsTest` passes.
5. Preview the notes: `python3 tools/release_notes.py <previous-tag> HEAD`.
6. Check the release texts: `python3 tools/check_release_texts.py` passes, and `docs/release/store-page.md` and the version's notes still describe what is on `main` (paste the store page into both platforms when it changed).
7. The configuration table in `docs/guide.md` §13 matches the code: `python3 tools/check_config_docs.py` prints `ok` (it fails on a config section without a row, a row without a section, a nested group its row does not name, or a backticked name that is no longer a key; `--keys` lists the keys a row leaves out, `--self-test` checks the parser). Values whose defaults changed since the last release belong in the release notes.
8. The structure files carry no misplaced block entities: `python3 tools/check_structures.py` ends with `0 with problems` (it fails on block entity data on a state without one or with another block's id, and on an entity block without a block entity in a generated structure, which worldgen logs as "Tried to load a block entity … but failed"; keep its `MOD_RULES` in step with every new entity block).
9. Optionally a dry run: Actions → Release → Run workflow, `version` = the planned tag, `dry_run` checked. It builds, runs the tests on both loaders, shows the notes in the build job's summary and attaches both jars as a workflow artifact; the publish job shows as skipped.

Then tag and push:

```sh
git tag -a v0.1.0 -m "Pirates 'n' Ships 0.1.0"
git push origin v0.1.0
```

The tag push starts the workflow. After a final (non-pre-release) version, bump `mod_version` in `gradle.properties` to the next planned version.

A failed publish can be retried with Actions → Release → Run workflow, `version` = the existing tag, `dry_run` unchecked, and `publish` = the loader that failed (`neoforge` or `fabric`; the default `both` publishes both). It checks out the tag, not the branch. Retry only the failed loader: Modrinth accepts a second version with the same number, so republishing a loader that already went out makes a duplicate there.

A retry runs the whole workflow again (build, both suites, about half an hour) before it publishes. When nothing went out at all (say a secret was missing: the publish job fails on its first check), "Re-run failed jobs" on the failed run is quicker: it re-runs only the publish job with the artifact the build job already uploaded. Never use it after one loader did publish: the re-run publishes both again.

The two loaders publish in two separate steps of the publish job, NeoForge first. A failed NeoForge publish does not stop the Fabric one, and a failed Fabric publish does not touch the NeoForge version already published (nothing is rolled back); the publish job ends red and the failed loader is retried as above. Within one step mc-publish uploads to Modrinth, CurseForge and GitHub in turn, carries on past a platform that fails and fails the step at the end. A retry of that loader uploads to all three again, so first delete the version it did publish on Modrinth or CurseForge (GitHub just replaces the jar on the release), or upload the missing platform by hand instead.

## What the workflow does

`.github/workflows/release.yml`, on a `v*` tag push or on `workflow_dispatch` (inputs `version`, `dry_run` default true, `publish` default `both`). Three jobs (REL6): the cheap checks first, then the NeoForge and the Fabric suite side by side, then the publish. Before REL6 it was one job that ran every check in turn, and both v0.1.0 runs failed on a cheap check (a JUnit test, then the Fabric jar check) after the expensive steps had already run, the second after 47 minutes. Release covers everything the `Build` workflow does (build, JUnit, both loaders' GameTests) plus the jar checks, the release texts and the smoke tests; `Build` no longer runs on tags.

**`build`** (Build, JUnit and checks; about 6 minutes, the Gradle build being 5 of them):

1. Checks out with full history and tags.
2. Resolves the version from the tag (rejects anything but `vX.Y.Z[-alpha.N|-beta.N]`), the channel (after a self-check of the rule), and the previous `v*` tag for the notes. A non-dry run needs the tag to exist and checks it out; a dry run of a tag that does not exist uses the selected branch. Version, channel, the notes' ref, the commit sha and the previous tag become the job's outputs.
3. The release notes and texts, before any Gradle run (they need only Python and the committed generated lang, so they fail within the first minute): `tools/release_notes.py --self-test`, `tools/check_release_texts.py` with `VERSION` set, then the loader line, the hand-written `docs/release/<version>-notes.md` below its `PUBLISHED NOTES` marker (when it exists) and the generated commit list (when a previous tag exists) into `build/release/release-notes.md` and the job summary.
4. JDK 21 (Temurin), Gradle with caching, then `./gradlew build -Pversion=<version>` (includes JUnit).
5. Checks there is exactly one release jar `neoforge/build/libs/*-neoforge-*-<version>.jar` (sources and javadoc jars do not match) and that its `neoforge.mods.toml` has that version.
6. Checks there is exactly one release jar `fabric/build/libs/*-fabric-*-<version>.jar` and that its `fabric.mod.json` (parsed as JSON; Loom writes it minified) has `"version": "<version>"`.
7. Uploads both jars and `release-notes.md` as the workflow artifact `pirates_n_ships-<version>` (flat, no folders).

**`gametest`** (GameTests and server smoke; needs `build`; one job per loader, `neoforge` and `fabric`, running in parallel with `fail-fast: false`, so one loader's failure does not cancel the other's run; about 25 minutes each: setup and compile, the loader's GameTests about 12 minutes, the smoke test about 10):

1. Checks out the commit the build job resolved, JDK 21, Gradle with caching.
2. The loader's GameTests with `-Pversion=<version>`: `./gradlew :neoforge:runGameTestServer` or `./gradlew :fabric:runGameTest` (headless, as in CI).
3. The [dedicated-server smoke test](#dedicated-server-smoke-test) on that loader's release jar (`tools/server_smoke.sh <loader> --jar` with `SMOKE_GRADLE_ARGS=-Pversion=<version>`). The job builds that jar again from the same commit and version; the jars published are the build job's.
4. Uploads the smoke logs as `server-smoke-<loader>-<version>`, also when the run failed.

**`publish`** (needs `build` and both `gametest` jobs; skipped on a dry run; about 2 minutes; the only job with `contents: write`, the others read only):

1. Downloads the artifact `pirates_n_ships-<version>` into `dist/`, then checks the secrets exist and both jars and the notes are there.
2. [`Kira-NT/mc-publish@v3.3.1`](https://github.com/Kira-NT/mc-publish) (formerly `Kir-Antipov/mc-publish`; NeoForge support since v3.3.0) publishes the NeoForge jar to Modrinth and CurseForge and creates the GitHub release for the tag with the notes and the jar (skipped when `publish` is `fabric`).
3. A second mc-publish call publishes the Fabric jar to Modrinth and CurseForge (loader `fabric`, the Fabric dependencies) and adds it to the GitHub release of the tag (mc-publish finds the release by its tag; it creates it if the NeoForge step did not). It runs when the download and the secrets check passed, also after a failed NeoForge publish (skipped when `publish` is `neoforge`).

So a release takes about half an hour from tag to published versions (build about 6 minutes, the two suites side by side about 25, publish about 2), down from about 50 minutes in one job; a cheap failure shows within the first few minutes.

The manual performance test (PERF1, `PerformanceGameTests`) is not part of the pipeline: it registers only with
`-Ppirates_n_ships.perf=true`, which neither the release workflow nor CI passes, and its timings depend on the
machine. Run it by hand before a release that touches ship, crew or physics code (see [`guide.md`](guide.md),
"Testing note: the performance test", and [`audit/performance.md`](audit/performance.md)).

Releases run one at a time (`concurrency: release`). A second tag pushed meanwhile waits; GitHub keeps only one waiting run per group, so a third would cancel the waiting second one. Push release tags one by one. A tag push no longer triggers the normal `Build` workflow (it runs on branch pushes and pull requests only); the release workflow tests the tagged commit itself.

## One-time setup (the maintainer)

1. Create the project on [Modrinth](https://modrinth.com/) and on [CurseForge](https://www.curseforge.com/minecraft/mc-mods) (license PolyForm Noncommercial 1.0.0, NeoForge and Fabric, 1.21.1). One project per platform carries both loaders; if the project already exists with NeoForge only, add Fabric to its loaders (CurseForge: the project's settings; Modrinth fills the loaders from the uploaded versions). The first file on CurseForge may need the project to be approved first.
2. Tokens:
   - Modrinth: Settings → [Personal access tokens](https://modrinth.com/settings/pats), with the scopes "Create versions" and "Write versions" (and "Read projects").
   - CurseForge: [API tokens](https://legacy.curseforge.com/account/api-tokens) (the upload API token, not a CurseForge Core API key).
3. In the GitHub repository, Settings → Secrets and variables → Actions:
   - Secrets: `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`.
   - Variables (or secrets, both work): `MODRINTH_ID` (the project id from the Modrinth project page, e.g. `AANobbMI`, or its slug) and `CURSEFORGE_ID` (the numeric "Project ID" from the CurseForge project's About box).
4. Settings → Actions → General → Workflow permissions: allow read and write (the workflow also asks for `contents: write` itself).
