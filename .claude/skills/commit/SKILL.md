---
name: commit
description: Create a git commit in Angular style (subject only, no co-author trailers). Use whenever committing changes in this repo.
---

# Commit

1. Run `git status` and `git diff` (staged and unstaged) to see what changed.
2. Stage only the files related to the user's request. Never stage unrelated changes or secrets (`.env`, keys).
3. Write the message in Angular format:

   ```
   <type>(<scope>): <subject>
   ```

    - **type**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
    - **scope**: optional, the area touched (e.g. `api`, `ui`, `auth`)
    - **subject**: imperative, lowercase, no trailing period, ≤ 72 chars

4. **No body** unless the change genuinely needs explanation (e.g. a non-obvious reason or breaking change). Breaking changes use a `BREAKING CHANGE:` footer.
5. **No `Co-Authored-By:` or any attribution trailers.**
6. Commit with `git commit -m "<message>"`. Don't push unless asked.

## Examples

```
feat(api): add team registration endpoint
fix(ui): prevent double submit on login form
docs: add setup instructions to readme
chore: add eslint config
```