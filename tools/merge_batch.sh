#!/bin/bash
# Orchestrator batch stage: merges several verified agent branches into `.claude/worktrees/merge`, verifies the
# combined tree once (runData, guide, build, N GameTest rounds) and lands it on main.
#   tools/merge_batch.sh <gametest runs> "<pkg>|<branch>|<merge commit subject>" ...
#   FABRIC=1 adds one run of the Fabric GameTest suite after the NeoForge rounds.
#   CONTINUE=1 tools/merge_batch.sh <runs> "<remaining entries>"...   after resolving a conflict by hand: commits the
#   in-progress merge (conflicts resolved and added), merges the remaining entries, verifies, lands.
# Conflicts under common/src/generated/resources and the generated guides are taken from the branch and regenerated;
# any other conflict stops with exit 3 and the merge left in progress. Each branch gets its own merge commit, so the
# per-package docs steps can cite it. Landing: fast-forward, or a merge commit when main moved only by docs,
# CLAUDE.md, the generated guide or the stage scripts (exit 9 otherwise). Never runs Gradle in the main checkout.
set -u
RUNS=$1; shift
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WT=$ROOT/.claude/worktrees/merge
LOG=${STAGE_LOG_DIR:-$ROOT/.claude/worktrees/stage-logs}/batch-$(date +%H%M); mkdir -p "$LOG"
GEN_RE='^common/src/generated/resources/|/assets/pirates_n_ships/guides/'
DOCS_RE='^docs/|^CLAUDE\.md$|^common/src/main/resources/assets/pirates_n_ships/guides/|^tools/merge_(stage|batch)\.sh$|^\.claude/agents/'
cd "$WT" || exit 1
PKGS=""
if [ -z "${CONTINUE:-}" ]; then
  if [ -n "$(git status --short | grep -v '^??')" ]; then echo "merge worktree dirty"; git status --short | head; exit 2; fi
  git checkout -q --detach main || exit 2
  echo "base: $(git rev-parse --short HEAD) $(git log -1 --format=%s)"
  BASE=$(git rev-parse --short HEAD); echo "$BASE" > "$LOG/base"
else
  BASE=$(cat "$ROOT/.claude/worktrees/stage-logs/last-batch-base" 2>/dev/null || cat "$LOG/../last-batch-base" 2>/dev/null || git merge-base main HEAD)
  if [ -f .git/MERGE_HEAD ] || [ -f "$(git rev-parse --git-dir)/MERGE_HEAD" ]; then
    git commit -q --no-edit || { echo "COMMIT OF THE RESOLVED MERGE FAILED"; exit 7; }
    echo "committed the resolved merge as $(git rev-parse --short HEAD)"
  fi
fi
echo "$BASE" > "${STAGE_LOG_DIR:-$ROOT/.claude/worktrees/stage-logs}/last-batch-base"
for ENTRY in "$@"; do
  PKG=${ENTRY%%|*}; REST=${ENTRY#*|}; BRANCH=${REST%%|*}; SUBJECT=${REST#*|}
  if git log main.."$BRANCH" --format=%b | grep -qi 'co-authored-by\|generated with'; then echo "ATTRIBUTION TRAILER in $BRANCH (CLAUDE.md forbids it); strip it with: git -C <worktree> filter-branch -f --msg-filter 'grep -vi co-authored-by' -- main..HEAD"; exit 10; fi
  if ! git merge --no-ff --no-commit "$BRANCH" > "$LOG/merge-$PKG.log" 2>&1; then
    CONF=$(git diff --name-only --diff-filter=U)
    GEN=$(echo "$CONF" | grep -E "$GEN_RE" || true)
    REST_CONF=$(echo "$CONF" | grep -vE "$GEN_RE" || true)
    if [ -n "$GEN" ]; then
      echo "$GEN" | while read -r f; do git checkout --theirs -- "$f" 2>/dev/null && git add -- "$f" || git rm -q -- "$f"; done
      echo "$PKG: resolved $(echo "$GEN" | wc -l | tr -d ' ') generated-file conflicts"
    fi
    if [ -n "$REST_CONF" ]; then echo "MERGE CONFLICT in hand-written files while merging $PKG:"; echo "$REST_CONF"; echo "resolve, git add, then CONTINUE=1 with the remaining entries"; exit 3; fi
  fi
  git commit -q -m "$SUBJECT" || { echo "COMMIT FAILED for $PKG"; exit 7; }
  echo "merged $PKG at $(git rev-parse --short HEAD)"
  PKGS="$PKGS $PKG"
done
./gradlew -q :neoforge:runData > "$LOG/rundata.log" 2>&1 || { echo "RUNDATA FAILED"; tail -20 "$LOG/rundata.log"; exit 4; }
python3 tools/gen_guideme.py > "$LOG/guide.log" 2>&1 || { echo "GUIDE FAILED"; tail -20 "$LOG/guide.log"; exit 4; }
git add -A common/src/generated/resources common/src/main/resources/assets/pirates_n_ships/guides
git diff --cached --quiet || git commit -q -m "chore(merge): regenerate data and guide after merging$PKGS"
./gradlew build > "$LOG/build.log" 2>&1 || { echo "BUILD FAILED"; grep -E 'error:|FAILED|Exception' "$LOG/build.log" | head -20; exit 5; }
echo "build ok"
for i in $(seq 1 "$RUNS"); do
  if ./gradlew :neoforge:runGameTestServer > "$LOG/gametest-$i.log" 2>&1 && grep -q 'required tests passed' "$LOG/gametest-$i.log"; then
    echo "gametest run $i ok: $(grep -o 'All [0-9]* required tests' "$LOG/gametest-$i.log" | head -1 | sed 's/All //')"
  else
    echo "GAMETEST RUN $i FAILED"; grep -E 'failed at|required tests' "$LOG/gametest-$i.log" | head -5 | cut -c1-300; exit 6
  fi
done
if [ -n "${FABRIC:-}" ]; then
  if ./gradlew :fabric:runGameTest > "$LOG/gametest-fabric.log" 2>&1 && grep -q 'required tests passed' "$LOG/gametest-fabric.log"; then
    echo "fabric gametest ok: $(grep -o 'All [0-9]* required tests' "$LOG/gametest-fabric.log" | head -1 | sed 's/All //')"
  else
    echo "FABRIC GAMETEST FAILED"; grep -E 'failed at|required tests' "$LOG/gametest-fabric.log" | head -5 | cut -c1-300; exit 6
  fi
fi
H=$(git rev-parse --short HEAD)
cd "$ROOT" || exit 1
if git merge --ff-only -q "$H" 2> "$LOG/ff.log"; then echo "MERGED$PKGS -> main at $H (fast-forward)"; exit 0; fi
MOVED=$(git diff --name-only "$BASE" main | grep -vE "$DOCS_RE" || true)
if [ -z "$MOVED" ]; then
  git merge --no-ff -q "$H" -m "chore(merge): land the verified batch$PKGS on main" > "$LOG/land.log" 2>&1 || { echo "DOCS-ONLY MERGE FAILED"; cat "$LOG/land.log"; exit 8; }
  python3 tools/gen_guideme.py > /dev/null 2>&1 && { git diff --quiet || git commit -q -am "chore(guide): regenerate after landing$PKGS"; }
  echo "MERGED$PKGS -> main with a merge commit (main had moved by docs only) at $(git rev-parse --short HEAD)"; exit 0
fi
echo "MAIN MOVED by code since $BASE; restage:"; echo "$MOVED" | head; exit 9
