#!/usr/bin/env python3
"""Release notes from Angular-style commit subjects (docs/releasing.md).

Usage:
    release_notes.py <previous-ref> <ref> [--all]   Markdown notes for the commits in previous-ref..ref
    release_notes.py "" <ref> [--all]               no previous release: every commit up to ref
    release_notes.py --self-test                     check the grouping on fixed input

Groups, in this order: Features (feat), Fixes (fix), Art and sounds (feat(art), feat(audio)), Other (everything else,
including subjects that are not in Angular format). Internal types (build, ci, chore, docs, refactor, style, test) are
left out unless --all is passed or the commit is marked breaking ("type!:"). Commits are listed oldest first; merge
commits are skipped. The output depends only on the commit subjects, so the same range always gives the same notes.
"""
import re
import subprocess
import sys

SUBJECT = re.compile(r"^(?P<type>[a-z]+)(?:\((?P<scope>[^)]*)\))?(?P<breaking>!)?: (?P<text>.+)$")
INTERNAL_TYPES = {"build", "ci", "chore", "docs", "refactor", "style", "test"}
ART_SCOPES = {"art", "audio"}
GROUPS = ["Features", "Fixes", "Art and sounds", "Other"]
EMPTY = "_No player-facing changes._"


def classify(subject, include_all=False):
    """Returns (group, line) for one commit subject, or None when the commit is left out."""
    match = SUBJECT.match(subject.strip())
    if not match:
        return "Other", subject.strip()
    kind, scope, text = match["type"], match["scope"], match["text"]
    breaking = "**Breaking:** " if match["breaking"] else ""
    if kind == "feat" and scope in ART_SCOPES:
        return "Art and sounds", breaking + text
    line = breaking + (f"**{scope}:** {text}" if scope else text)
    if kind == "feat":
        return "Features", line
    if kind == "fix":
        return "Fixes", line
    if kind in INTERNAL_TYPES and not include_all and not breaking:
        return None
    return "Other", f"{kind}: {line}"


def render(subjects, include_all=False):
    """Markdown notes for the subjects (oldest first)."""
    grouped = {group: [] for group in GROUPS}
    for subject in subjects:
        if not subject.strip():
            continue
        result = classify(subject, include_all)
        if result:
            grouped[result[0]].append(result[1])
    sections = [f"## {group}\n\n" + "\n".join(f"- {line}" for line in lines)
                for group, lines in grouped.items() if lines]
    return "\n\n".join(sections) + "\n" if sections else EMPTY + "\n"


def subjects_between(previous, ref):
    rev_range = f"{previous}..{ref}" if previous else ref
    out = subprocess.run(["git", "log", "--no-merges", "--reverse", "--format=%s", rev_range],
                         check=True, capture_output=True, text=True).stdout
    return out.splitlines()


def self_test():
    subjects = [
        "feat(ship): add the anchor winch",
        "fix(trade): close the market screen on death",
        "feat(art): paint the navy flag",
        "feat(audio): add cannon fire sounds",
        "docs: record t1",
        "build: bump sable to 2.0.6",
        "chore: tidy",
        "refactor(trade): move the refresh",
        "test(melee): cover parry timing",
        "ci: cache gradle",
        "perf(hull): cache the flood fill",
        "feat!: rename the config file",
        "refactor(config)!: drop the old keys",
        "Merge something old-style",
        "feat: no scope",
        "",
    ]
    expected = (
        "## Features\n\n"
        "- **ship:** add the anchor winch\n"
        "- **Breaking:** rename the config file\n"
        "- no scope\n\n"
        "## Fixes\n\n"
        "- **trade:** close the market screen on death\n\n"
        "## Art and sounds\n\n"
        "- paint the navy flag\n"
        "- add cannon fire sounds\n\n"
        "## Other\n\n"
        "- perf: **hull:** cache the flood fill\n"
        "- refactor: **Breaking:** **config:** drop the old keys\n"
        "- Merge something old-style\n"
    )
    actual = render(subjects)
    assert actual == expected, f"grouping changed:\n{actual}"
    with_all = render(subjects, include_all=True)
    for line in ("- docs: record t1", "- build: bump sable to 2.0.6", "- ci: cache gradle",
                 "- refactor: **trade:** move the refresh", "- test: **melee:** cover parry timing"):
        assert line in with_all, f"--all lost {line!r}:\n{with_all}"
    assert render(["docs: only docs", "chore: x"]) == EMPTY + "\n"
    assert render(subjects) == render(list(subjects)), "not deterministic"
    print("release_notes self-test passed")


def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    flags = {a for a in argv if a.startswith("--")}
    unknown = flags - {"--all", "--self-test"}
    if unknown:
        sys.exit(f"unknown option(s): {' '.join(sorted(unknown))}\n{__doc__}")
    if "--self-test" in flags:
        self_test()
        return
    if len(args) != 2:
        sys.exit(__doc__)
    sys.stdout.write(render(subjects_between(args[0], args[1]), "--all" in flags))


if __name__ == "__main__":
    main(sys.argv[1:])
