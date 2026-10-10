"""Check generated structure layouts for a natural shape (STR2; STR3 and STR4 reuse it for the village and the outpost).

Usage:
    python3 tools/structure_check.py [DIR | layout-<seed>.txt ...] [--min-pieces 10] [--min-aspect 0.5]
                                     [--min-variants 4] [--map]

Reads the layout dumps ``StructureLayoutGameTests`` writes (``layout-<seed>.txt``, one per generated instance; default
folder ``neoforge/build/gametest/structure-layouts``, the GameTest run directory's ``structure-layouts``; format in
``world/structure/StructureLayout``) and checks every instance the way the GameTest does, from the piece list itself:

- at least ``--min-pieces`` pieces;
- a footprint aspect (the shorter side of the bounding box over the longer) of at least ``--min-aspect``: not a line;
- at least ``--min-variants`` distinct buildings (the names on the dump's ``buildings`` line);
- the jetty's (or pier's) far end over the water (the dump's ``water_end``);
- no two instances with the same piece sequence.

It prints one row per instance (pieces, size, aspect, buildings, the kinds of trail pieces) and, with ``--map``, a
top-down sketch of the piece boxes (one letter per piece, ``#`` where boxes overlap in plan). Exit code 1 when any
check fails. Standard library only. To make the dumps: ``./gradlew :neoforge:runGameTestServer
-Ppirates_n_ships.gametest.only=StructureLayoutGameTests`` (``art/README.md``, "Natural layout (STR2)").
"""
from __future__ import annotations

import argparse
import string
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DIR = ROOT / "neoforge" / "build" / "gametest" / "structure-layouts"


def parse(path: Path) -> dict:
    out = {"file": path.name, "piece_list": []}
    for line in path.read_text().splitlines():
        if not line.strip():
            continue
        key, _, rest = line.partition(" ")
        if key == "piece":
            _, name, *box = rest.split()
            out["piece_list"].append((name, tuple(int(v) for v in box)))
        else:
            out[key] = rest
    return out


def bounds(pieces):
    boxes = [b for _, b in pieces]
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), min(b[2] for b in boxes),
            max(b[3] for b in boxes), max(b[4] for b in boxes), max(b[5] for b in boxes))


def aspect(pieces) -> float:
    b = bounds(pieces)
    w, d = b[3] - b[0] + 1, b[5] - b[2] + 1
    return min(w, d) / max(w, d)


def sketch(pieces) -> list[str]:
    b = bounds(pieces)
    w, d = b[3] - b[0] + 1, b[5] - b[2] + 1
    grid = [[" "] * w for _ in range(d)]
    letters = string.ascii_letters + string.digits
    for i, (_, box) in enumerate(pieces):
        mark = letters[i % len(letters)]
        for z in range(box[2], box[5] + 1):
            for x in range(box[0], box[3] + 1):
                cell = grid[z - b[2]][x - b[0]]
                grid[z - b[2]][x - b[0]] = mark if cell == " " else "#"
    return ["".join(row).rstrip() for row in grid]


def check(layouts: list[dict], min_pieces: int, min_aspect: float, min_variants: int) -> list[str]:
    problems = []
    seen = {}
    for lay in layouts:
        seed = lay.get("seed", lay["file"])
        pieces = lay["piece_list"]
        buildings = set(filter(None, lay.get("buildings", "").split(",")))
        variants = {name for name, _ in pieces if name in buildings}
        if len(pieces) < min_pieces:
            problems.append(f"seed {seed}: {len(pieces)} pieces, at least {min_pieces}")
        a = aspect(pieces)
        if a < min_aspect:
            problems.append(f"seed {seed}: aspect {a:.2f}, at least {min_aspect:.2f} (a line)")
        if len(variants) < min_variants:
            problems.append(f"seed {seed}: {len(variants)} distinct buildings {sorted(variants)}, at least {min_variants}")
        if lay.get("water_end") != "true":
            problems.append(f"seed {seed}: the jetty's far end is not over the water")
        sequence = ",".join(name for name, _ in pieces)
        if sequence in seen:
            problems.append(f"seeds {seen[sequence]} and {seed} have the same piece sequence")
        seen.setdefault(sequence, seed)
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("paths", type=Path, nargs="*", help="layout dumps or a folder of them")
    parser.add_argument("--min-pieces", type=int, default=10)
    parser.add_argument("--min-aspect", type=float, default=0.5)
    parser.add_argument("--min-variants", type=int, default=4)
    parser.add_argument("--map", action="store_true", help="print a top-down sketch of each instance's piece boxes")
    args = parser.parse_args(argv)
    files = []
    for path in args.paths or [DEFAULT_DIR]:
        files += sorted(path.glob("layout-*.txt")) if path.is_dir() else [path]
    if not files:
        print(f"no layout dumps in {args.paths or [DEFAULT_DIR]}", file=sys.stderr)
        return 1
    layouts = [parse(f) for f in files]
    print(f"{'seed':>8} {'pieces':>6} {'size':>9} {'aspect':>6}  buildings / trail pieces")
    for lay in layouts:
        pieces = lay["piece_list"]
        b = bounds(pieces)
        buildings = set(filter(None, lay.get("buildings", "").split(",")))
        variants = sorted({name for name, _ in pieces if name in buildings})
        trails = sorted({name for name, _ in pieces if name.startswith(("path", "street", "wall"))})
        print(f"{lay.get('seed', '?'):>8} {len(pieces):>6} {b[3] - b[0] + 1:>4}x{b[5] - b[2] + 1:<4} {aspect(pieces):6.2f}  "
              f"{len(variants)}: {', '.join(variants)}")
        print(f"{'':>34}{', '.join(trails)}")
        if args.map:
            print("\n".join("    " + row for row in sketch(pieces)))
    problems = check(layouts, args.min_pieces, args.min_aspect, args.min_variants)
    for problem in problems:
        print(problem, file=sys.stderr)
    print(f"{len(layouts)} layouts, {len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
