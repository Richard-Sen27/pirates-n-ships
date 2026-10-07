# Releasing

How versions are numbered, how a release is cut, and what the release workflow (`.github/workflows/release.yml`) does. NeoForge on Minecraft 1.21.1 is the only published target until the Fabric port (design.md §2).

## Version scheme

- **Semantic versions** `MAJOR.MINOR.PATCH`, starting at `0.1.0`. Before `1.0.0` the mod is in beta by definition.
- **Pre-releases** add `-alpha.N` or `-beta.N`: `0.3.0-alpha.1`, `0.3.0-beta.2`. Nothing else (no `-rc.N`, no build metadata); the build and the workflow reject other forms.
- **Git tags** are the version with a `v`: `v0.1.0`, `v0.3.0-beta.1`.
- **Mod version and jar** carry the version without the `v`. The mod list shows it (`neoforge.mods.toml` has `version = "0.3.0-beta.1"`), and the jar is `pirates_n_ships-neoforge-1.21.1-<version>.jar` (`<mod_id>-<loader>-<minecraft version>-<version>`, set in `buildSrc/src/main/groovy/multiloader-common.gradle`).
- **`gradle.properties`** holds `mod_version`, the *next* planned version without a suffix (e.g. `0.1.0`). Bump it after a final release.
- **Local builds** are `<mod_version>-dev` (e.g. `pirates_n_ships-neoforge-1.21.1-0.1.0-dev.jar`), so a dev jar never looks like a release. A release build passes the tag's version: `./gradlew build -Pversion=0.1.0-beta.1`. The root `build.gradle` sets this for all projects and fails on a malformed `-Pversion`.

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

- Version number: the mod version, e.g. `0.3.0-beta.1`.
- Display name: `Pirates 'n' Ships 0.3.0-beta.1 for NeoForge 1.21.1`.
- Loader `neoforge`, game version `1.21.1`, Java 21.
- Dependencies:

