#!/bin/bash
# Orchestrator merge stage (docs/prompts/initial-orchestrator.md): verifies one agent branch in the
# `.claude/worktrees/merge` worktree and lands the verified merge commit on main.
#   tools/merge_stage.sh <branch> <pkg> "<merge commit subject>" [gametest runs, default 2]
#   CONTINUE=1 tools/merge_stage.sh ...   continues a merge whose hand-written conflicts were resolved by hand
# Steps: merge --no-ff --no-commit (conflicts under common/src/generated/resources and the generated guides are
# taken from the branch and regenerated; any other conflict stops with exit 3), runData, tools/gen_guideme.py,
# build, N GameTest rounds, commit, fast-forward main. If main moved only by docs, CLAUDE.md, the generated guide
# or this script, the verified commit is landed with a merge commit and the guide regenerated; otherwise exit 9.
# Never runs Gradle in the main checkout (the human's dev client runs from it). Logs: $STAGE_LOG_DIR/<pkg>/.
set -u
BRANCH=$1; PKG=$2; SUBJECT=$3; RUNS=${4:-2}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WT=$ROOT/.claude/worktrees/merge
LOG=${STAGE_LOG_DIR:-$ROOT/.claude/worktrees/stage-logs}/$PKG; mkdir -p "$LOG"
GEN_RE='^common/src/generated/resources/|/assets/pirates_n_ships/guides/'
DOCS_RE='^docs/|^CLAUDE\.md$|^common/src/main/resources/assets/pirates_n_ships/guides/|^tools/merge_stage\.sh$'
cd "$WT" || exit 1
if [ -z "${CONTINUE:-}" ]; then
  if [ -n "$(git status --short | grep -v '^??')" ]; then echo "merge worktree dirty"; git status --short | head; exit 2; fi
  git checkout -q --detach main || exit 2
  echo "base: $(git rev-parse --short HEAD) $(git log -1 --format=%s)"
  if ! git merge --no-ff --no-commit "$BRANCH" > "$LOG/merge.log" 2>&1; then
    CONF=$(git diff --name-only --diff-filter=U)
    GEN=$(echo "$CONF" | grep -E "$GEN_RE" || true)
    REST=$(echo "$CONF" | grep -vE "$GEN_RE" || true)
    if [ -n "$GEN" ]; then
      echo "$GEN" | while read -r f; do git checkout --theirs -- "$f" 2>/dev/null && git add -- "$f" || git rm -q -- "$f"; done
      echo "resolved $(echo "$GEN" | wc -l | tr -d ' ') generated-file conflicts"
    fi
    if [ -n "$REST" ]; then echo "MERGE CONFLICT in hand-written files:"; echo "$REST"; exit 3; fi
  fi
else
  echo "continuing a resolved merge of $BRANCH"
fi
BASE=$(git rev-parse --short HEAD)
./gradlew -q :neoforge:runData > "$LOG/rundata.log" 2>&1 || { echo "RUNDATA FAILED"; tail -20 "$LOG/rundata.log"; exit 4; }
python3 tools/gen_guideme.py > "$LOG/guide.log" 2>&1 || { echo "GUIDE FAILED"; tail -20 "$LOG/guide.log"; exit 4; }
git add -A common/src/generated/resources common/src/main/resources/assets/pirates_n_ships/guides
./gradlew build > "$LOG/build.log" 2>&1 || { echo "BUILD FAILED"; grep -E 'error:|FAILED|Exception' "$LOG/build.log" | head -20; exit 5; }
echo "build ok"
for i in $(seq 1 "$RUNS"); do
  if ./gradlew :neoforge:runGameTestServer > "$LOG/gametest-$i.log" 2>&1 && grep -q 'required tests passed' "$LOG/gametest-$i.log"; then
    echo "gametest run $i ok: $(grep -o 'All [0-9]* required tests' "$LOG/gametest-$i.log" | head -1 | sed 's/All //')"
  else
    echo "GAMETEST RUN $i FAILED"; grep -E 'failed at|required tests' "$LOG/gametest-$i.log" | head -5 | cut -c1-300; exit 6
  fi
done
git commit -q -m "$SUBJECT" || { echo "COMMIT FAILED"; exit 7; }
H=$(git rev-parse --short HEAD)
cd "$ROOT" || exit 1
if git merge --ff-only -q "$H" 2> "$LOG/ff.log"; then echo "MERGED $PKG -> main at $H (fast-forward)"; exit 0; fi
MOVED=$(git diff --name-only "$BASE" main | grep -vE "$DOCS_RE" || true)
if [ -z "$MOVED" ]; then
  git merge --no-ff -q "$H" -m "chore(merge): land the verified $PKG merge on main" > "$LOG/land.log" 2>&1 || { echo "DOCS-ONLY MERGE FAILED"; cat "$LOG/land.log"; exit 8; }
  python3 tools/gen_guideme.py > /dev/null 2>&1 && { git diff --quiet || git commit -q -am "chore(guide): regenerate after landing $PKG"; }
  echo "MERGED $PKG -> main with a merge commit (main had moved by docs only) at $(git rev-parse --short HEAD)"; exit 0
fi
echo "MAIN MOVED by code since $BASE; restage:"; echo "$MOVED" | head; exit 9
