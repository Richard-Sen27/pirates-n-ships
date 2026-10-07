"""Build the structure pieces from their BuildSpec sources through the minecraft-schematic-lab HTTP API.

Usage:
    python3 tools/build_structures.py                         # every art/structures/<group>/<piece>.py
    python3 tools/build_structures.py village/tavern [...]    # some pieces (<group>/<piece>, or a path to the .py)
    python3 tools/build_structures.py --views DIR             # also render the piece seen from the other corners

For every source (files starting with ``_`` are helpers, not pieces):

1. run it (``python3 <piece>.py``): it prints the BuildSpec JSON (the format of art/schematics/starter_sloop.py);
2. ``POST /api/session/validate`` and ``POST /api/session/build``; ``valid: false`` or any warning (unknown block id,
   blocks outside the size, a window without a wall) fails the run;
3. ``GET /api/session/export.schem?version=2`` -> ``art/schematics/structures/<group>/<piece>.schem`` (Sponge v2) and
   ``GET /api/session/preview.png`` -> ``art/renders/structures/<group>/<piece>.png`` (the lab's isometric render,
   seen from the south-east, flat block colours);
4. ``tools/schem_to_structure.py`` -> ``common/src/main/resources/data/pirates_n_ships/structure/<group>/<piece>.nbt``.

Files are only rewritten when their bytes change, and the lab's output is deterministic, so a second run changes
nothing. The lab (``minecraft-schematic-lab`` v0.1.0, run without ``--mcp`` it is a plain HTTP server that never opens
a browser) is found on ``--port`` (else the ``SCHEMATIC_LAB_PORT`` environment variable, else 8766; 8765 is the default of
the human's own MCP instance, and parallel agents each pick their own port) or started there
with ``npx -y github:SimoneRecchia/minecraft-schematic-lab#v0.1.0`` and stopped again at the end. ``--lab-cmd``
replaces that command. Standard library only.
"""
from __future__ import annotations

import argparse
import json
import os
import shlex
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "art" / "structures"
SCHEMATICS = ROOT / "art" / "schematics" / "structures"
RENDERS = ROOT / "art" / "renders" / "structures"
DEFAULT_PORT = 8766
LAB_CMD = "npx -y github:SimoneRecchia/minecraft-schematic-lab#v0.1.0"

sys.dont_write_bytecode = True  # no __pycache__ in tools/ or art/structures/
sys.path.insert(0, str(Path(__file__).resolve().parent))
import schem_to_structure  # noqa: E402  (same folder)


class Lab:
    def __init__(self, port: int, cmd: str):
        self.base = f"http://127.0.0.1:{port}"
        self.port = port
        self.cmd = cmd
        self.process: subprocess.Popen | None = None

    def _request(self, method: str, path: str, body: dict | None = None, timeout: float = 120) -> bytes:
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(self.base + path, data=data, method=method)
        if data is not None:
            request.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.read()

    def get(self, path: str) -> bytes:
        return self._request("GET", path)

    def post(self, path: str, body: dict) -> dict:
        return json.loads(self._request("POST", path, body))

    def healthy(self) -> bool:
        try:
            info = json.loads(self._request("GET", "/api/health", timeout=2))
        except (urllib.error.URLError, OSError, ValueError):
            return False
        return info.get("ok") is True and info.get("name") == "minecraft-schematic-lab"

    def __enter__(self):
        if self.healthy():
            print(f"using the schematic lab on {self.base}")
            return self
        env = dict(os.environ, PORT=str(self.port), HOST="127.0.0.1", LOG_LEVEL="warn")
        env.pop("MCP", None)
        print(f"starting the schematic lab on {self.base}: {self.cmd}")
        self.process = subprocess.Popen(shlex.split(self.cmd), env=env, stdin=subprocess.DEVNULL,
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                        start_new_session=True)
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            if self.healthy():
                return self
            if self.process.poll() is not None:
                raise RuntimeError(f"the schematic lab exited with {self.process.returncode}")
            time.sleep(0.5)
        self.__exit__(None, None, None)
        raise RuntimeError("the schematic lab did not answer within 120 s")

    def __exit__(self, *_):
        if self.process is not None:
            try:
                os.killpg(self.process.pid, 15)
            except ProcessLookupError:
                pass
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(self.process.pid, 9)
            self.process = None


