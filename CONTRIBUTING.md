# Contributing

Project for **Hack-Nation 7 (2026)**.

## Workflow

1. Branch off `main`: `git checkout -b <type>/<short-description>` (e.g. `feat/team-signup`).
2. Keep changes focused on one thing — no drive-by refactors.
3. Commit using the convention below.
4. Open a PR into `main` with a short description of what and why.

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

Shared rules for Claude Code and Codex live in `AGENTS.md` (`CLAUDE.md` imports it). The `commit` skill is in `.claude/skills/commit/`, symlinked to `.agents/skills/commit/` for Codex.