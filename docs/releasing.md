# Releasing

How versions are numbered, how a release is cut, and what the release workflow (`.github/workflows/release.yml`) does. Every release publishes two jars for Minecraft 1.21.1, one for NeoForge and one for Fabric (since REL2; the Fabric module: `docs/fabric.md`).

## Version scheme

- **Semantic versions** `MAJOR.MINOR.PATCH`, starting at `0.1.0`. Before `1.0.0` the mod is in beta by definition.
- **Pre-releases** add `-alpha.N` or `-beta.N`: `0.3.0-alpha.1`, `0.3.0-beta.2`. Nothing else (no `-rc.N`, no build metadata); the build and the workflow reject other forms.
- **Git tags** are the version with a `v`: `v0.1.0`, `v0.3.0-beta.1`.
- **Mod version and jars** carry the version without the `v`. The mod list shows it (`neoforge.mods.toml` has `version = "0.3.0-beta.1"`, `fabric.mod.json` has `"version": "0.3.0-beta.1"`), and the jars are `pirates_n_ships-neoforge-1.21.1-<version>.jar` and `pirates_n_ships-fabric-1.21.1-<version>.jar` (`<mod_id>-<loader>-<minecraft version>-<version>`, set in `buildSrc/src/main/groovy/multiloader-common.gradle`).
- **`gradle.properties`** holds `mod_version`, the *next* planned version without a suffix (e.g. `0.1.0`). Bump it after a final release.
- **Local builds** are `<mod_version>-dev` (e.g. `pirates_n_ships-neoforge-1.21.1-0.1.0-dev.jar`), so a dev jar never looks like a release. A release build passes the tag's version: `./gradlew build -Pversion=0.1.0-beta.1`. The root `build.gradle` sets this for all projects and fails on a malformed `-Pversion` (so a test build uses a well-formed version such as `0.0.0-beta.1`, not `0.0.0-test`).