def find_sources(names: list[str]) -> list[Path]:
    if not names:
        return sorted(p for p in SOURCES.glob("*/*.py") if not p.name.startswith("_"))
    out = []
    for name in names:
        path = Path(name)
        if path.suffix != ".py":
            path = SOURCES / (name + ".py")
        if not path.is_file():
            raise SystemExit(f"no piece source {path}")
        out.append(path.resolve())
    return out


def write_if_changed(path: Path, data: bytes) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_bytes() == data:
        return "unchanged"
    existed = path.exists()
    path.write_bytes(data)
    return "updated" if existed else "new"


def run_source(source: Path) -> dict:
    result = subprocess.run([sys.executable, "-B", str(source)], capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"{source.name} failed:\n{result.stderr}")
    return json.loads(result.stdout)


def mirrored(spec: dict, axes: str) -> dict:
    """The spec with positions mirrored on the given axes: the same build seen from another corner in the lab's
    fixed south-east render (block states are not mirrored; the render only uses block colours)."""
    size = spec["size"]
    out = json.loads(json.dumps(spec))

    def flip(pos):
        x, y, z = pos
        if "x" in axes:
            x = size["x"] - 1 - x
        if "z" in axes:
            z = size["z"] - 1 - z
        return [x, y, z]

    for op in out["operations"]:
        for key in ("from", "to", "pos", "center"):
            if key in op:
                op[key] = flip(op[key])
    return out


def build(lab: Lab, source: Path, views: Path | None) -> list[str]:
    group = source.parent.name
    piece = source.stem
    spec = run_source(source)
    problems = []
    check = lab.post("/api/session/validate", spec)
    if not check.get("valid"):
        return [f"{group}/{piece}: invalid: {check.get('errors')}"]
    result = lab.post("/api/session/build", spec)
    if not result.get("valid"):
        return [f"{group}/{piece}: invalid: {result.get('errors')}"]
    for warning in result.get("warnings") or []:
        problems.append(f"{group}/{piece}: warning: {warning}")
    if problems:
        return problems
    schem_path = SCHEMATICS / group / f"{piece}.schem"
    render_path = RENDERS / group / f"{piece}.png"
    schem_state = write_if_changed(schem_path, lab.get("/api/session/export.schem?version=2"))
    render_state = write_if_changed(render_path, lab.get("/api/session/preview.png"))
    nbt_path = schem_to_structure.default_out(schem_path) / f"{piece}.nbt"
    before = nbt_path.read_bytes() if nbt_path.exists() else None
    schem_to_structure.convert(schem_path)
    nbt_state = "unchanged" if before == nbt_path.read_bytes() else ("updated" if before is not None else "new")
    print(f"{group}/{piece}: {result['blockCount']} blocks; schem {schem_state}, render {render_state}, "
          f"nbt {nbt_state}")
    if views is not None:
        for axes, label in (("z", "ne"), ("x", "sw"), ("xz", "nw")):
            lab.post("/api/session/build", mirrored(spec, axes))
            write_if_changed(views / group / f"{piece}_{label}.png", lab.get("/api/session/preview.png"))
    return []


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("pieces", nargs="*", help="<group>/<piece> names or source paths (default: all)")
    parser.add_argument("--port", type=int, default=int(os.environ.get("SCHEMATIC_LAB_PORT", DEFAULT_PORT)),
                        help="lab port (default: $SCHEMATIC_LAB_PORT, else 8766)")
    parser.add_argument("--lab-cmd", default=LAB_CMD, help="command that starts the lab (PORT is set for it)")
    parser.add_argument("--views", type=Path, default=None,
                        help="also save renders from the north-east, south-west and north-west here (not committed)")
    args = parser.parse_args(argv)
    sources = find_sources(args.pieces)
    if not sources:
        print(f"no piece sources under {SOURCES}", file=sys.stderr)
        return 1
    problems = []
    with Lab(args.port, args.lab_cmd) as lab:
        for source in sources:
            problems += build(lab, source, args.views)
    for problem in problems:
        print(problem, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
