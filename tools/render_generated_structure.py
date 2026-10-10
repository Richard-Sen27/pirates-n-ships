"""Render a generated structure's region dump (STR2) as a top-down map and an isometric view, for the human to judge
a structure's layout without starting the game.

Usage:
    python3 tools/render_generated_structure.py DUMP.schem [DUMP.schem ...] --out DIR [--name NAME]
                                                [--scale 8] [--iso-scale 9] [--iso local|lab] [--port 8766]

``DUMP.schem`` is the Sponge v2 schematic ``StructureLayoutGameTests`` writes for a placed structure (the structure's
bounding box and 8 blocks round it, ``world/structure/RegionSchematic``), e.g. ``pirate_island_11.schem``. For each
dump it writes ``DIR/<NAME or the dump's stem>_top.png`` and ``_iso.png``:

- **top**: every column's topmost block seen from above, in rough Minecraft colours (``tools/iso_render.colour``),
  lit from the north-west by the height difference to the neighbours and darkened with depth under water, ``--scale``
  pixels per block, north up;
- **iso**: the south-east isometric view. ``--iso local`` (default) draws it with ``tools/iso_render.render``
  (slabs, stairs, fences, trapdoors and the like in their real shapes, rough Minecraft colours; no lab needed).
  ``--iso lab`` posts the region as a build to the minecraft-schematic-lab's HTTP API instead
  (``POST /api/session/build`` with the blocks as ``box`` runs, then ``GET /api/session/preview.png``), starting the
  lab on ``--port`` like ``tools/build_structures.py`` when none answers there; the lab's preview draws whole cubes in
  its own colour table, which paints most stairs, slabs and wool of a camp blue, so it is only a cross-check. Only
  exposed blocks go into the view (a block with air beside or above it), so the buried sand and the sea under its
  surface stay out.

Standard library only. ``art/README.md`` ("Natural layout (STR2)") says how to regenerate the committed renders.
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import iso_render  # noqa: E402  (same folder)
from schem_to_structure import read_nbt, read_schematic  # noqa: E402

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void"}
WATER = "minecraft:water"
SEE_THROUGH = ("glass", "pane", "fence", "chain", "torch", "lantern", "carpet", "button", "pressure_plate", "ladder",
               "trapdoor", "door", "sign", "banner", "skull", "rail", "bush", "grass", "fern", "flower", "cobweb",
               "leaves", "slab", "stairs", "campfire", "bed", "hammock", "rope_coil", "kelp", "seagrass", "lily_pad",
               "vine", "bars", "flagpole", "bell", "ring", "cleat", "map_tile", "jigsaw")


def name(state: str) -> str:
    return state.split("[", 1)[0]


def load(path: Path) -> tuple[dict, tuple[int, int, int]]:
    schem = read_schematic(read_nbt(path.read_bytes()))
    states = {pos: s for pos, s in schem["states"].items() if name(s) not in AIR}
    return states, schem["size"]


def opaque(state: str) -> bool:
    n = name(state)
    return n != WATER and not any(k in n for k in SEE_THROUGH)


def exposed(states: dict) -> dict:
    """The blocks that can be seen: something not opaque (or nothing) above or beside them."""
    out = {}
    for (x, y, z), s in states.items():
        for d in ((0, 1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
            n = states.get((x + d[0], y + d[1], z + d[2]))
            if n is None or (not opaque(n) and not (name(n) == WATER and name(s) == WATER)):
                out[(x, y, z)] = s
                break
    return out


def top_down(states: dict, size: tuple[int, int, int], scale: int) -> bytes:
    sx, sy, sz = size
    top = {}
    water_top = {}
    for (x, y, z), s in states.items():
        if name(s) == WATER:
            if y > water_top.get((x, z), -1):
                water_top[(x, z)] = y
            continue
        if y > top.get((x, z), (-1, None))[0]:
            top[(x, z)] = (y, s)
    width, height = sx * scale, sz * scale
    img = bytearray(bytes((24, 28, 34)) * (width * height))
    for z in range(sz):
        for x in range(sx):
            ground = top.get((x, z))
            wy = water_top.get((x, z), -1)
            if ground is None and wy < 0:
                continue
            gy = ground[0] if ground else -1
            if wy > gy:
                depth = wy - gy
                base = (63, 118, 228)
                k = max(0.45, 1.0 - 0.08 * depth)
                rgb = tuple(int(c * k) for c in base)
                if ground is not None and depth <= 2:      # the shallows show the bottom through the water
                    g = iso_render.colour(ground[1])
                    rgb = tuple(int(0.55 * a + 0.45 * b) for a, b in zip(rgb, g))
            else:
                rgb = iso_render.colour(ground[1])
                north = top.get((x, z - 1), (gy, None))[0]
                west = top.get((x - 1, z), (gy, None))[0]
                light = 1.0 + 0.12 * max(-3, min(3, (gy - north) + (gy - west)))
                light *= 0.85 + 0.15 * (gy / max(1, sy - 1))
                rgb = tuple(max(0, min(255, int(c * light))) for c in rgb)
            for py in range(z * scale, (z + 1) * scale):
                row = py * width
                for px in range(x * scale, (x + 1) * scale):
                    edge = scale >= 4 and (px % scale == 0 or py % scale == 0)
                    i = (row + px) * 3
                    img[i:i + 3] = bytes(int(c * 0.9) for c in rgb) if edge else bytes(rgb)
    return iso_render.png(width, height, img)


def spec(states: dict, size: tuple[int, int, int], title: str) -> dict:
    """A BuildSpec of the blocks (runs of one state along x become one box)."""
    palette, keys = {}, {}
    for s in sorted(set(states.values())):
        key = f"b{len(palette)}"
        palette[key] = s
        keys[s] = key
    ops = []
    rows = {}
    for (x, y, z) in states:
        rows.setdefault((y, z), []).append(x)
    for (y, z) in sorted(rows):
        xs = sorted(rows[(y, z)])
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1 and states[(x, y, z)] == states[(start, y, z)]:
                prev = x
                continue
            ops.append({"type": "box", "from": [start, y, z], "to": [prev, y, z], "block": keys[states[(start, y, z)]]})
            if x is not None:
                start = prev = x
    sx, sy, sz = size
    return {"id": f"pns_generated_{title}", "name": title, "minecraftVersion": "1.21.1",
            "size": {"x": sx, "y": sy, "z": sz}, "palette": palette, "operations": ops,
            "metadata": {"style": "generated structure region (STR2)", "notes": ["tools/render_generated_structure.py"]}}


def iso_lab(lab, states: dict, size: tuple[int, int, int], title: str) -> bytes:
    result = lab.post("/api/session/build", spec(states, size, title))
    if not result.get("valid"):
        raise RuntimeError(f"the lab refused the region: {result.get('errors')}")
    return lab.get("/api/session/preview.png")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("dumps", type=Path, nargs="+", help="region dumps (Sponge .schem) from StructureLayoutGameTests")
    parser.add_argument("--out", type=Path, required=True, help="folder for <name>_top.png and <name>_iso.png")
    parser.add_argument("--name", action="append", default=None,
                        help="output name per dump, in order (default: the dump's file name without .schem)")
    parser.add_argument("--scale", type=int, default=8, help="top view: pixels per block (default 8)")
    parser.add_argument("--iso-scale", type=int, default=9, help="local iso view: pixels per block edge (default 9)")
    parser.add_argument("--iso", choices=("local", "lab"), default="local", help="isometric renderer (default local)")
    parser.add_argument("--port", type=int, default=int(os.environ.get("SCHEMATIC_LAB_PORT", 8766)),
                        help="lab port (default: $SCHEMATIC_LAB_PORT, else 8766)")
    parser.add_argument("--lab-cmd", default=None, help="command that starts the lab (default: build_structures')")
    args = parser.parse_args(argv)
    args.out.mkdir(parents=True, exist_ok=True)
    names = args.name or [d.stem for d in args.dumps]
    if len(names) != len(args.dumps):
        parser.error("give one --name per dump")
    loaded = []
    for dump, title in zip(args.dumps, names):
        states, size = load(dump)
        (args.out / f"{title}_top.png").write_bytes(top_down(states, size, args.scale))
        print(f"{dump.name}: {size[0]}x{size[1]}x{size[2]}, {len(states)} blocks -> {title}_top.png")
        loaded.append((title, exposed(states), size))
    if args.iso == "local":
        for title, states, size in loaded:
            (args.out / f"{title}_iso.png").write_bytes(iso_render.render(states, size, 0, args.iso_scale, None))
            print(f"{title}_iso.png (local, {len(states)} exposed blocks)")
        return 0
    import build_structures  # noqa: E402  (same folder; the lab client)
    with build_structures.Lab(args.port, args.lab_cmd or build_structures.LAB_CMD) as lab:
        for title, states, size in loaded:
            (args.out / f"{title}_iso.png").write_bytes(iso_lab(lab, states, size, title))
            print(f"{title}_iso.png (lab, {len(states)} exposed blocks)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