Local check of both release jars (what the workflow's two jar checks test):

```sh
./gradlew build -Pversion=0.0.0-beta.1
ls neoforge/build/libs fabric/build/libs   # one jar each without -sources/-javadoc
unzip -p neoforge/build/libs/pirates_n_ships-neoforge-1.21.1-0.0.0-beta.1.jar META-INF/neoforge.mods.toml | grep 'version = "0.0.0-beta.1"'
unzip -p fabric/build/libs/pirates_n_ships-fabric-1.21.1-0.0.0-beta.1.jar fabric.mod.json | grep '"version": "0.0.0-beta.1"'
```

### Release channel

Both Modrinth and CurseForge get the same channel:

| Tag | Channel |
|---|---|
| `vX.Y.Z-alpha.N` | `alpha` |
| `vX.Y.Z-beta.N` | `beta` |
| `v0.Y.Z` (any 0.x version without suffix) | `beta` |
| `vX.Y.Z` with X ≥ 1 | `release` |

The GitHub release is marked as a pre-release for every channel except `release`.

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

## Release notes

`tools/release_notes.py <previous-tag> <tag>` prints Markdown notes for the commits between two tags, grouped by their Angular type (`.claude/skills/commit/SKILL.md`):

- **Features**: `feat(...)`, except art and audio
- **Fixes**: `fix(...)`
- **Art and sounds**: `feat(art)`, `feat(audio)`
- **Other**: everything else, including subjects that are not in Angular format. `build`, `ci`, `chore`, `docs`, `refactor`, `style` and `test` are left out unless `--all` is passed or the commit is marked breaking (`type!:`).

The script knows nothing about loaders. The workflow puts one line above its output in `build/release-notes.md`: the release is for NeoForge and Fabric, and which mods each loader needs. Both loaders' versions get the same notes.

Commits are listed oldest first, merge commits are skipped, and the output depends only on the subjects. Pass `""` as the previous tag for the first release. `--self-test` checks the grouping on fixed input; the workflow runs it before using the script.

So write commit subjects for players: the subject of every `feat` and `fix` ends up in the changelog as written.

## Release texts

Hand-written texts for the platforms and the release, in `docs/release/` (REL4), checked by a script:

- [`release/store-page.md`](release/store-page.md): the Modrinth and CurseForge description (one text for both, with what CurseForge's renderer does not take).
- [`release/1.0.0-notes.md`](release/1.0.0-notes.md): the 1.0.0 release notes (hand-written, since there is no earlier tag to generate from) and the `release.yml` change that publishes them.
- [`release/save-compatibility.md`](release/save-compatibility.md): what a pre-release world keeps and loses under 1.0.0 (SAV1); the notes link to it.
- [`../README.md`](../README.md): the repository's front page, with the screenshot list for the galleries.
- `tools/check_release_texts.py`: fails when the store page, the notes or the README name an item, block, mob or config key that the generated lang does not have, or a dependency version that differs from `gradle.properties` (`--self-test` checks its rules).

## Cutting a release

Before tagging, check:

1. `main` is green on CI (build, JUnit, GameTests).
2. The playtests for everything new since the last release are done (`docs/playtests/`, and the playtest list at the end of `docs/progress.md`). Nothing the release notes advertise should be unplaytested.
3. `docs/progress.md` has no open blocker for the features in this release.
4. `docs/release/save-compatibility.md` is current: every stored format, block state, registry id or config key changed since the last release is in it (what converts, what is lost, what a player does by hand), and `KeptIdsTest` passes.
5. Preview the notes: `python3 tools/release_notes.py <previous-tag> HEAD`.
6. Check the release texts: `python3 tools/check_release_texts.py` passes, and `docs/release/store-page.md` and the version's notes still describe what is on `main` (paste the store page into both platforms when it changed).
7. The configuration table in `docs/guide.md` §13 matches the code: `python3 tools/check_config_docs.py` prints `ok` (it fails on a config section without a row, a row without a section, a nested group its row does not name, or a backticked name that is no longer a key; `--keys` lists the keys a row leaves out, `--self-test` checks the parser). Values whose defaults changed since the last release belong in the release notes.
8. The structure files carry no misplaced block entities: `python3 tools/check_structures.py` ends with `0 with problems` (it fails on block entity data on a state without one or with another block's id, and on an entity block without a block entity in a generated structure, which worldgen logs as "Tried to load a block entity … but failed"; keep its `MOD_RULES` in step with every new entity block).
9. Optionally a dry run: Actions → Release → Run workflow, `version` = the planned tag, `dry_run` checked. It builds, runs the tests on both loaders, shows the notes in the job summary and attaches both jars as a workflow artifact.

Then tag and push:

```sh
git tag -a v0.1.0-beta.1 -m "Pirates 'n' Ships 0.1.0-beta.1"
git push origin v0.1.0-beta.1
```

The tag push starts the workflow. After a final (non-pre-release) version, bump `mod_version` in `gradle.properties` to the next planned version.

A failed publish can be retried with Actions → Release → Run workflow, `version` = the existing tag, `dry_run` unchecked, and `publish` = the loader that failed (`neoforge` or `fabric`; the default `both` publishes both). It checks out the tag, not the branch. Retry only the failed loader: Modrinth accepts a second version with the same number, so republishing a loader that already went out makes a duplicate there.

The two loaders publish in two separate steps, NeoForge first. A failed NeoForge publish does not stop the Fabric one, and a failed Fabric publish does not touch the NeoForge version already published (nothing is rolled back); the job ends red and the failed loader is retried as above. Within one step mc-publish uploads to Modrinth, CurseForge and GitHub in turn, carries on past a platform that fails and fails the step at the end. A retry of that loader uploads to all three again, so first delete the version it did publish on Modrinth or CurseForge (GitHub just replaces the jar on the release), or upload the missing platform by hand instead.

## What the workflow does

`.github/workflows/release.yml`, on a `v*` tag push or on `workflow_dispatch` (inputs `version`, `dry_run`, default true):

1. Checks out with full history and tags.
2. Resolves the version from the tag (rejects anything but `vX.Y.Z[-alpha.N|-beta.N]`), the channel (after a self-check of the rule), and the previous `v*` tag for the notes. A non-dry run needs the tag to exist and checks it out; a dry run of a tag that does not exist uses the selected branch.
3. JDK 21 (Temurin), Gradle with caching.
4. `./gradlew build -Pversion=<version>` (includes JUnit).
5. `./gradlew :neoforge:runGameTestServer -Pversion=<version>` (headless; the normal CI runs it on ubuntu too).
6. `./gradlew :fabric:runGameTest -Pversion=<version>` (headless, like the CI's Fabric GameTests).
7. Checks there is exactly one release jar `neoforge/build/libs/*-neoforge-*-<version>.jar` (sources and javadoc jars do not match) and that its `neoforge.mods.toml` has that version.
8. Checks there is exactly one release jar `fabric/build/libs/*-fabric-*-<version>.jar` and that its `fabric.mod.json` has `"version": "<version>"`.
9. Writes the notes (`tools/release_notes.py --self-test`, then the loader line and the notes into `build/release-notes.md` and the job summary).
10. Uploads both jars and the notes as a workflow artifact.
11. Unless dry run: checks the secrets exist, then [`Kira-NT/mc-publish@v3.3.1`](https://github.com/Kira-NT/mc-publish) (formerly `Kir-Antipov/mc-publish`; NeoForge support since v3.3.0) publishes the NeoForge jar to Modrinth and CurseForge and creates the GitHub release for the tag with the notes and the jar (skipped when `publish` is `fabric`).
12. Unless dry run: a second mc-publish call publishes the Fabric jar to Modrinth and CurseForge (loader `fabric`, the Fabric dependencies) and adds it to the GitHub release of the tag (mc-publish finds the release by its tag; it creates it if the NeoForge step did not). It runs when every check passed, also after a failed NeoForge publish (skipped when `publish` is `neoforge`).

Releases run one at a time (`concurrency: release`). A second tag pushed meanwhile waits; GitHub keeps only one waiting run per group, so a third would cancel the waiting second one. Push release tags one by one. A tag push also triggers the normal `Build` workflow; that is harmless duplicate work.

## One-time setup (the maintainer)

1. Create the project on [Modrinth](https://modrinth.com/) and on [CurseForge](https://www.curseforge.com/minecraft/mc-mods) (license PolyForm Noncommercial 1.0.0, NeoForge and Fabric, 1.21.1). One project per platform carries both loaders; if the project already exists with NeoForge only, add Fabric to its loaders (CurseForge: the project's settings; Modrinth fills the loaders from the uploaded versions). The first file on CurseForge may need the project to be approved first.
2. Tokens:
   - Modrinth: Settings → [Personal access tokens](https://modrinth.com/settings/pats), with the scopes "Create versions" and "Write versions" (and "Read projects").
   - CurseForge: [API tokens](https://legacy.curseforge.com/account/api-tokens) (the upload API token, not a CurseForge Core API key).
3. In the GitHub repository, Settings → Secrets and variables → Actions:
   - Secrets: `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`.
   - Variables (or secrets, both work): `MODRINTH_ID` (the project id from the Modrinth project page, e.g. `AANobbMI`, or its slug) and `CURSEFORGE_ID` (the numeric "Project ID" from the CurseForge project's About box).
4. Settings → Actions → General → Workflow permissions: allow read and write (the workflow also asks for `contents: write` itself).
