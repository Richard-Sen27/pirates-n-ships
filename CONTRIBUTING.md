# Contributing

Contribution guide for **Pirates 'n' Ships**. The design spec lives in [`docs/design.md`](docs/design.md).

## Workflow

1. Branch off `main`: `git checkout -b <type>/<short-description>` (e.g. `feat/ship-assembly`).
2. Keep changes focused on one thing — no drive-by refactors.
3. Commit using the convention below.
4. Before opening a PR, make sure `./gradlew build` and `./gradlew :neoforge:runGameTestServer` pass.
5. Open a PR into `main` with a short description of what and why.

## Commit messages

[Angular convention](https://github.com/angular/angular/blob/main/contributing-docs/commit-message-guidelines.md), subject line only:

```
<type>(<scope>): <subject>
```

| Type       | Use for                                   |
| ---------- | ----------------------------------------- |
| `feat`     | New feature                               |
| `fix`      | Bug fix                                   |
| `docs`     | Documentation only                        |
| `style`    | Formatting, no logic change               |
| `refactor` | Code change that isn't a fix or feature   |
| `perf`     | Performance improvement                   |
| `test`     | Adding or fixing tests                    |
| `build`    | Build system or dependencies              |
| `ci`       | CI configuration                          |
| `chore`    | Maintenance, tooling                      |
| `revert`   | Reverting a previous commit               |

- Imperative, lowercase subject, no trailing period, ≤ 72 chars.
- No commit body unless it's genuinely needed.
- No `Co-Authored-By:` trailers.

## AI assistants

Rules for Claude Code live in `CLAUDE.md`. The `commit` skill is in `.claude/skills/commit/`, and the shared permission allowlist is in `.claude/settings.json`.