| Mod | Type | Modrinth | CurseForge |
|---|---|---|---|
| Sable (ryanhcode) | required | [`sable`](https://modrinth.com/mod/sable), id `T9PomCSv` | [`sable`](https://www.curseforge.com/minecraft/mc-mods/sable), id `1312371` |
| GeckoLib | required | [`geckolib`](https://modrinth.com/mod/geckolib), id `8BmcQJ2H` | [`geckolib`](https://www.curseforge.com/minecraft/mc-mods/geckolib), id `388172` |
| Player Animation Library (ZigyTheBird) | required (client) | [`player-animation-library`](https://modrinth.com/mod/player-animation-library), id `ha1mEyJS` | [`player-animation-library`](https://www.curseforge.com/minecraft/mc-mods/player-animation-library), id `1283899` |
| GuideME | optional | [`guideme`](https://modrinth.com/mod/guideme), id `Ck4E7v7R` | [`guideme`](https://www.curseforge.com/minecraft/mc-mods/guideme), id `1173950` |

The workflow passes the numeric ids (more robust than slugs). When a dependency is added or dropped in `neoforge.mods.toml`, update the `dependencies` list in `release.yml` and this table.

## Release notes

`tools/release_notes.py <previous-tag> <tag>` prints Markdown notes for the commits between two tags, grouped by their Angular type (`.claude/skills/commit/SKILL.md`):

- **Features**: `feat(...)`, except art and audio
- **Fixes**: `fix(...)`
- **Art and sounds**: `feat(art)`, `feat(audio)`
- **Other**: everything else, including subjects that are not in Angular format. `build`, `ci`, `chore`, `docs`, `refactor`, `style` and `test` are left out unless `--all` is passed or the commit is marked breaking (`type!:`).

Commits are listed oldest first, merge commits are skipped, and the output depends only on the subjects. Pass `""` as the previous tag for the first release. `--self-test` checks the grouping on fixed input; the workflow runs it before using the script.

So write commit subjects for players: the subject of every `feat` and `fix` ends up in the changelog as written.

## Cutting a release

Before tagging, check:

1. `main` is green on CI (build, JUnit, GameTests).
2. The playtests for everything new since the last release are done (`docs/playtests/`, and the playtest list at the end of `docs/progress.md`). Nothing the release notes advertise should be unplaytested.
3. `docs/progress.md` has no open blocker for the features in this release.
4. Preview the notes: `python3 tools/release_notes.py <previous-tag> HEAD`.
5. Optionally a dry run: Actions → Release → Run workflow, `version` = the planned tag, `dry_run` checked. It builds, runs the tests, shows the notes in the job summary and attaches the jar as a workflow artifact.

Then tag and push:

```sh
git tag -a v0.1.0-beta.1 -m "Pirates 'n' Ships 0.1.0-beta.1"
git push origin v0.1.0-beta.1
```

The tag push starts the workflow. After a final (non-pre-release) version, bump `mod_version` in `gradle.properties` to the next planned version.

A failed publish can be retried with Actions → Release → Run workflow, `version` = the existing tag, `dry_run` unchecked. It checks out the tag, not the branch. A version that already exists on a platform is rejected there.

## What the workflow does

`.github/workflows/release.yml`, on a `v*` tag push or on `workflow_dispatch` (inputs `version`, `dry_run`, default true):

1. Checks out with full history and tags.
2. Resolves the version from the tag (rejects anything but `vX.Y.Z[-alpha.N|-beta.N]`), the channel (after a self-check of the rule), and the previous `v*` tag for the notes. A non-dry run needs the tag to exist and checks it out; a dry run of a tag that does not exist uses the selected branch.
3. JDK 21 (Temurin), Gradle with caching.
4. `./gradlew build -Pversion=<version>` (includes JUnit).
5. `./gradlew :neoforge:runGameTestServer -Pversion=<version>` (headless; the normal CI runs it on ubuntu too).
6. Checks there is exactly one release jar `neoforge/build/libs/*-neoforge-*-<version>.jar` (sources and javadoc jars do not match) and that its `neoforge.mods.toml` has that version.
7. Writes the notes (`tools/release_notes.py --self-test`, then the notes into `build/release-notes.md` and the job summary).
8. Uploads the jar and the notes as a workflow artifact.
9. Unless dry run: checks the secrets exist, then [`Kira-NT/mc-publish@v3.3.1`](https://github.com/Kira-NT/mc-publish) (formerly `Kir-Antipov/mc-publish`; NeoForge support since v3.3.0) publishes the jar to Modrinth and CurseForge and creates the GitHub release for the tag with the same notes and jar.

Releases run one at a time (`concurrency: release`). A second tag pushed meanwhile waits; GitHub keeps only one waiting run per group, so a third would cancel the waiting second one. Push release tags one by one. A tag push also triggers the normal `Build` workflow; that is harmless duplicate work.

## One-time setup (the maintainer)

1. Create the project on [Modrinth](https://modrinth.com/) and on [CurseForge](https://www.curseforge.com/minecraft/mc-mods) (license PolyForm Noncommercial 1.0.0, NeoForge, 1.21.1). The first file on CurseForge may need the project to be approved first.
2. Tokens:
   - Modrinth: Settings → [Personal access tokens](https://modrinth.com/settings/pats), with the scopes "Create versions" and "Write versions" (and "Read projects").
   - CurseForge: [API tokens](https://legacy.curseforge.com/account/api-tokens) (the upload API token, not a CurseForge Core API key).
3. In the GitHub repository, Settings → Secrets and variables → Actions:
   - Secrets: `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`.
   - Variables (or secrets, both work): `MODRINTH_ID` (the project id from the Modrinth project page, e.g. `AANobbMI`, or its slug) and `CURSEFORGE_ID` (the numeric "Project ID" from the CurseForge project's About box).
4. Settings → Actions → General → Workflow permissions: allow read and write (the workflow also asks for `contents: write` itself).
